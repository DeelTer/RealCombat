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
		if (mc.player == null) return;
		if (arm != ru.deelter.realcombat.client.ClientCombat.armOf(mc.player)) {
			realcombat$shield(poseStack, arm, mc);
			return;
		}
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
	/**
	 * The shield in the other hand comes up as long as the server takes to let it block (not at once, as the game
	 * draws it), and thrusts forward in a push.
	 */
	private static void realcombat$shield(PoseStack poseStack, HumanoidArm arm, Minecraft mc) {
		var player = mc.player;
		var hand = arm == player.getMainArm() ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
		if (!ru.deelter.realcombat.combat.Weapons.shield(player.getItemInHand(hand))) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (player.isUsingItem() && ru.deelter.realcombat.combat.Weapons.shield(player.getUseItem())) {
			float ticks = Math.max(1, ClientCombat.getRules().getShieldDelay() * 20);
			float share = Math.min(1, (player.getTicksUsingItem() + partial) / ticks);
			float down = (1 - share) * (1 - share);
			poseStack.translate(0, -0.45f * down, 0.1f * down);
		}
		CombatPose pose = ClientCombat.poseOf(player, partial);
		if (pose != null && pose.bash()) {
			float push = (float) Math.sin(Math.min(1f, pose.progress() * 1.6f) * Math.PI);
			poseStack.translate(0.12f * (arm == HumanoidArm.RIGHT ? -1 : 1) * push, 0.05f * push, -0.35f * push);
		}
	}
}
