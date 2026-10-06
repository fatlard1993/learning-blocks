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
import org.jspecify.annotations.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * "Which pen has more?" (or fewer): two pens (a rug border) of creatures, left and right, and an equals
 * tile between them for "they're the same". Click a pen, or the tile. Right: that pen's creatures
 * glow, "more!". Wrong: both pens are counted aloud, and it is asked again.
 */
public final class PensRound extends NumbersRound {
	private final int left;
	private final int right;
	private final Counting.What what;
	private final List<Entity> leftOnes = new ArrayList<>();
	private final List<Entity> rightOnes = new ArrayList<>();
	private final Choice leftPen;
	private final Choice rightPen;
	private final Choice same;

	public PensRound(Room room, String question, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.PENS, question, stage, random);
		// Few enough to compare by eye: one to four a pen.
		int a = 1 + random.nextInt(4);
		int b = a;
		if (!question.equals("same")) while (b == a) b = 1 + random.nextInt(4);
		this.left = a;
		this.right = b;
		this.what = Counting.What.CREATURES.get(random.nextInt(Counting.What.CREATURES.size()));
		leftPen = new Choice("left", null, NumberBlock.soundOf(left), Numbers.word(left));
		rightPen = new Choice("right", null, NumberBlock.soundOf(right), Numbers.word(right));
		same = new Choice("same", LearningBlocks.NUMBER.showing(NumberBlock.EQUALS), "phrase/the_same", "the same");
		choices.add(leftPen);
		choices.add(same);
		choices.add(rightPen);
		Choice answer = switch (question) {
			case "same" -> same;
			case "more" -> left > right ? leftPen : rightPen;
			default -> left < right ? leftPen : rightPen;
		};
		answer(answer);
	}

	public int left() {
		return left;
	}

	public int right() {
		return right;
	}

	/** A pen's middle: left or right of the room's middle, as the child faces the board (west is their left). */
	private BlockPos penCentre(boolean isLeft) {
		return layout.origin().offset(isLeft ? -3 : 3, 0, -1);
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		return switch (i) {
			case 0 -> penCentre(true);
			case 2 -> penCentre(false);
			default -> layout.origin().offset(0, 0, -2);
		};
	}

	/** A pen is clicked as its rug and floor, or a creature in it. */
	@Override
	protected boolean boxed(Choice choice) {
		return choice == same;
	}

	/** A pen's rug or floor, clicked as a block: that pen. */
	@Override
	protected @Nullable Choice choiceAt(BlockPos pos) {
		for (boolean isLeft : new boolean[] {true, false}) {
			BlockPos centre = penCentre(isLeft);
			int dx = pos.getX() - centre.getX();
			int dz = pos.getZ() - centre.getZ();
			if (pos.getY() <= centre.getY() && pos.getY() >= centre.getY() - 1 && Math.abs(dx) <= 2 && dz >= -2 && dz <= 1) return isLeft ? leftPen : rightPen;
		}
		return null;
	}

	@Override
	protected float hitWidth(Choice choice) {
		return choice == same ? 1.1F : 4.8F;
	}

	@Override
	protected float hitHeight(Choice choice) {
		return choice == same ? 1.5F : 1.6F;
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		if (choice == same) {
			super.stand(choice, spot);
			return;
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 1; dz++) {
				// A flat rug of a border, so the creatures inside are in full view.
				if (Math.abs(dx) == 2 || dz == -2 || dz == 1) set(spot.offset(dx, 0, dz), Blocks.CARPET.brown().defaultBlockState());
			}
		}
	}

	@Override
	protected void board() {}

	@Override
	protected void started(ServerPlayer player) {
		fill(player, true, left, leftOnes);
		fill(player, false, right, rightOnes);
		for (Entity one : leftOnes) clickable(one, leftPen);
		for (Entity one : rightOnes) clickable(one, rightPen);
	}

	private void fill(ServerPlayer player, boolean isLeft, int n, List<Entity> into) {
		BlockPos centre = penCentre(isLeft);
		List<Vec3> spots = new ArrayList<>();
		for (int i = 0; i < n; i++) spots.add(Vec3.atBottomCenterOf(centre).add((i % 2 - 0.5) * 1.3, 0, -1.0 + (i / 2) * 0.9));
		into.addAll(Counting.dispense(this, player, what, n, spots, 3, null, 0.6F));
	}

	@Override
	public Narration ask(Narration narration) {
		if (target.equals("fewer")) line(narration, "phrase/which_has_fewer", "Which pen has fewer?");
		else line(narration, "phrase/which_has_more", "Which pen has more?");
		return line(narration, "phrase/more_or_same", "Or are they the same?");
	}

	private Narration count(Narration telling, List<Entity> ones, int n, String tag) {
		return telling.say(NumberBlock.soundOf(n), tag, p -> ones.forEach(one -> Counting.glow(one, true)), CaptionLine.plain(Numbers.word(n)), 6)
			.then(tag + ":off", p -> ones.forEach(one -> Counting.glow(one, false)), 2);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		count(telling, leftOnes, left, "count:left");
		count(telling, rightOnes, right, "count:right");
		if (picked == same) line(telling, "phrase/the_same", "They're the same!");
		else {
			List<Entity> ones = picked == leftPen ? leftOnes : rightOnes;
			telling.say("word/" + target, "right:" + target, p -> ones.forEach(one -> Counting.glow(one, true)), CaptionLine.plain(target + "!"), AnswerNarration.WORD_GAP)
				.then("right:off", p -> ones.forEach(one -> Counting.glow(one, false)), 2);
		}
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		telling.then("wiggle", p -> wiggle(spotOf(picked)), 2);
		count(telling, leftOnes, left, "count:left");
		count(telling, rightOnes, right, "count:right");
	}
}
