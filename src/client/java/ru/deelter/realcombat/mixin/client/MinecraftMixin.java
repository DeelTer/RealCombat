package ru.deelter.realcombat.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.client.Corpses;
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
		// a dead body of ChopChop under the crosshair: the click is its cut
		if (!ClientCombat.handles() || ClientCombat.onThing() || Corpses.aimed()) return;
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
		// a fishing rod, a bow or food in the other hand, and nothing to parry in front: the use is that item's
		if (ClientCombat.offhandUse()) return;
		ci.cancel();
		// the press still reaches the server, with the sword hand only (nothing in the other hand is eaten or drawn):
		// a plugin's menu on a click, an NPC, a sign
		if (mc.gameMode == null) return;
		if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
			mc.gameMode.interact(mc.player, hit.getEntity(), hit, net.minecraft.world.InteractionHand.MAIN_HAND);
		} else if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		} else {
			mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
		}
	}
}
