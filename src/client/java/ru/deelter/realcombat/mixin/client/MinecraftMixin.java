package ru.deelter.realcombat.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.combat.Weapons;

/**
 * With a weapon in hand the attack button swings by our rules instead of the vanilla click, whatever the crosshair is
 * on, and mines only when held ({@link ClientCombat#mayMine}). The use button parries (read in
 * {@link ClientCombat#tick}, as a tap), except at a door, a chest and the like, which open as usual.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void realcombat$swing(CallbackInfoReturnable<Boolean> cir) {
		if (!ClientCombat.handles() || ClientCombat.onThing()) return;
		ClientCombat.attackPressed();
		cir.setReturnValue(false);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void realcombat$holdToMine(boolean down, CallbackInfo ci) {
		if (ClientCombat.handles() && !ClientCombat.mayMine()) ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void realcombat$parry(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (!ClientCombat.handles() || ClientCombat.onUsableBlock() || mc.player == null || Weapons.shield(mc.player.getOffhandItem())) return;
		ci.cancel();
	}
}
