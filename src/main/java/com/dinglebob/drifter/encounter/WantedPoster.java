package com.dinglebob.drifter.encounter;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.Disposition;
import com.dinglebob.drifter.world.DrifterRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A wanted poster (the user, 2026-10-20: "imagine i check the bounty board and its a map to a drifters house ... and the
 * reward is super valuable. i kill the drifter, collect the bounty"). A messenger walks up with a map: a real Drifter,
 * its home (or where it was last seen) marked, its crime and the reward written on it. Bring it down and a courier comes
 * with the reward ({@link WantedReward}). The bounty stands a week.
 */
final class WantedPoster extends Encounter {
	private static final String MESSENGER = "messenger";
	/** How far off the wanted one can be. */
	private static final double NEAREST = 150.0;
	private static final double FARTHEST = 1200.0;
	private static final List<String> CRIMES = List.of("stealing a farmer's horse", "burning down a barn", "robbing a village",
		"cheating at dice, then stabbing the winner", "running off with a merchant's gold", "poaching on a lord's land", "breaking out of jail",
		"selling poisoned stew");

	/** Who's wanted, and for what. */
	record Target(UUID id, String name, BlockPos where, boolean home, String crime, int reward) {
	}

	private final Target target;
	@Nullable
	private DrifterEntity messenger;
	private boolean delivered;

	private WantedPoster(ServerPlayer player, Target target) {
		super(Encounters.Kind.WANTED, player);
		this.target = target;
	}

	/** A Drifter to be wanted: one of the world's (a bandit first), some way off in the player's world; or null. */
	@Nullable
	static Target target(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		Target best = null;
		int bestScore = -1;
		for (DrifterRegistry.Known known : DrifterRegistry.all()) {
			if (known.level() != level.dimension()) {
				continue;
			}

			double distance = Math.sqrt(known.pos().distSqr(player.blockPosition()));
			if (distance < NEAREST || distance > FARTHEST) {
				continue;
			}

			BlockPos where = known.pos();
			boolean home = false;
			String crime = CRIMES.get(Math.floorMod(known.id().hashCode(), CRIMES.size()));
			int score = 1;
			if (level.getEntity(known.id()) instanceof DrifterEntity loaded) {
				if (loaded.isTemporary()) {
					continue;
				}

				var house = loaded.getDrifterBrain().home();
				if (house != null && house.dimension.equals(level.dimension().location())) {
					where = house.center;
					home = true;
				}

				if (loaded.getSocial().disposition() == Disposition.BANDIT) {
					crime = "robbing travellers on the road";
					score = 3;
				}
			}

			score += player.getRandom().nextInt(3);
			if (score > bestScore) {
				bestScore = score;
				// Farther and nastier pays more: 3 to 8 diamonds.
				int reward = Math.min(8, 3 + (int)(distance / 300.0) + (score >= 3 ? 2 : 0));
				best = new Target(known.id(), known.name(), where, home, crime, reward);
			}
		}

		return best;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		Target target = target(player);
		if (target == null) {
			return null;
		}

		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 30.0, 42.0, null, random);
		if (spot == null) {
			return null;
		}

		WantedPoster poster = new WantedPoster(player, target);
		ItemStack map = map(level, target);
		DrifterEntity messenger = EncounterSpawner.spawn(level, spot, Archetype.CASUAL, drifter -> {
			EncounterSpawner.traits(drifter, 0.5, 0.3, 0.5, 0.3);
			drifter.getInventory().add(map);
			drifter.getInventory().add(new ItemStack(Items.IRON_SWORD));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 3));
		});
		if (messenger == null) {
			return null;
		}

		poster.join(messenger, MESSENGER);
		poster.messenger = messenger;
		EncounterSpawner.add(level, messenger);
		think(messenger, "carrying a wanted poster for " + target.name());
		return poster;
	}

	/** The poster: a map with the wanted one's home (or last sighting) marked, named, its crime and reward written on it. */
	static ItemStack map(ServerLevel level, Target target) {
		ItemStack map = MapItem.create(level, target.where().getX(), target.where().getZ(), (byte)2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, target.where(), "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.CUSTOM_NAME, Component.literal("WANTED: " + target.name()));
		map.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("For " + target.crime() + "."),
			Component.literal(target.reward() + " diamonds to whoever brings them down."),
			Component.literal(target.home() ? "Their home is marked." : "Last seen at the mark."))));
		return map;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 4;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.messenger == null || !this.messenger.isAlive()) {
			this.end("the messenger is gone");
			return;
		}

		if (this.delivered) {
			this.end("the poster is delivered");
		} else if (player == null || player.distanceTo(this.messenger) > 96.0) {
			this.end("couldn't find them");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return this.player() == null || this.delivered ? 0.0 : 0.9;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "bring " + this.playerName + " a wanted poster";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		Target target = this.target;
		return new Script(drifter, this.describe(drifter), 20 * 60)
			.then(() -> walkUpTo(drifter, player, 2.5))
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.now(() -> {
				if (drifter.distanceTo(player) > 6.0) {
					return;
				}

				say(drifter, line(drifter, "wanted.poster", "target", target.name(), "crime", target.crime(), "reward", target.reward()));
				say(drifter, line(drifter, target.home() ? "wanted.map_home" : "wanted.map_seen"));
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (stack.is(Items.FILLED_MAP)) {
						drifter.tossForward(stack.split(1));
						break;
					}
				}

				Encounters.PlayerState state = Encounters.state(this.player);
				state.wanted = new Encounters.Wanted(target.id(), target.name(), target.crime(),
					BuiltInRegistries.ITEM.getKey(Items.DIAMOND).toString(), target.reward(), this.now() + Encounters.WANTED_LASTS);
				Encounters.changed();
				this.delivered = true;
			})
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 20));
	}
}
