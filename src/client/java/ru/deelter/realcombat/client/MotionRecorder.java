package ru.deelter.realcombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import ru.deelter.realcombat.mixin.client.DisplayAccessor;
import ru.deelter.realcombat.mixin.client.TextDisplayAccessor;
import ru.deelter.realcombat.RealCombat;
import ru.deelter.realcombat.combat.Dir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /realcombat record}: a script of prompts for a VR player — a body calibration, then every blow a few times
 * with a countdown, a guard, and a while of free swings — recording the head and both hands every frame. The file
 * ({@code realcombat-motion/<time>.json} in the game folder) is for shaping the swing animations on real motion.
 * <p>
 * Kept raw on purpose: positions in the world with the player's own position and body turn next to them, rotations as
 * quaternions, a timestamp per sample, no filtering. Each sample carries the label of the step it was taken in, the
 * countdowns too, so the start of a blow can be found from the "go" mark and from the motion itself.
 */
public final class MotionRecorder {
	private static final long MIN_FRAME_NANOS = 8_000_000;
	private static final int SECOND = 20;

	private record Step(String label, Component prompt, int ticks) {
	}

	private static List<Step> script;
	private static int step = -1;
	private static int stepTicks;
	private static long startNanos;
	private static long lastSample;
	private static StringBuilder samples;
	private static int count;
	private static String header;
	/**
	 * the prompt, floating in front of the eyes: the HUD of Vivecraft is on a hand, and reading it means holding the
	 * hand up
	 */
	private static Display.TextDisplay board;
	private static final int BOARD_ID = -7_700_200;

	private MotionRecorder() {
	}

	public static boolean active() {
		return script != null;
	}

	public static void start(int reps) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;
		VrClient.Sample first = VrClient.sample();
		if (first == null) {
			player.sendSystemMessage(Component.translatable("realcombat.record.no_vr"));
			return;
		}
		script = script(Math.clamp(reps, 1, 10));
		step = 0;
		stepTicks = 0;
		count = 0;
		samples = new StringBuilder();
		startNanos = System.nanoTime();
		header = String.format(Locale.ROOT, "\"version\":1,\"mod\":\"realcombat\",\"scale\":%.4f,\"seated\":%b,\"leftHanded\":%b,\"eyeHeight\":%.4f,\"reps\":%d",
				first.scale(), first.seated(), first.leftHanded(), player.getEyeHeight(), reps);
		player.sendSystemMessage(Component.translatable("realcombat.record.started", script.size()));
		board = new Display.TextDisplay(net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY, mc.level);
		board.setId(BOARD_ID);
		((DisplayAccessor) board).realcombat$setBillboardConstraints(Display.BillboardConstraints.CENTER);
		placeBoard();
		mc.level.addEntity(board);
	}

	/**
	 * Two metres in front of the head, at eye height, with the prompt of the step.
	 */
	private static void placeBoard() {
		Minecraft mc = Minecraft.getInstance();
		VrClient.Sample sample = VrClient.sample();
		if (board == null || sample == null || mc.player == null) return;
		double[] head = sample.head();
		float yaw = mc.player.getYRot();
		double x = head[0] - Math.sin(Math.toRadians(yaw)) * 2;
		double z = head[2] + Math.cos(Math.toRadians(yaw)) * 2;
		board.setPos(x, head[1] - 0.1, z);
		Component prompt = prompt();
		if (prompt != null) {
			((TextDisplayAccessor) board).realcombat$setText(prompt.copy().withStyle(recordingGo() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
		}
	}

	private static void removeBoard() {
		Minecraft mc = Minecraft.getInstance();
		if (board != null && mc.level != null && board.level() == mc.level) mc.level.removeEntity(BOARD_ID, Entity.RemovalReason.DISCARDED);
		board = null;
	}

	private static void cue(net.minecraft.core.Holder<SoundEvent> sound, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch));
	}

	public static void stop() {
		if (script == null) return;
		finish(false);
	}

	private static List<Step> script(int reps) {
		List<Step> steps = new ArrayList<>();
		// calibration: the body at rest, the reach, the top of the reach
		steps.add(new Step("calib.rest", Component.translatable("realcombat.record.calib.rest"), 3 * SECOND));
		steps.add(new Step("calib.forward", Component.translatable("realcombat.record.calib.forward"), 3 * SECOND));
		steps.add(new Step("calib.up", Component.translatable("realcombat.record.calib.up"), 3 * SECOND));
		steps.add(new Step("calib.guard", Component.translatable("realcombat.record.calib.guard"), 3 * SECOND));
		for (Dir dir : Dir.values()) {
			String name = dir.name().toLowerCase(Locale.ROOT);
			Component blow = Component.translatable("realcombat.record.dir." + name);
			for (int rep = 1; rep <= reps; rep++) {
				steps.add(new Step(name + "#" + rep + ".ready", Component.translatable("realcombat.record.ready", blow, rep, reps), SECOND));
				for (int n = 3; n >= 1; n--) {
					steps.add(new Step(name + "#" + rep + ".count" + n, Component.translatable("realcombat.record.count", blow, n), SECOND / 2));
				}
				steps.add(new Step(name + "#" + rep + ".go", Component.translatable("realcombat.record.go", blow), (int) (1.5 * SECOND)));
			}
		}
		for (int rep = 1; rep <= reps; rep++) {
			steps.add(new Step("parry#" + rep + ".ready", Component.translatable("realcombat.record.parry.ready", rep, reps), SECOND));
			steps.add(new Step("parry#" + rep + ".go", Component.translatable("realcombat.record.parry.go"), (int) (1.5 * SECOND)));
		}
		steps.add(new Step("free", Component.translatable("realcombat.record.free"), 12 * SECOND));
		return steps;
	}

	/**
	 * the prompt on screen now, or null
	 */
	public static Component prompt() {
		return script == null || step < 0 ? null : script.get(step).prompt();
	}

	/**
	 * the share of the current step gone, for a bar under the prompt
	 */
	public static float stepProgress() {
		return script == null ? 0 : Math.min(1f, stepTicks / (float) script.get(step).ticks());
	}

	public static boolean recordingGo() {
		return script != null && script.get(step).label().endsWith(".go");
	}

	public static void tick() {
		if (script == null) return;
		placeBoard();
		if (++stepTicks < script.get(step).ticks()) return;
		stepTicks = 0;
		step++;
		if (step >= script.size()) {
			finish(true);
			return;
		}
		// a click in the controller for each count and for the go
		String label = script.get(step).label();
		// heard and felt, not only seen: a click for each count, a bell for the blow
		if (label.contains(".count")) {
			VrClient.buzz(0.1f);
			cue(SoundEvents.NOTE_BLOCK_HAT, 1.2f);
		}
		if (label.endsWith(".go")) {
			VrClient.buzz(0.5f);
			cue(SoundEvents.NOTE_BLOCK_BELL, 1f);
		}
	}

	/**
	 * One sample a frame (a frame of both eyes once).
	 */
	public static void frame() {
		if (script == null) return;
		long now = System.nanoTime();
		if (now - lastSample < MIN_FRAME_NANOS) return;
		LocalPlayer player = Minecraft.getInstance().player;
		VrClient.Sample sample = VrClient.sample();
		if (player == null || sample == null) return;
		lastSample = now;
		if (count > 0) samples.append(",\n");
		samples.append(String.format(Locale.ROOT, "{\"t\":%.1f,\"step\":\"%s\",\"player\":[%.4f,%.4f,%.4f],\"bodyYaw\":%.2f,\"yaw\":%.2f,\"pitch\":%.2f,\"head\":%s,\"main\":%s,\"off\":%s}",
				(now - startNanos) / 1e6, script.get(step).label(), player.getX(), player.getY(), player.getZ(), player.yBodyRot, player.getYRot(),
				player.getXRot(), array(sample.head()), array(sample.main()), array(sample.off())));
		count++;
	}

	private static String array(double[] values) {
		if (values == null) return "null";
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) out.append(',');
			out.append(String.format(Locale.ROOT, "%.5f", values[i]));
		}
		return out.append(']').toString();
	}

	private static void finish(boolean complete) {
		Minecraft mc = Minecraft.getInstance();
		String steps = String.join(",", script.stream().map(s -> "[\"" + s.label() + "\"," + s.ticks() + "]").toList());
		String json = "{" + header + ",\"complete\":" + complete + ",\"steps\":[" + steps + "],\n\"samples\":[\n" + samples + "\n]}\n";
		script = null;
		step = -1;
		removeBoard();
		try {
			Path dir = mc.gameDirectory.toPath().resolve("realcombat-motion");
			Files.createDirectories(dir);
			Path file = dir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json");
			Files.writeString(file, json);
			if (mc.player != null) mc.player.sendSystemMessage(Component.translatable("realcombat.record.saved", count, file.toAbsolutePath().toString()));
		} catch (IOException e) {
			RealCombat.LOG.warn("Can't write the motion record", e);
		}
	}
}
