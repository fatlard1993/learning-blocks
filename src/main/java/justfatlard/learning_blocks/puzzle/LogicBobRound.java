package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.relate.Relations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Bob, logic edition. A sentence on the board with a word card to choose, the goal framed on the
 * board's left, word cards on the floor; picked, the sentence is read and Bob does exactly that.
 * <ul>
 *   <li>"put all the _ in the pen": pigs, cows and sheep about, one pen; all of the kind go in.</li>
 *   <li>"put the _ cat in the box": a big cat and a little one.</li>
 *   <li>"put the cat _ of the box": left or right, as the child sees it.</li>
 *   <li>What's wrong: Bob has sorted the animals into two pens, and one is in the wrong pen; click
 *   it, "Oops! Wrong pen!", and it goes where it belongs: "All sorted!".</li>
 * </ul>
 */
public final class LogicBobRound extends LogicRound {
	private static final int INK = BobProps.INK;
	private static final int CARD = BobProps.CARD;
	private @Nullable Villager bob;
	private Display.@Nullable TextDisplay sentence;
	private final List<Entity> animals = new ArrayList<>();
	private final Map<Entity, String> kindOf = new LinkedHashMap<>();
	private @Nullable Entity bigCat;
	private @Nullable Entity littleCat;
	private @Nullable Entity cat;
	/** What's wrong: the one in the wrong pen, its choice, and each animal's choice. */
	private final Map<Choice, Entity> animalOf = new LinkedHashMap<>();
	private @Nullable Entity misplaced;
	/** What Bob did last, for tests: the word on the card. */
	private @Nullable String did;

	public LogicBobRound(Room room, LogicCourse.Kind kind, String goal, int stage, RandomSource random) {
		super(room, kind, goal, stage, random);
		List<String> cards = switch (kind) {
			case BOBSORT -> new ArrayList<>(List.of("pigs", "cows", "sheep"));
			case BOBBIG -> new ArrayList<>(List.of("big", "little"));
			case BOBLEFT -> new ArrayList<>(List.of("left", "right"));
			default -> new ArrayList<>();
		};
		Collections.shuffle(cards, new java.util.Random(random.nextLong()));
		for (String word : cards) {
			Choice card = new Choice(word, null, "word/" + word, word);
			choices.add(card);
			if (word.equals(goal)) answer(card);
		}
		if (kind == LogicCourse.Kind.BOBFIX) {
			// Two pigs and two cows, one of them sorted wrong.
			for (int i = 0; i < 4; i++) choices.add(new Choice("a" + i, null, i < 2 ? "word/pig" : "word/cow", i < 2 ? "pig" : "cow"));
			answer(choices.get(random.nextInt(4)));
		}
	}

	private List<String> words(@Nullable String card) {
		String gap = card == null ? "_" : card;
		return switch (kind) {
			case BOBSORT -> List.of("put", "all", "the", gap, "in", "the", "pen");
			case BOBBIG -> List.of("put", "the", gap, "cat", "in", "the", "box");
			default -> List.of("put", "the", "cat", gap, "of", "the", "box");
		};
	}

	private void showSentence(@Nullable String card) {
		if (sentence != null) sentence.discard();
		if (kind == LogicCourse.Kind.BOBFIX) return;
		sentence = Counting.boardText(this, String.join(" ", words(card)), INK, 2.6F, 0.6, -0.5);
		sentence.setBackgroundColor(CARD);
	}

	private BlockPos penCentre(int which) {
		return layout.origin().offset(which == 0 ? -1 : 3, 0, 0);
	}

	private BlockPos boxAt() {
		return layout.origin().offset(1, 0, -1);
	}

	private static BlockState box() {
		return BobProps.box();
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		if (kind == LogicCourse.Kind.BOBFIX) return BlockPos.containing(fixSpot(i));
		// The cards standing in a row near the board, seen over everything in front.
		return layout.origin().offset(-2 + 2 * i, 0, -3);
	}

	@Override
	protected float hitWidth(Choice choice) {
		return kind == LogicCourse.Kind.BOBFIX ? 0.9F : 1.8F;
	}

	@Override
	protected float hitHeight(Choice choice) {
		return kind == LogicCourse.Kind.BOBFIX ? 1.0F : 1.1F;
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		if (kind == LogicCourse.Kind.BOBFIX) return;
		// A word card standing up to be read.
		Display.TextDisplay card = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		Vec3 at = Vec3.atBottomCenterOf(spot).add(0, 0.3, 0);
		card.snapTo(at.x, at.y, at.z, 0, 0);
		card.setText(Component.literal(choice.said()).withStyle(style -> style.withColor(TextColor.fromRgb(INK)).withBold(true)));
		card.setBackgroundColor(CARD);
		card.setTransformation(new Transformation(null, null, new Vector3f(2.4F, 2.4F, 2.4F), null));
		add(card);
	}

	/** What's wrong: where each animal stands, two in each pen, the wrong one swapped in. */
	private Vec3 fixSpot(int i) {
		int pen = i < 2 ? 0 : 1;
		Choice choice = choices.get(i);
		if (choice == answer()) pen = 1 - pen;
		Vec3 centre = Vec3.atBottomCenterOf(penCentre(pen));
		int k = (int) choices.subList(0, i).stream().filter(c -> penOfStart(choices.indexOf(c)) == penOfStart(i)).count();
		return centre.add((k - 1) * 0.9, 0, -0.3);
	}

	private int penOfStart(int i) {
		int pen = i < 2 ? 0 : 1;
		return choices.get(i) == answer() ? 1 - pen : pen;
	}

	@Override
	protected void board() {
		showSentence(null);
		if (kind != LogicCourse.Kind.BOBFIX) goalPicture();
	}

	/** The goal, small and framed on the board's left. */
	private void goalPicture() {
		Vec3 at = Vec3.atBottomCenterOf(layout.origin().offset(-4, RoomLayout.WORD_Y, -4)).add(0.3, -0.2, 0.2);
		Display.BlockDisplay frame = BobProps.goalFrame(level, at);
		if (frame != null) add(frame);
		switch (kind) {
			case BOBSORT -> {
				Display.BlockDisplay rug = Counting.block(level, Blocks.CARPET.brown().defaultBlockState(), 0.9F);
				rug.snapTo(at.x, at.y + 0.1, at.z, 0, 0);
				add(rug);
				ItemStack egg = new ItemStack(switch (target) {
					case "cows" -> Items.COW_SPAWN_EGG;
					case "sheep" -> Items.SHEEP_SPAWN_EGG;
					default -> Items.PIG_SPAWN_EGG;
				});
				for (int i = 0; i < 2; i++) icon(egg, at.add((i - 0.5) * 0.4, 0.4, 0.05), 0.4F);
			}
			case BOBBIG -> {
				Display.BlockDisplay base = Counting.block(level, box(), 0.45F);
				base.snapTo(at.x, at.y + 0.1, at.z, 0, 0);
				add(base);
				icon(new ItemStack(Items.CAT_SPAWN_EGG), at.add(0, 0.65, 0.05), target.equals("big") ? 0.55F : 0.3F);
			}
			default -> {
				Display.BlockDisplay base = Counting.block(level, box(), 0.45F);
				base.snapTo(at.x, at.y + 0.1, at.z, 0, 0);
				add(base);
				// The child's left is the room's west, which is the frame's left as they face it.
				icon(new ItemStack(Items.CAT_SPAWN_EGG), at.add(target.equals("left") ? -0.55 : 0.55, 0.2, 0.05), 0.35F);
			}
		}
	}

	private void icon(ItemStack stack, Vec3 at, float size) {
		Entity shown = Counting.item(level, stack, size);
		if (shown == null) return;
		shown.snapTo(at.x, at.y, at.z, 0, 0);
		add(shown);
	}

	@Override
	protected void started(ServerPlayer player) {
		spawnBob();
		switch (kind) {
			case BOBSORT -> {
				rug(penCentre(1));
				List<Counting.What> about = new ArrayList<>(List.of(Counting.What.PIG, Counting.What.PIG, Counting.What.COW, Counting.What.COW, Counting.What.SHEEP));
				Collections.shuffle(about, new java.util.Random(random.nextLong()));
				for (int i = 0; i < about.size(); i++) {
					Entity one = Counting.make(level, about.get(i), 0.5F);
					if (one == null) continue;
					Vec3 at = Vec3.atBottomCenterOf(layout.origin()).add(-2.8 + i * 0.8, 0, 0.3);
					one.snapTo(at.x, at.y, at.z, 0, 0);
					add(one);
					animals.add(one);
					kindOf.put(one, about.get(i).plural);
				}
			}
			case BOBBIG, BOBLEFT -> {
				set(boxAt(), box());
				if (kind == LogicCourse.Kind.BOBBIG) {
					bigCat = placeCat(Vec3.atBottomCenterOf(layout.origin()).add(-1.6, 0, 0.3), false);
					littleCat = placeCat(Vec3.atBottomCenterOf(layout.origin()).add(-0.6, 0, 0.3), true);
				} else {
					cat = placeCat(Vec3.atBottomCenterOf(layout.origin()).add(-1.0, 0, 0.6), false);
				}
			}
			default -> {
				rug(penCentre(0));
				rug(penCentre(1));
				for (int i = 0; i < 4; i++) {
					Entity one = Counting.make(level, i < 2 ? Counting.What.PIG : Counting.What.COW, 0.5F);
					if (one == null) continue;
					Vec3 at = fixSpot(i);
					one.snapTo(at.x, at.y, at.z, 0, 0);
					add(one);
					animalOf.put(choices.get(i), one);
					if (choices.get(i) == answer()) misplaced = one;
				}
			}
		}
	}

	private @Nullable Entity placeCat(Vec3 at, boolean little) {
		Entity one = Counting.make(level, little ? Counting.What.KITTEN : Counting.What.CAT, little ? 1.0F : 0.9F);
		if (one == null) return null;
		if (little && one instanceof AgeableMob young) young.setBaby(true);
		one.snapTo(at.x, at.y, at.z, 0, 0);
		return add(one);
	}

	private void rug(BlockPos centre) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) set(centre.offset(dx, 0, dz), Blocks.CARPET.brown().defaultBlockState());
			}
		}
	}

	private void spawnBob() {
		Villager villager = BobProps.bob(level, Vec3.atBottomCenterOf(layout.origin().offset(-4, 0, -2)), 0);
		if (villager == null) return;
		add(villager);
		bob = villager;
		BobProps.wave(villager);
	}

	@Override
	public Narration ask(Narration narration) {
		if (kind == LogicCourse.Kind.BOBFIX) return line(narration, "phrase/whats_wrong", "What's wrong?");
		return line(narration, "phrase/tell_bob", "Tell Bob!");
	}

	/** Bob does it, exactly: the card in the gap, read out, and done. */
	@Override
	protected @Nullable Narration act(ServerPlayer player, Choice picked) {
		if (kind == LogicCourse.Kind.BOBFIX) return null;
		Narration doing = new Narration();
		doing.then("card", p -> showSentence(picked.id()), 4);
		List<String> words = words(picked.id());
		for (int i = 0; i < words.size(); i++) {
			doing.say("word/" + words.get(i), "read:" + i, p -> {}, CaptionLine.sentence(words, i), 3);
		}
		doing.sayAs("Bob", "phrase/bob_okay", "bob_okay", null, 4);
		doing.then("bob_does", p -> {
			did = picked.id();
			if (bob != null) justfatlard.learning_blocks.feel.Gestures.run(bob, justfatlard.learning_blocks.feel.Gestures.Gesture.WAVE);
			doIt(picked.id());
		}, 16);
		return doing;
	}

	/** Where things were before Bob did it, to put back after a wrong card. */
	private final Map<Entity, Vec3> before = new LinkedHashMap<>();

	private void doIt(String card) {
		before.clear();
		switch (kind) {
			case BOBSORT -> {
				int k = 0;
				for (Entity one : animals) {
					if (!kindOf.get(one).equals(card)) continue;
					before.put(one, one.position());
					move(one, Vec3.atBottomCenterOf(penCentre(1)).add((k % 2 - 0.5) * 0.8, 0, (k / 2 - 0.5) * 0.8));
					k++;
				}
			}
			case BOBBIG -> {
				Entity which = card.equals("big") ? bigCat : littleCat;
				if (which != null) {
					before.put(which, which.position());
					move(which, Vec3.atBottomCenterOf(boxAt()).add(0, 0.8, 0));
				}
			}
			default -> {
				if (cat != null) {
					before.put(cat, cat.position());
					// The child's left, facing the board, is west: -x.
					move(cat, Vec3.atBottomCenterOf(boxAt()).add(card.equals("left") ? -1.0 : 1.0, 0, 0));
				}
			}
		}
	}

	private void undo() {
		for (Map.Entry<Entity, Vec3> back : before.entrySet()) move(back.getKey(), back.getValue());
		before.clear();
		showSentence(null);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		if (kind == LogicCourse.Kind.BOBFIX) {
			Entity wrong = misplaced;
			int pen = choices.indexOf(picked) < 2 ? 0 : 1;
			telling.sayAs("Bob", "phrase/bob_wrong_pen", "bob_wrong_pen", p -> hop(wrong, p), 4)
				.then("sorted", p -> move(wrong, Vec3.atBottomCenterOf(penCentre(pen)).add(0, 0, 0.5)), 4)
				.sayAs("Bob", "phrase/bob_sorted", "bob_sorted", null, AnswerNarration.WORD_GAP);
			return;
		}
		telling.sayAs("Bob", "phrase/bob_did_it", "bob_did_it", null, AnswerNarration.WORD_GAP);
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		if (kind == LogicCourse.Kind.BOBFIX) {
			Entity it = animalOf.get(picked);
			telling.then("hop", p -> hop(it, p), 2);
			super.tellWrong(player, picked, telling);
			return;
		}
		line(telling, "phrase/oops_thats", "Oops, that's");
		telling.then("undo", p -> undo(), 4);
	}

	/** For tests: the card Bob did last, and where things are. */
	public @Nullable String did() {
		return did;
	}

	public long inPen() {
		Vec3 pen = Vec3.atBottomCenterOf(penCentre(1));
		return animals.stream().filter(one -> one.position().distanceTo(pen) < 1.6).count();
	}

	public @Nullable Entity cat() {
		return kind == LogicCourse.Kind.BOBBIG ? (target.equals("big") ? bigCat : littleCat) : cat;
	}

	public Vec3 boxFoot() {
		return Vec3.atBottomCenterOf(boxAt());
	}
}
