package dev.atlasmap.client.keybind;

import dev.atlasmap.AtlasMap;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ModKeybinds {

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(AtlasMap.MOD_ID, "general"));

	public static final KeyMapping TOGGLE_MINIMAP = register("toggle_minimap", GLFW.GLFW_KEY_M);
	public static final KeyMapping ZOOM_IN = register("zoom_in", GLFW.GLFW_KEY_EQUAL);
	public static final KeyMapping ZOOM_OUT = register("zoom_out", GLFW.GLFW_KEY_MINUS);
	public static final KeyMapping OPEN_WORLDMAP = register("open_worldmap", GLFW.GLFW_KEY_APOSTROPHE);
	public static final KeyMapping OPEN_WAYPOINTS = register("open_waypoints", GLFW.GLFW_KEY_B);
	public static final KeyMapping OPEN_REGIONS = register("open_regions", GLFW.GLFW_KEY_N);
	public static final KeyMapping CYCLE_POSITION = register("cycle_position", GLFW.GLFW_KEY_LEFT_BRACKET);

	private ModKeybinds() {
	}

	private static KeyMapping register(String id, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + AtlasMap.MOD_ID + "." + id,
				InputConstants.Type.KEYSYM,
				defaultKey,
				CATEGORY
		));
	}

	public static void init() {
	}
}
