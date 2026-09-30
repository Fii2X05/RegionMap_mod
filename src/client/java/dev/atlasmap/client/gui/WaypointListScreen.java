package dev.atlasmap.client.gui;

import dev.atlasmap.client.ScreenUtil;
import dev.atlasmap.client.waypoint.ClientWaypointState;
import dev.atlasmap.waypoint.Waypoint;
import dev.atlasmap.waypoint.WaypointManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WaypointListScreen extends Screen {

	private static final int ROW_HEIGHT = 22;

	public WaypointListScreen() {
		super(Component.literal("AtlasMap Waypoints"));
	}

	@Override
	protected void init() {
		WaypointManager manager = ClientWaypointState.manager();
		List<Waypoint> waypoints = manager == null ? List.of() : new ArrayList<>(manager.all());

		int top = 40;
		int rowWidth = Math.min(360, this.width - 40);
		int left = (this.width - rowWidth) / 2;

		for (int i = 0; i < waypoints.size(); i++) {
			Waypoint wp = waypoints.get(i);
			int y = top + i * ROW_HEIGHT;

			this.addRenderableWidget(Button.builder(Component.literal(wp.getName()), btn ->
					ScreenUtil.open(new EditWaypointScreen(this, wp))
			).bounds(left, y, rowWidth - 110, 20).build());

			this.addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> {
				manager.remove(wp.getId());
				manager.save();
				ScreenUtil.open(new WaypointListScreen());
			}).bounds(left + rowWidth - 105, y, 50, 20).build());

			this.addRenderableWidget(Button.builder(Component.literal(wp.isVisible() ? "On" : "Off"), btn -> {
				wp.setVisible(!wp.isVisible());
				manager.save();
				ScreenUtil.open(new WaypointListScreen());
			}).bounds(left + rowWidth - 50, y, 50, 20).build());
		}

		int addY = top + waypoints.size() * ROW_HEIGHT + 10;
		this.addRenderableWidget(Button.builder(Component.literal("+ Add waypoint here"), btn ->
				ScreenUtil.open(new EditWaypointScreen(this, null))
		).bounds(left, addY, rowWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Close"), btn ->
				ScreenUtil.open(null)
		).bounds(left, this.height - 30, rowWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 15, 0xFFFFFFFF, true);

		WaypointManager manager = ClientWaypointState.manager();
		if (manager == null || manager.all().isEmpty()) {
			String msg = "No waypoints yet - add one below.";
			graphics.text(this.font, msg, (this.width - this.font.width(msg)) / 2, 40, 0xFFAAAAAA, false);
		}
	}
}
