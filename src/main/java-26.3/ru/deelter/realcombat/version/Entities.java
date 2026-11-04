package ru.deelter.realcombat.version;

import net.minecraft.world.entity.Entity;

/**
 * The few calls on entities that differ between Minecraft versions: here for 26.3.
 */
public final class Entities {
	private Entities() {
	}

	/**
	 * no hurt-immunity left from the last blow: the next one counts
	 */
	public static void clearInvulnerability(Entity entity) {
		entity.setInvulnerableTime(0);
	}

	/**
	 * the entity's motion changed on the server: tell its client
	 */
	public static void resendMotion(Entity entity) {
		entity.needsSync = true;
	}
}
