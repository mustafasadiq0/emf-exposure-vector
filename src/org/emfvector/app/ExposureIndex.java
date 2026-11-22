package org.emfvector.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Headline "total exposure index": the single number a user can read at a glance.
 *
 * Definition (compliance index, consistent with Sec. 14.2 and Eq. D7):
 *   I = max over physical quantities q of  < sum_i R_i,q >_T(q)
 * where ratios are only ever summed within one quantity, each quantity is time-averaged over its
 * ICNIRP averaging window T(q) (6 min for local SAR and S_ab, 30 min for whole-body incident S),
 * and the max — not a sum — combines different quantities. I < 100 % means every limit is met.
 *
 * Uncertainty: each component carries a log-normal standard uncertainty (dB). Within a quantity the
 * absolute uncertainties are combined in quadrature (independent sources); the result gives a 95 %
 * range and the probability that the true value is below the limit.
 */
public final class ExposureIndex {
    private static final double LN10_10 = Math.log(10) / 10;

    private static final class Sample {
        long t;
        final Map<String, double[]> q = new LinkedHashMap<>(); // quantity -> {sumR, absSigma(ln)}
    }

    public static final class QuantityResult {
        public String quantity;
        public double meanR, relSigma, windowSec, coveredSec;
        public double lo95() { return meanR * Math.exp(-1.96 * relSigma); }
        public double hi95() { return meanR * Math.exp(1.96 * relSigma); }
        public double pBelow() { return probBelowLimit(meanR, relSigma); }
    }

    public static final class Result {
        public QuantityResult top;
        public final List<QuantityResult> all = new ArrayList<>();
        public double energyJkg, sessionSec, peakSar;
        public double uncDb95() { return top == null ? Double.NaN : 1.96 * top.relSigma / LN10_10; }
    }

    private final List<Sample> window = new ArrayList<>();
    private long lastT;
    private double energyJkg, peakSar;
    private long startT;

    public static double windowFor(String q) { return q.equals(Model.Q_S) ? 1800 : 360; }

    public void add(RadioMonitor.Snapshot s) {
        Sample sm = new Sample();
        sm.t = s.time;
        for (Model.Component c : s.comps) {
            double r = c.ratio();
            if (Double.isNaN(r) || r <= 0) continue;
            double[] a = sm.q.get(c.quantity);
            if (a == null) { a = new double[2]; sm.q.put(c.quantity, a); }
            a[0] += r;
            double abs = r * c.uncDb * LN10_10;
            a[1] = Math.sqrt(a[1] * a[1] + abs * abs);
        }
        window.add(sm);
        while (!window.isEmpty() && s.time - window.get(0).t > 1800_000L) window.remove(0);

        double sar = Math.max(s.sarHeadTotal, s.sarBodyTotal);
        if (startT == 0) startT = s.time;
        if (lastT > 0) energyJkg += sar * Math.min(10, (s.time - lastT) / 1000.0); // cap gaps (app paused)
        lastT = s.time;
        peakSar = Math.max(peakSar, sar);
    }

    public Result compute() {
        Result res = new Result();
        res.energyJkg = energyJkg;
        res.peakSar = peakSar;
        if (window.isEmpty()) return res;
        long now = window.get(window.size() - 1).t;
        res.sessionSec = (now - startT) / 1000.0;
        List<String> qs = new ArrayList<>();
        for (Sample sm : window) for (String q : sm.q.keySet()) if (!qs.contains(q)) qs.add(q);
        for (String q : qs) {
            double w = windowFor(q);
            double sumR = 0, sumSig = 0, first = now;
            int n = 0;
            for (Sample sm : window) {
                if ((now - sm.t) / 1000.0 > w) continue;
                double[] a = sm.q.get(q);
                sumR += a == null ? 0 : a[0];
                sumSig += a == null ? 0 : a[1];
                n++;
                first = Math.min(first, sm.t);
            }
            if (n == 0) continue;
            QuantityResult qr = new QuantityResult();
            qr.quantity = q;
            qr.meanR = sumR / n;
            // absolute sigma of a time average with errors fully correlated over time (same model each sample)
            qr.relSigma = qr.meanR > 0 ? (sumSig / n) / qr.meanR : 0;
            qr.windowSec = w;
            qr.coveredSec = (now - first) / 1000.0;
            res.all.add(qr);
            if (res.top == null || qr.meanR > res.top.meanR) res.top = qr;
        }
        return res;
    }

    public void reset() { window.clear(); energyJkg = 0; peakSar = 0; lastT = 0; startT = 0; }

    /** P(true R < 1) for a log-normal estimate with median R and ln-sigma s. */
    public static double probBelowLimit(double r, double s) {
        if (r <= 0) return 1;
        if (s <= 1e-9) return r < 1 ? 1 : 0;
        return phi(-Math.log(r) / s);
    }

    /** Standard normal CDF (Abramowitz & Stegun 7.1.26, |error| < 1.5e-7). */
    public static double phi(double x) {
        double z = Math.abs(x) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * z);
        double erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-z * z);
        return x >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
    }

    public static String confidenceLabel(double unc95Db) {
        if (Double.isNaN(unc95Db)) return "—";
        if (unc95Db <= 3) return "High";
        if (unc95Db <= 8) return "Moderate";
        if (unc95Db <= 14) return "Low (screening grade)";
        return "Very low (indicative only)";
    }
}
