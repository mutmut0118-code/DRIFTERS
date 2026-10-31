package com.dinglebob.drifter.hands;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a Drifter's hands are at, told to the players watching it, for Touch'n Motion on their side (the client's
 * DrifterTouchNMotion hands it on as that mod's own word from a player's game). A player's game tells the mod's server
 * part what its own player's hands do; a Drifter has no game, but the server does every one of its actions itself. So:
 * the block or creature it uses (held a few ticks), the block it breaks, the container it has open and each stack it moves
 * there; and once, the acts the mod plays out (feeding, milking, shearing, dressing an armour stand, planting a seed).
 * Only to players whose game takes it; nothing else reads it.
 */
public final class HandSignals {
	private HandSignals() {
	}

	/** As Touch'n Motion's own (its HandsState and HandsAct). */
	public static final int USE = 1, ATTACK = 2, BLOCK = 4, ENTITY = 8;
	public static final int FEED = 0, MILK = 1, SHEAR = 2, STAND = 3, SEED = 4;
	/** How long a use or a blow counts as held (a player's click is a press of the button). */
	private static final int USE_TICKS = 6;
	private static final int ATTACK_TICKS = 6;

	private static final class Hands {
		long useUntil;
		long attackUntil;
		@Nullable
		BlockPos block;
		int face;
		Vec3 hit = Vec3.ZERO;
		int entity = -1;
		@Nullable
		BlockPos menu;
		int actions;
		@Nullable
		HandsPayload sent;
	}

	private static final Map<DrifterEntity, Hands> HANDS = new WeakHashMap<>();

	public static void register() {
		PayloadTypeRegistry.playS2C().register(HandsPayload.TYPE, HandsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ActPayload.TYPE, ActPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	private static Hands of(DrifterEntity drifter) {
		return HANDS.computeIfAbsent(drifter, d -> new Hands());
	}

	/** Its helper uses a block (ServerPlayerGameMode#useItemOn): a door, a button, a chest, a seed in farmland, a block put down. */
	public static void usedOn(DrifterEntity drifter, BlockHitResult hit, ItemStack stack, InteractionHand hand) {
		Hands hands = of(drifter);
		long now = drifter.level().getGameTime();
		hands.useUntil = now + USE_TICKS;
		hands.block = hit.getBlockPos().immutable();
		hands.face = hit.getDirection().get3DDataValue();
		hands.hit = hit.getLocation();
		hands.entity = -1;
		if (drifter.level().getBlockState(hit.getBlockPos()).getBlock() instanceof FarmBlock && hit.getDirection() == Direction.UP
			&& stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
			act(drifter, SEED, -1, hit.getLocation(), hand);
		}
	}

	/** Its helper uses a creature (Player#interactOn): feeding, milking, shearing, an armour stand, a trade. */
	public static void usedOn(DrifterEntity drifter, Entity target, ItemStack stack, InteractionHand hand) {
		Hands hands = of(drifter);
		hands.useUntil = drifter.level().getGameTime() + USE_TICKS;
		hands.block = null;
		hands.entity = target.getId();
		hands.hit = target.getBoundingBox().getCenter();
		int kind = -1;
		if (target instanceof ArmorStand) {
			kind = STAND;
		} else if (stack.is(Items.BUCKET) && (target instanceof Cow || target instanceof Goat)) {
			kind = MILK;
		} else if (stack.is(Items.SHEARS) && target instanceof Shearable shearable && shearable.readyForShearing()) {
			kind = SHEAR;
		} else if (target instanceof Animal animal && animal.isFood(stack)) {
			kind = FEED;
		}

		if (kind >= 0) {
			act(drifter, kind, target.getId(), kind == STAND ? target.position().add(0.0, 1.2, 0.0) : target.getEyePosition(), hand);
		}
	}

	/** Its helper hits a creature (Player#attack). */
	public static void attacked(DrifterEntity drifter, Entity target) {
		Hands hands = of(drifter);
		hands.attackUntil = drifter.level().getGameTime() + ATTACK_TICKS;
		hands.block = null;
		hands.entity = target.getId();
	}

	/** Breaking this block (each tick of it: MineTask). */
	public static void mining(DrifterEntity drifter, BlockPos pos) {
		Hands hands = of(drifter);
		hands.attackUntil = drifter.level().getGameTime() + ATTACK_TICKS;
		hands.entity = -1;
		if (!pos.equals(hands.block) || hands.useUntil > drifter.level().getGameTime()) {
			// Where its look meets the block, as a player's crosshair does.
			Vec3 eye = drifter.getEyePosition();
			Vec3 end = eye.add(drifter.getViewVector(1.0F).scale(6.0));
			BlockHitResult hit = drifter.level().getBlockState(pos).getShape(drifter.level(), pos).clip(eye, end, pos);
			if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
				hit = drifter.level().clip(new ClipContext(eye, pos.getCenter(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, drifter));
			}

			hands.block = pos.immutable();
			hands.face = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit.getDirection().get3DDataValue() : 1;
			hands.hit = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit.getLocation() : pos.getCenter();
		}

		hands.useUntil = 0;
	}

	/** A container opened at this block (ContainerTask), or closed (null). */
	public static void menu(DrifterEntity drifter, @Nullable BlockPos pos) {
		if (pos == null && !HANDS.containsKey(drifter)) {
			return;
		}

		of(drifter).menu = pos == null ? null : pos.immutable();
	}

	/** A stack moved in the open container. */
	public static void moved(DrifterEntity drifter) {
		Hands hands = of(drifter);
		hands.actions = hands.actions + 1 & 0x7FFF;
	}

	private static void act(DrifterEntity drifter, int kind, int entity, Vec3 at, InteractionHand hand) {
		boolean mainHand = hand == InteractionHand.MAIN_HAND;
		ActPayload payload = new ActPayload(drifter.getId(), kind, entity, at.x, at.y, at.z, mainHand);
		for (ServerPlayer player : PlayerLookup.tracking(drifter)) {
			if (ServerPlayNetworking.canSend(player, ActPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Each tick: what changed, to those watching. */
	private static void tick() {
		if (HANDS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<DrifterEntity, Hands>> it = HANDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<DrifterEntity, Hands> entry = it.next();
			DrifterEntity drifter = entry.getKey();
			Hands hands = entry.getValue();
			if (drifter.isRemoved()) {
				it.remove();
				continue;
			}

			HandsPayload now = payload(drifter, hands);
			if (now.equals(hands.sent)) {
				if (now.idle()) {
					it.remove();
				}

				continue;
			}

			hands.sent = now;
			for (ServerPlayer player : PlayerLookup.tracking(drifter)) {
				if (ServerPlayNetworking.canSend(player, HandsPayload.TYPE)) {
					ServerPlayNetworking.send(player, now);
				}
			}
		}
	}

	private static HandsPayload payload(DrifterEntity drifter, Hands hands) {
		long now = drifter.level().getGameTime();
		int flags = 0;
		if (hands.useUntil > now) {
			flags |= USE;
		}

		if (hands.attackUntil > now) {
			flags |= ATTACK;
		}

		BlockPos block = null;
		int entity = -1;
		if (flags != 0 && hands.block != null) {
			flags |= BLOCK;
			block = hands.block;
		} else if (flags != 0 && hands.entity >= 0) {
			flags |= ENTITY;
			entity = hands.entity;
		}

		Vec3 hit = block == null ? Vec3.ZERO : hands.hit;
		return new HandsPayload(drifter.getId(), flags, block, block == null ? 0 : hands.face, hit.x, hit.y, hit.z, entity, hands.menu != null,
			hands.menu, hands.menu == null ? 0 : hands.actions);
	}

	/** A Drifter's hands now: as Touch'n Motion's HandsState, with nothing it can't do (a throttle, typing, a train). */
	public record HandsPayload(int drifter, int flags, @Nullable BlockPos block, int face, double hitX, double hitY, double hitZ, int entity,
		boolean menu, @Nullable BlockPos menuPos, int actions) implements CustomPacketPayload {
		public static final Type<HandsPayload> TYPE = new Type<>(Drifter.id("hands"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HandsPayload> CODEC = StreamCodec.of(HandsPayload::write, HandsPayload::read);

		public boolean idle() {
			return this.flags == 0 && !this.menu;
		}

		private static void write(RegistryFriendlyByteBuf out, HandsPayload p) {
			out.writeVarInt(p.drifter);
			out.writeByte(p.flags);
			if (p.block != null) {
				out.writeBoolean(true);
				out.writeBlockPos(p.block);
				out.writeByte(p.face);
				out.writeDouble(p.hitX);
				out.writeDouble(p.hitY);
				out.writeDouble(p.hitZ);
			} else {
				out.writeBoolean(false);
			}

			out.writeVarInt(p.entity);
			out.writeBoolean(p.menu);
			out.writeBoolean(p.menuPos != null);
			if (p.menuPos != null) {
				out.writeBlockPos(p.menuPos);
			}

			out.writeShort(p.actions);
		}

		private static HandsPayload read(RegistryFriendlyByteBuf in) {
			int drifter = in.readVarInt();
			int flags = in.readByte() & 0xFF;
			BlockPos block = null;
			int face = 0;
			double x = 0.0;
			double y = 0.0;
			double z = 0.0;
			if (in.readBoolean()) {
				block = in.readBlockPos();
				face = in.readByte();
				x = in.readDouble();
				y = in.readDouble();
				z = in.readDouble();
			}

			int entity = in.readVarInt();
			boolean menu = in.readBoolean();
			BlockPos menuPos = in.readBoolean() ? in.readBlockPos() : null;
			return new HandsPayload(drifter, flags, block, face, x, y, z, entity, menu, menuPos, in.readShort());
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof HandsPayload p && p.drifter == this.drifter && p.flags == this.flags && Objects.equals(p.block, this.block)
				&& p.face == this.face && p.hitX == this.hitX && p.hitY == this.hitY && p.hitZ == this.hitZ && p.entity == this.entity
				&& p.menu == this.menu && Objects.equals(p.menuPos, this.menuPos) && p.actions == this.actions;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.drifter, this.flags, this.block, this.entity, this.menuPos, this.actions);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Something done once, as Touch'n Motion's HandsAct: feeding, milking, shearing, an armour stand, a seed. */
	public record ActPayload(int drifter, int kind, int entity, double x, double y, double z, boolean mainHand) implements CustomPacketPayload {
		public static final Type<ActPayload> TYPE = new Type<>(Drifter.id("hand_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActPayload> CODEC = StreamCodec.of(
			(out, p) -> {
				out.writeVarInt(p.drifter);
				out.writeByte(p.kind);
				out.writeVarInt(p.entity);
				out.writeDouble(p.x);
				out.writeDouble(p.y);
				out.writeDouble(p.z);
				out.writeBoolean(p.mainHand);
			},
			in -> new ActPayload(in.readVarInt(), in.readByte(), in.readVarInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
