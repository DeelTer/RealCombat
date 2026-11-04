package ru.deelter.realcombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;


/**
 * The keys beside the mouse: swing with the attack button (its direction from how the view moved), parry with the
 * use button, and these.
 */
public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse("realcombat:combat"));
	public static final KeyMapping STAB = register("stab", InputConstants.KEY_R);
	public static final KeyMapping OVERHEAD = register("overhead", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping FEINT = register("feint", InputConstants.KEY_Z);
	public static final KeyMapping KICK = register("kick", InputConstants.KEY_G);

	private Keys() {
	}

	public static void init() {
		// loading the class registers the keys
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.realcombat." + name, key, CATEGORY));
	}
}
