package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.social.Relationship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * What a word says (quests, carry word; the user, 2026-11-14: "i would also like to know what the word is, can you think of
 * multiple situations on why a word should go around?", and the letter: "opens one page when you right click it", "sometimes
 * the drifter tells you what it is straight up or you just find out by opening the page", "sometimes the letter can be
 * anonymous"). Each word comes from something true of the world: a friend in another town (news of their own, or of a death
 * in the town), a leader's business with another's (an offer, a complaint, help asked in a war, a debt by a peace), a
 * warning (a war, someone wanted), a town short of something asking another's shop. Its letter opens to one page (the
 * client: {@code LetterReading}); a private one is sealed, and whoever it's for can tell it's been opened.
 *
 * <p>Handing it over (the user: "when i give them the word they dont stop and process it"): said to them, thrown to them,
 * or stood right by them, they stop, turn, take it and read it, and answer: often with a reply to carry back, and with a
 * debt, sometimes the money itself.
 */
final class Words {
	private Words() {
	}

	/** The letter's marks (custom data on a sheet of paper): its quest, what's on it, sealed or opened. */
	static final String LETTER = "drifter_civ_letter";
	static final String TEXT = "drifter_civ_letter_text";
	static final String SEALED = "drifter_civ_letter_sealed";
	static final String OPENED = "drifter_civ_letter_opened";

	/** Debug (`/drifterciv quest offer word.<kind>`): only words of this kind. */
	@Nullable
	static String only;

	/** A Drifter it's this fond of (trust) is a friend it writes to. */
	private static final double FOND = 25.0;
	/** A grave this lately dug (days) is news to send. */
	private static final int DEATH_DAYS = 20;
	/** Reply chances: a friend's news, a death, a warning. */
	private static final double REPLY_FAMILY = 0.75;
	private static final double REPLY_DEATH = 0.5;
	private static final double REPLY_WARNING = 0.5;
	/** What delivering does to how the two towns stand: an offer or greeting, a warning, trade, a complaint, news. */
	private static final double OFFER_STANDING = 4.0;
	private static final double WARNING_STANDING = 3.0;
	private static final double TRADE_STANDING = 1.0;
	private static final double COMPLAINT_STANDING = -2.0;
	private static final double NEWS_STANDING = 1.0;
	/** A sealed letter that reached its reader open: what they think of the one who opened it. */
	private static final double OPENED_TRUST = -5.0;
	/** Ticks: handing over, the first word; read (a letter) or heard (by mouth); done. */
	private static final int SAY_AT = 6;
	private static final int READ_TICKS = 70;
	private static final int HEAR_TICKS = 35;
	private static final int DONE_AFTER = 30;
	/** How close they must stand for it to take the word without being spoken to (blocks); spoken to, this close. */
	static final double BESIDE = 3.5;
	private static final double NEAR_ENOUGH = 6.0;
	/** Walked further off than this before it's taken it in: it's not handed over. */
	private static final double STAYED = 8.0;

	/** A word that could be asked: who it's for, what it says, how it goes. */
	static final class Draft {
		String kind = "news";
		@Nullable
		UUID to;
		String toName = "";
		@Nullable
		UUID toTownId;
		String toTown = "";
		/** Who they are, for finding them ("the smith", "who keeps the stall", "a farmer"). */
		String toRole = "";
		@Nullable
		BlockPos at;
		String message = "";
		/** The giver's ask, without its pay ({pay} put in by Quests). */
		String ask = "";
		String why = "";
		/** Whose death it tells of; for a debt, how much (emeralds). */
		String about = "";
		int owed;
		boolean sealed;
		boolean anon;
		boolean forTown;
		double x;
		double z;
	}

	// ---------------------------------------------------------------------------------------------
	// Who they are
	// ---------------------------------------------------------------------------------------------

	/** Who someone of a town is, to find them by: "the leader", "who keeps the stall", "the smith", "a guard"... */
	static String role(@Nullable Town town, @Nullable UUID id) {
		if (town == null || id == null) {
			return "";
		}

		if (id.equals(town.leader)) {
			return "the leader";
		}

		if (id.equals(town.keeper)) {
			return "who keeps the stall";
		}

		String line = town.lines.get(id);
		if (line == null) {
			return "";
		}

		return switch (line) {
			case "smith", "enchanter" -> "the " + line;
			case "guard", "farmer", "fisher", "fletcher", "hunter", "miner", "builder", "woodcutter", "cook" -> "a " + line;
			default -> "";
		};
	}

	/** "Priya, who keeps the stall in Ashford", "Wren, the smith of Ashford", "Ash of Ashford". */
	static String described(String name, String role, String town) {
		if (role.isEmpty()) {
			return name + " of " + town;
		}

		return role.startsWith("who ") ? name + ", " + role + " in " + town : name + ", " + role + " of " + town;
	}

	// ---------------------------------------------------------------------------------------------
	// What there is to say
	// ---------------------------------------------------------------------------------------------

	/**
	 * The words this one could ask a player to carry now, in the order to try them: each from something true (a friend
	 * elsewhere, a recent grave, its town's dealings, a war, someone wanted, a shortage), to a town {@code places} names.
	 * Quests checks how far, wars and whether it's already carrying word there.
	 */
	static List<Draft> drafts(MinecraftServer server, Town town, DrifterEntity giver, ServerPlayer player, boolean leader, List<WorldPlan.Place> places) {
		List<Draft> out = new ArrayList<>();
		Random random = new Random(server.overworld().getGameTime() / 200L + giver.getId());
		long day = Chronicle.day(server);
		String from = giver.getName().getString();
		String called = TalkApi.callName(giver, player);

		// A friend in another town (each Drifter keeps who it knows and likes): news of its own, or of a death here.
		List<Relationship> friends = new ArrayList<>();
		for (Relationship r : giver.getBonds().all()) {
			Town theirs = r.trust >= FOND ? Diplomacy.townOf(r.player) : null;
			DrifterRecord record = theirs == null ? null : DrifterApi.record(r.player);
			if (theirs != null && theirs != town && record != null && record.status() != DrifterRecord.Status.GONE) {
				friends.add(r);
			}
		}

		Collections.shuffle(friends, random);
		TownGraves.Grave grave = null;
		for (TownGraves.Grave g : town.graves) {
			if (day - g.day() <= DEATH_DAYS && !g.name().equals(from) && (grave == null || g.day() > grave.day())) {
				grave = g;
			}
		}

		int personal = 0;
		for (Relationship friend : friends.subList(0, Math.min(2, friends.size()))) {
			personal++;
			Town theirs = Diplomacy.townOf(friend.player);
			Draft d = to(theirs, friend.player, friend.name);
			if (grave != null && !grave.name().equals(friend.name) && random.nextDouble() < 0.6) {
				d.kind = "death";
				d.about = grave.name();
				// (How, when it's known: a line that says how only then.)
				Object[] vars = grave.cause().isBlank() ? new Object[] {"who", friend.name, "dead", grave.name(), "town", town.name}
					: new Object[] {"who", friend.name, "dead", grave.name(), "cause", grave.cause(), "town", town.name};
				d.message = CivLines.pick(giver, "word.msg.death", "i'm sorry to write it, but " + grave.name() + " is dead. we buried them by the town.", vars);
				d.ask = CivLines.pick(giver, "word.ask.death", "could you tell " + friend.name + " in " + theirs.name + " that " + grave.name() + " died? they were close.",
					"player", called, "who", friend.name, "town", theirs.name, "dead", grave.name(), "pay", "{pay}");
				d.why = from + " of " + town.name + " wants " + friend.name + " to know that " + grave.name() + " has died.";
			} else {
				d.kind = "family";
				d.message = CivLines.pick(giver, "word.msg.family", "it's been too long. all's well here in " + town.name + ". come and see us when you can.", "who",
					friend.name, "town", town.name, "news", news(server, town, day), "from", from);
				d.ask = CivLines.pick(giver, "word.ask.family", "my old friend " + friend.name + " lives in " + theirs.name + " now. could you take them a word from me?",
					"player", called, "who", friend.name, "town", theirs.name, "pay", "{pay}");
				d.why = from + " of " + town.name + " misses " + friend.name + ", an old friend who lives in " + theirs.name + " now.";
			}

			out.add(d);
		}

		// The other towns it knows of: a leader's business, a warning, trade, or news.
		List<WorldPlan.Place> others = new ArrayList<>(places);
		Collections.shuffle(others, random);
		Town.Criminal wanted = null;
		for (Map.Entry<UUID, Town.Criminal> entry : town.crimes.entrySet()) {
			if (!entry.getKey().equals(player.getUUID()) && entry.getValue().wantedUntil > day) {
				wanted = entry.getValue();
			}
		}

		String enemy = WorldPlan.enemyOf(town);
		boolean speaks = leader || giver.getUUID().equals(town.keeper) || "smith".equals(town.lines.get(giver.getUUID()));
		net.minecraft.world.item.Item short_ = speaks ? shortOf(town, giver) : null;
		for (WorldPlan.Place place : others) {
			Town other = Diplomacy.byId(place.id.toString());
			if (other == null || other.group == null) {
				// (A town nobody's been near enough to be made: its leader by the summary, news only, as before.)
				if (!place.leader.isEmpty()) {
					Draft d = news(server, giver, town, place, null, null, place.leader, "leader", called, day, leader);
					out.add(d);
				}

				continue;
			}

			Diplomacy.Relation r = Diplomacy.relation(town, other);
			if (leader && other.leader != null && alive(other.leader)) {
				String name = name(other, other.leader);
				if (r != null && r.owed > 0 && other.id.equals(r.owedBy)) {
					Draft d = to(other, other.leader, name);
					d.kind = "debt";
					d.sealed = true;
					d.forTown = true;
					d.owed = r.owed;
					d.message = CivLines.pick(giver, "word.msg.debt", other.name + " still owes " + town.name + " " + DrifterApi.moneyWords(r.owed)
						+ " by the peace we made. we'd like it paid.", "who", name, "town", other.name, "mine", town.name, "owed", DrifterApi.moneyWords(r.owed));
					d.ask = CivLines.pick(giver, "word.ask.sealed", "i've a letter for " + name + " in " + other.name + ". its private, so it stays sealed.", "player",
						called, "who", name, "town", other.name, "pay", "{pay}");
					d.why = from + ", who leads " + town.name + ", has business with " + name + ", who leads " + other.name + ".";
					out.add(d);
				} else {
					out.add(leaderWord(giver, town, other, r, name, called, enemy, random));
				}
			}

			if (wanted != null || enemy != null) {
				UUID to = other.leader != null && alive(other.leader) ? other.leader : other.keeper;
				if (to != null && alive(to)) {
					String name = name(other, to);
					Draft d = to(other, to, name);
					d.kind = "warning";
					d.forTown = leader;
					if (wanted != null && (enemy == null || random.nextBoolean())) {
						d.about = wanted.name;
						d.message = CivLines.pick(giver, "word.msg.warning.wanted", "watch out for " + wanted.name + ". wanted here for " + crime(wanted.crime)
							+ ". if they come your way, keep your doors shut.", "who", name, "wanted", wanted.name, "crime", crime(wanted.crime), "town", town.name);
						// (Now and then whoever sends it would rather nobody knew it was them.)
						d.anon = !leader && random.nextDouble() < 0.5;
					} else {
						d.message = CivLines.pick(giver, "word.msg.warning.war", "we're at war with " + enemy + ". their bands are on the roads. keep your people close.",
							"who", name, "enemy", enemy, "town", town.name);
					}

					d.ask = CivLines.pick(giver, d.anon ? "word.ask.warning.anon" : "word.ask.warning", d.anon ? "take a warning to " + name + " in " + other.name
						+ " for me? just dont say it came from me." : "someone should warn " + name + " in " + other.name + ". would you take it?", "player", called, "who",
						name, "town", other.name, "what", d.message, "pay", "{pay}");
					d.why = (d.anon ? "Someone in " + town.name : from + " of " + town.name) + " wants " + other.name + " warned.";
					out.add(d);
				}
			}

			if (short_ != null) {
				boolean iron = short_ == Items.IRON_INGOT;
				UUID smith = null;
				for (Map.Entry<UUID, String> line : other.lines.entrySet()) {
					smith = line.getValue().equals("smith") ? line.getKey() : smith;
				}

				UUID to = iron && smith != null ? smith : other.keeper;
				if (to != null && alive(to)) {
					String name = name(other, to);
					String thing = Quests.plural(Quests.name(short_));
					Draft d = to(other, to, name);
					d.kind = "trade";
					d.forTown = true;
					d.about = thing;
					d.message = CivLines.pick(giver, "word.msg.trade", "we've run out of " + thing + " in " + town.name + ". if you can spare some, send it with anyone "
						+ "heading our way and we'll pay a fair price.", "who", name, "thing", thing, "town", town.name);
					d.ask = CivLines.pick(giver, "word.ask.trade", "we're out of " + thing + ". could you ask " + name + " in " + other.name + " if they can spare any?",
						"player", called, "who", name, "town", other.name, "thing", thing, "pay", "{pay}");
					d.why = town.name + " has run out of " + thing + ", and " + from + " hopes " + other.name + " can spare some.";
					out.add(d);
				}
			}

			// (News: what's happened here lately, to its leader or its shop.)
			UUID to = leader || other.keeper == null ? other.leader : other.keeper;
			if (to != null && alive(to)) {
				out.add(news(server, giver, town, place, other, to, name(other, to), "", called, day, leader));
			}
		}

		// The personal ones first (a friend's), the rest shuffled (else a leader would only ever write as a leader, and a warning
		// would always beat trade and news: found in review).
		Collections.shuffle(out.subList(Math.min(personal, out.size()), out.size()), random);
		if (only != null) {
			out.removeIf(d -> !d.kind.equals(only));
		}

		return out;
	}

	private static Draft to(Town town, UUID id, String name) {
		Draft d = new Draft();
		d.to = id;
		d.toName = name;
		d.toTownId = town.id;
		d.toTown = town.name;
		d.toRole = role(town, id);
		d.at = town.center;
		d.x = town.center.getX();
		d.z = town.center.getZ();
		return d;
	}

	private static Draft news(MinecraftServer server, DrifterEntity giver, Town town, WorldPlan.Place place, @Nullable Town other, @Nullable UUID to, String name,
		String role, String called, long day, boolean leader) {
		Draft d = other != null && to != null ? to(other, to, name) : new Draft();
		if (other == null) {
			d.toName = name;
			d.toTownId = place.id;
			d.toTown = place.name;
			d.toRole = role.isEmpty() ? "" : "the " + role;
			d.at = new BlockPos(place.x, 64, place.z);
			d.x = place.x;
			d.z = place.z;
		}

		d.kind = "news";
		d.forTown = leader;
		String from = giver.getName().getString();
		d.message = CivLines.pick(giver, leader ? "word.msg.news.leader" : "word.msg.news", "news from " + town.name + ": " + news(server, town, day) + ".", "who", name,
			"town", town.name, "news", news(server, town, day), "from", from);
		d.ask = CivLines.pick(giver, leader ? "quest.ask.word.leader" : "quest.ask.word", "could you take word to " + name + " in " + d.toTown + " for me?", "player",
			called, "who", name, "town", d.toTown, "pay", "{pay}");
		d.why = from + (leader ? ", who leads " + town.name + "," : " of " + town.name) + " has news for " + name + (d.toRole.isEmpty() ? "" : ", " + d.toRole)
			+ " in " + d.toTown + ", and can't leave to take it.";
		return d;
	}

	/** A leader's letter to another: by how they stand. Always sealed. */
	private static Draft leaderWord(DrifterEntity giver, Town town, Town other, @Nullable Diplomacy.Relation r, String name, String called, @Nullable String enemy,
		Random random) {
		Draft d = to(other, other.leader, name);
		d.kind = "leader";
		d.sealed = true;
		d.forTown = true;
		double standing = r == null ? 0.0 : r.standing;
		String what;
		if (enemy != null && standing >= 0.0 && (r == null || !r.war)) {
			what = "help";
		} else if (standing <= -25.0) {
			what = "complain";
		} else if (r != null && r.pacts.contains(Diplomacy.Pact.ALLIANCE)) {
			what = "greet";
		} else if (standing >= 25.0) {
			what = "ally";
		} else {
			what = r != null && r.pacts.contains(Diplomacy.Pact.TRADE) ? "greet" : "trade";
		}

		d.about = what;
		String fallback = switch (what) {
			case "help" -> "we're at war with " + enemy + ". if " + other.name + " would stand with us, we won't forget it.";
			case "complain" -> "your people have been no friends to us. this has to stop, or there'll be trouble between our towns.";
			case "ally" -> town.name + " and " + other.name + " have been good neighbours. i'd have us stand together, if you're willing.";
			case "trade" -> "our carts could use your roads, and yours ours. shall we trade?";
			default -> "greetings from " + town.name + ". may your harvests be good.";
		};
		d.message = CivLines.pick(giver, "word.msg.leader." + what, fallback, "who", name, "town", other.name, "mine", town.name, "enemy", enemy == null ? "" : enemy);
		d.ask = CivLines.pick(giver, "word.ask.sealed", "i've a letter for " + name + " in " + other.name + ". its private, so it stays sealed.", "player", called, "who",
			name, "town", other.name, "pay", "{pay}");
		d.why = giver.getName().getString() + ", who leads " + town.name + ", has a letter for " + name + ", who leads " + other.name + ".";
		return d;
	}

	/** Something of this town's lately, in a few words: a grave, a festival, its size. */
	private static String news(MinecraftServer server, Town town, long day) {
		for (TownGraves.Grave g : town.graves) {
			if (day - g.day() <= DEATH_DAYS) {
				return "we lost " + g.name() + " not long ago";
			}
		}

		String festival = TownCulture.festival(town, day);
		if (festival != null) {
			return "it's " + TownCulture.name(festival) + " here";
		}

		if (TownEvents.marketDay(town, day)) {
			return "it's market day here";
		}

		long age = Math.max(0, day - town.foundedDay);
		return age < 30 ? "the town's still finding its feet" : "the harvest's in and all's quiet";
	}

	/** Something its town has none of (as a quest's "bring" finds it), or null. */
	@Nullable
	private static net.minecraft.world.item.Item shortOf(Town town, DrifterEntity giver) {
		com.dinglebob.drifter.brain.Home home = DrifterApi.home(giver);
		if (home == null) {
			return null;
		}

		for (net.minecraft.world.item.Item item : List.of(Items.IRON_INGOT, Items.BREAD, Items.LEATHER, Items.GLASS, Items.STRING, Items.WHITE_WOOL)) {
			if (home.stored(item) + giver.getInventory().count(stack -> stack.is(item)) == 0) {
				return item;
			}
		}

		return null;
	}

	private static boolean alive(UUID id) {
		DrifterRecord record = DrifterApi.record(id);
		return record != null && record.status() != DrifterRecord.Status.GONE;
	}

	private static String name(Town town, UUID id) {
		DrifterRecord record = DrifterApi.record(id);
		return record != null ? record.name() : Diplomacy.name(town, id);
	}

	static String crime(String crime) {
		return switch (crime) {
			case "theft" -> "stealing";
			case "farms" -> "wrecking the farms";
			case "livestock" -> "hurting the animals";
			case "horse" -> "taking a horse";
			case "peace", "fight" -> "fighting in town";
			case "massacre" -> "a massacre";
			default -> crime.isBlank() ? "breaking the law" : crime.startsWith("murder") ? "murder" : crime;
		};
	}

	// ---------------------------------------------------------------------------------------------
	// The letter
	// ---------------------------------------------------------------------------------------------

	/** What's on its page: to whom, the word, who from (or nobody, unsigned). */
	static String page(Quests.Quest q) {
		return "To " + q.toName + ",\n\n" + capital(q.message) + (q.anon ? "" : "\n\n" + q.giverName);
	}

	/** A sheet of paper made this quest's letter. */
	static void write(Quests.Quest q, ItemStack letter) {
		CompoundTag mark = new CompoundTag();
		mark.putLong(LETTER, q.id);
		mark.putString(TEXT, page(q));
		mark.putBoolean(SEALED, q.sealed);
		letter.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
		name(letter, q, false);
	}

	private static void name(ItemStack letter, Quests.Quest q, boolean opened) {
		String what = q.sealed ? opened ? "Opened letter" : "Sealed letter" : "Letter";
		letter.set(DataComponents.CUSTOM_NAME, Component.literal(what + " for " + q.toName + " of " + q.toTown).withStyle(style -> style.withItalic(false)));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal(q.anon ? "No name on it" : "From " + q.giverName + " of " + q.townName).withStyle(net.minecraft.ChatFormatting.GRAY));
		lore.add(Component.literal(q.sealed && !opened ? "Right-click to break the seal and read it" : "Right-click to read it").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		letter.set(DataComponents.LORE, new ItemLore(lore));
	}

	/** One of its letters, by its quest (or -1). */
	static long letterOf(ItemStack stack) {
		CustomData data = stack.is(Items.PAPER) ? stack.get(DataComponents.CUSTOM_DATA) : null;
		return data != null && data.contains(LETTER) ? data.copyTag().getLong(LETTER) : -1L;
	}

	/** Server side of reading a letter (the page itself opens on the client): a seal broken stays broken. */
	static InteractionResultHolder<ItemStack> read(ServerPlayer player, ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		Quests.Quest q = Quests.byId(tag.getLong(LETTER));
		if (tag.getBoolean(SEALED) && !tag.getBoolean(OPENED)) {
			tag.putBoolean(OPENED, true);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			if (q != null) {
				name(stack, q, true);
				q.opened = true;
				q.known = true;
				Quests.changed(player.server);
			}

			DrifterCivilizations.LOGGER.info("[words] {} broke the seal of a letter for {}", player.getGameProfile().getName(), q == null ? "?" : q.toName);
		} else if (q != null && !q.known) {
			q.known = true;
			Quests.changed(player.server);
		}

		return InteractionResultHolder.success(stack);
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ---------------------------------------------------------------------------------------------
	// Handing it over
	// ---------------------------------------------------------------------------------------------

	/** Whom a word is being handed to now: the quest and the player. */
	private record Handing(long quest, UUID player, boolean letter, long at) {
	}

	/** A handing over it never got to (unloaded, held up) is given up this long after (ticks): asked again when they're by. */
	private static final long HANDING_LASTS = 20L * 30;

	private static final Map<UUID, Handing> handing = new HashMap<>();

	static void register() {
		DrifterApi.registerGoal(Take::new);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> handing.clear());
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide || !(player instanceof ServerPlayer one) || letterOf(stack) < 0) {
				return InteractionResultHolder.pass(stack);
			}

			return read(one, stack);
		});
	}

	/** Said to whoever a word of theirs is for (anything at all): it's handed over (true: answered). */
	static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		Quests.Quest q = Quests.wordFor(drifter.getUUID(), player.getUUID());
		// (Face to face only: a name called from across the town is a call, not the word handed over. Found in review.)
		if (q == null || player.level() != drifter.level() || player.distanceTo(drifter) > NEAR_ENOUGH || !drifter.hasLineOfSight(player)) {
			return false;
		}

		// (A sealed letter they never read: it's the letter that's wanted, not their guess at it.)
		if (q.letter && q.sealed && !q.known && !hasLetter(player, q.id)) {
			return TalkApi.answer(drifter, player, "word.no_letter", null, "who", q.giverName);
		}

		return hand(player.server, q, drifter, player, false);
	}

	/** It's handed this word now (it stops, turns, takes it, reads it, answers): false if it can't just now (asleep, fighting). */
	static boolean hand(MinecraftServer server, Quests.Quest q, DrifterEntity to, ServerPlayer player, boolean letter) {
		Handing now = handing.get(to.getUUID());
		long time = server.overworld().getGameTime();
		// (Already being handed it: unless its goal never got going, then it's asked again.)
		var goal = to.getDrifterBrain().currentGoal();
		if (now != null && now.quest() == q.id && time - now.at() < HANDING_LASTS && (time - now.at() < 40 || goal != null && goal.id().equals("take_word"))) {
			return true;
		}

		if (to.isSleeping() || to.inAFight()) {
			return false;
		}

		// (A sealed letter they never read and haven't on them: not by word of mouth.)
		if (q.letter && q.sealed && !q.known && !letter && !hasLetter(player, q.id)) {
			return false;
		}

		handing.put(to.getUUID(), new Handing(q.id, player.getUUID(), letter || q.letter && hasLetter(player, q.id), time));
		// (Whatever it was at is dropped: someone's come all this way.)
		to.getDrifterBrain().restart("handed word by " + player.getGameProfile().getName());
		DrifterCivilizations.LOGGER.info("[words] {} hands {} the word from {} (#{})", player.getGameProfile().getName(), q.toName, q.giverName, q.id);
		return true;
	}

	private static boolean hasLetter(ServerPlayer player, long id) {
		for (ItemStack stack : carried(player)) {
			if (letterOf(stack) == id) {
				return true;
			}
		}

		return false;
	}

	/** The letter, out of their pockets (or its own, thrown to it) and back to plain paper, its: was it opened? */
	private static boolean takeLetter(DrifterEntity to, ServerPlayer player, long id) {
		boolean opened = false;
		for (ItemStack stack : carried(player)) {
			if (letterOf(stack) == id) {
				opened = stack.get(DataComponents.CUSTOM_DATA).copyTag().getBoolean(OPENED);
				ItemStack sheet = stack.split(1);
				blank(sheet);
				to.getInventory().add(sheet);
				break;
			}
		}

		for (int slot = 0; slot < to.getInventory().getContainerSize(); slot++) {
			ItemStack held = to.getInventory().getItem(slot);
			if (letterOf(held) == id) {
				opened |= held.get(DataComponents.CUSTOM_DATA).copyTag().getBoolean(OPENED);
				blank(held);
			}
		}

		return opened;
	}

	/** What they have on them: pockets and the other hand. */
	private static List<ItemStack> carried(ServerPlayer player) {
		List<ItemStack> all = new ArrayList<>(player.getInventory().items);
		all.addAll(player.getInventory().offhand);
		return all;
	}

	private static void blank(ItemStack sheet) {
		sheet.remove(DataComponents.CUSTOM_DATA);
		sheet.remove(DataComponents.CUSTOM_NAME);
		sheet.remove(DataComponents.LORE);
	}

	/** It has taken it in: its answer, what it does, the reply to carry back. */
	private static void taken(MinecraftServer server, Quests.Quest q, DrifterEntity to, @Nullable ServerPlayer player, boolean letter) {
		boolean opened = q.opened;
		if (player != null) {
			opened |= takeLetter(to, player, q.id);
		}

		String called = player == null ? q.playerName : TalkApi.callName(to, player);
		Town mine = Diplomacy.byId(String.valueOf(q.toTownId));
		Town theirs = q.town == null ? null : Diplomacy.byId(q.town.toString());
		Random random = new Random(server.overworld().getGameTime() + to.getId());
		List<String> said = new ArrayList<>();
		if (q.sealed && opened) {
			said.add(CivLines.pick(to, "word.opened", "this has been opened.", "player", called, "who", q.giverName));
			DrifterApi.addTrustInPlayer(to, q.player, q.playerName, OPENED_TRUST);
		}

		String reply = "";
		Diplomacy.Relation r = mine != null && theirs != null ? Diplomacy.relation(mine, theirs) : null;
		double standing = r == null ? 0.0 : r.standing;
		String mood = standing >= 25.0 ? "good" : standing <= -25.0 ? "bad" : "fair";
		switch (q.wordKind.isEmpty() ? "news" : q.wordKind) {
			case "family" -> {
				said.add(CivLines.pick(to, "word.react.family", q.giverName + " wrote? thats good to hear", "player", called, "who", q.giverName));
				reply = random.nextDouble() < REPLY_FAMILY ? CivLines.pick(to, "word.reply.family", "tell " + q.giverName + " i miss them too", "who", q.giverName) : "";
			}
			case "death" -> {
				said.add(CivLines.pick(to, "word.react.death", q.about + "? ... no. thank you for telling me", "player", called, "who", q.giverName, "dead", q.about));
				reply = random.nextDouble() < REPLY_DEATH ? CivLines.pick(to, "word.reply.death", "tell " + q.giverName + " ill come and see the grave", "who",
					q.giverName, "dead", q.about) : "";
				DrifterApi.think(to, "heard that " + q.about + " died");
			}
			case "leader" -> {
				String what = q.about.isEmpty() ? "greet" : q.about;
				said.add(CivLines.pick(to, "word.react.leader." + mood, "from " + q.giverName + "? let me see", "player", called, "who", q.giverName));
				reply = CivLines.pick(to, "word.reply.leader." + what + "." + mood, mood.equals("bad") ? "tell " + q.giverName + " we heard them" : "tell " + q.giverName
					+ " we'll think on it", "who", q.giverName, "town", q.townName);
				if (mine != null && theirs != null) {
					Diplomacy.incident(server, mine, theirs, what.equals("complain") ? COMPLAINT_STANDING : OFFER_STANDING, "letter");
				}
			}
			case "warning" -> {
				said.add(CivLines.pick(to, "word.react.warning", "thanks for the warning", "player", called, "who", q.anon ? "whoever sent it" : q.giverName));
				reply = !q.anon && random.nextDouble() < REPLY_WARNING ? CivLines.pick(to, "word.reply.warning", "tell " + q.townName + " we owe them one", "who",
					q.giverName, "town", q.townName) : "";
				if (mine != null && theirs != null) {
					Diplomacy.incident(server, mine, theirs, WARNING_STANDING, null);
				}
			}
			case "trade" -> {
				said.add(CivLines.pick(to, "word.react.trade", "out of " + q.about + ", are they", "player", called, "who", q.giverName, "thing", q.about));
				boolean spare = standing >= 0.0 && random.nextDouble() < 0.6;
				reply = CivLines.pick(to, spare ? "word.reply.trade.yes" : "word.reply.trade.no", spare ? "tell them we can spare some" : "tell them we're short ourselves",
					"who", q.giverName, "town", q.townName, "thing", q.about);
				if (mine != null && theirs != null) {
					Diplomacy.incident(server, mine, theirs, TRADE_STANDING, null);
				}
			}
			case "debt" -> {
				said.add(CivLines.pick(to, "word.react.debt", "the money. of course its the money", "player", called, "who", q.giverName));
				int owed = r == null || mine == null || !mine.id.equals(r.owedBy) ? 0 : r.owed;
				boolean sent = r != null && mine != null && mine.id.equals(r.owedBy) && r.inTransit > 0;
				// (Paid out of its till, as much as is owed and is there: real money, handed to them to carry.)
				int paid = owed <= 0 || mine == null || player == null ? 0 : Math.abs(Diplomacy.till(server, mine, -owed));
				if (paid > 0) {
					r.owed = Math.max(0, r.owed - paid);
					// (Not while some of it is still on its way by the towns' own carriers: that would be lost. Found in review.)
					if (r.owed == 0 && r.inTransit == 0) {
						r.owedBy = null;
					}

					for (ItemStack stack : DrifterApi.moneyStacks(paid)) {
						to.tossForward(stack).setTarget(player.getUUID());
					}

					q.replyMoney = paid;
					reply = CivLines.pick(to, "word.reply.debt.paid", "here. take it to " + q.giverName + ", all of it", "who", q.giverName, "owed", DrifterApi.moneyWords(paid));
					Towns.save(server);
				} else if (sent) {
					reply = CivLines.pick(to, "word.reply.debt.sent", "tell them its on its way already", "who", q.giverName);
				} else {
					reply = CivLines.pick(to, "word.reply.debt.not", "tell them they'll have it when we have it", "who", q.giverName);
				}
			}
			default -> {
				said.add(CivLines.pick(to, "quest.word.got", "from " + q.giverName + "? ok, got it. thanks for coming all this way", "player", called, "who", q.giverName,
					"town", q.townName));
				if (mine != null && theirs != null) {
					Diplomacy.incident(server, mine, theirs, NEWS_STANDING, null);
				}
			}
		}

		if (!reply.isEmpty()) {
			said.add(reply);
		}

		if (player != null) {
			// (Its bits run together as one answer; a bit's own full stop not doubled: "opened this.. from".)
			String line = String.join(". ", said.stream().map(bit -> bit.replaceAll("[.\s]+$", "")).toList());
			TalkApi.sayTo(to, player, line);
		}

		q.reply = reply;
		DrifterApi.think(to, q.playerName + " brought " + (letter ? "a letter" : "word") + " from " + q.giverName);
		Quests.delivered(server, q);
	}

	// ---------------------------------------------------------------------------------------------
	// Its goal: take the word
	// ---------------------------------------------------------------------------------------------

	/** It stops, turns to them, takes the word (reads it) and answers. */
	static final class Take extends Goals.Goal {
		@Override
		public String id() {
			return "take_word";
		}

		@Override
		public String name(Goals.Context context) {
			return "take the word it's brought";
		}

		@Override
		public double urgency(Goals.Context context) {
			Handing h = handing.get(context.drifter().getUUID());
			return h != null && context.drifter().level().getGameTime() - h.at() < HANDING_LASTS ? 1.2 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return READ_TICKS + DONE_AFTER;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Handing h = handing.get(drifter.getUUID());
			MinecraftServer server = drifter.getServer();
			Quests.Quest q = h == null ? null : Quests.byId(h.quest());
			if (h == null || server == null || q == null || q.state != Quests.TAKEN) {
				handing.remove(drifter.getUUID());
				return null;
			}

			return new Activity(drifter) {
				private int ticks;
				private boolean done;

				@Override
				public String describe() {
					return "take the word from " + q.giverName;
				}

				@Override
				protected int timeoutTicks() {
					return 20 * 20;
				}

				@Override
				protected Status next() {
					ServerPlayer player = server.getPlayerList().getPlayer(h.player());
					Steering.stopMoving(this.drifter);
					if (player != null && player.level() == this.drifter.level()) {
						this.drifter.getCamera().lookAt(player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
					}

					this.ticks++;
					// (Something at it: it breaks off; it's handed over again once they're both free. And they must still be there to
					// hand it over. Both found in review.)
					if (!this.done && (this.drifter.inAFight() || player == null || player.level() != this.drifter.level() || player.distanceTo(this.drifter) > STAYED)) {
						handing.remove(this.drifter.getUUID());
						return Status.FAILURE;
					}

					if (this.ticks == SAY_AT && player != null) {
						TalkApi.sayTo(this.drifter, player, CivLines.pick(this.drifter, h.letter() ? "word.take.letter" : "word.take", h.letter() ? "a letter? for me?"
							: "word from " + q.giverName + "? go on", "player", TalkApi.callName(this.drifter, player), "who", q.anon ? "someone" : q.giverName,
							"town", q.townName));
					}

					if (!this.done && this.ticks >= (h.letter() ? READ_TICKS : HEAR_TICKS)) {
						this.done = true;
						handing.remove(this.drifter.getUUID());
						if (q.state == Quests.TAKEN) {
							taken(server, q, this.drifter, player, h.letter());
						}
					}

					return this.done && this.ticks >= (h.letter() ? READ_TICKS : HEAR_TICKS) + DONE_AFTER ? Status.SUCCESS : Status.RUNNING;
				}

				@Override
				protected void onStop() {
					super.onStop();
					// (Stopped before it took it in, by something that couldn't wait: it's asked again when they're by.)
					if (!this.done) {
						handing.remove(this.drifter.getUUID());
					}
				}
			};
		}
	}
}
