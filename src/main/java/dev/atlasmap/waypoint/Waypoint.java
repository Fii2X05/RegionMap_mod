package dev.atlasmap.waypoint;

import java.util.UUID;

public final class Waypoint {

	private final UUID id;
	private String name;
	private int x;
	private int y;
	private int z;
	private String dimension;
	private int colorArgb;
	private boolean visible = true;

	public Waypoint(UUID id, String name, int x, int y, int z, String dimension, int colorArgb) {
		this.id = id;
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.colorArgb = colorArgb;
	}

	public static Waypoint create(String name, int x, int y, int z, String dimension, int colorArgb) {
		return new Waypoint(UUID.randomUUID(), name, x, y, z, dimension, colorArgb);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getZ() {
		return z;
	}

	public void setPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public String getDimension() {
		return dimension;
	}

	public void setDimension(String dimension) {
		this.dimension = dimension;
	}

	public int getColorArgb() {
		return colorArgb;
	}

	public void setColorArgb(int colorArgb) {
		this.colorArgb = colorArgb;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public double horizontalDistance(double fromX, double fromZ) {
		double dx = x + 0.5 - fromX;
		double dz = z + 0.5 - fromZ;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
