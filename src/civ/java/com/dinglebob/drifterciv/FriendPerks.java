package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * What a friend does for a player (D2): food when they're low, tips whispered from what it has really seen (diamonds and
 * debris only for a best friend), a warning when its town's guards are after them, favors asked of them now and then, and
 * the moments its goals act on ({@link FriendGoals}: welcome back, asking them in). Each on a setting
 * ({@link CivConfig.Friends}). Nothing from nothing: the food is its own, the tips its own memory's.
 */
final class FriendPerks {
	private FriendPerks() {
	}

	/** A friend outside near its house at night or in the rain: who, the spot inside its door, its house's plot. */
	record Invite(ServerPlayer player, BlockPos inside, Town.Plot plot) {
	}

	/** How near a friend is for food, tips and favors (blocks). */
	private static final double NEAR = 16.0;
	/** Hungry at or under this food level. */
	private static final int HUNGRY = 6;
	private static final long FOOD_GAP = 6000L;
	/** Back after this long apart: welcomed, within this long of coming back. */
	static final long WELCOME_APART = 24000L;
	private static final long WELCOME_WITHIN = 2400L;
	/** Tips about places this far at most (blocks). */
	private static final double TIP_REACH = 800.0;
	/** Favors: what each needs done, and how long it has. */
	private static final int IRON_NEEDED = 8;
	private static final int FOOD_NEEDED = 4;
	private static final int ZOMBIES_NEEDED = 3;
	private static final double ZOMBIES_REACH = 32.0;
	private static final double FAVOR_TRUST = 6.0;
	private static final double FAVOR_FAMILIARITY = 2.0;
	private static final double FLAKED_TRUST = -3.0;
	/** A friend's invite in, at most this far from its door (blocks), and the friend this near it. */
	private static final double INVITE_HOME = 32.0;
	private static final double INVITE_NEAR = 24.0;

	private static final Map<UUID, long[]> inviteChecked = new HashMap<>();
	private static final Map<UUID, Invite> inviteCache = new HashMap<>();
	private static final Map<String, Long> warned = new HashMap<>();

	static void register() {
		DrifterEvents.RECEIVED.register((drifter, player, stack) -> {
			if (player instanceof ServerPlayer p) {
				received(drifter, p, stack);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer player) {
				killed(player, entity);
			}
		});
	}

	// ----- The loop (every second, from Friends) -----

	static void tick(MinecraftServer server, long now) {
		CivConfig.Friends cfg = CivConfig.friends();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(NEAR), Friends::eligible)) {
				Friends.Bond b = Friends.bond(drifter, player);
				if (b == null || !b.friend()) {
					continue;
				}

				boolean sees = now - TalkApi.lastSeen(drifter, player.getUUID()) <= 40;
				// A favor they said yes to and didn't do.
				if (b.favor != null && now > b.favor.due()) {
					if (b.favor.promised() && sees) {
						DrifterApi.addTrustInPlayer(drifter, player.getUUID(), player.getGameProfile().getName(), FLAKED_TRUST);
						say(drifter, player, "friends.favor.flaked", false, "what", what(b.favor.kind()));
						DrifterApi.think(drifter, player.getGameProfile().getName() + " never did the favor (" + b.favor.kind() + ")");
					}

					b.favor = null;
					b.lastFavor = now;
					Friends.dirty();
				}

				if (b.favor != null && b.favor.promised() && b.favor.done() >= needed(b.favor.kind()) && sees) {
					thank(drifter, player, b);
				}

				// (Chatting with its neighbours doesn't stop it: in a town someone's nearly always talking.)
				if (!sees || drifter.getTarget() != null) {
					continue;
				}

				// Food when they're low.
				if (cfg.perkFood && player.getFoodData().getFoodLevel() <= HUNGRY && !player.isUsingItem() && now - b.lastFood > FOOD_GAP) {
					Item food = TalkApi.spareFood(drifter);
					if (food == null) {
						b.lastFood = now;
						DrifterApi.think(drifter, player.getGameProfile().getName() + " looks starved, but it's got no food to spare");
					} else {
						b.lastFood = now;
						say(drifter, player, "friends.food", false);
						TalkApi.bring(drifter, player, food, 2);
						DrifterApi.think(drifter, "giving " + player.getGameProfile().getName() + " some food, they look starved");
						continue;
					}
				}

				// A tip now and then.
				if (cfg.perkTips && now - b.lastTip > (long)(cfg.tipDays * 24000) && drifter.getRandom().nextDouble() < 0.02 && tip(drifter, player, b)) {
					continue;
				}

				// A favor now and then.
				if (cfg.favors && b.favor == null && now - b.lastFavor > (long)(cfg.favorDays * 24000) && !TalkApi.talking(drifter)
					&& drifter.getRandom().nextDouble() < 0.01) {
					offerFavor(drifter, player, b, null);
				}
			}
		}

		// Favors long gone by, wherever the friend is.
		if (now % 1200 == 0) {
			for (Friends.Bond b : Friends.all()) {
				if (b.favor != null && now > b.favor.due() + 48000L) {
					b.favor = null;
					Friends.dirty();
				}
			}
		}
	}

	/** Says a line from the chat files to them (whispered, if so and the setting allows); false if there's no such line. */
	static boolean say(DrifterEntity drifter, ServerPlayer player, String key, boolean whisper, Object... more) {
		Object[] vars = new Object[more.length + 2];
		vars[0] = "player";
		vars[1] = TalkApi.callName(drifter, player);
		System.arraycopy(more, 0, vars, 2, more.length);
		String line = TalkApi.line(drifter, key, vars);
		if (line == null) {
			return false;
		}

		if (whisper && CivConfig.friends().whisper) {
			TalkApi.whisper(drifter, player, line);
		} else {
			TalkApi.sayTo(drifter, player, line);
		}

		return true;
	}

	// ----- Welcome back -----

	/** A friend near and seen, back after a day or more apart and not yet welcomed; or null. */
	@Nullable
	static ServerPlayer welcomeDue(DrifterEntity drifter) {
		long now = Friends.now();
		for (Friends.Bond b : Friends.ofDrifter(drifter.getUUID())) {
			if (!b.friend() || b.returned < 0 || now - b.returned > WELCOME_WITHIN || b.lastWelcome >= b.returned) {
				continue;
			}

			ServerPlayer player = drifter.getServer() == null ? null : drifter.getServer().getPlayerList().getPlayer(b.player);
			if (player != null && player.level() == drifter.level() && player.distanceTo(drifter) <= 40.0
				&& now - TalkApi.lastSeen(drifter, player.getUUID()) <= 100) {
				return player;
			}
		}

		return null;
	}

	static void welcomed(DrifterEntity drifter, ServerPlayer player) {
		Friends.Bond b = Friends.bond(drifter, player);
		if (b != null) {
			b.lastWelcome = Friends.now();
			Friends.dirty();
		}
	}

	// ----- Asking them in -----

	/** Night or rain, a friend outside near and its house near: the invite; or null. Worked out at most every two seconds. */
	@Nullable
	static Invite inviteDue(DrifterEntity drifter) {
		long now = Friends.now();
		long[] checked = inviteChecked.computeIfAbsent(drifter.getUUID(), k -> new long[] {Long.MIN_VALUE / 2});
		if (now - checked[0] < 40) {
			return inviteCache.get(drifter.getUUID());
		}

		checked[0] = now;
		Invite invite = findInvite(drifter, now);
		if (invite == null) {
			inviteCache.remove(drifter.getUUID());
		} else {
			inviteCache.put(drifter.getUUID(), invite);
		}

		return invite;
	}

	@Nullable
	private static Invite findInvite(DrifterEntity drifter, long now) {
		if (!Friends.eligible(drifter) || drifter.getServer() == null || drifter.getTarget() != null) {
			return null;
		}

		ServerLevel level = ((ServerLevel)drifter.level());
		long time = level.getDayTime() % 24000L;
		boolean night = time >= 12500L && time <= 23000L;
		if (!night && !level.isRaining()) {
			return null;
		}

		Town.Plot plot = plotOf(drifter);
		if (plot == null || !plot.built) {
			return null;
		}

		BlockPos door = DrifterApi.frontDoor(drifter.getServer(), plot.planId, plot.origin, plot.rotation, plot.front);
		if (door == null || door.distSqr(drifter.blockPosition()) > INVITE_HOME * INVITE_HOME) {
			return null;
		}

		long nightId = nightId(level);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, drifter.getBoundingBox().inflate(INVITE_NEAR), p -> p.isAlive() && !p.isSpectator())) {
			Friends.Bond b = Friends.bond(drifter, player);
			if (b != null && b.friend() && b.lastInvite != nightId && level.canSeeSky(player.blockPosition().above())
				&& now - TalkApi.lastSeen(drifter, player.getUUID()) <= 100) {
				return new Invite(player, FriendGoals.inside(plot, door), plot);
			}
		}

		return null;
	}

	/** Which night (or rainy day) it is, so it asks once in it. */
	private static long nightId(ServerLevel level) {
		return (level.getDayTime() + 12000L) / 24000L;
	}

	static void invited(DrifterEntity drifter, Invite invite) {
		Friends.Bond b = Friends.bond(drifter, invite.player());
		if (b != null) {
			b.lastInvite = nightId(((ServerLevel)drifter.level()));
			Friends.dirty();
		}

		inviteCache.remove(drifter.getUUID());
		long time = drifter.level().getDayTime() % 24000L;
		boolean night = time >= 12500L && time <= 23000L;
		say(drifter, invite.player(), drifter.level().isRaining() && !night ? "friends.invite.rain" : "friends.invite.night", false);
		DrifterApi.think(drifter, "asking " + invite.player().getGameProfile().getName() + " in");
	}

	/** A bed in its house that none of its households sleeps in (more beds than households), or null. */
	@Nullable
	static BlockPos spareBed(ServerLevel level, Town.Plot plot) {
		int households = (plot.owner != null ? 1 : 0) + plot.residents.size();
		int beds = 0;
		BlockPos spare = null;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = 0; x < plot.sizeX; x++) {
			for (int z = 0; z < plot.sizeZ; z++) {
				for (int y = -1; y <= 10; y++) {
					at.set(plot.origin.getX() + x, plot.origin.getY() + y, plot.origin.getZ() + z);
					BlockState state = level.getBlockState(at);
					if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
						beds++;
						spare = at.immutable();
					}
				}
			}
		}

		return beds > households ? spare : null;
	}

	/** The plot of the house it lives in, in its town; or null. */
	@Nullable
	static Town.Plot plotOf(DrifterEntity drifter) {
		Town town = Market.townOf(drifter);
		if (town == null) {
			return null;
		}

		for (Town.Plot plot : town.plots) {
			if (drifter.getUUID().equals(plot.owner) || plot.residents.contains(drifter.getUUID())) {
				return plot;
			}
		}

		return null;
	}

	/** They live in the same house. */
	static boolean housemates(DrifterEntity a, DrifterEntity b) {
		Town.Plot plot = plotOf(a);
		return plot != null && (b.getUUID().equals(plot.owner) || plot.residents.contains(b.getUUID()));
	}

	// ----- Tips -----

	/** It tells them of a place it knows (whispered): an ore, a dungeon, a portal, a fortress, a town that hates its own, bandits. */
	static boolean tip(DrifterEntity drifter, ServerPlayer player, Friends.Bond b) {
		boolean best = b.best();
		Vec3 from = player.position();
		String dimension = player.level().dimension().location().toString();
		List<String[]> picks = new ArrayList<>();
		List<BlockPos> where = new ArrayList<>();
		for (MemoryKind kind : List.of(MemoryKind.ORE, MemoryKind.STRUCTURE, MemoryKind.LANDMARK)) {
			for (MemoryEntry entry : TalkApi.memories(drifter, kind)) {
				if (!entry.dimension.toString().equals(dimension) || entry.pos.distanceTo(from) > TIP_REACH || b.tipped.contains(entry.blockPos().asLong())
					|| entry.confidence(Friends.now()) < 0.3F) {
					continue;
				}

				String what = tipWhat(kind, entry.label, best);
				if (what != null) {
					picks.add(new String[] {what, what.equals("diamonds") || what.equals("ancient debris") ? "friends.tip.diamonds"
						: kind == MemoryKind.ORE ? "friends.tip.ore" : "friends.tip.place"});
					where.add(entry.blockPos());
				}
			}
		}

		Town town = Market.townOf(drifter);
		if (picks.isEmpty() && town != null) {
			// What its town knows: bandits about, or a town that's against it.
			if (town.warning != null && !town.warning.isEmpty()) {
				b.lastTip = Friends.now();
				Friends.dirty();
				return say(drifter, player, "friends.tip.bandits", true, "warning", town.warning);
			}

			for (Diplomacy.Relation r : Diplomacy.of(town)) {
				if (r.standing <= -25 || r.war) {
					Town other = Diplomacy.byId((r.a.equals(town.id) ? r.b : r.a).toString());
					if (other != null && !b.tipped.contains(other.center.asLong())) {
						Friends.tipped(b, other.center);
						b.lastTip = Friends.now();
						return say(drifter, player, "friends.tip.town", true, "town", other.name, "way", way(from, Vec3.atCenterOf(other.center)));
					}
				}
			}

			return false;
		}

		if (picks.isEmpty()) {
			return false;
		}

		int i = drifter.getRandom().nextInt(picks.size());
		BlockPos pos = where.get(i);
		String[] pick = picks.get(i);
		Friends.tipped(b, pos);
		b.lastTip = Friends.now();
		DrifterApi.think(drifter, "telling " + player.getGameProfile().getName() + " about " + pick[0] + " at " + pos.toShortString());
		return say(drifter, player, pick[1], true, "what", pick[0], "where", where(drifter, from, pos));
	}

	/** What a remembered thing is, as told; null if it's nothing to tell (or diamonds to someone not a best friend). */
	@Nullable
	private static String tipWhat(MemoryKind kind, String label, boolean best) {
		String l = label.toLowerCase(Locale.ROOT);
		if (kind == MemoryKind.ORE) {
			if (l.contains("diamond_ore")) {
				return best ? "diamonds" : null;
			}

			if (l.contains("ancient_debris")) {
				return best ? "ancient debris" : null;
			}

			return l.contains("emerald_ore") ? "emeralds" : l.contains("gold_ore") ? "gold" : l.contains("iron_ore") ? "iron" : l.contains("lapis_ore") ? "lapis"
				: l.contains("redstone_ore") ? "redstone" : null;
		}

		if (kind == MemoryKind.STRUCTURE) {
			return l.contains("spawner") ? "a dungeon" : l.contains("nether_portal") ? "a portal" : l.contains("end_portal_frame") ? "the stronghold" : null;
		}

		return l.equals("fortress") ? "a fortress" : null;
	}

	/** Where, as it'd type it: exact (careless or chatty ones), rounded, or a direction and a distance. */
	private static String where(DrifterEntity drifter, Vec3 from, BlockPos pos) {
		if (DrifterApi.trait(drifter, "tidiness") < 0.4 || DrifterApi.trait(drifter, "sociability") >= 0.75) {
			return pos.getX() + " " + pos.getY() + " " + pos.getZ();
		}

		if (drifter.getRandom().nextBoolean()) {
			return "like " + Math.round(pos.getX() / 10.0) * 10 + " " + Math.round(pos.getY() / 10.0) * 10 + " " + Math.round(pos.getZ() / 10.0) * 10;
		}

		return way(from, Vec3.atCenterOf(pos)) + (pos.getY() < 30 ? ", deep down" : "");
	}

	/** "about 200 blocks north-east" from here. */
	static String way(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		int distance = (int)(Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10);
		String[] words = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		double angle = Math.toDegrees(Math.atan2(-dx, dz));
		int i = (int)Math.floorMod(Math.round(angle / 45.0), 8);
		return distance < 20 ? "right round here" : "about " + distance + " blocks " + words[i];
	}

	// ----- Warnings -----

	/**
	 * They're in trouble with this town now (a crime of theirs it saw, or wanted for one elsewhere: {@code elsewhere} its
	 * name): its people who are their friends, near enough, whisper it to them. Once a day for each town.
	 */
	static void warn(MinecraftServer server, Town town, ServerPlayer player, @Nullable String elsewhere) {
		if (!CivConfig.friends().perkWarnings) {
			return;
		}

		long now = Friends.now();
		String key = town.id + "|" + player.getUUID() + "|" + (elsewhere != null);
		if (now - warned.getOrDefault(key, Long.MIN_VALUE / 2) < 24000L) {
			return;
		}

		for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(96.0),
			d -> d.isAlive() && Market.townOf(d) == town && Friends.isFriend(d, player))) {
			if (say(drifter, player, elsewhere != null ? "friends.warn.wanted_elsewhere" : "friends.warn.wanted", true, "town", elsewhere == null ? town.name : elsewhere)) {
				warned.put(key, now);
				DrifterApi.think(drifter, "warning " + player.getGameProfile().getName() + " the town's after them");
				return;
			}
		}
	}

	// ----- Favors -----

	private static final List<String> FAVORS = List.of("iron", "food", "zombies");

	/** It asks them a favor (a topic ending on a yes or no); {@code kind} null: one that suits. */
	static boolean offerFavor(DrifterEntity drifter, ServerPlayer player, Friends.Bond b, @Nullable String kind) {
		long time = drifter.level().getDayTime() % 24000L;
		List<String> kinds = new ArrayList<>(FAVORS);
		// (Zombies by its place: asked of an evening; food when it's short of it.)
		if (time < 10000L || time > 13500L) {
			kinds.remove("zombies");
		}

		if (TalkApi.spareFood(drifter) != null) {
			kinds.remove("food");
		}

		if (DrifterApi.home(drifter) == null) {
			kinds.remove("zombies");
		}

		String chosen = kind != null ? kind : kinds.isEmpty() ? null : kinds.get(drifter.getRandom().nextInt(kinds.size()));
		if (chosen == null || !TalkApi.startTalk(drifter, player, "friends_favor_" + chosen)) {
			return false;
		}

		long now = Friends.now();
		long due = switch (chosen) {
			case "zombies" -> now + (24000L - time) + 1000L;
			case "food" -> now + 24000L;
			default -> now + 48000L;
		};
		var home = DrifterApi.home(drifter);
		b.favor = new Friends.Favor(chosen, now, due, false, 0, home != null ? home.center : drifter.blockPosition());
		b.lastFavor = now;
		Friends.dirty();
		DrifterApi.think(drifter, "asking " + player.getGameProfile().getName() + " a favor (" + chosen + ")");
		return true;
	}

	/** Their yes or no to a favor it asked. */
	static void favorAnswered(DrifterEntity drifter, ServerPlayer player, String kind, boolean yes) {
		Friends.Bond b = Friends.bond(drifter, player);
		if (b == null || b.favor == null || !b.favor.kind().equals(kind)) {
			return;
		}

		b.favor = yes ? new Friends.Favor(kind, b.favor.asked(), b.favor.due(), true, b.favor.done(), b.favor.at()) : null;
		Friends.dirty();
	}

	private static int needed(String kind) {
		return switch (kind) {
			case "iron" -> IRON_NEEDED;
			case "food" -> FOOD_NEEDED;
			default -> ZOMBIES_NEEDED;
		};
	}

	private static String what(String kind) {
		return switch (kind) {
			case "iron" -> "the iron";
			case "food" -> "the food";
			default -> "the zombies";
		};
	}

	/** A player threw it something: toward a favor? */
	private static void received(DrifterEntity drifter, ServerPlayer player, ItemStack stack) {
		Friends.Bond b = Friends.bond(drifter, player);
		if (b == null || b.favor == null || !b.favor.promised()) {
			return;
		}

		boolean counts = b.favor.kind().equals("iron") ? stack.is(Items.IRON_INGOT) : b.favor.kind().equals("food") && stack.has(DataComponents.FOOD);
		if (!counts) {
			return;
		}

		b.favor = new Friends.Favor(b.favor.kind(), b.favor.asked(), b.favor.due(), true, b.favor.done() + stack.getCount(), b.favor.at());
		Friends.dirty();
		if (b.favor.done() >= needed(b.favor.kind())) {
			thank(drifter, player, b);
		}
	}

	/** They killed a monster: near the home of a friend who asked them to clear the zombies? */
	private static void killed(ServerPlayer player, Entity mob) {
		for (Friends.Bond b : Friends.of(player.getUUID())) {
			if (b.favor != null && b.favor.promised() && b.favor.kind().equals("zombies") && b.favor.at() != null
				&& mob.blockPosition().distSqr(b.favor.at()) <= ZOMBIES_REACH * ZOMBIES_REACH) {
				b.favor = new Friends.Favor("zombies", b.favor.asked(), b.favor.due(), true, b.favor.done() + 1, b.favor.at());
				Friends.dirty();
			}
		}
	}

	/** The favor done: closer, and it says thanks (its own pay-back for gifts does the rest). */
	private static void thank(DrifterEntity drifter, ServerPlayer player, Friends.Bond b) {
		String kind = b.favor.kind();
		b.favor = null;
		b.lastFavor = Friends.now();
		String name = player.getGameProfile().getName();
		DrifterApi.addTrustInPlayer(drifter, player.getUUID(), name, FAVOR_TRUST);
		TalkApi.addFamiliarity(drifter, player.getUUID(), name, FAVOR_FAMILIARITY);
		say(drifter, player, "friends.favor.thanks." + kind, false);
		DrifterApi.think(drifter, name + " did the favor (" + kind + ")");
		Friends.dirty();
	}

	// ----- Debug -----

	static LiteralArgumentBuilder<CommandSourceStack> tipCommand() {
		return Commands.literal("tip").requires(source -> source.hasPermission(2)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			DrifterEntity drifter = Friends.friendNear(player);
			Friends.Bond b = drifter == null ? null : Friends.bond(drifter, player);
			boolean told = b != null && b.friend() && tip(drifter, player, b);
			ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + (told ? "a tip" : "nothing to tell (a friend? anything it's seen?)")), false);
			return told ? 1 : 0;
		});
	}

	/** Debug: whether its invite is due now, and if not why not; {@code home}: it and you put by its front door first. */
	static LiteralArgumentBuilder<CommandSourceStack> inviteCommand() {
		return Commands.literal("invite").requires(source -> source.hasPermission(2))
			.then(Commands.literal("home").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				DrifterEntity drifter = Friends.friendNear(player);
				Town.Plot plot = drifter == null ? null : plotOf(drifter);
				BlockPos door = plot == null || drifter.getServer() == null ? null : DrifterApi.frontDoor(drifter.getServer(), plot.planId, plot.origin, plot.rotation, plot.front);
				if (door == null) {
					ctx.getSource().sendFailure(Component.literal("No friend with a house near"));
					return 0;
				}

				BlockPos out = door.relative(plot.front, 4);
				drifter.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
				player.teleportTo(out.getX() + 1.5, out.getY(), out.getZ() + 1.5);
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: by " + drifter.getName().getString() + "'s door at " + door.toShortString()), false);
				return 1;
			}))
			.executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			DrifterEntity drifter = Friends.friendNear(player);
			String why;
			if (drifter == null) {
				why = "no friend near";
			} else {
				Town.Plot plot = plotOf(drifter);
				BlockPos door = plot == null || drifter.getServer() == null ? null : DrifterApi.frontDoor(drifter.getServer(), plot.planId, plot.origin, plot.rotation, plot.front);
				long time = drifter.level().getDayTime() % 24000L;
				why = drifter.getName().getString() + ": " + (plot == null ? "no house in a town" : !plot.built ? "house not up"
					: door == null ? "no door found" : "door at " + door.toShortString() + ", " + (int)Math.sqrt(door.distSqr(drifter.blockPosition())) + " blocks off")
					+ "; time " + time + (drifter.level().isRaining() ? " (rain)" : "") + "; you outside: " + drifter.level().canSeeSky(player.blockPosition().above())
					+ "; due: " + (findInvite(drifter, Friends.now()) != null) + (plot != null && drifter.level() instanceof ServerLevel level ? "; spare bed: " + spareBed(level, plot) : "");
			}

			String shown = why;
			ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + shown), false);
			return 1;
		});
	}

	/** Debug: what's in your hand to your friend near, as if thrown. */
	static LiteralArgumentBuilder<CommandSourceStack> handCommand() {
		return Commands.literal("hand").requires(source -> source.hasPermission(2)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			DrifterEntity drifter = Friends.friendNear(player);
			ItemStack held = player.getMainHandItem().copy();
			if (drifter == null || held.isEmpty()) {
				ctx.getSource().sendFailure(Component.literal("Nothing in your hand, or no friend near"));
				return 0;
			}

			player.getMainHandItem().setCount(0);
			drifter.getInventory().add(held.copy());
			DrifterEvents.RECEIVED.invoker().onReceived(drifter, player, held);
			ctx.getSource().sendSuccess(() -> Component.literal("Debug: handed " + held.getCount() + " " + held.getHoverName().getString() + " to "
				+ drifter.getName().getString()), false);
			return 1;
		});
	}

	static LiteralArgumentBuilder<CommandSourceStack> favorCommand() {
		return Commands.literal("favor").requires(source -> source.hasPermission(2))
			.then(Commands.argument("kind", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FAVORS, builder))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					DrifterEntity drifter = Friends.friendNear(player);
					Friends.Bond b = drifter == null ? null : Friends.bond(drifter, player);
					String kind = StringArgumentType.getString(ctx, "kind");
					boolean asked = b != null && b.friend() && FAVORS.contains(kind) && offerFavor(drifter, player, b, kind);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + (asked ? "favor asked (" + kind + ")" : "no favor (a friend? iron, food or zombies?)")),
						false);
					return asked ? 1 : 0;
				}));
	}
}
