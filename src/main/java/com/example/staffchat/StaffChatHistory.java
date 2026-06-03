package com.example.staffchat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * Holds intercepted staff messages plus a client-tick clock used to expire old messages from
 * the HUD. Each entry also keeps a wall-clock timestamp for the timestamp prefix.
 * Accessed from the client (render/tick) thread.
 */
public final class StaffChatHistory {

    public record Entry(Component component, long tick, long epochMillis) {
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static long currentTick = 0L;

    private StaffChatHistory() {
    }

    public static void add(Component message) {
        ENTRIES.addLast(new Entry(message, currentTick, System.currentTimeMillis()));
        int cap = StaffChatConfig.get().enabled ? StaffChatConfig.get().maxLines : 100;
        // Keep a generous buffer regardless of maxLines so the scrollable log has history.
        int keep = Math.max(200, cap);
        while (ENTRIES.size() > keep) {
            ENTRIES.removeFirst();
        }
    }

    public static void tick() {
        currentTick++;
    }

    public static long currentTick() {
        return currentTick;
    }

    /** Snapshot in insertion order (oldest first). */
    public static List<Entry> snapshot() {
        return new ArrayList<>(ENTRIES);
    }

    /** Snapshot filtered by a case-insensitive substring of the plain message text. */
    public static List<Entry> search(String query) {
        List<Entry> all = snapshot();
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (e.component().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
