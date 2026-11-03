package ru.deelter.realcombat.combat;

import org.junit.jupiter.api.Test;
import ru.deelter.realcombat.config.CombatConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BladeProxyTest {
	private final CombatConfig.Rules rules = new CombatConfig.Rules();

	@Test
	void parryLiesAcrossTheFace() {
		Fighter f = new Fighter(100);
		f.enter(Phase.PARRY, 5);
		// looking south (+z): the blade lies along x, in front of the eyes
		Blade.Segment s = BladeProxy.of(0, 0, 0, 0, 0, f, rules, 0.06);
		assertEquals(0.6, s.a().z(), 1e-9);
		assertEquals(0.6, s.b().z(), 1e-9);
		assertEquals(0.9, Math.abs(s.a().x() - s.b().x()), 1e-9);
	}

	@Test
	void slashFromTheRightStartsOnTheRight() {
		Fighter f = new Fighter(100);
		f.setDir(Dir.RIGHT);
		f.enter(Phase.RELEASE, 4);
		// at the start of the release, looking south: the right hand side is west, -x
		Blade.Segment s = BladeProxy.of(0, 0, 0, 0, 0, f, rules, 0.06);
		assertTrue(s.b().x() < s.a().x() - 0.5, "the blade points to the right: " + s);
	}

	@Test
	void noBladeWhenIdle() {
		assertNull(BladeProxy.of(0, 0, 0, 0, 0, new Fighter(100), rules, 0.06));
	}
}
