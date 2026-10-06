package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * What comes after (or before): a bit of the number line on the board, 3 4 _, and the gap's tile
 * picked from three. Right, it goes in the gap.
 */
public final class NextRound extends NumbersRound {
	private final boolean after;
	private final int from;
	private final int gap;

	public NextRound(Room room, String which, int from, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.NEXT, which, stage, random);
		this.after = which.equals("after");
		this.from = from;
		this.gap = after ? from + 1 : from - 1;
		tiles(gap, 0, 10);
	}

	public int from() {
		return from;
	}

	public int gap() {
		return gap;
	}

	private int[] row() {
		return after ? new int[] {from - 1, from, -1} : new int[] {-1, from, from + 1};
	}

	private BlockPos gapSpot() {
		return layout.rowSpot(after ? 2 : 0, 3);
	}

	@Override
	protected void board() {
		int[] row = row();
		for (int i = 0; i < 3; i++) {
			int shown = row[i];
			if (shown < 0 && i != (after ? 2 : 0)) continue;
			set(layout.rowSpot(i, 3), i == (after ? 2 : 0) ? LearningBlocks.BOARD_LETTER.showing(Letter.BLANK)
				: shown < 0 || shown > 10 ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : LearningBlocks.NUMBER.showing(shown));
		}
	}

	@Override
	public Narration ask(Narration narration) {
		if (after) line(narration, "phrase/what_comes_after", "What comes after");
		else line(narration, "phrase/what_comes_before", "What comes before");
		return narration.say(NumberBlock.soundOf(from), "number:" + from, p -> {}, CaptionLine.plain(Numbers.word(from) + "?"), Narration.GAP);
	}

	@Override
	protected void showRight(ServerPlayer player, Choice right) {
		set(gapSpot(), LearningBlocks.NUMBER.showing(gap));
	}
}
