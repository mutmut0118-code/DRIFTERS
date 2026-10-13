package com.dinglebob.drifter.brain.activity;

import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Hunting (DESIGN.md section 10.5): picks an adult animal of the wanted kinds that it has seen, chases it down, kills
 * it and picks up the drops, until it has enough of the wanted items. Goes to where it last remembers seeing one if
 * none is in view.
 * TODO(milestone 8): leave two of each species near home for breeding (long-term thinkers).
 */
public class HuntActivity extends Activity {
	private static final double DROP_PICKUP_RADIUS = 8.0;
	private static final double MEMORY_RANGE = 48.0;
	private static final int MAX_KILLS = 4;

	private final Set<EntityType<?>> prey;
	private final Predicate<ItemStack> items;
	private final int wanted;
	private final String label;
	private boolean collectNext;
	private boolean searched;
	private int kills;

	public HuntActivity(DrifterEntity drifter, Set<EntityType<?>> prey, Predicate<ItemStack> items, int wanted, String label) {
		super(drifter);
		this.prey = prey;
		this.items = items;
		this.wanted = wanted;
		this.label = label;
	}

	@Override
	public String describe() {
		return "hunt for " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 3;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof AttackTask && status == Status.SUCCESS) {
			this.kills++;
			this.collectNext = true;
		}
	}

	@Override
	protected Status next() {
		if (this.collectNext) {
			this.collectNext = false;
			return this.begin(new CollectItemsTask(this.drifter, DROP_PICKUP_RADIUS));
		}

		if (this.drifter.getInventory().count(this.items) >= this.wanted || this.kills >= MAX_KILLS) {
			return this.kills > 0 || this.drifter.getInventory().count(this.items) > 0 ? Status.SUCCESS : this.fail("got nothing");
		}

		LivingEntity target = this.pickTarget();
		if (target != null) {
			this.searched = false;
			return this.begin(new AttackTask(this.drifter, target, target.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
		}

		if (!this.searched) {
			MemoryEntry remembered = this.drifter.getMemory().nearest(MemoryKind.MOB, this.drifter.level().dimension().location(),
				this.drifter.position(), MEMORY_RANGE, entry -> this.isPreyId(entry.label) && (entry.detail == null || !entry.detail.contains("baby")));
			if (remembered != null) {
				this.searched = true;
				this.think("going to where it saw a " + remembered.label.replace("minecraft:", ""));
				return this.begin(new NavigateTask(this.drifter, remembered.pos, 4.0, "go where it saw animals"));
			}
		}

		return this.kills > 0 ? Status.SUCCESS : this.fail("hasn't seen any " + this.label + " animals nearby");
	}

	private boolean isPreyId(String id) {
		return this.prey.stream().anyMatch(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(id));
	}

	@Nullable
	private LivingEntity pickTarget() {
		return this.drifter.getSenses().tracked().stream()
			.filter(a -> a.identified && a.entity instanceof LivingEntity living && living.isAlive() && this.prey.contains(living.getType())
				&& !(living instanceof AgeableMob ageable && ageable.isBaby()) && this.now() - a.lastSeenTick < 20 * 5)
			.min(Comparator.comparingDouble(a -> a.entity.distanceToSqr(this.drifter)))
			.map(a -> (LivingEntity)a.entity)
			.orElse(null);
	}
}
