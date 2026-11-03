package ru.deelter.realcombat.combat;

/**
 * Where a fighter is in his swing, parry or kick. Every phase lasts a known number of ticks, so a client can draw it
 * from one message and an opponent can read it.
 */
public enum Phase {
	IDLE,
	/**
	 * the blade goes back: can still change direction (a morph), be feinted or cancelled into a parry
	 */
	WINDUP,
	/**
	 * the blade travels and hurts what it crosses
	 */
	RELEASE,
	/**
	 * the blade comes back; after a hit it is short and the next swing may start (a combo)
	 */
	RECOVERY,
	/**
	 * the blade is up: blows from the front are parried
	 */
	PARRY,
	/**
	 * a parry that caught nothing leaves the fighter open for a while
	 */
	PARRY_RECOVERY,
	/**
	 * a kick: hits at the end of its windup, breaks a parry or a shield
	 */
	KICK,
	/**
	 * flinched, kicked or out of stamina: can do nothing
	 */
	STUN;

	private static final Phase[] VALUES = values();

	public static Phase of(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
	}
}
