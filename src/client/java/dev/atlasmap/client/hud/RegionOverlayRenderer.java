package dev.atlasmap.client.hud;

import dev.atlasmap.client.region.ClientRegionState;
import dev.atlasmap.region.ChunkPos2D;
import dev.atlasmap.region.Region;
import dev.atlasmap.region.RegionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class RegionOverlayRenderer {

	private RegionOverlayRenderer() {
	}

	static void render(GuiGraphicsExtractor graphics, int mapX, int mapY, int size, int centerX, int centerY,
	                    double uvScale) {
		RegionManager manager = ClientRegionState.manager();
		if (manager == null || manager.all().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		String dimension = client.level.dimension().identifier().toString();
		int playerChunkX = client.player.blockPosition().getX() >> 4;
		int playerChunkZ = client.player.blockPosition().getZ() >> 4;

		int chunkRadius = (int) Math.ceil((size * uvScale) / 16.0) + 1;
		int cellSize = (int) Math.max(1, 16 / uvScale);
		int lineThickness = Math.max(1, cellSize / 6);

		for (Region region : manager.all()) {
			if (!region.getDimension().equals(dimension)) {
				continue;
			}
			int alpha = Math.round(region.getOpacity() * 255f);
			int color = (alpha << 24) | (region.getColorArgb() & 0x00FFFFFF);

			for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
				for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
					ChunkPos2D here = new ChunkPos2D(cx, cz);
					if (!region.containsChunk(here)) {
						continue;
					}

					double worldMinX = (cx << 4) - client.player.getX();
					double worldMinZ = (cz << 4) - client.player.getZ();
					int screenMinX = centerX + (int) (worldMinX / uvScale);
					int screenMinZ = centerY + (int) (worldMinZ / uvScale);

					boolean north = !region.containsChunk(new ChunkPos2D(cx, cz - 1));
					boolean south = !region.containsChunk(new ChunkPos2D(cx, cz + 1));
					boolean west = !region.containsChunk(new ChunkPos2D(cx - 1, cz));
					boolean east = !region.containsChunk(new ChunkPos2D(cx + 1, cz));

					if (north) {
						graphics.fill(screenMinX, screenMinZ, screenMinX + cellSize, screenMinZ + lineThickness, color);
					}
					if (south) {
						graphics.fill(screenMinX, screenMinZ + cellSize - lineThickness, screenMinX + cellSize,
								screenMinZ + cellSize, color);
					}
					if (west) {
						graphics.fill(screenMinX, screenMinZ, screenMinX + lineThickness, screenMinZ + cellSize, color);
					}
					if (east) {
						graphics.fill(screenMinX + cellSize - lineThickness, screenMinZ, screenMinX + cellSize,
								screenMinZ + cellSize, color);
					}
				}
			}
		}
	}
}
