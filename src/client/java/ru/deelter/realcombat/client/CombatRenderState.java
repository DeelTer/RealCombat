package ru.deelter.realcombat.client;

/**
 * Added to every render state: the swing to draw, taken when the state is filled from its entity.
 */
public interface CombatRenderState {
	CombatPose realcombat$pose();

	void realcombat$setPose(CombatPose pose);

	/**
	 * the arm that swings: the main one, or the other one when the sword hand is cut off; null for the main one
	 */
	net.minecraft.world.entity.HumanoidArm realcombat$arm();

	void realcombat$setArm(net.minecraft.world.entity.HumanoidArm arm);
}
