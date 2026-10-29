package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BannerBlock;

/**
 * Nations (milestone 22 step 6, part 3; docs/CIVILIZATION.md "Towns join into nations (by agreement ... or conquest)",
 * "tribute", "rebellions and independence", "flags and banners for towns and nations"):
 * <ul>
 *   <li>Joining: two towns allied a few days and on the best of terms: the bigger one's leader (or a nation's capital's)
 *       sends an envoy proposing they join under it ("UNION"). A town winning a war by a long way may demand the other
 *       submits to it instead ("SUBMIT"): conquered, it joins resentful.</li>
 *   <li>A nation: a capital (its leader rules), its other towns (their own leaders and laws still), a name by how it came
 *       about and its ruler's nature, and a colour: each of its towns' leaders makes a banner of it and stands it on the
 *       plaza.</li>
 *   <li>Its towns never fight each other; a war on one is a war on all of them, and only the capital starts wars.</li>
 *   <li>Tribute: every few days each town owes the capital emeralds (more if conquered), paid from its till into the
 *       capital's as the emeralds are there (real ones, never made).</li>
 *   <li>Rebellion: tribute, being dragged into the capital's wars and dying in them wear a town's standing with the
 *       capital down. Low enough, and its leader bold or ambitious, it declares itself free ("INDEPENDENCE"): a careful
 *       ruler lets it go; an aggressive one goes to war to keep it, and whoever has to ask for peace loses: the rebels
 *       stay (conquered) or go free.</li>
 * </ul>
 * Kept with the towns.
 */
final class Nations {
	private Nations() {
	}

	static final class Member {
		final UUID town;
		final long joinedDay;
		boolean conquered;
		long tributeDay;
		int owed;
		int inTransit;
		/** At war with its capital to be free of it. */
		boolean rebel;

		Member(UUID town, long joinedDay) {
			this.town = town;
			this.joinedDay = joinedDay;
		}
	}

	static final class Nation {
		final UUID id;
		String name;
		UUID capital;
		final DyeColor colour;
		final long foundedDay;
		final Map<UUID, Member> members = new LinkedHashMap<>();
		/** Where each of its towns stood its banner (and where it's to go). */
		final Map<UUID, BlockPos> banners = new LinkedHashMap<>();
		final Map<UUID, BlockPos> bannerSpots = new LinkedHashMap<>();
		/** The latest about it, for its people's talk: "formed", "joined", "submitted", "rebelled", "free", "kept", "banner"; the
		 * town it was about, and the day. */
		String news = "";
		String newsTown = "";
		long newsDay = -100;

		Nation(UUID id, String name, UUID capital, DyeColor colour, long foundedDay) {
			this.id = id;
			this.name = name;
			this.capital = capital;
			this.colour = colour;
			this.foundedDay = foundedDay;
		}

		Item banner() {
			return BannerBlock.byColor(this.colour).asItem();
		}

		boolean has(UUID town) {
			return this.capital.equals(town) || this.members.containsKey(town);
		}

		List<UUID> towns() {
			List<UUID> all = new ArrayList<>();
			all.add(this.capital);
			all.addAll(this.members.keySet());
			return all;
		}
	}

	/** Allied this many days, on terms this good: ready to join. */
	private static final long ALLIED_FOR = 3;
	private static final double FOR_UNION = 75.0;
	/** Winning a war by this many dead, this many days in: an aggressive leader may demand the other submit. */
	private static final int FOR_SUBMIT = 3;
	private static final long SUBMIT_AFTER = 3;
	/** Tribute: every so many days, so many emeralds (a conquered town pays more). */
	private static final long TRIBUTE_EVERY = 5;
	private static final int TRIBUTE = 3;
	private static final int TRIBUTE_CONQUERED = 5;
	/** What paying it does to a town's standing with the capital (a conquered one minds it more). */
	private static final double TRIBUTE_GRATES = -3.0;
	private static final double TRIBUTE_GRATES_CONQUERED = -5.0;
	/** Dragged into the capital's war; each of its people dead in a war while in the nation. */
	private static final double DRAGGED_IN = -6.0;
	private static final double DIED_FOR_IT = -2.0;
	/** A town this badly off with its capital, and a bold or ambitious leader: it declares itself free. */
	private static final double REBELS = -30.0;
	/** Where a conquered town (or rebels brought back) starts with its capital. */
	private static final double CONQUERED = -10.0;

	private static final Map<UUID, Nation> nations = new LinkedHashMap<>();
	/** Each town's day it last saw to its part (not saved). */
	private static final Map<UUID, Long> doneDay = new java.util.HashMap<>();

	static List<Nation> all() {
		return List.copyOf(nations.values());
	}

	@Nullable
	static Nation of(Town town) {
		return of(town.id);
	}

	@Nullable
	static Nation of(UUID town) {
		for (Nation nation : nations.values()) {
			if (nation.has(town)) {
				return nation;
			}
		}

		return null;
	}

	/** In the same nation. */
	static boolean fellows(Town x, Town y) {
		Nation nation = of(x);
		return nation != null && nation.has(y.id);
	}

	static boolean capital(Town town) {
		Nation nation = of(town);
		return nation != null && nation.capital.equals(town.id);
	}

	@Nullable
	static Member member(Town town) {
		Nation nation = of(town);
		return nation == null ? null : nation.members.get(town.id);
	}

	// ---------------------------------------------------------------------------------------------
	// Joining and leaving
	// ---------------------------------------------------------------------------------------------

	/** {@code town} joins {@code head}'s nation (a new one, {@code head} its capital, if it has none). */
	static Nation join(MinecraftServer server, Town head, Town town, boolean conquered) {
		long day = Chronicle.day(server);
		Nation nation = of(head);
		if (nation == null) {
			DrifterEntity ruler = head.leader == null ? null : TownFounding.loaded(server, head.leader);
			nation = new Nation(UUID.randomUUID(), name(server, head, ruler, conquered), head.id, colour(server), day);
			nations.put(nation.id, nation);
			nation.news = "formed";
			Chronicle.add(server, "nation", head.name + " and " + town.name + " joined together as " + nation.name + ", with " + head.name + " its capital ("
				+ nation.colour.getName().replace('_', ' ') + " its colour)");
		} else {
			nation.news = conquered ? "submitted" : "joined";
			Chronicle.add(server, "nation", town.name + (conquered ? " submitted to " : " joined ") + nation.name);
		}

		Member member = new Member(town.id, day);
		member.conquered = conquered;
		member.tributeDay = day + TRIBUTE_EVERY;
		nation.members.put(town.id, member);
		nation.newsTown = town.name;
		nation.newsDay = day;
		// Its towns: at peace and allied with each other from now on.
		for (UUID one : nation.towns()) {
			Town other = Diplomacy.byId(one.toString());
			if (other != null && other != town) {
				var r = Diplomacy.meet(server, town, other, 0.0, null);
				r.pacts.add(Diplomacy.Pact.PEACE);
				r.pacts.add(Diplomacy.Pact.ALLIANCE);
				if (other == head && conquered) {
					r.standing = Math.min(r.standing, CONQUERED);
				}
			}
		}

		Towns.save(server);
		return nation;
	}

	/** {@code town} is out of its nation (free, or the nation no more with only its capital left). */
	static void leave(MinecraftServer server, Town town, String how) {
		Nation nation = of(town);
		if (nation == null || nation.capital.equals(town.id)) {
			return;
		}

		long day = Chronicle.day(server);
		nation.members.remove(town.id);
		// (At peace with its old fellows still, but no longer their ally.)
		for (UUID id : nation.towns()) {
			Town other = Diplomacy.byId(id.toString());
			var r = other == null ? null : Diplomacy.relation(town, other);
			if (r != null) {
				r.pacts.remove(Diplomacy.Pact.ALLIANCE);
			}
		}

		nation.banners.remove(town.id);
		nation.bannerSpots.remove(town.id);
		nation.news = "free";
		nation.newsTown = town.name;
		nation.newsDay = day;
		Chronicle.add(server, "nation", town.name + " " + how);
		if (nation.members.isEmpty()) {
			nations.remove(nation.id);
			Chronicle.add(server, "nation", nation.name + " is no more");
		}

		Towns.save(server);
	}

	/** Its name: by how it came about and its ruler's nature. */
	private static String name(MinecraftServer server, Town capital, @Nullable DrifterEntity ruler, boolean conquest) {
		var random = server.overworld().getRandom();
		double aggression = ruler == null ? 0.5 : DrifterApi.trait(ruler, "aggression");
		double warmth = ruler == null ? 0.5 : DrifterApi.trait(ruler, "sociability");
		String[] forms = conquest || aggression >= 0.65 ? new String[] {"the %s Dominion", "the Realm of %s", "the %s Dominion", "the Crown of %s"}
			: warmth >= 0.6 ? new String[] {"the %s League", "the %s Accord", "the %s League", "the Union of %s"}
			: new String[] {"the Kingdom of %s", "the %s Union", "the Lands of %s", "the %s Union"};
		String name = String.format(java.util.Locale.ROOT, forms[random.nextInt(forms.length)], capital.name);
		return nations.values().stream().anyMatch(n -> n.name.equals(name)) ? "the Greater " + capital.name + " Union" : name;
	}

	/**
	 * Colours a nation can have: each made with one common flower's dye (its banner's wool dyed with it by whoever makes
	 * it), white last (plain wool: it's everyone's).
	 */
	private static final List<DyeColor> COLOURS = List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.MAGENTA,
		DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.LIGHT_GRAY);

	/** A colour no other nation has. */
	private static DyeColor colour(MinecraftServer server) {
		List<DyeColor> free = COLOURS.stream().filter(colour -> nations.values().stream().noneMatch(n -> n.colour == colour)).toList();
		return free.isEmpty() ? DyeColor.WHITE : free.get(server.overworld().getRandom().nextInt(free.size()));
	}

	// ---------------------------------------------------------------------------------------------
	// What its leaders send (Diplomacy.wants asks first)
	// ---------------------------------------------------------------------------------------------

	/** What this town's leader would send the other for its nation, or null (then the usual diplomacy). */
	@Nullable
	static String wants(MinecraftServer server, Town town, Town other, Diplomacy.Relation r, @Nullable DrifterEntity leader, long day) {
		Nation mine = of(town);
		Nation theirs = of(other);
		// Its capital, gone too far: free of it, if it's bold enough to say so.
		if (mine != null && mine == theirs && other.id.equals(mine.capital) && !r.war && r.standing <= REBELS
			&& (trait(leader, "aggression") >= 0.5 || leader != null && Politics.ambition(leader) >= 0.6)) {
			return "INDEPENDENCE";
		}

		// At war and winning by far: submit to it (a conqueror's demand), from a town not in a nation of its own already.
		if (r.war && (mine == null || mine.capital.equals(town.id)) && theirs == null && r.losses(other.id) >= r.losses(town.id) + FOR_SUBMIT
			&& day - r.warSinceDay >= SUBMIT_AFTER && trait(leader, "aggression") >= 0.6) {
			return "SUBMIT";
		}

		// An ally on the best of terms for a while, in no nation: join under it (the bigger town proposes, or a capital).
		if (!r.war && theirs == null && (mine == null || mine.capital.equals(town.id)) && r.pacts.contains(Diplomacy.Pact.ALLIANCE)
			&& r.standing >= FOR_UNION && day - r.alliedSinceDay >= ALLIED_FOR && (mine != null || size(town) >= size(other))) {
			return "UNION";
		}

		return null;
	}

	/** Its people (its group's size). */
	static int size(Town town) {
		var group = town.group == null ? null : DrifterApi.groupById(town.group);
		return group == null ? 0 : group.members.size();
	}

	/**
	 * The other town's answer to one of these (true if it was one): {@code from}'s envoy at {@code to}, {@code met} who heard
	 * it.
	 */
	static boolean answer(MinecraftServer server, Diplomacy.Mission mission, Town from, Town to, Diplomacy.Relation r, @Nullable DrifterEntity met) {
		DrifterEntity theirLeader = to.leader == null ? null : TownFounding.loaded(server, to.leader);
		String fromLeader = Diplomacy.name(from, from.leader);
		var random = server.overworld().getRandom();
		switch (mission.kind) {
			case "UNION" -> {
				if (of(to) != null || r.war) {
					return true;
				}

				// Sociable leaders join up; ambitious ones would rather not answer to anyone; the smaller town more readily.
				double lean = -0.25 + 0.3 * (trait(theirLeader, "sociability") - 0.5) - 0.3 * ((theirLeader == null ? 0.5 : Politics.ambition(theirLeader)) - 0.5)
					+ (size(to) < size(from) ? 0.2 : 0.0) + r.standing / 100.0;
				boolean yes = random.nextDouble() < lean;
				if (yes) {
					join(server, from, to, false);
				} else {
					Diplomacy.incident(server, from, to, -3.0, "refused");
					Chronicle.add(server, "nation", to.name + " turned down joining " + (of(from) != null ? of(from).name : from.name));
				}

				speak(met, yes ? "envoy.union_yes" : "envoy.union_no", fromLeader, from, to);
				return true;
			}
			case "SUBMIT" -> {
				if (!r.war) {
					return true;
				}

				// Weary and careful, it gives in; aggressive, it fights on.
				double lean = -0.3 + 0.3 * trait(theirLeader, "caution") - 0.3 * trait(theirLeader, "aggression")
					+ Diplomacy.weariness(to, r, theirLeader, Chronicle.day(server)) / 100.0;
				boolean yes = random.nextDouble() < lean;
				if (yes) {
					Diplomacy.peace(server, from, to, r, null, 0);
					join(server, from, to, true);
				} else {
					Chronicle.add(server, "war", to.name + " would not submit to " + from.name + ": the war goes on");
				}

				speak(met, yes ? "envoy.submit_yes" : "envoy.submit_no", fromLeader, from, to);
				return true;
			}
			case "INDEPENDENCE" -> {
				Nation nation = of(from);
				Member member = nation == null ? null : nation.members.get(from.id);
				if (member == null || !nation.capital.equals(to.id) || r.war) {
					return true;
				}

				// The ruler: aggressive (or greedy for its tribute), it won't let it go; else, it's free.
				boolean keep = trait(theirLeader, "aggression") >= 0.55 || trait(theirLeader, "greed") >= 0.75;
				nation.news = "rebelled";
				nation.newsTown = from.name;
				nation.newsDay = Chronicle.day(server);
				if (keep) {
					member.rebel = true;
					Chronicle.add(server, "nation", from.name + " rose up against " + nation.name + ": " + to.name + " won't let it go");
					Diplomacy.begin(server, to, from, r, Chronicle.day(server), "war");
					Chronicle.add(server, "war", to.name + " went to war with " + from.name + " to keep it in " + nation.name);
					// (The nation's other towns: with their capital.)
					war(server, to, from, Chronicle.day(server));
				} else {
					leave(server, from, "left " + nation.name + ", free again (" + to.name + " let it go)");
				}

				speak(met, keep ? "envoy.independence_war" : "envoy.independence_go", fromLeader, from, to);
				Towns.save(server);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private static void speak(@Nullable DrifterEntity met, String key, String fromLeader, Town from, Town to) {
		if (met != null) {
			Nation nation = of(from) != null ? of(from) : of(to);
			com.dinglebob.drifter.social.ChatLines.speak(met, line(met, key, "leader", fromLeader, "town", from.name, "them", to.name, "nation",
				nation == null ? from.name : nation.name));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// War and peace
	// ---------------------------------------------------------------------------------------------

	/** A war between two of the same nation: its capital and rebels. */
	static boolean rebellion(Town x, Town y) {
		Nation nation = of(x);
		if (nation == null || !nation.has(y.id)) {
			return false;
		}

		Member mx = nation.members.get(x.id);
		Member my = nation.members.get(y.id);
		return mx != null && mx.rebel || my != null && my.rebel;
	}

	/**
	 * A war begun between {@code attacker} and {@code defender}: each one's fellows in a nation are in it too (no staying
	 * out), resenting it if it was the capital's war they were dragged into. Not for a rebellion (the capital's other towns
	 * side with it then). Returns the towns brought in (Diplomacy's allies leave those be).
	 */
	static List<Town> war(MinecraftServer server, Town attacker, Town defender, long day) {
		List<Town> in = new ArrayList<>();
		if (fellows(attacker, defender)) {
			Nation nation = of(attacker);
			Town capital = Diplomacy.byId(nation.capital.toString());
			Town rebel = capital == attacker ? defender : attacker;
			for (UUID id : nation.towns()) {
				Town fellow = Diplomacy.byId(id.toString());
				if (fellow != null && fellow != capital && fellow != rebel) {
					bringIn(server, fellow, rebel, day, nation, in);
				}
			}

			return in;
		}

		for (Town side : new Town[] {attacker, defender}) {
			Nation nation = of(side);
			Town enemy = side == attacker ? defender : attacker;
			if (nation == null) {
				continue;
			}

			for (UUID id : nation.towns()) {
				Town fellow = Diplomacy.byId(id.toString());
				if (fellow != null && fellow != side && fellow != enemy) {
					bringIn(server, fellow, enemy, day, nation, in);
				}
			}
		}

		return in;
	}

	private static void bringIn(MinecraftServer server, Town fellow, Town enemy, long day, Nation nation, List<Town> in) {
		var against = Diplomacy.meet(server, fellow, enemy, 0.0, null);
		in.add(fellow);
		if (against.war) {
			return;
		}

		Chronicle.add(server, "war", fellow.name + " went to war with " + enemy.name + " for " + nation.name);
		Diplomacy.begin(server, fellow, enemy, against, day, "war");
		// (Not its war: it minds being dragged in.)
		Town capital = Diplomacy.byId(nation.capital.toString());
		if (capital != null && capital != fellow) {
			Diplomacy.incident(server, fellow, capital, DRAGGED_IN, null);
		}
	}

	/**
	 * A peace made between {@code x} and {@code y}, {@code payer} the side that asked for it (or was made to pay): in a
	 * rebellion, the rebels lose if they asked (back in, conquered), and go free if the capital did (or nobody did).
	 */
	static void peace(MinecraftServer server, Town x, Town y, @Nullable Town payer) {
		if (!rebellion(x, y)) {
			return;
		}

		Nation nation = of(x);
		Town rebel = nation.members.get(x.id) != null && nation.members.get(x.id).rebel ? x : y;
		Town capital = rebel == x ? y : x;
		Member member = nation.members.get(rebel.id);
		member.rebel = false;
		if (payer == rebel) {
			member.conquered = true;
			nation.news = "kept";
			nation.newsTown = rebel.name;
			nation.newsDay = Chronicle.day(server);
			var r = Diplomacy.relation(rebel, capital);
			if (r != null) {
				r.standing = CONQUERED;
				r.pacts.add(Diplomacy.Pact.ALLIANCE);
			}

			Chronicle.add(server, "nation", rebel.name + "'s rising was put down: it stays in " + nation.name);
		} else {
			leave(server, rebel, "won its freedom from " + nation.name);
		}

		Towns.save(server);
	}

	/** One of a town's people died in a war: it minds dying for the nation (not its capital). */
	static void fell(MinecraftServer server, Town town) {
		Nation nation = of(town);
		Member member = nation == null ? null : nation.members.get(town.id);
		Town capital = nation == null ? null : Diplomacy.byId(nation.capital.toString());
		if (member != null && !member.rebel && capital != null) {
			Diplomacy.incident(server, town, capital, DIED_FOR_IT, null);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Each check: tribute, banners
	// ---------------------------------------------------------------------------------------------

	static void check(MinecraftServer server, Town town) {
		Nation nation = of(town);
		if (nation == null) {
			return;
		}

		Member member = nation.members.get(town.id);
		Town capital = Diplomacy.byId(nation.capital.toString());
		if (capital == null) {
			return;
		}

		long day = Chronicle.day(server);
		if (member != null && doneDay.getOrDefault(town.id, -1L) != day) {
			doneDay.put(town.id, day);
			// Tribute due (not from rebels): owed, and it grates.
			if (!member.rebel && day >= member.tributeDay) {
				member.tributeDay = day + TRIBUTE_EVERY;
				member.owed += member.conquered ? TRIBUTE_CONQUERED : TRIBUTE;
				Diplomacy.incident(server, town, capital, member.conquered ? TRIBUTE_GRATES_CONQUERED : TRIBUTE_GRATES, null);
				Towns.save(server);
			}
		}

		// Paid out of its till as the emeralds are there, into the capital's.
		if (member != null && member.owed > 0) {
			int taken = Diplomacy.till(server, town, -member.owed);
			if (taken > 0) {
				member.owed -= taken;
				member.inTransit += taken;
				Chronicle.add(server, "nation", town.name + " paid " + taken + " emeralds in tribute to " + capital.name + (member.owed > 0 ? " (" + member.owed
					+ " still owed)" : ""));
				Towns.save(server);
			}
		}

		if (town == capital) {
			for (Member one : nation.members.values()) {
				if (one.inTransit > 0) {
					int put = Diplomacy.till(server, capital, one.inTransit);
					if (put > 0) {
						one.inTransit -= put;
						Towns.save(server);
					}
				}
			}
		}
	}

	/** The banner its town's bearer is to make and stand on the plaza (asked of each of its people), or null. */
	@Nullable
	static TownWork.Banner banner(Town town, DrifterEntity drifter) {
		Nation nation = of(town);
		if (nation == null || !drifter.getUUID().equals(bearer(town))) {
			return null;
		}

		var level = drifter.level();
		BlockPos up = nation.banners.get(town.id);
		if (up != null && (!level.isLoaded(up) || level.getBlockState(up).getBlock().asItem() == nation.banner())) {
			return null;
		}

		BlockPos spot = nation.bannerSpots.get(town.id);
		if (spot == null || !level.isLoaded(spot) || !free(level, spot)) {
			spot = spotFor(level, town);
			if (spot == null) {
				return null;
			}

			nation.bannerSpots.put(town.id, spot);
		}

		return new TownWork.Banner(nation.banner(), spot, "the banner of " + nation.name);
	}

	/** Who makes its banner: its leader, unless it keeps the stall (then the first of its people who doesn't, not a crook). */
	@Nullable
	private static UUID bearer(Town town) {
		if (town.leader != null && !town.leader.equals(town.keeper)) {
			return town.leader;
		}

		var group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return null;
		}

		return group.members.stream().filter(id -> !id.equals(town.keeper) && !town.crooks.contains(id)).findFirst().orElse(null);
	}

	static void bannerUp(MinecraftServer server, Town town, DrifterEntity drifter, BlockPos where) {
		Nation nation = of(town);
		if (nation == null) {
			return;
		}

		nation.banners.put(town.id, where.immutable());
		nation.news = "banner";
		nation.newsTown = town.name;
		nation.newsDay = Chronicle.day(server);
		Chronicle.add(server, "nation", drifter.getName().getString() + " raised the banner of " + nation.name + " over " + town.name);
		Towns.save(server);
	}

	/** On the plaza, a few blocks from its middle: open ground, nothing in the way. */
	@Nullable
	private static BlockPos spotFor(net.minecraft.world.level.Level level, Town town) {
		for (int ring = 3; ring <= 7; ring++) {
			for (Direction way : Direction.Plane.HORIZONTAL) {
				for (int side = -1; side <= 1; side += 2) {
					BlockPos at = town.center.relative(way, ring).relative(way.getClockWise(), side * 2);
					if (!level.isLoaded(at)) {
						continue;
					}

					BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
					if (Math.abs(top.getY() - town.center.getY()) <= 2 && free(level, top)) {
						return top;
					}
				}
			}
		}

		return null;
	}

	private static boolean free(net.minecraft.world.level.Level level, BlockPos spot) {
		// (On anything solid: a plaza's paths are, though they're not quite a full block.)
		return level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && level.getBlockState(spot.below()).isSolid();
	}

	// ---------------------------------------------------------------------------------------------
	// Talk
	// ---------------------------------------------------------------------------------------------

	/**
	 * What its people know of their nation to talk about: in one ({@code in_nation}; words {@code nation}, {@code capital}),
	 * its capital ({@code nation_capital}), conquered ({@code nation_conquered}), and the latest about it lately
	 * ({@code nation_formed}, {@code nation_joined}, {@code nation_submitted}, {@code nation_rebelled}, {@code nation_free},
	 * {@code nation_kept}, {@code nation_banner}; word {@code other}).
	 */
	static void talk(Town town, long day, Map<String, Object> words, java.util.Set<String> flags) {
		Nation nation = of(town);
		if (nation == null) {
			return;
		}

		Town capital = Diplomacy.byId(nation.capital.toString());
		flags.add("in_nation");
		words.put("nation", nation.name);
		words.put("capital", capital == null ? town.name : capital.name);
		if (nation.capital.equals(town.id)) {
			flags.add("nation_capital");
		}

		Member member = nation.members.get(town.id);
		if (member != null && member.conquered) {
			flags.add("nation_conquered");
		}

		if (member != null && member.owed + member.inTransit > 0) {
			flags.add("nation_tribute");
		}

		if (!nation.news.isEmpty() && day - nation.newsDay <= Diplomacy.NEWS_DAYS) {
			flags.add("nation_" + nation.news);
			words.put("other", nation.newsTown);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// For the commands
	// ---------------------------------------------------------------------------------------------

	static List<String> describe(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		for (Nation nation : nations.values()) {
			Town capital = Diplomacy.byId(nation.capital.toString());
			lines.add(nation.name + " (" + nation.colour.getName().replace('_', ' ') + "), since day " + nation.foundedDay + ": capital " + (capital == null ? "?"
				: capital.name + ", ruled by " + Diplomacy.name(capital, capital.leader)));
			for (Member member : nation.members.values()) {
				Town town = Diplomacy.byId(member.town.toString());
				if (town == null) {
					continue;
				}

				var r = capital == null ? null : Diplomacy.relation(town, capital);
				lines.add("  " + town.name + (member.conquered ? " (conquered)" : "") + (member.rebel ? " (in revolt)" : "") + ": with the capital "
					+ (r == null ? "?" : Diplomacy.word(r.standing) + " (" + Math.round(r.standing) + ")") + ", tribute owed " + (member.owed + member.inTransit)
					+ ", next due day " + member.tributeDay + (nation.banners.containsKey(town.id) ? ", banner up" : ""));
			}
		}

		return lines;
	}

	/** Debug: what's owed falls due now. */
	static void debugTribute(MinecraftServer server) {
		for (Nation nation : nations.values()) {
			for (Member member : nation.members.values()) {
				member.tributeDay = Chronicle.day(server);
			}
		}

		doneDay.clear();
	}

	private static double trait(@Nullable DrifterEntity drifter, String trait) {
		return drifter == null ? 0.5 : DrifterApi.trait(drifter, trait);
	}

	private static String line(DrifterEntity who, String key, Object... vars) {
		String line = com.dinglebob.drifter.social.Lines.pick(who, key, vars);
		return line == null ? "" : line;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the towns
	// ---------------------------------------------------------------------------------------------

	static void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Nation nation : nations.values()) {
			CompoundTag one = new CompoundTag();
			one.putUUID("Id", nation.id);
			one.putString("Name", nation.name);
			one.putUUID("Capital", nation.capital);
			one.putString("Colour", nation.colour.getName());
			one.putLong("FoundedDay", nation.foundedDay);
			one.putString("News", nation.news);
			one.putString("NewsTown", nation.newsTown);
			one.putLong("NewsDay", nation.newsDay);
			ListTag members = new ListTag();
			for (Member member : nation.members.values()) {
				CompoundTag m = new CompoundTag();
				m.putUUID("Town", member.town);
				m.putLong("JoinedDay", member.joinedDay);
				m.putBoolean("Conquered", member.conquered);
				m.putLong("TributeDay", member.tributeDay);
				m.putInt("Owed", member.owed);
				m.putInt("InTransit", member.inTransit);
				m.putBoolean("Rebel", member.rebel);
				members.add(m);
			}

			one.put("Members", members);
			CompoundTag banners = new CompoundTag();
			nation.banners.forEach((town, pos) -> banners.putLong(town.toString(), pos.asLong()));
			one.put("Banners", banners);
			list.add(one);
		}

		tag.put("Nations", list);
	}

	static void load(CompoundTag tag) {
		nations.clear();
		doneDay.clear();
		for (Tag t : tag.getList("Nations", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			DyeColor colour = DyeColor.byName(one.getString("Colour"), DyeColor.WHITE);
			Nation nation = new Nation(one.getUUID("Id"), one.getString("Name"), one.getUUID("Capital"), colour, one.getLong("FoundedDay"));
			nation.news = one.getString("News");
			nation.newsTown = one.getString("NewsTown");
			nation.newsDay = one.contains("NewsDay") ? one.getLong("NewsDay") : -100;
			for (Tag mt : one.getList("Members", Tag.TAG_COMPOUND)) {
				CompoundTag m = (CompoundTag)mt;
				Member member = new Member(m.getUUID("Town"), m.getLong("JoinedDay"));
				member.conquered = m.getBoolean("Conquered");
				member.tributeDay = m.getLong("TributeDay");
				member.owed = m.getInt("Owed");
				member.inTransit = m.getInt("InTransit");
				member.rebel = m.getBoolean("Rebel");
				nation.members.put(member.town, member);
			}

			CompoundTag banners = one.getCompound("Banners");
			for (String key : banners.getAllKeys()) {
				try {
					nation.banners.put(UUID.fromString(key), BlockPos.of(banners.getLong(key)));
				} catch (IllegalArgumentException ignored) {
				}
			}

			nations.put(nation.id, nation);
		}
	}
}
