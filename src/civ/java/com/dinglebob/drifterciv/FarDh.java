package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paper towns in Distant Horizons (optional: DH 3.3.x; docs/superpowers/specs/2026-11-14-lod-towns-design.md and the
 * spike's findings beside it). A world generator override that stands in front of whichever generator DH would have
 * used for the overworld (its own, or DH SeedGen's), lets that one make the far land, and paints the sketched towns and
 * roads into the result before DH gets it. It runs where the world is hosted (single player, a LAN host, a dedicated
 * server with DH), on DH's own threads, with the server's painter.
 *
 * <p>DH's API only, but for two reaches past it, by name and each in a try: finding another mod's override to wrap
 * ({@code WorldGeneratorInjector}'s per-level list) and making DH's own generator when there's none
 * ({@code DhWorldGenerator}). If either fails, no override is registered and DH carries on as without us.
 */
public final class FarDh {
	private FarDh() {
	}

	public static void start() {
		DhApiEventRegister.on(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
			@Override
			public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> event) {
				IDhApiLevelWrapper wrapper = event.value.levelWrapper;
				Object level = wrapper.getWrappedMcObject();
				if (level instanceof ServerLevel server && server.dimension() == Level.OVERWORLD && WorldPlan.planned) {
					DhApiResult<Void> result = DhApi.worldGenOverrides.registerWorldGeneratorOverride(wrapper, new Wrap(wrapper));
					DrifterCivilizations.LOGGER.info("[far] Distant Horizons: towns and roads are painted into its far view ({})", result.success ? "ready"
						: "refused: " + result.message);
				}
			}
		});
	}

	private static final class Wrap implements IDhApiWorldGenerator {
		private final IDhApiLevelWrapper level;
		private volatile IDhApiWorldGenerator inner;
		private final ConcurrentHashMap<BlockState, IDhApiBlockStateWrapper> blocks = new ConcurrentHashMap<>();
		private final AtomicLong painted = new AtomicLong();
		private final AtomicLong nanos = new AtomicLong();
		private final AtomicLong asked = new AtomicLong();
		private volatile boolean said;

		Wrap(IDhApiLevelWrapper level) {
			this.level = level;
		}

		@Override
		public int getPriority() {
			return 50;
		}

		/** The generator DH would have used without us: another mod's override (SeedGen), else DH's own. */
		private IDhApiWorldGenerator inner() {
			IDhApiWorldGenerator found = inner;
			if (found != null) {
				return found;
			}

			synchronized (this) {
				if (inner != null) {
					return inner;
				}

				try {
					Class<?> injectorClass = Class.forName("com.seibel.distanthorizons.coreapi.DependencyInjection.WorldGeneratorInjector");
					Object injector = injectorClass.getField("INSTANCE").get(null);
					java.lang.reflect.Field map = injectorClass.getDeclaredField("worldGeneratorByLevelWrapper");
					map.setAccessible(true);
					Object perLevel = ((java.util.Map<?, ?>) map.get(injector)).get(level);
					if (perLevel != null) {
						java.lang.reflect.Method get = perLevel.getClass().getMethod("get", Class.class, int.class);
						for (int priority = 49; priority >= 0 && found == null; priority--) {
							Object other = get.invoke(perLevel, IDhApiWorldGenerator.class, priority);
							if (other instanceof IDhApiWorldGenerator generator && other != this) {
								found = generator;
							}
						}
					}

					if (found == null) {
						Object world = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi").getMethod("getAbstractDhWorld").invoke(null);
						Object dhLevel = null;
						for (java.lang.reflect.Method m : world.getClass().getMethods()) {
							if (m.getName().equals("getLevel") && m.getParameterCount() == 1) {
								dhLevel = m.invoke(world, level);
								break;
							}
						}

						found = (IDhApiWorldGenerator) Class.forName("com.seibel.distanthorizons.core.generation.DhWorldGenerator").getConstructors()[0]
								.newInstance(dhLevel);
					}
				} catch (ReflectiveOperationException | RuntimeException e) {
					DrifterCivilizations.LOGGER.error("[far] Distant Horizons: couldn't find the generator to wrap", e);
					throw new IllegalStateException(e);
				}

				DrifterCivilizations.LOGGER.info("[far] Distant Horizons: wrapping {} (priority {}, returns {}, detail {}..{})", found.getClass().getName(), found.getPriority(),
						found.getReturnType(), found.getSmallestDataDetailLevel(), found.getLargestDataDetailLevel());
				inner = found;
				return found;
			}
		}

		@Override
		public byte getSmallestDataDetailLevel() {
			return inner().getSmallestDataDetailLevel();
		}

		@Override
		public byte getLargestDataDetailLevel() {
			return inner().getLargestDataDetailLevel();
		}

		@Override
		public boolean runApiValidation() {
			return inner().runApiValidation();
		}

		@Override
		public EDhApiWorldGeneratorReturnType getReturnType() {
			return inner().getReturnType();
		}

		@Override
		public void preGeneratorTaskStart() {
			inner().preGeneratorTaskStart();
		}

		@Override
		public void close() {
			IDhApiWorldGenerator found = inner;
			DrifterCivilizations.LOGGER.info("[far] Distant Horizons: closed: asked {} sections, painted {} in {} ms", asked.get(), painted.get(), nanos.get() / 1_000_000);
			if (found != null) {
				try {
					found.close();
				} catch (Exception e) {
					DrifterCivilizations.LOGGER.warn("[far] Distant Horizons: closing the wrapped generator", e);
				}
			}
		}

		@Override
		public CompletableFuture<Void> generateLod(int chunkMinX, int chunkMinZ, int posX, int posZ, byte detail, IDhApiFullDataSource pooled,
				EDhApiDistantGeneratorMode mode, ExecutorService pool, Consumer<IDhApiFullDataSource> result) {
			asked.incrementAndGet();
			return inner().generateLod(chunkMinX, chunkMinZ, posX, posZ, detail, pooled, mode, pool, source -> {
				try {
					paint(chunkMinX * 16, chunkMinZ * 16, detail, source);
				} catch (RuntimeException e) {
					DrifterCivilizations.LOGGER.error("[far] Distant Horizons: painting failed", e);
				}

				result.accept(source);
			});
		}

		private IDhApiBlockStateWrapper wrap(BlockState state) {
			return blocks.computeIfAbsent(state, s -> DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[] {s}, level));
		}

		/** The ground, as against what grows on it ({@link FarPaint#ground}). */
		private static boolean ground(DhApiTerrainDataPoint point) {
			if (point.blockStateWrapper.isAir()) {
				return false;
			}

			return !(point.blockStateWrapper.getWrappedMcObject() instanceof BlockState state) || FarPaint.ground(state);
		}

		private void paint(int originX, int originZ, byte detail, IDhApiFullDataSource source) {
			int width = source.getWidthInDataColumns();
			int step = 1 << detail;
			if (!FarPaint.SERVER.touches(originX, originZ, originX + width * step, originZ + width * step)) {
				return;
			}

			long started = System.nanoTime();
			int columns = 0;
			for (int rx = 0; rx < width; rx++) {
				for (int rz = 0; rz < width; rz++) {
					FarPaint.Column col = FarPaint.SERVER.column(originX + rx * step + step / 2, originZ + rz * step + step / 2);
					if (col == null) {
						continue;
					}

					List<DhApiTerrainDataPoint> points = new ArrayList<>(source.getApiDataPointColumn(rx, rz));
					points.sort(Comparator.comparingInt((DhApiTerrainDataPoint p) -> p.topYBlockPos).reversed());
					// (The ground: the highest point that isn't air, a tree or a plant. The top one is often a treetop.)
					int at = 0;
					while (at < points.size() && !ground(points.get(at))) {
						at++;
					}

					if (at >= points.size()) {
						continue;
					}

					DhApiTerrainDataPoint ground = points.get(at);
					IDhApiBiomeWrapper biome = ground.biomeWrapper;
					IDhApiBlockStateWrapper air = DhApi.Delayed.wrapperFactory.getAirBlockStateWrapper();
					List<DhApiTerrainDataPoint> made = new ArrayList<>();
					int ceiling = points.get(0).topYBlockPos;
					if (col.lightOnly()) {
						// Only a lamp's glow on it: everything as it was (its trees too), the ground lit.
						for (int i = 0; i < points.size(); i++) {
							DhApiTerrainDataPoint point = points.get(i);
							made.add(i == at ? DhApiTerrainDataPoint.create((byte)0, Math.max(col.light(), point.blockLightLevel), point.skyLightLevel,
								point.bottomYBlockPos, point.topYBlockPos, point.blockStateWrapper, point.biomeWrapper) : point);
						}

						source.setApiDataPointColumn(rx, rz, EDhApiWorldGenerationStep.LIGHT, made);
						columns++;
						continue;
					}

					// The ground's top, where the far land has it or (a road's bed, a house's plot) where it's asked for, the land
					// cut down or filled up to it; under it as it was; what grew on it gone.
					boolean water = ground.blockStateWrapper.isLiquid();
					int surface = ground.topYBlockPos;
					int floor = points.get(points.size() - 1).bottomYBlockPos;
					if (col.y() != FarPaint.NO_Y && !water && Math.abs(col.y() + 1 - surface) <= FarPaint.MOST_GRADE && col.y() > floor) {
						surface = col.y() + 1;
					}

					int top = surface;
					for (FarPaint.Span span : col.spans()) {
						top = Math.max(top, surface + span.top());
					}

					ceiling = Math.max(ceiling, top);
					int cursor = ceiling;
					boolean roofed = false;
					List<FarPaint.Span> spans = new ArrayList<>(col.spans());
					spans.sort(Comparator.comparingInt(FarPaint.Span::top).reversed());
					for (FarPaint.Span span : spans) {
						int spanTop = surface + span.top();
						int spanBottom = surface + span.bottom();
						if (cursor > spanTop) {
							made.add(DhApiTerrainDataPoint.create((byte)0, col.light(), roofed ? 0 : 15, spanTop, cursor, air, biome));
						}

						made.add(DhApiTerrainDataPoint.create((byte)0, span.light(), roofed ? 0 : 15, spanBottom, spanTop, wrap(span.block()), biome));
						roofed = true;
						cursor = spanBottom;
					}

					if (cursor > surface) {
						made.add(DhApiTerrainDataPoint.create((byte)0, col.light(), roofed ? 0 : 15, surface, cursor, air, biome));
					}

					// (The surface's own block, a block thick; then the land under it, down from there.)
					made.add(DhApiTerrainDataPoint.create((byte)0, Math.max(col.light(), ground.blockLightLevel), roofed ? 0 : 15, surface - 1, surface,
						col.surface() == null ? ground.blockStateWrapper : wrap(col.surface()), biome));
					int under = surface - 1;
					for (int i = at; i < points.size(); i++) {
						DhApiTerrainDataPoint point = points.get(i);
						int pointTop = Math.min(point.topYBlockPos, under);
						if (i == at && pointTop < under) {
							// (Filled up to its bed.)
							made.add(DhApiTerrainDataPoint.create((byte)0, 0, 0, pointTop, under, wrap(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()), biome));
						}

						if (pointTop > point.bottomYBlockPos) {
							made.add(DhApiTerrainDataPoint.create((byte)0, point.blockLightLevel, point.skyLightLevel, point.bottomYBlockPos, pointTop,
								point.blockStateWrapper, point.biomeWrapper));
							under = point.bottomYBlockPos;
						}
					}

					source.setApiDataPointColumn(rx, rz, EDhApiWorldGenerationStep.LIGHT, made);
					columns++;
				}
			}

			long took = System.nanoTime() - started;
			nanos.addAndGet(took);
			long n = painted.incrementAndGet();
			if (!said) {
				said = true;
				DrifterCivilizations.LOGGER.info("[far] Distant Horizons: the first piece with a sketched town or road painted, at {}, {} (detail {}, {} columns, {} us)",
					originX, originZ, detail, columns, took / 1000);
			}
		}
	}
}
