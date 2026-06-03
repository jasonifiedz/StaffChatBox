package com.example.staffchat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Timestamp formatting for staff messages. The pattern is a standard
 * {@link java.time.format.DateTimeFormatter} pattern, editable in the settings screen.
 */
public final class StaffChatFormat {

    private static String cachedPattern;
    private static DateTimeFormatter cachedFormatter;

    private StaffChatFormat() {
    }

    public static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        try {
            DateTimeFormatter.ofPattern(pattern);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String formatTime(long epochMillis, String pattern) {
        try {
            return formatter(pattern).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            return "";
        }
    }

    private static DateTimeFormatter formatter(String pattern) {
        if (cachedFormatter == null || !pattern.equals(cachedPattern)) {
            DateTimeFormatter f;
            try {
                f = DateTimeFormatter.ofPattern(pattern);
            } catch (Exception e) {
                f = DateTimeFormatter.ofPattern("HH:mm:ss");
            }
            cachedFormatter = f;
            cachedPattern = pattern;
        }
        return cachedFormatter;
    }

    /** Returns the message component, optionally prefixed with a formatted timestamp. */
    public static Component withTimestamp(StaffChatHistory.Entry entry) {
        StaffChatConfig cfg = StaffChatConfig.get();
        if (!cfg.showTimestamps) {
            return entry.component();
        }
        String ts = formatTime(entry.epochMillis(), cfg.timestampFormat);
        if (ts.isEmpty()) {
            return entry.component();
        }
        MutableComponent prefix = Component.literal("[" + ts + "] ")
                .withStyle(s -> s.withColor(cfg.timestampColor));
        return prefix.append(entry.component());
    }
}
