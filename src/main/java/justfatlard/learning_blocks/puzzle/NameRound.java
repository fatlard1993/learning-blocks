package justfatlard.learning_blocks.puzzle;

import net.minecraft.core.BlockPos;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * A number's name read: the word ("six") spelled on the board in letter tiles, and the tile with
 * that many dots picked. Reading and Numbers together: only for a word the child has read in
 * Reading. The word is never said until it is right.
 */
public final class NameRound extends NumbersRound {
	private final int n;

	public NameRound(Room room, int n, int lo, int hi, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.NAME, String.valueOf(n), stage, random);
		this.n = n;
		tiles(n, lo, hi);
	}

	/** Under the word, which fills the middle of the board. */
	@Override
	protected BlockPos tileGap() {
		return layout.rowSpot(0, 1).below();
	}

	@Override
	protected void board() {
		String word = Numbers.word(n);
		for (int i = 0; i < word.length(); i++) {
			Letter letter = Letter.of(String.valueOf(word.charAt(i)));
			if (letter != null) set(layout.rowSpot(i, word.length()), LearningBlocks.BOARD_LETTER.showing(letter));
		}
	}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/read_the_number", "Read it! Which number is it?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say("word/" + Numbers.word(n), "right:word", p -> {}, CaptionLine.plain(Numbers.word(n)), 4)
			.say(NumberBlock.soundOf(n), "right:" + n, p -> {}, CaptionLine.plain(Numbers.word(n) + "!"), AnswerNarration.WORD_GAP);
	}
}
