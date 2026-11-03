package ru.deelter.realcombat.mixin.client;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setBillboardConstraints")
	void realcombat$setBillboardConstraints(Display.BillboardConstraints constraints);
}
