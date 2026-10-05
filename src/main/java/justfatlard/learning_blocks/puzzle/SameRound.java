package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.Plushies;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Same or different: two pictures up on the board, side by side, "Same or different?", and two
 * tiles to answer with, a tick (the same) and a cross (different).
 */
public final class SameRound extends LogicRound {
	private final ItemStack left;
	private final ItemStack right;

	public SameRound(Room room, String which, int stage, RandomSource random) {
		super(room, LogicCourse.Kind.SAME, which, stage, random);
		List<ItemStack> pool = new ArrayList<>(List.of(new ItemStack(Items.APPLE), new ItemStack(Items.CARROT), new ItemStack(Items.CAKE),
			Plushies.stack(Plush.OWL), Plushies.stack(Plush.DUCK), Plushies.stack(Plush.TEDDY), new ItemStack(Items.POPPY), new ItemStack(Items.EGG)));
		Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		this.left = pool.get(0);
		this.right = which.equals("same") ? pool.get(0).copy() : pool.get(1);
		Choice yes = new Choice("same", LearningBlocks.NUMBER.showing(NumberBlock.YES), "word/same", "the same");
		Choice no = new Choice("different", LearningBlocks.NUMBER.showing(NumberBlock.NO), "word/different", "different");
		if (random.nextBoolean()) {
			choices.add(yes);
			choices.add(no);
		} else {
			choices.add(no);
			choices.add(yes);
		}
		answer(which.equals("same") ? yes : no);
	}

	@Override
	protected void board() {
		for (int side = 0; side < 2; side++) {
			Entity shown = Counting.item(level, side == 0 ? left : right, 1.6F);
			if (shown == null) continue;
			Vec3 at = Vec3.atBottomCenterOf(layout.origin().offset(0, 1, -4)).add(side == 0 ? -1.6 : 1.6, 0, 0.3);
			shown.snapTo(at.x, at.y, at.z, 0, 0);
			add(shown);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/same_or_different", "Same or different?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say(picked.clip(), "right:" + picked.id(), p -> {}, CaptionLine.plain(picked.said() + "!"), AnswerNarration.WORD_GAP);
	}
}
