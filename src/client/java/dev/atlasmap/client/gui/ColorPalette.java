package dev.atlasmap.client.gui;

public final class ColorPalette {

	public static final int[] COLORS = {
			0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1,
			0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60, 0xFFFFFFFF, 0xFF616161
	};

	public static final String[] NAMES = {
			"Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White", "Grey"
	};

	private ColorPalette() {
	}

	public static int next(int currentArgb) {
		int idx = indexOf(currentArgb);
		return COLORS[(idx + 1) % COLORS.length];
	}

	public static String nameOf(int argb) {
		int idx = indexOf(argb);
		return idx >= 0 ? NAMES[idx] : "Custom";
	}

	private static int indexOf(int argb) {
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i] == argb) return i;
		}
		return -1;
	}
}
