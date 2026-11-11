package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * A town's newspaper (the user, 2026-11-06: "the lectern should simulate a newspaper as an alternative for the chronicle,
 * to make it more immersive (also the idea of a drifter passing around written books of major news wouldn't be bad
 * aswell)"): the lectern on its plaza holds the day's Gazette, printed fresh each morning from the chronicle (its own news
 * first, then the world's; who lives there at the back); and its crier, one of its people, calls the headlines out on the
 * plaza of a morning and hands whoever's about a copy (a book and quill of its own, written up: {@code CrierGoal}).
 */
final class TownNews {
	private TownNews() {
	}

	/** The day each town's paper was last printed, and its crier cried (not saved: printed again after a reload). */
	private static final Map<UUID, Long> printed = new HashMap<>();
	private static final Map<UUID, Long> cried = new HashMap<>();
	/** Each town's crier. */
	private static final Map<UUID, UUID> criers = new HashMap<>();
	/** The paper's own news goes back this many days; the world's, this many. */
	private static final int OWN_DAYS = 5;
	private static final int WORLD_DAYS = 3;

	/** Each check: the morning's paper on its lectern. */
	static void check(MinecraftServer server, Town town) {
		long day = Chronicle.day(server);
		long time = server.overworld().getDayTime() % 24000L;
		if (printed.getOrDefault(town.id, -1L) == day || time < 1500L || time > 12000L) {
			return;
		}

		ServerLevel level = server.overworld();
		BlockPos lectern = lectern(level, town);
		if (lectern == null) {
			return;
		}

		if (level.getBlockEntity(lectern) instanceof LecternBlockEntity entity) {
			ItemStack paper = gazette(server, town);
			entity.setBook(paper);
			var content = paper.get(DataComponents.WRITTEN_BOOK_CONTENT);
			DrifterCivilizations.LOGGER.info("[news] {}'s Gazette for {} printed: {} pages", town.name, Chronicle.dayName(day), content == null ? 0 : content.pages().size());
			if (!level.getBlockState(lectern).getValue(LecternBlock.HAS_BOOK)) {
				level.setBlock(lectern, level.getBlockState(lectern).setValue(LecternBlock.HAS_BOOK, true), 3);
			}

			entity.setChanged();
			printed.put(town.id, day);
		}
	}

	/** The lectern by its plaza (its notice board's), loaded; or null. */
	@Nullable
	private static BlockPos lectern(ServerLevel level, Town town) {
		BlockPos c = town.center;
		if (!level.isLoaded(c)) {
			return null;
		}

		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-9, -3, -9), c.offset(9, 3, 9))) {
			if (level.getBlockState(pos).getBlock() instanceof LecternBlock) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** Today's paper: "The (town) Gazette", its front page the headlines, then the news, then the town's own pages. */
	static ItemStack gazette(MinecraftServer server, Town town) {
		long day = Chronicle.day(server);
		List<Chronicle.Entry> all = Chronicle.all();
		List<Chronicle.Entry> own = new ArrayList<>();
		List<Chronicle.Entry> world = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0; i--) {
			Chronicle.Entry entry = all.get(i);
			if (entry.text().contains(town.name)) {
				if (day - entry.day() < OWN_DAYS) {
					own.add(entry);
				}
			} else if (day - entry.day() < WORLD_DAYS && !entry.kind().equals("weather") && !entry.text().startsWith(History.AFAR)) {
				world.add(entry);
			}
		}

		// (News from afar: only what would have reached it. WorldPlan.newsFor.)
		for (History.News heard : WorldPlan.newsFor(town, day, WORLD_DAYS)) {
			world.add(new Chronicle.Entry(heard.day(), 0L, heard.kind(), heard.text()));
		}

		List<Filterable<Component>> pages = new ArrayList<>();
		StringBuilder front = new StringBuilder("THE " + town.name.toUpperCase(java.util.Locale.ROOT) + " GAZETTE\n" + Chronicle.dayName(day) + "\n\n");
		List<String> heads = headlines(town, server, 3);
		if (heads.isEmpty()) {
			front.append("All quiet today.");
		}

		for (String head : heads) {
			front.append("* ").append(head).append("\n\n");
		}

		pages.add(page(front.toString()));
		// The notice board (D5): what's wanted, and warnings.
		List<String> notices = TownNotices.lines(town);
		if (!notices.isEmpty()) {
			StringBuilder board = new StringBuilder("NOTICES\n\n");
			for (String notice : notices) {
				board.append("* ").append(notice).append("\n\n");
			}

			pages.add(page(board.toString()));
		}

		paginate(pages, "In " + town.name, own);
		paginate(pages, "From afar", world);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			StringBuilder people = new StringBuilder("The town of " + town.name + "\nFounded on day " + town.foundedDay + "\n\nIts people:\n");
			int lines = 4;
			for (String name : group.names.values()) {
				if (lines++ >= 13) {
					pages.add(page(people.toString()));
					people.setLength(0);
					lines = 1;
				}

				people.append(name).append('\n');
			}

			pages.add(page(people.toString()));
		}

		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("The " + town.name + " Gazette"), "the " + town.name
			+ " press", 0, pages.subList(0, Math.min(pages.size(), 100)), true));
		return book;
	}

	private static Filterable<Component> page(String text) {
		return Filterable.passThrough(Component.literal(text));
	}

	/** Entries onto pages under a heading (about thirteen lines a page). */
	private static void paginate(List<Filterable<Component>> pages, String heading, List<Chronicle.Entry> entries) {
		if (entries.isEmpty()) {
			return;
		}

		StringBuilder page = new StringBuilder(heading.toUpperCase(java.util.Locale.ROOT) + "\n\n");
		int lines = 2;
		for (Chronicle.Entry entry : entries) {
			String text = Chronicle.dayName(entry.day()) + ": " + entry.text() + "\n\n";
			int takes = 2 + text.length() / 19;
			if (lines + takes > 14 && lines > 2) {
				pages.add(page(page.toString()));
				page.setLength(0);
				lines = 0;
			}

			page.append(text);
			lines += takes;
		}

		pages.add(page(page.toString()));
	}

	/** The day's headlines (its own news first, then the world's biggest: wars, a town fallen, a leader), short. */
	static List<String> headlines(Town town, MinecraftServer server, int most) {
		long day = Chronicle.day(server);
		List<Chronicle.Entry> all = Chronicle.all();
		List<String> heads = new ArrayList<>();
		for (int pass = 0; pass < 2 && heads.size() < most; pass++) {
			for (int i = all.size() - 1; i >= 0 && heads.size() < most; i--) {
				Chronicle.Entry entry = all.get(i);
				boolean ours = entry.text().contains(town.name);
				boolean big = entry.kind().equals("war") || entry.kind().equals("nation") || entry.kind().equals("law") || entry.kind().equals("festival");
				if (day - entry.day() <= 1 && (pass == 0 ? ours : !ours && big)) {
					String text = entry.text();
					heads.add(text.length() > 80 ? text.substring(0, 77) + "..." : text);
				}
			}
		}

		return heads;
	}

	// ---------------------------------------------------------------------------------------------
	// The crier
	// ---------------------------------------------------------------------------------------------

	/** Its crier: the most sociable of its people (not its leader, keeper, a guard or a crook), picked again when gone. */
	@Nullable
	static UUID crier(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return null;
		}

		UUID now = criers.get(town.id);
		if (now != null && group.members.contains(now)) {
			return now;
		}

		UUID best = null;
		double bestScore = -1;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || id.equals(town.leader) || id.equals(town.keeper) || town.crooks.contains(id) || "guard".equals(town.lines.get(id))) {
				continue;
			}

			double score = DrifterApi.trait(member, "sociability");
			if (score > bestScore) {
				bestScore = score;
				best = id;
			}
		}

		if (best != null) {
			criers.put(town.id, best);
		}

		return best;
	}

	static boolean isCrier(Town town, DrifterEntity drifter) {
		MinecraftServer server = drifter.getServer();
		return server != null && drifter.getUUID().equals(crier(server, town));
	}

	/** The news to cry this morning, if it's the crier and hasn't yet (and there's news): the headlines and the paper. */
	@Nullable
	static TownWork.News news(Town town, DrifterEntity drifter) {
		MinecraftServer server = drifter.getServer();
		if (server == null || !isCrier(town, drifter)) {
			return null;
		}

		long day = Chronicle.day(server);
		TownDay.Part part = TownDay.part(DrifterApi.ownTime(drifter));
		if (cried.getOrDefault(town.id, -1L) == day || part != TownDay.Part.DAWN && part != TownDay.Part.MORNING) {
			return null;
		}

		// (Worked out once a day for its crier, not each time it weighs up what to do.)
		Today today = papers.get(town.id);
		if (today == null || today.day != day) {
			List<String> heads = headlines(town, server, 3);
			today = new Today(day, heads.isEmpty() ? null : new TownWork.News(heads, gazette(server, town)));
			papers.put(town.id, today);
		}

		return today.news;
	}

	/** Each town's news for its crier today. */
	private record Today(long day, @Nullable TownWork.News news) {
	}

	private static final Map<UUID, Today> papers = new HashMap<>();

	static void cried(Town town, DrifterEntity drifter) {
		if (drifter.getServer() != null) {
			cried.put(town.id, Chronicle.day(drifter.getServer()));
		}
	}
}
