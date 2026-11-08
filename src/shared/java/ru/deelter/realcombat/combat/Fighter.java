package ru.deelter.realcombat.combat;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * The combat state of one fighter: a player on the server, the player's own prediction on his client, or the
 * sparring partner of the duel command. Changed by {@link Rules} only.
 */
@Getter
@Setter
public final class Fighter {
	private Phase phase = Phase.IDLE;
	private Dir dir = Dir.RIGHT;
	/**
	 * ticks into the phase
	 */
	private int ticks;
	/**
	 * ticks the phase lasts
	 */
	private int length = 1;
	private float stamina;
	/**
	 * this swing is a riposte: faster, and it can't be feinted
	 */
	private boolean riposte;
	/**
	 * the last swing hit something: the recovery is short and a next swing may start in it
	 */
	private boolean combo;
	/**
	 * ticks left after a parry in which a swing is a riposte
	 */
	private int riposteWindow;
	/**
	 * the swing was feinted once: a second feint is not allowed
	 */
	private boolean feinted;
	/**
	 * ticks until stamina comes back after it was spent
	 */
	private int rest;
	/**
	 * entities this swing already hit
	 */
	private final Set<Integer> struck = new HashSet<>();
	/**
	 * changed since it was last sent
	 */
	private boolean dirty;
	/**
	 * changed by something the fighter did not do himself (a parry, a hit, a kick): his own client must hear about it
	 */
	private boolean forced;
	/**
	 * a Vivecraft player: his swings are his hands, not phases
	 */
	private boolean vr;
	/**
	 * what the fighter holds: set each tick from the hand
	 */
	private Weapon weapon = Weapon.SWORD;
	/**
	 * a weapon in each hand: the hands take turns in a string of swings. Set each tick from the hands
	 */
	private boolean dual;
	/**
	 * the swing now (or the last one) is the other hand's
	 */
	private boolean offhand;

	public Fighter(float stamina) {
		this.stamina = stamina;
	}

	public void enter(Phase phase, int length) {
		this.phase = phase;
		this.length = Math.max(1, length);
		this.ticks = 0;
		this.dirty = true;
	}

	/**
	 * 0 at the start of the phase, 1 at its end
	 */
	public float progress() {
		return Math.min(1f, ticks / (float) length);
	}

	/**
	 * the progress the phase had a tick ago
	 */
	public float previousProgress() {
		return Math.max(0f, (ticks - 1) / (float) length);
	}

	public boolean busy() {
		return phase != Phase.IDLE;
	}
}
