package ru.deelter.realcombat.mixin;

import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deelter.realcombat.config.CombatConfig;

/**
 * The shield comes up almost at once: the vanilla quarter of a second makes it a reaction too late to block a swing
 * read from its windup, as Chivalry's shields are.
 */
@Mixin(BlocksAttacks.class)
public abstract class BlocksAttacksMixin {
	@Inject(method = "blockDelayTicks", at = @At("RETURN"), cancellable = true)
	private void realcombat$fasterShield(CallbackInfoReturnable<Integer> cir) {
		// the setting both ways: the clients draw the shield coming up for exactly this long
		cir.setReturnValue(Math.round(CombatConfig.getInstance().getRules().getShieldDelay() * 20));
	}
}
