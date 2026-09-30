package dev.atlasmap.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * A minimal right-click popup menu: a list of labelled actions rendered as
 * a small panel and hit-tested manually, rather than built from real
 * Button widgets. This sidesteps needing to add/remove widgets from a
 * Screen dynamically (an API this project hasn't had a reason to touch
 * yet) - the menu is just data the owning screen renders and hit-tests
 * itself.
 */
public final class ContextMenu {

	public record Item(String label, boolean enabled, Runnable action) {
		public Item(String label, Runnable action) {
			this(label, true, action);
		}
	}

	private static final int ROW_HEIGHT = 16;
	private static final int PADDING = 4;

	public final int x;
	public final int y;
	public final List<Item> items;
	private final int width;

	public ContextMenu(Font font, int x, int y, List<Item> items) {
		this.x = x;
		this.y = y;
		this.items = items;
		int w = 60;
		for (Item item : items) {
			w = Math.max(w, font.width(item.label()) + PADDING * 2);
		}
		this.width = w;
	}

	public int height() {
		return items.size() * ROW_HEIGHT + PADDING * 2;
	}

	public int width() {
		return width;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && my >= y && mx < x + width && my < y + height();
	}

	/** Returns the item under (mx, my), or null if none. */
	public Item itemAt(double mx, double my) {
		if (!contains(mx, my)) {
			return null;
		}
		int row = (int) ((my - y - PADDING) / ROW_HEIGHT);
		if (row < 0 || row >= items.size()) {
			return null;
		}
		return items.get(row);
	}

	public void render(GuiGraphicsExtractor graphics, Font font, double mouseX, double mouseY) {
		graphics.fill(x, y, x + width, y + height(), 0xEE1A1A1A);
		graphics.outline(x, y, width, height(), 0xFF555555);

		Item hovered = itemAt(mouseX, mouseY);
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			int rowY = y + PADDING + i * ROW_HEIGHT;
			if (item == hovered && item.enabled()) {
				graphics.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, 0x552B7FD4);
			}
			int color = item.enabled() ? 0xFFFFFFFF : 0xFF808080;
			graphics.text(font, item.label(), x + PADDING, rowY + 4, color, false);
		}
	}
}
