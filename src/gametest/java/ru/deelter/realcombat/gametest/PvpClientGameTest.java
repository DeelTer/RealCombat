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

			// a raised shield held high: a blow from below goes under it, one from above does not get through
			server.waitFor(s -> CombatServer.fighter(duelist).getPhase() == Phase.IDLE, 60);
			server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				p.setHealth(p.getMaxHealth());
				duelist.snapTo(p.getX(), p.getY(), p.getZ() + 2.2, 180, 0);
				duelist.setYHeadRot(180);
				p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), java.util.Set.of(), 0, 0, true);
				return null;
			});
			context.waitTicks(10);
			context.runOnClient(client -> {
				client.player.setXRot(0);
				// the use button held, as a player raising his shield
				client.options.keyUse.setDown(true);
			});
			server.waitFor(s -> player(s).isBlocking(), 60);
			float shielded = context.computeOnClient(client -> client.player.getHealth());
			server.computeOnServer(s -> {
				RealCombatApi.perform(duelist, Protocol.Action.ATTACK, Dir.OVERHEAD);
				return null;
			});
			context.waitTicks(40);
			float afterHigh = context.computeOnClient(client -> client.player.getHealth());
			if (afterHigh < shielded) throw new AssertionError("a blow from above went through a high shield: " + shielded + " -> " + afterHigh);
			server.waitFor(s -> CombatServer.fighter(duelist).getPhase() == Phase.IDLE, 60);
			server.computeOnServer(s -> {
				RealCombatApi.perform(duelist, Protocol.Action.ATTACK, Dir.UNDER);
				return null;
			});
			context.waitFor(client -> client.player.getHealth() < afterHigh, 80);
			// the attack button behind the raised shield pushes with it (the fake duelist can't be hurt, so only the
			// push itself is checked: the server takes it as one)
			server.waitFor(s -> CombatServer.fighter(player(s)).getPhase() == Phase.IDLE, 60);
			context.runOnClient(client -> client.options.keyAttack.setDown(true));
			context.waitTicks(2);
			context.runOnClient(client -> client.options.keyAttack.setDown(false));
			context.waitTicks(3);
			boolean pushed = server.computeOnServer(s -> CombatServer.fighter(player(s)).getPhase() == Phase.KICK && CombatServer.fighter(player(s)).isBash());
			if (!pushed) throw new AssertionError("the attack button behind a raised shield did not push with it");
			context.runOnClient(client -> client.options.keyUse.setDown(false));
			server.computeOnServer(s -> {
				player(s).setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				return null;
			});

			// with ChopChop: the sword hand cut off, the duelist fights on with the sword in the other one
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("chopchop")) {
				server.waitFor(s -> CombatServer.fighter(duelist).getPhase() == Phase.IDLE, 60);
				float before = context.computeOnClient(client -> client.player.getHealth());
				boolean offHanded = server.computeOnServer(s -> {
					try {
						Class.forName("ru.deelter.chopchop.api.ChopChopApi")
								.getMethod("sever", net.minecraft.world.entity.LivingEntity.class, String.class, double.class, net.minecraft.world.entity.Entity.class, boolean.class)
								.invoke(null, duelist, "right_arm", 0.0, null, false);
					} catch (ReflectiveOperationException e) {
						throw new RuntimeException(e);
					}
					duelist.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					duelist.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
					duelist.setHealth(duelist.getMaxHealth());
					// the last blow shoved the player back: face to face again
					ServerPlayer p = player(s);
					p.teleportTo(p.level(), duelist.getX(), duelist.getY(), duelist.getZ() - 2.2, java.util.Set.of(), 0, 0, true);
					RealCombatApi.perform(duelist, Protocol.Action.ATTACK, Dir.LEFT);
					return ru.deelter.realcombat.combat.Weapons.offHanded(duelist);
				});
				if (!offHanded) throw new AssertionError("the duelist without a sword hand does not fight with the other one");
				context.waitFor(client -> client.player.getHealth() < before, 60);
			}

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
