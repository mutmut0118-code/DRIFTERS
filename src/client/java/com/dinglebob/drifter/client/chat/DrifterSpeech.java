package com.dinglebob.drifter.client.chat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.DrifterSaidPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Drifters' chat lines as the chat mods show players' (chat phase 5; user, 2026-10-23: "chat head and chat bubbles support
 * (both are mods found in drifter test)"). Both are optional, found when the game starts, and used through their own code
 * so they look and behave as they do for players (their settings too):
 * <ul>
 * <li><b>Talk Bubbles</b> ({@code talkbubbles}): a bubble over the Drifter with what it said, for as long as the mod keeps a
 * player's, wrapped as it wraps them, drawn by its {@code RenderBubble}.</li>
 * <li><b>Chat Heads</b> ({@code chat_heads}): the Drifter's face next to its line ({@code ChatHeadsMixin} asks {@link
 * #headFor} which Drifter a message is from).</li>
 * </ul>
 * The server says which Drifter said a line ({@link DrifterSaidPayload}) just before the line comes.
 */
public final class DrifterSpeech {
	private DrifterSpeech() {
	}

	/** A Drifter's bubble: its lines, how wide and how many, and when it said it (its tick count). */
	private record Bubble(List<String> lines, int width, int height, int said) {
	}

	/** The line just announced, waiting for the chat message it goes with (Chat Heads). */
	private record Pending(int drifterId, String text, long at) {
	}

	private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
	@Nullable
	private static Pending pending;

	// Talk Bubbles, looked up once (null without it, or if it changed beyond use).
	private static boolean bubblesLooked;
	@Nullable
	private static Method renderBubble;
	@Nullable
	private static Object bubbleConfig;
	@Nullable
	private static Field chatTime;
	@Nullable
	private static Field maxChatWidth;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DrifterSaidPayload.TYPE, (payload, context) -> said(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			BUBBLES.clear();
			pending = null;
		});
	}

	private static void said(DrifterSaidPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		pending = new Pending(payload.drifterId(), payload.text(), System.currentTimeMillis());
		if (minecraft.level == null || !bubbles()) {
			return;
		}

		Entity entity = minecraft.level.getEntity(payload.drifterId());
		if (entity instanceof DrifterEntity drifter) {
			BUBBLES.put(drifter.getId(), wrap(minecraft.font, payload.text(), drifter.tickCount));
		}
	}

	/** Its lines wrapped as Talk Bubbles wraps a player's (word by word up to its widest; the width made even). */
	private static Bubble wrap(Font font, String text, int now) {
		int most = intOf(maxChatWidth, 180);
		List<String> lines = new ArrayList<>();
		String line = "";
		int width = 0;
		for (String word : text.split(" ")) {
			if (!line.isEmpty() && font.width(line + " " + word) > most) {
				lines.add(line);
				width = Math.max(width, font.width(line));
				line = word;
			} else {
				line = line.isEmpty() ? word : line + " " + word;
			}
		}

		lines.add(line);
		width = Math.max(width, font.width(line));
		return new Bubble(lines, width % 2 == 0 ? width : width + 1, lines.size(), now);
	}

	/** Its bubble over it, while it lasts (called from its renderer, the pose at its feet). */
	public static void render(DrifterEntity drifter, PoseStack poseStack, MultiBufferSource buffers, int light, EntityRenderDispatcher dispatcher, Font font) {
		Bubble bubble = BUBBLES.get(drifter.getId());
		if (bubble == null || renderBubble == null) {
			return;
		}

		if (drifter.tickCount - bubble.said() > intOf(chatTime, 200) || drifter.tickCount < bubble.said() || !drifter.isAlive()) {
			BUBBLES.remove(drifter.getId());
			return;
		}

		if (drifter.isInvisible()) {
			return;
		}

		try {
			renderBubble.invoke(null, poseStack, buffers, font, dispatcher, bubble.lines(), bubble.width(), bubble.height(), drifter.getBbHeight(), light);
		} catch (ReflectiveOperationException | RuntimeException e) {
			Drifter.LOGGER.warn("Couldn't draw a Talk Bubbles bubble over a Drifter; not trying again", e);
			renderBubble = null;
		}
	}

	/** Talk Bubbles is here and usable (looked up the first time). */
	private static boolean bubbles() {
		if (!bubblesLooked) {
			bubblesLooked = true;
			if (FabricLoader.getInstance().isModLoaded("talkbubbles")) {
				try {
					Class<?> render = Class.forName("net.talkbubbles.util.RenderBubble");
					renderBubble = render.getMethod("renderBubble", PoseStack.class, MultiBufferSource.class, Font.class, EntityRenderDispatcher.class, List.class,
						int.class, int.class, float.class, int.class);
					bubbleConfig = Class.forName("net.talkbubbles.TalkBubbles").getField("CONFIG").get(null);
					Class<?> config = Class.forName("net.talkbubbles.config.TalkBubblesConfig");
					chatTime = config.getField("chatTime");
					maxChatWidth = config.getField("maxChatWidth");
				} catch (ReflectiveOperationException | RuntimeException e) {
					Drifter.LOGGER.warn("Talk Bubbles is here but not as Drifter knows it: no bubbles over Drifters", e);
					renderBubble = null;
				}
			}
		}

		return renderBubble != null;
	}

	private static int intOf(@Nullable Field field, int otherwise) {
		try {
			// (Read each time: its settings screen can change it.)
			Object config = bubbleConfig != null ? bubbleConfig : Class.forName("net.talkbubbles.TalkBubbles").getField("CONFIG").get(null);
			return field == null || config == null ? otherwise : field.getInt(config);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return otherwise;
		}
	}

	/**
	 * Chat Heads: the Drifter this message is from, as a player-list entry with its skin (null if it isn't a Drifter's
	 * line). The line must be the one the server just announced, so a player's message is never taken for a Drifter's.
	 */
	@Nullable
	public static PlayerInfo headFor(Component message) {
		Pending said = pending;
		Minecraft minecraft = Minecraft.getInstance();
		if (said == null || minecraft.level == null || System.currentTimeMillis() - said.at() > 5000L || !message.getString().contains(said.text())) {
			return null;
		}

		pending = null;
		if (!(minecraft.level.getEntity(said.drifterId()) instanceof DrifterEntity drifter)) {
			return null;
		}

		ResourceLocation texture = minecraft.getEntityRenderDispatcher().getRenderer(drifter).getTextureLocation(drifter);
		return new Face(drifter, texture);
	}

	/** A Drifter as a player-list entry for Chat Heads: its name and the skin it wears now (kept with the message). */
	private static final class Face extends PlayerInfo {
		private final PlayerSkin skin;

		Face(DrifterEntity drifter, ResourceLocation texture) {
			super(new GameProfile(drifter.getUUID(), drifter.getName().getString()), false);
			this.skin = new PlayerSkin(texture, null, null, null, drifter.hasSlimArms() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false);
		}

		@Override
		public PlayerSkin getSkin() {
			return this.skin;
		}
	}
}
