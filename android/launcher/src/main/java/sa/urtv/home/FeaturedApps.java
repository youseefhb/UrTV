package sa.urtv.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolve installed launch activities, never assume a Smarters package name. */
final class FeaturedApps {
    static final String[] ROLES = {"smarters", "youtube"};
    static String label(String role) { return "smarters".equals(role) ? "IPTV Smarters" : "YouTube"; }

    static Map<String, String> installed(Context c) {
        Map<String, String> apps = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (!ri.activityInfo.packageName.equals(c.getPackageName()))
                    apps.put(ri.activityInfo.packageName, String.valueOf(ri.loadLabel(c.getPackageManager())));
            }
        }
        return apps;
    }

    static String resolve(Context c, String role) {
        Map<String, String> apps = installed(c);
        String saved = Dock.prefs(c).getString("featured_" + role, "");
        return AppIdentity.resolve(role, saved, apps);
    }

    static boolean matches(String role, String pkg, String label) {
        return AppIdentity.matches(role, pkg, label);
    }
}
