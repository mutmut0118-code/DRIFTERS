package com.dinglebob.drifter.client.render;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Real player skins on the client (RealSkins): the game's own skin loader downloads the picture from Mojang and caches it
 * on this computer, as it does for a player you meet. Until it's ready (or with no internet) the Drifter shows its default
 * skin.
 */
public final class DrifterSkins {
	/** The skins asked for, by their textures text (a few hundred at most in view). */
	private static final Map<String, CompletableFuture<PlayerSkin>> SKINS = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<PlayerSkin>> eldest) {
			return this.size() > 512;
		}
	};

	private DrifterSkins() {
	}

	/** The downloaded skin's texture, or null while it's still on its way. */
	@Nullable
	public static ResourceLocation texture(String textures) {
		CompletableFuture<PlayerSkin> skin = SKINS.computeIfAbsent(textures, DrifterSkins::load);
		PlayerSkin loaded = skin.isCompletedExceptionally() ? null : skin.getNow(null);
		return loaded == null ? null : loaded.texture();
	}

	private static CompletableFuture<PlayerSkin> load(String textures) {
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(textures.getBytes(StandardCharsets.UTF_8)), "Drifter");
		profile.getProperties().put("textures", new Property("textures", textures));
		return Minecraft.getInstance().getSkinManager().getOrLoad(profile);
	}
}
