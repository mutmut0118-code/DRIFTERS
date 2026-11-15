package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * More kinds of board notices (the user, 2026-11-14: "lets make some more board notices!", then "Yes, build those five"),
 * each from something true of the town: one of its workers out of what their work takes (the smith's iron, the builder's
 * stone bricks, the fletcher's feathers, the cook's ingredients...: handed to them); something made it lacks (a bucket,
 * shears, a bed...); what its festival in the days ahead wants (cakes, pies, flowers, lanterns); monsters its people saw
 * about the town in the night, to be culled; and, with bandits about (its board's warning), a bounty on them. Kill
 * notices are paid by whoever put them up, when they're found afterwards. Bigger towns have more room on the board and
 * put up more each morning.
 *
 * <p>And a town's treasure (the user: "lets make rewards compatible with artifacts (the mod) ... emphasis on rare"): now and
 * then a town's leader keeps an Artifacts item (one town in eight, a city one in three), its own from the start, and it may
 * give it for a big enough job (Rewards).
 */
final class NoticeKinds {
	private NoticeKinds() {
	}

	static final String GOODS = "goods";
	static final String WORKER = "worker";
	static final String MADE = "made";
	static final String FESTIVAL = "festival";
	static final String CULL = "cull";
	static final String BANDIT = "bandit";

	/** Room on a board, and notices put up a morning: by how big the town is. */
	private static final int ROOM = 5;
	private static final int CITY_ROOM = 6;
	private static final int CITY = 25;
	private static final int BIG_TOWN = 15;
	/** A kill counts this far past the town's land (blocks); a bandit this far from its middle. */
	private static final double CULL_REACH = 48.0;
	private static final double BANDIT_REACH = 300.0;
	/** Seen in the night: monsters a notice is put up for, at least; asked to cull, at least and at most. */
	private static final int CULL_SEEN = 1;
	private static final int CULL_LEAST = 4;
	private static final int CULL_MOST = 10;
	/** Paid by whoever put it up when they're this near and have seen them lately (ticks). */
	private static final double PAID_NEAR = 6.0;
	private static final long SEEN = 100L;
	/** A town's treasure: one in this many towns, cities. */
	private static final int TREASURE_ONE_IN = 8;
	private static final int CITY_TREASURE_ONE_IN = 3;

	/** What each line of work runs short of. */
	private static final Map<String, List<String>> NEEDS = Map.ofEntries(Map.entry("smith", List.of("minecraft:iron_ingot", "minecraft:coal", "minecraft:copper_ingot")),
		Map.entry("builder", List.of("minecraft:stone_bricks", "minecraft:oak_log", "minecraft:glass", "minecraft:cobblestone")),
		Map.entry("fletcher", List.of("minecraft:feather", "minecraft:flint", "minecraft:string", "minecraft:stick")),
		Map.entry("farmer", List.of("minecraft:bone_meal", "minecraft:sugar_cane", "minecraft:melon_seeds")),
		Map.entry("enchanter", List.of("minecraft:lapis_lazuli", "minecraft:book")),
		Map.entry("cook", List.of("farmersdelight:tomato", "farmersdelight:cabbage", "farmersdelight:onion", "farmersdelight:rice", "minecraft:egg", "minecraft:sugar")),
		Map.entry("fisher", List.of("minecraft:string", "minecraft:stick")),
		Map.entry("hunter", List.of("minecraft:arrow", "minecraft:feather", "minecraft:flint")),
		Map.entry("miner", List.of("minecraft:torch", "minecraft:coal", "minecraft:bread")),
		Map.entry("woodcutter", List.of("minecraft:bread", "minecraft:torch")),
		Map.entry("guard", List.of("minecraft:arrow", "minecraft:bread", "minecraft:cooked_beef")));

	/** Things made, and what goes into one. */
	private static final Map<Item, Map<Item, Integer>> MADE_OF = new LinkedHashMap<>();

	static {
		MADE_OF.put(Items.BUCKET, Map.of(Items.IRON_INGOT, 3));
		MADE_OF.put(Items.SHEARS, Map.of(Items.IRON_INGOT, 2));
		MADE_OF.put(Items.WHITE_BED, Map.of(Items.WHITE_WOOL, 3, Items.OAK_PLANKS, 3));
		MADE_OF.put(Items.IRON_PICKAXE, Map.of(Items.IRON_INGOT, 3, Items.STICK, 2));
		MADE_OF.put(Items.IRON_AXE, Map.of(Items.IRON_INGOT, 3, Items.STICK, 2));
		MADE_OF.put(Items.SHIELD, Map.of(Items.IRON_INGOT, 1, Items.OAK_PLANKS, 6));
		MADE_OF.put(Items.BOW, Map.of(Items.STRING, 3, Items.STICK, 3));
		MADE_OF.put(Items.FISHING_ROD, Map.of(Items.STRING, 2, Items.STICK, 3));
		MADE_OF.put(Items.LANTERN, Map.of(Items.IRON_INGOT, 1, Items.TORCH, 1));
		MADE_OF.put(Items.CHEST, Map.of(Items.OAK_PLANKS, 8));
		MADE_OF.put(Items.BARREL, Map.of(Items.OAK_PLANKS, 7));
	}

	/** What a festival wants, and how many (at least, at most). */
	private static final Map<Item, int[]> FESTIVAL_WANTS = Map.of(Items.CAKE, new int[] {1, 3}, Items.PUMPKIN_PIE, new int[] {4, 8}, Items.COOKIE, new int[] {16, 32},
		Items.POPPY, new int[] {8, 16}, Items.DANDELION, new int[] {8, 16}, Items.LANTERN, new int[] {4, 8}, Items.CANDLE, new int[] {4, 8}, Items.BREAD, new int[] {16, 32});

	/** A monster to cull: what stands for it on the board, and its bounty a head (silver). */
	private record Quarry(Item icon, double silver) {
	}

	private static final Map<String, Quarry> QUARRY = Map.ofEntries(Map.entry("minecraft:zombie", new Quarry(Items.ROTTEN_FLESH, 0.5)),
		Map.entry("minecraft:husk", new Quarry(Items.ROTTEN_FLESH, 0.5)), Map.entry("minecraft:drowned", new Quarry(Items.ROTTEN_FLESH, 0.6)),
		Map.entry("minecraft:zombie_villager", new Quarry(Items.ROTTEN_FLESH, 0.5)), Map.entry("minecraft:skeleton", new Quarry(Items.BONE, 0.7)),
		Map.entry("minecraft:stray", new Quarry(Items.BONE, 0.7)), Map.entry("minecraft:spider", new Quarry(Items.STRING, 0.6)),
		Map.entry("minecraft:cave_spider", new Quarry(Items.STRING, 0.7)), Map.entry("minecraft:creeper", new Quarry(Items.GUNPOWDER, 1.0)),
		Map.entry("minecraft:witch", new Quarry(Items.GLASS_BOTTLE, 2.0)), Map.entry("minecraft:enderman", new Quarry(Items.ENDER_PEARL, 1.5)),
		Map.entry("minecraft:slime", new Quarry(Items.SLIME_BALL, 0.4)), Map.entry("minecraft:phantom", new Quarry(Items.PHANTOM_MEMBRANE, 1.0)));

	/** A bounty on the bandits about (silver). */
	private static final double BANDIT_SILVER = 20.0;

	/** Each town: the monsters its people saw about it last night, by kind (each one once). Not saved. */
	private static final Map<UUID, Map<String, Set<Integer>>> night = new HashMap<>();

	/** What this line of work runs short of (none for others). */
	static List<Item> needs(@Nullable String line) {
		List<Item> out = new ArrayList<>();
		for (String id : line == null ? List.<String>of() : NEEDS.getOrDefault(line, List.of())) {
			Item item = item(id);
			if (item != null) {
				out.add(item);
			}
		}

		return out;
	}

	static boolean kill(TownNotices.Notice n) {
		return CULL.equals(n.kind) || BANDIT.equals(n.kind);
	}

	/** The morning's done: last night's monsters forgotten (put up or not). */
	static void nightOver(Town town) {
		night.remove(town.id);
	}

	/** Room on its board. */
	static int room(Town town) {
		return size(town) >= CITY ? CITY_ROOM : ROOM;
	}

	/** New notices a morning. */
	static int posts(Town town) {
		return size(town) >= BIG_TOWN ? 2 : 1;
	}

	private static int size(Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		return group == null ? 0 : group.members.size();
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(NoticeKinds::died);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 17) {
				pay(server);
			}

			if (server.getTickCount() % 200 == 31) {
				watch(server);
			}

			// (Townsfolk fill their purses at home: Rewards.)
			if (server.getTickCount() % 600 == 43) {
				Rewards.purses(server);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> night.clear());
		// Debug: `/drifterciv notices` lists the nearest town's notices (kind, progress, poster), `notices post` puts one up now,
		// `notices goto` goes to the poster of the first kill notice.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("drifterciv").then(net.minecraft.commands.Commands.literal("notices").requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
					List<String> lines = new ArrayList<>();
					if (town != null) {
						Map<String, Set<Integer>> seen = night.get(town.id);
						lines.add(town.name + ": room " + room(town) + ", " + posts(town) + " a morning; seen last night " + (seen == null ? "nothing" : seen.keySet())
							+ "; treasure decided " + town.treasureDecided);
						for (TownNotices.Notice n : town.notices) {
							lines.add(n.kind + ": " + TownNotices.title(n) + ", " + Prices.say(n.points) + ", by " + n.posterName + ", progress " + n.delivered.values());
						}
					}

					lines.forEach(line -> DrifterCivilizations.LOGGER.info("[notices] {}", line));
					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(String.join(" | ", lines)), false);
					return 1;
				}).then(net.minecraft.commands.Commands.literal("post").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
					boolean up = town != null && post(server, town, Chronicle.day(server), new Random());
					if (town != null) {
						treasure(server, town);
					}

					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Debug: " + (up ? "a notice went up" : "nothing to put up")), false);
					return up ? 1 : 0;
				})).then(net.minecraft.commands.Commands.literal("goto").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					for (TownNotices.Notice n : town == null ? List.<TownNotices.Notice>of() : town.notices) {
						DrifterEntity poster = kill(n) ? TownFounding.loaded(ctx.getSource().getServer(), n.poster) : null;
						if (poster != null) {
							player.teleportTo(player.serverLevel(), poster.getX() + 2.0, poster.getY(), poster.getZ(), player.getYRot(), player.getXRot());
							return 1;
						}
					}

					return 0;
				})))));
	}

	// ---------------------------------------------------------------------------------------------
	// Putting them up
	// ---------------------------------------------------------------------------------------------

	/** One new notice of some kind, if there's anything true to put up (true: one went up). */
	static boolean post(MinecraftServer server, Town town, long day, Random random) {
		List<String> kinds = new ArrayList<>(List.of(GOODS, WORKER, WORKER, MADE, FESTIVAL, CULL, BANDIT));
		Collections.shuffle(kinds, random);
		// (A festival close at hand, monsters seen, bandits about: those first, they're the news.)
		for (String first : List.of(BANDIT, CULL, FESTIVAL)) {
			kinds.remove(first);
			kinds.add(0, first);
		}

		for (String kind : kinds) {
			boolean up = switch (kind) {
				case WORKER -> worker(server, town, day, random);
				case MADE -> made(server, town, day, random);
				case FESTIVAL -> festival(server, town, day, random);
				case CULL -> cull(server, town, day, random);
				case BANDIT -> bandit(server, town, day);
				default -> TownNotices.postGoods(server, town, day);
			};
			if (up) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static Item item(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}

	private static boolean asked(Town town, String itemId) {
		return town.notices.stream().anyMatch(n -> n.item.equals(itemId) && !kill(n));
	}

	private static int has(DrifterEntity one, Item item) {
		Home home = DrifterApi.home(one);
		return (home == null ? 0 : home.stored(item)) + one.getInventory().count(stack -> stack.is(item));
	}

	/** One of its workers out of what their work takes. */
	private static boolean worker(MinecraftServer server, Town town, long day, Random random) {
		List<Map.Entry<UUID, String>> lines = new ArrayList<>(town.lines.entrySet());
		Collections.shuffle(lines, random);
		for (Map.Entry<UUID, String> line : lines) {
			List<String> needs = NEEDS.get(line.getValue());
			DrifterEntity worker = needs == null ? null : TownFounding.loaded(server, line.getKey());
			if (worker == null) {
				continue;
			}

			List<String> ids = new ArrayList<>(needs);
			Collections.shuffle(ids, random);
			for (String id : ids) {
				Item item = item(id);
				if (item == null || asked(town, id) || has(worker, item) >= 8) {
					continue;
				}

				int count = item.getDefaultMaxStackSize() < 16 ? 1 + random.nextInt(4) : 8 * (1 + random.nextInt(4));
				double points = Prices.goods(item, count, Prices.NOTICE, 1.0, Prices.NOTICE_LEAST);
				if (!TownNotices.affords(server, town, worker, points)) {
					continue;
				}

				String what = Quests.things(count, item);
				put(town, worker, WORKER, id, count, points, CivLines.pick(worker, "notice.worker." + line.getValue(), "need " + what + " for my work, paying "
					+ Prices.say(points), "what", what, "pay", Prices.say(points)), day, "");
				return true;
			}
		}

		return false;
	}

	/** Something made that it lacks: by whoever would use it (its shopkeeper, its leader, one of its workers). */
	private static boolean made(MinecraftServer server, Town town, long day, Random random) {
		List<DrifterEntity> askers = new ArrayList<>();
		for (UUID id : new UUID[] {town.keeper, town.leader}) {
			DrifterEntity one = id == null ? null : TownFounding.loaded(server, id);
			if (one != null) {
				askers.add(one);
			}
		}

		if (askers.isEmpty()) {
			return false;
		}

		DrifterEntity asker = askers.get(random.nextInt(askers.size()));
		List<Item> things = new ArrayList<>(MADE_OF.keySet());
		Collections.shuffle(things, random);
		for (Item thing : things) {
			String id = BuiltInRegistries.ITEM.getKey(thing).toString();
			if (asked(town, id) || has(asker, thing) > 0) {
				continue;
			}

			// (What goes into it, a little over a stall's price, and a little more for the making.)
			double points = 0;
			for (Map.Entry<Item, Integer> part : MADE_OF.get(thing).entrySet()) {
				points += Prices.goods(part.getKey(), part.getValue(), Prices.NOTICE, 1.0, 0.0);
			}

			points = Math.max(Prices.SILVER, points * 1.25);
			if (!TownNotices.affords(server, town, asker, points)) {
				continue;
			}

			String what = Quests.things(1, thing);
			put(town, asker, MADE, id, 1, points, CivLines.pick(asker, "notice.made", "anyone able to make " + what + "? paying " + Prices.say(points), "what", what,
				"pay", Prices.say(points)), day, "");
			return true;
		}

		return false;
	}

	/** Its festival in the days ahead: what it wants for it. */
	private static boolean festival(MinecraftServer server, Town town, long day, Random random) {
		String festival = null;
		for (int ahead = 1; ahead <= 3 && festival == null; ahead++) {
			festival = TownCulture.festival(town, day + ahead);
		}

		DrifterEntity leader = town.leader == null ? null : TownFounding.loaded(server, town.leader);
		DrifterEntity asker = leader != null ? leader : town.keeper == null ? null : TownFounding.loaded(server, town.keeper);
		if (festival == null || asker == null) {
			return false;
		}

		List<Item> wants = new ArrayList<>(FESTIVAL_WANTS.keySet());
		Collections.shuffle(wants, random);
		for (Item item : wants) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			if (asked(town, id)) {
				continue;
			}

			int[] range = FESTIVAL_WANTS.get(item);
			int count = range[0] + random.nextInt(range[1] - range[0] + 1);
			double points = Prices.goods(item, count, Prices.NOTICE, 1.0, Prices.NOTICE_LEAST);
			if (!TownNotices.affords(server, town, asker, points)) {
				continue;
			}

			String what = Quests.things(count, item);
			String name = TownCulture.name(festival);
			put(town, asker, FESTIVAL, id, count, points, CivLines.pick(asker, "notice.festival", what + " wanted for " + name + ", paying " + Prices.say(points), "what",
				what, "festival", name, "pay", Prices.say(points)), day, name);
			return true;
		}

		return false;
	}

	/** Monsters its people saw about the town in the night: culled, a bounty a head. */
	private static boolean cull(MinecraftServer server, Town town, long day, Random random) {
		Map<String, Set<Integer>> seen = night.get(town.id);
		if (seen == null || town.notices.stream().anyMatch(n -> CULL.equals(n.kind))) {
			return false;
		}

		String type = null;
		for (Map.Entry<String, Set<Integer>> one : seen.entrySet()) {
			if (QUARRY.containsKey(one.getKey()) && one.getValue().size() >= CULL_SEEN && (type == null || one.getValue().size() > seen.get(type).size())) {
				type = one.getKey();
			}
		}

		DrifterEntity asker = guardOrLeader(server, town);
		if (type == null || asker == null) {
			return false;
		}

		Quarry quarry = QUARRY.get(type);
		int count = Math.max(CULL_LEAST, Math.min(CULL_MOST, seen.get(type).size() * 2));
		double points = Math.max(2 * Prices.SILVER, count * quarry.silver() * Prices.SILVER);
		if (!TownNotices.affords(server, town, asker, points)) {
			return false;
		}

		String what = count + " " + plural(type);
		night.remove(town.id);
		put(town, asker, CULL, BuiltInRegistries.ITEM.getKey(quarry.icon()).toString(), count, points, CivLines.pick(asker, "notice.cull", plural(type)
			+ " came up to the town last night. kill " + count + " about the place, " + Prices.say(points), "what", what, "kind", plural(type), "count", count, "pay",
			Prices.say(points)), day, type);
		return true;
	}

	/** Bandits about (its board's warning): a bounty on them. */
	private static boolean bandit(MinecraftServer server, Town town, long day) {
		if (town.warning == null || town.notices.stream().anyMatch(n -> BANDIT.equals(n.kind))) {
			return false;
		}

		DrifterEntity asker = guardOrLeader(server, town);
		double points = BANDIT_SILVER * Prices.SILVER;
		if (asker == null || !TownNotices.affords(server, town, asker, points)) {
			return false;
		}

		put(town, asker, BANDIT, "minecraft:iron_sword", 1, points, CivLines.pick(asker, "notice.bandit", "a bounty on the bandits about: " + Prices.say(points)
			+ " to whoever deals with them", "pay", Prices.say(points)), day, "");
		return true;
	}

	@Nullable
	private static DrifterEntity guardOrLeader(MinecraftServer server, Town town) {
		for (Map.Entry<UUID, String> line : town.lines.entrySet()) {
			DrifterEntity guard = line.getValue().equals("guard") ? TownFounding.loaded(server, line.getKey()) : null;
			if (guard != null) {
				return guard;
			}
		}

		return town.leader == null ? null : TownFounding.loaded(server, town.leader);
	}

	private static void put(Town town, DrifterEntity poster, String kind, String item, int count, double points, String said, long day, String about) {
		String text = said + " - " + poster.getName().getString();
		TownNotices.Notice notice = new TownNotices.Notice(poster.getUUID(), poster.getName().getString(), item, count, points, text, day);
		notice.kind = kind;
		notice.about = about;
		town.notices.add(notice);
		DrifterApi.think(poster, "put up a notice: " + said);
		DrifterCivilizations.LOGGER.info("[notices] {} put up a notice in {} ({}): {}", poster.getName().getString(), town.name, kind, said);
	}

	/** "zombies", "skeletons" (a monster's name, many). */
	static String plural(String type) {
		EntityType<?> entity = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(type)).orElse(null);
		String name = entity == null ? "monster" : entity.getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		return name.endsWith("s") || name.equals("drowned") ? name : name.endsWith("ch") ? name + "es" : name.equals("enderman") ? "endermen" : name + "s";
	}

	// ---------------------------------------------------------------------------------------------
	// The night's monsters, the kills, the pay
	// ---------------------------------------------------------------------------------------------

	/** At night, every ten seconds: monsters about the town that its people see. */
	private static void watch(MinecraftServer server) {
		long time = server.overworld().getDayTime() % 24000L;
		if (time < 13000L || time > 23000L) {
			return;
		}

		for (Town town : Towns.all()) {
			DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
			if (group == null) {
				continue;
			}

			for (UUID id : group.members) {
				DrifterEntity one = TownFounding.loaded(server, id);
				if (one == null) {
					continue;
				}

				for (var seen : one.getSenses().tracked()) {
					if (seen.visibleNow && seen.entity instanceof LivingEntity monster && monster instanceof Enemy && monster.isAlive()
						&& monster.blockPosition().closerThan(town.center, town.land() + 16)) {
						String type = BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()).toString();
						night.computeIfAbsent(town.id, k -> new HashMap<>()).computeIfAbsent(type, k -> new HashSet<>()).add(monster.getId());
					}
				}
			}
		}
	}

	/** A kill by a player: toward a cull or a bandit bounty it's for. */
	private static void died(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		MinecraftServer server = victim.getServer();
		ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : victim.getKillCredit() instanceof ServerPlayer p ? p : null;
		if (server == null || player == null) {
			return;
		}

		String type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
		String dimension = victim.level().dimension().location().toString();
		// (Not one of a town's own, a crook or anyone of its people: found in review, its resident crook was bounty too.)
		boolean bandit = victim instanceof DrifterEntity drifter && DrifterApi.bandit(drifter) && com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID()) == null
			&& Towns.all().stream().noneMatch(t -> t.crooks.contains(drifter.getUUID()));
		boolean changed = false;
		// (The nearest town only: one kill isn't every town's bounty. Found in review.)
		Town nearest = null;
		double best = Double.MAX_VALUE;
		for (Town town : Towns.all()) {
			double far = Math.hypot(victim.getX() - town.center.getX(), victim.getZ() - town.center.getZ());
			if (town.dimension.equals(dimension) && far < best) {
				nearest = town;
				best = far;
			}
		}

		for (Town town : nearest == null ? List.<Town>of() : List.of(nearest)) {
			double far = best;
			for (TownNotices.Notice n : town.notices) {
				if (CULL.equals(n.kind) && n.about.equals(type) && far <= town.land() + CULL_REACH
					|| BANDIT.equals(n.kind) && bandit && far <= BANDIT_REACH) {
					int now = n.delivered.merge(player.getUUID(), 1, Integer::sum);
					n.delivered.put(player.getUUID(), Math.min(n.count, now));
					changed = true;
				}
			}
		}

		if (changed) {
			Towns.save(server);
		}
	}

	/** Kill notices done: paid by whoever put them up, when it sees them close by. And what a town owes, paid when it can. */
	private static void pay(MinecraftServer server) {
		for (Town town : Towns.all()) {
			settle(server, town);
			for (TownNotices.Notice n : List.copyOf(town.notices)) {
				if (!kill(n)) {
					continue;
				}

				// (Its poster gone for good: the town owes it, paid by its keeper or leader. Found in review.)
				var record = DrifterApi.record(n.poster);
				if (record != null && record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.GONE && owe(town, n)) {
					town.notices.remove(n);
					Towns.save(server);
					continue;
				}

				DrifterEntity poster = null;
				for (Map.Entry<UUID, Integer> done : n.delivered.entrySet()) {
					ServerPlayer player = done.getValue() >= n.count ? server.getPlayerList().getPlayer(done.getKey()) : null;
					poster = player == null ? null : poster != null ? poster : TownFounding.loaded(server, n.poster);
					long now = server.overworld().getGameTime();
					if (player == null || poster == null || !sees(poster, player, now)) {
						continue;
					}

					TownNotices.pay(server, town, poster, player, n.points);
					town.notices.remove(n);
					String title = TownNotices.title(n);
					Quests.record(server, player, "notice", "A notice filled: " + title, poster.getUUID(), n.posterName, town, "Done, and paid for.");
					Chronicle.add(server, "town", player.getGameProfile().getName() + " " + (BANDIT.equals(n.kind) ? "dealt with the bandits about " : "culled the "
						+ plural(n.about) + " about ") + town.name);
					DrifterApi.addTrustInPlayer(poster, player.getUUID(), player.getGameProfile().getName(), 10.0);
					Towns.save(server);
					break;
				}
			}
		}
	}

	/** Done and not paid (it came down, its poster's gone): owed to the first who did it (true if anyone had). */
	static boolean owe(Town town, TownNotices.Notice n) {
		if (!kill(n)) {
			return false;
		}

		for (Map.Entry<UUID, Integer> done : n.delivered.entrySet()) {
			if (done.getValue() >= n.count) {
				town.owedPlayers.merge(done.getKey(), n.points, Double::sum);
				return true;
			}
		}

		return false;
	}

	/** Close by and seen: known for who they are lately, or right in front of it in plain sight (as a quest's giver, Quests.sees). */
	private static boolean sees(DrifterEntity drifter, ServerPlayer player, long now) {
		if (drifter.level() != player.level() || drifter.distanceTo(player) > PAID_NEAR) {
			return false;
		}

		long seen = TalkApi.lastSeen(drifter, player.getUUID());
		return seen >= 0 && now - seen <= SEEN || drifter.distanceTo(player) <= 5.0 && drifter.hasLineOfSight(player);
	}

	/** What it owes someone for a notice (short of money then): its shopkeeper pays it when it sees them close by, with money. */
	private static void settle(MinecraftServer server, Town town) {
		if (town.owedPlayers.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		for (Map.Entry<UUID, Double> owed : List.copyOf(town.owedPlayers.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(owed.getKey());
			// (Its keeper, else its leader: found in review, a town with no keeper never paid.)
			DrifterEntity keeper = null;
			for (UUID id : new UUID[] {town.keeper, town.leader}) {
				DrifterEntity one = id == null || player == null ? null : TownFounding.loaded(server, id);
				if (keeper == null && one != null && sees(one, player, now) && TownNotices.affords(server, town, one, Math.min(owed.getValue(), Prices.SILVER))) {
					keeper = one;
				}
			}

			if (keeper == null) {
				continue;
			}

			town.owedPlayers.remove(owed.getKey());
			TownNotices.pay(server, town, keeper, player, owed.getValue());
			Towns.save(server);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// A town's treasure (Artifacts)
	// ---------------------------------------------------------------------------------------------

	/** Once a town: whether its leader keeps a treasure (an Artifacts item), and if so, in its pockets from now. */
	static void treasure(MinecraftServer server, Town town) {
		if (town.treasureDecided || town.leader == null) {
			return;
		}

		DrifterEntity leader = TownFounding.loaded(server, town.leader);
		if (leader == null) {
			return;
		}

		town.treasureDecided = true;
		List<Item> artifacts = artifacts();
		Random random = new Random(town.id.getMostSignificantBits() ^ 0x5eed);
		if (artifacts.isEmpty() || random.nextInt(size(town) >= CITY ? CITY_TREASURE_ONE_IN : TREASURE_ONE_IN) != 0) {
			Towns.save(server);
			return;
		}

		Item item = artifacts.get(random.nextInt(artifacts.size()));
		// (No room in its pockets: decided again another day. Found in review.)
		if (!leader.getInventory().add(new ItemStack(item))) {
			town.treasureDecided = false;
			return;
		}

		Towns.save(server);
		DrifterCivilizations.LOGGER.info("[notices] {}, leader of {}, keeps a treasure: {}", leader.getName().getString(), town.name,
			BuiltInRegistries.ITEM.getKey(item));
	}

	/** The Artifacts mod's artifacts (none without it): its items that aren't blocks or spawn eggs. */
	static List<Item> artifacts() {
		List<Item> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
			if (key.getNamespace().equals("artifacts") && !(item instanceof BlockItem) && !key.getPath().contains("spawn_egg") && !key.getPath().contains("mimic")) {
				out.add(item);
			}
		}

		return out;
	}

	/** One of the Artifacts mod's. */
	static boolean artifact(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("artifacts");
	}
}
