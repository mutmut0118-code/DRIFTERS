package com.dinglebob.drifter.social;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The manhunt test mode (DESIGN.md section 25), hunter role: a 1v1 between a real player (the runner) and a Drifter (the
 * hunter). Off unless started by command; it changes no core rules. Toward the runner only, the Drifter is Vengeful for
 * the game (both sides agreed, so no warning first); it has a tracking compass that tells it the runner's direction when
 * it checks it (never the exact position); it respawns at the world spawn with nothing when it dies; the runner dying
 * ends the game, and the runner wins by reaching the Nether (the simple goal for early tests) or killing the dragon.
 */
public final class Manhunt {
	private Manhunt() {
	}

	public enum Goal {
		NETHER, DRAGON
	}

	@Nullable
	private static UUID hunter;
	@Nullable
	private static UUID runner;
	private static String runnerName = "";
	private static Goal goal = Goal.NETHER;

	public static boolean active() {
		return hunter != null && runner != null;
	}

	/** This Drifter is hunting this player in a manhunt. */
	public static boolean hunting(DrifterEntity drifter, Player player) {
		return active() && drifter.getUUID().equals(hunter) && player.getUUID().equals(runner);
	}

	public static boolean isHunter(DrifterEntity drifter) {
		return active() && drifter.getUUID().equals(hunter);
	}

	@Nullable
	public static UUID runner() {
		return runner;
	}

	public static String status() {
		return active() ? "manhunt: " + runnerName + " runs (goal: " + goal.name().toLowerCase(java.util.Locale.ROOT) + "), a Drifter hunts" : "no manhunt running";
	}

	public static void start(DrifterEntity drifter, ServerPlayer player, Goal winGoal) {
		hunter = drifter.getUUID();
		runner = player.getUUID();
		runnerName = player.getGameProfile().getName();
		goal = winGoal;
		prepare(drifter);
		announce(drifter.getServer(), "Manhunt! " + drifter.getName().getString() + " is hunting " + runnerName + ". "
			+ runnerName + " wins by " + (winGoal == Goal.NETHER ? "reaching the Nether" : "killing the Ender Dragon") + ".");
	}

	public static void stop(@Nullable MinecraftServer server, String why) {
		if (active() && server != null) {
			announce(server, "Manhunt over: " + why);
		}

		hunter = null;
		runner = null;
	}

	/** The hunter's standing with the runner for the game, and its tracking compass. */
	private static void prepare(DrifterEntity drifter) {
		Relationship r = drifter.getSocial().getOrMeet(runner, runnerName);
		r.trust = -Relationship.MAX_TRUST;
		r.wronged = true;
		if (r.grudges.stream().noneMatch(Relationship.Grudge::forever)) {
			r.grudges.add(new Relationship.Grudge(SocialEvent.ATTACKED, 100.0, drifter.level().getGameTime(), true));
		}

		if (drifter.getInventory().count(stack -> stack.is(Items.COMPASS)) == 0) {
			ItemStack compass = new ItemStack(Items.COMPASS);
			compass.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Tracking compass"));
			drifter.getInventory().add(compass);
		}
	}

	/**
	 * What the tracking compass shows: the runner's direction, as a point some way along it at its own height (only in the
	 * runner's dimension; elsewhere it spins). Null if it has no compass or it spins.
	 */
	@Nullable
	public static Vec3 compassPoint(DrifterEntity drifter, double distance) {
		if (!isHunter(drifter) || drifter.getInventory().count(stack -> stack.is(Items.COMPASS)) == 0 || drifter.getServer() == null) {
			return null;
		}

		ServerPlayer player = drifter.getServer().getPlayerList().getPlayer(runner);
		if (player == null || player.level() != drifter.level()) {
			return null;
		}

		Vec3 toward = player.position().subtract(drifter.position()).multiply(1.0, 0.0, 1.0);
		if (toward.lengthSqr() < 1.0) {
			return null;
		}

		return drifter.position().add(toward.normalize().scale(distance));
	}

	public static void register() {
		// The hunter dies: it comes back at the world spawn with nothing, like a manhunt hunter.
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!active()) {
				return;
			}

			if (victim instanceof DrifterEntity drifter && drifter.getUUID().equals(hunter)) {
				MinecraftServer server = drifter.getServer();
				if (server != null) {
					CompoundTag saved = new CompoundTag();
					drifter.saveWithoutId(saved);
					server.execute(() -> respawn(server, saved));
				}
			} else if (victim instanceof Player player && player.getUUID().equals(runner)) {
				stop(player.getServer(), "the hunter wins! " + runnerName + " died.");
			} else if (victim instanceof EnderDragon && goal == Goal.DRAGON) {
				stop(victim.getServer(), runnerName + " wins: the dragon is dead!");
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!active() || server.getTickCount() % 20 != 0) {
				return;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(runner);
			if (player != null && goal == Goal.NETHER && player.level().dimension() == Level.NETHER) {
				stop(server, runnerName + " wins: they reached the Nether!");
			}
		});
	}

	/** The same Drifter (memory, personality, standing) back at the world spawn, with an empty inventory. */
	private static void respawn(MinecraftServer server, CompoundTag saved) {
		ServerLevel level = server.overworld();
		DrifterEntity fresh = com.dinglebob.drifter.registry.ModEntities.DRIFTER.create(level);
		if (fresh == null) {
			return;
		}

		for (String key : new String[]{"DrifterInventory", "ArmorItems", "HandItems", "UUID", "Health", "DeathTime", "HurtTime", "Fire", "Pos",
			"Motion", "XpP", "XpLevel", "XpTotal"}) {
			saved.remove(key);
		}

		fresh.load(saved);
		BlockPos spawn = level.getSharedSpawnPos();
		fresh.moveTo(spawn.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
			spawn.getZ() + 0.5, 0.0F, 0.0F);
		fresh.setHealth(fresh.getMaxHealth());
		level.addFreshEntity(fresh);
		hunter = fresh.getUUID();
		prepare(fresh);
		announce(server, fresh.getName().getString() + " respawned and is back on the hunt.");
	}

	private static void announce(@Nullable MinecraftServer server, String text) {
		if (server != null) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("[Drifter] " + text), false);
		}
	}

	/** A hunter's quarry, or null (for the Hunt goal). */
	@Nullable
	public static Player quarry(DrifterEntity drifter) {
		return isHunter(drifter) && drifter.getServer() != null ? drifter.getServer().getPlayerList().getPlayer(runner) : null;
	}
}
