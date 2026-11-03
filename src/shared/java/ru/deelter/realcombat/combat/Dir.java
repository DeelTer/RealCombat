package ru.deelter.realcombat.combat;

/**
 * Where a blow comes from, as the attacker sees it. Mordhau has eight angles; four slashes and a stab read well enough
 * on a blocky arm and are easy to pick with the mouse.
 */
public enum Dir {
	/**
	 * from above, down onto the head
	 */
	OVERHEAD(1.15f),
	/**
	 * from below, up into the body
	 */
	UNDER(0.9f),
	/**
	 * from the attacker's right to his left
	 */
	RIGHT(1f),
	/**
	 * from the attacker's left to his right
	 */
	LEFT(1f),
	/**
	 * straight ahead
	 */
	STAB(0.95f);

	private static final Dir[] VALUES = values();

	/**
	 * how hard it hits, against a slash
	 */
	public final float damage;

	Dir(float damage) {
		this.damage = damage;
	}

	public static Dir of(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RIGHT;
	}

	public boolean slash() {
		return this != STAB;
	}

	/**
	 * the blade sweeps sideways, not up and down
	 */
	public boolean horizontal() {
		return this == RIGHT || this == LEFT;
	}
}
