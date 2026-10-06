package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Read it, for numbers: a numeral on the board, never said, and three pictures on the floor, each
 * a little group of things; which picture has that many? Right: that picture's things are counted,
 * glowing.
 */
public final class PictureRound extends NumbersRound {
	private final int n;
	private final Counting.What what;
	private final Map<Choice, List<Entity>> groups = new LinkedHashMap<>();

	public PictureRound(Room room, int n, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.PICTURE, String.valueOf(n), stage, random);
		this.n = n;
		List<Counting.What> things = List.of(Counting.What.APPLE, Counting.What.CARROT, Counting.What.CAKE);
		this.what = things.get(random.nextInt(things.size()));
		List<Integer> counts = new ArrayList<>();
		for (int k = 1; k <= 10; k++) if (k != n) counts.add(k);
		Collections.shuffle(counts, new java.util.Random(random.nextLong()));
		counts.sort(java.util.Comparator.comparingInt(k -> Math.abs(k - n)));
		List<Integer> shown = new ArrayList<>(counts.subList(0, 2));
		shown.add(n);
		Collections.shuffle(shown, new java.util.Random(random.nextLong()));
		for (int k : shown) {
			Choice choice = new Choice(String.valueOf(k), null, NumberBlock.soundOf(k), Numbers.word(k));
			choices.add(choice);
			if (k == n) answer(choice);
		}
	}

	@Override
	protected BlockPos spotFor(int i, int count) {
		// Near enough for a child to click from where they stand: the mats are wide, three apart.
		return layout.origin().offset((i - 1) * 3, 0, 0);
	}

	@Override
	protected float hitWidth(Choice choice) {
		return 2.4F;
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		int k = number(choice);
		List<Entity> group = new ArrayList<>();
		// A white mat, and the things in rows of five on it.
		Entity mat = Counting.block(level, net.minecraft.world.level.block.Blocks.CARPET.white().defaultBlockState(), 1.0F);
		if (mat != null) {
			mat.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
			if (mat instanceof net.minecraft.world.entity.Display.BlockDisplay display) {
				display.setTransformation(new com.mojang.math.Transformation(new org.joml.Vector3f(-1.2F, 0, -0.75F), null, new org.joml.Vector3f(2.4F, 1, 1.5F), null));
			}
			add(mat);
		}
		for (int j = 0; j < k; j++) {
			Entity thing = Counting.make(level, what, 0.4F);
			if (thing == null) continue;
			Vec3 at = Vec3.atBottomCenterOf(spot).add((j % 5 - 2) * 0.44, 0.07, (j / 5 - 0.5) * 0.6);
			thing.snapTo(at.x, at.y, at.z, 0, 0);
			add(thing);
			group.add(thing);
		}
		groups.put(choice, group);
	}

	@Override
	protected void board() {
		set(layout.rowSpot(0, 1), LearningBlocks.NUMBER.showing(n));
	}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/which_picture_has", "Which picture has that many?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		Counting.chant(telling, n, groups.getOrDefault(picked, List.of()), false);
	}
}
