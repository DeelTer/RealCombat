package ru.deelter.realcombat.api;

import net.minecraft.server.level.ServerPlayer;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.net.Protocol;
import ru.deelter.realcombat.server.CombatServer;

/**
 * The server-side API of RealCombat on Fabric. Call it on the server thread.
 * <p>
 * Turning the mechanic off for a player makes him fight the vanilla way: his clicks hit as usual, he does not parry,
 * and the blows of others land on him as on a player without the mod. His client is told and switches too. For
 * arenas and minigames: set {@code enabledByDefault: false} in {@code config/realcombat.json} and turn it on for the
 * players who enter.
 */
public final class RealCombatApi {
	private RealCombatApi() {
	}

	/**
	 * the player's client has the mod (only then can he fight by these rules)
	 */
	public static boolean hasMod(ServerPlayer player) {
		return CombatServer.modded(player);
	}

	/**
	 * the mechanic is on for the player, whether or not his client has the mod
	 */
	public static boolean isEnabled(ServerPlayer player) {
		return CombatServer.enabled(player);
	}

	/**
	 * Makes a player with no client (a fake player, an NPC of another mod) fight by these rules, driven by
	 * {@link #perform}. It is seen by the players around, parried and hit as any fighter.
	 */
	public static void addFighter(ServerPlayer puppet) {
		CombatServer.addPuppet(puppet);
	}

	public static void removeFighter(ServerPlayer puppet) {
		CombatServer.removePuppet(puppet);
	}

	/**
	 * Presses something for a fighter, as his client would: {@code ATTACK} in a direction, {@code FEINT}, {@code PARRY},
	 * {@code KICK}. Refused as for a client when the rules do not allow it right now.
	 */
	public static void perform(ServerPlayer fighter, Protocol.Action action, Dir dir) {
		CombatServer.perform(fighter, action, dir);
	}

	/**
	 * Turns the mechanic on or off for the player until he leaves the server.
	 */
	public static void setEnabled(ServerPlayer player, boolean enabled) {
		CombatServer.setEnabled(player, enabled);
	}
}
