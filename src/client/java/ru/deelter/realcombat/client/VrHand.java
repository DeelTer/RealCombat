package ru.deelter.realcombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.deelter.realcombat.combat.Blade;
import ru.deelter.realcombat.combat.Weapons;
import ru.deelter.realcombat.compat.Vivecraft;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The own sword hand in VR, as it is drawn: a {@link Blade} run every frame against the bodies, blocks and the other VR
 * players' blades around, so the hand and the sword in it stop where the blade touched instead of sinking into a mob.
 * Vivecraft draws the hand at {@link #offset} from the controller ({@code RenderHelperMixin}). The server runs the
 * same blade for what it hurts; this one is for the eye and for the buzz of the controller.
 */
public final class VrHand {
	private static final long FRAME_NANOS = 4_000_000;
	private static Blade blade;
	private static Vec3 offset = Vec3.ZERO;
	private static long lastStep;
	private static boolean touching;
	/**
	 * the body the blade is stuck in, where it was, and when the controller last buzzed for the blade moving in it
	 */
	private static Entity stuckIn;
	private static Vec3 stuckAt;
	private static long lastGrind;
	private static Vec3 tipBefore;
	/**
	 * where the tip is and how it moves, blocks per tick, for the sparring partner to read
	 */
	private static Vec3 tip;
	private static Vec3 tipMotion = Vec3.ZERO;

	private VrHand() {
	}

	public static Vec3 tip() {
		return tip;
	}

	public static Vec3 tipMotion() {
		return tipMotion;
	}

	public static double tipSpeed() {
		return tipMotion.length();
	}

	/**
	 * the own VR blade lies across a blow from {@code from} to {@code to}
	 */
	public static boolean blocks(Vec3 from, Vec3 to) {
		if (blade == null) return false;
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		return Blade.distance(blade.getHand(), blade.tip(), v(from), v(to)) <= compat.getVrBladeRadius() * 2 + compat.getVrParryDistance();
	}

	/**
	 * how far the drawn sword hand is from the controller this frame
	 */
	public static Vec3 offset() {
		update();
		return offset;
	}

	private static void update() {
		long now = System.nanoTime();
		if (now - lastStep < FRAME_NANOS) return;
		double ticks = blade == null ? 1 : Math.min(1, (now - lastStep) / 50_000_000.0);
		lastStep = now;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		CombatConfig.Compat compat = CombatConfig.getInstance().getCompat();
		Vivecraft.Hand hand = player != null && compat.isVivecraft() && compat.isVrPhysics() && Weapons.armed(player) ? VrClient.renderHand() : null;
		if (hand == null || mc.level == null) {
			blade = null;
			offset = Vec3.ZERO;
			return;
		}
		if (blade == null) blade = new Blade(compat.getVrBladeLength(), compat.getVrBladeRadius());
		AABB area = new AABB(vec(blade.getHand()), hand.pos()).inflate(compat.getVrBladeLength() + 0.5);
		List<Blade.Box> bodies = new ArrayList<>();
		List<Entity> owners = new ArrayList<>();
		List<Blade.Segment> blades = new ArrayList<>();
		for (Entity entity : mc.level.getEntities(player, area)) {
			if (entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
				bodies.add(box(living.getBoundingBox()));
				owners.add(living);
			}
			if (entity instanceof Player other && Weapons.armed(other)) {
				Vivecraft.Hand theirs = VrClient.renderHand(other);
				if (theirs != null) {
					blades.add(new Blade.Segment(v(theirs.pos()), v(theirs.pos().add(theirs.dir().normalize().scale(compat.getVrBladeLength()))),
							compat.getVrBladeRadius()));
				}
			}
		}
		// the sparring partner's blade, while it parries or swings
		int botBlade = -1;
		Blade.Segment theirs = DuelBot.blade();
		if (theirs != null) {
			botBlade = blades.size();
			blades.add(theirs);
		}
		List<Blade.Box> solids = ru.deelter.realcombat.combat.Sweep.solidBlocks(mc.level, area);
		// the spring is set per tick; a frame makes its share of a tick's way
		double follow = 1 - Math.pow(1 - compat.getVrFollow(), ticks);
		if (blade.isLodged()) {
			if (stuckIn == null || !stuckIn.isAlive() || stuckIn.isRemoved()) {
				blade.unlodge();
				stuckIn = null;
			} else {
				blade.shift(v(stuckIn.position().subtract(stuckAt)));
				stuckAt = stuckIn.position();
			}
		}
		if (tipBefore == null) tipBefore = vec(blade.tip());
		Blade.Result result = blade.step(v(hand.pos()), v(hand.dir().normalize()), bodies, blades, solids, follow, compat.getVrMaxStretch());
		offset = vec(blade.offset(v(hand.pos())));
		Vec3 motion = vec(result.at()).subtract(tipBefore);
		// a frame is a share of a tick: speeds are per tick, as on the server
		double speed = result.speed() / Math.max(ticks, 1e-3);
		tip = vec(blade.tip());
		tipMotion = motion.scale(1 / Math.max(ticks, 1e-3));
		boolean onBot = result.contact() == Blade.Contact.BODY && owners.get(result.index()) == DuelBot.entity();
		tipBefore = vec(blade.tip());
		switch (result.contact()) {
			case BODY -> {
				// point first and fast: the blade goes in and sticks, as on the server
				boolean thrust = compat.isVrStab() && speed >= compat.getVrSwingSpeed() * 0.55 && motion.lengthSqr() > 1e-8
						&& motion.normalize().dot(hand.dir().normalize()) > 0.7;
				// the sparring partner lives on this client only: its cuts are counted here
				if (onBot && (thrust || speed >= compat.getVrSwingSpeed())) DuelBot.vrStrike(thrust ? ru.deelter.realcombat.combat.Dir.STAB : classify(player, motion), speed / compat.getVrSwingSpeed());
				if (thrust) {
					blade.lodge(compat.getVrStabDepth());
					stuckIn = owners.get(result.index());
					stuckAt = stuckIn.position();
					VrClient.buzz(1f);
					touching = true;
					return;
				}
			}
			case LODGED -> {
				// a light grind while the blade moves in the wound
				if (result.speed() > 0.004 && now - lastGrind > 60_000_000L) {
					lastGrind = now;
					VrClient.buzz(0.15f);
				}
				return;
			}
			case FREED -> {
				if (stuckIn != null && stuckIn == DuelBot.entity()) DuelBot.vrPullOut();
				stuckIn = null;
				VrClient.buzz(0.7f);
				touching = false;
				return;
			}
			case BLADE -> {
				if (result.index() == botBlade && speed >= compat.getVrSwingSpeed() * 0.5) DuelBot.vrClash(speed);
			}
			default -> {
			}
		}
		boolean touch = result.contact() != Blade.Contact.NONE;
		// a knock on touching, not a buzz all along a blade resting on something
		if (touch && !touching) VrClient.buzz((float) Math.min(1, speed / compat.getVrSwingSpeed()));
		touching = touch;
	}

	private static ru.deelter.realcombat.combat.Dir classify(LocalPlayer player, Vec3 motion) {
		Vec3 look = player.getViewVector(1f);
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		double side = motion.dot(right);
		double up = motion.y;
		if (motion.length() > 1e-6 && motion.dot(look) > motion.length() * 0.8) return ru.deelter.realcombat.combat.Dir.STAB;
		if (Math.abs(up) > Math.abs(side)) return up < 0 ? ru.deelter.realcombat.combat.Dir.OVERHEAD : ru.deelter.realcombat.combat.Dir.UNDER;
		return side < 0 ? ru.deelter.realcombat.combat.Dir.RIGHT : ru.deelter.realcombat.combat.Dir.LEFT;
	}

	private static Blade.V v(Vec3 v) {
		return new Blade.V(v.x, v.y, v.z);
	}

	private static Vec3 vec(Blade.V v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	private static Blade.Box box(AABB a) {
		return new Blade.Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ);
	}
}
