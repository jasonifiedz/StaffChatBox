package com.example.staffchat;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Injects a second, separate staff-chat box into the vanilla chat screen. When you press T,
 * the normal chat input is there as always, AND a staff panel appears (its own search box,
 * scrollable log, and its own input that sends to staff chat via /sc). Two boxes, both typeable,
 * fully independent. The panel can be dragged by its title bar.
 *
 * <p>The log hugs its content (top under the title, newest at the bottom) so it lines up exactly
 * with the passive HUD box — opening/closing chat doesn't make messages jump. Search and input
 * sit below the log. Built on fabric-screen-api-v1 (no mixins).
 */
public final class StaffChatOverlay {

    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    private static final int SEARCH_H = 14;
    private static final int INPUT_H = 14;
    private static final int GAP = 2;

    private static EditBox searchBox;
    private static EditBox staffInput;
    private static int scrollLines = 0; // 0 = pinned to the newest line
    private static ChatScreen currentChat = null;
    private static List<FormattedCharSequence> cachedLines = new ArrayList<>();

    // Panel geometry. ax/ay/pw/maxRows/panelH persist; the rest are derived per frame.
    private static int ax, ay, pw, maxRows, panelH;
    private static int logX, logY, logW, logH, panelBottom;

    // Title-bar drag state.
    private static boolean dragging = false;
    private static boolean wasMouseDown = false;
    private static double grabDx, grabDy;

    private StaffChatOverlay() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen chat)) {
                return;
            }
            if (!StaffChatConfig.get().enabled) {
                return;
            }
            build(client, chat, w, h);
        });
    }

    private static void build(Minecraft client, ChatScreen screen, int w, int h) {
        StaffChatConfig cfg = StaffChatConfig.get();
        Font font = client.font;
        int lineHeight = font.lineHeight + 1;
        int headerH = font.lineHeight + 2;

        pw = Math.max(120, Math.min(cfg.width, w - 8));
        maxRows = Math.max(1, cfg.maxLines);
        panelH = headerH + maxRows * lineHeight + GAP + SEARCH_H + GAP + INPUT_H + 4;

        ax = clamp(cfg.posX, 2, Math.max(2, w - pw - 2));
        ay = clamp(cfg.posY, 2, Math.max(2, h - panelH - 2));

        searchBox = new EditBox(font, ax + 1, ay, pw - 2, SEARCH_H, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search staff chat..."));
        searchBox.setMaxLength(128);
        searchBox.setResponder(s -> scrollLines = 0);

        staffInput = new EditBox(font, ax + 1, ay, pw - 2, INPUT_H, Component.literal("Message staff"));
        staffInput.setHint(Component.literal("Message staff..."));
        staffInput.setMaxLength(256);

        List<AbstractWidget> widgets = Screens.getButtons(screen);
        widgets.add(searchBox);
        widgets.add(staffInput);

        cachedLines = wrappedLines();
        recomputeGeometry();
        scrollLines = 0;
        dragging = false;

        // Register per-screen callbacks once per screen instance (init also fires on resize).
        ChatScreen prev = currentChat;
        currentChat = screen;
        if (prev != screen) {
            ScreenEvents.beforeRender(screen).register((s, g, mx, my, td) -> {
                cachedLines = wrappedLines();
                handleDrag(mx, my);
                recomputeGeometry();
                drawBackground(g);
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, td) -> drawForeground(g));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> onKey(keyEvent.key()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hor, ver) -> onScroll(mx, my, ver));
        }
    }

    /** Sizes the log to its content (capped at maxRows) and positions the boxes below it. */
    private static void recomputeGeometry() {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int headerH = font.lineHeight + 2;

        int rowsToShow = Math.min(Math.max(cachedLines.size(), 1), maxRows);
        logX = ax;
        logW = pw;
        logY = ay + headerH;
        logH = rowsToShow * lineHeight;
        int searchY = logY + logH + GAP;
        int inputY = searchY + SEARCH_H + GAP;
        panelBottom = inputY + INPUT_H + 2;

        if (searchBox != null) {
            searchBox.setX(ax + 1);
            searchBox.setY(searchY);
            searchBox.setWidth(pw - 2);
        }
        if (staffInput != null) {
            staffInput.setX(ax + 1);
            staffInput.setY(inputY);
            staffInput.setWidth(pw - 2);
        }
    }

    /** Drags the panel when the left mouse button is held over its title bar. */
    private static void handleDrag(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().handle();
        boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();

        if (down && !wasMouseDown
                && mouseX >= ax && mouseX <= ax + pw && mouseY >= ay && mouseY < ay + (mc.font.lineHeight + 2)) {
            dragging = true;
            grabDx = mouseX - ax;
            grabDy = mouseY - ay;
        }
        if (dragging && down) {
            ax = clamp((int) Math.round(mouseX - grabDx), 2, Math.max(2, gw - pw - 2));
            ay = clamp((int) Math.round(mouseY - grabDy), 2, Math.max(2, gh - panelH - 2));
            StaffChatConfig.get().posX = ax;
            StaffChatConfig.get().posY = ay;
        }
        if (!down && wasMouseDown && dragging) {
            dragging = false;
            StaffChatConfig.get().save();
        }
        wasMouseDown = down;
    }

    /**
     * Opens the chat screen and focuses the staff input, so a hotkey jumps you straight into
     * typing a staff message. Falls back to plain chat if the panel is disabled.
     */
    public static void openStaffChat(Minecraft client) {
        client.setScreen(new ChatScreen("", false));
        if (StaffChatConfig.get().enabled && currentChat != null && staffInput != null) {
            currentChat.setFocused(staffInput);
        }
    }

    private static boolean onKey(int key) {
        if (key == KEY_ENTER || key == KEY_NUMPAD_ENTER) {
            if (staffInput != null && staffInput.isFocused()) {
                sendStaff();
                return false; // cancel vanilla Enter (would send to public chat and close)
            }
            if (searchBox != null && searchBox.isFocused()) {
                return false; // don't send public chat when finishing a search
            }
        }
        return true;
    }

    private static void sendStaff() {
        Minecraft mc = Minecraft.getInstance();
        String text = staffInput.getValue().trim();
        if (!text.isEmpty() && mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(StaffChatConfig.get().command + " " + text);
        }
        staffInput.setValue("");
        scrollLines = 0;
        mc.setScreen(null); // close chat like vanilla so the player can move again
    }

    private static boolean onScroll(double mx, double my, double verticalAmount) {
        if (mx >= logX && mx <= logX + logW && my >= logY && my <= logY + logH) {
            int total = cachedLines.size();
            int visible = Math.min(total, maxRows);
            scrollLines = clamp(scrollLines + (int) Math.round(verticalAmount), 0, Math.max(0, total - visible));
            return false; // consume so the vanilla chat doesn't scroll too
        }
        return true;
    }

    private static List<FormattedCharSequence> wrappedLines() {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = new ArrayList<>();
        String q = searchBox != null ? searchBox.getValue() : "";
        for (StaffChatHistory.Entry e : StaffChatHistory.search(q)) {
            lines.addAll(font.split(StaffChatFormat.withTimestamp(e), Math.max(10, pw - 8)));
        }
        return lines;
    }

    private static void drawBackground(GuiGraphics g) {
        g.fill(ax, ay, ax + pw, panelBottom, (StaffChatConfig.get().bgOpacity << 24));
        g.fill(logX, logY, logX + logW, logY + logH, 0x66000000);
    }

    private static void drawForeground(GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;

        // Title doubles as the drag handle; the grip hint makes that discoverable.
        g.drawString(font, Component.literal("Staff Chat")
                .withStyle(s -> s.withColor(0x4DD1CC).withBold(true)), ax + 1, ay, 0xFFFFFFFF);
        g.drawString(font, Component.literal("☰ drag"), ax + pw - 34, ay, 0xFF777777);

        String q = searchBox != null ? searchBox.getValue() : "";
        if (!q.isBlank()) {
            int matches = StaffChatHistory.search(q).size();
            g.drawString(font, Component.literal(matches + (matches == 1 ? " match" : " matches")),
                    ax + 64, ay, 0xFFFFFF55);
        }

        int total = cachedLines.size();
        int visible = Math.min(total, maxRows);
        scrollLines = clamp(scrollLines, 0, Math.max(0, total - visible));

        if (total == 0) {
            String msg = q.isBlank() ? "No staff messages yet." : "No matches.";
            g.drawString(font, Component.literal(msg), logX + 3, logY + 2, 0xFF888888);
            return;
        }

        // Show a window of `visible` lines ending `scrollLines` above the newest, top-anchored.
        int startIdx = total - visible - scrollLines;
        g.enableScissor(logX, logY, logX + logW, logY + logH);
        int y = logY;
        for (int i = startIdx; i < startIdx + visible; i++) {
            g.drawString(font, cachedLines.get(i), logX + 3, y, 0xFFFFFFFF);
            y += lineHeight;
        }
        g.disableScissor();

        // Scrollbar (only when there's more history than fits).
        if (total > visible) {
            int trackX = logX + logW - 3;
            int thumbH = Math.max(8, (int) ((float) visible / total * logH));
            float p = (float) scrollLines / (total - visible); // 0 = newest (bottom)
            int thumbY = (int) (logY + (1.0f - p) * (logH - thumbH));
            g.fill(trackX, logY, trackX + 3, logY + logH, 0x55000000);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
