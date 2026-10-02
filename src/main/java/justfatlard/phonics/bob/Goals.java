package justfatlard.phonics.bob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Every goal Bob can be asked to make, in the order they come: plain sentences first, then with
 * big, with the and a, and two in a row where doing them the other way round goes wrong (the lid
 * on first, and the cat sits on the lid).
 */
public final class Goals {
	private Goals() {}

	/** The goals written by hand, in order: shape, then the sentence or two. */
	private static final String[][] CURATED = {
		{"SIMPLE", "put cat in box"},
		{"SIMPLE", "put hat on dog"},
		{"SIMPLE", "put rat in tub"},
		{"SIMPLE", "put cat on bed"},
		{"SIMPLE", "put cap on pig"},
		{"SIMPLE", "put ant in pot"},
		{"SIMPLE", "put dog on rug"},
		{"SIMPLE", "put fox in bin"},
		{"SIMPLE", "put cat on pig"},
		{"SIMPLE", "put wig on cat"},
		{"SIMPLE", "put bug in box"},
		{"SIMPLE", "put hen on bed"},
		{"SIMPLE", "put duck in tub"},
		{"SIMPLE", "put crab in jar"},
		{"SIMPLE", "put rat under crib"},
		{"SIMPLE", "put cat under desk"},
		{"BIG", "put big cat in box"},
		{"BIG", "put hat on big dog"},
		{"BIG", "put rat in big tub"},
		{"BIG", "put big pig on rug"},
		{"ARTICLES", "put the cat in the box"},
		{"ARTICLES", "put a hat on the pig"},
		{"ARTICLES", "put a rat in the tub"},
		{"ARTICLES", "put the big dog on the rug"},
		{"RECIPE", "put cat in box", "put lid on box"},
		{"RECIPE", "put ant in pot", "put lid on pot"},
		{"RECIPE", "put crab in jar", "put lid on jar"},
		{"RECIPE", "put hat on cat", "put cat on bed"},
	};

	private static volatile List<Goal> all;

	static {
		justfatlard.phonics.relate.Relations.onReload(() -> all = null);
	}

	/**
	 * Every goal: the ones written by hand, then, made from the things' affordances, one for each
	 * small thing and each thing it can go in, on or ride ("put doll in crib", "put owl in car",
	 * "put cap on owl"), all plain sentences. Which a child is given still depends on the words they
	 * read.
	 */
	public static List<Goal> all() {
		List<Goal> made = all;
		if (made != null) return made;
		java.util.LinkedHashMap<String, Goal> goals = new java.util.LinkedHashMap<>();
		for (String[] row : CURATED) {
			try {
				Goal goal = Goal.of(Goal.Shape.valueOf(row[0]), java.util.Arrays.copyOfRange(row, 1, row.length));
				goals.put(goal.key(), goal);
			} catch (IllegalArgumentException e) {
				justfatlard.phonics.Phonics.LOGGER.warn("[{}] Leaving out Bob's goal {}: {}", justfatlard.phonics.Phonics.MOD_ID, String.join(" / ",
					java.util.Arrays.copyOfRange(row, 1, row.length)), e.getMessage());
			}
		}
		int curated = goals.size();
		for (Goal goal : generated()) goals.putIfAbsent(goal.key(), goal);
		made = List.copyOf(goals.values());
		justfatlard.phonics.Phonics.LOGGER.info("[{}] Bob's goals: {} written, {} made from the things' affordances", justfatlard.phonics.Phonics.MOD_ID,
			curated, made.size() - curated);
		all = made;
		return made;
	}

	/** Goals the things themselves suggest: each small thing with each thing that would take it, in the world as for Bob. */
	static List<Goal> generated() {
		List<Goal> out = new ArrayList<>();
		for (justfatlard.phonics.relate.Thing held : justfatlard.phonics.relate.Relations.all()) {
			if (held.creature() || !held.small() || held.word().equals("bug") && justfatlard.phonics.relate.Relations.thing("ladybug") == null) continue;
			for (justfatlard.phonics.relate.Thing target : justfatlard.phonics.relate.Relations.all()) {
				var fit = justfatlard.phonics.relate.Relations.fit(held, target);
				if (fit == null) continue;
				String prep = switch (fit.rel()) {
					case IN -> "in";
					case RIDES -> "in";
					default -> "on";
				};
				String sentence = "put " + held.word() + " " + prep + " " + target.word();
				try {
					Goal goal = Goal.of(Goal.Shape.SIMPLE, sentence);
					// Only a goal whose sentence makes the very place the world would put it.
					var placement = justfatlard.phonics.relate.Relations.resolve(held.word(), prep, target.word(), word -> null);
					if (placement != null && placement.rel() == fit.rel()) out.add(goal);
				} catch (IllegalArgumentException e) {
					// Not a sentence Bob can read: left out.
				}
			}
		}
		return out;
	}

	public static @Nullable Goal byId(String id) {
		for (Goal goal : all()) if (goal.id().equals(id) || goal.key().equals(id)) return goal;
		return null;
	}

	/** How many of a shape a child masters before the next opens. */
	public static final int OPENS_NEXT = 2;

	/**
	 * The goals a child can be given: every word in them one they can read ({@code readable}), and
	 * a shape open once {@link #OPENS_NEXT} of the one before are mastered (or it has nothing left).
	 */
	public static List<Goal> open(Predicate<String> readable, Predicate<Goal> mastered) {
		List<Goal> open = new ArrayList<>();
		for (Goal.Shape shape : Goal.Shape.values()) {
			List<Goal> these = all().stream().filter(goal -> goal.shape() == shape && goal.cards().stream().allMatch(readable)).toList();
			open.addAll(these);
			long done = these.stream().filter(mastered).count();
			boolean exhausted = these.stream().allMatch(mastered);
			if (done < OPENS_NEXT && !exhausted) break;
		}
		return open;
	}

	/**
	 * The next goal: one not yet mastered and not done today, the fewest clean first, else any open
	 * one. Null if none is open.
	 */
	public static @Nullable Goal pick(List<Goal> open, Predicate<Goal> mastered, Predicate<Goal> usedToday,
			java.util.function.ToIntFunction<Goal> clean, @Nullable Goal last, Random random) {
		if (open.isEmpty()) return null;
		List<Goal> from = new ArrayList<>(open.stream().filter(goal -> !mastered.test(goal) && !usedToday.test(goal)).toList());
		if (from.isEmpty()) from = new ArrayList<>(open.stream().filter(goal -> !usedToday.test(goal)).toList());
		if (from.isEmpty()) from = new ArrayList<>(open);
		if (from.size() > 1 && last != null) from.remove(last);
		Collections.shuffle(from, random);
		from.sort(java.util.Comparator.comparingInt(clean));
		return from.getFirst();
	}

	/** The extra cards on the floor, words that make some other sentence: another thing, another little word. */
	public static List<String> decoys(Goal goal, Predicate<String> readable, int count, Random random) {
		List<String> nouns = new ArrayList<>();
		for (Noun noun : Noun.all()) {
			if (!goal.things().contains(noun.word()) && readable.test(noun.word())) nouns.add(noun.word());
		}
		Collections.shuffle(nouns, random);
		List<String> little = new ArrayList<>();
		for (String prep : Noun.PREPOSITIONS) if (!goal.cards().contains(prep) && readable.test(prep)) little.add(prep);
		if (goal.shape() == Goal.Shape.ARTICLES) {
			for (String article : Noun.ARTICLES) if (!goal.cards().contains(article) && readable.test(article)) little.add(article);
		}
		Collections.shuffle(little, random);
		List<String> out = new ArrayList<>();
		// A thing first, then a little word, then another thing: the decoys a child is most likely to weigh.
		if (!nouns.isEmpty() && out.size() < count) out.add(nouns.removeFirst());
		if (!little.isEmpty() && out.size() < count) out.add(little.removeFirst());
		if (!nouns.isEmpty() && out.size() < count) out.add(nouns.removeFirst());
		return out;
	}

	/** One way a sentence can be wrong, and what it says instead. */
	public enum Mistake { NOUN, LITTLE_WORD, ORDER }

	/** A sentence that is wrong in one way, and the card it needs back. */
	public record Wrong(Mistake mistake, List<String> row, List<String> needed) {}

	/**
	 * This one-sentence goal said wrong, one of three ways: a thing swapped for another (put cat in
	 * tub), the little word swapped (put cat on box), or the two things the wrong way round (put box
	 * in cat). Always a sentence Bob can read that makes something else. Null for a goal of two
	 * sentences, or when no such sentence can be made from words the child reads.
	 */
	public static @Nullable Wrong wrong(Goal goal, @Nullable Mistake wanted, Predicate<String> readable, Random random) {
		if (goal.rows().size() != 1) return null;
		List<String> row = goal.rows().getFirst();
		Instruction step = goal.steps().getFirst();
		List<Mistake> ways = new ArrayList<>(List.of(Mistake.values()));
		Collections.shuffle(ways, random);
		if (wanted != null) {
			ways.remove(wanted);
			ways.addFirst(wanted);
		}
		for (Mistake way : ways) {
			List<Wrong> options = new ArrayList<>();
			switch (way) {
				case NOUN -> {
					Noun ny = Noun.of(step.y());
					for (Noun other : Noun.all()) {
						if (ny == null || other.kind() != ny.kind() || goal.things().contains(other.word()) || !readable.test(other.word())) continue;
						List<String> changed = replaceLast(row, step.y(), other.word());
						options.add(new Wrong(way, changed, List.of(step.y())));
					}
				}
				case LITTLE_WORD -> {
					for (String prep : Noun.PREPOSITIONS) {
						if (prep.equals(step.prep()) || !readable.test(prep)) continue;
						options.add(new Wrong(way, replaceLast(row, step.prep(), prep), List.of(step.prep())));
					}
				}
				case ORDER -> {
					List<String> swapped = new ArrayList<>(row);
					int ix = swapped.indexOf(step.x());
					int iy = swapped.lastIndexOf(step.y());
					if (ix >= 0 && iy >= 0) {
						swapped.set(ix, step.y());
						swapped.set(iy, step.x());
						options.add(new Wrong(way, swapped, List.of()));
					}
				}
			}
			options.removeIf(option -> {
				Instruction said = Instruction.parse(option.row());
				return said == null || goal.madeBy(List.of(option.row()));
			});
			if (!options.isEmpty()) return options.get(random.nextInt(options.size()));
		}
		return null;
	}

	private static List<String> replaceLast(List<String> row, String from, String to) {
		List<String> out = new ArrayList<>(row);
		int at = out.lastIndexOf(from);
		if (at >= 0) out.set(at, to);
		return out;
	}
}
