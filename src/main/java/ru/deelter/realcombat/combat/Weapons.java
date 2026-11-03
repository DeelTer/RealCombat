package ru.deelter.realcombat.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What fights by these rules: swords and axes. Anything else in the hand keeps the vanilla click.
 */
public final class Weapons {
	private Weapons() {
	}

	public static boolean isWeapon(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES));
	}

	public static boolean armed(LivingEntity entity) {
		return isWeapon(entity.getMainHandItem());
	}

	/**
	 * the kind of weapon in the main hand: an axe fights heavy
	 */
	public static Weapon kind(LivingEntity entity) {
		return entity.getMainHandItem().is(ItemTags.AXES) ? Weapon.AXE : Weapon.SWORD;
	}

	public static boolean shield(ItemStack stack) {
		return stack.is(Items.SHIELD);
	}

	/**
	 * attack speed of the held weapon: slower weapons swing longer
	 */
	public static double speed(LivingEntity entity) {
		return entity.getAttributes().hasAttribute(Attributes.ATTACK_SPEED) ? entity.getAttributeValue(Attributes.ATTACK_SPEED) : 1.6;
	}

	public static float damage(LivingEntity entity) {
		return entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1f;
	}
}
