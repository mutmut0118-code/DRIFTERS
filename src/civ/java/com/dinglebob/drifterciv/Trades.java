package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * Lines of work (milestone 23, docs/LIVED_WORLD.md B1 to B3: "each townsperson takes up a line by how it plays and what
 * the town needs ... a line is a leaning, not a cage: everyone still eats, sleeps, chats and does whims"). The lines are
 * data ({@code data/drifter_civ/drifter_lines/<line>.json}): the goals each leans toward, the natures that take to it, and
 * for a trade (smith, fletcher, fisher, enchanter) what it makes, sells and buys. Its place first (the keeper, the
 * leader, the guards), then its nature, then what the town is short of. A trade's people make what they sell (B2: got
 * the way they get anything) and sell it at their own doors in the mornings (B3: the trading screen, from their pockets,
 * stocked from their own chests).
 */
final class Trades {
	private Trades() {
	}

	/** One line of work, as its data file has it. */
	record Line(String name, Set<String> goals, double leaning, Set<String> natures, int pool, @Nullable Shop shop) {
	}

	/** A trade's shop: from what size of town it has one, the word on its sign, what it makes, sells and buys. */
	record Shop(int from, String sign, List<Make> makes, List<Item> sells, List<Item> buys) {
	}

	/** Something it makes: how many it keeps in stock (at home and on it), and how many it makes at a go. */
	record Make(Item item, int keep, int batch) {
	}

	private static final double LEANING = 0.08;
	/** Every so many people in a town past a trade's first, another of that trade (a city: three or four smiths). */
	private static final int MORE_EVERY = 30;
	/** A trade's shop is open these hours (game ticks): late morning, so it's the rest of the day to make what it sells. */
	private static final long OPENS = 2000L;
	private static final long CLOSES = 5000L;
	/** What it keeps for itself of what it sells: one of each tool, a few meals. */
	private static final int KEEP_FOOD = 8;
	/** It buys what it has fewer than this of. */
	private static final int SHORT = 32;
	private static final double BUYS_AT = 0.8;
	/** Modded gear a smith (or fletcher) makes to sell, beyond its file's list: iron tier or lower (B6 keeps diamond to "sells"). */
	private static final int GEAR_TIER = 3;

	private static Map<String, Line> lines = Map.of();

	static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ResourceLocation.fromNamespaceAndPath("drifter_civ", "drifter_lines");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		Map<String, Line> read = new LinkedHashMap<>();
		manager.listResources("drifter_lines", path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
			String name = file.getPath().substring("drifter_lines/".length(), file.getPath().length() - ".json".length());
			try (var reader = resource.openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				Set<String> goals = new java.util.HashSet<>();
				json.getAsJsonArray("goals").forEach(goal -> goals.add(goal.getAsString()));
				Set<String> natures = new java.util.HashSet<>();
				if (json.has("natures")) {
					json.getAsJsonArray("natures").forEach(nature -> natures.add(nature.getAsString().toUpperCase(Locale.ROOT)));
				}

				Shop shop = null;
				if (json.has("shop")) {
					JsonObject s = json.getAsJsonObject("shop");
					List<Make> makes = new ArrayList<>();
					s.getAsJsonArray("makes").forEach(element -> {
						JsonObject make = element.getAsJsonObject();
						makes.add(new Make(item(make.get("item")), make.get("keep").getAsInt(), make.has("batch") ? make.get("batch").getAsInt() : 1));
					});
					List<Item> sells = new ArrayList<>(makes.stream().map(Make::item).toList());
					s.getAsJsonArray("sells").forEach(element -> sells.add(item(element)));
					List<Item> buys = new ArrayList<>();
					s.getAsJsonArray("buys").forEach(element -> buys.add(item(element)));
					shop = new Shop(s.get("from").getAsInt(), s.get("sign").getAsString(), makes, sells, buys);
				}

				read.put(name, new Line(name, goals, json.has("leaning") ? json.get("leaning").getAsDouble() : LEANING, natures,
					json.has("pool") ? json.get("pool").getAsInt() : -1, shop));
			} catch (Exception e) {
				DrifterCivilizations.LOGGER.error("Couldn't read line of work {}", file, e);
			}
		});
		lines = read;
		DrifterCivilizations.LOGGER.info("[town] {} lines of work", read.size());
	}

	private static Item item(JsonElement element) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(element.getAsString()));
	}

	@Nullable
	private static Line line(String name) {
		return lines.get(name);
	}

	/** Lines filled by need, in their order when they're even. */
	private static List<String> pool() {
		return lines.values().stream().filter(line -> line.pool() >= 0).sorted(Comparator.comparingInt(Line::pool)).map(Line::name).toList();
	}

	/** How many of this trade a town of so many people has (none below its size). */
	private static int wanted(Line line, int people) {
		return line.shop() == null || people < line.shop().from() ? 0 : 1 + (people - line.shop().from()) / MORE_EVERY;
	}

	// ---------------------------------------------------------------------------------------------
	// Who does what
	// ---------------------------------------------------------------------------------------------

	/** Its line in its town: the one it has, or one given it now. */
	static String of(Town town, DrifterEntity drifter) {
		String line = town.lines.get(drifter.getUUID());
		if (line == null) {
			line = choose(town, drifter);
			town.lines.put(drifter.getUUID(), line);
			workshop(town, drifter, line);
		}

		return line;
	}

	/**
	 * A craft taken up in a town made ready: its workshop with its starting kit (B5, B6), so its services and wares can be had
	 * from the first day: an enchanter's table in its house, a few levels, lapis and books; a smith's anvil and smithing
	 * table, a few levels. Once (it has them after).
	 */
	static void workshop(Town town, DrifterEntity drifter, String line) {
		if (!town.spawned || !line.equals("enchanter") && !line.equals("smith")) {
			return;
		}

		Home home = DrifterApi.home(drifter);
		var box = home == null ? null : home.planBox();
		if (box == null || !(drifter.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}

		if (line.equals("enchanter")) {
			if (home.enchantingTable(level) != null) {
				return;
			}

			BlockPos spot = indoorSpot(level, home, box, java.util.Set.of());
			if (spot == null) {
				return;
			}

			level.setBlock(spot, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
			home.enchantingTable = spot;
			drifter.getSenses().terrain().mark(spot);
			drifter.giveExperienceLevels(10 + drifter.getRandom().nextInt(11));
			drifter.getInventory().add(new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, 12 + drifter.getRandom().nextInt(13)));
			drifter.getInventory().add(new ItemStack(net.minecraft.world.item.Items.BOOK, 3 + drifter.getRandom().nextInt(4)));
		} else {
			BlockPos anvil = indoorSpot(level, home, box, java.util.Set.of());
			if (anvil == null) {
				return;
			}

			level.setBlock(anvil, net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState(), 3);
			drifter.getSenses().terrain().mark(anvil);
			BlockPos table = indoorSpot(level, home, box, java.util.Set.of(anvil));
			if (table != null) {
				level.setBlock(table, net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState(), 3);
				drifter.getSenses().terrain().mark(table);
			}

			drifter.giveExperienceLevels(6 + drifter.getRandom().nextInt(9));
		}

		drifter.getSenses().terrain().mark(home.center);
		DrifterCivilizations.LOGGER.info("[trades] {} set up as {} in {}", drifter.getName().getString(), line, town.name);
	}

	/**
	 * A free spot in its house for a workshop's thing: the room round its bed first, as a player furnishes it (off the stairs,
	 * clear of the other furniture); else on its house's floor against a wall, under its roof and walled in each way (not on
	 * a porch or its foundation: found by the user, an enchanting table outside), nothing on it, room over it, not by a door;
	 * or null.
	 */
	@Nullable
	private static BlockPos indoorSpot(net.minecraft.server.level.ServerLevel level, Home home, net.minecraft.world.level.levelgen.structure.BoundingBox box,
		java.util.Set<BlockPos> notHere) {
		BlockPos inRoom = DrifterApi.furnitureSpot(level, home, notHere);
		if (inRoom != null) {
			return inRoom;
		}

		BlockPos bed = home.houseBed(level);
		int y = bed != null ? bed.getY() : home.center.getY();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX() + 1, y, box.minZ() + 1, box.maxX() - 1, y, box.maxZ() - 1)) {
			var below = level.getBlockState(pos.below());
			if (notHere.contains(pos) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
				|| !below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP) || !indoors(level, box, pos)) {
				continue;
			}

			boolean wall = false;
			boolean door = false;
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				var next = level.getBlockState(pos.relative(side));
				wall |= next.isFaceSturdy(level, pos.relative(side), side.getOpposite());
				door |= next.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || next.is(net.minecraft.tags.BlockTags.BEDS);
			}

			if (wall && !door) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** Under a roof (a solid block over it in the house) and walled each way along its floor, within the house. */
	private static boolean indoors(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box, BlockPos pos) {
		boolean roof = false;
		for (int up = 2; up <= 6 && pos.getY() + up <= box.maxY() && !roof; up++) {
			roof = !level.getBlockState(pos.above(up)).getCollisionShape(level, pos.above(up)).isEmpty();
		}

		if (!roof) {
			return false;
		}

		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			boolean wall = false;
			for (BlockPos at = pos.relative(side); box.isInside(at) && !wall; at = at.relative(side)) {
				wall = !level.getBlockState(at).getCollisionShape(level, at).isEmpty();
			}

			if (!wall) {
				return false;
			}
		}

		return true;
	}

	private static long count(Town town, String line) {
		return town.lines.values().stream().filter(line::equals).count();
	}

	private static int people(Town town) {
		var group = town.group == null ? null : DrifterApi.groupById(town.group);
		return group == null ? 8 : group.members.size();
	}

	private static String choose(Town town, DrifterEntity drifter) {
		UUID id = drifter.getUUID();
		if (id.equals(town.keeper)) {
			return "keeper";
		}

		if (id.equals(town.leader)) {
			return "leader";
		}

		if (count(town, "guard") < Towns.guardsWanted(town, people(town)) && DrifterApi.trait(drifter, "bravery") + DrifterApi.trait(drifter, "aggression") >= 1.1) {
			return "guard";
		}

		if (count(town, "farmer") < 2) {
			return "farmer";
		}

		int people = people(town);
		String nature = drifter.getPersonality().archetype().name();
		// A trade the town has room for and nobody's taken up yet, that suits it.
		for (Line line : lines.values()) {
			if (count(town, line.name()) < wanted(line, people) && line.natures().contains(nature)) {
				return line.name();
			}
		}

		// (By nature while its line isn't a quarter of the town already: found in a town of 24, six builders.)
		for (Line line : lines.values()) {
			if (line.pool() >= 0 && line.natures().contains(nature) && count(town, line.name()) < Math.max(2, people / 4)) {
				return line.name();
			}
		}

		// A trade the town has room for still: someone takes it up.
		for (Line line : lines.values()) {
			if (count(town, line.name()) < wanted(line, people)) {
				return line.name();
			}
		}

		Map<String, Integer> counts = new HashMap<>();
		pool().forEach(line -> counts.put(line, 0));
		town.lines.values().forEach(line -> counts.computeIfPresent(line, (k, v) -> v + 1));
		return pool().stream().min(Comparator.comparingInt(counts::get)).orElse("farmer");
	}

	/**
	 * A town's trades kept filled (its lines given before there were trades, or its smith gone): one who has a common line
	 * (the most of them first; not the last two farmers) takes the trade up, the one it suits best.
	 */
	private static boolean fill(MinecraftServer server, Town town, DrifterGroups.Group group) {
		town.lines.keySet().retainAll(group.members);
		int people = group.members.size();
		boolean changed = false;
		// Its guards: the boldest of those in a common line take it up, till there are enough (a city more of them).
		while (count(town, "guard") < Towns.guardsWanted(town, people)) {
			Map<String, Long> counts = new HashMap<>();
			town.lines.values().forEach(other -> counts.merge(other, 1L, Long::sum));
			DrifterEntity best = null;
			double bestScore = -1.0;
			for (UUID id : group.members) {
				String now = town.lines.get(id);
				Line was = now == null ? null : line(now);
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member == null || was == null || was.pool() < 0 || town.crooks.contains(id) || id.equals(town.leader) || id.equals(town.keeper)
					|| now.equals("farmer") && counts.getOrDefault("farmer", 0L) <= 2) {
					continue;
				}

				double score = DrifterApi.trait(member, "bravery") + DrifterApi.trait(member, "aggression");
				if (score > bestScore) {
					best = member;
					bestScore = score;
				}
			}

			if (best == null) {
				break;
			}

			town.lines.put(best.getUUID(), "guard");
			DrifterApi.think(best, "is one of " + town.name + "'s guards now");
			Chronicle.add(server, "town", best.getName().getString() + " joined " + town.name + "'s guard");
			changed = true;
		}

		for (Line line : lines.values()) {
			int tries = 0;
			while (count(town, line.name()) < wanted(line, people) && tries++ < people) {
				Map<String, Long> counts = new HashMap<>();
				town.lines.values().forEach(other -> counts.merge(other, 1L, Long::sum));
				DrifterEntity best = null;
				double bestScore = -1.0;
				for (UUID id : group.members) {
					String now = town.lines.get(id);
					Line was = now == null ? null : line(now);
					DrifterEntity member = TownFounding.loaded(server, id);
					// (Not one in this trade already: taking it up again changed nothing, and the town picked it over and over for
					// good, its log filling with it; found in a test once the hunter had a shop.)
					if (member == null || was == null || was.pool() < 0 || now.equals(line.name()) || now.equals("farmer") && counts.getOrDefault("farmer", 0L) <= 2) {
						continue;
					}

					double score = counts.getOrDefault(now, 0L) + (line.natures().contains(member.getPersonality().archetype().name()) ? 10.0 : 0.0);
					if (score > bestScore) {
						best = member;
						bestScore = score;
					}
				}

				if (best == null) {
					break;
				}

				town.lines.put(best.getUUID(), line.name());
				workshop(town, best, line.name());
				DrifterApi.think(best, "is taking up the " + line.name() + "'s trade in " + town.name);
				Chronicle.add(server, "town", best.getName().getString() + " took up the " + line.name() + "'s trade in " + town.name);
				changed = true;
			}
		}

		return changed;
	}

	/** How much more this goal counts for it, by its line. */
	static double leaning(Town town, DrifterEntity drifter, String goal) {
		Line line = line(of(town, drifter));
		return line != null && line.goals().contains(goal) ? line.leaning() : 0.0;
	}

	/** Who does what, for /drifterciv town: "farmer: a, b; builder: c". */
	static String describe(Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || town.lines.isEmpty()) {
			return "no lines of work yet";
		}

		Map<String, List<String>> by = new java.util.TreeMap<>();
		town.lines.forEach((id, line) -> {
			if (group.members.contains(id)) {
				by.computeIfAbsent(line, k -> new ArrayList<>()).add(group.names.getOrDefault(id, "?"));
			}
		});
		return String.join("; ", by.entrySet().stream().map(e -> e.getKey() + ": " + String.join(", ", e.getValue())).toList());
	}

	/** Its line, for talk ({@code line_<line>}, word {@code line}). */
	static void talk(@Nullable Town town, DrifterEntity drifter, Map<String, Object> words, java.util.Set<String> flags) {
		if (town == null) {
			return;
		}

		String line = of(town, drifter);
		flags.add("line_" + line);
		words.put("line", line);
	}

	/** Each check of a town: its trades filled, and each of its trade's people with its shop. True if anything changed. */
	static boolean tend(MinecraftServer server, Town town, DrifterGroups.Group group) {
		if (lines.isEmpty()) {
			return false;
		}

		boolean changed = fill(server, town, group);
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || id.equals(town.keeper)) {
				continue;
			}

			Line line = line(of(town, member));
			if (line != null && line.shop() != null) {
				if (!(member.getShop() instanceof LineShop shop && shop.town == town && shop.line == line)) {
					DrifterApi.setShop(member, new LineShop(town, line));
				}

				firstStock(member, town, line);

				((LineShop)member.getShop()).sign(member);
			} else if (member.getShop() instanceof LineShop) {
				DrifterApi.setShop(member, null);
			}
		}

		return changed;
	}

	// ---------------------------------------------------------------------------------------------
	// Making what it sells (B2)
	// ---------------------------------------------------------------------------------------------

	/** What its trade has it make now: the things it's short of in stock (at home and on it, beyond its own). */
	@Nullable
	static TownWork.Wares wares(Town town, DrifterEntity drifter) {
		Line line = line(of(town, drifter));
		Home home = DrifterApi.home(drifter);
		if (line == null || line.shop() == null || home == null) {
			return null;
		}

		List<Item> short_ = new ArrayList<>();
		int have = 0;
		int batch = 1;
		for (Make make : line.shop().makes()) {
			// (Its household's: the store's are the town stall's to sell. 0.23.33.)
			int stock = home.storedOwn(make.item()) + forSale(drifter, make.item());
			if (stock < make.keep()) {
				short_.add(make.item());
				// (What it carries of them counts toward the plan: it ends up with that many more.)
				have += drifter.getInventory().count(stack -> stack.is(make.item()));
				batch = Math.max(batch, Math.min(make.batch(), make.keep() - stock));
			}
		}

		// Modded gear it knows how to make (Drifter's gear knowledge), one of each kept.
		for (Item item : gearMakes(line, drifter)) {
			if (home.storedOwn(item) + forSale(drifter, item) < 1) {
				short_.add(item);
				have += drifter.getInventory().count(stack -> stack.is(item));
			}
		}

		return short_.isEmpty() ? null : new TownWork.Wares(short_, have + batch, line.name().equals("smith") ? "tools to sell" : "things to sell");
	}

	/**
	 * A trade's first stock, once (0.23.33: its shop sells its household's things, not the store's): the things its trade
	 * makes that are in the town's store, as many as it keeps of each, out of the store's chests into its household's own
	 * chest (a town made ready: its trades were at it before anyone came). Without it, every trade of a town just made was
	 * short of everything at once, and off to the store for the makings together.
	 */
	private static void firstStock(DrifterEntity member, Town town, Line line) {
		if (town.lineStocked.contains(member.getUUID()) || !(member.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}

		Home home = DrifterApi.home(member);
		net.minecraft.core.BlockPos own = home == null ? null : home.ownChests().stream().filter(pos -> !home.townChests.contains(pos)
			&& level.isLoaded(pos) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container).findFirst().orElse(null);
		if (own == null || town.store.stream().anyMatch(pos -> !level.isLoaded(pos))) {
			return;
		}

		town.lineStocked.add(member.getUUID());
		net.minecraft.world.Container into = (net.minecraft.world.Container)level.getBlockEntity(own);
		int moved = 0;
		for (Make make : line.shop().makes()) {
			int want = make.keep() - home.storedOwn(make.item());
			for (net.minecraft.core.BlockPos from : town.store) {
				if (want <= 0 || !(level.getBlockEntity(from) instanceof net.minecraft.world.Container store)) {
					continue;
				}

				for (int slot = 0; slot < store.getContainerSize() && want > 0; slot++) {
					ItemStack stack = store.getItem(slot);
					if (!stack.is(make.item())) {
						continue;
					}

					ItemStack taken = stack.split(Math.min(want, stack.getCount()));
					ItemStack left = put(into, taken);
					int put = taken.getCount() - left.getCount();
					want -= put;
					moved += put;
					if (!left.isEmpty()) {
						stack.grow(left.getCount());
						want = 0;
					}

					store.setChanged();
				}
			}
		}

		into.setChanged();
		DrifterApi.rememberChest(member, own);
		town.store.forEach(pos -> DrifterApi.rememberChest(member, pos));
		if (moved > 0) {
			DrifterCivilizations.LOGGER.info("[trades] {} ({}): {} things of its trade out of {}'s store into its own chest", member.getName().getString(), line.name(),
				moved, town.name);
		}
	}

	/** As much of this as fits into the container (merged into stacks of it first); what's left over. */
	private static ItemStack put(net.minecraft.world.Container into, ItemStack stack) {
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int slot = 0; slot < into.getContainerSize() && !stack.isEmpty(); slot++) {
				ItemStack there = into.getItem(slot);
				if (pass == 0 && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
					int n = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(n);
					stack.shrink(n);
				} else if (pass == 1 && there.isEmpty()) {
					into.setItem(slot, stack.copy());
					stack.setCount(0);
				}
			}
		}

		return stack;
	}

	/**
	 * The modded gear (any weapon or armor mod) a smith makes beyond its file's list (weapons and armour only: no trinkets
	 * or shields), or a fletcher's bows and crossbows: what it knows how to make (Drifter's gear knowledge, a few it likes
	 * best), iron tier or lower. None for other trades.
	 */
	private static List<Item> gearMakes(Line line, DrifterEntity drifter) {
		boolean smith = line.name().equals("smith");
		if (!smith && !line.name().equals("fletcher")) {
			return List.of();
		}

		List<Item> out = new ArrayList<>();
		for (Item item : DrifterApi.gearItCanMake(drifter, GEAR_TIER, stack -> smith ? DrifterApi.armorPoints(stack) > 0 || DrifterApi.isWeapon(stack)
			: DrifterApi.isLauncher(stack))) {
			if (!"minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()) && line.shop().makes().stream().noneMatch(make -> make.item() == item)) {
				out.add(item);
			}
		}

		return out;
	}

	/** What its shop sells: its file's list, and the modded gear it makes ({@link #gearMakes}). */
	private static List<Item> sells(Line line, DrifterEntity keeper) {
		List<Item> gear = gearMakes(line, keeper);
		if (gear.isEmpty()) {
			return line.shop().sells();
		}

		List<Item> all = new ArrayList<>(line.shop().sells());
		gear.stream().filter(item -> !all.contains(item)).forEach(all::add);
		return all;
	}

	/**
	 * One it uses itself (a tool, a weapon, a bow, a shield, any mod's: it keeps one of each kind). Not armour: what it wears
	 * is in its armour slots, not its pockets.
	 */
	private static boolean ownUse(Item item) {
		ItemStack stack = new ItemStack(item);
		return (DrifterApi.isGear(stack) && DrifterApi.armorPoints(stack) <= 0) || item instanceof ShearsItem || item instanceof FishingRodItem
			|| item instanceof TieredItem;
	}

	/**
	 * How many of these it carries to sell: all but its own (one of a tool, weapon, bow or shield, a few meals). Armour it
	 * carries is all for sale: what it wears isn't in its pockets.
	 */
	private static int forSale(DrifterEntity drifter, Item item) {
		int carried = drifter.getInventory().count(stack -> stack.is(item));
		int keep = ownUse(item) ? 1 : new ItemStack(item).has(net.minecraft.core.component.DataComponents.FOOD) ? KEEP_FOOD : 0;
		return Math.max(0, carried - keep);
	}

	// ---------------------------------------------------------------------------------------------
	// Its shop at its door (B3)
	// ---------------------------------------------------------------------------------------------

	/** A trade's shop: at its door in the mornings, selling what it made from its pockets, buying what its trade needs. */
	static final class LineShop implements ShopKeeper {
		final Town town;
		final Line line;
		@Nullable
		private BlockPos stand;
		@Nullable
		private BlockPos signAt;
		private long lookedAt = Long.MIN_VALUE / 2;
		private boolean signed;

		LineShop(Town town, Line line) {
			this.town = town;
			this.line = line;
		}

		@Override
		public boolean open(DrifterEntity keeper) {
			long time = Schedule.believedTime(keeper) % 24000L;
			return time >= OPENS && time < CLOSES;
		}

		/** Its own household's things to sell, not the town's store's (0.23.33). */
		@Override
		public boolean stocksFromTown(DrifterEntity keeper) {
			return false;
		}

		/** Its house in the town. */
		@Nullable
		private Town.Plot house(DrifterEntity keeper) {
			for (Town.Plot plot : this.town.plots) {
				if (plot.built && (keeper.getUUID().equals(plot.owner) || plot.residents.contains(keeper.getUUID()))) {
					return plot;
				}
			}

			return null;
		}

		/** Out by its door path, by the sign with its name if there is one (looked for now and then till it's found). */
		@Nullable
		@Override
		public BlockPos stand(DrifterEntity keeper) {
			long now = keeper.level().getGameTime();
			if (this.stand != null || now - this.lookedAt < 20L * 60) {
				return this.stand;
			}

			this.lookedAt = now;
			MinecraftServer server = keeper.getServer();
			Town.Plot house = this.house(keeper);
			BlockPos door = server == null || house == null ? null : DrifterApi.frontDoor(server, house.planId, house.origin, house.rotation, house.front);
			if (door == null) {
				return null;
			}

			// The sign with its name on (by its path, where it leaves the house's ground: A6), and the path beside it.
			String name = keeper.getName().getString();
			Direction front = house.front;
			Direction side = front.getClockWise();
			for (int out = 1; out <= 17 && this.signAt == null; out++) {
				for (int across = -2; across <= 2 && this.signAt == null; across++) {
					for (int dy = -4; dy <= 4; dy++) {
						BlockPos at = door.relative(front, out).relative(side, across).above(dy);
						if (keeper.level().getBlockEntity(at) instanceof SignBlockEntity sign && named(sign, name)) {
							this.signAt = at;
							break;
						}
					}
				}
			}

			if (this.signAt != null) {
				for (Direction way : Direction.Plane.HORIZONTAL) {
					BlockPos beside = standable(keeper, this.signAt.relative(way));
					if (beside != null && (this.stand == null || beside.distSqr(door) < this.stand.distSqr(door))) {
						this.stand = beside;
					}
				}
			}

			if (this.stand == null) {
				this.stand = standable(keeper, door.relative(front, 2));
			}

			return this.stand;
		}

		/** Why it has no stand (for `/drifterciv prices`). */
		String why(DrifterEntity keeper) {
			Town.Plot house = this.house(keeper);
			if (house == null) {
				return "no house in the town";
			}

			BlockPos door = DrifterApi.frontDoor(keeper.getServer(), house.planId, house.origin, house.rotation, house.front);
			return door == null ? "no front door in " + house.planId : "nowhere to stand by its door at " + door.toShortString() + " (sign " + this.signAt + ")";
		}

		/** Its sign says what it sells ("Ann's smithy"), written when it's there by it in its open hours (as a player would). */
		void sign(DrifterEntity keeper) {
			if (this.signed || this.signAt == null || !this.open(keeper) || keeper.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(this.signAt)) > 4.5) {
				return;
			}

			this.signed = true;
			if (keeper.level().getBlockEntity(this.signAt) instanceof SignBlockEntity sign) {
				String name = keeper.getName().getString();
				String owner = name.length() > 13 ? name.substring(0, 13) : name;
				var text = new net.minecraft.world.level.block.entity.SignText().setMessage(1, Component.literal(owner + "'s"))
					.setMessage(2, Component.literal(this.line.shop().sign()));
				sign.setText(text, true);
				sign.setText(text, false);
				keeper.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				DrifterApi.think(keeper, "put its " + this.line.shop().sign() + " on its sign");
			}
		}

		@Override
		public Map<Item, Integer> stock(DrifterEntity keeper) {
			Map<Item, Integer> stock = new LinkedHashMap<>();
			Home home = DrifterApi.home(keeper);
			if (home != null) {
				for (Item item : sells(this.line, keeper)) {
					// (What its household has: the town's store is the town stall's to sell.)
					int stored = home.storedOwn(item);
					if (stored > 0) {
						stock.put(item, Math.min(stored, item.getDefaultMaxStackSize() == 1 ? 3 : 32));
					}
				}
			}

			return stock;
		}

		@Override
		public List<ShopOffer> offers(DrifterEntity keeper, @Nullable Player player) {
			List<ShopOffer> offers = new ArrayList<>();
			// What the town thinks of them (D1): dearer the less it likes them; the wanted, nothing.
			double standing = TownReputation.of(this.town, player).prices;
			if (standing < 0) {
				return offers;
			}

			for (Item item : sells(this.line, keeper)) {
				ShopOffer offer = Market.trade(item, Market.price(keeper, item) * standing, forSale(keeper, item), true);
				if (offer != null && !offers.stream().anyMatch(other -> other.goods().is(item))) {
					offers.add(offer);
				}
			}

			for (Item item : this.line.shop().buys()) {
				Home home = DrifterApi.home(keeper);
				int has = (home == null ? 0 : home.stored(item)) + keeper.getInventory().count(stack -> stack.is(item));
				if (has >= SHORT) {
					continue;
				}

				ShopOffer offer = Market.trade(item, Market.price(keeper, item) * BUYS_AT, 0, false);
				if (offer != null) {
					ItemStack money = offer.goods();
					int times = keeper.getInventory().count(stack -> stack.is(money.getItem())) / money.getCount();
					if (times > 0) {
						offers.add(new ShopOffer(offer.pay(), ItemStack.EMPTY, money, times));
					}
				}
			}

			return offers;
		}

		@Override
		public void traded(DrifterEntity keeper, Player player, ShopOffer offer) {
			this.town.trades++;
			if (DrifterApi.moneyWorth(offer.goods()) <= 0) {
				Market.sold(this.town, offer.goods().getItem());
			}
		}

		@Override
		public void soldTo(DrifterEntity keeper, DrifterEntity buyer, ShopOffer offer, int times) {
			this.town.trades++;
			this.town.drifterTrades++;
		}

		/** Its takings are its own (it buys with them). */
		@Override
		public int keepOnHand() {
			return 64;
		}
	}

	/** This sign has its name on (one line of it). */
	private static boolean named(SignBlockEntity sign, String name) {
		for (Component line : sign.getFrontText().getMessages(false)) {
			if (line.getString().startsWith(name.length() > 13 ? name.substring(0, 13) : name)) {
				return true;
			}
		}

		return false;
	}

	/** Feet room at this column near this height (ground under, two clear), or null. */
	@Nullable
	private static BlockPos standable(DrifterEntity keeper, BlockPos near) {
		var level = keeper.level();
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos feet = near.above(dy);
			// (Ground under: a door path's a dirt path, not a full block; found in testing, no stand by any path.)
			if (!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return feet;
			}
		}

		return null;
	}

	/** Each trade's shop in this town, for `/drifterciv prices`: who, what it sells and buys, and for how much. */
	static List<String> shops(MinecraftServer server, Town town) {
		List<String> out = new ArrayList<>();
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return out;
		}

		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || !(member.getShop() instanceof LineShop shop)) {
				continue;
			}

			BlockPos stand = shop.stand(member);
			out.add(member.getName().getString() + "'s " + shop.line.shop().sign() + (stand == null ? " (no stand yet: " + shop.why(member) + ")" : " at " + stand.toShortString())
				+ (shop.open(member) ? ", open" : ", shut"));
			var wares = wares(town, member);
			if (wares != null) {
				out.add("  Making: " + String.join(", ", wares.items().stream().map(item -> new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT)).toList()));
			}

			Map<Item, Integer> stock = shop.stock(member);
			if (!stock.isEmpty()) {
				out.add("  At home to sell: " + String.join(", ", stock.entrySet().stream().map(e -> Market.describe(new ItemStack(e.getKey(), e.getValue()))).toList()));
			}

			for (ShopKeeper.ShopOffer offer : shop.offers(member, null)) {
				out.add("  " + Market.describe(offer.goods()) + " for " + Market.describe(offer.pay()) + " (" + offer.times() + " times)");
			}
		}

		return out;
	}
}
