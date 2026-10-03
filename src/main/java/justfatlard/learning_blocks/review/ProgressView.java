package justfatlard.learning_blocks.review;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.editor.Ui;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.reward.Memory;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * A reader's progress, for the grown-ups: their stage; every letter and two-letter sound, and every
 * word, coloured by how well it is known (not tried yet, learning, shaky, solid); the sounds they mix
 * up most; their time and rounds this week; and their lessons. {@code /learn progress <player>}, or a
 * reader's name on the prize editor's General tab. {@code export} writes the same as JSON to
 * {@code config/learning-blocks/reports/<player>.json}, for looking at outside the game.
 */
public final class ProgressView {
	private ProgressView() {}

	public static final String TYPE = LearningBlocks.MOD_ID + ":progress";
	private static final int WIDTH = 316;
	private static final int PAD = Ui.PAD;
	private static final int INNER = WIDTH - 2 * PAD;
	private static final int UNIT = 12;
	private static final int VISIBLE = 17;
	private static final int TILE = 18;
	/** Roughly a character of the game's font, for wrapping a row of words. */
	private static final int CHAR = 6;

	private static final Map<Memory.Mastery, String> COLOUR = Map.of(
		Memory.Mastery.NOT_SEEN, "#FFE4E4E4",
		Memory.Mastery.LEARNING, "#FF6FA8DC",
		Memory.Mastery.SHAKY, "#FFE6B450",
		Memory.Mastery.SOLID, "#FF6ABE6A");
	private static final Map<Memory.Mastery, String> NAME = Map.of(
		Memory.Mastery.NOT_SEEN, "not tried", Memory.Mastery.LEARNING, "learning",
		Memory.Mastery.SHAKY, "shaky", Memory.Mastery.SOLID, "solid");

	/** The lines each viewer was last shown, for tests. */
	private static final Map<UUID, List<String>> shown = new HashMap<>();
	private static final Map<UUID, String> screens = new HashMap<>();

	public static void register(ScreenApi api) {
		api.onActionFallback(TYPE, (player, data) -> {
			String id = data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY);
			if ("close".equals(id)) {
				String open = screens.remove(player.getUUID());
				if (open != null) PandoricalApi.screens().close(player, open);
			}
		});
		api.onClose(TYPE, player -> screens.remove(player.getUUID()));
	}

	/** What this viewer was last shown, line by line: for tests. */
	public static List<String> lastShown(ServerPlayer viewer) {
		return shown.getOrDefault(viewer.getUUID(), List.of());
	}

	public static void forget(UUID player) {
		shown.remove(player);
		screens.remove(player);
	}

	public static void clear() {
		shown.clear();
		screens.clear();
	}

	/** The mix-ups, most often first, as a grown-up would say them. */
	public static List<String> confusionLines(Memory memory, int most) {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : memory.confusions()) {
			if (lines.size() >= most) break;
			String[] pair = entry.getKey().split("\\|");
			int times = entry.getValue();
			lines.add("mixes up " + pair[0] + " and " + pair[1] + ": " + times + (times == 1 ? " time" : " times"));
		}
		return lines;
	}

	/** Seconds and rounds over the last seven days. */
	private static int[] week(Memory memory) {
		java.time.LocalDate from = Progress.date().minusDays(6);
		int seconds = 0;
		int rounds = 0;
		for (Map.Entry<String, Memory.Day> day : memory.days().entrySet()) {
			try {
				if (java.time.LocalDate.parse(day.getKey()).isBefore(from)) continue;
			} catch (java.time.format.DateTimeParseException e) {
				continue;
			}
			seconds += day.getValue().seconds();
			rounds += day.getValue().rounds();
		}
		return new int[] {seconds, rounds};
	}

	private static List<Letter> tiles() {
		List<Letter> tiles = new ArrayList<>();
		for (Letter letter : Letter.values()) if (letter.isLetter() && !letter.isCapital()) tiles.add(letter);
		return tiles;
	}

	/** Open it for this viewer, about this reader. False without Pandorical on the viewer's game. */
	public static boolean open(ServerPlayer viewer, ServerPlayer reader) {
		if (!PandoricalApi.isAvailable(viewer)) return false;
		Progress progress = Progress.of(reader);
		Memory memory = progress.memory();
		List<String> lines = new ArrayList<>();
		List<ComponentDef> rows = new ArrayList<>();
		int[] unit = {0};
		java.util.function.Consumer<String> line = text -> {
			rows.add(Ui.text("line:" + unit[0], 2, unit[0] * UNIT + 2, text).build());
			lines.add(text);
			unit[0]++;
		};
		java.util.function.Consumer<String> heading = text -> {
			unit[0]++;
			rows.add(Ui.text("head:" + unit[0], 2, unit[0] * UNIT + 2, "§l" + text).build());
			lines.add(text);
			unit[0]++;
		};

		line.accept(Stages.describe(progress));
		int[] week = week(memory);
		line.accept("This week: " + (week[0] + 59) / 60 + " minutes, " + week[1] + " rounds");
		int required = justfatlard.learning_blocks.classroom.Schedules.requiredToday(reader);
		justfatlard.learning_blocks.classroom.Schedules.Entry schedule = justfatlard.learning_blocks.classroom.Schedules.of(reader.getUUID());
		if (schedule != null && schedule.enabled()) {
			line.accept("Today: " + progress.doneToday() + " of " + required + " lessons done (" + schedule.lessons() + " a day"
				+ (justfatlard.learning_blocks.classroom.Schedules.weekendsOff(reader.getUUID()) ? ", weekends off" : "") + ")");
		} else line.accept("Today: " + progress.doneToday() + " rounds solved; no daily schedule");
		StringBuilder days = new StringBuilder("Solved:");
		java.time.LocalDate today = Progress.date();
		for (int back = 6; back >= 0; back--) {
			java.time.LocalDate day = today.minusDays(back);
			days.append(' ').append(day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH))
				.append(' ').append(memory.solvedOn(day.toString()));
		}
		line.accept(days.toString());

		// The key, one swatch a colour.
		int x = 2;
		for (Memory.Mastery mastery : Memory.Mastery.values()) {
			rows.add(swatch("key:" + mastery, x, unit[0] * UNIT + 1, 9, COLOUR.get(mastery)));
			rows.add(Ui.faint("keytext:" + mastery, x + 11, unit[0] * UNIT + 2, NAME.get(mastery)).build());
			x += 18 + NAME.get(mastery).length() * CHAR;
		}
		unit[0]++;

		int lettersMastered = 0;
		for (Letter letter : tiles()) if (progress.letterHits(letter.grapheme()) >= Progress.masteredAt()) lettersMastered++;
		heading.accept("Letters and sounds: " + lettersMastered + " of " + tiles().size() + " mastered");
		List<Letter> tiles = tiles();
		int perRow = INNER / (TILE + 2);
		for (int i = 0; i < tiles.size(); i++) {
			Letter letter = tiles.get(i);
			int tx = (i % perRow) * (TILE + 2);
			int ty = (unit[0] + (i / perRow) * 2) * UNIT;
			Memory.Mastery mastery = progress.letterHits(letter.grapheme()) >= Progress.masteredAt() ? Memory.Mastery.SOLID
				: memory.mastery(Memory.tileKey(letter.grapheme()), Progress.masteredAt());
			rows.add(swatch("tile:" + letter.grapheme(), tx, ty, TILE, COLOUR.get(mastery)));
			rows.add(new ComponentBuilder("tiletext:" + letter.grapheme(), ComponentType.TEXT).bounds(tx, ty + 5, TILE, 10)
				.prop(ComponentType.PROP_TEXT, letter.grapheme()).prop(ComponentType.PROP_ALIGN, "center")
				.prop(ComponentType.PROP_COLOR, Ui.INK).build());
		}
		unit[0] += ((tiles.size() + perRow - 1) / perRow) * 2;

		int casesMastered = 0;
		for (Letter letter : justfatlard.learning_blocks.puzzle.Letters.all()) if (justfatlard.learning_blocks.puzzle.Letters.caseHits(progress, letter) >= Progress.masteredAt()) casesMastered++;
		heading.accept("Big and little: " + casesMastered + " of " + justfatlard.learning_blocks.puzzle.Letters.all().size() + " mastered"
			+ (progress.casesWaived() ? " (letters level done before capitals)" : ""));
		List<Letter> caps = justfatlard.learning_blocks.puzzle.Letters.all();
		for (int i = 0; i < caps.size(); i++) {
			Letter letter = caps.get(i);
			int tx = (i % perRow) * (TILE + 2);
			int ty = (unit[0] + (i / perRow) * 2) * UNIT;
			int hits = justfatlard.learning_blocks.puzzle.Letters.caseHits(progress, letter);
			Memory.Mastery mastery = hits >= Progress.masteredAt() ? Memory.Mastery.SOLID
				: memory.mastery(justfatlard.learning_blocks.puzzle.Letters.caseKey(letter), Progress.masteredAt());
			rows.add(swatch("case:" + letter.grapheme(), tx, ty, TILE, COLOUR.get(mastery)));
			rows.add(new ComponentBuilder("casetext:" + letter.grapheme(), ComponentType.TEXT).bounds(tx, ty + 5, TILE, 10)
				.prop(ComponentType.PROP_TEXT, letter.upper().shown() + letter.shown()).prop(ComponentType.PROP_ALIGN, "center")
				.prop(ComponentType.PROP_COLOR, Ui.INK).build());
		}
		unit[0] += ((caps.size() + perRow - 1) / perRow) * 2;
		lines.add("big and little mastered: " + casesMastered);

		heading.accept("Mixes up");
		List<String> confusions = confusionLines(memory, 5);
		if (confusions.isEmpty()) line.accept("nothing mixed up yet");
		for (String text : confusions) line.accept(text);

		heading.accept("Words");
		// By band, 1a, 1b, 1c: the parts of a stage a child is gated through.
		for (Stages.Band band : Stages.bands()) {
			List<Word> words = new ArrayList<>(band.words());
			words.sort((a, b) -> a.text().compareTo(b.text()));
			long mastered = words.stream().filter(progress::mastered).count();
			String label = "Stage " + band.label() + " (" + mastered + "/" + words.size() + "):";
			rows.add(Ui.faint("stage:" + band.code(), 2, unit[0] * UNIT + 2, label).build());
			lines.add(label);
			int wx = 2 + (label.length() + 1) * CHAR;
			for (Word word : words) {
				String shownText = word.text() + " " + Math.min(progress.clean(word), Progress.masteredAt()) + "/" + Progress.masteredAt();
				int width = (shownText.length() + 1) * CHAR;
				if (wx + width > INNER) {
					unit[0]++;
					wx = 2 + 6 * CHAR;
				}
				Memory.Mastery mastery = memory.mastery(Memory.wordKey(word.id().toString()), Progress.masteredAt());
				if (mastery == Memory.Mastery.NOT_SEEN && progress.words().contains(word.id().toString())) mastery = Memory.Mastery.LEARNING;
				rows.add(Ui.text("word:" + word.id(), wx, unit[0] * UNIT + 2, shownText)
					.prop(ComponentType.PROP_COLOR, darker(COLOUR.get(mastery))).build());
				wx += width;
			}
			unit[0]++;
		}

		// Sentences told to Bob, by goal: clean days towards mastery, as words are.
		List<justfatlard.learning_blocks.bob.Goal> goals = justfatlard.learning_blocks.bob.Goals.all();
		long sentencesMastered = goals.stream().filter(goal -> progress.sentenceMastered(goal.key())).count();
		long sentencesSeen = goals.stream().filter(goal -> memory.item(Progress.sentenceKey(goal.key())) != null).count();
		heading.accept("Sentences: " + sentencesMastered + " of " + goals.size() + " mastered, " + sentencesSeen + " tried");
		for (justfatlard.learning_blocks.bob.Goal goal : goals) {
			Memory.Item item = memory.item(Progress.sentenceKey(goal.key()));
			if (item == null) continue;
			Memory.Mastery mastery = memory.mastery(Progress.sentenceKey(goal.key()), Progress.masteredAt());
			String text = goal.key() + "  " + Math.min(progress.sentenceClean(goal.key()), Progress.masteredAt()) + "/" + Progress.masteredAt()
				+ (item.misses() > 0 ? ", " + item.misses() + (item.misses() == 1 ? " miss" : " misses") : "");
			rows.add(Ui.text("sentence:" + goal.id(), 2, unit[0] * UNIT + 2, text).prop(ComponentType.PROP_COLOR, darker(COLOUR.get(mastery))).build());
			lines.add(text);
			unit[0]++;
		}
		if (sentencesSeen == 0) line.accept("no sentences yet (Bob comes at stage 5)");

		heading.accept("Lessons");
		List<String> lessons = memory.lessons();
		if (lessons.isEmpty()) line.accept("no lessons yet");
		for (int i = lessons.size() - 1; i >= 0 && i >= lessons.size() - 8; i--) line.accept(lessons.get(i));

		int listY = 22;
		int height = listY + VISIBLE * UNIT + 30;
		ScreenBuilder screen = new ScreenBuilder(TYPE).title(reader.getGameProfile().name() + "'s progress").pauseGame(false);
		screen.size(WIDTH, height);
		screen.panel("dialog", 0, 0, WIDTH, height, Ui.panel());
		screen.component(Ui.icon("title_icon", PAD, 3, "minecraft:writable_book", 0.75F));
		screen.component(Ui.text("title", PAD + 16, 7, reader.getGameProfile().name() + "'s progress"));
		screen.component(Ui.button("close", WIDTH - PAD - 44, height - 24, 44, Ui.BUTTON - 2, "Done"));
		screen.component(Ui.faint("export_hint", PAD, height - 18, "/learn progress " + reader.getGameProfile().name() + " export"));
		screen.scrollPanel("progress", PAD, listY, INNER, VISIBLE * UNIT, Map.of(
			ComponentType.PROP_ITEM_HEIGHT, String.valueOf(UNIT),
			ComponentType.PROP_VISIBLE_ITEMS, String.valueOf(VISIBLE),
			ComponentType.PROP_TOTAL_ITEMS, String.valueOf(Math.max(unit[0], 1)),
			ComponentType.PROP_SHOW_SCROLLBAR, String.valueOf(unit[0] > VISIBLE),
			ComponentType.PROP_SCROLL_OFFSET, "0",
			ComponentType.PROP_BACKGROUND, "#00000000"), rows);
		shown.put(viewer.getUUID(), List.copyOf(lines));
		screens.put(viewer.getUUID(), screen.screenId());
		PandoricalApi.screens().open(viewer, screen.build());
		return true;
	}

	private static ComponentDef swatch(String id, int x, int y, int size, String colour) {
		return new ComponentBuilder(id, ComponentType.PANEL).bounds(x, y, size, size).prop(ComponentType.PROP_BACKGROUND, colour)
			.prop(ComponentType.PROP_BORDER, "flat").prop(ComponentType.PROP_BORDER_COLOR, "#FF555555").build();
	}

	/** The same colour darkened, to be read as words on the light panel. */
	private static String darker(String colour) {
		int argb = (int) Long.parseLong(colour.substring(1), 16);
		int r = (argb >> 16 & 0xFF) * 3 / 5;
		int g = (argb >> 8 & 0xFF) * 3 / 5;
		int b = (argb & 0xFF) * 3 / 5;
		return String.format("#FF%02X%02X%02X", r, g, b);
	}

	/** The same, as JSON under config/learning-blocks/reports; the file written, or null if it could not be. */
	public static @Nullable Path export(ServerPlayer reader) {
		Progress progress = Progress.of(reader);
		Memory memory = progress.memory();
		JsonObject root = new JsonObject();
		root.addProperty("player", reader.getGameProfile().name());
		root.addProperty("stage", Stages.describe(progress));
		root.addProperty("rounds", memory.rounds());
		int[] week = week(memory);
		JsonObject weekJson = new JsonObject();
		weekJson.addProperty("seconds", week[0]);
		weekJson.addProperty("rounds", week[1]);
		root.add("this_week", weekJson);
		root.addProperty("mastery_clean", Progress.masteredAt());
		JsonObject today = new JsonObject();
		today.addProperty("date", Progress.today());
		today.addProperty("done", progress.doneToday());
		today.addProperty("required", justfatlard.learning_blocks.classroom.Schedules.requiredToday(reader));
		root.add("today", today);
		JsonObject days = new JsonObject();
		for (Map.Entry<String, Memory.Day> day : memory.days().entrySet()) {
			JsonObject d = new JsonObject();
			d.addProperty("seconds", day.getValue().seconds());
			d.addProperty("rounds", day.getValue().rounds());
			d.addProperty("solved", day.getValue().solved());
			days.add(day.getKey(), d);
		}
		root.add("days", days);
		JsonObject sounds = new JsonObject();
		for (Letter letter : tiles()) {
			JsonObject sound = new JsonObject();
			sound.addProperty("clean", progress.letterHits(letter.grapheme()));
			sound.addProperty("mastery", NAME.get(progress.letterHits(letter.grapheme()) >= Progress.masteredAt() ? Memory.Mastery.SOLID
				: memory.mastery(Memory.tileKey(letter.grapheme()), Progress.masteredAt())));
			sounds.add(letter.grapheme(), sound);
		}
		root.add("sounds", sounds);
		JsonObject items = new JsonObject();
		for (Map.Entry<String, Memory.Item> entry : memory.items().entrySet()) {
			Memory.Item item = entry.getValue();
			JsonObject json = new JsonObject();
			json.addProperty("mastery", NAME.get(memory.mastery(entry.getKey(), Progress.masteredAt())));
			json.addProperty("attempts", item.attempts());
			json.addProperty("first_try", item.firstTry());
			json.addProperty("misses", item.misses());
			json.addProperty("box", item.box());
			JsonArray mixups = new JsonArray();
			item.mixups().forEach(mixups::add);
			json.add("built_instead", mixups);
			items.add(entry.getKey(), json);
		}
		root.add("items", items);
		JsonArray confusions = new JsonArray();
		for (Map.Entry<String, Integer> entry : memory.confusions()) {
			JsonObject json = new JsonObject();
			json.addProperty("pair", entry.getKey().replace('|', '/'));
			json.addProperty("times", entry.getValue());
			confusions.add(json);
		}
		root.add("mix_ups", confusions);
		JsonArray read = new JsonArray();
		progress.wordsInOrder().forEach(read::add);
		root.add("words_read", read);
		JsonArray lessons = new JsonArray();
		memory.lessons().forEach(lessons::add);
		root.add("lessons", lessons);
		Path file = FabricLoader.getInstance().getConfigDir().resolve("learning-blocks").resolve("reports")
			.resolve(reader.getGameProfile().name() + ".json");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
			return file;
		} catch (IOException e) {
			LearningBlocks.LOGGER.error("[{}] Could not write {}'s progress to {}", LearningBlocks.MOD_ID, reader.getGameProfile().name(), file, e);
			return null;
		}
	}
}
