package dev.atlasmap.client.gui;

import dev.atlasmap.client.ScreenUtil;
import dev.atlasmap.client.waypoint.ClientWaypointState;
import dev.atlasmap.waypoint.Waypoint;
import dev.atlasmap.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class EditWaypointScreen extends Screen {

	private final Screen parent;
	private final Waypoint editing;
	private EditBox nameBox;
	private int colorArgb;

	public EditWaypointScreen(Screen parent, Waypoint editing) {
		super(Component.literal(editing == null ? "New Waypoint" : "Edit Waypoint"));
		this.parent = parent;
		this.editing = editing;
		this.colorArgb = editing != null ? editing.getColorArgb() : ColorPalette.COLORS[0];
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int boxWidth = 200;

		this.nameBox = new EditBox(this.font, centerX - boxWidth / 2, 50, boxWidth, 20, Component.literal("Name"));
		this.nameBox.setValue(editing != null ? editing.getName() : "Waypoint");
		this.addRenderableWidget(this.nameBox);

		this.addRenderableWidget(Button.builder(Component.literal("Color: " + ColorPalette.nameOf(colorArgb)), btn -> {
			this.colorArgb = ColorPalette.next(this.colorArgb);
			btn.setMessage(Component.literal("Color: " + ColorPalette.nameOf(colorArgb)));
		}).bounds(centerX - boxWidth / 2, 80, boxWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
				.bounds(centerX - boxWidth / 2, 120, boxWidth / 2 - 5, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn ->
				ScreenUtil.open(parent)
		).bounds(centerX + 5, 120, boxWidth / 2 - 5, 20).build());
	}

	private void save() {
		Minecraft client = Minecraft.getInstance();
		WaypointManager manager = ClientWaypointState.manager();
		if (manager == null || client.player == null || client.level == null) {
			ScreenUtil.open(parent);
			return;
		}

		String name = this.nameBox.getValue().isBlank() ? "Waypoint" : this.nameBox.getValue();

		if (editing != null) {
			editing.setName(name);
			editing.setColorArgb(colorArgb);
		} else {
			BlockPos pos = client.player.blockPosition();
			String dimension = client.level.dimension().identifier().toString();
			manager.add(Waypoint.create(name, pos.getX(), pos.getY(), pos.getZ(), dimension, colorArgb));
		}
		manager.save();
		ScreenUtil.open(new WaypointListScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 20, 0xFFFFFFFF, true);

		String posLabel;
		if (editing != null) {
			posLabel = "Position: " + editing.getX() + ", " + editing.getY() + ", " + editing.getZ();
		} else {
			Minecraft client = Minecraft.getInstance();
			posLabel = client.player != null
					? "Position: current (" + client.player.blockPosition().toShortString() + ")"
					: "Position: current";
		}
		graphics.text(this.font, posLabel, (this.width - this.font.width(posLabel)) / 2, 150, 0xFFAAAAAA, false);
	}
}
