package ru.deelter.realcombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.client.CombatPose;
import ru.deelter.realcombat.client.Poses;

/**
 * The first person sword follows the swing with key frames of its own ({@link Poses#hand}). 26.3 draws the first
 * person hands in a renderer of their own.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void realcombat$swing(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || arm != ru.deelter.realcombat.client.ClientCombat.armOf(mc.player)) return;
		CombatPose pose = ClientCombat.poseOf(mc.player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		if (pose == null) return;
		pose = pose.forArm(arm);
		Poses.Hand hand = Poses.hand(pose);
		float mirror = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(hand.tx() * mirror, hand.ty(), hand.tz());
		poseStack.rotate(Axis.XP.rotationDegrees(hand.rx()));
		poseStack.rotate(Axis.YP.rotationDegrees(hand.ry() * mirror));
		poseStack.rotate(Axis.ZP.rotationDegrees(hand.rz() * mirror));
		float roll = Poses.roll(pose);
		if (roll != 0) poseStack.rotate(Poses.FIRST_PERSON_BLADE.rotation(roll * mirror));
	}

	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void realcombat$kickLeg(float partial, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.PlayerRenderState player, net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		poseStack.pushPose();
		try {
			var leg = ru.deelter.realcombat.client.FirstPersonKick.leg(poseStack, partial);
			if (leg != null) collector.submitModelPart(leg, poseStack, ru.deelter.realcombat.client.FirstPersonKick.type(), player.avatarRenderState == null ? 0xF000F0 : player.avatarRenderState.lightCoords,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, (net.minecraft.client.renderer.texture.UvMapping) null);
		} finally {
			poseStack.popPose();
		}
	}
}
