package justfatlard.learning_blocks.reward;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * What learning earns, from {@code config/learning-blocks/prizes.json}.
 *
 * <p>A server's, not a world's, like PvP Dimensions' presets: written with every setting and a
 * comment beside it the first time the server starts, read at every start, and read again by
 * {@code /learn reload}. The file is JSON with comments, read leniently, so the comments can stay.
 *
 * <p>Nothing in it can stop the server. A file that will not parse at all keeps whatever was loaded
 * before (or nothing, the first time) and says so; an entry that is wrong on its own - an item this
 * server has not got, a count that is not a number - is left out and named in the log, and the rest
 * of the file goes on working.
 */
public final class PrizeConfig {


	/** One event: whether it pays, what in points and what in prizes. */
	public record Event(boolean enabled, int points, List<Prize> prizes) {
		static final Event OFF = new Event(false, 0, List.of());
	}

	/** A total of distinct words, or of points, and what reaching it pays once. */
	public record Mark(int at, List<Prize> prizes) {}

	public final boolean enabled;
	public final int dailyCap;
	public final Event newWord;
	public final Event solved;
	public final boolean solvedFirstTryOnly;
	public final Event streak;
	public final int streakLength;
	public final Event stageComplete;
	public final boolean milestonesEnabled;
	public final List<Mark> milestones;
	public final Event dailyVisit;
	public final boolean pointsEnabled;
	public final List<Mark> thresholds;
	/**
	 * Situation puzzles: now and then, instead of asking for a word, the room acts out a problem one
	 * of the child's read words fixes. Not a prize, so the prizes' on-off leaves it alone.
	 */
	public final boolean scenes;
	/** One round in this many is a scene, once there is a read word with one. */
	public final int sceneEvery;
	/** New readers start on the letters level, stage 0, before any words. */
	public final boolean lettersFirst;
	/**
	 * Clean completions - solved first try, with nothing wrong built or picked before - that make a
	 * word, a letter or a two-letter tile mastered. Misses neither add nor take away.
	 */
	public final int masteryClean;
	/**
	 * In the letters level, once the first group is known: one round in this many is a word built
	 * only from letters the child knows; 0 for none.
	 */
	public final int letterWordEvery;
	/** "Read it" rounds: a word already read, shown whole, and its picture picked from two or three; from stage 2. */
	public final boolean readIt;
	/** One round in this many is a read-it round, once there is one to ask. */
	public final int readItEvery;
	/** Out of 100: how often a round is a word or letter due for review rather than the usual pick. */
	public final int reviewShare;
	/** The kinds of word round: the first sound swapped, the last, the vowel in the middle. */
	public final boolean firstSound;
	public final boolean lastSound;
	public final boolean middleVowel;
	/** Whole-word rounds: every slot a gap, the word built from its tiles and a decoy or two. */
	public final boolean wholeWord;

	private PrizeConfig(boolean enabled, int dailyCap, Event newWord, Event solved, boolean solvedFirstTryOnly,
			Event streak, int streakLength, Event stageComplete, boolean milestonesEnabled, List<Mark> milestones, Event dailyVisit,
			boolean pointsEnabled, List<Mark> thresholds, boolean scenes, int sceneEvery, boolean lettersFirst, int masteryClean,
			int letterWordEvery, boolean readIt, int readItEvery, int reviewShare, boolean firstSound, boolean lastSound, boolean middleVowel, boolean wholeWord) {
		this.enabled = enabled;
		this.dailyCap = dailyCap;
		this.newWord = newWord;
		this.solved = solved;
		this.solvedFirstTryOnly = solvedFirstTryOnly;
		this.streak = streak;
		this.streakLength = streakLength;
		this.stageComplete = stageComplete;
		this.milestonesEnabled = milestonesEnabled;
		this.milestones = List.copyOf(milestones);
		this.dailyVisit = dailyVisit;
		this.pointsEnabled = pointsEnabled;
		this.thresholds = List.copyOf(thresholds);
		this.scenes = scenes;
		this.sceneEvery = sceneEvery;
		this.lettersFirst = lettersFirst;
		this.masteryClean = masteryClean;
		this.letterWordEvery = letterWordEvery;
		this.readIt = readIt;
		this.readItEvery = readItEvery;
		this.reviewShare = reviewShare;
		this.firstSound = firstSound;
		this.lastSound = lastSound;
		this.middleVowel = middleVowel;
		this.wholeWord = wholeWord;
	}

	/** The same, with whole-word rounds on or off: for tests. */
	public static void useWholeWord(boolean on) {
		PrizeConfig c = current;
		current = new PrizeConfig(c.enabled, c.dailyCap, c.newWord, c.solved, c.solvedFirstTryOnly, c.streak, c.streakLength,
			c.stageComplete, c.milestonesEnabled, c.milestones, c.dailyVisit, c.pointsEnabled, c.thresholds, c.scenes, c.sceneEvery,
			c.lettersFirst, c.masteryClean, c.letterWordEvery, c.readIt, c.readItEvery, c.reviewShare, c.firstSound, c.lastSound, c.middleVowel, on);
	}

	/** The same, with these kinds of round on: for tests. */
	public static void useKinds(boolean first, boolean last, boolean middle) {
		PrizeConfig c = current;
		current = new PrizeConfig(c.enabled, c.dailyCap, c.newWord, c.solved, c.solvedFirstTryOnly, c.streak, c.streakLength,
			c.stageComplete, c.milestonesEnabled, c.milestones, c.dailyVisit, c.pointsEnabled, c.thresholds, c.scenes, c.sceneEvery,
			c.lettersFirst, c.masteryClean, c.letterWordEvery, c.readIt, c.readItEvery, c.reviewShare, first, last, middle, c.wholeWord);
	}

	/** The same, with a word round in the letters level every this many rounds (0 for none): for tests. */
	public static void useLetterWords(int every) {
		PrizeConfig c = current;
		current = new PrizeConfig(c.enabled, c.dailyCap, c.newWord, c.solved, c.solvedFirstTryOnly, c.streak, c.streakLength,
			c.stageComplete, c.milestonesEnabled, c.milestones, c.dailyVisit, c.pointsEnabled, c.thresholds, c.scenes, c.sceneEvery,
			c.lettersFirst, c.masteryClean, Math.max(0, every), c.readIt, c.readItEvery, c.reviewShare, c.firstSound, c.lastSound, c.middleVowel, c.wholeWord);
	}

	/** The same, with read-it rounds and review set so: for tests. */
	public static void useReadIt(boolean on, int every, int reviewShare) {
		PrizeConfig c = current;
		current = new PrizeConfig(c.enabled, c.dailyCap, c.newWord, c.solved, c.solvedFirstTryOnly, c.streak, c.streakLength,
			c.stageComplete, c.milestonesEnabled, c.milestones, c.dailyVisit, c.pointsEnabled, c.thresholds, c.scenes, c.sceneEvery,
			c.lettersFirst, c.masteryClean, c.letterWordEvery, on, Math.max(1, every), reviewShare, c.firstSound, c.lastSound, c.middleVowel, c.wholeWord);
		extrasOff = false;
	}

	/**
	 * Tests that walk through rounds expect the round they asked for: with this on, read-it rounds
	 * and review picks are off whatever the file says, until a test turns them on with {@link #useReadIt}.
	 */
	private static volatile boolean extrasOff;

	public static void extrasOffForTests(boolean off) {
		extrasOff = off;
	}

	public boolean readItOn() {
		return readIt && !extrasOff;
	}

	public int reviewPercent() {
		return extrasOff ? 0 : reviewShare;
	}

	/** Whether the rounds that come in on their own now and then (tricky words, plurals, phrases, capitals as review) may. */
	public boolean extrasOn() {
		return !extrasOff;
	}

	/** The same, with scenes set so: for tests, which need to know when a scene comes. */
	public static void useScenes(boolean on, int every) {
		PrizeConfig c = current;
		current = new PrizeConfig(c.enabled, c.dailyCap, c.newWord, c.solved, c.solvedFirstTryOnly, c.streak, c.streakLength,
			c.stageComplete, c.milestonesEnabled, c.milestones, c.dailyVisit, c.pointsEnabled, c.thresholds, on, Math.max(1, every),
			c.lettersFirst, c.masteryClean, c.letterWordEvery, c.readIt, c.readItEvery, c.reviewShare, c.firstSound, c.lastSound, c.middleVowel, c.wholeWord);
	}

	/** Nothing pays: before any file has been read, or when every attempt to read one failed. */
	public static final PrizeConfig NONE = new PrizeConfig(false, 0, Event.OFF, Event.OFF, false,
		Event.OFF, 3, Event.OFF, false, List.of(), Event.OFF, false, List.of(), false, 4, true, 3, 3, true, 5, 30, true, true, true, true);

	private static volatile PrizeConfig current = NONE;

	private static volatile int teamUpEvery = 3;
	private static volatile @org.jspecify.annotations.Nullable Integer teamUpForTests;

	/** When two read together, one round in this many is a team-up; 0 for none. */
	public static int teamUpEvery() {
		Integer forced = teamUpForTests;
		return forced != null ? forced : teamUpEvery;
	}

	/** For tests: team-ups this often (0 for none), or null for the file's. */
	public static void teamUpForTests(@org.jspecify.annotations.Nullable Integer every) {
		teamUpForTests = every;
	}

	public static PrizeConfig get() {
		return current;
	}

	private static volatile Event toyMastered = new Event(true, 0, List.of());

	/**
	 * A plushie or toy earned by mastering every word that earns it: the toy, and these prizes too.
	 * On unless the file says otherwise, a file from before it included.
	 */
	public static Event toyMastered() {
		return toyMastered;
	}

	private static volatile int tellBobEvery = 5;
	private static volatile int whatsWrongEvery = 6;
	private static volatile boolean whatsWrong = true;
	private static volatile boolean sillyCounts = false;
	private static volatile java.util.Map<String, Boolean> subjectsOn = java.util.Map.of();
	private static volatile justfatlard.learning_blocks.subject.Subjects.Mix subjectMix = justfatlard.learning_blocks.subject.Subjects.Mix.ALTERNATE;
	private static volatile int subjectBlock = 3;
	private static volatile java.util.Map<String, Integer> subjectWeights = java.util.Map.of();

	/** Whether a subject is on by default, for a child nobody has set it for: all of them, unless the file says. */
	public static boolean subjectOn(String subject) {
		return subjectsOn.getOrDefault(subject, true);
	}

	/** How a child's subjects take turns. */
	public static justfatlard.learning_blocks.subject.Subjects.Mix subjectMix() {
		return subjectMix;
	}

	/** In {@code blocks}, how many rounds of one before the next. */
	public static int subjectBlock() {
		return subjectBlock;
	}

	/** In {@code weights}, how often a subject comes, against the others (1 each unless the file says). */
	public static int subjectWeight(String subject) {
		return subjectWeights.getOrDefault(subject, 1);
	}

	/** For tests: the mix and the block size. */
	public static void useSubjectMix(justfatlard.learning_blocks.subject.Subjects.Mix mix, int block) {
		subjectMix = mix;
		subjectBlock = Math.max(1, block);
	}
	private static volatile int bobPropShare = 20;

	/** From stage 5, one round in this many is Bob's: telling him what to do, or fixing what he did; 0 for none. */
	public static int tellBobEvery() {
		return tellBobEvery;
	}

	/** Whether what's-wrong rounds come: Bob's sentence (from stage 5) and a word with one wrong letter (from stage 2). */
	public static boolean whatsWrong() {
		return whatsWrong;
	}

	/** From stage 2, one round in this many is a word with one wrong letter to fix; 0 for none. */
	public static int whatsWrongEvery() {
		return whatsWrong ? whatsWrongEvery : 0;
	}

	/**
	 * Out of 100, at most how often a goal made from the things' affordances is one with props only
	 * (a mug on a desk) rather than a plushie or a toy; and never two in a row.
	 */
	public static int bobPropShare() {
		return bobPropShare;
	}

	/** For tests. */
	public static void useBobPropShare(int share) {
		bobPropShare = Math.max(0, Math.min(100, share));
	}

	/** Whether a silly sentence told to Bob counts as a lesson. Never towards mastery. */
	public static boolean sillyCounts() {
		return sillyCounts;
	}

	public static Path file() {
		return PrizeFile.path();
	}

	/**
	 * Read the file, writing the commented defaults first if there is none. What went wrong, one line
	 * per problem; empty when all was well.
	 */
	public static List<String> load(MinecraftServer server) {
		List<String> problems = new ArrayList<>();
		try {
			JsonObject tree = PrizeFile.read();
			if (migrate(tree)) {
				PrizeFile.write(tree);
				LearningBlocks.LOGGER.info("[{}] prizes.json: letter_known_after is now mastery_clean", LearningBlocks.MOD_ID);
			}
			current = parse(tree, server, problems);
		} catch (IOException | RuntimeException e) {
			problems.add("could not read " + file() + ": " + e.getMessage() + "; keeping what was loaded before");
		}
		for (String problem : problems) LearningBlocks.LOGGER.warn("[{}] prizes.json: {}", LearningBlocks.MOD_ID, problem);
		lastProblems = List.copyOf(problems);
		return problems;
	}

	private static volatile List<String> lastProblems = List.of();

	/** What the last read of the file complained about. */
	public static List<String> lastProblems() {
		return lastProblems;
	}

	/** Use this text instead of the file: for tests, which need to know what pays. */
	public static List<String> use(String text, MinecraftServer server) {
		List<String> problems = new ArrayList<>();
		current = parse(PrizeFile.parse(text), server, problems);
		return problems;
	}

	/**
	 * Files from before mastery said {@code letter_known_after}; it becomes {@code mastery_clean}.
	 * Left at its old default of 2, it takes the new default instead, as nobody chose it. True if the
	 * tree changed and wants writing back.
	 */
	static boolean migrate(JsonObject json) {
		if (!json.has("puzzles") || !json.get("puzzles").isJsonObject()) return false;
		JsonObject puzzles = json.getAsJsonObject("puzzles");
		if (!puzzles.has("letter_known_after")) return false;
		com.google.gson.JsonElement old = puzzles.remove("letter_known_after");
		if (!puzzles.has("mastery_clean")) {
			int value = old.isJsonPrimitive() && old.getAsJsonPrimitive().isNumber() ? old.getAsInt() : 3;
			puzzles.addProperty("mastery_clean", value == 2 ? 3 : value);
		}
		return true;
	}

	/** The mastery count a file would give: for tests of the migration. */
	public static int masteryIn(String text) {
		JsonObject json = PrizeFile.parse(text);
		migrate(json);
		JsonObject puzzles = json.has("puzzles") ? json.getAsJsonObject("puzzles") : new JsonObject();
		return puzzles.has("mastery_clean") ? puzzles.get("mastery_clean").getAsInt() : 3;
	}

	/** The same, with this many clean completions to master: for tests. */
	public static void useMastery(int clean) {
		PrizeConfig c = current;
		current = new PrizeConfig(c.enabled, c.dailyCap, c.newWord, c.solved, c.solvedFirstTryOnly, c.streak, c.streakLength,
			c.stageComplete, c.milestonesEnabled, c.milestones, c.dailyVisit, c.pointsEnabled, c.thresholds, c.scenes, c.sceneEvery,
			c.lettersFirst, Math.max(1, clean), c.letterWordEvery, c.readIt, c.readItEvery, c.reviewShare, c.firstSound, c.lastSound, c.middleVowel, c.wholeWord);
	}

	static PrizeConfig parse(JsonObject json, MinecraftServer server, List<String> problems) {
		migrate(json);
		if (mentionsWordBook(json)) {
			LearningBlocks.LOGGER.info("[{}] prizes.json still mentions the word book, which is gone (My words replaced it); "
				+ "ignored, and dropped the next time the prize editor saves", LearningBlocks.MOD_ID);
		}
		JsonObject events = object(json, "events");
		JsonObject solvedJson = object(events, "solved");
		JsonObject streakJson = object(events, "streak");
		JsonObject milestoneJson = object(events, "milestone");
		JsonObject pointsJson = object(json, "points");
		JsonObject puzzlesJson = object(json, "puzzles");

		// Reading together: how often a round is a team-up when two share a room (0, or team_up off, for never).
		teamUpEvery = bool(puzzlesJson, "team_up", true, problems) ? Math.max(0, integer(puzzlesJson, "team_up_every", 3, problems)) : 0;
		// Sentences: Bob's rounds, what's-wrong rounds, and whether the silly sandbox counts.
		toyMastered = object(events, "toy_mastered").isEmpty() ? new Event(true, 0, List.of()) : event(events, "toy_mastered", server, problems);
		tellBobEvery = bool(puzzlesJson, "tell_bob", true, problems) ? Math.max(0, integer(puzzlesJson, "tell_bob_every", 5, problems)) : 0;
		whatsWrong = bool(puzzlesJson, "whats_wrong", true, problems);
		whatsWrongEvery = Math.max(0, integer(puzzlesJson, "whats_wrong_every", 6, problems));
		sillyCounts = bool(puzzlesJson, "silly_counts", false, problems);
		// Subjects: which are on by default, and how they take turns.
		JsonObject subjectsJson = object(json, "subjects");
		java.util.Map<String, Boolean> on = new java.util.LinkedHashMap<>();
		for (String subject : justfatlard.learning_blocks.subject.Subjects.ids()) on.put(subject, bool(subjectsJson, subject, true, problems));
		subjectsOn = java.util.Map.copyOf(on);
		JsonElement mixJson = subjectsJson.get("mix");
		String mix = mixJson != null && mixJson.isJsonPrimitive() ? mixJson.getAsString() : "alternate";
		subjectMix = justfatlard.learning_blocks.subject.Subjects.Mix.of(mix);
		if (!subjectMix.id().equals(mix)) problems.add("subjects.mix: " + mix + " is not alternate, blocks or weights; using alternate");
		subjectBlock = Math.max(1, integer(subjectsJson, "block", 3, problems));
		JsonObject weightsJson = object(subjectsJson, "weights");
		java.util.Map<String, Integer> weights = new java.util.LinkedHashMap<>();
		for (String subject : justfatlard.learning_blocks.subject.Subjects.ids()) weights.put(subject, Math.max(0, integer(weightsJson, subject, 1, problems)));
		subjectWeights = java.util.Map.copyOf(weights);
		bobPropShare = Math.max(0, Math.min(100, integer(puzzlesJson, "bob_prop_share", 20, problems)));
		return new PrizeConfig(
			bool(json, "enabled", true, problems),
			Math.max(0, integer(json, "daily_cap", 20, problems)),
			event(events, "new_word", server, problems),
			event(events, "solved", server, problems),
			bool(solvedJson, "first_try_only", true, problems),
			event(events, "streak", server, problems),
			Math.max(1, integer(streakJson, "length", 3, problems)),
			event(events, "stage_complete", server, problems),
			bool(milestoneJson, "enabled", false, problems),
			marks(milestoneJson, "every", "words", "events.milestone.every", server, problems),
			event(events, "daily_visit", server, problems),
			bool(pointsJson, "enabled", false, problems),
			marks(pointsJson, "thresholds", "points", "points.thresholds", server, problems),
			bool(puzzlesJson, "scenes", true, problems),
			Math.max(1, integer(puzzlesJson, "scene_every", 4, problems)),
			bool(puzzlesJson, "letters_first", true, problems),
			Math.max(1, Math.min(10, integer(puzzlesJson, "mastery_clean", 3, problems))),
			Math.max(0, integer(puzzlesJson, "letter_word_every", 3, problems)),
			bool(puzzlesJson, "read_it", true, problems),
			Math.max(1, integer(puzzlesJson, "read_it_every", 5, problems)),
			Math.max(0, Math.min(90, integer(puzzlesJson, "review_share", 30, problems))),
			bool(puzzlesJson, "first_sound", true, problems),
			bool(puzzlesJson, "last_sound", true, problems),
			bool(puzzlesJson, "middle_vowel", true, problems),
			bool(puzzlesJson, "whole_word", true, problems));
	}

	private static Event event(JsonObject events, String name, MinecraftServer server, List<String> problems) {
		JsonObject json = object(events, name);
		if (json.isEmpty()) return Event.OFF;
		return new Event(bool(json, "enabled", false, problems), Math.max(0, integer(json, "points", 0, problems)),
			Prize.list(json.get("prizes"), "events." + name + ".prizes", server, problems));
	}

	private static List<Mark> marks(JsonObject json, String key, String at, String where, MinecraftServer server, List<String> problems) {
		List<Mark> marks = new ArrayList<>();
		JsonElement list = json.get(key);
		if (list == null) return marks;
		if (!list.isJsonArray()) {
			problems.add(where + ": expected a list");
			return marks;
		}
		int index = 0;
		for (JsonElement element : list.getAsJsonArray()) {
			String here = where + "[" + index++ + "]";
			if (!element.isJsonObject() || !element.getAsJsonObject().has(at)) {
				problems.add(here + ": needs \"" + at + "\"; skipped");
				continue;
			}
			JsonObject mark = element.getAsJsonObject();
			try {
				int value = mark.get(at).getAsInt();
				if (value <= 0) throw new IllegalArgumentException(at + " must be more than nought");
				if (marks.stream().anyMatch(other -> other.at() == value)) {
					problems.add(here + ": another entry is already at " + value + "; both are kept, and both pay");
				}
				marks.add(new Mark(value, Prize.list(mark.get("prizes"), here + ".prizes", server, problems)));
			} catch (RuntimeException e) {
				problems.add(here + ": " + e.getMessage() + "; skipped");
			}
		}
		marks.sort((a, b) -> Integer.compare(a.at(), b.at()));
		return marks;
	}

	/** Whether the word book turns up anywhere in the file: the old setting or a prize. */
	private static boolean mentionsWordBook(JsonElement json) {
		if (json.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
				if (entry.getKey().equals("word_book") || mentionsWordBook(entry.getValue())) return true;
			}
		} else if (json.isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray()) if (mentionsWordBook(element)) return true;
		}
		return false;
	}

	private static JsonObject object(@Nullable JsonObject json, String key) {
		if (json == null) return new JsonObject();
		JsonElement element = json.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean bool(JsonObject json, String key, boolean fallback, List<String> problems) {
		JsonElement element = json.get(key);
		if (element == null) return fallback;
		try {
			if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean();
		} catch (RuntimeException ignored) {
			// Reported below.
		}
		problems.add(key + ": expected true or false; using " + fallback);
		return fallback;
	}

	private static int integer(JsonObject json, String key, int fallback, List<String> problems) {
		JsonElement element = json.get(key);
		if (element == null) return fallback;
		try {
			return element.getAsInt();
		} catch (RuntimeException e) {
			problems.add(key + ": expected a whole number; using " + fallback);
			return fallback;
		}
	}

}
