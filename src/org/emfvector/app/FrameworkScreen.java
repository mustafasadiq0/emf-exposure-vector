package org.emfvector.app;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Reference content: the framework's principles, tables and qualifications. */
public final class FrameworkScreen {
    private final ScrollView root;

    public FrameworkScreen(Activity a) {
        LinearLayout[] h = new LinearLayout[1];
        root = Ui.page(a, h);
        LinearLayout l = h[0];

        LinearLayout c = Ui.card(a, l, "Central position");
        c.addView(Ui.body(a, "Risk assessment requires exposure characterization, and exposure characterization requires frequency-appropriate dosimetry."));
        c.addView(Ui.gap(a, 6));
        c.addView(Ui.mono(a, "Frequency + Power + Distance + Geometry\n+ Time + Human anatomy → Dosimetric exposure"));
        c.addView(Ui.gap(a, 6));
        c.addView(Ui.mono(a, "Source → Field → Exposure → Absorption\n→ Thermal response → Biological outcome"));
        c.addView(Ui.note(a, "This app reports exposure as accurately as a phone allows. It does not claim disease causation, " +
                "and it never collapses different physical quantities into one risk score."));

        c = Ui.card(a, l, "How the Live tab measures");
        c.addView(Ui.table(a, new String[][]{
                {"Reading", "What Android gives", "How it becomes exposure"},
                {"LTE downlink", "RSRP per resource element, EARFCN, bandwidth", "RSRP + 10·log(N_RE) = full-load carrier power; S = P / (Gλ²/4π)"},
                {"5G NR downlink", "SS-RSRP, NR-ARFCN", "Full-load, peak-beam extrapolation; time average × TDD × beam factor"},
                {"GSM / UMTS", "RSSI / CPICH RSCP, ARFCN", "BCCH full power / pilot + 10 dB"},
                {"Wi-Fi", "RSSI, frequency, link speed", "S from RSSI × AP duty measured from your traffic"},
                {"Bluetooth LE", "RSSI of nearby advertisers", "S from RSSI × advertising duty"},
                {"Phone uplink", "Not exposed by Android", "3GPP open-loop power control from path loss; duty from upload traffic"},
                {"SAR", "Not exposed by Android", "Declared SAR × estimated average power / declared-at power"},
                {"Magnetometer", "Static |B| (µT)", "Compared with ICNIRP static-field limit"}}, new float[]{0.9f, 1.2f, 1.6f}));
        c.addView(Ui.note(a, "Accuracy: phone RSSI is typically ±3–6 dB and the antenna gain is unknown (default −3 dBi), so downlink S is an " +
                "order-of-magnitude screening value. Uplink and SAR are model estimates. Use a calibrated probe or a SAR lab for compliance."));

        c = Ui.card(a, l, "Table 1 · Exposure regimes");
        c.addView(Ui.table(a, new String[][]{
                {"Regime", "Applications", "Quantity", "Interaction"},
                {"Static", "MRI, magnets, DC", "B, H, E", "Forces; motion-induced"},
                {"ELF", "Power systems, motors", "E, B, H", "Induced fields/currents"},
                {"RF < 6 GHz", "Cellular, Wi-Fi, BT", "E, H, S, SAR", "Absorption, heating"},
                {"5G mid-band", "3.3–4 GHz", "E, H, S, SAR", "RF absorption"},
                {"mmWave", "24–100+ GHz, radar", "S, S_ab", "Superficial absorption"},
                {"Infrared / visible", "Heaters, lighting", "Irradiance", "Thermal / photochemical"},
                {"UV", "Sun, sterilization", "Irradiance / dose", "Photochemical, DNA, eye"},
                {"X, gamma, particles", "CT, nuclear", "Gy, Sv", "Ionization"}}, new float[]{1, 1.2f, 1, 1.3f}));
        c.addView(Ui.small(a, "W/kg (SAR), Gy (ionizing dose) and V/m (ELF field) describe different mechanisms and are never summed."));

        c = Ui.card(a, l, "Decision tree (Fig. 6)");
        c.addView(Ui.mono(a, "f ≤ 3 kHz    → E, B     → induced fields\n" +
                "f ≤ 6 GHz    → SAR      → volumetric absorption\n" +
                "f > 6 GHz    → S, S_ab  → superficial absorption\n" +
                "        ↘ exposure assessment + uncertainty ↙"));

        c = Ui.card(a, l, "ICNIRP 2020 basic restrictions (Table 2)");
        c.addView(Ui.table(a, new String[][]{
                {"Quantity", "Range", "Public", "Occupational"},
                {"Whole-body SAR (30 min)", "100 kHz–300 GHz", "0.08 W/kg", "0.4 W/kg"},
                {"Local SAR head/torso (10 g, 6 min)", "100 kHz–6 GHz", "2 W/kg", "10 W/kg"},
                {"Local SAR limbs", "100 kHz–6 GHz", "4 W/kg", "20 W/kg"},
                {"Absorbed power density (4 cm²)", "> 6 GHz", "20 W/m²", "100 W/m²"},
                {"Absorbed power density (1 cm²)", "> 30 GHz", "40 W/m²", "200 W/m²"}}, new float[]{1.6f, 1.1f, 0.8f, 0.9f}));
        c.addView(Ui.small(a, "Limits carry wide safety margins below the levels at which established heating effects occur; they are not thresholds where disease begins."));

        c = Ui.card(a, l, "Reference levels (Table 3 and ICNIRP 2010)");
        c.addView(Ui.table(a, new String[][]{
                {"Frequency", "Public", "Occupational"},
                {"30–400 MHz", "2 W/m²", "10 W/m²"},
                {"400–2000 MHz", "f/200 W/m²", "f/40 W/m²"},
                {"> 2 GHz", "10 W/m²", "50 W/m²"},
                {"50 Hz electric field", "5 kV/m", "10 kV/m"},
                {"50 Hz magnetic field", "200 µT (1998: 100 µT)", "1000 µT"}}, null));

        c = Ui.card(a, l, "Three-tier evidence classification (Sec. 10.1)");
        c.addView(Ui.pill(a, "Established", Ui.C_BAD));
        c.addView(Ui.body(a, "Heating from sufficiently intense RF; DNA and tissue damage from ionizing radiation; UV skin cancer and eye injury; " +
                "nerve and muscle stimulation from intense low-frequency fields."));
        c.addView(Ui.gap(a, 8));
        c.addView(Ui.pill(a, "Investigated, not established", Ui.C_WARN));
        c.addView(Ui.body(a, "Low-level RF and cancer, cognition, sleep, reproduction, oxidative stress, genetic effects. WHO-commissioned systematic " +
                "reviews (2024, 2025) find mobile-phone RF likely does not increase the risk of the most-studied brain tumours; less-studied outcomes " +
                "have lower certainty. ARPANSA's review of 138 studies above 6 GHz found no confirmed hazard from low-level 5G fields."));
        c.addView(Ui.gap(a, 8));
        c.addView(Ui.pill(a, "Unknown", Ui.MUTED));
        c.addView(Ui.body(a, "Decades of 6G exposure, dense simultaneous multi-source exposure, sub-THz systems, reconfigurable intelligent surfaces, " +
                "and some high-power, highly directional occupational scenarios."));

        c = Ui.card(a, l, "Limitations of analytical screening (Sec. 23.2)");
        c.addView(Ui.body(a, "The free-space model does not include multipath, building penetration, beamforming dynamics, body coupling, direct SAR " +
                "calculation or thermoregulation, and it does not establish health outcomes. Close-proximity devices need full-wave simulation with " +
                "anatomical phantoms (openEMS, CST, HFSS, COMSOL) or validated measurement (IEC/IEEE 62209-1528, IEC/IEEE 63195-1, IEC 62232)."));

        c = Ui.card(a, l, "Threats to validity (Sec. 28.1)");
        c.addView(Ui.body(a, "Model (phantoms approximate bodies) · Parameter (tissue properties vary) · Exposure (power varies dynamically) · " +
                "Environment (buildings, vehicles, people) · Behaviour (posture, device placement) · Epidemiological (dose ≠ clinical outcome)."));

        c = Ui.card(a, l, "Source");
        c.addView(Ui.body(a, "Mustafa Sadiq Latief, “Human Electromagnetic Exposure in the 5G/6G Era: A Multi-Source Dosimetric Framework " +
                "Integrating RF, mmWave, ELF, Occupational Exposure, and Specific Absorption Rate”, technical report."));
        c.addView(Ui.small(a, "Key references: ICNIRP 2020 (Health Phys. 118:483); ICNIRP 2010 (Health Phys. 99:818); IEEE C95.1-2019; " +
                "Karipidis et al. 2021 (JESEE 31:585); Karipidis et al. 2024, 2025 (Environ. Int. 191:108983, 196:109274); FCC OET-65; 3GPP TS 36.213, 38.213."));
    }

    public View view() { return root; }
}
