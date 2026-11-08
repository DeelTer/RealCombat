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

	/**
	 * the hand on a side ("left", "right") of an entity is cut off (ChopChop); one each for the server and the client,
	 * set by the compat that knows the wounds there
	 */
	public static java.util.function.BiPredicate<LivingEntity, String> serverHandless = (e, side) -> false;
	public static java.util.function.BiPredicate<LivingEntity, String> clientHandless = (e, side) -> false;

	private static boolean handless(LivingEntity entity, net.minecraft.world.entity.HumanoidArm arm) {
		var test = entity.level().isClientSide() ? clientHandless : serverHandless;
		return test.test(entity, arm == net.minecraft.world.entity.HumanoidArm.LEFT ? "left" : "right");
	}

	/**
	 * The sword hand is cut off and the other one holds a weapon: the fighter fights with that one, mirrored.
	 */
	public static boolean offHanded(LivingEntity entity) {
		var main = entity.getMainArm();
		return isWeapon(entity.getOffhandItem()) && handless(entity, main) && !handless(entity, main.getOpposite());
	}

	/**
	 * the weapon the fighter fights with: the one in the main hand, or in the other one when that is gone
	 */
	public static ItemStack weapon(LivingEntity entity) {
		return offHanded(entity) ? entity.getOffhandItem() : entity.getMainHandItem();
	}

	public static net.minecraft.world.InteractionHand hand(LivingEntity entity) {
		return offHanded(entity) ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND;
	}

	/**
	 * the arm that swings: the main one, or the other one when the main hand is gone
	 */
	public static net.minecraft.world.entity.HumanoidArm arm(LivingEntity entity) {
		return offHanded(entity) ? entity.getMainArm().getOpposite() : entity.getMainArm();
	}

	public static boolean armed(LivingEntity entity) {
		return isWeapon(entity.getMainHandItem()) || offHanded(entity);
	}

	/**
	 * the kind of weapon in the main hand: an axe fights heavy
	 */
	public static Weapon kind(LivingEntity entity) {
		return weapon(entity).is(ItemTags.AXES) ? Weapon.AXE : Weapon.SWORD;
	}

	public static boolean shield(ItemStack stack) {
		return stack.is(Items.SHIELD);
	}

	/**
	 * attack speed of the held weapon: slower weapons swing longer
	 */
	public static double speed(LivingEntity entity) {
		return entity.getAttributes().hasAttribute(Attributes.ATTACK_SPEED) ? fighting(entity, Attributes.ATTACK_SPEED) : 1.6;
	}

	public static float damage(LivingEntity entity) {
		return entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float) fighting(entity, Attributes.ATTACK_DAMAGE) : 1f;
	}

	/**
	 * An attribute as the fighting hand makes it: the game counts only the main hand's item, so with the weapon in
	 * the other hand its modifiers take the place of the main hand's.
	 */
	private static double fighting(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		double value = entity.getAttributeValue(attribute);
		if (!offHanded(entity)) return value;
		var none = net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY;
		var slot = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
		double main = entity.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, none).compute(attribute, 0, slot);
		double off = entity.getOffhandItem().getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, none).compute(attribute, 0, slot);
		return value - main + off;
	}
}
