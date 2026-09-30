package dev.atlasmap.client.gui;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;

public final class BannerPatternCatalog {

	public static final String[] IDS = {
			"base", "stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center",
			"stripe_middle", "cross", "straight_cross", "triangle_bottom", "triangle_top",
			"triangles_bottom", "triangles_top", "diagonal_left", "diagonal_right",
			"circle", "rhombus", "border", "curly_border", "gradient", "gradient_up",
			"half_horizontal", "half_vertical", "square_bottom_left", "square_bottom_right",
			"square_top_left", "square_top_right"
	};

	private BannerPatternCatalog() {
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

	public static ResourceKey<BannerPattern> resourceKey(String id) {
		return switch (id) {
			case "stripe_bottom" -> BannerPatterns.STRIPE_BOTTOM;
			case "stripe_top" -> BannerPatterns.STRIPE_TOP;
			case "stripe_left" -> BannerPatterns.STRIPE_LEFT;
			case "stripe_right" -> BannerPatterns.STRIPE_RIGHT;
			case "stripe_center" -> BannerPatterns.STRIPE_CENTER;
			case "stripe_middle" -> BannerPatterns.STRIPE_MIDDLE;
			case "cross" -> BannerPatterns.CROSS;
			case "straight_cross" -> BannerPatterns.STRAIGHT_CROSS;
			case "triangle_bottom" -> BannerPatterns.TRIANGLE_BOTTOM;
			case "triangle_top" -> BannerPatterns.TRIANGLE_TOP;
			case "triangles_bottom" -> BannerPatterns.TRIANGLES_BOTTOM;
			case "triangles_top" -> BannerPatterns.TRIANGLES_TOP;
			case "diagonal_left" -> BannerPatterns.DIAGONAL_LEFT;
			case "diagonal_right" -> BannerPatterns.DIAGONAL_RIGHT;
			case "circle" -> BannerPatterns.CIRCLE_MIDDLE;
			case "rhombus" -> BannerPatterns.RHOMBUS_MIDDLE;
			case "border" -> BannerPatterns.BORDER;
			case "curly_border" -> BannerPatterns.CURLY_BORDER;
			case "gradient" -> BannerPatterns.GRADIENT;
			case "gradient_up" -> BannerPatterns.GRADIENT_UP;
			case "half_horizontal" -> BannerPatterns.HALF_HORIZONTAL;
			case "half_vertical" -> BannerPatterns.HALF_VERTICAL;
			case "square_bottom_left" -> BannerPatterns.SQUARE_BOTTOM_LEFT;
			case "square_bottom_right" -> BannerPatterns.SQUARE_BOTTOM_RIGHT;
			case "square_top_left" -> BannerPatterns.SQUARE_TOP_LEFT;
			case "square_top_right" -> BannerPatterns.SQUARE_TOP_RIGHT;
			default -> BannerPatterns.BASE;
		};
	}

	private static int indexOf(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) return i;
		}
		return -1;
	}
}
