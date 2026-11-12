package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asking a townsperson about its town in chat (docs/PLAYER_TALK.md section D, batch 1): what the place is, who runs
 * it, its rules, where the stall, the smith, the well and the rest are, where to sleep, what's for sale, whether
 * there's work, whether you're wanted, what's going on today, its own job; and "show me around" / "take me to the
 * shop" (it leads the way: Drifter's {@code GuideGoal}). Everything it says it knows as one of the town; the lines are
 * {@code town.talk.*} in {@code drifter_chat/town_talk.json}.
 */
final class TownTalk {
	private TownTalk() {
	}

	private static final String[] INTENTS = {"town_name", "town_leader", "town_rules", "town_people", "town_story", "where_shop", "where_smith",
		"where_enchanter", "where_plaza", "where_well", "where_board", "where_mine", "where_fire", "where_sleep", "what_sell", "any_work", "am_wanted",
		"whats_on", "tour", "take_me", "your_job"};

	static void register() {
		TalkApi.registerPlaces(TownTalk::places);
		TalkApi.registerConvoFacts((drifter, player, words) -> {
			Town town = Market.townOf(drifter);
			if (town != null) {
				words.putIfAbsent("town", town.name);
				String leader = leader(town);
				if (leader != null) {
					words.putIfAbsent("leader", leader);
				}
			}
		});
		// A threat to everyone, said in a town: the town hears of it.
		TalkApi.registerThreatened((drifter, player, all) -> {
			Town town = Market.townOf(drifter);
			if (town != null && within(town, player)) {
				town.heard.merge(player.getUUID(), all ? -20.0 : -6.0, (a, b) -> Math.max(-100.0, a + b));
				if (all) {
					Chronicle.add(drifter.getServer(), "law", player.getGameProfile().getName() + " threatened the people of " + town.name);
				}
			}
		});
	}

	private static boolean within(Town town, ServerPlayer player) {
		return player.level().dimension().location().toString().equals(town.dimension)
			&& Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) < town.land() + 16;
	}

	@Nullable
	private static String leader(Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		UUID leader = town.leader != null ? town.leader : group == null ? null : group.leader;
		return leader == null || group == null ? null : group.names.get(leader);
	}

	private static BlockPos middle(Town.Plot plot) {
		return plot.origin.offset(plot.sizeX / 2, 0, plot.sizeZ / 2);
	}

	/** Where someone with this job lives (the smith's house is the smithy), or null. */
	@Nullable
	private static BlockPos worker(Town town, String job) {
		for (Map.Entry<UUID, String> entry : town.lines.entrySet()) {
			if (!entry.getValue().equals(job)) {
				continue;
			}

			for (Town.Plot plot : town.plots) {
				if (plot.built && (entry.getKey().equals(plot.owner) || plot.residents.contains(entry.getKey()))) {
					return middle(plot);
				}
			}
		}

		return null;
	}

	/** The places of its town it could show someone, by what they are (in the order of a walk round). */
	private static Map<String, BlockPos> places(DrifterEntity drifter) {
		Town town = Market.townOf(drifter);
		Map<String, BlockPos> places = new LinkedHashMap<>();
		if (town == null) {
			return places;
		}

		if (!town.plaza.isEmpty()) {
			places.put("plaza", middle(town.plaza.get(0)));
		}

		if (town.stallBuilt && town.stallPlot != null) {
			places.put("shop", middle(town.stallPlot));
		}

		if (town.bountyBoard != null) {
			places.put("board", town.bountyBoard);
		}

		if (town.well != null) {
			places.put("well", town.well);
		}

		BlockPos smith = worker(town, "smith");
		if (smith != null) {
			places.put("smith", smith);
		}

		if (town.fire != null) {
			places.put("fire", town.fire);
		}

		if (town.storeBuilt && town.storePlot != null) {
			places.put("store", middle(town.storePlot));
		}

		if (!town.fields.isEmpty()) {
			places.put("farm", town.fields.get(0));
		}

		if (town.pen != null) {
			places.put("pen", town.pen);
		}

		if (town.mine != null) {
			places.put("mine", town.mine);
		}

		return places;
	}

	private static String way(ServerPlayer player, BlockPos to) {
		double dx = to.getX() + 0.5 - player.getX();
		double dz = to.getZ() + 0.5 - player.getZ();
		String[] ways = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		return ways[Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0), 8)];
	}

	/** Said to a townsperson: one of the things to ask about its town (true: answered). */
	static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		Town town = Market.townOf(drifter);
		if (town == null) {
			return false;
		}

		String mine = TalkApi.better(text, intent, INTENTS);
		if (mine == null) {
			return false;
		}

		MinecraftServer server = drifter.getServer();
		long day = Chronicle.day(server);
		TownReputation.Standing standing = TownReputation.of(town, player);
		Town.Criminal criminal = town.crimes.get(player.getUUID());
		if (mine.equals("am_wanted")) {
			if (criminal != null && criminal.wantedUntil > 0) {
				say(drifter, player, "town.talk.wanted.yes", null, "crime", criminal.crime, "days", Math.max(1, criminal.wantedUntil - day));
			} else if (criminal != null && criminal.exiledUntil > 0) {
				say(drifter, player, "town.talk.wanted.exiled", null, "crime", criminal.crime, "days", Math.max(1, criminal.exiledUntil - day));
			} else if (criminal != null && criminal.fine > 0) {
				say(drifter, player, "town.talk.wanted.fine", null, "crime", criminal.crime, "fine", TownNotices.describe(DrifterApi.money(criminal.fine)));
			} else {
				say(drifter, player, standing == TownReputation.Standing.WELCOME ? "town.talk.wanted.liked" : "town.talk.wanted.no", null);
			}

			return true;
		}

		// (Someone the town wants gets nothing out of its people.)
		if (standing == TownReputation.Standing.WANTED || standing == TownReputation.Standing.UNWELCOME) {
			say(drifter, player, "town.talk.unwelcome", null);
			return true;
		}

		String leader = leader(town);
		switch (mine) {
			case "town_name" -> say(drifter, player, "town.talk.name", null);
			case "town_leader" -> {
				if (leader == null) {
					say(drifter, player, "town.talk.leader.none", null);
				} else if (leader.equals(drifter.getName().getString())) {
					say(drifter, player, "town.talk.leader.me", null);
				} else {
					say(drifter, player, "town.talk.leader", null);
				}
			}
			case "town_rules" -> {
				List<String> laws = new ArrayList<>();
				if (town.laws.contains(Politics.NO_STEALING)) {
					laws.add("no stealing");
				}

				if (town.laws.contains(Politics.NO_KILLING)) {
					laws.add("no killing");
				}

				if (town.laws.contains(Politics.FARMS)) {
					laws.add("leave the farms alone");
				}

				if (town.laws.contains(Politics.TAX) && town.tax > 0) {
					laws.add("we pay a tax");
				}

				say(drifter, player, laws.isEmpty() ? "town.talk.rules.none" : "town.talk.rules", null, "laws", String.join(", ", laws));
			}
			case "town_people" -> {
				DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
				int count = group == null ? 0 : group.members.size();
				say(drifter, player, "town.talk.people", null, "count", count < 6 ? "a handful" : count < 13 ? "about ten" : count < 25 ? "about twenty"
					: count < 45 ? "thirty or forty" : "loads");
			}
			case "town_story" -> {
				long age = Math.max(0, day - town.foundedDay);
				say(drifter, player, age < 3 ? "town.talk.story.new" : "town.talk.story", null, "days", age);
			}
			case "where_shop" -> where(drifter, player, town, "shop", "the stall");
			case "where_smith" -> where(drifter, player, town, "smith", "the smith");
			case "where_enchanter" -> {
				BlockPos at = worker(town, "enchanter");
				whereAt(drifter, player, at, "enchanter", "the enchanter");
			}
			case "where_plaza" -> where(drifter, player, town, "plaza", "the plaza");
			case "where_well" -> where(drifter, player, town, "well", "the well");
			case "where_board" -> where(drifter, player, town, "board", "the notice board");
			case "where_mine" -> where(drifter, player, town, "mine", "the mine");
			case "where_fire" -> where(drifter, player, town, "fire", "the town fire");
			case "where_sleep" -> {
				BlockPos empty = null;
				for (Town.Plot plot : town.plots) {
					if (plot.built && plot.owner == null && plot.residents.isEmpty() && plot != town.stallPlot && plot != town.storePlot && !town.plaza.contains(plot)) {
						empty = middle(plot);
						break;
					}
				}

				if (empty != null) {
					say(drifter, player, "town.talk.sleep.empty", null, "way", way(player, empty));
				} else {
					say(drifter, player, "town.talk.sleep.none", null);
				}
			}
			case "what_sell" -> {
				DrifterEntity keeper = town.keeper == null ? null : TownFounding.loaded(server, town.keeper);
				List<String> goods = new ArrayList<>();
				if (keeper != null && keeper.getShop() != null) {
					for (ShopKeeper.ShopOffer offer : keeper.getShop().offers(keeper, null)) {
						String name = offer.goods().getHoverName().getString().toLowerCase(Locale.ROOT);
						if (DrifterApi.moneyWorth(offer.goods()) <= 0 && !goods.contains(name) && goods.size() < 4) {
							goods.add(name);
						}
					}
				}

				if (goods.isEmpty()) {
					say(drifter, player, keeper == null ? "town.talk.sells.no_stall" : "town.talk.sells.nothing", null);
				} else {
					say(drifter, player, keeper == drifter ? "town.talk.sells.mine" : "town.talk.sells", null, "goods", String.join(", ", goods), "who",
						keeper.getName().getString());
				}
			}
			case "any_work" -> {
				if (town.notices.isEmpty()) {
					say(drifter, player, "town.talk.work.none", null);
				} else {
					TownNotices.Notice notice = town.notices.get(drifter.getRandom().nextInt(town.notices.size()));
					String what = notice.count + " " + new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
						net.minecraft.resources.ResourceLocation.parse(notice.item))).getHoverName().getString().toLowerCase(Locale.ROOT);
					say(drifter, player, "town.talk.work.notice", null, "what", what, "who", notice.posterName);
				}
			}
			case "whats_on" -> {
				String festival = TownCulture.festival(town, day);
				if (TownBells.alarmed(town)) {
					say(drifter, player, "town.talk.on.alarm", null);
				} else if (festival != null) {
					say(drifter, player, "town.talk.on.festival", null, "what", TownCulture.name(festival));
				} else if (TownEvents.marketDay(town, day)) {
					say(drifter, player, "town.talk.on.market", null);
				} else if (TownEvents.contestDay(town, day)) {
					say(drifter, player, "town.talk.on.contest", null);
				} else {
					say(drifter, player, "town.talk.on.nothing", null);
				}
			}
			case "your_job" -> {
				String job = drifter.getUUID().equals(town.keeper) ? "keeper" : leader != null && leader.equals(drifter.getName().getString()) ? "leader"
					: town.lines.get(drifter.getUUID());
				if (job == null || !say(drifter, player, "town.talk.job." + job, null)) {
					say(drifter, player, "town.talk.job.none", null);
				}
			}
			case "tour" -> {
				if (standing == TownReputation.Standing.WARY) {
					say(drifter, player, "town.talk.tour.no", null);
				} else {
					TalkApi.tour(drifter, player);
				}
			}
			case "take_me" -> {
				Map<String, BlockPos> places = places(drifter);
				String said = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
				String what = null;
				for (Map.Entry<String, List<String>> entry : WORDS.entrySet()) {
					if (entry.getValue().stream().anyMatch(word -> said.contains(" " + word + " "))) {
						what = entry.getKey();
						break;
					}
				}

				BlockPos at = what == null ? null : places.get(what);
				if (at == null) {
					say(drifter, player, what == null ? "town.talk.take.where" : "town.talk.take.none", null);
				} else if (standing == TownReputation.Standing.WARY) {
					say(drifter, player, "town.talk.tour.no", null);
				} else {
					String place = what;
					say(drifter, player, "town.talk.take.yes", () -> TalkApi.leadTo(drifter, player, at, "convo.tour." + place, "the way"));
				}
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	/** The words for each place in "take me to ...". */
	private static final Map<String, List<String>> WORDS = Map.of("shop", List.of("shop", "stall", "market", "store", "keeper", "trader"),
		"smith", List.of("smith", "blacksmith", "smithy", "anvil"), "plaza", List.of("plaza", "square", "center", "centre"), "well", List.of("well"),
		"board", List.of("board", "notices", "bounties", "noticeboard"), "mine", List.of("mine", "mines", "mineshaft"), "fire", List.of("fire", "campfire"),
		"farm", List.of("farm", "farms", "fields", "crops"), "pen", List.of("pen", "animals", "cows", "barn"));

	private static void where(DrifterEntity drifter, ServerPlayer player, Town town, String what, String called) {
		whereAt(drifter, player, places(drifter).get(what), what, called);
	}

	private static void whereAt(DrifterEntity drifter, ServerPlayer player, @Nullable BlockPos at, String what, String called) {
		if (at == null) {
			say(drifter, player, "town.talk.where.none", null, "what", called);
		} else if (at.closerThan(player.blockPosition(), 12.0)) {
			say(drifter, player, "town.talk.where.here", null, "what", called);
		} else {
			say(drifter, player, "town.talk.where", null, "what", called, "way", way(player, at));
		}
	}

	private static boolean say(DrifterEntity drifter, ServerPlayer player, String key, @Nullable Runnable then, Object... more) {
		return TalkApi.answer(drifter, player, key, then, more);
	}
}
