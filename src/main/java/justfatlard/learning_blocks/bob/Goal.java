package justfatlard.learning_blocks.bob;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Something to get Bob to make: the sentence (or two) that makes it, read exactly as Bob reads
 * them, and so the picture they make. {@code key} is the sentences as written, one per row,
 * {@code "put cat in box / put lid on box"}: what mastery counts it by.
 */
public record Goal(String key, Shape shape, List<List<String>> rows, List<Instruction> steps) {

	/** The order sentences come in: a plain one, with big, with the and a, then two in a row where the order matters. */
	public enum Shape { SIMPLE, BIG, ARTICLES, RECIPE }

	/** A goal from its sentences, as written; throws on one Bob could not read. */
	public static Goal of(Shape shape, String... sentences) {
		List<List<String>> rows = new ArrayList<>();
		List<Instruction> steps = new ArrayList<>();
		for (String sentence : sentences) {
			List<String> words = List.of(sentence.split(" "));
			Instruction step = Instruction.parse(words);
			if (step == null) throw new IllegalArgumentException("Bob can't read \"" + sentence + "\"");
			rows.add(words);
			steps.add(step);
		}
		return new Goal(String.join(" / ", sentences), shape, List.copyOf(rows), List.copyOf(steps));
	}

	/** A short name for commands and tests: {@code cat-in-box}, {@code the-cat-in-the-box}, {@code cat-in-box+lid-on-box}. */
	public String id() {
		List<String> parts = new ArrayList<>();
		for (List<String> row : rows) {
			parts.add(String.join("-", row.subList(1, row.size())));
		}
		return String.join("+", parts).toLowerCase(Locale.ROOT);
	}

	/** Everything in it, in the order it is first named: what stands on Bob's stage to begin with. */
	public List<String> things() {
		LinkedHashSet<String> things = new LinkedHashSet<>();
		for (Instruction step : steps) {
			things.add(step.x());
			things.add(step.y());
		}
		return List.copyOf(things);
	}

	/** Every word on its cards, repeats and all: put, the, cat, in, the, box. */
	public List<String> cards() {
		List<String> cards = new ArrayList<>();
		for (List<String> row : rows) cards.addAll(row);
		return cards;
	}

	/** Where it all starts: everything on the floor. */
	public BobScene start() {
		return new BobScene(things());
	}

	/** What it looks like done right: each sentence done in turn. */
	public BobScene end() {
		BobScene scene = start();
		for (Instruction step : steps) scene.apply(step);
		return scene;
	}

	/** Whether these rows, done in turn from the start, make it. False for a row Bob can't read. */
	public boolean madeBy(List<List<String>> built) {
		BobScene scene = start();
		for (List<String> row : built) {
			Instruction step = Instruction.parse(row);
			if (step == null) return false;
			scene.apply(step);
		}
		return scene.equals(end());
	}
}
