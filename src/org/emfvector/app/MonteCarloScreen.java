package org.emfvector.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import java.util.Arrays;
import java.util.Random;

/**
 * Monte Carlo uncertainty propagation (Sec. 19). Models have the form Y = K · Π xᵢ^aᵢ, so
 * ln Y is additive in independent terms and first-order Sobol indices (Eq. 17) are exact:
 * Sᵢ = Var(aᵢ ln xᵢ) / Var(ln Y). Local elasticities (Eq. 16) are the exponents aᵢ.
 */
public final class MonteCarloScreen {
    static final int UNIF = 0, DBM_NORMAL = 1;

    static final class Param {
        String name; int kind; double lo, hi, exp, clip, unitScale;
        Param(String n, int k, double lo, double hi, double exp) { name = n; kind = k; this.lo = lo; this.hi = hi; this.exp = exp; clip = Double.NaN; unitScale = 1; }
    }

    static final class Scenario {
        String name, output, unit; double k, limit; Param[] ps;
    }

    private final Activity a;
    private final ScrollView root;
    private LinearLayout paramBox, outBox;
    private EditText nField;
    private EditText[][] pf;
    private ChartView hist;
    private Scenario sc;
    private final Handler h = new Handler(Looper.getMainLooper());

    public MonteCarloScreen(Activity act) {
        a = act;
        LinearLayout[] hh = new LinearLayout[1];
        root = Ui.page(a, hh);
        LinearLayout l = hh[0];
        LinearLayout c = Ui.card(a, l, "Uncertainty: Monte Carlo (Sec. 19.3)");
        c.addView(Ui.small(a, "Draws N random input sets, evaluates the model for each and reports the distribution p(Y) instead of one number."));
        Spinner sp = Ui.spinner(a, c, "Scenario", new String[]{"3.5 GHz macro site: passer-by S (Case 1)",
                "Smartphone head SAR under power control (Case 3)", "28 GHz handset S_ab near body (Case 5)"}, 0);
        paramBox = new LinearLayout(a);
        paramBox.setOrientation(LinearLayout.VERTICAL);
        c.addView(paramBox);
        nField = Ui.num(a, c, "Samples N", 10000);
        Button run = Ui.button(a, "Run simulation", true);
        run.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { run(); } });
        c.addView(run);

        LinearLayout oc = Ui.card(a, l, "Distribution of R = Y / limit");
        hist = new ChartView(a, 220);
        hist.xLabel = "log₁₀ R";
        hist.yLabel = "share of samples (%)";
        oc.addView(hist);
        outBox = new LinearLayout(a);
        outBox.setOrientation(LinearLayout.VERTICAL);
        oc.addView(outBox);

        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { setScenario(pos); run(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    public View view() { return root; }

    private void setScenario(int i) {
        sc = new Scenario();
        switch (i) {
            case 0:
                sc.name = "macro"; sc.output = "S"; sc.unit = "W/m²"; sc.limit = 10;
                sc.k = 200 * Physics.dbToLin(25) / (4 * Math.PI);
                sc.ps = new Param[]{new Param("Distance d (m), uniform", UNIF, 20, 200, -2),
                        new Param("Reflection factor (power), uniform", UNIF, 1, 2.56, 1),
                        new Param("TDD downlink fraction, uniform", UNIF, 0.6, 0.85, 1),
                        new Param("Beam / traffic factor, uniform", UNIF, 0.1, 0.5, 1)};
                break;
            case 1:
                sc.name = "phone"; sc.output = "SAR"; sc.unit = "W/kg"; sc.limit = 2;
                sc.k = 0.85 / 200.0;
                Param p = new Param("Output power, normal in dBm (mean, SD)", DBM_NORMAL, 10, 8, 1);
                p.clip = 23; p.unitScale = 1; // mW
                sc.ps = new Param[]{p, new Param("Transmit activity over 6 min, uniform", UNIF, 0.1, 1, 1),
                        new Param("Position / posture factor, uniform", UNIF, 0.3, 1, 1)};
                break;
            default:
                sc.name = "mmw"; sc.output = "S_ab"; sc.unit = "W/m²"; sc.limit = 20;
                sc.k = 1 / (4 * Math.PI);
                Param q = new Param("EIRP, normal in dBm (mean, SD)", DBM_NORMAL, 20, 4, 1);
                q.clip = 30; q.unitScale = 1e-3; // W
                sc.ps = new Param[]{q, new Param("Distance to body (m), uniform", UNIF, 0.01, 0.05, -2),
                        new Param("Uplink duty, uniform", UNIF, 0.1, 0.5, 1)};
        }
        paramBox.removeAllViews();
        pf = new EditText[sc.ps.length][2];
        for (int j = 0; j < sc.ps.length; j++) {
            Param p = sc.ps[j];
            paramBox.addView(Ui.gap(a, 6));
            paramBox.addView(Ui.small(a, p.name + "   (exponent aᵢ = " + Physics.fmt(p.exp) + ")"));
            pf[j][0] = Ui.num(a, paramBox, p.kind == UNIF ? "  min" : "  mean (dBm)", p.lo);
            pf[j][1] = Ui.num(a, paramBox, p.kind == UNIF ? "  max" : "  SD (dB)", p.hi);
        }
    }

    private void run() {
        if (sc == null) return;
        for (int j = 0; j < sc.ps.length; j++) { sc.ps[j].lo = Ui.val(pf[j][0], sc.ps[j].lo); sc.ps[j].hi = Ui.val(pf[j][1], sc.ps[j].hi); }
        final int n = (int) Math.max(100, Math.min(200000, Ui.val(nField, 10000)));
        final Scenario s = sc;
        new Thread(new Runnable() { public void run() {
            final Result r = simulate(s, n, new Random(42));
            h.post(new Runnable() { public void run() { show(s, r); } });
        }}).start();
    }

    static final class Result { double[] y, sobol; int n; }

    static Result simulate(Scenario s, int n, Random rnd) {
        int m = s.ps.length;
        double[] y = new double[n];
        double[][] terms = new double[m][n];
        for (int i = 0; i < n; i++) {
            double ln = Math.log(s.k);
            for (int j = 0; j < m; j++) {
                Param p = s.ps[j];
                double x;
                if (p.kind == UNIF) x = p.lo + (p.hi - p.lo) * rnd.nextDouble();
                else {
                    double db = p.lo + p.hi * rnd.nextGaussian();
                    if (!Double.isNaN(p.clip)) db = Math.min(db, p.clip);
                    x = Math.pow(10, db / 10) * p.unitScale;
                }
                x = Math.max(x, 1e-12);
                terms[j][i] = p.exp * Math.log(x);
                ln += terms[j][i];
            }
            y[i] = Math.exp(ln);
        }
        double vt = 0, mt = 0;
        double[] lny = new double[n];
        for (int i = 0; i < n; i++) { lny[i] = Math.log(y[i]); mt += lny[i]; }
        mt /= n;
        for (int i = 0; i < n; i++) vt += (lny[i] - mt) * (lny[i] - mt);
        Result r = new Result();
        r.sobol = new double[m];
        for (int j = 0; j < m; j++) {
            double mm = 0, v = 0;
            for (int i = 0; i < n; i++) mm += terms[j][i];
            mm /= n;
            for (int i = 0; i < n; i++) v += (terms[j][i] - mm) * (terms[j][i] - mm);
            r.sobol[j] = vt > 0 ? v / vt : 0;
        }
        r.y = y; r.n = n;
        return r;
    }

    private void show(Scenario s, Result r) {
        double[] y = r.y.clone();
        Arrays.sort(y);
        int n = r.n;
        double mean = 0, sd = 0, exceed = 0;
        for (double v : y) { mean += v; if (v > s.limit) exceed++; }
        mean /= n;
        for (double v : y) sd += (v - mean) * (v - mean);
        sd = Math.sqrt(sd / (n - 1));
        outBox.removeAllViews();
        String[][] t = {{"Statistic", s.output + " (" + s.unit + ")", "R"},
                st("Mean", mean, s), st("Median", q(y, 0.5), s), st("SD", sd, s), st("P5", q(y, 0.05), s), st("P25", q(y, 0.25), s),
                st("P50", q(y, 0.5), s), st("P75", q(y, 0.75), s), st("P95", q(y, 0.95), s), st("Max", y[n - 1], s)};
        outBox.addView(Ui.table(a, t, null));
        outBox.addView(Ui.gap(a, 6));
        Ui.kv(a, outBox, "95 % interval of R", Physics.pct(q(y, 0.025) / s.limit) + " – " + Physics.pct(q(y, 0.975) / s.limit));
        Ui.kv(a, outBox, "Samples above the limit", Physics.pct(exceed / n)).setTextColor(exceed > 0 ? Ui.C_BAD : Ui.C_OK);
        outBox.addView(Ui.divider(a));
        outBox.addView(Ui.h2(a, "Sensitivity"));
        String[][] t2 = new String[s.ps.length + 1][3];
        t2[0] = new String[]{"Input", "Elasticity Sₓ (Eq. 16)", "Sobol Sᵢ (Eq. 17)"};
        for (int j = 0; j < s.ps.length; j++)
            t2[j + 1] = new String[]{s.ps[j].name.split(",")[0], Physics.fmt(s.ps[j].exp), Physics.pct(r.sobol[j])};
        outBox.addView(Ui.table(a, t2, new float[]{1.6f, 1, 1}));
        outBox.addView(Ui.note(a, "Sobol indices are for ln Y, where the model is additive, so they are exact and sum to 100 %. The input with the largest " +
                "index is where better measurement data would shrink the uncertainty most. Input ranges are illustrative and must be justified " +
                "from measurements or literature before use in a study (Sec. 19.3)."));

        int bins = 32;
        double lo = Math.log10(y[0] / s.limit), hi = Math.log10(y[n - 1] / s.limit);
        if (hi - lo < 1e-6) hi = lo + 1;
        double[] cx = new double[bins], cy = new double[bins];
        for (int b = 0; b < bins; b++) cx[b] = lo + (b + 0.5) * (hi - lo) / bins;
        for (double v : y) {
            int b = (int) ((Math.log10(v / s.limit) - lo) / (hi - lo) * bins);
            cy[Math.max(0, Math.min(bins - 1, b))] += 100.0 / n;
        }
        hist.clear();
        hist.xMin = lo; hist.xMax = hi; hist.yMin = 0;
        hist.add(new ChartView.Series(null, cx, cy, Ui.C_RF).bars());
        if (0 >= lo && 0 <= hi) hist.vmark(0, Ui.C_BAD);
        hist.refresh();
    }

    private static double q(double[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int i = (int) Math.floor(idx);
        int j = Math.min(sorted.length - 1, i + 1);
        return sorted[i] + (sorted[j] - sorted[i]) * (idx - i);
    }

    private static String[] st(String n, double v, Scenario s) { return new String[]{n, Physics.fmt(v), Physics.pct(v / s.limit)}; }
}
