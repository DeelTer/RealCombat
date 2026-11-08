package ru.deelter.realcombat.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.client.CombatRenderState;

/**
 * Notes the swing of the entity in its render state, for the model to pose.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void realcombat$pose(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
		((CombatRenderState) state).realcombat$setPose(ClientCombat.poseOf(entity, partial));
		((CombatRenderState) state).realcombat$setArm(ru.deelter.realcombat.combat.Weapons.arm(entity));
	}
}
