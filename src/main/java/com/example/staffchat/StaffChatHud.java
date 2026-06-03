package com.example.staffchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws the separate staff-chat box. {@link #drawBox} is the shared renderer used by both the
 * live in-game HUD and the settings-screen preview, so what you see while configuring matches
 * exactly what you get in game.
 */
public final class StaffChatHud {

    public static final int PADDING = 2;

    private StaffChatHud() {
    }

    /** Called every frame from the HUD render callback. */
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        StaffChatConfig cfg = StaffChatConfig.get();

        if (!cfg.enabled || mc.options.hideGui || mc.level == null) {
            return;
        }
        // The chat screen shows the interactive staff panel, and the settings screen shows its
        // own preview — don't double up the passive box on top of those.
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen
                || mc.screen instanceof StaffChatSettingsScreen) {
            return;
        }

        boolean screenOpen = mc.screen != null;
        long now = StaffChatHistory.currentTick();

        List<StaffChatHistory.Entry> entries = StaffChatHistory.snapshot();
        if (entries.isEmpty()) {
            return;
        }
        // Hide the whole box once the most recent message has aged out (keeps the box's contents
        // identical to the chat-screen panel whenever it IS shown, so nothing jumps on open).
        long newestAge = now - entries.get(entries.size() - 1).tick();
        if (!screenOpen && newestAge > cfg.fadeTicks) {
            return;
        }

        List<Component> messages = new ArrayList<>();
        for (StaffChatHistory.Entry entry : entries) {
            messages.add(StaffChatFormat.withTimestamp(entry));
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = clamp(cfg.posX, 0, Math.max(0, screenW - 20));
        int y = clamp(cfg.posY, 0, Math.max(0, screenH - 20));

        drawBox(graphics, x, y, cfg.scale, cfg.width, cfg.maxLines, cfg.bgOpacity, messages);
    }

    /**
     * Draws the box at (posX, posY) and returns its on-screen size {@code [width, height]} in
     * scaled pixels (useful for hit-testing in the settings screen).
     */
    public static int[] drawBox(GuiGraphics graphics, int posX, int posY, float scale,
                                int width, int maxLines, int bgOpacity, List<Component> messages) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int lineHeight = font.lineHeight + 1;

        // Wrap newest -> oldest until we reach maxLines, then flip so oldest is on top.
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && lines.size() < maxLines; i--) {
            List<FormattedCharSequence> wrapped = font.split(messages.get(i), width);
            for (int j = wrapped.size() - 1; j >= 0 && lines.size() < maxLines; j--) {
                lines.add(wrapped.get(j));
            }
        }
        Collections.reverse(lines);

        int headerHeight = font.lineHeight + 2;
        int blockWidth = width + PADDING * 2;
        int blockHeight = headerHeight + Math.max(1, lines.size()) * lineHeight + PADDING;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(posX, posY);
        pose.scale(scale, scale);

        graphics.fill(0, 0, blockWidth, blockHeight, (clamp(bgOpacity, 0, 255) << 24));

        Component title = Component.literal("Staff Chat")
                .withStyle(s -> s.withColor(0x4DD1CC).withBold(true));
        graphics.drawString(font, title, PADDING, PADDING, 0xFFFFFFFF);

        int y = PADDING + headerHeight;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, PADDING, y, 0xFFFFFFFF);
            y += lineHeight;
        }

        pose.popMatrix();

        return new int[]{Math.round(blockWidth * scale), Math.round(blockHeight * scale)};
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
