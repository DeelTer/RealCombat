package ru.deelter.realcombat.combat;

import lombok.Getter;
import ru.deelter.realcombat.config.CombatConfig;

import java.util.Random;

/**
 * The decisions of the sparring partner, apart from its body so they can be tried out in a test: it reads the
 * opponent's windup and decides once per swing whether to parry, chamber or take it, attacks, feints, ripostes and
 * kicks a turtling parry. The decision to parry is made early, at the start of a windup, with a reaction time: a feint
 * after it fools the bot as it would fool a player.
 */
public final class BotBrain {
	@Getter
	private final Difficulty difficulty;
	private final Random random;
	private long now;
	private int think = 30;
	private boolean decided;
	private long reactAt = -1;
	private boolean chamber;
	private boolean willFeint;
	/**
	 * the part of the windup at which the coming feint goes: late, after the foe has committed to a parry
	 */
	private float feintAt;
	private boolean riposteDecided;
	private int foeTicks = Integer.MAX_VALUE;
	/**
	 * how much longer it waits between its own blows: more against a player whose body has to do the swinging
	 */
	@lombok.Setter
	private float pace = 1f;

	public BotBrain(Difficulty difficulty, Random random) {
		this.difficulty = difficulty;
		this.random = random;
	}

	/**
	 * How well it reads you.
	 *
	 * @param parry    chance to parry a blow it sees coming
	 * @param reaction ticks from the start of a windup until it can decide
	 * @param jitter   ticks its parry may be off by
	 * @param feint    chance to feint its own blow
	 * @param readFeint chance to see a feint and hold its parry
	 * @param chamber  chance to chamber instead of parrying
	 * @param riposte  chance to riposte after a parry
	 * @param pressure how eager it is to attack, 1 is normal
	 */
	public enum Difficulty {
		EASY(0.4f, 5, 2, 0.05f, 0f, 0f, 0.4f, 0.8f),
		NORMAL(0.68f, 4, 1, 0.3f, 0.3f, 0.1f, 0.7f, 1.15f),
		HARD(0.85f, 3, 1, 0.45f, 0.55f, 0.2f, 0.9f, 1.4f);

		final float parry;
		final int reaction;
		final int jitter;
		final float feint;
		final float readFeint;
		final float chamber;
		final float riposte;
		final float pressure;

		Difficulty(float parry, int reaction, int jitter, float feint, float readFeint, float chamber, float riposte, float pressure) {
			this.parry = parry;
			this.reaction = reaction;
			this.jitter = jitter;
			this.feint = feint;
			this.readFeint = readFeint;
			this.chamber = chamber;
			this.riposte = riposte;
			this.pressure = pressure;
		}
	}

	/**
	 * One tick of thinking, after both fighters ticked.
	 *
	 * @param self     the bot
	 * @param foe      the one it fights
	 * @param distance between them, blocks
	 * @param aimed    the foe looks at the bot, so his windup is meant for it
	 * @param speed    the bot's weapon speed
	 */
	public void tick(Fighter self, Fighter foe, double distance, boolean aimed, CombatConfig.Rules r, double speed) {
		now++;
		defend(self, foe, distance, aimed, r, speed);
		attack(self, distance, r, speed);
	}

	private void defend(Fighter self, Fighter foe, double distance, boolean aimed, CombatConfig.Rules r, double speed) {
		boolean threat = (foe.getPhase() == Phase.WINDUP || foe.getPhase() == Phase.RELEASE) && distance < 4.5 && aimed;
		// a feint straight into a new windup: the old swing is gone without a tick in between
		boolean fresh = foe.getPhase() == Phase.WINDUP && foe.getTicks() < foeTicks;
		foeTicks = foe.getPhase() == Phase.WINDUP ? foe.getTicks() : Integer.MAX_VALUE;
		if (!threat || fresh) {
			// the windup was feinted (or is over): a sharp bot sees it and keeps its parry
			if (decided && reactAt >= 0 && random.nextFloat() < difficulty.readFeint) reactAt = -1;
			decided = false;
		}
		if (threat && !decided && (foe.getPhase() == Phase.RELEASE || foe.getTicks() >= difficulty.reaction)) {
			decided = true;
			if (random.nextFloat() < difficulty.parry) {
				// the blow lands about two ticks into the release; a parry lasts r.parry ticks: start it so the blow falls
				// in its middle, a chamber just before the release
				int toRelease = foe.getPhase() == Phase.WINDUP ? foe.getLength() - foe.getTicks() : 0;
				chamber = self.getPhase() == Phase.IDLE && random.nextFloat() < difficulty.chamber;
				int lead = chamber ? 1 : Math.max(0, r.getParry() / 2 - 2);
				int offset = random.nextInt(difficulty.jitter * 2 + 1) - difficulty.jitter;
				reactAt = now + Math.max(0, toRelease - lead + offset);
			}
		}
		if (reactAt >= 0 && now >= reactAt) {
			reactAt = -1;
			if (chamber) Rules.attack(self, foe.getDir(), r, speed);
			else Rules.parry(self, r);
		}
		// a foe who hides behind parries gets kicked
		if (difficulty != Difficulty.EASY && foe.getPhase() == Phase.PARRY && distance < 2.2 && random.nextFloat() < 0.08f / pace) {
			Rules.kick(self, r);
		}
	}

	private void attack(Fighter self, double distance, CombatConfig.Rules r, double speed) {
		// a riposte is decided once, the moment the parry lands
		if (self.getRiposteWindow() > 0 && !riposteDecided) {
			riposteDecided = true;
			if (random.nextFloat() < difficulty.riposte && Rules.attack(self, randomDir(), r, speed)) {
				willFeint = false;
				return;
			}
		}
		if (self.getRiposteWindow() == 0) riposteDecided = false;
		if (self.getPhase() == Phase.WINDUP && willFeint && self.progress() >= feintAt) {
			willFeint = false;
			// straight into a new blow, while the fooled parry is still recovering
			if (Rules.feint(self, r)) Rules.attack(self, randomDir(), r, speed);
			return;
		}
		// out of breath: it keeps its guard and waits for the stamina to come back
		if (self.getPhase() != Phase.IDLE || distance > 3.4 || reactAt >= 0 || self.getStamina() < r.getTiredStamina() + 15) return;
		if (--think > 0) return;
		think = (int) ((8 + random.nextInt(22)) * pace / difficulty.pressure);
		if (Rules.attack(self, randomDir(), r, speed)) {
			willFeint = random.nextFloat() < difficulty.feint;
			feintAt = 0.55f + random.nextFloat() * (r.getFeintWindow() - 0.55f);
		}
	}

	private Dir randomDir() {
		float roll = random.nextFloat();
		if (roll < 0.3f) return Dir.OVERHEAD;
		if (roll < 0.5f) return Dir.RIGHT;
		if (roll < 0.7f) return Dir.LEFT;
		if (roll < 0.85f) return Dir.STAB;
		return Dir.UNDER;
	}
}
