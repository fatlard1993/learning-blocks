package justfatlard.learning_blocks.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import static justfatlard.learning_blocks.editor.Ui.*;

/**
 * Every loot table the server has loaded, found by typing part of its name and taken with a click.
 * PvP Dimensions' block picker, made a list: a loot table has an id and no picture.
 *
 * <p>Laid out once and refilled in place as the search changes, which leaves the search box holding
 * the keyboard between letters. Read from the server each time it is opened, so a data pack's
 * tables are there after a {@code /reload}.
 */
final class LootPicker {
	private LootPicker() {}

	static final String SEARCH = "loot_search";
	static final int ROWS = 6;

	static List<String> all(MinecraftServer server) {
		List<String> ids = new ArrayList<>();
		server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
			.ifPresent(tables -> tables.listElementIds().forEach(key -> ids.add(key.identifier().toString())));
		ids.sort(String::compareTo);
		return ids;
	}

	/** The ids holding every word typed, those whose path starts with the first word ahead. */
	static List<String> matches(MinecraftServer server, String query) {
		String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
		List<String> all = all(server);
		if (words.length == 1 && words[0].isEmpty()) return all;
		List<String> ahead = new ArrayList<>();
		List<String> rest = new ArrayList<>();
		for (String id : all) {
			boolean every = true;
			for (String word : words) every &= id.contains(word);
			if (!every) continue;
			String path = id.substring(id.indexOf(':') + 1);
			(path.startsWith(words[0]) || path.contains("/" + words[0]) ? ahead : rest).add(id);
		}
		ahead.addAll(rest);
		return ahead;
	}

	static int pages(List<String> found) {
		return Math.max(1, (found.size() + ROWS - 1) / ROWS);
	}

	static @Nullable String at(MinecraftServer server, String query, int page, int row) {
		List<String> found = matches(server, query);
		int index = page * ROWS + row;
		return row >= 0 && row < ROWS && index < found.size() ? found.get(index) : null;
	}

	/** The picker's own rows. @return how tall */
	static int layout(List<ComponentBuilder> into, MinecraftServer server, int x, int y, int width, String label, String query, int page) {
		Map<String, Map<String, String>> state = state(server, query, page);
		int top = y;
		into.add(text("loot_label", x, y + 4, clip(label, 50)));
		y += 16;
		into.add(new ComponentBuilder(SEARCH, ComponentType.TEXT_INPUT).bounds(x, y, width, 16)
			.prop(ComponentType.PROP_VALUE, query)
			.prop(ComponentType.PROP_PLACEHOLDER, "Type part of a loot table's name")
			.prop(ComponentType.PROP_MAX_LENGTH, "60")
			.prop(ComponentType.PROP_FOCUSED, "true"));
		y += 20;
		for (int i = 0; i < ROWS; i++) {
			into.add(button("loot:" + i, x, y + i * (BUTTON + 1), width, BUTTON, "").props(state.get("loot:" + i)));
		}
		y += ROWS * (BUTTON + 1) + 3;
		into.add(button("loot_prev", x, y, 20, BUTTON, "<").props(state.get("loot_prev")));
		into.add(centred("loot_page", x + 20, y + 6, 90, "").props(state.get("loot_page")));
		into.add(button("loot_next", x + 110, y, 20, BUTTON, ">").props(state.get("loot_next")));
		into.add(button("loot_cancel", x + width - 50, y, 50, BUTTON, "Back", "Change nothing"));
		return y + BUTTON - top;
	}

	static List<ComponentUpdate> refresh(MinecraftServer server, String query, int page) {
		List<ComponentUpdate> updates = new ArrayList<>();
		state(server, query, page).forEach((id, props) -> updates.add(new ComponentUpdateBuilder(id).props(props).build()));
		return updates;
	}

	private static Map<String, Map<String, String>> state(MinecraftServer server, String query, int page) {
		List<String> found = matches(server, query);
		int pages = pages(found);
		Map<String, Map<String, String>> state = new LinkedHashMap<>();
		for (int i = 0; i < ROWS; i++) {
			int index = page * ROWS + i;
			String id = index < found.size() ? found.get(index) : null;
			state.put("loot:" + i, Map.of(ComponentType.PROP_VISIBLE, String.valueOf(id != null),
				ComponentType.PROP_LABEL, id != null ? clip(id, 48) : "", ComponentType.PROP_TOOLTIP, id != null ? id : ""));
		}
		state.put("loot_page", Map.of(ComponentType.PROP_TEXT, found.isEmpty() ? "Nothing found" : (page + 1) + " of " + pages));
		state.put("loot_prev", Map.of(ComponentType.PROP_ENABLED, String.valueOf(page > 0)));
		state.put("loot_next", Map.of(ComponentType.PROP_ENABLED, String.valueOf(page + 1 < pages)));
		return state;
	}
}
