package ru.deelter.realcombat.client;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.client.VRRenderingAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import ru.deelter.realcombat.compat.Vivecraft;

/**
 * The client half of Vivecraft: whether this client plays in VR, where its hands are for drawing, and the buzz of a
 * controller. Same rules as {@link Vivecraft}: nothing of Vivecraft is loaded unless it is there.
 */
public final class VrClient {
	private static boolean broken;

	private VrClient() {
	}

	public static boolean active() {
		if (!Vivecraft.LOADED || broken) return false;
		try {
			return Api.active();
		} catch (Throwable e) {
			broken = true;
			return false;
		}
	}

	/**
	 * the own main hand as drawn this frame
	 */
	public static Vivecraft.@Nullable Hand renderHand() {
		if (!active()) return null;
		try {
			return Api.ownHand();
		} catch (Throwable e) {
			broken = true;
			return null;
		}
	}

	/**
	 * another VR player's main hand as drawn this frame
	 */
	public static Vivecraft.@Nullable Hand renderHand(Player player) {
		if (!Vivecraft.LOADED || broken) return null;
		try {
			return Api.hand(VRRenderingAPI.instance().getWorldRenderPose(player));
		} catch (Throwable e) {
			broken = true;
			return null;
		}
	}

	/**
	 * One frame of the own body as tracked: head and both hands, positions in the world and rotations.
	 *
	 * @param head  x, y, z, then the rotation as a quaternion x, y, z, w
	 * @param main  the same for the main hand
	 * @param off   the same for the other hand
	 * @param scale Vivecraft's world scale
	 */
	public record Sample(double[] head, double[] main, double[] off, double[] sword, float scale, boolean seated, boolean leftHanded) {
	}

	public static @Nullable Sample sample() {
		if (!active()) return null;
		try {
			return Api.sample();
		} catch (Throwable e) {
			broken = true;
			return null;
		}
	}

	/**
	 * where a player's head looks, [yaw, pitch], on the client thread: the own head in VR, another VR player's head
	 */
	public static float @Nullable [] view(net.minecraft.world.entity.Entity entity) {
		if (!Vivecraft.LOADED || broken || !net.minecraft.client.Minecraft.getInstance().isSameThread()) return null;
		if (!(entity instanceof Player player)) return null;
		try {
			Vivecraft.Hand head = player == net.minecraft.client.Minecraft.getInstance().player
					? (active() ? Api.head(VRClientAPI.instance().getWorldRenderPose()) : null)
					: Api.head(VRRenderingAPI.instance().getWorldRenderPose(player));
			return head == null ? null : ru.deelter.realcombat.combat.Sweep.yawPitch(head.dir());
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * the own head as drawn this frame
	 */
	public static Vivecraft.@Nullable Hand head() {
		if (!active()) return null;
		try {
			return Api.head(VRClientAPI.instance().getWorldRenderPose());
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * buzzes the sword hand, harder for a harder knock
	 */
	public static void buzz(float strength) {
		if (!active()) return;
		try {
			Api.buzz(strength);
		} catch (Throwable e) {
			broken = true;
		}
	}

	private static final class Api {
		static boolean active() {
			return VRClientAPI.instance().isVRActive();
		}

		/**
		 * the own main hand: the position of the controller, the axis the sword is drawn along (Vivecraft's hand
		 * pose, as its own swing tracker takes it; the API gives the controller's aim, at an angle to the sword)
		 */
		static Vivecraft.Hand ownHand() {
			VRPose pose = VRClientAPI.instance().getWorldRenderPose();
			if (pose == null || pose.getMainHand() == null) return null;
			var vr = org.vivecraft.client_vr.ClientDataHolderVR.getInstance().vrPlayer;
			if (vr == null || vr.vrdata_world_render == null) return hand(pose);
			var sword = vr.vrdata_world_render.getHand(0).getCustomVector(org.vivecraft.common.utils.MathUtils.BACK);
			return new Vivecraft.Hand(pose.getMainHand().getPos(), new net.minecraft.world.phys.Vec3(sword.x(), sword.y(), sword.z()));
		}

		static Vivecraft.Hand head(VRPose pose) {
			if (pose == null || pose.getHead() == null) return null;
			return new Vivecraft.Hand(pose.getHead().getPos(), pose.getHead().getDir());
		}

		static Vivecraft.Hand hand(VRPose pose) {
			if (pose == null) return null;
			VRBodyPartData hand = pose.getMainHand();
			return hand == null ? null : new Vivecraft.Hand(hand.getPos(), hand.getDir());
		}

		static Sample sample() {
			VRClientAPI api = VRClientAPI.instance();
			VRPose pose = api.getWorldRenderPose();
			if (pose == null) return null;
			Vivecraft.Hand own = ownHand();
			double[] sword = own == null ? null : new double[]{own.dir().x, own.dir().y, own.dir().z};
			return new Sample(part(pose.getHead()), part(pose.getMainHand()), part(pose.getOffHand()), sword, api.getWorldScale(), api.isSeated(), api.isLeftHanded());
		}

		private static double[] part(VRBodyPartData data) {
			if (data == null) return null;
			var p = data.getPos();
			var q = data.getRotation();
			return new double[]{p.x, p.y, p.z, q.x(), q.y(), q.z(), q.w()};
		}

		static void buzz(float strength) {
			VRClientAPI.instance().triggerHapticPulse(VRBodyPart.MAIN_HAND, Math.clamp(0.02f + strength * 0.08f, 0.02f, 0.15f));
		}
	}
}
