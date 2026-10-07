package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.Plushies;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.ToyBlock;
import justfatlard.learning_blocks.toy.Toys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sorting: "Put the animals in their pens!" Two pens, each with one already in it to show what
 * goes there (a pig, a cow), and a row of mixed ones out of the button between. Click one, then
 * its pen, and it hops in; the wrong pen, and it hops back ("oops"). All in, it's done. By kind
 * (pigs and cows), by colour (two colours of sheep, each pen's rug its colour), by size (grown-ups
 * and babies), and things: toys into the box, plushies into the crib, as the relations engine has
 * them held.
 */
public final class SortRound extends LogicRound {
	private final List<Entity> things = new ArrayList<>();
	/** Each thing's group: 0 for the left pen, 1 for the right. */
	private final Map<Choice, Integer> groupOf = new LinkedHashMap<>();
	private final Map<Choice, Entity> thingOf = new LinkedHashMap<>();
	private final Choice[] pens = new Choice[2];
	private final int count;
	private final List<Integer> groups = new ArrayList<>();
	private final DyeColor[] colours = {DyeColor.RED, DyeColor.BLUE};
	private @Nullable Choice selected;
	private int sorted;
	private final int[] inPen = new int[2];

	public SortRound(Room room, String by, int stage, RandomSource random) {
		super(room, LogicCourse.Kind.SORT, by, stage, random);
		this.count = 4 + random.nextInt(2);
		for (int i = 0; i < count; i++) groups.add(i % 2);
		Collections.shuffle(groups, new java.util.Random(random.nextLong()));
		if (by.equals("colour")) {
			List<DyeColor> pick = new ArrayList<>(List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.PINK));
			Collections.shuffle(pick, new java.util.Random(random.nextLong()));
			colours[0] = pick.get(0);
			colours[1] = pick.get(1);
		}
		for (int i = 0; i < count; i++) {
			Choice thing = new Choice("t" + i, null, groupClip(groups.get(i)), groupWord(groups.get(i)));
			choices.add(thing);
			groupOf.put(thing, groups.get(i));
		}
		pens[0] = new Choice("pen0", null, groupClip(0), groupWord(0));
		pens[1] = new Choice("pen1", null, groupClip(1), groupWord(1));
		choices.add(pens[0]);
		choices.add(pens[1]);
		answer(pens[0]);
	}

	private String groupWord(int group) {
		return switch (target) {
			case "colour" -> colours[group].getSerializedName();
			case "size" -> group == 0 ? "big" : "little";
			case "things" -> group == 0 ? "box" : "crib";
			default -> group == 0 ? "pigs" : "cows";
		};
	}

	private String groupClip(int group) {
		return "word/" + groupWord(group);
	}

	/** Left pen and right pen, as the child faces the board; the things in a row between, nearer. */
	private BlockPos penCentre(int group) {
		return layout.origin().offset(group == 0 ? -3 : 3, 0, -1);
	}

	/**
	 * In an arc round where the child stands, each as far from them as the next: a click aimed at one
	 * from there never passes through another's box on the way, as it would along a straight row.
	 */
	private static final double ARC = 2.4;
	private static final float ANIMAL_BOX_WIDTH = 1.1F;
	private static final float ANIMAL_BOX_HEIGHT = 1.5F;
	/** With one picked, the animals' boxes shrink to the animals, so a click past them reaches the pens. */
	private static final float PICKED_BOX = 0.55F;
	private static final double ARC_STEP = Math.toRadians(38);

	private Vec3 thingSpot(int i) {
		double angle = (i - (count - 1) / 2.0) * ARC_STEP;
		return layout.spawn().add(Math.sin(angle) * ARC, 0, -Math.cos(angle) * ARC);
	}

	private Vec3 inPenSpot(int group, int k) {
		Vec3 centre = Vec3.atBottomCenterOf(penCentre(group));
		return centre.add(((k + 1) % 3 - 1) * 0.8, 0, (k + 1) / 3 * 0.8 - 0.6);
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		if (i < count) return BlockPos.containing(thingSpot(i));
		return penCentre(i - count);
	}

	@Override
	protected Vec3 hitSpot(Choice choice, int i, int n) {
		return i < count ? thingSpot(i) : Vec3.atBottomCenterOf(penCentre(i - count));
	}

	/** A pen is clicked as its rug and floor, or an animal already in it. */
	@Override
	protected boolean boxed(Choice choice) {
		return choice != pens[0] && choice != pens[1];
	}

	/**
	 * With one picked, a click on an animal (that one or another) only standing between the child and
	 * a pen is aimed at the pen: if the line of sight runs on past it onto a pen within reach, that pen.
	 */
	@Override
	protected Choice past(ServerPlayer player, Choice clicked) {
		if (selected == null || clicked == pens[0] || clicked == pens[1]) return clicked;
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
		var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
			net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return clicked;
		Choice pen = choiceAt(hit.getBlockPos());
		return pen != null ? pen : clicked;
	}

	/** A pen's rug (or the box and the crib), clicked as a block: that pen. */
	@Override
	protected @Nullable Choice choiceAt(BlockPos pos) {
		for (int group = 0; group < 2; group++) {
			BlockPos centre = penCentre(group);
			int dx = pos.getX() - centre.getX();
			int dz = pos.getZ() - centre.getZ();
			if (pos.getY() <= centre.getY() && pos.getY() >= centre.getY() - 1 && Math.abs(dx) <= 2 && dz >= -2 && dz <= 1) return pens[group];
		}
		return null;
	}

	@Override
	protected float hitWidth(Choice choice) {
		return choice == pens[0] || choice == pens[1] ? 4.8F : ANIMAL_BOX_WIDTH;
	}

	@Override
	protected float hitHeight(Choice choice) {
		return choice == pens[0] || choice == pens[1] ? 1.6F : ANIMAL_BOX_HEIGHT;
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		int group = choice == pens[0] ? 0 : choice == pens[1] ? 1 : -1;
		if (group < 0) return;
		if (target.equals("things")) {
			set(spot, group == 0 ? BobProps.box() : Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, Toy.CRIB).setValue(ToyBlock.FACING, Direction.NORTH));
			return;
		}
		BlockState rug = Blocks.CARPET.pick(target.equals("colour") ? colours[group] : DyeColor.BROWN).defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 1; dz++) {
				if (Math.abs(dx) == 2 || dz == -2 || dz == 1) set(spot.offset(dx, 0, dz), rug);
			}
		}
	}

	/** One of a group: a creature (or a thing, for things), not yet added. */
	private @Nullable Entity make(int group) {
		switch (target) {
			case "things" -> {
				return Counting.item(level, group == 0 ? Toys.stack(Toy.values()[random.nextInt(4)]) : Plushies.stack(Plush.values()[random.nextInt(6)]), 0.6F);
			}
			case "colour" -> {
				Entity sheep = Counting.make(level, Counting.What.SHEEP, 0.55F);
				if (sheep instanceof Sheep woolly) woolly.setColor(colours[group]);
				return sheep;
			}
			case "size" -> {
				Entity pig = Counting.make(level, Counting.What.PIG, 0.6F);
				if (group == 1 && pig instanceof AgeableMob young) young.setBaby(true);
				return pig;
			}
			default -> {
				return Counting.make(level, group == 0 ? Counting.What.PIG : Counting.What.COW, 0.55F);
			}
		}
	}

	@Override
	protected void board() {}

	@Override
	protected void started(ServerPlayer player) {
		// One in each pen already, to show what goes there (the box and the crib show themselves).
		if (!target.equals("things")) {
			for (int group = 0; group < 2; group++) {
				Entity shown = make(group);
				if (shown == null) continue;
				Vec3 at = inPenSpot(group, -1);
				shown.snapTo(at.x, at.y, at.z, 0, 0);
				add(shown);
				clickable(shown, pens[group]);
			}
		}
		for (int i = 0; i < count; i++) {
			Entity thing = make(groups.get(i));
			if (thing == null) continue;
			things.add(thing);
			thingOf.put(choices.get(i), thing);
			clickable(thing, choices.get(i));
			Counting.fly(this, player, thing, thingSpot(i), i * 4, null);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		return switch (target) {
			case "colour" -> line(narration, "phrase/sort_by_colour", "Sort them by colour!");
			case "size" -> line(narration, "phrase/sort_by_size", "Big ones here, little ones there!");
			case "things" -> line(narration, "phrase/toys_in_box", "Toys in the box, plushies in the crib!");
			default -> line(narration, "phrase/put_in_pens", "Put the animals in their pens!");
		};
	}

	public List<Choice> unsorted() {
		return thingOf.keySet().stream().filter(choice -> thingOf.get(choice) != null && !sortedOnes.contains(choice)).toList();
	}

	public Choice penFor(Choice thing) {
		return pens[groupOf.get(thing)];
	}

	public Choice wrongPenFor(Choice thing) {
		return pens[1 - groupOf.get(thing)];
	}

	public int sorted() {
		return sorted;
	}

	private final List<Choice> sortedOnes = new ArrayList<>();

	@Override
	protected Choice answer() {
		List<Choice> left = unsorted();
		return left.isEmpty() ? pens[0] : penFor(left.getFirst());
	}

	@Override
	protected Verdict judge(ServerPlayer player, Choice picked) {
		if (picked != pens[0] && picked != pens[1]) return Verdict.HOLD;
		if (selected == null) return Verdict.HOLD;
		if (pens[groupOf.get(selected)] != picked) return Verdict.WRONG;
		return sorted + 1 == count ? Verdict.RIGHT : Verdict.HOLD;
	}

	@Override
	protected void hold(ServerPlayer player, Choice picked, Narration telling) {
		if (picked != pens[0] && picked != pens[1]) {
			// Picked up: it glows until it is put somewhere.
			if (selected != null) Counting.glow(thingOf.get(selected), false);
			selected = picked;
			for (Choice one : unsorted()) resizeHit(one, PICKED_BOX, PICKED_BOX);
			Entity thing = thingOf.get(picked);
			telling.then("picked", p -> {
				Counting.glow(thing, true);
				hop(thing, p);
				if (thing != null) level.playSound(null, thing.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
					net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.6F);
			}, 2);
			return;
		}
		if (selected == null) {
			telling.then("nothing_picked", p -> wiggle(spotOf(picked)), 2);
			return;
		}
		put(player, telling);
	}

	private void put(ServerPlayer player, Narration telling) {
		Choice thing = selected;
		int group = groupOf.get(thing);
		Entity entity = thingOf.get(thing);
		Vec3 to = inPenSpot(group, inPen[group]++);
		sorted++;
		sortedOnes.add(thing);
		selected = null;
		unhit(thing);
		if (entity != null) clickable(entity, pens[group]);
		for (Choice one : unsorted()) resizeHit(one, ANIMAL_BOX_WIDTH, ANIMAL_BOX_HEIGHT);
		telling.say(thing.clip(), "put:" + thing.id(), p -> {
			Counting.glow(entity, false);
			move(entity, to);
		}, CaptionLine.plain(thing.said()), 3);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		put(player, telling);
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		Choice thing = selected;
		Entity entity = thing == null ? null : thingOf.get(thing);
		telling.then("wiggle", p -> {
			wiggle(spotOf(picked));
			hop(entity, p);
		}, 2).say("phrase/oops_thats", 2);
		if (thing != null) telling.say(thing.clip(), "belongs:" + thing.id(), p -> {}, CaptionLine.plain(thing.said()), AnswerNarration.WORD_GAP);
	}

	public @Nullable Entity thingFor(Choice choice) {
		return thingOf.get(choice);
	}

	public @Nullable Choice selected() {
		return selected;
	}

	public List<Entity> things() {
		return List.copyOf(things);
	}
}
