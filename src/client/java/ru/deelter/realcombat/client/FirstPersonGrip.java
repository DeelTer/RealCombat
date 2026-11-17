package ru.deelter.realcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * In first person the game draws a held sword without the hand that holds it. In a fight the hand is drawn too,
 * closed round the grip, so the sword sits in it through every swing: the arm of the player's own model in his skin,
 * placed where the sword was just placed. The version mixins note that place and submit the arm.
 */
public final class FirstPersonGrip {
	/**
	 * the grip in the sword's frame (T), and the shoulder the forearm runs to in the view's (R), public for
	 * tuning by eye with the pose gametest ({@code -PgripTune})
	 */
	public static float WIDTH = 0.75f;
	/**
	 * the middle of a sword's handle in its item model (a 16 pixel texture centred on 0): low on the left
	 */
	private static final float HANDLE_X = -0.33f, HANDLE_Y = -0.33f;
	public static float TX = 0, TY = 0, TZ = 0, RX = 0.6f, RY = -0.95f, RZ = 0.15f;
	private static ModelPart wideRight, wideLeft, slimRight, slimLeft;
	private static PoseStack noted;
	private static HumanoidArm notedArm;

	private FirstPersonGrip() {
	}

	/**
	 * Notes where the sword of the swinging arm is drawn this frame.
	 */
	public static void note(PoseStack poseStack, HumanoidArm arm) {
		PoseStack copy = new PoseStack();
		copy.last().set(poseStack.last());
		noted = copy;
		notedArm = arm;
	}

	/**
	 * The arm to draw this frame, placed on {@code poseStack}; null when no sword was noted.
	 */
	public static ModelPart arm(PoseStack poseStack) {
		if (noted == null) return null;
		Minecraft mc = Minecraft.getInstance();
		HumanoidArm arm = notedArm;
		PoseStack held = noted;
		noted = null;
		if (mc.player == null) return null;
		if (wideRight == null) {
			var wide = mc.getEntityModels().bakeLayer(ModelLayers.PLAYER);
			var slim = mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM);
			wideRight = wide.getChild("right_arm");
			wideLeft = wide.getChild("left_arm");
			slimRight = slim.getChild("right_arm");
			slimLeft = slim.getChild("left_arm");
		}
		boolean thin = mc.player.getSkin().model() == PlayerModelType.SLIM;
		boolean left = arm == HumanoidArm.LEFT;
		ModelPart part = left ? (thin ? slimLeft : wideLeft) : (thin ? slimRight : wideRight);
		part.resetPose();
		part.x = 0;
		part.y = -10;
		part.z = 0;
		float mirror = left ? -1 : 1;
		// the fist at the grip of the sword as drawn, in the frame of the view
		org.joml.Matrix4f base = new org.joml.Matrix4f(poseStack.last().pose());
		// the grip as the game draws a sword there: the handle pixel of the texture, through the first person
		// transform of a handheld item (turned 0, -90, 25 degrees, moved 1.13, 3.2, 1.13 pixels, scaled 0.68)
		org.joml.Vector3f handle = new org.joml.Vector3f(HANDLE_X, HANDLE_Y, 0).mul(0.68f);
		new org.joml.Quaternionf().rotationXYZ(0, (float) Math.toRadians(-90 * mirror), (float) Math.toRadians(25 * mirror)).transform(handle);
		handle.add(1.13f / 16 * mirror, 3.2f / 16, 1.13f / 16).add(TX * mirror, TY, TZ);
		org.joml.Vector3f grip = held.last().pose().transformPosition(handle);
		new org.joml.Matrix4f(base).invert().transformPosition(grip);
		// the forearm runs from the fist to the shoulder, low at the side of the view, and is as long as it takes
		org.joml.Vector3f shoulder = new org.joml.Vector3f(RX * mirror, RY, RZ);
		org.joml.Vector3f along = new org.joml.Vector3f(shoulder).sub(grip);
		float length = along.length();
		if (length < 1e-3f) return null;
		poseStack.translate(grip.x, grip.y, grip.z);
		poseStack.rotateAround(new org.joml.Quaternionf().rotationTo(0, -1, 0, along.x, along.y, along.z), 0, 0, 0);
		poseStack.scale(WIDTH, length / 0.75f, WIDTH);
		return part;
	}

	public static RenderType type() {
		return RenderTypes.entityCutout(Minecraft.getInstance().player.getSkin().body().texturePath());
	}
}
