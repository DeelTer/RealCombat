package ru.deelter.realcombat.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Fighter;
import ru.deelter.realcombat.combat.Outcome;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Rules;
import ru.deelter.realcombat.combat.Sweep;
import ru.deelter.realcombat.combat.Weapons;
import ru.deelter.realcombat.compat.Vivecraft;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.net.ActionPayload;
import ru.deelter.realcombat.net.Protocol;
import ru.deelter.realcombat.net.RulesPayload;
import ru.deelter.realcombat.net.StatePayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server half: keeps a {@link Fighter} for every player whose client has the mod, runs the rules every tick,
 * finds what a releasing blade crosses and tells the players around what each fighter does. Players without the mod
 * play vanilla, and can be hit, parried and kicked like mobs.
 */
public final class CombatServer {
	/**
	 * the players who fight by these rules right now: their client has the mod and the mechanic is on for them.
	 * Everyone else is vanilla to every check below
	 */
	private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();
	/**
	 * the players whose client has the mod
	 */
	private static final Set<UUID> MODDED = new HashSet<>();
	/**
	 * the mechanic turned on or off for a player by the API or a command, over {@code enabledByDefault}
	 */
	private static final Map<UUID, Boolean> SWITCHED = new HashMap<>();
	/**
	 * fighters with no client: server-side players (a fake player of a mod, an NPC) driven by {@link #perform}
	 */
	private static final Map<UUID, ServerPlayer> PUPPETS = new HashMap<>();
	/**
	 * where each player's box was in the last ticks, newest first: a blow is checked against where the attacker saw
	 * his target, not where it is by the time his swing reaches the server
	 */
	private static final Map<UUID, ArrayDeque<AABB>> BOXES = new HashMap<>();
	/**
	 * blows at fighters that wait a few ticks before they land: a parry pressed in time on the defender's screen
	 * reaches the server a ping later, and still counts
	 */
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final int HISTORY = 20;
	/**
	 * the game is dealing one of our blows: neither the vanilla nor ChopChop's handling should touch it
	 */
	private static boolean dealing;
	/**
	 * ChopChop, when it is there
	 */
	public static Bodies bodies = Bodies.NONE;

	private CombatServer() {
	}

	private record Pending(ServerPlayer attacker, Fighter attack, ServerPlayer target, Dir dir, float damage, int due) {
	}

	/**
	 * the player's client has the mod
	 */
	public static boolean modded(ServerPlayer player) {
		return MODDED.contains(player.getUUID());
	}

	/**
	 * the player fights by these rules right now
	 */
	public static boolean enabled(ServerPlayer player) {
		return rules().isEnabled() && SWITCHED.getOrDefault(player.getUUID(), rules().isEnabledByDefault());
	}

	/**
	 * Turns the mechanic on or off for a player: off, he swings, parries and is hit the vanilla way, and his client
	 * is told so. Kept until he leaves; a player without the mod is noted and gets it when he has it.
	 */
	public static void setEnabled(ServerPlayer player, boolean enabled) {
		SWITCHED.put(player.getUUID(), enabled);
		apply(player);
	}

	/**
	 * Brings a player in line with {@link #enabled}: his fighter, his client's rules, and a still pose for the others.
	 */
	private static void apply(ServerPlayer player) {
		if (!MODDED.contains(player.getUUID())) return;
		boolean on = enabled(player);
		Fighter f = FIGHTERS.get(player.getUUID());
		if (on && f == null) FIGHTERS.put(player.getUUID(), new Fighter(rules().getMaxStamina()));
		if (!on && f != null) {
			FIGHTERS.remove(player.getUUID());
			PENDING.removeIf(p -> p.attacker() == player || p.target() == player);
			VrBlades.forget(player.getUUID());
			// the others see him stand down: a state of no length means "not a fighter any more"
			StatePayload gone = new StatePayload(new Protocol.StateMessage(player.getId(), Phase.IDLE.ordinal(), 0, 0, 0, 0, false, true));
			for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
				if (ServerPlayNetworking.canSend(watcher, StatePayload.TYPE)) ServerPlayNetworking.send(watcher, gone);
			}
		}
		ServerPlayNetworking.send(player, new RulesPayload(CombatConfig.rulesJson(rules(), on)));
	}

	/**
	 * After the settings were read again: everyone gets the new rules.
	 */
	public static void reapply(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) apply(player);
	}

	/**
	 * Makes a player with no client of its own (a fake player, an NPC) fight by these rules: it is driven by
	 * {@link #perform}, and is seen, parried and hit as any fighter.
	 */
	public static void addPuppet(ServerPlayer puppet) {
		PUPPETS.put(puppet.getUUID(), puppet);
		FIGHTERS.put(puppet.getUUID(), new Fighter(rules().getMaxStamina()));
	}

	public static void removePuppet(ServerPlayer puppet) {
		PUPPETS.remove(puppet.getUUID());
		FIGHTERS.remove(puppet.getUUID());
		PENDING.removeIf(p -> p.attacker() == puppet || p.target() == puppet);
	}

	/**
	 * Presses something for a fighter, as his client would: a swing in a direction, a feint, a parry, a kick.
	 */
	public static void perform(ServerPlayer player, Protocol.Action action, Dir dir) {
		if (action != Protocol.Action.HELLO) act(player, new ActionPayload(action, dir.ordinal()));
	}

	public static boolean dealing() {
		return dealing;
	}

	public static Fighter fighter(ServerPlayer player) {
		return FIGHTERS.get(player.getUUID());
	}

	private static CombatConfig.Rules rules() {
		return CombatConfig.getInstance().getRules();
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> act(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ru.deelter.realcombat.net.BladePayload.TYPE, (payload, context) ->
				VrBlades.report(context.player(), payload.x(), payload.y(), payload.z(), context.server().getTickCount()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FIGHTERS.remove(handler.getPlayer().getUUID());
			MODDED.remove(handler.getPlayer().getUUID());
			BOXES.remove(handler.getPlayer().getUUID());
			VrBlades.forget(handler.getPlayer().getUUID());
			PENDING.removeIf(p -> p.attacker() == handler.getPlayer() || p.target() == handler.getPlayer());
			SWITCHED.remove(handler.getPlayer().getUUID());
			FELL.remove(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(CombatServer::tick);
		Commands.init();
		// the vanilla click of a player who swings by our rules does nothing: his blade does the hitting
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !rules().isEnabled()) return InteractionResult.PASS;
			Fighter f = FIGHTERS.get(player.getUUID());
			// only living targets are the blade's: a boat, a minecart, an item frame break with a click as usual
			return f != null && !f.isVr() && Weapons.armed(player) && entity instanceof LivingEntity ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatServer::allowDamage);
		// back from death with full stamina and nothing in progress
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
			if (FIGHTERS.containsKey(player.getUUID())) {
				Fighter fresh = new Fighter(rules().getMaxStamina());
				fresh.setForced(true);
				FIGHTERS.put(player.getUUID(), fresh);
				send(player, fresh);
			}
			PENDING.removeIf(p -> p.attacker() == old || p.target() == old);
			BOXES.remove(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			// a vanilla hit (a mob, an arrow) flinches a windup too
			if (dealing || taken <= 0 || !(entity instanceof ServerPlayer player)) return;
			Fighter f = FIGHTERS.get(player.getUUID());
			if (f != null && f.getPhase() == Phase.WINDUP && !f.isRiposte()) Rules.stun(f, rules().getFlinch());
		});
	}

	private static void act(ServerPlayer player, ActionPayload payload) {
		CombatConfig.Rules r = rules();
		if (payload.action() == Protocol.Action.HELLO) {
			MODDED.add(player.getUUID());
			apply(player);
			return;
		}
		if (payload.action() == Protocol.Action.TOGGLE) {
			if (r.isPlayerToggle()) setEnabled(player, payload.dir() == 1);
			else apply(player);
			return;
		}
		Fighter f = FIGHTERS.get(player.getUUID());
		if (f == null || !r.isEnabled() || !player.isAlive()) return;
		f.setWeapon(Weapons.kind(player));
		f.setDual(Weapons.dual(player));
		boolean done = switch (payload.action()) {
			case ATTACK -> Weapons.armed(player) && bodies.canSwing(player)
					&& Rules.attack(f, Dir.of(payload.dir()), r, Weapons.speed(player));
			case FEINT -> Rules.feint(f, r);
			case PARRY -> Weapons.armed(player) && bodies.canSwing(player) && Rules.parry(f, r);
			case KICK -> bodies.canKick(player) && Rules.kick(f, r);
			default -> true;
		};
		// the client guessed wrong: set it right
		if (!done) {
			f.setDirty(true);
			f.setForced(true);
		}
		// whatever the defender did may answer a blow still waiting for him: a parry, a chamber
		settle(player, Integer.MAX_VALUE);
	}

	/**
	 * the server tick each player was last seen falling: a blow landing a moment after he touched down still counts
	 * as struck from the air
	 */
	private static final Map<UUID, Integer> FELL = new HashMap<>();
	private static final int FELL_GRACE = 5;

	public static boolean fell(ServerPlayer player) {
		Integer at = FELL.get(player.getUUID());
		return at != null && player.level().getServer().getTickCount() - at <= FELL_GRACE;
	}

	private static void tick(MinecraftServer server) {
		CombatConfig.Rules r = rules();
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		PUPPETS.values().removeIf(puppet -> puppet.isRemoved() && !puppet.isAlive());
		players.addAll(PUPPETS.values());
		for (ServerPlayer player : players) {
			if (!player.onGround() && player.getDeltaMovement().y < -0.08 && !player.isInWater() && !player.getAbilities().flying) {
				FELL.put(player.getUUID(), server.getTickCount());
			}
			ArrayDeque<AABB> boxes = BOXES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
			boxes.addFirst(player.getBoundingBox());
			if (boxes.size() > HISTORY) boxes.removeLast();
		}
		for (Pending pending : List.copyOf(PENDING)) {
			if (pending.due() <= server.getTickCount()) settle(pending);
		}
		for (ServerPlayer player : players) {
			Fighter f = FIGHTERS.get(player.getUUID());
			if (f == null) continue;
			f.setVr(CombatConfig.getInstance().getCompat().isVivecraft() && Vivecraft.isVrPlayer(player));
			f.setWeapon(Weapons.kind(player));
			f.setDual(Weapons.dual(player));
			if (!player.isAlive()) {
				if (f.busy()) f.enter(Phase.IDLE, 1);
				continue;
			}
			if (f.isVr()) {
				VrBlades.tick(player, f, server.getTickCount(), FIGHTERS);
				continue;
			}
			// the weapon was put away mid-swing
			if (!Weapons.armed(player) && f.getPhase() != Phase.IDLE && f.getPhase() != Phase.STUN && f.getPhase() != Phase.KICK) {
				f.enter(Phase.IDLE, 1);
			}
			Rules.tick(f, r, Weapons.speed(player));
			if (f.getPhase() == Phase.RELEASE) {
				if (f.getTicks() == 0) sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.3f);
				else sweep(player, f, r);
			}
			if (Rules.kickLands(f, r)) kick(player, r);
			if (f.isDirty() || server.getTickCount() % 20 == 0) send(player, f);
		}
	}

	/**
	 * Hurts what the blade crossed this tick.
	 */
	private static void sweep(ServerPlayer player, Fighter f, CombatConfig.Rules r) {
		double reach = player.entityInteractionRange() + (f.getDir() == Dir.STAB ? r.getStabReachBonus() : 0);
		Vec3 eye = player.getEyePosition();
		ServerLevel level = player.level();
		AABB area = player.getBoundingBox().inflate(reach + 1);
		// the attacker saw his target half a ping ago, and two ticks behind that: Minecraft draws other players that much
		// behind where they are, smoothing their motion
		int rewind = r.isLagCompensation() ? Math.clamp(Math.round(player.connection.latency() / 100f) + 2, 0, r.getMaxRewindTicks()) : 0;
		// a ghast's fireball the blade crosses is sent back, as a click on it would
		for (var ball : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile.class, area,
				e -> e.isAlive() && !f.getStruck().contains(e.getId()))) {
			if (Sweep.closest(eye, ball.getBoundingBox()).distanceTo(eye) > reach + 0.5) continue;
			double[] angles = Sweep.angles(player, ball.getBoundingBox().getCenter());
			if (!Sweep.crosses(f.getDir(), f.previousProgress(), f.progress(), angles[0], angles[1], r)) continue;
			f.getStruck().add(ball.getId());
			ball.hurtServer(level, player.damageSources().playerAttack(player), 1);
		}
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area.inflate(rewind * 0.4), e -> e != player && e.isAlive() && !e.isSpectator())) {
			if (f.getStruck().contains(target.getId()) || player.isPassengerOfSameVehicle(target)) continue;
			AABB box = seen(target, rewind);
			if (Sweep.closest(eye, box).distanceTo(eye) > reach) continue;
			if (!crossesBox(player, f, box, eye, r)) continue;
			if (!player.hasLineOfSight(target)) continue;
			f.getStruck().add(target.getId());
			land(player, f, target, r);
			// a stab stops in the first body; a parried blade stops altogether
			if (f.getDir() == Dir.STAB || f.getPhase() != Phase.RELEASE) break;
		}
	}

	/**
	 * The blade crosses some part of the box: its middle, the height of the attacker's eyes on it, or its point
	 * nearest to them. Close, looking a little down or up at a body, its middle can be out of the line of a side
	 * slash while the blade plainly goes through the chest.
	 */
	private static boolean crossesBox(ServerPlayer player, Fighter f, AABB box, Vec3 eye, CombatConfig.Rules r) {
		Vec3 center = box.getCenter();
		Vec3[] points = {center, new Vec3(center.x, Math.clamp(eye.y, box.minY + Math.min(0.2, box.getYsize() / 2), box.maxY - Math.min(0.2, box.getYsize() / 2)), center.z), Sweep.closest(eye, box)};
		for (Vec3 point : points) {
			double[] angles = Sweep.angles(player, point);
			if (Sweep.crosses(f.getDir(), f.previousProgress(), f.progress(), angles[0], angles[1], r)) return true;
		}
		return false;
	}

	/**
	 * The target's box as the attacker saw it, {@code rewind} ticks ago. Mobs move on the attacker's screen about as
	 * they do on the server and are taken as they are.
	 */
	private static AABB seen(LivingEntity target, int rewind) {
		ArrayDeque<AABB> boxes = target instanceof ServerPlayer ? BOXES.get(target.getUUID()) : null;
		if (boxes == null || boxes.isEmpty() || rewind <= 0) return target.getBoundingBox();
		int i = 0;
		for (AABB box : boxes) {
			if (i++ >= rewind) return box;
		}
		return boxes.getLast();
	}

	private static void land(ServerPlayer player, Fighter f, LivingEntity target, CombatConfig.Rules r) {
		Fighter defender = target instanceof ServerPlayer p ? FIGHTERS.get(p.getUUID()) : null;
		float damage = Weapons.damage(player, f.isOffhand()) * f.getWeapon().damage(f.getDir()) * r.getDamageMultiplier() * Rules.damageShare(f, r);
		if (defender != null && !defender.isVr() && r.isLagCompensation()) {
			// half the defender's ping and a tick more: how late his parry may arrive and still have been in time
			int grace = Math.clamp(Math.round(((ServerPlayer) target).connection.latency() / 100f) + 1, 1, r.getMaxParryGrace());
			PENDING.add(new Pending(player, f, (ServerPlayer) target, f.getDir(), damage, player.level().getServer().getTickCount() + grace));
			return;
		}
		resolve(player, f, target, f.getDir(), damage, r);
	}

	/**
	 * Lands the blows waiting for {@code target} that are due by {@code tick}.
	 */
	private static void settle(ServerPlayer target, int tick) {
		for (Pending pending : List.copyOf(PENDING)) {
			if (pending.target() == target && pending.due() <= tick) settle(pending);
		}
	}

	private static void settle(Pending pending) {
		PENDING.remove(pending);
		if (!pending.attacker().isAlive() || !pending.target().isAlive() || pending.attacker().isRemoved() || pending.target().isRemoved()) return;
		resolve(pending.attacker(), pending.attack(), pending.target(), pending.dir(), pending.damage(), rules());
		send(pending.attacker(), pending.attack());
	}

	/**
	 * The blow can hurt the target at all: PvP on, not a teammate the server protects, not a creative player. If not,
	 * it does nothing, not even the sounds and the flinch of a blow.
	 */
	static boolean harmable(ServerPlayer attacker, LivingEntity target) {
		if (target instanceof net.minecraft.world.entity.player.Player victim && !attacker.canHarmPlayer(victim)) return false;
		return !target.isInvulnerableTo(attacker.level(), attacker.damageSources().playerAttack(attacker));
	}

	private static void resolve(ServerPlayer player, Fighter f, LivingEntity target, Dir dir, float damage, CombatConfig.Rules r) {
		if (!harmable(player, target)) return;
		Fighter defender = target instanceof ServerPlayer p ? FIGHTERS.get(p.getUUID()) : null;
		boolean facing = Sweep.facing(target, player, r);
		// a VR defender parries with his blade in the way of the attacker's blade, not near the line of the blow
		boolean guarding = defender != null && defender.isVr() && VrBlades.meets((ServerPlayer) target, player, f, r);
		// a waiting blow is judged with the direction it was swung in, even if the attacker swung again since
		Dir now = f.getDir();
		f.setDir(dir);
		Outcome outcome = Rules.blow(f, defender, facing, target.isBlocking(), guarding, damage, r);
		if (f.getPhase() != Phase.RECOVERY || outcome == Outcome.HIT) f.setDir(now);
		effects(player, target, outcome);
		// a side slash carries its line through the neck and the legs, as a VR one does
		if (outcome == Outcome.HIT) deal(player, target, dir, damage, null, null, dir.horizontal());
		if (outcome == Outcome.PARRY_BROKEN && target.isBlocking()) breakShield(target);
		if (defender != null && defender.isDirty() && target instanceof ServerPlayer p) send(p, defender);
	}

	public static void deal(ServerPlayer player, LivingEntity target, Dir dir, float damage) {
		deal(player, target, dir, damage, null, null, false);
	}

	/**
	 * @param from    where the blade came into the body (a VR blade), null for the attacker's eyes
	 * @param along   the way it came, null for the attacker's look
	 * @param vrSlash a strong side slash of a VR blade, for ChopChop
	 */
	public static void deal(ServerPlayer player, LivingEntity target, Dir dir, float damage, Vec3 from, Vec3 along, boolean vrSlash) {
		ServerLevel level = player.level();
		Fighter fighter = FIGHTERS.get(player.getUUID());
		boolean offSwing = fighter != null && fighter.isOffhand() && Weapons.dual(player);
		ItemStack weapon = Weapons.weapon(player, offSwing);
		EquipmentSlot slot = offSwing || Weapons.offHanded(player) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
		DamageSource source = player.damageSources().playerAttack(player);
		damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
		ru.deelter.realcombat.version.Entities.clearInvulnerability(target);
		boolean hurt;
		dealing = true;
		try {
			hurt = target.hurtServer(level, source, damage);
		} finally {
			dealing = false;
		}
		if (!hurt) return;
		// fire aspect, knockback and the like, as a vanilla blow does
		EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
		Vec3 away = target.position().subtract(player.position()).horizontal().normalize();
		shove(target, away, dir == Dir.STAB ? 0.35 : 0.25);
		player.setLastHurtMob(target);
		weapon.hurtAndBreak(1, player, slot);
		try {
			bodies.struck(player, target, dir, along != null ? along : Sweep.swing(player, dir), damage, !target.isAlive(), from, along, vrSlash);
		} catch (Throwable e) {
			ru.deelter.realcombat.RealCombat.LOG.warn("Body hook failed", e);
		}
	}

	private static void kick(ServerPlayer player, CombatConfig.Rules r) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.5), e -> e != player && e.isAlive())) {
			if (Sweep.closest(player, target).distanceTo(eye) > 2.2) continue;
			double[] angles = Sweep.angles(player, target.getBoundingBox().getCenter());
			if (Math.abs(angles[0]) > 40) continue;
			if (!harmable(player, target)) return;
			Fighter defender = target instanceof ServerPlayer p ? FIGHTERS.get(p.getUUID()) : null;
			boolean shield = target.isBlocking();
			Rules.kick(defender, shield, r);
			if (shield) breakShield(target);
			Vec3 away = target.position().subtract(player.position()).horizontal().normalize();
			shove(target, away, 0.6);
			effects(player, target, Outcome.KICKED);
			if (defender != null && target instanceof ServerPlayer p) send(p, defender);
			return;
		}
	}

	private static void breakShield(LivingEntity target) {
		ItemStack stack = target.getUseItem();
		BlocksAttacks blocks = stack.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks != null && target.level() instanceof ServerLevel level) blocks.disable(level, target, 3f, stack);
		target.stopUsingItem();
	}

	/**
	 * A mob's (or a vanilla player's) melee blow at a fighter who parries is parried.
	 */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		CombatConfig.Rules r = rules();
		if (dealing || !r.isParryMobs() || !(entity instanceof ServerPlayer player)) return true;
		Fighter f = FIGHTERS.get(player.getUUID());
		if (f == null || f.getPhase() != Phase.PARRY) return true;
		if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) return true;
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) return true;
		if (!Sweep.facing(player, attacker, r)) return true;
		Fighter mob = new Fighter(0);
		Outcome outcome = Rules.blow(mob, f, true, false, false, amount, r);
		effects(attacker, player, outcome);
		send(player, f);
		if (outcome != Outcome.PARRIED) return true;
		Vec3 away = attacker.position().subtract(player.position()).horizontal().normalize();
		shove(attacker, away, 0.4);
		return false;
	}

	private static void shove(LivingEntity target, Vec3 away, double strength) {
		target.push(away.x * strength, 0.08, away.z * strength);
		// a player moves on his own client: tell it
		ru.deelter.realcombat.version.Entities.resendMotion(target);
	}

	static void effects(LivingEntity attacker, LivingEntity target, Outcome outcome) {
		ServerLevel level = (ServerLevel) target.level();
		if (outcome != Outcome.HIT) {
			String key = "realcombat.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT);
			if (target instanceof ServerPlayer p && FIGHTERS.containsKey(p.getUUID())) p.sendOverlayMessage(Component.translatable(key));
			if (attacker instanceof ServerPlayer p && FIGHTERS.containsKey(p.getUUID())) p.sendOverlayMessage(Component.translatable(key + ".by"));
		}
		Vec3 at = target.getEyePosition().add(attacker.getEyePosition()).scale(0.5);
		switch (outcome) {
			case HIT -> {
				sound(target, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y - 0.3, at.z, 1, 0, 0, 0, 0);
			}
			case PARRIED, CHAMBERED -> {
				sound(target, SoundEvents.ANVIL_LAND, 0.35f, 1.9f);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.1, 0.1, 0.1, 0.4);
			}
			case BLOCKED -> sound(target, SoundEvents.SHIELD_BLOCK.value(), 1f, 1f);
			case PARRY_BROKEN -> {
				sound(target, SoundEvents.SHIELD_BREAK.value(), 1f, 1f);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.2, 0.2, 0.2, 0.5);
			}
			case KICKED -> sound(target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
		}
	}

	private static void sound(LivingEntity at, SoundEvent sound, float volume, float pitch) {
		at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void send(ServerPlayer player, Fighter f) {
		StatePayload payload = StatePayload.of(player.getId(), f);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player && ServerPlayNetworking.canSend(watcher, StatePayload.TYPE)) ServerPlayNetworking.send(watcher, payload);
		}
		if (ServerPlayNetworking.canSend(player, StatePayload.TYPE)) ServerPlayNetworking.send(player, payload);
		f.setDirty(false);
		f.setForced(false);
	}
}
