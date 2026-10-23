package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.DrifterBonds;
import com.dinglebob.drifter.social.News;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What one Drifter has to talk about with another (the dynamic chat): words for its lines ({@code ore}, {@code ore_count},
 * {@code death}, {@code mob}...) and flags for the topics' conditions ({@code found_ore}, {@code died_lately}, {@code
 * friends}...). Only what it knows: its diary, its pockets, its home, its mood, its news, the players and Drifters it
 * knows; and the day as anyone sees it (the time, the weather).
 */
public final class Facts {
	public final Map<String, Object> words = new HashMap<>();
	public final Set<String> flags = new HashSet<>();

	private Facts() {
	}

	/** Its facts, talking with {@code other} (a Drifter; null when it's talking to a player). */
	public static Facts of(DrifterEntity me, @Nullable DrifterEntity other) {
		Facts facts = new Facts();
		long now = me.level().getGameTime();
		Diary diary = me.getDiary();
		facts.words.put("day", now / 24000L + 1);

		// What it did lately.
		Diary.Entry ore = diary.best(Diary.Kind.ORE, now, 24000L);
		if (ore != null) {
			facts.words.put("ore", ore.what);
			facts.words.put("ore_count", ore.count);
			facts.flags.add("found_ore");
			if (ore.what.equals("diamonds") || ore.what.equals("ancient debris") || ore.what.equals("emeralds")) {
				facts.flags.add("found_rare");
			}
		}

		Diary.Entry died = diary.latest(Diary.Kind.DIED, now, 24000L * 2);
		if (died != null) {
			facts.words.put("death", died.what);
			facts.flags.add("died_lately");
		}

		Diary.Entry close = diary.latest(Diary.Kind.CLOSE_CALL, now, 24000L);
		if (close != null) {
			facts.words.put("close_call", close.what);
			facts.flags.add("close_call");
		}

		Diary.Entry killed = diary.best(Diary.Kind.KILLED, now, 24000L);
		if (killed != null) {
			facts.words.put("mob", killed.what);
			facts.words.put("mob_count", killed.count);
			facts.words.put("kills", diary.total(Diary.Kind.KILLED, now, 24000L));
			facts.flags.add("killed_mobs");
			if (diary.total(Diary.Kind.KILLED, now, 24000L) >= 5) {
				facts.flags.add("killed_many");
			}
		}

		Diary.Entry built = diary.latest(Diary.Kind.BUILT, now, 24000L * 3);
		if (built != null) {
			facts.words.put("built", built.what);
			facts.flags.add("built_lately");
		}

		Diary.Entry traded = diary.latest(Diary.Kind.TRADED, now, 24000L);
		if (traded != null) {
			facts.words.put("traded_with", traded.what);
			facts.flags.add("traded_lately");
		}

		Diary.Entry saw = diary.latest(Diary.Kind.SAW, now, 24000L * 2);
		if (saw != null) {
			facts.words.put("place", saw.what);
			facts.flags.add("saw_place");
		}

		// A running joke (a silly death it saw this week): who, what happened, the nickname it earned.
		Diary.Entry joke = diary.latest(Diary.Kind.JOKE, now, Diary.JOKE_KEEPS);
		if (joke != null && joke.what.contains("|") && !joke.what.startsWith(me.getName().getString() + "|")) {
			String who = joke.what.substring(0, joke.what.indexOf('|'));
			String how = joke.what.substring(joke.what.indexOf('|') + 1);
			String what = com.dinglebob.drifter.social.Lines.pick(me, "joke.what." + how);
			String nick = com.dinglebob.drifter.social.Lines.pick(me, "joke.nick." + how);
			if (what != null && nick != null) {
				facts.words.put("joke_who", who);
				facts.words.put("joke_what", what);
				facts.words.put("joke_nick", nick);
				facts.flags.add("has_joke");
				if (other != null && other.getDiary().knowsJoke(joke.what, now)) {
					facts.flags.add("shares_joke");
				}
			}
		}

		// What it's doing now.
		var activity = me.getDrifterBrain().activity();
		if (activity != null) {
			// (Without its progress: "gather birch log (0/3)" is "gather birch log".)
			facts.words.put("doing", activity.describe().replaceAll("\\s*\\(.*\\)$", "").toLowerCase(Locale.ROOT));
		}

		// Its gear and its pockets.
		String armor = armor(me);
		facts.words.put("armor", armor);
		facts.flags.add("armor_" + armor);
		ItemStack held = me.getMainHandItem();
		if (!held.isEmpty()) {
			facts.words.put("held", held.getHoverName().getString().toLowerCase(Locale.ROOT));
		}

		int diamonds = me.getInventory().count(stack -> stack.is(Items.DIAMOND));
		if (diamonds > 0) {
			facts.words.put("diamonds", diamonds);
			facts.flags.add("has_diamonds");
		}

		if (me.getFoodData().getFoodLevel() <= 8) {
			facts.flags.add("hungry");
		}

		if (me.getHealth() <= me.getMaxHealth() * 0.4F) {
			facts.flags.add("hurt");
		}

		// Its home.
		Home home = me.getDrifterBrain().home();
		if (home == null) {
			facts.flags.add("no_home");
		} else if (home.planDone && home.planId != null) {
			facts.flags.add("has_house");
		} else if (home.hutBuilt) {
			facts.flags.add("has_hut");
		}

		DrifterGroups.Group group = DrifterGroups.of(me.getUUID());
		if (group != null) {
			facts.words.put("group", group.name);
			facts.flags.add("in_group");
			if (other != null && group.members.contains(other.getUUID())) {
				facts.flags.add("groupmates");
			}
		}

		if (me.getTownWork() != null && group != null) {
			facts.words.put("town", group.name);
			facts.flags.add("in_town");
		}

		// Its mood.
		facts.flags.add("mood_" + me.getPersonality().mood().current().id());

		if (other == null) {
			return facts;
		}

		// Each other.
		Relationship bond = me.getBonds().get(other.getUUID());
		if (bond == null || bond.familiarity < 5.0) {
			facts.flags.add("strangers");
		} else if (bond.trust >= DrifterBonds.FRIEND) {
			facts.flags.add("friends");
		} else if (bond.trust <= DrifterBonds.RIVAL) {
			facts.flags.add("rivals");
		} else {
			facts.flags.add("acquainted");
		}

		// News it has that the other doesn't.
		News news = freshNews(me, other, now);
		if (news != null) {
			facts.words.put("news_who", news.aboutName());
			facts.words.put("news_what", news.what());
			facts.flags.add("has_news");
		}

		// Players it thinks well or badly of.
		Relationship liked = null;
		Relationship disliked = null;
		for (Relationship player : me.getSocial().all()) {
			if (player.trust >= 30.0 && (liked == null || player.trust > liked.trust)) {
				liked = player;
			}

			if (player.trust <= -20.0 && (disliked == null || player.trust < disliked.trust)) {
				disliked = player;
			}
		}

		if (liked != null) {
			facts.words.put("liked_player", liked.name);
			facts.flags.add("likes_a_player");
		}

		if (disliked != null) {
			facts.words.put("disliked_player", disliked.name);
			facts.flags.add("dislikes_a_player");
		}

		return facts;
	}

	/**
	 * A player, as this Drifter sees them (chat phase 3: when it starts a talk with them, they're {@code b}): how it feels
	 * about them, how they look (hurt, their gear, what they hold), a joke about them, whether it has news for them. Only
	 * what it can see and knows.
	 */
	public static Facts player(DrifterEntity me, net.minecraft.world.entity.player.Player player) {
		Facts facts = new Facts();
		long now = me.level().getGameTime();
		String name = player.getGameProfile().getName();
		Relationship r = me.getSocial().get(player.getUUID());
		double trust = r == null ? me.getSocial().strangerBaseline() : r.trust;
		if (r == null || r.familiarity < 3.0) {
			facts.flags.add("stranger");
		}

		if (trust >= com.dinglebob.drifter.social.Mode.FRIENDLY_TRUST) {
			facts.flags.add("friend");
		} else if (trust >= 10.0) {
			facts.flags.add("known");
		}

		if (r != null && !r.debts.isEmpty()) {
			facts.flags.add("owed");
		}

		if (r != null && r.lastInsultAt >= 0 && now - r.lastInsultAt < 24000L && !r.lastInsult.isEmpty()) {
			facts.words.put("insult", r.lastInsult);
			facts.flags.add("insulted");
		}

		if (player.getHealth() <= 8.0F) {
			facts.flags.add("hurt");
		}

		String armor = armor(player);
		facts.words.put("armor", armor);
		facts.flags.add("armor_" + armor);
		if (armor.equals("diamond") || armor.equals("netherite")) {
			facts.flags.add("armor_good");
		}

		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty()) {
			facts.words.put("held", held.getHoverName().getString().toLowerCase(Locale.ROOT));
			facts.flags.add("holding");
			if (held.is(Items.DIAMOND_SWORD) || held.is(Items.DIAMOND_PICKAXE) || held.is(Items.DIAMOND_AXE) || held.is(Items.NETHERITE_SWORD)
				|| held.is(Items.NETHERITE_PICKAXE) || held.is(Items.NETHERITE_AXE) || held.is(Items.MACE) || held.is(Items.TRIDENT)) {
				facts.flags.add("holding_fancy");
			}
		}

		// A running joke about them (it was there).
		for (Diary.Entry entry : me.getDiary().all()) {
			if (entry.kind == Diary.Kind.JOKE && entry.what.startsWith(name + "|") && now - entry.time <= Diary.JOKE_KEEPS) {
				String how = entry.what.substring(entry.what.indexOf('|') + 1);
				String nick = com.dinglebob.drifter.social.Lines.pick(me, "joke.nick." + how);
				String what = com.dinglebob.drifter.social.Lines.pick(me, "joke.what." + how);
				if (nick != null && what != null) {
					facts.words.put("joke_nick", nick);
					facts.words.put("joke_what", what);
					facts.flags.add("joked_about");
				}
			}
		}

		// News for them: something it heard about someone else.
		for (News news : me.getNews().all()) {
			if (!news.stale(now) && !news.aboutName().equals(name)) {
				facts.words.put("news_who", news.aboutName());
				facts.words.put("news_what", news.what());
				facts.flags.add("has_news");
				break;
			}
		}

		return facts;
	}

	/**
	 * The world as anyone sees it: time of day, weather, and whether the two are out under the sky (flags with no a: or b:
	 * in front; nobody talks about the rain down a mine).
	 */
	public static Set<String> world(DrifterEntity someone, net.minecraft.world.entity.LivingEntity other) {
		Set<String> flags = new HashSet<>();
		if (someone.level().canSeeSky(someone.blockPosition().above()) && other.level().canSeeSky(other.blockPosition().above())) {
			flags.add("outside");
		}

		long time = someone.level().getDayTime() % 24000L;
		flags.add(time < 3000L ? "morning" : time < 11000L ? "day" : time < 13500L ? "evening" : "night");
		if (someone.level().isThundering()) {
			flags.add("thunder");
		} else if (someone.level().isRaining()) {
			flags.add("rain");
		} else {
			flags.add("clear");
		}

		return flags;
	}

	@Nullable
	private static News freshNews(DrifterEntity me, DrifterEntity other, long now) {
		News best = null;
		for (News news : me.getNews().all()) {
			if (!news.stale(now) && !other.getNews().knows(news.key()) && !news.aboutName().equals(other.getName().getString())
				&& (best == null || news.since() > best.since())) {
				best = news;
			}
		}

		return best;
	}

	/** Its armor in a word: none, leather, chain, iron, gold, diamond, netherite (the best piece it wears). */
	private static String armor(net.minecraft.world.entity.LivingEntity drifter) {
		String best = "none";
		int rank = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack piece = drifter.getItemBySlot(slot);
			if (piece.getItem() instanceof ArmorItem armor) {
				String material = armor.getMaterial().unwrapKey().map(key -> key.location().getPath()).orElse("none");
				int r = switch (material) {
					case "leather" -> 1;
					case "chainmail" -> 2;
					case "gold" -> 2;
					case "iron" -> 3;
					case "diamond" -> 4;
					case "netherite" -> 5;
					default -> 1;
				};
				if (r > rank) {
					rank = r;
					best = material.equals("chainmail") ? "chain" : material;
				}
			}
		}

		return best;
	}
}
