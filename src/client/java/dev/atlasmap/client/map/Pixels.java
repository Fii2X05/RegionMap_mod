package dev.atlasmap.client.map;

public final class Pixels {

	private Pixels() {
	}

	/**
	 * Converts 0xAARRGGBB into the 0xAABBGGRR layout that
	 * {@code NativeImage#setPixelABGR} expects. Passing plain ARGB swaps the
	 * red and blue channels (ice/water turned pink/red in the first build).
	 */
	public static int toAbgr(int argb) {
		return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
	}
}
