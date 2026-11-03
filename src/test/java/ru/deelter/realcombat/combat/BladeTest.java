package ru.deelter.realcombat.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BladeTest {
	private static final Blade.V UP = new Blade.V(0, 1, 0);

	/**
	 * a blade held upright, swung sideways along x
	 */
	private static Blade upright(double x) {
		Blade blade = new Blade(1, 0.05);
		blade.place(new Blade.V(x, 0, 0), UP);
		return blade;
	}

	@Test
	void stopsAtABody() {
		Blade blade = upright(0);
		Blade.Box body = new Blade.Box(1, -1, -0.5, 2, 2, 0.5);
		Blade.Result result = blade.step(new Blade.V(1.5, 0, 0), UP, List.of(body), List.of(), List.of(), 1, 2);
		assertEquals(Blade.Contact.BODY, result.contact());
		assertTrue(blade.getHand().x() < 1, "the hand stays out of the body: " + blade.getHand());
		assertTrue(result.speed() > 0.5, "the blade came in fast: " + result.speed());
	}

	@Test
	void bladesDoNotPassEachOther() {
		Blade blade = upright(0);
		// another blade lying across the way at x = 0.6
		Blade.Segment guard = new Blade.Segment(new Blade.V(0.6, 0.5, -1), new Blade.V(0.6, 0.5, 1), 0.05);
		Blade.Result result = blade.step(new Blade.V(1.2, 0, 0), UP, List.of(), List.of(guard), List.of(), 1, 2);
		assertEquals(Blade.Contact.BLADE, result.contact());
		assertTrue(blade.getHand().x() < 0.6, "stopped before the other blade: " + blade.getHand());
	}

	@Test
	void slidesAlongABlade() {
		Blade blade = upright(0);
		// a blade lying along z at x = 0.6: a hand moving to +x and +z is stopped in x but keeps going along it
		Blade.Segment guard = new Blade.Segment(new Blade.V(0.6, 0.5, -3), new Blade.V(0.6, 0.5, 3), 0.05);
		Blade.Result result = blade.step(new Blade.V(1.2, 0, 1), UP, List.of(), List.of(guard), List.of(), 1, 5);
		assertEquals(Blade.Contact.BLADE, result.contact());
		assertTrue(blade.getHand().x() < 0.6, "not through the blade: " + blade.getHand());
		assertTrue(blade.getHand().z() > 0.8, "slid along it: " + blade.getHand());
	}

	@Test
	void stuckBladeMovesOnlyAlongItself() {
		Blade blade = new Blade(1, 0.05);
		Blade.V forward = new Blade.V(1, 0, 0);
		blade.place(Blade.V.ZERO, forward);
		blade.lodge(0.4);
		// pushed in and to the side: it goes in, as deep as it may, and not sideways
		Blade.Result in = blade.step(new Blade.V(0.8, 0, 0.3), forward, List.of(), List.of(), List.of(), 1, 1);
		assertEquals(Blade.Contact.LODGED, in.contact());
		assertEquals(0.4, blade.getHand().x(), 1e-9);
		assertEquals(0, blade.getHand().z(), 1e-9);
		// the body steps aside: the blade goes with it
		blade.shift(new Blade.V(0, 0, 0.5));
		assertEquals(0.5, blade.getHand().z(), 1e-9);
		// drawn back: out
		Blade.Result out = blade.step(new Blade.V(-0.5, 0, 0.5), forward, List.of(), List.of(), List.of(), 1, 1);
		assertEquals(Blade.Contact.FREED, out.contact());
		assertFalse(blade.isLodged());
	}

	@Test
	void pulledFreeWhenHeldBackTooFar() {
		Blade blade = upright(0);
		Blade.Box body = new Blade.Box(0.3, -1, -0.5, 2, 2, 0.5);
		blade.step(new Blade.V(1.5, 0, 0), UP, List.of(body), List.of(), List.of(), 1, 0.5);
		assertTrue(blade.isSlipped());
		assertEquals(1.5, blade.getHand().x(), 1e-9);
	}

	@Test
	void freeWayFollowsTheController() {
		Blade blade = upright(0);
		Blade.Result result = blade.step(new Blade.V(0.8, 0, 0), UP, List.of(), List.of(), List.of(), 0.5, 2);
		assertEquals(Blade.Contact.NONE, result.contact());
		assertEquals(0.4, blade.getHand().x(), 1e-9);
		assertFalse(blade.isSlipped());
	}

	@Test
	void segmentDistance() {
		Blade.V o = Blade.V.ZERO;
		assertEquals(1, Blade.distance(o, new Blade.V(0, 0, 1), new Blade.V(1, 0, 0), new Blade.V(1, 0, 1)), 1e-9);
		assertEquals(0, Blade.distance(new Blade.V(-1, 0, 0), new Blade.V(1, 0, 0), new Blade.V(0, -1, 0), new Blade.V(0, 1, 0)), 1e-9);
	}
}
