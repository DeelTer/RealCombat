package ru.deelter.realcombat.client;

import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.combat.BotBrain;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Fighter;
import ru.deelter.realcombat.combat.Outcome;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Rules;
import ru.deelter.realcombat.combat.Sweep;
import ru.deelter.realcombat.combat.Weapons;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.UUID;

/**
 * A sparring partner that exists on this client only ({@code /realcombat duel}): a player model with an iron sword
 * that keeps its distance, winds up blows you have to read, parries yours (sometimes too early, falling for feints),
 * ripostes, chambers and kicks a turtling parry. Nothing it does reaches the server: health is counted here and
 * shown on the HUD.
 */
public final class DuelBot {
	private static final int ID = -7_700_001;
	private static final float MAX_HEALTH = 20;
	private static final double SWORD_SPEED = 1.6;
	/**
	 * against a VR player the bot swings as with a heavier sword: windups a third longer, for a body that has to
	 * move to parry, not a finger
	 */
	private static final double VR_SWORD_SPEED = SWORD_SPEED / (1.35 * 1.35);
	private static final float VR_PACE = 1.6f;
	private static final float PULL_OUT_DAMAGE = 1.5f;
	/**
	 * the VR player's blade as the bot reads it: a "release" while its tip flies at the bot fast
	 */
	private static final Fighter VR_FOE = new Fighter(100);
	private static long lastVrCut;
	private static final float SWORD_DAMAGE = 6;
	private static final RandomSource RANDOM = RandomSource.create();

	private static RemotePlayer bot;
	private static Fighter fighter;
	@Getter
	private static BotBrain.Difficulty difficulty = BotBrain.Difficulty.NORMAL;
	private static BotBrain brain;
	@Getter
	private static float botHealth;
	@Getter
	private static float playerHealth;
	@Getter
	private static int wins;
	@Getter
	private static int losses;
	private static int strafe = 1;
	/**
	 * a pose held for a look at it ({@code /realcombat pose}): the bot stands still and only turns to the player
	 */
	@Getter
	private static CombatPose frozen;

	private DuelBot() {
	}

	public static RemotePlayer entity() {
		return bot;
	}

	public static boolean active() {
		return bot != null;
	}

	public static void freeze(CombatPose pose) {
		frozen = pose;
	}

	public static CombatPose frozenPose(Entity entity) {
		return bot != null && entity == bot ? frozen : null;
	}

	public static Fighter fighterOf(Entity entity) {
		return bot != null && entity == bot ? fighter : null;
	}

	public static void start(BotBrain.Difficulty level) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		stop();
		difficulty = level;
		Vec3 look = Sweep.viewVector(player).horizontal().normalize();
		Vec3 at = player.position().add(look.scale(3.5));
		bot = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Sparring"));
		bot.setId(ID);
		bot.snapTo(at.x, ground(mc.level, at, player.getY()), at.z, Sweep.view(player)[0] + 180, 0);
		bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		mc.level.addEntity(bot);
		fighter = new Fighter(ClientCombat.getRules().getMaxStamina());
		botHealth = MAX_HEALTH;
		playerHealth = MAX_HEALTH;
		brain = new BotBrain(level, new java.util.Random());
		player.sendOverlayMessage(Component.translatable("realcombat.duel.start", Component.translatable("realcombat.duel." + level.name().toLowerCase())));
	}

	public static void stop() {
		Minecraft mc = Minecraft.getInstance();
		if (bot != null && mc.level != null && bot.level() == mc.level) mc.level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
		bot = null;
		fighter = null;
		brain = null;
	}

	public static void tick(Minecraft mc) {
		if (bot == null) return;
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || bot.level() != mc.level || bot.isRemoved()) {
			bot = null;
			fighter = null;
			return;
		}
		CombatConfig.Rules rules = ClientCombat.getRules();
		Fighter own = ClientCombat.getOwn();
		move(mc.level, player);
		if (frozen != null) return;
		boolean vr = VrClient.active();
		double speed = vr ? VR_SWORD_SPEED : SWORD_SPEED;
		Rules.tick(fighter, rules, speed);
		double distance = bot.position().distanceTo(player.position());

		boolean aimed = Math.abs(Sweep.angles(player, bot.getBoundingBox().getCenter())[0]) < 70;
		brain.setPace(vr ? VR_PACE : 1f);
		brain.tick(fighter, vr ? readVr(rules) : own, distance, vr || aimed, rules, speed);

		if (fighter.getPhase() == Phase.RELEASE) {
			if (fighter.getTicks() == 0) sound(bot, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.3f);
			else if (!fighter.getStruck().contains(player.getId())) strikePlayer(player, own, rules);
		}
		if (Rules.kickLands(fighter, rules) && distance < 2.4) {
			boolean shield = player.isBlocking();
			Rules.kick(own, shield, rules);
			Vec3 away = player.position().subtract(bot.position()).horizontal().normalize();
			player.push(away.x * 0.6, 0.1, away.z * 0.6);
			feedback(bot, player, Outcome.KICKED);
		}
	}

	/**
	 * Faces the player, keeps a sword's length and a half away and circles a little.
	 */
	private static void move(ClientLevel level, LocalPlayer player) {
		Vec3 to = player.position().subtract(bot.position());
		Vec3 flat = to.horizontal();
		double distance = flat.length();
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) Math.toDegrees(-Math.atan2(player.getEyeY() - bot.getEyeY(), Math.max(0.1, distance)));
		Vec3 pos = bot.position();
		if (frozen == null && fighter.getPhase() != Phase.STUN && distance > 0.01) {
			Vec3 forward = flat.normalize();
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			if (RANDOM.nextInt(60) == 0) strafe = -strafe;
			double step = Mth.clamp((distance - 2.4) * 0.25, -0.12, 0.17);
			// steps in while winding up, as players do to drag a blow into range
			if (fighter.getPhase() == Phase.WINDUP) step += 0.05;
			pos = pos.add(forward.scale(step)).add(right.scale(0.035 * strafe));
		}
		bot.setPos(pos.x, ground(level, pos, player.getY()), pos.z);
		// a held pose keeps its facing, so it can be looked at from the side
		if (frozen != null) return;
		bot.setYRot(yaw);
		bot.setYHeadRot(yaw);
		bot.yBodyRot = yaw;
		bot.setXRot(pitch);
	}

	private static double ground(ClientLevel level, Vec3 at, double fallback) {
		int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
		return Math.abs(height - fallback) <= 2 ? height : fallback;
	}

	/**
	 * A VR player has no phases to read: the bot sees a blow in his blade's tip flying at it fast, and nothing else.
	 */
	private static Fighter readVr(CombatConfig.Rules rules) {
		Vec3 tip = VrHand.tip();
		boolean swinging = tip != null && VrHand.tipSpeed() > CombatConfig.getInstance().getCompat().getVrSwingSpeed() * 0.6
				&& tip.distanceTo(bot.getBoundingBox().getCenter()) < 3
				&& VrHand.tipMotion().dot(bot.getBoundingBox().getCenter().subtract(tip)) > 0;
		if (swinging && VR_FOE.getPhase() != Phase.RELEASE) {
			VR_FOE.setDir(Dir.STAB);
			VR_FOE.enter(Phase.RELEASE, rules.getRelease());
		} else if (!swinging && VR_FOE.getPhase() != Phase.IDLE) {
			VR_FOE.enter(Phase.IDLE, 1);
		}
		return VR_FOE;
	}

	/**
	 * the bot's blade, where a VR blade can meet it: in its parry and along its swing
	 */
	public static ru.deelter.realcombat.combat.Blade.Segment blade() {
		if (bot == null || fighter == null || frozen != null) return null;
		Vec3 eye = bot.getEyePosition();
		return ru.deelter.realcombat.combat.BladeProxy.of(eye.x, eye.y, eye.z, bot.getYRot(), bot.getXRot(), fighter, ClientCombat.getRules(),
				CombatConfig.getInstance().getCompat().getVrBladeRadius());
	}

	/**
	 * A VR blade came into the bot fast enough: a cut, or a thrust.
	 *
	 * @param force how much faster than a swing it came
	 */
	static void vrStrike(Dir dir, double force) {
		long now = System.nanoTime();
		// one cut per pass of the blade, as on the server
		if (bot == null || now - lastVrCut < 400_000_000L) return;
		lastVrCut = now;
		LocalPlayer player = Minecraft.getInstance().player;
		CombatConfig.Rules rules = ClientCombat.getRules();
		Fighter own = ClientCombat.getOwn();
		own.setDir(dir);
		float damage = (float) (Weapons.damage(player, own.isOffhand()) * own.getWeapon().damage(dir) * rules.getDamageMultiplier() * Rules.damageShare(own, rules) * Math.clamp(force * 0.7, 0.5, 1.3));
		Outcome outcome = Rules.blow(own, fighter, Sweep.facing(bot, player, rules), false, false, damage, rules);
		// a VR blade does not recoil: the hand goes on
		if (own.getPhase() == Phase.RECOVERY) own.enter(Phase.IDLE, 1);
		feedback(player, bot, outcome);
		if (outcome == Outcome.PARRIED || outcome == Outcome.CHAMBERED) VrClient.buzz(0.8f);
		if (outcome == Outcome.HIT) hurtBot(player, damage);
	}

	/**
	 * a VR blade met the bot's blade: a swing the bot was making is stopped as by a parry
	 */
	static void vrClash(double speed) {
		if (bot == null) return;
		Rules.clash(fighter, ClientCombat.getRules());
		Vec3 at = bot.getEyePosition().add(Minecraft.getInstance().player.getEyePosition()).scale(0.5);
		sound(bot, SoundEvents.ANVIL_LAND, (float) Math.min(0.5, 0.15 + speed * 0.3), 1.9f);
		for (int i = 0; i < 6; i++) {
			Minecraft.getInstance().level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.2);
		}
	}

	/**
	 * a VR blade stuck in the bot was pulled out
	 */
	static void vrPullOut() {
		if (bot == null) return;
		sound(bot, SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 1.4f);
		hurtBot(Minecraft.getInstance().player, PULL_OUT_DAMAGE);
	}

	private static void hurtBot(LocalPlayer player, float damage) {
		botHealth -= damage;
		bot.animateHurt(0);
		if (botHealth <= 0) {
			wins++;
			player.sendOverlayMessage(Component.translatable("realcombat.duel.won", wins, losses));
			botHealth = MAX_HEALTH;
			playerHealth = MAX_HEALTH;
		}
	}

	private static void strikePlayer(LocalPlayer player, Fighter own, CombatConfig.Rules rules) {
		double reach = 3.0 + (fighter.getDir() == Dir.STAB ? rules.getStabReachBonus() : 0);
		if (Sweep.closest(bot, player).distanceTo(bot.getEyePosition()) > reach) return;
		double[] angles = Sweep.angles(bot, player.getBoundingBox().getCenter());
		if (!Sweep.crosses(fighter.getDir(), fighter.previousProgress(), fighter.progress(), angles[0], angles[1], rules)) return;
		fighter.getStruck().add(player.getId());
		float damage = SWORD_DAMAGE * fighter.getWeapon().damage(fighter.getDir());
		// a VR player parries with his blade held across the blow
		boolean vr = VrClient.active();
		Vec3 botEye = bot.getEyePosition();
		boolean guarding = vr && ru.deelter.realcombat.combat.BladeProxy.meets(botEye.x, botEye.y, botEye.z, bot.getYRot(), bot.getXRot(), fighter, rules,
				VrHand.segment(), CombatConfig.getInstance().getCompat().getVrBladeRadius());
		Outcome outcome = Rules.blow(fighter, ClientCombat.handles() || vr ? own : null, Sweep.facing(player, bot, rules), player.isBlocking(), guarding, damage, rules);
		if (vr && outcome != Outcome.HIT) VrClient.buzz(0.8f);
		feedback(bot, player, outcome);
		if (outcome != Outcome.HIT) return;
		playerHealth -= damage;
		player.animateHurt(0);
		if (playerHealth <= 0) {
			losses++;
			player.sendOverlayMessage(Component.translatable("realcombat.duel.lost", wins, losses));
			playerHealth = MAX_HEALTH;
			botHealth = MAX_HEALTH;
		}
	}

	/**
	 * The player's own blade and kick against the bot, after the player's fighter ticked.
	 */
	static void ownTick(Fighter own) {
		if (bot == null) return;
		LocalPlayer player = Minecraft.getInstance().player;
		CombatConfig.Rules rules = ClientCombat.getRules();
		if (own.getPhase() == Phase.RELEASE && own.getTicks() > 0 && !own.getStruck().contains(ID)) {
			double reach = player.entityInteractionRange() + (own.getDir() == Dir.STAB ? rules.getStabReachBonus() : 0);
			double[] angles = Sweep.angles(player, bot.getBoundingBox().getCenter());
			if (Sweep.closest(player, bot).distanceTo(player.getEyePosition()) <= reach
					&& Sweep.crosses(own.getDir(), own.previousProgress(), own.progress(), angles[0], angles[1], rules)) {
				own.getStruck().add(ID);
				float damage = Weapons.damage(player, own.isOffhand()) * own.getWeapon().damage(own.getDir()) * rules.getDamageMultiplier() * Rules.damageShare(own, rules);
				Outcome outcome = Rules.blow(own, fighter, Sweep.facing(bot, player, rules), false, false, damage, rules);
				feedback(player, bot, outcome);
				if (outcome == Outcome.HIT) hurtBot(player, damage);
			}
		}
		if (Rules.kickLands(own, rules) && Sweep.closest(player, bot).distanceTo(player.getEyePosition()) < 2.2) {
			Rules.kick(fighter, false, rules);
			Vec3 away = bot.position().subtract(player.position()).horizontal().normalize().scale(0.7);
			bot.setPos(bot.getX() + away.x, bot.getY(), bot.getZ() + away.z);
			feedback(player, bot, Outcome.KICKED);
		}
	}

	private static void feedback(Entity attacker, Entity target, Outcome outcome) {
		Vec3 at = attacker.getEyePosition().add(target.getEyePosition()).scale(0.5);
		ClientLevel level = Minecraft.getInstance().level;
		switch (outcome) {
			case HIT -> {
				sound(target, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
				level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y - 0.3, at.z, 0, 0, 0);
			}
			case PARRIED, CHAMBERED -> {
				sound(target, SoundEvents.ANVIL_LAND, 0.35f, 1.9f);
				for (int i = 0; i < 10; i++) {
					level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, RANDOM.nextGaussian() * 0.3, RANDOM.nextGaussian() * 0.3, RANDOM.nextGaussian() * 0.3);
				}
			}
			case BLOCKED -> sound(target, SoundEvents.SHIELD_BLOCK.value(), 1f, 1f);
			case PARRY_BROKEN -> sound(target, SoundEvents.SHIELD_BREAK.value(), 1f, 1f);
			case KICKED -> sound(target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (outcome != Outcome.HIT && player != null) {
			String key = "realcombat.outcome." + outcome.name().toLowerCase();
			player.sendOverlayMessage(Component.translatable(attacker == player ? key + ".by" : key));
		}
	}

	private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
		Minecraft.getInstance().level.playLocalSound(at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
	}
}
