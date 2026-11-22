package org.emfvector.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** Minimal XY chart with optional log axes, reference lines and point markers. */
public class ChartView extends View {
    public static final class Series {
        double[] x, y; int color; String name; boolean dashed, points, bars;
        public Series(String n, double[] x, double[] y, int color) { name = n; this.x = x; this.y = y; this.color = color; }
        public Series dashed() { dashed = true; return this; }
        public Series points() { points = true; return this; }
        public Series bars() { bars = true; return this; }
    }

    private static final class HLine { double v; String label; int color; }

    private final List<Series> series = new ArrayList<>();
    private final List<HLine> hlines = new ArrayList<>();
    private final List<double[]> vmarks = new ArrayList<>();
    public boolean logX, logY;
    public String xLabel = "", yLabel = "";
    public double xMin = Double.NaN, xMax = Double.NaN, yMin = Double.NaN, yMax = Double.NaN;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public ChartView(Context c, int heightDp) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, heightDp)));
    }

    public ChartView clear() { series.clear(); hlines.clear(); vmarks.clear(); xMin = xMax = yMin = yMax = Double.NaN; return this; }
    public ChartView add(Series s) { series.add(s); return this; }
    public ChartView hline(double v, String label, int color) {
        HLine h = new HLine(); h.v = v; h.label = label; h.color = color; hlines.add(h); return this;
    }
    public ChartView vmark(double x, int color) { vmarks.add(new double[]{x, color}); return this; }
    public void refresh() { invalidate(); }

    private double tx(double v) { return logX ? Math.log10(Math.max(v, 1e-300)) : v; }
    private double ty(double v) { return logY ? Math.log10(Math.max(v, 1e-300)) : v; }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float left = 52 * d, right = w - 10 * d, top = 10 * d, bottom = h - 34 * d;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (Series s : series)
            for (int i = 0; i < s.x.length; i++) {
                if (Double.isNaN(s.y[i]) || (logY && s.y[i] <= 0) || (logX && s.x[i] <= 0)) continue;
                x0 = Math.min(x0, tx(s.x[i])); x1 = Math.max(x1, tx(s.x[i]));
                y0 = Math.min(y0, ty(s.y[i])); y1 = Math.max(y1, ty(s.y[i]));
            }
        for (HLine hl : hlines) if (!(logY && hl.v <= 0)) { y0 = Math.min(y0, ty(hl.v)); y1 = Math.max(y1, ty(hl.v)); }
        if (x0 == Double.MAX_VALUE) { x0 = 0; x1 = 1; }
        if (y0 == Double.MAX_VALUE) { y0 = logY ? -3 : 0; y1 = logY ? 0 : 1; }
        if (!Double.isNaN(xMin)) x0 = tx(xMin);
        if (!Double.isNaN(xMax)) x1 = tx(xMax);
        if (!Double.isNaN(yMin)) y0 = ty(yMin);
        if (!Double.isNaN(yMax)) y1 = ty(yMax);
        if (x0 >= x1) { x1 = x0 + 1; }
        if (y0 >= y1) { y1 = y0 + 1; }
        if (logY) { y0 = Math.floor(y0); y1 = Math.ceil(y1); }
        else { double pad = (y1 - y0) * 0.06; y1 += pad; if (y0 > 0 && y0 - pad < 0) y0 = 0; else y0 -= pad; }
        if (logX) { x0 = Math.floor(x0); x1 = Math.ceil(x1); }
        final double X0 = x0, X1 = x1, Y0 = y0, Y1 = y1;

        // grid and ticks
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setPathEffect(null);
        p.setTextSize(10 * d);
        int ny = logY ? (int) Math.min(8, Y1 - Y0) : 5;
        int stepY = logY && (Y1 - Y0) > 8 ? (int) Math.ceil((Y1 - Y0) / 8) : 1;
        for (int i = 0; i <= ny; i++) {
            double v = logY ? Y0 + i * stepY : Y0 + (Y1 - Y0) * i / ny;
            if (v > Y1 + 1e-9) break;
            float yy = (float) (bottom - (v - Y0) / (Y1 - Y0) * (bottom - top));
            p.setColor(0xFFE8ECF0);
            cv.drawLine(left, yy, right, yy, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.MUTED);
            String lab = logY ? tickLog(v) : Physics.fmt(v);
            cv.drawText(lab, left - p.measureText(lab) - 4 * d, yy + 3.5f * d, p);
            p.setStyle(Paint.Style.STROKE);
        }
        int nx = logX ? (int) Math.min(8, X1 - X0) : 5;
        for (int i = 0; i <= nx; i++) {
            double v = logX ? X0 + i * Math.max(1, Math.ceil((X1 - X0) / 8)) : X0 + (X1 - X0) * i / nx;
            if (v > X1 + 1e-9) break;
            float xx = (float) (left + (v - X0) / (X1 - X0) * (right - left));
            p.setColor(0xFFE8ECF0);
            cv.drawLine(xx, top, xx, bottom, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.MUTED);
            String lab = logX ? tickLog(v) : Physics.fmt(v);
            cv.drawText(lab, xx - p.measureText(lab) / 2, bottom + 13 * d, p);
            p.setStyle(Paint.Style.STROKE);
        }
        p.setColor(0xFFB8C2CC);
        cv.drawLine(left, bottom, right, bottom, p);
        cv.drawLine(left, top, left, bottom, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.MUTED);
        cv.drawText(xLabel, (left + right) / 2 - p.measureText(xLabel) / 2, h - 6 * d, p);
        cv.save();
        cv.rotate(-90);
        cv.drawText(yLabel, -(top + bottom) / 2 - p.measureText(yLabel) / 2, 11 * d, p);
        cv.restore();

        // reference lines
        for (HLine hl : hlines) {
            if (logY && hl.v <= 0) continue;
            float yy = (float) (bottom - (ty(hl.v) - Y0) / (Y1 - Y0) * (bottom - top));
            if (yy < top - 1 || yy > bottom + 1) continue;
            p.setStyle(Paint.Style.STROKE);
            p.setColor(hl.color);
            p.setStrokeWidth(1.4f * d);
            p.setPathEffect(new DashPathEffect(new float[]{6 * d, 4 * d}, 0));
            cv.drawLine(left, yy, right, yy, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
            cv.drawText(hl.label, right - p.measureText(hl.label) - 2 * d, yy - 3 * d, p);
        }
        for (double[] vm : vmarks) {
            float xx = (float) (left + (tx(vm[0]) - X0) / (X1 - X0) * (right - left));
            p.setStyle(Paint.Style.STROKE);
            p.setColor((int) vm[1]);
            p.setStrokeWidth(1.2f * d);
            p.setPathEffect(new DashPathEffect(new float[]{3 * d, 3 * d}, 0));
            cv.drawLine(xx, top, xx, bottom, p);
            p.setPathEffect(null);
        }

        // series
        int legendY = 0;
        for (Series s : series) {
            p.setColor(s.color);
            p.setStrokeWidth(2.2f * d);
            p.setPathEffect(s.dashed ? new DashPathEffect(new float[]{8 * d, 5 * d}, 0) : null);
            if (s.bars) {
                p.setStyle(Paint.Style.FILL);
                float bw = (right - left) / Math.max(1, s.x.length) * 0.92f;
                for (int i = 0; i < s.x.length; i++) {
                    float xx = (float) (left + (tx(s.x[i]) - X0) / (X1 - X0) * (right - left));
                    float yy = (float) (bottom - (ty(s.y[i]) - Y0) / (Y1 - Y0) * (bottom - top));
                    cv.drawRect(xx - bw / 2, Math.min(yy, bottom), xx + bw / 2, bottom, p);
                }
            } else {
                Path path = new Path();
                boolean started = false;
                for (int i = 0; i < s.x.length; i++) {
                    if (Double.isNaN(s.y[i]) || (logY && s.y[i] <= 0)) { started = false; continue; }
                    float xx = (float) (left + (tx(s.x[i]) - X0) / (X1 - X0) * (right - left));
                    float yy = (float) (bottom - (ty(s.y[i]) - Y0) / (Y1 - Y0) * (bottom - top));
                    if (s.points) {
                        p.setStyle(Paint.Style.FILL);
                        cv.drawCircle(xx, yy, 4.5f * d, p);
                    } else if (!started) { path.moveTo(xx, yy); started = true; }
                    else path.lineTo(xx, yy);
                }
                if (!s.points) {
                    p.setStyle(Paint.Style.STROKE);
                    cv.save();
                    cv.clipRect(left, top, right, bottom);
                    cv.drawPath(path, p);
                    cv.restore();
                }
            }
            p.setPathEffect(null);
            if (s.name != null && !s.name.isEmpty()) {
                p.setStyle(Paint.Style.FILL);
                float ly = top + 6 * d + legendY * 13 * d;
                cv.drawRect(left + 8 * d, ly - 3 * d, left + 20 * d, ly + 1 * d, p);
                p.setColor(Ui.INK);
                cv.drawText(s.name, left + 24 * d, ly + 2 * d, p);
                legendY++;
            }
        }
    }

    private static String tickLog(double e) {
        int k = (int) Math.round(e);
        if (k >= -2 && k <= 3) return Physics.fmt(Math.pow(10, k));
        return "1e" + k;
    }
}
