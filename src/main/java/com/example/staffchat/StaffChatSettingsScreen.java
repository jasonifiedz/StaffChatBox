package com.example.staffchat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Configuration screen for the Staff Chat Box. Shows a live preview of the box that can be
 * dragged to reposition and resized from its bottom-right corner, plus sliders and toggles for
 * scale, opacity, line count, fade time, timestamps (with an editable format), and more.
 */
public class StaffChatSettingsScreen extends Screen {

    private static final int DRAG_NONE = 0;
    private static final int DRAG_MOVE = 1;
    private static final int DRAG_RESIZE = 2;
    private static final int HANDLE = 8;

    private static final int[] GRADIENT = {0x22C2D8, 0x4DD1CC, 0x78E1C1, 0xA2F0B5, 0xCDFFA9};

    private final Screen parent;
    private final StaffChatConfig cfg = StaffChatConfig.get();

    private int dragMode = DRAG_NONE;
    private double grabDx, grabDy;
    private int lastBoxW = 0, lastBoxH = 0;

    private EditBox formatBox;

    public StaffChatSettingsScreen(Screen parent) {
        super(Component.literal("Staff Chat Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = this.width - 160;
        int w = 150;
        int h = 18;
        int rowH = 19;
        int y = 14;

        addRenderableWidget(new OptionSlider(panelX, y, w, h, 0.5, 2.5, 0.05, cfg.scale,
                v -> String.format("Scale: %.2f", v), v -> cfg.scale = (float) (double) v));
        y += rowH;
        addRenderableWidget(new OptionSlider(panelX, y, w, h, 0, 255, 5, cfg.bgOpacity,
                v -> "BG Opacity: " + (int) (double) v, v -> cfg.bgOpacity = (int) Math.round(v)));
        y += rowH;
        addRenderableWidget(new OptionSlider(panelX, y, w, h, 1, 16, 1, cfg.maxLines,
                v -> "Max Lines: " + (int) (double) v, v -> cfg.maxLines = (int) Math.round(v)));
        y += rowH;
        addRenderableWidget(new OptionSlider(panelX, y, w, h, 80, 600, 5, cfg.width,
                v -> "Width: " + (int) (double) v, v -> cfg.width = (int) Math.round(v)));
        y += rowH;
        addRenderableWidget(new OptionSlider(panelX, y, w, h, 2, 300, 1, cfg.fadeTicks / 20.0,
                v -> "Fade: " + (int) (double) v + "s", v -> cfg.fadeTicks = (int) Math.round(v * 20)));
        y += rowH + 2;

        addRenderableWidget(Button.builder(boxLabel(), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(boxLabel());
        }).bounds(panelX, y, w, h).build());
        y += rowH;
        addRenderableWidget(Button.builder(timestampsLabel(), b -> {
            cfg.showTimestamps = !cfg.showTimestamps;
            b.setMessage(timestampsLabel());
        }).bounds(panelX, y, w, h).build());
        y += rowH;
        addRenderableWidget(Button.builder(mirrorLabel(), b -> {
            cfg.mirrorInMainChat = !cfg.mirrorInMainChat;
            b.setMessage(mirrorLabel());
        }).bounds(panelX, y, w, h).build());
        y += rowH + 12; // leave room for the "Time format:" label drawn in render()

        this.formatBox = new EditBox(this.font, panelX, y, w, h, Component.literal("Time format"));
        this.formatBox.setMaxLength(32);
        this.formatBox.setValue(cfg.timestampFormat);
        this.formatBox.setResponder(s -> cfg.timestampFormat = s.isBlank() ? "HH:mm:ss" : s);
        addRenderableWidget(this.formatBox);
        y += rowH + 2;

        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            cfg.resetToDefaults();
            rebuildWidgets();
        }).bounds(panelX, y, w, h).build());
        y += rowH;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(panelX, y, w, h).build());
    }

    private MutableComponent boxLabel() {
        return Component.literal("Box: " + (cfg.enabled ? "ON" : "OFF"));
    }

    private MutableComponent timestampsLabel() {
        return Component.literal("Timestamps: " + (cfg.showTimestamps ? "ON" : "OFF"));
    }

    private MutableComponent mirrorLabel() {
        return Component.literal("Keep in chat: " + (cfg.mirrorInMainChat ? "ON" : "OFF"));
    }

    // ----- preview messages -----

    private List<Component> previewMessages() {
        List<StaffChatHistory.Entry> hist = StaffChatHistory.snapshot();
        if (hist.isEmpty()) {
            long now = System.currentTimeMillis();
            hist = new ArrayList<>();
            hist.add(new StaffChatHistory.Entry(sample("Notch", "north border looks clear"), 0, now));
            hist.add(new StaffChatHistory.Entry(sample("Dinnerbone", "on my way over"), 0, now));
            hist.add(new StaffChatHistory.Entry(sample("jeb_", "thanks, watching spawn"), 0, now));
        }
        List<Component> msgs = new ArrayList<>();
        for (StaffChatHistory.Entry e : hist) {
            msgs.add(StaffChatFormat.withTimestamp(e));
        }
        return msgs;
    }

    private Component sample(String name, String message) {
        MutableComponent c = Component.empty();
        String staff = "Staff";
        for (int i = 0; i < staff.length(); i++) {
            int col = GRADIENT[i % GRADIENT.length];
            c.append(Component.literal(String.valueOf(staff.charAt(i)))
                    .withStyle(s -> s.withColor(col).withBold(true)));
        }
        c.append(Component.literal(" " + name + ": ").withStyle(s -> s.withColor(0xFFFFFF)));
        c.append(Component.literal(message).withStyle(s -> s.withColor(0xDDDDDD)));
        return c;
    }

    // ----- dragging / resizing the preview -----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int bx = cfg.posX, by = cfg.posY, bw = lastBoxW, bh = lastBoxH;
            if (mouseX >= bx + bw - HANDLE && mouseX <= bx + bw && mouseY >= by + bh - HANDLE && mouseY <= by + bh) {
                this.dragMode = DRAG_RESIZE;
                return true;
            }
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                this.dragMode = DRAG_MOVE;
                this.grabDx = mouseX - bx;
                this.grabDy = mouseY - by;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragMode == DRAG_MOVE) {
            cfg.posX = clamp((int) Math.round(mouseX - grabDx), 0, this.width - 10);
            cfg.posY = clamp((int) Math.round(mouseY - grabDy), 0, this.height - 10);
            return true;
        }
        if (this.dragMode == DRAG_RESIZE) {
            float scale = Math.max(0.1f, cfg.scale);
            int pad = StaffChatHud.PADDING;
            int lineHeight = this.font.lineHeight + 1;
            int headerH = this.font.lineHeight + 2;
            int scaledW = (int) (mouseX - cfg.posX);
            int scaledH = (int) (mouseY - cfg.posY);
            cfg.width = clamp(Math.round(scaledW / scale) - pad * 2, 80, 600);
            int linesPx = Math.round(scaledH / scale) - headerH - pad;
            cfg.maxLines = clamp(Math.round(linesPx / (float) lineHeight), 1, 16);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = this.dragMode != DRAG_NONE;
        this.dragMode = DRAG_NONE;
        if (wasDragging) {
            cfg.sanitize();
            rebuildWidgets(); // resync the Width / Max Lines sliders
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ----- rendering -----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        // Live preview (drawn first so the control panel sits on top).
        int[] dims = StaffChatHud.drawBox(graphics, cfg.posX, cfg.posY, cfg.scale,
                cfg.width, cfg.maxLines, cfg.bgOpacity, previewMessages());
        this.lastBoxW = dims[0];
        this.lastBoxH = dims[1];

        // Outline + resize handle.
        int bx = cfg.posX, by = cfg.posY, bw = dims[0], bh = dims[1];
        int outline = (dragMode != DRAG_NONE) ? 0xFFFFFFFF : 0xFF55FFFF;
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by, outline);
        graphics.fill(bx - 1, by + bh, bx + bw + 1, by + bh + 1, outline);
        graphics.fill(bx - 1, by, bx, by + bh, outline);
        graphics.fill(bx + bw, by, bx + bw + 1, by + bh, outline);
        graphics.fill(bx + bw - HANDLE, by + bh - HANDLE, bx + bw, by + bh, 0xAAFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, Component.literal("Staff Chat Settings")
                .withStyle(s -> s.withColor(0x4DD1CC).withBold(true)), 10, 8, 0xFFFFFFFF);
        graphics.drawString(this.font,
                Component.literal("Drag the box to move • drag the corner to resize"),
                10, 22, 0xFFAAAAAA);

        // Label + validity for the timestamp format field.
        if (this.formatBox != null) {
            graphics.drawString(this.font, Component.literal("Time format:"),
                    this.formatBox.getX(), this.formatBox.getY() - 10, 0xFFFFFFFF);
            boolean ok = StaffChatFormat.isValidPattern(this.formatBox.getValue());
            graphics.drawString(this.font, Component.literal(ok ? "valid" : "invalid"),
                    this.formatBox.getX() + 70, this.formatBox.getY() - 10, ok ? 0xFF55FF55 : 0xFFFF5555);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        // Shade the control panel for readability.
        graphics.fill(this.width - 164, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void onClose() {
        cfg.sanitize();
        cfg.save();
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
