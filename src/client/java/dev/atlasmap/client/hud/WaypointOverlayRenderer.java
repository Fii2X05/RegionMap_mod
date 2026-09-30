package dev.atlasmap.client.hud;

import dev.atlasmap.client.waypoint.ClientWaypointState;
import dev.atlasmap.waypoint.Waypoint;
import dev.atlasmap.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class WaypointOverlayRenderer {

	private WaypointOverlayRenderer() {
	}

	static void render(GuiGraphicsExtractor graphics, int size, int centerX, int centerY, double uvScale) {
		WaypointManager manager = ClientWaypointState.manager();
		if (manager == null || manager.all().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		String dimension = client.level.dimension().identifier().toString();
		double playerX = client.player.getX();
		double playerZ = client.player.getZ();
		double halfSpan = (size * uvScale) / 2.0;

		for (Waypoint wp : manager.all()) {
			if (!wp.isVisible() || !wp.getDimension().equals(dimension)) {
				continue;
			}
			double dx = wp.getX() + 0.5 - playerX;
			double dz = wp.getZ() + 0.5 - playerZ;
			if (Math.abs(dx) > halfSpan || Math.abs(dz) > halfSpan) {
				continue;
			}

			int screenX = centerX + (int) (dx / uvScale);
			int screenZ = centerY + (int) (dz / uvScale);

			int dotRadius = 2;
			graphics.outline(screenX - dotRadius - 1, screenZ - dotRadius - 1, dotRadius * 2 + 3, dotRadius * 2 + 3, 0xFF000000);
			graphics.fill(screenX - dotRadius, screenZ - dotRadius, screenX + dotRadius, screenZ + dotRadius,
					wp.getColorArgb() | 0xFF000000);

			graphics.text(client.font, wp.getName(), screenX + 4, screenZ - 4, 0xFFFFFFFF, true);
		}
	}
}
