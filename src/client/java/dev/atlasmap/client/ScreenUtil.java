package dev.atlasmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenUtil {

	private ScreenUtil() {
	}

	public static void open(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
