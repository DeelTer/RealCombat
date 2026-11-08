package ru.deelter.realcombat.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deelter.realcombat.server.CombatServer;

/**
 * A blow that went past a raised shield (over a low one, under a high one) is not blocked by it: the rules decided
 * where the shield was, the game would block anything in front.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBlockingMixin {
	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void realcombat$pastShield(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (CombatServer.pastShield()) cir.setReturnValue(0f);
	}
}
