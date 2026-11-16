package com.dinglebob.drifterciv;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * A town's stable and its horses (horse culture: docs/superpowers/specs/2026-11-16-horse-culture-design.md, "Stables"):
 * how big it is for its town, the breeds it keeps for its land (Icy's Better Horses' breeds; plain horses without it).
 */
final class Stables {
	private Stables() {
	}

	/**
	 * A stall for every this many households (the rest of its people walk, or ride behind a friend: the user, 2026-11-16,
	 * "half of the war band should have their own horse, with the other half riding on that horse").
	 */
	static final int STALL_PER_HOUSEHOLDS = 2;
	/** Stalls at least, in a town with a stable. */
	static final int FEWEST_STALLS = 3;
	/** Stalls at most (a city's long stable, two rows). */
	static final int MOST_STALLS = 16;
	/** Stalls kept free for riders from elsewhere (a horse for sale never stands in one). */
	static final int VISITOR_STALLS = 2;
	/** Horses for sale: a town's, a city's. */
	static final int FOR_SALE_TOWN = 1;
	static final int FOR_SALE_CITY = 2;

	/** Its own people's stalls for this many households (visitors' not counted). */
	static int workStalls(int households) {
		return Math.max(FEWEST_STALLS, Math.min(MOST_STALLS, households / STALL_PER_HOUSEHOLDS));
	}

	/** The riding breeds of a town's land (by its look: the biome it's in). */
	static List<String> breeds(TownBuilder.Style look) {
		return switch (look.name()) {
			case "desert" -> List.of("arabian");
			case "snowy" -> List.of("icelandic");
			case "savanna" -> List.of("appaloosa", "mustang");
			case "taiga" -> List.of("haflinger", "friesian");
			default -> List.of("thoroughbred", "andalusian", "morgan", "quarter");
		};
	}

	/** The draft breeds (every stable keeps one, for carts). */
	static final List<String> DRAFT = List.of("shire", "clydesdale", "belgian", "percheron");

	static String pick(List<String> from, RandomSource random) {
		return from.get(random.nextInt(from.size()));
	}

	/** Its horses for sale offered to Drifters (horse culture: a traveller without a horse buys one). */
	static void register() {
		DrifterApi.setHorseSales(Stables::forSaleTo, Stables::sellTo);
	}

	/** The town whose land this is (in the overworld), or null. */
	@Nullable
	static Town townAt(ServerLevel level, BlockPos pos) {
		String dimension = level.dimension().location().toString();
		for (Town town : Towns.all()) {
			int land = town.land();
			if (town.dimension.equals(dimension) && town.center.distSqr(pos) <= (double)land * land) {
				return town;
			}
		}

		return null;
	}

	/** A horse for sale at the stable of the town it's in, still tied in its stall, if it has the money for it; or null. */
	@Nullable
	static DrifterApi.HorseForSale forSaleTo(DrifterEntity drifter) {
		if (!(drifter.level() instanceof ServerLevel level)) {
			return null;
		}

		Town town = townAt(level, drifter.blockPosition());
		if (town == null || town.forSale.isEmpty() || DrifterApi.moneyHeldPoints(drifter.getInventory()) < Prices.HORSE) {
			return null;
		}

		for (java.util.UUID id : town.forSale) {
			if (level.getEntity(id) instanceof AbstractHorse horse && horse.isAlive() && horse.isLeashed() && !horse.isVehicle()) {
				return new DrifterApi.HorseForSale(horse, stallOf(town, horse), Prices.HORSE);
			}
		}

		return null;
	}

	/** The stall of its town's stable this horse stands in (the nearest). */
	static BlockPos stallOf(Town town, AbstractHorse horse) {
		BlockPos best = horse.blockPosition();
		double nearest = Double.MAX_VALUE;
		for (BlockPos stall : town.stalls) {
			double d = stall.distSqr(horse.blockPosition());
			if (d < nearest) {
				nearest = d;
				best = stall;
			}
		}

		return best;
	}

	/** The sale: its money into the town's till, the horse its own (its stall with it). False if it can't. */
	static boolean sellTo(DrifterEntity buyer, AbstractHorse horse) {
		if (!(buyer.level() instanceof ServerLevel level) || level.getServer() == null) {
			return false;
		}

		Town town = townAt(level, horse.blockPosition());
		if (town == null || !town.forSale.contains(horse.getUUID()) || DrifterApi.moneyHeldPoints(buyer.getInventory()) < Prices.HORSE) {
			return false;
		}

		net.minecraft.world.Container till = TownNotices.till(level.getServer(), town);
		if (till == null) {
			return false;
		}

		double paid = DrifterApi.moneyTakePoints(buyer.getInventory(), Prices.HORSE);
		DrifterApi.moneyPutPoints(till, paid);
		town.forSale.remove(horse.getUUID());
		town.drifterTrades++;
		town.booksChanged = true;
		DrifterApi.keepHorse(buyer, horse, stallOf(town, horse));
		DrifterCivilizations.LOGGER.info("[stable] {} bought {} at {}'s stable for {} points", buyer.getName().getString(), horse.getName().getString(),
			town.name, String.format(java.util.Locale.ROOT, "%.1f", paid));
		return true;
	}
}
