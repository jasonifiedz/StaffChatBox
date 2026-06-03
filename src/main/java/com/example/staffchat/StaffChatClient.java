package com.example.staffchat;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;

/**
 * Entry point. Loads config, intercepts staff messages into the separate box, draws the passive
 * box during gameplay, and adds the interactive staff panel to the vanilla chat screen (press T
 * for two boxes: normal chat + staff chat, both typeable). Settings are via ModMenu → Configure.
 */
public class StaffChatClient implements ClientModInitializer {

    private static KeyMapping openStaffKey;

    @Override
    public void onInitializeClient() {
        StaffChatConfig.load();

        // Optional hotkey: opens chat focused on the staff input. Default I; rebind/clear in
        // Options > Controls > Staff Chat Box.
        openStaffKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.staffchatbox.open_staff",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                KeyMapping.Category.MISC));

        // Divert staff messages out of the main chat (unless mirroring is enabled).
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) {
                return true;
            }
            if (StaffChatDetector.isStaffChat(message)) {
                StaffChatHistory.add(message);
                return StaffChatConfig.get().mirrorInMainChat;
            }
            return true;
        });

        // Passive box during gameplay.
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> StaffChatHud.render(graphics));

        // Interactive staff panel inside the vanilla chat screen (press T).
        StaffChatOverlay.register();

        // Advance the fade clock and poll the optional hotkey.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            StaffChatHistory.tick();
            while (openStaffKey.consumeClick()) {
                if (client.screen == null) {
                    StaffChatOverlay.openStaffChat(client);
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> StaffChatConfig.get().save());
    }
}
