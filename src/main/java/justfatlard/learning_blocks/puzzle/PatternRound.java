package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Subject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * "What comes next?": the Logic stub's one kind. On the board, a pattern of two colours turn and
 * turn about (red, blue, red, blue, red) and a gap; on the floor, three coloured blocks, the next
 * one and a third colour among them. The right one goes into the gap.
 */
public final class PatternRound extends SubjectRound {
	private static final int SHOWN = 5;
	private final DyeColor a;
	private final DyeColor b;
	private final Choice answer;
	private BlockPos gap = BlockPos.ZERO;

	public PatternRound(Room room, DyeColor a, DyeColor b, DyeColor other, RandomSource random) {
		super(room);
		this.a = a;
		this.b = b;
		List<DyeColor> shown = new ArrayList<>(List.of(a, b, other));
		Collections.shuffle(shown, new java.util.Random(random.nextLong()));
		Choice right = null;
		for (DyeColor colour : shown) {
			Choice choice = new Choice(colour.getSerializedName(), Blocks.WOOL.pick(colour).defaultBlockState(), "word/" + colour.getSerializedName(), colour.getSerializedName());
			choices.add(choice);
			// After a, b, a, b, a comes b.
			if (colour == b) right = choice;
		}
		this.answer = right;
	}

	@Override
	public Subject subject() {
		return Logic.INSTANCE;
	}

	@Override
	public String kind() {
		return Logic.NEXT;
	}

	@Override
	public String item() {
		return Logic.AB;
	}

	public DyeColor next() {
		return b;
	}

	@Override
	protected Choice answer() {
		return answer;
	}

	@Override
	protected void board() {
		for (int i = 0; i < SHOWN; i++) set(layout.rowSpot(i, SHOWN + 1), Blocks.WOOL.pick(i % 2 == 0 ? a : b).defaultBlockState());
		gap = layout.rowSpot(SHOWN, SHOWN + 1);
		set(gap, justfatlard.learning_blocks.LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
	}

	@Override
	protected void showRight(ServerPlayer player, Choice right) {
		set(gap, right.block());
	}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/what_comes_next", "What comes next?");
	}

	@Override
	protected boolean levelDone(Progress progress) {
		return Logic.INSTANCE.levelDone(progress);
	}

	@Override
	protected String levelKey() {
		return "logic:1";
	}

	@Override
	protected String nextLevelLine() {
		return "phrase/next_level_logic";
	}

	@Override
	protected String nextLevelWords() {
		return "You can find what comes next! Ready for trickier ones!";
	}
}
