package dev.atlasmap.client;

import dev.atlasmap.AtlasMap;
import dev.atlasmap.client.config.MinimapConfig;
import dev.atlasmap.client.gui.RegionEditorScreen;
import dev.atlasmap.client.gui.WaypointListScreen;
import dev.atlasmap.client.gui.WorldMapScreen;
import dev.atlasmap.client.hud.MinimapHudElement;
import dev.atlasmap.client.keybind.ModKeybinds;
import dev.atlasmap.client.map.MapManager;
import dev.atlasmap.client.region.ClientRegionState;
import dev.atlasmap.client.waypoint.ClientWaypointState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class AtlasMapClient implements ClientModInitializer {

	private static MinimapConfig config;
	private static MinimapHudElement minimapElement;

	@Override
	public void onInitializeClient() {
		config = MinimapConfig.loadOrCreate(FabricLoader.getInstance().getConfigDir().resolve(AtlasMap.MOD_ID));
		minimapElement = new MinimapHudElement();

		ModKeybinds.init();

		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(AtlasMap.MOD_ID, "minimap"),
				(graphics, tickCounter) -> minimapElement.render(graphics, tickCounter, config)
		);

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientRegionState.openFor(currentWorldOrServerId());
			ClientWaypointState.openFor(currentWorldOrServerId());
			MapManager.open(currentWorldOrServerId());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientRegionState.close();
			ClientWaypointState.close();
			MapManager.close();
		});

		AtlasMap.LOGGER.info("[AtlasMap] Client init complete (minimap enabled: {})", config.enabled);
	}

	private void onClientTick(Minecraft client) {
		MapManager.tick(client);

		if (ModKeybinds.TOGGLE_MINIMAP.consumeClick()) {
			config.enabled = !config.enabled;
			config.save(FabricLoader.getInstance().getConfigDir().resolve(AtlasMap.MOD_ID));
		}
		while (ModKeybinds.ZOOM_IN.consumeClick()) {
			config.zoomLevel = Math.max(config.zoomLevel - 1, -4);
		}
		while (ModKeybinds.ZOOM_OUT.consumeClick()) {
			config.zoomLevel = Math.min(config.zoomLevel + 1, 8);
		}
		if (ModKeybinds.OPEN_WORLDMAP.consumeClick()) {
			ScreenUtil.open(new WorldMapScreen());
		}
		if (ModKeybinds.OPEN_WAYPOINTS.consumeClick()) {
			ScreenUtil.open(new WaypointListScreen());
		}
		if (ModKeybinds.OPEN_REGIONS.consumeClick()) {
			ScreenUtil.open(new RegionEditorScreen());
		}
		while (ModKeybinds.CYCLE_POSITION.consumeClick()) {
			if (config.corner == MinimapConfig.Corner.TOP_RIGHT) {
				config.corner = MinimapConfig.Corner.TOP_LEFT;
			} else {
				config.corner = MinimapConfig.Corner.TOP_RIGHT;
			}
		}
	}

	private static String currentWorldOrServerId() {
		Minecraft client = Minecraft.getInstance();
		if (client.hasSingleplayerServer()) {
			return "singleplayer";
		}
		if (client.getCurrentServer() != null) {
			return "server_" + client.getCurrentServer().ip;
		}
		return "unknown_world";
	}

	public static MinimapConfig config() {
		return config;
	}
}
