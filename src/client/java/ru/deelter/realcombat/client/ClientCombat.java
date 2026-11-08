package ru.deelter.realcombat.client;

import lombok.Getter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Fighter;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Rules;
import ru.deelter.realcombat.combat.Sweep;
import ru.deelter.realcombat.combat.Weapons;
import ru.deelter.realcombat.compat.Vivecraft;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.net.ActionPayload;
import ru.deelter.realcombat.net.Protocol;
import ru.deelter.realcombat.net.StatePayload;

import java.util.HashMap;
import java.util.Map;

/**
 * The client half: turns the mouse and keys into swings, predicts the player's own fighter so his swing starts on
 * the click and not a round trip later, and keeps what the server says about the others for drawing them.
 */
public final class ClientCombat {
	private static final int HISTORY = 4;
	private static final Map<Integer, Remote> REMOTE = new HashMap<>();
	private static final float[] YAWS = new float[HISTORY];
	private static final float[] PITCHES = new float[HISTORY];
	@Getter
	private static Fighter own = new Fighter(100);
	/**
	 * the server's rules once it said them, our own until then
	 */
	@Getter
	private static CombatConfig.Rules rules = CombatConfig.getInstance().getRules();
	@Getter
	private static boolean serverSpeaks;
	private static long clientTicks;
	private static boolean useWasDown;
	private static LocalPlayer lastPlayer;
	private static final int MINE_AFTER = 6;
	/**
	 * ticks the attack button has been held
	 */
	private static int attackHeld;
	private static final int STICKY_TICKS = 8;
	private static Dir lastDir;
	private static long lastDirAt;
	/**
	 * the controls were shown this session
	 */
	private static boolean hinted;
	/**
	 * the player's own pose held for a look at it, in first person
	 */
	@lombok.Setter
	private static CombatPose ownFrozen;

	private ClientCombat() {
	}

	/**
	 * What the server said about someone else, run on by the client between messages.
	 */
	private record Remote(Phase phase, Dir dir, int ticks, int length, boolean riposte, long at, boolean offhand) {
	}

	public static void reset() {
		serverSpeaks = false;
		rules = CombatConfig.getInstance().getRules();
		own = new Fighter(rules.getMaxStamina());
		REMOTE.clear();
	}

	public static void serverRules(CombatConfig.Rules serverRules) {
		rules = serverRules;
		serverSpeaks = true;
		own = new Fighter(rules.getMaxStamina());
	}

	/**
	 * The mouse buttons swing and parry: a weapon in hand, a server or a sparring partner that knows the rules, and
	 * not in VR (there the hands swing).
	 */
	public static boolean handles() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		return player != null && rules.isEnabled() && Weapons.armed(player) && (serverSpeaks || DuelBot.active())
				&& !VrClient.active() && !player.isSpectator();
	}

	/**
	 * The crosshair is on a block that does something when used (a door, a chest, a lever): the use button opens it
	 * instead of parrying. Grass, the ground and walls are no reason not to parry.
	 */
	public static boolean onUsableBlock() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) return false;
		BlockState state = mc.level.getBlockState(hit.getBlockPos());
		Block block = state.getBlock();
		// an axe strips a log and scrapes copper: that use is the axe's, not a parry
		if (mc.player != null && mc.player.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) {
			String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
			if (state.is(net.minecraft.tags.BlockTags.LOGS) || path.contains("copper") || path.equals("bamboo_block")) return true;
		}
		return state.hasBlockEntity() || state.getMenuProvider(mc.level, hit.getBlockPos()) != null || block instanceof DoorBlock
				|| block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof ButtonBlock || block instanceof LeverBlock
				|| block instanceof BellBlock || block instanceof BedBlock;
	}

	private static final double THREAT_RANGE = 6;
	private static final double MOB_RANGE = 4;
	private static final double THREAT_COS = Math.cos(Math.toRadians(70));

	/**
	 * The use button is the other hand's item (a fishing rod, a bow, food) rather than a parry: something usable
	 * there, and nothing in front that could be parried, no one winding up a blow nor a monster close by.
	 */
	public static boolean offhandUse() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return false;
		var off = player.getOffhandItem();
		if (off.isEmpty() || Weapons.isWeapon(off) || Weapons.shield(off)) return false;
		return !threatened(player);
	}

	private static boolean threatened(LocalPlayer player) {
		if (DuelBot.active()) return true;
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		for (var e : REMOTE.entrySet()) {
			Phase phase = e.getValue().phase;
			if (phase != Phase.WINDUP && phase != Phase.RELEASE) continue;
			Entity other = mc.level.getEntity(e.getKey());
			if (other != null && inFront(eye, look, other, THREAT_RANGE)) return true;
		}
		for (Entity mob : mc.level.getEntities(player, player.getBoundingBox().inflate(MOB_RANGE),
				e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
			if (inFront(eye, look, mob, MOB_RANGE)) return true;
		}
		return false;
	}

	private static boolean inFront(Vec3 eye, Vec3 look, Entity other, double range) {
		Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
		double distance = to.length();
		return distance <= range && (distance < 1e-3 || to.normalize().dot(look) >= THREAT_COS);
	}

	/**
	 * The crosshair is on an entity that is not alive (a boat, a minecart, an item frame): the click breaks it as usual.
	 */
	public static boolean onThing() {
		Entity entity = Minecraft.getInstance().crosshairPickEntity;
		return entity != null && !(entity instanceof net.minecraft.world.entity.LivingEntity);
	}

	/**
	 * The attack button mines only when held: a click is a swing, whatever the crosshair is on (grass, the ground under
	 * an upward blow), as in adventure mode. Holding it on a block mines after a moment, out of a swing.
	 */
	public static boolean mayMine() {
		return attackHeld >= MINE_AFTER && own.getPhase() != Phase.WINDUP && own.getPhase() != Phase.RELEASE;
	}

	public static void attackPressed() {
		attack(pickDir());
	}

	public static void attack(Dir dir) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		own.setWeapon(Weapons.kind(player));
		own.setDual(Weapons.dual(player));
		if (Rules.attack(own, dir, rules, Weapons.speed(player))) send(Protocol.Action.ATTACK, dir.ordinal());
	}

	public static void parry() {
		if (Rules.parry(own, rules)) send(Protocol.Action.PARRY, 0);
	}

	private static void feint() {
		if (Rules.feint(own, rules)) send(Protocol.Action.FEINT, 0);
	}

	private static void kick() {
		if (Rules.kick(own, rules)) send(Protocol.Action.KICK, 0);
	}

	private static void send(Protocol.Action action, int dir) {
		// a sparring fight is this client's own: the server, which does not know the partner, would see a parry that
		// caught nothing and refuse the riposte after it
		if (serverSpeaks && !DuelBot.active()) ClientPlayNetworking.send(new ActionPayload(action, dir));
	}

	/**
	 * The direction of a click: where the view turned in the moment before it, as Mordhau reads the mouse.
	 */
	public static Dir pickDir() {
		Dir moved = motionDir();
		if (moved != null) return moved;
		// a flick of the mouse holds for a moment, so it can be followed by the click
		if (lastDir != null && clientTicks - lastDirAt <= STICKY_TICKS) return lastDir;
		try {
			return Dir.valueOf(CombatConfig.getInstance().getClient().getDefaultDir());
		} catch (IllegalArgumentException e) {
			return Dir.RIGHT;
		}
	}

	/**
	 * the direction the view is turning in right now, or null if it holds still
	 */
	private static Dir motionDir() {
		LocalPlayer player = Minecraft.getInstance().player;
		CombatConfig.Client client = CombatConfig.getInstance().getClient();
		float yaw = (float) Sweep.wrap(player.getYRot() - YAWS[HISTORY - 1]);
		float pitch = player.getXRot() - PITCHES[HISTORY - 1];
		if (Math.max(Math.abs(yaw), Math.abs(pitch)) < client.getDirectionThreshold()) return null;
		if (Math.abs(pitch) > Math.abs(yaw) * 0.8f) {
			boolean up = pitch < 0 != client.isInvertVertical();
			return up ? Dir.OVERHEAD : Dir.UNDER;
		}
		boolean right = yaw > 0 != client.isInvertHorizontal();
		return right ? Dir.RIGHT : Dir.LEFT;
	}

	public static void tick(Minecraft mc) {
		clientTicks++;
		LocalPlayer player = mc.player;
		if (player == null) return;
		// a new player object: respawned or changed worlds, nothing of the old fight goes on
		if (player != lastPlayer) {
			lastPlayer = player;
			own = new Fighter(rules.getMaxStamina());
		}
		System.arraycopy(YAWS, 0, YAWS, 1, HISTORY - 1);
		System.arraycopy(PITCHES, 0, PITCHES, 1, HISTORY - 1);
		YAWS[0] = player.getYRot();
		PITCHES[0] = player.getXRot();
		Dir moved = motionDir();
		if (moved != null) {
			lastDir = moved;
			lastDirAt = clientTicks;
		}

		own.setWeapon(Weapons.kind(player));
		own.setDual(Weapons.dual(player));
		boolean handles = handles() && mc.gui.screen() == null;
		if (handles && !hinted && CombatConfig.getInstance().getClient().isHints()) {
			hinted = true;
			player.sendSystemMessage(Component.translatable("realcombat.hint", mc.options.keyAttack.getTranslatedKeyMessage(),
					mc.options.keyUse.getTranslatedKeyMessage(), Keys.STAB.getTranslatedKeyMessage(), Keys.FEINT.getTranslatedKeyMessage(),
					Keys.KICK.getTranslatedKeyMessage()));
		}
		while (Keys.STAB.consumeClick()) if (handles) attack(Dir.STAB);
		while (Keys.OVERHEAD.consumeClick()) if (handles) attack(Dir.OVERHEAD);
		while (Keys.FEINT.consumeClick()) if (handles) feint();
		while (Keys.KICK.consumeClick()) if (handles) kick();
		// a parry is a tap: holding the button does not parry again and again
		boolean useDown = mc.options.keyUse.isDown();
		if (handles && useDown && !useWasDown && !onUsableBlock() && !Weapons.shield(player.getOffhandItem()) && !offhandUse()) parry();
		attackHeld = mc.options.keyAttack.isDown() ? attackHeld + 1 : 0;
		useWasDown = useDown;

		if (!Weapons.armed(player) && own.getPhase() != Phase.IDLE && own.getPhase() != Phase.STUN) own.enter(Phase.IDLE, 1);
		Rules.tick(own, rules, Weapons.speed(player));
		if (DuelBot.active()) DuelBot.ownTick(own);
		// in VR: the way the sword points as drawn, for the server's blade to be the one seen
		if (serverSpeaks && Weapons.armed(player) && ClientPlayNetworking.canSend(ru.deelter.realcombat.net.BladePayload.TYPE)) {
			ru.deelter.realcombat.compat.Vivecraft.Hand hand = VrClient.renderHand();
			if (hand != null) {
				var dir = hand.dir().normalize();
				ClientPlayNetworking.send(new ru.deelter.realcombat.net.BladePayload((float) dir.x, (float) dir.y, (float) dir.z));
			}
		}
	}

	public static void receive(StatePayload payload) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && payload.entity() == player.getId()) {
			if (DuelBot.active()) return;
			own.setStamina(payload.stamina());
			if (payload.forced()) {
				// the same swing as predicted (a blow of ours landed): keep its timing, the client is a ping ahead
				Phase phase = Phase.of(payload.phase());
				boolean same = own.getDir() == Dir.of(payload.dir())
						&& (own.getPhase() == phase || (phase == Phase.RELEASE && own.getPhase() == Phase.RECOVERY));
				// already recovering here: the landed blow makes it the short recovery of a combo
				if (same && payload.combo() && own.getPhase() == Phase.RECOVERY) {
					own.setLength(Math.min(own.getLength(), rules.scaled(rules.getComboRecovery(), Weapons.speed(player))));
				}
				if (!same) {
					own.setOffhand(payload.offhand());
					own.setPhase(Phase.of(payload.phase()));
					own.setDir(Dir.of(payload.dir()));
					own.setTicks(payload.ticks());
					own.setLength(Math.max(1, payload.length()));
				}
				own.setRiposte(payload.riposte());
				own.setCombo(payload.combo());
				// a parry of ours caught a blow on the server: the riposte window opens here too
				own.setRiposteWindow(payload.ripostes() ? Math.max(own.getRiposteWindow(), rules.getRiposteWindow() - 1) : 0);
			}
			return;
		}
		// no length: he stood down, the mechanic is off for him
		if (payload.length() == 0) {
			REMOTE.remove(payload.entity());
			return;
		}
		REMOTE.put(payload.entity(), new Remote(Phase.of(payload.phase()), Dir.of(payload.dir()), payload.ticks(),
				Math.max(1, payload.length()), payload.riposte(), clientTicks, payload.offhand()));
	}

	public static long ticks() {
		return clientTicks;
	}

	public static void forget(int entity) {
		REMOTE.remove(entity);
	}

	public static Iterable<Integer> remoteIds() {
		return REMOTE.keySet();
	}

	/**
	 * The arm an entity swings with now: the other one for a swing of the other hand (two weapons) or when the sword
	 * hand is gone.
	 */
	public static net.minecraft.world.entity.HumanoidArm armOf(net.minecraft.world.entity.LivingEntity entity) {
		Minecraft mc = Minecraft.getInstance();
		boolean off;
		if (entity == mc.player) off = own.isOffhand();
		else {
			Fighter bot = DuelBot.fighterOf(entity);
			Remote remote = REMOTE.get(entity.getId());
			off = bot != null ? bot.isOffhand() : remote != null && remote.offhand;
		}
		return Weapons.arm(entity, off);
	}

	/**
	 * What to draw for an entity, or null for one that does not fight by these rules.
	 */
	public static CombatPose poseOf(Entity entity, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) {
			if (ownFrozen != null) return ownFrozen;
			if (!handles()) return null;
			return pose(own, partial);
		}
		CombatPose frozen = DuelBot.frozenPose(entity);
		if (frozen != null) return frozen;
		Fighter bot = DuelBot.fighterOf(entity);
		if (bot != null) return pose(bot, partial);
		Remote remote = REMOTE.get(entity.getId());
		if (remote == null || !(entity instanceof net.minecraft.world.entity.LivingEntity living) || !Weapons.armed(living)) return null;
		// the message came half a ping late: the fighter is that much further on the server, where the blow lands
		float ticks = remote.ticks + (clientTicks - remote.at) + partial + lead();
		Phase phase = remote.phase;
		int length = remote.length;
		// a windup runs into its release and a release into its recovery by themselves: the next message only says so
		// a ping later, and the arm should not stop for it
		double speed = Weapons.speed(living);
		if (phase == Phase.WINDUP && ticks >= length) {
			ticks -= length;
			phase = Phase.RELEASE;
			length = rules.scaled(rules.getRelease(), speed);
		}
		if (phase == Phase.RELEASE && ticks >= length) {
			ticks -= length;
			phase = Phase.RECOVERY;
			length = rules.scaled(rules.getRecovery(), speed);
		}
		return new CombatPose(phase, remote.dir, Math.min(1f, ticks / Math.max(1, length)), remote.riposte);
	}

	/**
	 * half the own ping, in ticks (at most 4): how far behind the server a fighter's state is when it arrives
	 */
	private static float lead() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) return 0;
		var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : Math.min(4f, info.getLatency() / 100f);
	}

	private static CombatPose pose(Fighter f, float partial) {
		float progress = f.getPhase() == Phase.IDLE ? 0 : Math.min(1f, (f.getTicks() + partial) / f.getLength());
		return new CombatPose(f.getPhase(), f.getDir(), progress, f.isRiposte());
	}
}
