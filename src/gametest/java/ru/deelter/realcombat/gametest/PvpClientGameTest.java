package ru.deelter.realcombat.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.deelter.realcombat.api.RealCombatApi;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.net.Protocol;
import ru.deelter.realcombat.server.CombatServer;

import java.util.Properties;
import java.util.UUID;

/**
 * Player against player on a dedicated server: a second fighter (a server-side player, driven by the API) swings at
 * the client's player. Parried over the network, the blow does nothing and the attacker recoils; not parried, it
 * hurts. This is the path of the lag compensation: the blow waits for the defender's parry before it lands.
 */
public class PvpClientGameTest implements FabricClientGameTest {
	private static ServerPlayer duelist;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getProperty("realcombat.paperPort") != null) return;
		Properties properties = new Properties();
		properties.setProperty("online-mode", "false");
		properties.setProperty("difficulty", "easy");
		properties.setProperty("pvp", "true");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			server.runCommand("time set 6000");
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a iron_sword");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitFor(client -> ClientCombat.isServerSpeaks() && client.player != null);
			context.waitTicks(10);
			server.computeOnServer(s -> {
				ServerPlayer player = player(s);
				duelist = FakePlayer.get(player.level(), new GameProfile(UUID.randomUUID(), "Duelist"));
				duelist.snapTo(player.getX(), player.getY(), player.getZ() + 2.2, 180, 0);
				duelist.setYHeadRot(180);
				duelist.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				RealCombatApi.addFighter(duelist);
				return null;
			});
			context.waitTicks(5);

			// parried: the client sees the blow coming (here: the server says it is about to land) and parries
			float health = context.computeOnClient(client -> client.player.getHealth());
			server.computeOnServer(s -> {
				RealCombatApi.perform(duelist, Protocol.Action.ATTACK, Dir.OVERHEAD);
				return null;
			});
			server.waitFor(s -> {
				var f = CombatServer.fighter(duelist);
				return f.getPhase() == Phase.WINDUP && f.getTicks() >= f.getLength() - 3;
			}, 40);
			context.runOnClient(client -> ClientCombat.parry());
			server.waitFor(s -> CombatServer.fighter(duelist).getPhase() == Phase.RECOVERY, 40);
			context.waitTicks(10);
			float parried = context.computeOnClient(client -> client.player.getHealth());
			if (parried < health) throw new AssertionError("a parried blow hurt: " + health + " -> " + parried);
			if (CombatServer.fighter(duelist).getStruck().isEmpty()) throw new AssertionError("the duelist's blow never reached the player");

			// not parried: it hurts
			server.waitFor(s -> CombatServer.fighter(duelist).getPhase() == Phase.IDLE, 60);
			context.waitTicks(10);
			server.computeOnServer(s -> {
				RealCombatApi.perform(duelist, Protocol.Action.ATTACK, Dir.RIGHT);
				return null;
			});
			context.waitFor(client -> client.player.getHealth() < parried, 60);

			server.computeOnServer(s -> {
				RealCombatApi.removeFighter(duelist);
				return null;
			});
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
