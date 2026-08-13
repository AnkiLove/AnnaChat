package dev.annachat.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static long parseMillis(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("permanent")
                || input.equals("永久") || input.equalsIgnoreCase("forever")) {
            return 0;
        }
        Matcher matcher = PART.matcher(input.toLowerCase(Locale.ROOT));
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("无效时长");
            long amount = Long.parseLong(matcher.group(1));
            total = Math.addExact(total, switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount).toMillis();
                case "m" -> Duration.ofMinutes(amount).toMillis();
                case "h" -> Duration.ofHours(amount).toMillis();
                case "d" -> Duration.ofDays(amount).toMillis();
                case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7)).toMillis();
                default -> throw new IllegalArgumentException("无效时长单位");
            });
            end = matcher.end();
        }
        if (end != input.length() || total <= 0) throw new IllegalArgumentException("无效时长");
        return total;
    }

    public static String format(long millis) {
        if (millis <= 0) return "0秒";
        long seconds = (millis + 999) / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder output = new StringBuilder();
        if (days > 0) output.append(days).append("天");
        if (hours > 0) output.append(hours).append("时");
        if (minutes > 0) output.append(minutes).append("分");
        if (seconds > 0 || output.isEmpty()) output.append(seconds).append("秒");
        return output.toString();
    }
}
