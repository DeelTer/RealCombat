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
		ru.deelter.realcombat.combat.Weapons.serverHandless = (entity, side) -> on() && ChopChopApi.isHandless(entity, side);
		// our blows are wounded here; everything else (arrows, mobs, vanilla players) stays ChopChop's own
		ChopChopApi.setAutomaticWounds((target, source) -> !CombatServer.dealing());
		// a mace smash that kills flattens the body: everything flies off
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((target, source) -> {
			if (!on() || !CombatConfig.getInstance().getCompat().isMaceShatter()) return;
			if (!source.is(net.minecraft.world.damagesource.DamageTypes.MACE_SMASH) || !ChopChopApi.hasBody(target)) return;
			for (String part : SHATTER) ChopChopApi.sever(target, part, 0, source.getEntity(), true);
		});
	}

	/**
	 * the bodies a swing went through, by fighter, so a swing cleaves one only once
	 */
	private static final java.util.Map<java.util.UUID, java.util.Set<Integer>> CLEFT = new java.util.HashMap<>();

	@Override
	public void sweepCorpses(ServerPlayer attacker, ru.deelter.realcombat.combat.Fighter f, Vec3 eye, double reach,
							 CombatConfig.Rules r) {
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		if (!on() || f.getDir() == Dir.STAB || compat.getCorpseCleaveChance() <= 0) return;
		var cleft = CLEFT.computeIfAbsent(attacker.getUUID(), id -> new java.util.HashSet<>());
		// a new swing: forget the last one's
		if (f.previousProgress() == 0) cleft.clear();
		var area = attacker.getBoundingBox().inflate(reach + 1);
		for (var corpse : ChopChopApi.corpses(attacker.level(), area).entrySet()) {
			if (!cleft.add(corpse.getKey())) continue;
			Vec3 at = corpse.getValue();
			// a body lies along the ground: a flat box about where it fell
			var box = new net.minecraft.world.phys.AABB(at.x - 0.9, at.y, at.z - 0.9, at.x + 0.9, at.y + 0.4, at.z + 0.9);
			if (ru.deelter.realcombat.combat.Sweep.closest(eye, box).distanceTo(eye) > reach + 0.5) {
				cleft.remove(corpse.getKey());
				continue;
			}
			double[] angles = ru.deelter.realcombat.combat.Sweep.angles(attacker, box.getCenter());
			// the blade goes on down to the ground through a body lying low
			if (!ru.deelter.realcombat.combat.Arc.crosses(f.getDir(), f.previousProgress(), f.progress(), angles[0],
					f.getDir() == Dir.OVERHEAD ? angles[1] - 30 * f.progress() : angles[1], r)) {
				cleft.remove(corpse.getKey());
				continue;
			}
			if (RANDOM.nextFloat() >= compat.getCorpseCleaveChance()) continue;
			ChopChopApi.severCorpse(corpse.getKey(), "body", 0.5, true);
			for (String leg : new String[]{"right_leg", "left_leg"}) ChopChopApi.severCorpse(corpse.getKey(), leg, 0, true);
		}
	}

	static final String[] SHATTER = {"head", "right_arm", "left_arm", "right_leg", "left_leg"};

	private static boolean on() {
		return CombatConfig.getInstance().getCompat().isChopchop();
	}

	@Override
	public boolean canSwing(LivingEntity fighter) {
		// the hand that holds the sword
		// without the sword hand, a weapon in the other one fights instead
		return !on() || !ChopChopApi.isHandless(fighter, fighter.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? "left" : "right")
				|| ru.deelter.realcombat.combat.Weapons.offHanded(fighter);
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
