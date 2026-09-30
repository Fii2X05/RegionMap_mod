package dev.atlasmap.client.gui;

import dev.atlasmap.client.ScreenUtil;
import dev.atlasmap.client.region.ClientRegionState;
import dev.atlasmap.region.ChunkPos2D;
import dev.atlasmap.region.Region;
import dev.atlasmap.region.RegionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grid-per-chunk region editor: a grid of chunk cells around the player on
 * the left, a region list + create/rename/color/opacity/banner/delete panel
 * on the right. Click a cell to toggle it into whichever region is
 * currently selected.
 * <p>
 * MAPPING NOTES: as of the mapping set 26.x carries forward, mouse input
 * handling changed shape - {@code mouseClicked(double, double, int)} became
 * {@code mouseClicked(MouseButtonEvent event, boolean doubleClick)} (get
 * coordinates via {@code event.x()}/{@code event.y()}), while
 * {@code mouseMoved(double, double)} kept its old signature but is
 * confirmed to return {@code void}, not {@code boolean} (this was wrong in
 * an earlier draft of this file and is fixed here).
 */
public final class RegionEditorScreen extends Screen {

	private static final int CELL = 22;
	private static final int GRID_RADIUS = 6; // chunks in each direction -> 13x13 grid
	private static UUID selectedRegionId;

	private RegionManager manager;
	private int gridOriginX;
	private int gridOriginY;
	private int playerChunkX;
	private int playerChunkZ;
	private String dimension;
	private int hoverCol = -1;
	private int hoverRow = -1;

	public RegionEditorScreen() {
		super(Component.literal("AtlasMap Regions"));
	}

	@Override
	protected void init() {
		this.manager = ClientRegionState.manager();
		Minecraft client = Minecraft.getInstance();

		if (manager == null || client.player == null || client.level == null) {
			this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> ScreenUtil.open(null))
					.bounds(this.width / 2 - 50, this.height / 2, 100, 20).build());
			return;
		}

		this.playerChunkX = client.player.blockPosition().getX() >> 4;
		this.playerChunkZ = client.player.blockPosition().getZ() >> 4;
		this.dimension = client.level.dimension().identifier().toString();

		this.gridOriginX = 20;
		this.gridOriginY = 40;

		int sidebarX = this.gridOriginX + (GRID_RADIUS * 2 + 1) * CELL + 20;
		int y = 40;

		List<Region> regions = new ArrayList<>(manager.all());
		Region selected = null;
		for (Region region : regions) {
			if (region.getId().equals(selectedRegionId)) {
				selected = region;
				break;
			}
		}
		if (selected == null && !regions.isEmpty()) {
			selected = regions.get(0);
			selectedRegionId = selected.getId();
		}

		for (Region region : regions) {
			boolean isSelected = region.getId().equals(selectedRegionId);
			String label = (isSelected ? "> " : "") + region.getName() + " (" + region.size() + ")";
			this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
				selectedRegionId = region.getId();
				ScreenUtil.open(new RegionEditorScreen());
			}).bounds(sidebarX, y, 150, 20).build());
			y += 22;
		}

		y += 6;
		this.addRenderableWidget(Button.builder(Component.literal("+ New region"), btn -> {
			Region created = Region.create("Region " + (regions.size() + 1), ColorPalette.COLORS[0], dimension);
			manager.add(created);
			manager.save();
			selectedRegionId = created.getId();
			ScreenUtil.open(new RegionEditorScreen());
		}).bounds(sidebarX, y, 150, 20).build());
		y += 30;

		if (selected != null) {
			Region finalSelected = selected;

			EditBox nameBox = new EditBox(this.font, sidebarX, y, 150, 20, Component.literal("Name"));
			nameBox.setValue(finalSelected.getName());
			nameBox.setResponder(text -> {
				if (!text.isBlank()) {
					finalSelected.setName(text);
					manager.save();
				}
			});
			this.addRenderableWidget(nameBox);
			y += 26;

			this.addRenderableWidget(Button.builder(
					Component.literal("Color: " + ColorPalette.nameOf(finalSelected.getColorArgb())), btn -> {
				finalSelected.setColorArgb(ColorPalette.next(finalSelected.getColorArgb()));
				manager.save();
				btn.setMessage(Component.literal("Color: " + ColorPalette.nameOf(finalSelected.getColorArgb())));
			}).bounds(sidebarX, y, 150, 20).build());
			y += 26;

			this.addRenderableWidget(Button.builder(
					Component.literal("Opacity: " + Math.round(finalSelected.getOpacity() * 100) + "%"), btn -> {
				finalSelected.setOpacity(nextOpacity(finalSelected.getOpacity()));
				manager.save();
				btn.setMessage(Component.literal("Opacity: " + Math.round(finalSelected.getOpacity() * 100) + "%"));
			}).bounds(sidebarX, y, 150, 20).build());
			y += 26;

			this.addRenderableWidget(Button.builder(Component.literal("Edit banner logo"), btn ->
					ScreenUtil.open(new BannerDesignScreen(this, finalSelected))
			).bounds(sidebarX, y, 150, 20).build());
			y += 26;

			this.addRenderableWidget(Button.builder(Component.literal("Delete region"), btn -> {
				manager.remove(finalSelected.getId());
				manager.save();
				selectedRegionId = null;
				ScreenUtil.open(new RegionEditorScreen());
			}).bounds(sidebarX, y, 150, 20).build());
			y += 30;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> {
			manager.save();
			ScreenUtil.open(null);
		}).bounds(sidebarX, this.height - 30, 150, 20).build());
	}

	private static float nextOpacity(float current) {
		float[] steps = {0.25f, 0.5f, 0.75f, 1.0f};
		for (int i = 0; i < steps.length; i++) {
			if (Math.abs(current - steps[i]) < 0.01f) {
				return steps[(i + 1) % steps.length];
			}
		}
		return steps[0];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (manager != null && selectedRegionId != null) {
			int col = (int) ((mouseX - gridOriginX) / CELL);
			int row = (int) ((mouseY - gridOriginY) / CELL);
			int gridSize = GRID_RADIUS * 2 + 1;
			if (mouseX >= gridOriginX && mouseY >= gridOriginY && col >= 0 && col < gridSize && row >= 0 && row < gridSize) {
				Region selected = manager.getById(selectedRegionId);
				if (selected != null) {
					int cx = playerChunkX - GRID_RADIUS + col;
					int cz = playerChunkZ - GRID_RADIUS + row;
					selected.toggleChunk(new ChunkPos2D(cx, cz));
					manager.save();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		int col = (int) ((mouseX - gridOriginX) / CELL);
		int row = (int) ((mouseY - gridOriginY) / CELL);
		int gridSize = GRID_RADIUS * 2 + 1;
		if (mouseX >= gridOriginX && mouseY >= gridOriginY && col >= 0 && col < gridSize && row >= 0 && row < gridSize) {
			hoverCol = col;
			hoverRow = row;
		} else {
			hoverCol = -1;
			hoverRow = -1;
		}
		super.mouseMoved(mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, 20, 15, 0xFFFFFFFF, true);

		if (manager == null) {
			graphics.text(this.font, "No world loaded.", 20, 40, 0xFFAAAAAA, false);
			return;
		}

		int gridSize = GRID_RADIUS * 2 + 1;
		List<Region> regions = new ArrayList<>(manager.all());

		for (int row = 0; row < gridSize; row++) {
			for (int col = 0; col < gridSize; col++) {
				int cx = playerChunkX - GRID_RADIUS + col;
				int cz = playerChunkZ - GRID_RADIUS + row;
				int x = gridOriginX + col * CELL;
				int y = gridOriginY + row * CELL;

				Region owner = null;
				for (Region region : regions) {
					if (region.getDimension().equals(dimension) && region.containsChunk(new ChunkPos2D(cx, cz))) {
						owner = region;
						break;
					}
				}

				int fill = owner != null ? (owner.getColorArgb() & 0x00FFFFFF) | 0x99000000 : 0x33222222;
				graphics.fill(x, y, x + CELL - 1, y + CELL - 1, fill);

				boolean isPlayerChunk = cx == playerChunkX && cz == playerChunkZ;
				boolean isHovered = col == hoverCol && row == hoverRow;
				int borderColor = isPlayerChunk ? 0xFFFF5555 : (isHovered ? 0xFFFFFFFF : 0xFF444444);
				graphics.outline(x, y, CELL - 1, CELL - 1, borderColor);
			}
		}

		if (selectedRegionId == null) {
			String msg = "Create or select a region on the right, then click chunks to assign them.";
			graphics.text(this.font, msg, gridOriginX, gridOriginY + gridSize * CELL + 10, 0xFFAAAAAA, false);
		}
	}
}
