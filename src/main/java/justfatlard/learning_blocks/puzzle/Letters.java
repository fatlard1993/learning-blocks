package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The letters level, stage 0: hear a sound, find its letter. Letters come in the usual synthetic
 * letter groups, a group at a time; a letter is known once it has been matched first try a few
 * times, and a group all known opens the next. Every group known and the child is on to words.
 *
 * <p>Each letter has a keyword, the word the alphabet chant ends on: "mmm, mmm, map!". Where a
 * puzzle word starts with the letter it is that word; where none does, a keyword-only word file
 * ({@code "keyword": true}) - apple, egg, sun - which is said and does its thing but is never a
 * puzzle. Q waits for the "qu" stage; x ends its keyword, fox, rather than starting it.
 *
 * <p>Once the first group is known, some letters rounds are words instead, built only from letters
 * the child knows (sat, tap, sip), asked by their first sound: blending from the start, growing as
 * the letters do.
 */
public final class Letters {
	private Letters() {}

	public static final List<List<Letter>> GROUPS = List.of(
		List.of(Letter.S, Letter.A, Letter.T, Letter.P, Letter.I, Letter.N),
		List.of(Letter.M, Letter.D, Letter.G, Letter.O, Letter.C, Letter.K),
		List.of(Letter.E, Letter.U, Letter.R, Letter.H, Letter.B),
		List.of(Letter.F, Letter.L, Letter.J, Letter.V, Letter.W),
		List.of(Letter.X, Letter.Y, Letter.Z));

	/**
	 * The word each letter's chant ends on. A vowel's is also the word the Vowels review shows it
	 * with, so a child meets one anchor word per vowel, not two.
	 */
	public static final Map<Letter, String> KEYWORDS = Map.ofEntries(
		Map.entry(Letter.S, "sun"), Map.entry(Letter.A, "apple"), Map.entry(Letter.T, "turtle"), Map.entry(Letter.P, "pig"),
		Map.entry(Letter.I, "ink"), Map.entry(Letter.N, "nest"),
		Map.entry(Letter.M, "map"), Map.entry(Letter.D, "dog"), Map.entry(Letter.G, "gold"), Map.entry(Letter.O, "on"),
		Map.entry(Letter.C, "cat"), Map.entry(Letter.K, "kid"),
		Map.entry(Letter.E, "egg"), Map.entry(Letter.U, "up"), Map.entry(Letter.R, "rabbit"), Map.entry(Letter.H, "hat"),
		Map.entry(Letter.B, "bat"),
		Map.entry(Letter.F, "fox"), Map.entry(Letter.L, "log"), Map.entry(Letter.J, "jump"), Map.entry(Letter.V, "villager"),
		Map.entry(Letter.W, "web"),
		Map.entry(Letter.X, "fox"), Map.entry(Letter.Y, "yum"), Map.entry(Letter.Z, "zap"));

	/**
	 * Tiles that say the same sound: never two of them on the floor in one letters round, where the
	 * sound is the whole question and either would be right.
	 */
	public static final Map<Letter, Set<Letter>> SAME_SOUND = Map.of(
		Letter.C, Set.of(Letter.K, Letter.CK, Letter.Q),
		Letter.K, Set.of(Letter.C, Letter.CK, Letter.Q),
		Letter.CK, Set.of(Letter.C, Letter.K, Letter.Q),
		Letter.Q, Set.of(Letter.C, Letter.K, Letter.CK));

	public static boolean soundAlike(Letter a, Letter b) {
		return a == b || SAME_SOUND.getOrDefault(a, Set.of()).contains(b);
	}

	public static List<Letter> all() {
		List<Letter> all = new ArrayList<>();
		for (List<Letter> group : GROUPS) all.addAll(group);
		return all;
	}

	public static @Nullable Word keyword(Letter letter) {
		String text = KEYWORDS.get(letter);
		if (text == null) return null;
		Word word = Words.spelled(text);
		return word != null && word.outcome().usable() ? word : null;
	}

	public static int knownAfter() {
		return Math.max(1, PrizeConfig.get().masteryClean);
	}

	public static boolean known(Progress progress, Letter letter) {
		return progress.letterHits(letter.grapheme()) >= knownAfter();
	}

	public static Set<Letter> knownLetters(Progress progress) {
		Set<Letter> known = new LinkedHashSet<>();
		for (Letter letter : all()) if (known(progress, letter)) known.add(letter);
		return known;
	}

	/** The group being learned: the first with a letter whose sound, or whose big-and-little pair, is not yet mastered; -1 once all are. */
	public static int currentGroup(Progress progress) {
		for (int i = 0; i < GROUPS.size(); i++) {
			for (Letter letter : GROUPS.get(i)) if (!known(progress, letter) || !caseKnown(progress, letter)) return i;
		}
		return -1;
	}


	public static String caseKey(Letter letter) {
		return "case:" + letter.lower().grapheme();
	}

	public static int caseHits(Progress progress, Letter letter) {
		return progress.letterHits(caseKey(letter));
	}

	/**
	 * Whether this letter's big and little are mastered: by clean matches, or waived for a reader who
	 * had finished the letters level before capitals came in (they still meet them, now and then).
	 */
	public static boolean caseKnown(Progress progress, Letter letter) {
		return progress.casesWaived() || caseHits(progress, letter) >= knownAfter();
	}

	/** Pairs that look alike turned round: never both on the floor until both are mastered. */
	private static final Map<Letter, Letter> MIRRORED = Map.of(Letter.B, Letter.D, Letter.D, Letter.B, Letter.P, Letter.Q, Letter.Q, Letter.P,
		Letter.M, Letter.W, Letter.W, Letter.M, Letter.N, Letter.U, Letter.U, Letter.N);

	/**
	 * A letter to match big and little for, or null: one whose sound is mastered and whose pair is
	 * not, from the groups met so far (every group, with {@code anyGroup}, for review later on). The
	 * waiver is ignored here: waived pairs still come round.
	 */
	public static @Nullable Letter pickCase(Progress progress, RandomSource random, @Nullable Letter last, boolean anyGroup) {
		int group = anyGroup ? GROUPS.size() - 1 : Math.max(0, currentGroup(progress));
		List<Letter> open = new ArrayList<>();
		for (int i = 0; i <= group && i < GROUPS.size(); i++) {
			for (Letter letter : GROUPS.get(i)) {
				if (known(progress, letter) && caseHits(progress, letter) < knownAfter() && letter != last) open.add(letter);
			}
		}
		return open.isEmpty() ? null : open.get(random.nextInt(open.size()));
	}

	public static boolean soundsLeft(Progress progress) {
		int group = currentGroup(progress);
		return group >= 0 && GROUPS.get(group).stream().anyMatch(letter -> !known(progress, letter));
	}

	/**
	 * The other tiles for a big-and-little round: two more of the same kind (capitals when a capital
	 * is asked for), from letters whose sound is mastered, none that says the same sound and no
	 * mirror-image partner until both are mastered.
	 */
	public static List<Letter> caseDistractors(Progress progress, Letter base, boolean capitals, RandomSource random) {
		List<Letter> pool = new ArrayList<>();
		for (Letter letter : all()) {
			if (letter == base || !known(progress, letter) || soundAlike(letter, base)) continue;
			Letter partner = MIRRORED.get(base);
			if (letter == partner && !(caseHits(progress, letter) >= knownAfter() && caseHits(progress, base) >= knownAfter())) continue;
			pool.add(letter);
		}
		if (pool.size() < 2) for (Letter letter : all()) if (letter != base && !pool.contains(letter) && pool.size() < 6 && letter != MIRRORED.get(base)) pool.add(letter);
		java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		List<Letter> chosen = new ArrayList<>();
		for (Letter letter : pool) {
			if (chosen.size() >= 2) break;
			chosen.add(capitals ? letter.upper() : letter);
		}
		return chosen;
	}

	public static boolean complete(Progress progress) {
		return currentGroup(progress) < 0;
	}

	/**
	 * The letter to ask for: one of the group's not yet known, and now and then (one in four) a
	 * known one from before, to keep them fresh.
	 */
	public static Letter pickTarget(Progress progress, RandomSource random, @Nullable Letter last) {
		int group = Math.max(0, currentGroup(progress));
		// Now and then a letter due for another look - missed lately, or never first try - from those met.
		if (random.nextInt(100) < PrizeConfig.get().reviewPercent()) {
			Letter due = null;
			int box = Integer.MAX_VALUE;
			for (int i = 0; i <= group && i < GROUPS.size(); i++) {
				for (Letter letter : GROUPS.get(i)) {
					String key = justfatlard.learning_blocks.reward.Memory.tileKey(letter.grapheme());
					if (letter == last || !progress.memory().due(key)) continue;
					int at = progress.memory().item(key).box();
					if (at < box) {
						due = letter;
						box = at;
					}
				}
			}
			if (due != null) return due;
		}
		List<Letter> learning = new ArrayList<>();
		for (Letter letter : GROUPS.get(group)) if (!known(progress, letter)) learning.add(letter);
		if (learning.isEmpty()) learning.addAll(GROUPS.get(group));
		List<Letter> review = new ArrayList<>();
		for (int i = 0; i < group; i++) review.addAll(GROUPS.get(i));
		List<Letter> from = !review.isEmpty() && random.nextInt(4) == 0 ? review : learning;
		if (from.size() > 1 && last != null) from.remove(last);
		return from.get(random.nextInt(from.size()));
	}

	/**
	 * The other tiles on the floor: letters already met - the group being learned and everything
	 * known - never the target. Two at first; three once the first two groups are known.
	 */
	public static List<Letter> distractors(Progress progress, Letter target, RandomSource random) {
		int group = Math.max(0, currentGroup(progress));
		Set<Letter> met = new LinkedHashSet<>(GROUPS.get(group));
		met.addAll(knownLetters(progress));
		met.remove(target);
		met.removeIf(letter -> soundAlike(letter, target));
		List<Letter> pool = new ArrayList<>(met);
		int wanted = group >= 2 ? 3 : 2;
		List<Letter> chosen = new ArrayList<>();
		while (chosen.size() < wanted && !pool.isEmpty()) {
			Letter next = pool.remove(random.nextInt(pool.size()));
			if (chosen.stream().noneMatch(other -> soundAlike(other, next))) chosen.add(next);
		}
		return chosen;
	}


	public static final int WORD_TILES = 3;

	/**
	 * Whether this letters round is due to be a word: once the first group is known, one round in
	 * {@code letter_word_every}, counting rounds since the last word round.
	 */
	public static boolean wordDue(Progress progress, int sinceWord) {
		int every = PrizeConfig.get().letterWordEvery;
		return every > 0 && firstGroupKnown(progress) && sinceWord >= every - 1;
	}

	public static boolean firstGroupKnown(Progress progress) {
		return GROUPS.getFirst().stream().allMatch(letter -> known(progress, letter));
	}

	/**
	 * A word built only from letters this child knows, asked by its first sound: the target, and on
	 * the floor its first letter and two other known letters, the family's where they make real words
	 * (sat, pat), and never one that says the same sound. Null while no word fits.
	 */
	public static @Nullable Puzzle earlyWord(Progress progress, RandomSource random, java.util.Collection<Word> words, @Nullable Word last) {
		Set<Letter> known = knownLetters(progress);
		List<Word> fits = new ArrayList<>();
		for (Word word : words) {
			List<Letter> letters = word.letters();
			if (letters == null || letters.size() != 3 || !known.containsAll(letters) || !word.outcome().usable()) continue;
			if (word.picture().isEmpty()) continue;
			fits.add(word);
		}
		if (fits.size() > 1 && last != null) fits.removeIf(word -> AnswerCommit.sameWord(word, last));
		if (fits.isEmpty()) return null;
		Word target = fits.get(random.nextInt(fits.size()));
		Letter answer = target.letters().getFirst();
		String rime = target.rime();
		List<Word> family = new ArrayList<>();
		for (Word word : words) {
			List<Letter> letters = word.letters();
			if (letters == null || letters.size() != 3 || !word.rime().equals(rime) || !word.outcome().usable()) continue;
			if (known.contains(letters.getFirst())) family.add(word);
		}
		if (!family.contains(target)) family.add(target);
		List<Letter> shelf = new ArrayList<>(List.of(answer));
		List<Word> others = new ArrayList<>(family);
		others.remove(target);
		java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
		for (Word other : others) {
			Letter tile = other.letters().getFirst();
			if (shelf.size() < WORD_TILES && shelf.stream().noneMatch(had -> soundAlike(had, tile))) shelf.add(tile);
		}
		// Short of real words: other known consonants, which build something that is not a word.
		List<Letter> fill = new ArrayList<>(known);
		java.util.Collections.shuffle(fill, new java.util.Random(random.nextLong()));
		for (Letter tile : fill) {
			if (shelf.size() >= WORD_TILES) break;
			if (tile.isVowel() || !tile.isSingle()) continue;
			if (shelf.stream().noneMatch(had -> soundAlike(had, tile))) shelf.add(tile);
		}
		java.util.Collections.shuffle(shelf, new java.util.Random(random.nextLong()));
		return new Puzzle(target, List.copyOf(family), List.copyOf(shelf), 0, Puzzle.Kind.FIRST);
	}

	public static int groupOf(Letter letter) {
		for (int i = 0; i < GROUPS.size(); i++) if (GROUPS.get(i).contains(letter)) return i;
		return -1;
	}
}
