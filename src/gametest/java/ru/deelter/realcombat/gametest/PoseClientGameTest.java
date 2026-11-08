package ru.deelter.realcombat.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.client.CombatPose;
import ru.deelter.realcombat.client.DuelBot;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds every moment of a swing that matters for reading it on the sparring partner and takes a look from the front
 * (with the player's own first person sword in the same pose) and from the side. The shots are put together into
 * contact sheets in {@code build/run/clientGameTest/screenshots}: {@code poses-front.png} and {@code poses-side.png}.
 */
public class PoseClientGameTest implements FabricClientGameTest {
	private record Shot(String name, CombatPose pose) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// the run against Paper has a test of its own
		if (System.getProperty("realcombat.paperPort") != null) return;
		List<Shot> shots = new ArrayList<>();
		shots.add(new Shot("guard", new CombatPose(Phase.IDLE, Dir.RIGHT, 0, false)));
		for (Dir dir : Dir.values()) {
			String name = dir.name().toLowerCase();
			shots.add(new Shot(name + " windup", new CombatPose(Phase.WINDUP, dir, 1f, false)));
			shots.add(new Shot(name + " release", new CombatPose(Phase.RELEASE, dir, 0.5f, false)));
			shots.add(new Shot(name + " end", new CombatPose(Phase.RELEASE, dir, 1f, false)));
		}
		shots.add(new Shot("parry", new CombatPose(Phase.PARRY, Dir.RIGHT, 1f, false)));
		shots.add(new Shot("kick", new CombatPose(Phase.KICK, Dir.RIGHT, 0.3f, false)));
		shots.add(new Shot("shield push", new CombatPose(Phase.KICK, Dir.RIGHT, 0.3f, false, true)));
		shots.add(new Shot("stun", new CombatPose(Phase.STUN, Dir.RIGHT, 0f, false)));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("give @a iron_sword");
			context.waitTicks(20);
			context.runOnClient(client -> {
				DuelBot.start(ru.deelter.realcombat.combat.BotBrain.Difficulty.NORMAL);
				DuelBot.freeze(shots.getFirst().pose());
			});
			context.waitTicks(10);
			Vec3[] spots = context.computeOnClient(client -> {
				RemotePlayer bot = DuelBot.entity();
				Vec3 forward = Vec3.directionFromRotation(0, bot.getYRot());
				Vec3 right = new Vec3(-forward.z, 0, forward.x);
				return new Vec3[]{bot.position(), bot.position().add(forward.scale(2.2)), bot.position().add(right.scale(2.4))};
			});

			List<BufferedImage> front = new ArrayList<>();
			List<BufferedImage> side = new ArrayList<>();
			for (Shot shot : shots) {
				context.runOnClient(client -> {
					DuelBot.freeze(shot.pose());
					ClientCombat.setOwnFrozen(shot.pose());
				});
				look(context, world, spots[1], spots[0]);
				front.add(read(context.takeScreenshot("pose-front-" + shot.name().replace(' ', '-'))));
				look(context, world, spots[2], spots[0]);
				side.add(read(context.takeScreenshot("pose-side-" + shot.name().replace(' ', '-'))));
			}
			// which local axis runs along the blade: a side slash in release, the sword turned a quarter about each
			if (System.getProperty("realcombat.rollAxes") != null) {
				CombatPose slash = new CombatPose(Phase.RELEASE, Dir.RIGHT, 0.4f, false);
				for (String name : new String[]{"XP", "YP", "ZP"}) {
					com.mojang.math.Axis axis = name.equals("XP") ? com.mojang.math.Axis.XP : name.equals("YP") ? com.mojang.math.Axis.YP : com.mojang.math.Axis.ZP;
					context.runOnClient(client -> {
						ru.deelter.realcombat.client.Poses.THIRD_PERSON_BLADE = axis;
						ru.deelter.realcombat.client.Poses.FIRST_PERSON_BLADE = axis;
						DuelBot.freeze(slash);
						ClientCombat.setOwnFrozen(slash);
					});
					look(context, world, spots[1], spots[0]);
					context.takeScreenshot("roll-" + name);
				}
			}
			// the kicking leg in first person, for tuning by eye
			String tune = System.getProperty("realcombat.kickTune");
			if (tune != null) {
				CombatPose kick = new CombatPose(Phase.KICK, Dir.RIGHT, 0.3f, false);
				int n = 0;
				for (String set : tune.split(";")) {
					String[] v = set.split(",");
					int shot = n++;
					context.runOnClient(client -> {
						ru.deelter.realcombat.client.FirstPersonKick.KX = Float.parseFloat(v[0]);
						ru.deelter.realcombat.client.FirstPersonKick.KY = Float.parseFloat(v[1]);
						ru.deelter.realcombat.client.FirstPersonKick.KZ = Float.parseFloat(v[2]);
						ru.deelter.realcombat.client.FirstPersonKick.KXROT = Float.parseFloat(v[3]);
						ru.deelter.realcombat.client.FirstPersonKick.KZROT = Float.parseFloat(v[4]);
						if (v.length > 5) ru.deelter.realcombat.client.FirstPersonKick.KYROT = Float.parseFloat(v[5]);
						ClientCombat.setOwnFrozen(kick);
					});
					look(context, world, spots[1], spots[0]);
					context.takeScreenshot("kick-tune-" + shot);
				}
			}
			Path dir = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("screenshots"));
			sheet(front, shots, dir.resolve("poses-front.png"));
			sheet(side, shots, dir.resolve("poses-side.png"));
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				DuelBot.stop();
			});
		}
	}

	private static void look(ClientGameTestContext context, TestSingleplayerContext world, Vec3 from, Vec3 at) {
		Vec3 to = at.add(0, 1.2, 0).subtract(from.add(0, 1.62, 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", from.x, from.y, from.z, yaw, pitch));
		context.waitTicks(4);
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * The shots, shrunk and labelled, five to a row.
	 */
	private static void sheet(List<BufferedImage> images, List<Shot> shots, Path out) {
		int columns = 5;
		int width = 400;
		BufferedImage first = images.getFirst();
		int height = width * first.getHeight() / first.getWidth();
		int rows = (images.size() + columns - 1) / columns;
		BufferedImage sheet = new BufferedImage(width * columns, height * rows, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		for (int i = 0; i < images.size(); i++) {
			int x = i % columns * width;
			int y = i / columns * height;
			g.drawImage(images.get(i), x, y, width, height, null);
			g.setColor(Color.BLACK);
			g.fillRect(x, y, 170, 22);
			g.setColor(Color.YELLOW);
			g.drawString(shots.get(i).name(), x + 6, y + 16);
		}
		g.dispose();
		try {
			Files.createDirectories(out.getParent());
			ImageIO.write(sheet, "png", out.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
