package dev.atlasmap.client.hud;

/**
 * Satu-satunya sumber kebenaran untuk konversi koordinat dunia -> layar,
 * dipakai bersama oleh minimap (yang berotasi) dan peta penuh (north-up).
 * <p>
 * Dengan begitu tekstur peta, region, waypoint, mob, dan pemain SELALU
 * memakai rumus rotasi yang sama, jadi tidak mungkin saling bergeser.
 * <p>
 * Konvensi Minecraft: yaw 0 = menghadap +Z (selatan), yaw 90 = menghadap -X (barat).
 * Agar arah hadap pemain berada di atas layar, peta diputar sebesar
 * {@code rotation = toRadians(180 - yaw)} (koordinat layar: y ke bawah).
 */
public final class RadarView {

	private final double centerX;
	private final double centerY;
	private final double originX;
	private final double originZ;
	private final double pixelsPerBlock;
	private final double rotation;
	private final double cos;
	private final double sin;

	/**
	 * @param centerX        titik tengah tampilan di layar (piksel GUI)
	 * @param centerY        titik tengah tampilan di layar (piksel GUI)
	 * @param originX        koordinat dunia yang ditaruh tepat di titik tengah
	 * @param originZ        koordinat dunia yang ditaruh tepat di titik tengah
	 * @param pixelsPerBlock skala: berapa piksel GUI per 1 blok
	 * @param rotation       rotasi peta dalam RADIAN (0 = utara di atas)
	 */
	public RadarView(double centerX, double centerY, double originX, double originZ,
	                 double pixelsPerBlock, double rotation) {
		this.centerX = centerX;
		this.centerY = centerY;
		this.originX = originX;
		this.originZ = originZ;
		this.pixelsPerBlock = pixelsPerBlock;
		this.rotation = rotation;
		this.cos = Math.cos(rotation);
		this.sin = Math.sin(rotation);
	}

	public static RadarView unrotated(double centerX, double centerY, double originX, double originZ,
	                                  double pixelsPerBlock) {
		return new RadarView(centerX, centerY, originX, originZ, pixelsPerBlock, 0.0);
	}

	public double screenX(double worldX, double worldZ) {
		double dx = worldX - originX;
		double dz = worldZ - originZ;
		return centerX + (dx * cos - dz * sin) * pixelsPerBlock;
	}

	public double screenY(double worldX, double worldZ) {
		double dx = worldX - originX;
		double dz = worldZ - originZ;
		return centerY + (dx * sin + dz * cos) * pixelsPerBlock;
	}

	/**
	 * Sudut putar (radian) untuk menggambar panah yang menghadap {@code entityYawDeg}
	 * (yaw Minecraft, derajat), dengan panah "lurus" menunjuk ke atas layar.
	 */
	public float arrowAngle(float entityYawDeg) {
		return (float) (Math.toRadians(entityYawDeg) + rotation + Math.PI);
	}

	public double centerX() {
		return centerX;
	}

	public double centerY() {
		return centerY;
	}

	public double originX() {
		return originX;
	}

	public double originZ() {
		return originZ;
	}

	public double pixelsPerBlock() {
		return pixelsPerBlock;
	}

	/** Rotasi peta dalam radian. */
	public double rotation() {
		return rotation;
	}
}
