package ru.deelter.realcombat.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.deelter.chopchop.client.api.ChopChopClientApi;

import java.util.ArrayList;
import java.util.List;

/**
 * ChopChop's dead bodies, which live on the client only: a click at one is ChopChop's to cut, and a VR blade meets
 * them, slides on them and cuts them. Nothing of ChopChop is touched unless it is there.
 */
public final class Corpses {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("chopchop");
	private static boolean broken;

	private Corpses() {
	}

	public record Body(int id, AABB box) {
	}

	/**
	 * a dead body is under the crosshair: the click is ChopChop's
	 */
	public static boolean aimed() {
		if (!LOADED || broken) return false;
		try {
			return Api.aimed();
		} catch (Throwable e) {
			broken = true;
			return false;
		}
	}

	public static List<Body> near(AABB area) {
		if (!LOADED || broken) return List.of();
		try {
			return Api.near(area);
		} catch (Throwable e) {
			// a ChopChop from before its client API: no bodies to meet
			broken = true;
			return List.of();
		}
	}

	public static void cut(int id, Vec3 from, Vec3 dir, double length) {
		if (!LOADED || broken) return;
		try {
			Api.cut(id, from, dir, length);
		} catch (Throwable e) {
			broken = true;
		}
	}

	private static boolean noHands;

	/**
	 * the hand on that side of an entity is cut off, as this client draws it (false without ChopChop, or with one
	 * from before it could tell)
	 */
	public static boolean handless(net.minecraft.world.entity.LivingEntity entity, String side) {
		if (!LOADED || noHands) return false;
		try {
			return ChopChopClientApi.isHandless(entity, side);
		} catch (Throwable e) {
			noHands = true;
			return false;
		}
	}

	private static final class Api {
		static boolean aimed() {
			return ChopChopClientApi.corpseUnderCrosshair();
		}

		static List<Body> near(AABB area) {
			List<Body> bodies = new ArrayList<>();
			for (ChopChopClientApi.Corpse corpse : ChopChopClientApi.corpses(area)) bodies.add(new Body(corpse.id(), corpse.box()));
			return bodies;
		}

		static void cut(int id, Vec3 from, Vec3 dir, double length) {
			ChopChopClientApi.cut(id, from, dir, length);
		}
	}
}
