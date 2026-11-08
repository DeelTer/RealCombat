package ru.deelter.realcombat.combat;

import org.junit.jupiter.api.Test;
import ru.deelter.realcombat.config.CombatConfig;

import static org.junit.jupiter.api.Assertions.*;

class DualTest {
	private final CombatConfig.Rules r = new CombatConfig.Rules();

	private Fighter fighter(boolean dual) {
		Fighter f = new Fighter(r.getMaxStamina());
		f.setDual(dual);
		return f;
	}

	private static void run(Fighter f, CombatConfig.Rules r, Phase until) {
		for (int i = 0; i < 200 && f.getPhase() != until; i++) Rules.tick(f, r, 1.6);
	}

	private void late(Fighter f) {
		for (int i = 0; i < 100 && f.getPhase() == Phase.RECOVERY && f.getTicks() < f.getLength() * 0.6f; i++) Rules.tick(f, r, 1.6);
	}

	@Test
	void theHandsTakeTurnsInAStringAndAFreshOneStartsWithTheMainHand() {
		Fighter f = fighter(true);
		assertTrue(Rules.attack(f, Dir.RIGHT, r, 1.6));
		assertFalse(f.isOffhand());
		int whole = f.getLength();
		run(f, r, Phase.RECOVERY);
		// late in the recovery: the next swing is the other hand's, and quicker
		late(f);
		assertTrue(Rules.attack(f, Dir.LEFT, r, 1.6));
		assertTrue(f.isOffhand());
		assertTrue(f.getLength() < whole, "a chained windup is shorter");
		run(f, r, Phase.RECOVERY);
		late(f);
		assertTrue(Rules.attack(f, Dir.RIGHT, r, 1.6));
		assertFalse(f.isOffhand(), "back to the main hand");
		// let it all run out: a new string starts with the main hand
		run(f, r, Phase.IDLE);
		assertTrue(Rules.attack(f, Dir.RIGHT, r, 1.6));
		assertFalse(f.isOffhand());
		assertEquals(whole, f.getLength());
	}

	@Test
	void twoBladesDoLessEachCostMoreAndGuardWorse() {
		Fighter one = fighter(false), two = fighter(true);
		Rules.attack(one, Dir.RIGHT, r, 1.6);
		Rules.attack(two, Dir.RIGHT, r, 1.6);
		assertTrue(two.getStamina() < one.getStamina());
		assertEquals(1f, Rules.damageShare(one, r));
		assertTrue(Rules.damageShare(two, r) < 1f);
		Fighter p1 = fighter(false), p2 = fighter(true);
		Rules.parry(p1, r);
		Rules.parry(p2, r);
		assertTrue(p2.getLength() < p1.getLength());
	}

	@Test
	void everyBlowOfAStringCanBeParried() {
		Fighter attacker = fighter(true), defender = fighter(false);
		for (int blow = 0; blow < 3; blow++) {
			late(attacker);
			assertTrue(Rules.attack(attacker, blow % 2 == 0 ? Dir.RIGHT : Dir.LEFT, r, 1.6), "blow " + blow);
			// the defender sees it coming and raises the blade as it is about to land
			while (attacker.getPhase() == Phase.WINDUP && attacker.getLength() - attacker.getTicks() > 2) Rules.tick(attacker, r, 1.6);
			defender.enter(Phase.IDLE, 1);
			assertTrue(Rules.parry(defender, r));
			Outcome outcome = Rules.blow(attacker, defender, true, false, false, 6, r);
			assertNotEquals(Outcome.HIT, outcome, "blow " + blow);
			defender.setStamina(r.getMaxStamina());
			run(attacker, r, Phase.RECOVERY);
		}
	}

	@Test
	void aWindupChangesItsSideOnceOnly() {
		Fighter f = fighter(false);
		assertTrue(Rules.attack(f, Dir.RIGHT, r, 1.6));
		assertTrue(Rules.attack(f, Dir.LEFT, r, 1.6), "the first change of mind");
		assertFalse(Rules.attack(f, Dir.OVERHEAD, r, 1.6), "a third side");
		assertEquals(Dir.LEFT, f.getDir());
		// a new swing may change again
		run(f, r, Phase.IDLE);
		assertTrue(Rules.attack(f, Dir.RIGHT, r, 1.6));
		assertTrue(Rules.attack(f, Dir.UNDER, r, 1.6));
	}

	@Test
	void aParryThatCaughtABlowComesUpAgainAtOnce() {
		Fighter attacker = fighter(true), defender = fighter(false);
		Rules.attack(attacker, Dir.RIGHT, r, 1.6);
		while (attacker.getPhase() == Phase.WINDUP && attacker.getLength() - attacker.getTicks() > 2) Rules.tick(attacker, r, 1.6);
		assertTrue(Rules.parry(defender, r));
		assertNotEquals(Outcome.HIT, Rules.blow(attacker, defender, true, false, false, 6, r));
		run(defender, r, Phase.PARRY_RECOVERY);
		assertTrue(Rules.parry(defender, r), "the next blow of the string is met");
		// a parry that caught nothing waits out its recovery
		Fighter idle = fighter(false);
		Rules.parry(idle, r);
		run(idle, r, Phase.PARRY_RECOVERY);
		assertFalse(Rules.parry(idle, r));
	}
}
