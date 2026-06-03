package com.example.staffchat;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * Decides whether an incoming chat {@link Component} is a StaffChat message.
 *
 * <p>The server's staff header is a gradient "Staff" built from {@code &#RRGGBB} codes. Bukkit's
 * {@code translateAlternateColorCodes('&', ...)} only converts single-letter codes (so {@code &l}
 * becomes bold) and leaves {@code &#hex} as literal text — so it arrives verbatim on the client
 * and makes a stable fingerprint. Signatures are read from {@link StaffChatConfig}.
 */
public final class StaffChatDetector {

    private StaffChatDetector() {
    }

    public static boolean isStaffChat(Component message) {
        if (message == null) {
            return false;
        }
        StaffChatConfig cfg = StaffChatConfig.get();

        String plain = message.getString().toUpperCase(Locale.ROOT);
        for (String sig : cfg.textSignatures) {
            if (sig == null || sig.isEmpty()) {
                continue;
            }
            String up = sig.toUpperCase(Locale.ROOT);
            if (plain.contains(up)) {
                return true;
            }
            // Tolerate a stripped leading '&' (e.g. "#22C2D8").
            if (up.startsWith("&") && plain.contains(up.substring(1))) {
                return true;
            }
        }

        return hasSignatureColor(message, cfg);
    }

    private static boolean hasSignatureColor(Component component, StaffChatConfig cfg) {
        TextColor color = component.getStyle().getColor();
        if (color != null) {
            int rgb = color.getValue() & 0xFFFFFF;
            for (int sig : cfg.colorSignatures) {
                if (rgb == sig) {
                    return true;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (hasSignatureColor(sibling, cfg)) {
                return true;
            }
        }
        return false;
    }
}
