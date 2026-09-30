package dev.atlasmap.client.gui;

import dev.atlasmap.client.ScreenUtil;
import dev.atlasmap.region.BannerDesign;
import dev.atlasmap.region.Region;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lets the player design a banner (base color + up to 6 pattern layers) to
 * use as a region's "logo". The preview is a real vanilla banner
 * {@link ItemStack} built from {@link DataComponents#BANNER_PATTERNS}.
 * <p>
 * MAPPING NOTES: {@code Items.WHITE_BANNER} and its 15 siblings do NOT
 * exist as direct constants as of this Minecraft generation - per the
 * 26.1->26.2 migration notes, Items/Blocks/etc. now store their
 * {@code ResourceKey}s in a separate {@code *Ids} class (e.g. presumably
 * {@code ItemIds}), referenced whenever an id is needed, rather than
 * exposing the Item instances directly as before. Rather than guess that
 * class's exact name too, {@link #bannerItem(String)} below looks the item
 * up by its plain vanilla id string (e.g. "white_banner") via the same
 * registry-lookup pattern already confirmed working for banner patterns -
 * this only depends on the item's id, which is completely stable regardless
 * of which Java class exposes a constant for it.
 * <p>
 * NOTE on item rendering: {@code GuiGraphicsExtractor} has no {@code renderItem}
 * method at all - like {@code drawString} -> {@code text}, GUI item drawing was
 * renamed as part of the 26.x "extract" rendering split. The method is
 * {@code item(ItemStack, int x, int y)} (plus {@code fakeItem(...)}, which
 * passes a null holding entity, and {@code itemDecorations(...)} for the
 * count/durability overlay), per NeoForged's 26.1 screen docs. An earlier
 * draft used a {@code renderItem(stack, x, y, z, width, height)} signature
 * that actually belongs to a third-party wrapper library, not to
 * GuiGraphicsExtractor itself.
 * <p>
 * VERIFY-ON-26.2: if {@code item(...)} doesn't resolve either, autocomplete on
 * {@code graphics.} and look for "item" - {@code graphics.fakeItem(stack, x, y)}
 * is the closest alternative.
 */
public final class BannerDesignScreen extends Screen {

	private final Screen parent;
	private final Region region;
	private final BannerDesign design;
	private String pendingPatternId = BannerPatternCatalog.IDS[1];
	private String pendingColorId = DyePalette.IDS[5];

	public BannerDesignScreen(Screen parent, Region region) {
		super(Component.literal("Region Banner - " + region.getName()));
		this.parent = parent;
		this.region = region;
		this.design = region.getBannerDesign() != null ? region.getBannerDesign() : BannerDesign.createDefault();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int leftX = centerX - 90;

		this.addRenderableWidget(Button.builder(
				Component.literal("Base: " + DyePalette.displayName(design.getBaseColorId())), btn -> {
			design.setBaseColorId(DyePalette.next(design.getBaseColorId()));
			btn.setMessage(Component.literal("Base: " + DyePalette.displayName(design.getBaseColorId())));
		}).bounds(leftX, 70, 180, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.literal("Pattern: " + BannerPatternCatalog.displayName(pendingPatternId)), btn -> {
			pendingPatternId = BannerPatternCatalog.next(pendingPatternId);
			btn.setMessage(Component.literal("Pattern: " + BannerPatternCatalog.displayName(pendingPatternId)));
		}).bounds(leftX, 100, 180, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.literal("Layer color: " + DyePalette.displayName(pendingColorId)), btn -> {
			pendingColorId = DyePalette.next(pendingColorId);
			btn.setMessage(Component.literal("Layer color: " + DyePalette.displayName(pendingColorId)));
		}).bounds(leftX, 130, 180, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("+ Add layer"), btn -> {
			if (design.addLayer(pendingPatternId, pendingColorId)) {
				region.setBannerDesign(design);
				ScreenUtil.open(new BannerDesignScreen(parent, region));
			}
		}).bounds(leftX, 160, 85, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Remove last"), btn -> {
			design.removeLastLayer();
			region.setBannerDesign(design);
			ScreenUtil.open(new BannerDesignScreen(parent, region));
		}).bounds(leftX + 95, 160, 85, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> {
			region.setBannerDesign(design);
			ScreenUtil.open(parent);
		}).bounds(leftX, this.height - 50, 85, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn ->
				ScreenUtil.open(parent)
		).bounds(leftX + 95, this.height - 50, 85, 20).build());
	}

	private ItemStack buildPreviewStack() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return ItemStack.EMPTY;
		}

		Item bannerItem = bannerItem(design.getBaseColorId());
		if (bannerItem == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(bannerItem);
		if (design.getLayers().isEmpty()) {
			return stack;
		}

		HolderLookup.RegistryLookup<BannerPattern> patterns =
				client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);

		List<BannerPatternLayers.Layer> layers = new ArrayList<>();
		for (BannerDesign.Layer layer : design.getLayers()) {
			Holder<BannerPattern> patternHolder = patterns.getOrThrow(BannerPatternCatalog.resourceKey(layer.patternId()));
			DyeColor color = DyeColor.valueOf(layer.colorId().toUpperCase(Locale.ROOT));
			layers.add(new BannerPatternLayers.Layer(patternHolder, color));
		}
		stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
		return stack;
	}

	/**
	 * Looks up e.g. "minecraft:white_banner" directly by id, sidestepping
	 * whatever the current Items/ItemIds constants class is actually called
	 * - see the class-level MAPPING NOTES.
	 */
	private static Item bannerItem(String colorId) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}
		HolderLookup.RegistryLookup<Item> items = client.level.registryAccess().lookupOrThrow(Registries.ITEM);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Identifier.fromNamespaceAndPath("minecraft", colorId + "_banner"));
		return items.getOrThrow(key).value();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 15, 0xFFFFFFFF, true);

		int previewX = this.width / 2 + 60;
		int previewY = 90;
		graphics.pose().pushMatrix();
		graphics.pose().translate(previewX, previewY);
		graphics.pose().scale(3f, 3f);
		graphics.item(buildPreviewStack(), 0, 0);
		graphics.pose().popMatrix();

		int listY = 190;
		graphics.text(this.font, "Layers (" + design.getLayers().size() + "/" + BannerDesign.MAX_LAYERS + "):",
				this.width / 2 - 90, listY, 0xFFAAAAAA, false);
		for (int i = 0; i < design.getLayers().size(); i++) {
			BannerDesign.Layer layer = design.getLayers().get(i);
			String line = (i + 1) + ". " + BannerPatternCatalog.displayName(layer.patternId())
					+ " (" + DyePalette.displayName(layer.colorId()) + ")";
			graphics.text(this.font, line, this.width / 2 - 90, listY + 12 + i * 11, 0xFFFFFFFF, false);
		}
	}
}
