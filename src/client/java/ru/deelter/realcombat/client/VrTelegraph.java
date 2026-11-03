package ru.deelter.realcombat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Sweep;
import ru.deelter.realcombat.compat.Vivecraft;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.mixin.client.DisplayAccessor;
import ru.deelter.realcombat.mixin.client.TextDisplayAccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The arrows of blows wound up at a VR player, in the world instead of on the HUD: Vivecraft's HUD and crosshair go
 * with the sword hand, so an arrow at the crosshair moves with every swing. Here an arrow floats a little in front of
 * the head, on the line to the attacker, on the side the blade comes from.
 */
public final class VrTelegraph {
	private static final int FIRST_ID = -7_700_300;
	private static final double AHEAD = 1.1;
	private static final double SIDE = 0.28;
	private static final Map<Integer, Display.TextDisplay> ARROWS = new HashMap<>();

	private VrTelegraph() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		Vivecraft.Hand head = VrClient.head();
		if (player == null || mc.level == null || head == null || !CombatConfig.getInstance().getClient().isTelegraph()) {
			clear(mc);
			return;
		}
		Set<Integer> shown = new HashSet<>();
		for (Entity attacker : Hud.threats(mc)) {
			CombatPose pose = ClientCombat.poseOf(attacker, 0);
			if (pose == null || (pose.phase() != Phase.WINDUP && pose.phase() != Phase.RELEASE)) continue;
			if (attacker.distanceTo(player) > CombatConfig.getInstance().getClient().getTelegraphRange()) continue;
			if (Math.abs(Sweep.angles(attacker, player.getBoundingBox().getCenter())[0]) > 45) continue;
			show(mc, attacker, pose, head.pos());
			shown.add(attacker.getId());
		}
		for (Integer id : Set.copyOf(ARROWS.keySet())) {
			if (!shown.contains(id)) remove(mc, id);
		}
	}

	private static void show(Minecraft mc, Entity attacker, CombatPose pose, Vec3 eye) {
		Display.TextDisplay arrow = ARROWS.computeIfAbsent(attacker.getId(), id -> {
			Display.TextDisplay created = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, mc.level);
			created.setId(FIRST_ID - ARROWS.size());
			((DisplayAccessor) created).realcombat$setBillboardConstraints(Display.BillboardConstraints.CENTER);
			mc.level.addEntity(created);
			return created;
		});
		Vec3 to = ((LivingEntity) attacker).getEyePosition().subtract(eye);
		Vec3 forward = to.horizontal().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : to.horizontal().normalize();
		// our right, looking at the attacker
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 at = eye.add(to.normalize().scale(AHEAD));
		String glyph;
		switch (pose.dir()) {
			case OVERHEAD -> {
				at = at.add(0, SIDE, 0);
				glyph = "▼";
			}
			case UNDER -> {
				at = at.add(0, -SIDE, 0);
				glyph = "▲";
			}
			// the attacker's right is our left
			case RIGHT -> {
				at = at.subtract(right.scale(SIDE));
				glyph = "▶";
			}
			case LEFT -> {
				at = at.add(right.scale(SIDE));
				glyph = "◀";
			}
			default -> glyph = "◆";
		}
		arrow.setPos(at.x, at.y, at.z);
		int color = pose.riposte() ? 0xC060FF : pose.phase() == Phase.RELEASE ? 0xFF3030 : pose.progress() > 0.5f ? 0xFF8020 : 0xFFE040;
		((TextDisplayAccessor) arrow).realcombat$setText(Component.literal(glyph).withStyle(style -> style.withColor(TextColor.fromRgb(color)).withBold(true)));
	}

	private static void remove(Minecraft mc, int attacker) {
		Display.TextDisplay arrow = ARROWS.remove(attacker);
		if (arrow != null && mc.level != null && arrow.level() == mc.level) mc.level.removeEntity(arrow.getId(), Entity.RemovalReason.DISCARDED);
	}

	public static void clear(Minecraft mc) {
		for (Integer id : Set.copyOf(ARROWS.keySet())) remove(mc, id);
	}
}
