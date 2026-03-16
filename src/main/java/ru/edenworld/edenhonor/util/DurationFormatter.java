package ru.edenworld.edenhonor.util;

public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String formatMillis(long millis) {
        if (millis <= 0L) {
            return "0м";
        }

        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("д ");
        }
        if (hours > 0) {
            builder.append(hours).append("ч ");
        }
        if (minutes > 0 || builder.isEmpty()) {
            builder.append(minutes).append("м");
        }

        return builder.toString().trim();
    }
}
