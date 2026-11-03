package ru.deelter.realcombat.paper.api;

import org.bukkit.entity.Player;
import ru.deelter.realcombat.paper.RealCombatPlugin;

/**
 * The API of the RealCombat Paper plugin. Call it on the main thread; add {@code softdepend: [RealCombat]} (or
 * {@code depend}) to your plugin.yml.
 * <p>
 * Turning the mechanic off for a player makes him fight the vanilla way: his clicks hit as usual, he does not parry,
 * and the blows of others land on him as on a player without the mod. His client is told and switches too. For
 * arenas and minigames: set {@code enabledByDefault: false} in {@code plugins/RealCombat/realcombat.json} and turn it
 * on for the players who enter.
 */
public final class RealCombatApi {
	private RealCombatApi() {
	}

	/**
	 * the player's client has the mod (only then can he fight by these rules)
	 */
	public static boolean hasMod(Player player) {
		return RealCombatPlugin.getInstance().hasMod(player);
	}

	/**
	 * the mechanic is on for the player, whether or not his client has the mod
	 */
	public static boolean isEnabled(Player player) {
		return RealCombatPlugin.getInstance().enabled(player);
	}

	/**
	 * Turns the mechanic on or off for the player until he leaves the server.
	 */
	public static void setEnabled(Player player, boolean enabled) {
		RealCombatPlugin.getInstance().setEnabled(player, enabled);
	}
}
