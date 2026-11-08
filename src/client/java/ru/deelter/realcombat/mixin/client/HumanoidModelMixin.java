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
		HumanoidArm swinging = ((CombatRenderState) state).realcombat$arm();
		boolean left = (swinging != null ? swinging : state.mainArm) == HumanoidArm.LEFT;
		pose = pose.forArm(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
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
		if (pose.bash()) {
			// a push with the shield: the other arm thrusts it forward, no leg
			ModelPart shieldArm = left ? model.rightArm : model.leftArm;
			shieldArm.xRot = -1.45f + 0.4f * kick / 1.5f;
			shieldArm.yRot = 0.35f * (left ? 1 : -1);
			shieldArm.z -= 3 * -kick / 1.5f;
		} else if (kick != 0) (left ? model.leftLeg : model.rightLeg).xRot = kick;
	}

	private static void realcombat$smoothShield(HumanoidModel<?> model, HumanoidRenderState state) {
		// as long as the server takes to let the shield block: the arm is up when the shield holds, not sooner
		var rules = ru.deelter.realcombat.client.ClientCombat.getRules();
		float ticks = Math.max(1, rules.getShieldDelay() * 20);
		// held low (looking down) the shield is lower, for the others to read: a blow from above goes over it
		boolean low = state.xRot > rules.getShieldLowPitch();
		if (state.rightArmPose == HumanoidModel.ArmPose.BLOCK) realcombat$raise(model.rightArm, state.ticksUsingItem(HumanoidArm.RIGHT) / ticks, low);
		if (state.leftArmPose == HumanoidModel.ArmPose.BLOCK) realcombat$raise(model.leftArm, state.ticksUsingItem(HumanoidArm.LEFT) / ticks, low);
	}

	private static void realcombat$raise(ModelPart arm, float share, boolean low) {
		if (low) arm.xRot += 0.55f;
		if (share >= 1) return;
		share = Math.max(0, share);
		share = 1 - (1 - share) * (1 - share);
		arm.xRot *= share;
		arm.yRot *= share;
	}
}
