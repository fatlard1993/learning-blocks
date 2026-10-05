package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * "Make five!": the child puts a number tile in the gap on the board and presses the button, and
 * exactly that many things come out. The right number: counted, glowing. A smaller one: "Hmm, not
 * enough!". A bigger one: that many chickens, all at once - "Whoa, that's too many!" - and the
 * gap is empty again to try another.
 */
public final class MakeRound extends NumbersRound {
	private final int n;
	private final Counting.What what;
	private @Nullable Choice chosen;
	private final List<Entity> out = new ArrayList<>();

	public MakeRound(Room room, int n, int lo, int hi, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.MAKE, String.valueOf(n), stage, random);
		this.n = n;
		List<Counting.What> things = List.of(Counting.What.APPLE, Counting.What.CARROT, Counting.What.CAKE, Counting.What.PIG, Counting.What.SHEEP);
		this.what = things.get(random.nextInt(things.size()));
		tiles(n, Math.max(1, lo), hi);
	}

	private BlockPos gap() {
		return layout.rowSpot(0, 1);
	}

	@Override
	protected void board() {
		set(gap(), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
	}

	@Override
	public Narration ask(Narration narration) {
		line(narration, "phrase/make", "Make");
		narration.say(NumberBlock.soundOf(n), "number:" + n, p -> {}, CaptionLine.plain(Numbers.word(n) + "!"), 6);
		return line(narration, "phrase/put_number_press", "Put a number in the gap and press the button!");
	}

	@Override
	protected Verdict judge(ServerPlayer player, Choice picked) {
		return Verdict.HOLD;
	}

	/** A tile into the gap (the one there before goes back): said, and waiting for the button. */
	@Override
	protected void hold(ServerPlayer player, Choice picked, Narration telling) {
		chosen = picked;
		telling.say(picked.clip(), "in_gap:" + picked.id(), p -> set(gap(), LearningBlocks.NUMBER.showing(number(picked))),
			CaptionLine.plain(picked.said()), 4);
	}

	@Override
	protected boolean waitingForButton() {
		return chosen != null;
	}

	/** The tile in the gap just now, for tests. */
	public @Nullable Choice chosen() {
		return chosen;
	}

	@Override
	protected boolean press(ServerPlayer player) {
		Choice picked = chosen;
		if (picked == null) return false;
		int made = number(picked);
		boolean right = made == n;
		Narration before = new Narration();
		List<Vec3> spots = new ArrayList<>();
		for (int i = 0; i < made; i++) spots.add(Counting.spot(layout, i, made));
		if (made > n) {
			// Too many: all at once, and chickens, whatever was asked for.
			before.then("burst", p -> out.addAll(Counting.dispense(this, p, Counting.What.CHICKEN, made, spots, 0, null)), Counting.dispenseTicks(1, 0) + 6);
			line(before, "phrase/too_many", "Whoa, that's too many!");
		} else {
			before.then("make", p -> out.addAll(Counting.dispense(this, p, what, made, spots, 3, null)), Counting.dispenseTicks(made, 3));
			if (right) Counting.chant(before, n, out, false);
			else line(before, "phrase/not_enough", "Hmm, not enough!");
		}
		if (!right) {
			before.then("tidy", p -> {
				for (Entity thing : out) {
					level.sendParticles(ParticleTypes.POOF, thing.getX(), thing.getY() + 0.3, thing.getZ(), 4, 0.15, 0.15, 0.15, 0.01);
					thing.discard();
				}
				out.clear();
				set(gap(), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
			}, 4);
			chosen = null;
		}
		conclude(player, picked, right, before);
		return true;
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say(NumberBlock.soundOf(n), "right:" + n, p -> {}, CaptionLine.plain(Numbers.word(n) + " " + what.plural + "!"), AnswerNarration.WORD_GAP);
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {}

	/** What the button made, for tests. */
	public List<Entity> out() {
		return List.copyOf(out);
	}
}
