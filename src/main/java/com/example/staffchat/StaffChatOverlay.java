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
 * fully independent. The panel can be dragged by its title bar to reposition it (the position is
 * shared with the settings preview and the passive HUD box). Built on fabric-screen-api-v1.
 */
public final class StaffChatOverlay {

    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    private static final int SEARCH_H = 14;
    private static final int INPUT_H = 14;
    private static final int GAP = 2;

    private static EditBox searchBox;
    private static EditBox staffInput;
    private static int scrollPixels = 0;
    private static ChatScreen currentChat = null;

    // Panel geometry (pw/logH/panelH persist; the rest are derived by layout()).
    private static int ax, ay, pw, logH, panelH;
    private static int searchYTop, logX, logY, logW, inputYTop, panelBottom;

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
        int rows = Math.max(4, Math.min(cfg.maxLines, 16));
        logH = rows * lineHeight;
        panelH = headerH + SEARCH_H + GAP + logH + GAP + INPUT_H + 4;

        ax = clamp(cfg.posX, 2, Math.max(2, w - pw - 2));
        ay = clamp(cfg.posY, 2, Math.max(2, h - panelH - 2));

        searchBox = new EditBox(font, ax + 1, ay, pw - 2, SEARCH_H, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search staff chat..."));
        searchBox.setMaxLength(128);
        searchBox.setResponder(s -> scrollPixels = 0);

        staffInput = new EditBox(font, ax + 1, ay, pw - 2, INPUT_H, Component.literal("Message staff"));
        staffInput.setHint(Component.literal("Message staff..."));
        staffInput.setMaxLength(256);

        List<AbstractWidget> widgets = Screens.getButtons(screen);
        widgets.add(searchBox);
        widgets.add(staffInput);

        layout(ax, ay);
        scrollPixels = 0;
        dragging = false;

        // Register per-screen callbacks once per screen instance (init also fires on resize).
        ChatScreen prev = currentChat;
        currentChat = screen;
        if (prev != screen) {
            ScreenEvents.beforeRender(screen).register((s, g, mx, my, td) -> {
                handleDrag(mx, my);
                drawBackground(g);
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, td) -> drawForeground(g));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> onKey(keyEvent.key()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hor, ver) -> onScroll(mx, my, ver));
        }
    }

    /** Positions all geometry (and the two edit boxes) for the given top-left anchor. */
    private static void layout(int newAx, int newAy) {
        Font font = Minecraft.getInstance().font;
        int headerH = font.lineHeight + 2;
        ax = newAx;
        ay = newAy;
        searchYTop = ay + headerH;
        logX = ax;
        logW = pw;
        logY = searchYTop + SEARCH_H + GAP;
        inputYTop = logY + logH + GAP;
        panelBottom = inputYTop + INPUT_H + 2;
        if (searchBox != null) {
            searchBox.setX(ax + 1);
            searchBox.setY(searchYTop);
        }
        if (staffInput != null) {
            staffInput.setX(ax + 1);
            staffInput.setY(inputYTop);
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
                && mouseX >= ax && mouseX <= ax + pw && mouseY >= ay && mouseY < searchYTop) {
            dragging = true;
            grabDx = mouseX - ax;
            grabDy = mouseY - ay;
        }
        if (dragging && down) {
            int nx = clamp((int) Math.round(mouseX - grabDx), 2, Math.max(2, gw - pw - 2));
            int ny = clamp((int) Math.round(mouseY - grabDy), 2, Math.max(2, gh - panelH - 2));
            StaffChatConfig.get().posX = nx;
            StaffChatConfig.get().posY = ny;
            layout(nx, ny);
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
        mc.setScreen(null); // close chat like vanilla so the player can move again
    }

    private static boolean onScroll(double mx, double my, double verticalAmount) {
        if (mx >= logX && mx <= logX + logW && my >= logY && my <= logY + logH) {
            int lineHeight = Minecraft.getInstance().font.lineHeight + 1;
            int total = wrappedLines().size() * lineHeight;
            scrollPixels = clamp((int) (scrollPixels + verticalAmount * lineHeight * 3), 0, Math.max(0, total - logH));
            return false; // consume so the vanilla chat doesn't scroll too
        }
        return true;
    }

    private static List<FormattedCharSequence> wrappedLines() {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = new ArrayList<>();
        String q = searchBox != null ? searchBox.getValue() : "";
        for (StaffChatHistory.Entry e : StaffChatHistory.search(q)) {
            lines.addAll(font.split(StaffChatFormat.withTimestamp(e), logW - 6));
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
        g.drawString(font, Component.literal("☰ drag")
                .withStyle(s -> s.withColor(0x777777)), ax + pw - 34, ay, 0xFF777777);

        String q = searchBox != null ? searchBox.getValue() : "";
        if (!q.isBlank()) {
            int matches = StaffChatHistory.search(q).size();
            g.drawString(font, Component.literal(matches + (matches == 1 ? " match" : " matches")),
                    ax + 64, ay, 0xFFFFFF55);
        }

        List<FormattedCharSequence> lines = wrappedLines();
        int total = lines.size() * lineHeight;
        scrollPixels = clamp(scrollPixels, 0, Math.max(0, total - logH));

        g.enableScissor(logX, logY, logX + logW, logY + logH);
        int y = (logY + logH) - total + scrollPixels;
        for (FormattedCharSequence line : lines) {
            if (y + lineHeight >= logY && y <= logY + logH) {
                g.drawString(font, line, logX + 3, y, 0xFFFFFFFF);
            }
            y += lineHeight;
        }
        g.disableScissor();

        if (lines.isEmpty()) {
            String msg = q.isBlank() ? "No staff messages yet." : "No matches.";
            g.drawString(font, Component.literal(msg), logX + 3, logY + 2, 0xFF888888);
        }

        int max = Math.max(0, total - logH);
        if (max > 0) {
            int trackX = logX + logW - 3;
            int thumbH = Math.max(12, (int) ((logH / (float) total) * logH));
            int thumbY = (int) (logY + (1.0 - scrollPixels / (float) max) * (logH - thumbH));
            g.fill(trackX, logY, trackX + 3, logY + logH, 0x55000000);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
