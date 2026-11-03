package ru.deelter.realcombat.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.combat.Dir;

/**
 * What a body allows and what a blow does to it. Without ChopChop bodies are whole and blows leave no marks.
 */
public interface Bodies {
	Bodies NONE = new Bodies() {
	};

	/**
	 * a fighter without his sword hand can't swing, nor parry
	 */
	default boolean canSwing(LivingEntity fighter) {
		return true;
	}

	/**
	 * a fighter without legs can't kick
	 */
	default boolean canKick(LivingEntity fighter) {
		return true;
	}

	/**
	 * A blow landed and hurt.
	 *
	 * @param swing  the way the blade moved through the world
	 * @param killed the blow killed the target
	 */
	default void struck(ServerPlayer attacker, LivingEntity target, Dir dir, Vec3 swing, float damage, boolean killed) {
	}
}
