package dev.atlasmap.client.hud;

import dev.atlasmap.client.waypoint.ClientWaypointState;
import dev.atlasmap.waypoint.Waypoint;
import dev.atlasmap.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class WaypointOverlayRenderer {

	private WaypointOverlayRenderer() {
	}

	/**
	 * Posisi dihitung lewat {@link RadarView} (ikut rotasi peta), tetapi ikon dan nama
	 * digambar tanpa rotasi supaya teksnya tetap terbaca.
	 *
	 * @param halfSize setengah sisi kotak minimap dalam piksel GUI
	 */
	static void render(GuiGraphicsExtractor graphics, RadarView view, double halfSize) {
		WaypointManager manager = ClientWaypointState.manager();
		if (manager == null || manager.all().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		String dimension = client.level.dimension().identifier().toString();

		for (Waypoint wp : manager.all()) {
			if (!wp.isVisible() || !wp.getDimension().equals(dimension)) {
				continue;
			}

			double wx = wp.getX() + 0.5;
			double wz = wp.getZ() + 0.5;
			double sxD = view.screenX(wx, wz);
			double syD = view.screenY(wx, wz);
			if (Math.abs(sxD - view.centerX()) > halfSize || Math.abs(syD - view.centerY()) > halfSize) {
				continue;
			}

			int screenX = (int) Math.round(sxD);
			int screenZ = (int) Math.round(syD);

			int dotRadius = 2;
			graphics.outline(screenX - dotRadius - 1, screenZ - dotRadius - 1, dotRadius * 2 + 3, dotRadius * 2 + 3, 0xFF000000);
			graphics.fill(screenX - dotRadius, screenZ - dotRadius, screenX + dotRadius, screenZ + dotRadius,
					wp.getColorArgb() | 0xFF000000);

			graphics.text(client.font, wp.getName(), screenX + 4, screenZ - 4, 0xFFFFFFFF, true);
		}
	}
}
