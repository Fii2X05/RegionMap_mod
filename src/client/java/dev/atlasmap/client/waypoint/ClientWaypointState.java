package dev.atlasmap.client.waypoint;

import dev.atlasmap.waypoint.WaypointManager;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientWaypointState {

	private static WaypointManager activeManager;

	private ClientWaypointState() {
	}

	public static WaypointManager manager() {
		return activeManager;
	}

	public static void openFor(String worldOrServerId) {
		WaypointManager manager = new WaypointManager();
		manager.open(FabricLoader.getInstance().getConfigDir(), worldOrServerId);
		activeManager = manager;
	}

	public static void close() {
		if (activeManager != null) {
			activeManager.save();
		}
		activeManager = null;
	}
}
