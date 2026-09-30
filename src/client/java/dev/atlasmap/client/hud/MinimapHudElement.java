package dev.atlasmap.client.hud;

import dev.atlasmap.client.config.MinimapConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;

public final class MinimapHudElement {

	private final MinimapRenderer renderer = new MinimapRenderer();

	public void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter, MinimapConfig config) {
		Minecraft client = Minecraft.getInstance();
		if (!config.enabled || client.player == null || client.level == null) {
			return;
		}

		renderer.tick(config);

		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		int size = config.sizePx;

		int x = switch (config.corner) {
			case TOP_LEFT, BOTTOM_LEFT -> config.marginX;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenW - size - config.marginX;
		};
		int y = switch (config.corner) {
			case TOP_LEFT, TOP_RIGHT -> config.marginY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - size - config.marginY;
		};

		double viewRadius = config.effectiveViewRadius();
		double sampleSpan = viewRadius * 2.0;
		double uvScale = sampleSpan / renderer.workingSize();

		float partialTick = tickCounter.getGameTimeDeltaPartialTick(true);
		float yawDeg = config.rotateWithPlayer
				? Mth.rotLerp(partialTick, client.player.yRotO, client.player.getYRot())
				: 0f;

		int centerX = x + size / 2;
		int centerY = y + size / 2;

		// 1. AKTIFKAN SCISSOR
		graphics.enableScissor(x, y, x + size, y + size);

		// 2. GAMBAR TEKSTUR PETA
		graphics.pose().pushMatrix();

		// Geser titik nol (origin) matriks tepat ke tengah-tengah kotak UI
		graphics.pose().translate(centerX, centerY);

		// Terapkan rotasi
		if (config.rotateWithPlayer) {
			graphics.pose().rotate(180 - yawDeg);
		}


		// Skalakan ukuran peta agar jumlah blok yang dirender (sampleSpan)
		// ditarik memenuhi besaran kotak UI (size) di layar Anda.
		float scale = (float) (size / sampleSpan);
		graphics.pose().scale(scale, scale);

		// Karena peta bisa berotasi menyamping, ujung kotaknya akan terlihat kosong
		// jika kita hanya merender pas seukuran 'size'. Kita harus merender sedikit
		// lebih lebar dari kotak (dikali 1.5) untuk menutupi diagonal rotasinya.
		int drawTexSize = (int) (sampleSpan * 1.5);
		drawTexSize = Math.min(drawTexSize, renderer.workingSize()); // Maksimal memori 256

		int half = renderer.workingSize() / 2;
		int u = half - (drawTexSize / 2);
		int v = half - (drawTexSize / 2);

		// Karena origin matriks sekarang di (0,0) (tengah layar UI),
		// posisikan pojok kiri atas gambar di posisi minus setengah dari ukurannya
		int drawX = -drawTexSize / 2;
		int drawY = -drawTexSize / 2;

		graphics.blit(RenderPipelines.GUI_TEXTURED, renderer.textureId(),
				drawX, drawY, u, v, drawTexSize, drawTexSize,
				renderer.workingSize(), renderer.workingSize());

		graphics.pose().popMatrix();

		// --- Panggil Entity Radar di sini ---
		EntityRadarRenderer.render(graphics, client, size, centerX, centerY, sampleSpan, yawDeg, config.rotateWithPlayer);

		// 3. OVERLAYS (Region & Waypoints)
		// Overlay tetap berada di dalam blok Scissor tapi menggunakan matriks normal
		if (config.showRegionOverlay) {
			RegionOverlayRenderer.render(graphics, x, y, size, centerX, centerY, uvScale);
		}
		if (config.showWaypoints) {
			WaypointOverlayRenderer.render(graphics, size, centerX, centerY, uvScale);
		}

		// 4. MATIKAN SCISSOR & GAMBAR BORDER LUAR
		graphics.disableScissor();

		graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFF2B2B2B);

		int centerXi = x + size / 2;
		graphics.fill(centerXi - 1, y, centerXi + 1, y + 3, 0xFFFF5555);
	}

	public void close() {
		renderer.close();
	}
}