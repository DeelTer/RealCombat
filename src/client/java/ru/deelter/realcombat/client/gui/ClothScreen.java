package ru.deelter.realcombat.client.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings screen, on Cloth Config. Only loaded once Cloth Config is known to be installed. The rules are the
 * ones of a world opened here; a server sends its own.
 */
public final class ClothScreen {
	private ClothScreen() {
	}

	public static Screen create(Screen parent) {
		CombatConfig config = CombatConfig.getInstance();
		CombatConfig.Client client = config.getClient();
		CombatConfig.Rules rules = config.getRules();
		CombatConfig.Compat compat = config.getCompat();
		CombatConfig.Client clientDefaults = new CombatConfig.Client();
		CombatConfig.Rules rulesDefaults = new CombatConfig.Rules();
		CombatConfig.Compat compatDefaults = new CombatConfig.Compat();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("realcombat.config.title"))
				.setSavingRunnable(CombatConfig::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory controls = builder.getOrCreateCategory(Component.translatable("realcombat.config.client"));
		controls.addEntry(entries.startFloatField(text("directionThreshold"), client.getDirectionThreshold())
				.setDefaultValue(clientDefaults.getDirectionThreshold()).setMin(0).setMax(45).setSaveConsumer(client::setDirectionThreshold).build());
		controls.addEntry(entries.startBooleanToggle(text("invertHorizontal"), client.isInvertHorizontal())
				.setDefaultValue(clientDefaults.isInvertHorizontal()).setSaveConsumer(client::setInvertHorizontal).build());
		controls.addEntry(entries.startBooleanToggle(text("invertVertical"), client.isInvertVertical())
				.setDefaultValue(clientDefaults.isInvertVertical()).setSaveConsumer(client::setInvertVertical).build());
		controls.addEntry(entries.startStringDropdownMenu(text("defaultDir"), client.getDefaultDir())
				.setSelections(java.util.List.of("RIGHT", "LEFT", "OVERHEAD", "UNDER", "STAB"))
				.setDefaultValue(clientDefaults.getDefaultDir()).setSaveConsumer(client::setDefaultDir).build());
		controls.addEntry(entries.startBooleanToggle(text("telegraph"), client.isTelegraph())
				.setDefaultValue(clientDefaults.isTelegraph()).setSaveConsumer(client::setTelegraph).build());
		controls.addEntry(entries.startFloatField(text("telegraphRange"), client.getTelegraphRange())
				.setDefaultValue(clientDefaults.getTelegraphRange()).setMin(2).setMax(32).setSaveConsumer(client::setTelegraphRange).build());
		controls.addEntry(entries.startBooleanToggle(text("directionPreview"), client.isDirectionPreview())
				.setDefaultValue(clientDefaults.isDirectionPreview()).setSaveConsumer(client::setDirectionPreview).build());
		controls.addEntry(entries.startBooleanToggle(text("hints"), client.isHints())
				.setDefaultValue(clientDefaults.isHints()).setSaveConsumer(client::setHints).build());
		controls.addEntry(entries.startBooleanToggle(text("staminaBar"), client.isStaminaBar())
				.setDefaultValue(clientDefaults.isStaminaBar()).setSaveConsumer(client::setStaminaBar).build());
		controls.addEntry(entries.startFloatField(text("shieldRaiseTicks"), client.getShieldRaiseTicks())
				.setDefaultValue(clientDefaults.getShieldRaiseTicks()).setMin(0).setMax(10).setSaveConsumer(client::setShieldRaiseTicks).build());
		controls.addEntry(entries.startStringDropdownMenu(text("duelDifficulty"), client.getDuelDifficulty())
				.setSelections(java.util.List.of("EASY", "NORMAL", "HARD"))
				.setDefaultValue(clientDefaults.getDuelDifficulty()).setSaveConsumer(client::setDuelDifficulty).build());

		ConfigCategory timing = builder.getOrCreateCategory(Component.translatable("realcombat.config.rules"));
		timing.addEntry(entries.startBooleanToggle(text("enabled"), rules.isEnabled())
				.setDefaultValue(rulesDefaults.isEnabled()).setSaveConsumer(rules::setEnabled).build());
		ticks(timing, entries, "windup", rules::getWindup, rulesDefaults.getWindup(), rules::setWindup);
		ticks(timing, entries, "stabWindup", rules::getStabWindup, rulesDefaults.getStabWindup(), rules::setStabWindup);
		ticks(timing, entries, "riposteWindup", rules::getRiposteWindup, rulesDefaults.getRiposteWindup(), rules::setRiposteWindup);
		ticks(timing, entries, "release", rules::getRelease, rulesDefaults.getRelease(), rules::setRelease);
		ticks(timing, entries, "recovery", rules::getRecovery, rulesDefaults.getRecovery(), rules::setRecovery);
		ticks(timing, entries, "comboRecovery", rules::getComboRecovery, rulesDefaults.getComboRecovery(), rules::setComboRecovery);
		ticks(timing, entries, "parry", rules::getParry, rulesDefaults.getParry(), rules::setParry);
		ticks(timing, entries, "parryRecovery", rules::getParryRecovery, rulesDefaults.getParryRecovery(), rules::setParryRecovery);
		ticks(timing, entries, "riposteWindow", rules::getRiposteWindow, rulesDefaults.getRiposteWindow(), rules::setRiposteWindow);
		ticks(timing, entries, "chamberWindow", rules::getChamberWindow, rulesDefaults.getChamberWindow(), rules::setChamberWindow);
		ticks(timing, entries, "parriedRecoil", rules::getParriedRecoil, rulesDefaults.getParriedRecoil(), rules::setParriedRecoil);
		ticks(timing, entries, "flinch", rules::getFlinch, rulesDefaults.getFlinch(), rules::setFlinch);
		timing.addEntry(entries.startFloatField(text("maxStamina"), rules.getMaxStamina())
				.setDefaultValue(rulesDefaults.getMaxStamina()).setMin(1).setSaveConsumer(rules::setMaxStamina).build());
		timing.addEntry(entries.startFloatField(text("damageMultiplier"), rules.getDamageMultiplier())
				.setDefaultValue(rulesDefaults.getDamageMultiplier()).setMin(0).setSaveConsumer(rules::setDamageMultiplier).build());
		timing.addEntry(entries.startFloatField(text("guardArc"), rules.getGuardArc())
				.setDefaultValue(rulesDefaults.getGuardArc()).setMin(10).setMax(180).setSaveConsumer(rules::setGuardArc).build());
		timing.addEntry(entries.startFloatField(text("shieldDelay"), rules.getShieldDelay())
				.setDefaultValue(rulesDefaults.getShieldDelay()).setMin(0).setMax(0.25f).setSaveConsumer(rules::setShieldDelay).build());
		timing.addEntry(entries.startBooleanToggle(text("lagCompensation"), rules.isLagCompensation())
				.setDefaultValue(rulesDefaults.isLagCompensation()).setSaveConsumer(rules::setLagCompensation).build());
		timing.addEntry(entries.startBooleanToggle(text("parryMobs"), rules.isParryMobs())
				.setDefaultValue(rulesDefaults.isParryMobs()).setSaveConsumer(rules::setParryMobs).build());

		ConfigCategory mods = builder.getOrCreateCategory(Component.translatable("realcombat.config.compat"));
		mods.addEntry(entries.startBooleanToggle(text("vivecraft"), compat.isVivecraft())
				.setDefaultValue(compatDefaults.isVivecraft()).setSaveConsumer(compat::setVivecraft).build());
		mods.addEntry(entries.startBooleanToggle(text("vrPhysics"), compat.isVrPhysics())
				.setDefaultValue(compatDefaults.isVrPhysics()).setSaveConsumer(compat::setVrPhysics).build());
		mods.addEntry(entries.startFloatField(text("vrFollow"), compat.getVrFollow())
				.setDefaultValue(compatDefaults.getVrFollow()).setMin(0.1f).setMax(1).setSaveConsumer(compat::setVrFollow).build());
		mods.addEntry(entries.startFloatField(text("vrSwingSpeed"), compat.getVrSwingSpeed())
				.setDefaultValue(compatDefaults.getVrSwingSpeed()).setMin(0.05f).setMax(2).setSaveConsumer(compat::setVrSwingSpeed).build());
		mods.addEntry(entries.startFloatField(text("vrParryDistance"), compat.getVrParryDistance())
				.setDefaultValue(compatDefaults.getVrParryDistance()).setMin(0.05f).setMax(1.5f).setSaveConsumer(compat::setVrParryDistance).build());
		mods.addEntry(entries.startBooleanToggle(text("chopchop"), compat.isChopchop())
				.setDefaultValue(compatDefaults.isChopchop()).setSaveConsumer(compat::setChopchop).build());
		mods.addEntry(entries.startIntSlider(text("killSeverChance"), Math.round(compat.getKillSeverChance() * 100), 0, 100)
				.setDefaultValue(Math.round(compatDefaults.getKillSeverChance() * 100)).setSaveConsumer(v -> compat.setKillSeverChance(v / 100f)).build());
		mods.addEntry(entries.startBooleanToggle(text("lowHealthSever"), compat.isLowHealthSever())
				.setDefaultValue(compatDefaults.isLowHealthSever()).setSaveConsumer(compat::setLowHealthSever).build());
		mods.addEntry(entries.startIntSlider(text("severChance"), Math.round(compat.getSeverChance() * 100), 0, 100)
				.setDefaultValue(Math.round(compatDefaults.getSeverChance() * 100)).setSaveConsumer(v -> compat.setSeverChance(v / 100f)).build());
		mods.addEntry(entries.startBooleanToggle(text("decapitate"), compat.isDecapitate())
				.setDefaultValue(compatDefaults.isDecapitate()).setSaveConsumer(compat::setDecapitate).build());
		return builder.build();
	}

	private static void ticks(ConfigCategory category, ConfigEntryBuilder entries, String key, Supplier<Integer> value, int defaultValue,
							  Consumer<Integer> save) {
		category.addEntry(entries.startIntSlider(text(key), value.get(), 1, 40).setDefaultValue(defaultValue).setSaveConsumer(save).build());
	}

	private static Component text(String key) {
		return Component.translatable("realcombat.config." + key);
	}
}
