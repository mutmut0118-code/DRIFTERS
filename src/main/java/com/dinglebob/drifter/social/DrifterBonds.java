package com.dinglebob.drifter.social;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A Drifter's standing with other Drifters (DESIGN.md section 21 "Drifter to Drifter", milestone 12 step 1): the same
 * Trust, Fear and Familiarity it keeps for players. They meet when one first sees the other, starting from both
 * temperaments; time spent near each other builds familiarity and, unless they're competing for the same ground, trust;
 * homes close together make rivals; help in a fight and hits move it a lot. Everything comes from what it sees and feels.
 */
public class DrifterBonds {
	/** How close another Drifter must be, in sight, to count as time spent together. */
	private static final double NEAR = 16.0;
	/** Homes this close compete for the same trees, ore and ground. */
	private static final double RIVAL_HOMES = 64.0;
	private static final int TICK_EVERY = 20;
	/** Per minute together: familiarity, and trust (or its loss for rivals). */
	private static final double FAMILIARITY_PER_MINUTE = 1.0;
	private static final double TRUST_PER_MINUTE = 0.5;
	private static final double RIVAL_TRUST_PER_MINUTE = -1.0;
	private static final double HELPED_TRUST = 15.0;
	private static final double HIT_TRUST_PER_DAMAGE = -5.0;
	private static final double HIT_FEAR_PER_DAMAGE = 2.0;
	/** Trust at or above which another Drifter is a friend, at or below which a rival. */
	public static final double FRIEND = 50.0;
	public static final double RIVAL = -30.0;

	private final DrifterEntity drifter;
	private final Map<UUID, Relationship> byDrifter = new LinkedHashMap<>();

	public DrifterBonds(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	public Collection<Relationship> all() {
		return this.byDrifter.values();
	}

	@Nullable
	public Relationship get(UUID drifter) {
		return this.byDrifter.get(drifter);
	}

	/** Friend, rival or neither, as a word. */
	public static String attitude(Relationship r) {
		return r.trust >= FRIEND ? "friend" : r.trust <= RIVAL ? "rival" : "neutral";
	}

	public void tick() {
		if (this.drifter.tickCount % TICK_EVERY != 0) {
			return;
		}

		double minutes = TICK_EVERY / 1200.0;
		for (Awareness awareness : this.drifter.getSenses().tracked()) {
			if (!(awareness.entity instanceof DrifterEntity other) || other == this.drifter || !other.isAlive() || !awareness.identified
				|| !awareness.visibleNow || other.distanceTo(this.drifter) > NEAR) {
				continue;
			}

			Relationship r = this.byDrifter.get(other.getUUID());
			if (r == null) {
				r = this.meet(other);
			}

			r.name = other.getName().getString();
			r.addFamiliarity(FAMILIARITY_PER_MINUTE * minutes);
			r.addTrust((this.rivals(other) ? RIVAL_TRUST_PER_MINUTE : TRUST_PER_MINUTE) * minutes);
		}
	}

	/** First meeting: starting Trust from both temperaments (each one's stranger baseline, averaged). */
	private Relationship meet(DrifterEntity other) {
		double baseline = (this.drifter.getSocial().strangerBaseline() + other.getSocial().strangerBaseline()) / 2.0;
		Relationship r = new Relationship(other.getUUID(), other.getName().getString(), baseline);
		this.byDrifter.put(other.getUUID(), r);
		String first = baseline >= 15 ? "seems friendly" : baseline <= -15 ? "doesn't like the look of them" : "a stranger";
		this.think(String.format(Locale.ROOT, "met %s (%s)", r.name, first));
		return r;
	}

	/** Both settled within {@link #RIVAL_HOMES} of each other: the same trees, ore and ground. */
	private boolean rivals(DrifterEntity other) {
		var mine = this.drifter.getDrifterBrain().home();
		var theirs = other.getDrifterBrain().home();
		return mine != null && theirs != null && mine.dimension.equals(theirs.dimension) && mine.center.closerThan(theirs.center, RIVAL_HOMES);
	}

	private Relationship known(DrifterEntity other) {
		return this.byDrifter.computeIfAbsent(other.getUUID(), id -> new Relationship(id, other.getName().getString(),
			this.drifter.getSocial().strangerBaseline()));
	}

	public void onHurtBy(DrifterEntity other, float amount) {
		Relationship r = this.known(other);
		r.addTrust(HIT_TRUST_PER_DAMAGE * amount);
		r.addFear(HIT_FEAR_PER_DAMAGE * amount);
		this.think(other.getName().getString() + " hit it: trust " + Math.round(r.trust));
	}

	public void onHelped(DrifterEntity helper) {
		Relationship r = this.known(helper);
		r.addTrust(HELPED_TRUST);
		r.addFamiliarity(5.0);
		this.think(helper.getName().getString() + " helped it in a fight: trust " + Math.round(r.trust));
	}

	private void think(String text) {
		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), text);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Relationship r : this.byDrifter.values()) {
			list.add(r.save());
		}

		tag.put("Drifters", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.byDrifter.clear();
		for (Tag t : tag.getList("Drifters", Tag.TAG_COMPOUND)) {
			Relationship r = Relationship.load((CompoundTag)t);
			this.byDrifter.put(r.player, r);
		}
	}
}
