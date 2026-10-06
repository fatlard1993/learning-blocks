package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * One word round: the word to make, the gap in it, and the family it comes from.
 *
 * <p>The loose tiles are what the family has in the gap: one makes the target, the others make
 * real words too, so a child who puts b in the gap hears "bat" and meets a bat, and learns from the
 * wrong answer as well as the right one. A middle-vowel family spans every stage up to the
 * child's, since it needs vowels from several; the other kinds' families are one stage's words.
 *
 * @param target  the word the room asks for
 * @param family  every usable word in the target's frame, the target included
 * @param shelf   the loose tiles, in the order they stand: what the family has in the gap, shuffled
 * @param gap     which tile of the word is missing
 */
public record Puzzle(Word target, List<Word> family, List<Letter> shelf, int gap, Kind kind) {

	/** At most this many loose tiles, which is as many as the floor in front of the board holds. */
	public static final int MAX_TILES = 5;

	/**
	 * Which tile is missing: the first, the last, the vowel in the middle, or all of them (build the
	 * whole word); a plural's first and its s; and reading together, a team-up's first and last, one
	 * reader each.
	 */
	public enum Kind { FIRST, LAST, MIDDLE, WHOLE, PLURAL, TEAM, CHUNK }

	/** Tiles on the floor for a whole-word round: the word's, and a decoy or two. */
	public static final int MAX_WHOLE_TILES = 6;

	public List<Integer> gaps() {
		// A plural: the first sound, and the s on the end.
		if (kind == Kind.PLURAL || kind == Kind.TEAM) return List.of(0, target.tiles().size() - 1);
		// A big word's chunk: every tile of the chunk the gap starts.
		if (kind == Kind.CHUNK) {
			for (int[] range : Words.chunkRanges(target)) {
				if (range[0] != gap) continue;
				List<Integer> chunk = new ArrayList<>();
				for (int i = range[0]; i < range[1]; i++) chunk.add(i);
				return chunk;
			}
		}
		if (kind != Kind.WHOLE) return List.of(gap);
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < target.tiles().size(); i++) all.add(i);
		return all;
	}

	public Puzzle(Word target, List<Word> family, List<Letter> shelf) {
		this(target, family, shelf, 0, Kind.FIRST);
	}

	public Letter answer() {
		return Letter.of(target.tiles().get(gap));
	}

	public @Nullable Word spelled(List<String> tiles) {
		for (Word word : family) {
			if (word.tiles().equals(tiles)) return word;
		}
		return null;
	}

	/** Where a kind puts the gap in this word, or -1 where it cannot: a middle vowel needs three tiles. */
	static int gapOf(Kind kind, Word word) {
		int n = word.tiles().size();
		return switch (kind) {
			case FIRST, WHOLE, PLURAL, TEAM -> 0;
			case LAST -> n - 1;
			case MIDDLE -> n == 3 && "aeiou".contains(word.tiles().get(1)) ? 1 : -1;
			case CHUNK -> buildableChunks(word).isEmpty() ? -1 : buildableChunks(word).getFirst()[0];
		};
	}

	/** A word a whole-word round can build: no tile twice, since each tile on the floor has one place. */
	static boolean buildable(Word word) {
		return word.tiles().stream().distinct().count() == word.tiles().size();
	}

	/** The word with its gap blanked, which is what a family shares: "_at", "ma_", "c_t". */
	static @Nullable String frameOf(Kind kind, Word word) {
		// Plural rounds are made on purpose, never picked from frames.
		if (kind == Kind.PLURAL || kind == Kind.TEAM) return null;
		// No more tiles than the floor has places for.
		if (kind == Kind.WHOLE) return buildable(word) && word.tiles().size() <= MAX_WHOLE_TILES ? "_".repeat(word.tiles().size()) + "/" + word.text() : null;
		if (kind == Kind.CHUNK) return buildableChunks(word).isEmpty() ? null : "chunk/" + word.text();
		int at = gapOf(kind, word);
		if (at < 0) return null;
		List<String> tiles = new ArrayList<>(word.tiles());
		tiles.set(at, "_");
		return String.join("", tiles);
	}

	/** A round from these words at the earliest stage there is, ignoring what anybody has read. */
	public static @Nullable Puzzle pick(Collection<Word> words, RandomSource random) {
		int lowest = Integer.MAX_VALUE;
		for (Word word : words) if (usable(word)) lowest = Math.min(lowest, word.stage());
		return pick(words, random, Set.of(), lowest == Integer.MAX_VALUE ? -1 : lowest);
	}

	public static @Nullable Puzzle pick(Collection<Word> words, RandomSource random, Set<String> read, int stage) {
		return pick(words, random, read, stage, Set.of(Kind.FIRST));
	}

	/**
	 * A round at this stage (or from every stage, for -1), of one of these kinds, preferring a
	 * target this child has not read. Frames with fewer than two words are skipped. If no frame of
	 * these kinds has anything unread and some other kind would, that kind is used: a word only a
	 * last-sound round can reach (web: we_) must never hold a child at a stage.
	 */
	public static @Nullable Puzzle pick(Collection<Word> words, RandomSource random, Set<String> read, int stage, Set<Kind> kinds) {
		return pick(words, random, read, stage, kinds, singles());
	}

	public static Set<Letter> singles() {
		Set<Letter> all = new java.util.LinkedHashSet<>();
		for (Letter letter : Letter.values()) if (letter.isSingle()) all.add(letter);
		return all;
	}

	/** The same, with the tiles this reader knows, for a whole-word round's decoys. */
	public static @Nullable Puzzle pick(Collection<Word> words, RandomSource random, Set<String> read, int stage, Set<Kind> kinds,
			Set<Letter> known) {
		return pick(words, random, read, stage, kinds, known, word -> 0, null);
	}

	/**
	 * The same, with {@code read} the words done with (mastered): among a family's words not done
	 * with, the fewest clean completions first, and never {@code avoid}, the last round's target,
	 * while there is another.
	 */
	public static @Nullable Puzzle pick(Collection<Word> words, RandomSource random, Set<String> read, int stage, Set<Kind> kinds,
			Set<Letter> known, java.util.function.ToIntFunction<Word> clean, @Nullable Word avoid) {
		return pick(words, random, read, stage, kinds, known, clean, avoid, word -> stage < 0 || word.stage() == stage);
	}

	/**
	 * The same, with the target one of the words {@code targetable} allows (the child's band): the
	 * family around it, and so the tiles on the floor, still from its whole stage.
	 */
	public static @Nullable Puzzle pick(Collection<Word> words, RandomSource random, Set<String> read, int stage, Set<Kind> kinds,
			Set<Letter> known, java.util.function.ToIntFunction<Word> clean, @Nullable Word avoid, java.util.function.Predicate<Word> targetable) {
		Map<Kind, List<List<Word>>> frames = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values()) {
			// Only families with a word that may be the target.
			frames.put(kind, frames(words, kind, stage).stream().filter(family -> family.stream().anyMatch(targetable)).toList());
		}
		List<Kind> allowed = new ArrayList<>();
		for (Kind kind : Kind.values()) if (kinds.contains(kind) && !frames.get(kind).isEmpty()) allowed.add(kind);

		// Something unread at this stage that the allowed kinds cannot reach: reach it another way.
		boolean anyFresh = false;
		for (Kind kind : allowed) anyFresh |= frames.get(kind).stream().anyMatch(family -> hasFresh(family, read, targetable));
		if (!anyFresh) {
			for (Kind kind : Kind.values()) {
				if (!allowed.contains(kind) && frames.get(kind).stream().anyMatch(family -> hasFresh(family, read, targetable))) allowed.add(kind);
			}
		}
		if (allowed.isEmpty()) {
			// Nothing in a real frame: the old way, one word and its own tile, rather than no round at all.
			List<Word> any = new ArrayList<>();
			for (Word word : words) if (usable(word) && targetable.test(word)) any.add(word);
			if (any.isEmpty()) return null;
			Word target = any.get(random.nextInt(any.size()));
			return of(target, List.of(target), 0, Kind.FIRST, random, known, words);
		}
		// Only kinds with something unread, while any has; first-sound rounds most often, the others now and then.
		List<Kind> useful = new ArrayList<>();
		for (Kind kind : allowed) if (frames.get(kind).stream().anyMatch(family -> hasFresh(family, read, targetable))) useful.add(kind);
		if (useful.isEmpty()) useful = allowed;
		// Not the last round's target again: kinds that can ask something else first, while any can.
		if (avoid != null) {
			List<Kind> other = useful.stream().filter(k -> frames.get(k).stream().anyMatch(family -> family.stream().anyMatch(word ->
				!word.tiles().equals(avoid.tiles()) && !read.contains(word.id().toString()) && targetable.test(word)))).toList();
			if (!other.isEmpty()) useful = other;
		}
		List<Kind> weighted = new ArrayList<>();
		for (Kind kind : useful) for (int i = 0; i < (kind == Kind.FIRST ? 2 : 1); i++) weighted.add(kind);
		Kind kind = weighted.get(random.nextInt(weighted.size()));
		List<List<Word>> real = frames.get(kind);
		List<List<Word>> fresh = new ArrayList<>();
		for (List<Word> family : real) if (hasFresh(family, read, targetable)) fresh.add(family);
		List<List<Word>> choices = new ArrayList<>(fresh.isEmpty() ? real : fresh);
		// Not the last round's target again: a family whose only open word it is waits, if another has one.
		if (avoid != null && choices.size() > 1) {
			List<List<Word>> other = choices.stream().filter(family -> family.stream().anyMatch(word -> !word.tiles().equals(avoid.tiles())
				&& !read.contains(word.id().toString()) && targetable.test(word))).toList();
			if (!other.isEmpty()) choices = new ArrayList<>(other);
		}
		List<Word> family = choices.get(random.nextInt(choices.size()));
		List<Word> unread = family.stream().filter(word -> !read.contains(word.id().toString()) && targetable.test(word)).toList();
		List<Word> from = new ArrayList<>(unread.isEmpty() ? family.stream().filter(targetable).toList() : unread);
		if (avoid != null && from.size() > 1) from.removeIf(word -> word.tiles().equals(avoid.tiles()));
		int fewest = from.stream().mapToInt(clean).min().orElse(0);
		from.removeIf(word -> clean.applyAsInt(word) > fewest);
		Word target = from.get(random.nextInt(from.size()));
		return of(target, family, gapOf(kind, target), kind, random, known, words);
	}

	private static boolean hasFresh(List<Word> family, Set<String> read, java.util.function.Predicate<Word> targetable) {
		return family.stream().anyMatch(word -> !read.contains(word.id().toString()) && targetable.test(word));
	}

	/**
	 * Every frame of this kind with two or more different tiles in the gap: first and last sound
	 * from this stage's words, the middle vowel from every stage up to this one. A whole-word frame
	 * is a word on its own: the whole word is the puzzle.
	 */
	static List<List<Word>> frames(Collection<Word> words, Kind kind, int stage) {
		if (kind == Kind.WHOLE || kind == Kind.CHUNK) {
			List<List<Word>> each = new ArrayList<>();
			for (Word word : words) {
				if (usable(word) && frameOf(kind, word) != null && (stage < 0 || word.stage() == stage)) each.add(List.of(word));
			}
			return each;
		}
		Map<String, List<Word>> families = new LinkedHashMap<>();
		for (Word word : words) {
			if (!usable(word)) continue;
			boolean inStage = kind == Kind.MIDDLE ? stage < 0 || word.stage() <= stage : stage < 0 || word.stage() == stage;
			if (!inStage) continue;
			String frame = frameOf(kind, word);
			if (frame == null) continue;
			String key = kind == Kind.MIDDLE ? frame : word.stage() + "/" + frame;
			families.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
		}
		List<List<Word>> real = new ArrayList<>();
		for (List<Word> family : families.values()) {
			int at = gapOf(kind, family.getFirst());
			if (family.stream().map(word -> word.tiles().get(at)).distinct().count() >= 2) real.add(family);
		}
		return real;
	}

	/** A round for exactly this target: a first-sound round if it has a family, else last sound, else middle vowel. */
	public static @Nullable Puzzle forTarget(Word target, Collection<Word> words, RandomSource random) {
		for (Kind kind : Kind.values()) {
			Puzzle puzzle = forTarget(target, words, random, kind);
			if (puzzle != null && puzzle.shelf().size() >= 2) return puzzle;
		}
		return usable(target) ? of(target, List.of(target), 0, Kind.FIRST, random, singles(), words) : null;
	}

	public static @Nullable Puzzle forTarget(Word target, Collection<Word> words, RandomSource random, Kind kind) {
		if (!usable(target)) return null;
		String frame = frameOf(kind, target);
		if (frame == null) return null;
		if (kind == Kind.WHOLE || kind == Kind.CHUNK) return of(target, List.of(target), 0, kind, random, singles(), words);
		List<Word> family = new ArrayList<>();
		for (Word word : words) {
			if (!usable(word) || !frame.equals(frameOf(kind, word))) continue;
			if (kind != Kind.MIDDLE && word.stage() != target.stage()) continue;
			family.add(word);
		}
		return of(target, family, gapOf(kind, target), kind, random, singles(), words);
	}

	private static Puzzle of(Word target, List<Word> family, int gap, Kind kind, RandomSource random, Set<Letter> known,
			Collection<Word> words) {
		if (kind == Kind.WHOLE) return whole(target, random, known, words);
		if (kind == Kind.CHUNK) {
			List<int[]> chunks = buildableChunks(target);
			return chunk(target, chunks.get(random.nextInt(chunks.size()))[0], random, known, words);
		}
		List<Letter> shelf = new ArrayList<>();
		shelf.add(Letter.of(target.tiles().get(gap)));
		List<Word> others = new ArrayList<>(family);
		others.remove(target);
		shuffle(others, random);
		for (Word other : others) {
			Letter tile = Letter.of(other.tiles().get(gap));
			if (shelf.size() >= MAX_TILES) break;
			if (tile != null && !shelf.contains(tile)) shelf.add(tile);
		}
		shuffle(shelf, random);
		return new Puzzle(target, List.copyOf(family), List.copyOf(shelf), gap, kind);
	}

	/**
	 * Build the whole word: every tile on the floor, and a decoy or two from tiles this reader knows.
	 * Any real word of the same length built instead is a word too, with its own outcome.
	 */
	private static Puzzle whole(Word target, RandomSource random, Set<Letter> known, Collection<Word> words) {
		List<Letter> shelf = new ArrayList<>();
		for (String tile : target.tiles()) shelf.add(Letter.of(tile));
		List<Letter> decoys = new ArrayList<>(known);
		decoys.removeAll(shelf);
		shuffle(decoys, random);
		int room = Math.min(2, MAX_WHOLE_TILES - shelf.size());
		for (int i = 0; i < room && i < decoys.size(); i++) shelf.add(decoys.get(i));
		shuffle(shelf, random);
		List<Word> family = new ArrayList<>();
		for (Word word : words) if (usable(word) && word.tiles().size() == target.tiles().size()) family.add(word);
		if (!family.contains(target)) family.add(target);
		return new Puzzle(target, List.copyOf(family), List.copyOf(shelf), 0, Kind.WHOLE);
	}

	/**
	 * A big word's chunks that can be built from the floor: more than one chunk to the word, and
	 * no tile twice in the chunk (pop, with its two p's, can't: the floor has one of each).
	 */
	static List<int[]> buildableChunks(Word word) {
		List<int[]> out = new ArrayList<>();
		if (Words.chunks(word) == null) return out;
		for (int[] range : Words.chunkRanges(word)) {
			List<String> tiles = word.tiles().subList(range[0], range[1]);
			if (tiles.stream().distinct().count() == tiles.size()) out.add(range);
		}
		return out;
	}

	/**
	 * Build one chunk of a big word: the other chunk on the board, this one's tiles on the floor
	 * with a decoy or two this reader knows. {@code start} is the chunk's first tile.
	 */
	public static Puzzle chunk(Word target, int start, RandomSource random, Set<Letter> known, Collection<Word> words) {
		int end = start;
		for (int[] range : Words.chunkRanges(target)) if (range[0] == start) end = range[1];
		List<Letter> shelf = new ArrayList<>();
		for (String tile : target.tiles().subList(start, end)) shelf.add(Letter.of(tile));
		List<Letter> decoys = new ArrayList<>(known);
		decoys.removeAll(shelf);
		shuffle(decoys, random);
		int room = Math.min(2, MAX_WHOLE_TILES - shelf.size());
		for (int i = 0; i < room && i < decoys.size(); i++) shelf.add(decoys.get(i));
		shuffle(shelf, random);
		List<Word> family = new ArrayList<>();
		for (Word word : words) if (usable(word) && word.tiles().size() == target.tiles().size()) family.add(word);
		if (!family.contains(target)) family.add(target);
		return new Puzzle(target, List.copyOf(family), List.copyOf(shelf), start, Kind.CHUNK);
	}

	private static boolean usable(Word word) {
		return word.letters() != null && word.outcome().usable();
	}

	private static <T> void shuffle(List<T> list, RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			T swap = list.get(i);
			list.set(i, list.get(j));
			list.set(j, swap);
		}
	}
}
