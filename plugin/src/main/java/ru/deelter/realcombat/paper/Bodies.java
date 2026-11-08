package ru.deelter.realcombat.paper;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.deelter.realcombat.combat.Dir;

/**
 * What a body allows and what a blow does to it. Without ChopChop bodies are whole and blows leave no marks.
 */
interface Bodies {
	Bodies NONE = new Bodies() {
	};

	/**
	 * the hand on that side ("left", "right") is cut off
	 */
	default boolean handless(LivingEntity fighter, String side) {
		return false;
	}

	default boolean canSwing(LivingEntity fighter) {
		return true;
	}

	default boolean canKick(LivingEntity fighter) {
		return true;
	}

	default void struck(Player attacker, LivingEntity target, Dir dir, Vector swing, float damage, boolean killed) {
	}
}
