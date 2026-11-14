package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Batch 2 of asking a townsperson about its town (docs/PLAYER_TALK.md section D, the rows marked 2), and what Drifter's
 * talk asks of the addon: the words for its work a player may call it by ("guard", "smith"), what happened round it
 * lately (the chronicle, as one of the town tells it), and whose a house is. Lines: {@code town.more.*} in
 * {@code drifter_civ/drifter_chat/town_more.json}.
 */
final class TownTalkMore {
	private TownTalkMore() {
	}

	static final String[] INTENTS = {"price", "buys", "money_take", "live_here", "build_here", "nation", "like_town", "allies", "war", "wanted_list",
		"join_festival", "leader_opinion", "who_lives", "guards_call", "didnt_do_it", "sorry_guard", "haggle", "buy_it", "job_done", "market_when",
		"report_crime", "raid_coming"};

	/** The words for each work (as Whereabouts knows them), for being called by it. */
	private static final Map<String, List<String>> ROLES = Map.ofEntries(Map.entry("keeper", List.of("shopkeeper", "keeper", "merchant", "trader", "vendor")),
		Map.entry("leader", List.of("leader", "mayor", "chief", "boss", "king", "queen", "elder")), Map.entry("guard", List.of("guard", "guards", "soldier",
			"officer", "sheriff")), Map.entry("smith", List.of("smith", "blacksmith")), Map.entry("enchanter", List.of("enchanter", "wizard")),
		Map.entry("farmer", List.of("farmer")), Map.entry("fisher", List.of("fisher", "fisherman")), Map.entry("fletcher", List.of("fletcher")),
		Map.entry("hunter", List.of("hunter")), Map.entry("miner", List.of("miner")), Map.entry("builder", List.of("builder")), Map.entry("woodcutter",
			List.of("woodcutter", "lumberjack")), Map.entry("cook", List.of("cook", "baker", "chef")));

	static void register() {
		TalkApi.registerRoles(drifter -> {
			Town town = Market.townOf(drifter);
			if (town == null) {
				return Set.of();
			}

			Set<String> words = new HashSet<>();
			String job = town.lines.get(drifter.getUUID());
			if (job != null && ROLES.containsKey(job)) {
				words.addAll(ROLES.get(job));
			}

			if (drifter.getUUID().equals(town.keeper)) {
				words.addAll(ROLES.get("keeper"));
			}

			if (drifter.getUUID().equals(leaderId(town))) {
				words.addAll(ROLES.get("leader"));
			}

			return words;
		});
		TalkApi.registerHistory(drifter -> {
			Town town = Market.townOf(drifter);
			MinecraftServer server = drifter.getServer();
			if (town == null || server == null) {
				return List.of();
			}

			long day = Chronicle.day(server);
			List<String[]> out = new ArrayList<>();
			List<Chronicle.Entry> all = new ArrayList<>(Chronicle.all());
			java.util.Collections.reverse(all);
			for (Chronicle.Entry entry : all) {
				if (out.size() >= 6 || day - entry.day() > 6) {
					break;
				}

				if (!entry.text().contains(town.name)) {
					continue;
				}

				String said = entry.text().replaceAll("\\s*\\([^)]*\\)", "").replaceAll("[.!]+$", "").trim();
				said = said.isEmpty() ? said : Character.toLowerCase(said.charAt(0)) + said.substring(1);
				long ago = day - entry.day();
				out.add(new String[] {said, ago <= 0 ? "today" : ago == 1 ? "yesterday" : "a few days ago"});
			}

			return out;
		});
		TalkApi.registerOwner((drifter, pos) -> {
			Town town = Market.townOf(drifter);
			if (town == null) {
				return null;
			}

			DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
			for (Town.Plot plot : town.plots) {
				boolean in = pos.getX() >= plot.origin.getX() - 1 && pos.getX() <= plot.origin.getX() + plot.sizeX && pos.getZ() >= plot.origin.getZ() - 1
					&& pos.getZ() <= plot.origin.getZ() + plot.sizeZ;
				if (in) {
					if (plot == town.stallPlot) {
						return "the stall";
					}

					UUID owner = plot.owner != null ? plot.owner : plot.residents.isEmpty() ? null : plot.residents.get(0);
					return owner == null || group == null ? null : group.names.get(owner);
				}
			}

			return null;
		});
	}

	@Nullable
	private static UUID leaderId(Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		return town.leader != null ? town.leader : group == null ? null : group.leader;
	}

	private static boolean say(DrifterEntity drifter, ServerPlayer player, String key, Object... more) {
		return TalkApi.answer(drifter, player, key, null, more);
	}

	private static String money(double points) {
		return TownNotices.describe(DrifterApi.money(Math.max(0.0005, points)));
	}

	/** Said to a townsperson: one of these (true: answered). */
	static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent, Town town) {
		String mine = TalkApi.better(text, intent, INTENTS);
		if (mine == null) {
			return false;
		}

		MinecraftServer server = drifter.getServer();
		long day = Chronicle.day(server);
		TownReputation.Standing standing = TownReputation.of(town, player);
		boolean guard = "guard".equals(town.lines.get(drifter.getUUID()));
		DrifterEntity keeper = town.keeper == null ? null : TownFounding.loaded(server, town.keeper);
		Item item = TalkApi.itemNamed(text);
		String itemName = item == null ? "" : new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		switch (mine) {
			case "price", "buy_it" -> {
				if (item == null) {
					say(drifter, player, "town.more.price.what");
				} else if (keeper == null || keeper.getShop() == null) {
					say(drifter, player, "town.talk.sells.no_stall");
				} else {
					boolean sold = false;
					for (ShopKeeper.ShopOffer offer : keeper.getShop().offers(keeper, null)) {
						sold |= offer.goods().is(item);
					}

					String key = !sold ? "town.more.price.not_sold" : keeper == drifter ? mine.equals("buy_it") ? "town.more.buy.mine" : "town.more.price.mine"
						: "town.more.price";
					say(drifter, player, key, "item", itemName, "price", money(Market.sells(keeper, item)), "who", keeper.getName().getString());
				}
			}
			case "buys" -> {
				if (keeper == null) {
					say(drifter, player, "town.talk.sells.no_stall");
				} else if (item == null) {
					List<String> wanted = new ArrayList<>();
					for (Item one : Market.wanted()) {
						if (wanted.size() < 4) {
							wanted.add(new ItemStack(one).getHoverName().getString().toLowerCase(Locale.ROOT));
						}
					}

					say(drifter, player, "town.more.buys.list", "goods", String.join(", ", wanted), "who", keeper.getName().getString());
				} else {
					say(drifter, player, "town.more.buys.item", "item", itemName, "price", money(Market.buys(keeper, item)), "who", keeper.getName().getString());
				}
			}
			case "money_take" -> say(drifter, player, DrifterApi.coins() ? "town.more.money.coins" : "town.more.money.emeralds");
			case "live_here" -> {
				String leader = TownTalk.leaderName(town);
				say(drifter, player, standing == TownReputation.Standing.WELCOME ? "town.more.live.welcome" : standing == TownReputation.Standing.NEUTRAL
					? "town.more.live.maybe" : "town.more.live.no", "leader", leader == null ? "the leader" : leader);
			}
			case "build_here" -> {
				int plaza = Math.max(48, town.land() - 16);
				boolean inside = Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) < plaza;
				say(drifter, player, inside ? "town.more.build.inside" : "town.more.build.outside");
			}
			case "nation" -> {
				Nations.Nation nation = Nations.of(town);
				if (nation == null) {
					say(drifter, player, "town.more.nation.none");
				} else {
					Town capital = Diplomacy.byId(nation.capital.toString());
					say(drifter, player, capital == town ? "town.more.nation.capital" : "town.more.nation", "nation", nation.name, "capital",
						capital == null ? "" : capital.name);
				}
			}
			case "like_town" -> {
				boolean taxed = town.laws.contains(Politics.TAX) && town.tax > 0;
				var mood = drifter.getPersonality().mood().current();
				say(drifter, player, mood == com.dinglebob.drifter.personality.Mood.GRIEVING ? "town.more.like.sad" : taxed && drifter.getPersonality().get(
					com.dinglebob.drifter.personality.Trait.GREED) >= 0.6 ? "town.more.like.taxes" : "town.more.like.yes");
			}
			case "allies", "war" -> {
				List<String> allies = new ArrayList<>();
				List<String> enemies = new ArrayList<>();
				for (Diplomacy.Relation r : Diplomacy.of(town)) {
					Town other = Diplomacy.byId((r.a.equals(town.id) ? r.b : r.a).toString());
					if (other == null) {
						continue;
					}

					if (r.war) {
						enemies.add(other.name);
					} else if (r.pacts.contains(Diplomacy.Pact.ALLIANCE) || r.pacts.contains(Diplomacy.Pact.PEACE)) {
						allies.add(other.name);
					}
				}

				if (mine.equals("war")) {
					say(drifter, player, enemies.isEmpty() ? "town.more.war.none" : "town.more.war", "them", String.join(" and ", enemies));
				} else {
					say(drifter, player, allies.isEmpty() && enemies.isEmpty() ? "town.more.allies.none" : allies.isEmpty() ? "town.more.allies.enemies_only"
						: "town.more.allies", "them", String.join(", ", allies), "enemies", String.join(" and ", enemies));
				}
			}
			case "wanted_list" -> {
				List<String> wanted = new ArrayList<>();
				for (Town.Criminal criminal : town.crimes.values()) {
					if (criminal.wantedUntil > 0 && wanted.size() < 3) {
						wanted.add(criminal.name);
					}
				}

				say(drifter, player, wanted.isEmpty() ? "town.more.wanted.none" : "town.more.wanted", "who", String.join(", ", wanted));
			}
			case "join_festival" -> {
				String festival = TownCulture.festival(town, day);
				say(drifter, player, festival != null || TownEvents.marketDay(town, day) ? "town.more.join.yes" : "town.more.join.nothing");
			}
			case "market_when" -> {
				int next = -1;
				for (int ahead = 0; ahead <= 8; ahead++) {
					if (TownEvents.marketDay(town, day + ahead)) {
						next = ahead;
						break;
					}
				}

				say(drifter, player, next == 0 ? "town.more.market.today" : next == 1 ? "town.more.market.tomorrow" : next > 1 ? "town.more.market.soon"
					: "town.more.market.none", "days", next);
			}
			case "leader_opinion" -> {
				String leader = TownTalk.leaderName(town);
				boolean unhappy = town.revolt || town.laws.contains(Politics.TAX) && town.tax > 1;
				boolean friend = TalkApi.familiarity(drifter, player.getUUID()) >= 20.0;
				say(drifter, player, leader == null ? "town.talk.leader.none" : drifter.getName().getString().equals(leader) ? "town.more.leader.me"
					: unhappy ? friend ? "town.more.leader.frank" : "town.more.leader.careful" : "town.more.leader.good", "leader", leader == null ? "" : leader);
			}
			case "who_lives" -> {
				String owner = TalkApi.owner(drifter, player.blockPosition());
				say(drifter, player, owner == null ? "town.more.who_lives.nobody" : owner.equals(drifter.getName().getString()) ? "town.more.who_lives.me"
					: "town.more.who_lives", "who", owner == null ? "" : owner);
			}
			case "guards_call" -> {
				int coming = 0;
				for (Map.Entry<UUID, String> entry : town.lines.entrySet()) {
					if (!entry.getValue().equals("guard") || coming >= 2) {
						continue;
					}

					DrifterEntity one = TownFounding.loaded(server, entry.getKey());
					if (one != null && one.distanceTo(player) < 48.0 && one.isAlive()) {
						TalkApi.comeHelp(one, player);
						coming++;
					}
				}

				say(drifter, player, coming > 0 ? "town.more.guards.coming" : "town.more.guards.none");
			}
			case "report_crime" -> say(drifter, player, guard ? "town.more.report.guard" : "town.more.report");
			case "didnt_do_it" -> {
				Town.Criminal criminal = town.crimes.get(player.getUUID());
				boolean friend = TalkApi.familiarity(drifter, player.getUUID()) >= 25.0;
				say(drifter, player, criminal == null ? "town.more.innocent.nothing" : guard ? "town.more.innocent.guard" : friend ? "town.more.innocent.friend"
					: "town.more.innocent.doubt");
			}
			case "sorry_guard" -> {
				Town.Criminal criminal = town.crimes.get(player.getUUID());
				say(drifter, player, criminal == null || criminal.fine <= 0 && criminal.wantedUntil <= 0 ? "town.more.sorry.nothing" : criminal.fine > 0
					? "town.more.sorry.fine" : "town.more.sorry.leader", "fine", criminal == null ? "" : DrifterApi.moneyWords(criminal.fine));
			}
			case "haggle" -> say(drifter, player, keeper != drifter ? "town.more.haggle.not_mine" : drifter.getPersonality().archetype()
				== com.dinglebob.drifter.personality.Archetype.HOARDER ? "town.more.haggle.never" : TalkApi.familiarity(drifter, player.getUUID()) >= 25.0
				? "town.more.haggle.friend" : "town.more.haggle.no");
			case "job_done" -> say(drifter, player, "town.more.job_done");
			case "raid_coming" -> {
				boolean believed = TalkApi.familiarity(drifter, player.getUUID()) >= 5.0 && standing != TownReputation.Standing.WARY
					&& standing != TownReputation.Standing.UNWELCOME && standing != TownReputation.Standing.WANTED;
				if (believed) {
					Chronicle.add(server, "town", player.getGameProfile().getName() + " warned " + town.name + " of a coming raid");
				}

				say(drifter, player, believed ? guard ? "town.more.raid.guard" : "town.more.raid.believe" : "town.more.raid.doubt");
			}
			default -> {
				return false;
			}
		}

		return true;
	}
}
