package ru.deelter.realcombat.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Weapons;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Against a real Paper server with the plugin: {@code gradlew runClientGameTest -PpaperPort=25599
 * -PpaperConsole=<file the server reads its console from>}. Skipped otherwise. Checks the hello and the rules, a swing
 * the plugin lands on a mob, a mob's blow the plugin parries, and the same blow landing without a parry.
 */
public class PaperClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		String port = System.getProperty("realcombat.paperPort");
		String console = System.getProperty("realcombat.paperConsole");
		if (port == null || console == null) return;
		Path consoleFile = Path.of(console);

		context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client,
				ServerAddress.parseString("localhost:" + port), new ServerData("paper", "localhost:" + port, ServerData.Type.OTHER),
				false, null));
		context.waitFor(client -> client.level != null && client.player != null, 1200);
		context.waitFor(client -> ClientCombat.isServerSpeaks(), 200);
		context.runOnClient(client -> {
			if (client.player.isDeadOrDying()) client.player.respawn();
		});
		run(consoleFile, "gamerule spawn_monsters false", "gamerule doMobSpawning false", "kill @e[type=!player]", "gamemode survival @a", "time set 18000", "effect give @a night_vision infinite 0 true", "effect give @a instant_health 1 10 true",
				"tp @a 0 -60 0 0 0", "item replace entity @a weapon.mainhand with iron_sword",
				"summon zombie 0 -60 2.2 {NoAI:1b,active_effects:[{id:\"minecraft:fire_resistance\",duration:-1}],PersistenceRequired:1b,Silent:1b,Tags:[\"rc\"],Rotation:[180f,0f]}");
		context.waitFor(client -> client.player != null && Weapons.armed(client.player) && zombie(client) != null, 200);
		context.waitTicks(20);

		// a swing the plugin lands
		float before = context.computeOnClient(client -> zombie(client).getHealth());
		context.runOnClient(client -> {
			client.player.setYRot(0);
			client.player.setXRot(10);
			ClientCombat.attack(Dir.OVERHEAD);
		});
		context.waitFor(client -> zombie(client).getHealth() < before, 60);
		context.takeScreenshot("realcombat-paper-swing");

		// a mob's blow in the parry is parried. The console is read about once a second: the parry is held long for it
		context.waitTicks(30);
		Path settings = consoleFile.resolveSibling("plugins/RealCombat/realcombat.json");
		String usual = read(settings);
		write(settings, usual.replaceFirst("\"parry\": \\d+", "\"parry\": 100"));
		run(consoleFile, "realcombatadmin reload");
		context.waitTicks(40);
		float health = context.computeOnClient(client -> client.player.getHealth());
		context.runOnClient(client -> ClientCombat.parry());
		context.waitTicks(1);
		run(consoleFile, "damage @p 4 minecraft:mob_attack by @e[tag=rc,limit=1]");
		context.waitTicks(10);
		float parried = context.computeOnClient(client -> client.player.getHealth());
		if (parried < health) throw new AssertionError("a parried blow hurt: " + health + " -> " + parried);

		write(settings, usual);
		run(consoleFile, "realcombatadmin reload");
		// the same blow without a parry lands
		context.waitTicks(130);
		run(consoleFile, "damage @p 4 minecraft:mob_attack by @e[tag=rc,limit=1]");
		context.waitFor(client -> client.player.getHealth() < health, 60);
		// the mechanic turned off and on, as for an arena: the client follows
		run(consoleFile, "realcombatadmin disable @a");
		context.waitFor(client -> !ClientCombat.getRules().isEnabled() && !ClientCombat.handles(), 100);
		run(consoleFile, "realcombatadmin enable @a");
		context.waitFor(client -> ClientCombat.getRules().isEnabled() && ClientCombat.handles(), 100);
		// a kill with a mace smash tears the body apart: every piece that flies keeps its skin
		run(consoleFile, "summon husk 1.5 -60 2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"mace\"]}");
		context.waitTicks(40);
		run(consoleFile, "item replace entity @a weapon.mainhand with mace", "damage @e[tag=mace,limit=1] 1000 minecraft:mace_smash by @p");
		context.waitTicks(60);
		run(consoleFile, "tp @a 1.5 -60 0.8 0 60");
		context.waitTicks(30);
		context.takeScreenshot("realcombat-paper-mace");
		net.minecraft.world.phys.AABB lying = context.computeOnClient(client -> {
			var all = ru.deelter.chopchop.client.api.ChopChopClientApi.corpses(client.player.getBoundingBox().inflate(6));
			return all.isEmpty() ? null : all.getLast().box();
		});
		if (lying != null) {
			var c = lying.getCenter();
			System.out.println("[mace] body box " + lying);
			run(consoleFile, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f -45 40", c.x - 1.4, lying.minY, c.z - 1.4));
			context.waitTicks(30);
			context.takeScreenshot("realcombat-paper-mace-close");
			run(consoleFile, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 135 40", c.x + 1.4, lying.minY, c.z + 1.4));
			context.waitTicks(30);
			context.takeScreenshot("realcombat-paper-mace-close2");
		}
		context.runOnClient(client -> {
			try {
				Class<?> debris = Class.forName("ru.deelter.chopchop.client.render.Debris");
				var f = debris.getDeclaredField("CHUNKS");
				f.setAccessible(true);
				Class<?> chunk = Class.forName("ru.deelter.chopchop.client.render.Debris$Chunk");
				var part = chunk.getDeclaredField("part");
				var cut = chunk.getDeclaredField("cut");
				var mask = chunk.getDeclaredField("mask");
				part.setAccessible(true);
				cut.setAccessible(true);
				mask.setAccessible(true);
				var gone = Class.forName("ru.deelter.chopchop.client.render.VoxelMask").getMethod("gone", int.class, int.class, int.class);
				for (Object c : (java.util.List<?>) f.get(null)) {
					Object p = part.get(c);
					int gx = (int) p.getClass().getMethod("gx").invoke(p), gy = (int) p.getClass().getMethod("gy").invoke(p), gz = (int) p.getClass().getMethod("gz").invoke(p);
					int kept = 0;
					for (int x = 0; x < gx; x++) for (int y = 0; y < gy; y++) for (int z = 0; z < gz; z++) if (!(boolean) gone.invoke(mask.get(c), x, y, z)) kept++;
					System.out.println("[mace] piece " + p.getClass().getMethod("getName").invoke(p) + " cut=" + cut.get(c) + " kept=" + kept);
				}
				// and the body on the ground keeps none of them
				var wounds = Class.forName("ru.deelter.chopchop.client.ClientWounds").getMethod("get", int.class);
				for (var body : ru.deelter.chopchop.client.api.ChopChopClientApi.corpses(client.player.getBoundingBox().inflate(6))) {
					Object state = wounds.invoke(null, body.id());
					if (state == null) continue;
					StringBuilder kept = new StringBuilder();
					int count = (int) state.getClass().getMethod("partCount").invoke(state);
					for (int i = 0; i < count; i++) {
						Object ps = state.getClass().getMethod("part", int.class).invoke(state, i);
						Object def = ps.getClass().getMethod("getPart").invoke(ps);
						String name = (String) def.getClass().getMethod("getName").invoke(def);
						if (!(boolean) ps.getClass().getMethod("severed").invoke(ps)) kept.append(name).append(' ');
					}
					System.out.println("[mace] body " + body.id() + " keeps: " + kept);
				}
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		run(consoleFile, "kill @e[tag=rc]");
		context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
		context.waitTicks(20);
	}

	private static Zombie zombie(net.minecraft.client.Minecraft client) {
		if (client.level == null) return null;
		// the one summoned in front of the player, not one the night brought
		Zombie closest = null;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Zombie zombie && zombie.isAlive() && !zombie.isRemoved() && zombie.distanceToSqr(0, -60, 2.2) < 1
					&& (closest == null || zombie.distanceToSqr(0, -60, 2.2) < closest.distanceToSqr(0, -60, 2.2))) closest = zombie;
		}
		return closest;
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void run(Path console, String... commands) {
		try {
			Files.writeString(console, String.join("\n", commands) + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
