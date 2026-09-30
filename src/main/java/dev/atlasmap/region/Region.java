package dev.atlasmap.region;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Region {

	private final UUID id;
	private String name;
	private int colorArgb;
	private float opacity = 0.6f;
	private String owner;
	private final String dimension;
	private final Set<ChunkPos2D> chunks = new HashSet<>();
	private BannerDesign bannerDesign;
	/** Bumped whenever the chunk set changes; lets renderers cache things like the label position. */
	private int revision;

	public Region(UUID id, String name, int colorArgb, String dimension) {
		this.id = id;
		this.name = name;
		this.colorArgb = colorArgb;
		this.dimension = dimension;
	}

	public static Region create(String name, int colorArgb, String dimension) {
		return new Region(UUID.randomUUID(), name, colorArgb, dimension);
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

	public int getColorArgb() {
		return colorArgb;
	}

	public void setColorArgb(int colorArgb) {
		this.colorArgb = colorArgb;
	}

	public float getOpacity() {
		return opacity;
	}

	public void setOpacity(float opacity) {
		this.opacity = Math.max(0f, Math.min(1f, opacity));
	}

	public BannerDesign getBannerDesign() {
		return bannerDesign;
	}

	public void setBannerDesign(BannerDesign bannerDesign) {
		this.bannerDesign = bannerDesign;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getDimension() {
		return dimension;
	}

	public Set<ChunkPos2D> getChunks() {
		return chunks;
	}

	public boolean containsChunk(ChunkPos2D pos) {
		return chunks.contains(pos);
	}

	public void addChunk(ChunkPos2D pos) {
		if (chunks.add(pos)) {
			revision++;
		}
	}

	public void removeChunk(ChunkPos2D pos) {
		if (chunks.remove(pos)) {
			revision++;
		}
	}

	public void toggleChunk(ChunkPos2D pos) {
		if (chunks.remove(pos)) {
			revision++;
		} else {
			chunks.add(pos);
			revision++;
		}
	}

	public int getRevision() {
		return revision;
	}

	public int size() {
		return chunks.size();
	}
}
