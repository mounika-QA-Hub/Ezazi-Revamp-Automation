package org.intelehealth.ezazi.automation.utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;

/**
 * Date/time helpers, including <b>relative tokens</b> so test data never goes stale.
 *
 * <table border="1">
 *   <caption>Supported tokens (case-insensitive)</caption>
 *   <tr><th>Token</th><th>Meaning</th></tr>
 *   <tr><td>TODAY, TODAY+3, TODAY-1, YESTERDAY, TOMORROW</td><td>date relative to today</td></tr>
 *   <tr><td>NOW, NOW+30m, NOW-2h, NOW-1d</td><td>date-time relative to now (truncated to minutes)</td></tr>
 *   <tr><td>TODAY-1 23:30</td><td>relative date + fixed time</td></tr>
 *   <tr><td>2026-01-31, 2026-01-31 08:15</td><td>ISO literal</td></tr>
 * </table>
 * Example: an Excel cell {@code TODAY+1 08:00} always means "tomorrow 08:00" whenever the suite runs.
 */
public final class DateTimeUtils {

    private static final Pattern DAY_TOKEN = Pattern.compile("(?i)^(TODAY|YESTERDAY|TOMORROW)\\s*([+-]\\s*\\d+)?$");
    private static final Pattern NOW_TOKEN = Pattern.compile("(?i)^NOW\\s*(?:([+-])\\s*(\\d+)\\s*([mhd]))?$");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter[] TIME_PATTERNS = {
            DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH), DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)};

    private DateTimeUtils() {
    }

    /**
     * Resolves a date token or ISO date ({@code yyyy-MM-dd}).
     *
     * @param token e.g. "TODAY-1"
     * @return resolved date, or {@code null} for a blank token
     */
    public static LocalDate resolveDate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = token.trim();
        Matcher m = DAY_TOKEN.matcher(value);
        if (m.matches()) {
            LocalDate base = switch (m.group(1).toUpperCase(Locale.ROOT)) {
                case "YESTERDAY" -> LocalDate.now().minusDays(1);
                case "TOMORROW" -> LocalDate.now().plusDays(1);
                default -> LocalDate.now();
            };
            return m.group(2) == null ? base : base.plusDays(Long.parseLong(m.group(2).replace(" ", "")));
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(value, appDateFormatter());
        }
    }

    /**
     * Resolves a time literal ({@code HH:mm}, {@code hh:mm a}) or a NOW token.
     *
     * @param token e.g. "23:30" or "NOW-15m"
     * @return resolved time, or {@code null} for a blank token
     */
    public static LocalTime resolveTime(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = token.trim();
        if (NOW_TOKEN.matcher(value).matches()) {
            return resolveDateTime(value).toLocalTime();
        }
        for (DateTimeFormatter f : TIME_PATTERNS) {
            try {
                return LocalTime.parse(value.toUpperCase(Locale.ENGLISH), f);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        throw new IllegalArgumentException("Unrecognised time '" + token + "'. Use HH:mm, hh:mm a or NOW+/-Nm.");
    }

    /**
     * Resolves a date-time token: "NOW-2h", "TODAY-1 23:30", "2026-01-31 08:15".
     *
     * @param token token
     * @return resolved date-time, or {@code null} for a blank token
     */
    public static LocalDateTime resolveDateTime(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = token.trim();
        Matcher now = NOW_TOKEN.matcher(value);
        if (now.matches()) {
            LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            if (now.group(1) == null) {
                return base;
            }
            long amount = Long.parseLong(now.group(2)) * ("-".equals(now.group(1)) ? -1 : 1);
            return switch (now.group(3).toLowerCase(Locale.ROOT)) {
                case "m" -> base.plusMinutes(amount);
                case "h" -> base.plusHours(amount);
                default -> base.plusDays(amount);
            };
        }
        int lastSpace = value.lastIndexOf(' ');
        if (lastSpace > 0 && value.substring(lastSpace + 1).contains(":")) {
            String datePart = value.substring(0, lastSpace).trim();
            String timePart = value.substring(lastSpace + 1).trim();
            // support "hh:mm AM" as well
            if (timePart.equalsIgnoreCase("AM") || timePart.equalsIgnoreCase("PM")) {
                int prev = datePart.lastIndexOf(' ');
                timePart = datePart.substring(prev + 1) + " " + timePart;
                datePart = datePart.substring(0, prev).trim();
            }
            return LocalDateTime.of(resolveDate(datePart), resolveTime(timePart));
        }
        return resolveDate(value).atStartOfDay();
    }

    /**
     * True when a time range wraps past midnight, e.g. 23:30 -> 00:30.
     *
     * @param start start time
     * @param end   end time
     * @return true if {@code end} is earlier in the day than {@code start}
     */
    public static boolean crossesMidnight(LocalTime start, LocalTime end) {
        return end.isBefore(start);
    }

    /**
     * Duration between two times assuming {@code end} is on the next day when it wraps midnight.
     *
     * @return non-negative duration (23:30 -> 00:30 = 1h)
     */
    public static Duration durationAcrossMidnight(LocalTime start, LocalTime end) {
        Duration d = Duration.between(start, end);
        return d.isNegative() ? d.plusDays(1) : d;
    }

    /** Formats a date using {@code app.date.format}. */
    public static String formatForApp(LocalDate date) {
        return date.format(appDateFormatter());
    }

    /** Formats a time using {@code app.time.format}. */
    public static String formatForApp(LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern(ConfigReader.get(ConfigKeys.APP_TIME_FORMAT, "hh:mm a"), Locale.ENGLISH));
    }

    /** @return formatter for {@code app.date.format} (default dd/MM/yyyy) */
    public static DateTimeFormatter appDateFormatter() {
        return DateTimeFormatter.ofPattern(ConfigReader.get(ConfigKeys.APP_DATE_FORMAT, "dd/MM/yyyy"), Locale.ENGLISH);
    }

    /** @return timestamp safe for file names, e.g. 20261102_221530_123 */
    public static String fileTimestamp() {
        return LocalDateTime.now().format(FILE_TS);
    }
}
