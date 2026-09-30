package dev.atlasmap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.atlasmap.AtlasMap;
import dev.atlasmap.map.ChunkMapData;
import dev.atlasmap.map.DimensionMapCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fixed pool of reusable GPU textures for the fullscreen map. One tile is
 * {@link #TILE_BLOCKS} x {@link #TILE_BLOCKS} blocks (8x8 chunks), one texel
 * per block. Tiles are assigned to whatever is on screen, rebuilt only when
 * the cache says their content changed, and recycled least-recently-used -
 * so memory stays bounded no matter how much of the world is explored, and
 * no texture ever has to be released.
 */
public final class MapTilePool {

	public static final int TILE_CHUNKS = DimensionMapCache.TILE_CHUNKS;
	public static final int TILE_BLOCKS = TILE_CHUNKS * 16;
	public static final MapTilePool INSTANCE = new MapTilePool();

	private static final int MAX_TILES = 256;
	private static final int MAX_REBUILDS_PER_FRAME = 16;

	public static final class Tile {
		public final Identifier id;
		private final NativeImage image;
		private final DynamicTexture texture;
		private String key;
		private long version = -1;
		private long lastUsedFrame = -1;

		private Tile(Identifier id, NativeImage image, DynamicTexture texture) {
			this.id = id;
			this.image = image;
			this.texture = texture;
		}
	}

	private final List<Tile> tiles = new ArrayList<>();
	private final Map<String, Tile> byKey = new HashMap<>();
	private int rebuilds;

	private MapTilePool() {
	}

	public void beginFrame() {
		rebuilds = 0;
	}

	/** Returns a ready-to-draw tile, or null if the pool/rebuild budget is exhausted this frame. */
	public Tile obtain(DimensionMapCache cache, int tx, int tz, long frame) {
		String key = cache.dimensionId() + "|" + tx + "|" + tz;
		Tile tile = byKey.get(key);
		if (tile == null) {
			tile = allocate(frame);
			if (tile == null) {
				return null;
			}
			if (tile.key != null) {
				byKey.remove(tile.key);
			}
			tile.key = key;
			tile.version = -1;
			byKey.put(key, tile);
		}

		long version = cache.tileVersion(tx, tz);
		if (tile.version != version) {
			if (rebuilds >= MAX_REBUILDS_PER_FRAME) {
				if (tile.version < 0) {
					return null; // never built yet - try again next frame
				}
				tile.lastUsedFrame = frame;
				return tile; // stale but drawable; refresh next frame
			}
			rebuild(tile, cache, tx, tz);
			tile.version = version;
			rebuilds++;
		}
		tile.lastUsedFrame = frame;
		return tile;
	}

	private Tile allocate(long frame) {
		if (tiles.size() < MAX_TILES) {
			Identifier id = Identifier.fromNamespaceAndPath(AtlasMap.MOD_ID, "map_tile_" + tiles.size());
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, TILE_BLOCKS, TILE_BLOCKS, true);
			DynamicTexture texture = new DynamicTexture(() -> "atlasmap_map_tile", image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			Tile tile = new Tile(id, image, texture);
			tiles.add(tile);
			return tile;
		}
		Tile oldest = null;
		for (Tile t : tiles) {
			if (t.lastUsedFrame < frame && (oldest == null || t.lastUsedFrame < oldest.lastUsedFrame)) {
				oldest = t;
			}
		}
		return oldest;
	}

	private void rebuild(Tile tile, DimensionMapCache cache, int tx, int tz) {
		for (int cdz = 0; cdz < TILE_CHUNKS; cdz++) {
			for (int cdx = 0; cdx < TILE_CHUNKS; cdx++) {
				ChunkMapData data = cache.get(tx * TILE_CHUNKS + cdx, tz * TILE_CHUNKS + cdz);
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						int px = cdx * 16 + lx;
						int pz = cdz * 16 + lz;
						int c = data == null ? 0 : Pixels.toAbgr(data.argb[lz * 16 + lx]);
						tile.image.setPixelABGR(px, pz, c);
					}
				}
			}
		}
		tile.texture.upload();
	}
}
