package dev.atlasmap.client.gui;

public final class DyePalette {

	public static final String[] IDS = {
			"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
			"lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
	};

	private static final int[] SWATCH = {
			0xFFF9FFFE, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21, 0xFF835432, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
			0xFF80C71F, 0xFF5E7C16, 0xFF169C9C, 0xFF3AB3DA, 0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA
	};

	private DyePalette() {
	}

	public static int swatchOf(String id) {
		int idx = indexOf(id);
		return idx >= 0 ? SWATCH[idx] : 0xFFFFFFFF;
	}

	public static String next(String currentId) {
		int idx = indexOf(currentId);
		return IDS[(idx + 1) % IDS.length];
	}

	public static String displayName(String id) {
		String[] parts = id.split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
		}
		return sb.toString().trim();
	}

	private static int indexOf(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) return i;
		}
		return -1;
	}
}
