package dev.atlasmap.client.map;

import dev.atlasmap.map.ChunkMapData;
import dev.atlasmap.map.DimensionMapCache;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the per-world, per-dimension map caches: keeps them filled by
 * scanning loaded chunks a few per tick (nearest first, so a fresh chunk
 * shows up within a couple of ticks and the tick cost stays small), loads
 * them from disk on first use, and saves them when leaving the world.
 * <p>
 * Only the Overworld, Nether and End are treated specially (the Nether uses
 * a ceiling-aware scan); any other dimension behaves like the Overworld.
 */
public final class MapManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("atlasmap-map");
	private static final int SCAN_RADIUS = 12;
	private static final int[][] OFFSETS = buildOffsets();
	private static final Map<String, DimensionMapCache> CACHES = new HashMap<>();

	private static String worldId;
	private static long tick;
	private static int cursor;
	private static long lastAutosave;

	private MapManager() {
	}

	private static int[][] buildOffsets() {
		List<int[]> list = new ArrayList<>();
		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
			for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
				list.add(new int[]{dx, dz});
			}
		}
		list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
		return list.toArray(new int[0][]);
	}

	public static void open(String id) {
		close();
		worldId = id;
		tick = 0;
		cursor = 0;
		lastAutosave = 0;
	}

	public static void close() {
		if (worldId != null) {
			for (Map.Entry<String, DimensionMapCache> e : CACHES.entrySet()) {
				if (e.getValue().isDirty()) {
					try {
						e.getValue().save(fileFor(e.getKey()));
					} catch (IOException ex) {
						LOGGER.warn("[AtlasMap] Failed to save map cache for {}", e.getKey(), ex);
					}
				}
			}
		}
		CACHES.clear();
		worldId = null;
	}

	public static boolean isOpen() {
		return worldId != null;
	}

	/** The cache for a dimension (loading it from disk the first time); null if no world is open. */
	public static DimensionMapCache cacheFor(String dimensionId) {
		if (worldId == null) {
			return null;
		}
		return CACHES.computeIfAbsent(dimensionId, id -> {
			try {
				return DimensionMapCache.load(id, fileFor(id));
			} catch (IOException e) {
				LOGGER.warn("[AtlasMap] Could not read map cache for {}, starting fresh", id, e);
				return new DimensionMapCache(id);
			}
		});
	}

	private static Path fileFor(String dimensionId) {
		return FabricLoader.getInstance().getGameDir()
				.resolve("atlasmap").resolve("maps")
				.resolve(sanitize(worldId))
				.resolve(sanitize(dimensionId) + ".bin");
	}

	private static String sanitize(String s) {
		return s.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	/** Called once per client tick. */
	public static void tick(Minecraft client) {
		if (worldId == null) {
			return;
		}
		Level level = client.level;
		if (level == null || client.player == null) {
			return;
		}

		String dimension = level.dimension().identifier().toString();
		DimensionMapCache cache = cacheFor(dimension);
		tick++;

		boolean ceiling = "minecraft:the_nether".equals(dimension);
		BlockPos playerPos = client.player.blockPosition();
		int playerChunkX = playerPos.getX() >> 4;
		int playerChunkZ = playerPos.getZ() >> 4;
		int maxScans = ceiling ? 4 : 12;
		int scans = 0;
		int checks = 0;

		while (checks < 160 && scans < maxScans) {
			if (cursor >= OFFSETS.length) {
				cursor = 0;
			}
			int[] o = OFFSETS[cursor++];
			checks++;

			int cx = playerChunkX + o[0];
			int cz = playerChunkZ + o[1];
			if (!level.hasChunk(cx, cz)) {
				continue;
			}
			ChunkMapData existing = cache.get(cx, cz);
			int dist = Math.max(Math.abs(o[0]), Math.abs(o[1]));
			long maxAge = dist <= 3 ? 40 : (dist <= 8 ? 200 : 1200);
			if (existing == null || tick - existing.lastScanTick > maxAge) {
				cache.put(cx, cz, ChunkScanner.scan(level, cx, cz, ceiling, playerPos.getY(), tick));
				scans++;
			}
		}

		if (tick - lastAutosave >= 6000) {
			lastAutosave = tick;
			for (Map.Entry<String, DimensionMapCache> e : CACHES.entrySet()) {
				if (e.getValue().isDirty()) {
					e.getValue().saveAsync(fileFor(e.getKey()));
				}
			}
		}
	}
}
