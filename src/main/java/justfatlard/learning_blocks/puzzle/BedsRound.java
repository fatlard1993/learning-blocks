package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.relate.Relations;
import justfatlard.learning_blocks.relate.Thing;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * One to one: "Every kitten needs a bed!" Some kittens in a row, fewer little beds in front of
 * them: how many more beds? The right number: the missing beds pop out of the button, and each
 * kitten hops onto its bed and settles down - on it as the relations engine says a creature sits
 * on a bed.
 */
public final class BedsRound extends NumbersRound {
	private final int kittens;
	private final int beds;
	private final List<Entity> cats = new ArrayList<>();
	private final List<Entity> bedsOut = new ArrayList<>();
	private static final float BED = 0.7F;

	public BedsRound(Room room, int more, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.BEDS, String.valueOf(more), stage, random);
		this.beds = 1 + random.nextInt(Math.max(1, 5 - more));
		this.kittens = beds + more;
		tiles(more, 1, Math.max(4, kittens));
	}

	private Vec3 kittenSpot(int i) {
		return Vec3.atBottomCenterOf(layout.origin()).add((i - (kittens - 1) / 2.0) * 1.15, 0, -1.0);
	}

	private Vec3 bedSpot(int i) {
		return kittenSpot(i).add(0, 0, 1.2);
	}

	private static BlockState bed() {
		return Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
	}

	@Override
	protected void board() {}

	@Override
	protected void started(ServerPlayer player) {
		List<Vec3> spots = new ArrayList<>();
		for (int i = 0; i < kittens; i++) spots.add(kittenSpot(i));
		cats.addAll(Counting.dispense(this, player, Counting.What.KITTEN, kittens, spots, 4, null));
		for (int i = 0; i < beds; i++) {
			Entity bed = Counting.block(level, bed(), BED);
			if (bed == null) continue;
			Vec3 at = bedSpot(i);
			bed.snapTo(at.x, at.y, at.z, 0, 0);
			add(bed);
			bedsOut.add(bed);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		line(narration, "phrase/every_kitten_needs_a_bed", "Every kitten needs a bed!");
		line(narration, "phrase/how_many_more", "How many more");
		return narration.say("word/beds", "what:beds", p -> {}, CaptionLine.plain("beds?"), Narration.GAP);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		int more = kittens - beds;
		telling.then("beds", p -> {
			for (int i = beds; i < kittens; i++) {
				Entity bed = Counting.block(level, bed(), BED);
				if (bed == null) continue;
				bedsOut.add(bed);
				Counting.fly(this, p, bed, bedSpot(i), (i - beds) * 4, null);
			}
		}, Counting.dispenseTicks(more, 4));
		// Each kitten onto its bed, the height the relations say a creature sits on a bed at.
		Thing bedThing = Relations.thing("bed");
		Thing.Hold on = bedThing == null ? null : bedThing.hold(Thing.Hold.Way.ON);
		double up = (on == null ? 0.5625 : on.y()) * BED;
		for (int i = 0; i < kittens; i++) {
			int which = i;
			telling.say(NumberBlock.soundOf(i + 1), "tuck:" + i, p -> {
				if (which >= cats.size()) return;
				Entity cat = cats.get(which);
				Vec3 at = bedSpot(which).add(0, up, 0);
				cat.snapTo(at.x, at.y, at.z, 0, 0);
				if (cat instanceof TamableAnimal sitter) sitter.setInSittingPose(true);
				Counting.glow(cat, true);
				level.sendParticles(ParticleTypes.HEART, at.x, at.y + 0.5, at.z, 2, 0.2, 0.1, 0.2, 0.0);
			}, CaptionLine.plain(Numbers.word(i + 1)), 3);
		}
		telling.then("settled", p -> cats.forEach(cat -> Counting.glow(cat, false)), 2);
		telling.say(NumberBlock.soundOf(more), "right:" + more, p -> {}, CaptionLine.plain(Numbers.word(more) + " more beds!"), AnswerNarration.WORD_GAP);
	}

	/** The kittens and the beds, for tests. */
	public int kittens() {
		return kittens;
	}

	public int beds() {
		return beds;
	}

	public List<Entity> bedsOut() {
		return List.copyOf(bedsOut);
	}

	public List<Entity> cats() {
		return List.copyOf(cats);
	}
}
