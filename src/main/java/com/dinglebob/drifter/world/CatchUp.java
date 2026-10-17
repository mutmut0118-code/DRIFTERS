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
 * loads again, by rates instead of actions. The time away (capped, 10 days by default) is worked through in slices of a
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
		Map<String, Map<String, Double>> archetypes, double xpPerWorkHour, double structureFindsPerDay, List<StructureLoot> structureLoot) {
	}

	/** A kind of chest it may come across while away (a vanilla loot table), how often against the others, and from which day
	 * of its time away (the rarer ones only after it has been out a while). */
	private record StructureLoot(String table, double weight, double fromDay, String name) {
	}

	private static volatile Rates rates = new Rates(2400, 240000, 6000, 0.45, 24.0, Map.of(), 0.4, 0.02, Map.of(), Map.of(), 4.0, 0.0, List.of());

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
					json.get("torches_per_work_hour").getAsDouble(), json.get("death_chance_per_day").getAsDouble(), stages, archetypes,
					json.has("xp_per_work_hour") ? json.get("xp_per_work_hour").getAsDouble() : 4.0,
					json.has("structure_finds_per_day") ? json.get("structure_finds_per_day").getAsDouble() : 0.0, structureLoot(json));
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter catch-up file {}", file.getKey(), e);
			}
		}
	}

	private static List<StructureLoot> structureLoot(JsonObject json) {
		List<StructureLoot> list = new ArrayList<>();
		if (json.has("structure_loot")) {
			for (JsonElement element : json.getAsJsonArray("structure_loot")) {
				JsonObject entry = element.getAsJsonObject();
				list.add(new StructureLoot(entry.get("table").getAsString(), entry.get("weight").getAsDouble(),
					entry.has("from_day") ? entry.get("from_day").getAsDouble() : 0.0, entry.get("name").getAsString()));
			}
		}

		return list;
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
		int built;
		boolean settled;
		boolean chest;
		int chests;
		final List<String> furnished = new ArrayList<>();
		final List<String> raided = new ArrayList<>();
		final Map<Item, Integer> looted = new LinkedHashMap<>();
		int stored;
		int xp;

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

			if (this.settled) {
				line.append("; settled down there");
			}

			if (this.built > 0) {
				line.append("; built ").append(this.built).append(" blocks of its hut");
			}

			if (!this.furnished.isEmpty()) {
				line.append("; put in ").append(String.join(", ", this.furnished));
			}

			if (this.chest) {
				line.append(this.chests > 1 ? "; put " + this.chests + " chests in it" : "; put a chest in it");
			}

			if (!this.raided.isEmpty()) {
				line.append("; went through the chests of ").append(String.join(", ", this.raided));
				if (!this.looted.isEmpty()) {
					line.append(" (").append(list(this.looted)).append(")");
				}
			}

			if (this.stored > 0) {
				line.append("; stored ").append(this.stored).append(" items at home");
			}

			if (this.xp > 0) {
				line.append("; earned ").append(this.xp).append(" experience");
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
			craftUseful(drifter, report);
			findStructure(drifter, r, done / 24000.0, slice, random, report);
			// A day or more out with no home: it settles where it is (found by the user: Drifters that set off at the start of
			// the world came back days later with no hut, no chest, and so never into the iron age).
			if (done >= SETTLE_AFTER_TICKS) {
				settle(drifter, report);
			}

			buildHut(drifter, report);
			furnish(drifter, done / 24000.0, report);
			// Far enough along: its house, if it has what it takes (user's request, 2026-10-17).
			String house = CatchUpHouse.build(drifter, Strategist.stage(drifter));
			if (house != null) {
				report.furnished.add(house);
			}
			placeChest(drifter, report);
			// Its best armor and shield on before anything goes in the chest (or spares and its best both went in).
			com.dinglebob.drifter.executor.task.WearArmorTask.equipAllNow(drifter);
			store(drifter, report);
			// Experience from mining and fighting, at a player's pace (found by the user: they all came back at level 0).
			int xp = (int)Math.round(workHours * r.xpPerWorkHour() * (0.6 + 0.8 * random.nextDouble()));
			if (xp > 0) {
				drifter.giveExperiencePoints(xp);
				report.xp += xp;
			}
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
		// A target that needs a trip out (a bed's wool, a shelter) is passed over for the ones after it it can make on the spot
		// (found by the user: stuck behind its first night's targets, it carried 14 iron ingots and never made a tool of them).
		// (Only targets it works on count toward the limit; passing one over doesn't, or a few passed over used up the slice
		// and it never replaced a worn-out pickaxe: found in testing.)
		java.util.Set<String> passed = new java.util.HashSet<>();
		int worked = 0;
		for (int step = 0; step < MAX_TARGET_STEPS * 5 && worked < MAX_TARGET_STEPS; step++) {
			Strategist.Target target = Strategist.nextTarget(drifter, t -> !passed.contains(t.label()));
			if (target == null) {
				return;
			}

			Planner.Plan plan = Planner.plan(drifter, KnownWorld.scan(drifter), target.items(), target.count());
			boolean onTheSpot = plan.feasible() && !plan.actions().isEmpty();
			for (Planner.Action action : plan.actions()) {
				if (!(action instanceof Planner.Craft) && !(action instanceof Planner.Place) && !(action instanceof Planner.Smelt)) {
					onTheSpot = false;
				}
			}

			if (!onTheSpot) {
				passed.add(target.label());
				continue;
			}

			worked++;

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
				passed.add(target.label());
			}
		}
	}

	private static final long SETTLE_AFTER_TICKS = 24000;
	private static final int MAX_CHESTS = 4;

	/** Settles where it stands, on firm dry ground, if it has no home (not a nomad: they barely settle anywhere). */
	private static void settle(DrifterEntity drifter, Report report) {
		Level level = drifter.level();
		if (drifter.getDrifterBrain().home() != null || drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD
			|| level.dimension() != Level.OVERWORLD || drifter.isInWater() || !drifter.onGround()) {
			return;
		}

		// A clear spot near where it stands: nothing in the way of the hut, no tree in it (found by the user: a hut built
		// round a tree, which it had settled under).
		BlockPos site = clearSite(drifter);
		if (site == null) {
			return;
		}

		Home home = new Home(site, level.dimension().location(), level.getGameTime());
		home.hutDoor = com.dinglebob.drifter.building.StarterHut.chooseDoor(level, site);
		drifter.getDrifterBrain().setHome(home);
		report.settled = true;
	}

	private static final int SITE_SEARCH = 10;

	/** The nearest spot within {@link #SITE_SEARCH} where the whole hut fits on open ground: no logs, leaves or water in it. */
	@Nullable
	private static BlockPos clearSite(DrifterEntity drifter) {
		Level level = drifter.level();
		BlockPos feet = drifter.blockPosition();
		for (int ring = 0; ring <= SITE_SEARCH; ring += 2) {
			for (int dx = -ring; dx <= ring; dx += 2) {
				for (int dz = -ring; dz <= ring; dz += 2) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					BlockPos column = feet.offset(dx, 0, dz);
					if (!level.isLoaded(column)) {
						continue;
					}

					BlockPos spot = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
					if (Math.abs(spot.getY() - feet.getY()) <= 3 && siteClear(level, drifter, spot)) {
						return spot;
					}
				}
			}
		}

		return null;
	}

	private static boolean siteClear(Level level, DrifterEntity drifter, BlockPos spot) {
		var hut = com.dinglebob.drifter.building.StarterHut.of(new Home(spot, level.dimension().location(), level.getGameTime()));
		List<BlockPos> cells = new ArrayList<>(hut.floor());
		cells.addAll(hut.inside());
		cells.addAll(hut.roof());
		for (int dy = 0; dy <= hut.wallHeight; dy++) {
			cells.addAll(hut.wallLayer(dy));
		}

		for (BlockPos cell : cells) {
			if (!level.isLoaded(cell)) {
				return false;
			}

			var state = level.getBlockState(cell);
			if (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES) || !state.getFluidState().isEmpty()
				|| com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(level, cell)) {
				return false;
			}
		}

		return !level.getBlockState(spot.below()).getFluidState().isEmpty() ? false : true;
	}

	/** A chest in its hut, from one it carries or eight planks, at a furniture spot inside (so it can store, and the iron
	 * age starts: the strategist wants a home with a chest). */
	private static void placeChest(DrifterEntity drifter, Report report) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		// (Another once its chests are nearly full, up to four: ten days of finds didn't fit in one; found in testing.)
		if (home == null || home.hasChest() && (home.fullness() < 0.9 || home.chests().size() >= MAX_CHESTS)
			|| !home.dimension.equals(level.dimension().location()) || !level.isLoaded(home.center)) {
			return;
		}

		boolean haveChest = take(drifter, stack -> stack.is(Items.CHEST), 1) == 1;
		boolean planks = drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.PLANKS)) >= 8;
		// (Or two logs, which make the eight planks: it had used its planks on tools and carried 141 logs.)
		boolean logs = drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.LOGS)) >= 2;
		if (!haveChest && !planks && !logs) {
			return;
		}

		for (BlockPos spot : chestSpots(home)) {
			if (level.isLoaded(spot) && level.getBlockState(spot).canBeReplaced() && !level.getBlockState(spot.below()).canBeReplaced()
				&& !com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(level, spot)) {
				if (!haveChest && planks) {
					takePlanks(drifter, 8);
				} else if (!haveChest) {
					take(drifter, stack -> stack.is(net.minecraft.tags.ItemTags.LOGS), 2);
				}

				level.setBlock(spot, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
				home.addChest(spot);
				report.chest = true;
				report.chests++;
				return;
			}
		}

		if (haveChest) {
			give(drifter, new ItemStack(Items.CHEST), report);
		}
	}

	/**
	 * What it doesn't need to carry goes in its chests at home, as it would on its trips back (section 22: "added to
	 * inventory or home chests"; found in testing: ten days of cobblestone, coal and mob drops filled its pockets and what
	 * it made later fell out). Only into chests that are there and loaded.
	 */
	private static void store(DrifterEntity drifter, Report report) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		if (home == null || !home.hasChest() || !home.dimension.equals(level.dimension().location())) {
			return;
		}

		var inventory = drifter.getInventory();
		for (Map.Entry<Integer, Integer> entry : com.dinglebob.drifter.brain.Storage.toDeposit(drifter).entrySet()) {
			ItemStack stack = inventory.getItem(entry.getKey());
			// Materials it crafts its next things from stay on it: catch-up only crafts from its pockets (found in testing: its
			// iron went in the chest and it never made iron tools or armor).
			if (isMaterial(stack)) {
				continue;
			}

			int count = Math.min(entry.getValue(), stack.getCount());
			for (BlockPos chest : home.chests()) {
				if (count <= 0 || !level.isLoaded(chest) || !(level.getBlockEntity(chest) instanceof net.minecraft.world.Container container)) {
					continue;
				}

				ItemStack moving = stack.split(count);
				ItemStack left = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, container, moving, null);
				report.stored += moving.getCount() - left.getCount();
				count = left.getCount();
				if (!left.isEmpty()) {
					stack.grow(left.getCount());
				}

				home.record(chest, container);
			}
		}
	}

	private static boolean isMaterial(ItemStack stack) {
		return stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON) || stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD)
			|| stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.OBSIDIAN)
			|| stack.is(Items.STRING) || stack.is(Items.LEATHER) || stack.is(Items.FEATHER) || stack.is(Items.FLINT)
			|| stack.is(Items.STICK) || stack.is(Items.BOOK) || stack.is(Items.PAPER) || stack.is(net.minecraft.tags.ItemTags.PLANKS)
			|| stack.is(net.minecraft.tags.ItemTags.WOOL);
	}

	/** Things a player out exploring makes when it can, beyond its targets: a bow once it has string. */
	private static void craftUseful(DrifterEntity drifter, Report report) {
		if (count(drifter, Items.BOW) == 0 && count(drifter, Items.STRING) >= 3) {
			var recipe = com.dinglebob.drifter.hands.Crafting.find(drifter, stack -> stack.is(Items.BOW), true);
			if (recipe.isPresent()) {
				ItemStack made = com.dinglebob.drifter.hands.Crafting.craftOnce(drifter, recipe.get().recipe());
				if (!made.isEmpty()) {
					report.made.merge(made.getItem(), made.getCount(), Integer::sum);
				}
			}
		}
	}

	/**
	 * Out exploring it comes across structures now and then and goes through their chests: rolled from the game's own
	 * loot tables, more often for explorers, and the rarer ones (temples, buried treasure, a mansion) only once it has been
	 * out a while (user's request: travelers should come back like players who went out exploring, with loot to show).
	 */
	private static void findStructure(DrifterEntity drifter, Rates r, double day, long slice, RandomSource random, Report report) {
		if (r.structureLoot().isEmpty() || !(drifter.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}

		String archetype = drifter.getPersonality().archetype().name().toLowerCase(Locale.ROOT);
		double rate = r.structureFindsPerDay() * r.archetypes().getOrDefault(archetype, Map.of()).getOrDefault("structure_finds", 1.0);
		if (random.nextDouble() >= rate * slice / 24000.0) {
			return;
		}

		double total = 0.0;
		for (StructureLoot loot : r.structureLoot()) {
			total += loot.fromDay() <= day ? loot.weight() : 0.0;
		}

		double roll = random.nextDouble() * total;
		for (StructureLoot loot : r.structureLoot()) {
			if (loot.fromDay() > day || (roll -= loot.weight()) > 0.0) {
				continue;
			}

			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.parse(loot.table()));
			var table = level.getServer().reloadableRegistries().getLootTable(key);
			var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, drifter.position())
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
			for (ItemStack stack : table.getRandomItems(params)) {
				Item item = stack.getItem();
				int kept = give(drifter, stack, report);
				if (kept > 0) {
					report.looted.merge(item, kept, Integer::sum);
				}
			}

			report.raided.add(loot.name());
			return;
		}
	}

	/** Where chests go: the back corners first; more chests on free floor cells that aren't the middle (it pillars there),
	 * just inside the door, or the bed's. */
	private static List<BlockPos> chestSpots(Home home) {
		return com.dinglebob.drifter.building.StarterHut.of(home).chestSpots();
	}

	/**
	 * The hut finished as it would finish it itself, from what it carries: a wooden door in the doorway, its crafting table
	 * and furnace in the corners, and from about the second day a bed (found by the user: caught-up huts had no doors, no
	 * furnace though it cooked its food, and no bed). Only at its own loaded home, never over a player's block.
	 */
	private static void furnish(DrifterEntity drifter, double day, Report report) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		if (home == null || home.borrowed() || !home.dimension.equals(level.dimension().location()) || !level.isLoaded(home.center)) {
			return;
		}

		var hut = com.dinglebob.drifter.building.StarterHut.of(home);
		// Only once the shell is up (its walls stand on either side of the doorway).
		if (level.getBlockState(hut.doorPos().relative(hut.door.getClockWise())).canBeReplaced()) {
			return;
		}

		BlockPos door = hut.doorPos();
		if (free(level, door) && free(level, door.above()) && (take(drifter, stack -> stack.is(Items.OAK_DOOR), 1) == 1 || usePlanks(drifter, 6))) {
			var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, hut.door.getOpposite());
			level.setBlock(door, lower, 3);
			level.setBlock(door.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
			report.furnished.add("a door");
		}

		List<BlockPos> corners = hut.furnitureSpots();
		placeStation(drifter, level, corners.get(1), Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE, "a crafting table", report,
			() -> usePlanks(drifter, 4));
		placeStation(drifter, level, corners.get(2), Blocks.FURNACE, Items.FURNACE, "a furnace", report,
			() -> take(drifter, stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE), 8) == 8);
		// A smoker from the third day (a furnace and four logs), a blast furnace once it has iron to spare (a furnace, five
		// ingots and three smooth stone, which is cobblestone smelted twice); user's request, 2026-10-16.
		List<BlockPos> stations = hut.stationSpots();
		if (day >= 2.5) {
			placeStation(drifter, level, stations.get(1), Blocks.SMOKER, Items.SMOKER, "a smoker", report,
				() -> count(drifter, Items.COBBLESTONE) >= 8 && drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.LOGS)) >= 4
					&& take(drifter, stack -> stack.is(Items.COBBLESTONE), 8) == 8 && take(drifter, stack -> stack.is(net.minecraft.tags.ItemTags.LOGS), 4) == 4);
		}

		if (day >= 4.0) {
			placeStation(drifter, level, stations.get(0), Blocks.BLAST_FURNACE, Items.BLAST_FURNACE, "a blast furnace", report,
				() -> count(drifter, Items.COBBLESTONE) >= 11 && count(drifter, Items.IRON_INGOT) >= 5
					&& take(drifter, stack -> stack.is(Items.COBBLESTONE), 11) == 11 && take(drifter, stack -> stack.is(Items.IRON_INGOT), 5) == 5);
		}

		// A bed from the second day: three wool (or the string for it, four each) and three planks.
		if (day >= 1.5 && hut.bed(level) == null) {
			var spot = hut.bedSpot(level);
			int wool = drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.WOOL));
			int string = count(drifter, Items.STRING);
			boolean haveBed = take(drifter, stack -> stack.is(net.minecraft.tags.ItemTags.BEDS), 1) == 1;
			boolean canMake = !haveBed && (wool >= 3 || string >= 12) && hasPlanks(drifter, 3);
			if (spot != null && (haveBed || canMake)) {
				if (canMake) {
					int fromWool = Math.min(3, wool);
					take(drifter, stack -> stack.is(net.minecraft.tags.ItemTags.WOOL), fromWool);
					take(drifter, stack -> stack.is(Items.STRING), (3 - fromWool) * 4);
					usePlanks(drifter, 3);
				}

				var foot = Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, spot.facing());
				level.setBlock(spot.foot(), foot, 3);
				level.setBlock(spot.foot().relative(spot.facing()), foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
					net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
				report.furnished.add("a bed");
			}
		}

		// A torch on the back wall inside, as it puts one up itself.
		BlockPos torch = hut.torchSpot();
		if (free(level, torch) && take(drifter, stack -> stack.is(Items.TORCH), 1) == 1) {
			level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, hut.door), 3);
			report.furnished.add("a torch");
		}

		// Glass in the windows: glass it has, or sand it smelted (user's rule: huts have glass windows).
		for (BlockPos window : hut.windowCells()) {
			if (!free(level, window)) {
				continue;
			}

			if (CatchUpHouse.takeFromAll(drifter, stack -> stack.is(Items.GLASS) || stack.is(Items.GLASS_PANE), 1)
				|| CatchUpHouse.takeFromAll(drifter, stack -> stack.is(Items.SAND) || stack.is(Items.RED_SAND), 1)) {
				level.setBlock(window, Blocks.GLASS.defaultBlockState(), 3);
				report.furnished.add("glass in a window");
			}
		}

		// Done only when it all stands (found in testing: a door and a bed made a hut with 55 blocks missing "finished").
		if (!free(level, door) && hut.bed(level) != null && hut.missingBlocks(level) <= home.allowedGaps("hut")) {
			home.hutBuilt = true;
		}
	}

	private static void placeStation(DrifterEntity drifter, Level level, BlockPos spot, net.minecraft.world.level.block.Block block, Item item,
		String name, Report report, java.util.function.BooleanSupplier make) {
		var hut = com.dinglebob.drifter.building.StarterHut.of(drifter.getDrifterBrain().home());
		List<BlockPos> cells = new ArrayList<>(hut.inside());
		cells.addAll(hut.stationSpots());
		for (BlockPos cell : cells) {
			if (level.getBlockState(cell).is(block)) {
				return;
			}
		}

		if (!free(level, spot)) {
			return;
		}

		// (It keeps carrying one too if it has two: one for the hut, one on it, as it does.)
		boolean carried = count(drifter, item) > 0 && take(drifter, stack -> stack.is(item), 1) == 1;
		if (carried || make.getAsBoolean()) {
			level.setBlock(spot, block.defaultBlockState(), 3);
			report.furnished.add(name);
		}
	}

	private static boolean free(Level level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced() && !com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(level, pos);
	}

	private static boolean hasPlanks(DrifterEntity drifter, int count) {
		return drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.PLANKS)) >= count
			|| drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.LOGS)) >= (count + 3) / 4
			|| chestLogs(drifter) >= (count + 3) / 4;
	}

	/** Logs in its chests at home (it goes to them for what it needs, as it does live). */
	private static int chestLogs(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return 0;
		}

		int logs = 0;
		for (BlockPos pos : home.chests()) {
			if (drifter.level().isLoaded(pos) && drifter.level().getBlockEntity(pos) instanceof net.minecraft.world.Container chest) {
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					if (chest.getItem(slot).is(net.minecraft.tags.ItemTags.LOGS)) {
						logs += chest.getItem(slot).getCount();
					}
				}
			}
		}

		return logs;
	}

	/** Uses this many planks, or the logs that make them (four planks a log). */
	private static boolean usePlanks(DrifterEntity drifter, int count) {
		if (drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.PLANKS)) >= count) {
			takePlanks(drifter, count);
			return true;
		}

		int logs = (count + 3) / 4;
		return CatchUpHouse.takeFromAll(drifter, stack -> stack.is(net.minecraft.tags.ItemTags.LOGS), logs);
	}

	private static void takePlanks(DrifterEntity drifter, int count) {
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE && count > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(net.minecraft.tags.ItemTags.PLANKS)) {
				int used = Math.min(count, stack.getCount());
				stack.shrink(used);
				count -= used;
			}
		}
	}

	/**
	 * Section 22 catch-up "Building": the shell of its starter hut (floor, walls, roof) with the blocks it has, only at
	 * its own home, only while that's loaded, never where a player put a block or something stands, the door gap left
	 * open. The rest (door, bed, torch) it does for real next time it's about.
	 */
	private static void buildHut(DrifterEntity drifter, Report report) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		if (home == null || home.hutBuilt || home.borrowed() || !home.dimension.equals(level.dimension().location()) || !level.isLoaded(home.center)) {
			return;
		}

		com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
		List<BlockPos> cells = new ArrayList<>(hut.floor());
		for (int dy = 0; dy < hut.wallHeight; dy++) {
			for (BlockPos cell : hut.wallLayer(dy)) {
				if (!hut.isDoorColumn(cell)) {
					cells.add(cell);
				}
			}
		}

		cells.addAll(hut.roof());
		// Trees and plants where the hut goes, and inside it, cleared first (as it chops them itself; found in testing: a
		// spruce standing in a hut's walls left it full of gaps).
		List<BlockPos> clear = new ArrayList<>(cells);
		clear.addAll(hut.inside());
		// (The doorway too: a tree's leaves hanging in it kept the door out.)
		clear.add(hut.doorPos());
		clear.add(hut.doorPos().above());
		for (BlockPos cell : clear) {
			var state = level.getBlockState(cell);
			if (level.isLoaded(cell) && !state.isAir() && (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)
				|| state.canBeReplaced() && state.getFluidState().isEmpty()) && com.dinglebob.drifter.brain.KnownBlocks.mayTake(level, cell)
				&& !PlayerPlacedBlocks.isPlayerPlaced(level, cell)) {
				level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
			}
		}

		for (BlockPos cell : cells) {
			if (!level.isLoaded(cell) || com.dinglebob.drifter.building.StarterHut.filled(level, cell) || !level.getBlockState(cell).canBeReplaced()
				|| PlayerPlacedBlocks.isPlayerPlaced(level, cell) || !level.getEntities(null, new net.minecraft.world.phys.AABB(cell)).isEmpty()) {
				continue;
			}

			ItemStack block = styledBlock(drifter, home, hut, cell);
			if (block == null) {
				block = hutBlock(drifter);
			}

			if (block == null) {
				return;
			}

			level.setBlock(cell, ((net.minecraft.world.item.BlockItem)block.getItem()).getBlock().defaultBlockState(), 3);
			block.shrink(1);
			report.built++;
		}
	}

	/** The block its hut's look wants here (its stone or wooden floor, a log corner post), if it carries one; else null. */
	@Nullable
	private static ItemStack styledBlock(DrifterEntity drifter, Home home, com.dinglebob.drifter.building.StarterHut hut, BlockPos cell) {
		java.util.function.Predicate<ItemStack> wanted = null;
		if (cell.getY() == hut.center.getY() - 1) {
			if (home.hutFloor.equals("stone")) {
				wanted = stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE);
			} else if (home.hutFloor.equals("wood")) {
				wanted = stack -> stack.is(ItemTags.PLANKS);
			}
		} else if (home.hutLogCorners && hut.isCornerPost(cell)) {
			wanted = stack -> stack.is(ItemTags.LOGS);
		}

		if (wanted == null) {
			return null;
		}

		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem && wanted.test(stack)) {
				return stack;
			}
		}

		return null;
	}

	/** Stone first, then planks, then any building block (the hut's own rule): a block it carries, or null. */
	@Nullable
	private static ItemStack hutBlock(DrifterEntity drifter) {
		DrifterInventory inventory = drifter.getInventory();
		for (int pass = 0; pass < 3; pass++) {
			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem) || !com.dinglebob.drifter.hands.Tools.BUILDING_BLOCK.test(stack)) {
					continue;
				}

				boolean planks = stack.is(ItemTags.PLANKS);
				boolean dirt = stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT);
				if (pass == 0 && !planks && !dirt || pass == 1 && planks || pass == 2) {
					return stack;
				}
			}
		}

		return null;
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
