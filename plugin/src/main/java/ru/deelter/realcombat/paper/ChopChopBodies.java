package ru.deelter.realcombat.paper;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import ru.deelter.chopchop.paper.api.ChopChopApi;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Dismember;
import ru.deelter.realcombat.config.CombatConfig;

/**
 * ChopChop's Paper API: we tell it where the blade went, as the Fabric side does. Only loaded when ChopChop is there.
 */
final class ChopChopBodies implements Bodies, org.bukkit.event.Listener {
	private static final String[] SHATTER = {"head", "right_arm", "left_arm", "right_leg", "left_leg"};

	/**
	 * A mace smash that kills flattens the body: everything flies off. Before ChopChop's own handling of the death
	 * (MONITOR), which keeps the body as it is then: cut after it, the parts would fly and stay on the body too.
	 */
	@org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDeath(org.bukkit.event.entity.EntityDeathEvent event) {
		if (!CombatConfig.getInstance().getCompat().isChopchop() || !CombatConfig.getInstance().getCompat().isMaceShatter()) return;
		var source = event.getDamageSource();
		if (source.getDamageType() != org.bukkit.damage.DamageType.MACE_SMASH || !ChopChopApi.hasBody(event.getEntity())) return;
		for (String part : SHATTER) ChopChopApi.sever(event.getEntity(), part, 0, source.getCausingEntity(), true);
	}

	private static final java.util.Random RANDOM = new java.util.Random();
	private ChopChopBodies() {
	}

	static ChopChopBodies create(RealCombatPlugin plugin) {
		// our blows are wounded here; arrows, mobs and players without the mod stay ChopChop's own
		ChopChopApi.setAutomaticWounds((target, event) -> !(plugin.dealing() && event instanceof EntityDamageByEntityEvent));
		ChopChopBodies bodies = new ChopChopBodies();
		plugin.getServer().getPluginManager().registerEvents(bodies, plugin);
		return bodies;
	}

	void close() {
		ChopChopApi.setAutomaticWounds(null);
	}

	private final java.util.Map<java.util.UUID, java.util.Set<Integer>> cleft = new java.util.HashMap<>();

	@Override
	public void sweepCorpses(Player attacker, ru.deelter.realcombat.combat.Fighter f, org.bukkit.Location eye, double reach,
							 CombatConfig.Rules r) {
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		if (!compat.isChopchop() || f.getDir() == Dir.STAB || compat.getCorpseCleaveChance() <= 0) return;
		var done = cleft.computeIfAbsent(attacker.getUniqueId(), id -> new java.util.HashSet<>());
		if (f.previousProgress() == 0) done.clear();
		var area = attacker.getBoundingBox().expand(reach + 1);
		for (var corpse : ChopChopApi.corpses(attacker.getWorld(), area).entrySet()) {
			if (!done.add(corpse.getKey())) continue;
			var at = corpse.getValue();
			var box = new org.bukkit.util.BoundingBox(at.getX() - 0.9, at.getY(), at.getZ() - 0.9, at.getX() + 0.9, at.getY() + 0.4, at.getZ() + 0.9);
			var near = new Vector(Math.clamp(eye.getX(), box.getMinX(), box.getMaxX()), Math.clamp(eye.getY(), box.getMinY(), box.getMaxY()),
					Math.clamp(eye.getZ(), box.getMinZ(), box.getMaxZ()));
			Vector c = box.getCenter();
			double[] a = ru.deelter.realcombat.combat.Arc.angles(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), c.getX(), c.getY(), c.getZ());
			boolean crosses = near.distance(eye.toVector()) <= reach + 0.5 && ru.deelter.realcombat.combat.Arc.crosses(f.getDir(),
					f.previousProgress(), f.progress(), a[0], f.getDir() == Dir.OVERHEAD ? a[1] - 30 * f.progress() : a[1], r);
			if (!crosses) {
				done.remove(corpse.getKey());
				continue;
			}
			if (RANDOM.nextFloat() >= compat.getCorpseCleaveChance()) continue;
			ChopChopApi.severCorpse(corpse.getKey(), "body", 0.5, true);
			for (String leg : new String[]{"right_leg", "left_leg"}) ChopChopApi.severCorpse(corpse.getKey(), leg, 0, true);
		}
	}

	@Override
	public boolean handless(LivingEntity fighter, String side) {
		return CombatConfig.getInstance().getCompat().isChopchop() && ChopChopApi.isHandless(fighter, side);
	}

	@Override
	public boolean canSwing(LivingEntity fighter) {
		boolean left = fighter instanceof org.bukkit.entity.HumanEntity human && human.getMainHand() == org.bukkit.inventory.MainHand.LEFT;
		// without the sword hand, a weapon in the other one fights instead
		return !ChopChopApi.isHandless(fighter, left ? "left" : "right") || RealCombatPlugin.offHanded(fighter);
	}

	@Override
	public boolean canKick(LivingEntity fighter) {
		return !ChopChopApi.isLegless(fighter);
	}

	@Override
	public void struck(Player attacker, LivingEntity target, Dir dir, Vector swing, float damage, boolean killed) {
		if (!ChopChopApi.hasBody(target)) return;
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		ChopChopApi.BodySpot spot = ChopChopApi.locate(target, attacker.getEyeLocation(), attacker.getEyeLocation().getDirection(), 5, true);
		if (spot == null) return;
		double max = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20 : target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
		float share = (float) (target.getHealth() / Math.max(1, max));
		boolean falling = RealCombatPlugin.getInstance().fell(attacker);
		switch (Dismember.decide(spot.part(), dir, killed, share, dir.horizontal(), falling, compat, RANDOM)) {
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
