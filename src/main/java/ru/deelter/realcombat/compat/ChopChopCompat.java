package ru.deelter.realcombat.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.deelter.chopchop.api.ChopChopApi;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.server.Bodies;
import ru.deelter.realcombat.server.CombatServer;

/**
 * ChopChop decides nothing about our blows: we tell it where the blade went. Slashes cut along the swing, stabs
 * leave holes, heavy blows take limbs, a killing overhead takes the head. A fighter without his right hand can't swing,
 * without legs can't kick. Only loaded when ChopChop is there.
 */
public final class ChopChopCompat implements Bodies {
	private ChopChopCompat() {
	}

	public static void init() {
		CombatServer.bodies = new ChopChopCompat();
		// our blows are wounded here; everything else (arrows, mobs, vanilla players) stays ChopChop's own
		ChopChopApi.setAutomaticWounds((target, source) -> !CombatServer.dealing());
	}

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
	public void struck(ServerPlayer attacker, LivingEntity target, Dir dir, Vec3 swing, float damage, boolean killed) {
		if (!on() || !ChopChopApi.hasBody(target)) return;
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		ChopChopApi.BodySpot spot = ChopChopApi.locate(target, attacker.getEyePosition(), attacker.getViewVector(1f), attacker.entityInteractionRange() + 1, true);
		if (spot == null) return;
		if (killed && compat.isDecapitate() && dir == Dir.OVERHEAD && spot.part().equals("head")) {
			ChopChopApi.sever(target, "head", 0, attacker, false);
			return;
		}
		if (dir == Dir.STAB) {
			ChopChopApi.wound(target, spot, ChopChopApi.WoundShape.hole(1.2 + damage * 0.08, 2), attacker);
			return;
		}
		boolean heavy = damage >= compat.getSeverDamage() || killed;
		if (heavy && !spot.part().equals("body") && attacker.getRandom().nextFloat() < compat.getSeverChance()) {
			ChopChopApi.sever(target, spot, attacker, false);
		} else {
			ChopChopApi.wound(target, spot, ChopChopApi.WoundShape.cut(swing, 4 + damage * 0.5, 1.4, damage > 6 ? 2 : 1), attacker);
		}
	}
}
