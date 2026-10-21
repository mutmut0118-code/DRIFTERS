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
	/** Seen this close to its own home, another Drifter's home is known. */
	private static final double BASE_SEEN = 32.0;
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
		if ((this.drifter.tickCount + this.drifter.getId()) % TICK_EVERY != 0) {
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
			r.lastSeenAt = other.level().getGameTime();
			r.lastSeenPos = other.blockPosition().immutable();
			// Seen by its own home: now it knows where they live (what a raid needs).
			var theirHome = other.getDrifterBrain().home();
			if (theirHome != null && theirHome.dimension.equals(other.level().dimension().location()) && theirHome.center.closerThan(other.blockPosition(), BASE_SEEN)) {
				r.knownBase = theirHome.center;
			}

			r.addFamiliarity(FAMILIARITY_PER_MINUTE * minutes);
			r.addTrust((this.rivals(other) ? RIVAL_TRUST_PER_MINUTE : TRUST_PER_MINUTE) * minutes);
			this.maybeGroup(other, r);
			this.share(other);
			// Close enough to talk: news passes on (milestone 22 groundwork, "news that travels").
			if (other.distanceTo(this.drifter) <= SHARE_RANGE) {
				this.drifter.getNews().meet(other, r);
				com.dinglebob.drifter.trade.DrifterDeals.meet(this.drifter, other, r);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Groups (milestone 12 step 2)
	// ---------------------------------------------------------------------------------------------

	/** How close two members must be to swap news (section 21 "Shared memory": about 8 blocks). */
	private static final double SHARE_RANGE = 8.0;
	/** Once a minute per pair at most. */
	private static final long SHARE_EVERY = 20 * 60;
	private final Map<UUID, Long> lastShared = new java.util.HashMap<>();

	/**
	 * Friends both ways (Trust 50 or more each) team up: a new group of the two, the one with more Skill (or Familiarity)
	 * leading, or the newcomer joins the other's group if it has room. Members move into the leader's home.
	 */
	private void maybeGroup(DrifterEntity other, Relationship mine) {
		Relationship theirs = other.getBonds().get(this.drifter.getUUID());
		if (mine.trust < FRIEND || theirs == null || theirs.trust < FRIEND) {
			return;
		}

		var groups = com.dinglebob.drifter.world.DrifterGroups.class;
		var myGroup = com.dinglebob.drifter.world.DrifterGroups.of(this.drifter.getUUID());
		var theirGroup = com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID());
		if (myGroup != null && myGroup == theirGroup) {
			return;
		}

		String me = this.drifter.getName().getString();
		String them = other.getName().getString();
		if (myGroup == null && theirGroup == null) {
			boolean iLead = leads(this.drifter, other);
			DrifterEntity leader = iLead ? this.drifter : other;
			DrifterEntity follower = iLead ? other : this.drifter;
			var group = com.dinglebob.drifter.world.DrifterGroups.form(leader.getUUID(), leader.getName().getString(), follower.getUUID(),
				follower.getName().getString(), groupName(leader));
			this.think("teamed up with " + them + ": \"" + group.name + "\"");
			other.getMemory().thoughts().add(other.level().getGameTime(), "teamed up with " + me + ": \"" + group.name + "\"");
			moveIn(follower, leader);
		} else if (myGroup == null && !theirGroup.full()) {
			com.dinglebob.drifter.world.DrifterGroups.join(theirGroup, this.drifter.getUUID(), me);
			this.think("joined " + them + "'s group \"" + theirGroup.name + "\"");
			DrifterEntity leader = this.drifter.level() instanceof net.minecraft.server.level.ServerLevel level
				&& level.getEntity(theirGroup.leader) instanceof DrifterEntity d ? d : other;
			moveIn(this.drifter, leader);
		}
	}

	/** The leader: more Skill, or (about the same) the one the other knows better. */
	private static boolean leads(DrifterEntity a, DrifterEntity b) {
		double skillA = a.getPersonality().get(com.dinglebob.drifter.personality.Trait.SKILL);
		double skillB = b.getPersonality().get(com.dinglebob.drifter.personality.Trait.SKILL);
		return Math.abs(skillA - skillB) > 0.05 ? skillA > skillB : a.getUUID().compareTo(b.getUUID()) < 0;
	}

	private static String groupName(DrifterEntity leader) {
		String[] words = {"Wanderers", "Crew", "Gang", "Squad", "Band", "Clan", "Club", "Team"};
		return leader.getName().getString() + "'s " + words[leader.getRandom().nextInt(words.length)];
	}

	/** Shared base (section 21): the member takes the leader's home as its own, chests and all. */
	private static void moveIn(DrifterEntity member, DrifterEntity leader) {
		var home = leader.getDrifterBrain().home();
		if (home == null) {
			return;
		}

		// All of it: the hut (built, its door and size), the farm, pen, house, mine and chests (found in review: a bare copy
		// had no hut, so the newcomer set about building a second one on top of the leader's).
		var shared = com.dinglebob.drifter.brain.Home.load(home.save());
		if (shared == null) {
			return;
		}

		// Its own chests (if it had set some down) become the group's too (found in testing: a member's old chest was left
		// out, and after it died its mate looted it like a stranger's).
		var old = member.getDrifterBrain().home();
		if (old != null && old.dimension.equals(home.dimension)) {
			for (var chest : old.chests()) {
				if (!home.chests().contains(chest)) {
					home.addChest(chest);
					shared.addChest(chest);
				}
			}
		}

		member.getDrifterBrain().setHome(shared);
		member.getMemory().thoughts().add(member.level().getGameTime(), "moved in with " + leader.getName().getString());
	}

	/**
	 * Members within about 8 blocks swap news (section 21 "Shared memory"): dangers, ore and resources and landmarks the
	 * other doesn't know, learned as "told" (a little less sure than seeing it).
	 */
	private void share(DrifterEntity other) {
		var group = com.dinglebob.drifter.world.DrifterGroups.of(this.drifter.getUUID());
		long now = this.drifter.level().getGameTime();
		if (group == null || group != com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID()) || other.distanceTo(this.drifter) > SHARE_RANGE
			|| now - this.lastShared.getOrDefault(other.getUUID(), Long.MIN_VALUE / 2) < SHARE_EVERY) {
			return;
		}

		this.lastShared.put(other.getUUID(), now);
		int told = 0;
		for (var kind : new com.dinglebob.drifter.memory.MemoryKind[] {com.dinglebob.drifter.memory.MemoryKind.DANGER,
			com.dinglebob.drifter.memory.MemoryKind.ORE, com.dinglebob.drifter.memory.MemoryKind.RESOURCE, com.dinglebob.drifter.memory.MemoryKind.LANDMARK}) {
			for (var entry : other.getMemory().find(kind, e -> e.source != com.dinglebob.drifter.memory.MemorySource.TOLD)) {
				var remembered = this.drifter.getMemory().remember(kind, entry.label, entry.pos, entry.dimension, now,
					com.dinglebob.drifter.memory.MemorySource.TOLD, entry.importance * 0.8F, null);
				if (remembered.isNew()) {
					told++;
				}
			}
		}

		if (told > 0) {
			this.think(other.getName().getString() + " told it about " + told + " places");
		}
	}

	/** First meeting: starting Trust from both temperaments (each one's stranger baseline, averaged). */
	private Relationship meet(DrifterEntity other) {
		// (Shifted by the aggression-toward-each-other setting, as strangers' trust is for players.)
		double baseline = (this.drifter.getSocial().temperBaseline() + other.getSocial().temperBaseline()) / 2.0
			+ com.dinglebob.drifter.DrifterConfig.aggressionShift(com.dinglebob.drifter.DrifterConfig.drifterAggression());
		Relationship r = new Relationship(other.getUUID(), other.getName().getString(), baseline);
		this.byDrifter.put(other.getUUID(), r);
		String first = baseline >= 15 ? "seems friendly" : baseline <= -15 ? "doesn't like the look of them" : "a stranger";
		this.think(String.format(Locale.ROOT, "met %s (%s)", r.name, first));
		return r;
	}

	/**
	 * Both settled within {@link #RIVAL_HOMES} of each other: the same trees, ore and ground. Not group mates (they share
	 * them: a base, or a town).
	 */
	private boolean rivals(DrifterEntity other) {
		var group = com.dinglebob.drifter.world.DrifterGroups.of(this.drifter.getUUID());
		if (group != null && group == com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID())) {
			return false;
		}

		var mine = this.drifter.getDrifterBrain().home();
		var theirs = other.getDrifterBrain().home();
		return mine != null && theirs != null && mine.dimension.equals(theirs.dimension) && mine.center.closerThan(theirs.center, RIVAL_HOMES);
	}

	private Relationship known(DrifterEntity other) {
		return this.byDrifter.computeIfAbsent(other.getUUID(), id -> new Relationship(id, other.getName().getString(),
			this.drifter.getSocial().temperBaseline()));
	}

	/** Debug: its trust in that Drifter, set outright. */
	public void setTrust(DrifterEntity other, double trust) {
		Relationship r = this.known(other);
		r.addTrust(trust - r.trust);
	}

	/** Debug: friends both ways at once, and teamed up (the way they would after enough time together). */
	public void teamUp(DrifterEntity other) {
		this.setTrust(other, Math.max(FRIEND + 10.0, this.known(other).trust));
		other.getBonds().setTrust(this.drifter, Math.max(FRIEND + 10.0, other.getBonds().known(this.drifter).trust));
		this.maybeGroup(other, this.known(other));
	}

	public void onHurtBy(DrifterEntity other, float amount) {
		com.dinglebob.drifter.world.DrifterWars.hurt(other, this.drifter, amount);
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
