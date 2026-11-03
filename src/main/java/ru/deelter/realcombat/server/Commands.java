package ru.deelter.realcombat.server;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.Collection;

/**
 * {@code /realcombat enable|disable <players>} and {@code /realcombat reload}, for operators.
 */
final class Commands {
	private Commands() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				net.minecraft.commands.Commands.literal("realcombat")
						.requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
						.then(net.minecraft.commands.Commands.literal("enable")
								.then(net.minecraft.commands.Commands.argument("players", EntityArgument.players()).executes(c -> set(c, true))))
						.then(net.minecraft.commands.Commands.literal("disable")
								.then(net.minecraft.commands.Commands.argument("players", EntityArgument.players()).executes(c -> set(c, false))))
						.then(net.minecraft.commands.Commands.literal("reload").executes(c -> {
							CombatConfig.load(CombatConfig.path());
							CombatServer.reapply(c.getSource().getServer());
							c.getSource().sendSuccess(() -> Component.literal("RealCombat reloaded"), true);
							return 1;
						}))));
	}

	private static int set(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
		for (ServerPlayer player : players) CombatServer.setEnabled(player, enabled);
		context.getSource().sendSuccess(() -> Component.literal("RealCombat " + (enabled ? "on" : "off") + " for " + players.size() + " player(s)"), true);
		return players.size();
	}
}
