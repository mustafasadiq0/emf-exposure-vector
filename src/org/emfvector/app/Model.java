package org.emfvector.app;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Shared data types and user settings. */
public final class Model {
    private Model() {}

    /** One element R_i of the normalized exposure vector (Eq. 10). */
    public static final class Component {
        public String source;      // e.g. "LTE B3 serving cell"
        public String tech;        // LTE, NR, GSM, UMTS, Wi-Fi, BT, ELF, Static...
        public String direction;   // "Downlink", "Uplink", "Ambient", "Model"
        public String provenance;  // "measured", "estimated", "assumed", "model"
        public double fHz;
        public String quantity;    // physical quantity name
        public String unit;
        public double value;
        public double limit;
        public String detail = "";
        /** Standard (1-sigma) uncertainty of value in dB, i.e. log-normal spread versus a calibrated reference. */
        public double uncDb = 3;

        public double ratio() { return (limit > 0 && !Double.isNaN(limit)) ? value / limit : Double.NaN; }

        public Component(String source, String tech, String direction, String provenance, double fHz,
                         String quantity, String unit, double value, double limit) {
            this.source = source; this.tech = tech; this.direction = direction; this.provenance = provenance;
            this.fHz = fHz; this.quantity = quantity; this.unit = unit; this.value = value; this.limit = limit;
        }
    }

    public static final String Q_S = "Incident power density S (whole body)";
    public static final String Q_SAR_HEAD = "Local SAR, head (10 g)";
    public static final String Q_SAR_BODY = "Local SAR, body/torso (10 g)";
    public static final String Q_SAB = "Absorbed power density S_ab (local, >6 GHz)";
    public static final String Q_ELF_E = "ELF electric field E";
    public static final String Q_ELF_B = "ELF magnetic flux density B";
    public static final String Q_STATIC = "Static magnetic flux density B";

    /** Same-quantity summation is allowed (Eq. D7); cross-quantity summation never is (Sec. 14.2). */
    public static List<String> quantities(List<Component> cs) {
        List<String> q = new ArrayList<>();
        for (Component c : cs) if (!q.contains(c.quantity)) q.add(c.quantity);
        return q;
    }

    public static double sumRatio(List<Component> cs, String quantity) {
        double s = 0;
        for (Component c : cs) if (c.quantity.equals(quantity) && !Double.isNaN(c.ratio())) s += c.ratio();
        return s;
    }

    /** User-adjustable assumptions, persisted. Every estimate in the app traces to one of these. */
    public static final class Settings {
        public boolean occupational = false;
        public double rxGainDbi = -3;        // assumed handset antenna gain used to convert received power to S
        public double sarHeadDeclared = 0.85; // declared max 10 g head SAR (W/kg) — from *#07# or spec sheet
        public double sarBodyDeclared = 1.0;  // declared max 10 g body-worn SAR (W/kg)
        public double sarRefMw = 200;         // max average output power at which declared SAR was measured
        public int useMode = 0;               // 0 auto, 1 at head (call), 2 on body (pocket), 3 in hand / away
        public double bodyDistanceCm = 1.5;   // device-to-body distance for mmWave S_ab screening
        public double p0Dbm = -90;            // open-loop P0 nominal PUSCH
        public double alpha = 0.8;            // fractional path-loss compensation
        public double rsEpreDbm = 15;         // base-station reference-signal power per RE
        public int ulPrb = 10;                // uplink resource blocks when transmitting
        public double pcMaxDbm = 23;          // UE power class 3
        public double ulCapMbps = 10;         // cellular uplink capacity used for duty estimate
        public double wifiTxDbm = 17;         // typical client Wi-Fi transmit power
        public double btTxDbm = 4;            // Bluetooth class 2 (2.5 mW)
        public double tddDl = 0.75, beamFactor = 0.25; // Appendix D.1 assumptions for time-averaged downlink

        public void load(SharedPreferences p) {
            occupational = p.getBoolean("occ", occupational);
            rxGainDbi = d(p, "rxg", rxGainDbi); sarHeadDeclared = d(p, "sarh", sarHeadDeclared);
            sarBodyDeclared = d(p, "sarb", sarBodyDeclared); sarRefMw = d(p, "sarref", sarRefMw);
            useMode = p.getInt("mode", useMode); bodyDistanceCm = d(p, "bodyd", bodyDistanceCm);
            p0Dbm = d(p, "p0", p0Dbm); alpha = d(p, "alpha", alpha); rsEpreDbm = d(p, "rsepre", rsEpreDbm);
            ulPrb = p.getInt("prb", ulPrb); pcMaxDbm = d(p, "pcmax", pcMaxDbm); ulCapMbps = d(p, "ulcap", ulCapMbps);
            wifiTxDbm = d(p, "wifitx", wifiTxDbm); btTxDbm = d(p, "bttx", btTxDbm);
            tddDl = d(p, "tdd", tddDl); beamFactor = d(p, "beam", beamFactor);
        }

        public void save(SharedPreferences p) {
            p.edit().putBoolean("occ", occupational)
                    .putFloat("rxg", (float) rxGainDbi).putFloat("sarh", (float) sarHeadDeclared)
                    .putFloat("sarb", (float) sarBodyDeclared).putFloat("sarref", (float) sarRefMw)
                    .putInt("mode", useMode).putFloat("bodyd", (float) bodyDistanceCm)
                    .putFloat("p0", (float) p0Dbm).putFloat("alpha", (float) alpha).putFloat("rsepre", (float) rsEpreDbm)
                    .putInt("prb", ulPrb).putFloat("pcmax", (float) pcMaxDbm).putFloat("ulcap", (float) ulCapMbps)
                    .putFloat("wifitx", (float) wifiTxDbm).putFloat("bttx", (float) btTxDbm)
                    .putFloat("tdd", (float) tddDl).putFloat("beam", (float) beamFactor).apply();
        }

        private static double d(SharedPreferences p, String k, double def) { return p.getFloat(k, (float) def); }
    }

    public static Settings SETTINGS = new Settings();
}
