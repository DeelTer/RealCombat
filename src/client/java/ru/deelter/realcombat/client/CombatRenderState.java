package ru.deelter.realcombat.client;

/**
 * Added to every render state: the swing to draw, taken when the state is filled from its entity.
 */
public interface CombatRenderState {
	CombatPose realcombat$pose();

	void realcombat$setPose(CombatPose pose);
}
