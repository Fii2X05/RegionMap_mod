package dev.atlasmap.client.map;

import dev.atlasmap.map.ChunkMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns one loaded chunk into a 16x16 block of top-down colors.
 * <p>
 * Overworld/End ("surface mode"): the highest block of each column, using
 * the world-surface heightmap. Nether ("ceiling mode"): the Nether has a
 * solid bedrock roof, so a plain surface scan would paint the whole map as
 * bedrock - instead each column is scanned downward starting just above the
 * player's height, which shows the cavern the player is actually in.
 * (Real cave overlays are a later phase.)
 * <p>
 * Relief shading is baked in: a column is lighter/darker depending on how
 * much higher/lower it is than its west and north neighbours.
 */
final class ChunkScanner {

	private static final int VOID = 0xFF06060A;

	private ChunkScanner() {
	}

	static ChunkMapData scan(Level level, int cx, int cz, boolean ceilingMode, int referenceY, long tick) {
		ChunkMapData out = new ChunkMapData();
		out.lastScanTick = tick;

		int minY = level.getMinY();
		int[] ys = new int[256];
		int[] rgb = new int[256];
		boolean[] flat = new boolean[256];
		boolean[] isVoid = new boolean[256];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				int wx = (cx << 4) + lx;
				int wz = (cz << 4) + lz;
				int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);

				int y;
				BlockState state;
				if (ceilingMode) {
					y = Math.min(referenceY + 3, topY - 1);
					state = null;
					for (; y >= minY; y--) {
						pos.set(wx, y, wz);
						BlockState s = level.getBlockState(pos);
						if (!s.isAir()) {
							state = s;
							break;
						}
					}
					if (state == null) {
						isVoid[i] = true;
						ys[i] = minY;
						continue;
					}
				} else {
					y = Math.max(minY, topY - 1);
					pos.set(wx, y, wz);
					state = level.getBlockState(pos);
				}

				MapColor mapColor = state.getMapColor(level, pos);
				// Blocks without a map color (glass, void air...): look a bit further down.
				for (int d = 0; d < 24 && mapColor == MapColor.NONE && y > minY; d++) {
					y--;
					pos.setY(y);
					state = level.getBlockState(pos);
					mapColor = state.getMapColor(level, pos);
				}
				if (mapColor == MapColor.NONE) {
					isVoid[i] = true;
					ys[i] = y;
					continue;
				}

				ys[i] = y;
				rgb[i] = mapColor.col;
				flat[i] = mapColor == MapColor.WATER;
			}
		}

		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				if (isVoid[i]) {
					out.argb[i] = VOID;
					continue;
				}
				double shade = 1.0;
				if (!flat[i]) {
					int diff = 0;
					int y = ys[i];
					if (lx > 0) {
						diff += y - ys[i - 1];
					} else if (!ceilingMode && level.hasChunk(cx - 1, cz)) {
						diff += y - (level.getHeight(Heightmap.Types.WORLD_SURFACE, (cx << 4) - 1, (cz << 4) + lz) - 1);
					}
					if (lz > 0) {
						diff += y - ys[i - 16];
					} else if (!ceilingMode && level.hasChunk(cx, cz - 1)) {
						diff += y - (level.getHeight(Heightmap.Types.WORLD_SURFACE, (cx << 4) + lx, (cz << 4) - 1) - 1);
					}
					shade = 1.0 + 0.05 * Math.max(-6, Math.min(6, diff));
				}
				out.argb[i] = 0xFF000000 | shadeRgb(rgb[i], shade);
			}
		}
		return out;
	}

	private static int shadeRgb(int rgb, double factor) {
		int r = clamp255((int) (((rgb >> 16) & 0xFF) * factor));
		int g = clamp255((int) (((rgb >> 8) & 0xFF) * factor));
		int b = clamp255((int) ((rgb & 0xFF) * factor));
		return (r << 16) | (g << 8) | b;
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
