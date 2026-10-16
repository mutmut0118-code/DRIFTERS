package com.dinglebob.drifter.mixin;

import java.util.Comparator;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;

/**
 * Piglins go for a Drifter not wearing gold, as they go for a player (section 1: hostile mobs treat it like a player;
 * section 19 piglin etiquette). Vanilla only looks at players, so they ignored Drifters entirely. Only when the piglin has
 * nothing else to attack (a player without gold, a nemesis, whoever angered it come first), and only one it can see.
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
	/** How far a piglin looks for one without gold (its sensor's range for players). */
	private static final double RANGE = 16.0;

	@Inject(method = "findNearestValidAttackTarget", at = @At("RETURN"), cancellable = true)
	private static void drifter$drifterWithoutGold(Piglin piglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
		if (cir.getReturnValue().isPresent() || !piglin.isAdult()) {
			return;
		}

		// (Near a zombified piglin it attacks nobody: vanilla returned empty for that, and so does this.)
		if (piglin.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED)
			&& piglin.closerThan(piglin.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED).get(), 6.0)) {
			return;
		}

		Optional<DrifterEntity> drifter = piglin.level().getEntitiesOfClass(DrifterEntity.class, piglin.getBoundingBox().inflate(RANGE),
				candidate -> candidate.isAlive() && !PiglinAi.isWearingGold(candidate) && Sensor.isEntityAttackable(piglin, candidate)
					&& piglin.getSensing().hasLineOfSight(candidate))
			.stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(piglin)));
		if (drifter.isPresent()) {
			cir.setReturnValue(drifter);
		}
	}
}
