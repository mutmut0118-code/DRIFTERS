package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * A Drifter's fake-player helper has no advancements to load or follow (milestone 16: making one read an advancement
 * file and signed it up to every advancement in the game, a millisecond or two each, and 300 soldiers' first swings in a
 * battle came at once; nor should a helper earn advancements).
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@org.spongepowered.asm.mixin.Shadow
	public abstract net.minecraft.advancements.AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

	/** An advancement made that shows in chat: Drifters about may say gg (ChatReplies; the user's chat guidebook). */
	@Inject(method = "award", at = @At("RETURN"))
	private void drifter$congrats(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.player != null && this.getOrStartProgress(advancement).isDone()
			&& advancement.value().display().map(display -> display.shouldAnnounceChat()).orElse(false)
			&& this.player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
			com.dinglebob.drifter.social.ChatReplies.advancement(this.player);
		}
	}

	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void drifter$noAdvancementsForHelpers(ServerAdvancementManager manager, CallbackInfo ci) {
		if (this.player != null && FakePlayerBridge.isHelper(this.player.getUUID())) {
			ci.cancel();
		}
	}
}
