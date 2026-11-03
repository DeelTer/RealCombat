package ru.deelter.realcombat.combat;

/**
 * What became of a blow that reached its target.
 */
public enum Outcome {
	HIT,
	/**
	 * the target parried; it may riposte now
	 */
	PARRIED,
	/**
	 * the target answered with the same blow at the right moment: the attack is parried and his goes on
	 */
	CHAMBERED,
	/**
	 * taken on a shield
	 */
	BLOCKED,
	/**
	 * parried, but the parry cost the last stamina: the defender is stunned
	 */
	PARRY_BROKEN,
	/**
	 * kicked out of a parry, a shield or a windup
	 */
	KICKED
}
