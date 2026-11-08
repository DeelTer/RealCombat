package ru.deelter.realcombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.client.CombatPose;
import ru.deelter.realcombat.client.Poses;

/**
 * The first person sword follows the swing with key frames of its own ({@link Poses#hand}).
 */
@Mixin(ItemInHandRenderer.class)
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
		poseStack.mulPose(Axis.XP.rotationDegrees(hand.rx()));
		poseStack.mulPose(Axis.YP.rotationDegrees(hand.ry() * mirror));
		poseStack.mulPose(Axis.ZP.rotationDegrees(hand.rz() * mirror));
		float roll = Poses.roll(pose);
		if (roll != 0) poseStack.mulPose(Poses.FIRST_PERSON_BLADE.rotation(roll * mirror));
	}

	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void realcombat$kickLeg(float partial, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.player.LocalPlayer player, int lightCoords, CallbackInfo ci) {
		poseStack.pushPose();
		try {
			var leg = ru.deelter.realcombat.client.FirstPersonKick.leg(poseStack, partial);
			if (leg != null) collector.submitModelPart(leg, poseStack, ru.deelter.realcombat.client.FirstPersonKick.type(), lightCoords,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, (net.minecraft.client.renderer.texture.TextureAtlasSprite) null);
		} finally {
			poseStack.popPose();
		}
	}
}
