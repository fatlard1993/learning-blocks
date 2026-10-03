package justfatlard.learning_blocks.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.reward.PrizeFile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import static justfatlard.learning_blocks.editor.Ui.*;

/**
 * Every prize setting, in game, for operators: PvP Dimensions' preset editor, made for one file.
 *
 * <p>A tab per part of the file, a row per setting with the control that changes it, and every
 * change written to {@code config/learning-blocks/prizes.json} as it is made. The file stays the one source
 * of truth: a change here reads the file, changes it, writes it, and loads the config back from it
 * exactly as {@code /learn reload} does, so what the screen shows and what the file says cannot
 * come apart, and a hand edit followed by a reload shows up here at once.
 *
 * <p>Everything the file can hold has a control. Prize items are edited by holding them
 * ({@link PrizeSessions}), loot tables are picked from every table the server has loaded
 * ({@link LootPicker}), and experience has a row of its own on every prize list.
 *
 * <p>Component ids name the setting's path in the file - {@code next:events.streak.length} - rather
 * than its place on screen, so a press means the same setting even if the file changed underneath
 * the open screen.
 */
public final class PrizeEditor {
	private PrizeEditor() {}

	public static final String TYPE = LearningBlocks.MOD_ID + ":prizes";

	public enum Tab {
		GENERAL("General"), EVENTS("Events"), MILESTONES("Milestones"), THRESHOLDS("Thresholds");

		final String label;

		Tab(String label) {
			this.label = label;
		}

		static Tab of(String name) {
			try {
				return valueOf(name);
			} catch (IllegalArgumentException e) {
				return GENERAL;
			}
		}
	}

	/** Fits the smallest window the game allows, 320 by 240 at any GUI scale. */
	private static final int WIDTH = 316;
	private static final int ROW = 22;
	private static final int ROWS = 6;
	private static final int CONTROL = 150;

	private record Pick(String prizes, String query, int page) {}

	private record State(Tab tab, int scroll, @Nullable Pick pick) {}

	private static final Map<UUID, State> states = new ConcurrentHashMap<>();
	private static final Map<UUID, String> screens = new ConcurrentHashMap<>();

	// --- Ladders a number steps along ---

	private static final int[] CAP = {0, 1, 2, 3, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200};
	private static final int[] POINTS = {0, 1, 2, 3, 5, 10, 15, 20, 25, 50, 100};
	private static final int[] LENGTH = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20};
	private static final int[] WORDS = {1, 2, 3, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 250, 500, 1000};
	private static final int[] TOTALS = {1, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 250, 500, 1000};
	private static final int[] SCENE_EVERY = {1, 2, 3, 4, 5, 6, 8, 10};
	private static final int[] LETTER_WORD_EVERY = {0, 2, 3, 4, 5, 6, 8, 10};
	private static final int[] REVIEW_SHARE = {0, 10, 20, 30, 40, 50};
	private static final int[] MASTERY = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
	private static final int[] WEIGHTS = {0, 1, 2, 3, 4, 5, 10};
	private static final List<String> MIXES = List.of("alternate", "blocks", "weights");
	private static final int[] XP = {0, 1, 2, 3, 5, 10, 15, 20, 25, 30, 50, 75, 100, 150, 200, 300, 500};

	private static final Map<String, String> EVENT_NAMES = Map.of(
		"new_word", "New word", "solved", "Word read", "streak", "Streak",
		"stage_complete", "Stage complete", "milestone", "Milestones", "toy_mastered", "Toy mastered", "daily_visit", "Daily visit");

	private static final Map<String, String> EVENT_HELP = Map.of(
		"new_word", "The first time a child ever reads a word; once per word",
		"solved", "Any word read",
		"streak", "Every so many words read in a row",
		"stage_complete", "Every word of a stage band (1a, 1b...) mastered",
		"toy_mastered", "A plushie or toy, earned by mastering every word that brings it",
		"daily_visit", "The first visit of each real day");

	public static void register(ScreenApi screens) {
		screens.onActionFallback(TYPE, PrizeEditor::pressed);
		screens.onClose(TYPE, player -> {
			states.remove(player.getUUID());
			PrizeEditor.screens.remove(player.getUUID());
		});
	}

	/** The server stopped: nobody is editing. */
	public static void clear() {
		states.clear();
		screens.clear();
	}

	public static void forget(UUID player) {
		states.remove(player);
		screens.remove(player);
	}

	public static boolean canShow(ServerPlayer player) {
		return Admins.may(player) && PandoricalApi.isAvailable(player);
	}

	/** Open on this tab; a string, so a session kept in a save can name it. */
	public static void show(ServerPlayer player, String tab) {
		State before = states.get(player.getUUID());
		Tab chosen = Tab.of(tab);
		show(player, new State(chosen, before != null && before.tab() == chosen ? before.scroll() : 0, null));
	}

	/** Words for a prize list's path, for a chat line: "New word", "the 10 words milestone". */
	public static String describe(String prizes) {
		String[] parts = prizes.split("\\.");
		if (parts.length >= 3 && parts[0].equals("events") && !parts[1].equals("milestone")) {
			return EVENT_NAMES.getOrDefault(parts[1], parts[1]);
		}
		return "a " + (prizes.startsWith("points") ? "points threshold" : "milestone");
	}

	/**
	 * Change the file: read it, change the tree, write it, load it. The problems the load found,
	 * or null when the file could not be read or written at all, in which case nothing changed.
	 */
	public static @Nullable List<String> change(MinecraftServer server, Consumer<JsonObject> edit) {
		try {
			JsonObject tree = PrizeFile.read();
			edit.accept(tree);
			PrizeFile.write(tree);
		} catch (Exception e) {
			LearningBlocks.LOGGER.error("[{}] Could not change prizes.json", LearningBlocks.MOD_ID, e);
			return null;
		}
		return PrizeConfig.load(server);
	}

	/** A press, as a click would send it: for tests. */
	public static void press(ServerPlayer player, String componentId) {
		pressed(player, Map.of(ScreenApi.FALLBACK_COMPONENT_ID_KEY, componentId));
	}

	/** A press with data, such as the text typed: for tests. */
	public static void press(ServerPlayer player, String componentId, Map<String, String> data) {
		Map<String, String> all = new java.util.HashMap<>(data);
		all.put(ScreenApi.FALLBACK_COMPONENT_ID_KEY, componentId);
		pressed(player, all);
	}

	// --- Rows ---

	private sealed interface Row {
		record Heading(String text, String help) implements Row {}
		record Note(String text) implements Row {}
		record Toggle(String label, String path, boolean fallback, String on, String off, String help) implements Row {}
		record Number(String label, String path, int fallback, int[] ladder, String unit, @Nullable String remove) implements Row {}
		record Choice(String label, String path, List<String> options, String fallback, String help) implements Row {}
		record Items(String prizes) implements Row {}
		record Xp(String prizes) implements Row {}
		record Loot(String prizes, int index, String table) implements Row {}
		/**
		 * A toybox prize in a prize list, as {@code <key>:<which>}: {@code toybox:random_new}, or a
		 * plushie or a toy, a particular one or random_new.
		 */
		record Toybox(String prizes, int index, String choice) implements Row {}
		record Action(String id, String label, String help) implements Row {}
		/** A reader's daily reading: lessons a day, and weekends off or not. */
		record Daily(java.util.UUID player, String name) implements Row {}
	}

	private static List<Row> rows(JsonObject tree, Tab tab, MinecraftServer server) {
		List<Row> rows = new ArrayList<>();
		boolean points = bool(tree, "points.enabled", false);
		switch (tab) {
			case GENERAL -> {
				rows.add(new Row.Toggle("Prizes", "enabled", true, "On", "Off", "Every prize below, on or off at once"));
				rows.add(new Row.Number("Daily cap", "daily_cap", 0, CAP, " a day", null));
				rows.add(new Row.Toggle("Points", "points.enabled", false, "On", "Off", "Points from events, and thresholds they pay at"));
				rows.add(new Row.Heading("Puzzles", "How rounds are asked"));
				rows.add(new Row.Toggle("Situations", "puzzles.scenes", true, "On", "Off",
					"Now and then the room acts out a problem one of their read words fixes"));
				rows.add(new Row.Number("How often", "puzzles.scene_every", 4, SCENE_EVERY, " rounds", null));
				rows.add(new Row.Toggle("Letters first", "puzzles.letters_first", true, "On", "Off",
					"Start new readers on letters (stage 0): hear a sound, find its letter"));
				rows.add(new Row.Number("Mastered after", "puzzles.mastery_clean", 3, MASTERY, " clean", null));
				rows.add(new Row.Number("Words in letters", "puzzles.letter_word_every", 3, LETTER_WORD_EVERY, " rounds", null));
				rows.add(new Row.Toggle("Read it", "puzzles.read_it", true, "On", "Off",
					"From stage 2: a read word shown whole, and its picture picked from two or three"));
				rows.add(new Row.Number("Read it every", "puzzles.read_it_every", 5, SCENE_EVERY, " rounds", null));
				rows.add(new Row.Number("Review share", "puzzles.review_share", 30, REVIEW_SHARE, "%", null));
				rows.add(new Row.Toggle("First sound", "puzzles.first_sound", true, "On", "Off", "_at: cat, bat, hat"));
				rows.add(new Row.Toggle("Last sound", "puzzles.last_sound", true, "On", "Off", "ma_: map, mat, man; once most of a stage is read"));
				rows.add(new Row.Toggle("Middle vowel", "puzzles.middle_vowel", true, "On", "Off", "c_t: cat, cot, cut; from stage 2"));
				rows.add(new Row.Toggle("Whole word", "puzzles.whole_word", true, "On", "Off", "Build the whole word from its tiles; from stage 6"));
				rows.add(new Row.Toggle("Team-up", "puzzles.team_up", true, "On", "Off", "Reading together: both build one word, a gap each"));
				rows.add(new Row.Number("Team-up every", "puzzles.team_up_every", 3, LETTER_WORD_EVERY, " rounds", null));
				rows.add(new Row.Toggle("Tell Bob", "puzzles.tell_bob", true, "On", "Off",
					"From stage 5: word cards make a sentence, and Bob does exactly what it says"));
				rows.add(new Row.Number("Tell Bob every", "puzzles.tell_bob_every", 5, SCENE_EVERY, " rounds", null));
				rows.add(new Row.Toggle("What's wrong", "puzzles.whats_wrong", true, "On", "Off",
					"Bob's sentence made wrong, one card to fix; from stage 2, a word with one wrong letter"));
				rows.add(new Row.Number("Fix a word every", "puzzles.whats_wrong_every", 6, SCENE_EVERY, " rounds", null));
				rows.add(new Row.Number("Bob's props at most", "puzzles.bob_prop_share", 20, REVIEW_SHARE, "%", null));
				rows.add(new Row.Toggle("Silly sentences count", "puzzles.silly_counts", false, "Yes", "No",
					"Whether a silly sentence told to Bob counts as a lesson (never towards mastery)"));
				rows.add(new Row.Heading("Subjects", "/learn subjects <player> sets one child's"));
				rows.add(new Row.Toggle("Reading", "subjects.reading", true, "On", "Off", "Letters, words, sentences with Bob"));
				rows.add(new Row.Toggle("Numbers", "subjects.numbers", true, "On", "Off", "Which block says three? (1 to 5, for now)"));
				rows.add(new Row.Toggle("Logic", "subjects.logic", true, "On", "Off", "What comes next? (two colours, for now)"));
				rows.add(new Row.Choice("Mixed", "subjects.mix", MIXES, "alternate",
					"alternate: one of each in turn; blocks: a few of one, then the next; weights: by chance"));
				rows.add(new Row.Number("Blocks of", "subjects.block", 3, SCENE_EVERY, " rounds", null));
				for (String subject : justfatlard.learning_blocks.subject.Subjects.ids()) {
					rows.add(new Row.Number("Weight: " + subject, "subjects.weights." + subject, 1, WEIGHTS, "", null));
				}
				List<String> problems = PrizeConfig.lastProblems();
				rows.add(new Row.Note(problems.isEmpty() ? "The file read cleanly." : problems.size()
					+ (problems.size() == 1 ? " entry was" : " entries were") + " skipped; see the log"));
				rows.add(new Row.Action("reload", "Read the file again", "Pick up a hand edit to prizes.json"));
				// Where everybody online is, since reading earns by stage; /learn stage moves them.
				rows.add(new Row.Heading("Readers", "/learn stage <player> <n> moves one"));
				int listed = 0;
				for (ServerPlayer online : server.getPlayerList().getPlayers()) {
					if (listed++ >= 12) break;
					int left = justfatlard.learning_blocks.classroom.Travel.lessons(online) + Progress.of(online).lessonsOwed();
					rows.add(new Row.Action("progress:" + online.getGameProfile().name(), online.getGameProfile().name(),
						Stages.describe(Progress.of(online)) + (left > 0 ? ", " + left + " lessons left" : "") + ". Click for their progress"));
					rows.add(new Row.Daily(online.getUUID(), online.getGameProfile().name()));
				}
			}
			case EVENTS -> {
				for (String event : PrizeFile.EVENTS) {
					if (event.equals("milestone")) continue;
					String base = "events." + event;
					rows.add(new Row.Heading(EVENT_NAMES.get(event), EVENT_HELP.getOrDefault(event, "")));
					rows.add(new Row.Toggle("Pays", base + ".enabled", event.equals("toy_mastered"), "Yes", "No", EVENT_HELP.getOrDefault(event, "")));
					if (points) rows.add(new Row.Number("Points", base + ".points", 0, POINTS, "", null));
					if (event.equals("solved")) {
						rows.add(new Row.Toggle("First try only", base + ".first_try_only", true, "Yes", "No",
							"Only when no other word was built this round first"));
					}
					if (event.equals("streak")) rows.add(new Row.Number("Every", base + ".length", 3, LENGTH, " in a row", null));
					prizeRows(rows, tree, base + ".prizes");
				}
			}
			case MILESTONES -> {
				rows.add(new Row.Toggle("Milestones", "events.milestone.enabled", false, "On", "Off", "Totals of different words ever read"));
				marks(rows, tree, "events.milestone.every", "words", WORDS, " words");
				rows.add(new Row.Action("add:events.milestone.every", "Add a milestone", "Another total of words to pay at"));
			}
			case THRESHOLDS -> {
				if (!points) rows.add(new Row.Note("Points are off; turn them on under General."));
				marks(rows, tree, "points.thresholds", "points", TOTALS, " points");
				rows.add(new Row.Action("add:points.thresholds", "Add a threshold", "Another points total to pay at"));
			}
		}
		return rows;
	}

	private static void marks(List<Row> rows, JsonObject tree, String list, String key, int[] ladder, String unit) {
		JsonElement found = PrizeFile.get(tree, list);
		if (found == null || !found.isJsonArray()) return;
		JsonArray marks = found.getAsJsonArray();
		for (int i = 0; i < marks.size(); i++) {
			String base = list + "." + i;
			rows.add(new Row.Number("At", base + "." + key, 1, ladder, unit, "rm:" + base));
			prizeRows(rows, tree, base + ".prizes");
		}
	}

	/** A prize list's rows: its items, its experience, each loot table. */
	private static void prizeRows(List<Row> rows, JsonObject tree, String prizes) {
		rows.add(new Row.Items(prizes));
		rows.add(new Row.Xp(prizes));
		JsonElement found = PrizeFile.get(tree, prizes);
		if (found != null && found.isJsonArray()) {
			JsonArray list = found.getAsJsonArray();
			for (int i = 0; i < list.size(); i++) {
				JsonElement entry = list.get(i);
				if (entry.isJsonObject() && entry.getAsJsonObject().has("loot_table")) {
					rows.add(new Row.Loot(prizes, i, entry.getAsJsonObject().get("loot_table").getAsString()));
				}
				String choice = entry.isJsonObject() ? toyboxChoice(entry.getAsJsonObject()) : null;
				if (choice != null) rows.add(new Row.Toybox(prizes, i, choice));
			}
		}
		rows.add(new Row.Action("lootadd:" + prizes, "Add a loot table", "One roll of it, when this pays"));
		rows.add(new Row.Action("plushadd:" + prizes, "Add a plushie", "A new one for their collection, or one in particular"));
		rows.add(new Row.Action("toyadd:" + prizes, "Add a toy", "A new one for their collection, or one in particular"));
	}

	// --- Drawing ---

	private static void show(ServerPlayer player, State state) {
		if (!canShow(player)) return;
		MinecraftServer server = player.level().getServer();
		JsonObject tree;
		try {
			tree = PrizeFile.read();
		} catch (Exception e) {
			player.sendSystemMessage(Component.literal("The prizes file could not be read: " + e.getMessage()));
			return;
		}
		states.put(player.getUUID(), state);
		int inner = WIDTH - PAD * 2;
		List<ComponentBuilder> under = new ArrayList<>();
		int y = 20;

		Tab[] tabs = Tab.values();
		int tabW = (inner - 2 * (tabs.length - 1)) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			ComponentBuilder tab = button("tab:" + tabs[i].name(), PAD + i * (tabW + 2), y, tabW, BUTTON, tabs[i].label);
			if (tabs[i] == state.tab()) tab.prop(ComponentType.PROP_STYLE, "pressed");
			under.add(tab);
		}
		y += BUTTON + 4;

		if (state.pick() != null) {
			Pick pick = state.pick();
			List<ComponentBuilder> picker = new ArrayList<>();
			y += LootPicker.layout(picker, server, PAD, y, inner, "A loot table for " + describe(pick.prizes()), pick.query(), pick.page()) + PAD;
			ScreenBuilder screen = new ScreenBuilder(TYPE).title("Prizes").pauseGame(false).size(WIDTH, y);
			screen.panel("dialog", 0, 0, WIDTH, y, panel());
			screen.component(icon("title_icon", PAD, 3, "minecraft:chest", 0.75F));
			screen.component(text("title", PAD + 16, 7, "Learning Blocks prizes"));
			for (ComponentBuilder component : under) screen.component(component.prop(ComponentType.PROP_ENABLED, "false"));
			for (ComponentBuilder component : picker) screen.component(component);
			screens.put(player.getUUID(), screen.screenId());
			PandoricalApi.screens().open(player, screen.build());
			return;
		}

		List<Row> rows = rows(tree, state.tab(), server);
		List<ComponentBuilder> drawn = new ArrayList<>();
		int controlX = inner - 6 - CONTROL;
		for (int i = 0; i < rows.size(); i++) row(drawn, tree, server, rows.get(i), i * ROW, controlX);
		int listY = y;
		y += ROWS * ROW + 4;
		under.add(faint("file", PAD, y + 6, "Saved to config/learning-blocks/prizes.json"));
		under.add(button("close", PAD + inner - 50, y, 50, BUTTON, "Done"));
		y += BUTTON;

		int height = y + PAD;
		ScreenBuilder screen = new ScreenBuilder(TYPE).title("Prizes").pauseGame(false).size(WIDTH, height);
		screen.panel("dialog", 0, 0, WIDTH, height, panel());
		screen.component(icon("title_icon", PAD, 3, "minecraft:chest", 0.75F));
		screen.component(text("title", PAD + 16, 7, "Learning Blocks prizes"));
		for (ComponentBuilder component : under) screen.component(component);
		List<ComponentDef> defs = new ArrayList<>();
		for (ComponentBuilder component : drawn) defs.add(component.build());
		int count = rows.size();
		screen.scrollPanel("rows", PAD, listY, inner, ROWS * ROW, Map.of(
			ComponentType.PROP_ITEM_HEIGHT, String.valueOf(ROW),
			ComponentType.PROP_VISIBLE_ITEMS, String.valueOf(ROWS),
			ComponentType.PROP_TOTAL_ITEMS, String.valueOf(Math.max(count, 1)),
			ComponentType.PROP_SHOW_SCROLLBAR, String.valueOf(count > ROWS),
			ComponentType.PROP_SCROLL_OFFSET, String.valueOf(Math.max(0, Math.min(state.scroll(), Math.max(0, count - ROWS)))),
			ComponentType.PROP_BACKGROUND, "#00000000"), defs);
		screens.put(player.getUUID(), screen.screenId());
		PandoricalApi.screens().open(player, screen.build());
	}

	private static void row(List<ComponentBuilder> out, JsonObject tree, MinecraftServer server, Row row, int y, int x) {
		switch (row) {
			case Row.Heading heading -> {
				out.add(text("h:" + y, 2, y + 8, clip(heading.text(), 22)));
				if (!heading.help().isEmpty()) out.add(faint("hh:" + y, 100, y + 8, clip(heading.help(), 34)));
			}
			case Row.Note note -> out.add(faint("n:" + y, 2, y + 7, clip(note.text(), 50)));
			case Row.Toggle toggle -> {
				out.add(text("l:" + toggle.path(), 2, y + 6, clip(toggle.label(), 22)));
				boolean on = bool(tree, toggle.path(), toggle.fallback());
				out.add(button("t:" + toggle.path(), x, y, CONTROL, BUTTON, on ? toggle.on() : toggle.off(), toggle.help())
					.prop(ComponentType.PROP_STYLE, on ? "accepted" : "default"));
			}
			case Row.Number number -> {
				out.add(text("l:" + number.path(), 2, y + 6, clip(number.label(), 22)));
				int value = integer(tree, number.path(), number.fallback());
				int width = number.remove() != null ? CONTROL - 18 : CONTROL;
				String shown = number.path().equals("daily_cap") && value == 0 ? "No cap"
					: number.path().equals("puzzles.scene_every") ? (value == 1 ? "Every round" : "1 in " + value + number.unit())
					: value + number.unit();
				stepper(out, number.path(), x, y, width, shown, step(number.ladder(), value, -1) != value, step(number.ladder(), value, 1) != value);
				if (number.remove() != null) out.add(button(number.remove(), x + CONTROL - 14, y, 14, BUTTON, "✕", "Remove it, prizes and all"));
			}
			case Row.Choice choice -> {
				out.add(text("l:" + choice.path(), 2, y + 6, clip(choice.label(), 22)));
				String value = string(tree, choice.path(), choice.fallback());
				stepper(out, choice.path(), x, y, CONTROL, value, true, true);
			}
			case Row.Items items -> {
				out.add(text("l:" + items.prizes() + "#items", 2, y + 6, "Items"));
				JsonArray list = PrizeFile.array(tree, items.prizes());
				List<ItemStack> stacks = PrizeSessions.items(list, server);
				int missing = 0;
				for (JsonElement entry : list) {
					if (entry.isJsonObject() && entry.getAsJsonObject().has("item") && !PrizeSessions.laidOut(entry)) missing++;
				}
				int room = 5;
				int shown = stacks.size() > room ? room - 1 : stacks.size();
				for (int i = 0; i < shown; i++) {
					ItemStack stack = stacks.get(i);
					out.add(icon("i" + i + ":" + items.prizes(), x + i * 17, y + 2,
						BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
				}
				if (stacks.isEmpty()) out.add(faint("v:" + items.prizes(), x + 2, y + 6, missing > 0 ? missing + " not here" : "None"));
				else if (stacks.size() > shown) out.add(faint("more:" + items.prizes(), x + shown * 17 + 1, y + 6, "+" + (stacks.size() - shown)));
				out.add(button("items:" + items.prizes(), x + CONTROL - 52, y, 36, BUTTON, "Edit",
					"Hold the items you want given, then say done"
						+ (missing > 0 ? ". " + missing + " from mods not installed here are kept" : "")));
				out.add(button("clear:" + items.prizes(), x + CONTROL - 14, y, 14, BUTTON, "✕", "No items"));
			}
			case Row.Xp xp -> {
				out.add(text("l:" + xp.prizes() + "#xp", 2, y + 6, "Experience"));
				int value = xp(PrizeFile.array(tree, xp.prizes()));
				stepper(out, "xp:" + xp.prizes(), x, y, CONTROL, value == 0 ? "None" : value + (value == 1 ? " point" : " points"),
					value > 0, step(XP, value, 1) != value);
			}
			case Row.Loot loot -> {
				out.add(faint("l:" + loot.prizes() + "#" + loot.index(), 2, y + 6, "Loot table"));
				out.add(icon("li:" + loot.prizes() + "#" + loot.index(), x, y + 2, "minecraft:chest", 1F));
				out.add(text("lv:" + loot.prizes() + "#" + loot.index(), x + 18, y + 6, clip(shortLoot(loot.table()), 20)));
				out.add(button("lootrm:" + loot.prizes() + "#" + loot.index(), x + CONTROL - 14, y, 14, BUTTON, "✕", loot.table()));
			}
			case Row.Toybox toybox -> {
				String at = toybox.prizes() + "#" + toybox.index();
				String key = toybox.choice().substring(0, toybox.choice().indexOf(':'));
				String which = toybox.choice().substring(key.length() + 1);
				out.add(faint("l:" + at, 2, y + 6, key.equals("plush") ? "Plushie" : key.equals("toy") ? "Toy" : "Toybox"));
				String shown = key.equals("toybox") ? "Plushie or toy" : which.equals("random_new") ? "A new one"
					: which.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + which.substring(1);
				stepper(out, "plush:" + at, x, y, CONTROL - 16, shown, true, true);
				out.add(button("plushrm:" + at, x + CONTROL - 14, y, 14, BUTTON, "✕", "No prize here"));
			}
			case Row.Action action -> out.add(button(action.id(), x, y, CONTROL, BUTTON, action.label(), action.help()));
			case Row.Daily daily -> {
				String key = daily.player().toString();
				var entry = justfatlard.learning_blocks.classroom.Schedules.of(daily.player());
				int lessons = entry == null || !entry.enabled() ? 0 : entry.lessons();
				out.add(faint("l:daily:" + key, 2, y + 6, clip(daily.name() + " daily", 18)));
				stepper(out, "daily:" + key, x, y, CONTROL - 42, lessons == 0 ? "Off" : lessons + " a day", lessons > 0, lessons < 50);
				boolean off = justfatlard.learning_blocks.classroom.Schedules.weekendsOff(daily.player());
				out.add(button("weekend:" + key, x + CONTROL - 40, y, 40, BUTTON, off ? "Wkd off" : "Wkd on",
					off ? "No reading needed at weekends; click for every day" : "Reading every day; click for weekends off"));
			}
		}
	}

	private static void stepper(List<ComponentBuilder> out, String key, int x, int y, int width, String value, boolean back, boolean forward) {
		out.add(button("prev:" + key, x, y, 20, BUTTON, "<").prop(ComponentType.PROP_ENABLED, String.valueOf(back)));
		out.add(centred("v:" + key, x + 20, y + 6, width - 40, clip(value, 20)));
		out.add(button("next:" + key, x + width - 20, y, 20, BUTTON, ">").prop(ComponentType.PROP_ENABLED, String.valueOf(forward)));
	}

	private static String shortLoot(String table) {
		String path = table.substring(table.indexOf(':') + 1);
		return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
	}

	// --- Presses ---

	private static void pressed(ServerPlayer player, Map<String, String> data) {
		String id = data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY);
		State state = states.getOrDefault(player.getUUID(), new State(Tab.GENERAL, 0, null));
		if (id == null || !Admins.may(player)) return;
		MinecraftServer server = player.level().getServer();
		if (id.equals("rows")) {
			try {
				states.put(player.getUUID(), new State(state.tab(), Integer.parseInt(data.getOrDefault("scroll_offset", "0")), null));
			} catch (NumberFormatException ignored) {
			}
			return;
		}
		if (state.pick() != null) {
			picking(player, server, data, id, state);
			return;
		}
		if (id.equals("close")) {
			String open = screens.remove(player.getUUID());
			states.remove(player.getUUID());
			if (open != null) PandoricalApi.screens().close(player, open);
			return;
		}
		if (id.equals("reload")) {
			PrizeConfig.load(server);
			show(player, state);
			return;
		}

		int colon = id.indexOf(':');
		if (colon < 0) return;
		String kind = id.substring(0, colon);
		String path = id.substring(colon + 1);

		switch (kind) {
			case "tab" -> {
				show(player, new State(Tab.of(path), 0, null));
				return;
			}
			case "weekend" -> {
				java.util.UUID reader = java.util.UUID.fromString(path);
				justfatlard.learning_blocks.classroom.Schedules.setWeekendsOff(reader, readerName(server, reader),
					!justfatlard.learning_blocks.classroom.Schedules.weekendsOff(reader));
				show(player, state);
				return;
			}
			case "prev", "next" -> {
				if (path.startsWith("daily:")) {
					java.util.UUID reader = java.util.UUID.fromString(path.substring("daily:".length()));
					var entry = justfatlard.learning_blocks.classroom.Schedules.of(reader);
					int now = entry == null || !entry.enabled() ? 0 : entry.lessons();
					int by = kind.equals("next") ? 1 : -1;
					justfatlard.learning_blocks.classroom.Schedules.set(reader, readerName(server, reader), Math.max(0, Math.min(50, now + by)));
					show(player, state);
					return;
				}
			}
			case "progress" -> {
				ServerPlayer reader = server.getPlayerList().getPlayerByName(path);
				if (reader != null) justfatlard.learning_blocks.review.ProgressView.open(player, reader);
				return;
			}
			case "items" -> {
				String problem = PrizeSessions.start(player, path, state.tab().name());
				if (problem != null) player.sendSystemMessage(Component.literal(problem));
				else {
					String open = screens.remove(player.getUUID());
					if (open != null) PandoricalApi.screens().close(player, open);
				}
				return;
			}
			case "lootadd" -> {
				show(player, new State(state.tab(), state.scroll(), new Pick(path, "", 0)));
				return;
			}
			default -> { }
		}

		List<String> result = change(server, tree -> edit(tree, kind, path));
		if (result == null) player.sendSystemMessage(Component.literal("The prizes file could not be changed; see the log"));
		show(player, states.getOrDefault(player.getUUID(), state));
	}

	/** A prize entry that comes from the toybox, as {@code <key>:<which>}; null for any other prize. */
	private static @org.jspecify.annotations.Nullable String toyboxChoice(JsonObject prize) {
		for (String key : List.of("plush", "toy", "toybox")) {
			if (prize.has(key) && prize.get(key).isJsonPrimitive()) return key + ":" + prize.get(key).getAsString();
		}
		return null;
	}

	/** One press's change to the tree. */
	private static void edit(JsonObject tree, String kind, String path) {
		switch (kind) {
			case "t" -> PrizeFile.set(tree, path, new JsonPrimitive(!bool(tree, path, defaultBool(path))));
			case "prev", "next" -> {
				int by = kind.equals("next") ? 1 : -1;

				if (path.startsWith("plush:")) {
					edit(tree, by > 0 ? "plushnext" : "plushprev", path.substring("plush:".length()));
					return;
				}
				if (path.startsWith("xp:")) {
					String prizes = path.substring(3);
					JsonArray list = PrizeFile.array(tree, prizes);
					setXp(list, step(XP, xp(list), by));
					return;
				}
				if (path.equals("subjects.mix")) {
					int at = Math.max(0, MIXES.indexOf(string(tree, path, "alternate")));
					PrizeFile.set(tree, path, new JsonPrimitive(MIXES.get(Math.floorMod(at + by, MIXES.size()))));
					return;
				}
				int[] ladder = ladderFor(path);
				int fallback = path.startsWith("subjects.weights.") ? 1 : path.equals("subjects.block") ? 3 : path.equals("puzzles.scene_every") ? 4 : path.equals("puzzles.mastery_clean") ? 3
					: path.equals("puzzles.tell_bob_every") ? 5 : path.equals("puzzles.whats_wrong_every") ? 6
					: path.equals("puzzles.letter_word_every") || path.equals("puzzles.team_up_every") ? 3 : path.equals("puzzles.read_it_every") ? 5
					: path.equals("puzzles.review_share") ? 30 : path.equals("puzzles.bob_prop_share") ? 20 : ladder[0];
				PrizeFile.set(tree, path, new JsonPrimitive(step(ladder, integer(tree, path, fallback), by)));
			}
			case "clear" -> {
				JsonArray list = PrizeFile.array(tree, path);
				JsonArray kept = new JsonArray();
				for (JsonElement entry : list) if (!PrizeSessions.laidOut(entry)) kept.add(entry);
				PrizeFile.set(tree, path, kept);
			}
			case "plushadd", "toyadd" -> {
				JsonObject entry = new JsonObject();
				entry.addProperty(kind.equals("toyadd") ? "toy" : "plush", "random_new");
				PrizeFile.array(tree, path).add(entry);
			}
			case "plushrm", "plushprev", "plushnext" -> {
				int hash = path.lastIndexOf('#');
				if (hash < 0) return;
				JsonArray list = PrizeFile.array(tree, path.substring(0, hash));
				int index = Integer.parseInt(path.substring(hash + 1));
				if (index < 0 || index >= list.size() || !list.get(index).isJsonObject()) return;
				JsonObject prize = list.get(index).getAsJsonObject();
				String now = toyboxChoice(prize);
				if (now == null) return;
				if (kind.equals("plushrm")) {
					list.remove(index);
					return;
				}
				// A plushie steps through random_new and every plushie, a toy through random_new and every
				// toy, round again; the toybox's own prize is always random_new.
				String key = now.substring(0, now.indexOf(':'));
				if (key.equals("toybox")) return;
				List<String> choices = new ArrayList<>();
				choices.add("random_new");
				if (key.equals("plush")) {
					for (justfatlard.learning_blocks.plush.Plush p : justfatlard.learning_blocks.plush.Plush.values()) choices.add(p.word());
				} else {
					for (justfatlard.learning_blocks.toy.Toy t : justfatlard.learning_blocks.toy.Toy.values()) choices.add(t.word());
				}
				int at = Math.max(0, choices.indexOf(now.substring(key.length() + 1)));
				at = (at + (kind.equals("plushnext") ? 1 : choices.size() - 1)) % choices.size();
				prize.addProperty(key, choices.get(at));
			}
			case "lootrm" -> {
				int hash = path.lastIndexOf('#');
				if (hash < 0) return;
				JsonArray list = PrizeFile.array(tree, path.substring(0, hash));
				int index = Integer.parseInt(path.substring(hash + 1));
				if (index >= 0 && index < list.size() && list.get(index).isJsonObject() && list.get(index).getAsJsonObject().has("loot_table")) {
					list.remove(index);
				}
			}
			case "rm" -> {
				int dot = path.lastIndexOf('.');
				JsonArray list = PrizeFile.array(tree, path.substring(0, dot));
				int index = Integer.parseInt(path.substring(dot + 1));
				if (index >= 0 && index < list.size()) list.remove(index);
			}
			case "add" -> {
				JsonArray list = PrizeFile.array(tree, path);
				boolean words = path.startsWith("events");
				int[] ladder = words ? WORDS : TOTALS;
				int highest = 0;
				for (JsonElement mark : list) {
					JsonElement at = mark.isJsonObject() ? mark.getAsJsonObject().get(words ? "words" : "points") : null;
					if (at != null && at.isJsonPrimitive()) highest = Math.max(highest, at.getAsInt());
				}
				JsonObject mark = new JsonObject();
				mark.addProperty(words ? "words" : "points", step(ladder, highest, 1));
				mark.add("prizes", new JsonArray());
				list.add(mark);
			}
			default -> { }
		}
	}

	private static void picking(ServerPlayer player, MinecraftServer server, Map<String, String> data, String id, State state) {
		Pick pick = state.pick();
		switch (id) {
			case LootPicker.SEARCH -> {
				Pick typed = new Pick(pick.prizes(), data.getOrDefault("text", ""), 0);
				states.put(player.getUUID(), new State(state.tab(), state.scroll(), typed));
				refresh(player, server, typed);
				return;
			}
			case "loot_prev", "loot_next" -> {
				int pages = LootPicker.pages(LootPicker.matches(server, pick.query()));
				int page = Math.max(0, Math.min(pages - 1, pick.page() + (id.equals("loot_next") ? 1 : -1)));
				Pick turned = new Pick(pick.prizes(), pick.query(), page);
				states.put(player.getUUID(), new State(state.tab(), state.scroll(), turned));
				refresh(player, server, turned);
				return;
			}
			case "loot_cancel" -> {
				show(player, new State(state.tab(), state.scroll(), null));
				return;
			}
			default -> { }
		}
		if (!id.startsWith("loot:")) return;
		String table;
		try {
			table = LootPicker.at(server, pick.query(), pick.page(), Integer.parseInt(id.substring(5)));
		} catch (NumberFormatException e) {
			return;
		}
		if (table == null) return;
		change(server, tree -> {
			JsonObject entry = new JsonObject();
			entry.addProperty("loot_table", table);
			PrizeFile.array(tree, pick.prizes()).add(entry);
		});
		show(player, new State(state.tab(), state.scroll(), null));
	}

	private static void refresh(ServerPlayer player, MinecraftServer server, Pick pick) {
		String screen = screens.get(player.getUUID());
		if (screen != null) PandoricalApi.screens().update(player, screen, LootPicker.refresh(server, pick.query(), pick.page()));
	}

	private static String readerName(MinecraftServer server, java.util.UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) return online.getGameProfile().name();
		var entry = justfatlard.learning_blocks.classroom.Schedules.of(id);
		return entry == null ? id.toString() : entry.name();
	}

	// --- Reading the tree ---

	private static int[] ladderFor(String path) {
		if (path.equals("daily_cap")) return CAP;
		if (path.equals("puzzles.scene_every") || path.equals("subjects.block")) return SCENE_EVERY;
		if (path.startsWith("subjects.weights.")) return WEIGHTS;
		if (path.equals("puzzles.letter_word_every") || path.equals("puzzles.team_up_every")) return LETTER_WORD_EVERY;
		if (path.equals("puzzles.read_it_every") || path.equals("puzzles.tell_bob_every") || path.equals("puzzles.whats_wrong_every")) return SCENE_EVERY;
		if (path.equals("puzzles.review_share") || path.equals("puzzles.bob_prop_share")) return REVIEW_SHARE;
		if (path.endsWith(".points") && path.startsWith("points.thresholds")) return TOTALS;
		if (path.endsWith(".points")) return POINTS;
		if (path.equals("puzzles.mastery_clean")) return MASTERY;
		if (path.endsWith(".length")) return LENGTH;
		if (path.endsWith(".words")) return WORDS;
		return POINTS;
	}

	private static boolean defaultBool(String path) {
		return path.equals("enabled") || path.endsWith("first_try_only") || path.equals("puzzles.scenes") || path.equals("events.toy_mastered.enabled")
			|| path.equals("puzzles.letters_first") || path.equals("puzzles.first_sound") || path.equals("puzzles.last_sound")
			|| path.equals("puzzles.middle_vowel") || path.equals("puzzles.whole_word") || path.equals("puzzles.read_it")
			|| path.equals("puzzles.team_up") || path.equals("puzzles.tell_bob") || path.equals("puzzles.whats_wrong")
			|| path.startsWith("subjects.") && !path.equals("subjects.mix");
	}

	/** The next value along the ladder from this one, which need not be on it; itself at an end. */
	static int step(int[] ladder, int value, int by) {
		if (by > 0) {
			for (int rung : ladder) if (rung > value) return rung;
		} else {
			for (int i = ladder.length - 1; i >= 0; i--) if (ladder[i] < value) return ladder[i];
		}
		return value;
	}

	private static int xp(JsonArray list) {
		int total = 0;
		for (JsonElement entry : list) {
			if (entry.isJsonObject() && entry.getAsJsonObject().has("xp")) {
				try {
					total += entry.getAsJsonObject().get("xp").getAsInt();
				} catch (RuntimeException ignored) {
					// A bad value is the config's to report.
				}
			}
		}
		return total;
	}

	/** All of a list's experience as one entry, or none. */
	private static void setXp(JsonArray list, int points) {
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).isJsonObject() && list.get(i).getAsJsonObject().has("xp")) list.remove(i);
		}
		if (points > 0) {
			JsonObject entry = new JsonObject();
			entry.addProperty("xp", points);
			list.add(entry);
		}
	}

	private static boolean bool(JsonObject tree, String path, boolean fallback) {
		JsonElement found = PrizeFile.get(tree, path);
		try {
			return found != null && found.isJsonPrimitive() && found.getAsJsonPrimitive().isBoolean() ? found.getAsBoolean() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static int integer(JsonObject tree, String path, int fallback) {
		JsonElement found = PrizeFile.get(tree, path);
		try {
			return found != null && found.isJsonPrimitive() ? found.getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static String string(JsonObject tree, String path, String fallback) {
		JsonElement found = PrizeFile.get(tree, path);
		return found != null && found.isJsonPrimitive() ? found.getAsString() : fallback;
	}

	/** For the settings screen: a value straight from the file. */
	public static JsonObject tree() {
		try {
			return PrizeFile.read();
		} catch (Exception e) {
			return PrizeFile.defaults();
		}
	}

	public static boolean readBool(String path, boolean fallback) {
		return bool(tree(), path, fallback);
	}

	public static int readInt(String path, int fallback) {
		return integer(tree(), path, fallback);
	}

	public static String readString(String path, String fallback) {
		return string(tree(), path, fallback);
	}
}
