package org.emfvector.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Live readings from the phone's radios turned into the paper's exposure vector (Sec. 25.2 observatory). */
public final class LiveScreen {
    public static final int REQ_EXPORT = 4711;
    private static final int MAX_HIST = 180;

    private final Activity act;
    private final RadioMonitor mon;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ScrollView root;
    private LinearLayout statusBox, notesBox, vectorBox, uplinkBox, cellBox, wifiBox, btBox, magBox;
    private VectorView vector;
    private ChartView hist;
    private Switch showDerivations;
    private Button runBtn;
    private boolean running = true, visible;
    private int tick;

    private final List<double[]> history = new ArrayList<>(); // t, sumS, sarHead/body, maxSab
    private final List<String> csv = new ArrayList<>();
    private final ExposureIndex index = new ExposureIndex();
    private LinearLayout headBtn, headDetail;
    private TextView hTitle, hBig, hSub, hDriver, hRange, hConf;
    private boolean headOpen;
    private long t0;

    public LiveScreen(Activity a) {
        act = a;
        mon = new RadioMonitor(a);
        LinearLayout[] holder = new LinearLayout[1];
        root = Ui.page(a, holder);
        build(holder[0]);
    }

    public View view() { return root; }

    private void build(LinearLayout l) {
        buildHeadline(l);
        LinearLayout head = Ui.card(act, l, "Live exposure from this phone");
        head.addView(Ui.small(act, "Downlink: measured received power from the cell network, Wi-Fi and Bluetooth around you. " +
                "Uplink: your phone's own transmitters, estimated from 3GPP power control and live traffic. " +
                "Each result is compared with the frequency-appropriate ICNIRP quantity."));
        LinearLayout btns = Ui.hrow(act);
        runBtn = Ui.button(act, "Pause", true);
        runBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleRun(); } });
        Button scan = Ui.button(act, "Scan Wi-Fi", false);
        scan.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { mon.requestWifiScan(); } });
        Button exp = Ui.button(act, "Export CSV", false);
        exp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { export(); } });
        btns.addView(runBtn); btns.addView(scan); btns.addView(exp);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(act);
        hs.addView(btns);
        head.addView(hs);
        showDerivations = Ui.toggle(act, head, "Show how each value was derived", false);
        statusBox = vbox(head);

        notesBox = vbox(l);

        LinearLayout vc = Ui.card(act, l, "Exposure vector R = [R₁ … Rₙ]");
        vc.addView(Ui.small(act, "Each bar is one source divided by its own limit (Eq. 10). Colours show the physical quantity: " +
                "blue = incident S, orange = SAR, teal = mmWave absorbed power density, purple = ELF, grey = static field."));
        vector = new VectorView(act);
        vc.addView(vector);
        vectorBox = vbox(vc);
        vc.addView(Ui.note(act, "R is a ratio to an exposure limit. It is not a probability of disease, and ratios of different " +
                "physical quantities are never added together (Sec. 14.2)."));

        LinearLayout hc = Ui.card(act, l, "Trend (this session)");
        hist = new ChartView(act, 190);
        hist.logY = true;
        hist.xLabel = "time (s)";
        hist.yLabel = "R (fraction of limit)";
        hc.addView(hist);

        uplinkBox = section(l, "Uplink: your phone's transmitters (estimated)");
        cellBox = section(l, "Cellular downlink (measured)");
        wifiBox = section(l, "Wi-Fi (measured)");
        btBox = section(l, "Bluetooth (measured)");
        magBox = section(l, "Magnetic field sensor (measured)");

        buildSettings(l);
    }

    private LinearLayout vbox(LinearLayout parent) {
        LinearLayout b = new LinearLayout(act);
        b.setOrientation(LinearLayout.VERTICAL);
        parent.addView(b);
        return b;
    }

    private LinearLayout section(LinearLayout l, String title) {
        LinearLayout c = Ui.card(act, l, title);
        return vbox(c);
    }

    // ------------------------------------------------------------------ headline button
    private void buildHeadline(LinearLayout l) {
        headBtn = new LinearLayout(act);
        headBtn.setOrientation(LinearLayout.VERTICAL);
        headBtn.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(act, 18);
        headBtn.setPadding(p, p, p, p);
        headBtn.setMinimumHeight(Ui.dp(act, 200));
        headBtn.setClickable(true);
        headBtn.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(act, 10);
        headBtn.setLayoutParams(lp);
        hTitle = center(Ui.text(act, "TOTAL EXPOSURE INDEX", 13, 0xE6FFFFFF));
        hTitle.setLetterSpacing(0.08f);
        hBig = center(Ui.text(act, "…", 58, 0xFFFFFFFF));
        hBig.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        hSub = center(Ui.text(act, "of the strictest applicable ICNIRP limit", 13.5f, 0xF2FFFFFF));
        hDriver = center(Ui.text(act, "Gathering readings…", 13, 0xE6FFFFFF));
        hRange = center(Ui.text(act, "", 13, 0xE6FFFFFF));
        hConf = center(Ui.text(act, "", 14, 0xFFFFFFFF));
        hConf.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        headBtn.addView(hTitle); headBtn.addView(hBig); headBtn.addView(hSub);
        headBtn.addView(Ui.gap(act, 8));
        headBtn.addView(hDriver); headBtn.addView(hRange); headBtn.addView(hConf);
        headBtn.addView(Ui.gap(act, 6));
        headBtn.addView(center(Ui.text(act, "Tap for breakdown and accuracy ▾", 12, 0xCCFFFFFF)));
        paintHead(Ui.MUTED);
        headBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            headOpen = !headOpen;
            headDetail.setVisibility(headOpen ? View.VISIBLE : View.GONE);
        }});
        l.addView(headBtn);
        headDetail = Ui.card(act);
        headDetail.setVisibility(View.GONE);
        l.addView(headDetail);
    }

    private TextView center(TextView t) { t.setGravity(android.view.Gravity.CENTER); return t; }

    private void paintHead(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{color, darker(color)});
        g.setCornerRadius(Ui.dp(act, 22));
        headBtn.setBackground(g);
        headBtn.setElevation(Ui.dp(act, 4));
    }

    private static int darker(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.72), g = (int) (((c >> 8) & 0xFF) * 0.72), b = (int) ((c & 0xFF) * 0.72);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String shortQuantity(String q) {
        if (q.equals(Model.Q_S)) return "whole-body RF power density";
        if (q.equals(Model.Q_SAR_HEAD)) return "SAR in the head";
        if (q.equals(Model.Q_SAR_BODY)) return "SAR in the body";
        if (q.equals(Model.Q_SAB)) return "mmWave skin absorption";
        if (q.equals(Model.Q_STATIC)) return "static magnetic field";
        if (q.equals(Model.Q_ELF_B)) return "ELF magnetic field";
        return q;
    }

    private static String mins(double sec) {
        return sec >= 60 ? String.format(Locale.US, "%.0f min", sec / 60) : String.format(Locale.US, "%.0f s", sec);
    }

    private void renderHeadline() {
        ExposureIndex.Result r = index.compute();
        if (r.top == null) {
            hBig.setText("—");
            hDriver.setText("Gathering readings… grant permissions and keep the app open");
            hRange.setText(""); hConf.setText("");
            paintHead(Ui.MUTED);
            return;
        }
        ExposureIndex.QuantityResult t = r.top;
        double unc = r.uncDb95();
        hBig.setText(Physics.pct(t.meanR));
        paintHead(t.meanR >= 1 ? Ui.C_BAD : t.meanR >= 0.2 ? 0xFFC98A00 : 0xFF178A4C);
        boolean filling = t.coveredSec < t.windowSec - 3;
        hDriver.setText(String.format(Locale.US, "Set by %s · %s average%s", shortQuantity(t.quantity), mins(t.windowSec),
                filling ? " (" + mins(t.coveredSec) + " collected so far)" : ""));
        hRange.setText("95 % range " + Physics.pct(t.lo95()) + " – " + Physics.pct(t.hi95()) +
                String.format(Locale.US, "  (±%.0f dB)", unc));
        double pb = t.pBelow();
        hConf.setText("Confidence: " + ExposureIndex.confidenceLabel(unc) + " · " +
                (pb > 0.9999 ? ">99.99" : String.format(Locale.US, "%.2f", pb * 100)) + " % likely below limit");

        headDetail.removeAllViews();
        headDetail.addView(Ui.h2(act, "What the number means"));
        headDetail.addView(Ui.body(act, "Each kind of exposure has its own limit: SAR in W/kg, power density in W/m², static field in µT. " +
                "The index is the highest of these percentages. If it is below 100 %, every limit is met. Different quantities are never " +
                "added together, because they act on the body in different ways (paper, Sec. 14.2)."));
        headDetail.addView(Ui.divider(act));
        headDetail.addView(Ui.h2(act, "Breakdown (time-averaged)"));
        String[][] rows = new String[r.all.size() + 1][4];
        rows[0] = new String[]{"Quantity", "Average", "95 % range", "Window"};
        for (int i = 0; i < r.all.size(); i++) {
            ExposureIndex.QuantityResult q = r.all.get(i);
            rows[i + 1] = new String[]{shortQuantity(q.quantity), Physics.pct(q.meanR), Physics.pct(q.lo95()) + "–" + Physics.pct(q.hi95()), mins(q.windowSec)};
        }
        headDetail.addView(Ui.table(act, rows, new float[]{1.5f, 0.9f, 1.4f, 0.7f}));
        headDetail.addView(Ui.divider(act));
        headDetail.addView(Ui.h2(act, "Accumulated SAR this session"));
        Ui.kv(act, headDetail, "Session length", mins(r.sessionSec));
        Ui.kv(act, headDetail, "Absorbed energy per kg (∫SAR dt)", Physics.fmt(r.energyJkg) + " J/kg");
        Ui.kv(act, headDetail, "Average SAR over session", Physics.fmt(r.sessionSec > 0 ? r.energyJkg / r.sessionSec : 0) + " W/kg");
        Ui.kv(act, headDetail, "Peak instantaneous SAR estimate", Physics.fmt(r.peakSar) + " W/kg");
        Ui.kv(act, headDetail, "Equivalent temperature rise, no cooling", Physics.fmt(r.energyJkg / 3696) + " K");
        headDetail.addView(Ui.small(act, "The last line is an upper bound: blood flow removes heat continuously, so the real rise is smaller (Eq. D5 vs D6)."));
        headDetail.addView(Ui.divider(act));
        headDetail.addView(Ui.h2(act, "Accuracy compared with real measuring equipment"));
        headDetail.addView(Ui.table(act, new String[][]{
                {"Method", "Typical 95 % uncertainty", "Factor"},
                {"This phone (current reading)", String.format(Locale.US, "±%.0f dB", unc), "×÷" + Physics.fmt(Math.pow(10, unc / 10))},
                {"Certified SAR lab (IEC/IEEE 62209-1528 robot + probe)", "about ±1.2 dB (±30 %)", "×÷1.3"},
                {"Calibrated RF field meter (IEC 62232)", "about ±2–3 dB", "×÷1.6–2"},
                {"Phone uplink model alone", "about ±13 dB", "×÷20"}}, new float[]{1.6f, 1.2f, 0.7f}));
        headDetail.addView(Ui.note(act, "Phones do not report their real transmit power or a calibrated field strength, so this app's numbers are " +
                "estimates. They are good for comparing places and habits and for spotting anything near a limit. A low index with high " +
                "confidence is reliable; an index near 100 % should be confirmed with a calibrated meter or a SAR lab."));
        headDetail.addView(Ui.small(act, "Uncertainty budget per component (1σ): cellular downlink ≈4.8 dB (signal reading 2, antenna gain 2.5, " +
                "load 3, body shadowing 2); Wi-Fi ≈4.9–5.5 dB; Bluetooth ≈6.5 dB; uplink SAR ≈6.6 dB (power control 5, activity 3, " +
                "position 3, lab SAR 0.6); magnetometer ≈0.8 dB."));
        Button reset = Ui.button(act, "Reset session totals", false);
        reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { index.reset(); renderHeadline(); } });
        headDetail.addView(reset);
    }

    // ------------------------------------------------------------------ settings
    private void buildSettings(LinearLayout l) {
        Model.Settings s = Model.SETTINGS;
        LinearLayout c = Ui.card(act, l, "Assumptions and phone data");
        c.addView(Ui.small(act, "Your phone's declared SAR is on its spec sheet or under Settings › About › Legal / Certification " +
                "(dial *#07# on many phones). Leave the other values at their defaults unless you know your network's parameters."));
        Spinner mode = Ui.spinner(act, c, "Phone position (sets head, body or limbs SAR)",
                new String[]{"Auto (head during a call, in hand otherwise)", "At head (call)", "On body (pocket, body-worn)", "In hand / away from head"}, s.useMode);
        Switch occ = Ui.toggle(act, c, "Compare with occupational limits (trained workers)", s.occupational);
        EditText sarH = Ui.num(act, c, "Declared head SAR, 10 g (W/kg)", s.sarHeadDeclared);
        EditText sarB = Ui.num(act, c, "Declared body SAR, 10 g (W/kg)", s.sarBodyDeclared);
        EditText ref = Ui.num(act, c, "Power at which SAR was declared (mW)", s.sarRefMw);
        EditText bodyD = Ui.num(act, c, "Body distance for mmWave (cm)", s.bodyDistanceCm);
        c.addView(Ui.divider(act));
        c.addView(Ui.small(act, "Received-power to exposure conversion"));
        EditText rxg = Ui.num(act, c, "Phone antenna gain (dBi)", s.rxGainDbi);
        EditText tdd = Ui.num(act, c, "5G TDD downlink fraction", s.tddDl);
        EditText beam = Ui.num(act, c, "5G beam/traffic factor", s.beamFactor);
        c.addView(Ui.divider(act));
        c.addView(Ui.small(act, "Uplink power-control model (3GPP TS 36.213 / 38.213 open loop)"));
        EditText p0 = Ui.num(act, c, "P0 nominal PUSCH (dBm)", s.p0Dbm);
        EditText al = Ui.num(act, c, "Path-loss factor α", s.alpha);
        EditText rs = Ui.num(act, c, "Cell reference-signal power per RE (dBm)", s.rsEpreDbm);
        EditText prb = Ui.num(act, c, "Uplink resource blocks", s.ulPrb);
        EditText pc = Ui.num(act, c, "Max UE power P_CMAX (dBm)", s.pcMaxDbm);
        EditText cap = Ui.num(act, c, "Cellular uplink capacity (Mbit/s)", s.ulCapMbps);
        EditText wtx = Ui.num(act, c, "Wi-Fi client TX power (dBm)", s.wifiTxDbm);
        EditText btx = Ui.num(act, c, "Bluetooth TX power (dBm)", s.btTxDbm);
        Button save = Ui.button(act, "Apply", true);
        Button reset = Ui.button(act, "Restore defaults", false);
        LinearLayout r = Ui.hrow(act);
        r.addView(save); r.addView(reset);
        c.addView(r);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            s.useMode = mode.getSelectedItemPosition();
            s.occupational = occ.isChecked();
            s.sarHeadDeclared = Ui.val(sarH, s.sarHeadDeclared);
            s.sarBodyDeclared = Ui.val(sarB, s.sarBodyDeclared);
            s.sarRefMw = Math.max(1, Ui.val(ref, s.sarRefMw));
            s.bodyDistanceCm = Math.max(0.1, Ui.val(bodyD, s.bodyDistanceCm));
            s.rxGainDbi = Ui.val(rxg, s.rxGainDbi);
            s.tddDl = Ui.val(tdd, s.tddDl);
            s.beamFactor = Ui.val(beam, s.beamFactor);
            s.p0Dbm = Ui.val(p0, s.p0Dbm);
            s.alpha = Ui.val(al, s.alpha);
            s.rsEpreDbm = Ui.val(rs, s.rsEpreDbm);
            s.ulPrb = (int) Math.max(1, Ui.val(prb, s.ulPrb));
            s.pcMaxDbm = Ui.val(pc, s.pcMaxDbm);
            s.ulCapMbps = Math.max(0.1, Ui.val(cap, s.ulCapMbps));
            s.wifiTxDbm = Ui.val(wtx, s.wifiTxDbm);
            s.btTxDbm = Ui.val(btx, s.btTxDbm);
            s.save(act.getSharedPreferences("emf", 0));
            android.widget.Toast.makeText(act, "Applied", android.widget.Toast.LENGTH_SHORT).show();
            update();
        }});
        reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Model.SETTINGS = new Model.Settings();
            Model.SETTINGS.save(act.getSharedPreferences("emf", 0));
            act.recreate();
        }});
    }

    // ------------------------------------------------------------------ lifecycle
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (running) update();
            h.postDelayed(this, 2000);
        }
    };

    public void resume() {
        mon.start();
        if (t0 == 0) t0 = System.currentTimeMillis();
        h.removeCallbacks(loop);
        h.post(loop);
    }

    public void pause() {
        h.removeCallbacks(loop);
        mon.stop();
    }

    public void setVisible(boolean v) { visible = v; if (v) update(); }

    private void toggleRun() {
        running = !running;
        runBtn.setText(running ? "Pause" : "Resume");
    }

    // ------------------------------------------------------------------ rendering
    private void update() {
        RadioMonitor.Snapshot s;
        try { s = mon.read(); } catch (Throwable t) { return; }
        if (tick++ % 15 == 0) mon.requestWifiScan();
        record(s);
        index.add(s);
        if (!visible) return;
        renderHeadline();
        boolean der = showDerivations.isChecked();

        statusBox.removeAllViews();
        statusBox.addView(Ui.gap(act, 4));
        Ui.kv(act, statusBox, "Position used for SAR", s.mode);
        Ui.kv(act, statusBox, "Call in progress", s.inCall ? "yes" : "no");
        Ui.kv(act, statusBox, "Mobile data ↑ / ↓", String.format(Locale.US, "%.1f / %.1f kbit/s", s.mobTxKbps, s.mobRxKbps));
        Ui.kv(act, statusBox, "Wi-Fi data ↑ / ↓", String.format(Locale.US, "%.1f / %.1f kbit/s", s.wifiTxKbps, s.wifiRxKbps));
        Ui.kv(act, statusBox, "Limits", Model.SETTINGS.occupational ? "ICNIRP occupational" : "ICNIRP general public");

        notesBox.removeAllViews();
        for (String n : s.notes) notesBox.addView(Ui.note(act, "⚠ " + n));

        vector.set(s.comps);
        vectorBox.removeAllViews();
        vectorBox.addView(Ui.small(act, "Same-quantity totals (allowed, Eq. D7): each line adds only ratios of one physical quantity."));
        for (String q : Model.quantities(s.comps)) {
            double sum = Model.sumRatio(s.comps, q);
            TextView v = Ui.kv(act, vectorBox, q, Physics.pct(sum));
            v.setTextColor(Ui.ratioColor(sum));
        }

        uplinkBox.removeAllViews();
        int nUp = 0;
        for (Model.Component c : s.comps) if (c.direction.equals("Uplink")) { comp(uplinkBox, c, der); nUp++; }
        if (nUp == 0) uplinkBox.addView(Ui.small(act, "No active transmitter detected (airplane mode, or permissions missing)."));
        else {
            uplinkBox.addView(Ui.note(act, String.format(Locale.US,
                    "Total estimated SAR now: head %s W/kg, body %s W/kg. Android does not report true transmit power, so these are " +
                            "model estimates scaled from your declared SAR. Over 6 minutes at this level the adiabatic upper-bound " +
                            "temperature rise is %s K (Eq. D5).",
                    Physics.fmt(s.sarHeadTotal), Physics.fmt(s.sarBodyTotal),
                    Physics.fmt(Physics.dTAdiabatic(Math.max(s.sarHeadTotal, s.sarBodyTotal), 360, 3696)))));
        }

        cellBox.removeAllViews();
        if (s.cells.isEmpty()) cellBox.addView(Ui.small(act, "No cell measurements (no SIM, airplane mode, or permission/location off)."));
        for (RadioMonitor.CellReading r : s.cells) {
            if (r.primary == RadioMonitor.UNAVAILABLE) continue;
            LinearLayout row = Ui.hrow(act);
            TextView t = Ui.body(act, r.label);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(t);
            row.addView(Ui.pill(act, r.primaryName + " " + r.primary + " dBm", Ui.PRIMARY));
            cellBox.addView(row);
            StringBuilder b = new StringBuilder();
            b.append(String.format(Locale.US, "DL %s MHz%s", Physics.fmt(r.dlMHz), r.freqAssumed ? " (assumed)" : ""));
            if (r.registered) b.append(String.format(Locale.US, " · UL %s MHz", Physics.fmt(r.ulMHz)));
            if (r.rssi != RadioMonitor.UNAVAILABLE) b.append(" · RSSI ").append(r.rssi).append(" dBm");
            if (r.quality != RadioMonitor.UNAVAILABLE) b.append(" · Q ").append(r.quality).append(" dB");
            if (r.sinr != RadioMonitor.UNAVAILABLE) b.append(" · SINR ").append(r.sinr);
            if (r.ta != RadioMonitor.UNAVAILABLE && r.ta >= 0 && r.ta < 2000 && r.tech.equals("LTE"))
                b.append(String.format(Locale.US, " · TA %d (≈%s m to site)", r.ta, Physics.fmt(r.ta * 78.12)));
            b.append(String.format(Locale.US, "\nFull-load S %s W/m² · E %s V/m · R %s",
                    Physics.fmt(r.dlSfull), Physics.fmt(Physics.eFromS(r.dlSfull)),
                    Physics.pct(r.dlSfull / Physics.refLevelS(r.dlMHz * 1e6, Model.SETTINGS.occupational))));
            if (r.registered && r.ulTxDbm != 0)
                b.append(String.format(Locale.US, "\nPath loss %.0f dB → phone TX ≈ %.1f dBm when sending", r.plDb, r.ulTxDbm));
            cellBox.addView(Ui.small(act, b.toString()));
            cellBox.addView(Ui.divider(act));
        }

        fill(wifiBox, s.comps, "Wi-Fi", "Downlink", der, "No Wi-Fi readings. Turn on Wi-Fi and location, then tap Scan Wi-Fi.");
        fill(btBox, s.comps, "Bluetooth", "Downlink", der, s.btOn ? "Scanning for Bluetooth LE devices…" : "Bluetooth is off.");
        magBox.removeAllViews();
        if (Double.isNaN(s.magStaticUt)) magBox.addView(Ui.small(act, "No magnetometer on this device."));
        else {
            Ui.kv(act, magBox, "Static |B|", Physics.fmt(s.magStaticUt) + " µT");
            Ui.kv(act, magBox, "Fluctuation (std-dev)", Physics.fmt(s.magAcUt) + " µT");
            Ui.kv(act, magBox, "Sensor rate", Physics.fmt(s.magRateHz) + " Hz");
            for (Model.Component c : s.comps) if (c.tech.equals("Static") || c.tech.equals("ELF")) comp(magBox, c, der);
        }
        drawHistory();
    }

    private void fill(LinearLayout box, List<Model.Component> cs, String tech, String dir, boolean der, String empty) {
        box.removeAllViews();
        int n = 0;
        for (Model.Component c : cs) if (c.tech.equals(tech) && c.direction.equals(dir)) { comp(box, c, der); n++; }
        if (n == 0) box.addView(Ui.small(act, empty));
    }

    private void comp(LinearLayout box, Model.Component c, boolean der) {
        LinearLayout row = Ui.hrow(act);
        TextView t = Ui.body(act, c.source);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(t);
        row.addView(Ui.pill(act, "R " + Physics.pct(c.ratio()), Ui.ratioColor(c.ratio())));
        box.addView(row);
        String f = c.fHz > 0 ? Physics.freq(c.fHz) + " · " : "";
        box.addView(Ui.small(act, String.format(Locale.US, "%s%s = %s %s (limit %s %s) · %s",
                f, shortQ(c.quantity), Physics.fmt(c.value), c.unit, Physics.fmt(c.limit), c.unit, c.provenance)));
        if (der && !c.detail.isEmpty()) box.addView(Ui.text(act, c.detail, 11.5f, 0xFF4C5966));
        box.addView(Ui.divider(act));
    }

    private static String shortQ(String q) {
        if (q.equals(Model.Q_S)) return "S";
        if (q.equals(Model.Q_SAR_HEAD)) return "SAR head 10 g";
        if (q.equals(Model.Q_SAR_BODY)) return "SAR 10 g";
        if (q.equals(Model.Q_SAB)) return "S_ab";
        if (q.equals(Model.Q_STATIC)) return "B static";
        if (q.equals(Model.Q_ELF_B)) return "B ELF";
        return q;
    }

    // ------------------------------------------------------------------ history and export
    private void record(RadioMonitor.Snapshot s) {
        double t = (s.time - t0) / 1000.0;
        double sumS = Model.sumRatio(s.comps, Model.Q_S);
        double sar = Model.sumRatio(s.comps, Model.Q_SAR_HEAD) + 0; // head
        double sarB = Model.sumRatio(s.comps, Model.Q_SAR_BODY);
        double sab = Model.sumRatio(s.comps, Model.Q_SAB);
        history.add(new double[]{t, sumS, Math.max(sar, sarB), sab});
        if (history.size() > MAX_HIST) history.remove(0);
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(s.time));
        for (Model.Component c : s.comps) {
            csv.add(String.format(Locale.US, "%s,\"%s\",%s,%s,%s,%.6g,\"%s\",%.6g,%s,%.6g,%.6g",
                    ts, c.source.replace("\"", "'"), c.tech, c.direction, c.provenance, c.fHz, c.quantity, c.value, c.unit, c.limit, c.ratio()));
        }
        while (csv.size() > 60000) csv.remove(0);
    }

    private void drawHistory() {
        int n = history.size();
        if (n < 2) return;
        double[] x = new double[n], a = new double[n], b = new double[n], c = new double[n];
        for (int i = 0; i < n; i++) {
            double[] r = history.get(i);
            x[i] = r[0]; a[i] = r[1] > 0 ? r[1] : Double.NaN; b[i] = r[2] > 0 ? r[2] : Double.NaN; c[i] = r[3] > 0 ? r[3] : Double.NaN;
        }
        hist.clear();
        hist.add(new ChartView.Series("ΣS/L whole body", x, a, Ui.C_RF));
        hist.add(new ChartView.Series("SAR / limit", x, b, Ui.C_SAR));
        hist.add(new ChartView.Series("S_ab / limit", x, c, Ui.C_MMW));
        hist.hline(1, "100 % of limit", Ui.C_BAD);
        hist.refresh();
    }

    private void export() {
        if (csv.isEmpty()) { android.widget.Toast.makeText(act, "Nothing recorded yet", android.widget.Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "emf_exposure_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".csv");
        act.startActivityForResult(i, REQ_EXPORT);
    }

    public void onExport(android.net.Uri uri) {
        try (OutputStream os = act.getContentResolver().openOutputStream(uri)) {
            StringBuilder b = new StringBuilder("time,source,technology,direction,provenance,frequency_hz,quantity,value,unit,limit,ratio_R\n");
            for (String line : csv) b.append(line).append('\n');
            os.write(b.toString().getBytes("UTF-8"));
            android.widget.Toast.makeText(act, "Saved " + csv.size() + " rows", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(act, "Export failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
