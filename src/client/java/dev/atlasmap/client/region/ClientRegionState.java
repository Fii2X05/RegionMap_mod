package dev.atlasmap.client.region;

import dev.atlasmap.region.RegionManager;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientRegionState {

	private static RegionManager activeManager;

	private ClientRegionState() {
	}

	public static RegionManager manager() {
		return activeManager;
	}

	public static void openFor(String worldOrServerId) {
		RegionManager manager = new RegionManager();
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
