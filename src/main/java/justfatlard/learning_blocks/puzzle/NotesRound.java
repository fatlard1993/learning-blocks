package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A pattern of notes: a row of note blocks on the board, each played in turn, "Listen!" - high,
 * low, high, low, high... and a gap. Two bells on the floor, a little one (high) and a big one
 * (low): which comes next? Played again, it all plays with the answer in the gap.
 */
public final class NotesRound extends LogicRound {
	private static final int SHOWN = 5;
	private static final float HIGH = 1.6F;
	private static final float LOW = 0.7F;
	private final boolean[] unit;
	private final boolean startHigh;

	public NotesRound(Room room, String type, int stage, RandomSource random) {
		super(room, LogicCourse.Kind.NOTES, type, stage, random);
		this.unit = type.equals("abb") ? new boolean[] {true, false, false} : new boolean[] {true, false};
		this.startHigh = random.nextBoolean();
		Choice high = new Choice("high", Blocks.NOTE_BLOCK.defaultBlockState(), "word/high", "high");
		Choice low = new Choice("low", Blocks.NOTE_BLOCK.defaultBlockState(), "word/low", "low");
		if (random.nextBoolean()) {
			choices.add(high);
			choices.add(low);
		} else {
			choices.add(low);
			choices.add(high);
		}
		answer(high(SHOWN) ? high : low);
	}

	private boolean high(int i) {
		return unit[i % unit.length] == startHigh;
	}

	private BlockPos at(int i) {
		return layout.rowSpot(i, SHOWN + 1).below();
	}

	@Override
	protected void board() {
		for (int i = 0; i < SHOWN; i++) set(at(i), Blocks.NOTE_BLOCK.defaultBlockState());
		set(at(SHOWN), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		super.stand(choice, spot);
		// A bell on top: a little one for high, a big one for low.
		boolean isHigh = choice.id().equals("high");
		Entity bell = Counting.item(level, new ItemStack(Items.BELL), isHigh ? 0.45F : 0.9F);
		if (bell == null) return;
		Vec3 top = Vec3.atBottomCenterOf(spot).add(0, 1.0, 0);
		bell.snapTo(top.x, top.y, top.z, 0, 0);
		add(bell);
	}

	private void play(BlockPos where, boolean isHigh) {
		Vec3 at = Vec3.atCenterOf(where);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, isHigh ? HIGH : LOW);
		level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 0.7, at.z, 1, 0, 0, 0, isHigh ? 0.9 : 0.2);
	}

	private Narration tune(Narration narration, int upTo, boolean withAnswer) {
		for (int i = 0; i < upTo; i++) {
			int which = i;
			narration.then("note:" + i, p -> play(at(which), high(which)), 9);
		}
		if (withAnswer) narration.then("note:" + upTo, p -> play(at(upTo), high(upTo)), 9);
		return narration;
	}

	@Override
	public Narration ask(Narration narration) {
		line(narration, "phrase/listen", "Listen!");
		tune(narration, SHOWN, false);
		return line(narration, "phrase/what_comes_next", "What comes next?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say(picked.clip(), "right:" + picked.id(), p -> set(at(SHOWN), Blocks.NOTE_BLOCK.defaultBlockState()), CaptionLine.plain(picked.said() + "!"), 4);
		tune(telling, SHOWN, true);
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		telling.then("its_note", p -> play(spotOf(picked), picked.id().equals("high")), 6);
		super.tellWrong(player, picked, telling);
	}
}
