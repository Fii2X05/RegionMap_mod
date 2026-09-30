package dev.atlasmap.region;

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

public final class RegionManager {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<UUID, Region> regions = new LinkedHashMap<>();
	private Path storageFile;

	public void open(Path configDir, String worldOrServerId) {
		Path dir = configDir.resolve("atlasmap").resolve("regions").resolve(sanitize(worldOrServerId));
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create AtlasMap region directory", e);
		}
		this.storageFile = dir.resolve("regions.json");
		this.regions.clear();
		load();
	}

	public Collection<Region> all() {
		return regions.values();
	}

	public Region getById(UUID id) {
		return regions.get(id);
	}

	public void add(Region region) {
		regions.put(region.getId(), region);
	}

	public void remove(UUID id) {
		regions.remove(id);
	}

	/**
	 * Adds/removes one chunk for a region. A chunk can belong to only one
	 * region per dimension (administrative borders don't overlap), so adding
	 * it to {@code target} takes it away from any other region first.
	 */
	public void paint(Region target, ChunkPos2D pos, boolean add) {
		if (add) {
			for (Region other : regions.values()) {
				if (other != target && other.getDimension().equals(target.getDimension())) {
					other.removeChunk(pos);
				}
			}
			target.addChunk(pos);
		} else {
			target.removeChunk(pos);
		}
	}

	public Region findByChunk(ChunkPos2D pos, String dimension) {
		for (Region r : regions.values()) {
			if (r.getDimension().equals(dimension) && r.containsChunk(pos)) {
				return r;
			}
		}
		return null;
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
				int color = obj.get("color").getAsInt();
				String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
				Region region = new Region(id, name, color, dim);
				if (obj.has("owner") && !obj.get("owner").isJsonNull()) {
					region.setOwner(obj.get("owner").getAsString());
				}
				if (obj.has("opacity")) {
					region.setOpacity(obj.get("opacity").getAsFloat());
				}
				if (obj.has("banner") && obj.get("banner").isJsonObject()) {
					JsonObject bannerObj = obj.getAsJsonObject("banner");
					BannerDesign banner = new BannerDesign(bannerObj.get("baseColor").getAsString());
					if (bannerObj.has("layers")) {
						for (JsonElement layerEl : bannerObj.getAsJsonArray("layers")) {
							JsonObject layerObj = layerEl.getAsJsonObject();
							banner.addLayer(layerObj.get("pattern").getAsString(), layerObj.get("color").getAsString());
						}
					}
					region.setBannerDesign(banner);
				}
				JsonArray chunkArray = obj.getAsJsonArray("chunks");
				for (JsonElement chunkEl : chunkArray) {
					String[] xz = chunkEl.getAsString().split(",");
					region.addChunk(new ChunkPos2D(Integer.parseInt(xz[0]), Integer.parseInt(xz[1])));
				}
				regions.put(id, region);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to read AtlasMap regions.json", e);
		}
	}

	public void save() {
		if (storageFile == null) {
			return;
		}
		JsonArray root = new JsonArray();
		for (Region region : regions.values()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("id", region.getId().toString());
			obj.addProperty("name", region.getName());
			obj.addProperty("color", region.getColorArgb());
			obj.addProperty("owner", region.getOwner());
			obj.addProperty("dimension", region.getDimension());
			obj.addProperty("opacity", region.getOpacity());
			if (region.getBannerDesign() != null) {
				JsonObject bannerObj = new JsonObject();
				bannerObj.addProperty("baseColor", region.getBannerDesign().getBaseColorId());
				JsonArray layersArray = new JsonArray();
				for (BannerDesign.Layer layer : region.getBannerDesign().getLayers()) {
					JsonObject layerObj = new JsonObject();
					layerObj.addProperty("pattern", layer.patternId());
					layerObj.addProperty("color", layer.colorId());
					layersArray.add(layerObj);
				}
				bannerObj.add("layers", layersArray);
				obj.add("banner", bannerObj);
			}
			JsonArray chunkArray = new JsonArray();
			for (ChunkPos2D pos : region.getChunks()) {
				chunkArray.add(pos.toString());
			}
			obj.add("chunks", chunkArray);
			root.add(obj);
		}
		try (Writer writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write AtlasMap regions.json", e);
		}
	}

	private static String sanitize(String id) {
		return id.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
