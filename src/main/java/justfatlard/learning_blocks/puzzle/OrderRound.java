package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberCourse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * A number line: the tiles 1 to 5 (or 1 to 10) jumbled on the floor, "Put the numbers in order!"
 * Each one clicked in its turn goes up to the next place on the board, saying itself; one out of
 * turn wiggles and says its name, and stays. The last one in, the row is counted.
 */
public final class OrderRound extends NumbersRound {
	private final int n;
	private int done;

	public OrderRound(Room room, int n, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.ORDER, String.valueOf(n), stage, random);
		this.n = n;
		List<Integer> jumbled = new ArrayList<>();
		for (int i = 1; i <= n; i++) jumbled.add(i);
		Collections.shuffle(jumbled, new java.util.Random(random.nextLong()));
		for (int i : jumbled) choices.add(tile(i));
		answer(choices.stream().filter(choice -> number(choice) == 1).findFirst().orElseThrow());
	}

	@Override
	protected BlockPos spotFor(int i, int count) {
		if (count <= 5) return layout.shelfSpot(i, count);
		// Ten side by side, across the room.
		return layout.origin().offset(i - 5, 0, -2);
	}

	@Override
	protected float hitWidth(Choice choice) {
		return n > 5 ? 0.95F : super.hitWidth(choice);
	}

	/** The board's place for the k-th number (0 for 1): a row of five, and a second row above for 1-5 when there are ten. */
	private BlockPos slot(int k) {
		if (n <= 5) return layout.rowSpot(k, n);
		return k < 5 ? layout.rowSpot(k, 5).above() : layout.rowSpot(k - 5, 5);
	}

	/** The next empty place in the row. */
	@Override
	protected @org.jspecify.annotations.Nullable BlockPos tileGap() {
		return done < n ? slot(done) : null;
	}

	/** Any empty place in the row: the tile goes in the next one whichever was aimed at. */
	@Override
	protected boolean aimedAtGap(BlockPos hit, BlockPos target) {
		for (int k = done; k < n; k++) if (hit.equals(slot(k)) || target.equals(slot(k))) return true;
		return super.aimedAtGap(hit, target);
	}

	@Override
	protected void board() {
		for (int k = 0; k < n; k++) set(slot(k), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
	}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/put_in_order", "Put the numbers in order!");
	}

	@ForTests
	public int done() {
		return done;
	}

	@ForTests
	public Choice next() {
		return choices.stream().filter(choice -> number(choice) == done + 1).findFirst().orElseThrow();
	}

	@Override
	protected Choice answer() {
		return next();
	}

	@Override
	protected Verdict judge(ServerPlayer player, Choice picked) {
		if (number(picked) != done + 1) return Verdict.WRONG;
		return done + 1 == n ? Verdict.RIGHT : Verdict.HOLD;
	}

	private void up(Choice picked) {
		BlockPos from = spotOf(picked);
		if (from != null) level.setBlock(from, Blocks.AIR.defaultBlockState(), RoomLayout.FLAGS);
		unhit(picked);
		set(slot(number(picked) - 1), LearningBlocks.NUMBER.showing(number(picked)));
	}

	@Override
	protected void hold(ServerPlayer player, Choice picked, Narration telling) {
		done++;
		telling.say(picked.clip(), "up:" + picked.id(), p -> up(picked), CaptionLine.plain(picked.said()), 4);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		done++;
		telling.say(picked.clip(), "up:" + picked.id(), p -> up(picked), CaptionLine.plain(picked.said()), 6);
		Counting.chant(telling, n, List.of(), false);
	}
}
