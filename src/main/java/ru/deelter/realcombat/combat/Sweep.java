package ru.deelter.realcombat.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.config.CombatConfig;

/**
 * {@link Arc} for Minecraft entities: angles, reach and the way the blade moves through the world.
 */
public final class Sweep {
	private Sweep() {
	}

	public static boolean crosses(Dir dir, float from, float to, double yaw, double pitch, CombatConfig.Rules r) {
		return Arc.crosses(dir, from, to, yaw, pitch, r);
	}

	/**
	 * Degrees from where {@code viewer} looks to {@code point}: [yaw, positive to the right; pitch, positive down].
	 */
	public static double[] angles(Entity viewer, Vec3 point) {
		Vec3 eye = viewer.getEyePosition();
		return Arc.angles(eye.x, eye.y, eye.z, viewer.getYRot(), viewer.getXRot(), point.x, point.y, point.z);
	}

	/**
	 * {@code defender} looks towards {@code attacker} closely enough to parry or block him
	 */
	public static boolean facing(Entity defender, Entity attacker, CombatConfig.Rules r) {
		return Math.abs(angles(defender, attacker.getEyePosition())[0]) <= r.getGuardArc();
	}

	/**
	 * The point of the target's box closest to the attacker's eyes.
	 */
	public static Vec3 closest(Entity attacker, Entity target) {
		return closest(attacker.getEyePosition(), target.getBoundingBox());
	}

	public static Vec3 closest(Vec3 eye, net.minecraft.world.phys.AABB box) {
		return new Vec3(Math.clamp(eye.x, box.minX, box.maxX), Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ));
	}

	/**
	 * The way the blade moves through the world, for wounds that follow it.
	 */
	public static Vec3 swing(Entity attacker, Dir dir) {
		Vec3 look = attacker.getViewVector(1f);
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		return switch (dir) {
			case RIGHT -> right.scale(-1);
			case LEFT -> right;
			case OVERHEAD -> new Vec3(0, -1, 0).add(look.scale(0.3)).normalize();
			case UNDER -> new Vec3(0, 1, 0).add(look.scale(0.3)).normalize();
			case STAB -> look;
		};
	}

	/**
	 * The blocks a blade can't pass: whole cubes only. Grass, flowers, slabs, fences, leaves and the like let it
	 * through, so a low swing does not catch on the undergrowth.
	 */
	public static java.util.List<ru.deelter.realcombat.combat.Blade.Box> solidBlocks(net.minecraft.world.level.Level level, net.minecraft.world.phys.AABB area) {
		java.util.List<ru.deelter.realcombat.combat.Blade.Box> boxes = new java.util.ArrayList<>();
		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
				net.minecraft.util.Mth.floor(area.minX), net.minecraft.util.Mth.floor(area.minY), net.minecraft.util.Mth.floor(area.minZ),
				net.minecraft.util.Mth.floor(area.maxX), net.minecraft.util.Mth.floor(area.maxY), net.minecraft.util.Mth.floor(area.maxZ))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(net.minecraft.tags.BlockTags.LEAVES) || !state.isCollisionShapeFullBlock(level, pos)) continue;
			boxes.add(new ru.deelter.realcombat.combat.Blade.Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
		}
		return boxes;
	}

	public static double wrap(double degrees) {
		return Arc.wrap(degrees);
	}
}
