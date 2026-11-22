package org.emfvector.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/** Small programmatic-UI toolkit. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFFF3F5F8, CARD = 0xFFFFFFFF, INK = 0xFF15212B, MUTED = 0xFF5E6B78,
            PRIMARY = 0xFF0F5E6E, LINE = 0xFFDCE2E8;
    public static final int C_RF = 0xFF2F6FDE, C_MMW = 0xFF0E9F8A, C_ELF = 0xFF7B4FB8, C_SAR = 0xFFE07A1F,
            C_STATIC = 0xFF6B7785, C_OK = 0xFF1F9D55, C_WARN = 0xFFD99A00, C_BAD = 0xFFD64545;

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static int ratioColor(double r) {
        if (Double.isNaN(r)) return MUTED;
        if (r >= 1) return C_BAD;
        if (r >= 0.2) return C_WARN;
        return C_OK;
    }

    public static int quantityColor(String q) {
        if (q.equals(Model.Q_SAR_HEAD) || q.equals(Model.Q_SAR_BODY)) return C_SAR;
        if (q.equals(Model.Q_SAB)) return C_MMW;
        if (q.equals(Model.Q_ELF_B) || q.equals(Model.Q_ELF_E)) return C_ELF;
        if (q.equals(Model.Q_STATIC)) return C_STATIC;
        return C_RF;
    }

    public static ScrollView page(Context c, LinearLayout[] holder) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 12);
        l.setPadding(p, p, p, dp(c, 40));
        sv.addView(l);
        holder[0] = l;
        return sv;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(c, 14));
        g.setStroke(dp(c, 1), LINE);
        l.setBackground(g);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout card(Context c, LinearLayout parent, String title) {
        LinearLayout k = card(c);
        if (title != null) k.addView(h2(c, title));
        parent.addView(k);
        return k;
    }

    public static TextView h1(Context c, String s) {
        TextView t = text(c, s, 21, INK);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView h2(Context c, String s) {
        TextView t = text(c, s, 16.5f, INK);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, 0, 0, dp(c, 6));
        return t;
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    public static TextView body(Context c, String s) { return text(c, s, 14, INK); }

    public static TextView small(Context c, String s) { return text(c, s, 12.5f, MUTED); }

    public static TextView mono(Context c, String s) {
        TextView t = text(c, s, 13, INK);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static View gap(Context c, int dpv) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpv)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 8);
        v.setLayoutParams(lp);
        return v;
    }

    /** Label on the left, value on the right. Returns the value TextView. */
    public static TextView kv(Context c, LinearLayout parent, String k, String v) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(c, 3), 0, dp(c, 3));
        TextView a = text(c, k, 13.5f, MUTED);
        a.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        TextView b = text(c, v, 14, INK);
        b.setGravity(Gravity.END);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(a);
        row.addView(b);
        parent.addView(row);
        return b;
    }

    public static EditText num(Context c, LinearLayout parent, String label, double value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = text(c, label, 13.5f, INK);
        a.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(trim(value));
        e.setTextSize(14.5f);
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.END);
        e.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(a);
        row.addView(e);
        parent.addView(row);
        return e;
    }

    public static String trim(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    /** Parses numbers typed with Arabic-Indic or Persian digits and comma decimals. */
    public static double val(EditText e, double def) {
        String s = e.getText().toString().trim();
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + ch - '۰'));
            else if (ch == ',' || ch == '٫') b.append('.');
            else if (ch == '−') b.append('-');
            else b.append(ch);
        }
        try { return Double.parseDouble(b.toString()); } catch (Exception ex) { return def; }
    }

    public static Spinner spinner(Context c, LinearLayout parent, String label, String[] items, int sel) {
        if (label != null) parent.addView(small(c, label));
        Spinner sp = new Spinner(c);
        ArrayAdapter<String> ad = new ArrayAdapter<>(c, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(sel);
        parent.addView(sp);
        return sp;
    }

    public static Switch toggle(Context c, LinearLayout parent, String label, boolean on) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextSize(14);
        s.setTextColor(INK);
        s.setChecked(on);
        s.setPadding(0, dp(c, 6), 0, dp(c, 6));
        parent.addView(s);
        return s;
    }

    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 22));
        if (primary) { g.setColor(PRIMARY); b.setTextColor(Color.WHITE); }
        else { g.setColor(0xFFE6EEF0); b.setTextColor(PRIMARY); }
        b.setBackground(g);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 42));
        lp.rightMargin = dp(c, 8);
        lp.topMargin = dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }

    public static LinearLayout hrow(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    public static TextView pill(Context c, String s, int color) {
        TextView t = text(c, s, 12, color);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 10));
        g.setColor((color & 0x00FFFFFF) | 0x1F000000);
        t.setBackground(g);
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** A callout box for qualifications the paper insists on (e.g. R is not a disease probability). */
    public static TextView note(Context c, String s) {
        TextView t = text(c, s, 12.5f, 0xFF3D4A56);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 10));
        g.setColor(0xFFEEF3F6);
        t.setBackground(g);
        int p = dp(c, 10);
        t.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 4);
        t.setLayoutParams(lp);
        return t;
    }

    /** Simple table: rows of strings, first row is header. */
    public static LinearLayout table(Context c, String[][] rows, float[] weights) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setPadding(0, dp(c, 4), 0, dp(c, 4));
            if (i == 0) r.setBackgroundColor(0xFFEEF2F5);
            for (int j = 0; j < rows[i].length; j++) {
                TextView cell = text(c, rows[i][j], i == 0 ? 11.5f : 12f, i == 0 ? MUTED : INK);
                if (i == 0) cell.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                cell.setPadding(dp(c, 3), 0, dp(c, 3), 0);
                float w = weights != null && j < weights.length ? weights[j] : 1f;
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w));
                r.addView(cell);
            }
            t.addView(r);
            if (i > 0) {
                View d = new View(c);
                d.setBackgroundColor(0xFFEDF0F3);
                d.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                t.addView(d);
            }
        }
        return t;
    }
}
