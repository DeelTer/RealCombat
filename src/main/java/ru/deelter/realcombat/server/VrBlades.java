package ru.deelter.realcombat.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import ru.deelter.realcombat.combat.Blade;
import ru.deelter.realcombat.combat.BladeProxy;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Fighter;
import ru.deelter.realcombat.combat.Outcome;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Rules;
import ru.deelter.realcombat.combat.Sweep;
import ru.deelter.realcombat.combat.Weapons;
import ru.deelter.realcombat.compat.Vivecraft;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The blades of Vivecraft players, with bodies ({@link Blade}): there is no windup to read, the blade is where the
 * hand holds it, and it stops at what it touches. A blade that comes into a body fast enough cuts it; a blade that
 * comes into another blade is stopped by it, and so is the swing that blade was making: fencing. The blades of
 * players without VR take part too, while they parry or swing ({@link #desktop}), so a VR player can beat a mouse
 * swing aside and can be parried by one.
 */
final class VrBlades {
	private static final Map<UUID, Blade> BLADES = new HashMap<>();
	private static final Map<UUID, Vec3> TIPS = new HashMap<>();
	/**
	 * entity id -> the tick it was last cut by this player's blade
	 */
	private static final Map<UUID, Map<Integer, Integer>> STRUCK = new HashMap<>();
	private static final int RECUT_TICKS = 8;
	/**
	 * the body a player's blade is stuck in, and where that body was last tick
	 */
	private static final Map<UUID, Integer> LODGED = new HashMap<>();
	private static final Map<UUID, Vec3> LODGED_AT = new HashMap<>();
	private static final float PULL_OUT_DAMAGE = 1.5f;
	/**
	 * a thrust's share of the speed of a cut: hands thrust at a third to half of their slashing speed
	 */
	static final float THRUST_SPEED = 0.55f;

	private VrBlades() {
	}

	/**
	 * the way each VR player's sword points as his client draws it, and the tick it was said
	 */
	private static final Map<UUID, Vec3> REPORTED = new HashMap<>();
	private static final Map<UUID, Integer> REPORTED_AT = new HashMap<>();

	static void report(ServerPlayer player, float x, float y, float z, int now) {
		Vec3 dir = new Vec3(x, y, z);
		double length = dir.length();
		// a direction, nothing else: anything else is ignored
		if (!Double.isFinite(length) || length < 0.5 || length > 2) return;
		REPORTED.put(player.getUUID(), dir.scale(1 / length));
		REPORTED_AT.put(player.getUUID(), now);
	}

	private static Vec3 bladeDir(ServerPlayer player, Vivecraft.Hand hand, int now) {
		Integer at = REPORTED_AT.get(player.getUUID());
		return at != null && now - at <= 10 ? REPORTED.get(player.getUUID()) : hand.dir().normalize();
	}

	static void forget(UUID player) {
		REPORTED.remove(player);
		REPORTED_AT.remove(player);
		BLADES.remove(player);
		LODGED.remove(player);
		LODGED_AT.remove(player);
		TIPS.remove(player);
		STRUCK.remove(player);
	}

	static void tick(ServerPlayer player, Fighter f, int now, Map<UUID, Fighter> fighters) {
		CombatConfig config = CombatConfig.getInstance();
		CombatConfig.Rules r = config.getRules();
		CombatConfig.Compat compat = config.getCompat();
		Rules.tick(f, r, Weapons.speed(player));
		Vivecraft.Hand hand = Vivecraft.mainHand(player);
		if (hand == null || !Weapons.armed(player) || !CombatServer.bodies.canSwing(player)) {
			forget(player.getUUID());
			return;
		}
		Blade blade = BLADES.computeIfAbsent(player.getUUID(), id -> new Blade(compat.getVrBladeLength(), compat.getVrBladeRadius()));
		ServerLevel level = player.level();
		Vec3 tipBefore = TIPS.getOrDefault(player.getUUID(), vec(blade.tip()));
		AABB area = new AABB(vec(blade.getHand()), hand.pos()).inflate(compat.getVrBladeLength() + 0.5);

		List<LivingEntity> bodies = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !e.isSpectator());
		List<Blade.Box> boxes = new ArrayList<>();
		for (LivingEntity body : bodies) boxes.add(box(body.getBoundingBox()));
		List<ServerPlayer> owners = new ArrayList<>();
		List<Blade.Segment> blades = new ArrayList<>();
		for (ServerPlayer other : level.players()) {
			if (other == player || other.distanceToSqr(player) > 36) continue;
			Blade.Segment segment = bladeOf(other, fighters.get(other.getUUID()), r);
			if (segment != null) {
				owners.add(other);
				blades.add(segment);
			}
		}
		List<Blade.Box> solids = ru.deelter.realcombat.combat.Sweep.solidBlocks(level, area);

		// stuck in a body: the body carries the blade, and is slowed by it
		LivingEntity stuckIn = null;
		if (blade.isLodged()) {
			Integer id = LODGED.get(player.getUUID());
			if (id != null && level.getEntity(id) instanceof LivingEntity body && body.isAlive()) {
				stuckIn = body;
				blade.shift(v(body.position().subtract(LODGED_AT.getOrDefault(player.getUUID(), body.position()))));
				LODGED_AT.put(player.getUUID(), body.position());
				body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 1, false, false));
			} else {
				blade.unlodge();
				LODGED.remove(player.getUUID());
			}
		}
		Vec3 swordDir = bladeDir(player, hand, now);
		Blade.Result result = blade.step(v(hand.pos()), v(swordDir), boxes, blades, solids, compat.getVrFollow(), compat.getVrMaxStretch());
		// without a body the blade is wherever the hand is, and only finds what it crossed
		if (!compat.isVrPhysics()) blade.place(v(hand.pos()), v(swordDir));
		Vec3 tip = vec(blade.tip());
		TIPS.put(player.getUUID(), tip);
		float threshold = compat.getVrSwingSpeed();
		switch (result.contact()) {
			case BODY -> {
				LivingEntity target = bodies.get(result.index());
				Map<Integer, Integer> struck = STRUCK.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
				Integer last = struck.get(target.getId());
				Vec3 motion = vec(result.at()).subtract(tipBefore);
				// point first, along the blade: a thrust, which goes in and sticks; it is slower than a cut
				boolean thrust = compat.isVrStab() && motion.lengthSqr() > 1e-6 && motion.normalize().dot(swordDir) > 0.7;
				if (result.speed() < threshold * (thrust ? THRUST_SPEED : 1) || (last != null && now - last < RECUT_TICKS)) return;
				struck.put(target.getId(), now);
				Dir dir = thrust ? Dir.STAB : classify(player, motion);
				// for ChopChop: where the tip came in, the way it moved, and a strong side slash for the neck and the legs
				Vec3 way = motion.lengthSqr() > 1e-8 ? motion.normalize() : swordDir;
				Vec3 at = vec(result.at());
				boolean slash = dir.horizontal() && result.speed() / threshold >= 1.3;
				Outcome outcome = cut(player, f, target, dir, result.speed() / threshold, fighters, r, at.subtract(way.scale(0.5)), way, slash);
				if (thrust && outcome == Outcome.HIT && target.isAlive()) {
					blade.lodge(compat.getVrStabDepth());
					LODGED.put(player.getUUID(), target.getId());
					LODGED_AT.put(player.getUUID(), target.position());
					level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.6f);
				}
			}
			case FREED -> {
				LODGED.remove(player.getUUID());
				if (stuckIn != null) {
					// the blade comes out: a little more harm, and a sound of it
					CombatServer.deal(player, stuckIn, Dir.STAB, PULL_OUT_DAMAGE);
					level.playSound(null, stuckIn.getX(), stuckIn.getY(), stuckIn.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 1.4f);
				}
			}
			case BLADE -> {
				if (result.speed() < threshold * 0.5) return;
				ServerPlayer other = owners.get(result.index());
				Fighter otherFighter = fighters.get(other.getUUID());
				// the other blade was swinging: it is stopped as by a parry
				Rules.clash(otherFighter, r);
				if (otherFighter != null && otherFighter.isDirty()) CombatServer.send(other, otherFighter);
				Vec3 at = vec(result.at());
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, (float) Math.min(0.5, 0.15 + result.speed() * 0.3), 1.9f);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.3);
			}
			default -> {
			}
		}
	}

	private static Outcome cut(ServerPlayer player, Fighter f, LivingEntity target, Dir dir, double force, Map<UUID, Fighter> fighters, CombatConfig.Rules r,
							   Vec3 from, Vec3 along, boolean slash) {
		if (f.getPhase() == Phase.STUN || !CombatServer.harmable(player, target)) return Outcome.BLOCKED;
		f.setDir(dir);
		Fighter defender = target instanceof ServerPlayer p ? fighters.get(p.getUUID()) : null;
		float damage = (float) (Weapons.damage(player) * f.getWeapon().damage(dir) * r.getDamageMultiplier() * Rules.damageShare(f, r) * Math.clamp(force * 0.7, 0.5, 1.3));
		// the VR defender's parry is his blade, which has had its say already: only a mouse parry or a shield are left
		Outcome outcome = Rules.blow(f, defender != null && !defender.isVr() ? defender : null, Sweep.facing(target, player, r),
				CombatServer.shieldCovers(target, dir, r), false, damage, r);
		CombatServer.effects(player, target, outcome);
		if (outcome == Outcome.HIT) CombatServer.dealPast(player, target, dir, damage, from, along, slash);
		if (defender != null && target instanceof ServerPlayer p) CombatServer.send(p, defender);
		// a VR blade is not stopped by a recoil: the hand goes on
		if (f.getPhase() == Phase.RECOVERY) f.enter(Phase.IDLE, 1);
		return outcome;
	}

	/**
	 * The blade of a player, if it is somewhere it can be touched: a VR player's always, a mouse player's while he
	 * parries or swings.
	 */
	static Blade.@Nullable Segment bladeOf(ServerPlayer player, @Nullable Fighter f, CombatConfig.Rules r) {
		if (f == null || !Weapons.armed(player)) return null;
		if (f.isVr()) {
			Blade blade = BLADES.get(player.getUUID());
			return blade == null ? null : blade.segment();
		}
		return desktop(player, f, r);
	}

	/**
	 * Where a mouse player's blade is, drawn from his phase as the arm is drawn: across the face in a parry, along the
	 * arc of the swing in a release.
	 */
	static Blade.@Nullable Segment desktop(ServerPlayer player, Fighter f, CombatConfig.Rules r) {
		Vec3 eye = player.getEyePosition();
		return BladeProxy.of(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot(), f, r, CombatConfig.getInstance().getCompat().getVrBladeRadius());
	}

	/**
	 * The VR defender's blade is in the way of a mouse player's swinging blade this tick.
	 */
	static boolean meets(ServerPlayer defender, ServerPlayer attacker, Fighter swing, CombatConfig.Rules r) {
		Blade blade = BLADES.get(defender.getUUID());
		if (blade == null) return false;
		Vec3 eye = attacker.getEyePosition();
		float[] view = Sweep.view(attacker);
		return BladeProxy.meets(eye.x, eye.y, eye.z, view[0], view[1], swing, r, blade.segment(), CombatConfig.getInstance().getCompat().getVrBladeRadius());
	}

	/**
	 * The VR defender holds his blade across a mouse player's blow from {@code from} to {@code to}.
	 */
	static boolean blocks(ServerPlayer defender, Vec3 from, Vec3 to) {
		Blade blade = BLADES.get(defender.getUUID());
		if (blade == null) return false;
		double radius = CombatConfig.getInstance().getCompat().getVrBladeRadius();
		return Blade.distance(blade.getHand(), blade.tip(), v(from), v(to)) <= radius * 2 + CombatConfig.getInstance().getCompat().getVrParryDistance();
	}

	private static Dir classify(ServerPlayer player, Vec3 motion) {
		Vec3 look = Sweep.viewVector(player);
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		double forward = motion.dot(look);
		double side = motion.dot(right);
		double up = motion.y;
		double length = motion.length();
		if (length < 1e-6) return Dir.RIGHT;
		if (forward > length * 0.8) return Dir.STAB;
		if (Math.abs(up) > Math.abs(side)) return up < 0 ? Dir.OVERHEAD : Dir.UNDER;
		// moving to the left is a blow from the right
		return side < 0 ? Dir.RIGHT : Dir.LEFT;
	}

	static Blade.V v(Vec3 v) {
		return new Blade.V(v.x, v.y, v.z);
	}

	static Vec3 vec(Blade.V v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	static Blade.Box box(AABB a) {
		return new Blade.Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ);
	}
}
