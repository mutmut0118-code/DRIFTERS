package com.dinglebob.drifter.world;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.brain.Strategist;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.hands.Crafting;
import com.dinglebob.drifter.personality.Trait;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Catch-up (DESIGN.md section 22): a Drifter that wasn't kept running while its area was unloaded fast-forwards when it
 * loads again, by rates instead of actions. The time away (capped, 3 days by default) is worked through in slices of a
 * quarter day: it gathers what a Drifter of its stage and archetype gathers in that time (from the data file
 * {@code data/<namespace>/drifter_catchup/}, with randomness), eats, wears its tools down and burns torches, smelts and
 * crafts what its new things allow toward its next goal (so its stage can rise from one slice to the next), and runs a
 * small risk of dying. Then it's put back at home if its home is loaded, healed by the time that passed.
 */
public final class CatchUp {
	public static final String DIRECTORY = "drifter_catchup";

	/** Most crafting steps it takes toward its goals per slice (a guard; a target is rarely more than a few). */
	private static final int MAX_TARGET_STEPS = 6;
	/** Items smelted by one coal (or charcoal), and by one log or plank, as in a furnace. */
	private static final double COAL_ITEMS = 8.0;
	private static final double WOOD_ITEMS = 1.5;

	/** The rates file's contents. */
	private record Rates(int minGapTicks, int capTicks, int sliceTicks, double workShare, double foodPointsPerDay,
		Map<String, Double> wear, double torchesPerWorkHour, double deathChancePerDay, Map<Integer, Map<String, Double>> stages,
		Map<String, Map<String, Double>> archetypes) {
	}

	private static volatile Rates rates = new Rates(2400, 72000, 6000, 0.45, 24.0, Map.of(), 0.4, 0.02, Map.of(), Map.of());

	private CatchUp() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("catchup");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				Map<Integer, Map<String, Double>> stages = new HashMap<>();
				for (Map.Entry<String, JsonElement> stage : json.getAsJsonObject("stages").entrySet()) {
					stages.put(Integer.parseInt(stage.getKey()), numbers(stage.getValue().getAsJsonObject()));
				}

				Map<String, Map<String, Double>> archetypes = new HashMap<>();
				for (Map.Entry<String, JsonElement> archetype : json.getAsJsonObject("archetypes").entrySet()) {
					archetypes.put(archetype.getKey(), numbers(archetype.getValue().getAsJsonObject()));
				}

				rates = new Rates(json.get("min_gap_ticks").getAsInt(), json.get("cap_ticks").getAsInt(), json.get("slice_ticks").getAsInt(),
					json.get("work_share").getAsDouble(), json.get("food_points_per_day").getAsDouble(), numbers(json.getAsJsonObject("tool_wear_per_work_hour")),
					json.get("torches_per_work_hour").getAsDouble(), json.get("death_chance_per_day").getAsDouble(), stages, archetypes);
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter catch-up file {}", file.getKey(), e);
			}
		}
	}

	private static Map<String, Double> numbers(JsonObject object) {
		Map<String, Double> map = new LinkedHashMap<>();
		object.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsDouble()));
		return map;
	}

	/** Gaps shorter than this aren't worth catching up. */
	public static int minGapTicks() {
		return rates.minGapTicks();
	}

	/** What one catch-up did, for its thoughts and the command. */
	public static final class Report {
		public final long ticks;
		final Map<Item, Integer> gained = new LinkedHashMap<>();
		final Map<Item, Integer> made = new LinkedHashMap<>();
		final List<String> broke = new ArrayList<>();
		int eaten;
		int torches;
		boolean died;
		int stageBefore;
		int stageAfter;
		int lost;

		Report(long ticks) {
			this.ticks = ticks;
		}

		public String describe() {
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "caught up on %.1f days away", this.ticks / 24000.0));
			if (this.died) {
				line.append(": died out there and lost what it carried");
			}

			if (!this.gained.isEmpty()) {
				line.append("; gathered ").append(list(this.gained));
			}

			if (!this.made.isEmpty()) {
				line.append("; made ").append(list(this.made));
			}

			if (this.eaten > 0) {
				line.append("; ate ").append(this.eaten).append(" food");
			}

			if (this.torches > 0) {
				line.append("; used ").append(this.torches).append(" torches");
			}

			if (!this.broke.isEmpty()) {
				line.append("; wore out its ").append(String.join(", ", this.broke));
			}

			if (this.lost > 0) {
				line.append("; left ").append(this.lost).append(" items behind (no room)");
			}

			if (this.stageAfter != this.stageBefore) {
				line.append("; now stage ").append(this.stageAfter).append(" (").append(Strategist.stageName(this.stageAfter)).append(")");
			}

			return line.toString();
		}

		private static String list(Map<Item, Integer> items) {
			List<String> parts = new ArrayList<>();
			items.forEach((item, count) -> parts.add(count + " " + Planner.name(item)));
			return String.join(", ", parts);
		}
	}

	/** Fast-forwards this Drifter by {@code ticks} of time away (capped). */
	public static Report run(DrifterEntity drifter, long ticks) {
		return run(drifter, ticks, 0L);
	}

	/**
	 * As {@link #run(DrifterEntity, long)}, plus {@code offlineTicks} for real time the world was closed (offline
	 * progression "real time", already capped by its own cap).
	 */
	public static Report run(DrifterEntity drifter, long ticks, long offlineTicks) {
		Rates r = rates;
		long total = Math.min(ticks, r.capTicks()) + Math.max(0L, offlineTicks);
		Report report = new Report(total);
		report.stageBefore = Strategist.stage(drifter);
		RandomSource random = drifter.getRandom();
		for (long done = 0; done < total && !report.died; done += r.sliceTicks()) {
			long slice = Math.min(r.sliceTicks(), total - done);
			int stage = Strategist.stage(drifter);
			double workHours = slice / 1000.0 * r.workShare();
			gather(drifter, r, stage, workHours, random, report);
			eat(drifter, r.foodPointsPerDay() * slice / 24000.0, report);
			wear(drifter, r, stage, workHours, report);
			torches(drifter, r, stage, workHours, random, report);
			smelt(drifter, stage, report);
			craftTowardGoals(drifter, report);
			if (random.nextDouble() < deathChance(drifter, r, stage, slice)) {
				die(drifter, report);
			}
		}

		report.stageAfter = Strategist.stage(drifter);
		// Healed by the time that passed, as a fed player would be.
		if (drifter.getFoodData().getFoodLevel() > 6) {
			drifter.setHealth(drifter.getMaxHealth());
		}

		goHome(drifter);
		drifter.getDrifterBrain().restart("caught up");
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), report.describe());
		return report;
	}

	// ---------------------------------------------------------------------------------------------

	private static void gather(DrifterEntity drifter, Rates r, int stage, double workHours, RandomSource random, Report report) {
		Map<String, Double> perHour = r.stages().get(Mth.clamp(stage, 0, 8));
		if (perHour == null) {
			return;
		}

		String archetype = drifter.getPersonality().archetype().name().toLowerCase(Locale.ROOT);
		Map<String, Double> multipliers = r.archetypes().getOrDefault(archetype, Map.of());
		for (Map.Entry<String, Double> entry : perHour.entrySet()) {
			Item item = resolve(drifter, entry.getKey());
			if (item == null) {
				continue;
			}

			double mean = entry.getValue() * multipliers.getOrDefault(entry.getKey(), 1.0) * workHours;
			// Somewhere between half and one and a half times the usual, so no two catch-ups are alike.
			int count = (int)Math.floor(mean * (0.5 + random.nextDouble()) + random.nextDouble());
			if (count > 0) {
				int kept = give(drifter, new ItemStack(item, count), report);
				if (kept > 0) {
					report.gained.merge(item, kept, Integer::sum);
				}
			}
		}
	}

	@Nullable
	private static Item resolve(DrifterEntity drifter, String key) {
		if (key.equals("#logs")) {
			Item best = Items.OAK_LOG;
			int bestCount = 0;
			Map<Item, Integer> counts = counts(drifter);
			for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
				if (new ItemStack(entry.getKey()).is(ItemTags.LOGS_THAT_BURN) && entry.getValue() > bestCount) {
					best = entry.getKey();
					bestCount = entry.getValue();
				}
			}

			return best;
		}

		ResourceLocation id = ResourceLocation.tryParse(key);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	/** Into its inventory, then its home chests if they're loaded; the rest is left behind. Returns how many it kept. */
	private static int give(DrifterEntity drifter, ItemStack stack, Report report) {
		int wanted = stack.getCount();
		drifter.getInventory().add(stack);
		if (!stack.isEmpty()) {
			Home home = drifter.getDrifterBrain().home();
			if (home != null && home.dimension.equals(drifter.level().dimension().location())) {
				for (BlockPos chest : home.chests()) {
					var container = drifter.level().isLoaded(chest) ? com.dinglebob.drifter.hands.Containers.get(drifter.level(), chest) : null;
					if (container == null) {
						continue;
					}

					for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
						ItemStack there = container.getItem(slot);
						if (there.isEmpty()) {
							container.setItem(slot, stack.split(stack.getMaxStackSize()));
						} else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
							int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
							there.grow(move);
							stack.shrink(move);
						}
					}

					container.setChanged();
					home.record(chest, container);
				}
			}
		}

		report.lost += stack.getCount();
		return wanted - stack.getCount();
	}

	/** Eats what it carries to cover the hunger of that time, cooked first; short of food, it goes hungry. */
	private static void eat(DrifterEntity drifter, double points, Report report) {
		DrifterInventory inventory = drifter.getInventory();
		double left = points;
		for (boolean raw : new boolean[] {false, true}) {
			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				FoodProperties food = stack.get(DataComponents.FOOD);
				if (food == null || com.dinglebob.drifter.brain.Food.isBad(stack)
					|| raw != com.dinglebob.drifter.brain.Food.isCookable(drifter.level(), stack)) {
					continue;
				}

				while (!stack.isEmpty() && left > 0) {
					left -= food.nutrition();
					stack.shrink(1);
					report.eaten++;
				}
			}
		}

		if (left > 0) {
			var foodData = drifter.getFoodData();
			foodData.setFoodLevel(Math.max(1, foodData.getFoodLevel() - (int)Math.ceil(left)));
		}
	}

	/** Its tools wear as they would in that much work; one that wears out is gone. */
	private static void wear(DrifterEntity drifter, Rates r, int stage, double workHours, Report report) {
		Map<String, net.minecraft.tags.TagKey<Item>> kinds = Map.of("pickaxe", ItemTags.PICKAXES, "axe", ItemTags.AXES, "shovel", ItemTags.SHOVELS,
			"sword", ItemTags.SWORDS);
		for (Map.Entry<String, net.minecraft.tags.TagKey<Item>> kind : kinds.entrySet()) {
			int damage = (int)Math.round(r.wear().getOrDefault(kind.getKey(), 0.0) * workHours);
			ItemStack tool = best(drifter, kind.getValue());
			if (tool == null || damage <= 0) {
				continue;
			}

			int after = tool.getDamageValue() + damage;
			if (after >= tool.getMaxDamage()) {
				report.broke.add(Planner.name(tool.getItem()));
				tool.shrink(1);
			} else {
				tool.setDamageValue(after);
			}
		}
	}

	/** The tool of this kind it would use: the one with the most uses left. */
	@Nullable
	private static ItemStack best(DrifterEntity drifter, net.minecraft.tags.TagKey<Item> kind) {
		ItemStack best = null;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(kind) && stack.isDamageableItem() && (best == null || stack.getMaxDamage() - stack.getDamageValue() > best.getMaxDamage() - best.getDamageValue())) {
				best = stack;
			}
		}

		return best;
	}

	private static void torches(DrifterEntity drifter, Rates r, int stage, double workHours, RandomSource random, Report report) {
		if (stage < 3) {
			return;
		}

		String archetype = drifter.getPersonality().archetype().name().toLowerCase(Locale.ROOT);
		double mean = r.torchesPerWorkHour() * r.archetypes().getOrDefault(archetype, Map.of()).getOrDefault("torches", 1.0) * workHours;
		int used = take(drifter, stack -> stack.is(Items.TORCH), (int)Math.floor(mean + random.nextDouble()));
		report.torches += used;
	}

	/** Smelts its raw iron and raw food, as it would at its furnace, with the fuel it has (coal first, then wood). */
	private static void smelt(DrifterEntity drifter, int stage, Report report) {
		boolean furnace = stage >= 3 || count(drifter, Items.FURNACE) > 0
			|| com.dinglebob.drifter.brain.Stations.usable(drifter, Blocks.FURNACE) != null;
		if (!furnace) {
			return;
		}

		Level level = drifter.level();
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			Item output = stack.is(Items.RAW_IRON) ? Items.IRON_INGOT : stack.is(Items.RAW_GOLD) ? Items.GOLD_INGOT
				: com.dinglebob.drifter.brain.Food.isCookable(level, stack) ? Planner.cookedForm(level, stack.getItem()) : null;
			if (output == null) {
				continue;
			}

			int wanted = stack.getCount();
			int smelted = fuelFor(drifter, wanted);
			if (smelted <= 0) {
				return;
			}

			stack.shrink(smelted);
			int kept = give(drifter, new ItemStack(output, smelted), report);
			report.made.merge(output, kept, Integer::sum);
		}
	}

	/** Burns fuel for up to {@code items} smelts; returns how many it had fuel for. */
	private static int fuelFor(DrifterEntity drifter, int items) {
		int done = 0;
		while (done < items) {
			if (take(drifter, stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL), 1) == 1) {
				done += (int)COAL_ITEMS;
			} else if (take(drifter, stack -> stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS), 1) == 1) {
				done += (int)Math.ceil(WOOD_ITEMS);
			} else {
				break;
			}
		}

		return Math.min(done, items);
	}

	/**
	 * Works toward its next goals with what it has now: if the plan for the next target is only crafting and smelting
	 * (no trips out), it does it on the spot, with the real recipes. Then the next target, and so on.
	 */
	private static void craftTowardGoals(DrifterEntity drifter, Report report) {
		for (int step = 0; step < MAX_TARGET_STEPS; step++) {
			Strategist.Target target = Strategist.nextTarget(drifter);
			if (target == null) {
				return;
			}

			Planner.Plan plan = Planner.plan(drifter, KnownWorld.scan(drifter), target.items(), target.count());
			if (!plan.feasible() || plan.actions().isEmpty()) {
				return;
			}

			for (Planner.Action action : plan.actions()) {
				if (!(action instanceof Planner.Craft) && !(action instanceof Planner.Place) && !(action instanceof Planner.Smelt)) {
					return;
				}
			}

			for (Planner.Action action : plan.actions()) {
				if (action instanceof Planner.Craft craft) {
					for (int i = 0; i < craft.times(); i++) {
						var recipe = Crafting.find(drifter, stack -> stack.is(craft.item()), true);
						if (recipe.isEmpty()) {
							return;
						}

						ItemStack made = Crafting.craftOnce(drifter, recipe.get().recipe());
						if (made.isEmpty()) {
							return;
						}

						report.made.merge(made.getItem(), made.getCount(), Integer::sum);
					}
				} else if (action instanceof Planner.Smelt smelt) {
					int have = take(drifter, smelt.input(), smelt.count());
					int fuel = fuelFor(drifter, have);
					give(drifter, new ItemStack(smelt.output(), fuel), report);
					report.made.merge(smelt.output(), fuel, Integer::sum);
					if (fuel < smelt.count()) {
						return;
					}
				}
				// (A Place is a station put down at its base: it already has one where it works.)
			}

			if (!target.satisfied().test(drifter)) {
				return;
			}
		}
	}

	private static double deathChance(DrifterEntity drifter, Rates r, int stage, long slice) {
		double caution = drifter.getPersonality().get(Trait.CAUTION);
		// Riskier for the reckless and for the first days (no armor); a little riskier deep in the mines later on.
		double stageFactor = stage <= 2 ? 1.3 : stage >= 5 ? 1.1 : 1.0;
		return r.deathChancePerDay() * slice / 24000.0 * (1.5 - caution) * stageFactor;
	}

	/** Died out there: what it carried is gone (it's far away, wherever that was), and it starts again at home. */
	private static void die(DrifterEntity drifter, Report report) {
		report.died = true;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			inventory.setItem(slot, ItemStack.EMPTY);
		}

		drifter.getFoodData().setFoodLevel(20);
	}

	/** Back at home if its home is in this dimension and loaded (else it carries on from where it is). */
	private static void goHome(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		if (home == null || !home.dimension.equals(level.dimension().location()) || !level.isLoaded(home.center)) {
			return;
		}

		BlockPos spot = null;
		for (BlockPos pos : BlockPos.withinManhattan(home.center, 4, 3, 4)) {
			if (com.dinglebob.drifter.brain.KnownBlocks.isStandable(level, pos) && level.getFluidState(pos).isEmpty()) {
				spot = pos.immutable();
				break;
			}
		}

		if (spot != null) {
			drifter.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}
	}

	// ---------------------------------------------------------------------------------------------

	private static Map<Item, Integer> counts(DrifterEntity drifter) {
		Map<Item, Integer> counts = new HashMap<>();
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		return counts;
	}

	private static int count(DrifterEntity drifter, Item item) {
		return counts(drifter).getOrDefault(item, 0);
	}

	/** Takes up to {@code count} matching items from the main inventory; returns how many. */
	private static int take(DrifterEntity drifter, java.util.function.Predicate<ItemStack> filter, int count) {
		int taken = 0;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE && taken < count; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && filter.test(stack)) {
				int move = Math.min(stack.getCount(), count - taken);
				stack.shrink(move);
				taken += move;
			}
		}

		return taken;
	}
}
