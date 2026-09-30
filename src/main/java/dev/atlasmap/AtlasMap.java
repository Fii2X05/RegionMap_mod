package dev.atlasmap;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtlasMap implements ModInitializer {

	public static final String MOD_ID = "atlasmap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[AtlasMap] Common init");
	}
}
