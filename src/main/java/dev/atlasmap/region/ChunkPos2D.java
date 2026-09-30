package dev.atlasmap.region;

public record ChunkPos2D(int x, int z) {

	public static ChunkPos2D fromBlock(int blockX, int blockZ) {
		return new ChunkPos2D(blockX >> 4, blockZ >> 4);
	}

	public long asLong() {
		return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
	}

	public static ChunkPos2D fromLong(long packed) {
		return new ChunkPos2D((int) (packed & 0xFFFFFFFFL), (int) (packed >>> 32));
	}

	public int minBlockX() {
		return x << 4;
	}

	public int minBlockZ() {
		return z << 4;
	}

	@Override
	public String toString() {
		return x + "," + z;
	}

	public static ChunkPos2D parse(String s) {
		String[] parts = s.split(",");
		return new ChunkPos2D(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
	}
}
