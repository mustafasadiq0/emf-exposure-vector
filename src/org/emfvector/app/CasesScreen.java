package org.emfvector.app;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import java.util.Locale;

/** Interactive versions of the Appendix D worked case studies. */
public final class CasesScreen {
    private final Activity a;
    private final ScrollView root;
    private LinearLayout inBox, outBox;
    private ChartView chart;
    private Runnable compute;
    private final TextWatcher tw = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
        public void onTextChanged(CharSequence s, int st, int b, int c) {}
        public void afterTextChanged(Editable e) { if (compute != null) compute.run(); }
    };

    public CasesScreen(Activity act) {
        a = act;
        LinearLayout[] h = new LinearLayout[1];
        root = Ui.page(a, h);
        LinearLayout l = h[0];
        LinearLayout c = Ui.card(a, l, "Worked case studies (Appendix D)");
        Spinner sp = Ui.spinner(a, c, null, new String[]{
                "1 · 3.5 GHz 5G massive-MIMO macro site", "2 · 900 MHz mast, occupational near field",
                "3 · Smartphone at the head, 1.8 GHz (SAR, ΔT)", "5 · mmWave: 28 GHz handset and 77 GHz radar",
                "6 · 400 kV, 50 Hz overhead line (E and B)"}, 0);
        c.addView(Ui.small(a, "Case 4 (home Wi-Fi, Bluetooth and base station) is the first preset on the Vector tab."));
        LinearLayout ic = Ui.card(a, l, "Inputs");
        inBox = new LinearLayout(a);
        inBox.setOrientation(LinearLayout.VERTICAL);
        ic.addView(inBox);
        LinearLayout oc = Ui.card(a, l, "Calculation");
        outBox = new LinearLayout(a);
        outBox.setOrientation(LinearLayout.VERTICAL);
        oc.addView(outBox);
        LinearLayout cc = Ui.card(a, l, null);
        chart = new ChartView(a, 240);
        cc.addView(chart);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { show(pos); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        show(0);
    }

    public View view() { return root; }

    private EditText in(String label, double v) {
        EditText e = Ui.num(a, inBox, label, v);
        e.addTextChangedListener(tw);
        return e;
    }

    private void show(int i) {
        compute = null;
        inBox.removeAllViews();
        chart.clear();
        chart.logX = chart.logY = false;
        switch (i) {
            case 0: macro(); break;
            case 1: mast(); break;
            case 2: phone(); break;
            case 3: mmwave(); break;
            default: line();
        }
        compute.run();
    }

    // ---------------------------------------------------------------- Case 1
    private void macro() {
        final EditText p = in("Transmit power P (W)", 200), g = in("Gain G (dBi)", 25), tdd = in("TDD downlink fraction", 0.75),
                beam = in("Beam / traffic factor", 0.25), refl = in("Reflection factor (power)", 2.56), r = in("Distance of interest (m)", 50);
        chart.logX = true; chart.logY = true;
        chart.xLabel = "distance (m)"; chart.yLabel = "S (W/m²)";
        compute = new Runnable() { public void run() {
            double eirp = Ui.val(p, 200) * Physics.dbToLin(Ui.val(g, 25));
            double fb = Ui.val(refl, 2.56), fc = fb * Ui.val(tdd, 0.75), fd = fc * Ui.val(beam, 0.25);
            double[] F = {1, fb, fc, fd};
            String[] nm = {"A peak, free space", "B + reflection", "C + TDD duty", "D + beam/traffic"};
            outBox.removeAllViews();
            Ui.kv(a, outBox, "EIRP = P·G", Physics.fmt(eirp) + " W (" + Physics.fmt(Physics.wToDbm(eirp)) + " dBm)");
            String[][] t = new String[5][4];
            t[0] = new String[]{"Scenario", "F", "Public 10 W/m²", "Occ. 50 W/m²"};
            for (int k = 0; k < 4; k++)
                t[k + 1] = new String[]{nm[k], Physics.fmt(F[k]), Physics.dist(Physics.complianceDistance(eirp, F[k], 10)),
                        Physics.dist(Physics.complianceDistance(eirp, F[k], 50))};
            outBox.addView(Ui.table(a, t, new float[]{1.6f, 0.7f, 1, 1}));
            double d = Ui.val(r, 50), s = Physics.sFar(fd * eirp, d);
            outBox.addView(Ui.gap(a, 8));
            Ui.kv(a, outBox, "Scenario D at " + Physics.dist(d) + ": S", Physics.fmt(s) + " W/m²");
            Ui.kv(a, outBox, "E", Physics.fmt(Physics.eFromS(s)) + " V/m");
            Ui.kv(a, outBox, "R (public)", Physics.pct(s / 10)).setTextColor(Ui.ratioColor(s / 10));
            outBox.addView(Ui.note(a, "Same equipment, public distance from 15.5 to 35.9 m depending only on the assessment method: EIRP alone does not determine exposure (Sec. 13)."));
            int n = 50;
            double[] x = new double[n];
            double[][] y = new double[4][n];
            for (int k = 0; k < n; k++) {
                x[k] = Math.pow(10, 0.7 + 1.7 * k / (n - 1));
                for (int j = 0; j < 4; j++) y[j][k] = Physics.sFar(F[j] * eirp, x[k]);
            }
            chart.clear();
            int[] col = {Ui.C_RF, Ui.C_BAD, Ui.C_WARN, Ui.C_OK};
            for (int j = 0; j < 4; j++) chart.add(new ChartView.Series(nm[j], x, y[j], col[j]));
            chart.hline(10, "public 10", Ui.C_BAD);
            chart.hline(50, "occupational 50", Ui.C_WARN);
            chart.refresh();
        }};
    }

    // ---------------------------------------------------------------- Case 2
    private void mast() {
        final EditText f = in("Frequency (MHz)", 900), p = in("Power P (W)", 40), g = in("Gain (dBi)", 17), h = in("Panel height h (m)", 2.5),
                bw = in("Horizontal beamwidth (°)", 65), r = in("Worker distance (m)", 3);
        chart.logX = true; chart.logY = true;
        chart.xLabel = "distance in front of panel (m)"; chart.yLabel = "S (W/m²)";
        compute = new Runnable() { public void run() {
            double fh = Ui.val(f, 900) * 1e6, P = Ui.val(p, 40), eirp = P * Physics.dbToLin(Ui.val(g, 17)), H = Ui.val(h, 2.5), B = Ui.val(bw, 65), R = Ui.val(r, 3);
            double lp = Physics.refLevelS(fh, false), lo = Physics.refLevelS(fh, true);
            double sff = Physics.sFar(eirp, R), scy = Physics.sCylindrical(P, B, R, H);
            outBox.removeAllViews();
            Ui.kv(a, outBox, "Limits public / occupational", Physics.fmt(lp) + " / " + Physics.fmt(lo) + " W/m²");
            Ui.kv(a, outBox, "EIRP", Physics.fmt(eirp) + " W");
            Ui.kv(a, outBox, "Far-field boundary 2D²/λ", Physics.dist(Physics.farFieldBoundary(H, fh)));
            String[][] t = {{"Model", "S at " + Physics.dist(R), "R occ.", "R public", "Public dist."},
                    {"Far field (Eq. 5)", Physics.fmt(sff), Physics.pct(sff / lo), Physics.pct(sff / lp), Physics.dist(Physics.complianceDistance(eirp, 1, lp))},
                    {"Cylindrical (D3)", Physics.fmt(scy), Physics.pct(scy / lo), Physics.pct(scy / lp), Physics.dist(180 * P / (B * Math.PI * H) / lp)}};
            outBox.addView(Ui.table(a, t, new float[]{1.4f, 0.9f, 0.8f, 0.8f, 0.9f}));
            Ui.kv(a, outBox, "Occupational distance (near-field model)", Physics.dist(180 * P / (B * Math.PI * H) / lo));
            outBox.addView(Ui.note(a, "The far-field formula overestimates S inside the near field of a large aperture: safe for exclusion zones, " +
                    "but not evidence of an exceedance. Behind and beside the panel the field is lower again."));
            int n = 50;
            double[] x = new double[n], y1 = new double[n], y2 = new double[n];
            for (int k = 0; k < n; k++) { x[k] = Math.pow(10, -0.7 + 2.2 * k / (n - 1)); y1[k] = Physics.sFar(eirp, x[k]); y2[k] = Physics.sCylindrical(P, B, x[k], H); }
            chart.clear();
            chart.add(new ChartView.Series("far field 1/R²", x, y1, Ui.C_RF));
            chart.add(new ChartView.Series("cylindrical 1/R", x, y2, Ui.C_MMW));
            chart.hline(lp, "public", Ui.C_BAD);
            chart.hline(lo, "occupational", Ui.C_WARN);
            chart.vmark(Physics.farFieldBoundary(H, fh), Ui.MUTED);
            chart.refresh();
        }};
    }

    // ---------------------------------------------------------------- Case 3
    private void phone() {
        final EditText sar = in("Declared 10 g head SAR (W/kg)", 0.85), pref = in("…at output power (mW)", 200),
                pnow = in("Actual output power (mW)", 200), sig = in("Brain σ (S/m)", 1.23), rho = in("Density ρ (kg/m³)", 1045),
                cc = in("Specific heat c (J/kg·K)", 3696), t = in("Exposure time (s)", 360), wb = in("Perfusion ω_b (1/s)", 0.0105);
        chart.logX = true; chart.logY = true;
        chart.xLabel = "handset output power (mW)"; chart.yLabel = "SAR (W/kg)";
        compute = new Runnable() { public void run() {
            double sd = Ui.val(sar, 0.85), pr = Ui.val(pref, 200), pn = Ui.val(pnow, 200);
            double s = sd * pn / pr, sg = Ui.val(sig, 1.23), rh = Ui.val(rho, 1045);
            outBox.removeAllViews();
            Ui.kv(a, outBox, "SAR = declared × P/P_ref", Physics.fmt(s) + " W/kg");
            Ui.kv(a, outBox, "R = SAR / 2 W/kg", Physics.pct(s / 2)).setTextColor(Ui.ratioColor(s / 2));
            Ui.kv(a, outBox, "Tissue E_rms (Eq. D4)", Physics.fmt(Physics.eRmsFromSar(s, rh, sg)) + " V/m");
            Ui.kv(a, outBox, "E_rms at the 2 W/kg limit", Physics.fmt(Physics.eRmsFromSar(2, rh, sg)) + " V/m");
            double dta = Physics.dTAdiabatic(s, Ui.val(t, 360), Ui.val(cc, 3696));
            double dts = Physics.dTSteady(rh, s, 1050, 3617, Ui.val(wb, 0.0105));
            Ui.kv(a, outBox, "ΔT adiabatic upper bound (Eq. D5)", Physics.fmt(dta) + " K");
            Ui.kv(a, outBox, "ΔT perfusion steady state (Eq. D6)", Physics.fmt(dts) + " K");
            String[][] tab = {{"Output", "SAR", "R", "ΔT 6 min"},
                    row(sd, pr, 200, Ui.val(cc, 3696)), row(sd, pr, 20, Ui.val(cc, 3696)), row(sd, pr, 2, Ui.val(cc, 3696))};
            outBox.addView(Ui.table(a, tab, null));
            outBox.addView(Ui.note(a, "Both temperature estimates are far below the ≈1 K rise associated with established thermal effects. " +
                    "Power control usually keeps a phone 10–100× below its maximum; the Live tab estimates your phone's actual power."));
            double[] x = {1, 2, 5, 10, 20, 50, 100, 200}, y = new double[x.length];
            for (int k = 0; k < x.length; k++) y[k] = sd * x[k] / pr;
            chart.clear();
            chart.add(new ChartView.Series("SAR ∝ P", x, y, Ui.C_SAR));
            chart.add(new ChartView.Series(null, new double[]{pn}, new double[]{s}, Ui.INK).points());
            chart.hline(2, "2 W/kg limit", Ui.C_BAD);
            chart.refresh();
        }};
    }

    private static String[] row(double sd, double pr, double p, double c) {
        double s = sd * p / pr;
        return new String[]{Physics.fmt(p) + " mW (" + Physics.fmt(Physics.wToDbm(p / 1000)) + " dBm)", Physics.fmt(s), Physics.pct(s / 2),
                Physics.fmt(Physics.dTAdiabatic(s, 360, c)) + " K"};
    }

    // ---------------------------------------------------------------- Case 5
    private void mmwave() {
        final EditText he = in("Handset EIRP (dBm)", 30), hd = in("Handset distance to body (cm)", 2), hduty = in("Uplink duty", 0.25),
                re = in("Radar EIRP (dBm)", 35), rd = in("Pedestrian distance to radar (m)", 0.5),
                e1 = in("Skin ε′ at 28 GHz", 17), e2 = in("Skin ε″ at 28 GHz", 16), k = in("Skin thermal conductivity k", 0.37);
        chart.xLabel = "skin perfusion ω_b (10⁻³ /s)"; chart.yLabel = "surface ΔT (K)";
        compute = new Runnable() { public void run() {
            double eirp = Physics.dbmToW(Ui.val(he, 30)), d = Ui.val(hd, 2) / 100, du = Ui.val(hduty, 0.25);
            double s = Physics.sFar(eirp, d);
            double emax = 20 * 4 * Math.PI * d * d;
            outBox.removeAllViews();
            outBox.addView(Ui.h2(a, "28 GHz handset"));
            Ui.kv(a, outBox, "Far-field boundary (15 mm array)", Physics.dist(Physics.farFieldBoundary(0.015, 28e9)));
            Ui.kv(a, outBox, "S at body (screening)", Physics.fmt(s) + " W/m²");
            Ui.kv(a, outBox, "R vs 20 W/m², no back-off", Physics.pct(s / 20)).setTextColor(Ui.ratioColor(s / 20));
            Ui.kv(a, outBox, "Max compliant EIRP (continuous)", Physics.fmt(Physics.wToDbm(emax)) + " dBm");
            Ui.kv(a, outBox, "Max EIRP at duty " + Physics.fmt(du), Physics.fmt(Physics.wToDbm(emax / du)) + " dBm");
            Ui.kv(a, outBox, "Back-off required", Physics.fmt(Math.max(0, Ui.val(he, 30) - Physics.wToDbm(emax / du))) + " dB");
            outBox.addView(Ui.h2(a, "77 GHz radar"));
            double reW = Physics.dbmToW(Ui.val(re, 35));
            double[] ds = {0.3, Ui.val(rd, 0.5), 1, 2};
            String[][] t = new String[ds.length + 1][3];
            t[0] = new String[]{"Distance", "S (W/m²)", "R vs 40 W/m²"};
            for (int j = 0; j < ds.length; j++) { double v = Physics.sFar(reW, ds[j]); t[j + 1] = new String[]{Physics.dist(ds[j]), Physics.fmt(v), Physics.pct(v / 40)}; }
            outBox.addView(Ui.table(a, t, null));
            outBox.addView(Ui.h2(a, "Penetration and heating"));
            double dp = Physics.penetrationDepth(28e9, Ui.val(e1, 17), Ui.val(e2, 16));
            Ui.kv(a, outBox, "Penetration depth δp at 28 GHz (Eq. D8)", Physics.fmt(dp * 1000) + " mm");
            Ui.kv(a, outBox, "Penetration depth at 77 GHz (ε 6.5 − j8.5)", Physics.fmt(Physics.penetrationDepth(77e9, 6.5, 8.5) * 1000) + " mm");
            double sars = 20 / (dp * 1109);
            Ui.kv(a, outBox, "Surface SAR at 20 W/m²", Physics.fmt(sars) + " W/kg");
            Ui.kv(a, outBox, "Initial heating rate", Physics.fmt(sars / 3391) + " K/s");
            double kk = Ui.val(k, 0.37);
            Ui.kv(a, outBox, "Steady ΔT at 20 / 40 W/m² (ω 1.5e-3)", Physics.fmt(Physics.dTSurface(20, kk, 1050, 3617, 1.5e-3)) + " / "
                    + Physics.fmt(Physics.dTSurface(40, kk, 1050, 3617, 1.5e-3)) + " K");
            outBox.addView(Ui.note(a, "In the mmWave regime proximity, not nominal power, is the dominant variable. Energy is deposited within a fraction of a millimetre, which is why the limit changes from SAR to S_ab above 6 GHz."));
            int n = 30;
            double[] x = new double[n], y1 = new double[n], y2 = new double[n];
            for (int j = 0; j < n; j++) {
                x[j] = 0.4 + 2.8 * j / (n - 1);
                y1[j] = Physics.dTSurface(20, kk, 1050, 3617, x[j] * 1e-3);
                y2[j] = Physics.dTSurface(40, kk, 1050, 3617, x[j] * 1e-3);
            }
            chart.clear();
            chart.add(new ChartView.Series("at 20 W/m² (>6 GHz)", x, y1, Ui.C_MMW));
            chart.add(new ChartView.Series("at 40 W/m² (>30 GHz)", x, y2, Ui.C_SAR));
            chart.hline(1, "≈1 K thermal-effect onset", Ui.C_BAD);
            chart.refresh();
        }};
    }

    // ---------------------------------------------------------------- Case 6
    private void line() {
        final EditText v = in("Line voltage (kV, line-to-line)", 400), i = in("Phase current (A)", 1000), sp = in("Phase spacing (m)", 11),
                ht = in("Conductor height at mid-span (m)", 12), y = in("Evaluation height (m)", 1), nb = in("Sub-conductors per bundle", 4);
        chart.xLabel = "lateral distance from centre phase (m)"; chart.yLabel = "% of ICNIRP 2010 reference level";
        compute = new Runnable() { public void run() {
            PowerLine pl = new PowerLine();
            pl.voltageKv = Ui.val(v, 400); pl.currentA = Ui.val(i, 1000); pl.spacing = Math.max(0.5, Ui.val(sp, 11));
            pl.height = Math.max(1, Ui.val(ht, 12)); pl.evalHeight = Ui.val(y, 1); pl.subConductors = (int) Math.max(1, Ui.val(nb, 4));
            int n = 121;
            double[] x = new double[n], e = new double[n], b = new double[n];
            double emax = 0, xmax = 0, zoneLo = Double.NaN, zoneHi = Double.NaN;
            for (int k = 0; k < n; k++) {
                x[k] = 60.0 * k / (n - 1);
                double ev = pl.eField_kVm(x[k]), bv = pl.bField_uT(x[k]);
                e[k] = ev / 5 * 100; b[k] = bv / 200 * 100;
                if (ev > emax) { emax = ev; xmax = x[k]; }
                if (ev > 5) { if (Double.isNaN(zoneLo)) zoneLo = x[k]; zoneHi = x[k]; }
            }
            outBox.removeAllViews();
            Ui.kv(a, outBox, "Phase-to-earth voltage", Physics.fmt(pl.voltageKv / Math.sqrt(3)) + " kV");
            Ui.kv(a, outBox, "Bundle equivalent radius", Physics.fmt(pl.bundleRadius()) + " m");
            Ui.kv(a, outBox, "Line charges |Q|", String.format(Locale.US, "%s, %s, %s µC/m", Physics.fmt(pl.chargeMagnitude_uC(0)),
                    Physics.fmt(pl.chargeMagnitude_uC(1)), Physics.fmt(pl.chargeMagnitude_uC(2))));
            Ui.kv(a, outBox, "Max E", Physics.fmt(emax) + " kV/m at " + Physics.dist(xmax));
            Ui.kv(a, outBox, "Max B (under centre)", Physics.fmt(pl.bField_uT(0)) + " µT");
            if (!Double.isNaN(zoneLo)) Ui.kv(a, outBox, "E above 5 kV/m between", Physics.dist(zoneLo) + " and " + Physics.dist(zoneHi) + " each side");
            double[] pts = {0, 10, 20, 30, 40, 50};
            String[][] t = new String[pts.length + 1][5];
            t[0] = new String[]{"x", "E kV/m", "E/5 kV/m", "B µT", "B/200 µT"};
            for (int k = 0; k < pts.length; k++) {
                double ev = pl.eField_kVm(pts[k]), bv = pl.bField_uT(pts[k]);
                t[k + 1] = new String[]{Physics.dist(pts[k]), Physics.fmt(ev), Physics.pct(ev / 5), Physics.fmt(bv), Physics.pct(bv / 200)};
            }
            outBox.addView(Ui.table(a, t, null));
            outBox.addView(Ui.note(a, "At 50 Hz the mechanism is induced current, not heating, so E and B are kept as separate components and never converted to SAR. " +
                    "Exceeding a reference level does not by itself mean the basic restriction is exceeded. Buildings, trees and fences strongly reduce E but not B."));
            chart.clear();
            chart.add(new ChartView.Series("E / 5 kV/m", x, e, Ui.C_ELF));
            chart.add(new ChartView.Series("B / 200 µT", x, b, Ui.C_RF));
            chart.hline(100, "100 %", Ui.C_BAD);
            chart.refresh();
        }};
    }
}
