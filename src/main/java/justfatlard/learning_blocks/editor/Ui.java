package justfatlard.learning_blocks.editor;

import java.util.Map;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;

/**
 * The pieces every screen here is built from, drawn the way the game's own screens are: a panel
 * the colour of the inventory, dark words on it, pictures laid over the buttons they belong to.

 */
public final class Ui {
	private Ui() {}

	public static final String INK = "#FF404040";
	public static final String FAINT = "#FF707070";
	public static final int PAD = 8;
	public static final int ICON = 16;
	public static final int BUTTON = 20;

	public static ComponentBuilder button(String id, int x, int y, int w, int h, String label) {
		return new ComponentBuilder(id, ComponentType.BUTTON).bounds(x, y, w, h).prop(ComponentType.PROP_LABEL, label);
	}

	public static ComponentBuilder button(String id, int x, int y, int w, int h, String label, String tooltip) {
		ComponentBuilder button = button(id, x, y, w, h, label);
		if (tooltip != null && !tooltip.isEmpty()) button.prop(ComponentType.PROP_TOOLTIP, tooltip);
		return button;
	}

	public static ComponentBuilder text(String id, int x, int y, String words) {
		return new ComponentBuilder(id, ComponentType.TEXT).pos(x, y)
			.prop(ComponentType.PROP_TEXT, words)
			.prop(ComponentType.PROP_COLOR, INK);
	}

	public static ComponentBuilder faint(String id, int x, int y, String words) {
		return text(id, x, y, words).prop(ComponentType.PROP_COLOR, FAINT);
	}

	/** Words over a button, where the panel's dark ink is washed out: the game's own white on one. */
	public static ComponentBuilder lit(String id, int x, int y, String words) {
		return text(id, x, y, words).prop(ComponentType.PROP_COLOR, "#FFFFFFFF").prop(ComponentType.PROP_SHADOW, "true");
	}

	public static ComponentBuilder litFaint(String id, int x, int y, String words) {
		return lit(id, x, y, words).prop(ComponentType.PROP_COLOR, "#FFB4B4B4");
	}

	public static ComponentBuilder centred(String id, int x, int y, int w, String words) {
		return new ComponentBuilder(id, ComponentType.TEXT).bounds(x, y, w, 10)
			.prop(ComponentType.PROP_TEXT, words)
			.prop(ComponentType.PROP_COLOR, INK)
			.prop(ComponentType.PROP_ALIGN, "center");
	}

	public static ComponentBuilder icon(String id, int x, int y, String item, float scale) {
		return new ComponentBuilder(id, ComponentType.ITEM_ICON)
			.bounds(x, y, ICON, ICON)
			.scale(scale)
			.prop(ComponentType.PROP_ITEM_ID, item);
	}

	public static ComponentBuilder icon(String id, int x, int y, String item, int count) {
		return icon(id, x, y, item, 1F).prop(ComponentType.PROP_ITEM_COUNT, String.valueOf(count));
	}

	public static Map<String, String> panel() {
		return Map.of(ComponentType.PROP_BORDER, "beveled");
	}

	public static String clip(String words, int letters) {
		return words.length() <= letters ? words : words.substring(0, Math.max(1, letters - 3)) + "...";
	}
}
