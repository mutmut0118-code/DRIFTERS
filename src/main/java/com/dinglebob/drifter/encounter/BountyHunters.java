package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.Relationship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Bounty hunters (the user, 2026-10-20: "i could be chilling at my base, or going somewhere, and suddenly a group of armed
 * bounty hunters are here to kill me"). A Drifter with a heavy grudge (or the friends of the Drifters a player killed)
 * put a price on the player's head a day or two ago; now three to five armed hunters, as well armed as the player or a
 * little better, come for them. They know only what word got round: where some Drifter last saw the player, or their
 * base (nobody's seen them: no hunters, yet). They walk in from out of sight, search round there, and on sight go after
 * them with no warning, relentless. Over when the player is dead (they leave, the price paid), they're all dead, or
 * they give up (ten minutes).
 */
final class BountyHunters extends Encounter {
	private static final String HUNTER = "hunter";
	/** They search within this much of where word says the player is. */
	private static final double SEARCH_RADIUS = 24.0;

	private final String issuer;
	/** Where they're looking (word they were given, then where one of them last saw the player). */
	private Vec3 lead;
	private boolean spotted;
	private boolean announced;

	private BountyHunters(ServerPlayer player, String issuer, Vec3 lead) {
		super(Encounters.Kind.BOUNTY_HUNTERS, player);
		this.issuer = issuer;
		this.lead = lead;
	}

	@Nullable
	static Encounter start(ServerPlayer player, Encounters.PlayerState state) {
		Vec3 lead = whereabouts(player);
		if (lead == null) {
			return null;
		}

		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, lead, 50.0, 80.0, null, random);
		if (spot == null) {
			return null;
		}

		String issuer = state.bounty == null ? "someone" : state.bounty.issuer();
		BountyHunters hunters = new BountyHunters(player, issuer, lead);
		int tier = EncounterSpawner.gearTier(player);
		int count = 3 + Math.min(2, tier);
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + random.nextInt(7) - 3, spot.getZ() + random.nextInt(7) - 3);
			boolean archer = i == count - 1;
			DrifterEntity hunter = EncounterSpawner.spawn(level, at == null ? spot : at, Archetype.PVP_SWEAT, drifter -> {
				EncounterSpawner.fighter(drifter, tier, archer, random);
				EncounterSpawner.traits(drifter, 0.85, 0.85, 0.15, 0.9);
				// Word of the job: who it's after and where they were seen (no warning owed: they've come for them).
				Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
				r.trust = -60.0;
				r.lastSeenPos = BlockPos.containing(lead);
			});
			if (hunter != null) {
				hunters.join(hunter, HUNTER);
				EncounterSpawner.add(level, hunter);
				think(hunter, "taking the job " + issuer + " put up: " + player.getGameProfile().getName() + ", last seen near " + BlockPos.containing(lead).toShortString());
			}
		}

		return hunters;
	}

	/**
	 * Where word has it the player is: the newest sighting any Drifter has of them, or the base one has seen (what got
	 * round; never where they really are unless someone saw). Null if no Drifter knows.
	 */
	@Nullable
	static Vec3 whereabouts(ServerPlayer player) {
		long newest = Long.MIN_VALUE;
		BlockPos best = null;
		BlockPos base = null;
		for (ServerLevel level : player.server.getAllLevels()) {
			if (level != player.serverLevel()) {
				continue;
			}

			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				Relationship r = drifter.isTemporary() ? null : drifter.getSocial().get(player.getUUID());
				if (r == null) {
					continue;
				}

				if (r.lastSeenPos != null && r.lastSeenAt > newest) {
					newest = r.lastSeenAt;
					best = r.lastSeenPos;
				}

				if (r.knownBase != null) {
					base = r.knownBase;
				}
			}
		}

		// A sighting in the last day beats a base; an old one doesn't.
		if (best != null && player.level().getGameTime() - newest < 24000L) {
			return Vec3.atBottomCenterOf(best);
		}

		return base != null ? Vec3.atBottomCenterOf(base) : best != null ? Vec3.atBottomCenterOf(best) : null;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 10;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.members.isEmpty()) {
			Encounters.clearBounty(this.player);
			this.end("the hunters are all dead");
			return;
		}

		if (player == null) {
			return;
		}

		for (DrifterEntity hunter : this.members) {
			if (sees(hunter, player)) {
				// They tell each other: where one sees them is where they all look.
				this.lead = player.position();
				hunter.getDrifterBrain().pursue(hunter, player, this.now());
				if (!this.announced) {
					this.announced = true;
					say(hunter, this.playerName + "! there's a price on your head, from " + this.issuer + ". we've come to collect.");
				}

				this.spotted = true;
			}
		}
	}

	@Override
	protected void playerDied() {
		for (DrifterEntity hunter : this.members) {
			say(hunter, "job's done");
			break;
		}

		Encounters.clearBounty(this.player);
		this.end("the player is dead");
	}

	/** However it ends, the price is spent (paid, or the hunters died or gave up): another needs another grudge. */
	@Override
	public void end(String why) {
		Encounters.clearBounty(this.player);
		super.end(why);
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		// After them in sight: the chase (its own goal) does it. Otherwise: to where word has it they are, and search.
		var pursuit = drifter.getDrifterBrain().pursuit();
		return pursuit != null && pursuit.active(drifter, this.now()) ? 0.0 : 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "hunt " + this.playerName + " for the bounty";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return player.getUUID().equals(this.player);
	}

	@Override
	public boolean relentless(DrifterEntity drifter) {
		return true;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		return search(drifter, "hunt " + this.playerName, this.playerName, () -> this.lead, SEARCH_RADIUS);
	}

	@Override
	public String status() {
		return super.status() + (this.spotted ? ", found them" : ", searching near " + BlockPos.containing(this.lead).toShortString());
	}
}
