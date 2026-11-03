package ru.deelter.realcombat.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Adds the settings button to the mod list of Mod Menu, when Cloth Config is there to build the screen. Without it
 * the settings are in {@code config/realcombat.json}.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> FabricLoader.getInstance().isModLoaded("cloth-config") ? ClothScreen.create(parent) : null;
	}
}
