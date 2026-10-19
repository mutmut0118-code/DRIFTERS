package com.dinglebob.drifter.client.render;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * A stand-in player per fishing Drifter, on the client only (never in the world): vanilla's fishing bobber keeps itself
 * only while its owner is a player and draws its line from that player's hand, so a Drifter's bobber is given this one,
 * which copies the Drifter (where it is, which way it faces, what it holds, its arm swing) every tick.
 */
public final class DrifterFishers {
	private DrifterFishers() {
	}

	private static final Map<DrifterEntity, RemotePlayer> STAND_INS = new WeakHashMap<>();

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (STAND_INS.isEmpty()) {
				return;
			}

			STAND_INS.entrySet().removeIf(entry -> entry.getKey().isRemoved() || entry.getValue().level() != entry.getKey().level());
			STAND_INS.forEach(DrifterFishers::copy);
		});
	}

	/** This Drifter's stand-in (made the first time). */
	public static RemotePlayer standIn(DrifterEntity drifter) {
		RemotePlayer standIn = STAND_INS.get(drifter);
		if (standIn == null || standIn.level() != drifter.level()) {
			UUID id = UUID.nameUUIDFromBytes(("drifter-fisher:" + drifter.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			standIn = new RemotePlayer((ClientLevel)drifter.level(), new GameProfile(id, "Drifter"));
			STAND_INS.put(drifter, standIn);
		}

		copy(drifter, standIn);
		return standIn;
	}

	private static void copy(DrifterEntity drifter, RemotePlayer standIn) {
		standIn.xo = drifter.xo;
		standIn.yo = drifter.yo;
		standIn.zo = drifter.zo;
		standIn.xOld = drifter.xOld;
		standIn.yOld = drifter.yOld;
		standIn.zOld = drifter.zOld;
		standIn.setPos(drifter.getX(), drifter.getY(), drifter.getZ());
		standIn.yRotO = drifter.yRotO;
		standIn.xRotO = drifter.xRotO;
		standIn.setYRot(drifter.getYRot());
		standIn.setXRot(drifter.getXRot());
		standIn.yBodyRotO = drifter.yBodyRotO;
		standIn.yBodyRot = drifter.yBodyRot;
		standIn.yHeadRot = drifter.yHeadRot;
		standIn.yHeadRotO = drifter.yHeadRotO;
		standIn.setItemSlot(EquipmentSlot.MAINHAND, drifter.getMainHandItem());
		standIn.setItemSlot(EquipmentSlot.OFFHAND, drifter.getOffhandItem());
		standIn.setMainArm(drifter.getMainArm());
		standIn.setPose(drifter.getPose());
		standIn.attackAnim = drifter.attackAnim;
		standIn.oAttackAnim = drifter.oAttackAnim;
		standIn.tickCount = drifter.tickCount;
	}
}
