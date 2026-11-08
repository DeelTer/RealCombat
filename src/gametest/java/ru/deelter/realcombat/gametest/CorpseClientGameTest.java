package ru.deelter.realcombat.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ru.deelter.realcombat.client.ClientCombat;
import ru.deelter.realcombat.combat.Dir;

/**
 * With ChopChop: a swing goes into a dead body lying in its arc and cuts it, without aiming the crosshair at it.
 */
public class CorpseClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getProperty("realcombat.paperPort") != null || !FabricLoader.getInstance().isModLoaded("chopchop")) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamemode survival @a");
			context.waitFor(client -> ClientCombat.isServerSpeaks() && client.player != null);
			int id = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
				Vec3 at = player.position();
				player.teleportTo(player.level(), at.x, at.y, at.z, java.util.Set.of(), 0, 0, true);
				Zombie z = EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
				z.setNoAi(true);
				z.setPos(at.x, at.y, at.z + 1.6);
				player.level().addFreshEntity(z);
				return z.getId();
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				var z = server.getPlayerList().getPlayers().getFirst().level().getEntity(id);
				((Zombie) z).hurtServer((net.minecraft.server.level.ServerLevel) z.level(), z.damageSources().generic(), 1000);
			});
			// the body falls and lies still
			context.waitTicks(60);
			int before = context.computeOnClient(client -> cuts(id));
			// looking straight ahead, over the body: an overhead swing comes down through it
			// looking straight ahead, over the body: an overhead swing comes down through it (no cleaving yet)
			var compat = ru.deelter.realcombat.config.CombatConfig.getInstance().getCompat();
			float chance = compat.getCorpseCleaveChance();
			compat.setCorpseCleaveChance(0);
			// ChopChop wounds a body with a chance: a few blows, as a player would
			int after = before;
			for (int i = 0; i < 4 && after <= before; i++) {
				context.runOnClient(client -> ClientCombat.attack(Dir.OVERHEAD));
				context.waitTicks(30);
				after = context.computeOnClient(client -> cuts(id));
			}
			System.out.println("[corpse] cuts " + before + " -> " + after);
			if (after <= before) throw new AssertionError("the swing went over the body and did not cut it: " + before + " -> " + after);

			// a cleaving blow cuts it in two (here every one does)
			compat.setCorpseCleaveChance(1);
			try {
				context.waitTicks(20);
				context.runOnClient(client -> ClientCombat.attack(Dir.RIGHT));
				context.waitTicks(30);
			} finally {
				compat.setCorpseCleaveChance(chance);
			}
			int cleft = context.computeOnClient(client -> cuts(id));
			// a mace smash that kills tears the body apart: every piece that flies has its skin
			int maced = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Vec3 at = player.position();
				Zombie z = EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
				z.setNoAi(true);
				z.setPos(at.x + 2, at.y, at.z + 1.6);
				player.level().addFreshEntity(z);
				return z.getId();
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var z = (Zombie) player.level().getEntity(maced);
				var source = new net.minecraft.world.damagesource.DamageSource(player.level().registryAccess()
						.lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(net.minecraft.world.damagesource.DamageTypes.MACE_SMASH), player);
				z.hurtServer(player.level(), source, 1000);
			});
			context.waitTicks(40);
			world.getServer().runCommand("execute as @a at @s run tp @s ~2 ~ ~-0.6 0 55");
			context.waitTicks(5);
			context.takeScreenshot("mace-shatter");
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
					for (Object c : (java.util.List<?>) f.get(null)) {
						Object p = part.get(c);
						int gx = (int) p.getClass().getMethod("gx").invoke(p), gy = (int) p.getClass().getMethod("gy").invoke(p), gz = (int) p.getClass().getMethod("gz").invoke(p);
						Object m = mask.get(c);
						var gone = Class.forName("ru.deelter.chopchop.client.render.VoxelMask").getMethod("gone", int.class, int.class, int.class);
						int kept = 0;
						for (int x = 0; x < gx; x++) for (int y = 0; y < gy; y++) for (int zz = 0; zz < gz; zz++) if (!(boolean) gone.invoke(m, x, y, zz)) kept++;
						System.out.println("[mace] piece " + p.getClass().getMethod("getName").invoke(p) + " cut=" + cut.get(c) + " kept=" + kept);
					}
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			});
			System.out.println("[corpse] cleft " + after + " -> " + cleft);
			if (cleft < after + 1000) throw new AssertionError("a cleaving blow did not cut the body in two: " + after + " -> " + cleft);
		}
	}

	/**
	 * carved voxels and cut parts of the body on this client
	 */
	private static int cuts(int id) {
		try {
			Object state = Class.forName("ru.deelter.chopchop.client.ClientWounds").getMethod("get", int.class).invoke(null, id);
			if (state == null) return 0;
			int count = (int) state.getClass().getMethod("partCount").invoke(state), sum = 0;
			for (int i = 0; i < count; i++) {
				Object part = state.getClass().getMethod("part", int.class).invoke(state, i);
				sum += (int) part.getClass().getMethod("carvedCount").invoke(part);
				if ((boolean) part.getClass().getMethod("severed").invoke(part)) sum += 1000;
			}
			return sum;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
