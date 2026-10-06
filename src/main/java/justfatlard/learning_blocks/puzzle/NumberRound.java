package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * "Which block says... five?", as the letters level asks for a letter: the number said, three
 * tiles on the floor, distractors from the numbers around it. The right one: the chant, "five!
 * one, two, three, four, five!", while five things pop out of the button, each glowing as it is
 * counted. Another: it says its own name, gently, and the number is asked for again.
 */
public final class NumberRound extends NumbersRound {
	private final int n;
	private final Counting.What what;
	private final List<Entity> popped = new ArrayList<>();

	public NumberRound(Room room, int n, int lo, int hi, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.FIND, String.valueOf(n), stage, random);
		this.n = n;
		List<Counting.What> things = List.of(Counting.What.APPLE, Counting.What.PIG, Counting.What.CHICKEN, Counting.What.CARROT, Counting.What.SHEEP);
		this.what = things.get(random.nextInt(things.size()));
		tiles(n, lo, hi);
	}

	public int number() {
		return n;
	}

	@Override
	protected void board() {}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/which_block_says", "Which block says")
			.say(NumberBlock.soundOf(n), "number:" + n, p -> {}, CaptionLine.plain(Numbers.word(n) + "?"), Narration.GAP);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		if (n == 0) {
			// Zero: nothing comes out at all, which is the point.
			telling.say(NumberBlock.soundOf(0), "right:0", p -> wiggle(layout.button()), CaptionLine.plain("zero! nothing at all!"), AnswerNarration.WORD_GAP);
			return;
		}
		List<net.minecraft.world.phys.Vec3> spots = new ArrayList<>();
		for (int i = 0; i < n; i++) spots.add(Counting.spot(layout, i, n));
		telling.then("pop", p -> popped.addAll(Counting.dispense(this, p, what, n, spots, 3, null)), Counting.dispenseTicks(n, 3));
		Counting.chant(telling, n, popped, true);
	}

	@ForTests
	public List<Entity> popped() {
		return List.copyOf(popped);
	}
}
