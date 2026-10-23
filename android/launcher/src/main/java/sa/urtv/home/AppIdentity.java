package sa.urtv.home;

import java.util.Locale;
import java.util.Map;

/** Pure selection rules: ambiguous variants require an explicit user choice. */
final class AppIdentity {
    static String resolve(String role, String saved, Map<String, String> installed) {
        if (installed.containsKey(saved)) return saved;
        String match = null;
        for (Map.Entry<String, String> entry : installed.entrySet()) {
            if (!matches(role, entry.getKey(), entry.getValue())) continue;
            if (match != null) return null;
            match = entry.getKey();
        }
        return match;
    }
    static boolean matches(String role, String pkg, String label) {
        String name = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String id = pkg.toLowerCase(Locale.ROOT);
        if ("smarters".equals(role)) return name.contains("smarters") || id.contains("smarters");
        return (name.equals("youtube") || name.equals("youtubetv") || id.equals("com.google.android.youtube.tv")
                || id.equals("com.google.android.youtube")) && !id.contains("music");
    }
}
