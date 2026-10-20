package com.dinglebob.drifter.skills;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * The potions it brews and uses (DESIGN.md section 10.14): fire resistance before the Nether's lava and blazes, healing
 * and strength for fights, night vision for caving. What makes each (the ingredient on an awkward potion), and drinking
 * one (1.6 s, as for a player) or, a splash one, throwing it down at its feet.
 */
public final class Potions {
	public enum Kind {
		FIRE_RESISTANCE("fire resistance", MobEffects.FIRE_RESISTANCE, Items.MAGMA_CREAM),
		HEALING("healing", MobEffects.HEAL, Items.GLISTERING_MELON_SLICE),
		STRENGTH("strength", MobEffects.DAMAGE_BOOST, Items.BLAZE_POWDER),
		NIGHT_VISION("night vision", MobEffects.NIGHT_VISION, Items.GOLDEN_CARROT);

		public final String label;
		public final Holder<MobEffect> effect;
		/** What goes on an awkward potion to make it. */
		public final Item ingredient;

		Kind(String label, Holder<MobEffect> effect, Item ingredient) {
			this.label = label;
			this.effect = effect;
			this.ingredient = ingredient;
		}
	}

	private Potions() {
	}

	/** A potion (to drink or a splash one) with this kind's effect (any strength or length of it). */
	public static boolean isKind(ItemStack stack, Kind kind) {
		if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION)) {
			return false;
		}

		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}

		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (effect.getEffect().equals(kind.effect)) {
				return true;
			}
		}

		return false;
	}

	/** A bottle of this base (water, awkward), not thrown. */
	public static boolean isBase(ItemStack stack, Holder<Potion> base) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return stack.is(Items.POTION) && contents != null && contents.is(base);
	}

	public static boolean isWater(ItemStack stack) {
		return isBase(stack, net.minecraft.world.item.alchemy.Potions.WATER);
	}

	public static boolean isAwkward(ItemStack stack) {
		return isBase(stack, net.minecraft.world.item.alchemy.Potions.AWKWARD);
	}

	/** Any potion bottle (to drink, splash or lingering). */
	public static boolean isAnyPotion(ItemStack stack) {
		return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
	}

	public static int count(DrifterEntity drifter, Kind kind) {
		return drifter.getInventory().count(stack -> isKind(stack, kind));
	}

	public static boolean feels(DrifterEntity drifter, Kind kind) {
		return drifter.hasEffect(kind.effect);
	}

	/**
	 * Using one: a splash potion first when there is one (thrown at its feet: no 1.6 s drink), else drinking one. Null
	 * if it has none.
	 */
	@Nullable
	public static Task use(DrifterEntity drifter, Kind kind) {
		boolean splash = drifter.getInventory().count(stack -> stack.is(Items.SPLASH_POTION) && isKind(stack, kind)) > 0;
		boolean drink = drifter.getInventory().count(stack -> stack.is(Items.POTION) && isKind(stack, kind)) > 0;
		if (!splash && !drink) {
			return null;
		}

		Item item = splash ? Items.SPLASH_POTION : Items.POTION;
		Predicate<ItemStack> wanted = stack -> stack.is(item) && isKind(stack, kind);
		Task use = splash ? new ThrowAtFeetTask(drifter) : new UseItemTask(drifter);
		return new SequenceTask(drifter, (splash ? "throw down" : "drink") + " a potion of " + kind.label,
			List.of(new SelectItemTask(drifter, wanted, "a potion of " + kind.label), use));
	}

	/** A splash potion thrown straight down at its feet (looking down, as a player does). */
	private static final class ThrowAtFeetTask extends Task {
		private int ticks;

		ThrowAtFeetTask(DrifterEntity drifter) {
			super(drifter);
		}

		@Override
		public String describe() {
			return "throw the potion down";
		}

		@Override
		protected int timeoutTicks() {
			return 30;
		}

		@Override
		protected Status onTick() {
			this.drifter.getKeys().releaseAll();
			this.drifter.getCamera().lookAt(this.drifter.getYRot(), 90.0F, CameraModel.TurnSpeed.COMBAT);
			if (++this.ticks < 4 || this.drifter.getXRot() < 75.0F && this.ticks < 12) {
				return Status.RUNNING;
			}

			if (!this.drifter.getMainHandItem().is(Items.SPLASH_POTION)) {
				return this.fail("the potion isn't in its hand");
			}

			FakePlayerBridge.act(this.drifter, fake -> fake.gameMode.useItem(fake, fake.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND));
			this.drifter.swing(InteractionHand.MAIN_HAND);
			return Status.SUCCESS;
		}
	}
}
