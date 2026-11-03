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
		HEAD
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
			if (compat.isSideSever() && healthShare < compat.getSideSeverShare() && random.nextFloat() < compat.getSideSeverChance()) {
				return head ? Cut.HEAD : Cut.SEVER;
			}
			return Cut.WOUND;
		}
		if (part.equals("head")) return killed && compat.isDecapitate() ? Cut.HEAD : Cut.WOUND;
		if (killed) return random.nextFloat() < compat.getKillSeverChance() ? Cut.SEVER : Cut.WOUND;
		boolean nearlyDead = compat.isLowHealthSever() && healthShare < compat.getLowHealthShare();
		return nearlyDead && random.nextFloat() < compat.getSeverChance() ? Cut.SEVER : Cut.WOUND;
	}
}
