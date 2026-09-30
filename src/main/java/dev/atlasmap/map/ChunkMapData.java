package dev.atlasmap.map;

/**
 * The top-down colors of one chunk: 16x16 ARGB pixels, index = z * 16 + x
 * (x/z being the block offset inside the chunk). Colors already include
 * relief shading, so consumers (minimap, world map) just copy them.
 * No Minecraft imports.
 */
public final class ChunkMapData {

	public final int[] argb = new int[256];
	/** Client tick this chunk was last scanned at (not persisted). */
	public long lastScanTick;
}
