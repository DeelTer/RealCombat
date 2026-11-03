package ru.deelter.realcombat.mixin.client.vivecraft;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deelter.realcombat.client.VrHand;

/**
 * Everything Vivecraft draws at a controller (the hand, the item in it) is placed from this position: the sword hand
 * is moved to where its blade stopped. Only applied when Vivecraft is there ({@code VivecraftMixinPlugin}).
 */
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.RenderHelper", remap = false)
public abstract class RenderHelperMixin {
	@Inject(method = "getControllerRenderPos", at = @At("RETURN"), cancellable = true)
	private static void realcombat$heldBack(int controller, CallbackInfoReturnable<Vec3> cir) {
		// controller 0 is the main hand
		if (controller != 0) return;
		Vec3 offset = VrHand.offset();
		if (offset != Vec3.ZERO) cir.setReturnValue(cir.getReturnValue().add(offset));
	}
}
