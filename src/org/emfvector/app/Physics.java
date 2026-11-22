package org.emfvector.app;

/**
 * Analytical dosimetry engine implementing the equations of
 * "Human Electromagnetic Exposure in the 5G/6G Era" (Latief).
 * Pure Java: no Android dependencies, so it can be unit-tested on a JVM.
 */
public final class Physics {
    private Physics() {}

    public static final double C = 299_792_458.0;
    public static final double C_ROUND = 3.0e8;          // value used in the report's worked examples
    public static final double ETA0 = 377.0;             // free-space wave impedance used in the report
    public static final double H_PLANCK = 6.62607015e-34;
    public static final double E_CHARGE = 1.602176634e-19;
    public static final double MU0 = 4e-7 * Math.PI;
    public static final double EPS0 = 8.854e-12;
    /** Rough lower bound of photon energy needed to ionize biological molecules (eV). */
    public static final double IONIZATION_EV = 10.0;

    // ------------------------------------------------------------------ basic relations
    public static double lambda(double fHz) { return C_ROUND / fHz; }

    /** Eq. (1): photon energy in eV. */
    public static double photonEnergyEv(double fHz) { return H_PLANCK * fHz / E_CHARGE; }

    public static double dbToLin(double db) { return Math.pow(10, db / 10.0); }
    public static double linToDb(double x) { return 10 * Math.log10(x); }
    public static double dbmToW(double dbm) { return Math.pow(10, (dbm - 30) / 10.0); }
    public static double wToDbm(double w) { return 10 * Math.log10(w) + 30; }

    /** Eq. (5): far-field incident power density, W/m^2. */
    public static double sFar(double eirpW, double rM) { return eirpW / (4 * Math.PI * rM * rM); }

    /** Eq. (7): plane-wave E (V/m) from S. */
    public static double eFromS(double s) { return Math.sqrt(ETA0 * s); }

    /** Appendix A.4: plane-wave H (A/m) from S. */
    public static double hFromS(double s) { return Math.sqrt(s / ETA0); }

    /** Eq. (8): change in power density (dB) when distance changes from r1 to r2. */
    public static double distanceSensitivityDb(double r1, double r2) { return -20 * Math.log10(r2 / r1); }

    /** Eq. (D2): distance at which F*EIRP produces the limit S_L. */
    public static double complianceDistance(double eirpW, double factorF, double sLimit) {
        return Math.sqrt(factorF * eirpW / (4 * Math.PI * sLimit));
    }

    /** Eq. (15): radar average power. */
    public static double radarAvg(double peakW, double duty) { return peakW * duty; }

    // ------------------------------------------------------------------ near field
    /** Fraunhofer far-field boundary 2D^2/lambda. */
    public static double farFieldBoundary(double apertureM, double fHz) {
        double l = lambda(fHz);
        return 2 * apertureM * apertureM / l;
    }

    /** Reactive near-field boundary lambda/(2*pi). */
    public static double reactiveBoundary(double fHz) { return lambda(fHz) / (2 * Math.PI); }

    /** Eq. (D3): cylindrical near-field model (FCC OET-65) for a tall collinear/panel antenna. */
    public static double sCylindrical(double pW, double beamwidthDeg, double rM, double heightM) {
        return 180.0 * pW / (beamwidthDeg * Math.PI * rM * heightM);
    }

    // ------------------------------------------------------------------ ICNIRP 2020 limits
    /** Table 3: whole-body incident power-density reference level (W/m^2). NaN outside 30 MHz - 300 GHz. */
    public static double refLevelS(double fHz, boolean occupational) {
        double fMHz = fHz / 1e6;
        if (fMHz < 30 || fMHz > 300_000) return Double.NaN;
        if (fMHz < 400) return occupational ? 10 : 2;
        if (fMHz < 2000) return occupational ? fMHz / 40.0 : fMHz / 200.0;
        return occupational ? 50 : 10;
    }

    /** Tables 2/4: local absorbed power density basic restriction above 6 GHz (W/m^2). NaN below 6 GHz. */
    public static double localAbsorbedLimit(double fHz, boolean occupational) {
        if (fHz <= 6e9) return Double.NaN;
        if (fHz > 30e9) return occupational ? 200 : 40;
        return occupational ? 100 : 20;
    }

    /** Averaging area for the local restriction above 6 GHz (cm^2). */
    public static double localAveragingAreaCm2(double fHz) { return fHz > 30e9 ? 1 : 4; }

    /** Table 2: local 10 g SAR limit, head/torso (W/kg). */
    public static double sarLimitHead(boolean occ) { return occ ? 10 : 2; }
    public static double sarLimitLimbs(boolean occ) { return occ ? 20 : 4; }
    public static double sarLimitWholeBody(boolean occ) { return occ ? 0.4 : 0.08; }

    // ------------------------------------------------------------------ ICNIRP ELF (2010 / 1998)
    /** ICNIRP 2010 electric-field reference level, kV/m, valid 25 Hz - 3 kHz. */
    public static double elfLimitE_kVm(double fHz, boolean occ) {
        if (fHz < 25 || fHz > 3000) return Double.NaN;
        if (fHz <= 50) return occ ? 10 : 5;
        return occ ? 500.0 / fHz : 250.0 / fHz;
    }

    /** ICNIRP 2010 magnetic-flux reference level, microtesla, valid 25 - 300 Hz. */
    public static double elfLimitB_uT(double fHz, boolean occ) {
        if (fHz < 25 || fHz > 300) return Double.NaN;
        return occ ? 1000 : 200;
    }

    /** ICNIRP 1998 public magnetic-flux reference level (conservative benchmark), microtesla. */
    public static double elfLimitB1998_uT(double fHz) {
        if (fHz < 25 || fHz > 800) return Double.NaN;
        return 5000.0 / fHz;
    }

    // ------------------------------------------------------------------ regime decision tree (Fig. 6)
    public enum Regime {
        STATIC("Static field", "B, H, E", "Forces; induced effects in motion"),
        ELF("ELF", "E, B (H)", "Induced electric fields and currents"),
        IF("Intermediate frequency", "Induced E-field; SAR near 100 kHz", "Nerve stimulation to heating transition"),
        RF("RF below 6 GHz", "E, H, S and SAR", "Volumetric energy absorption (heating)"),
        MMW("Above 6 GHz / mmWave", "S and absorbed power density S_ab", "Superficial (skin-surface) absorption"),
        SUBTHZ("Sub-THz (6G)", "S_ab; field still non-ionizing", "Very superficial absorption; open research gap");

        public final String label, quantity, mechanism;
        Regime(String l, String q, String m) { label = l; quantity = q; mechanism = m; }
    }

    public static Regime regime(double fHz) {
        if (fHz <= 1) return Regime.STATIC;
        if (fHz <= 3000) return Regime.ELF;
        if (fHz < 100e3) return Regime.IF;
        if (fHz <= 6e9) return Regime.RF;
        if (fHz <= 300e9) return Regime.MMW;
        return Regime.SUBTHZ;
    }

    // ------------------------------------------------------------------ SAR and thermal (Eqs. 2, 11, D4-D9)
    /** Eq. (2) with peak field amplitude: SAR = sigma |E|^2 / (2 rho). */
    public static double sarFromPeakE(double sigma, double ePeak, double rho) { return sigma * ePeak * ePeak / (2 * rho); }

    /** Eq. (D4): rms tissue field for a given SAR. */
    public static double eRmsFromSar(double sar, double rho, double sigma) { return Math.sqrt(sar * rho / sigma); }

    /** Eq. (D5): adiabatic temperature rise (K). */
    public static double dTAdiabatic(double sar, double tSec, double c) { return sar * tSec / c; }

    /** Eq. (D6): perfusion-limited steady state (K). */
    public static double dTSteady(double rho, double sar, double rhoB, double cB, double wB) {
        return rho * sar / (rhoB * cB * wB);
    }

    /** Eq. (D9): perfusion length (m). */
    public static double perfusionLength(double k, double rhoB, double cB, double wB) {
        return Math.sqrt(k / (rhoB * cB * wB));
    }

    /** Eq. (D9): steady-state surface temperature rise for surface flux q (W/m^2). */
    public static double dTSurface(double q, double k, double rhoB, double cB, double wB) {
        return q * perfusionLength(k, rhoB, cB, wB) / k;
    }

    /** Eq. (D8): power penetration depth (m) from complex permittivity eps' - j eps''. */
    public static double penetrationDepth(double fHz, double epsR, double epsI) {
        // principal square root of (epsR - j epsI)
        double mod = Math.hypot(epsR, epsI);
        double im = Math.sqrt((mod - epsR) / 2.0);
        double alpha = (2 * Math.PI * fHz / C) * Math.abs(im);
        return 1.0 / (2 * alpha);
    }

    /** Conductivity (S/m) from imaginary relative permittivity. */
    public static double sigmaFromEpsI(double fHz, double epsI) { return 2 * Math.PI * fHz * EPS0 * epsI; }

    // ------------------------------------------------------------------ cellular full-load extrapolation
    /** Number of resource elements across a carrier for RSRP extrapolation. */
    public static double resourceElements(double bandwidthMHz, double scsKHz) {
        // 90 % occupancy approximation of 3GPP N_RB tables
        return Math.floor(bandwidthMHz * 1000 * 0.9 / (12 * scsKHz)) * 12;
    }

    /** Incident power density from power received by an antenna of gain gDbi: S = Pr / Ae, Ae = G lambda^2 / 4 pi. */
    public static double sFromReceived(double prW, double fHz, double gDbi) {
        double l = C / fHz;
        double ae = dbToLin(gDbi) * l * l / (4 * Math.PI);
        return prW / ae;
    }

    // ------------------------------------------------------------------ formatting helpers
    public static String fmt(double v) {
        if (Double.isNaN(v)) return "n/a";
        if (Double.isInfinite(v)) return "∞";
        double a = Math.abs(v);
        if (a == 0) return "0";
        if (a >= 1e5 || a < 1e-3) return String.format(java.util.Locale.US, "%.3g", v).replace("e", "×10^");
        if (a >= 100) return String.format(java.util.Locale.US, "%.1f", v);
        if (a >= 1) return String.format(java.util.Locale.US, "%.3g", v);
        return String.format(java.util.Locale.US, "%.3g", v);
    }

    public static String pct(double ratio) {
        if (Double.isNaN(ratio)) return "n/a";
        double p = ratio * 100;
        if (p >= 100) return String.format(java.util.Locale.US, "%.0f %%", p);
        if (p >= 1) return String.format(java.util.Locale.US, "%.1f %%", p);
        if (p >= 0.01) return String.format(java.util.Locale.US, "%.3f %%", p);
        return String.format(java.util.Locale.US, "%.2g %%", p);
    }

    public static String freq(double fHz) {
        if (fHz >= 1e9) return fmt(fHz / 1e9) + " GHz";
        if (fHz >= 1e6) return fmt(fHz / 1e6) + " MHz";
        if (fHz >= 1e3) return fmt(fHz / 1e3) + " kHz";
        return fmt(fHz) + " Hz";
    }

    public static String dist(double m) {
        if (m < 0.01) return fmt(m * 1000) + " mm";
        if (m < 1) return fmt(m * 100) + " cm";
        return fmt(m) + " m";
    }
}
