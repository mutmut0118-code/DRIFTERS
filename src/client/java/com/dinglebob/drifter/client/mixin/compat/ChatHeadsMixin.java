package com.dinglebob.drifter.client.mixin.compat;

import java.lang.reflect.Method;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.client.chat.DrifterSpeech;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;

/**
 * Chat Heads (optional; {@code @Pseudo}: nothing without it): a Drifter's line gets the Drifter's face, as a player's gets
 * theirs. Chat Heads looks for a message's sender among the players; before it does, {@link DrifterSpeech#headFor} says
 * whether it's the line a Drifter just said.
 */
@Pseudo
@Mixin(targets = "dzwdz.chat_heads.ChatHeads", remap = false)
public abstract class ChatHeadsMixin {
	private static Method drifter$headDataOf;
	private static boolean drifter$broken;

	@Inject(method = "detectPlayer", at = @At("HEAD"), cancellable = true, require = 0)
	private static void drifter$drifterHead(Component message, ChatType.Bound bound, PlayerInfo info, CallbackInfoReturnable<Object> cir) {
		if (drifter$broken) {
			return;
		}

		PlayerInfo face = DrifterSpeech.headFor(message);
		if (face == null) {
			return;
		}

		try {
			if (drifter$headDataOf == null) {
				drifter$headDataOf = Class.forName("dzwdz.chat_heads.HeadData").getMethod("of", PlayerInfo.class);
			}

			cir.setReturnValue(drifter$headDataOf.invoke(null, face));
		} catch (ReflectiveOperationException | RuntimeException e) {
			Drifter.LOGGER.warn("Chat Heads isn't as Drifter knows it: no heads for Drifters", e);
			drifter$broken = true;
		}
	}
}
