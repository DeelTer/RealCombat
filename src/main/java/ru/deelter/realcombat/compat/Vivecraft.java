package ru.deelter.realcombat.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import ru.deelter.realcombat.RealCombat;

/**
 * Vivecraft, optional. Its API classes are only touched from the nested holders, which the JVM loads on their first
 * use, after {@link #LOADED} said Vivecraft is there; without it nothing of Vivecraft is ever looked up. An API that is
 * not as expected turns the support off with one line in the log.
 */
public final class Vivecraft {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("vivecraft");
	private static boolean broken;

	private Vivecraft() {
	}

	/**
	 * A hand of a VR player: where the controller is and where it points.
	 */
	public record Hand(Vec3 pos, Vec3 dir) {
	}

	public static boolean isVrPlayer(Player player) {
		if (!LOADED || broken) return false;
		try {
			return Common.isVrPlayer(player);
		} catch (Throwable e) {
			fail(e);
			return false;
		}
	}

	public static @Nullable Hand mainHand(Player player) {
		if (!LOADED || broken) return null;
		try {
			return Common.mainHand(player);
		} catch (Throwable e) {
			fail(e);
			return null;
		}
	}

	/**
	 * the head of a VR player: where it is and where it looks
	 */
	public static @Nullable Hand head(Player player) {
		if (!LOADED || broken) return null;
		try {
			return Common.head(player);
		} catch (Throwable e) {
			fail(e);
			return null;
		}
	}

	static void fail(Throwable e) {
		if (broken) return;
		broken = true;
		RealCombat.LOG.warn("Vivecraft's API is not as expected, VR combat is off: {}", e.toString());
	}

	public static boolean broken() {
		return broken;
	}

	private static final class Common {
		static boolean isVrPlayer(Player player) {
			return VRAPI.instance().isVRPlayer(player);
		}

		static Hand head(Player player) {
			if (!VRAPI.instance().isVRPlayer(player)) return null;
			VRPose pose = VRAPI.instance().getVRPose(player);
			VRBodyPartData head = pose == null ? null : pose.getHead();
			return head == null ? null : new Hand(head.getPos(), head.getDir());
		}

		static Hand mainHand(Player player) {
			VRPose pose = VRAPI.instance().getVRPose(player);
			if (pose == null) return null;
			VRBodyPartData hand = pose.getMainHand();
			return hand == null ? null : new Hand(hand.getPos(), hand.getDir());
		}
	}
}
