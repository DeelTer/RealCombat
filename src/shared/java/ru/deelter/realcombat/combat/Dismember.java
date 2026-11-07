package ru.deelter.realcombat.combat;

import ru.deelter.realcombat.config.CombatConfig;

import java.util.Random;

/**
 * What a blow takes off, as in Mordhau: the killing blow takes the part it landed on (the head always, a limb mostly);
 * a living fighter keeps his limbs, unless the server wants a blow at one who is nearly dead to take one too
 * ({@code lowHealthSever}). A thrust only makes holes.
 */
public final class Dismember {
	private Dismember() {
	}

	public enum Cut {
		/**
		 * a wound, nothing comes off
		 */
		WOUND,
		/**
		 * the part the blow landed on comes off
		 */
		SEVER,
		/**
		 * the head comes off
		 */
		HEAD,
		/**
		 * the body is cut in two at the waist: a killing side slash
		 */
		HALVE,
		/**
		 * the body is cleaved top to bottom: a killing overhead blow, mostly off a jump
		 */
		SPLIT
	}

	/**
	 * @param falling the attacker was falling (off a jump, a ledge) as the blow landed
	 */
	public static Cut decide(String part, Dir dir, boolean killed, float healthShare, boolean vrSlash, boolean falling,
							 CombatConfig.Compat compat, Random random) {
		// a fatality finishes, it never happens to the living
		if (killed && compat.isCleave()) {
			if (dir == Dir.OVERHEAD && random.nextFloat() < (falling ? compat.getSplitFallingChance() : compat.getSplitChance())) return Cut.SPLIT;
			if (dir.horizontal() && random.nextFloat() < compat.getHalveChance()) return Cut.HALVE;
		}
		return decide(part, dir, killed, healthShare, vrSlash, compat, random);
	}

	/**
	 * @param vr a VR blade: it lands many more blows than a mouse, so a living target loses a limb to one of them only
	 *           {@code vrSeverScale} as often
	 */
	public static Cut decide(String part, Dir dir, boolean killed, float healthShare, boolean vrSlash, boolean falling, boolean vr,
							 CombatConfig.Compat compat, Random random) {
		if (vr && !killed && random.nextFloat() >= compat.getVrSeverScale()) return Cut.WOUND;
		return decide(part, dir, killed, healthShare, vrSlash, falling, compat, random);
	}

	/**
	 * @param part        the part hit: "head", "body" or a limb
	 * @param killed      the blow killed
	 * @param healthShare the health left after the blow, of the maximum
	 */
	public static Cut decide(String part, Dir dir, boolean killed, float healthShare, CombatConfig.Compat compat, Random random) {
		return decide(part, dir, killed, healthShare, false, compat, random);
	}

	/**
	 * @param vrSlash a side slash (a mouse one, or a strong one of a VR blade): its line through the neck or the legs
	 *                takes them more readily, even off one nearly dead ({@code sideSever})
	 */
	public static Cut decide(String part, Dir dir, boolean killed, float healthShare, boolean vrSlash, CombatConfig.Compat compat, Random random) {
		if (dir == Dir.STAB || part.equals("body")) return Cut.WOUND;
		boolean head = part.equals("head");
		if (vrSlash && (head || part.contains("leg"))) {
			if (killed) return head ? Cut.HEAD : random.nextFloat() < Math.max(compat.getKillSeverChance(), 0.95f) ? Cut.SEVER : Cut.WOUND;
			// the head comes off only with the life: in the fight only limbs go
			if (!head && compat.isSideSever() && healthShare < compat.getSideSeverShare() && random.nextFloat() < compat.getSideSeverChance()) {
				return Cut.SEVER;
			}
			return Cut.WOUND;
		}
		if (part.equals("head")) return killed && compat.isDecapitate() ? Cut.HEAD : Cut.WOUND;
		if (killed) return random.nextFloat() < compat.getKillSeverChance() ? Cut.SEVER : Cut.WOUND;
		// a living fighter loses an arm or a leg, never the head (that kills) nor the body
		boolean limb = part.contains("arm") || part.contains("leg");
		boolean nearlyDead = compat.isLowHealthSever() && healthShare < compat.getLowHealthShare();
		return limb && nearlyDead && random.nextFloat() < compat.getSeverChance() ? Cut.SEVER : Cut.WOUND;
	}
}
