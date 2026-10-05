package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Towers: two stacks of wooden blocks. "Which tower is taller?" (or shorter): click a tower. Or
 * "How many more blocks to make them the same?": pick the tile, and the blocks fly onto the short
 * one, each glowing as it is counted, until they match.
 */
public final class TowersRound extends NumbersRound {
	/** Small blocks, and at most four: both towers stay in view from where a child stands. */
	private static final float BLOCK = 0.45F;
	private final int left;
	private final int right;
	private final List<Entity> leftBlocks = new ArrayList<>();
	private final List<Entity> rightBlocks = new ArrayList<>();
	private final List<Entity> added = new ArrayList<>();
	private final boolean towersPicked;
	private Choice leftTower;
	private Choice rightTower;

	public TowersRound(Room room, String question, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.TOWERS, question, stage, random);
		int a = 1 + random.nextInt(4);
		int b = a;
		while (b == a) b = 1 + random.nextInt(4);
		this.left = a;
		this.right = b;
		this.towersPicked = !question.equals("diff");
		if (towersPicked) {
			leftTower = new Choice("left", null, NumberBlock.soundOf(left), Numbers.word(left));
			rightTower = new Choice("right", null, NumberBlock.soundOf(right), Numbers.word(right));
			choices.add(leftTower);
			choices.add(rightTower);
			boolean leftTaller = left > right;
			answer(question.equals("taller") == leftTaller ? leftTower : rightTower);
		} else {
			tiles(Math.abs(left - right), 1, 6);
		}
	}

	private BlockPos foot(boolean isLeft) {
		return layout.origin().offset(isLeft ? -3 : 3, 0, -3);
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		// The towers themselves, or the tiles in a row between them.
		return towersPicked ? foot(i == 0) : layout.origin().offset(i - 1, 0, -2);
	}

	@Override
	protected float hitWidth(Choice choice) {
		return towersPicked ? 1.1F : 0.95F;
	}

	@Override
	protected float hitHeight(Choice choice) {
		return towersPicked ? Math.max(left, right) * BLOCK + 0.2F : super.hitHeight(choice);
	}

	private static BlockState wood() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	private Vec3 storey(boolean isLeft, int i) {
		return Vec3.atBottomCenterOf(foot(isLeft)).add(0, i * BLOCK, 0);
	}

	@Override
	protected void board() {}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		if (!towersPicked) super.stand(choice, spot);
	}

	@Override
	protected void started(ServerPlayer player) {
		build(true, left, leftBlocks);
		build(false, right, rightBlocks);
	}

	private void build(boolean isLeft, int n, List<Entity> into) {
		for (int i = 0; i < n; i++) {
			Entity block = Counting.block(level, wood(), BLOCK);
			if (block == null) continue;
			Vec3 at = storey(isLeft, i);
			block.snapTo(at.x, at.y, at.z, 0, 0);
			add(block);
			into.add(block);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		return switch (target) {
			case "taller" -> line(narration, "phrase/which_is_taller", "Which tower is taller?");
			case "shorter" -> line(narration, "phrase/which_is_shorter", "Which tower is shorter?");
			default -> line(narration, "phrase/how_many_more_blocks", "How many more blocks to make them the same?");
		};
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		if (towersPicked) {
			List<Entity> tower = picked == leftTower ? leftBlocks : rightBlocks;
			telling.say("word/" + target, "right:" + target, p -> tower.forEach(b -> Counting.glow(b, true)), CaptionLine.plain(target + "!"), AnswerNarration.WORD_GAP)
				.then("right:off", p -> tower.forEach(b -> Counting.glow(b, false)), 2);
			return;
		}
		boolean leftShort = left < right;
		int from = Math.min(left, right);
		int more = Math.abs(left - right);
		telling.then("stack", p -> {
			for (int i = 0; i < more; i++) {
				Entity block = Counting.block(level, wood(), BLOCK);
				if (block == null) continue;
				added.add(block);
				Counting.fly(this, p, block, storey(leftShort, from + i), i * 5, null);
			}
		}, Counting.dispenseTicks(more, 5));
		Counting.chant(telling, more, added, false);
		line(telling, "phrase/the_same", "They're the same!");
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		if (!towersPicked) {
			super.tellWrong(player, picked, telling);
			return;
		}
		telling.then("wiggle", p -> wiggle(spotOf(picked)), 2);
		telling.say(NumberBlock.soundOf(left), "count:left", p -> leftBlocks.forEach(b -> Counting.glow(b, true)), CaptionLine.plain(Numbers.word(left)), 6)
			.then("left:off", p -> leftBlocks.forEach(b -> Counting.glow(b, false)), 2)
			.say(NumberBlock.soundOf(right), "count:right", p -> rightBlocks.forEach(b -> Counting.glow(b, true)), CaptionLine.plain(Numbers.word(right)), 6)
			.then("right:off", p -> rightBlocks.forEach(b -> Counting.glow(b, false)), 2);
	}

	public int left() {
		return left;
	}

	public int right() {
		return right;
	}

	public List<Entity> added() {
		return List.copyOf(added);
	}
}
