package ru.deelter.realcombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.deelter.realcombat.client.CombatPose;
import ru.deelter.realcombat.client.CombatRenderState;
import ru.deelter.realcombat.client.Poses;

/**
 * Turns the sword in the hand about its own length for a side slash, so it cuts with the edge, not the flat.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void realcombat$edge(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
								 PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		if (arm != state.mainArm) return;
		CombatPose pose = ((CombatRenderState) state).realcombat$pose();
		if (pose == null) return;
		float roll = Poses.roll(pose);
		if (roll != 0) poseStack.rotateAround(Poses.THIRD_PERSON_BLADE.rotation(arm == HumanoidArm.RIGHT ? roll : -roll), 0, 0, 0);
	}
}
