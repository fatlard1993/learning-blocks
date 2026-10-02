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

	private static final List<Goal> ALL = List.of(
		Goal.of(Goal.Shape.SIMPLE, "put cat in box"),
		Goal.of(Goal.Shape.SIMPLE, "put hat on dog"),
		Goal.of(Goal.Shape.SIMPLE, "put rat in tub"),
		Goal.of(Goal.Shape.SIMPLE, "put cat on bed"),
		Goal.of(Goal.Shape.SIMPLE, "put cap on pig"),
		Goal.of(Goal.Shape.SIMPLE, "put ant in pot"),
		Goal.of(Goal.Shape.SIMPLE, "put dog on rug"),
		Goal.of(Goal.Shape.SIMPLE, "put fox in bin"),
		Goal.of(Goal.Shape.SIMPLE, "put cat on pig"),
		Goal.of(Goal.Shape.SIMPLE, "put wig on cat"),
		Goal.of(Goal.Shape.SIMPLE, "put bug in box"),
		Goal.of(Goal.Shape.SIMPLE, "put hen on bed"),
		Goal.of(Goal.Shape.SIMPLE, "put duck in tub"),
		Goal.of(Goal.Shape.SIMPLE, "put crab in jar"),
		Goal.of(Goal.Shape.SIMPLE, "put rat under crib"),
		Goal.of(Goal.Shape.SIMPLE, "put cat under desk"),
		Goal.of(Goal.Shape.BIG, "put big cat in box"),
		Goal.of(Goal.Shape.BIG, "put hat on big dog"),
		Goal.of(Goal.Shape.BIG, "put rat in big tub"),
		Goal.of(Goal.Shape.BIG, "put big pig on rug"),
		Goal.of(Goal.Shape.ARTICLES, "put the cat in the box"),
		Goal.of(Goal.Shape.ARTICLES, "put a hat on the pig"),
		Goal.of(Goal.Shape.ARTICLES, "put a rat in the tub"),
		Goal.of(Goal.Shape.ARTICLES, "put the big dog on the rug"),
		Goal.of(Goal.Shape.RECIPE, "put cat in box", "put lid on box"),
		Goal.of(Goal.Shape.RECIPE, "put ant in pot", "put lid on pot"),
		Goal.of(Goal.Shape.RECIPE, "put crab in jar", "put lid on jar"),
		Goal.of(Goal.Shape.RECIPE, "put hat on cat", "put cat on bed"));

	public static List<Goal> all() {
		return ALL;
	}

	public static @Nullable Goal byId(String id) {
		for (Goal goal : ALL) if (goal.id().equals(id) || goal.key().equals(id)) return goal;
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
			List<Goal> these = ALL.stream().filter(goal -> goal.shape() == shape && goal.cards().stream().allMatch(readable)).toList();
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
