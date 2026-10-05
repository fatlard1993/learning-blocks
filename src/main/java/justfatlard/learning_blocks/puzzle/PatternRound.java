package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.Plushies;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patterns, three ways, on the board's row: coloured blocks (or plushies) in a pattern - AB, AAB,
 * ABB, ABC - or a growing one (one block, two, three...).
 * <ul>
 *   <li>What comes next: the row and a gap, three to pick from on the floor; the right one goes in
 *   the gap.</li>
 *   <li>Find the mistake: the row full, one of it wrong; click the wrong one and it is put right.</li>
 *   <li>Copy it: the pattern up, and a row of your own on the floor to build it on, one at a time
 *   from the floor's three. Reading together, a team-up: one builds the first half, the other the
 *   rest.</li>
 * </ul>
 */
public final class PatternRound extends LogicRound {
	private static final List<DyeColor> COLOURS = List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.PINK);
	private static final List<Plush> PLUSHIES = List.of(Plush.OWL, Plush.DUCK, Plush.TEDDY, Plush.CRAB);
	private static final int SHOWN = 6;
	private static final int COPIED = 4;
	/** The pattern's unit, as indexes into the things: AB is [0, 1]. */
	private final int[] unit;
	private final boolean grow;
	private final boolean plushies;
	private final List<Integer> things = new ArrayList<>();
	/** Find the mistake: which of the row is wrong, and what it shows instead. */
	private int wrongAt = -1;
	private int wrongShows = -1;
	private int copied;
	private final List<Entity> shownPlushies = new ArrayList<>();

	public PatternRound(Room room, LogicCourse.Kind kind, String type, int stage, RandomSource random) {
		super(room, kind, type, stage, random);
		this.grow = type.equals("grow");
		this.plushies = !grow && random.nextInt(3) == 0;
		this.unit = switch (type) {
			case "aab" -> new int[] {0, 0, 1};
			case "abb" -> new int[] {0, 1, 1};
			case "abc" -> new int[] {0, 1, 2};
			default -> new int[] {0, 1};
		};
		List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
		Collections.shuffle(order, new java.util.Random(random.nextLong()));
		things.addAll(order);
		if (grow) {
			// Columns of 1, 2, 3 and a gap: 4 comes next.
			for (int height : shuffled(List.of(2, 4, 5))) {
				Choice choice = new Choice(String.valueOf(height), null, justfatlard.learning_blocks.numbers.NumberBlock.soundOf(height),
					justfatlard.learning_blocks.subject.Numbers.word(height));
				choices.add(choice);
				if (height == 4) answer(choice);
			}
			return;
		}
		if (kind == LogicCourse.Kind.MISTAKE) {
			wrongAt = 2 + random.nextInt(SHOWN - 2);
			// Something never in the pattern at all: the fourth thing.
			wrongShows = 3;
			for (int i = 0; i < SHOWN; i++) {
				Choice spot = new Choice("at" + i, null, clipOf(at(i)), saidOf(at(i)));
				choices.add(spot);
				if (i == wrongAt) answer(spot);
			}
			return;
		}
		// What comes next and copy it: the pattern's things and one more.
		int used = unit.length == 3 ? 3 : 2;
		List<Integer> offered = new ArrayList<>();
		for (int i = 0; i < used; i++) offered.add(i);
		if (offered.size() < 3) offered.add(2);
		for (int index : shuffled(offered)) {
			Choice choice = new Choice(String.valueOf(index), plushies ? null : Blocks.WOOL.pick(colourOf(index)).defaultBlockState(), clipOf(index), saidOf(index));
			choices.add(choice);
		}
		answer(choiceFor(kind == LogicCourse.Kind.COPY ? at(0) : at(SHOWN)));
	}

	private List<Integer> shuffled(List<Integer> list) {
		List<Integer> out = new ArrayList<>(list);
		Collections.shuffle(out, new java.util.Random(random.nextLong()));
		return out;
	}

	/** The pattern's i-th thing, as an index (0, 1, 2) into the round's things. */
	private int at(int i) {
		return unit[i % unit.length];
	}

	private DyeColor colourOf(int index) {
		return COLOURS.get(things.get(index));
	}

	private Plush plushOf(int index) {
		return PLUSHIES.get(things.get(index));
	}

	private String clipOf(int index) {
		return plushies ? "word/" + plushOf(index).word() : "word/" + colourOf(index).getSerializedName();
	}

	private String saidOf(int index) {
		return plushies ? plushOf(index).word() : colourOf(index).getSerializedName();
	}

	private @Nullable Choice choiceFor(int index) {
		for (Choice choice : choices) if (choice.id().equals(String.valueOf(index))) return choice;
		return null;
	}

	/** A place in the board's row, a block lower than words go: under the caption, in full view. A growing pattern's columns stand a space apart. */
	private BlockPos rowSpot(int i) {
		int n = kind == LogicCourse.Kind.COPY ? COPIED : SHOWN + (kind == LogicCourse.Kind.NEXT ? 1 : 0);
		if (grow) return layout.rowSpot(2 * i, 7).below();
		return layout.rowSpot(i, n).below();
	}

	/** A thing of the pattern up on the board at i. */
	private void show(int i, int index) {
		BlockPos at = rowSpot(i);
		if (!plushies) {
			set(at, Blocks.WOOL.pick(colourOf(index)).defaultBlockState());
			return;
		}
		set(at, LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
		Entity plush = Counting.item(level, Plushies.stack(plushOf(index)), 0.9F);
		if (plush == null) return;
		Vec3 spot = Vec3.atBottomCenterOf(at).add(0, 0, 0.6);
		plush.snapTo(spot.x, spot.y, spot.z, 0, 0);
		add(plush);
		shownPlushies.add(plush);
	}

	@Override
	protected void board() {
		if (grow) {
			for (int column = 0; column < 3; column++) {
				for (int h = 0; h <= column; h++) set(rowSpot(column).above(h), Blocks.WOOL.pick(colourOf(0)).defaultBlockState());
			}
			set(rowSpot(3), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
			return;
		}
		switch (kind) {
			case MISTAKE -> {
				for (int i = 0; i < SHOWN; i++) show(i, i == wrongAt ? wrongShows : at(i));
			}
			case COPY -> {
				for (int i = 0; i < COPIED; i++) show(i, at(i));
			}
			default -> {
				for (int i = 0; i < SHOWN; i++) show(i, at(i));
				set(rowSpot(SHOWN), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
			}
		}
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		if (kind == LogicCourse.Kind.MISTAKE) return rowSpot(i);
		return super.spotFor(i, n);
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		if (kind == LogicCourse.Kind.MISTAKE) return;
		if (grow) {
			int height = Integer.parseInt(choice.id());
			for (int h = 0; h < height; h++) {
				Entity block = Counting.block(level, Blocks.WOOL.pick(colourOf(0)).defaultBlockState(), 0.3F);
				if (block == null) continue;
				Vec3 at = Vec3.atBottomCenterOf(spot).add(0, h * 0.3, 0);
				block.snapTo(at.x, at.y, at.z, 0, 0);
				add(block);
			}
			return;
		}
		if (!plushies) {
			super.stand(choice, spot);
			return;
		}
		Entity plush = Counting.item(level, Plushies.stack(plushOf(Integer.parseInt(choice.id()))), 0.8F);
		if (plush == null) return;
		Vec3 at = Vec3.atBottomCenterOf(spot);
		plush.snapTo(at.x, at.y, at.z, 0, 0);
		add(plush);
	}

	@Override
	public Narration ask(Narration narration) {
		return switch (kind) {
			case MISTAKE -> line(narration, "phrase/find_the_mistake", "Oops, one is wrong! Can you find it?");
			case COPY -> line(narration, "phrase/copy_it", "Can you make one just like it?");
			default -> line(narration, "phrase/what_comes_next", "What comes next?");
		};
	}

	/** Copy it: where the next one goes, on the floor row in front. */
	private BlockPos copySpot(int i) {
		return layout.origin().offset(i - COPIED / 2, 0, 0);
	}

	private void copyPut(int index) {
		BlockPos at = copySpot(copied);
		if (!plushies) set(at, Blocks.CARPET.pick(colourOf(index)).defaultBlockState());
		else {
			Entity plush = Counting.item(level, Plushies.stack(plushOf(index)), 0.6F);
			if (plush != null) {
				Vec3 spot = Vec3.atBottomCenterOf(at);
				plush.snapTo(spot.x, spot.y, spot.z, 0, 0);
				add(plush);
			}
		}
		copied++;
	}

	@Override
	protected Choice answer() {
		if (kind == LogicCourse.Kind.COPY && !grow) {
			Choice next = choiceFor(at(copied));
			if (next != null) return next;
		}
		return super.answer();
	}

	@Override
	protected Verdict judge(ServerPlayer player, Choice picked) {
		if (kind != LogicCourse.Kind.COPY || grow) return super.judge(player, picked);
		if (!picked.equals(answer())) return Verdict.WRONG;
		return copied + 1 == COPIED ? Verdict.RIGHT : Verdict.HOLD;
	}

	@Override
	protected void hold(ServerPlayer player, Choice picked, Narration telling) {
		int index = Integer.parseInt(picked.id());
		telling.say(picked.clip(), "copy:" + copied, p -> copyPut(index), CaptionLine.plain(picked.said()), 3);
	}

	/** Reading together, copy it is a team-up: the turn reader the first half, the other the rest. */
	@Override
	protected @Nullable UUID whoseTurn() {
		if (kind != LogicCourse.Kind.COPY || !room.shared() || room.turn == null) return null;
		if (copied < COPIED / 2) return room.turn;
		for (UUID member : room.members) if (!member.equals(room.turn)) return member;
		return room.turn;
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		switch (kind) {
			case MISTAKE -> telling.say(clipOf(at(wrongAt)), "fixed", p -> {
				BlockPos at = rowSpot(wrongAt);
				if (!plushies) set(at, Blocks.WOOL.pick(colourOf(at(wrongAt))).defaultBlockState());
				else {
					Entity wrong = wrongAt < shownPlushies.size() ? shownPlushies.get(wrongAt) : null;
					if (wrong != null) wrong.discard();
					Entity plush = Counting.item(level, Plushies.stack(plushOf(at(wrongAt))), 0.9F);
					if (plush != null) {
						Vec3 spot = Vec3.atBottomCenterOf(at).add(0, 0, 0.6);
						plush.snapTo(spot.x, spot.y, spot.z, 0, 0);
						add(plush);
					}
				}
			}, CaptionLine.plain(saidOf(at(wrongAt)) + "!"), AnswerNarration.WORD_GAP);
			case COPY -> {
				int index = Integer.parseInt(picked.id());
				telling.say(picked.clip(), "copy:" + copied, p -> copyPut(index), CaptionLine.plain(picked.said() + "!"), AnswerNarration.WORD_GAP);
			}
			default -> telling.say(picked.clip(), "right:" + picked.id(), p -> {
				if (grow) {
					for (int h = 0; h < 4; h++) set(rowSpot(3).above(h), Blocks.WOOL.pick(colourOf(0)).defaultBlockState());
				} else show(SHOWN, Integer.parseInt(picked.id()));
			}, CaptionLine.plain(picked.said() + "!"), AnswerNarration.WORD_GAP);
		}
	}

	/** The next one's colour, for tests of the old stub's kind. */
	public DyeColor next() {
		return colourOf(at(SHOWN));
	}

	public int copied() {
		return copied;
	}

	public int wrongAt() {
		return wrongAt;
	}

	public boolean plushies() {
		return plushies;
	}
}
