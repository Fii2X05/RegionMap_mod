package dev.atlasmap.client.hud;

import dev.atlasmap.client.config.MinimapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/**
 * Menggambar mob dan pemain lain di atas peta. Dipakai oleh minimap
 * (berotasi) maupun peta penuh (north-up) lewat {@link RadarView}, jadi
 * kedua peta menampilkan entity dengan rumus yang persis sama.
 * <ul>
 *   <li>Mob = titik 3x3. Warna per jenis untuk beberapa mob (lihat {@link #TYPE_COLORS}),
 *       sisanya berwarna sesuai kategori. Mob hostile diberi outline merah tua.</li>
 *   <li>Pemain lain = panah yang menunjukkan arah hadapnya.</li>
 * </ul>
 * Posisi diinterpolasi dengan partialTick supaya gerakan tidak patah-patah
 * (kode lama memotong posisi ke int per tick).
 */
public final class EntityRadarRenderer {

	private enum Kind { HOSTILE, NEUTRAL, PASSIVE }

	private static final int COLOR_HOSTILE = 0xFFFF4D4D;
	private static final int COLOR_NEUTRAL = 0xFFFFB84D;
	private static final int COLOR_PASSIVE = 0xFF7CE07C;
	private static final int COLOR_PLAYER = 0xFF55E6FF;
	private static final int COLOR_SELF = 0xFFFF5555;

	private static final int OUTLINE_DEFAULT = 0xFF000000;
	private static final int OUTLINE_HOSTILE = 0xFF6B0000;

	/** Warna khusus per jenis mob. Jenis lain memakai warna kategori. */
	private static final Map<EntityType<?>, Integer> TYPE_COLORS = Map.of(
			EntityTypes.SHEEP, 0xFFF2F2F2,
			EntityTypes.CHICKEN, 0xFFFFE066,
			EntityTypes.COW, 0xFF9C6B3F,
			EntityTypes.ZOMBIE, 0xFF3E8E41,
			EntityTypes.CREEPER, 0xFF5CFF5C,
			EntityTypes.SKELETON, 0xFFCFCFCF
	);

	private static final double MAX_QUERY_RANGE = 1024.0;
	private static final int[][] OUTLINE_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

	private EntityRadarRenderer() {
	}

	/**
	 * @param rangeBlocks radius pencarian (blok) di sekitar titik tengah {@code view}; harus
	 *                    menutupi seluruh area yang terlihat, termasuk saat peta berotasi
	 * @param halfWidth   setengah lebar area gambar (piksel GUI), untuk membuang entity di luar layar
	 * @param halfHeight  setengah tinggi area gambar (piksel GUI)
	 * @param playerNames tampilkan nama di bawah panah pemain lain
	 */
	public static void render(GuiGraphicsExtractor graphics, Minecraft client, MinimapConfig config,
	                          RadarView view, float partialTick, double rangeBlocks,
	                          double halfWidth, double halfHeight, boolean playerNames) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (!config.showHostileMobs && !config.showPassiveMobs && !config.showPlayers) {
			return;
		}

		double range = Math.min(rangeBlocks, MAX_QUERY_RANGE);
		AABB box = new AABB(
				view.originX() - range, -2048.0, view.originZ() - range,
				view.originX() + range, 2048.0, view.originZ() + range);

		if (config.showHostileMobs || config.showPassiveMobs) {
			for (Mob mob : client.level.getEntitiesOfClass(Mob.class, box)) {
				if (!mob.isAlive() || mob.isInvisible()) {
					continue;
				}
				Kind kind = classify(mob);
				boolean allowed = kind == Kind.HOSTILE ? config.showHostileMobs : config.showPassiveMobs;
				if (!allowed) {
					continue;
				}

				double wx = Mth.lerp(partialTick, mob.xo, mob.getX());
				double wz = Mth.lerp(partialTick, mob.zo, mob.getZ());
				double sx = view.screenX(wx, wz);
				double sy = view.screenY(wx, wz);
				if (outside(view, sx, sy, halfWidth, halfHeight)) {
					continue;
				}

				int outline = kind == Kind.HOSTILE ? OUTLINE_HOSTILE : OUTLINE_DEFAULT;
				drawDot(graphics, (float) sx, (float) sy, colorFor(mob, kind), outline);
			}
		}

		if (config.showPlayers) {
			for (AbstractClientPlayer other : client.level.players()) {
				if (other == client.player || other.isSpectator() || other.isInvisible()) {
					continue;
				}

				double wx = Mth.lerp(partialTick, other.xo, other.getX());
				double wz = Mth.lerp(partialTick, other.zo, other.getZ());
				double sx = view.screenX(wx, wz);
				double sy = view.screenY(wx, wz);
				if (outside(view, sx, sy, halfWidth, halfHeight)) {
					continue;
				}

				drawArrow(graphics, (float) sx, (float) sy, view.arrowAngle(other.getViewYRot(partialTick)), COLOR_PLAYER);

				if (playerNames) {
					String name = other.getName().getString();
					graphics.text(client.font, name, (int) sx - client.font.width(name) / 2, (int) sy + 6,
							0xFFFFFFFF, true);
				}
			}
		}
	}

	/** Panah untuk pemain lokal, di posisi dunia mana pun (tengah minimap, atau posisi di peta penuh). */
	public static void drawSelf(GuiGraphicsExtractor graphics, RadarView view,
	                            double worldX, double worldZ, float yawDeg) {
		drawArrow(graphics, (float) view.screenX(worldX, worldZ), (float) view.screenY(worldX, worldZ),
				view.arrowAngle(yawDeg), COLOR_SELF);
	}

	// ---- klasifikasi ----------------------------------------------------

	private static Kind classify(Mob mob) {
		// NeutralMob dicek dulu: Enderman & Zombified Piglin juga Enemy, tapi tidak agresif duluan.
		if (mob instanceof NeutralMob) {
			return Kind.NEUTRAL;
		}
		if (mob instanceof Enemy) {
			return Kind.HOSTILE;
		}
		return Kind.PASSIVE;
	}

	private static int colorFor(Mob mob, Kind kind) {
		Integer special = TYPE_COLORS.get(mob.getType());
		if (special != null) {
			return special;
		}
		return switch (kind) {
			case HOSTILE -> COLOR_HOSTILE;
			case NEUTRAL -> COLOR_NEUTRAL;
			case PASSIVE -> COLOR_PASSIVE;
		};
	}

	private static boolean outside(RadarView view, double sx, double sy, double halfWidth, double halfHeight) {
		return Math.abs(sx - view.centerX()) > halfWidth + 4 || Math.abs(sy - view.centerY()) > halfHeight + 4;
	}

	// ---- gambar ---------------------------------------------------------

	private static void drawDot(GuiGraphicsExtractor graphics, float sx, float sy, int fill, int outline) {
		var pose = graphics.pose();
		pose.pushMatrix();
		// -0.5 supaya piksel tengah (0,0) berpusat tepat di sx,sy
		pose.translate(sx - 0.5f, sy - 0.5f);
		graphics.fill(-2, -2, 3, 3, outline);
		graphics.fill(-1, -1, 2, 2, fill);
		pose.popMatrix();
	}

	private static void drawArrow(GuiGraphicsExtractor graphics, float sx, float sy, float angle, int color) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(sx, sy);
		pose.rotate(angle);
		pose.translate(-0.5f, -0.5f);

		for (int[] offset : OUTLINE_OFFSETS) {
			pose.pushMatrix();
			pose.translate(offset[0], offset[1]);
			arrowShape(graphics, 0xFF000000);
			pose.popMatrix();
		}
		arrowShape(graphics, color);

		pose.popMatrix();
	}

	/** Panah menunjuk ke atas (-y), berpusat di piksel (0,0). */
	private static void arrowShape(GuiGraphicsExtractor graphics, int color) {
		graphics.fill(0, -4, 1, -3, color);
		graphics.fill(-1, -3, 2, -2, color);
		graphics.fill(-2, -2, 3, -1, color);
		graphics.fill(-3, -1, 4, 0, color);
		graphics.fill(-1, 0, 2, 2, color);
	}
}
