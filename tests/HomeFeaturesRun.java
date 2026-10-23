package sa.urtv.home;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

public class HomeFeaturesRun {
    private static int count;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        count++;
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        Date utc = FixtureTime.parse("2026-10-23T21:30:00Z", "", "");
        check(utc != null, "UTC timestamp");
        check(utc.equals(FixtureTime.parse("2026-10-24T00:30:00+03:00", "", "")), "explicit offset");
        check(utc.equals(FixtureTime.parse("2026-10-23T21:30:00", "", "")), "zone-less API timestamp is UTC");
        check(utc.equals(FixtureTime.parse("", "2026-10-23", "21:30:00")), "date and time fallback");
        check(utc.equals(FixtureTime.parse("bad", "2026-10-23", "21:30")), "invalid timestamp fallback");
        check(utc.equals(FixtureTime.parse("2026-10-23T21:30:00.000Z", "", "")), "milliseconds");
        check(FixtureTime.parse("", "2026-10-23", "") == null, "unknown time is never midnight");
        check(FixtureTime.parse("", "2026-02-30", "20:00") == null, "invalid calendar date");
        check(FixtureTime.parse("2026-10-23T21:30:00Zjunk", "", "") == null, "reject trailing junk");
        check(FixtureTime.day("null") == null, "missing date");
        String shown = FixtureTime.display(utc, true);
        check(shown.contains("٢٤") && shown.contains("١٢:٣٠"), "Riyadh midnight rollover: " + shown);
        check(FixtureTime.display(FixtureTime.day("2026-10-23"), false).contains("الوقت غير محدد"), "TBD time label");
        long before = utc.getTime() - 1000;
        check(FixtureTime.upcoming("2026-10-23T21:30:00Z", "", "", "NS", before), "future match");
        check(!FixtureTime.upcoming("2026-10-23T21:30:00Z", "", "", "NS", utc.getTime()), "started match excluded");
        for (String status : new String[]{"FT", "AET", "PEN", "Match Finished", "Postponed", "Cancelled", "Abandoned"})
            check(!FixtureTime.upcoming("2026-10-23T21:30:00Z", "", "", status, before), status + " excluded");
        check(FixtureTime.upcoming("", "2026-10-24", "", "", before), "future fixture with unknown time");
        check(!FixtureTime.upcoming("", "2026-10-20", "", "", before), "expired date-only fixture");
        check(!FixtureTime.upcoming("", "invalid", "", "", before), "invalid fixture");
        Map<String, String> apps = new LinkedHashMap<>();
        apps.put("tv.vendor.player", "IPTV Smarters Pro");
        apps.put("com.google.android.youtube.tv", "YouTube");
        apps.put("org.smarttube", "SmartTube");
        check("tv.vendor.player".equals(AppIdentity.resolve("smarters", "", apps)), "Smarters found by label with unknown package");
        check("com.google.android.youtube.tv".equals(AppIdentity.resolve("youtube", "", apps)), "official YouTube");
        apps.put("com.smarters.variant", "Player");
        check(AppIdentity.resolve("smarters", "", apps) == null, "ambiguous variants need user choice");
        check("tv.vendor.player".equals(AppIdentity.resolve("smarters", "tv.vendor.player", apps)), "explicit variant wins");
        apps.remove("tv.vendor.player");
        check("com.smarters.variant".equals(AppIdentity.resolve("smarters", "tv.vendor.player", apps)), "removed saved app rediscovered");
        apps.remove("com.google.android.youtube.tv");
        check(AppIdentity.resolve("youtube", "", apps) == null, "no implicit SmartTube replacement");
        check(!AppIdentity.matches("youtube", "com.google.android.apps.youtube.music", "YouTube Music"), "exclude YouTube Music");
        apps.clear();
        check(AppIdentity.resolve("smarters", "missing", apps) == null, "no installed app");
        System.out.println("PASS " + count + " home feature checks");
    }
}
