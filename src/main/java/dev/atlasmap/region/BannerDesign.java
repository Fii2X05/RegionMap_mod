package dev.atlasmap.region;

import java.util.ArrayList;
import java.util.List;

public final class BannerDesign {

	public static final int MAX_LAYERS = 6;

	public record Layer(String patternId, String colorId) {
	}

	private String baseColorId;
	private final List<Layer> layers = new ArrayList<>();

	public BannerDesign(String baseColorId) {
		this.baseColorId = baseColorId;
	}

	public static BannerDesign createDefault() {
		return new BannerDesign("white");
	}

	public String getBaseColorId() {
		return baseColorId;
	}

	public void setBaseColorId(String baseColorId) {
		this.baseColorId = baseColorId;
	}

	public List<Layer> getLayers() {
		return layers;
	}

	public boolean addLayer(String patternId, String colorId) {
		if (layers.size() >= MAX_LAYERS) {
			return false;
		}
		layers.add(new Layer(patternId, colorId));
		return true;
	}

	public void removeLastLayer() {
		if (!layers.isEmpty()) {
			layers.remove(layers.size() - 1);
		}
	}
}
