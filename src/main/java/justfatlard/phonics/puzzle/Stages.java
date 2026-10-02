package justfatlard.phonics.puzzle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import justfatlard.phonics.reward.Progress;

/**
 * Where a child is in the course: one stage per short vowel, in the order phonics is usually taught
 * - 1 short a, 2 short i, 3 short o, 4 short u, 5 short e - though the numbers are only what the
 * word files say, so a data pack can add stages or move words between them.
 *
 * <p>A child's stage is the lowest one, at or above the floor an operator may have set, with a word
 * they have not mastered ({@code mastery_clean} clean completions). Mastering every word of it
 * completes it, and the next round is from the next: the gate between the layers.
 * Past the last stage everything is open, and rounds are picked from all of it.
 *
 * <p>Only words this server can actually show count, so a stage whose words name a missing mod's
 * creatures is not left forever incomplete.
 *
 * <p>A long stage is split into bands, 1a, 1b, 1c, by the letters its words need, in the order
 * letters are taught (s a t p i n, then m d g o c k, e u r h b, f l j v w, x y z, then two-letter
 * tiles): the first two word stages by letter group, any other that would take more than about
 * {@link #LONG_STAGE} rounds in two halves. A band is what the gate is: its words are the targets,
 * its mastering opens the next. A round's family is still its whole stage's, so the tiles on the
 * floor stay three or four real ones.
 */
public final class Stages {
	private Stages() {}

	/** A stage longer than this many rounds, at mastery, is split in two. */
	public static final int LONG_STAGE = 45;
	/** A band smaller than this goes in with the one before it (or after, if it is first). */
	static final int SMALLEST_BAND = 4;
	private static final String[] GROUPS = {"satpin", "mdgock", "eurhb", "fljvw", "xyz"};

	/**
	 * A part of a stage: stage 1's band 0 is 1a. Its code, stage * 100 + index, orders bands and
	 * is what a band floor and a band's payout are kept as; stage 0, the letters level, is 0.
	 */
	public record Band(int stage, int index, int of, List<Word> words) {
		public int code() {
			return stage * 100 + index;
		}

		/** "1b", or "3" for a stage in one band. */
		public String label() {
			return of > 1 ? stage + String.valueOf((char) ('a' + index)) : String.valueOf(stage);
		}

		public boolean contains(Word word) {
			return words.stream().anyMatch(w -> w.id().equals(word.id()));
		}
	}

	/** The letter group a tile is taught in, 1 to 5; a two-letter tile after them all, 6. */
	static int group(String tile) {
		if (tile.length() != 1) return GROUPS.length + 1;
		for (int i = 0; i < GROUPS.length; i++) if (GROUPS[i].contains(tile)) return i + 1;
		return GROUPS.length;
	}

	/** The latest group any of a word's tiles is taught in: what a child must know to read it. */
	static int hardest(Word word) {
		int hardest = 1;
		for (String tile : word.tiles()) hardest = Math.max(hardest, group(tile));
		return hardest;
	}

	/** Its tiles' groups, latest first: words needing the same latest group, easier ones first. */
	private static String order(Word word) {
		StringBuilder key = new StringBuilder();
		word.tiles().stream().map(Stages::group).sorted(java.util.Comparator.reverseOrder()).forEach(key::append);
		return key + "/" + word.text();
	}

	private static volatile List<Word> bandsFor = null;
	private static volatile int bandsAt = -1;
	private static volatile List<Band> bands = List.of();

	/** Every band, in order: the course as the child meets it. */
	public static List<Band> bands() {
		List<Word> now = Words.all();
		int mastery = Progress.masteredAt();
		if (now != bandsFor || mastery != bandsAt) {
			List<Band> made = new ArrayList<>();
			List<Integer> stages = all();
			List<Integer> byGroup = stages.stream().filter(stage -> stage >= 1).limit(2).toList();
			for (int stage : stages) {
				List<Word> words = new ArrayList<>(at(stage));
				words.sort(java.util.Comparator.comparing(Stages::order));
				List<List<Word>> parts = byGroup.contains(stage) ? byGroup(words) : words.size() * mastery > LONG_STAGE
					? List.of(words.subList(0, (words.size() + 1) / 2), words.subList((words.size() + 1) / 2, words.size())) : List.of(words);
				for (int i = 0; i < parts.size(); i++) made.add(new Band(stage, i, parts.size(), List.copyOf(parts.get(i))));
			}
			bands = List.copyOf(made);
			bandsFor = now;
			bandsAt = mastery;
		}
		return bands;
	}

	/** A first stage's words by letter group: the first group's alone, then with the second, then the rest. */
	private static List<List<Word>> byGroup(List<Word> words) {
		List<List<Word>> parts = new ArrayList<>(List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
		for (Word word : words) parts.get(Math.min(hardest(word), 3) - 1).add(word);
		parts.removeIf(List::isEmpty);
		for (int i = 0; i < parts.size() && parts.size() > 1; ) {
			if (parts.get(i).size() >= SMALLEST_BAND) {
				i++;
				continue;
			}
			List<Word> small = parts.remove(i);
			if (i == 0) parts.getFirst().addAll(0, small);
			else parts.get(i - 1).addAll(small);
		}
		return parts;
	}

	/** The bands of one stage. */
	public static List<Band> bandsOf(int stage) {
		return bands().stream().filter(band -> band.stage() == stage).toList();
	}

	/** The band a word is in, or null for a word in none (a tricky word, a plural, one that cannot be shown). */
	public static @org.jspecify.annotations.Nullable Band bandOf(Word word) {
		for (Band band : bands()) if (band.stage() == word.stage() && band.contains(word)) return band;
		return null;
	}

	/** A band by its label, "1b", or a whole stage's first band, "1"; null if there is none. */
	public static @org.jspecify.annotations.Nullable Band parse(String label) {
		for (Band band : bands()) if (band.label().equals(label)) return band;
		try {
			int stage = Integer.parseInt(label);
			List<Band> of = bandsOf(stage);
			return of.isEmpty() ? null : of.getFirst();
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Every band's label: "1a, 1b, 1c, 2a...". */
	public static List<String> labels() {
		return bands().stream().map(Band::label).toList();
	}

	/**
	 * This child's band: the first, at or past their floor, with a word not mastered; null once
	 * every band is done (or in the letters level, which has none).
	 */
	public static @org.jspecify.annotations.Nullable Band currentBand(Progress progress) {
		if (progress.stageFloor() == 0 && !Letters.complete(progress)) return null;
		int floor = Math.max(progress.stageFloor() * 100, progress.bandFloor());
		for (Band band : bands()) {
			if (band.code() < floor) continue;
			if (!complete(progress, band)) return band;
		}
		return null;
	}

	public static boolean complete(Progress progress, Band band) {
		for (Word word : band.words()) if (!progress.mastered(word)) return false;
		return true;
	}

	/** Whether this word is one this child's band asks for: the band's own, or any once the course is done. */
	public static boolean inBand(Progress progress, Word word) {
		Band band = currentBand(progress);
		return band == null ? current(progress) != 0 : band.contains(word);
	}

	/** Every stage any usable word is at, lowest first. */
	public static List<Integer> all() {
		Set<Integer> stages = new TreeSet<>();
		for (Word word : usable()) stages.add(word.stage());
		return new ArrayList<>(stages);
	}

	/** Words the room can build and whose outcome can run here. */
	public static List<Word> usable() {
		List<Word> words = new ArrayList<>();
		for (Word word : Words.all()) if (word.letters() != null && word.outcome().usable()) words.add(word);
		return words;
	}

	public static List<Word> at(int stage) {
		List<Word> words = new ArrayList<>();
		for (Word word : usable()) if (word.stage() == stage) words.add(word);
		return words;
	}

	/** Every word of the stage mastered: the gate to the next is open. */
	public static boolean complete(Progress progress, int stage) {
		for (Word word : at(stage)) if (!progress.mastered(word)) return false;
		return true;
	}

	/** How many of a stage's words this child has mastered. */
	public static int masteredAt(Progress progress, int stage) {
		int count = 0;
		for (Word word : at(stage)) if (progress.mastered(word)) count++;
		return count;
	}

	/**
	 * This child's stage, or -1 once every stage is done and everything is open to them. Stage 0 is
	 * the letters level, for a reader who started there and has letters still to learn.
	 */
	public static int current(Progress progress) {
		if (progress.stageFloor() == 0 && !Letters.complete(progress)) return 0;
		Band band = currentBand(progress);
		return band == null ? -1 : band.stage();
	}

	/** How many of a stage's words this child has read. */
	public static int readAt(Progress progress, int stage) {
		Set<String> read = progress.words();
		int count = 0;
		for (Word word : at(stage)) if (read.contains(word.id().toString())) count++;
		return count;
	}

	/** The words to forget when a child is put at this stage: every word from it up. */
	public static Set<String> from(int stage) {
		Set<String> ids = new HashSet<>();
		for (Word word : Words.all()) if (word.stage() >= stage) ids.add(word.id().toString());
		return ids;
	}

	/** The same from a band: its words and every later band's, and any later stage's word in no band. */
	public static Set<String> from(Band band) {
		Set<String> ids = new HashSet<>();
		for (Band later : bands()) if (later.code() >= band.code()) for (Word word : later.words()) ids.add(word.id().toString());
		for (Word word : Words.all()) if (word.stage() > band.stage()) ids.add(word.id().toString());
		return ids;
	}

	/** "stage 2, 3 of 6 read", or "every stage done". */
	public static String describe(Progress progress) {
		int stage = current(progress);
		if (stage < 0) return "every stage done (" + progress.words().size() + " words)";
		if (stage == 0) return "stage 0 (letters), " + Letters.knownLetters(progress).size() + " of " + Letters.all().size() + " known";
		Band band = currentBand(progress);
		if (band != null && band.of() > 1) {
			long mastered = band.words().stream().filter(progress::mastered).count();
			long read = band.words().stream().filter(w -> progress.words().contains(w.id().toString())).count();
			return "stage " + band.label() + ", " + mastered + " of " + band.words().size() + " mastered, " + read + " read";
		}
		return "stage " + stage + ", " + masteredAt(progress, stage) + " of " + at(stage).size() + " mastered, " + readAt(progress, stage) + " read";
	}
}
