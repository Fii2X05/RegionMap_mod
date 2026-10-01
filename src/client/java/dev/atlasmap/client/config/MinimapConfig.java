package dev.atlasmap.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MinimapConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Shape { SQUARE, CIRCLE }
	public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	public boolean enabled = true;
	public Shape shape = Shape.SQUARE;
	public Corner corner = Corner.TOP_RIGHT;
	public int sizePx = 128;
	public int baseViewRadius = 64;
	public int zoomLevel = 0;
	public boolean rotateWithPlayer = true;
	public boolean reliefShading = true;
	public float opacity = 1.0f;
	public boolean showRegionOverlay = true;
	public boolean showWaypoints = true;

	// Radar entity (mob & pemain lain). Config lama tanpa kolom ini otomatis memakai nilai default.
	public boolean showHostileMobs = true;
	/** Mencakup mob pasif, netral (serigala, enderman, ...), dan hewan air. */
	public boolean showPassiveMobs = true;
	public boolean showPlayers = true;
	public boolean showPlayerNames = false;
	public int refreshIntervalMs = 250;
	public int marginX = 6;
	public int marginY = 6;

	public static MinimapConfig loadOrCreate(Path configDir) {
		Path file = configDir.resolve("atlasmap-minimap.json");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				MinimapConfig loaded = GSON.fromJson(reader, MinimapConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException ignored) {
			}
		}
		MinimapConfig fresh = new MinimapConfig();
		fresh.save(configDir);
		return fresh;
	}

	public void save(Path configDir) {
		Path file = configDir.resolve("atlasmap-minimap.json");
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save AtlasMap minimap config", e);
		}
	}

	public double effectiveViewRadius() {
		return baseViewRadius * Math.pow(0.8, zoomLevel);
	}
}
