package org.emfvector.app;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

import java.util.Locale;

/** Single-source far-field screening calculator: Eqs. (1), (5)-(9), (15), (D2), (D3), decision tree of Fig. 6. */
public final class CalcScreen {
    private final Activity a;
    private final ScrollView root;
    private EditText fMHz, pW, gDbi, dist, duty, apert, height, bw, wall;
    private Switch occ, refl;
    private LinearLayout out, regimeBox;
    private ChartView chartS, chartR;

    public CalcScreen(Activity act) {
        a = act;
        LinearLayout[] h = new LinearLayout[1];
        root = Ui.page(a, h);
        build(h[0]);
        calc();
    }

    public View view() { return root; }

    private void build(LinearLayout l) {
        LinearLayout in = Ui.card(a, l, "Source parameters  𝒮ᵢ = {P, G, f, d, D}");
        fMHz = Ui.num(a, in, "Frequency f (MHz)", 3500);
        pW = Ui.num(a, in, "Transmit power Pₜ (W, average or peak)", 200);
        gDbi = Ui.num(a, in, "Antenna gain G (dBi)", 25);
        dist = Ui.num(a, in, "Distance r (m)", 50);
        duty = Ui.num(a, in, "Duty cycle / time factor D (0–1)", 1);
        wall = Ui.num(a, in, "Building / wall loss (dB)", 0);
        refl = Ui.toggle(a, in, "Ground reflection factor 2.56 (field ×1.6)", false);
        occ = Ui.toggle(a, in, "Occupational limits", false);
        in.addView(Ui.small(a, "Optional antenna geometry for near-field checks"));
        apert = Ui.num(a, in, "Largest antenna dimension D (m), 0 = unknown", 0.6);
        height = Ui.num(a, in, "Panel height h for cylindrical model (m), 0 = off", 0);
        bw = Ui.num(a, in, "Horizontal beamwidth (°)", 65);
        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) { calc(); }
        };
        for (EditText e : new EditText[]{fMHz, pW, gDbi, dist, duty, apert, height, bw, wall}) e.addTextChangedListener(tw);
        CompoundButton.OnCheckedChangeListener cl = new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { calc(); }
        };
        occ.setOnCheckedChangeListener(cl);
        refl.setOnCheckedChangeListener(cl);

        LinearLayout rc = Ui.card(a, l, "Dosimetric decision (Fig. 6)");
        regimeBox = new LinearLayout(a);
        regimeBox.setOrientation(LinearLayout.VERTICAL);
        rc.addView(regimeBox);

        LinearLayout oc = Ui.card(a, l, "Results");
        out = new LinearLayout(a);
        out.setOrientation(LinearLayout.VERTICAL);
        oc.addView(out);

        LinearLayout c1 = Ui.card(a, l, "Power density vs distance (Fig. 1)");
        chartS = new ChartView(a, 230);
        chartS.logX = true; chartS.logY = true;
        chartS.xLabel = "distance r (m)"; chartS.yLabel = "S (W/m²)";
        c1.addView(chartS);
        c1.addView(Ui.small(a, "Doubling the distance lowers S by 6.02 dB (Eq. 8). The far-field curve is drawn dashed inside the near field."));

        LinearLayout c2 = Ui.card(a, l, "EIRP sensitivity at this distance (Fig. 4–5)");
        chartR = new ChartView(a, 210);
        chartR.logX = true; chartR.logY = true;
        chartR.xLabel = "EIRP (W)"; chartR.yLabel = "R = S / L";
        c2.addView(chartR);
    }

    private void calc() {
        if (out == null) return;
        boolean o = occ.isChecked();
        double f = Ui.val(fMHz, 3500) * 1e6;
        double p = Ui.val(pW, 1), g = Ui.val(gDbi, 0), r = Math.max(1e-4, Ui.val(dist, 1));
        double dc = Math.max(0, Math.min(1, Ui.val(duty, 1)));
        double wl = Physics.dbToLin(-Ui.val(wall, 0));
        double F = (refl.isChecked() ? 2.56 : 1) * dc * wl;
        double eirp = p * Physics.dbToLin(g);
        double s = Physics.sFar(F * eirp, r);
        double L = Physics.refLevelS(f, o);
        double lam = Physics.lambda(f);
        Physics.Regime reg = Physics.regime(f);

        regimeBox.removeAllViews();
        Ui.kv(a, regimeBox, "Regime", reg.label);
        Ui.kv(a, regimeBox, "Governing quantity", reg.quantity);
        Ui.kv(a, regimeBox, "Mechanism", reg.mechanism);
        double ev = Physics.photonEnergyEv(f);
        Ui.kv(a, regimeBox, "Photon energy E = hf", Physics.fmt(ev) + " eV");
        regimeBox.addView(Ui.small(a, String.format(Locale.US, "That is %s times below the ≈10 eV needed to ionize molecules: RF heating ≠ ionizing radiation.",
                Physics.fmt(Physics.IONIZATION_EV / ev))));
        if (reg == Physics.Regime.ELF || reg == Physics.Regime.STATIC || reg == Physics.Regime.IF)
            regimeBox.addView(Ui.note(a, "Below 100 kHz the far-field power-density model does not apply. Use measured E and B against the " +
                    "ICNIRP 2010 reference levels (Cases tab › 400 kV line)."));

        out.removeAllViews();
        Ui.kv(a, out, "Wavelength λ", Physics.dist(lam));
        Ui.kv(a, out, "EIRP = Pₜ·G", Physics.fmt(eirp) + " W (" + Physics.fmt(Physics.wToDbm(eirp)) + " dBm)");
        Ui.kv(a, out, "Combined factor F", Physics.fmt(F));
        Ui.kv(a, out, "Incident S (Eq. 5)", Physics.fmt(s) + " W/m²");
        Ui.kv(a, out, "E = √(377·S) (Eq. 7)", Physics.fmt(Physics.eFromS(s)) + " V/m");
        Ui.kv(a, out, "H = √(S/377)", Physics.fmt(Physics.hFromS(s)) + " A/m");
        Ui.kv(a, out, "Reference level L", Double.isNaN(L) ? "n/a (outside 30 MHz–300 GHz)" : Physics.fmt(L) + " W/m²");
        double R = s / L;
        android.widget.TextView rv = Ui.kv(a, out, "R = S / L (Eq. 9)", Physics.pct(R));
        rv.setTextColor(Ui.ratioColor(R));
        double loc = Physics.localAbsorbedLimit(f, o);
        if (!Double.isNaN(loc)) {
            android.widget.TextView lv = Ui.kv(a, out, "Local S_ab vs " + Physics.fmt(loc) + " W/m² (" + Physics.fmt(Physics.localAveragingAreaCm2(f)) + " cm²)",
                    Physics.pct(s / loc));
            lv.setTextColor(Ui.ratioColor(s / loc));
            out.addView(Ui.small(a, "Above 6 GHz the basic restriction is absorbed power density; this assumes all incident power is absorbed (conservative)."));
        }
        out.addView(Ui.divider(a));
        double rp = Physics.complianceDistance(eirp, F, Physics.refLevelS(f, false));
        double ro = Physics.complianceDistance(eirp, F, Physics.refLevelS(f, true));
        Ui.kv(a, out, "Public compliance distance (Eq. D2)", Physics.dist(rp));
        Ui.kv(a, out, "Occupational compliance distance", Physics.dist(ro));
        if (dc < 1) Ui.kv(a, out, "Average power P·D (Eq. 15)", Physics.fmt(p * dc) + " W");

        // near field
        double D = Ui.val(apert, 0);
        double rff = D > 0 ? Physics.farFieldBoundary(D, f) : Double.NaN;
        double rre = Physics.reactiveBoundary(f);
        out.addView(Ui.divider(a));
        Ui.kv(a, out, "Reactive near field < λ/2π", Physics.dist(rre));
        if (D > 0) Ui.kv(a, out, "Far field beyond 2D²/λ", Physics.dist(rff));
        if (r < rre) out.addView(Ui.note(a, "⚠ Inside the reactive near field: E ≠ √(377·S). Only full-wave simulation or measurement is valid here (Sec. 20.1)."));
        else if (D > 0 && r < rff) out.addView(Ui.note(a, "⚠ Inside the radiating near field of this aperture. The far-field formula overestimates S here, " +
                "so it is conservative for exclusion zones but cannot prove an exceedance (Case Study 2)."));
        double hh = Ui.val(height, 0);
        if (hh > 0) {
            double sc = Physics.sCylindrical(p * dc * wl, Ui.val(bw, 65), r, hh);
            Ui.kv(a, out, "Cylindrical near-field S (Eq. D3)", Physics.fmt(sc) + " W/m² · R " + Physics.pct(sc / L));
        }

        // charts
        int n = 60;
        double[] xs = new double[n], ys = new double[n], yn = new double[n];
        double lo = Math.log10(Math.max(1e-3, r / 100)), hi = Math.log10(r * 30);
        for (int i = 0; i < n; i++) {
            xs[i] = Math.pow(10, lo + (hi - lo) * i / (n - 1));
            double v = Physics.sFar(F * eirp, xs[i]);
            boolean near = xs[i] < rre || (D > 0 && xs[i] < rff);
            ys[i] = near ? Double.NaN : v;
            yn[i] = near ? v : Double.NaN;
        }
        chartS.clear();
        chartS.add(new ChartView.Series("far field", xs, ys, Ui.C_RF));
        chartS.add(new ChartView.Series("near field (indicative)", xs, yn, Ui.C_RF).dashed());
        chartS.add(new ChartView.Series(null, new double[]{r}, new double[]{s}, Ui.INK).points());
        if (!Double.isNaN(L)) {
            chartS.hline(Physics.refLevelS(f, false), "public " + Physics.fmt(Physics.refLevelS(f, false)), Ui.C_BAD);
            chartS.hline(Physics.refLevelS(f, true), "occupational " + Physics.fmt(Physics.refLevelS(f, true)), Ui.C_WARN);
        }
        chartS.refresh();

        double[] ex = {1, 10, 100, 1e3, 1e4, 1e5};
        double[] rpub = new double[ex.length], rocc = new double[ex.length];
        for (int i = 0; i < ex.length; i++) {
            rpub[i] = Physics.sFar(F * ex[i], r) / Physics.refLevelS(f, false);
            rocc[i] = Physics.sFar(F * ex[i], r) / Physics.refLevelS(f, true);
        }
        chartR.clear();
        chartR.add(new ChartView.Series("vs public", ex, rpub, Ui.C_RF));
        chartR.add(new ChartView.Series("vs occupational", ex, rocc, Ui.C_WARN));
        chartR.add(new ChartView.Series(null, new double[]{eirp}, new double[]{s / Physics.refLevelS(f, o) / Math.max(1e-30, F) * F}, Ui.INK).points());
        chartR.hline(1, "100 %", Ui.C_BAD);
        chartR.refresh();
    }
}
