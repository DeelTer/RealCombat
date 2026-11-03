package ru.deelter.realcombat.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.deelter.realcombat.client.CombatPose;
import ru.deelter.realcombat.client.CombatRenderState;
import ru.deelter.realcombat.client.Poses;
import ru.deelter.realcombat.config.CombatConfig;

/**
 * Poses the sword arm (and the kicking leg) after the vanilla animation, and raises a shield over a few ticks instead
 * of in one frame.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void realcombat$pose(HumanoidRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		realcombat$smoothShield(model, state);
		CombatPose pose = ((CombatRenderState) state).realcombat$pose();
		if (pose == null) return;
		boolean left = state.mainArm == HumanoidArm.LEFT;
		ModelPart arm = left ? model.leftArm : model.rightArm;
		float mirror = left ? -1 : 1;
		// the body turns into the blow and the shoulders go with it, as in the vanilla swing
		float twist = Poses.twist(pose) * mirror;
		if (twist != 0) {
			model.body.yRot += twist;
			float sin = (float) Math.sin(model.body.yRot);
			float cos = (float) Math.cos(model.body.yRot);
			model.rightArm.z = sin * 5;
			model.rightArm.x = -cos * 5;
			model.leftArm.z = -sin * 5;
			model.leftArm.x = cos * 5;
			model.rightArm.yRot += twist;
			model.leftArm.yRot += twist;
		}
		Poses.Arm angles = Poses.arm(pose);
		arm.xRot = angles.x();
		arm.yRot = angles.y() * mirror + twist;
		arm.zRot = angles.z() * mirror;
		arm.z += angles.push();
		float kick = Poses.kickLeg(pose);
		if (kick != 0) (left ? model.leftLeg : model.rightLeg).xRot = kick;
	}

	private static void realcombat$smoothShield(HumanoidModel<?> model, HumanoidRenderState state) {
		float ticks = CombatConfig.getInstance().getClient().getShieldRaiseTicks();
		if (ticks <= 0) return;
		if (state.rightArmPose == HumanoidModel.ArmPose.BLOCK) realcombat$raise(model.rightArm, state.ticksUsingItem(HumanoidArm.RIGHT) / ticks);
		if (state.leftArmPose == HumanoidModel.ArmPose.BLOCK) realcombat$raise(model.leftArm, state.ticksUsingItem(HumanoidArm.LEFT) / ticks);
	}

	private static void realcombat$raise(ModelPart arm, float share) {
		if (share >= 1) return;
		share = Math.max(0, share);
		share = 1 - (1 - share) * (1 - share);
		arm.xRot *= share;
		arm.yRot *= share;
	}
}
