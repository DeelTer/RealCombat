package ru.deelter.realcombat.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import ru.deelter.realcombat.combat.Arc;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Fighter;
import ru.deelter.realcombat.combat.Outcome;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Rules;
import ru.deelter.realcombat.combat.Weapon;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.net.Protocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Paper backend: the same rules and protocol as the Fabric server ({@code CombatServer}), on Bukkit events. A
 * player whose client says hello fights by the rules; the others play vanilla and can be hit, parried and kicked.
 * <p>
 * Not here, against the Fabric server: Vivecraft swings (its Paper side has an API of its own) and enchantment bonuses
 * on our blows (Bukkit has no call for them; the weapon's attack damage counts, Sharpness does not).
 */
public final class RealCombatPlugin extends JavaPlugin implements Listener, PluginMessageListener {
	private static final int HISTORY = 20;

	/**
	 * the players who fight by these rules right now; everyone else is vanilla to every check
	 */
	private final Map<UUID, Fighter> fighters = new HashMap<>();
	/**
	 * the players whose client has the mod
	 */
	private final java.util.Set<UUID> modded = new java.util.HashSet<>();
	/**
	 * the mechanic turned on or off for a player by the API or a command, over enabledByDefault
	 */
	private final Map<UUID, Boolean> switched = new HashMap<>();
	private final Map<UUID, ArrayDeque<BoundingBox>> boxes = new HashMap<>();
	private final List<Pending> pending = new ArrayList<>();
	private Bodies bodies = Bodies.NONE;
	private boolean dealing;
	/**
	 * every action and blow in the log: -Drealcombat.debug=true
	 */
	private final boolean debug = Boolean.getBoolean("realcombat.debug");
	private int tick;

	private record Pending(Player attacker, Fighter attack, Player target, Dir dir, float damage, int due) {
	}

	@lombok.Getter
	private static RealCombatPlugin instance;

	@Override
	public void onEnable() {
		instance = this;
		CombatConfig.load(getDataFolder().toPath().resolve("realcombat.json"));
		getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.ACTION, this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.STATE);
		getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.RULES);
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
		if (getServer().getPluginManager().isPluginEnabled("ChopChop") && CombatConfig.getInstance().getCompat().isChopchop()) {
			bodies = ChopChopBodies.create(this);
			getLogger().info("ChopChop found: blows cut and sever");
		}
	}

	@Override
	public void onDisable() {
		if (bodies instanceof ChopChopBodies chop) chop.close();
	}

	private CombatConfig.Rules rules() {
		return CombatConfig.getInstance().getRules();
	}

	boolean dealing() {
		return dealing;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			CombatConfig.load(getDataFolder().toPath().resolve("realcombat.json"));
			for (Player player : getServer().getOnlinePlayers()) apply(player);
			sender.sendMessage("RealCombat reloaded");
			return true;
		}
		if (args.length >= 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
			boolean on = args[0].equalsIgnoreCase("enable");
			List<Player> players = new ArrayList<>();
			for (Entity entity : getServer().selectEntities(sender, args[1])) {
				if (entity instanceof Player player) players.add(player);
			}
			for (Player player : players) setEnabled(player, on);
			sender.sendMessage("RealCombat " + (on ? "on" : "off") + " for " + players.size() + " player(s)");
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- the client

	@Override
	public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] data) {
		if (!channel.equals(Protocol.ACTION)) return;
		Protocol.ActionMessage message;
		try {
			message = Protocol.action(data);
		} catch (IllegalArgumentException e) {
			return;
		}
		CombatConfig.Rules r = rules();
		if (message.action() == Protocol.Action.HELLO) {
			modded.add(player.getUniqueId());
			getLogger().info(player.getName() + " has the RealCombat mod");
			apply(player);
			return;
		}
		if (message.action() == Protocol.Action.TOGGLE) {
			if (r.isPlayerToggle()) setEnabled(player, message.dir() == 1);
			else apply(player);
			return;
		}
		Fighter f = fighters.get(player.getUniqueId());
		if (f == null || !r.isEnabled() || player.isDead()) return;
		f.setWeapon(kind(player));
		boolean done = switch (message.action()) {
			case ATTACK -> armed(player) && bodies.canSwing(player) && Rules.attack(f, Dir.of(message.dir()), r, speed(player));
			case FEINT -> Rules.feint(f, r);
			case PARRY -> armed(player) && bodies.canSwing(player) && Rules.parry(f, r);
			case KICK -> bodies.canKick(player) && Rules.kick(f, r);
			default -> true;
		};
		if (debug) getLogger().info(player.getName() + " " + message.action() + " " + Dir.of(message.dir()) + (done ? "" : " refused") + " in " + f.getPhase());
		if (!done) {
			f.setDirty(true);
			f.setForced(true);
		}
		settle(player, Integer.MAX_VALUE);
	}

	/**
	 * The hello may come before the client said which channels it listens on, and Bukkit drops a message to a channel
	 * not listened on: the rules wait for that, a few seconds at most.
	 */
	private void sendRules(Player player, int tries) {
		if (!player.isOnline()) return;
		if (player.getListeningPluginChannels().contains(Protocol.RULES)) {
			player.sendPluginMessage(this, Protocol.RULES, Protocol.rules(CombatConfig.rulesJson(rules(), enabled(player))));
		} else if (tries < 100) {
			getServer().getScheduler().runTaskLater(this, () -> sendRules(player, tries + 1), 1);
		} else {
			getLogger().warning(player.getName() + " said hello but does not listen on " + Protocol.RULES);
		}
	}

	public boolean hasMod(Player player) {
		return modded.contains(player.getUniqueId());
	}

	public boolean enabled(Player player) {
		return rules().isEnabled() && switched.getOrDefault(player.getUniqueId(), rules().isEnabledByDefault());
	}

	public void setEnabled(Player player, boolean enabled) {
		switched.put(player.getUniqueId(), enabled);
		apply(player);
	}

	/**
	 * Brings a player in line with {@link #enabled}: his fighter, his client's rules, and the others' view of him.
	 */
	private void apply(Player player) {
		if (!modded.contains(player.getUniqueId())) return;
		boolean on = enabled(player);
		Fighter f = fighters.get(player.getUniqueId());
		if (on && f == null) fighters.put(player.getUniqueId(), new Fighter(rules().getMaxStamina()));
		if (!on && f != null) {
			fighters.remove(player.getUniqueId());
			pending.removeIf(p -> p.attacker() == player || p.target() == player);
			// a state of no length: not a fighter any more
			byte[] gone = Protocol.state(new Protocol.StateMessage(player.getEntityId(), Phase.IDLE.ordinal(), 0, 0, 0, 0, false, true));
			for (Player watcher : player.getTrackedBy()) {
				if (modded.contains(watcher.getUniqueId())) watcher.sendPluginMessage(this, Protocol.STATE, gone);
			}
		}
		sendRules(player, 0);
	}

	private void send(Player player, Fighter f) {
		byte[] message = Protocol.state(new Protocol.StateMessage(player.getEntityId(), f.getPhase().ordinal(), f.getDir().ordinal(),
				f.getTicks(), f.getLength(), f.getStamina(), f.isRiposte(), f.isForced(), f.isCombo(), f.getRiposteWindow() > 0));
		for (Player watcher : player.getTrackedBy()) {
			if (modded.contains(watcher.getUniqueId())) watcher.sendPluginMessage(this, Protocol.STATE, message);
		}
		player.sendPluginMessage(this, Protocol.STATE, message);
		f.setDirty(false);
		f.setForced(false);
	}

	// ---------------------------------------------------------------- events

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		int ticks = Math.round(rules().getShieldDelay() * 20);
		if (ticks < event.getPlayer().getShieldBlockingDelay()) event.getPlayer().setShieldBlockingDelay(ticks);
	}

	/**
	 * back from death with full stamina and nothing in progress
	 */
	@EventHandler
	public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		pending.removeIf(p -> p.attacker() == player || p.target() == player);
		boxes.remove(player.getUniqueId());
		fell.remove(player.getUniqueId());
		if (!fighters.containsKey(player.getUniqueId())) return;
		Fighter fresh = new Fighter(rules().getMaxStamina());
		fresh.setForced(true);
		fighters.put(player.getUniqueId(), fresh);
		getServer().getScheduler().runTask(this, () -> send(player, fresh));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		fighters.remove(player.getUniqueId());
		modded.remove(player.getUniqueId());
		switched.remove(player.getUniqueId());
		boxes.remove(player.getUniqueId());
		fell.remove(player.getUniqueId());
		pending.removeIf(p -> p.attacker() == player || p.target() == player);
	}

	/**
	 * The vanilla click of a fighter with a weapon does nothing (his blade does the hitting), and a melee blow at a
	 * fighter who parries is parried.
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onMelee(EntityDamageByEntityEvent event) {
		if (dealing || !rules().isEnabled()) return;
		EntityDamageEvent.DamageCause cause = event.getCause();
		boolean melee = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
		if (!melee) return;
		if (event.getDamager() instanceof Player attacker && fighters.containsKey(attacker.getUniqueId()) && armed(attacker)) {
			event.setCancelled(true);
			return;
		}
		CombatConfig.Rules r = rules();
		if (!r.isParryMobs() || !(event.getEntity() instanceof Player player) || !(event.getDamager() instanceof LivingEntity attacker)) return;
		Fighter f = fighters.get(player.getUniqueId());
		if (f == null || f.getPhase() != Phase.PARRY || !facing(player, attacker)) return;
		Outcome outcome = Rules.blow(new Fighter(0), f, true, false, false, (float) event.getDamage(), r);
		effects(attacker, player, outcome);
		send(player, f);
		if (outcome != Outcome.PARRIED) return;
		event.setCancelled(true);
		shove(attacker, away(player, attacker), 0.4);
	}

	/**
	 * a vanilla hit (a mob, an arrow) flinches a windup too
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHurt(EntityDamageEvent event) {
		if (dealing || event.getFinalDamage() <= 0 || !(event.getEntity() instanceof Player player)) return;
		Fighter f = fighters.get(player.getUniqueId());
		if (f != null && f.getPhase() == Phase.WINDUP && !f.isRiposte()) Rules.stun(f, rules().getFlinch());
	}

	// ---------------------------------------------------------------- the fight

	/**
	 * the tick each player was last seen falling: a blow landing a moment after he touched down still counts as
	 * struck from the air
	 */
	private final Map<UUID, Integer> fell = new HashMap<>();

	boolean fell(Player player) {
		Integer at = fell.get(player.getUniqueId());
		return at != null && tick - at <= 5;
	}

	private void tick() {
		tick++;
		CombatConfig.Rules r = rules();
		for (Player player : getServer().getOnlinePlayers()) {
			// the fall distance grows on the way down; the client's own velocity is not seen on the server
			if (player.getFallDistance() > 0.2f && !player.isFlying() && !player.isInWater()) fell.put(player.getUniqueId(), tick);
			ArrayDeque<BoundingBox> history = boxes.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
			history.addFirst(player.getBoundingBox());
			if (history.size() > HISTORY) history.removeLast();
		}
		for (Pending p : List.copyOf(pending)) {
			if (p.due() <= tick) settle(p);
		}
		for (Player player : getServer().getOnlinePlayers()) {
			Fighter f = fighters.get(player.getUniqueId());
			if (f == null) continue;
			if (player.isDead()) {
				if (f.busy()) f.enter(Phase.IDLE, 1);
				continue;
			}
			f.setWeapon(kind(player));
			if (!armed(player) && f.getPhase() != Phase.IDLE && f.getPhase() != Phase.STUN && f.getPhase() != Phase.KICK) f.enter(Phase.IDLE, 1);
			Rules.tick(f, r, speed(player));
			if (f.getPhase() == Phase.RELEASE) {
				if (f.getTicks() == 0) sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.3f);
				else sweep(player, f, r);
			}
			if (Rules.kickLands(f, r)) kick(player, r);
			if (f.isDirty() || tick % 20 == 0) send(player, f);
		}
	}

	private void sweep(Player player, Fighter f, CombatConfig.Rules r) {
		double reach = attribute(player, Attribute.ENTITY_INTERACTION_RANGE, 3) + (f.getDir() == Dir.STAB ? r.getStabReachBonus() : 0);
		Location eye = player.getEyeLocation();
		// half a ping ago, and two ticks behind that, as the client draws other players
		int rewind = r.isLagCompensation() ? Math.clamp(Math.round(player.getPing() / 100f) + 2, 0, r.getMaxRewindTicks()) : 0;
		BoundingBox area = player.getBoundingBox().expand(reach + 1 + rewind * 0.4);
		// a ghast's fireball the blade crosses is sent back the way the player looks
		for (Entity entity : player.getWorld().getNearbyEntities(area, e -> e instanceof org.bukkit.entity.Fireball && !e.isDead())) {
			if (f.getStruck().contains(entity.getEntityId())) continue;
			if (closest(eye, entity.getBoundingBox()).distance(eye.toVector()) > reach + 0.5) continue;
			Vector c = entity.getBoundingBox().getCenter();
			double[] a = Arc.angles(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), c.getX(), c.getY(), c.getZ());
			if (!Arc.crosses(f.getDir(), f.previousProgress(), f.progress(), a[0], a[1], r)) continue;
			f.getStruck().add(entity.getEntityId());
			org.bukkit.entity.Fireball ball = (org.bukkit.entity.Fireball) entity;
			Vector look = eye.getDirection();
			ball.setShooter(player);
			ball.setVelocity(look.clone().multiply(Math.max(0.5, ball.getVelocity().length())));
			ball.setAcceleration(look.clone().multiply(0.1));
		}
		for (Entity entity : player.getWorld().getNearbyEntities(area, e -> e != player && e instanceof LivingEntity l && !l.isDead())) {
			LivingEntity target = (LivingEntity) entity;
			if (target instanceof Player p && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
			if (f.getStruck().contains(target.getEntityId())) continue;
			BoundingBox box = seen(target, rewind);
			if (closest(eye, box).distance(eye.toVector()) > reach) continue;
			if (!crossesBox(eye, f, box, r)) continue;
			if (!player.hasLineOfSight(target)) continue;
			f.getStruck().add(target.getEntityId());
			land(player, f, target, r);
			if (f.getDir() == Dir.STAB || f.getPhase() != Phase.RELEASE) break;
		}
	}

	/**
	 * The blade crosses some part of the box: its middle, the height of the eyes on it, or its point nearest to them.
	 */
	private static boolean crossesBox(Location eye, Fighter f, BoundingBox box, CombatConfig.Rules r) {
		Vector center = box.getCenter();
		Vector near = closest(eye, box);
		Vector[] points = {center, new Vector(center.getX(), Math.clamp(eye.getY(), box.getMinY() + Math.min(0.2, box.getHeight() / 2), box.getMaxY() - Math.min(0.2, box.getHeight() / 2)), center.getZ()), near};
		for (Vector p : points) {
			double[] angles = Arc.angles(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), p.getX(), p.getY(), p.getZ());
			if (Arc.crosses(f.getDir(), f.previousProgress(), f.progress(), angles[0], angles[1], r)) return true;
		}
		return false;
	}

	private BoundingBox seen(LivingEntity target, int rewind) {
		ArrayDeque<BoundingBox> history = target instanceof Player ? boxes.get(target.getUniqueId()) : null;
		if (history == null || history.isEmpty() || rewind <= 0) return target.getBoundingBox();
		int i = 0;
		for (BoundingBox box : history) {
			if (i++ >= rewind) return box;
		}
		return history.getLast();
	}

	private void land(Player player, Fighter f, LivingEntity target, CombatConfig.Rules r) {
		Fighter defender = target instanceof Player p ? fighters.get(p.getUniqueId()) : null;
		float damage = (float) attribute(player, Attribute.ATTACK_DAMAGE, 1) * f.getWeapon().damage(f.getDir()) * r.getDamageMultiplier();
		if (defender != null && r.isLagCompensation()) {
			// half the defender's ping and a tick more: how late his parry may arrive and still have been in time
			int grace = Math.clamp(Math.round(((Player) target).getPing() / 100f) + 1, 1, r.getMaxParryGrace());
			pending.add(new Pending(player, f, (Player) target, f.getDir(), damage, tick + grace));
			return;
		}
		resolve(player, f, target, f.getDir(), damage, r);
	}

	private void settle(Player target, int due) {
		for (Pending p : List.copyOf(pending)) {
			if (p.target() == target && p.due() <= due) settle(p);
		}
	}

	private void settle(Pending p) {
		pending.remove(p);
		if (!p.attacker().isOnline() || !p.target().isOnline() || p.attacker().isDead() || p.target().isDead()) return;
		resolve(p.attacker(), p.attack(), p.target(), p.dir(), p.damage(), rules());
		send(p.attacker(), p.attack());
	}

	/**
	 * the blow can hurt the target at all: PvP on in the world, not a creative or spectating player, not invulnerable
	 */
	private static boolean harmable(Player attacker, LivingEntity target) {
		if (target.isInvulnerable()) return false;
		if (target instanceof Player victim) {
			if (!attacker.getWorld().getPVP()) return false;
			org.bukkit.GameMode mode = victim.getGameMode();
			if (mode == org.bukkit.GameMode.CREATIVE || mode == org.bukkit.GameMode.SPECTATOR) return false;
		}
		return true;
	}

	private void resolve(Player player, Fighter f, LivingEntity target, Dir dir, float damage, CombatConfig.Rules r) {
		if (!harmable(player, target)) return;
		Fighter defender = target instanceof Player p ? fighters.get(p.getUniqueId()) : null;
		boolean shield = target instanceof Player p && p.isBlocking();
		Dir now = f.getDir();
		f.setDir(dir);
		Outcome outcome = Rules.blow(f, defender, facing(target, player), shield, false, damage, r);
		if (debug) getLogger().info(player.getName() + " " + dir + " at " + target.getType() + ": " + outcome);
		if (f.getPhase() != Phase.RECOVERY || outcome == Outcome.HIT) f.setDir(now);
		effects(player, target, outcome);
		if (outcome == Outcome.HIT) deal(player, target, dir, damage);
		if (outcome == Outcome.PARRY_BROKEN && shield) breakShield((Player) target);
		if (defender != null && defender.isDirty()) send((Player) target, defender);
	}

	private void deal(Player player, LivingEntity target, Dir dir, float damage) {
		target.setNoDamageTicks(0);
		org.bukkit.inventory.ItemStack weapon = player.getInventory().getItemInMainHand();
		damage += enchantBonus(weapon, target);
		DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build();
		double before = target.getHealth() + target.getAbsorptionAmount();
		dealing = true;
		try {
			target.damage(damage, source);
		} finally {
			dealing = false;
		}
		if (target.getHealth() + target.getAbsorptionAmount() >= before && !target.isDead()) return;
		shove(target, away(player, target), (dir == Dir.STAB ? 0.35 : 0.25) + 0.25 * weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.KNOCKBACK));
		int fire = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FIRE_ASPECT);
		if (fire > 0 && !target.isDead()) target.setFireTicks(Math.max(target.getFireTicks(), 80 * fire));
		player.damageItemStack(EquipmentSlot.HAND, 1);
		try {
			bodies.struck(player, target, dir, swing(player, dir), damage, target.isDead() || target.getHealth() <= 0);
		} catch (Throwable e) {
			getLogger().warning("Body hook failed: " + e);
		}
	}

	/**
	 * What the enchantments of the weapon add to a blow, as vanilla counts them: the plugin deals the damage itself.
	 */
	private static float enchantBonus(org.bukkit.inventory.ItemStack weapon, LivingEntity target) {
		float bonus = 0;
		int sharp = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS);
		if (sharp > 0) bonus += 0.5f * sharp + 0.5f;
		int smite = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SMITE);
		if (smite > 0 && org.bukkit.Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(target.getType())) bonus += 2.5f * smite;
		int bane = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.BANE_OF_ARTHROPODS);
		if (bane > 0 && org.bukkit.Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(target.getType())) bonus += 2.5f * bane;
		return bonus;
	}

	private void kick(Player player, CombatConfig.Rules r) {
		Location eye = player.getEyeLocation();
		for (Entity entity : player.getNearbyEntities(2.5, 2.5, 2.5)) {
			if (!(entity instanceof LivingEntity target) || target.isDead()) continue;
			if (closest(eye, target.getBoundingBox()).distance(eye.toVector()) > 2.2) continue;
			Vector center = target.getBoundingBox().getCenter();
			double[] angles = Arc.angles(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), center.getX(), center.getY(), center.getZ());
			if (Math.abs(angles[0]) > 40) continue;
			if (!harmable(player, target)) return;
			Fighter defender = target instanceof Player p ? fighters.get(p.getUniqueId()) : null;
			boolean shield = target instanceof Player p && p.isBlocking();
			Rules.kick(defender, shield, r);
			if (shield) breakShield((Player) target);
			shove(target, away(player, target), 0.6);
			effects(player, target, Outcome.KICKED);
			if (defender != null) send((Player) target, defender);
			return;
		}
	}

	private void breakShield(Player target) {
		target.setCooldown(Material.SHIELD, 60);
		target.clearActiveItem();
	}

	private void effects(LivingEntity attacker, LivingEntity target, Outcome outcome) {
		Location at = target.getEyeLocation().add(attacker.getEyeLocation()).multiply(0.5);
		switch (outcome) {
			case HIT -> {
				sound(target, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
				target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, -0.3, 0), 1, 0, 0, 0, 0);
			}
			case PARRIED, CHAMBERED -> {
				sound(target, Sound.BLOCK_ANVIL_LAND, 0.35f, 1.9f);
				target.getWorld().spawnParticle(Particle.CRIT, at, 12, 0.1, 0.1, 0.1, 0.4);
			}
			case BLOCKED -> sound(target, Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
			case PARRY_BROKEN -> {
				sound(target, Sound.ITEM_SHIELD_BREAK, 1f, 1f);
				target.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.2, 0.2, 0.2, 0.5);
			}
			case KICKED -> sound(target, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
		}
		if (outcome != Outcome.HIT) {
			// the text is in the mod's language files: only players with the mod are told
			String key = "realcombat.outcome." + outcome.name().toLowerCase(Locale.ROOT);
			if (target instanceof Player p && fighters.containsKey(p.getUniqueId())) p.sendActionBar(Component.translatable(key));
			if (attacker instanceof Player p && fighters.containsKey(p.getUniqueId())) p.sendActionBar(Component.translatable(key + ".by"));
		}
	}

	// ---------------------------------------------------------------- helpers

	static boolean armed(LivingEntity entity) {
		ItemStack hand = entity.getEquipment() == null ? null : entity.getEquipment().getItemInMainHand();
		return hand != null && (Tag.ITEMS_SWORDS.isTagged(hand.getType()) || Tag.ITEMS_AXES.isTagged(hand.getType()));
	}

	static Weapon kind(LivingEntity entity) {
		ItemStack hand = entity.getEquipment() == null ? null : entity.getEquipment().getItemInMainHand();
		return hand != null && Tag.ITEMS_AXES.isTagged(hand.getType()) ? Weapon.AXE : Weapon.SWORD;
	}

	private static double speed(Player player) {
		return attribute(player, Attribute.ATTACK_SPEED, 1.6);
	}

	private static double attribute(LivingEntity entity, Attribute attribute, double fallback) {
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance == null ? fallback : instance.getValue();
	}

	private boolean facing(LivingEntity defender, LivingEntity attacker) {
		Location eye = defender.getEyeLocation();
		Location other = attacker.getEyeLocation();
		double[] angles = Arc.angles(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), other.getX(), other.getY(), other.getZ());
		return Math.abs(angles[0]) <= rules().getGuardArc();
	}

	private static Vector closest(Location eye, BoundingBox box) {
		return new Vector(Math.clamp(eye.getX(), box.getMinX(), box.getMaxX()), Math.clamp(eye.getY(), box.getMinY(), box.getMaxY()),
				Math.clamp(eye.getZ(), box.getMinZ(), box.getMaxZ()));
	}

	private static Vector away(Entity from, Entity to) {
		Vector away = to.getLocation().toVector().subtract(from.getLocation().toVector()).setY(0);
		return away.lengthSquared() < 1e-6 ? new Vector() : away.normalize();
	}

	private static void shove(LivingEntity target, Vector away, double strength) {
		target.setVelocity(target.getVelocity().add(away.multiply(strength)).add(new Vector(0, 0.08, 0)));
	}

	/**
	 * the way the blade moves through the world, for wounds that follow it
	 */
	static Vector swing(Player attacker, Dir dir) {
		Vector look = attacker.getEyeLocation().getDirection();
		Vector right = look.clone().crossProduct(new Vector(0, 1, 0)).normalize();
		return switch (dir) {
			case RIGHT -> right.multiply(-1);
			case LEFT -> right;
			case OVERHEAD -> new Vector(0, -1, 0).add(look.clone().multiply(0.3)).normalize();
			case UNDER -> new Vector(0, 1, 0).add(look.clone().multiply(0.3)).normalize();
			case STAB -> look;
		};
	}

	private static void sound(Entity at, Sound sound, float volume, float pitch) {
		at.getWorld().playSound(at.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
