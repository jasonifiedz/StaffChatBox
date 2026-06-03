package com.example.staffchat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Hooks the settings screen into ModMenu's "Configure" button (Mods menu → this mod → gear).
 * This class is only loaded when ModMenu is installed, so it adds no hard dependency.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StaffChatSettingsScreen::new;
    }
}
