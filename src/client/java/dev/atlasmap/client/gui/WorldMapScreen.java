package dev.atlasmap.client.gui;

import dev.atlasmap.client.ScreenUtil;
import dev.atlasmap.client.map.MapManager;
import dev.atlasmap.client.map.MapTilePool;
import dev.atlasmap.client.region.ClientRegionState;
import dev.atlasmap.client.waypoint.ClientWaypointState;
import dev.atlasmap.map.DimensionMapCache;
import dev.atlasmap.region.ChunkPos2D;
import dev.atlasmap.region.Region;
import dev.atlasmap.region.RegionManager;
import dev.atlasmap.waypoint.Waypoint;
import dev.atlasmap.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fullscreen, Xaero-style world map: pan by dragging, zoom with the scroll
 * wheel, right-click for a menu (add a waypoint, start/stop drawing a
 * region, manage regions). Reuses the same {@link DimensionMapCache} the
 * minimap reads from via {@link MapManager}, rendered through a pool of
 * reusable tile textures ({@link MapTilePool}) so panning/zooming around a
 * heavily-explored world doesn't require rebuilding huge textures.
 * <p>
 * Zoom is implemented by pushing a scaled matrix and blitting each tile at
 * its native 1:1 resolution inside that scale, rather than by stretching
 * the blit itself - every matrix/blit call used here (`pose().pushMatrix()`,
 * `.translate()`, `.scale()`, `.popMatrix()`, and the
 * `blit(RenderPipelines.GUI_TEXTURED, ...)` overload) is one already proven
 * to compile elsewhere in this mod (minimap, banner preview), so this
 * avoids re-guessing a stretching overload that was never confirmed.
 * <p>
 * Mouse handling follows the confirmed 26.x shape: clicks/releases carry a
 * {@code MouseButtonEvent} (coordinates via {@code event.x()/y()}), and
 * {@code mouseDragged} receives the frame's movement as a delta
 * ({@code offsetX}/{@code offsetY}), not an absolute position.
 */
public final class WorldMapScreen extends Screen {

	private static final double MIN_SCALE = 0.25;
	private static final double MAX_SCALE = 24.0;
	private static final int WAYPOINT_HIT_RADIUS = 6;

	private double centerX;
	private double centerZ;
	private double blocksPerPixel = 2.0;
	private String dimensionId = "minecraft:overworld";

	private boolean paintMode;
	private UUID paintRegionId;
	private ContextMenu contextMenu;
	private long frame;

	public WorldMapScreen() {
		super(Component.literal("AtlasMap World Map"));
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			centerX = client.player.getX();
			centerZ = client.player.getZ();
		}
		if (client.level != null) {
			dimensionId = client.level.dimension().identifier().toString();
		}
	}

	@Override
	protected void init() {
		// No persistent widgets - everything is drawn/hit-tested manually so
		// panning/zooming/painting can happen without rebuilding the screen.
	}

	private DimensionMapCache cache() {
		return MapManager.cacheFor(dimensionId);
	}

	private RegionManager regions() {
		return ClientRegionState.manager();
	}

	private WaypointManager waypoints() {
		return ClientWaypointState.manager();
	}

	// ---- coordinate conversion --------------------------------------------

	private double screenToWorldX(double sx) {
		return centerX + (sx - this.width / 2.0) * blocksPerPixel;
	}

	private double screenToWorldZ(double sy) {
		return centerZ + (sy - this.height / 2.0) * blocksPerPixel;
	}

	private double worldToScreenX(double wx) {
		return this.width / 2.0 + (wx - centerX) / blocksPerPixel;
	}

	private double worldToScreenZ(double wz) {
		return this.height / 2.0 + (wz - centerZ) / blocksPerPixel;
	}

	// ---- input -------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (contextMenu != null) {
			ContextMenu.Item item = contextMenu.itemAt(event.x(), event.y());
			contextMenu = null;
			if (item != null && item.enabled()) {
				item.action().run();
			}
			return true;
		}

		if (event.button() == 1) {
			contextMenu = new ContextMenu(this.font, (int) event.x(), (int) event.y(),
					buildContextMenu(screenToWorldX(event.x()), screenToWorldZ(event.y())));
			return true;
		}

		if (event.button() == 0) {
			if (!paintMode) {
				Waypoint hit = findWaypointNear(event.x(), event.y());
				if (hit != null) {
					ScreenUtil.open(new EditWaypointScreen(this, hit));
					return true;
				}
			} else if (paintRegionId != null && regions() != null) {
				Region target = regions().getById(paintRegionId);
				if (target != null) {
					ChunkPos2D pos = ChunkPos2D.fromBlock((int) screenToWorldX(event.x()), (int) screenToWorldZ(event.y()));
					regions().paint(target, pos, !target.containsChunk(pos));
					regions().save();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
		if (contextMenu != null) {
			return true;
		}
		if (event.button() == 0) {
			if (paintMode && paintRegionId != null && regions() != null) {
				Region target = regions().getById(paintRegionId);
				if (target != null) {
					ChunkPos2D pos = ChunkPos2D.fromBlock((int) screenToWorldX(event.x()), (int) screenToWorldZ(event.y()));
					if (!target.containsChunk(pos)) {
						regions().paint(target, pos, true);
						regions().save();
					}
				}
			} else {
				centerX -= offsetX * blocksPerPixel;
				centerZ -= offsetY * blocksPerPixel;
			}
			return true;
		}
		return super.mouseDragged(event, offsetX, offsetY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double factor = verticalAmount > 0 ? 0.8 : 1.25;
		blocksPerPixel = Math.max(MIN_SCALE, Math.min(MAX_SCALE, blocksPerPixel * factor));
		return true;
	}

	private Waypoint findWaypointNear(double sx, double sy) {
		WaypointManager manager = waypoints();
		if (manager == null) {
			return null;
		}
		Waypoint best = null;
		double bestDist = WAYPOINT_HIT_RADIUS;
		for (Waypoint wp : manager.all()) {
			if (!wp.getDimension().equals(dimensionId)) {
				continue;
			}
			double dx = worldToScreenX(wp.getX() + 0.5) - sx;
			double dy = worldToScreenZ(wp.getZ() + 0.5) - sy;
			double dist = Math.sqrt(dx * dx + dy * dy);
			if (dist < bestDist) {
				bestDist = dist;
				best = wp;
			}
		}
		return best;
	}

	// ---- context menu actions ----------------------------------------------

	private List<ContextMenu.Item> buildContextMenu(double worldX, double worldZ) {
		List<ContextMenu.Item> items = new ArrayList<>();
		items.add(new ContextMenu.Item("Add waypoint here", () -> addWaypointAt(worldX, worldZ)));

		if (paintMode) {
			items.add(new ContextMenu.Item("Stop drawing region", () -> {
				paintMode = false;
				paintRegionId = null;
			}));
		} else {
			ChunkPos2D chunkHere = ChunkPos2D.fromBlock((int) worldX, (int) worldZ);
			items.add(new ContextMenu.Item("New region here", () -> startNewRegionAt(chunkHere)));

			RegionManager manager = regions();
			if (manager != null) {
				int shown = 0;
				for (Region region : manager.all()) {
					if (!region.getDimension().equals(dimensionId)) {
						continue;
					}
					if (shown++ >= 6) {
						break;
					}
					UUID id = region.getId();
					items.add(new ContextMenu.Item("Draw: " + region.getName(), () -> {
						paintRegionId = id;
						paintMode = true;
					}));
				}
			}
		}

		items.add(new ContextMenu.Item("Manage regions...", () -> ScreenUtil.open(new RegionEditorScreen())));
		items.add(new ContextMenu.Item("Waypoint list...", () -> ScreenUtil.open(new WaypointListScreen())));
		return items;
	}

	private void addWaypointAt(double worldX, double worldZ) {
		WaypointManager manager = waypoints();
		if (manager == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int y = client.player != null ? client.player.blockPosition().getY() : 64;
		Waypoint created = Waypoint.create("Waypoint", (int) worldX, y, (int) worldZ, dimensionId, ColorPalette.COLORS[0]);
		manager.add(created);
		manager.save();
		ScreenUtil.open(new EditWaypointScreen(this, created));
	}

	private void startNewRegionAt(ChunkPos2D chunkHere) {
		RegionManager manager = regions();
		if (manager == null) {
			return;
		}
		Region created = Region.create("Region " + (manager.all().size() + 1), ColorPalette.COLORS[0], dimensionId);
		created.addChunk(chunkHere);
		manager.add(created);
		manager.save();
		paintRegionId = created.getId();
		paintMode = true;
	}

	// ---- rendering -----------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0xFF000000);

		DimensionMapCache cache = cache();
		if (cache != null) {
			renderTiles(graphics, cache);
			renderRegionLines(graphics, cache);
			renderWaypoints(graphics);
			renderPlayerMarker(graphics);
		}

		renderHeader(graphics, mouseX, mouseY);

		if (contextMenu != null) {
			contextMenu.render(graphics, this.font, mouseX, mouseY);
		}
	}

	private void renderTiles(GuiGraphicsExtractor graphics, DimensionMapCache cache) {
		int tileBlocks = MapTilePool.TILE_BLOCKS;
		double worldLeft = screenToWorldX(0);
		double worldRight = screenToWorldX(this.width);
		double worldTop = screenToWorldZ(0);
		double worldBottom = screenToWorldZ(this.height);

		int tileMinX = (int) Math.floor(worldLeft / tileBlocks) - 1;
		int tileMaxX = (int) Math.floor(worldRight / tileBlocks) + 1;
		int tileMinZ = (int) Math.floor(worldTop / tileBlocks) - 1;
		int tileMaxZ = (int) Math.floor(worldBottom / tileBlocks) + 1;

		frame++;
		MapTilePool.INSTANCE.beginFrame();
		float scale = (float) (1.0 / blocksPerPixel);

		for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
			for (int tx = tileMinX; tx <= tileMaxX; tx++) {
				MapTilePool.Tile tile = MapTilePool.INSTANCE.obtain(cache, tx, tz, frame);
				if (tile == null) {
					continue;
				}
				double screenX = worldToScreenX(tx * (double) tileBlocks);
				double screenY = worldToScreenZ(tz * (double) tileBlocks);

				graphics.pose().pushMatrix();
				graphics.pose().translate((float) screenX, (float) screenY);
				graphics.pose().scale(scale, scale);
				graphics.blit(RenderPipelines.GUI_TEXTURED, tile.id, 0, 0, 0, 0, tileBlocks, tileBlocks, tileBlocks, tileBlocks);
				graphics.pose().popMatrix();
			}
		}
	}

	private void renderRegionLines(GuiGraphicsExtractor graphics, DimensionMapCache cache) {
		RegionManager manager = regions();
		if (manager == null || manager.all().isEmpty()) {
			return;
		}
		double cellSize = 16.0 / blocksPerPixel;
		if (cellSize < 1.0) {
			return; // zoomed out too far for per-chunk lines to mean anything
		}
		int thickness = Math.max(1, (int) (cellSize / 6));

		for (Region region : manager.all()) {
			if (!region.getDimension().equals(dimensionId)) {
				continue;
			}
			int alpha = Math.round(region.getOpacity() * 255f);
			int color = (alpha << 24) | (region.getColorArgb() & 0x00FFFFFF);

			for (ChunkPos2D pos : region.getChunks()) {
				double sx = worldToScreenX(pos.minBlockX());
				double sy = worldToScreenZ(pos.minBlockZ());
				if (sx + cellSize < 0 || sy + cellSize < 0 || sx > this.width || sy > this.height) {
					continue;
				}
				int x0 = (int) sx;
				int y0 = (int) sy;
				int x1 = (int) (sx + cellSize);
				int y1 = (int) (sy + cellSize);

				if (!region.containsChunk(new ChunkPos2D(pos.x(), pos.z() - 1))) {
					graphics.fill(x0, y0, x1, y0 + thickness, color);
				}
				if (!region.containsChunk(new ChunkPos2D(pos.x(), pos.z() + 1))) {
					graphics.fill(x0, y1 - thickness, x1, y1, color);
				}
				if (!region.containsChunk(new ChunkPos2D(pos.x() - 1, pos.z()))) {
					graphics.fill(x0, y0, x0 + thickness, y1, color);
				}
				if (!region.containsChunk(new ChunkPos2D(pos.x() + 1, pos.z()))) {
					graphics.fill(x1 - thickness, y0, x1, y1, color);
				}
			}
		}
	}

	private void renderWaypoints(GuiGraphicsExtractor graphics) {
		WaypointManager manager = waypoints();
		if (manager == null) {
			return;
		}
		for (Waypoint wp : manager.all()) {
			if (!wp.isVisible() || !wp.getDimension().equals(dimensionId)) {
				continue;
			}
			int sx = (int) worldToScreenX(wp.getX() + 0.5);
			int sy = (int) worldToScreenZ(wp.getZ() + 0.5);
			if (sx < -20 || sy < -20 || sx > this.width + 20 || sy > this.height + 20) {
				continue;
			}
			graphics.outline(sx - 4, sy - 4, 8, 8, 0xFF000000);
			graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, wp.getColorArgb() | 0xFF000000);
			graphics.text(this.font, wp.getName(), sx + 6, sy - 4, 0xFFFFFFFF, true);
		}
	}

	private void renderPlayerMarker(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null
				|| !client.level.dimension().identifier().toString().equals(dimensionId)) {
			return;
		}
		int sx = (int) worldToScreenX(client.player.getX());
		int sy = (int) worldToScreenZ(client.player.getZ());
		graphics.outline(sx - 4, sy - 4, 8, 8, 0xFF000000);
		graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFFFF5555);
	}

	private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String left = dimensionId.replace("minecraft:", "");
		String coords = "x=" + (int) screenToWorldX(mouseX) + " z=" + (int) screenToWorldZ(mouseY);
		String hint = paintMode ? "Drawing region - left-click/drag to paint, right-click to stop"
				: "Right-click for options - scroll to zoom - drag to pan";

		graphics.fill(0, 0, this.width, 16, 0x99000000);
		graphics.text(this.font, left, 4, 4, 0xFFFFFFFF, false);
		graphics.text(this.font, coords, this.width / 2 - this.font.width(coords) / 2, 4, 0xFFAAAAAA, false);
		graphics.text(this.font, hint, this.width - this.font.width(hint) - 4, 4,
				paintMode ? 0xFFFF5555 : 0xFFAAAAAA, false);
	}
}
