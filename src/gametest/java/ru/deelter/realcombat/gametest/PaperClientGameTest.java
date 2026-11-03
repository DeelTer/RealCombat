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
		run(consoleFile, "realcombat reload");
		context.waitTicks(40);
		float health = context.computeOnClient(client -> client.player.getHealth());
		context.runOnClient(client -> ClientCombat.parry());
		context.waitTicks(1);
		run(consoleFile, "damage @p 4 minecraft:mob_attack by @e[tag=rc,limit=1]");
		context.waitTicks(10);
		float parried = context.computeOnClient(client -> client.player.getHealth());
		if (parried < health) throw new AssertionError("a parried blow hurt: " + health + " -> " + parried);

		write(settings, usual);
		run(consoleFile, "realcombat reload");
		// the same blow without a parry lands
		context.waitTicks(130);
		run(consoleFile, "damage @p 4 minecraft:mob_attack by @e[tag=rc,limit=1]");
		context.waitFor(client -> client.player.getHealth() < health, 60);
		// the mechanic turned off and on, as for an arena: the client follows
		run(consoleFile, "realcombat disable @a");
		context.waitFor(client -> !ClientCombat.getRules().isEnabled() && !ClientCombat.handles(), 100);
		run(consoleFile, "realcombat enable @a");
		context.waitFor(client -> ClientCombat.getRules().isEnabled() && ClientCombat.handles(), 100);
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
