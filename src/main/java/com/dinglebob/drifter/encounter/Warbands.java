package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.world.Battle;
import com.dinglebob.drifter.world.SoldierKit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A battle between warbands (the user, 2026-10-20: "can we add more variety into the gear? and then make that a random
 * encounter?"): some way off, two bands in their colours, six to ten a side, captains with their banners, fight it out
 * (the battle rules: the other side on sight, archers at the back). The player can watch, or join in: hit one side and it
 * turns on them; bring down one of the side that loses and the winners' captain shares the war chest. Over when a side
 * breaks (two or fewer left: they run), or after eight minutes. What the fallen carried lies where they fell. Killing a
 * soldier is no crime: it's a war.
 */
final class Warbands extends Encounter {
	/** A side breaks with this many or fewer still standing. */
	private static final int BREAKS_AT = 2;
	private static final List<String> RALLY = List.of("for %s!", "hold the line!", "%s, with me!", "charge!", "stand fast, %s!");

	private final SoldierKit.Army[] armies;
	private final String[] names = new String[2];
	private int battle = -1;
	private final Map<UUID, Integer> sideOf = new HashMap<>();
	private final List<DrifterEntity> everyone = new ArrayList<>();
	private final Set<UUID> counted = new HashSet<>();
	/** A side the player has struck: it turns on them. */
	private final boolean[] turned = new boolean[2];
	/** Soldiers of each side the player brought down. */
	private final int[] downedByPlayer = new int[2];
	private boolean rallied;
	private boolean warned;
	/** The side that won (once one breaks), and whether its captain has shared the spoils. */
	@Nullable
	private Integer winner;
	private boolean paid;
	private long wonAt;

	private Warbands(ServerPlayer player, SoldierKit.Army[] armies) {
		super(Encounters.Kind.WARBANDS, player);
		this.armies = armies;
		for (int side = 0; side < 2; side++) {
			String colour = armies[side].colour().getName().replace('_', ' ');
			this.names[side] = "the " + capitalised(colour) + " Band";
		}
	}

	private static String capitalised(String words) {
		StringBuilder out = new StringBuilder();
		for (String word : words.split(" ")) {
			out.append(out.length() == 0 ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}

		return out.toString();
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos center = EncounterSpawner.hiddenSpot(level, player.position(), 55.0, 80.0, null, random);
		if (center == null) {
			return null;
		}

		int perSide = 6 + random.nextInt(5);
		SoldierKit.Army[] armies = SoldierKit.opposed(level, SoldierKit.Quality.forTier(EncounterSpawner.gearTier(player)), random);
		Warbands war = new Warbands(player, armies);
		Battle.Started started = Battle.start(level, Vec3.atBottomCenterOf(center), perSide, 28, armies, (soldier, side) -> {
			war.join(soldier, armies[side].colour().getName());
			war.sideOf.put(soldier.getUUID(), side);
		});
		war.battle = started.id();
		war.everyone.addAll(started.soldiers());
		if (war.standing(0) < 3 || war.standing(1) < 3) {
			war.members.forEach(DrifterEntity::discard);
			Battle.stop(war.battle);
			return null;
		}

		for (DrifterEntity soldier : started.soldiers()) {
			think(soldier, "fighting for " + war.names[war.sideOf.get(soldier.getUUID())] + " against " + war.names[1 - war.sideOf.get(soldier.getUUID())]);
		}

		return war;
	}

	private int standing(int side) {
		int count = 0;
		for (DrifterEntity soldier : this.members) {
			if (soldier.isAlive() && this.sideOf.getOrDefault(soldier.getUUID(), -1) == side) {
				count++;
			}
		}

		return count;
	}

	/** The side's captain if it's still standing (the one with the banner and the war chest), else any of it standing. */
	@Nullable
	private DrifterEntity captain(int side) {
		DrifterEntity any = null;
		for (DrifterEntity soldier : this.members) {
			if (!soldier.isAlive() || this.sideOf.getOrDefault(soldier.getUUID(), -1) != side) {
				continue;
			}

			if (soldier.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.BannerItem) {
				return soldier;
			}

			any = any == null ? soldier : any;
		}

		return any;
	}

	@Override
	protected boolean sameSide(DrifterEntity one, DrifterEntity other) {
		return this.sideOf.get(one.getUUID()) != null && this.sideOf.get(one.getUUID()).equals(this.sideOf.get(other.getUUID()));
	}

	@Override
	protected long longest() {
		return 20L * 60 * 8;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		// Who the player has brought down, on each side.
		for (DrifterEntity soldier : this.everyone) {
			if (!soldier.isAlive() && this.counted.add(soldier.getUUID()) && player != null && soldier.getLastHurtByMob() == player) {
				this.downedByPlayer[this.sideOf.get(soldier.getUUID())]++;
			}
		}

		if (!this.rallied) {
			this.rallied = true;
			for (int side = 0; side < 2; side++) {
				DrifterEntity captain = this.captain(side);
				if (captain != null) {
					say(captain, String.format(Locale.ROOT, RALLY.get(captain.getRandom().nextInt(RALLY.size())), this.names[side]));
				}
			}
		}

		if (player != null && this.winner == null) {
			for (DrifterEntity soldier : this.members) {
				Integer side = this.sideOf.get(soldier.getUUID());
				boolean hit = soldier.getLastHurtByMob() == player && soldier.tickCount - soldier.getLastHurtByMobTimestamp() < 40;
				if (side != null && hit && !this.turned[side]) {
					// Struck by the player: that side turns on them.
					this.turned[side] = true;
					DrifterEntity captain = this.captain(side);
					if (captain != null) {
						say(captain, "you'll regret picking a side, " + this.playerName + "!");
					}
				}

				if (side != null && this.turned[side] && sees(soldier, player) && soldier.distanceTo(player) < 16.0) {
					soldier.getDrifterBrain().pursue(soldier, player, this.now());
				}

				if (!this.warned && side != null && sees(soldier, player) && soldier.distanceTo(player) < 24.0 && !this.turned[side]) {
					this.warned = true;
					say(soldier, "stay out of this, stranger!");
				}
			}
		}

		if (this.winner == null) {
			int[] standing = {this.standing(0), this.standing(1)};
			if (standing[0] == 0 && standing[1] == 0) {
				this.end("both bands fell");
				return;
			}

			for (int side = 0; side < 2; side++) {
				if (standing[side] <= BREAKS_AT && standing[1 - side] > standing[side]) {
					this.win(1 - side);
					return;
				}
			}

			return;
		}

		// Won: the spoils shared (if the player helped), then they go.
		boolean helped = this.downedByPlayer[1 - this.winner] > 0 && !this.turned[this.winner];
		if (this.paid || !helped || player == null || player.distanceTo(this.captain(this.winner) == null ? player : this.captain(this.winner)) > 64.0
			|| this.now() - this.wonAt > 20L * 60) {
			this.end(this.names[this.winner] + " won" + (this.paid ? ", and shared the spoils" : ""));
		}
	}

	/** One side broke: the battle's over; the losers run, the winners cheer. */
	private void win(int side) {
		this.winner = side;
		this.wonAt = this.now();
		Battle.stop(this.battle);
		DrifterEntity captain = this.captain(side);
		if (captain != null) {
			say(captain, "victory! the field is ours!");
		}

		DrifterEntity loser = this.captain(1 - side);
		if (loser != null) {
			say(loser, "fall back! fall back!");
		}

		for (DrifterEntity soldier : this.members) {
			think(soldier, this.names[side] + " won the battle");
			soldier.getDrifterBrain().endPursuit();
		}
	}

	@Override
	public void end(String why) {
		if (this.battle >= 0) {
			Battle.stop(this.battle);
		}

		super.end(why);
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		Integer side = this.sideOf.get(drifter.getUUID());
		if (this.winner == null || side == null) {
			// The fighting is the battle's (on sight, archers at the back, marching on the enemy).
			return 0.0;
		}

		if (side.intValue() != this.winner) {
			// Beaten: they run.
			return 0.97;
		}

		// The winners' captain goes to thank the player who helped.
		boolean helped = this.downedByPlayer[1 - this.winner] > 0 && !this.turned[this.winner];
		return helped && !this.paid && drifter == this.captain(this.winner) && this.player() != null ? 0.95 : 0.0;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return this.urgency(drifter) > 0.0;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		Integer side = this.sideOf.get(drifter.getUUID());
		if (side == null) {
			return "fight";
		}

		return this.winner == null ? "fight for " + this.names[side] : side.intValue() == this.winner ? "share the spoils" : "run from the field";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		Integer side = this.sideOf.get(drifter.getUUID());
		return side != null && this.turned[side] && player.getUUID().equals(this.player);
	}

	@Override
	public boolean relentless(DrifterEntity drifter) {
		Integer side = this.sideOf.get(drifter.getUUID());
		return side != null && this.turned[side];
	}

	/** It's a war: no crime in it. */
	@Override
	public Killing killing(DrifterEntity drifter, Player player) {
		return Killing.NONE;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		Integer side = this.sideOf.get(drifter.getUUID());
		ServerPlayer player = this.player();
		if (side == null || this.winner == null) {
			return null;
		}

		if (side.intValue() != this.winner) {
			// Off the field, away from the winners.
			DrifterEntity victor = this.captain(this.winner);
			Vec3 away = beyond(victor != null ? victor.position() : drifter.position().add(1.0, 0.0, 0.0), drifter.position(), 60.0);
			return new Script(drifter, "run from the field", 20 * 60).then(() -> new NavigateTask(drifter, away, 4.0, "run from the field", NavigateTask.Pace.TRAVEL));
		}

		if (player == null) {
			return null;
		}

		return new Script(drifter, "share the spoils", 20 * 45)
			.then(() -> walkUpTo(drifter, player, 2.5))
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.now(() -> {
				boolean any = false;
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (stack.is(Items.EMERALD)) {
						drifter.tossForward(stack.split(stack.getCount()));
						any = true;
					}
				}

				say(drifter, any ? "you fought well, friend. the war chest is yours." : "you fought well, friend. take what you like from the field.");
				think(drifter, this.playerName + " fought for " + this.names[this.winner]);
				this.paid = true;
			});
	}

	@Override
	public String status() {
		return super.status() + "; " + this.names[0] + " " + this.standing(0) + " standing, " + this.names[1] + " " + this.standing(1)
			+ (this.winner != null ? "; " + this.names[this.winner] + " won" : "") + "; the player brought down " + this.downedByPlayer[0] + " and "
			+ this.downedByPlayer[1];
	}
}
