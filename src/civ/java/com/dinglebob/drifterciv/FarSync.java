package com.dinglebob.drifterciv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Paper towns: the sketches and the roads sent to a player whose own game draws the far view (a LAN guest with Voxy:
 * its far-view mod runs in its program, where the server's painter isn't). The client asks once ({@link Want}); the
 * server sends everything in parts ({@link Part}: the sketches and road lines, compressed), again when they change, and
 * a small {@link State} whenever a place is built or a piece of road laid (those stop being drawn). The host's own
 * game needs none of this: it reads the server's painter.
 */
public final class FarSync {
	private FarSync() {
	}

	/** A part's most bytes (a packet to a client may be a megabyte). */
	private static final int PART = 400_000;

	/** From a client: send me the sketches (and keep me up to date). */
	public record Want() implements CustomPacketPayload {
		public static final Type<Want> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "far_want"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Want> CODEC = StreamCodec.unit(new Want());

		@Override
		public Type<Want> type() {
			return TYPE;
		}
	}

	/** One part of all the sketches and roads (index of {@code of}; the first begins anew). */
	public record Part(int index, int of, byte[] bytes) implements CustomPacketPayload {
		public static final Type<Part> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "far_part"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Part> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Part::index, ByteBufCodecs.VAR_INT,
			Part::of, ByteBufCodecs.BYTE_ARRAY, Part::bytes, Part::new);

		@Override
		public Type<Part> type() {
			return TYPE;
		}
	}

	/** What's really there now: the places built, and every road's pieces laid (a byte each, road after road). */
	public record State(List<UUID> built, byte[] pieces) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "far_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()),
			State::built, ByteBufCodecs.BYTE_ARRAY, State::pieces, State::new);

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The server's side
	// ---------------------------------------------------------------------------------------------

	/** The players who asked, and what each was last sent: {the data's version, the state's}. */
	private static final Map<UUID, long[]> WANTING = new HashMap<>();

	static void register() {
		PayloadTypeRegistry.playC2S().register(Want.TYPE, Want.CODEC);
		PayloadTypeRegistry.playS2C().register(Part.TYPE, Part.CODEC);
		PayloadTypeRegistry.playS2C().register(State.TYPE, State.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Want.TYPE, (want, context) -> WANTING.put(context.player().getUUID(), new long[] {Long.MIN_VALUE, Long.MIN_VALUE}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WANTING.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WANTING.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (WANTING.isEmpty() || server.getTickCount() % 20 != 0 || !WorldPlan.planned || !WorldPlan.ready || !Sketches.ready()) {
				return;
			}

			List<FarPaint.Road> roads = FarPaint.serverRoads();
			long data = Sketches.changes() * 31L + roads.size();
			long state = 0;
			List<UUID> built = new ArrayList<>();
			for (WorldPlan.Place place : WorldPlan.places) {
				if (place.made) {
					built.add(place.id);
					state += 1_000_003L;
				}
			}

			ByteArrayOutputStream pieces = new ByteArrayOutputStream();
			for (FarPaint.Road road : roads) {
				for (boolean laid : road.built()) {
					pieces.write(laid ? 1 : 0);
					state += laid ? 1 : 0;
				}
			}

			byte[] all = null;
			for (var entry : WANTING.entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				long[] sent = entry.getValue();
				if (player == null || !ServerPlayNetworking.canSend(player, Part.TYPE)) {
					continue;
				}

				if (sent[0] != data) {
					if (all == null) {
						all = pack(roads);
					}

					int of = Math.max(1, (all.length + PART - 1) / PART);
					for (int i = 0; i < of; i++) {
						ServerPlayNetworking.send(player, new Part(i, of, java.util.Arrays.copyOfRange(all, i * PART, Math.min(all.length, (i + 1) * PART))));
					}

					DrifterCivilizations.LOGGER.info("[far] {} sketches and {} roads sent to {} ({} bytes in {} parts)", Sketches.all().size(), roads.size(),
						player.getGameProfile().getName(), all.length, of);
					sent[0] = data;
					sent[1] = Long.MIN_VALUE;
				}

				if (sent[1] != state) {
					ServerPlayNetworking.send(player, new State(built, pieces.toByteArray()));
					sent[1] = state;
				}
			}
		});
	}

	private static byte[] pack(List<FarPaint.Road> roads) {
		CompoundTag tag = new CompoundTag();
		ListTag sketches = new ListTag();
		for (Sketch sketch : Sketches.all()) {
			sketches.add(sketch.save());
		}

		tag.put("Sketches", sketches);
		ListTag lines = new ListTag();
		for (FarPaint.Road road : roads) {
			CompoundTag one = new CompoundTag();
			long[] line = new long[road.line().size() * 2];
			byte[] bridge = new byte[road.bridge().length];
			for (int i = 0; i < road.line().size(); i++) {
				line[i * 2] = Double.doubleToLongBits(road.line().get(i)[0]);
				line[i * 2 + 1] = Double.doubleToLongBits(road.line().get(i)[1]);
				bridge[i] = (byte)(road.bridge()[i] ? 1 : 0);
			}

			one.putLongArray("Line", line);
			one.putByteArray("Bridge", bridge);
			one.putInt("Pieces", road.built().length);
			one.putLong("Salt", road.salt());
			lines.add(one);
		}

		tag.put("Roads", lines);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(tag, out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The client's side (called from its packet handlers, on its own thread)
	// ---------------------------------------------------------------------------------------------

	private static ByteArrayOutputStream arriving = new ByteArrayOutputStream();
	private static int next;
	private static List<Sketch> sketches = List.of();
	private static List<FarPaint.Road> roads = List.of();
	private static Set<UUID> built = Set.of();

	/** A part's come: kept in order; with the last, everything's read and the client's painter filled. */
	public static void part(Part part) {
		if (part.index() == 0) {
			arriving = new ByteArrayOutputStream();
			next = 0;
		}

		if (part.index() != next) {
			// (Out of order: this lot's dropped; the next whole lot begins at 0 again.)
			next = -1;
			return;
		}

		arriving.writeBytes(part.bytes());
		next++;
		if (next < part.of()) {
			return;
		}

		try {
			CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(arriving.toByteArray()), NbtAccounter.unlimitedHeap());
			List<Sketch> read = new ArrayList<>();
			for (Tag one : tag.getList("Sketches", Tag.TAG_COMPOUND)) {
				read.add(Sketch.load((CompoundTag)one));
			}

			List<FarPaint.Road> lines = new ArrayList<>();
			ListTag list = tag.getList("Roads", Tag.TAG_COMPOUND);
			for (int h = 0; h < list.size(); h++) {
				CompoundTag one = list.getCompound(h);
				long[] packed = one.getLongArray("Line");
				byte[] bridges = one.getByteArray("Bridge");
				List<double[]> line = new ArrayList<>();
				boolean[] bridge = new boolean[packed.length / 2];
				for (int i = 0; i < packed.length / 2; i++) {
					line.add(new double[] {Double.longBitsToDouble(packed[i * 2]), Double.longBitsToDouble(packed[i * 2 + 1])});
					bridge[i] = i < bridges.length && bridges[i] == 1;
				}

				lines.add(new FarPaint.Road(h, line, bridge, new boolean[Math.max(1, one.getInt("Pieces"))], one.getLong("Salt")));
			}

			sketches = read;
			roads = lines;
			FarPaint.CLIENT.set(sketches, built, roads);
			DrifterCivilizations.LOGGER.info("[far] {} sketches and {} roads came from the server ({} bytes)", read.size(), lines.size(), arriving.size());
		} catch (IOException | RuntimeException e) {
			DrifterCivilizations.LOGGER.warn("[far] the sketches from the server couldn't be read ({}); the far view is drawn without them", e.toString());
		}

		arriving = new ByteArrayOutputStream();
	}

	/** What's built now: those places' sketches and those pieces of road stop being drawn. */
	public static void state(State state) {
		built = new HashSet<>(state.built());
		int at = 0;
		for (FarPaint.Road road : roads) {
			for (int p = 0; p < road.built().length && at < state.pieces().length; p++, at++) {
				road.built()[p] = state.pieces()[at] == 1;
			}
		}

		FarPaint.CLIENT.set(sketches, built, roads);
	}

	/** The world's left: nothing of it kept. */
	public static void forget() {
		sketches = List.of();
		roads = List.of();
		built = Set.of();
		arriving = new ByteArrayOutputStream();
		next = 0;
		FarPaint.CLIENT.clear();
	}

	/** How much the client holds (a debug line). */
	public static String held() {
		int pieces = 0;
		int laid = 0;
		for (FarPaint.Road road : roads) {
			pieces += road.built().length;
			for (boolean one : road.built()) {
				laid += one ? 1 : 0;
			}
		}

		return sketches.size() + " sketches (" + built.size() + " built), " + roads.size() + " roads (" + laid + " of " + pieces + " pieces laid)";
	}
}
