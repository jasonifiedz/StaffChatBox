package com.example.staffchat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Persistent, user-editable settings for the Staff Chat Box. Loaded from / saved to
 * {@code config/staffchatbox.json}. The {@link StaffChatSettingsScreen} mutates the live
 * singleton directly so changes are reflected immediately, then {@link #save()} persists them.
 */
public final class StaffChatConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static StaffChatConfig instance;
    private static transient Path path;

    // ----- persisted fields (with defaults) -----

    /** Master on/off for the separate box. */
    public boolean enabled = true;

    /** Top-left anchor of the box, in GUI-scaled pixels. */
    public int posX = 4;
    public int posY = 4;

    /** Content width (before {@link #scale}) in pixels. Also adjustable by dragging the corner. */
    public int width = 300;

    /** Max wrapped lines shown at once. Also adjustable by dragging the corner. */
    public int maxLines = 12;

    /** Render scale for the whole box. */
    public float scale = 1.0f;

    /** Background alpha 0-255. */
    public int bgOpacity = 0x66;

    /** Ticks (20/s) a message stays in the HUD before disappearing (when no screen is open). */
    public int fadeTicks = 400;

    /** If true, staff messages stay in vanilla chat too; if false they only show in the box. */
    public boolean mirrorInMainChat = false;

    /** Command (without slash) used to send from the input box. Default {@code sc} → "/sc &lt;msg&gt;". */
    public String command = "sc";

    /** Show a timestamp prefix on each message. */
    public boolean showTimestamps = true;

    /** java.time pattern for the timestamp, editable in the settings screen. */
    public String timestampFormat = "HH:mm:ss";

    /** Colour (0xRRGGBB) of the timestamp prefix. */
    public int timestampColor = 0xAAAAAA;

    // ----- detection signatures (editable via JSON; not in the GUI) -----

    /** Literal {@code &#hex} substrings that mark a message as staff chat. */
    public String[] textSignatures = {"&#22C2D8"};

    /** Gradient RGB values, used as a fallback when hex was translated to real colours. */
    public int[] colorSignatures = {0x22C2D8, 0x4DD1CC, 0x78E1C1, 0xA2F0B5, 0xCDFFA9};

    // ----- lifecycle -----

    public static StaffChatConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve("staffchatbox.json");
        StaffChatConfig loaded = null;
        try {
            if (Files.exists(path)) {
                loaded = GSON.fromJson(Files.readString(path), StaffChatConfig.class);
            }
        } catch (Exception e) {
            System.err.println("[StaffChatBox] Could not read config, using defaults: " + e);
        }
        if (loaded == null) {
            loaded = new StaffChatConfig();
        }
        loaded.sanitize();
        instance = loaded;
        instance.save();
    }

    public void save() {
        try {
            if (path == null) {
                path = FabricLoader.getInstance().getConfigDir().resolve("staffchatbox.json");
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[StaffChatBox] Could not save config: " + e);
        }
    }

    public void sanitize() {
        width = clamp(width, 80, 600);
        maxLines = clamp(maxLines, 1, 16);
        scale = Math.max(0.5f, Math.min(2.5f, scale));
        bgOpacity = clamp(bgOpacity, 0, 255);
        fadeTicks = clamp(fadeTicks, 40, 6000);
        if (timestampFormat == null || timestampFormat.isBlank()) {
            timestampFormat = "HH:mm:ss";
        }
        if (command == null || command.isBlank()) {
            command = "sc";
        }
        command = command.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (textSignatures == null || textSignatures.length == 0) {
            textSignatures = new String[]{"&#22C2D8"};
        }
        if (colorSignatures == null || colorSignatures.length == 0) {
            colorSignatures = new int[]{0x22C2D8};
        }
    }

    /** Resets only the GUI-facing fields (keeps detection signatures intact). */
    public void resetToDefaults() {
        StaffChatConfig d = new StaffChatConfig();
        enabled = d.enabled;
        posX = d.posX;
        posY = d.posY;
        width = d.width;
        maxLines = d.maxLines;
        scale = d.scale;
        bgOpacity = d.bgOpacity;
        fadeTicks = d.fadeTicks;
        mirrorInMainChat = d.mirrorInMainChat;
        showTimestamps = d.showTimestamps;
        timestampFormat = d.timestampFormat;
        timestampColor = d.timestampColor;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
