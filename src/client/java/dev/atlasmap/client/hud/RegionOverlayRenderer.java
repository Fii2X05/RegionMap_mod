package dev.atlasmap.client.hud;

import dev.atlasmap.client.region.ClientRegionState;
import dev.atlasmap.region.ChunkPos2D;
import dev.atlasmap.region.Region;
import dev.atlasmap.region.RegionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Garis batas region di minimap. Digambar di dalam matriks yang sama dengan tekstur peta
 * (diputar + diskalakan), dengan satuan 1 unit = 1 blok, sehingga ikut berputar bersama peta.
 */
final class RegionOverlayRenderer {

	private RegionOverlayRenderer() {
	}

	/** @param halfSize setengah sisi kotak minimap dalam piksel GUI */
	static void render(GuiGraphicsExtractor graphics, RadarView view, double halfSize) {
		RegionManager manager = ClientRegionState.manager();
		if (manager == null || manager.all().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		String dimension = client.level.dimension().identifier().toString();
		double ppb = view.pixelsPerBlock();

		// Origin dibulatkan ke blok supaya koordinat yang digambar kecil (presisi float aman
		// di koordinat jauh); sisa pecahannya ditangani lewat translate.
		int originBlockX = Mth.floor(view.originX());
		int originBlockZ = Mth.floor(view.originZ());
		int originChunkX = originBlockX >> 4;
		int originChunkZ = originBlockZ >> 4;

		// Sudut kotak yang berotasi menjangkau halfSize*sqrt(2) piksel dari pusat.
		int chunkRadius = (int) Math.ceil((halfSize * 1.42 / ppb) / 16.0) + 1;
		// Ketebalan garis ~2 piksel, dinyatakan dalam blok.
		int thickness = Math.min(8, Math.max(1, (int) Math.round(2.0 / ppb)));

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) view.centerX(), (float) view.centerY());
		pose.rotate((float) view.rotation());
		pose.scale((float) ppb, (float) ppb);
		pose.translate((float) -(view.originX() - originBlockX), (float) -(view.originZ() - originBlockZ));

		for (Region region : manager.all()) {
			if (!region.getDimension().equals(dimension)) {
				continue;
			}
			int alpha = Math.round(region.getOpacity() * 255f);
			int color = (alpha << 24) | (region.getColorArgb() & 0x00FFFFFF);

			for (int cx = originChunkX - chunkRadius; cx <= originChunkX + chunkRadius; cx++) {
				for (int cz = originChunkZ - chunkRadius; cz <= originChunkZ + chunkRadius; cz++) {
					if (!region.containsChunk(new ChunkPos2D(cx, cz))) {
						continue;
					}

					int x0 = (cx << 4) - originBlockX;
					int z0 = (cz << 4) - originBlockZ;
					int x1 = x0 + 16;
					int z1 = z0 + 16;

					if (!region.containsChunk(new ChunkPos2D(cx, cz - 1))) {
						graphics.fill(x0, z0, x1, z0 + thickness, color);
					}
					if (!region.containsChunk(new ChunkPos2D(cx, cz + 1))) {
						graphics.fill(x0, z1 - thickness, x1, z1, color);
					}
					if (!region.containsChunk(new ChunkPos2D(cx - 1, cz))) {
						graphics.fill(x0, z0, x0 + thickness, z1, color);
					}
					if (!region.containsChunk(new ChunkPos2D(cx + 1, cz))) {
						graphics.fill(x1 - thickness, z0, x1, z1, color);
					}
				}
			}
		}

		pose.popMatrix();
	}
}
