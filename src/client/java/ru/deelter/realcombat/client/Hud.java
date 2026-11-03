package ru.deelter.realcombat.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Fighter;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.combat.Rules;
import ru.deelter.realcombat.combat.Sweep;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Around the crosshair: the stamina bar, and an arrow for every blow wound up at the player, on the side it will come
 * from (For Honor's indicator, which Mordhau leaves to the eye; it can be turned off). The arrow goes from yellow to
 * red as the windup runs out, and is purple for a riposte.
 */
public final class Hud {
	private static final Identifier ARROW_UP = Identifier.parse("realcombat:hud/arrow_up");
	private static final Identifier ARROW_DOWN = Identifier.parse("realcombat:hud/arrow_down");
	private static final Identifier ARROW_LEFT = Identifier.parse("realcombat:hud/arrow_left");
	private static final Identifier ARROW_RIGHT = Identifier.parse("realcombat:hud/arrow_right");

	private static final Identifier PARRIED = Identifier.parse("realcombat:hud/parried");
	private static final Identifier PARRY_BROKEN = Identifier.parse("realcombat:hud/parry_broken");
	private static final int ICON_TICKS = 25;
	private static Identifier icon;
	private static int iconColor;
	private static long iconAt = Long.MIN_VALUE / 2;

	private Hud() {
	}

	/**
	 * Takes an outcome message that has an icon: a parry (the shield) or a broken guard (the broken shield). White
	 * when it is our doing, red when it is done to us.
	 *
	 * @return the message is shown as an icon, not as words
	 */
	public static boolean outcomeIcon(Component message) {
		if (!(message.getContents() instanceof TranslatableContents translatable)) return false;
		String key = translatable.getKey();
		switch (key) {
			case "realcombat.outcome.parried" -> show(PARRIED, 0xFFFFFFFF);
			case "realcombat.outcome.parried.by" -> show(PARRIED, 0xFFFF7070);
			case "realcombat.outcome.parry_broken" -> show(PARRY_BROKEN, 0xFFFF7070);
			case "realcombat.outcome.parry_broken.by" -> show(PARRY_BROKEN, 0xFFFFFFFF);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void show(Identifier sprite, int color) {
		icon = sprite;
		iconColor = color;
		iconAt = ClientCombat.ticks();
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && MotionRecorder.active()) {
			recording(graphics, mc.font);
			return;
		}
		if (player == null || !ClientCombat.handles()) return;
		CombatConfig.Client client = CombatConfig.getInstance().getClient();
		Font font = mc.font;
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		float partial = delta.getGameTimeDeltaPartialTick(true);

		if (client.isTelegraph()) {
			for (Entity attacker : threats(mc)) {
				CombatPose pose = ClientCombat.poseOf(attacker, partial);
				if (pose == null || (pose.phase() != Phase.WINDUP && pose.phase() != Phase.RELEASE)) continue;
				if (attacker.distanceTo(player) > client.getTelegraphRange()) continue;
				// only blows wound up at us: the attacker looks our way
				if (Math.abs(Sweep.angles(attacker, player.getBoundingBox().getCenter())[0]) > 45) continue;
				int color = pose.riposte() ? 0xFFC060FF : pose.phase() == Phase.RELEASE ? 0xFFFF3030 : lerpColor(0xFFFFE040, 0xFFFF6020, pose.progress());
				arrow(graphics, font, pose.dir(), true, cx, cy, 26, 16, color);
			}
		}

		long age = ClientCombat.ticks() - iconAt;
		if (icon != null && age < ICON_TICKS) {
			float fade = Math.clamp(1 - (age + partial - ICON_TICKS / 2f) / (ICON_TICKS / 2f), 0f, 1f);
			int color = Math.round(fade * (iconColor >>> 24)) << 24 | (iconColor & 0xFFFFFF);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, cx - 8, cy + 30, 16, 16, color);
		}

		Fighter own = ClientCombat.getOwn();
		if (client.isDirectionPreview()) {
			// the own blow: where it would come from now, or where the wound up one comes from
			boolean winding = own.getPhase() == Phase.WINDUP || own.getPhase() == Phase.RELEASE;
			boolean ready = own.getPhase() == Phase.IDLE || (own.getPhase() == Phase.RECOVERY && own.isCombo());
			if (winding || ready) {
				Dir dir = winding ? own.getDir() : ClientCombat.pickDir();
				int color = own.getRiposteWindow() > 0 ? 0xFFC060FF : winding ? 0xFFFFFFFF : 0x90FFFFFF;
				arrow(graphics, font, dir, false, cx, cy, 14, 10, color);
			}
		}
		if (client.isStaminaBar()) staminaBar(graphics, own, cx, graphics.guiHeight(), partial);
		if (own.getRiposteWindow() > 0) {
			graphics.centeredText(font, Component.translatable("realcombat.hud.riposte"), cx, cy + 50, 0xFFC060FF);
		}
		if (DuelBot.active()) {
			graphics.centeredText(font, Component.translatable("realcombat.hud.duel",
					Math.round(DuelBot.getPlayerHealth()), Math.round(DuelBot.getBotHealth()), DuelBot.getWins(), DuelBot.getLosses()), cx, 8, 0xFFFFFFFF);
		}
	}

	/**
	 * The prompt of the recording, large, and a bar of the time left in the step; green while the blow is recorded.
	 */
	private static void recording(GuiGraphicsExtractor graphics, Font font) {
		Component prompt = MotionRecorder.prompt();
		if (prompt == null) return;
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 3;
		int color = MotionRecorder.recordingGo() ? 0xFF60FF60 : 0xFFFFFFFF;
		graphics.centeredText(font, prompt, cx, y, color);
		int width = 120;
		graphics.fill(cx - width / 2, y + 14, cx + width / 2, y + 16, 0x80000000);
		graphics.fill(cx - width / 2, y + 14, cx - width / 2 + Math.round(width * MotionRecorder.stepProgress()), y + 16, color);
	}

	/**
	 * An arrow on the side of the crosshair the blade comes from, pointing the way it travels.
	 *
	 * @param incoming the blow comes at us: the attacker's right is our left
	 */
	private static void arrow(GuiGraphicsExtractor graphics, Font font, Dir dir, boolean incoming, int cx, int cy, int radius, int size, int color) {
		Dir side = !incoming ? dir : dir == Dir.RIGHT ? Dir.LEFT : dir == Dir.LEFT ? Dir.RIGHT : dir;
		int half = size / 2;
		switch (side) {
			// pointing the way the blade travels: down from above, right from the left side...
			case OVERHEAD -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_DOWN, cx - half, cy - radius - half, size, size, color);
			case UNDER -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_UP, cx - half, cy + radius - half, size, size, color);
			case RIGHT -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_LEFT, cx + radius - half, cy - half, size, size, color);
			case LEFT -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_RIGHT, cx - radius - half, cy - half, size, size, color);
			case STAB -> graphics.centeredText(font, "◆", cx, cy + radius / 2 - 2, color);
		}
	}

	/**
	 * Over the food bar, the width of it, thin and half see-through as the experience bar: it shows while stamina is
	 * spent and fades away a second after it is full again. Yellow-green when fresh, orange and blinking when tired
	 * (slower windups, no feints, no chambers), red when empty (the next parry breaks).
	 */
	private static void staminaBar(GuiGraphicsExtractor graphics, Fighter own, int cx, int height, float partial) {
		float max = ClientCombat.getRules().getMaxStamina();
		float share = max <= 0 ? 0 : Math.clamp(own.getStamina() / max, 0f, 1f);
		long now = ClientCombat.ticks();
		if (share < 0.999f) lastSpent = now;
		float shown = Math.clamp(1 - (now - lastSpent - 20 + partial) / 20f, 0f, 1f);
		if (shown <= 0) return;
		int alpha = Math.round(shown * 0xB0);
		int width = 81;
		int x = cx + 10;
		int y = height - 52;
		graphics.fill(x, y, x + width, y + 3, alpha << 24);
		boolean tired = Rules.tired(own, ClientCombat.getRules());
		int color = share <= 0 ? 0xE03030 : tired ? ((now / 4) % 2 == 0 ? 0xFF8020 : 0xC05010) : 0x9BD040;
		graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * share), y + 2, alpha << 24 | color);
	}

	private static long lastSpent = Long.MIN_VALUE / 2;

	private static List<Entity> threats(Minecraft mc) {
		List<Entity> list = new ArrayList<>();
		for (int id : ClientCombat.remoteIds()) {
			Entity entity = mc.level.getEntity(id);
			if (entity instanceof LivingEntity) list.add(entity);
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (DuelBot.fighterOf(entity) != null) list.add(entity);
		}
		return list;
	}

	private static int lerpColor(int from, int to, float t) {
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
