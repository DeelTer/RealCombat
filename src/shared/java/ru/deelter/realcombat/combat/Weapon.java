package ru.deelter.realcombat.combat;

/**
 * How a weapon fights, beyond its damage and speed (which come from the item's attributes: an axe is already slower
 * and harder). Mordhau's heavy weapons: a blow that costs more to parry and more to throw, that knocks a shield out of
 * the hands, and no point to stab with.
 *
 * @param parryCost   how much more stamina parrying or blocking its blows costs
 * @param swingCost   how much more stamina throwing a blow costs
 * @param breaksShield a blow into a shield knocks it away for a while, as a vanilla axe does
 * @param stab        damage of a stab: an axe only jabs with its head
 */
public enum Weapon {
	SWORD(1f, 1f, false, 1f),
	AXE(1.5f, 1.4f, true, 0.45f);

	public final float parryCost;
	public final float swingCost;
	public final boolean breaksShield;
	public final float stab;

	Weapon(float parryCost, float swingCost, boolean breaksShield, float stab) {
		this.parryCost = parryCost;
		this.swingCost = swingCost;
		this.breaksShield = breaksShield;
		this.stab = stab;
	}

	/**
	 * how hard a blow in this direction hits with this weapon, against a sword's slash
	 */
	public float damage(Dir dir) {
		return dir == Dir.STAB ? dir.damage * stab : dir.damage;
	}
}
