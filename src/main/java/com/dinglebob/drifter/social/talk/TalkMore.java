package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Mode;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.SocialEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/**
 * Batch 2 of talking with a player (docs/PLAYER_TALK.md, the rows marked 2): more of the glue of a talk, more about the
 * Drifter itself, more of what it knows of the world, and more to ask it to do. Each answer comes from what it is,
 * has, saw or was told; lines are {@code more.*} in {@code drifter_chat/convo_more.json}. Called from
 * {@link PlayerConvo#handle} for the meanings here.
 */
final class TalkMore {
	private TalkMore() {
	}

	/** The glue: matched only when nothing older is meant. */
	static final String[] GLUE = {"gm", "back", "gg_said", "later", "ew", "anyway", "btw", "already_said", "ignoring", "shut_up", "leave_me", "repeat",
		"sure", "when_q", "how_q", "who_meant", "where_that", "tell_more", "ask_me", "im_bored", "im_sad", "im_happy", "im_angry", "im_lonely",
		"logging_off", "im_busy", "saved_me", "good_job", "condolence", "lost_stuff", "distress", "did_it", "missed_you", "love", "fun_fact",
		"correction", "rematch", "fight_me", "are_we_good", "we_friends", "owe_me", "ready", "done_outing", "deal", "no_deal", "hurry", "slow_down"};
	/** Things to ask and tell: matched over an older meaning when they say more. */
	static final String[] TOPICS = {"how_long", "from_where", "what_kind", "why_doing", "standing", "next_plan", "hungry_q", "inventory", "rich",
		"item_story", "saving_for", "pets", "deaths_q", "best_find", "been_nether", "story", "dream", "building_what", "favorite", "hate_what",
		"think_of", "rate", "good_at", "brave_q", "lonely_q", "like_here", "miss_x", "best_friend", "enemies", "strongest", "group_q", "make_up",
		"where_find", "where_died", "which_way", "biome_q", "over_there", "bandits_q", "what_killed", "weather_q", "who_here", "who_stole", "whose",
		"x_doing", "where_everyone", "gossip", "recipe", "ylevel", "fight_tips", "go_away", "move_aside", "meet_me", "come_up", "show_where",
		"walk_me_home", "explore", "calm_together", "race", "eat_together", "bring_me", "craft_me", "rescue", "work_for_me", "attack_that",
		"run_away", "stop_attacking", "yield_q", "want_this", "name_price", "give_back", "rob", "use_station", "take_from_chest", "pet_animals",
		"dont_touch", "what_should_i_do", "look_my_house", "relay", "new_gear", "safe_now", "home_alarm", "x_died"};

	/** "a or b?" (two short choices, the whole message). */
	private static final Pattern EITHER = Pattern.compile("^\\s*([a-z]+(?: [a-z]+)?) or ([a-z]+(?: [a-z]+)?)\\s*\\??\\s*$");

	/** A message that's only "x or y?": a pick; else null. */
	@Nullable
	static String either(String text) {
		Matcher m = EITHER.matcher(text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ?]", ""));
		return m.matches() && !m.group(1).equals(m.group(2)) ? m.group(1) + "|" + m.group(2) : null;
	}

	// ---------------------------------------------------------------------------------------------
	// Its tastes: one answer per Drifter, kept (picked by its id, so friends can remember it)
	// ---------------------------------------------------------------------------------------------

	private static final Map<String, List<String>> FAVORITES = new LinkedHashMap<>();
	private static final Map<String, List<String>> FAVORITE_WORDS = new LinkedHashMap<>();

	static {
		fav("food", List.of("food", "foods", "meal", "snack", "eat"), "steak", "golden carrots", "baked potatoes", "cooked salmon", "bread", "pumpkin pie",
			"cookies", "porkchops", "rabbit stew", "sweet berries", "honey", "mushroom stew", "cake");
		fav("block", List.of("block", "blocks", "building block"), "deepslate bricks", "spruce planks", "copper", "stone bricks", "mud bricks", "quartz",
			"dark oak", "calcite", "tuff bricks", "cherry planks", "polished andesite", "moss", "glass");
		fav("mob", List.of("mob", "mobs", "creature", "monster"), "axolotls", "foxes", "wolves", "cats", "bees", "allays", "frogs", "parrots", "endermen",
			"striders", "sniffers", "camels", "pandas", "goats");
		fav("biome", List.of("biome", "biomes", "place to live"), "cherry groves", "taiga", "plains", "dark forest", "badlands", "jungle", "snowy slopes",
			"mushroom fields", "birch forest", "savanna", "lush caves", "swamps");
		fav("wood", List.of("wood", "wood type", "log"), "spruce", "dark oak", "oak", "birch", "cherry", "mangrove", "jungle", "acacia");
		fav("tool", List.of("tool", "tools", "weapon"), "pickaxe", "axe", "sword", "shovel", "bow", "crossbow", "trident", "shears", "fishing rod", "mace");
		fav("enchant", List.of("enchant", "enchantment", "enchants"), "mending", "efficiency", "fortune", "unbreaking", "sharpness", "silk touch",
			"protection", "looting", "feather falling", "infinity");
		fav("color", List.of("color", "colour"), "green", "blue", "red", "black", "orange", "purple", "white", "yellow", "cyan", "lime");
		fav("time", List.of("time of day", "time"), "sunrise", "sunset", "midday", "night", "morning", "evening");
		fav("animal", List.of("animal", "pet"), "wolves", "cats", "horses", "parrots", "foxes", "axolotls", "bees", "pigs", "sheep", "chickens");
		fav("ore", List.of("ore", "ores", "mineral"), "diamond", "iron", "emerald", "gold", "copper", "lapis", "redstone", "ancient debris", "coal");
		fav("dimension", List.of("dimension"), "the overworld", "the nether", "the end");
	}

	private static void fav(String kind, List<String> words, String... picks) {
		FAVORITES.put(kind, List.of(picks));
		FAVORITE_WORDS.put(kind, words);
	}

	private static final List<String> DISLIKES = List.of("phantoms", "gravel", "creepers", "drowned", "pillagers", "cave spiders", "endermen staring",
		"slimes", "griefers", "running out of torches", "lava", "baby zombies", "witches", "silverfish", "powder snow", "rain", "skeletons");

	private static String pick(DrifterEntity drifter, List<String> from, String salt) {
		return from.get(Math.floorMod((drifter.getUUID().toString() + salt).hashCode(), from.size()));
	}

	// ---------------------------------------------------------------------------------------------
	// Things in the world, by the words players use for them
	// ---------------------------------------------------------------------------------------------

	/** A word → (memory kind, label part, what it's called). */
	private record Thing(MemoryKind kind, String label, String called) {
	}

	private static final Map<String, Thing> THINGS = new LinkedHashMap<>();

	static {
		for (String ore : List.of("coal", "iron", "copper", "gold", "redstone", "lapis", "emerald", "diamond")) {
			THINGS.put(ore, new Thing(MemoryKind.ORE, ore + "_ore", ore));
		}

		THINGS.put("diamonds", new Thing(MemoryKind.ORE, "diamond_ore", "diamonds"));
		THINGS.put("dias", new Thing(MemoryKind.ORE, "diamond_ore", "diamonds"));
		THINGS.put("emeralds", new Thing(MemoryKind.ORE, "emerald_ore", "emeralds"));
		THINGS.put("debris", new Thing(MemoryKind.ORE, "ancient_debris", "ancient debris"));
		THINGS.put("netherite", new Thing(MemoryKind.ORE, "ancient_debris", "ancient debris"));
		THINGS.put("quartz", new Thing(MemoryKind.ORE, "quartz_ore", "quartz"));
		for (String structure : List.of("village", "fortress", "bastion", "stronghold", "temple", "shipwreck", "monument", "mansion", "outpost", "ruin",
			"mineshaft", "ancient city", "trial chamber", "igloo")) {
			THINGS.put(structure, new Thing(MemoryKind.LANDMARK, structure.replace(' ', '_'), "a " + structure));
		}

		THINGS.put("spawner", new Thing(MemoryKind.STRUCTURE, "spawner", "a spawner"));
		THINGS.put("dungeon", new Thing(MemoryKind.STRUCTURE, "spawner", "a dungeon"));
		THINGS.put("portal", new Thing(MemoryKind.STRUCTURE, "nether_portal", "a portal"));
		THINGS.put("cave", new Thing(MemoryKind.LANDMARK, "cave", "a cave"));
		THINGS.put("ravine", new Thing(MemoryKind.LANDMARK, "ravine", "a ravine"));
		for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark oak", "cherry", "mangrove")) {
			THINGS.put(wood, new Thing(MemoryKind.RESOURCE, wood.replace(' ', '_') + "_log", wood + " trees"));
		}

		THINGS.put("wood", new Thing(MemoryKind.RESOURCE, "_log", "trees"));
		THINGS.put("trees", new Thing(MemoryKind.RESOURCE, "_log", "trees"));
		for (String block : List.of("sand", "clay", "gravel", "water", "lava", "pumpkin", "melon", "sugar cane", "bamboo", "obsidian")) {
			THINGS.put(block, new Thing(MemoryKind.RESOURCE, block.replace(' ', '_'), block));
		}

		for (String animal : List.of("cow", "sheep", "pig", "chicken", "horse", "wolf", "bee", "rabbit", "fox", "cat", "goat", "llama")) {
			THINGS.put(animal, new Thing(MemoryKind.MOB, animal, animal + "s"));
			THINGS.put(animal + "s", new Thing(MemoryKind.MOB, animal, animal + "s"));
		}

		THINGS.put("wolves", new Thing(MemoryKind.MOB, "wolf", "wolves"));
		THINGS.put("sheeps", new Thing(MemoryKind.MOB, "sheep", "sheep"));
	}

	@Nullable
	private static Thing thing(String text) {
		String said = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]+", " ") + " ";
		Thing best = null;
		int length = 0;
		for (Map.Entry<String, Thing> entry : THINGS.entrySet()) {
			if (said.contains(" " + entry.getKey() + " ") && entry.getKey().length() > length) {
				best = entry.getValue();
				length = entry.getKey().length();
			}
		}

		return best;
	}

	@Nullable
	private static MemoryEntry remembered(DrifterEntity drifter, Thing thing) {
		return drifter.getMemory().nearest(thing.kind(), drifter.level().dimension().location(), drifter.position(), Double.MAX_VALUE,
			entry -> entry.label.contains(thing.label()));
	}

	private static String coords(Vec3 pos) {
		return (int)Math.floor(pos.x) + " " + (int)Math.floor(pos.y) + " " + (int)Math.floor(pos.z);
	}

	// ---------------------------------------------------------------------------------------------
	// Things it's asked to pass on
	// ---------------------------------------------------------------------------------------------

	private record Relay(UUID carrier, String to, String from, String what, long at) {
	}

	private static final List<Relay> relays = new ArrayList<>();

	/** Each second: anyone carrying word to someone now close and in sight passes it on. */
	static void tick(net.minecraft.server.MinecraftServer server, long now) {
		if (relays.isEmpty()) {
			return;
		}

		relays.removeIf(relay -> now - relay.at() > 24000L * 2);
		for (java.util.Iterator<Relay> it = relays.iterator(); it.hasNext();) {
			Relay relay = it.next();
			DrifterEntity carrier = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(relay.carrier()) instanceof DrifterEntity d && d.isAlive()) {
					carrier = d;
				}
			}

			if (carrier == null || carrier.isSleeping() || carrier.inAFight()) {
				continue;
			}

			for (LivingEntity near : carrier.level().getEntitiesOfClass(LivingEntity.class, carrier.getBoundingBox().inflate(8.0),
				one -> one != null && one.isAlive() && (one instanceof DrifterEntity || one instanceof ServerPlayer))) {
				String name = near.getName().getString();
				if (near == carrier || !com.dinglebob.drifter.social.SocialWatcher.called(" " + relay.to().toLowerCase(Locale.ROOT) + " ", name)
					&& !name.equalsIgnoreCase(relay.to()) || !carrier.hasLineOfSight(near)) {
					continue;
				}

				String line = Lines.pick(carrier, "more.relay.deliver", "who", name, "from", relay.from(), "what", relay.what());
				if (line != null) {
					carrier.getCamera().lookAt(near.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
					com.dinglebob.drifter.social.ChatLines.to(carrier, near);
					com.dinglebob.drifter.social.ChatLines.speak(carrier, line);
					if (near instanceof DrifterEntity other) {
						String back = Lines.pick(other, "more.relay.heard", "from", relay.from());
						if (back != null) {
							PlayerTalk.sayAt(other, back, now + 50);
						}
					}
				}

				it.remove();
				break;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The answers
	// ---------------------------------------------------------------------------------------------

	private static boolean say(DrifterEntity drifter, ServerPlayer player, String key, Object... more) {
		return PlayerConvo.say(drifter, player, key, null, more);
	}

	private static boolean sayThen(DrifterEntity drifter, ServerPlayer player, String key, Runnable then, Object... more) {
		return PlayerConvo.say(drifter, player, key, then, more);
	}

	private static boolean night(DrifterEntity drifter) {
		long time = drifter.level().getDayTime() % 24000L;
		return time >= 13000L && time < 23000L;
	}

	/** What it's looking at for them: the block or creature they're looking at, as far as 32 blocks. */
	private static Vec3 lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		return player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(48.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
			player)).getLocation();
	}

	@Nullable
	private static LivingEntity lookedAtMob(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestDot = 0.97;
		for (LivingEntity one : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), e -> e != player && e.isAlive())) {
			Vec3 to = one.getBoundingBox().getCenter().subtract(eye);
			double dot = to.normalize().dot(look);
			if (dot > bestDot && player.hasLineOfSight(one)) {
				bestDot = dot;
				best = one;
			}
		}

		return best;
	}

	/**
	 * One of the meanings here, said to it: true when answered. {@code friend}, {@code knows}: its trust in them.
	 */
	static boolean handle(DrifterEntity drifter, ServerPlayer player, String text, String mine, Relationship r, boolean friend, boolean knows, long now) {
		String name = Heard.callName(drifter, player);
		Archetype kind = drifter.getPersonality().archetype();
		double bravery = drifter.getPersonality().get(Trait.BRAVERY);
		double sociability = drifter.getPersonality().get(Trait.SOCIABILITY);
		boolean helper = kind == Archetype.HELPER;
		switch (mine) {
			// ----- the glue -----
			case "gm" -> say(drifter, player, night(drifter) ? "more.gm.night" : "more.gm");
			case "back" -> say(drifter, player, "more.back");
			case "gg_said", "rematch" -> {
				boolean fighter = kind == Archetype.PVP_SWEAT || bravery + drifter.getPersonality().get(Trait.AGGRESSION) >= 1.1;
				if (mine.equals("rematch") && fighter) {
					PlayerConvo.offerDuelTo(drifter, player, now);
				} else {
					say(drifter, player, "more." + mine);
				}
			}
			case "later", "ew", "anyway", "btw", "already_said", "repeat_no", "how_q", "who_meant", "where_that", "when_q", "deal", "no_deal", "hurry",
				"slow_down", "correction", "fun_fact", "did_it", "good_job", "distress" -> {
				say(drifter, player, "more." + mine);
				if (mine.equals("btw")) {
					PlayerConvo.extend(player, 20L * 30);
				}

				if (mine.equals("good_job") && PlayerConvo.onceIn(drifter, player, "praised", now, 24000L)) {
					drifter.getSocial().record(player.getUUID(), name, SocialEvent.COMPLIMENT, 0.0);
				}
			}
			case "ignoring" -> say(drifter, player, drifter.getDrifterBrain().activity() != null && PlayerTalk.doing(drifter) != null ? "more.ignoring.busy"
				: r.trust < 0.0 ? "more.ignoring.cold" : "more.ignoring");
			case "shut_up" -> {
				say(drifter, player, kind == Archetype.PVP_SWEAT || kind == Archetype.BANDIT ? "more.shut_up.back" : "more.shut_up");
				PlayerConvo.quiet(drifter, player, now + 20L * 60 * 3);
				PlayerConvo.over(player);
			}
			case "leave_me" -> {
				say(drifter, player, "more.leave_me");
				PlayerConvo.quiet(drifter, player, now + 24000L);
				drifter.getSocial().watcher().toldToStop(player);
				PlayerConvo.over(player);
			}
			case "repeat" -> {
				String[] last = PlayerConvo.lastSaid(drifter);
				if (last == null || !say(drifter, player, "more.repeat", "last", last[1])) {
					say(drifter, player, "convo.wdym_nothing");
				}
			}
			case "sure" -> {
				String[] last = PlayerConvo.lastSaid(drifter);
				String key = last == null ? "" : last[0];
				say(drifter, player, key.contains("news") || key.contains("danger.told") ? "more.sure.heard" : key.contains("today") || key.contains("have")
					|| key.contains("gear") ? "more.sure.mine" : "more.sure");
			}
			case "tell_more" -> {
				if (!Conversations.idle(drifter) || PlayerChats.start(drifter, player, null, now) == null) {
					say(drifter, player, "more.tell_more");
				}
			}
			case "ask_me" -> {
				if (!Conversations.idle(drifter) || PlayerChats.start(drifter, player, null, now) == null) {
					say(drifter, player, "more.ask_me");
				}
			}
			case "im_bored", "im_sad", "im_happy", "im_angry", "im_lonely" -> {
				say(drifter, player, "more.feel." + mine.substring(3));
				if (mine.equals("im_lonely")) {
					PlayerConvo.extend(player, 20L * 40);
				}
			}
			case "logging_off" -> {
				say(drifter, player, friend ? "more.logoff.friend" : "more.logoff");
				PlayerConvo.over(player);
			}
			case "im_busy" -> {
				say(drifter, player, "more.im_busy");
				PlayerConvo.over(player);
			}
			case "saved_me" -> {
				if (PlayerConvo.onceIn(drifter, player, "saved", now, 24000L)) {
					r.addTrust(2.0);
				}

				say(drifter, player, "more.saved_me");
			}
			case "condolence" -> say(drifter, player, drifter.getPersonality().mood().current() == Mood.GRIEVING ? "more.condolence.grief" : "more.condolence.none");
			case "lost_stuff" -> {
				Item food = PlayerTalk.spareFood(drifter);
				if (food != null && (friend || helper || knows)) {
					say(drifter, player, "more.lost_stuff.offer");
					PlayerConvo.askFood(drifter, player, now);
				} else {
					say(drifter, player, "more.lost_stuff");
				}
			}
			case "missed_you" -> {
				long gap = r.lastSeenAt < 0 ? 0 : now - r.lastSeenAt;
				say(drifter, player, !knows ? "more.missed.stranger" : gap > 24000L * 2 ? "more.missed.long" : "more.missed.friend");
			}
			case "love" -> say(drifter, player, friend ? "more.love.friend" : "more.love.stranger");
			case "fight_me" -> {
				boolean fighter = kind == Archetype.PVP_SWEAT || kind == Archetype.BANDIT || bravery + drifter.getPersonality().get(Trait.AGGRESSION) >= 1.1;
				if (fighter) {
					PlayerConvo.offerDuelTo(drifter, player, now);
				} else {
					say(drifter, player, "more.fight_me.no");
				}
			}
			case "are_we_good" -> say(drifter, player, r.grudges.stream().anyMatch(g -> g.severity() >= 40.0 || g.forever()) ? "more.we_good.no"
				: r.trust < 0.0 || !r.grudges.isEmpty() ? "more.we_good.almost" : "more.we_good.yes");
			case "we_friends" -> say(drifter, player, friend ? "more.remember.yes" : knows ? "more.remember.sort_of" : "more.remember.no");
			case "owe_me" -> say(drifter, player, !r.debts.isEmpty() ? "more.owe.yes" : "more.owe.no");
			case "ready" -> say(drifter, player, drifter.getSocial().watcher().invitedFrom(player) != null ? "more.ready.yes" : "more.ready.what");
			case "done_outing" -> {
				drifter.getSocial().watcher().toldToStop(player);
				Facts facts = Facts.of(drifter, null);
				say(drifter, player, facts.words.containsKey("ore") ? "more.done.ore" : facts.words.containsKey("mob") ? "more.done.mob" : "more.done");
				PlayerConvo.over(player);
			}

			// ----- about it -----
			case "how_long" -> {
				long days = drifter.daysHere();
				say(drifter, player, days < 2 ? "more.how_long.new" : "more.how_long", "days", days);
			}
			case "from_where" -> say(drifter, player, Lines.has("more.from.town") && PlayerConvo.facts(drifter, player).containsKey("town") ? "more.from.town"
				: kind == Archetype.NOMAD ? "more.from.road" : "more.from.around");
			case "what_kind" -> {
				if (!say(drifter, player, "more.kind." + kind.name().toLowerCase(Locale.ROOT))) {
					say(drifter, player, "more.kind");
				}
			}
			case "why_doing" -> {
				var goal = drifter.getDrifterBrain().currentGoal();
				String doing = PlayerTalk.doing(drifter);
				if (doing == null || goal == null) {
					say(drifter, player, "more.why_doing.nothing");
				} else {
					say(drifter, player, "more.why_doing", "goal", goal.name(null == null ? null : null).toLowerCase(Locale.ROOT), "doing", doing);
				}
			}
			case "standing" -> say(drifter, player, "more.standing");
			case "next_plan" -> PlayerTalk.answerAs(drifter, player, "going", text, now);
			case "hungry_q" -> say(drifter, player, drifter.getFoodData().getFoodLevel() <= 10 ? "more.hungry.yes" : "more.hungry.no");
			case "inventory", "rich" -> {
				boolean guarded = !friend && (kind == Archetype.HOARDER || kind == Archetype.BANDIT || r.trust < 0.0);
				int diamonds = drifter.getInventory().count(stack -> stack.is(Items.DIAMOND));
				if (guarded) {
					say(drifter, player, "more.inv.guarded");
				} else if (mine.equals("rich")) {
					say(drifter, player, diamonds >= 5 ? "more.rich.yes" : diamonds > 0 ? "more.rich.some" : "more.rich.no", "count", diamonds);
				} else {
					say(drifter, player, "more.inv", "items", notable(drifter));
				}
			}
			case "item_story" -> {
				ItemStack held = drifter.getMainHandItem();
				if (held.isEmpty()) {
					say(drifter, player, "more.item.nothing");
				} else {
					String item = held.getHoverName().getString().toLowerCase(Locale.ROOT);
					List<String> enchants = new ArrayList<>();
					held.getEnchantments().keySet().forEach(e -> enchants.add(e.unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("")));
					say(drifter, player, !enchants.isEmpty() ? "more.item.enchanted" : craftable(drifter, held.getItem()) ? "more.item.made" : "more.item.found",
						"item", item, "enchants", String.join(" and ", enchants));
				}
			}
			case "saving_for" -> say(drifter, player, "more.saving", "goal", savingFor(drifter));
			case "pets" -> say(drifter, player, "more.pets.none");
			case "deaths_q" -> {
				int deaths = drifter.deaths();
				Facts facts = Facts.of(drifter, null);
				say(drifter, player, deaths == 0 ? "more.deaths.none" : facts.words.containsKey("death") ? "more.deaths.last" : "more.deaths.some", "count",
					deaths);
			}
			case "best_find" -> {
				Diary.Entry best = drifter.getDiary().best(Diary.Kind.ORE, now, 24000L * 30);
				say(drifter, player, best == null ? "more.best_find.none" : "more.best_find", "ore", best == null ? "" : best.what);
			}
			case "been_nether" -> {
				String said = Lines.normalize(text);
				boolean end = said.contains(" end ") || said.contains(" dragon ");
				String dim = end ? "the_end" : "the_nether";
				boolean been = drifter.getMemory().all().anyMatch(entry -> entry.dimension.getPath().equals(dim));
				say(drifter, player, (end ? "more.been_end." : "more.been_nether.") + (been ? "yes" : "no"));
			}
			case "story" -> {
				Facts facts = Facts.of(drifter, null);
				String topic = facts.flags.contains("died_lately") ? "died_story" : facts.flags.contains("found_rare") ? "brag_ore" : null;
				if (topic == null || !Conversations.idle(drifter) || PlayerChats.start(drifter, player, topic, now) == null) {
					say(drifter, player, "more.story");
				}
			}
			case "dream" -> {
				if (!say(drifter, player, "more.dream." + kind.name().toLowerCase(Locale.ROOT))) {
					say(drifter, player, "more.dream");
				}
			}
			case "building_what" -> {
				String doing = PlayerTalk.doing(drifter);
				boolean building = doing != null && (doing.contains("build") || doing.contains("place") || doing.contains("house") || doing.contains("wall"));
				say(drifter, player, building ? "more.building.yes" : "more.building.no", "doing", doing == null ? "" : doing);
			}
			case "favorite" -> {
				String said = " " + Lines.normalize(text) + " ";
				String which = null;
				for (Map.Entry<String, List<String>> entry : FAVORITE_WORDS.entrySet()) {
					for (String word : entry.getValue()) {
						if (said.contains(" " + Lines.normalize(word).trim() + " ")) {
							which = entry.getKey();
							break;
						}
					}

					if (which != null) {
						break;
					}
				}

				if (which == null) {
					say(drifter, player, "more.fav.what");
				} else {
					say(drifter, player, "more.fav", "what", which, "pick", pick(drifter, FAVORITES.get(which), which));
				}
			}
			case "hate_what" -> say(drifter, player, "more.hate", "pick", pick(drifter, DISLIKES, "hate"));
			case "this_or_that" -> {
				String[] two = text.contains("|") ? text.split("\\|") : null;
				if (two == null || two.length != 2) {
					say(drifter, player, "convo.unknown");
				} else {
					say(drifter, player, "more.pick", "pick", pick(drifter, List.of(two[0], two[1]), two[0] + two[1]));
				}
			}
			case "think_of" -> {
				String said = " " + Lines.normalize(text) + " ";
				String about = null;
				for (String thing : List.of("villagers", "pvp", "nether", "end", "creepers", "endermen", "phantoms", "mining", "building", "farming",
					"fishing", "diamonds", "redstone", "zombies", "skeletons", "piglins", "wolves", "cats", "night", "rain", "caves")) {
					if (said.contains(" " + Lines.normalize(thing).trim() + " ")) {
						about = thing;
						break;
					}
				}

				if (about == null || !say(drifter, player, "more.think." + about)) {
					say(drifter, player, "more.think");
				}
			}
			case "rate" -> {
				long day = now / 24000L;
				int score = 4 + Math.floorMod((player.getUUID().toString() + text + day).hashCode(), 6) + (helper ? 1 : 0) - (kind == Archetype.BUILDER ? 1 : 0);
				say(drifter, player, "more.rate", "n", Math.max(2, Math.min(10, score)));
			}
			case "good_at" -> {
				if (!say(drifter, player, "more.good_at." + kind.name().toLowerCase(Locale.ROOT))) {
					say(drifter, player, "more.good_at");
				}
			}
			case "brave_q" -> say(drifter, player, com.dinglebob.drifter.brain.Stakes.of(drifter) >= com.dinglebob.drifter.brain.Stakes.FINAL ? "more.brave.last_life"
				: bravery >= 0.6 ? "more.brave.yes" : bravery <= 0.35 ? "more.brave.no" : "more.brave.mid");
			case "lonely_q" -> say(drifter, player, sociability >= 0.6 && friends(drifter) == 0 ? "more.lonely.yes" : sociability < 0.3 ? "more.lonely.loner"
				: "more.lonely.no");
			case "like_here" -> say(drifter, player, drifter.getDrifterBrain().home() == null ? "more.like_here.passing" : drifter.getPersonality().mood().current()
				== Mood.GRIEVING ? "more.like_here.sad" : "more.like_here.yes");
			case "miss_x" -> {
				Deaths.Death death = Deaths.named(drifter, text);
				if (death == null) {
					say(drifter, player, "more.miss.alive");
				} else {
					var bond = drifter.getBonds().get(death.id());
					say(drifter, player, bond != null && bond.trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND ? "more.miss.friend" : "more.miss.barely",
						"who", death.who());
				}
			}
			case "best_friend", "enemies" -> {
				Relationship best = null;
				for (Relationship bond : drifter.getBonds().all()) {
					if (bond.name == null || bond.name.isBlank()) {
						continue;
					}

					if (best == null || (mine.equals("best_friend") ? bond.trust > best.trust : bond.trust < best.trust)) {
						best = bond;
					}
				}

				boolean has = best != null && (mine.equals("best_friend") ? best.trust >= 20.0 : best.trust <= -15.0);
				boolean secret = mine.equals("enemies") && (kind == Archetype.HERMIT || !knows);
				say(drifter, player, secret ? "more.enemies.secret" : has ? "more." + mine : "more." + mine + ".none", "who", has ? best.name : "");
			}
			case "strongest" -> {
				DrifterEntity best = null;
				for (DrifterEntity other : drifter.level().getEntitiesOfClass(DrifterEntity.class, drifter.getBoundingBox().inflate(32.0), d -> d.isAlive()
					&& !d.isTemporary() && drifter.hasLineOfSight(d))) {
					if (best == null || com.dinglebob.drifter.social.SocialWatcher.gearScore(other) > com.dinglebob.drifter.social.SocialWatcher.gearScore(best)) {
						best = other;
					}
				}

				say(drifter, player, best == null ? "more.strongest.none" : best == drifter ? "more.strongest.me" : "more.strongest", "who",
					best == null ? "" : best.getName().getString());
			}
			case "group_q" -> {
				var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
				if (group == null) {
					say(drifter, player, "more.group.none");
				} else {
					String leader = group.names.getOrDefault(group.leader, "");
					say(drifter, player, drifter.getUUID().equals(group.leader) ? "more.group.lead" : "more.group", "group", group.name, "leader", leader);
				}
			}
			case "make_up" -> {
				String grudge = null;
				for (Relationship.Grudge g : r.grudges) {
					grudge = switch (g.event()) {
						case HIT_ONCE, ATTACKED, NEARLY_KILLED, KILLED -> "hit";
						case OPENED_CHEST, TOOK_VALUABLES, CAUGHT_STEALING, HAD_ITS_THINGS -> "theft";
						case BROKE_BLOCK, FIRE_NEAR_BASE -> "grief";
						default -> grudge;
					};
				}

				if (grudge == null && r.lastInsultAt >= 0 && now - r.lastInsultAt < 24000L * 3) {
					grudge = "insult";
				}

				say(drifter, player, grudge == null ? r.trust >= PlayerTalk.KNOWS_THEM ? "more.make_up.nothing" : "more.make_up.time" : "more.make_up." + grudge);
			}

			// ----- what it knows -----
			case "where_find", "show_where" -> {
				Thing thing = thing(text);
				if (thing == null) {
					say(drifter, player, "more.find.what");
					break;
				}

				MemoryEntry entry = remembered(drifter, thing);
				boolean hoards = drifter.getPersonality().get(Trait.GREED) >= 0.8 && thing.kind() == MemoryKind.ORE;
				if (entry == null) {
					say(drifter, player, "more.find.none", "what", thing.called());
				} else if (mine.equals("show_where") && (friend || helper && knows) && !hoards) {
					BlockPos at = BlockPos.containing(entry.pos);
					sayThen(drifter, player, "more.show_where.yes", () -> GuideGoal.lead(drifter, player, List.of(new GuideGoal.Stop(at, "more.show_where.here", 4.0)),
						thing.called()), "what", thing.called());
					PlayerConvo.moving(player);
				} else if ((friend || helper && knows) && !hoards) {
					say(drifter, player, "more.find.yes", "what", thing.called(), "coords", coords(entry.pos));
				} else if (knows && !hoards) {
					say(drifter, player, "more.find.way", "what", thing.called(), "way", PlayerTalk.way(player.position(), entry.pos), "dist",
						PlayerTalk.distance(player.position(), entry.pos));
				} else {
					say(drifter, player, "more.find.no", "what", thing.called());
				}
			}
			case "where_died" -> {
				Deaths.Death death = Deaths.sawPlayerDie(drifter, player);
				if (death == null) {
					say(drifter, player, "more.your_death.no");
				} else {
					say(drifter, player, "more.your_death.yes", "way", PlayerTalk.way(player.position(), Vec3.atCenterOf(death.pos())), "dist",
						PlayerTalk.distance(player.position(), Vec3.atCenterOf(death.pos())), "when", Deaths.when(death.at(), now));
				}
			}
			case "which_way" -> {
				String said = " " + Lines.normalize(text) + " ";
				if (said.contains(" spawn ")) {
					BlockPos spawn = drifter.level().getSharedSpawnPos();
					drifter.getCamera().lookAt(Vec3.atCenterOf(spawn), CameraModel.TurnSpeed.NORMAL);
					say(drifter, player, "more.way.spawn", "way", PlayerTalk.way(player.position(), Vec3.atCenterOf(spawn)), "dist",
						PlayerTalk.distance(player.position(), Vec3.atCenterOf(spawn)));
				} else {
					String dir = said.contains(" north ") ? "north" : said.contains(" south ") ? "south" : said.contains(" east ") ? "east" : said.contains(" west ")
						? "west" : null;
					if (dir == null) {
						say(drifter, player, "more.way.what");
					} else {
						Vec3 eye = drifter.getEyePosition();
						Vec3 to = switch (dir) {
							case "north" -> eye.add(0, 0, -10);
							case "south" -> eye.add(0, 0, 10);
							case "east" -> eye.add(10, 0, 0);
							default -> eye.add(-10, 0, 0);
						};
						drifter.getCamera().lookAt(to, CameraModel.TurnSpeed.NORMAL);
						PlayerConvo.pointAt(player, to, now);
						say(drifter, player, "more.way.point", "way", dir);
					}
				}
			}
			case "biome_q" -> {
				String biome = drifter.level().getBiome(player.blockPosition()).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("somewhere");
				say(drifter, player, "more.biome", "biome", biome);
			}
			case "over_there" -> {
				Vec3 at = lookedAt(player);
				MemoryEntry known = null;
				double best = 24.0 * 24.0;
				for (MemoryKind memory : List.of(MemoryKind.LANDMARK, MemoryKind.STRUCTURE, MemoryKind.ORE)) {
					MemoryEntry one = drifter.getMemory().nearest(memory, drifter.level().dimension().location(), at, 24.0, entry -> true);
					if (one != null && one.pos.distanceToSqr(at) < best) {
						best = one.pos.distanceToSqr(at);
						known = one;
					}
				}

				drifter.getCamera().lookAt(at, CameraModel.TurnSpeed.NORMAL);
				PlayerConvo.pointAt(player, at, now);
				say(drifter, player, known == null ? "more.over_there.unknown" : "more.over_there.known", "what", known == null ? ""
					: known.label.replace("minecraft:", "").replace('_', ' '));
			}
			case "bandits_q" -> {
				boolean seen = drifter.getSenses().tracked().stream().anyMatch(a -> a.entity instanceof DrifterEntity d && d.getPersonality().archetype()
					== Archetype.BANDIT && a.identified);
				Map<String, Object> facts = PlayerConvo.facts(drifter, player);
				say(drifter, player, facts.containsKey("warning") ? "more.bandits.warning" : seen ? "more.bandits.seen" : "more.bandits.none");
			}
			case "what_killed", "x_died" -> {
				Deaths.Death death = Deaths.named(drifter, text);
				if (death == null) {
					say(drifter, player, mine.equals("x_died") ? "more.x_died.doubt" : "more.killed.unknown");
				} else {
					var bond = drifter.getBonds().get(death.id());
					boolean close = bond != null && bond.trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND;
					say(drifter, player, mine.equals("x_died") ? close ? "more.x_died.friend" : "more.x_died.known" : "more.killed.known", "who", death.who(),
						"how", death.how(), "when", Deaths.when(death.at(), now));
				}
			}
			case "weather_q" -> say(drifter, player, drifter.level().isThundering() ? "more.weather.thunder" : drifter.level().isRaining() ? "more.weather.rain"
				: night(drifter) ? "more.weather.night" : "more.weather.clear");
			case "who_here" -> {
				List<String> seen = new ArrayList<>();
				for (var a : drifter.getSenses().tracked()) {
					if (a.identified && a.entity != player && (a.entity instanceof DrifterEntity || a.entity instanceof ServerPlayer) && seen.size() < 3) {
						seen.add(a.entity.getName().getString());
					}
				}

				say(drifter, player, seen.isEmpty() ? "more.who_here.nobody" : "more.who_here", "who", String.join(", ", seen));
			}
			case "who_stole" -> say(drifter, player, "more.who_stole");
			case "whose" -> {
				Vec3 at = lookedAt(player);
				String owner = com.dinglebob.drifter.api.TalkApi.owner(drifter, BlockPos.containing(at));
				Home home = drifter.getDrifterBrain().home();
				boolean mineHere = home != null && home.center.closerThan(BlockPos.containing(at), 10.0);
				say(drifter, player, mineHere ? "more.whose.mine" : owner == null ? "more.whose.unknown" : "more.whose.known", "who", owner == null ? "" : owner);
			}
			case "x_doing" -> {
				DrifterEntity other = null;
				String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", " ") + " ";
				for (DrifterEntity one : drifter.level().getEntitiesOfClass(DrifterEntity.class, drifter.getBoundingBox().inflate(32.0), d -> d != drifter && d.isAlive())) {
					if (com.dinglebob.drifter.social.SocialWatcher.called(words, one.getName().getString()) && drifter.hasLineOfSight(one)) {
						other = one;
					}
				}

				String doing = other == null ? null : PlayerTalk.doing(other);
				say(drifter, player, other == null ? "more.x_doing.unknown" : doing == null ? "more.x_doing.nothing" : "more.x_doing", "who",
					other == null ? "" : other.getName().getString(), "doing", doing == null ? "" : doing);
			}
			case "where_everyone" -> say(drifter, player, night(drifter) ? "more.everyone.night" : "more.everyone.day");
			case "gossip" -> {
				Map<String, Object> facts = PlayerConvo.facts(drifter, player);
				if (facts.containsKey("news_what") && say(drifter, player, "convo.news.yes")) {
					break;
				}

				Relationship a = null;
				for (Relationship bond : drifter.getBonds().all()) {
					if (bond.name != null && !bond.name.isBlank() && Math.abs(bond.trust) >= 25.0 && (a == null || Math.abs(bond.trust) > Math.abs(a.trust))) {
						a = bond;
					}
				}

				boolean chatty = sociability >= 0.5 || friend;
				say(drifter, player, !chatty ? "more.gossip.no" : a == null ? "more.gossip.none" : a.trust > 0 ? "more.gossip.likes" : "more.gossip.hates", "who",
					a == null ? "" : a.name);
			}
			case "recipe" -> {
				Item item = PlayerTalk.namedItem(text);
				String needs = item == null ? null : recipe(drifter, item);
				say(drifter, player, item == null ? "more.recipe.what" : needs == null ? "more.recipe.unknown" : "more.recipe", "item",
					item == null ? "" : new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT), "needs", needs == null ? "" : needs);
			}
			case "ylevel" -> {
				Thing thing = thing(text);
				String ore = thing == null || thing.kind() != MemoryKind.ORE ? "diamond" : thing.label().replace("_ore", "").replace("ancient_debris", "debris");
				if (!say(drifter, player, "more.ylevel." + ore)) {
					say(drifter, player, "more.ylevel.diamond");
				}
			}
			case "fight_tips" -> {
				String said = " " + Lines.normalize(text) + " ";
				String mob = null;
				for (String one : List.of("creeper", "skeleton", "enderman", "zombie", "spider", "witch", "blaze", "ghast", "warden", "phantom", "slime",
					"drowned", "piglin", "pillager", "breeze", "wither", "dragon", "guardian", "evoker", "ravager", "hoglin", "vindicator")) {
					if (said.contains(" " + one) && said.contains(one)) {
						mob = one;
						break;
					}
				}

				if (mob == null || !say(drifter, player, "more.tips." + mob)) {
					say(drifter, player, "more.tips.general");
				}
			}

			// ----- doing things -----
			case "go_away", "move_aside" -> {
				Vec3 away = drifter.position().subtract(player.position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
				Home home = drifter.getDrifterBrain().home();
				boolean inItsHouse = home != null && home.center.closerThan(drifter.blockPosition(), 8.0);
				if (mine.equals("go_away") && inItsHouse) {
					say(drifter, player, "more.go_away.my_house");
					break;
				}

				double far = mine.equals("go_away") ? 10.0 : 2.5;
				Vec3 side = mine.equals("go_away") ? away : new Vec3(-away.z, 0, away.x);
				BlockPos to = BlockPos.containing(drifter.position().add(side.scale(far)));
				sayThen(drifter, player, "more." + mine, () -> StepGoal.go(drifter, to, 1.5, 0, mine.equals("go_away") ? "move away from a player" : "step aside",
					null));
				PlayerConvo.moving(player);
				if (mine.equals("go_away")) {
					PlayerConvo.over(player);
				}
			}
			case "meet_me" -> {
				Telling.Where where = Telling.location(player, text);
				BlockPos to = where != null && !where.vague() ? where.pos() : com.dinglebob.drifter.api.TalkApi.places(drifter).get(
					Lines.normalize(text).contains(" plaza ") ? "plaza" : Lines.normalize(text).contains(" well ") ? "well" : "fire");
				if (!(friend || knows && r.familiarity >= 3.0)) {
					say(drifter, player, "more.meet.no");
				} else if (to == null) {
					say(drifter, player, "more.meet.where");
				} else {
					sayThen(drifter, player, "more.meet.yes", () -> StepGoal.go(drifter, to, 3.0, 20 * 60 * 3, "meet a player", null), "where", to.getX() + " "
						+ to.getY() + " " + to.getZ());
					PlayerConvo.moving(player);
				}
			}
			case "come_up" -> PlayerTalk.answerAs(drifter, player, "come", text, now);
			case "walk_me_home" -> {
				if (r.knownBase == null) {
					say(drifter, player, "more.walk_home.unknown");
				} else if (!(friend || helper && knows)) {
					say(drifter, player, "more.walk_home.no");
				} else {
					BlockPos at = r.knownBase;
					sayThen(drifter, player, "more.walk_home.yes", () -> GuideGoal.lead(drifter, player, List.of(new GuideGoal.Stop(at, "more.walk_home.here", 6.0)),
						"the way home"));
					PlayerConvo.moving(player);
				}
			}
			case "explore", "calm_together" -> {
				if (!(friend || knows && r.familiarity >= 3.0)) {
					say(drifter, player, "convo.outing.no_trust");
				} else {
					sayThen(drifter, player, "more." + mine + ".yes", () -> drifter.getSocial().watcher().invite(player, 20 * 30, 20 * 60 * 15));
					PlayerConvo.moving(player);
				}
			}
			case "race" -> say(drifter, player, "more.race");
			case "eat_together" -> {
				Item food = PlayerTalk.spareFood(drifter);
				if (food == null) {
					say(drifter, player, "more.eat.none");
				} else {
					say(drifter, player, "more.eat.yes");
					PlayerConvo.askFood(drifter, player, now);
				}
			}
			case "bring_me" -> {
				Item item = PlayerTalk.namedItem(text);
				int spare = 0;
				if (item != null) {
					for (Map.Entry<Integer, Integer> entry : com.dinglebob.drifter.brain.Storage.spare(drifter).entrySet()) {
						if (drifter.getInventory().getItem(entry.getKey()).is(item)) {
							spare += entry.getValue();
						}
					}
				}

				if (item == null) {
					say(drifter, player, "more.bring.what");
				} else if (spare <= 0) {
					say(drifter, player, "more.bring.none", "item", new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT));
				} else if (!(friend || helper && knows)) {
					say(drifter, player, "more.bring.no");
				} else {
					int count = Math.min(spare, 8);
					Item what = item;
					sayThen(drifter, player, "more.bring.yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.GIVE, player, what, count), "item",
						new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT));
					PlayerConvo.moving(player);
				}
			}
			case "craft_me" -> say(drifter, player, "more.craft_me");
			case "rescue" -> {
				boolean comes = friend || helper && r.trust >= 0.0 || knows && bravery >= 0.6;
				if (comes) {
					sayThen(drifter, player, "more.rescue.yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.HELP, player, null, 0));
					PlayerConvo.moving(player);
				} else {
					say(drifter, player, "more.rescue.no");
				}
			}
			case "work_for_me" -> say(drifter, player, "more.work_for_me");
			case "attack_that" -> {
				LivingEntity target = lookedAtMob(player, 24.0);
				if (!(target instanceof Enemy)) {
					say(drifter, player, target == null ? "more.attack.what" : "more.attack.no");
				} else if (!(friend || knows) || bravery < 0.3) {
					say(drifter, player, "more.attack.scared");
				} else {
					sayThen(drifter, player, "more.attack.yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.HELP, player, null, 0));
					PlayerConvo.moving(player);
				}
			}
			case "run_away" -> {
				drifter.getSocial().watcher().toldToStop(player);
				say(drifter, player, drifter.inAFight() ? "more.run.fighting" : "more.run");
			}
			case "stop_attacking" -> {
				LivingEntity target = drifter.getTarget();
				if (target != null && !(target instanceof Enemy)) {
					drifter.setTarget(null);
					say(drifter, player, "more.stop_attacking.yes");
				} else {
					say(drifter, player, target == null ? "more.stop_attacking.nothing" : "more.stop_attacking.monster");
				}
			}
			case "yield_q" -> say(drifter, player, Spar.on(drifter, player) && drifter.getHealth() < drifter.getMaxHealth() * 0.35F ? "more.yield.yes" : "more.yield.no");
			case "want_this" -> {
				Item item = PlayerTalk.namedItem(text);
				ItemStack held = player.getMainHandItem();
				ItemStack offered = item != null ? new ItemStack(item) : held;
				boolean food = offered.has(DataComponents.FOOD);
				boolean wants = !offered.isEmpty() && (food && drifter.getFoodData().getFoodLevel() < 18 || offered.getItem() instanceof ArmorItem armor
					&& drifter.getItemBySlot(armor.getEquipmentSlot()).isEmpty() || com.dinglebob.drifter.brain.ItemValues.of(offered) >= 5.0);
				say(drifter, player, offered.isEmpty() ? "more.want.what" : wants ? "more.want.yes" : "more.want.no", "item",
					offered.getHoverName().getString().toLowerCase(Locale.ROOT));
			}
			case "name_price" -> say(drifter, player, drifter.getShop() != null ? "ask.shop" : "more.name_price");
			case "give_back" -> say(drifter, player, !r.debts.isEmpty() ? "more.give_back.gift" : "more.give_back.nothing");
			case "rob" -> {
				boolean outmatched = com.dinglebob.drifter.social.SocialWatcher.gearScore(player) > com.dinglebob.drifter.social.SocialWatcher.gearScore(drifter) + 2.0;
				if (PlayerConvo.onceIn(drifter, player, "robbed", now, 20L * 60 * 5)) {
					r.addTrust(-25.0);
					r.addFear(outmatched ? 20.0 : 5.0);
					com.dinglebob.drifter.api.TalkApi.threatened(drifter, player, false);
				}

				if (outmatched && bravery < 0.6 && kind != Archetype.PVP_SWEAT && kind != Archetype.BANDIT) {
					// It hands over a little: what it can spare, not its gear.
					int given = 0;
					for (Map.Entry<Integer, Integer> entry : com.dinglebob.drifter.brain.Storage.spare(drifter).entrySet()) {
						ItemStack stack = drifter.getInventory().getItem(entry.getKey());
						if (given < 2 && entry.getValue() > 0 && !stack.isEmpty()) {
							drifter.tossForward(stack.split(Math.min(entry.getValue(), stack.getCount())));
							given++;
						}
					}

					say(drifter, player, given > 0 ? "more.rob.gives" : "more.rob.nothing");
				} else {
					say(drifter, player, "more.rob.brave");
				}
			}
			case "use_station" -> {
				boolean chest = Lines.normalize(text).contains(" chest");
				if (chest && !(friend || helper && knows)) {
					say(drifter, player, "more.use.chest_no");
				} else {
					if (chest) {
						PlayerConvo.allowChests(drifter, player, now + 20L * 60 * 10);
					}

					say(drifter, player, "more.use.yes");
				}
			}
			case "take_from_chest" -> {
				if (friend || helper && knows) {
					PlayerConvo.allowChests(drifter, player, now + 20L * 60 * 10);
					say(drifter, player, kind == Archetype.HOARDER ? "more.take.hoarder" : "more.take.yes");
				} else {
					say(drifter, player, "more.take.no");
				}
			}
			case "pet_animals" -> say(drifter, player, "more.pet_animals");
			case "dont_touch" -> say(drifter, player, "more.dont_touch");
			case "what_should_i_do" -> {
				boolean armored = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
				say(drifter, player, night(drifter) ? "more.suggest.night" : !armored ? "more.suggest.gear" : "more.suggest.explore");
			}
			case "look_my_house" -> {
				if (!(friend || knows)) {
					say(drifter, player, "more.look_house.no");
				} else {
					sayThen(drifter, player, "more.look_house.yes", () -> {
						RequestGoal.ask(drifter, RequestGoal.Kind.COME, player, null, 0);
						String opinion = Lines.pick(drifter, kind == Archetype.BUILDER ? "more.look_house.builder" : "more.look_house.opinion",
							PlayerConvo.vars(drifter, player));
						if (opinion != null) {
							PlayerTalk.sayAt(drifter, opinion, drifter.level().getGameTime() + 20L * 12);
						}
					});
					PlayerConvo.moving(player);
				}
			}
			case "relay" -> {
				String to = relayTarget(drifter, text);
				if (to == null) {
					say(drifter, player, "more.relay.who");
				} else {
					String said = Lines.normalize(text);
					String what = said.contains(" looking for ") ? "is looking for you" : said.contains(" sorry") ? "says sorry" : said.contains(" thanks")
						|| said.contains(" thank ") ? "says thanks" : "says hi";
					relays.add(new Relay(drifter.getUUID(), to, player.getGameProfile().getName(), what, now));
					say(drifter, player, "more.relay.ok", "who", to);
				}
			}
			case "new_gear" -> {
				ItemStack held = player.getMainHandItem();
				drifter.getCamera().lookAt(player.getEyePosition().add(0, -0.5, 0), CameraModel.TurnSpeed.NORMAL);
				say(drifter, player, held.isEmpty() ? "more.new_gear.nothing" : "more.new_gear", "item", held.getHoverName().getString().toLowerCase(Locale.ROOT));
			}
			case "safe_now" -> {
				int forgot = Dangers.cleared(drifter, player, player.blockPosition());
				say(drifter, player, forgot > 0 || r.trust >= 0 ? "more.safe_now" : "more.safe_now.doubt");
			}
			case "home_alarm" -> {
				Home home = drifter.getDrifterBrain().home();
				if (home == null) {
					say(drifter, player, "convo.your_house.none");
				} else {
					BlockPos at = home.center;
					sayThen(drifter, player, "more.home_alarm.go", () -> StepGoal.go(drifter, at, 4.0, 20, "go home to check on it", () -> {
						boolean wrong = fireNear(drifter, at);
						if (!wrong) {
							r.addTrust(-3.0);
						}

						String line = Lines.pick(drifter, wrong ? "more.home_alarm.real" : "more.home_alarm.false", PlayerConvo.vars(drifter, player));
						if (line != null) {
							com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
						}
					}));
					PlayerConvo.moving(player);
				}
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private static int friends(DrifterEntity drifter) {
		int count = 0;
		for (Relationship bond : drifter.getBonds().all()) {
			if (bond.trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND) {
				count++;
			}
		}

		return count;
	}

	/** What's worth naming in what it carries: its best weapon, its armor, its food. */
	private static String notable(DrifterEntity drifter) {
		List<String> things = new ArrayList<>();
		ItemStack held = drifter.getMainHandItem();
		if (!held.isEmpty()) {
			things.add(held.getHoverName().getString().toLowerCase(Locale.ROOT));
		}

		ItemStack chest = drifter.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.isEmpty()) {
			things.add(chest.getHoverName().getString().toLowerCase(Locale.ROOT));
		}

		int food = drifter.getInventory().count(stack -> stack.has(DataComponents.FOOD));
		if (food > 0) {
			things.add(food > 16 ? "plenty of food" : "a bit of food");
		}

		int torches = drifter.getInventory().count(stack -> stack.is(Items.TORCH));
		if (torches > 0) {
			things.add("torches");
		}

		return things.isEmpty() ? "not much" : String.join(", ", things);
	}

	private static String savingFor(DrifterEntity drifter) {
		if (drifter.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
			return "some armor";
		}

		if (drifter.getDrifterBrain().home() == null) {
			return "a house";
		}

		boolean diamondPick = drifter.getInventory().count(stack -> stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.NETHERITE_PICKAXE)) > 0;
		return diamondPick ? "netherite, eventually" : "a diamond pick";
	}

	private static boolean craftable(DrifterEntity drifter, Item item) {
		return drifter.getServer() != null && drifter.getServer().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
			.anyMatch(holder -> holder.value().getResultItem(drifter.registryAccess()).is(item));
	}

	/** A crafting recipe for this, said as players say it ("3 iron ingot and 2 stick"); null if there's none. */
	@Nullable
	private static String recipe(DrifterEntity drifter, Item item) {
		if (drifter.getServer() == null) {
			return null;
		}

		for (var holder : drifter.getServer().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			if (!holder.value().getResultItem(drifter.registryAccess()).is(item)) {
				continue;
			}

			Map<String, Integer> counts = new LinkedHashMap<>();
			for (Ingredient ingredient : holder.value().getIngredients()) {
				ItemStack[] options = ingredient.getItems();
				if (options.length == 0) {
					continue;
				}

				String called = options.length > 1 && options[0].is(net.minecraft.tags.ItemTags.PLANKS) ? "planks"
					: options[0].getHoverName().getString().toLowerCase(Locale.ROOT);
				counts.merge(called, 1, Integer::sum);
			}

			if (counts.isEmpty()) {
				continue;
			}

			List<String> parts = new ArrayList<>();
			counts.forEach((what, n) -> parts.add(n + " " + what));
			return String.join(", ", parts);
		}

		return null;
	}

	private static boolean fireNear(DrifterEntity drifter, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-8, -3, -8), at.offset(8, 6, 8))) {
			if (drifter.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
				return true;
			}
		}

		return false;
	}

	/** Who "tell X i said hi" is for: a Drifter or a player it knows by name, or null. */
	@Nullable
	private static String relayTarget(DrifterEntity drifter, String text) {
		String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", " ") + " ";
		String best = null;
		for (Relationship bond : drifter.getBonds().all()) {
			if (bond.name != null && !bond.name.isBlank() && com.dinglebob.drifter.social.SocialWatcher.called(words, bond.name)
				&& (best == null || bond.name.length() > best.length())) {
				best = bond.name;
			}
		}

		if (best == null) {
			for (Relationship known : drifter.getSocial().all()) {
				if (known.name != null && !known.name.isBlank() && com.dinglebob.drifter.social.SocialWatcher.called(words, known.name)) {
					best = known.name;
				}
			}
		}

		return best;
	}
}
