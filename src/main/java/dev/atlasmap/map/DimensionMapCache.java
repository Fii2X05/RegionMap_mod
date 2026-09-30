package dev.atlasmap.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Every chunk the player has seen in ONE dimension, as pixel colors. This
 * is the shared data source for the minimap and the fullscreen world map,
 * and it is what makes explored-but-unloaded terrain stay visible.
 * <p>
 * Chunks are grouped into "tiles" of {@link #TILE_CHUNKS} x {@link #TILE_CHUNKS}
 * chunks; each tile has a version counter that goes up whenever one of its
 * chunks changes, so renderers know when a cached texture is stale.
 * <p>
 * On-disk format (gzip): magic, format, count, then per chunk: cx, cz and
 * 256 ARGB ints. Writes go to a temp file that is atomically moved into
 * place, so a crash mid-save can never corrupt the previous file.
 */
public final class DimensionMapCache {

	public static final int TILE_CHUNKS = 8;

	private static final Logger LOGGER = LoggerFactory.getLogger("atlasmap-map");
	private static final int MAGIC = 0x41544C53; // "ATLS"
	private static final int FORMAT = 1;
	private static final Object SAVE_LOCK = new Object();

	private final String dimensionId;
	private final Map<Long, ChunkMapData> chunks = new HashMap<>();
	private final Map<Long, Long> tileVersions = new HashMap<>();
	private long modCount;
	private boolean dirty;

	public DimensionMapCache(String dimensionId) {
		this.dimensionId = dimensionId;
	}

	public static long key(int x, int z) {
		return ((long) x & 0xFFFFFFFFL) | ((long) z << 32);
	}

	public static int keyX(long key) {
		return (int) key;
	}

	public static int keyZ(long key) {
		return (int) (key >> 32);
	}

	public String dimensionId() {
		return dimensionId;
	}

	public ChunkMapData get(int cx, int cz) {
		return chunks.get(key(cx, cz));
	}

	public int chunkCount() {
		return chunks.size();
	}

	public long modCount() {
		return modCount;
	}

	public boolean isDirty() {
		return dirty;
	}

	/** Version of the tile at tile coordinates (tx, tz); 0 means "no data at all in this tile". */
	public long tileVersion(int tx, int tz) {
		return tileVersions.getOrDefault(key(tx, tz), 0L);
	}

	/**
	 * Stores a freshly scanned chunk. Returns false (and only refreshes the
	 * scan time) when the pixels are identical to what was already stored,
	 * so an unchanged chunk doesn't invalidate textures or trigger saves.
	 */
	public boolean put(int cx, int cz, ChunkMapData data) {
		long k = key(cx, cz);
		ChunkMapData old = chunks.get(k);
		if (old != null && Arrays.equals(old.argb, data.argb)) {
			old.lastScanTick = data.lastScanTick;
			return false;
		}
		chunks.put(k, data);
		modCount++;
		dirty = true;
		bumpTile(cx, cz);
		return true;
	}

	private void bumpTile(int cx, int cz) {
		long tk = key(Math.floorDiv(cx, TILE_CHUNKS), Math.floorDiv(cz, TILE_CHUNKS));
		tileVersions.merge(tk, 1L, Long::sum);
	}

	// ---- persistence -----------------------------------------------------

	private record Snapshot(long[] keys, int[][] data) {
	}

	private Snapshot snapshot() {
		long[] keys = new long[chunks.size()];
		int[][] data = new int[chunks.size()][];
		int i = 0;
		for (Map.Entry<Long, ChunkMapData> e : chunks.entrySet()) {
			keys[i] = e.getKey();
			data[i] = e.getValue().argb.clone();
			i++;
		}
		return new Snapshot(keys, data);
	}

	/** Synchronous save (used when leaving a world). */
	public void save(Path file) throws IOException {
		Snapshot s = snapshot();
		dirty = false;
		writeSnapshot(s, file);
	}

	/** Snapshots on the calling thread, writes on a background thread (used for autosave). */
	public void saveAsync(Path file) {
		Snapshot s = snapshot();
		dirty = false;
		Thread t = new Thread(() -> {
			try {
				writeSnapshot(s, file);
			} catch (IOException e) {
				LOGGER.warn("[AtlasMap] Failed to autosave map cache {}", file, e);
			}
		}, "AtlasMap-MapSave");
		t.setDaemon(false);
		t.start();
	}

	private static void writeSnapshot(Snapshot s, Path file) throws IOException {
		synchronized (SAVE_LOCK) {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
					new GZIPOutputStream(Files.newOutputStream(tmp)), 1 << 16))) {
				out.writeInt(MAGIC);
				out.writeInt(FORMAT);
				out.writeInt(s.keys().length);
				for (int i = 0; i < s.keys().length; i++) {
					out.writeInt(keyX(s.keys()[i]));
					out.writeInt(keyZ(s.keys()[i]));
					for (int v : s.data()[i]) {
						out.writeInt(v);
					}
				}
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/** Loads a cache from disk; returns an empty cache if the file doesn't exist. */
	public static DimensionMapCache load(String dimensionId, Path file) throws IOException {
		DimensionMapCache cache = new DimensionMapCache(dimensionId);
		if (!Files.exists(file)) {
			return cache;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(
				new GZIPInputStream(Files.newInputStream(file)), 1 << 16))) {
			if (in.readInt() != MAGIC) {
				throw new IOException("Not an AtlasMap map file");
			}
			if (in.readInt() != FORMAT) {
				throw new IOException("Unsupported AtlasMap map format");
			}
			int count = in.readInt();
			for (int i = 0; i < count; i++) {
				int cx = in.readInt();
				int cz = in.readInt();
				ChunkMapData d = new ChunkMapData();
				for (int j = 0; j < 256; j++) {
					d.argb[j] = in.readInt();
				}
				d.lastScanTick = -1_000_000L; // treat as very old so it gets refreshed once loaded
				cache.chunks.put(key(cx, cz), d);
				cache.bumpTile(cx, cz);
			}
		}
		cache.modCount++;
		cache.dirty = false;
		return cache;
	}
}
