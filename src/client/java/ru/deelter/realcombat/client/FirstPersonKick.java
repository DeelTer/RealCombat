package ru.deelter.realcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;
import ru.deelter.realcombat.combat.Phase;

/**
 * In first person a kick shows the leg: it comes up from the bottom of the screen and goes back, so the player sees
 * he kicks. A leg of the player's own model, in his skin, drawn after the hands; the version mixins submit it.
 */
public final class FirstPersonKick {
	private static ModelPart right, left;

	private FirstPersonKick() {
	}

	/**
	 * the leg to draw now, posed and placed on {@code poseStack}; null when no kick is under way
	 */
	public static ModelPart leg(PoseStack poseStack, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return null;
		CombatPose pose = ClientCombat.poseOf(mc.player, partial);
		if (pose == null || pose.phase() != Phase.KICK) return null;
		float lift = (float) Math.sin(Math.min(1f, pose.progress() * 1.6f) * Math.PI);
		if (lift <= 0.02f) return null;
		if (right == null) {
			ModelPart root = mc.getEntityModels().bakeLayer(ModelLayers.PLAYER);
			right = root.getChild("right_leg");
			left = root.getChild("left_leg");
		}
		boolean leftLeg = mc.player.getMainArm() == HumanoidArm.LEFT;
		ModelPart leg = leftLeg ? left : right;
		leg.resetPose();
		leg.x = 0;
		leg.y = 0;
		leg.z = 0;
		// the hip a little below and behind the eyes; the leg swings up and forward into the bottom of the view
		float side = leftLeg ? -1 : 1;
		poseStack.translate(0.24f * side, -0.25f - 0.25f * (1 - lift), 0.29f);
		// the model's down is the view's down, its front the view's front
		poseStack.scale(-1, -1, 1);
		leg.xRot = -1.45f * lift;
		leg.zRot = 0.12f * side * lift;
		return leg;
	}

	public static RenderType type() {
		Minecraft mc = Minecraft.getInstance();
		return RenderTypes.entityCutout(mc.player.getSkin().body().texturePath());
	}
}
