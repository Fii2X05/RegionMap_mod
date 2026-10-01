package dev.atlasmap.client.hud;

import dev.atlasmap.client.config.MinimapConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Minimap HUD.
 * <p>
 * Perbaikan rotasi (dibanding versi sebelumnya):
 * <ul>
 *   <li><b>Satuan sudut.</b> {@code pose().rotate(...)} milik JOML menerima RADIAN. Kode lama
 *       mengirim derajat ({@code 180 - yawDeg}), sehingga 1 derajat putaran kepala menjadi
 *       1 radian (~57 derajat) di peta. Itu penyebab rotasi liar dan tidak halus.</li>
 *   <li><b>Titik putar.</b> Peta sekarang diputar tepat di posisi pemain (termasuk pecahan
 *       koordinatnya), bukan di pusat blok. Tidak ada lagi lompatan sebesar 1 blok saat pemain
 *       melewati batas blok.</li>
 *   <li><b>Arah kamera.</b> Yaw diambil dengan cara yang sama seperti kamera vanilla
 *       ({@code getViewYRot(partialTick)}, ditambah 180 di tampilan third-person depan),
 *       jadi peta selalu sinkron dengan apa yang terlihat di layar.</li>
 *   <li><b>Overlay ikut berputar.</b> Region, waypoint, mob, dan pemain semuanya memakai
 *       {@link RadarView} yang sama dengan tekstur peta.</li>
 * </ul>
 */
public final class MinimapHudElement {

	private final MinimapRenderer renderer = new MinimapRenderer();

	public void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter, MinimapConfig config) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (!config.enabled || player == null || client.level == null) {
			return;
		}

		float partialTick = tickCounter.getGameTimeDeltaPartialTick(true);

		// Posisi & arah pemain yang diinterpolasi per-frame (bukan per-tick) agar gerak halus.
		double px = Mth.lerp(partialTick, player.xo, player.getX());
		double pz = Mth.lerp(partialTick, player.zo, player.getZ());
		float cameraYaw = player.getViewYRot(partialTick);
		if (client.options.getCameraType().isMirrored()) {
			cameraYaw += 180.0f; // tampilan third-person depan: kamera menghadap balik
		}

		renderer.tick(config, px, pz);
		if (!renderer.isReady()) {
			return;
		}

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

		double sampleSpan = config.effectiveViewRadius() * 2.0;
		double pixelsPerBlock = size / sampleSpan;

		int centerX = x + size / 2;
		int centerY = y + size / 2;

		// Rotasi dalam RADIAN. Arah hadap pemain ke atas layar => putar 180 - yaw.
		double rotation = config.rotateWithPlayer ? Math.toRadians(180.0 - cameraYaw) : 0.0;
		RadarView view = new RadarView(centerX, centerY, px, pz, pixelsPerBlock, rotation);

		// Scissor HARUS dipasang sebelum matriks diputar (scissor ikut ditransformasi pose).
		graphics.enableScissor(x, y, x + size, y + size);

		drawMapTexture(graphics, view, px, pz);

		if (config.showRegionOverlay) {
			RegionOverlayRenderer.render(graphics, view, size / 2.0);
		}
		if (config.showWaypoints) {
			WaypointOverlayRenderer.render(graphics, view, size / 2.0);
		}

		// Sudut kotak yang berotasi terlihat sampai radius*sqrt(2) dari pusat.
		double halfSpanBlocks = sampleSpan / 2.0;
		EntityRadarRenderer.render(graphics, client, config, view, partialTick,
				halfSpanBlocks * 1.5, size / 2.0, size / 2.0, config.showPlayerNames);

		// Panah pemain sendiri selalu di tengah. Saat peta berotasi, ia menunjuk ke atas
		// (kecuali di third-person depan, di mana kamera menghadap balik).
		EntityRadarRenderer.drawSelf(graphics, view, px, pz, player.getViewYRot(partialTick));

		drawNorthMarker(graphics, client, view, size);

		graphics.disableScissor();

		graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFF2B2B2B);
	}

	/**
	 * Menggambar seluruh tekstur kerja dengan pusat putar tepat di posisi pemain.
	 * Titik (u,v) di tekstur yang ditempati pemain dipindah ke origin matriks, baru diputar.
	 */
	private void drawMapTexture(GuiGraphicsExtractor graphics, RadarView view, double px, double pz) {
		int tex = renderer.workingSize();
		float half = tex / 2.0f;
		// Piksel (i,j) tekstur menutupi blok (centerBlock + i - half); posisi kontinu pemain dalam tekstur:
		float playerU = (float) (px - renderer.centerBlockX()) + half;
		float playerV = (float) (pz - renderer.centerBlockZ()) + half;

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) view.centerX(), (float) view.centerY());
		pose.rotate((float) view.rotation());
		pose.scale((float) view.pixelsPerBlock(), (float) view.pixelsPerBlock());
		pose.translate(-playerU, -playerV);

		graphics.blit(RenderPipelines.GUI_TEXTURED, renderer.textureId(),
				0, 0, 0.0f, 0.0f, tex, tex, tex, tex);

		pose.popMatrix();
	}

	/** Huruf "N" yang mengelilingi tepi minimap dan selalu menunjuk ke utara sejati. */
	private void drawNorthMarker(GuiGraphicsExtractor graphics, Minecraft client, RadarView view, int size) {
		// Vektor utara (0,-1) setelah diputar sebesar rotation.
		double nx = Math.sin(view.rotation());
		double ny = -Math.cos(view.rotation());
		double reach = size / 2.0 - 7.0;
		double scale = reach / Math.max(Math.abs(nx), Math.abs(ny));

		int tx = (int) Math.round(view.centerX() + nx * scale);
		int ty = (int) Math.round(view.centerY() + ny * scale);
		graphics.text(client.font, "N", tx - 2, ty - 4, 0xFFFFFFFF, true);
	}

	public void close() {
		renderer.close();
	}
}
