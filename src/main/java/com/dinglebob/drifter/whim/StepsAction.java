package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.executor.task.UseOnEntityTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The "steps" action (user, 2026-10-20: "can you make a system where its more easier to add new whims in the future?"): a
 * whim written out step by step in its data file, for whims no other action fits. It names what it needs first ("find":
 * a block, an animal, a friend, a spot at home...), then does its steps in order: walk over, look, use, hold, place,
 * wait, say, jump and so on (docs/WHIMS_HOWTO.md has them all). Every step goes through the usual tasks, so reach,
 * sight, real items and player time apply as everywhere else. A step whose target is gone is skipped; one that fails
 * ends the whim (it's a whim, it doesn't try hard).
 */
final class StepsAction implements WhimAction {
	/** What a target is. */
	enum Kind {
		/** A block it has seen (ids: block ids or "#tags"). */
		BLOCK(16),
		/** An animal or mob it can see (ids: mob ids). */
		MOB(16),
		/** A friend it can see: a group mate, a Drifter it trusts, a player it likes. */
		FRIEND(20),
		/** Anyone it can see: a player or another Drifter. */
		COMPANY(16),
		/** The middle of its home, if it's near. */
		HOME(48),
		/** Its bed at home. */
		BED(24),
		/** One of its chests at home. */
		CHEST(16),
		/** A spot it knows under the open sky. */
		SKY(12),
		/** A free bit of flat ground it knows (to put something down on). */
		GROUND(8);

		final int radius;

		Kind(int radius) {
			this.radius = radius;
		}
	}

	/** {@code reach}: a step uses it (it must be within a player's reach of somewhere to stand); one it only walks to or looks at needn't be (water, a pressure plate). */
	record Target(String name, Kind kind, List<String> ids, int radius, boolean optional, boolean reach) {
		Target withReach() {
			return new Target(this.name, this.kind, this.ids, this.radius, this.optional, true);
		}
	}

	/** One step; the fields a verb doesn't use keep their defaults. */
	record Step(String verb, @Nullable String target, @Nullable String item, List<String> lines, String at, int ticks, int times, int gap,
		double near, boolean hops, boolean keep, double chance) {
	}

	record Plan(Map<String, Target> targets, List<Step> steps, int timeout) {
	}

	/** Each verb and the settings it takes (besides "do" and "chance"). */
	static final Map<String, Set<String>> VERBS = new LinkedHashMap<>();
	private static final Set<String> TARGET_KEYS = Set.of("type", "ids", "radius", "optional");
	private static final List<String> LOOK_AT = List.of("sun", "moon", "stars", "clouds", "rain", "ahead", "up", "down");

	static {
		VERBS.put("walk_to", Set.of("target", "near"));
		VERBS.put("look_at", Set.of("target", "at", "ticks", "hops"));
		VERBS.put("use", Set.of("target", "item", "times", "gap"));
		VERBS.put("hold", Set.of("item"));
		VERBS.put("place", Set.of("item", "target"));
		VERBS.put("wait", Set.of("ticks"));
		VERBS.put("say", Set.of("lines", "to"));
		VERBS.put("think", Set.of("text"));
		VERBS.put("jump", Set.of("times"));
		VERBS.put("swing", Set.of("times"));
		VERBS.put("look_around", Set.of("times"));
		VERBS.put("eat", Set.of("item"));
		VERBS.put("drop", Set.of("item", "count", "keep"));
		VERBS.put("pick_up", Set.of("radius"));
		VERBS.put("crouch", Set.of("ticks"));
	}

	// ---------------------------------------------------------------------------------------------
	// Reading the data
	// ---------------------------------------------------------------------------------------------

	/** Each whim's plan, read once (kept with the whim it came from: a reload makes new whims). */
	private record Cached(Whim whim, Plan plan) {
	}

	private static final Map<String, Cached> PLANS = new java.util.concurrent.ConcurrentHashMap<>();

	static Plan plan(Whim whim) {
		Cached cached = PLANS.get(whim.id());
		if (cached != null && cached.whim() == whim) {
			return cached.plan();
		}

		Plan plan = parse(whim.params(), new ArrayList<>());
		PLANS.put(whim.id(), new Cached(whim, plan));
		return plan;
	}

	@Override
	public void check(Whim whim, List<String> warnings) {
		PLANS.put(whim.id(), new Cached(whim, parse(whim.params(), warnings)));
	}

	/** The plan in a whim's params; a mistake that breaks it throws, saying which step and what. */
	static Plan parse(JsonObject params, List<String> warnings) {
		Map<String, Target> targets = new LinkedHashMap<>();
		if (params.has("find")) {
			for (Map.Entry<String, JsonElement> entry : params.getAsJsonObject("find").entrySet()) {
				String name = entry.getKey();
				String where = "target \"" + name + "\"";
				JsonObject json = entry.getValue().getAsJsonObject();
				if (!json.has("type")) {
					throw new IllegalArgumentException(where + " has no \"type\" (" + names(Kind.values()) + ")");
				}

				Kind kind = Whim.named(Kind.class, json.get("type").getAsString(), "target type");
				for (String key : json.keySet()) {
					if (!TARGET_KEYS.contains(key)) {
						warnings.add(where + ": no setting \"" + key + "\"" + WhimCheck.didYouMean(key, TARGET_KEYS) + " (ignored)");
					}
				}

				List<String> ids = new ArrayList<>();
				if (json.has("ids")) {
					JsonElement value = json.get("ids");
					if (value.isJsonArray()) {
						value.getAsJsonArray().forEach(id -> ids.add(id.getAsString()));
					} else {
						ids.add(value.getAsString());
					}
				}

				if ((kind == Kind.BLOCK || kind == Kind.MOB) && ids.isEmpty()) {
					throw new IllegalArgumentException(where + " (a " + kind.name().toLowerCase(Locale.ROOT) + ") needs \"ids\": what to look for");
				}

				for (int i = 0; i < ids.size(); i++) {
					if (kind == Kind.BLOCK) {
						WhimCheck.block(ids.get(i), where, warnings);
					} else if (kind == Kind.MOB) {
						// (Compared by name: "cat" is "minecraft:cat".)
						ids.set(i, ids.get(i).contains(":") ? ids.get(i) : "minecraft:" + ids.get(i));
						WhimCheck.mob(ids.get(i), where, warnings);
					}
				}

				targets.put(name, new Target(name, kind, List.copyOf(ids), json.has("radius") ? json.get("radius").getAsInt() : kind.radius,
					json.has("optional") && json.get("optional").getAsBoolean(), false));
			}
		}

		if (!params.has("steps") || params.getAsJsonArray("steps").isEmpty()) {
			throw new IllegalArgumentException("a steps whim needs a list of \"steps\"");
		}

		List<Step> steps = new ArrayList<>();
		int timeout = 20 * 20;
		int n = 0;
		for (JsonElement element : params.getAsJsonArray("steps")) {
			n++;
			JsonObject json = element.getAsJsonObject();
			if (!json.has("do")) {
				throw new IllegalArgumentException("step " + n + " has no \"do\" (" + String.join(", ", VERBS.keySet()) + ")");
			}

			String verb = json.get("do").getAsString();
			Set<String> keys = VERBS.get(verb);
			if (keys == null) {
				throw new IllegalArgumentException("step " + n + ": no step called \"" + verb + "\"" + WhimCheck.didYouMean(verb, VERBS.keySet())
					+ " (there are: " + String.join(", ", VERBS.keySet()) + ")");
			}

			String where = "step " + n + " (" + verb + ")";
			for (String key : json.keySet()) {
				if (!key.equals("do") && !key.equals("chance") && !keys.contains(key)) {
					warnings.add(where + ": no setting \"" + key + "\"" + WhimCheck.didYouMean(key, keys) + " (ignored)");
				}
			}

			String target = string(json, verb.equals("say") ? "to" : "target");
			if (target != null && !targets.containsKey(target)) {
				throw new IllegalArgumentException(where + ": no target called \"" + target + "\"" + WhimCheck.didYouMean(target, targets.keySet())
					+ " (name it under \"find\" first)");
			}

			Kind kind = target == null ? null : targets.get(target).kind();
			if (target == null && (verb.equals("walk_to") || verb.equals("use") || verb.equals("place"))) {
				throw new IllegalArgumentException(where + " needs a \"target\"");
			}

			if (verb.equals("use") && (kind == Kind.HOME || kind == Kind.SKY || kind == Kind.GROUND)) {
				throw new IllegalArgumentException(where + ": a " + kind.name().toLowerCase(Locale.ROOT) + " target is a spot, nothing to use");
			}

			if (verb.equals("use") && target != null) {
				targets.put(target, targets.get(target).withReach());
			}

			if (verb.equals("place") && kind != Kind.GROUND && kind != Kind.SKY && kind != Kind.BLOCK) {
				throw new IllegalArgumentException(where + ": it places on a ground, sky or block target");
			}

			String item = string(json, "item");
			if (item == null && Set.of("hold", "place", "eat", "drop").contains(verb)) {
				throw new IllegalArgumentException(where + " needs an \"item\"");
			}

			if (item != null) {
				WhimCheck.item(item, where, warnings);
			}

			List<String> lines = new ArrayList<>();
			if (verb.equals("think") && json.has("text")) {
				lines.add(json.get("text").getAsString());
			} else if (json.has("lines")) {
				JsonElement value = json.get("lines");
				if (value.isJsonArray()) {
					value.getAsJsonArray().forEach(line -> lines.add(line.getAsString()));
				} else {
					lines.add(value.getAsString());
				}
			}

			if (lines.isEmpty() && (verb.equals("say") || verb.equals("think"))) {
				throw new IllegalArgumentException(where + " needs " + (verb.equals("say") ? "\"lines\"" : "a \"text\""));
			}

			String at = json.has("at") ? json.get("at").getAsString() : "";
			if (verb.equals("look_at") && target == null && !LOOK_AT.contains(at)) {
				throw new IllegalArgumentException(where + " needs a \"target\", or \"at\" one of: " + String.join(", ", LOOK_AT)
					+ (at.isEmpty() ? "" : WhimCheck.didYouMean(at, LOOK_AT)));
			}

			double chance = json.has("chance") ? json.get("chance").getAsDouble() : 1.0;
			if (chance <= 0.0 || chance > 1.0) {
				warnings.add(where + ": \"chance\" is from 0 to 1 (0.5: half the time); " + chance + " taken as 1");
				chance = 1.0;
			}

			Step step = new Step(verb, target, item, List.copyOf(lines), at, integer(json, "ticks", verb.equals("look_at") ? 60 : 40),
				integer(json, verb.equals("drop") ? "count" : "times", verb.equals("jump") ? 3 : 1), integer(json, "gap", 10),
				json.has(verb.equals("pick_up") ? "radius" : "near") ? json.get(verb.equals("pick_up") ? "radius" : "near").getAsDouble()
					: verb.equals("pick_up") ? 4.0 : 2.5,
				json.has("hops") && json.get("hops").getAsBoolean(), json.has("keep") && json.get("keep").getAsBoolean(), chance);
			steps.add(step);
			timeout += ticksFor(step);
		}

		return new Plan(Map.copyOf(targets), List.copyOf(steps), timeout);
	}

	/** About how long a step can take (the whim's time limit is their sum, and some to spare). */
	private static int ticksFor(Step step) {
		return switch (step.verb()) {
			case "walk_to", "place" -> 20 * 40;
			case "look_at", "wait", "crouch" -> step.ticks() + 20;
			case "use" -> 20 * 30 + step.times() * (step.gap() + 30);
			case "jump" -> step.times() * 30 + 20;
			case "swing" -> step.times() * 10;
			case "look_around" -> step.times() * 100;
			case "eat" -> 20 * 6;
			case "pick_up" -> 20 * 10;
			default -> 40;
		};
	}

	private static String names(Enum<?>[] values) {
		List<String> names = new ArrayList<>();
		for (Enum<?> value : values) {
			names.add(value.name().toLowerCase(Locale.ROOT));
		}

		return String.join(", ", names);
	}

	@Nullable
	private static String string(JsonObject json, String key) {
		return json.has(key) ? json.get(key).getAsString() : null;
	}

	private static int integer(JsonObject json, String key, int fallback) {
		return json.has(key) ? json.get(key).getAsInt() : fallback;
	}

	// ---------------------------------------------------------------------------------------------
	// Finding its targets
	// ---------------------------------------------------------------------------------------------

	/** A target found: a block (or a spot), or an entity. */
	record Found(@Nullable BlockPos pos, @Nullable Entity entity) {
		boolean gone() {
			return this.entity != null && !this.entity.isAlive();
		}

		Vec3 eye() {
			return this.entity != null ? this.entity.getEyePosition() : Vec3.atCenterOf(this.pos);
		}

		Vec3 feet() {
			return this.entity != null ? this.entity.position() : Vec3.atBottomCenterOf(this.pos);
		}
	}

	/** Where this target is now, from what it knows (null: none about). */
	@Nullable
	static Found find(DrifterEntity drifter, Target target) {
		return switch (target.kind()) {
			case BLOCK -> {
				for (BlockPos pos : KnownBlocks.find(drifter, target.radius(), WhimActions.WatchBlock.matcher(target.ids()), 6)) {
					if (!target.reach() || Reach.canReach(drifter, pos) || KnownBlocks.standingSpotFor(drifter, pos, Set.of()) != null) {
						yield new Found(pos, null);
					}
				}

				yield null;
			}
			case MOB -> entity(WhimActions.seen(drifter, target.ids(), target.radius()));
			case FRIEND -> entity(WhimActions.friend(drifter, target.radius()));
			case COMPANY -> {
				Entity company = WhimActions.Chat.nearestCompany(drifter);
				yield company != null && company.distanceTo(drifter) <= target.radius() ? new Found(null, company) : null;
			}
			case HOME -> {
				Home home = WhimActions.homeNear(drifter, target.radius());
				yield home == null ? null : new Found(home.center, null);
			}
			case BED -> {
				BlockPos bed = WhimActions.homeNear(drifter, target.radius()) == null ? null : WhimActions.Hop.bed(drifter);
				yield bed == null ? null : new Found(bed, null);
			}
			case CHEST -> {
				Home home = WhimActions.homeNear(drifter, target.radius());
				BlockPos best = null;
				if (home != null) {
					for (BlockPos chest : home.chests()) {
						if (best == null || chest.distSqr(drifter.blockPosition()) < best.distSqr(drifter.blockPosition())) {
							best = chest;
						}
					}
				}

				yield best == null ? null : new Found(best, null);
			}
			case SKY -> {
				BlockPos spot = WhimActions.openSky(drifter, target.radius());
				yield spot == null ? null : new Found(spot, null);
			}
			case GROUND -> {
				BlockPos spot = WhimActions.freeGround(drifter, target.radius(), 1, 2);
				yield spot == null ? null : new Found(spot, null);
			}
		};
	}

	@Nullable
	private static Found entity(@Nullable Entity entity) {
		return entity == null ? null : new Found(null, entity);
	}

	/** Its block and mob targets are about its home (catch-up): each non-optional one, by the checks given. */
	static boolean aroundHome(Whim whim, @Nullable Home home, java.util.function.BiPredicate<Integer, List<String>> blocks,
		java.util.function.BiPredicate<Integer, List<String>> mobs) {
		for (Target target : plan(whim).targets().values()) {
			if (!target.optional() && (target.kind() == Kind.BLOCK && !blocks.test(target.radius(), target.ids())
				|| target.kind() == Kind.MOB && !mobs.test(target.radius(), target.ids()))) {
				return false;
			}
		}

		return true;
	}

	/** Could it have done this while away (catch-up): nobody was with it, and spots at home only if it has them. */
	static boolean plausibleAway(Whim whim, @Nullable Home home) {
		for (Target target : plan(whim).targets().values()) {
			if (target.optional()) {
				continue;
			}

			boolean there = switch (target.kind()) {
				case FRIEND, COMPANY -> false;
				case HOME -> home != null;
				case BED -> home != null && (home.houseBed != null || home.hutBuilt);
				case CHEST -> home != null && home.hasChest();
				default -> true;
			};
			if (!there) {
				return false;
			}
		}

		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// The action
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean possible(DrifterEntity drifter, Whim whim) {
		Plan plan = plan(whim);
		// What it holds, uses, places, eats or drops: carried (or listed in its needs, which the whim goal sees to).
		for (Step step : plan.steps()) {
			if (step.item() != null && step.chance() >= 1.0 && WhimGoal.count(drifter, step.item()) == 0
				&& whim.needs().stream().noneMatch(need -> need.item().equals(step.item()))) {
				return false;
			}
		}

		for (Target target : plan.targets().values()) {
			if (!target.optional() && find(drifter, target) == null) {
				return false;
			}
		}

		return true;
	}

	@Nullable
	@Override
	public Activity start(DrifterEntity drifter, Whim whim) {
		Plan plan = plan(whim);
		Map<String, Found> found = new java.util.HashMap<>();
		for (Target target : plan.targets().values()) {
			Found f = find(drifter, target);
			if (f == null && !target.optional()) {
				return null;
			}

			if (f != null) {
				found.put(target.name(), f);
			}
		}

		WhimActions.Steps steps = new WhimActions.Steps(drifter, whim.thought(), plan.timeout());
		for (Step step : plan.steps()) {
			// (A step with a chance is in or out for this time round.)
			if (step.chance() >= 1.0 || drifter.getRandom().nextDouble() < step.chance()) {
				add(drifter, whim, steps, step, step.target() == null ? null : () -> found.get(step.target()));
			}
		}

		return steps;
	}

	/** The tasks for one step (each made when its turn comes, so it reads the world then). */
	private static void add(DrifterEntity drifter, Whim whim, WhimActions.Steps steps, Step step, @Nullable Supplier<Found> target) {
		String item = step.item();
		switch (step.verb()) {
			case "walk_to" -> steps.then(() -> {
				Found f = live(target);
				if (f == null || drifter.position().distanceTo(f.feet()) <= step.near() + 0.3) {
					return null;
				}

				return new NavigateTask(drifter, f.feet(), step.near(), "walk over", NavigateTask.Pace.STROLL);
			});
			case "look_at" -> steps.then(() -> {
				if (target != null) {
					Found f = live(target);
					return f == null ? null : new WhimActions.Gaze(drifter, () -> f.gone() ? null : f.eye(), step.ticks(), step.hops(), whim.thought());
				}

				Vec3 ahead = drifter.getLookAngle().multiply(1, 0, 1).normalize();
				Vec3 eye = drifter.getEyePosition();
				Vec3 fixed = switch (step.at()) {
					case "ahead" -> eye.add(ahead.scale(20));
					case "up" -> eye.add(ahead).add(0, 10, 0);
					case "down" -> eye.add(ahead.scale(2)).add(0, -1.6, 0);
					default -> null;
				};
				return new WhimActions.Gaze(drifter, fixed != null ? () -> fixed : () -> WhimActions.skyPoint(drifter, step.at()), step.ticks(), step.hops(),
					whim.thought());
			});
			case "use" -> use(drifter, whim, steps, step, target);
			case "hold" -> steps.then(() -> WhimGoal.count(drifter, item) == 0 ? null : new SelectItemTask(drifter, WhimActions.itemMatcher(item), item));
			case "place" -> {
				steps.then(() -> {
					BlockPos at = placeAt(drifter, live(target));
					return at == null || at.closerThan(drifter.blockPosition(), 3.5) ? null
						: new NavigateTask(drifter, Vec3.atBottomCenterOf(at), 2.5, "walk over", NavigateTask.Pace.STROLL);
				});
				steps.then(() -> {
					BlockPos at = placeAt(drifter, live(target));
					return at == null || WhimGoal.count(drifter, item) == 0 ? null : new PlaceBlockTask(drifter, at, WhimActions.itemMatcher(item), item);
				});
			}
			case "wait" -> steps.then(() -> {
				Vec3 point = drifter.getEyePosition().add(drifter.getLookAngle().scale(5));
				return new WhimActions.Gaze(drifter, () -> point, step.ticks(), false, whim.thought());
			});
			case "say" -> {
				if (target != null) {
					steps.then(() -> {
						Found f = live(target);
						return f == null ? null : new WhimActions.Gaze(drifter, () -> f.gone() ? null : f.eye(), 15, false, "turn to them");
					});
				}

				steps.then(() -> {
					WhimActions.say(drifter, WhimActions.Sign.fill(drifter, step.lines().get(drifter.getRandom().nextInt(step.lines().size()))));
					return null;
				});
			}
			case "think" -> steps.then(() -> {
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), WhimActions.Sign.fill(drifter, step.lines().get(0)));
				return null;
			});
			case "jump" -> steps.then(() -> new WhimActions.Bounce(drifter, step.times(), false));
			case "swing" -> {
				for (int i = 0; i < step.times(); i++) {
					steps.then(() -> {
						drifter.swing(InteractionHand.MAIN_HAND);
						Vec3 point = drifter.getEyePosition().add(drifter.getLookAngle().scale(5));
						return new WhimActions.Gaze(drifter, () -> point, 8, false, whim.thought());
					});
				}
			}
			case "look_around" -> {
				for (int i = 0; i < step.times(); i++) {
					steps.then(() -> new LookAroundTask(drifter));
				}
			}
			case "eat" -> {
				steps.then(() -> WhimGoal.count(drifter, item) == 0 ? null : new SelectItemTask(drifter, WhimActions.itemMatcher(item), item));
				steps.then(() -> WhimGoal.count(drifter, item) == 0 ? null : new UseItemTask(drifter));
			}
			case "drop" -> steps.then(() -> {
				var matcher = WhimActions.itemMatcher(item);
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (matcher.test(stack)) {
						// Kept: a fumble it picks back up; else left where it fell, as a player who drops a thing on purpose.
						drifter.tossForward(stack.split(Math.min(step.times(), stack.getCount())), !step.keep());
						break;
					}
				}

				return null;
			});
			case "pick_up" -> steps.then(() -> new CollectItemsTask(drifter, step.near()));
			case "crouch" -> steps.then(() -> new Crouch(drifter, step.ticks()));
			default -> {
			}
		}
	}

	/** Using a block (from where it can reach it) or an entity (close enough to touch), with an item in hand if it says. */
	private static void use(DrifterEntity drifter, Whim whim, WhimActions.Steps steps, Step step, @Nullable Supplier<Found> target) {
		String item = step.item();
		steps.then(() -> {
			Found f = live(target);
			if (f == null) {
				return null;
			}

			if (f.entity() != null) {
				return f.entity().distanceTo(drifter) <= 2.5 ? null
					: new NavigateTask(drifter, f.entity().position(), 2.0, "walk up to it", NavigateTask.Pace.STROLL);
			}

			if (Reach.canReach(drifter, f.pos())) {
				return null;
			}

			BlockPos stand = KnownBlocks.standingSpotFor(drifter, f.pos(), Set.of());
			return stand == null ? null : new NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.6, "walk over", NavigateTask.Pace.STROLL);
		});
		for (int i = 0; i < step.times(); i++) {
			if (item != null) {
				steps.then(() -> WhimGoal.count(drifter, item) == 0 ? null : new SelectItemTask(drifter, WhimActions.itemMatcher(item), item));
			}

			steps.then(() -> {
				Found f = live(target);
				if (f == null || item != null && WhimGoal.count(drifter, item) == 0) {
					return null;
				}

				if (f.entity() != null) {
					return new UseOnEntityTask(drifter, f.entity(), whim.thought());
				}

				Direction face = WhimActions.UseBlock.face(drifter, f.pos());
				return face == null ? null : UseOnBlockTask.useBlock(drifter, f.pos(), face, whim.thought());
			});
			if (step.gap() > 0) {
				steps.then(() -> {
					Found f = live(target);
					return f == null ? null : new WhimActions.Gaze(drifter, () -> f.gone() ? null : f.eye(), step.gap(), false, whim.thought());
				});
			}
		}
	}

	/** The target if it's still there. */
	@Nullable
	private static Found live(@Nullable Supplier<Found> target) {
		Found f = target == null ? null : target.get();
		return f == null || f.gone() ? null : f;
	}

	/** Where a place step puts its block: the free spot itself, or on top of a block. */
	@Nullable
	private static BlockPos placeAt(DrifterEntity drifter, @Nullable Found f) {
		if (f == null || f.pos() == null) {
			return null;
		}

		BlockPos at = drifter.level().getBlockState(f.pos()).canBeReplaced() ? f.pos() : f.pos().above();
		return drifter.level().getBlockState(at).canBeReplaced() ? at : null;
	}

	/** Holds sneak for a while (a crouch, a bow to someone). */
	static final class Crouch extends Task {
		private final int ticks;
		private int t;

		Crouch(DrifterEntity drifter, int ticks) {
			super(drifter);
			this.ticks = ticks;
		}

		@Override
		public String describe() {
			return "crouch";
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 40;
		}

		@Override
		protected Status onTick() {
			VirtualKeys keys = this.drifter.getKeys();
			keys.releaseAll();
			keys.set(VirtualKeys.Key.SNEAK, true);
			return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}
}
