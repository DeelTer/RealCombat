package ru.deelter.realcombat.combat;

import org.junit.jupiter.api.Test;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fights run tick by tick on the rules alone, two fighters a sword's length apart and looking at each other, to check
 * that the timings leave a human a fair chance and that the bot's difficulties are what they say.
 * <p>
 * The "human" sees a windup after a reaction time and times his parry with a normal error of {@code sigma} ticks:
 * 1.5 is a practised player, 3 a new one.
 */
class DuelSimulationTest {
	private static final double SPEED = 1.6;
	private static final float DAMAGE = 6;
	private static final int TICKS = 20 * 60 * 20;

	private final CombatConfig.Rules rules = new CombatConfig.Rules();

	/**
	 * A side of a fight: decides each tick what its fighter does.
	 */
	private interface Mind {
		void tick(Fighter self, Fighter foe);
	}

	private static final class Tally {
		final Map<Outcome, Integer> blows = new EnumMap<>(Outcome.class);
		int kills;

		void add(Outcome outcome) {
			blows.merge(outcome, 1, Integer::sum);
		}

		int count(Outcome outcome) {
			return blows.getOrDefault(outcome, 0);
		}

		int total() {
			return blows.values().stream().mapToInt(Integer::intValue).sum();
		}

		/**
		 * blows that met a parry or a chamber, of all that reached
		 */
		double parried() {
			int total = total() - count(Outcome.KICKED);
			return total == 0 ? 0 : (count(Outcome.PARRIED) + count(Outcome.CHAMBERED) + count(Outcome.PARRY_BROKEN)) / (double) total;
		}
	}

	/**
	 * Runs a fight; returns what the blows of each side came to: [a's blows, b's blows].
	 */
	private Tally[] fight(Mind a, Mind b, long seed) {
		Fighter fa = new Fighter(rules.getMaxStamina());
		Fighter fb = new Fighter(rules.getMaxStamina());
		Tally[] tallies = {new Tally(), new Tally()};
		float[] health = {20, 20};
		for (int t = 0; t < TICKS; t++) {
			Rules.tick(fa, rules, SPEED);
			Rules.tick(fb, rules, SPEED);
			a.tick(fa, fb);
			b.tick(fb, fa);
			strike(fa, fb, tallies[0], health, 1);
			strike(fb, fa, tallies[1], health, 0);
			if (health[0] <= 0 || health[1] <= 0) {
				tallies[health[1] <= 0 ? 0 : 1].kills++;
				health[0] = health[1] = 20;
			}
		}
		return tallies;
	}

	private void strike(Fighter attacker, Fighter defender, Tally tally, float[] health, int target) {
		if (attacker.getPhase() == Phase.RELEASE && attacker.getTicks() > 0 && attacker.getStruck().isEmpty()
				&& Sweep.crosses(attacker.getDir(), attacker.previousProgress(), attacker.progress(), 0, 0, rules)) {
			attacker.getStruck().add(1);
			float damage = DAMAGE * attacker.getWeapon().damage(attacker.getDir());
			Outcome outcome = Rules.blow(attacker, defender, true, false, false, damage, rules);
			tally.add(outcome);
			if (outcome == Outcome.HIT) health[target] -= damage;
		}
		if (Rules.kickLands(attacker, rules)) tally.add(Rules.kick(defender, false, rules));
	}

	/**
	 * the release tick on which a blow aimed straight at the target lands
	 */
	private int landingTick(Dir dir) {
		int release = rules.scaled(rules.getRelease(), SPEED);
		for (int tick = 1; tick <= release; tick++) {
			if (Sweep.crosses(dir, (tick - 1) / (float) release, Math.min(1f, tick / (float) release), 0, 0, rules)) return tick;
		}
		return release;
	}

	/**
	 * Swings every {@code every} ticks in a random direction, feinting some of them.
	 */
	private Mind swinger(int every, float feint, Random random) {
		int[] wait = {every};
		boolean[] feinting = {false};
		return (self, foe) -> {
			if (self.getPhase() == Phase.WINDUP && feinting[0] && self.progress() >= 0.6f) {
				feinting[0] = false;
				if (Rules.feint(self, rules)) Rules.attack(self, Dir.of(random.nextInt(5)), rules, SPEED);
				return;
			}
			if (--wait[0] > 0 || self.getPhase() != Phase.IDLE) return;
			wait[0] = every;
			if (Rules.attack(self, Dir.of(random.nextInt(5)), rules, SPEED)) feinting[0] = random.nextFloat() < feint;
		};
	}

	/**
	 * A human: parries what he sees with a reaction and an error, attacks now and then, ripostes after a parry.
	 */
	private Mind human(double sigma, boolean attacks, Random random) {
		long[] clock = {0};
		long[] parryAt = {-1};
		boolean[] decided = {false};
		int[] wait = {25};
		return (self, foe) -> {
			clock[0]++;
			boolean threat = foe.getPhase() == Phase.WINDUP || foe.getPhase() == Phase.RELEASE;
			if (!threat) {
				decided[0] = false;
			} else if (!decided[0] && (foe.getPhase() == Phase.RELEASE || foe.getTicks() >= 4)) {
				decided[0] = true;
				int toRelease = foe.getPhase() == Phase.WINDUP ? foe.getLength() - foe.getTicks() : 0;
				int toHit = toRelease + landingTick(foe.getDir());
				// aims to have the blow fall in the middle of the parry
				long at = clock[0] + Math.round(toHit - rules.getParry() / 2.0 + random.nextGaussian() * sigma);
				parryAt[0] = Math.max(clock[0], at);
			}
			if (parryAt[0] >= 0 && clock[0] >= parryAt[0]) {
				parryAt[0] = -1;
				Rules.parry(self, rules);
			}
			if (!attacks) return;
			if (self.getRiposteWindow() > 0) {
				Rules.attack(self, Dir.of(random.nextInt(5)), rules, SPEED);
				return;
			}
			if (--wait[0] > 0 || self.getPhase() != Phase.IDLE || threat || self.getStamina() < rules.getTiredStamina() + 15) return;
			wait[0] = 15 + random.nextInt(25);
			Rules.attack(self, Dir.of(random.nextInt(5)), rules, SPEED);
		};
	}

	private Mind bot(BotBrain.Difficulty difficulty, Random random) {
		BotBrain brain = new BotBrain(difficulty, random);
		return (self, foe) -> brain.tick(self, foe, 2.4, true, rules, SPEED);
	}

	@Test
	void report() {
		System.out.printf("landing ticks: %s%n", java.util.Arrays.stream(Dir.values()).map(d -> d + "=" + landingTick(d)).toList());
		for (double sigma : new double[]{1, 1.5, 2, 3}) {
			Tally[] t = fight(swinger(30, 0, new Random(1)), human(sigma, false, new Random(2)), 3);
			System.out.printf("human sigma %.1f parries %.0f%% of plain blows%n", sigma, t[0].parried() * 100);
		}
		for (BotBrain.Difficulty d : BotBrain.Difficulty.values()) {
			Tally[] plain = fight(swinger(30, 0, new Random(1)), bot(d, new Random(2)), 3);
			Tally[] feints = fight(swinger(30, 1, new Random(1)), bot(d, new Random(2)), 3);
			Tally[] good = fight(human(1.5, true, new Random(4)), bot(d, new Random(5)), 3);
			Tally[] newbie = fight(human(3, true, new Random(4)), bot(d, new Random(5)), 3);
			System.out.printf("%-6s parries plain %.0f%%, after feints %.0f%%; practised human wins %d:%d, new human %d:%d; bot blows %s%n",
					d, plain[0].parried() * 100, feints[0].parried() * 100, good[0].kills, good[1].kills, newbie[0].kills, newbie[1].kills, good[1].blows);
		}
	}

	@Test
	void practisedPlayerCanParry() {
		Tally[] t = fight(swinger(30, 0, new Random(1)), human(1.5, false, new Random(2)), 3);
		assertTrue(t[0].parried() > 0.65, "a practised player parries most blows: " + t[0].parried());
	}

	@Test
	void feintsWork() {
		Tally[] t = fight(swinger(60, 1, new Random(1)), human(1.5, false, new Random(2)), 3);
		assertTrue(t[0].parried() < 0.5, "a feint into a new blow beats a parry made early: " + t[0].parried());
	}

	@Test
	void difficultiesRank() {
		double[] parried = new double[3];
		int[] wins = new int[3];
		for (BotBrain.Difficulty d : BotBrain.Difficulty.values()) {
			parried[d.ordinal()] = fight(swinger(30, 0, new Random(1)), bot(d, new Random(2)), 3)[0].parried();
			wins[d.ordinal()] = fight(human(1.5, true, new Random(4)), bot(d, new Random(5)), 3)[0].kills;
		}
		assertTrue(parried[0] < parried[1] && parried[1] < parried[2], "harder bots parry more: " + java.util.Arrays.toString(parried));
		assertTrue(wins[0] > wins[1] && wins[1] > wins[2], "a player wins less against harder bots: " + java.util.Arrays.toString(wins));
	}
}
