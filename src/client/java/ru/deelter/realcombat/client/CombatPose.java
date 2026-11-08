package ru.deelter.realcombat.client;

import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;

/**
 * What to draw for a fighter this frame.
 *
 * @param progress 0 at the start of the phase, 1 at its end, with the partial tick
 */
public record CombatPose(Phase phase, Dir dir, float progress, boolean riposte, boolean bash) {
	public CombatPose(Phase phase, Dir dir, float progress, boolean riposte) {
		this(phase, dir, progress, riposte, false);
	}

	/**
	 * The pose to mirror onto the left arm: the poses are made for the right one, and mirroring them turns a blow from
	 * the right into one from the left, so the side is swapped first. The blow then comes from the side it really
	 * comes from, the side the arrows show.
	 */
	public CombatPose forArm(net.minecraft.world.entity.HumanoidArm arm) {
		if (arm != net.minecraft.world.entity.HumanoidArm.LEFT || !dir.horizontal()) return this;
		return new CombatPose(phase, dir == Dir.RIGHT ? Dir.LEFT : Dir.RIGHT, progress, riposte, bash);
	}
}
