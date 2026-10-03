package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subject;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A round of Numbers: its kind and target, the stage it counts towards, and the number tiles most
 * kinds are answered with. The kinds are in their own classes; {@link Numbers} picks them.
 */
public abstract class NumbersRound extends SubjectRound {
	protected final NumberCourse.Kind kind;
	protected final String target;
	protected final int stage;
	protected final RandomSource random;
	private @Nullable Choice answer;

	protected NumbersRound(Room room, NumberCourse.Kind kind, String target, int stage, RandomSource random) {
		super(room);
		this.kind = kind;
		this.target = target;
		this.stage = stage;
		this.random = random;
	}

	@Override
	public Subject subject() {
		return Numbers.INSTANCE;
	}

	@Override
	public String kind() {
		return kind.id;
	}

	public String target() {
		return target;
	}

	@Override
	public String item() {
		return kind.item(target);
	}

	@Override
	protected Choice answer() {
		return answer;
	}

	protected void answer(Choice answer) {
		this.answer = answer;
	}

	@Override
	protected boolean levelDone(Progress progress) {
		return NumberCourse.stageDone(progress, stage);
	}

	@Override
	protected String levelKey() {
		return "numbers:N" + stage;
	}

	@Override
	protected String nextLevelLine() {
		return switch (stage) {
			case 0 -> "phrase/next_level_numbers";
			case 1 -> "phrase/next_level_counting";
			default -> "phrase/next_level_comparing";
		};
	}

	@Override
	protected String nextLevelWords() {
		return switch (stage) {
			case 0 -> "You know your numbers! Ready for more numbers!";
			case 1 -> "You can count to ten! Ready for more!";
			default -> "You know more and fewer! Ready for more numbers!";
		};
	}

	/** A number tile to pick. */
	protected static Choice tile(int n) {
		return new Choice(String.valueOf(n), LearningBlocks.NUMBER.showing(n), NumberBlock.soundOf(n), Numbers.word(n));
	}

	/** Three tiles, shuffled: the answer and two others near it, within {@code lo} to {@code hi}. */
	protected void tiles(int right, int lo, int hi) {
		List<Integer> others = new ArrayList<>();
		for (int n = lo; n <= hi; n++) if (n != right) others.add(n);
		// Near ones first: the ones a child mixes up are close by (and 6 and 9, 2 and 5 look alike).
		Collections.shuffle(others, new java.util.Random(random.nextLong()));
		others.sort(java.util.Comparator.comparingInt(n -> Math.min(Math.abs(n - right), looksLike(n, right) ? 0 : 99)));
		List<Integer> shown = new ArrayList<>(others.subList(0, Math.min(2, others.size())));
		shown.add(right);
		Collections.shuffle(shown, new java.util.Random(random.nextLong()));
		for (int n : shown) {
			Choice choice = tile(n);
			choices.add(choice);
			if (n == right) answer(choice);
		}
	}

	/** The numerals children mix up by their look. */
	static boolean looksLike(int a, int b) {
		return a == 6 && b == 9 || a == 9 && b == 6 || a == 2 && b == 5 || a == 5 && b == 2;
	}

	/** The number a tile choice is. */
	protected static int number(Choice choice) {
		try {
			return Integer.parseInt(choice.id());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** A sentence read aloud a word at a time, lit as it goes: numbers by their number clips. */
	protected static Narration read(Narration narration, List<String> words) {
		for (int i = 0; i < words.size(); i++) {
			String word = words.get(i);
			String clip = word.chars().allMatch(Character::isDigit) ? NumberBlock.soundOf(Integer.parseInt(word)) : "word/" + word;
			List<String> shown = words.stream().map(w -> w.chars().allMatch(Character::isDigit) ? Numbers.word(Integer.parseInt(w)) : w).toList();
			narration.say(clip, "read:" + i, p -> {}, CaptionLine.sentence(shown, i), 3);
		}
		return narration;
	}
}
