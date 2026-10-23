package sa.urtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Framework widgets provide D-pad/OK focus and back navigation on API 24. */
public class HomeSettingsActivity extends Activity {
    private static final int PICK_IMAGES = 41;
    private TextView summary;
    private boolean importing;
    private Button choose, reset;

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
        choose = button(column, "اختيار صورة أو عدة صور من التلفزيون (حتى ٢٠ صورة)", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivityForResult(intent, PICK_IMAGES); }
            catch (RuntimeException e) { toast("لا يوجد منتقي ملفات؛ ثبّت مدير ملفات يدعم اختيار الصور بالريموت"); }
        });
        button(column, "مدة تغيير الخلفية", () -> {
            final long[] minutes = {1, 5, 10, 15, 30, 60};
            String[] labels = {"دقيقة", "٥ دقائق", "١٠ دقائق", "١٥ دقيقة", "٣٠ دقيقة", "٦٠ دقيقة"};
            long saved = Dock.prefs(this).getLong("wallpaperMinutes", 10); int checked = 2;
            for (int i = 0; i < minutes.length; i++) if (saved == minutes[i]) checked = i;
            new AlertDialog.Builder(this).setTitle("مدة تغيير الخلفية").setSingleChoiceItems(labels, checked, (d, which) -> {
                Dock.prefs(this).edit().putLong("wallpaperMinutes", minutes[which])
                    .putLong("wallpaperRevision", System.currentTimeMillis()).apply();
                d.dismiss(); refresh();
            }).setNegativeButton("رجوع", null).show();
        });
        reset = button(column, "استخدام خلفيات UrTV الافتراضية", () -> {
            if (!importing) { WallpaperStore.save(this, Collections.emptyList()); refresh(); }
        });
        for (String role : FeaturedApps.ROLES) button(column, "اختيار تطبيق " + FeaturedApps.label(role), () -> selectApp(role));
        button(column, "رجوع", this::finish);
        setContentView(scroll); refresh(); choose.requestFocus();
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
        StringBuilder s = new StringBuilder("الخلفيات المحلية: " + WallpaperStore.files(this).length
                + " · التبديل كل " + Dock.prefs(this).getLong("wallpaperMinutes", 10) + " دقيقة\n"
                + "صورة واحدة تبقى ثابتة. الحد الأقصى للصورة ٢٠ ميغابايت.\n");
        for (String role : FeaturedApps.ROLES) {
            String pkg = FeaturedApps.resolve(this, role);
            s.append(FeaturedApps.label(role)).append(": ").append(pkg == null ? "غير محدد؛ اختر التطبيق المثبت" : pkg).append('\n');
        }
        summary.setText(s);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGES || result != RESULT_OK || data == null || importing) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            if (data.getClipData().getItemCount() > 20) { toast("اختر حتى ٢٠ صورة في المرة الواحدة"); return; }
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return;
        importing = true; choose.setEnabled(false); reset.setEnabled(false); toast("جارٍ حفظ الصور…");
        new Thread(() -> {
            List<String> names = new ArrayList<>();
            for (Uri uri : uris) {
                try { names.add(WallpaperStore.importImage(getApplicationContext(), uri)); }
                catch (Exception ignored) { }
            }
            if (!names.isEmpty()) WallpaperStore.save(getApplicationContext(), names);
            runOnUiThread(() -> {
                importing = false;
                if (isDestroyed()) return;
                choose.setEnabled(true); reset.setEnabled(true); refresh();
                toast(names.isEmpty() ? "تعذر قراءة الصور؛ بقيت الخلفيات السابقة" : "تم حفظ " + names.size() + " من " + uris.size() + " صورة");
            });
        }, "urtv-import").start();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
