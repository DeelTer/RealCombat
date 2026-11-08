package ru.deelter.realcombat.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.deelter.realcombat.combat.BotBrain;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.net.ActionPayload;
import ru.deelter.realcombat.net.Protocol;
import ru.deelter.realcombat.net.RulesPayload;
import ru.deelter.realcombat.net.StatePayload;

import java.util.Locale;

/**
 * Client entry point: input, prediction, drawing, the HUD and the sparring command.
 */
public final class RealCombatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Keys.init();
		ru.deelter.realcombat.combat.Weapons.clientHandless = Corpses::handless;
		ru.deelter.realcombat.combat.Sweep.clientView = VrClient::view;
		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> ClientCombat.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (payload, context) -> ClientCombat.serverRules(CombatConfig.rulesFromJson(payload.json())));
		// before anything of the server arrives: a Paper server may answer the hello before the join
		ClientPlayConnectionEvents.INIT.register((handler, client) -> {
			ClientCombat.reset();
			helloSent = false;
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// a server without the mod does not listen: its players fight the vanilla way
			hello();
		});
		// Paper names the channels it listens on only after the join: the hello waits for that
		ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
			if (channels.contains(ActionPayload.TYPE.id())) client.execute(RealCombatClient::hello);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DuelBot.stop();
			ClientCombat.reset();
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> ClientCombat.forget(entity.getId()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientCombat.tick(client);
			DuelBot.tick(client);
			MotionRecorder.tick();
			VrTelegraph.tick(client);
		});
		LevelRenderEvents.END_MAIN.register(context -> MotionRecorder.frame());
		HudElementRegistry.addLast(Identifier.parse("realcombat:hud"), Hud::extract);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			// every choice is a word of its own: no argument shadows "stop" or "off"
			var duel = ClientCommands.literal("duel")
					.executes(c -> duel(CombatConfig.getInstance().getClient().getDuelDifficulty()))
					.then(ClientCommands.literal("stop").executes(c -> {
						DuelBot.stop();
						return 1;
					}));
			for (BotBrain.Difficulty d : BotBrain.Difficulty.values()) {
				duel.then(ClientCommands.literal(d.name().toLowerCase(Locale.ROOT)).executes(c -> duel(d.name())));
			}
			// holds the sparring partner (and the own first person arm) in one moment of a swing, to look at it
			var pose = ClientCommands.literal("pose")
					.then(ClientCommands.literal("off").executes(c -> {
						DuelBot.freeze(null);
						ClientCombat.setOwnFrozen(null);
						return 1;
					}));
			for (Phase phase : Phase.values()) {
				var byPhase = ClientCommands.literal(phase.name().toLowerCase(Locale.ROOT));
				for (Dir dir : Dir.values()) {
					byPhase.then(ClientCommands.literal(dir.name().toLowerCase(Locale.ROOT))
							.then(ClientCommands.argument("progress", FloatArgumentType.floatArg(0, 1))
									.executes(c -> pose(phase.name(), dir.name(), FloatArgumentType.getFloat(c, "progress")))));
				}
				pose.then(byPhase);
			}
			// a script of VR blows recorded to a file, for shaping the animations on real motion
			var record = ClientCommands.literal("record")
					.executes(c -> {
						MotionRecorder.start(3);
						return 1;
					})
					.then(ClientCommands.literal("stop").executes(c -> {
						MotionRecorder.stop();
						return 1;
					}))
					.then(ClientCommands.argument("reps", IntegerArgumentType.integer(1, 10)).executes(c -> {
						MotionRecorder.start(IntegerArgumentType.getInteger(c, "reps"));
						return 1;
					}));
			dispatcher.register(ClientCommands.literal("realcombat").then(duel).then(pose).then(record)
					.then(ClientCommands.literal("on").executes(c -> toggle(true)))
					.then(ClientCommands.literal("off").executes(c -> toggle(false))));
		});
	}

	private static boolean helloSent;
	/**
	 * the player turned the mechanic off for himself this game: said again to every server he joins
	 */
	private static boolean switchedOff;

	/**
	 * {@code /realcombat on|off}: the server turns the mechanic on or off for this player (if it lets players do that)
	 */
	private static int toggle(boolean on) {
		switchedOff = !on;
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (!ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
			if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("realcombat.toggle.noserver"));
			return 0;
		}
		ClientPlayNetworking.send(new ActionPayload(Protocol.Action.TOGGLE, on ? 1 : 0));
		if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(on ? "realcombat.toggle.on" : "realcombat.toggle.off"));
		return 1;
	}

	private static void hello() {
		// the channels may be named before the game is in the play state (Vivecraft does that): then the join says it
		if (helloSent || !ClientPlayNetworking.canSend(ActionPayload.TYPE)) return;
		try {
			ClientPlayNetworking.send(new ActionPayload(Protocol.Action.HELLO, 0));
			if (switchedOff) ClientPlayNetworking.send(new ActionPayload(Protocol.Action.TOGGLE, 0));
			helloSent = true;
		} catch (IllegalStateException e) {
			// not in the game yet: the join comes next
		}
	}

	private static int pose(String phase, String dir, float progress) {
		try {
			CombatPose pose = new CombatPose(Phase.valueOf(phase.toUpperCase(Locale.ROOT)), Dir.valueOf(dir.toUpperCase(Locale.ROOT)), progress, false);
			DuelBot.freeze(pose);
			ClientCombat.setOwnFrozen(pose);
			return 1;
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

	private static int duel(String difficulty) {
		try {
			DuelBot.start(BotBrain.Difficulty.valueOf(difficulty.toUpperCase(Locale.ROOT)));
			return 1;
		} catch (IllegalArgumentException e) {
			net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(Component.translatable("realcombat.duel.unknown", difficulty));
			return 0;
		}
	}
}
