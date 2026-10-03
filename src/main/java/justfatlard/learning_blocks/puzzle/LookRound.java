package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import org.jspecify.annotations.Nullable;

/**
 * A quick look: a die's face or a ten-frame of dots flashes on the board for two seconds, "How
 * many?", and the tile is picked. Seen at a glance, not counted one by one: that is the point.
 * The button flashes it again.
 */
public final class LookRound extends NumbersRound {
	/** How long the dots are up. */
	static final int FLASH = 40;
	private final int n;
	private Display.@Nullable BlockDisplay shown;

	public LookRound(Room room, int n, int lo, int hi, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.LOOK, String.valueOf(n), stage, random);
		this.n = n;
		tiles(n, lo, hi);
	}

	@Override
	protected void board() {}

	private void flash(boolean on) {
		if (shown != null) shown.discard();
		shown = on ? Counting.boardDots(this, n, 2.2F) : null;
	}

	/** Whether the dots are up just now, for tests. */
	public boolean showing() {
		return shown != null && shown.isAlive();
	}

	@Override
	public Narration ask(Narration narration) {
		line(narration, "phrase/quick_look", "Quick, look!");
		narration.then("flash", p -> flash(true), FLASH);
		narration.then("unflash", p -> flash(false), 2);
		return line(narration, "phrase/how_many", "How many?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say(NumberBlock.soundOf(n), "right:" + n, p -> flash(true), CaptionLine.plain(Numbers.word(n) + "!"), AnswerNarration.WORD_GAP);
	}
}
