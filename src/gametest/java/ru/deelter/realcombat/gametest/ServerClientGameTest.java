package ru.deelter.realcombat.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.server.CombatServer;

import java.util.Properties;

/**
 * The client against a dedicated server over the network, as in multiplayer: the hello and the server's rules, a
 * swing that the server lands on a mob, a parry the server sees, and the stamina the server sends back.
 */
public class ServerClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// the run against Paper has a test of its own
		if (System.getProperty("realcombat.paperPort") != null) return;
		vivecraftMixin();
		Properties properties = new Properties();
		properties.setProperty("online-mode", "false");
		properties.setProperty("spawn-protection", "0");
		properties.setProperty("difficulty", "easy");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			server.runCommand("time set 6000");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a iron_sword");
			context.waitFor(client -> ClientCombat.isServerSpeaks());
			context.waitFor(client -> client.player != null && ru.deelter.realcombat.combat.Weapons.armed(client.player));

			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(5);
			server.runCommand("execute as @a at @s rotated 0 0 run summon zombie ^ ^ ^2.2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"realcombat\"]}");
			context.waitTicks(10);
			float before = server.computeOnServer(ServerClientGameTest::zombieHealth);

			// a swing: the client starts it, the server lands it
			context.runOnClient(client -> ClientCombat.attack(Dir.OVERHEAD));
			server.waitFor(s -> zombieHealth(s) < before, 60);
			context.takeScreenshot("realcombat-server-swing");

			// a parry: the server enters it from the client's message
			context.waitTicks(30);
			context.runOnClient(client -> ClientCombat.parry());
			server.waitFor(s -> CombatServer.fighter(player(s)).getPhase() == Phase.PARRY, 20);

			// the mechanic turned off and on for a player, as for an arena: his client follows
			server.runCommand("realcombatadmin disable @a");
			context.waitFor(client -> !ClientCombat.getRules().isEnabled() && !ClientCombat.handles(), 40);
			server.waitFor(s -> CombatServer.fighter(player(s)) == null, 20);
			server.runCommand("realcombatadmin enable @a");
			context.waitFor(client -> ClientCombat.getRules().isEnabled() && ClientCombat.handles(), 40);

			// a missed parry costs nothing, a whiff does: the server's stamina reaches the client
			context.waitTicks(30);
			context.runOnClient(client -> ClientCombat.attack(Dir.RIGHT));
			server.runCommand("kill @e[tag=realcombat]");
			context.waitTicks(25);
			float serverStamina = server.computeOnServer(s -> CombatServer.fighter(player(s)).getStamina());
			context.waitTicks(25);
			float clientStamina = context.computeOnClient(client -> ClientCombat.getOwn().getStamina());
			if (Math.abs(serverStamina - clientStamina) > 20) {
				throw new AssertionError("client stamina " + clientStamina + " is far from the server's " + serverStamina);
			}
		}
	}

	/**
	 * With Vivecraft on the class path ({@code -PwithVivecraft}) the sword hand handler is woven into its renderer.
	 */
	private static void vivecraftMixin() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vivecraft")) return;
		try {
			Class<?> renderer = Class.forName("org.vivecraft.client_vr.render.helpers.RenderHelper");
			for (java.lang.reflect.Method method : renderer.getDeclaredMethods()) {
				if (method.getName().contains("realcombat$heldBack")) return;
			}
			throw new AssertionError("RealCombat's hand mixin is not in Vivecraft's RenderHelper");
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Vivecraft's RenderHelper is gone", e);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static float zombieHealth(MinecraftServer server) {
		for (Entity entity : server.overworld().getAllEntities()) {
			if (entity instanceof Zombie zombie && zombie.entityTags().contains("realcombat")) return zombie.getHealth();
		}
		return -1;
	}
}
