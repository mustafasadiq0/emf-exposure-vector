package org.emfvector.app;

import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Multi-source exposure vector builder (Sec. 14, 18 and Case Study 4). */
public final class VectorScreen {
    static final int FAR = 0, SAR = 1, MMW = 2, ELF_E = 3, ELF_B = 4;
    static final String[] TYPES = {"Far-field RF source (incident S)", "Body-worn / handheld device (SAR)",
            "mmWave source near body (S_ab)", "ELF electric field (50/60 Hz)", "ELF magnetic field (50/60 Hz)"};

    static final class Src {
        int type; String name; double fHz, eirp, dist, duty = 1, factor = 1, wallDb, value; boolean head;
        Src(int t, String n, double f) { type = t; name = n; fHz = f; }
    }

    private final Activity a;
    private final ScrollView root;
    private final List<Src> sources = new ArrayList<>();
    private LinearLayout listBox, sumBox, form;
    private VectorView vv;
    private Switch occ;
    private Spinner typeSp;
    private EditText[] fields = new EditText[6];

    public VectorScreen(Activity act) {
        a = act;
        LinearLayout[] h = new LinearLayout[1];
        root = Ui.page(a, h);
        build(h[0]);
        preset(0);
    }

    public View view() { return root; }

    private void build(LinearLayout l) {
        LinearLayout c = Ui.card(a, l, "Multi-source scenario");
        c.addView(Ui.small(a, "X = [X_ELF, X_5G, X_phone, X_WiFi, X_BT, X_radar, X_IoT]: every source keeps its own unit and its own limit."));
        final Spinner ps = Ui.spinner(a, c, "Preset", new String[]{"Home: Wi-Fi, Bluetooth, outdoor 5G (Case Study 4)",
                "Urban subject (Table 9)", "Six case studies (Table 22)", "Empty"}, 0);
        ps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (first) { first = false; return; } preset(pos); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        occ = Ui.toggle(a, c, "Occupational limits", false);
        occ.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean v) { refresh(); }
        });

        LinearLayout vc = Ui.card(a, l, "Normalized exposure vector R");
        vv = new VectorView(a);
        vc.addView(vv);
        sumBox = new LinearLayout(a);
        sumBox.setOrientation(LinearLayout.VERTICAL);
        vc.addView(sumBox);
        vc.addView(Ui.note(a, "Why no single “risk index”? MSEEI = Σ wᵢRᵢ would need weights linking ELF induced currents, RF heating " +
                "and mmWave surface heating to one health scale. No validated weights exist, so the vector is kept (Sec. 14.2)."));

        LinearLayout lc = Ui.card(a, l, "Sources");
        listBox = new LinearLayout(a);
        listBox.setOrientation(LinearLayout.VERTICAL);
        lc.addView(listBox);

        LinearLayout ac = Ui.card(a, l, "Add a source");
        typeSp = Ui.spinner(a, ac, "Type", TYPES, 0);
        form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        ac.addView(form);
        typeSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { buildForm(pos); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        buildForm(0);
        Button add = Ui.button(a, "Add to vector", true);
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { addFromForm(); } });
        ac.addView(add);
    }

    private EditText nameField;

    private void buildForm(int t) {
        form.removeAllViews();
        nameField = new EditText(a);
        nameField.setHint("Name");
        nameField.setText(new String[]{"Rooftop antenna", "Smartphone", "28 GHz device", "Overhead line E", "Overhead line B"}[t]);
        form.addView(nameField);
        String[][] labels = {
                {"Frequency (MHz)", "EIRP (W)", "Distance (m)", "Duty / time factor", "Reflection factor (1–2.56)", "Wall loss (dB)"},
                {"Frequency (MHz)", "Local SAR 10 g (W/kg)", "At head? 1 = yes, 0 = body", null, null, null},
                {"Frequency (MHz)", "EIRP (W)", "Distance (m)", "Duty", null, null},
                {"Frequency (Hz)", "E (kV/m)", null, null, null, null},
                {"Frequency (Hz)", "B (µT)", null, null, null, null}};
        double[][] defs = {{3500, 1000, 30, 1, 1, 0}, {1800, 0.3, 1, 0, 0, 0}, {28000, 1, 0.05, 0.25, 0, 0},
                {50, 2, 0, 0, 0, 0}, {50, 5, 0, 0, 0, 0}};
        for (int i = 0; i < 6; i++) {
            fields[i] = null;
            if (labels[t][i] != null) fields[i] = Ui.num(a, form, labels[t][i], defs[t][i]);
        }
    }

    private void addFromForm() {
        int t = typeSp.getSelectedItemPosition();
        double f0 = Ui.val(fields[0], 0);
        Src s = new Src(t, nameField.getText().toString(), t >= ELF_E ? f0 : f0 * 1e6);
        switch (t) {
            case FAR: s.eirp = Ui.val(fields[1], 1); s.dist = Ui.val(fields[2], 1); s.duty = Ui.val(fields[3], 1);
                s.factor = Ui.val(fields[4], 1); s.wallDb = Ui.val(fields[5], 0); break;
            case SAR: s.value = Ui.val(fields[1], 0); s.head = Ui.val(fields[2], 1) >= 0.5; break;
            case MMW: s.eirp = Ui.val(fields[1], 1); s.dist = Ui.val(fields[2], 0.05); s.duty = Ui.val(fields[3], 1); break;
            default: s.value = Ui.val(fields[1], 0);
        }
        sources.add(s);
        refresh();
    }

    private void preset(int i) {
        sources.clear();
        Src s;
        double bsEirp = 200 * Physics.dbToLin(25);
        switch (i) {
            case 0:
                s = new Src(FAR, "Wi-Fi router 2.4 GHz, 2 m", 2.4e9); s.eirp = 0.1; s.dist = 2; s.duty = 0.1; sources.add(s);
                s = new Src(FAR, "Wi-Fi router 5 GHz, 2 m", 5e9); s.eirp = 0.2; s.dist = 2; s.duty = 0.1; sources.add(s);
                s = new Src(FAR, "Laptop Wi-Fi, 0.5 m", 5e9); s.eirp = 0.0316; s.dist = 0.5; s.duty = 0.1; sources.add(s);
                s = new Src(FAR, "Bluetooth device, 0.3 m", 2.44e9); s.eirp = 0.0025; s.dist = 0.3; s.duty = 0.3; sources.add(s);
                s = new Src(FAR, "3.5 GHz base station, 150 m, indoors", 3.5e9); s.eirp = bsEirp; s.dist = 150; s.duty = 0.75 * 0.25;
                s.factor = 2.56; s.wallDb = 15; sources.add(s);
                break;
            case 1:
                s = new Src(FAR, "5G base station 3.5 GHz, 50 m", 3.5e9); s.eirp = bsEirp; s.dist = 50; s.duty = 0.1875; s.factor = 2.56; sources.add(s);
                s = new Src(SAR, "Smartphone at head, 20 mW", 1.8e9); s.value = 0.085; s.head = true; sources.add(s);
                s = new Src(FAR, "Wi-Fi 5 GHz, 3 m", 5e9); s.eirp = 0.2; s.dist = 3; s.duty = 0.1; sources.add(s);
                s = new Src(FAR, "Bluetooth 2.4 GHz, 0.3 m", 2.44e9); s.eirp = 0.0025; s.dist = 0.3; s.duty = 0.3; sources.add(s);
                s = new Src(MMW, "Automotive radar 77 GHz, 2 m", 77e9); s.eirp = 3.16; s.dist = 2; sources.add(s);
                s = new Src(ELF_E, "Power line 50 Hz (20 m), E", 50); s.value = 4.20; sources.add(s);
                s = new Src(ELF_B, "Power line 50 Hz (20 m), B", 50); s.value = 8.46; sources.add(s);
                s = new Src(SAR, "Wearable 2.4 GHz on wrist", 2.44e9); s.value = 0.02; s.head = false; sources.add(s);
                s = new Src(FAR, "IoT sensor 868 MHz, 1 m", 868e6); s.eirp = 0.025; s.dist = 1; s.duty = 0.01; sources.add(s);
                break;
            case 2:
                s = new Src(FAR, "1. 3.5 GHz macro site, 50 m (scenario D)", 3.5e9); s.eirp = bsEirp; s.dist = 50; s.duty = 0.1875; s.factor = 2.56; sources.add(s);
                s = new Src(SAR, "3. Smartphone at head, 1.8 GHz, max power", 1.8e9); s.value = 0.85; s.head = true; sources.add(s);
                s = new Src(MMW, "5a. 28 GHz handset at 2 cm, no back-off", 28e9); s.eirp = 1; s.dist = 0.02; sources.add(s);
                s = new Src(MMW, "5b. 77 GHz radar at 0.5 m", 77e9); s.eirp = 3.16; s.dist = 0.5; sources.add(s);
                s = new Src(ELF_E, "6. 400 kV line, max E", 50); s.value = 6.24; sources.add(s);
                s = new Src(ELF_B, "6. 400 kV line, max B", 50); s.value = 18.2; sources.add(s);
                break;
            default:
        }
        refresh();
    }

    private List<Model.Component> components() {
        boolean o = occ.isChecked();
        List<Model.Component> cs = new ArrayList<>();
        for (Src s : sources) {
            Model.Component c;
            switch (s.type) {
                case FAR: {
                    double v = Physics.sFar(s.eirp * s.duty * s.factor * Physics.dbToLin(-s.wallDb), s.dist);
                    c = new Model.Component(s.name, "RF", "Model", "model", s.fHz, Model.Q_S, "W/m²", v, Physics.refLevelS(s.fHz, o));
                    if (s.fHz > 6e9) c.detail = "Above 6 GHz: also check local S_ab.";
                    break;
                }
                case SAR:
                    c = new Model.Component(s.name, "RF", "Model", "model", s.fHz, s.head ? Model.Q_SAR_HEAD : Model.Q_SAR_BODY, "W/kg",
                            s.value, s.head ? Physics.sarLimitHead(o) : Physics.sarLimitLimbs(o));
                    break;
                case MMW: {
                    double v = Physics.sFar(s.eirp * s.duty, s.dist);
                    c = new Model.Component(s.name, "mmWave", "Model", "model", s.fHz, Model.Q_SAB, "W/m²", v, Physics.localAbsorbedLimit(s.fHz, o));
                    break;
                }
                case ELF_E:
                    c = new Model.Component(s.name, "ELF", "Model", "model", s.fHz, Model.Q_ELF_E, "kV/m", s.value, Physics.elfLimitE_kVm(s.fHz, o));
                    break;
                default:
                    c = new Model.Component(s.name, "ELF", "Model", "model", s.fHz, Model.Q_ELF_B, "µT", s.value, Physics.elfLimitB_uT(s.fHz, o));
            }
            cs.add(c);
        }
        return cs;
    }

    private void refresh() {
        List<Model.Component> cs = components();
        vv.set(cs);
        sumBox.removeAllViews();
        sumBox.addView(Ui.small(a, "Same-quantity totals (Eq. D7). No line adds two different quantities:"));
        for (String q : Model.quantities(cs)) {
            double s = Model.sumRatio(cs, q);
            TextView t = Ui.kv(a, sumBox, q, Physics.pct(s));
            t.setTextColor(Ui.ratioColor(s));
        }
        // dominant source in the RF incident group
        Model.Component dom = null;
        double tot = Model.sumRatio(cs, Model.Q_S);
        for (Model.Component c : cs) if (c.quantity.equals(Model.Q_S) && (dom == null || c.ratio() > dom.ratio())) dom = c;
        if (dom != null && tot > 0)
            sumBox.addView(Ui.small(a, String.format(Locale.US, "Dominant whole-body RF source: %s (%s of the RF total).",
                    dom.source, Physics.pct(dom.ratio() / tot))));

        listBox.removeAllViews();
        for (int i = 0; i < cs.size(); i++) {
            final int idx = i;
            Model.Component c = cs.get(i);
            LinearLayout row = Ui.hrow(a);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            col.addView(Ui.body(a, c.source));
            col.addView(Ui.small(a, String.format(Locale.US, "%s · %s %s vs %s · R %s", c.fHz > 0 ? Physics.freq(c.fHz) : "",
                    Physics.fmt(c.value), c.unit, Physics.fmt(c.limit), Physics.pct(c.ratio()))));
            row.addView(col);
            Button del = Ui.button(a, "✕", false);
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sources.remove(idx); refresh(); } });
            row.addView(del);
            listBox.addView(row);
            listBox.addView(Ui.divider(a));
        }
        if (cs.isEmpty()) listBox.addView(Ui.small(a, "No sources. Add one below or choose a preset."));
    }
}
