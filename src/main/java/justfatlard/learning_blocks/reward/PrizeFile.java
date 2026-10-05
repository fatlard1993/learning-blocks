package justfatlard.learning_blocks.reward;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/learning-blocks/prizes.json} as a tree: read, changed, and written back with its comments.
 *
 * <p>The file is the one source of truth. The in-game editor changes the tree and writes it, and
 * the config is then read back from the file exactly as {@code /learn reload} reads it, so the
 * menu can only ever show what the file says. Comments are the mod's own, kept here by path and
 * written beside the settings they describe every time; a comment somebody adds by hand is lost at
 * the next save from the menu, and the settings they changed are not.
 *
 * <p>Entries the config skips - an item from a mod that is not installed - stay in the tree and in
 * the file, so a mod taken out for a while does not cost anybody their prize list.
 */
public final class PrizeFile {
	private PrizeFile() {}

	private static final Gson GSON = new Gson();

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("learning-blocks").resolve("prizes.json");
	}

	/** The file's tree, writing the defaults first if there is no file. */
	public static JsonObject read() throws IOException {
		Path path = path();
		if (!Files.exists(path)) write(defaults());
		return parse(Files.readString(path, StandardCharsets.UTF_8));
	}

	public static JsonObject parse(String text) {
		JsonReader reader = new JsonReader(new StringReader(text));
		reader.setStrictness(Strictness.LENIENT);
		JsonElement root = JsonParser.parseReader(reader);
		if (!root.isJsonObject()) throw new IllegalArgumentException("the file is not a JSON object");
		return root.getAsJsonObject();
	}

	public static void write(JsonObject tree) throws IOException {
		dropWordBook(tree);
		Path path = path();
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling("prizes.json.tmp");
		Files.writeString(temporary, text(tree), StandardCharsets.UTF_8);
		Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}

	/** Everything the file holds when nobody has touched it. */
	public static JsonObject defaults() {
		return parse(DEFAULT_VALUES);
	}

	// --- Paths: "events.new_word.prizes.2" ---

	/** What is at this path, or null. */
	public static @Nullable JsonElement get(JsonObject tree, String path) {
		JsonElement at = tree;
		for (String part : path.split("\\.")) {
			if (at == null) return null;
			if (at.isJsonObject()) at = at.getAsJsonObject().get(part);
			else if (at.isJsonArray()) {
				int index = index(part);
				JsonArray array = at.getAsJsonArray();
				at = index >= 0 && index < array.size() ? array.get(index) : null;
			} else return null;
		}
		return at;
	}

	/** Put this at the path, making the objects on the way if they are missing. */
	public static void set(JsonObject tree, String path, JsonElement value) {
		String[] parts = path.split("\\.");
		JsonElement at = tree;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonElement next = child(at, parts[i]);
			if (next == null || next.isJsonNull() || next.isJsonPrimitive()) {
				next = new JsonObject();
				if (at.isJsonObject()) at.getAsJsonObject().add(parts[i], next);
				else return;
			}
			at = next;
		}
		String last = parts[parts.length - 1];
		if (at.isJsonObject()) at.getAsJsonObject().add(last, value);
		else if (at.isJsonArray()) {
			int index = index(last);
			if (index >= 0 && index < at.getAsJsonArray().size()) at.getAsJsonArray().set(index, value);
		}
	}

	/** The array at this path, made empty there if missing. */
	public static JsonArray array(JsonObject tree, String path) {
		JsonElement found = get(tree, path);
		if (found != null && found.isJsonArray()) return found.getAsJsonArray();
		JsonArray made = new JsonArray();
		set(tree, path, made);
		return made;
	}

	private static @Nullable JsonElement child(JsonElement at, String part) {
		if (at.isJsonObject()) return at.getAsJsonObject().get(part);
		if (at.isJsonArray()) {
			int index = index(part);
			return index >= 0 && index < at.getAsJsonArray().size() ? at.getAsJsonArray().get(index) : null;
		}
		return null;
	}

	private static int index(String part) {
		try {
			return Integer.parseInt(part);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	// --- Writing, with comments ---

	static String text(JsonObject tree) {
		StringBuilder out = new StringBuilder();
		for (String line : HEADER.split("\n")) out.append("// ").append(line).append('\n');
		out.append('\n');
		object(out, tree, "", 0);
		out.append('\n');
		return out.toString().replace("// \n", "//\n");
	}

	private static void object(StringBuilder out, JsonObject object, String path, int depth) {
		out.append("{\n");
		int i = 0;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String here = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
			String comment = COMMENTS.get(here);
			if (comment != null) {
				if (i > 0) out.append('\n');
				for (String line : comment.split("\n")) indent(out, depth + 1).append("// ").append(line).append('\n');
			}
			indent(out, depth + 1).append(GSON.toJson(entry.getKey())).append(": ");
			value(out, entry.getValue(), here, depth + 1);
			if (++i < object.size()) out.append(',');
			out.append('\n');
		}
		indent(out, depth).append('}');
	}

	private static void value(StringBuilder out, JsonElement value, String path, int depth) {
		if (value.isJsonObject()) {
			String compact = GSON.toJson(value);
			if (compact.length() <= 70 && !COMMENTED_OBJECTS.contains(path.replaceAll("\\.\\d+", ""))) {
				out.append(spaced(compact));
				return;
			}
			object(out, value.getAsJsonObject(), path, depth);
		} else if (value.isJsonArray()) {
			JsonArray array = value.getAsJsonArray();
			if (array.isEmpty()) {
				out.append("[]");
				return;
			}
			String compact = spaced(GSON.toJson(array));
			if (compact.length() <= 60) {
				out.append(compact);
				return;
			}
			out.append("[\n");
			for (int i = 0; i < array.size(); i++) {
				indent(out, depth + 1);
				value(out, array.get(i), path + "." + i, depth + 1);
				if (i + 1 < array.size()) out.append(',');
				out.append('\n');
			}
			indent(out, depth).append(']');
		} else {
			out.append(value instanceof JsonPrimitive ? GSON.toJson(value) : "null");
		}
	}

	/** The word book is gone: its setting and any word book prize are left out of what is written. */
	private static void dropWordBook(JsonElement json) {
		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			object.remove("word_book");
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) dropWordBook(entry.getValue());
		} else if (json.isJsonArray()) {
			JsonArray list = json.getAsJsonArray();
			for (int i = list.size() - 1; i >= 0; i--) {
				JsonElement entry = list.get(i);
				if (entry.isJsonObject() && entry.getAsJsonObject().has("word_book")) list.remove(i);
				else dropWordBook(entry);
			}
		}
	}

	/** Gson's compact form, with a space after each colon and comma, the way a person writes it. */
	private static String spaced(String compact) {
		StringBuilder out = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < compact.length(); i++) {
			char c = compact.charAt(i);
			if (c == '"' && (i == 0 || compact.charAt(i - 1) != '\\')) quoted = !quoted;
			out.append(c);
			if (!quoted && (c == ':' || c == ',')) out.append(' ');
			if (!quoted && (c == '{' || c == '[')) out.append(' ');
			if (!quoted && i + 1 < compact.length() && (compact.charAt(i + 1) == '}' || compact.charAt(i + 1) == ']')) out.append(' ');
		}
		return out.toString().replace("{ }", "{}").replace("[ ]", "[]");
	}

	private static StringBuilder indent(StringBuilder out, int depth) {
		return out.append("  ".repeat(depth));
	}

	private static final String HEADER = """
		Learning Blocks prizes: what learning earns, handed over at home when a child leaves the classroom.
		The rest of the mod's server settings are here too: puzzles (kinds of round) and subjects.

		Nothing here goes into the classroom's own inventory. Everything earned waits with the
		things put aside at the door and is handed back after them; whatever does not fit is
		dropped at the child's feet at home.

		A prize is one of:
		  { "item": "minecraft:cookie", "count": 3 }                 items; "components" optional
		  { "item": "minecraft:paper", "components": { "minecraft:custom_name": "A gold star" } }
		  { "xp": 10 }                                               experience points
		  { "loot_table": "minecraft:gameplay/cat_morning_gift" }    one roll of a loot table
		  { "plush": "random_new" }                                  a plushie the child hasn't got yet
		  { "plush": "owl" }                                         that plushie
		  { "toy": "random_new" }                                    a toy the child hasn't got yet
		  { "toy": "drum" }                                          that toy
		  { "toybox": "random_new" }                                 a plushie or a toy they haven't got
		Anything naming an item or loot table this server has not got is skipped, with a line in
		the log, and kept in this file. There are no command prizes.

		Every setting here can also be changed in game: /learn prizes (operators only). The menu
		writes this file as it goes, comments and all; /learn reload reads a hand edit.""";

	/** Objects written out in full even when short, because their fields carry comments. */
	private static final java.util.Set<String> COMMENTED_OBJECTS = java.util.Set.of(
		"events", "points", "events.new_word", "events.solved", "events.streak", "events.stage_complete",
		"events.milestone", "events.toy_mastered", "events.daily_visit", "puzzles");

	private static final Map<String, String> COMMENTS = Map.ofEntries(
		Map.entry("enabled", "Everything below, on or off at once."),
		Map.entry("daily_cap", """
			At most this many payouts per child per real day, counting every event and threshold
			that pays (20 by default, so a long day of streaks can't pay without end). 0 for no cap. A payout over the cap is not lost: it is owed, and paid on a
			later day, oldest first, within that day's cap."""),
		Map.entry("events.new_word", """
			Learned something new: the first time a child ever gets a thing right in any subject (a
			word, a letter, a numeral, a pattern). Pays once for each thing, ever, so guessing every
			answer in turn earns nothing more."""),
		Map.entry("events.solved", "Got one right, in any subject. first_try_only: only when nothing wrong was tried this round first."),
		Map.entry("events.streak", "Every \"length\" right in a row, in any subject. A wrong answer starts the count over."),
		Map.entry("events.stage_complete", """
			A part of the course finished: every word of a Reading band (1a, 1b...) mastered, the
			letters level, or a Numbers or Logic stage (N0, L0...). Pays once for each."""),
		Map.entry("events.milestone", """
			Totals of things learned in every subject: words read, and Numbers' and Logic's things
			got right. ("words" is the key's name; it counts them all.) Each pays once."""),
		Map.entry("events.toy_mastered", """
			A plushie or toy earned by mastering every word that brings it (owl by owl, the teddy by
			hug, the car by car): the toy itself, once, ever, and these prizes as well. On by default:
			this is how the collection fills."""),
		Map.entry("events.daily_visit", "The first visit of each real day."),
		Map.entry("puzzles", """
			How rounds are asked. scenes: now and then, instead of "Can you make...", the room acts
			out a little problem (a villager in the rain) and the child works out which word helps.
			Only words already read are asked this way. scene_every: one round in this many.
			letters_first: a new reader starts on the letters level (stage 0): hear a sound, find its
			letter, before any words. Readers from before are left where they are. mastery_clean
			(1 to 10): clean completions - right first time, nothing wrong built or picked before - that
			master a word, a letter or a two-letter tile; a stage (or a group of letters) opens the next
			only when everything in it is mastered. Misses neither add nor take away.
			letter_word_every: once s a t p i n are known, one letters round in this many is a word
			built only from letters the child knows (sat, tap, sip); 0 for none.
			read_it: from stage 2, now and then a word already read is shown whole, not said, and the
			child picks its picture from two or three that sound alike; read_it_every: one in this many.
			review_share: out of 100, how often a round is a word or letter due for another look (one
			missed lately, or never got first try) rather than the usual pick.
			first_sound, last_sound, middle_vowel: the kinds of word round, each on or off. First sound
			swaps the first tile (_at: cat, bat, hat); last sound, once most of a stage is read, the
			last (ma_: map, mat, man); middle vowel, from stage 2, the vowel (c_t: cat, cot, cut).
			whole_word: from stage 6, every slot a gap: the word built from its own tiles and a
			decoy or two the child knows. Words with no family are asked this way too.
			team_up: when two read together, now and then both work on one word, each with a gap of
			their own; team_up_every: one round in this many (0 for none).
			tell_bob: from stage 5, now and then the child tells Bob, a very literal helper, what to
			do, with word cards (put the cat in the box), and he does exactly what it says;
			tell_bob_every: one round in this many (0 for none). whats_wrong: what's-wrong rounds,
			Bob's sentence already made and acted out wrong, one card to fix (from stage 5), and a
			word on the board with one wrong letter (from stage 2); whats_wrong_every: one round in
			this many is a word to fix. silly_counts: whether a silly sentence told to Bob (Silly
			sentences, from the action menu) counts as a lesson; never towards mastery.
			bob_prop_share: out of 100, at most how often Bob's goal is one with only props in it (a
			mug on a desk) rather than a plushie or a toy; never two of those in a row."""),
		Map.entry("subjects", """
			Subjects: reading, numbers and logic, each on (true) or off by default for a child nobody
			has set them for (/learn subjects sets one child's). mix: how a child's subjects take
			turns: alternate (one round of each in turn, skipping any with nothing to ask), blocks
			(block rounds of one, then the next) or weights (each by chance, as often as its weight
			against the others). A daily schedule asking for some of each comes first."""),
		Map.entry("points", """
			Points add up from the events above that give them. Each threshold pays once, the
			first time the total reaches it. Off by default."""));

	private static final String DEFAULT_VALUES = """
		{
		  "enabled": true,
		  "daily_cap": 20,
		  "events": {
		    "new_word": { "enabled": true, "points": 0, "prizes": [ { "item": "minecraft:cookie", "count": 1 } ] },
		    "solved": { "enabled": false, "first_try_only": true, "points": 1, "prizes": [] },
		    "streak": { "enabled": true, "length": 3, "points": 0, "prizes": [ { "xp": 10 } ] },
		    "stage_complete": { "enabled": true, "points": 0, "prizes": [ { "item": "minecraft:emerald", "count": 1 } ] },
		    "milestone": { "enabled": true, "every": [
		      { "words": 10, "prizes": [ { "item": "minecraft:emerald", "count": 3 } ] },
		      { "words": 25, "prizes": [ { "item": "minecraft:diamond", "count": 1 } ] },
		      { "words": 100, "prizes": [ { "item": "minecraft:diamond", "count": 5 } ] }
		    ] },
		    "toy_mastered": { "enabled": true, "points": 0, "prizes": [] },
		    "daily_visit": { "enabled": false, "points": 0, "prizes": [ { "item": "minecraft:apple", "count": 1 } ] }
		  },
		  "points": { "enabled": false, "thresholds": [
		    { "points": 10, "prizes": [ { "item": "minecraft:gold_ingot", "count": 1 } ] },
		    { "points": 50, "prizes": [ { "item": "minecraft:diamond", "count": 1 } ] }
		  ] },
		  "puzzles": { "scenes": true, "scene_every": 4, "letters_first": true, "mastery_clean": 3, "letter_word_every": 3, "read_it": true, "read_it_every": 5, "review_share": 30,
		    "first_sound": true, "last_sound": true, "middle_vowel": true, "whole_word": true, "team_up": true, "team_up_every": 3,
		    "tell_bob": true, "tell_bob_every": 5, "whats_wrong": true, "whats_wrong_every": 6, "silly_counts": false, "bob_prop_share": 20 },
		  "subjects": { "reading": true, "numbers": true, "logic": true, "mix": "alternate", "block": 3,
		    "weights": { "reading": 1, "numbers": 1, "logic": 1 } }
		}
		""";

	/** Names of the events, in the order the file and the editor show them. */
	public static final List<String> EVENTS = List.of("new_word", "solved", "streak", "stage_complete", "milestone", "toy_mastered", "daily_visit");
}
