package sa.urtv.home;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** API 24 compatible UTC parsing. Never turn an unspecified kick-off into midnight. */
final class FixtureTime {
    static final TimeZone RIYADH = TimeZone.getTimeZone("Asia/Riyadh");
    static Date parse(String timestamp, String date, String time) {
        if (timestamp != null && !timestamp.isEmpty() && !timestamp.equals("null")) {
            for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mmXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm"}) {
                Date d = parseExact(timestamp, pattern); if (d != null) return d;
            }
        }
        if (time == null || time.isEmpty() || time.equals("null")) return null;
        return parseExact(date + "T" + time, time.length() == 5 ? "yyyy-MM-dd'T'HH:mm" : "yyyy-MM-dd'T'HH:mm:ss");
    }
    static boolean upcoming(String timestamp, String date, String time, String status, long now) {
        if (status.toUpperCase(Locale.ROOT).matches(".*(CANCEL|FINISH|POSTPON|ABANDON).*|FT|AET|PEN")) return false;
        Date start = parse(timestamp, date, time), day = day(date);
        return start != null ? start.getTime() > now : day != null && day.getTime() + 86400_000L > now;
    }
    static Date day(String date) { return parseExact(date, "yyyy-MM-dd"); }
    private static Date parseExact(String value, String pattern) {
        if (value == null || value.isEmpty()) return null;
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC")); f.setLenient(false);
        ParsePosition p = new ParsePosition(0); Date d = f.parse(value, p);
        return d != null && p.getIndex() == value.length() ? d : null;
    }
    static String display(Date date, boolean knownTime) {
        SimpleDateFormat f = new SimpleDateFormat(knownTime ? "EEE d MMM · h:mm a" : "EEE d MMM", new Locale("ar", "SA"));
        f.setTimeZone(knownTime ? RIYADH : TimeZone.getTimeZone("UTC"));
        return f.format(date) + (knownTime ? " · السعودية" : " · الوقت غير محدد");
    }
}
