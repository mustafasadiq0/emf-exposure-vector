package org.emfvector.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the normalized exposure vector R = [R_1 ... R_N] (Eq. 10) as one bar per component on a
 * logarithmic axis, coloured by physical quantity. Components are never summed across quantities.
 */
public class VectorView extends View {
    private final List<Model.Component> items = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private static final float ROW = 34;

    public VectorView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 60)));
    }

    public void set(List<Model.Component> cs) {
        items.clear();
        for (Model.Component c : cs) if (!Double.isNaN(c.ratio())) items.add(c);
        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = (int) ((items.size() * ROW + 34) * d);
        setLayoutParams(lp);
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth();
        float left = 8 * d, right = w - 56 * d;
        double lo = -8, hi = 1; // 1e-8 .. 10  (100 % at 0)
        for (Model.Component c : items) {
            double r = c.ratio();
            if (r > 0) { lo = Math.min(lo, Math.floor(Math.log10(r))); hi = Math.max(hi, Math.ceil(Math.log10(r)) + 0.3); }
        }
        lo = Math.max(lo, -14);
        p.setTextSize(10 * d);
        // decade grid
        for (int e = (int) lo; e <= (int) Math.floor(hi); e++) {
            float x = (float) (left + (e - lo) / (hi - lo) * (right - left));
            p.setColor(0xFFEDF0F3);
            p.setStrokeWidth(1);
            cv.drawLine(x, 0, x, items.size() * ROW * d, p);
            if ((e - (int) lo) % 2 == 0 || e == 0) {
                p.setColor(Ui.MUTED);
                String lab = e == 0 ? "100%" : (e == -2 ? "1%" : "1e" + (e + 2) + "%");
                if (e == -1) lab = "10%";
                if (e == 1) lab = "1000%";
                cv.drawText(lab, x - p.measureText(lab) / 2, items.size() * ROW * d + 14 * d, p);
            }
        }
        float x100 = (float) (left + (0 - lo) / (hi - lo) * (right - left));
        p.setColor(Ui.C_BAD);
        p.setStrokeWidth(1.5f * d);
        p.setPathEffect(new DashPathEffect(new float[]{5 * d, 4 * d}, 0));
        cv.drawLine(x100, 0, x100, items.size() * ROW * d, p);
        p.setPathEffect(null);
        for (int i = 0; i < items.size(); i++) {
            Model.Component c = items.get(i);
            float y = i * ROW * d;
            double r = c.ratio();
            float xr = r > 0 ? (float) (left + (Math.log10(r) - lo) / (hi - lo) * (right - left)) : left;
            xr = Math.max(left + 2 * d, Math.min(xr, w - 2 * d));
            p.setColor((Ui.quantityColor(c.quantity) & 0x00FFFFFF) | 0x33000000);
            cv.drawRect(left, y + 15 * d, right, y + 27 * d, p);
            p.setColor(Ui.quantityColor(c.quantity));
            cv.drawRect(left, y + 15 * d, Math.min(xr, right), y + 27 * d, p);
            p.setColor(Ui.INK);
            p.setTextSize(11 * d);
            String lab = c.source;
            if (p.measureText(lab) > right - left) lab = lab.substring(0, Math.max(1, (int) (lab.length() * (right - left) / p.measureText(lab)) - 1)) + "…";
            cv.drawText(lab, left, y + 12 * d, p);
            p.setColor(Ui.ratioColor(r));
            p.setTextSize(11.5f * d);
            String pc = Physics.pct(r);
            cv.drawText(pc, w - p.measureText(pc) - 2 * d, y + 25 * d, p);
        }
    }
}
