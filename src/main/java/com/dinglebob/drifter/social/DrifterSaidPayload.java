package com.dinglebob.drifter.social;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which Drifter said a chat line (chat phase 5; user, 2026-10-23: "chat head and chat bubbles support"): sent with the
 * line to each player who hears it, so their game can put a bubble over that Drifter (Talk Bubbles) and its face next to
 * the line (Chat Heads). Those mods find a speaker among the players; a Drifter isn't one. Nothing happens on a client
 * without them.
 */
public record DrifterSaidPayload(int drifterId, String text) implements CustomPacketPayload {
	public static final Type<DrifterSaidPayload> TYPE = new Type<>(Drifter.id("said"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DrifterSaidPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, DrifterSaidPayload::drifterId, ByteBufCodecs.STRING_UTF8, DrifterSaidPayload::text, DrifterSaidPayload::new);

	public static void register() {
		PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
	}

	/** Tell this player's game who's about to say this (before the line itself, so it's there when the line comes). */
	public static void send(ServerPlayer player, DrifterEntity drifter, String text) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, new DrifterSaidPayload(drifter.getId(), text));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
