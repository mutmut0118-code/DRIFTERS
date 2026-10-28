package com.dinglebob.drifter.social;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A town's crook (milestone 22 step 5; the user, 2026-10-28: "bandits shouldnt be in towns, kinda like a skyrim bandit but
 * if they are in a town they should be commiting several crimes to really give that bandit feel"): a Bandit living in a
 * town ({@link TownWork#crook}) doesn't kill on sight there, but every so often, when none of its town mates is looking, it
 * steals from a neighbour's chest or the town's till, or picks a fight with a town mate it doesn't like (to a few hearts,
 * not to the death). It tells the town ({@link TownWork#crime}), which judges by who saw it.
 */
public final class CrookGoal extends Goals.Goal {
	/** Between crimes (game ticks): about half a day to a day. */
	private static final int REST_MIN = 20 * 60 * 8;
	private static final int REST_MAX = 20 * 60 * 18;
	/** Its first after it settles (or after a reload). */
	private static final int FIRST_MIN = 20 * 60;
	private static final int FIRST_MAX = 20 * 60 * 4;
	/** A town mate it sees this close is watching: not now. */
	private static final double WATCHED = 16.0;
	/** How far it goes for a chest to rob. */
	private static final double RANGE = 64.0;
	/** Most it takes from one chest (items), and of how many kinds (the best there). */
	private static final int TAKES = 32;
	private static final int KINDS = 3;
	/** Not worth taking: less than this each (points; an iron ingot is 2, a loaf of bread about 1). */
	private static final double WORTHLESS = 0.4;

	/** When each crook may go at it next (game time; not saved: a reload starts it afresh). */
	private static final Map<UUID, Long> next = new HashMap<>();
	/** The last chest each robbed (it tries another next time). */
	private static final Map<UUID, BlockPos> lastChest = new HashMap<>();
	/** Chests it found nothing worth taking in, till when it leaves them be (game time). */
	private static final Map<UUID, Map<BlockPos, Long>> empty = new HashMap<>();
	private static final long EMPTY_FOR = 24000L;

	@Override
	public String id() {
		return "crook";
	}

	@Override
	public String name(Goals.Context context) {
		return "up to no good";
	}

	@Nullable
	private static TownWork town(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town != null && town.crook(drifter) ? town : null;
	}

	/** Debug (`/drifterciv town crook`): it goes at it soon. */
	public static void soon(DrifterEntity drifter) {
		next.put(drifter.getUUID(), drifter.level().getGameTime() + 20 * 5);
	}

	private static boolean due(DrifterEntity drifter) {
		long now = drifter.level().getGameTime();
		long at = next.computeIfAbsent(drifter.getUUID(), id -> now + FIRST_MIN + drifter.getRandom().nextInt(FIRST_MAX - FIRST_MIN));
		return now >= at;
	}

	private static void rest(DrifterEntity drifter) {
		next.put(drifter.getUUID(), drifter.level().getGameTime() + REST_MIN + drifter.getRandom().nextInt(REST_MAX - REST_MIN));
	}

	/** One of its town mates it sees now, near: someone would see. */
	private static boolean watched(DrifterEntity drifter) {
		var mine = DrifterGroups.of(drifter.getUUID());
		for (var awareness : drifter.getSenses().tracked()) {
			if (awareness.visibleNow && awareness.entity instanceof DrifterEntity other && other.isAlive() && mine != null
				&& DrifterGroups.of(other.getUUID()) == mine && other.distanceTo(drifter) <= WATCHED) {
				return true;
			}
		}

		return false;
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (town(drifter) == null || context.threat().urgent() || drifter.getHealth() < drifter.getMaxHealth() * 0.6F || !due(drifter)) {
			return 0.0;
		}

		// (An itch that wins over most chores; more so in the dark. Found in testing: at 0.55 the crook was nearly always
		// busy with something else.)
		return drifter.level().isDay() ? 0.66 : 0.76;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = town(drifter);
		if (town == null) {
			return null;
		}

		// A fight with a town mate it can't stand (now and then, or when there's no chest to rob), else a theft; neither: it
		// tries again later.
		BlockPos chest = chest(drifter, town);
		DrifterEntity mark = chest == null || drifter.getRandom().nextFloat() < 0.4F ? mark(drifter) : null;
		if (mark != null) {
			rest(drifter);
			Brawl.start(drifter, mark, town);
			return null;
		}

		if (chest == null) {
			next.put(drifter.getUUID(), drifter.level().getGameTime() + 20 * 60);
			return null;
		}

		// (Interrupted on the way, it tries again soon; the rest comes once it's done the deed.)
		next.put(drifter.getUUID(), drifter.level().getGameTime() + 20 * 60 * 2);
		lastChest.put(drifter.getUUID(), chest);
		return new Theft(drifter, town, chest);
	}

	/**
	 * A chest to rob, one of those it has seen: the town's (its store, its till) or a neighbour's, picked at random, not the
	 * one it robbed last.
	 */
	@Nullable
	private static BlockPos chest(DrifterEntity drifter, TownWork town) {
		BlockPos last = lastChest.get(drifter.getUUID());
		long now = drifter.level().getGameTime();
		Map<BlockPos, Long> bare = empty.computeIfAbsent(drifter.getUUID(), id -> new HashMap<>());
		bare.values().removeIf(until -> until < now);
		java.util.List<BlockPos> known = new java.util.ArrayList<>();
		var dimension = drifter.level().dimension().location();
		for (MemoryEntry e : drifter.getMemory().find(MemoryKind.STORAGE, entry -> true)) {
			BlockPos pos = e.blockPos();
			if (e.dimension.equals(dimension) && !e.label.equals(com.dinglebob.drifter.perception.Senses.CHEST_CART)
				&& pos.closerThan(drifter.blockPosition(), RANGE) && !bare.containsKey(pos) && town.othersChest(drifter, pos)) {
				known.add(pos);
			}
		}

		if (known.size() > 1) {
			known.remove(last);
		}

		return known.isEmpty() ? null : known.get(drifter.getRandom().nextInt(known.size()));
	}

	/** The town mate it likes least, in sight and near (not the leader: that's a plot, not a brawl). */
	@Nullable
	private static DrifterEntity mark(DrifterEntity drifter) {
		var mine = DrifterGroups.of(drifter.getUUID());
		DrifterEntity best = null;
		double lowest = Double.MAX_VALUE;
		for (var awareness : drifter.getSenses().tracked()) {
			if (!awareness.visibleNow || !(awareness.entity instanceof DrifterEntity other) || !other.isAlive() || mine == null
				|| DrifterGroups.of(other.getUUID()) != mine || other.getUUID().equals(mine.leader) || other.distanceTo(drifter) > WATCHED) {
				continue;
			}

			var r = drifter.getBonds().get(other.getUUID());
			double trust = r == null ? 0.0 : r.trust;
			if (trust < lowest) {
				lowest = trust;
				best = other;
			}
		}

		return best;
	}

	/** What one of these is worth to a thief (points): money as money, the rest as Drifters trade it. */
	static double value(ItemStack stack) {
		return Math.max(ItemValues.each(stack), com.dinglebob.drifter.trade.Money.worth(stack) / Math.max(1, stack.getCount()));
	}

	/** The best kinds of thing in the chest (now it's open in front of it), worth taking. */
	private static Set<net.minecraft.world.item.Item> best(net.minecraft.world.Container chest) {
		Map<net.minecraft.world.item.Item, Double> kinds = new HashMap<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (!stack.isEmpty() && value(stack) >= WORTHLESS) {
				kinds.merge(stack.getItem(), value(stack), Math::max);
			}
		}

		Set<net.minecraft.world.item.Item> best = new java.util.HashSet<>();
		kinds.entrySet().stream().sorted(Map.Entry.<net.minecraft.world.item.Item, Double>comparingByValue().reversed()).limit(KINDS)
			.forEach(entry -> best.add(entry.getKey()));
		return best;
	}

	/** To the chest, a look round, and what's worth taking (if nobody's watching when it gets there). */
	private static final class Theft extends Activity {
		private final TownWork town;
		private final BlockPos chest;
		private int walks;
		private boolean took;
		private double before;

		Theft(DrifterEntity drifter, TownWork town, BlockPos chest) {
			super(drifter);
			this.town = town;
			this.chest = chest;
		}

		@Override
		public String describe() {
			return "sneak to the chest at " + this.chest.toShortString();
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 90;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		private double carried() {
			double sum = 0.0;
			var inventory = this.drifter.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty()) {
					sum += value(stack) * stack.getCount();
				}
			}

			return sum;
		}

		@Override
		protected Status next() {
			if (this.took) {
				double gain = this.carried() - this.before;
				if (gain <= 0.0) {
					this.think("nothing worth taking in the chest at " + this.chest.toShortString());
					empty.computeIfAbsent(this.drifter.getUUID(), id -> new HashMap<>()).put(this.chest, this.now() + EMPTY_FOR);
					next.put(this.drifter.getUUID(), this.now() + 20 * 60);
					return Status.SUCCESS;
				}

				this.think("stole from the chest at " + this.chest.toShortString());
				rest(this.drifter);
				this.town.crime(this.drifter, "theft", null, this.chest);
				return Status.SUCCESS;
			}

			net.minecraft.world.Container container = com.dinglebob.drifter.hands.Containers.get(this.drifter.level(), this.chest);
			if (container == null) {
				return this.fail("the chest is gone");
			}

			if (Reach.canReach(this.drifter, this.chest)) {
				if (watched(this.drifter)) {
					// (Someone's about: another time.)
					next.put(this.drifter.getUUID(), this.now() + 20 * 60 * 2);
					return this.fail("not now: someone's watching");
				}

				this.took = true;
				this.before = this.carried();
				Set<net.minecraft.world.item.Item> best = best(container);
				return this.begin(new ContainerTask(this.drifter, this.chest, ContainerTask.Mode.TAKE, stack -> best.contains(stack.getItem()), "steal",
					TAKES, null));
			}

			if (this.walks++ > 2) {
				return this.fail("can't get to the chest");
			}

			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
			Vec3 target = spot != null ? Vec3.atBottomCenterOf(spot) : Vec3.atCenterOf(this.chest);
			return this.begin(new NavigateTask(this.drifter, target, spot != null ? 0.5 : 2.5, "sneak to the chest"));
		}
	}

	/**
	 * A fight it picks with a town mate: to a few hearts on either side, not to the death (a blow that would take either
	 * below that doesn't land, and it's over). Half a minute at most.
	 */
	public static final class Brawl {
		private Brawl() {
		}

		/** Either one at this health: it's over. */
		private static final float DOWN_TO = 8.0F;
		private static final float LAST_HEARTS = 4.0F;
		private static final long LONGEST = 20L * 30;

		private record Fight(DrifterEntity crook, DrifterEntity mark, long started) {
		}

		private static final Map<UUID, Fight> fights = new HashMap<>();
		/** After a brawl, how long blows between the two don't land ("crook:mark" -> until, game time). */
		private static final Map<String, Long> calming = new HashMap<>();
		private static final long CALM = 20L * 10;

		public static void register() {
			ServerTickEvents.END_SERVER_TICK.register(Brawl::tick);
			ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
				// (Just over: no more blows. Found in testing: the crook kept on with its chase and killed the one it went for.)
				DrifterEntity hitter = source.getEntity() instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
				if (hitter != null && (calming.getOrDefault(hitter.getUUID() + ":" + entity.getUUID(), Long.MIN_VALUE) > entity.level().getGameTime()
					|| calming.getOrDefault(entity.getUUID() + ":" + hitter.getUUID(), Long.MIN_VALUE) > entity.level().getGameTime())) {
					return false;
				}

				Fight fight = of(entity, source.getEntity());
				if (fight != null && entity.getHealth() - amount < LAST_HEARTS) {
					end(fight);
					return false;
				}

				return true;
			});
		}

		/** The brawl between this one (hurt) and the one hitting it, if they're in one. */
		@Nullable
		private static Fight of(LivingEntity hurt, @Nullable net.minecraft.world.entity.Entity by) {
			DrifterEntity hitter = by instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(by);
			if (hitter == null) {
				return null;
			}

			for (Fight fight : fights.values()) {
				if (fight.crook == hitter && fight.mark == hurt || fight.mark == hitter && fight.crook == hurt) {
					return fight;
				}
			}

			return null;
		}

		/** In a brawl now (either side). */
		public static boolean on(DrifterEntity drifter) {
			return fights.values().stream().anyMatch(fight -> fight.crook == drifter || fight.mark == drifter);
		}

		static void start(DrifterEntity crook, DrifterEntity mark, TownWork town) {
			long now = crook.level().getGameTime();
			fights.put(crook.getUUID(), new Fight(crook, mark, now));
			String name = mark.getName().getString();
			ChatLines.speak(crook, Lines.pick(crook, "crook.brawl", "who", name));
			crook.getMemory().thoughts().add(now, "picking a fight with " + name);
			crook.getDrifterBrain().restart("picked a fight with " + name);
			crook.getDrifterBrain().pursue(crook, mark, now);
			town.crime(crook, "fight", mark, crook.blockPosition());
		}

		private static void tick(MinecraftServer server) {
			if (server.getTickCount() % 10 != 0 || fights.isEmpty()) {
				return;
			}

			ending.retainAll(fights.keySet());
			for (Iterator<Fight> it = fights.values().iterator(); it.hasNext();) {
				Fight fight = it.next();
				DrifterEntity crook = fight.crook;
				DrifterEntity mark = fight.mark;
				boolean over = !crook.isAlive() || crook.isRemoved() || !mark.isAlive() || mark.isRemoved() || crook.level() != mark.level()
					|| crook.level().getGameTime() - fight.started > LONGEST || crook.getHealth() <= DOWN_TO || mark.getHealth() <= DOWN_TO
					|| crook.distanceTo(mark) > 32.0 || ending.remove(crook.getUUID());
				if (over) {
					it.remove();
					calm(fight);
					continue;
				}

				crook.getDrifterBrain().pursue(crook, mark, crook.level().getGameTime());
			}
		}

		/** Over at the next check (not in the middle of a blow: the crook's brain starts afresh then). */
		private static void end(Fight fight) {
			ending.add(fight.crook.getUUID());
		}

		private static final Set<UUID> ending = new java.util.HashSet<>();

		/** Both stop (the one it went for too: it was only a brawl). */
		private static void calm(Fight fight) {
			long now = fight.crook.level().getGameTime();
			for (DrifterEntity one : new DrifterEntity[]{fight.crook, fight.mark}) {
				one.getDrifterBrain().endPursuit();
				one.setTarget(null);
				one.setLastHurtByMob(null);
			}

			// (It drops the chase too.)
			fight.crook.getDrifterBrain().restart("the fight with " + fight.mark.getName().getString() + " is over");
			calming.values().removeIf(until -> until < now);
			calming.put(fight.crook.getUUID() + ":" + fight.mark.getUUID(), now + CALM);

			fight.crook.getMemory().thoughts().add(now, "the fight with " + fight.mark.getName().getString() + " is over");
			fight.mark.getMemory().thoughts().add(now, fight.crook.getName().getString() + " picked a fight with it");
		}
	}
}
