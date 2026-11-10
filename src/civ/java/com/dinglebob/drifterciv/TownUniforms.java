package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TownUniform;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

/**
 * A town's uniform (modded armor in starting kits, docs/superpowers/specs/2026-11-10-modded-armor-kits-design.md; Drifter's
 * API 6): the armor set its guards wear, kept with the town, and its trim: its own (worked out from its id), or its
 * nation's while it's in one (from the nation's id and colour), shared by the nation's towns. Joining or leaving a nation
 * switches the trim its guards and leader wear, once (the user, 2026-11-10); those not loaded then switch at a later check.
 */
final class TownUniforms {
	private TownUniforms() {
	}

	/** A switch still to make on its guards and leader not loaded when it was due is given up after this many days. */
	static final long RETRIM_DAYS = 7;

	/** Its uniform now (its set chosen and kept the first time it's needed: a town from before uniforms). */
	static TownUniform uniform(Town town) {
		if (town.uniformSet.isEmpty()) {
			town.uniformSet = DrifterApi.chooseUniformSet(RandomSource.create(town.id.getMostSignificantBits() ^ town.id.getLeastSignificantBits()));
		}

		return uniform(town.id, town.uniformSet);
	}

	/** The uniform of the town with this id in this set (a town being made: a planned nation's town wears its trim from the start). */
	static TownUniform uniform(UUID town, String armorSet) {
		Nations.Nation nation = Nations.of(town);
		return nation != null ? DrifterApi.nationUniform(nation.id, nation.colour, armorSet) : DrifterApi.townUniform(town, armorSet);
	}

	private static String trim(TownUniform uniform) {
		return uniform.trimPattern() + "|" + uniform.trimMaterial();
	}

	/**
	 * Its uniform was {@code was} (before it joined or left a nation): its guards and leader switch the worn pieces carrying
	 * that trim to its trim now; any not loaded switch when they are and it's checked ({@link #check}).
	 */
	static void changed(MinecraftServer server, Town town, TownUniform was) {
		TownUniform now = uniform(town);
		String old = trim(was);
		String next = trim(now);
		if (old.equals(next)) {
			return;
		}

		// (A switch still pending from an earlier change, its people not loaded yet: kept, so they switch to the newest trim.)
		List<String> from = new ArrayList<>(froms(town));
		if (!from.contains(old)) {
			from.add(old);
		}

		from.remove(next);
		town.retrimFrom = String.join(";", from);
		town.retrimDay = Chronicle.day(server);
		if (retrim(server, town, now)) {
			town.retrimFrom = "";
		}
	}

	/** Each check: the switch still due on its guards and leader now loaded; done when all of them were, or after a week. */
	static void check(MinecraftServer server, Town town) {
		if (town.retrimFrom.isEmpty()) {
			return;
		}

		if (Chronicle.day(server) - town.retrimDay > RETRIM_DAYS || retrim(server, town, uniform(town))) {
			town.retrimFrom = "";
			Towns.save(server);
		}
	}

	private static List<String> froms(Town town) {
		return town.retrimFrom.isEmpty() ? List.of() : List.of(town.retrimFrom.split(";"));
	}

	/** Switches its loaded guards' and leader's pieces from each pending trim to {@code now}'s; true if every one was loaded. */
	private static boolean retrim(MinecraftServer server, Town town, TownUniform now) {
		boolean all = true;
		int changed = 0;
		for (UUID id : wearers(town)) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one == null) {
				all = false;
				continue;
			}

			for (String from : froms(town)) {
				String[] parts = from.split("\\|", 2);
				if (parts.length == 2) {
					changed += DrifterApi.retrim(one, new TownUniform(now.armorSet(), parts[0], parts[1]), now);
				}
			}
		}

		if (changed > 0) {
			DrifterCivilizations.LOGGER.info("[uniform] {}: {} worn pieces switched to {} {}", town.name, changed, now.trimPattern(), now.trimMaterial());
		}

		return all;
	}

	/** Who wears its uniform: its guards (of its people still) and its leader. */
	static List<UUID> wearers(Town town) {
		var group = town.group == null ? null : DrifterApi.groupById(town.group);
		List<UUID> all = new ArrayList<>();
		town.lines.forEach((id, line) -> {
			if (line.equals("guard") && (group == null || group.members.contains(id))) {
				all.add(id);
			}
		});

		if (town.leader != null && !all.contains(town.leader)) {
			all.add(town.leader);
		}

		return all;
	}

	/** For the command: its set and trims, the switch still due, and each of its guards and leader loaded with what they wear. */
	static List<String> describe(MinecraftServer server, Town town) {
		boolean chosen = town.uniformSet.isEmpty();
		TownUniform now = uniform(town);
		TownUniform own = DrifterApi.townUniform(town.id, town.uniformSet);
		Nations.Nation nation = Nations.of(town);
		List<String> lines = new ArrayList<>();
		lines.add(town.name + "'s uniform: set " + town.uniformSet + (chosen ? " (chosen now)" : "") + ", its own trim " + own.trimPattern() + " "
			+ own.trimMaterial() + (nation == null ? "" : ", wearing " + nation.name + "'s (" + nation.colour.getName() + ") " + now.trimPattern() + " "
			+ now.trimMaterial()) + (town.retrimFrom.isEmpty() ? "" : ", still to switch from " + town.retrimFrom + " (since day " + town.retrimDay + ")"));
		int unloaded = 0;
		for (UUID id : wearers(town)) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one == null) {
				unloaded++;
				continue;
			}

			StringBuilder worn = new StringBuilder();
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack stack = one.getItemBySlot(slot);
				ArmorTrim trim = stack.isEmpty() ? null : stack.get(DataComponents.TRIM);
				worn.append(worn.isEmpty() ? "" : ", ").append(stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).append(trim == null
					? " (no trim)" : " (" + trim.pattern().unwrapKey().map(k -> k.location().toString()).orElse("?") + " " + trim.material().unwrapKey().map(
					k -> k.location().toString()).orElse("?") + ")");
			}

			lines.add("  " + (id.equals(town.leader) ? "leader " : "guard ") + one.getName().getString() + ": " + worn);
		}

		if (unloaded > 0) {
			lines.add("  (" + unloaded + " not loaded)");
		}

		return lines;
	}
}
