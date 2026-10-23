package sa.urtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Framework widgets provide D-pad/OK focus and back navigation on API 24. */
public class HomeSettingsActivity extends Activity {
    private TextView summary;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF071810);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(64, 24, 64, 40);
        scroll.addView(column);
        TextView title = new TextView(this);
        title.setText("تخصيص الشاشة الرئيسية"); title.setTextSize(26); title.setTextColor(0xFFD6AF6B);
        title.setGravity(Gravity.RIGHT); column.addView(title);
        summary = new TextView(this); summary.setTextColor(Color.WHITE); summary.setTextSize(16); column.addView(summary);
        for (String role : FeaturedApps.ROLES) button(column, "اختيار تطبيق " + FeaturedApps.label(role), () -> selectApp(role));
        button(column, "رجوع", this::finish);
        setContentView(scroll); refresh();
    }

    private Button button(LinearLayout col, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(18);
        col.addView(b, new LinearLayout.LayoutParams(-1, -2)); b.setOnClickListener(v -> action.run()); return b;
    }

    private void selectApp(String role) {
        Map<String, String> apps = FeaturedApps.installed(this);
        List<String> pkgs = new ArrayList<>(apps.keySet());
        Collections.sort(pkgs, (a, b) -> {
            int match = Boolean.compare(FeaturedApps.matches(role, b, apps.get(b)), FeaturedApps.matches(role, a, apps.get(a)));
            return match != 0 ? match : apps.get(a).compareToIgnoreCase(apps.get(b));
        });
        String[] labels = new String[pkgs.size() + 1]; labels[0] = "اكتشاف تلقائي";
        for (int i = 0; i < pkgs.size(); i++) labels[i + 1] = apps.get(pkgs.get(i)) + " · " + pkgs.get(i);
        new AlertDialog.Builder(this).setTitle("اختر " + FeaturedApps.label(role)).setItems(labels, (d, which) -> {
            Dock.prefs(this).edit().putString("featured_" + role, which == 0 ? "" : pkgs.get(which - 1)).apply(); refresh();
        }).setNegativeButton("رجوع", null).show();
    }

    private void refresh() {
        StringBuilder s = new StringBuilder("عرض الصور المضمنة يتغير كل ١٠ ثوانٍ. الخلفية هي خلفية UrTV الأصلية.\n");
        for (String role : FeaturedApps.ROLES) {
            String pkg = FeaturedApps.resolve(this, role);
            s.append(FeaturedApps.label(role)).append(": ").append(pkg == null ? "غير محدد؛ اختر التطبيق المثبت" : pkg).append('\n');
        }
        summary.setText(s);
    }

}
