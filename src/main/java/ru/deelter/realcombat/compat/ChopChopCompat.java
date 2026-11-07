package ru.deelter.realcombat.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.deelter.chopchop.api.ChopChopApi;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Dismember;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.server.Bodies;
import ru.deelter.realcombat.server.CombatServer;

/**
 * ChopChop decides nothing about our blows: we tell it where the blade went. Slashes cut along the swing, stabs
 * leave holes, heavy blows take limbs, a killing overhead takes the head. A fighter without his right hand can't swing,
 * without legs can't kick. Only loaded when ChopChop is there.
 */
public final class ChopChopCompat implements Bodies {
	private static final java.util.Random RANDOM = new java.util.Random();
	private ChopChopCompat() {
	}

	public static void init() {
		CombatServer.bodies = new ChopChopCompat();
		// our blows are wounded here; everything else (arrows, mobs, vanilla players) stays ChopChop's own
		ChopChopApi.setAutomaticWounds((target, source) -> !CombatServer.dealing());
		// a mace smash that kills flattens the body: everything flies off
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((target, source) -> {
			if (!on() || !CombatConfig.getInstance().getCompat().isMaceShatter()) return;
			if (!source.is(net.minecraft.world.damagesource.DamageTypes.MACE_SMASH) || !ChopChopApi.hasBody(target)) return;
			for (String part : SHATTER) ChopChopApi.sever(target, part, 0, source.getEntity(), true);
		});
	}

	static final String[] SHATTER = {"head", "right_arm", "left_arm", "right_leg", "left_leg"};

	private static boolean on() {
		return CombatConfig.getInstance().getCompat().isChopchop();
	}

	@Override
	public boolean canSwing(LivingEntity fighter) {
		// the hand that holds the sword
		return !on() || !ChopChopApi.isHandless(fighter, fighter.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? "left" : "right");
	}

	@Override
	public boolean canKick(LivingEntity fighter) {
		return !on() || !ChopChopApi.isLegless(fighter);
	}

	@Override
	public void struck(ServerPlayer attacker, LivingEntity target, Dir dir, Vec3 swing, float damage, boolean killed,
					   Vec3 from, Vec3 along, boolean vrSlash) {
		if (!on() || !ChopChopApi.hasBody(target)) return;
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		ChopChopApi.BodySpot spot = ChopChopApi.locate(target, from != null ? from : attacker.getEyePosition(), along != null ? along : attacker.getViewVector(1f),
				attacker.entityInteractionRange() + 1, true);
		if (spot == null) return;
		float share = target.getMaxHealth() <= 0 ? 0 : target.getHealth() / target.getMaxHealth();
		// falling as the blade lands, or a moment after touching down
		boolean falling = ru.deelter.realcombat.server.CombatServer.fell(attacker);
		switch (Dismember.decide(spot.part(), dir, killed, share, vrSlash, falling, from != null, compat, RANDOM)) {
			case SPLIT -> {
				if (!ChopChopApi.split(target, attacker, true) && !ChopChopApi.halve(target, 0.5, attacker, true)) ChopChopApi.sever(target, spot, attacker, false);
			}
			case HALVE -> {
				if (!ChopChopApi.halve(target, 0.5, attacker, true)) ChopChopApi.sever(target, spot, attacker, false);
			}
			case HEAD -> ChopChopApi.sever(target, "head", 0, attacker, false);
			case SEVER -> ChopChopApi.sever(target, spot, attacker, false);
			case WOUND -> {
				if (dir == Dir.STAB) ChopChopApi.wound(target, spot, ChopChopApi.WoundShape.hole(1.2 + damage * 0.08, 2), attacker);
				else ChopChopApi.wound(target, spot, ChopChopApi.WoundShape.cut(swing, 4 + damage * 0.5, 1.4, damage > 6 ? 2 : 1), attacker);
			}
		}
	}
}
