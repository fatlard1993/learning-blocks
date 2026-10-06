package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.Toys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/**
 * "Which one doesn't belong?" Four in a row: three pigs and a cow; then subtler, three red sheep
 * and a blue one; three animals and a toy. Click it: the right one waves bye and goes off, puff; a
 * wrong one hops, "oops", and it is asked again.
 */
public final class OddRound extends LogicRound {
	private static final int N = 4;
	private final int odd;
	private final List<Entity> shown = new ArrayList<>();
	private final String[] words = new String[N];
	private final Counting.What[] whats = new Counting.What[N];
	private Toy toy = Toy.CAR;
	private DyeColor usual = DyeColor.RED;
	private DyeColor other = DyeColor.BLUE;

	public OddRound(Room room, String by, int stage, RandomSource random) {
		super(room, LogicCourse.Kind.ODD, by, stage, random);
		this.odd = random.nextInt(N);
		List<Counting.What> animals = List.of(Counting.What.PIG, Counting.What.COW, Counting.What.CHICKEN, Counting.What.SHEEP, Counting.What.CAT);
		Counting.What main = animals.get(random.nextInt(animals.size()));
		Counting.What second = main;
		while (second == main) second = animals.get(random.nextInt(animals.size()));
		List<DyeColor> colours = new ArrayList<>(List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.PINK));
		java.util.Collections.shuffle(colours, new java.util.Random(random.nextLong()));
		usual = colours.get(0);
		other = colours.get(1);
		List<Toy> toys = List.of(Toy.CAR, Toy.DRUM, Toy.KITE, Toy.TOP);
		toy = toys.get(random.nextInt(toys.size()));
		for (int i = 0; i < N; i++) {
			boolean isOdd = i == odd;
			switch (by) {
				case "colour" -> {
					whats[i] = Counting.What.SHEEP;
					words[i] = (isOdd ? other : usual).getSerializedName();
				}
				case "category" -> {
					// Three animals, all different, and a toy.
					whats[i] = isOdd ? null : animals.get(i % animals.size());
					words[i] = isOdd ? toy.word() : singular(whats[i]);
				}
				default -> {
					whats[i] = isOdd ? second : main;
					words[i] = singular(whats[i]);
				}
			}
			Choice choice = new Choice("t" + i, null, "word/" + words[i], words[i]);
			choices.add(choice);
			if (isOdd) answer(choice);
		}
	}

	private static String singular(Counting.What what) {
		return switch (what) {
			case PIG -> "pig";
			case COW -> "cow";
			case CHICKEN -> "hen";
			case SHEEP -> "sheep";
			default -> "cat";
		};
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		return BlockPos.containing(layout.arcSpot(i, n));
	}

	@Override
	protected Vec3 hitSpot(Choice choice, int i, int n) {
		return layout.arcSpot(i, n);
	}

	@Override
	protected float hitWidth(Choice choice) {
		return 1.2F;
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		int i = choices.indexOf(choice);
		Entity thing;
		if (whats[i] == null) thing = Counting.item(level, Toys.stack(toy), 0.9F);
		else {
			thing = Counting.make(level, whats[i], 0.6F);
			if (thing instanceof Sheep sheep) sheep.setColor(i == odd ? other : usual);
		}
		if (thing == null) return;
		Vec3 at = layout.arcSpot(i, choices.size());
		thing.snapTo(at.x, at.y, at.z, 0, 0);
		add(thing);
		shown.add(thing);
		clickable(thing, choice);
	}

	@Override
	protected void board() {}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/which_doesnt_belong", "Which one doesn't belong?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		int i = choices.indexOf(picked);
		Entity it = i < shown.size() ? shown.get(i) : null;
		telling.say(picked.clip(), "right:" + picked.id(), p -> hop(it, p), CaptionLine.plain(picked.said() + "!"), 4);
		line(telling.then("wave", p -> {
			if (it != null) justfatlard.learning_blocks.feel.Gestures.run(it, justfatlard.learning_blocks.feel.Gestures.Gesture.WAVE);
		}, 2), "phrase/bye_bye", "Bye bye!");
		telling.then("off", p -> {
			if (it == null || !it.isAlive()) return;
			level.sendParticles(ParticleTypes.POOF, it.getX(), it.getY() + 0.4, it.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
			it.discard();
		}, 4);
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		int i = choices.indexOf(picked);
		Entity it = i < shown.size() ? shown.get(i) : null;
		telling.then("hop", p -> hop(it, p), 2);
		super.tellWrong(player, picked, telling);
	}

	public int odd() {
		return odd;
	}

	public List<Entity> shown() {
		return List.copyOf(shown);
	}
}
