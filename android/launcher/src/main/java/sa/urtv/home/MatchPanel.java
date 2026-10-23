package sa.urtv.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent of the optional screensaver card, with per-team offline cache and bounded downloads. */
final class MatchPanel {
    private static final String BASE = "https://www.thesportsdb.com/api/v1/json/123/";
    private static final String[] IDS = {"136013", "133667"}, NAMES = {"الهلال", "AC Milan"};
    private static final long REFRESH = 30 * 60_000L;
    private final Activity activity;
    private final LinearLayout root;
    private final LinearLayout[] cards = new LinearLayout[2];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> badges = new HashMap<>();
    private final boolean[] failed = new boolean[2];
    private boolean active, busy;
    private volatile boolean destroyed;
    private boolean restored;
    private int page;
    private long lastAttempt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            page++; render();
            if (System.currentTimeMillis() - lastAttempt >= REFRESH) refresh();
            handler.postDelayed(this, 10_000);
        }
    };

    MatchPanel(Activity activity) {
        this.activity = activity;
        root = new LinearLayout(activity); root.setOrientation(LinearLayout.HORIZONTAL);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (int i = 0; i < cards.length; i++) {
            cards[i] = new LinearLayout(activity); cards[i].setGravity(Gravity.CENTER_VERTICAL);
            cards[i].setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(0xBB071810); bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), 0x66D6AF6B); cards[i].setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.setMargins(i == 0 ? 0 : dp(8), 0, i == 0 ? dp(8) : 0, 0); root.addView(cards[i], p);
        }
        render();
    }
    View view() { return root; }
    void resume() {
        active = true; render();
        if (!restored) {
            restored = true;
            worker.execute(() -> {
                for (int i = 0; i < IDS.length && !destroyed; i++) for (JSONObject event : fixtures(i)) {
                    for (String field : new String[]{"idHomeTeam", "idAwayTeam"}) {
                        String id = event.optString(field);
                        if (!id.matches("[0-9]+")) continue;
                        Bitmap bitmap = BitmapFactory.decodeFile(new File(activity.getCacheDir(), "team-badges/" + id + ".png").getPath());
                        if (bitmap != null) handler.post(() -> { if (!destroyed) { badges.put(id, bitmap); if (active) render(); } });
                    }
                }
            });
        }
        boolean stale = false;
        for (String id : IDS) stale |= System.currentTimeMillis() - Dock.prefs(activity).getLong("fixturesAt_" + id, 0) >= REFRESH;
        if (stale && System.currentTimeMillis() - lastAttempt > 60_000) refresh();
        handler.removeCallbacks(tick); handler.postDelayed(tick, 10_000);
    }
    void pause() { active = false; handler.removeCallbacks(tick); }
    void destroy() { destroyed = true; pause(); worker.shutdownNow(); }

    private List<JSONObject> fixtures(int team) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray events = new JSONArray(Dock.prefs(activity).getString("fixtures_" + IDS[team], "[]"));
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                if (!IDS[team].equals(e.optString("idHomeTeam")) && !IDS[team].equals(e.optString("idAwayTeam"))) continue;
                if (!FixtureTime.upcoming(e.optString("strTimestamp"), e.optString("dateEvent"),
                        e.optString("strTime"), e.optString("strStatus"), System.currentTimeMillis())) continue;
                out.add(e);
            }
        } catch (Exception ignored) { }
        java.util.Collections.sort(out, (a, b) -> Long.compare(sortTime(a), sortTime(b)));
        return out;
    }
    private static Date kickoff(JSONObject e) {
        return FixtureTime.parse(e.optString("strTimestamp"), e.optString("dateEvent"), e.optString("strTime"));
    }
    private static long sortTime(JSONObject e) {
        Date d = kickoff(e); if (d == null) d = FixtureTime.day(e.optString("dateEvent")); return d == null ? Long.MAX_VALUE : d.getTime();
    }
    private void render() {
        if (destroyed) return;
        for (int i = 0; i < cards.length; i++) {
            LinearLayout card = cards[i]; card.removeAllViews();
            List<JSONObject> events = fixtures(i);
            JSONObject event = events.isEmpty() ? null : events.get(page % Math.min(events.size(), 3));
            String home = event == null ? "" : event.optString("idHomeTeam");
            String away = event == null ? "" : event.optString("idAwayTeam");
            addBadge(card, home, event == null ? NAMES[i] : event.optString("strHomeTeam"));
            LinearLayout text = new LinearLayout(activity); text.setOrientation(LinearLayout.VERTICAL); text.setGravity(Gravity.CENTER);
            card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            line(text, NAMES[i] + " · المباريات القادمة", 13, 0xFFD6AF6B);
            if (event == null) {
                line(text, "لا تتوفر مباريات قادمة مؤكدة", 14, Color.WHITE);
            } else {
                line(text, event.optString("strHomeTeam") + " × " + event.optString("strAwayTeam"), 14, Color.WHITE);
                line(text, event.optString("strLeague", "البطولة غير محددة"), 12, 0xFFE1D9C8);
                Date start = kickoff(event), day = FixtureTime.day(event.optString("dateEvent"));
                line(text, start != null ? FixtureTime.display(start, true) : day != null ? FixtureTime.display(day, false) : "الموعد غير محدد", 12, Color.WHITE);
            }
            long at = Dock.prefs(activity).getLong("fixturesAt_" + IDS[i], 0);
            boolean stale = at > 0 && System.currentTimeMillis() - at >= REFRESH;
            String status = failed[i] ? "تعذر التحديث" : stale ? "بيانات محفوظة" : "";
            if (at > 0) {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d/M h:mm a", new Locale("ar", "SA"));
                f.setTimeZone(FixtureTime.RIYADH); status += " · آخر تحديث " + f.format(new Date(at));
            }
            line(text, "TheSportsDB" + (status.isEmpty() ? "" : " · " + status), 10, 0xFFB6B6AA);
            addBadge(card, away, event == null ? "" : event.optString("strAwayTeam"));
        }
    }
    private void line(LinearLayout parent, String value, int size, int color) {
        TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }
    private void addBadge(LinearLayout parent, String id, String label) {
        ImageView image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(label); image.setImageBitmap(badges.get(id));
        if (!badges.containsKey(id)) image.setImageResource(R.drawable.ic_input);
        parent.addView(image, new LinearLayout.LayoutParams(dp(38), dp(44)));
    }
    private void refresh() {
        if (busy || destroyed) return;
        busy = true; lastAttempt = System.currentTimeMillis();
        worker.execute(() -> {
            for (int i = 0; i < IDS.length && !destroyed; i++) {
                final int team = i;
                try {
                    JSONObject response = new JSONObject(new String(download(BASE + "eventsnext.php?id=" + IDS[i], 2 * 1024 * 1024), "UTF-8"));
                    if (!response.has("events")) throw new java.io.IOException("missing events");
                    JSONArray events = response.optJSONArray("events");
                    if (!response.isNull("events") && events == null) throw new java.io.IOException("invalid events");
                    Dock.prefs(activity).edit().putString("fixtures_" + IDS[i], events == null ? "[]" : events.toString())
                        .putLong("fixturesAt_" + IDS[i], System.currentTimeMillis()).apply();
                    handler.post(() -> { failed[team] = false; if (active) render(); });
                } catch (Exception e) { handler.post(() -> { failed[team] = true; if (active) render(); }); }
            }
            // Cache badges for the nearest three fixtures of each club. No network or disk work on the UI thread.
            for (int i = 0; i < IDS.length && !destroyed; i++) {
                List<JSONObject> events = fixtures(i);
                for (int j = 0; j < Math.min(3, events.size()) && !destroyed; j++) {
                    for (String field : new String[]{"idHomeTeam", "idAwayTeam"}) {
                        String id = events.get(j).optString(field);
                        if (id.matches("[0-9]+")) loadBadge(id);
                    }
                }
            }
            handler.post(() -> { busy = false; if (active) render(); });
        });
    }
    private void loadBadge(String id) {
        try {
            File dir = new File(activity.getCacheDir(), "team-badges");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File file = new File(dir, id + ".png");
            Bitmap bitmap = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
            if (bitmap == null || System.currentTimeMillis() - file.lastModified() > 7 * 86400_000L) {
                JSONArray teams = new JSONObject(new String(download(BASE + "lookupteam.php?id=" + id, 1024 * 1024), "UTF-8")).getJSONArray("teams");
                JSONObject team = teams.getJSONObject(0);
                String badge = team.optString("strBadge"); if (badge.isEmpty() || badge.equals("null")) badge = team.optString("strTeamBadge");
                URL url = new URL(badge);
                if (!url.getProtocol().equals("https") || !(url.getHost().equals("thesportsdb.com") || url.getHost().endsWith(".thesportsdb.com"))) return;
                byte[] data = download(badge, 2 * 1024 * 1024);
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o); o.inSampleSize = 1;
                if (o.outWidth < 1 || o.outHeight < 1) return;
                while (o.outWidth / o.inSampleSize > 128 || o.outHeight / o.inSampleSize > 128) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false; bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (bitmap != null) try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
            }
            final Bitmap result = bitmap;
            if (result != null) handler.post(() -> { if (!destroyed) { badges.put(id, result); if (active) render(); } });
        } catch (Exception ignored) { }
    }
    private static byte[] download(String url, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { if (out.size() + n > limit) throw new java.io.IOException("response too large"); out.write(b, 0, n); }
                return out.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
