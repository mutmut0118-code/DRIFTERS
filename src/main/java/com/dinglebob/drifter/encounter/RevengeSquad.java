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
 * A revenge squad (docs/ENCOUNTERS.md 7; the user, 2026-10-20: "and the revenge one too"): the friends of a Drifter a
 * player killed come for them, a quarter of a day to a day later. Two of them (three for a murder: one that had given
 * up), armed about as well as the player. They know only what got round: where some Drifter last saw the player, else
 * where their friend died. They search round there; on sight they say why and go after them, relentless. Over when the
 * player is dead ("that was for ..."), they're all dead, or they give up (ten minutes). Killing them is self-defence.
 */
final class RevengeSquad extends Encounter {
	private static final String FRIEND = "friend";
	/** They search within this much of where they think the player is. */
	private static final double SEARCH_RADIUS = 24.0;
	/** Where they look has to be this near the player (or they're off looking somewhere else for now). */
	private static final double LEAD_WITHIN = 160.0;

	private final String victim;
	private final boolean murder;
	/** Where they're looking (what got round, then where one of them last saw the player). */
	private Vec3 lead;
	private boolean spotted;

	private RevengeSquad(ServerPlayer player, String victim, boolean murder, Vec3 lead) {
		super(Encounters.Kind.REVENGE, player);
		this.victim = victim;
		this.murder = murder;
		this.lead = lead;
	}

	@Nullable
	static Encounter start(ServerPlayer player, Encounters.PlayerState state) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		Encounters.Revenge revenge = state.revenge;
		// Where they look: the newest word of the player, else where their friend died. (By command with nothing pending: a
		// made-up friend, and they know where the player is.)
		Vec3 seen = BountyHunters.whereabouts(player);
		Vec3 died = revenge != null ? Vec3.atBottomCenterOf(revenge.where()) : player.position();
		Vec3 lead = seen != null && seen.distanceTo(player.position()) <= LEAD_WITHIN ? seen : died;
		if (lead.distanceTo(player.position()) > LEAD_WITHIN) {
			return null;
		}

		BlockPos spot = EncounterSpawner.hiddenSpot(level, lead, 40.0, 60.0, null, random);
		if (spot == null) {
			return null;
		}

		String victim = revenge != null ? revenge.victim() : com.dinglebob.drifter.identity.Usernames.generate(random, level.getServer());
		boolean murder = revenge != null && revenge.murder();
		RevengeSquad squad = new RevengeSquad(player, victim, murder, lead);
		int tier = EncounterSpawner.gearTier(player);
		// One band, in one set of colours.
		var band = EncounterSpawner.band(level, tier, random);
		int count = murder ? 3 : 2;
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + random.nextInt(5) - 2, spot.getZ() + random.nextInt(5) - 2);
			boolean archer = i == 2;
			DrifterEntity friend = EncounterSpawner.spawn(level, at == null ? spot : at, i == 0 ? Archetype.PVP_SWEAT : Archetype.CASUAL, drifter -> {
				EncounterSpawner.fighter(drifter, band, archer, random);
				EncounterSpawner.traits(drifter, 0.85, 0.8, 0.2, 1.0);
				Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
				r.trust = -80.0;
				r.lastSeenPos = BlockPos.containing(lead);
			});
			if (friend != null) {
				squad.join(friend, FRIEND);
				EncounterSpawner.add(level, friend);
				think(friend, "come to make " + player.getGameProfile().getName() + " pay for " + victim);
			}
		}

		if (squad.members.isEmpty()) {
			return null;
		}

		state.revenge = null;
		Encounters.changed();
		return squad;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 10;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.members.isEmpty()) {
			this.end("the squad is dead");
			return;
		}

		if (player == null) {
			return;
		}

		for (DrifterEntity friend : this.members) {
			if (!sees(friend, player)) {
				continue;
			}

			// They tell each other: where one sees them is where they all look.
			this.lead = player.position();
			friend.getDrifterBrain().pursue(friend, player, this.now());
			if (!this.spotted) {
				this.spotted = true;
				say(friend, line(friend, this.murder ? "revenge.found_murder" : "revenge.found", "player", this.playerName, "victim", this.victim));
				for (DrifterEntity other : this.members) {
					if (other != friend) {
						say(other, line(other, "revenge.join", "victim", this.victim));
						break;
					}
				}
			}
		}
	}

	@Override
	protected void playerDied() {
		if (!this.members.isEmpty()) {
			say(this.members.get(0), line(this.members.get(0), "revenge.done", "victim", this.victim));
		}

		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		// After them in sight: the chase (its own goal) does it. Otherwise: to where they think they are, and search.
		var pursuit = drifter.getDrifterBrain().pursuit();
		return pursuit != null && pursuit.active(drifter, this.now()) ? 0.0 : 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "make " + this.playerName + " pay for " + this.victim;
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
		return search(drifter, this.describe(drifter), this.playerName, () -> this.lead, SEARCH_RADIUS);
	}

	@Override
	public String status() {
		return super.status() + " (for " + this.victim + (this.murder ? ", murdered" : "") + ")"
			+ (this.spotted ? ", found them" : ", searching near " + BlockPos.containing(this.lead).toShortString());
	}
}
