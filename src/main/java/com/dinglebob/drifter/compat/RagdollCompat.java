package com.dinglebob.drifter.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.mojang.authlib.GameProfile;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Sable Player Ragdoll and Ragdoll Corpse (optional; user, 2026-10-17: "can we make drifters have player ragdoll
 * aswell?"). A Drifter that dies becomes a player ragdoll, in its own skin (a profile whose UUID lands on its default
 * skin, as the game picks a skinless player's), wearing its armor and holding what it held, flung the way the killing
 * blow sent it; its body goes at once. With Ragdoll Corpse too, and a player near enough to have seen it fall, the
 * ragdoll is a corpse holding its things (opened like a player's corpse, there till it's emptied); otherwise its things
 * drop as before and the ragdoll goes in time. All through reflection: neither mod is needed.
 */
public final class RagdollCompat {
	private RagdollCompat() {
	}

	/** Fastest it's flung, metres a second. */
	private static final double MOST_FLING = 3.0;

	/** How near a player must be for a dead Drifter to leave a corpse rather than a ragdoll that goes. */
	private static final double CORPSE_WITNESS = 48.0;

	private static boolean looked;
	@Nullable
	private static Method spawnPlayerless;
	@Nullable
	private static Method neverRule;
	@Nullable
	private static Method defaultRule;
	@Nullable
	private static Method sessionId;
	@Nullable
	private static Method setCorpse;
	@Nullable
	private static Method setGrabDisabled;
	@Nullable
	private static Method applyEquipment;
	@Nullable
	private static Constructor<?> snapshotOf;
	// Ragdoll Corpse
	@Nullable
	private static Method registerSession;
	@Nullable
	private static Method corpseData;
	@Nullable
	private static Method store;
	@Nullable
	private static Method noRestore;

	private static void look() {
		if (looked) {
			return;
		}

		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("sable_player_ragdoll")) {
			return;
		}

		try {
			Class<?> api = Class.forName("dev.leo.sableplayerragdoll.api.RagdollAPI");
			Class<?> rule = Class.forName("dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule");
			Class<?> session = Class.forName("dev.leo.sableplayerragdoll.api.PlayerlessRagdollSession");
			Class<?> snapshot = Class.forName("dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot");
			spawnPlayerless = api.getMethod("spawnPlayerless", ServerLevel.class, Vec3.class, double.class, GameProfile.class, Vec3.class, rule);
			neverRule = rule.getMethod("never");
			defaultRule = rule.getMethod("defaultRule");
			sessionId = session.getMethod("id");
			setCorpse = api.getMethod("setCorpse", ServerLevel.class, UUID.class, boolean.class);
			setGrabDisabled = api.getMethod("setGrabDisabled", ServerLevel.class, UUID.class, boolean.class);
			applyEquipment = api.getMethod("applyEquipmentSnapshot", ServerLevel.class, UUID.class, snapshot);
			snapshotOf = snapshot.getConstructor(Map.class, Map.class, Map.class);
			Drifter.LOGGER.info("[Drifter] Sable Player Ragdoll found: Drifters become ragdolls when they die");
			// Its own mob ragdoll for a mob a player kills (a Drifter is one): not for Drifters while they get a player
			// ragdoll of their own (two would fall).
			Class<?> bus = Class.forName("dev.leo.sableplayerragdoll.fabric.platform.RagdollEvents");
			Class<?> mobStart = Class.forName("dev.leo.sableplayerragdoll.mob.api.MobRagdollStartEvent");
			Method entity = mobStart.getMethod("entity");
			Method cancel = mobStart.getMethod("setCanceled", boolean.class);
			Object eventBus = bus.getField("EVENT_BUS").get(null);
			java.util.function.Consumer<Object> noMobRagdoll = event -> {
				try {
					if (entity.invoke(event) instanceof DrifterEntity && DrifterConfig.ragdolls()) {
						cancel.invoke(event, true);
					}
				} catch (ReflectiveOperationException e) {
					Drifter.LOGGER.warn("[Drifter] couldn't stop a mob ragdoll for a Drifter", e);
				}
			};
			bus.getMethod("addListener", Class.class, java.util.function.Consumer.class).invoke(eventBus, mobStart, noMobRagdoll);
			if (FabricLoader.getInstance().isModLoaded("ragdoll_corpse")) {
				Class<?> sessions = Class.forName("dev.leo.ragdollcorpse.corpse.CorpseRagdollSessions");
				Class<?> data = Class.forName("dev.leo.ragdollcorpse.corpse.CorpseSavedData");
				Class<?> target = Class.forName("dev.leo.ragdollcorpse.corpse.CorpseSavedData$RestoreTarget");
				registerSession = sessions.getDeclaredMethod("register", session);
				registerSession.setAccessible(true);
				corpseData = data.getMethod("get", ServerLevel.class);
				store = data.getMethod("store", UUID.class, List.class, List.class, String.class, UUID.class, net.minecraft.core.BlockPos.class, snapshot);
				noRestore = target.getMethod("none");
				Drifter.LOGGER.info("[Drifter] Ragdoll Corpse found: a Drifter killed near a player leaves a corpse with its things");
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			Drifter.LOGGER.warn("[Drifter] Sable Player Ragdoll is installed but its API wasn't as expected; Drifters won't ragdoll", e);
			spawnPlayerless = null;
			registerSession = null;
		}
	}

	public static boolean active() {
		look();
		return spawnPlayerless != null && DrifterConfig.ragdolls();
	}

	/**
	 * The Drifter just died: a ragdoll where it fell. Returns the ragdoll's id if it's a corpse that will hold its things
	 * (pass it to {@link #fillCorpse}), a random id if it's only a ragdoll, null if there's none (the body then dies as
	 * usual).
	 */
	@Nullable
	public static Ragdoll onDeath(DrifterEntity drifter, DamageSource source) {
		if (!active() || !(drifter.level() instanceof ServerLevel level) || drifter.isInLava() || drifter.getY() < level.getMinBuildHeight()) {
			return null;
		}

		boolean corpse = registerSession != null && level.getNearestPlayer(drifter, CORPSE_WITNESS) != null;
		try {
			// (Its motion in blocks a tick; the ragdoll takes metres a second.)
			Vec3 fling = drifter.getDeltaMovement().scale(20.0);
			// (Not too hard: a ragdoll launched fast came out in one stiff piece.)
			if (fling.length() > MOST_FLING) {
				fling = fling.normalize().scale(MOST_FLING);
			}

			Object rule = corpse ? neverRule.invoke(null) : defaultRule.invoke(null);
			Object session = spawnPlayerless.invoke(null, level, drifter.position(), (double)drifter.getYRot(), profile(drifter), fling, rule);
			if (session == null) {
				return null;
			}

			UUID id = (UUID)sessionId.invoke(session);
			Object snapshot = snapshot(drifter);
			if (corpse) {
				registerSession.invoke(null, session);
				setCorpse.invoke(null, level, id, true);
				setGrabDisabled.invoke(null, level, id, true);
			}

			applyEquipment.invoke(null, level, id, snapshot);
			return new Ragdoll(id, corpse, snapshot);
		} catch (ReflectiveOperationException | RuntimeException e) {
			Drifter.LOGGER.warn("[Drifter] couldn't make {}'s ragdoll", drifter.getName().getString(), e);
			return null;
		}
	}

	/** A dead Drifter's ragdoll: its id, whether it's a corpse holding its things, and what it wears. */
	public record Ragdoll(UUID id, boolean corpse, Object equipment) {
	}

	/**
	 * Its things into its corpse (as many as a corpse holds; the rest are handed back to drop). Returns what didn't fit,
	 * or all of them if the corpse couldn't take them.
	 */
	public static List<ItemStack> fillCorpse(DrifterEntity drifter, Ragdoll ragdoll, List<ItemStack> things) {
		if (!ragdoll.corpse() || store == null || !(drifter.level() instanceof ServerLevel level)) {
			return things;
		}

		List<ItemStack> inside = new ArrayList<>();
		List<ItemStack> rest = new ArrayList<>();
		for (ItemStack stack : things) {
			(inside.size() < CORPSE_SLOTS ? inside : rest).add(stack);
		}

		try {
			List<Object> targets = new ArrayList<>();
			for (int i = 0; i < inside.size(); i++) {
				targets.add(noRestore.invoke(null));
			}

			Object data = corpseData.invoke(null, level);
			store.invoke(data, ragdoll.id(), inside, targets, drifter.getName().getString(), drifter.getUUID(), drifter.blockPosition(), ragdoll.equipment());
			return rest;
		} catch (ReflectiveOperationException | RuntimeException e) {
			Drifter.LOGGER.warn("[Drifter] couldn't put {}'s things in its corpse", drifter.getName().getString(), e);
			return things;
		}
	}

	/** Slots in a Ragdoll Corpse corpse. */
	private static final int CORPSE_SLOTS = 54;

	private static Object snapshot(DrifterEntity drifter) throws ReflectiveOperationException {
		Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = drifter.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				worn.put(slot, stack.copy());
			}
		}

		return snapshotOf.newInstance(worn, Map.of(), Map.of());
	}

	/**
	 * A profile that shows its skin: Drifters wear the game's default skins, which a player without one of their own gets
	 * by their UUID, so a UUID near its own that the game would give this skin. Its name as the profile's (a player name's
	 * letters only, 16 at most).
	 */
	static GameProfile profile(DrifterEntity drifter) {
		int want = skinIndex(drifter.getSkinTexture(), drifter.hasSlimArms());
		UUID base = drifter.getUUID();
		UUID id = base;
		for (long k = 0; k < 4096; k++) {
			UUID candidate = new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() ^ k);
			if (Math.floorMod(candidate.hashCode(), SKINS * 2) == want) {
				id = candidate;
				break;
			}
		}

		String name = drifter.getName().getString().replaceAll("[^A-Za-z0-9_]", "");
		return new GameProfile(id, name.isEmpty() ? "Drifter" : name.substring(0, Math.min(16, name.length())));
	}

	/** The game's default skins, in its order: these nine slim, then the same nine wide. */
	private static final List<String> SKIN_NAMES = List.of("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri");
	private static final int SKINS = 9;

	private static int skinIndex(net.minecraft.resources.ResourceLocation texture, boolean slim) {
		String path = texture.getPath();
		String file = path.substring(path.lastIndexOf('/') + 1).replace(".png", "");
		int at = SKIN_NAMES.indexOf(file);
		boolean slimFile = path.contains("/slim/");
		return (slimFile || slim && !path.contains("/wide/") ? 0 : SKINS) + Math.max(0, at);
	}
}
