package org.emfvector.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the phone's own radios and sensors and converts the readings into the
 * paper's normalized exposure vector. Downlink values are derived from measured
 * received power; uplink values (the phone's own transmitter) are estimated from
 * 3GPP open-loop power control and live traffic counters, because Android does not
 * expose actual transmit power to apps.
 */
public final class RadioMonitor implements SensorEventListener {
    public static final int UNAVAILABLE = Integer.MAX_VALUE;

    public static final class CellReading {
        public String tech, band, label;
        public boolean registered;
        public double dlMHz, ulMHz, bwMHz = Double.NaN;
        public int primary = UNAVAILABLE;   // RSRP / SS-RSRP / RSCP / RSSI (dBm)
        public int rssi = UNAVAILABLE, quality = UNAVAILABLE, sinr = UNAVAILABLE, ta = UNAVAILABLE, pci = UNAVAILABLE;
        public String primaryName;
        public boolean freqAssumed;
        // derived
        public double dlTotalDbm, dlSfull, plDb, ulTxDbm, ulAvgMw;
    }

    public static final class WifiReading {
        public String ssid, bssid;
        public int freqMHz, rssi;
        public boolean connected;
        public int linkMbps = -1, txLinkMbps = -1;
    }

    public static final class BleDevice {
        public String address, name;
        public int rssi;
        public long seen;
    }

    public static final class Snapshot {
        public long time;
        public final List<CellReading> cells = new ArrayList<>();
        public final List<WifiReading> wifi = new ArrayList<>();
        public final List<BleDevice> ble = new ArrayList<>();
        public final List<Model.Component> comps = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public double mobTxKbps, mobRxKbps, wifiTxKbps, wifiRxKbps;
        public double ulDutyCell, ulDutyWifi, dlDutyWifi, btDuty;
        public boolean inCall, btAudio, btOn;
        public String mode;
        public double magStaticUt = Double.NaN, magAcUt = Double.NaN, magRateHz = Double.NaN;
        public double sarHeadTotal, sarBodyTotal;
    }

    private final Context ctx;
    private final TelephonyManager tm;
    private final WifiManager wm;
    private final BluetoothAdapter bt;
    private final SensorManager sm;
    private final Map<String, BleDevice> bleMap = new HashMap<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCb;
    private long lastT;
    private long lastMobTx = -1, lastMobRx, lastTotTx, lastTotRx;
    private double mobTxBps, mobRxBps, wifiTxBps, wifiRxBps;

    // magnetometer
    private final double[] magBuf = new double[512];
    private int magN, magIdx;
    private long magFirstNs, magLastNs, magCount;

    public RadioMonitor(Context c) {
        ctx = c.getApplicationContext();
        tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        BluetoothAdapter b = null;
        try { b = BluetoothAdapter.getDefaultAdapter(); } catch (Throwable ignored) {}
        bt = b;
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
    }

    // ------------------------------------------------------------------ lifecycle
    public void start() {
        try {
            Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_FASTEST);
        } catch (Throwable ignored) {}
        startBle();
    }

    public void stop() {
        try { sm.unregisterListener(this); } catch (Throwable ignored) {}
        stopBle();
    }

    public void requestWifiScan() {
        try { if (wm != null) wm.startScan(); } catch (Throwable ignored) {}
    }

    private void startBle() {
        try {
            if (bt == null || !bt.isEnabled() || scanCb != null) return;
            if (Build.VERSION.SDK_INT >= 31 && !granted("android.permission.BLUETOOTH_SCAN")) return;
            if (Build.VERSION.SDK_INT < 31 && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) return;
            scanner = bt.getBluetoothLeScanner();
            if (scanner == null) return;
            scanCb = new ScanCallback() {
                @Override public void onScanResult(int type, ScanResult r) { onBle(r); }
                @Override public void onBatchScanResults(List<ScanResult> results) { for (ScanResult r : results) onBle(r); }
            };
            scanner.startScan(scanCb);
        } catch (Throwable t) { scanCb = null; }
    }

    private void stopBle() {
        try { if (scanner != null && scanCb != null) scanner.stopScan(scanCb); } catch (Throwable ignored) {}
        scanCb = null;
    }

    private void onBle(ScanResult r) {
        try {
            String addr = r.getDevice().getAddress();
            BleDevice d;
            synchronized (bleMap) {
                d = bleMap.get(addr);
                if (d == null) { d = new BleDevice(); d.address = addr; bleMap.put(addr, d); }
                d.rssi = r.getRssi();
                d.seen = System.currentTimeMillis();
                String n = null;
                try { if (r.getScanRecord() != null) n = r.getScanRecord().getDeviceName(); } catch (Throwable ignored) {}
                if (n != null) d.name = n;
            }
        } catch (Throwable ignored) {}
    }

    @Override public void onSensorChanged(SensorEvent e) {
        double m = Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
        synchronized (magBuf) {
            magBuf[magIdx] = m;
            magIdx = (magIdx + 1) % magBuf.length;
            if (magN < magBuf.length) magN++;
            if (magCount == 0) magFirstNs = e.timestamp;
            magLastNs = e.timestamp;
            magCount++;
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    public boolean granted(String perm) {
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------ snapshot
    public Snapshot read() {
        Model.Settings s = Model.SETTINGS;
        Snapshot snap = new Snapshot();
        snap.time = System.currentTimeMillis();
        updateTraffic(snap);
        checkEnvironment(snap);
        try { snap.inCall = tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE; } catch (Throwable ignored) {}
        readBtState(snap);

        int mode = s.useMode;
        if (mode == 0) mode = snap.inCall ? 1 : 3;
        snap.mode = mode == 1 ? "At head (call)" : mode == 2 ? "On body (pocket)" : "In hand";

        readCells(snap);
        readWifi(snap);
        readBle(snap);
        readMag(snap);

        buildCellComponents(snap, mode);
        buildWifiComponents(snap, mode);
        buildBtComponents(snap, mode);
        buildMagComponents(snap);
        startBle(); // retry if permissions were just granted
        return snap;
    }

    private void checkEnvironment(Snapshot snap) {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION))
            snap.notes.add("Location permission missing: Android hides cell and Wi-Fi measurements without it.");
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            boolean on = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!on) snap.notes.add("Location services are off: turn them on so Android returns cell and Wi-Fi scan data.");
        } catch (Throwable ignored) {}
        if (!granted(Manifest.permission.READ_PHONE_STATE))
            snap.notes.add("Phone-state permission missing: call detection (head-SAR mode) is disabled.");
    }

    private void updateTraffic(Snapshot snap) {
        long now = System.currentTimeMillis();
        long mtx = TrafficStats.getMobileTxBytes(), mrx = TrafficStats.getMobileRxBytes();
        long ttx = TrafficStats.getTotalTxBytes(), trx = TrafficStats.getTotalRxBytes();
        if (mtx < 0) mtx = 0;
        if (mrx < 0) mrx = 0;
        if (lastMobTx >= 0 && now > lastT) {
            double dt = (now - lastT) / 1000.0;
            mobTxBps = Math.max(0, (mtx - lastMobTx) * 8 / dt);
            mobRxBps = Math.max(0, (mrx - lastMobRx) * 8 / dt);
            wifiTxBps = Math.max(0, ((ttx - mtx) - (lastTotTx - lastMobTx)) * 8 / dt);
            wifiRxBps = Math.max(0, ((trx - mrx) - (lastTotRx - lastMobRx)) * 8 / dt);
        }
        lastT = now; lastMobTx = mtx; lastMobRx = mrx; lastTotTx = ttx; lastTotRx = trx;
        snap.mobTxKbps = mobTxBps / 1e3; snap.mobRxKbps = mobRxBps / 1e3;
        snap.wifiTxKbps = wifiTxBps / 1e3; snap.wifiRxKbps = wifiRxBps / 1e3;
    }

    private void readBtState(Snapshot snap) {
        try {
            snap.btOn = bt != null && bt.isEnabled();
            if (snap.btOn) {
                int a2dp = bt.getProfileConnectionState(BluetoothProfile.A2DP);
                int hs = bt.getProfileConnectionState(BluetoothProfile.HEADSET);
                snap.btAudio = a2dp == BluetoothProfile.STATE_CONNECTED || hs == BluetoothProfile.STATE_CONNECTED;
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ cellular
    private void readCells(Snapshot snap) {
        if (tm == null) return;
        List<CellInfo> infos = null;
        try { infos = tm.getAllCellInfo(); } catch (Throwable t) { snap.notes.add("Cell info unavailable: " + t.getClass().getSimpleName()); }
        if (infos == null) return;
        for (CellInfo ci : infos) {
            try {
                CellReading r = parseCell(ci);
                if (r != null) snap.cells.add(r);
            } catch (Throwable ignored) {}
        }
        addSignalStrengthFallback(snap);
        // serving cells first
        java.util.Collections.sort(snap.cells, new java.util.Comparator<CellReading>() {
            public int compare(CellReading a, CellReading b) {
                if (a.registered != b.registered) return a.registered ? -1 : 1;
                return Integer.compare(b.primary == UNAVAILABLE ? -999 : b.primary, a.primary == UNAVAILABLE ? -999 : a.primary);
            }
        });
    }

    /**
     * SignalStrength (API 28+) needs no location permission and also carries the 5G NSA leg,
     * which many phones omit from getAllCellInfo().
     */
    private void addSignalStrengthFallback(Snapshot snap) {
        boolean haveNr = false, haveAny = !snap.cells.isEmpty();
        for (CellReading r : snap.cells) if (r.tech.equals("NR")) haveNr = true;
        Object sig = call(tm, "getSignalStrength");
        Object list = call(sig, "getCellSignalStrengths");
        if (!(list instanceof List)) return;
        boolean first = true;
        for (Object ss : (List<?>) list) {
            String cls = ss.getClass().getSimpleName();
            boolean isNr = cls.equals("CellSignalStrengthNr");
            if (haveAny && !(isNr && !haveNr)) continue;
            CellReading r = new CellReading();
            if (!fillSignal(r, cls.replace("CellSignalStrength", "CellInfo"), ss)) continue;
            Bands.Carrier car = Bands.fallback(r.tech);
            r.freqAssumed = true;
            r.dlMHz = car.dlMHz; r.ulMHz = car.ulMHz; r.band = car.band;
            r.registered = !haveAny && first;
            first = false;
            r.label = r.tech + (isNr && haveAny ? " 5G NSA leg" : " " + r.band) + (r.registered ? " (serving)" : "");
            if (r.primary != UNAVAILABLE && (r.primary > -20 || r.primary < -160)) continue;
            snap.cells.add(r);
        }
    }

    private boolean fillSignal(CellReading r, String cls, Object ss) {
        switch (cls) {
            case "CellInfoLte":
                r.tech = "LTE";
                r.primary = callInt(ss, "getRsrp");
                if (r.primary == UNAVAILABLE) r.primary = callInt(ss, "getDbm");
                r.primaryName = "RSRP";
                r.rssi = callInt(ss, "getRssi"); r.quality = callInt(ss, "getRsrq");
                r.sinr = callInt(ss, "getRssnr"); r.ta = callInt(ss, "getTimingAdvance");
                return true;
            case "CellInfoNr":
                r.tech = "NR";
                r.primary = callInt(ss, "getSsRsrp");
                if (r.primary == UNAVAILABLE) r.primary = callInt(ss, "getCsiRsrp");
                if (r.primary == UNAVAILABLE) r.primary = callInt(ss, "getDbm");
                r.primaryName = "SS-RSRP";
                r.quality = callInt(ss, "getSsRsrq"); r.sinr = callInt(ss, "getSsSinr");
                return true;
            case "CellInfoGsm":
                r.tech = "GSM"; r.primary = callInt(ss, "getDbm"); r.primaryName = "RSSI"; r.ta = callInt(ss, "getTimingAdvance");
                return true;
            case "CellInfoWcdma":
            case "CellInfoTdscdma":
                r.tech = "UMTS"; r.primary = callInt(ss, "getDbm"); r.primaryName = "RSCP"; r.quality = callInt(ss, "getEcNo");
                return true;
            default:
                return false;
        }
    }

    private CellReading parseCell(CellInfo ci) {
        String cls = ci.getClass().getSimpleName();
        CellReading r = new CellReading();
        r.registered = ci.isRegistered();
        Object id = call(ci, "getCellIdentity");
        Object ss = call(ci, "getCellSignalStrength");
        Bands.Carrier car;
        switch (cls) {
            case "CellInfoLte": {
                r.tech = "LTE";
                int earfcn = callInt(id, "getEarfcn");
                car = earfcn != UNAVAILABLE ? Bands.lte(earfcn) : null;
                int bwKhz = callInt(id, "getBandwidth");
                if (bwKhz != UNAVAILABLE && bwKhz > 0) r.bwMHz = bwKhz / 1000.0;
                r.pci = callInt(id, "getPci");
                r.primary = callInt(ss, "getRsrp");
                if (r.primary == UNAVAILABLE) r.primary = callInt(ss, "getDbm");
                r.primaryName = "RSRP";
                r.rssi = callInt(ss, "getRssi");
                r.quality = callInt(ss, "getRsrq");
                r.sinr = callInt(ss, "getRssnr");
                r.ta = callInt(ss, "getTimingAdvance");
                break;
            }
            case "CellInfoNr": {
                r.tech = "NR";
                int arfcn = callInt(id, "getNrarfcn");
                car = arfcn != UNAVAILABLE ? Bands.nr(arfcn) : null;
                r.pci = callInt(id, "getPci");
                r.primary = callInt(ss, "getSsRsrp");
                if (r.primary == UNAVAILABLE) r.primary = callInt(ss, "getCsiRsrp");
                if (r.primary == UNAVAILABLE) r.primary = callInt(ss, "getDbm");
                r.primaryName = "SS-RSRP";
                r.quality = callInt(ss, "getSsRsrq");
                r.sinr = callInt(ss, "getSsSinr");
                break;
            }
            case "CellInfoGsm": {
                r.tech = "GSM";
                int arfcn = callInt(id, "getArfcn");
                car = arfcn != UNAVAILABLE ? Bands.gsm(arfcn) : null;
                r.primary = callInt(ss, "getDbm");
                r.primaryName = "RSSI";
                r.ta = callInt(ss, "getTimingAdvance");
                break;
            }
            case "CellInfoWcdma":
            case "CellInfoTdscdma": {
                r.tech = "UMTS";
                int u = callInt(id, "getUarfcn");
                car = u != UNAVAILABLE ? Bands.umts(u) : null;
                r.primary = callInt(ss, "getDbm");
                r.primaryName = "RSCP";
                r.quality = callInt(ss, "getEcNo");
                break;
            }
            default:
                return null;
        }
        if (car == null || Double.isNaN(car.dlMHz)) { car = Bands.fallback(r.tech); r.freqAssumed = true; }
        r.dlMHz = car.dlMHz; r.ulMHz = car.ulMHz; r.band = car.band;
        // sanity: Android uses large sentinels and occasionally positive garbage
        if (r.primary != UNAVAILABLE && (r.primary > -20 || r.primary < -160)) r.primary = UNAVAILABLE;
        if (r.rssi != UNAVAILABLE && (r.rssi > -10 || r.rssi < -140)) r.rssi = UNAVAILABLE;
        r.label = r.tech + " " + r.band + (r.pci != UNAVAILABLE ? " PCI " + r.pci : "") + (r.registered ? " (serving)" : " (neighbour)");
        return r;
    }

    private void buildCellComponents(Snapshot snap, int mode) {
        Model.Settings s = Model.SETTINGS;
        boolean dataOnCell = snap.mobTxKbps + snap.mobRxKbps > 0.5;
        double duty = Math.min(1, mobTxBps / (s.ulCapMbps * 1e6));
        if (dataOnCell) duty = Math.max(duty, 0.005); // HARQ ACK, CQI, SR overhead while connected
        if (snap.inCall) duty = Math.max(duty, 0.10); // VoLTE/VoNR: ~1 TTI per 20 ms plus control (assumed)
        snap.ulDutyCell = duty;
        boolean servingUplinkDone = false;

        for (CellReading r : snap.cells) {
            if (r.primary == UNAVAILABLE) continue;
            double fDl = r.dlMHz * 1e6;
            // ---- downlink: received power -> incident power density
            double extraDb;
            String how;
            switch (r.tech) {
                case "LTE": {
                    double bw = Double.isNaN(r.bwMHz) ? 20 : r.bwMHz;
                    double nre = Physics.resourceElements(bw, 15);
                    extraDb = 10 * Math.log10(nre);
                    how = String.format(Locale.US, "RSRP %d dBm per RE × %.0f RE (%s MHz%s) = full-load carrier power", r.primary, nre, Physics.fmt(bw), Double.isNaN(r.bwMHz) ? ", assumed" : "");
                    break;
                }
                case "NR": {
                    double nre = Physics.resourceElements(100, 30);
                    extraDb = 10 * Math.log10(nre);
                    how = String.format(Locale.US, "SS-RSRP %d dBm per RE × %.0f RE (100 MHz @30 kHz, assumed) = full-load, peak-beam", r.primary, nre);
                    break;
                }
                case "UMTS":
                    extraDb = 10; how = "CPICH RSCP + 10 dB (pilot ≈ 10 % of carrier power)"; break;
                default:
                    extraDb = 0; how = "GSM BCCH RSSI (transmitted continuously at full power)";
            }
            r.dlTotalDbm = r.primary + extraDb;
            r.dlSfull = Physics.sFromReceived(Physics.dbmToW(r.dlTotalDbm), fDl, s.rxGainDbi);
            Model.Component c = new Model.Component(r.label, r.tech, "Downlink", "measured → extrapolated", fDl,
                    Model.Q_S, "W/m²", r.dlSfull, Physics.refLevelS(fDl, s.occupational));
            StringBuilder d = new StringBuilder(how);
            d.append(String.format(Locale.US, "; S = P/Aₑ with Aₑ = Gλ²/4π, G = %s dBi (assumed). E = %s V/m.",
                    Physics.fmt(s.rxGainDbi), Physics.fmt(Physics.eFromS(r.dlSfull))));
            if (r.rssi != UNAVAILABLE) {
                double sNow = Physics.sFromReceived(Physics.dbmToW(r.rssi), fDl, s.rxGainDbi);
                d.append(" Current-load S from RSSI ").append(r.rssi).append(" dBm: ").append(Physics.fmt(sNow)).append(" W/m².");
            }
            if (r.tech.equals("NR"))
                d.append(String.format(Locale.US, " Time-averaged with TDD %.2f × beam %.2f: %s W/m².", s.tddDl, s.beamFactor, Physics.fmt(r.dlSfull * s.tddDl * s.beamFactor)));
            if (r.freqAssumed) d.append(" Channel number hidden by OS: frequency assumed.");
            c.detail = d.toString();
            c.uncDb = r.freqAssumed ? 5.2 : 4.8; // RSSI 2, antenna gain 2.5, load extrapolation 3, body shadowing 2 dB
            snap.comps.add(c);

            // ---- uplink: only the serving cell carries the phone's transmitter
            if (!r.registered || servingUplinkDone) continue;
            servingUplinkDone = true;
            double txDbm;
            String ulHow;
            switch (r.tech) {
                case "LTE":
                case "NR": {
                    r.plDb = s.rsEpreDbm - r.primary;
                    txDbm = Math.min(s.pcMaxDbm, s.p0Dbm + 10 * Math.log10(Math.max(1, s.ulPrb)) + s.alpha * r.plDb);
                    ulHow = String.format(Locale.US, "Open-loop PUSCH: P = min(%s, P0 %s + 10log(%d PRB) + %.1f × PL %.0f dB) = %.1f dBm",
                            Physics.fmt(s.pcMaxDbm), Physics.fmt(s.p0Dbm), s.ulPrb, s.alpha, r.plDb, txDbm);
                    break;
                }
                case "UMTS": {
                    r.plDb = 33 - r.primary;
                    txDbm = Math.max(-50, Math.min(24, 33 - r.primary - 103 - 10));
                    ulHow = String.format(Locale.US, "Open-loop: P = CPICH 33 − RSCP %d − interference 103 − 10 = %.1f dBm", r.primary, txDbm);
                    break;
                }
                default: {
                    r.plDb = 50 - r.primary;
                    double pmax = r.dlMHz > 1500 ? 30 : 33;
                    txDbm = Math.max(5, Math.min(pmax, -95 + r.plDb));
                    ulHow = String.format(Locale.US, "GSM power control: P = clamp(target −95 + PL %.0f dB) = %.1f dBm peak, ÷8 TDMA slots", r.plDb, txDbm);
                }
            }
            double peakMw = Math.pow(10, txDbm / 10);
            double avgMw = peakMw * duty * (r.tech.equals("GSM") ? 1.0 / 8 : 1);
            if (r.tech.equals("GSM") && snap.inCall) avgMw = peakMw / 8;
            r.ulTxDbm = txDbm;
            r.ulAvgMw = avgMw;
            addUplink(snap, r.tech + " " + r.band + " uplink (phone TX)", r.tech, r.ulMHz * 1e6, avgMw, peakMw, mode,
                    ulHow + String.format(Locale.US, "; uplink duty %.3f from live traffic %.1f kbit/s%s.",
                            duty, snap.mobTxKbps, snap.inCall ? " (call detected)" : ""));
        }
    }

    /** Converts estimated average transmit power into the frequency-appropriate local dosimetric quantity. */
    private void addUplink(Snapshot snap, String name, String tech, double fHz, double avgMw, double peakMw, int mode, String how) {
        Model.Settings s = Model.SETTINGS;
        if (fHz > 6e9) {
            // mmWave: SAR is not the governing quantity; screen with S_ab at body distance (Case Study 5).
            double eirpW = avgMw / 1000 * Physics.dbToLin(10); // 10 dBi array gain, assumed
            double d = Math.max(0.005, s.bodyDistanceCm / 100);
            double sab = Physics.sFar(eirpW, d);
            Model.Component c = new Model.Component(name, tech, "Uplink", "estimated", fHz, Model.Q_SAB, "W/m²", sab,
                    Physics.localAbsorbedLimit(fHz, s.occupational));
            c.detail = how + String.format(Locale.US, ". mmWave: S_ab = EIRP/(4πd²), 10 dBi array, d = %s cm, full absorption assumed (conservative).", Physics.fmt(s.bodyDistanceCm));
            c.uncDb = 7.0; // array gain, distance, power control, absorption fraction
            snap.comps.add(c);
            return;
        }
        boolean head = mode == 1;
        double declared = head ? s.sarHeadDeclared : s.sarBodyDeclared;
        double sar = declared * avgMw / s.sarRefMw;
        String q = head ? Model.Q_SAR_HEAD : Model.Q_SAR_BODY;
        double limit = mode == 3 ? Physics.sarLimitLimbs(s.occupational) : Physics.sarLimitHead(s.occupational);
        Model.Component c = new Model.Component(name, tech, "Uplink", "estimated", fHz, q, "W/kg", sar, limit);
        c.detail = how + String.format(Locale.US, ". Average TX %s mW (peak %s mW). SAR = declared %s W/kg × %s/%s mW%s. ΔT adiabatic 6 min = %s K.",
                Physics.fmt(avgMw), Physics.fmt(peakMw), Physics.fmt(declared), Physics.fmt(avgMw), Physics.fmt(s.sarRefMw),
                mode == 3 ? "; hand → limbs limit" : "", Physics.fmt(Physics.dTAdiabatic(sar, 360, 3696)));
        c.uncDb = 6.6; // power-control model 5, duty 3, position 3, declared-SAR lab uncertainty 0.6 dB
        snap.comps.add(c);
        if (head) snap.sarHeadTotal += sar; else snap.sarBodyTotal += sar;
    }

    // ------------------------------------------------------------------ Wi-Fi
    private void readWifi(Snapshot snap) {
        if (wm == null) return;
        String connectedBssid = null;
        try {
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null && wi.getNetworkId() != -1 && wi.getRssi() > -127) {
                WifiReading r = new WifiReading();
                r.connected = true;
                r.ssid = clean(wi.getSSID());
                r.bssid = wi.getBSSID();
                r.rssi = wi.getRssi();
                r.freqMHz = wi.getFrequency();
                r.linkMbps = wi.getLinkSpeed();
                int tx = callInt(wi, "getTxLinkSpeedMbps");
                r.txLinkMbps = tx != UNAVAILABLE && tx > 0 ? tx : r.linkMbps;
                snap.wifi.add(r);
                connectedBssid = r.bssid;
            }
        } catch (Throwable ignored) {}
        try {
            List<android.net.wifi.ScanResult> res = wm.getScanResults();
            if (res != null) {
                java.util.Collections.sort(res, new java.util.Comparator<android.net.wifi.ScanResult>() { public int compare(android.net.wifi.ScanResult a, android.net.wifi.ScanResult b) { return Integer.compare(b.level, a.level); } });
                int n = 0;
                for (android.net.wifi.ScanResult sr : res) {
                    if (sr.BSSID != null && sr.BSSID.equals(connectedBssid)) continue;
                    if (n++ >= 12) break;
                    WifiReading r = new WifiReading();
                    r.ssid = sr.SSID == null || sr.SSID.isEmpty() ? "(hidden)" : sr.SSID;
                    r.bssid = sr.BSSID; r.rssi = sr.level; r.freqMHz = sr.frequency;
                    snap.wifi.add(r);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void buildWifiComponents(Snapshot snap, int mode) {
        Model.Settings s = Model.SETTINGS;
        for (WifiReading r : snap.wifi) {
            if (r.freqMHz <= 0) continue;
            double f = r.freqMHz * 1e6;
            double sPeak = Physics.sFromReceived(Physics.dbmToW(r.rssi), f, s.rxGainDbi);
            double duty;
            String how;
            if (r.connected) {
                double link = Math.max(1, r.linkMbps) * 1e6 * 0.6;
                duty = Math.min(1, Math.max(0.02, wifiRxBps / link));
                snap.dlDutyWifi = duty;
                how = String.format(Locale.US, "AP duty %.3f measured from %.1f kbit/s downlink at %d Mbit/s link (+2 %% beacons)", duty, snap.wifiRxKbps, r.linkMbps);
            } else {
                duty = 0.02;
                how = "beacon-only duty 2 % (assumed; traffic to other clients unknown)";
            }
            Model.Component c = new Model.Component("Wi-Fi " + band(r.freqMHz) + " · " + r.ssid + (r.connected ? " (connected)" : ""),
                    "Wi-Fi", "Downlink", "measured", f, Model.Q_S, "W/m²", sPeak * duty, Physics.refLevelS(f, s.occupational));
            c.detail = String.format(Locale.US, "RSSI %d dBm → peak S %s W/m² (E %s V/m); %s.", r.rssi, Physics.fmt(sPeak), Physics.fmt(Physics.eFromS(sPeak)), how);
            c.uncDb = r.connected ? 4.9 : 5.5;
            snap.comps.add(c);

            if (r.connected) {
                double link = Math.max(1, r.txLinkMbps) * 1e6 * 0.6;
                double ulDuty = Math.min(1, Math.max(0.002, wifiTxBps / link + 0.1 * wifiRxBps / link)); // + block ACKs
                snap.ulDutyWifi = ulDuty;
                double peakMw = Math.pow(10, s.wifiTxDbm / 10);
                addUplink(snap, "Wi-Fi " + band(r.freqMHz) + " uplink (phone TX)", "Wi-Fi", f, peakMw * ulDuty, peakMw, mode,
                        String.format(Locale.US, "Client TX %s dBm (typical, assumed); duty %.4f from %.1f kbit/s uplink at %d Mbit/s plus ACKs",
                                Physics.fmt(s.wifiTxDbm), ulDuty, snap.wifiTxKbps, r.txLinkMbps));
            }
        }
    }

    private static String band(int mhz) {
        if (mhz < 3000) return "2.4 GHz";
        if (mhz < 5925) return "5 GHz";
        if (mhz < 7200) return "6 GHz";
        return Physics.freq(mhz * 1e6);
    }

    // ------------------------------------------------------------------ Bluetooth
    private void readBle(Snapshot snap) {
        long now = System.currentTimeMillis();
        synchronized (bleMap) {
            List<String> old = new ArrayList<>();
            for (BleDevice d : bleMap.values()) if (now - d.seen > 15000) old.add(d.address); else snap.ble.add(d);
            for (String a : old) bleMap.remove(a);
        }
        java.util.Collections.sort(snap.ble, new java.util.Comparator<BleDevice>() { public int compare(BleDevice a, BleDevice b) { return Integer.compare(b.rssi, a.rssi); } });
    }

    private void buildBtComponents(Snapshot snap, int mode) {
        Model.Settings s = Model.SETTINGS;
        int n = 0;
        for (BleDevice d : snap.ble) {
            if (n++ >= 8) break;
            double f = 2.44e9;
            double sPeak = Physics.sFromReceived(Physics.dbmToW(d.rssi), f, s.rxGainDbi);
            Model.Component c = new Model.Component("Bluetooth LE · " + (d.name != null ? d.name : d.address), "Bluetooth",
                    "Downlink", "measured", f, Model.Q_S, "W/m²", sPeak * 0.01, Physics.refLevelS(f, s.occupational));
            c.detail = String.format(Locale.US, "Advertising RSSI %d dBm → peak S %s W/m²; advertising duty 1 %% (assumed).", d.rssi, Physics.fmt(sPeak));
            c.uncDb = 6.5;
            snap.comps.add(c);
        }
        if (snap.btOn) {
            double duty = snap.btAudio ? 0.30 : 0.001;
            snap.btDuty = duty;
            double peakMw = Math.pow(10, s.btTxDbm / 10);
            addUplink(snap, "Bluetooth uplink (phone TX)", "Bluetooth", 2.44e9, peakMw * duty, peakMw, mode,
                    String.format(Locale.US, "Class 2 TX %s dBm; duty %.3f (%s, assumed)", Physics.fmt(s.btTxDbm), duty,
                            snap.btAudio ? "audio link connected, as in Case Study 4" : "radio on, idle"));
        }
    }

    // ------------------------------------------------------------------ magnetometer
    private void readMag(Snapshot snap) {
        synchronized (magBuf) {
            if (magN < 10) return;
            double sum = 0;
            for (int i = 0; i < magN; i++) sum += magBuf[i];
            double mean = sum / magN, var = 0;
            for (int i = 0; i < magN; i++) var += (magBuf[i] - mean) * (magBuf[i] - mean);
            snap.magStaticUt = mean;
            snap.magAcUt = Math.sqrt(var / magN);
            if (magLastNs > magFirstNs) snap.magRateHz = (magCount - 1) / ((magLastNs - magFirstNs) / 1e9);
        }
    }

    private void buildMagComponents(Snapshot snap) {
        Model.Settings s = Model.SETTINGS;
        if (Double.isNaN(snap.magStaticUt)) return;
        Model.Component c = new Model.Component("Phone magnetometer (static field)", "Static", "Ambient", "measured", 0,
                Model.Q_STATIC, "µT", snap.magStaticUt, s.occupational ? 2_000_000 : 400_000);
        c.detail = "ICNIRP 2009 static-field limit (400 mT public, 2 T occupational head/trunk). Earth's field is about 25–65 µT; " +
                "nearby magnets, speakers and steel raise the reading.";
        c.uncDb = 0.8;
        snap.comps.add(c);
        if (snap.magRateHz >= 100) {
            Model.Component e = new Model.Component("Magnetometer AC fluctuation (indicative)", "ELF", "Ambient", "indicative", 50,
                    Model.Q_ELF_B, "µT", snap.magAcUt, Physics.elfLimitB_uT(50, s.occupational));
            e.detail = String.format(Locale.US, "Std-dev of |B| at %.0f Hz sampling. Phone magnetometers are band-limited and alias 50 Hz, so this indicates" +
                    " fluctuation only; use a calibrated ELF meter for compliance (Case Study 6).", snap.magRateHz);
            e.uncDb = 10;
            snap.comps.add(e);
        }
    }

    // ------------------------------------------------------------------ reflection helpers (APIs newer than compile SDK)
    static Object call(Object o, String m) {
        if (o == null) return null;
        try {
            Method mm = o.getClass().getMethod(m);
            return mm.invoke(o);
        } catch (Throwable t) { return null; }
    }

    static int callInt(Object o, String m) {
        Object v = call(o, m);
        if (v instanceof Integer) return (Integer) v;
        return UNAVAILABLE;
    }

    private static String clean(String ssid) {
        if (ssid == null) return "?";
        if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() > 1) ssid = ssid.substring(1, ssid.length() - 1);
        if (ssid.equals("<unknown ssid>")) return "(SSID hidden — location off)";
        return ssid;
    }
}
