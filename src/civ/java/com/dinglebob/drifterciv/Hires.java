package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A hired sword (shops worth more than looting, part 3, the second of the things only money buys; the user, 2026-11-12:
 * "Yes, build it"; 2026-11-13, away: "automatically go with your reccomendation"): say "fight for me" to one of a town's
 * guards, it names its price for a day, paid ahead; throw it that and it comes with you and fights for you, in its own
 * gear, for as long as it's paid for (each day's price thrown buys another day, up to {@link #MOST_DAYS} ahead). It goes
 * home when its time's up, when it's let go ("you can go home"), when its hirer turns on it or on its own nation's
 * people, is wanted in its town, or loses it for {@link #LOST} (far off, or gone from the world).
 *
 * <p>What it fights: monsters and anyone attacking its hirer, bandits and its own town's enemies in a war that its
 * hirer strikes; never its own nation's people, and it doesn't strike first at people at peace with it (it's a guard,
 * not a murderer). The money is its own: thrown to it, in its pockets. While hired it isn't on its town's rounds.
 */
final class Hires {
	private Hires() {
	}

	/** A day of it, in money points (an emerald is 6), before what its town thinks of the hirer. */
	private static final double DAY_PRICE = 18.0;
	private static final int MOST_DAYS = 7;
	private static final long DAY = 24000L;
	/** A price asked for and not paid lapses. */
	private static final long ASK_LASTS = 20L * 60 * 2;
	/** Its hirer further than {@link #FAR} (or gone) this long: it goes home. */
	private static final long LOST = 20L * 60 * 5;
	private static final double FAR = 64.0;
	/** It says its time's nearly up this long before. */
	private static final long WARNS = 20L * 60 * 2;
	/** It follows closer than this; it fights what's within {@link #FIGHTS} of it. */
	private static final double FOLLOWS = 3.0;
	private static final double FIGHTS = 24.0;
	/** A blow this lately (ticks) is a fight still on. */
	private static final int LATELY = 100;
	/** Its hirer out of its sight this long (ticks), and it at where it last saw them: it's lost them. */
	private static final long LOST_SIGHT = 100L;

	static final class Hire {
		final UUID player;
		final String playerName;
		final UUID guard;
		final String guardName;
		final UUID town;
		final String townName;
		final double dayPrice;
		/** Thrown and not yet a whole day's worth. */
		double paid;
		/** Hired until then (game time); 0: asked, not paid for yet. */
		long until;
		final long at;
		long lostSince;
		boolean warned;

		Hire(UUID player, String playerName, UUID guard, String guardName, UUID town, String townName, double dayPrice, long at) {
			this.player = player;
			this.playerName = playerName;
			this.guard = guard;
			this.guardName = guardName;
			this.town = town;
			this.townName = townName;
			this.dayPrice = dayPrice;
			this.at = at;
		}
	}

	private static final List<Hire> hires = new ArrayList<>();

	static void register() {
		DrifterEvents.RECEIVED.register(Hires::received);
		DrifterApi.registerGoal(Follow::new);
		DrifterApi.registerGoal(Fight::new);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(Hires::load);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> hires.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7) {
				tick(server);
			}
		});
		// Debug: `/drifterciv hire` lists them; `hire nearest` hires the nearest guard for a day, free (testing only).
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("drifterciv")
			.then(Commands.literal("hire").requires(source -> source.hasPermission(2)).executes(ctx -> {
				long now = ctx.getSource().getServer().overworld().getGameTime();
				List<String> lines = new ArrayList<>();
				for (Hire h : hires) {
					lines.add(h.guardName + " of " + h.townName + " for " + h.playerName + ": " + (h.until == 0 ? "asked" : (h.until - now) + " ticks left") + ", "
						+ h.paid + " toward " + h.dayPrice + " a day" + why(ctx.getSource().getServer(), h));
				}

				DrifterCivilizations.LOGGER.info("[hires] {}", String.join(" | ", lines));
				ctx.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "Nobody's hired." : String.join("\n", lines)), false);
				return 1;
			}).then(Commands.literal("nearest").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				MinecraftServer server = ctx.getSource().getServer();
				DrifterEntity best = null;
				Town bestTown = null;
				for (DrifterEntity one : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(64.0))) {
					Town town = Market.townOf(one);
					if (town != null && "guard".equals(town.lines.get(one.getUUID())) && (best == null || one.distanceTo(player) < best.distanceTo(player))) {
						best = one;
						bestTown = town;
					}
				}

				if (best == null) {
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: no guard within 64 blocks"), false);
					return 0;
				}

				UUID guardId = best.getUUID();
				hires.removeIf(h -> h.player.equals(player.getUUID()) || h.guard.equals(guardId));
				Hire hire = new Hire(player.getUUID(), player.getGameProfile().getName(), best.getUUID(), best.getName().getString(), bestTown.id, bestTown.name,
					DAY_PRICE, server.overworld().getGameTime());
				hire.until = server.overworld().getGameTime() + DAY;
				hires.add(hire);
				save(server);
				String name = hire.guardName;
				DrifterCivilizations.LOGGER.info("[hires] debug: {} hired by {} for a day", name, hire.playerName);
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + name + " is hired for a day"), true);
				return 1;
			})))));
	}

	// ---------------------------------------------------------------------------------------------
	// What the rest asks
	// ---------------------------------------------------------------------------------------------

	/** (Debug.) What it'd fight now, and what its hirer was last hurt by and last hit. */
	private static String why(MinecraftServer server, Hire h) {
		DrifterEntity guard = TownFounding.loaded(server, h.guard);
		ServerPlayer player = server.getPlayerList().getPlayer(h.player);
		if (guard == null || player == null) {
			return "";
		}

		LivingEntity by = player.getLastHurtByMob();
		LivingEntity hit = player.getLastHurtMob();
		LivingEntity target = Fight.target(guard);
		var goal = guard.getDrifterBrain().currentGoal();
		return "; health " + guard.getHealth() + ", doing " + (goal == null ? "nothing" : goal.id()) + ", would fight " + (target == null ? "nothing" : target.getName().getString())
			+ "; hirer hurt by " + (by == null ? "nothing" : by.getName().getString() + " " + (player.tickCount - player.getLastHurtByMobTimestamp()) + " ticks ago, known "
				+ Fight.known(guard, by)) + ", hit " + (hit == null ? "nothing" : hit.getName().getString() + " " + (player.tickCount - player.getLastHurtMobTimestamp()) + " ticks ago");
	}

	/** This Drifter's hire, if it's hired now. */
	@Nullable
	static Hire hired(UUID guard) {
		for (Hire h : hires) {
			if (h.until > 0 && h.guard.equals(guard)) {
				return h;
			}
		}

		return null;
	}

	@Nullable
	private static Hire of(UUID guard, UUID player) {
		for (Hire h : hires) {
			if (h.guard.equals(guard) && h.player.equals(player)) {
				return h;
			}
		}

		return null;
	}

	/** Its hirer, here in its world and near enough to be with: or null. */
	@Nullable
	private static ServerPlayer hirer(DrifterEntity drifter) {
		Hire hire = hired(drifter.getUUID());
		MinecraftServer server = drifter.getServer();
		ServerPlayer player = hire == null || server == null ? null : server.getPlayerList().getPlayer(hire.player);
		return player != null && player.isAlive() && !player.isSpectator() && player.level() == drifter.level() && player.distanceTo(drifter) <= FAR ? player : null;
	}

	private static String pay(double points) {
		return TownNotices.describe(DrifterApi.money(Math.max(0.0, points)));
	}

	private static double whole(double points) {
		return DrifterApi.moneyWorth(DrifterApi.money(Math.max(0.0, points)));
	}

	private static void say(DrifterEntity drifter, String line) {
		com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
	}

	/** How long it's still hired for, in words. */
	private static String left(long ticks) {
		long days = (ticks + DAY - 1) / DAY;
		return ticks < DAY / 4 ? "a few hours more" : ticks <= DAY ? "the rest of the day" : days + " days more";
	}

	/** One of its own side: its town's nation (or its town, if it's in none). */
	private static boolean ownSide(Town town, DrifterEntity other) {
		Town theirs = Market.townOf(other);
		return theirs != null && (theirs == town || WorldPlan.powerOf(theirs).equals(WorldPlan.powerOf(town)));
	}

	private static boolean atWar(Town town) {
		for (Town other : Towns.all()) {
			Diplomacy.Relation relation = other == town ? null : Diplomacy.relation(town, other);
			if (relation != null && relation.war) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Asking, paying
	// ---------------------------------------------------------------------------------------------

	/** Said to a townsperson: "fight for me", or to one hired, "you can go home" (true: answered). */
	static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		Town town = Market.townOf(drifter);
		MinecraftServer server = drifter.getServer();
		String what = town == null || server == null ? null : TalkApi.better(text, intent, "hire", "hire_done");
		if (what == null) {
			return false;
		}

		long now = server.overworld().getGameTime();
		Hire mine = of(drifter.getUUID(), player.getUUID());
		if (what.equals("hire_done")) {
			if (mine == null || mine.until == 0) {
				return false;
			}

			hires.remove(mine);
			save(server);
			DrifterApi.think(drifter, "let go by " + mine.playerName + ": heading home");
			return TalkApi.answer(drifter, player, "hire.dismissed", null);
		}

		if (mine != null && mine.until > 0) {
			return TalkApi.answer(drifter, player, "hire.already", null, "when", left(mine.until - now), "pay", pay(mine.dayPrice));
		}

		String no = refusal(drifter, player, town);
		if (no != null) {
			String who = "";
			for (Hire h : hires) {
				who = h.until > 0 && h.player.equals(player.getUUID()) ? h.guardName : who;
			}

			return TalkApi.answer(drifter, player, no, null, "who", who);
		}

		TownReputation.Standing standing = TownReputation.of(town, player);
		double price = whole(Math.max(1, DAY_PRICE * Friends.price(drifter, player, standing.prices)));
		double thrown = mine == null ? 0.0 : mine.paid;
		hires.remove(mine);
		Hire hire = new Hire(player.getUUID(), player.getGameProfile().getName(), drifter.getUUID(), drifter.getName().getString(), town.id, town.name, price, now);
		hire.paid = thrown;
		hires.add(hire);
		DrifterApi.think(drifter, player.getGameProfile().getName() + " asked to hire it");
		return TalkApi.answer(drifter, player, "hire.quote", null, "pay", pay(price));
	}

	/** Why it won't be hired by them now (a line's key), or null: asked when they ask, and again when they pay. */
	@Nullable
	private static String refusal(DrifterEntity drifter, Player player, Town town) {
		if (!"guard".equals(town.lines.get(drifter.getUUID()))) {
			return "hire.notguard";
		}

		TownReputation.Standing standing = TownReputation.of(town, player);
		if (standing == TownReputation.Standing.WANTED || standing == TownReputation.Standing.UNWELCOME) {
			return "hire.no";
		}

		Hire mine = hired(drifter.getUUID());
		if (mine != null && !mine.player.equals(player.getUUID())) {
			return "hire.taken";
		}

		for (Hire h : hires) {
			if (h.until > 0 && h.player.equals(player.getUUID()) && !h.guard.equals(drifter.getUUID())) {
				return "hire.one";
			}
		}

		// (Its town keeps the guards it needs: none spared in a war, and never its last one.)
		long home = town.lines.entrySet().stream().filter(e -> e.getValue().equals("guard") && hired(e.getKey()) == null).count();
		if (atWar(town) || TownBells.alarmed(town)) {
			return "hire.war";
		}

		return mine == null && home <= 1 ? "hire.needed" : null;
	}

	/** Money thrown to a guard asked or hired: toward its days. */
	private static void received(DrifterEntity drifter, Player player, ItemStack stack) {
		MinecraftServer server = drifter.getServer();
		Hire hire = of(drifter.getUUID(), player.getUUID());
		double worth = DrifterApi.moneyWorth(stack);
		if (server == null || hire == null || worth <= 0 || TownServices.takesMoney(drifter, player)) {
			return;
		}

		long now = server.overworld().getGameTime();
		hire.paid += worth;
		int days = (int)Math.floor(hire.paid / hire.dayPrice + 1.0e-6);
		String name = player.getGameProfile().getName();
		if (days < 1) {
			say(drifter, CivLines.pick(drifter, "hire.short", pay(hire.dayPrice - hire.paid) + " more for the day", "pay", pay(hire.dayPrice - hire.paid), "player",
				name));
			return;
		}

		boolean first = hire.until == 0;
		// (Things may have changed since they asked: another guard hired meanwhile, its town now at war. Its money back, then.
		// Found in review: every guard could be asked, then paid.)
		Town town = first ? Market.townOf(drifter) : null;
		String no = first && town != null && player instanceof ServerPlayer one ? refusal(drifter, one, town) : null;
		if (no != null) {
			hires.remove(hire);
			giveBack(drifter, (ServerPlayer)player, hire.paid);
			say(drifter, CivLines.pick(drifter, no, "not now", "player", name, "who", ""));
			return;
		}

		hire.paid -= days * hire.dayPrice;
		hire.until = Math.min(Math.max(hire.until, now) + days * DAY, now + MOST_DAYS * DAY);
		hire.warned = false;
		hire.lostSince = 0;
		say(drifter, CivLines.pick(drifter, first ? "hire.hired" : "hire.more", first ? "right, im with you" : "ok, im yours a while longer", "player", name, "when",
			left(hire.until - now)));
		DrifterApi.think(drifter, (first ? "hired by " : "paid on by ") + name + ": " + left(hire.until - now));
		save(server);
		if (first) {
			// (Whatever it was at is dropped: it's theirs from now. Found in the user's look at a test, 2026-11-13, "drifter did
			// not follow me at all": it went on to the mine with its town first, and had lost them by the time it turned.)
			drifter.getDrifterBrain().restart("hired by " + name);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Its time
	// ---------------------------------------------------------------------------------------------

	private static void end(MinecraftServer server, Hire hire, @Nullable DrifterEntity guard, String line, String fallback, String thought) {
		hires.remove(hire);
		save(server);
		if (guard != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(hire.player);
			if (!line.isEmpty() && player != null && player.level() == guard.level() && player.distanceTo(guard) <= 24.0) {
				say(guard, CivLines.pick(guard, line, fallback, "player", hire.playerName, "pay", pay(hire.dayPrice)));
			}

			DrifterApi.think(guard, thought);
		}

		DrifterCivilizations.LOGGER.info("[hires] {} of {} is no longer hired by {}: {}", hire.guardName, hire.townName, hire.playerName, thought);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Hire hire : List.copyOf(hires)) {
			DrifterEntity guard = TownFounding.loaded(server, hire.guard);
			ServerPlayer player = server.getPlayerList().getPlayer(hire.player);
			if (hire.until == 0) {
				if (now - hire.at > ASK_LASTS) {
					// (What was thrown short of a day: handed back if they're by, else it's the guard's, as any gift.)
					hires.remove(hire);
					if (hire.paid > 0 && guard != null && player != null && player.level() == guard.level() && player.distanceTo(guard) <= 16.0) {
						giveBack(guard, player, hire.paid);
					}
				}

				continue;
			}

			var record = DrifterApi.record(hire.guard);
			Town town = Diplomacy.byId(hire.town.toString());
			if (record != null && record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.GONE) {
				end(server, hire, null, "", "", "gone");
			} else if (now >= hire.until) {
				end(server, hire, guard, "hire.over", "thats my time. im heading home", "its time's up: heading home");
			} else if (guard != null && (town == null || !"guard".equals(town.lines.get(hire.guard)))) {
				end(server, hire, guard, "", "", "not a guard of its town any more");
			} else if (guard != null && player != null && town != null) {
				LivingEntity hit = player.getLastHurtMob();
				boolean lately = hit != null && player.tickCount - player.getLastHurtMobTimestamp() < LATELY;
				if (lately && hit instanceof DrifterEntity victim && (victim == guard || ownSide(town, victim))) {
					end(server, hire, guard, "hire.betrayed", "thats one of ours. were done", "its hirer turned on its own: done with them");
				} else if (Justice.killOnSight(town, player)) {
					end(server, hire, guard, "hire.wanted", "youre wanted back home. were done", "its hirer is wanted in its town: done with them");
				} else if (player.level() != guard.level() || player.distanceTo(guard) > FAR) {
					hire.lostSince = hire.lostSince == 0 ? now : hire.lostSince;
					if (now - hire.lostSince > LOST) {
						end(server, hire, guard, "", "", "lost its hirer: heading home");
					}
				} else {
					hire.lostSince = 0;
					nudge(guard);
					if (!hire.warned && hire.until - now < WARNS) {
						hire.warned = true;
						say(guard, CivLines.pick(guard, "hire.ending", "my times nearly up. " + pay(hire.dayPrice) + " for another day", "player", hire.playerName, "pay",
							pay(hire.dayPrice)));
					}
				}
			} else if (guard != null && player == null) {
				// (Its hirer gone from the world: it waits a little, then goes home.)
				hire.lostSince = hire.lostSince == 0 ? now : hire.lostSince;
				if (now - hire.lostSince > LOST) {
					end(server, hire, guard, "", "", "its hirer's gone: heading home");
				}
			}
		}
	}

	/** Goals that are its own life at stake: never nudged out of. */
	private static final java.util.Set<String> ITS_LIFE = java.util.Set.of("fight", "flee", "rampage", "pursue", "outofwater");

	/**
	 * Each second, for a guard with its hirer by: someone to fight for them, and it's only walking after them, it drops
	 * that to decide afresh (its own fight for its life, or the fight for them); on something of its own that it wants less
	 * than keeping by them, it drops that. (A goal under way isn't left for another until a safe point, and has a bonus: a
	 * paid guard doesn't wait for either.)
	 */
	private static void nudge(DrifterEntity guard) {
		var brain = guard.getDrifterBrain();
		Goals.Goal current = brain.currentGoal();
		if (current == null || hirer(guard) == null) {
			return;
		}

		String id = current.id();
		if (Fight.target(guard) != null) {
			if (id.equals("hired_follow")) {
				brain.restart("who hired it is in a fight");
			}

			return;
		}

		if (id.equals("hired_follow") || id.equals("hired_fight") || ITS_LIFE.contains(id)) {
			return;
		}

		double follow = 0.0;
		double now = 0.0;
		for (var scored : brain.lastScores()) {
			if (scored.goal().id().equals("hired_follow")) {
				follow = scored.base();
			}

			if (scored.goal() == current) {
				now = scored.base();
			}
		}

		if (follow > now) {
			brain.restart("who hired it is moving on");
		}
	}

	/** This much money out of its pockets, thrown back: in the piece it's worth, as much of it as it has. */
	private static void giveBack(DrifterEntity guard, ServerPlayer player, double points) {
		ItemStack owed = DrifterApi.money(points);
		var pockets = guard.getInventory();
		int left = owed.getCount();
		for (int slot = 0; slot < pockets.getContainerSize() && left > 0; slot++) {
			if (pockets.getItem(slot).is(owed.getItem())) {
				ItemStack money = pockets.removeItem(slot, Math.min(left, pockets.getItem(slot).getCount()));
				left -= money.getCount();
				guard.tossForward(money).setTarget(player.getUUID());
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// What it does while hired
	// ---------------------------------------------------------------------------------------------

	/** It keeps by its hirer. */
	static final class Follow extends Goals.Goal {
		@Override
		public String id() {
			return "hired_follow";
		}

		@Override
		public String name(Goals.Context context) {
			return "keep by who hired it";
		}

		@Override
		public double urgency(Goals.Context context) {
			// (Under a fight for its own life, which must be able to take over from it: at 1.15 a husk beside it was left alone,
			// its own fight wanting 1.25 against this goal's 1.15 and what a goal under way is given on top; the user watching
			// that test, 2026-11-13: "its not fighting for me". Whatever else it was at gives way by nudge(), not by score. And
			// not while there's someone to fight for them.)
			return hirer(context.drifter()) != null && Fight.target(context.drifter()) == null ? 0.98 : 0.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			// (Always: what it had set itself to do doesn't keep it from them.)
			return hirer(context.drifter()) != null;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 10;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			ServerPlayer player = hirer(drifter);
			if (player == null) {
				return null;
			}

			return new Activity(drifter) {
				/** A way to them not found just now: a breath before it tries again. */
				private boolean stumped;
				/** It's said it lost them (once a losing). */
				private boolean asked;
				private long stumpedSaid = Long.MIN_VALUE / 2;

				@Override
				public String describe() {
					return "keep by " + player.getGameProfile().getName();
				}

				@Override
				protected int timeoutTicks() {
					return 20 * 60 * 5;
				}

				/** They're out of its sight, and it's where it last saw them: nothing more to walk to. */
				private boolean lost() {
					var seen = this.drifter.getSenses().awarenessOf(player);
					if (seen == null || seen.visibleNow || seen.lastSeenPos == null) {
						this.asked = false;
						return false;
					}

					return this.now() - seen.lastSeenTick > LOST_SIGHT && this.drifter.position().distanceTo(seen.lastSeenPos) <= FOLLOWS + 2.0;
				}

				/** (Paid to keep by them: a step that failed isn't a reason to stop.) */
				@Override
				protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
					return false;
				}

				@Override
				protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
					this.stumped = status == Status.FAILURE && step instanceof NavigateTask;
					if (this.stumped && this.now() - this.stumpedSaid > 200L) {
						this.stumpedSaid = this.now();
						this.think("couldn't " + step.describe() + ": " + step.failureReason());
					}
				}

				@Override
				protected boolean rethink() {
					return this.current() instanceof NavigateTask && this.lost();
				}

				@Override
				protected Status next() {
					if (hirer(this.drifter) != player) {
						return Status.SUCCESS;
					}

					if (this.stumped) {
						this.stumped = false;
						return this.begin(new com.dinglebob.drifter.executor.task.WaitTask(this.drifter, "look for a way to " + player.getGameProfile().getName(),
							() -> false, 30));
					}

					// (It only knows where it last saw them: there, it looks about, and says so, once.)
					if (this.lost()) {
						// (Not in its first moments: just hired, it hasn't had a look round for them yet. Found in testing: "where did you
						// go" to someone stood beside it.)
						if (!this.asked && this.ticksRunning() > 100) {
							this.asked = true;
							say(this.drifter, CivLines.pick(this.drifter, "hire.lost", "where did you go", "player", player.getGameProfile().getName()));
							this.think("lost sight of " + player.getGameProfile().getName() + ": looking about");
						}

						return this.begin(new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter, 4, 10));
					}

					return this.begin(NavigateTask.follow(this.drifter, player, FOLLOWS));
				}
			};
		}
	}

	/** It fights for its hirer. */
	static final class Fight extends Goals.Goal {
		@Override
		public String id() {
			return "hired_fight";
		}

		@Override
		public String name(Goals.Context context) {
			return "fight for who hired it";
		}

		@Override
		public double urgency(Goals.Context context) {
			// (Over keeping by them, which has what a goal under way is given on top, and a switch needs a margin. Found in the
			// user's look at a test, 2026-11-13, "its not fighting for me": at 1.3 it stood by and watched.)
			return target(context.drifter()) != null ? 1.5 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		/**
		 * A person at its hirer it'll fight: a bandit, an enemy in a war, or a wanderer of no town its hirer didn't start on.
		 * Never someone of a town at peace defending themselves (the hirer struck them first, or they're wanted there): it's a
		 * guard, not a murderer. Found in review.
		 */
		private static boolean fair(Town town, ServerPlayer player, DrifterEntity other) {
			if (TalkApi.hostileOnSight(other)) {
				return true;
			}

			Town theirs = Market.townOf(other);
			if (theirs != null) {
				return Annals.atWar(WorldPlan.powerOf(town), WorldPlan.powerOf(theirs));
			}

			return player.getLastHurtMob() != other;
		}

		/** It has seen (or heard) this one and knows what it is: it fights what it knows of, as any Drifter. */
		private static boolean known(DrifterEntity drifter, LivingEntity other) {
			var seen = drifter.getSenses().awarenessOf(other);
			return seen != null && seen.identified;
		}

		/** Whom it'd fight now: what's at its hirer, or what its hirer's at that it'll fight. */
		@Nullable
		static LivingEntity target(DrifterEntity drifter) {
			ServerPlayer player = drifter.getHealth() < 6.0F ? null : hirer(drifter);
			Town town = player == null ? null : Market.townOf(drifter);
			if (town == null) {
				return null;
			}

			LivingEntity by = player.getLastHurtByMob();
			if (by != null && by != drifter && by.isAlive() && player.tickCount - player.getLastHurtByMobTimestamp() < LATELY && by.distanceTo(drifter) <= FIGHTS
				&& known(drifter, by) && (by instanceof Enemy || by instanceof DrifterEntity other && !ownSide(town, other) && fair(town, player, other))) {
				return by;
			}

			LivingEntity hit = player.getLastHurtMob();
			if (hit != null && hit != drifter && hit.isAlive() && player.tickCount - player.getLastHurtMobTimestamp() < LATELY && hit.distanceTo(drifter) <= FIGHTS
				&& known(drifter, hit)) {
				if (hit instanceof Enemy) {
					return hit;
				}

				// (People: bandits, and its own town's enemies in a war. Not ones at peace with it: it's a guard.)
				if (hit instanceof DrifterEntity other && !ownSide(town, other)) {
					Town theirs = Market.townOf(other);
					if (TalkApi.hostileOnSight(other) || theirs != null && Annals.atWar(WorldPlan.powerOf(town), WorldPlan.powerOf(theirs))) {
						return hit;
					}
				}
			}

			return null;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			LivingEntity target = target(drifter);
			if (target == null) {
				return null;
			}

			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "fighting " + target.getName().getString() + " for who hired it");
			return new Activity(drifter) {
				private boolean attacked;

				@Override
				public String describe() {
					return "fight for who hired it";
				}

				@Override
				protected Status next() {
					if (this.attacked || !target.isAlive() || target.distanceTo(this.drifter) > FIGHTS || this.drifter.getHealth() < 6.0F) {
						return Status.SUCCESS;
					}

					this.attacked = true;
					return this.begin(new AttackTask(this.drifter, target, target.getName().getString().toLowerCase(Locale.ROOT)));
				}
			};
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The journal
	// ---------------------------------------------------------------------------------------------

	/** The journal's Quests page: who they've hired. */
	static List<Journal.Line> lines(ServerPlayer player) {
		List<Journal.Line> lines = new ArrayList<>();
		long now = player.server.overworld().getGameTime();
		for (Hire h : hires) {
			if (h.until > 0 && h.player.equals(player.getUUID())) {
				lines.add(new Journal.Line(h.guardName + " of " + h.townName + ", a guard", left(h.until - now), Journal.GOOD, List.of(
					"Comes with you and fights for you while it's paid for. " + Character.toUpperCase(pay(h.dayPrice).charAt(0)) + pay(h.dayPrice).substring(1)
						+ " thrown to it buys another day. Say \"you can go home\" to let it go.")));
			}
		}

		return lines;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved
	// ---------------------------------------------------------------------------------------------

	private static final int VERSION = 1;

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			hires.clear();
			ListTag list = tag.getList("hires", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag h = list.getCompound(i);
				if (!h.hasUUID("player") || !h.hasUUID("guard") || !h.hasUUID("town") || h.getLong("until") <= 0) {
					continue;
				}

				Hire hire = new Hire(h.getUUID("player"), h.getString("playerName"), h.getUUID("guard"), h.getString("guardName"), h.getUUID("town"),
					h.getString("townName"), Math.max(1.0, h.getDouble("dayPrice")), 0L);
				hire.paid = h.getDouble("paid");
				hire.until = h.getLong("until");
				hires.add(hire);
			}

			return new Saved();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("version", VERSION);
			ListTag list = new ListTag();
			for (Hire hire : hires) {
				if (hire.until == 0) {
					continue;
				}

				CompoundTag h = new CompoundTag();
				h.putUUID("player", hire.player);
				h.putString("playerName", hire.playerName);
				h.putUUID("guard", hire.guard);
				h.putString("guardName", hire.guardName);
				h.putUUID("town", hire.town);
				h.putString("townName", hire.townName);
				h.putDouble("dayPrice", hire.dayPrice);
				h.putDouble("paid", hire.paid);
				h.putLong("until", hire.until);
				list.add(h);
			}

			tag.put("hires", list);
			return tag;
		}
	}

	private static Saved saved(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_hires");
	}

	static void load(MinecraftServer server) {
		hires.clear();
		saved(server);
	}

	private static void save(MinecraftServer server) {
		saved(server).setDirty();
	}
}
