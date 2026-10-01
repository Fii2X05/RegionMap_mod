package dev.atlasmap.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.atlasmap.AtlasMap;
import dev.atlasmap.client.config.MinimapConfig;
import dev.atlasmap.client.map.MapManager;
import dev.atlasmap.client.map.Pixels;
import dev.atlasmap.map.ChunkMapData;
import dev.atlasmap.map.DimensionMapCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;

/**
 * Builds the minimap's working texture by reading from the SAME per-world
 * chunk-color cache the fullscreen map uses ({@link MapManager}), instead
 * of scanning blocks itself. Two benefits: the minimap and world map always
 * agree pixel-for-pixel, and blocks only get sampled once no matter how
 * many map surfaces are open.
 * <p>
 * Bug fix: the previous version wrote colors straight into
 * {@code NativeImage#setPixelABGR} as plain 0xAARRGGBB. That method's name
 * describes the int's BYTE order (A,B,G,R low-to-high), not a "just plug
 * ARGB in" call - passing ARGB directly swaps the red and blue channels,
 * which is exactly the pink ice / red water seen in the reported
 * screenshot. {@link Pixels#toAbgr(int)} does the correct swap.
 */
public final class MinimapRenderer {

	// 512 (bukan 256): saat peta berotasi, sudut kotak minimap menjangkau sampai span*0.71 dari
	// pusat. Dengan 256, zoom-out jauh membuat sudutnya kosong/gelap ketika diputar.
	private static final int WORKING_SIZE = 512;
	private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(AtlasMap.MOD_ID, "minimap_dynamic");
	private static final int UNSCANNED_COLOR = Pixels.toAbgr(0xFF141414);

	private NativeImage image;
	private DynamicTexture texture;
	private long lastRefreshMs = -1;

	private int lastCenterBlockX = Integer.MIN_VALUE;
	private int lastCenterBlockZ = Integer.MIN_VALUE;

	public void init() {
		image = new NativeImage(NativeImage.Format.RGBA, WORKING_SIZE, WORKING_SIZE, false);
		texture = new DynamicTexture(() -> "atlasmap_minimap", image);
		Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
	}

	public Identifier textureId() {
		return TEXTURE_ID;
	}

	public int workingSize() {
		return WORKING_SIZE;
	}

	/** Blok yang berada di piksel tengah tekstur yang terakhir dibangun. */
	public int centerBlockX() {
		return lastCenterBlockX;
	}

	public int centerBlockZ() {
		return lastCenterBlockZ;
	}

	/** False sebelum tekstur pertama selesai dibangun (mencegah blit tekstur yang belum ada). */
	public boolean isReady() {
		return image != null && lastCenterBlockX != Integer.MIN_VALUE;
	}

	/**
	 * @param playerX posisi X pemain yang sudah diinterpolasi (partialTick)
	 * @param playerZ posisi Z pemain yang sudah diinterpolasi (partialTick)
	 */
	public void tick(MinimapConfig config, double playerX, double playerZ) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || !MapManager.isOpen()) {
			return;
		}
		if (image == null) {
			init();
		}

		long now = System.currentTimeMillis();
		int centerX = Mth.floor(playerX);
		int centerZ = Mth.floor(playerZ);
		boolean moved = centerX != lastCenterBlockX || centerZ != lastCenterBlockZ;

		if (lastRefreshMs >= 0 && (now - lastRefreshMs) < config.refreshIntervalMs && !moved) {
			return;
		}
		lastRefreshMs = now;
		lastCenterBlockX = centerX;
		lastCenterBlockZ = centerZ;

		String dimension = client.level.dimension().identifier().toString();
		DimensionMapCache cache = MapManager.cacheFor(dimension);
		rebuild(cache, centerX, centerZ);
	}

	private void rebuild(DimensionMapCache cache, int centerBlockX, int centerBlockZ) {
		int half = WORKING_SIZE / 2;

		// Walk chunk-by-chunk (not pixel-by-pixel) so a fully-cached chunk is
		// one array lookup instead of 256 hash lookups.
		int minChunkX = (centerBlockX - half) >> 4;
		int maxChunkX = (centerBlockX + half) >> 4;
		int minChunkZ = (centerBlockZ - half) >> 4;
		int maxChunkZ = (centerBlockZ + half) >> 4;

		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				ChunkMapData data = cache.get(cx, cz);
				for (int lz = 0; lz < 16; lz++) {
					int worldZ = (cz << 4) + lz;
					int pz = worldZ - centerBlockZ + half;
					if (pz < 0 || pz >= WORKING_SIZE) continue;
					for (int lx = 0; lx < 16; lx++) {
						int worldX = (cx << 4) + lx;
						int px = worldX - centerBlockX + half;
						if (px < 0 || px >= WORKING_SIZE) continue;

						int c = data == null ? UNSCANNED_COLOR : Pixels.toAbgr(data.argb[lz * 16 + lx]);
						image.setPixelABGR(px, pz, c);
					}
				}
			}
		}

		texture.upload();
	}

	public void close() {
		if (texture != null) {
			texture.close();
		}
	}
}
