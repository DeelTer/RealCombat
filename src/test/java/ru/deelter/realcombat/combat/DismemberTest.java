package ru.deelter.realcombat.combat;

import org.junit.jupiter.api.Test;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DismemberTest {
	private final CombatConfig.Compat compat = new CombatConfig.Compat();

	@Test
	void killingSlashAtTheHeadTakesIt() {
		assertEquals(Dismember.Cut.HEAD, Dismember.decide("head", Dir.RIGHT, true, 0, compat, new Random(1)));
		assertEquals(Dismember.Cut.HEAD, Dismember.decide("head", Dir.OVERHEAD, true, 0, compat, new Random(1)));
	}

	@Test
	void thrustsAndTheBodyOnlyWound() {
		assertEquals(Dismember.Cut.WOUND, Dismember.decide("head", Dir.STAB, true, 0, compat, new Random(1)));
		assertEquals(Dismember.Cut.WOUND, Dismember.decide("body", Dir.OVERHEAD, true, 0, compat, new Random(1)));
	}

	@Test
	void theLivingKeepTheirLimbsByDefault() {
		for (int i = 0; i < 100; i++) {
			assertEquals(Dismember.Cut.WOUND, Dismember.decide("right_arm", Dir.LEFT, false, 0.05f, compat, new Random(i)));
		}
	}

	@Test
	void killingBlowsMostlyTakeTheLimb() {
		int severed = 0;
		Random random = new Random(7);
		for (int i = 0; i < 1000; i++) {
			if (Dismember.decide("left_leg", Dir.UNDER, true, 0, compat, random) == Dismember.Cut.SEVER) severed++;
		}
		assertEquals(750, severed, 50);
	}

	@Test
	void aStrongVrSideSlashTakesTheNeckAndTheLegsOfTheNearlyDead() {
		int heads = 0, legs = 0;
		Random random = new Random(3);
		for (int i = 0; i < 1000; i++) {
			if (Dismember.decide("head", Dir.RIGHT, false, 0.3f, true, compat, random) == Dismember.Cut.HEAD) heads++;
			if (Dismember.decide("right_leg", Dir.LEFT, false, 0.3f, true, compat, random) == Dismember.Cut.SEVER) legs++;
		}
		assertEquals(400, heads, 50);
		assertEquals(400, legs, 50);
		// healthy, it stays a wound
		assertEquals(Dismember.Cut.WOUND, Dismember.decide("head", Dir.RIGHT, false, 0.9f, true, compat, new Random(1)));
	}
}
