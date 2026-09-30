package dev.atlasmap.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WaypointManager {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<UUID, Waypoint> waypoints = new LinkedHashMap<>();
	private Path storageFile;

	public void open(Path configDir, String worldOrServerId) {
		Path dir = configDir.resolve("atlasmap").resolve("waypoints").resolve(sanitize(worldOrServerId));
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create AtlasMap waypoint directory", e);
		}
		this.storageFile = dir.resolve("waypoints.json");
		this.waypoints.clear();
		load();
	}

	public Collection<Waypoint> all() {
		return waypoints.values();
	}

	public Waypoint getById(UUID id) {
		return waypoints.get(id);
	}

	public void add(Waypoint waypoint) {
		waypoints.put(waypoint.getId(), waypoint);
	}

	public void remove(UUID id) {
		waypoints.remove(id);
	}

	public void load() {
		if (storageFile == null || !Files.exists(storageFile)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonArray()) {
				return;
			}
			for (JsonElement el : root.getAsJsonArray()) {
				JsonObject obj = el.getAsJsonObject();
				UUID id = UUID.fromString(obj.get("id").getAsString());
				String name = obj.get("name").getAsString();
				int x = obj.get("x").getAsInt();
				int y = obj.get("y").getAsInt();
				int z = obj.get("z").getAsInt();
				String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
				int color = obj.get("color").getAsInt();
				Waypoint wp = new Waypoint(id, name, x, y, z, dim, color);
				if (obj.has("visible")) {
					wp.setVisible(obj.get("visible").getAsBoolean());
				}
				waypoints.put(id, wp);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to read AtlasMap waypoints.json", e);
		}
	}

	public void save() {
		if (storageFile == null) {
			return;
		}
		JsonArray root = new JsonArray();
		for (Waypoint wp : waypoints.values()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("id", wp.getId().toString());
			obj.addProperty("name", wp.getName());
			obj.addProperty("x", wp.getX());
			obj.addProperty("y", wp.getY());
			obj.addProperty("z", wp.getZ());
			obj.addProperty("dimension", wp.getDimension());
			obj.addProperty("color", wp.getColorArgb());
			obj.addProperty("visible", wp.isVisible());
			root.add(obj);
		}
		try (Writer writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write AtlasMap waypoints.json", e);
		}
	}

	private static String sanitize(String id) {
		return id.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
