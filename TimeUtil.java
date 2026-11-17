package com.yourserver.giveaway;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhd])");

    private TimeUtil() {
    }

    /**
     * Parses a duration string like "30s", "5m", "1h30m", "2d" into total seconds.
     * Returns -1 if the string could not be parsed at all.
     */
    public static long parseSeconds(String input) {
        if (input == null || input.isEmpty()) {
            return -1;
        }
        Matcher matcher = PART.matcher(input.toLowerCase());
        long total = 0;
        boolean matchedAny = false;
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                // there was junk between two valid parts, e.g. "30x5m"
                return -1;
            }
            matchedAny = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "s" -> total += value;
                case "m" -> total += value * 60L;
                case "h" -> total += value * 3600L;
                case "d" -> total += value * 86400L;
                default -> {
                    return -1;
                }
            }
            lastEnd = matcher.end();
        }
        if (!matchedAny || lastEnd != input.length()) {
            return -1;
        }
        return total;
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long d = seconds / 86400;
        seconds %= 86400;
        long h = seconds / 3600;
        seconds %= 3600;
        long m = seconds / 60;
        seconds %= 60;
        long s = seconds;

        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
