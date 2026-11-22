package org.emfvector.app;

/** Case Study 6: 50 Hz three-phase overhead line, B by phasor Biot-Savart, E by image charges (Eqs. D10-D11). */
public final class PowerLine {
    public double voltageKv = 400;     // line-to-line rms
    public double currentA = 1000;     // per phase rms
    public double spacing = 11;        // between adjacent phases (flat arrangement)
    public double height = 12;         // conductor height
    public int subConductors = 4;
    public double subRadius = 0.0143;
    public double bundleSide = 0.45;   // square side for 4-bundle
    public double evalHeight = 1.0;

    private double[] xs() { return new double[]{-spacing, 0, spacing}; }

    private static final double[] PH_RE = {Math.cos(Math.toRadians(120)), 1, Math.cos(Math.toRadians(-120))};
    private static final double[] PH_IM = {Math.sin(Math.toRadians(120)), 0, Math.sin(Math.toRadians(-120))};

    public double bundleRadius() {
        int n = subConductors;
        if (n <= 1) return subRadius;
        double rb = bundleSide / (2 * Math.sin(Math.PI / n));
        return Math.pow(n * subRadius * Math.pow(rb, n - 1), 1.0 / n);
    }

    /** Resultant rms flux density (microtesla) at lateral x. */
    public double bField_uT(double x) {
        double[] xc = xs();
        double bxr = 0, bxi = 0, byr = 0, byi = 0;
        for (int i = 0; i < 3; i++) {
            double dx = x - xc[i], dy = evalHeight - height;
            double d2 = dx * dx + dy * dy;
            double k = Physics.MU0 * currentA / (2 * Math.PI * d2);
            // B = mu0 I /(2 pi d) * (z x r_hat) -> (-dy, dx)/d
            double bx = -k * dy, by = k * dx;
            bxr += bx * PH_RE[i]; bxi += bx * PH_IM[i];
            byr += by * PH_RE[i]; byi += by * PH_IM[i];
        }
        return Math.sqrt(bxr * bxr + bxi * bxi + byr * byr + byi * byi) * 1e6;
    }

    /** Resultant rms electric field (kV/m) at lateral x. */
    public double eField_kVm(double x) {
        double[] xc = xs();
        double req = bundleRadius();
        double k = 1.0 / (2 * Math.PI * Physics.EPS0);
        double[][] p = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                if (i == j) p[i][j] = k * Math.log(2 * height / req);
                else {
                    double dij = Math.abs(xc[i] - xc[j]);
                    double dpij = Math.hypot(dij, 2 * height);
                    p[i][j] = k * Math.log(dpij / dij);
                }
            }
        double[][] inv = invert3(p);
        double v = voltageKv * 1e3 / Math.sqrt(3);
        double[] qr = new double[3], qi = new double[3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                qr[i] += inv[i][j] * v * PH_RE[j];
                qi[i] += inv[i][j] * v * PH_IM[j];
            }
        double exr = 0, exi = 0, eyr = 0, eyi = 0;
        double y = evalHeight;
        for (int i = 0; i < 3; i++) {
            double dx = x - xc[i];
            double r1 = dx * dx + (y - height) * (y - height);
            double r2 = dx * dx + (y + height) * (y + height);
            double gx = k * (dx / r1 - dx / r2);
            double gy = k * ((y - height) / r1 - (y + height) / r2);
            exr += gx * qr[i]; exi += gx * qi[i];
            eyr += gy * qr[i]; eyi += gy * qi[i];
        }
        return Math.sqrt(exr * exr + exi * exi + eyr * eyr + eyi * eyi) / 1e3;
    }

    public double chargeMagnitude_uC(int phase) {
        // convenience: recompute charges
        double[] xc = xs();
        double req = bundleRadius();
        double k = 1.0 / (2 * Math.PI * Physics.EPS0);
        double[][] p = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                if (i == j) p[i][j] = k * Math.log(2 * height / req);
                else { double d = Math.abs(xc[i] - xc[j]); p[i][j] = k * Math.log(Math.hypot(d, 2 * height) / d); }
            }
        double[][] inv = invert3(p);
        double v = voltageKv * 1e3 / Math.sqrt(3);
        double re = 0, im = 0;
        for (int j = 0; j < 3; j++) { re += inv[phase][j] * v * PH_RE[j]; im += inv[phase][j] * v * PH_IM[j]; }
        return Math.hypot(re, im) * 1e6;
    }

    static double[][] invert3(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2], d = m[1][0], e = m[1][1], f = m[1][2], g = m[2][0], h = m[2][1], i = m[2][2];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        return new double[][]{
                {A / det, -(b * i - c * h) / det, (b * f - c * e) / det},
                {B / det, (a * i - c * g) / det, -(a * f - c * d) / det},
                {C / det, -(a * h - b * g) / det, (a * e - b * d) / det}};
    }
}
