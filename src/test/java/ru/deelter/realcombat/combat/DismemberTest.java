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
	void aFighterLowOnHealthLosesAnArmOrALegButNotTheHead() {
		int arms = 0;
		Random random = new Random(5);
		for (int i = 0; i < 1000; i++) {
			if (Dismember.decide("right_arm", Dir.OVERHEAD, false, 0.3f, compat, random) == Dismember.Cut.SEVER) arms++;
			assertEquals(Dismember.Cut.WOUND, Dismember.decide("head", Dir.OVERHEAD, false, 0.3f, compat, random));
			assertEquals(Dismember.Cut.WOUND, Dismember.decide("body", Dir.UNDER, false, 0.1f, compat, random));
		}
		assertEquals(300, arms, 50);
		// above the share the limbs stay on
		for (int i = 0; i < 100; i++) {
			assertEquals(Dismember.Cut.WOUND, Dismember.decide("left_arm", Dir.LEFT, false, 0.6f, compat, new Random(i)));
		}
	}

	@Test
	void fatalitiesOnlyFinish() {
		int falling = 0, ground = 0, waist = 0;
		Random random = new Random(9);
		for (int i = 0; i < 1000; i++) {
			if (Dismember.decide("body", Dir.OVERHEAD, true, 0, false, true, compat, random) == Dismember.Cut.SPLIT) falling++;
			if (Dismember.decide("body", Dir.OVERHEAD, true, 0, false, false, compat, random) == Dismember.Cut.SPLIT) ground++;
			if (Dismember.decide("body", Dir.RIGHT, true, 0, true, false, compat, random) == Dismember.Cut.HALVE) waist++;
			// the living are never cleaved, however low
			Dismember.Cut living = Dismember.decide("body", Dir.OVERHEAD, false, 0.01f, false, true, compat, random);
			assertEquals(Dismember.Cut.WOUND, living);
		}
		assertEquals(850, falling, 50);
		assertEquals(200, ground, 50);
		assertEquals(200, waist, 50);
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
	void aSideSlashTakesTheLegsOfTheNearlyDeadButTheHeadOnlyWithTheLife() {
		int legs = 0;
		Random random = new Random(3);
		for (int i = 0; i < 1000; i++) {
			assertEquals(Dismember.Cut.WOUND, Dismember.decide("head", Dir.RIGHT, false, 0.1f, true, compat, random));
			if (Dismember.decide("right_leg", Dir.LEFT, false, 0.3f, true, compat, random) == Dismember.Cut.SEVER) legs++;
		}
		assertEquals(400, legs, 50);
		assertEquals(Dismember.Cut.WOUND, Dismember.decide("right_leg", Dir.RIGHT, false, 0.9f, true, compat, new Random(1)));
	}

	@Test
	void aVrBladeTakesLimbsOffTheLivingLessOften() {
		int desktop = 0, vr = 0;
		Random random = new Random(11);
		for (int i = 0; i < 2000; i++) {
			if (Dismember.decide("right_arm", Dir.OVERHEAD, false, 0.3f, false, false, false, compat, random) == Dismember.Cut.SEVER) desktop++;
			if (Dismember.decide("right_arm", Dir.OVERHEAD, false, 0.3f, false, false, true, compat, random) == Dismember.Cut.SEVER) vr++;
		}
		assertEquals(600, desktop, 70);
		assertEquals(180, vr, 45);
		// a killing VR blow is not made rarer
		assertEquals(Dismember.Cut.HEAD, Dismember.decide("head", Dir.RIGHT, true, 0, false, false, true, compat, new Random(1)));
	}
}
