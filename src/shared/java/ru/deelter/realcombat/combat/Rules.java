package ru.deelter.realcombat.combat;

import ru.deelter.realcombat.config.CombatConfig;

/**
 * The rules of a fight, the same on the server, in a client's prediction and for the sparring partner. Nothing here
 * knows about Minecraft: each call takes the fighters and the settings and changes the fighters.
 */
public final class Rules {
	private Rules() {
	}

	/**
	 * Starts a swing, or changes the direction of one still early in its windup (a morph). A swing may start when idle,
	 * late in a recovery, at once after a hit (a combo) or after a parry (a riposte).
	 */
	public static boolean attack(Fighter f, Dir dir, CombatConfig.Rules r, double speed) {
		switch (f.getPhase()) {
			case IDLE -> {
			}
			case RECOVERY -> {
				if (!f.isCombo() && f.getTicks() < f.getLength() * 0.6f) return false;
			}
			case WINDUP -> {
				if (f.isRiposte() || dir == f.getDir() || f.progress() > r.getMorphWindow()) return false;
				f.setDir(dir);
				f.setDirty(true);
				return true;
			}
			default -> {
				return false;
			}
		}
		boolean riposte = f.getRiposteWindow() > 0;
		// two weapons: a swing out of the last one's recovery is the other hand's, and quicker; a fresh one starts with
		// the main hand. Each costs more and does less, so two blades are a faster string, not a stronger one
		boolean chained = f.isDual() && f.getPhase() == Phase.RECOVERY && !f.isFeinted();
		f.setOffhand(chained && !f.isOffhand());
		// a tired fighter swings slower; a swing costs stamina, a heavy weapon's more
		boolean tired = tired(f, r);
		spend(f, r.getAttackStamina() * f.getWeapon().swingCost * (f.isDual() ? r.getDualStamina() : 1), r);
		f.setDir(dir);
		f.setRiposte(riposte);
		f.setRiposteWindow(0);
		f.setCombo(false);
		f.setFeinted(false);
		f.getStruck().clear();
		int windup = riposte ? r.getRiposteWindup() : dir == Dir.STAB ? r.getStabWindup() : r.getWindup();
		int ticks = r.scaled(windup, speed);
		if (chained && !riposte) ticks = Math.max(2, Math.round(ticks * r.getDualChainWindup()));
		f.enter(Phase.WINDUP, ticks + (tired ? r.getTiredWindup() : 0));
		return true;
	}

	/**
	 * the share of a single weapon's damage a blow of this fighter does: less with two weapons
	 */
	public static float damageShare(Fighter f, CombatConfig.Rules r) {
		return f.isDual() ? r.getDualDamage() : 1;
	}

	/**
	 * low on stamina: slower windups, no feints and no chambers
	 */
	public static boolean tired(Fighter f, CombatConfig.Rules r) {
		return f.getStamina() < r.getTiredStamina();
	}

	/**
	 * Cancels a windup to throw the opponent's parry. A riposte can't be feinted, nor a swing twice.
	 */
	public static boolean feint(Fighter f, CombatConfig.Rules r) {
		if (f.getPhase() != Phase.WINDUP || f.isRiposte() || f.isFeinted() || f.progress() > r.getFeintWindow() || tired(f, r)) return false;
		if (f.getStamina() < r.getFeintStamina()) return false;
		spend(f, r.getFeintStamina(), r);
		f.setFeinted(true);
		f.enter(Phase.RECOVERY, r.getFeintRecovery());
		// the next swing may start at once
		f.setCombo(true);
		return true;
	}

	/**
	 * Raises the blade. From a windup it is a feint into a parry and costs a feint.
	 */
	public static boolean parry(Fighter f, CombatConfig.Rules r) {
		switch (f.getPhase()) {
			case IDLE, RECOVERY -> {
			}
			case WINDUP -> {
				if (f.isRiposte() || f.getStamina() < r.getFeintStamina()) return false;
				spend(f, r.getFeintStamina(), r);
			}
			default -> {
				return false;
			}
		}
		f.setRiposte(false);
		f.setOffhand(false);
		f.enter(Phase.PARRY, Math.max(2, r.getParry() - (f.isDual() ? r.getDualParryLoss() : 0)));
		return true;
	}

	public static boolean kick(Fighter f, CombatConfig.Rules r) {
		if (f.getPhase() != Phase.IDLE && !(f.getPhase() == Phase.RECOVERY && f.getTicks() >= f.getLength() / 2)) return false;
		f.enter(Phase.KICK, r.getKickWindup() + r.getKickRecovery());
		return true;
	}

	/**
	 * the kick lands on this tick
	 */
	public static boolean kickLands(Fighter f, CombatConfig.Rules r) {
		return f.getPhase() == Phase.KICK && f.getTicks() == r.getKickWindup();
	}

	public static void tick(Fighter f, CombatConfig.Rules r, double speed) {
		f.setTicks(f.getTicks() + 1);
		if (f.getRiposteWindow() > 0) f.setRiposteWindow(f.getRiposteWindow() - 1);
		if (f.getRest() > 0) f.setRest(f.getRest() - 1);
		else if (f.getPhase() == Phase.IDLE || f.getPhase() == Phase.RECOVERY) {
			f.setStamina(Math.min(r.getMaxStamina(), f.getStamina() + r.getStaminaRegen()));
		}
		if (f.getTicks() < f.getLength()) return;
		switch (f.getPhase()) {
			case IDLE -> f.setTicks(0);
			case WINDUP -> f.enter(Phase.RELEASE, r.scaled(r.getRelease(), speed));
			case RELEASE -> {
				if (f.getStruck().isEmpty() && !f.isCombo()) spend(f, r.getWhiffStamina(), r);
				f.enter(Phase.RECOVERY, r.scaled(f.isCombo() ? r.getComboRecovery() : r.getRecovery(), speed));
			}
			case PARRY -> f.enter(Phase.PARRY_RECOVERY, r.getParryRecovery());
			default -> {
				f.enter(Phase.IDLE, 1);
				f.setRiposte(false);
				f.setCombo(false);
			}
		}
	}

	public static void stun(Fighter f, int ticks) {
		f.setRiposte(false);
		f.setCombo(false);
		f.setRiposteWindow(0);
		f.enter(Phase.STUN, ticks);
		f.setForced(true);
	}

	/**
	 * A blow of {@code attacker} reaches its target.
	 *
	 * @param defender the target's state, null for a target that does not fight by these rules (a mob)
	 * @param facing   the target looks towards the attacker, within the guard arc
	 * @param shield   the target holds a raised shield
	 * @param guarding a Vivecraft target holds his blade across the blow
	 */
	public static Outcome blow(Fighter attacker, Fighter defender, boolean facing, boolean shield, boolean guarding,
							   float damage, CombatConfig.Rules r) {
		if (defender != null && facing) {
			if (defender.getPhase() == Phase.PARRY || guarding) {
				recoil(attacker, r);
				spend(defender, r.getParryStamina() * attacker.getWeapon().parryCost * Math.max(0.5f, damage / 7f), r);
				if (defender.getStamina() <= 0) {
					defender.setStamina(0);
					stun(defender, r.getParryBreakStun());
					return Outcome.PARRY_BROKEN;
				}
				// a parry that caught a blow ends at once, and the defender may riposte
				defender.enter(Phase.IDLE, 1);
				defender.setRiposteWindow(r.getRiposteWindow());
				defender.setForced(true);
				return Outcome.PARRIED;
			}
			if (defender.getPhase() == Phase.WINDUP && defender.getDir() == attacker.getDir()
					&& defender.getTicks() <= r.getChamberWindow() && !tired(defender, r)) {
				recoil(attacker, r);
				spend(defender, r.getChamberStamina(), r);
				// the chamber turns into a riposte: it can't be feinted and goes on
				defender.setRiposte(true);
				defender.setForced(true);
				return Outcome.CHAMBERED;
			}
		}
		if (shield && facing) {
			recoil(attacker, r);
			// a heavy blow knocks the shield out of the way
			if (attacker.getWeapon().breaksShield) {
				if (defender != null) stun(defender, r.getShieldBreakStun());
				return Outcome.PARRY_BROKEN;
			}
			if (defender != null) {
				spend(defender, r.getShieldStamina() * attacker.getWeapon().parryCost * Math.max(0.5f, damage / 7f), r);
				if (defender.getStamina() <= 0) {
					defender.setStamina(0);
					stun(defender, r.getParryBreakStun());
					return Outcome.PARRY_BROKEN;
				}
			}
			return Outcome.BLOCKED;
		}
		attacker.setCombo(true);
		// his client predicts the swing and does not know it landed: tell it, for the short recovery of a combo
		attacker.setForced(true);
		attacker.setDirty(true);
		attacker.setStamina(Math.min(r.getMaxStamina(), attacker.getStamina() + r.getHitStamina()));
		// a hit interrupts the target's windup (a flinch); a release goes on, both blows trade
		if (defender != null && (defender.getPhase() == Phase.WINDUP || defender.getPhase() == Phase.KICK)) {
			stun(defender, r.getFlinch());
		}
		return Outcome.HIT;
	}

	/**
	 * A kick reaches its target: a parry, a shield or a windup is broken and costs stamina.
	 */
	public static Outcome kick(Fighter defender, boolean shield, CombatConfig.Rules r) {
		if (defender == null) return Outcome.KICKED;
		boolean guarded = shield || defender.getPhase() == Phase.PARRY || defender.getPhase() == Phase.WINDUP;
		spend(defender, (guarded ? r.getKickStamina() : r.getKickStamina() / 3), r);
		stun(defender, guarded ? r.getKickStun() : r.getKickStun() / 2);
		return Outcome.KICKED;
	}

	/**
	 * Takes stamina; it comes back only after a pause, so a string of parries runs out, as in Mordhau.
	 */
	private static void spend(Fighter f, float amount, CombatConfig.Rules r) {
		f.setStamina(Math.max(0, f.getStamina() - amount));
		f.setRest(r.getStaminaDelay());
	}

	/**
	 * Two blades met (a VR blade against another blade): a swing one of them was making is stopped as by a parry.
	 */
	public static void clash(Fighter f, CombatConfig.Rules r) {
		if (f != null && f.getPhase() == Phase.RELEASE) recoil(f, r);
	}

	private static void recoil(Fighter attacker, CombatConfig.Rules r) {
		spend(attacker, r.getParriedStamina(), r);
		attacker.setCombo(false);
		attacker.setRiposte(false);
		attacker.enter(Phase.RECOVERY, r.getParriedRecoil());
		attacker.setForced(true);
	}
}
