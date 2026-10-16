package com.dinglebob.drifter.brain;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A trip to the Nether (DESIGN.md section 19, milestone 11), kept with its brain because going through a portal makes
 * the game build the Drifter afresh on the other side: where its portal home is, where it came out, and how far along
 * the trip is.
 */
public final class NetherTrip {
	public enum Phase {
		/** Walking into its portal at home. */
		GOING,
		/** In the Nether. */
		THERE,
		/** Headed back through the portal it came out of. */
		RETURNING
	}

	public Phase phase = Phase.GOING;
	/** The inside of its portal at home (overworld). */
	public BlockPos homePortal;
	/** The inside of the portal it came out of in the Nether, once it has looked. */
	@Nullable
	public BlockPos netherPortal;
	/** Walled the portal in with cobblestone (ghast-proofing). */
	public boolean walledIn;
	/** Had its look round on arrival. */
	public boolean lookedAround;
	public long startedAt;
	/** When it got to the Nether (game time), for how long it stays. */
	public long arrivedAt;

	public NetherTrip(BlockPos homePortal, long startedAt) {
		this.homePortal = homePortal.immutable();
		this.startedAt = startedAt;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Phase", this.phase.name());
		tag.putLong("HomePortal", this.homePortal.asLong());
		if (this.netherPortal != null) {
			tag.putLong("NetherPortal", this.netherPortal.asLong());
		}

		tag.putBoolean("WalledIn", this.walledIn);
		tag.putBoolean("LookedAround", this.lookedAround);
		tag.putLong("StartedAt", this.startedAt);
		tag.putLong("ArrivedAt", this.arrivedAt);
		return tag;
	}

	public static NetherTrip load(CompoundTag tag) {
		NetherTrip trip = new NetherTrip(BlockPos.of(tag.getLong("HomePortal")), tag.getLong("StartedAt"));
		try {
			trip.phase = Phase.valueOf(tag.getString("Phase"));
		} catch (IllegalArgumentException e) {
			trip.phase = Phase.GOING;
		}

		trip.netherPortal = tag.contains("NetherPortal") ? BlockPos.of(tag.getLong("NetherPortal")) : null;
		trip.walledIn = tag.getBoolean("WalledIn");
		trip.lookedAround = tag.getBoolean("LookedAround");
		trip.arrivedAt = tag.getLong("ArrivedAt");
		return trip;
	}
}
