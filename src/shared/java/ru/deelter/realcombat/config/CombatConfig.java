package ru.deelter.realcombat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything that can be set, in {@code config/realcombat.json}. The {@link Rules} are the server's: a client takes
 * them from the server it plays on, so its prediction and the server agree. {@link Client} is the player's own.
 */
@Getter
@Setter
public final class CombatConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	@Getter
	private static CombatConfig instance = new CombatConfig();

	private Rules rules = new Rules();
	private Client client = new Client();
	private Compat compat = new Compat();

	/**
	 * Timings are in ticks for a sword (attack speed 1.6); slower weapons stretch them, see {@code Rules.scaled}.
	 * The numbers are Mordhau's timings (windup ~0.45 s, release ~0.25 s, parry ~0.3 s) rounded to ticks.
	 */
	@Getter
	@Setter
	public static final class Rules {
		private boolean enabled = true;
		/**
		 * a player who joins fights by these rules; off, only those the server turns it on for do (an arena, a
		 * minigame: the API or {@code /realcombat enable})
		 */
		private boolean enabledByDefault = true;
		/**
		 * a player may turn the mechanic off for himself ({@code /realcombat off}), to strip logs and chop with an axe;
		 * off for an arena where everyone must fight by these rules
		 */
		private boolean playerToggle = true;
		private int windup = 8;
		private int stabWindup = 7;
		private int riposteWindup = 6;
		private int release = 4;
		private int recovery = 7;
		/**
		 * the recovery after a swing that hit: the next swing may start right away
		 */
		private int comboRecovery = 4;
		/**
		 * the part of the windup in which the direction may still be changed
		 */
		private float morphWindow = 0.5f;
		/**
		 * the part of the windup in which it may still be feinted
		 */
		private float feintWindow = 0.8f;
		private int feintRecovery = 4;
		private int parry = 5;
		private int parryRecovery = 10;
		/**
		 * ticks after a parry in which a swing starts as a riposte
		 */
		private int riposteWindow = 8;
		/**
		 * an attacker who was parried or blocked can do nothing this long
		 */
		private int parriedRecoil = 10;
		/**
		 * ticks after starting his own windup in which a defender chambers a blow of the same direction
		 */
		private int chamberWindow = 4;
		private int kickWindup = 5;
		private int kickRecovery = 8;
		private int kickStun = 14;
		private int flinch = 6;
		private int parryBreakStun = 24;

		private float maxStamina = 100;
		private float staminaRegen = 0.6f;
		/**
		 * ticks after spending stamina before it comes back
		 */
		private int staminaDelay = 14;
		private float parryStamina = 7;
		private float shieldStamina = 24;
		private float feintStamina = 12;
		private float chamberStamina = 8;
		private float whiffStamina = 5;
		private float kickStamina = 25;
		/**
		 * a swing costs this much (a heavy weapon's more); below tiredStamina windups are tiredWindup ticks longer and
		 * feints and chambers are not possible
		 */
		private float attackStamina = 4;
		private float tiredStamina = 25;
		private int tiredWindup = 4;
		/**
		 * a shield knocked away by a heavy blow leaves its holder open this long
		 */
		private int shieldBreakStun = 10;
		/**
		 * a blow that lands gives stamina back, a parried one costs it, as in Mordhau
		 */
		private float hitStamina = 6;
		private float parriedStamina = 5;

		private float damageMultiplier = 1f;
		/**
		 * width of a horizontal slash and height of a vertical one, degrees
		 */
		private float slashArc = 110;
		/**
		 * width across a vertical slash, and of a stab, degrees
		 */
		private float narrowArc = 30;
		private float stabReachBonus = 0.4f;
		/**
		 * a parry or a shield covers blows coming from within this many degrees of the look
		 */
		private float guardArc = 75;
		/**
		 * mobs' melee hits can be parried
		 */
		private boolean parryMobs = true;
		/**
		 * blows are checked against where the attacker saw his target, and a parry that left the defender in time counts
		 */
		private boolean lagCompensation = true;
		/**
		 * at most this far back, ticks (6 is a ping of 300 ms)
		 */
		private int maxRewindTicks = 6;
		/**
		 * a blow at a fighter waits at most this many ticks for his parry
		 */
		private int maxParryGrace = 4;
		/**
		 * the shield comes up after this many seconds, vanilla takes 0.25
		 */
		private float shieldDelay = 0.2f;
		/**
		 * two weapons: a swing straight out of the recovery of the last is the other hand's, its windup this share of a
		 * whole one; every blow does dualDamage of a single weapon's, a swing costs dualStamina times as much, and the
		 * parry is dualParryLoss ticks shorter (one blade holds a guard worse than a blade in both hands)
		 */
		private float dualChainWindup = 0.75f;
		private float dualDamage = 0.7f;
		private float dualStamina = 1.35f;
		private int dualParryLoss = 1;

		public int scaled(int ticks, double attackSpeed) {
			double speed = attackSpeed <= 0 ? 1.6 : attackSpeed;
			return Math.max(1, (int) Math.round(ticks * Math.sqrt(1.6 / speed)));
		}
	}

	@Getter
	@Setter
	public static final class Client {
		/**
		 * degrees the view must turn just before a click to pick a direction
		 */
		private float directionThreshold = 3f;
		/**
		 * turning the view right gives a slash from the left (the blade follows the mouse) instead of from the right
		 */
		private boolean invertHorizontal = false;
		private boolean invertVertical = false;
		/**
		 * the blow when the view did not move
		 */
		private String defaultDir = "RIGHT";
		/**
		 * arrows at the crosshair for blows wound up at you
		 */
		private boolean telegraph = true;
		private float telegraphRange = 6f;
		private boolean staminaBar = true;
		/**
		 * the blow a click would throw now, at the crosshair
		 */
		private boolean directionPreview = true;
		/**
		 * the controls in the chat the first time a weapon is taken up
		 */
		private boolean hints = true;
		/**
		 * the shield arm comes up over this many ticks instead of snapping
		 */
		private float shieldRaiseTicks = 5f;
		private String duelDifficulty = "NORMAL";
	}

	@Getter
	@Setter
	public static final class Compat {
		/**
		 * Vivecraft players swing with their hands: real swings hit, a blade held across a blow parries it
		 */
		private boolean vivecraft = true;
		/**
		 * blocks per tick the sword tip must move to cut
		 */
		private float vrSwingSpeed = 0.28f;
		private float vrBladeLength = 1.1f;
		/**
		 * the blade has a body: it follows the hand on a spring and stops at bodies, blocks and other blades, and the
		 * hand is drawn where the blade stopped. Off, blades pass through everything and only cut what they cross
		 */
		private boolean vrPhysics = true;
		private float vrBladeRadius = 0.06f;
		/**
		 * the share of the way to the controller the blade makes in a tick: lower is heavier
		 */
		private float vrFollow = 0.7f;
		/**
		 * how far the hand may be held back from the controller before it is pulled free, blocks
		 */
		private float vrMaxStretch = 0.6f;
		/**
		 * a fast thrust point first sticks in a body, as in Blade &amp; Sorcery: in and out along the blade only, until
		 * pulled out; this deep at most, blocks
		 */
		private boolean vrStab = true;
		private float vrStabDepth = 0.45f;
		/**
		 * how close the defender's blade must be to the attack to parry it, blocks
		 */
		private float vrParryDistance = 0.35f;
		/**
		 * ChopChop: cuts where blows land, limbs off for heavy ones
		 */
		private boolean chopchop = true;
		/**
		 * as in Mordhau: the killing blow takes the limb it landed on with this chance, and a killing slash at the head
		 * takes the head
		 */
		private float killSeverChance = 0.75f;
		private boolean decapitate = true;
		/**
		 * a slash at a living fighter left with less than lowHealthShare of his health takes the arm or leg it landed
		 * on, with severChance: an arm lost is a sword hand lost (ChopChop), a leg slows him
		 */
		private boolean lowHealthSever = true;
		/**
		 * a side slash (right to left, left to right: a mouse blow, or a strong VR one) through the legs of one
		 * left with less than sideSeverShare of his health takes a leg off with this chance; the neck only when it kills
		 */
		private boolean sideSever = true;
		private float sideSeverShare = 0.4f;
		private float sideSeverChance = 0.4f;
		private float lowHealthShare = 0.4f;
		private float severChance = 0.3f;
		/**
		 * fatalities, only off a killing blow: an overhead one cleaves the body top to bottom (splitFallingChance
		 * struck while falling, off a jump or a ledge; splitChance on the ground), a side one cuts it in two at the
		 * waist (halveChance)
		 */
		private boolean cleave = true;
		/**
		 * a VR blade lands many blows: of the limbs it would take off a living target, only this share come off
		 */
		private float vrSeverScale = 0.3f;
		/**
		 * a kill with a mace smash (off a fall) tears the body apart: the head and every limb fly
		 */
		private boolean maceShatter = true;
		private float splitFallingChance = 0.85f;
		private float splitChance = 0.2f;
		private float halveChance = 0.2f;
	}

	private static final System.Logger LOG = System.getLogger("RealCombat");
	/**
	 * the file the settings live in: {@code config/realcombat.json} of the game, {@code plugins/RealCombat/realcombat.json}
	 * of a Paper server
	 */
	private static Path path;

	public static Path path() {
		return path;
	}

	public static void load(Path file) {
		path = file;
		if (Files.exists(path)) {
			try {
				CombatConfig read = GSON.fromJson(Files.readString(path), CombatConfig.class);
				if (read != null) instance = read;
				if (instance.rules == null) instance.rules = new Rules();
				if (instance.client == null) instance.client = new Client();
				if (instance.compat == null) instance.compat = new Compat();
				migrate(instance);
			} catch (Exception e) {
				LOG.log(System.Logger.Level.WARNING, "Can't read " + path + ", using the defaults", e);
			}
		}
		save();
	}

	/**
	 * the settings as of this version: a file written before keeps the values a player changed, the old defaults
	 * that were tuned since are brought up to date
	 */
	private static final int VERSION = 2;
	private int version;

	private static void migrate(CombatConfig c) {
		if (c.version < 1) {
			Rules r = c.rules;
			if (r.staminaRegen == 0.9f) r.staminaRegen = 0.6f;
			if (r.staminaDelay == 8) r.staminaDelay = 14;
			if (r.tiredWindup == 3) r.tiredWindup = 4;
			if (r.shieldStamina == 16) r.shieldStamina = 24;
			if (r.shieldDelay == 0.05f) r.shieldDelay = 0.2f;
			if (c.client.shieldRaiseTicks == 3f) c.client.shieldRaiseTicks = 5f;
			if (!c.compat.lowHealthSever && c.compat.lowHealthShare == 0.25f) {
				c.compat.lowHealthSever = true;
				c.compat.lowHealthShare = 0.4f;
				c.compat.severChance = 0.3f;
			}
		}
		if (c.version < 2 && c.compat.sideSeverShare == 0.5f) c.compat.sideSeverShare = 0.4f;
		c.version = VERSION;
	}

	public static void save() {
		instance.version = VERSION;
		if (path == null) return;
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(instance));
		} catch (IOException e) {
			LOG.log(System.Logger.Level.WARNING, "Can't write " + path, e);
		}
	}

	public static String rulesJson(Rules rules) {
		return GSON.toJson(rules);
	}

	/**
	 * the rules as one player gets them: {@code enabled} says whether he fights by them right now
	 */
	public static String rulesJson(Rules rules, boolean enabled) {
		com.google.gson.JsonObject json = GSON.toJsonTree(rules).getAsJsonObject();
		json.addProperty("enabled", rules.isEnabled() && enabled);
		return GSON.toJson(json);
	}

	public static Rules rulesFromJson(String json) {
		Rules rules = GSON.fromJson(json, Rules.class);
		return rules == null ? new Rules() : rules;
	}
}
