package org.emfvector.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String[] TABS = {"Live", "Calculator", "Vector", "Cases", "Uncertainty", "Framework"};
    private final View[] screens = new View[TABS.length];
    private final List<TextView> tabViews = new ArrayList<>();
    private FrameLayout content;
    private LiveScreen live;
    private int current = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Model.SETTINGS.load(getSharedPreferences("emf", 0));
        Window w = getWindow();
        w.setStatusBarColor(Ui.PRIMARY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PRIMARY); // fills status/nav bar areas under edge-to-edge (target 35+)
        root.setFitsSystemWindows(true);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Ui.PRIMARY);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));
        TextView title = Ui.text(this, "EMF Exposure Vector", 20, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bar.addView(title);
        bar.addView(Ui.text(this, "Frequency-aware dosimetry for the 5G/6G era", 12.5f, 0xCCFFFFFF));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TABS[i], 14, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { select(idx); } });
            tabs.addView(t);
            tabViews.add(t);
        }
        hs.addView(tabs);
        bar.addView(hs);
        root.addView(bar);

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.setBackgroundColor(Ui.BG);
        root.addView(content);
        setContentView(root);

        live = new LiveScreen(this);
        screens[0] = live.view();
        select(0);
        askPermissions();
    }

    private void select(int i) {
        if (i == current) return;
        if (screens[i] == null) {
            switch (i) {
                case 1: screens[i] = new CalcScreen(this).view(); break;
                case 2: screens[i] = new VectorScreen(this).view(); break;
                case 3: screens[i] = new CasesScreen(this).view(); break;
                case 4: screens[i] = new MonteCarloScreen(this).view(); break;
                case 5: screens[i] = new FrameworkScreen(this).view(); break;
                default:
            }
        }
        content.removeAllViews();
        content.addView(screens[i]);
        current = i;
        live.setVisible(i == 0);
        for (int k = 0; k < tabViews.size(); k++) {
            TextView t = tabViews.get(k);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.dp(this, 16));
            g.setColor(k == i ? Color.WHITE : 0x22FFFFFF);
            t.setBackground(g);
            t.setTextColor(k == i ? Ui.PRIMARY : Color.WHITE);
        }
    }

    private void askPermissions() {
        List<String> p = new ArrayList<>();
        p.add("android.permission.ACCESS_FINE_LOCATION");
        p.add("android.permission.ACCESS_COARSE_LOCATION");
        p.add("android.permission.READ_PHONE_STATE");
        if (Build.VERSION.SDK_INT >= 31) { p.add("android.permission.BLUETOOTH_SCAN"); p.add("android.permission.BLUETOOTH_CONNECT"); }
        if (Build.VERSION.SDK_INT >= 33) p.add("android.permission.NEARBY_WIFI_DEVICES");
        List<String> need = new ArrayList<>();
        for (String s : p) if (checkSelfPermission(s) != android.content.pm.PackageManager.PERMISSION_GRANTED) need.add(s);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        live.pause();
        live.resume();
    }

    @Override protected void onResume() { super.onResume(); live.resume(); }
    @Override protected void onPause() { super.onPause(); live.pause(); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == LiveScreen.REQ_EXPORT && res == RESULT_OK && data != null && data.getData() != null) live.onExport(data.getData());
    }
}
