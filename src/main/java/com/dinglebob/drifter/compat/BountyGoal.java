package com.dinglebob.drifter.compat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Doing bounties (milestone 21, Bountiful; the user: "yes"). With a board it has seen close by, in the daytime with
 * nothing pressing, now and then it goes over and reads it: a bounty asking for things it can spare (what it carries,
 * beyond what it keeps for itself, as when it trades) it takes and turns in at once, as a player would, for the reward.
 * Bounties that ask for kills it leaves: what it kills isn't counted for its helper player. A board it has read it leaves
 * alone for half a day.
 */
public final class BountyGoal extends Goals.Goal {
	/** How far it goes to a board it knows. */
	private static final int BOARD_RANGE = 48;
	/** A board it has read, not again for this long. */
	private static final long READ_AGAIN_AFTER = 12000L;
	private static final double URGENCY = 0.35;

	/** When each Drifter last read each board (not saved: a restart lets it look again). */
	private static final Map<UUID, Map<BlockPos, Long>> read = new HashMap<>();

	@Nullable
	private BlockPos board;
	private long checkedAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "bounty";
	}

	@Override
	public String name(Goals.Context context) {
		return "see what's on the bounty board";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Block block = BountifulCompat.board();
		if (block == null || context.threat().urgent() || drifter.inCombat() || Schedule.isNight(drifter)
			|| drifter.level().dimension() != net.minecraft.world.level.Level.OVERWORLD || drifter.isTemporary()) {
			return 0.0;
		}

		long now = drifter.level().getGameTime();
		if (now - this.checkedAt >= 20 * 5) {
			this.checkedAt = now;
			this.board = null;
			Map<BlockPos, Long> mine = read.getOrDefault(drifter.getUUID(), Map.of());
			for (BlockPos pos : KnownBlocks.find(drifter, BOARD_RANGE, state -> state.is(block), 4)) {
				if (now - mine.getOrDefault(pos, Long.MIN_VALUE / 2) >= READ_AGAIN_AFTER) {
					this.board = pos;
					break;
				}
			}
		}

		// (Read since it was picked: not again yet.)
		if (this.board != null && now - read.getOrDefault(drifter.getUUID(), Map.of()).getOrDefault(this.board, Long.MIN_VALUE / 2) < READ_AGAIN_AFTER) {
			this.board = null;
		}

		// The greedy and the curious go sooner.
		var p = drifter.getPersonality();
		return this.board == null ? 0.0 : URGENCY + 0.1 * p.get(com.dinglebob.drifter.personality.Trait.GREED)
			+ 0.05 * p.get(com.dinglebob.drifter.personality.Trait.CURIOSITY);
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos board = this.board;
		Block block = BountifulCompat.board();
		if (board == null && block != null) {
			// (Only when made to by a debug command: a board it read lately too.)
			board = KnownBlocks.find(drifter, BOARD_RANGE, state -> state.is(block), 1).stream().findFirst().orElse(null);
		}

		return board == null ? null : new BountyRun(drifter, board);
	}

	/** Which bounty on this board it can do now with what it can spare (the one paying most), or null. */
	@Nullable
	static BountifulCompat.Offer doable(DrifterEntity drifter, List<BountifulCompat.Offer> offers) {
		BountifulCompat.Offer best = null;
		int bestPay = -1;
		for (BountifulCompat.Offer offer : offers) {
			if (offer.objectives().isEmpty() || !offer.objectives().stream().allMatch(entry -> entry.item() && spare(drifter, entry.content()) >= entry.amount())) {
				continue;
			}

			int pay = offer.rewards().stream().mapToInt(BountifulCompat.Entry::amount).sum();
			if (pay > bestPay) {
				bestPay = pay;
				best = offer;
			}
		}

		return best;
	}

	/** How many it can spare of an item (or of a tag's items), as when it trades. */
	private static int spare(DrifterEntity drifter, String content) {
		if (content.startsWith("#")) {
			var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse(content.substring(1)));
			Set<Item> seen = new java.util.HashSet<>();
			int total = 0;
			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack stack = drifter.getInventory().getItem(slot);
				if (stack.is(tag) && seen.add(stack.getItem())) {
					total += com.dinglebob.drifter.trade.Trading.surplus(drifter, stack.getItem());
				}
			}

			return total;
		}

		Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(content)).orElse(null);
		return item == null ? 0 : spare(drifter, item);
	}

	/**
	 * How many of one item it can spare: what it sells, over what it keeps (as when it trades); else never its gear, and
	 * it keeps 8 of a food, 32 of a building block, 4 of anything else.
	 */
	private static int spare(DrifterEntity drifter, Item item) {
		Integer keeps = com.dinglebob.drifter.trade.Trading.keeps(item);
		if (keeps != null) {
			return com.dinglebob.drifter.trade.Trading.surplus(drifter, item);
		}

		ItemStack sample = item.getDefaultInstance();
		if (sample.isDamageableItem()) {
			return 0;
		}

		int keep = sample.has(net.minecraft.core.component.DataComponents.FOOD) ? 8 : com.dinglebob.drifter.hands.Tools.BUILDING_BLOCK.test(sample) ? 32 : 4;
		return Math.max(0, drifter.getInventory().count(stack -> stack.is(item)) - keep);
	}

	/** Over to the board, read it, take a bounty it can do and turn it in. */
	private static final class BountyRun extends Activity {
		private final BlockPos board;
		private int step;
		private ItemStack paper = ItemStack.EMPTY;
		private String what = "";

		BountyRun(DrifterEntity drifter, BlockPos board) {
			super(drifter);
			this.board = board;
		}

		@Override
		public String describe() {
			return "see what's on the bounty board";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 90;
		}

		@Override
		protected Status next() {
			DrifterEntity drifter = this.drifter;
			ServerLevel level = (ServerLevel)drifter.level();
			switch (this.step++) {
				case 0 -> {
					if (Reach.canReach(drifter, this.board)) {
						return this.next();
					}

					BlockPos stand = KnownBlocks.standingSpotFor(drifter, this.board, Set.of());
					return stand == null ? this.fail("no way up to the board")
						: this.begin(new NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.6, "walk to the bounty board", NavigateTask.Pace.STROLL));
				}
				case 1 -> {
					return this.begin(new LookAtTask(drifter, Vec3.atCenterOf(this.board), 20));
				}
				case 2 -> {
					// It has read it (what's there now): not again for a while, whatever it finds.
					read.computeIfAbsent(drifter.getUUID(), id -> new HashMap<>()).put(this.board, level.getGameTime());
					List<BountifulCompat.Offer> offers = FakePlayerBridge.act(drifter, helper -> BountifulCompat.offers(level, this.board, helper));
					BountifulCompat.Offer offer = doable(drifter, offers);
					if (offer == null) {
						// What they want, a few (so it's clear why not).
						List<String> wants = new java.util.ArrayList<>();
						for (BountifulCompat.Offer o : offers) {
							for (BountifulCompat.Entry entry : o.objectives()) {
								String want = entry.amount() + " " + entry.content().replace("minecraft:", "").replace('_', ' ') + (entry.item() ? "" : " killed");
								if (wants.size() < 5 && !wants.contains(want)) {
									wants.add(want);
								}
							}
						}

						this.think("read the bounty board: nothing on it it can do now (" + offers.size() + " bounties; they want " + String.join(", ", wants) + ")");
						return Status.SUCCESS;
					}

					this.paper = FakePlayerBridge.act(drifter, helper -> BountifulCompat.take(level, this.board, helper, offer.slot()));
					if (this.paper.isEmpty() || !drifter.getInventory().add(this.paper.copy())) {
						return Status.SUCCESS;
					}

					this.what = describe(offer);
					this.think("took a bounty: " + this.what);
					return this.begin(new SelectItemTask(drifter, BountifulCompat::isBounty, "the bounty"));
				}
				case 3 -> {
					Direction face = face(drifter, this.board);
					return face == null ? this.fail("can't reach the board") : this.begin(UseOnBlockTask.useBlock(drifter, this.board, face, "turn in the bounty"));
				}
				default -> {
					boolean done = drifter.getInventory().count(BountifulCompat::isBounty) == 0;
					this.think(done ? "turned in the bounty (" + this.what + ")" : "couldn't turn in the bounty");
					return Status.SUCCESS;
				}
			}
		}

		private static String describe(BountifulCompat.Offer offer) {
			StringBuilder line = new StringBuilder();
			for (BountifulCompat.Entry entry : offer.objectives()) {
				line.append(line.length() == 0 ? "" : ", ").append(entry.amount()).append(" ").append(entry.content().replace("minecraft:", "").replace('_', ' '));
			}

			line.append(" for ");
			int at = line.length();
			for (BountifulCompat.Entry entry : offer.rewards()) {
				line.append(line.length() == at ? "" : ", ").append(entry.amount()).append(" ").append(entry.content().replace("minecraft:", "").replace('_', ' '));
			}

			return line.toString();
		}

		/** A face of the board it can see and reach from here. */
		@Nullable
		private static Direction face(DrifterEntity drifter, BlockPos pos) {
			Direction best = null;
			double bestDistance = Double.MAX_VALUE;
			for (Direction face : Direction.values()) {
				var point = Reach.aimPoint(drifter, pos, face);
				if (point.isPresent() && point.get().distanceTo(drifter.getEyePosition()) < bestDistance) {
					bestDistance = point.get().distanceTo(drifter.getEyePosition());
					best = face;
				}
			}

			return best;
		}
	}
}
