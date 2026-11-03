package ru.deelter.realcombat.mixin.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.deelter.realcombat.client.CombatPose;
import ru.deelter.realcombat.client.CombatRenderState;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements CombatRenderState {
	@Unique
	private CombatPose realcombat$pose;

	@Override
	public CombatPose realcombat$pose() {
		return realcombat$pose;
	}

	@Override
	public void realcombat$setPose(CombatPose pose) {
		realcombat$pose = pose;
	}
}
