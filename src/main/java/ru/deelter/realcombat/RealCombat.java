package ru.deelter.realcombat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.deelter.realcombat.compat.ChopChopCompat;
import ru.deelter.realcombat.config.CombatConfig;
import ru.deelter.realcombat.net.ActionPayload;
import ru.deelter.realcombat.net.RulesPayload;
import ru.deelter.realcombat.net.StatePayload;
import ru.deelter.realcombat.server.CombatServer;

/**
 * Common entry point: the channels, the settings and the server side, which runs on a dedicated server and on the
 * integrated server of a singleplayer world alike.
 */
public final class RealCombat implements ModInitializer {
	public static final String ID = "realcombat";
	public static final Logger LOG = LoggerFactory.getLogger("RealCombat");

	@Override
	public void onInitialize() {
		CombatConfig.load(FabricLoader.getInstance().getConfigDir().resolve(ID + ".json"));
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.CODEC);
		CombatServer.init();
		if (FabricLoader.getInstance().isModLoaded("chopchop")) ChopChopCompat.init();
	}
}
