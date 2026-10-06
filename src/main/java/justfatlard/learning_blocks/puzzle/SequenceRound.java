package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.classroom.Sky;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.Plushies;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Putting things in order, a card at a time: the cards jumbled on the floor, and their places on
 * the board. Click the first, then the next...; each goes up in its turn and says itself, one out
 * of turn wiggles. First, next, last: a seed, a sprout, a flower; an egg, a chick, a hen; day,
 * dusk, night (and then the child's own sky does it, day to dusk to night and back). Little to big:
 * the same plushie in three or four sizes. And a scene: "He's all mixed up!" - wake up, eat
 * breakfast, put on your boots, go outside.
 */
public final class SequenceRound extends LogicRound {
	private record Card(String clip, String said) {}

	private final List<Card> cards = new ArrayList<>();
	private final Map<Choice, List<Entity>> drawn = new LinkedHashMap<>();
	private final Plush plush;
	private int done;

	public SequenceRound(Room room, LogicCourse.Kind kind, String which, int stage, RandomSource random) {
		super(room, kind, which, stage, random);
		this.plush = List.of(Plush.OWL, Plush.DUCK, Plush.TEDDY).get(random.nextInt(3));
		switch (kind) {
			case SIZE -> {
				int n = Integer.parseInt(which);
				for (int i = 1; i <= n; i++) cards.add(new Card(justfatlard.learning_blocks.numbers.NumberBlock.soundOf(i), justfatlard.learning_blocks.subject.Numbers.word(i)));
			}
			case MORNING -> {
				cards.add(new Card("phrase/wake_up", "Wake up!"));
				cards.add(new Card("phrase/eat_breakfast", "Eat breakfast!"));
				cards.add(new Card("phrase/put_on_boots", "Put on your boots!"));
				cards.add(new Card("phrase/go_outside", "Go outside!"));
			}
			default -> {
				List<String> words = switch (which) {
					case "egg" -> List.of("egg", "chick", "hen");
					case "day" -> List.of("day", "dusk", "night");
					default -> List.of("seed", "sprout", "flower");
				};
				for (String word : words) cards.add(new Card("word/" + word, word));
			}
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < cards.size(); i++) order.add(i);
		Collections.shuffle(order, new java.util.Random(random.nextLong()));
		for (int i : order) choices.add(new Choice(String.valueOf(i), null, cards.get(i).clip(), cards.get(i).said()));
		answer(next());
	}

	@ForTests
	public Choice next() {
		return choices.stream().filter(c -> Integer.parseInt(c.id()) == done).findFirst().orElse(choices.getFirst());
	}

	@Override
	protected Choice answer() {
		return next();
	}

	public int done() {
		return done;
	}

	private BlockPos slot(int k) {
		// A block lower than words go: under the caption, in full view.
		return layout.rowSpot(k, cards.size()).below();
	}

	@Override
	protected BlockPos spotFor(int i, int n) {
		return layout.origin().offset(2 * i - (n - 1), 0, -1);
	}

	@Override
	protected float hitWidth(Choice choice) {
		return 1.4F;
	}

	@Override
	protected void board() {
		for (int k = 0; k < cards.size(); k++) set(slot(k), LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		drawn.put(choice, draw(Integer.parseInt(choice.id()), Vec3.atBottomCenterOf(spot)));
	}

	private List<Entity> draw(int card, Vec3 foot) {
		List<Entity> made = new ArrayList<>();
		switch (kind) {
			case SIZE -> {
				float size = 0.4F + card * 0.27F;
				made.add(item(Plushies.stack(plush), size, foot));
			}
			case MORNING -> {
				switch (card) {
					case 0 -> made.add(block(Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.HEAD), 0.7F, foot));
					case 1 -> made.add(item(new ItemStack(Items.BREAD), 1.1F, foot));
					case 2 -> made.add(item(new ItemStack(Items.LEATHER_BOOTS), 1.1F, foot));
					default -> made.add(item(new ItemStack(Items.OAK_DOOR), 1.1F, foot));
				}
			}
			default -> {
				switch (target) {
					case "egg" -> {
						if (card == 0) made.add(item(new ItemStack(Items.EGG), 1.0F, foot));
						else {
							Entity chicken = Counting.make(level, Counting.What.CHICKEN, 0.9F);
							if (card == 1 && chicken instanceof AgeableMob young) young.setBaby(true);
							if (chicken != null) {
								chicken.snapTo(foot.x, foot.y, foot.z, 0, 0);
								made.add(add(chicken));
							}
						}
					}
					case "day" -> made.addAll(sky(card, foot));
					default -> made.add(switch (card) {
						case 0 -> item(new ItemStack(Items.WHEAT_SEEDS), 1.0F, foot);
						case 1 -> item(new ItemStack(Items.OAK_SAPLING), 1.0F, foot);
						default -> item(new ItemStack(Items.POPPY), 1.1F, foot);
					});
				}
			}
		}
		made.removeIf(java.util.Objects::isNull);
		return made;
	}

	/** A little window on the sky: blue with the sun up high; orange with it low; dark blue with the moon. */
	private List<Entity> sky(int card, Vec3 foot) {
		BlockState back = switch (card) {
			case 0 -> Blocks.CONCRETE.lightBlue().defaultBlockState();
			case 1 -> Blocks.CONCRETE.orange().defaultBlockState();
			default -> Blocks.CONCRETE.blue().defaultBlockState();
		};
		BlockState light = card == 2 ? Blocks.CONCRETE.white().defaultBlockState() : Blocks.CONCRETE.yellow().defaultBlockState();
		List<Entity> out = new ArrayList<>();
		Display.BlockDisplay panel = Counting.block(level, back, 1.0F);
		panel.setTransformation(new Transformation(new Vector3f(-0.45F, 0.1F, -0.05F), null, new Vector3f(0.9F, 0.9F, 0.1F), null));
		panel.snapTo(foot.x, foot.y, foot.z, 0, 0);
		out.add(add(panel));
		Display.BlockDisplay disc = Counting.block(level, light, 1.0F);
		float up = card == 1 ? 0.2F : 0.6F;
		disc.setTransformation(new Transformation(new Vector3f(-0.12F, up, 0.06F), null, new Vector3f(0.24F, 0.24F, 0.05F), null));
		disc.snapTo(foot.x, foot.y, foot.z, 0, 0);
		out.add(add(disc));
		return out;
	}

	private Entity item(ItemStack stack, float size, Vec3 foot) {
		Entity shown = Counting.item(level, stack, size);
		if (shown == null) return null;
		shown.snapTo(foot.x, foot.y, foot.z, 0, 0);
		return add(shown);
	}

	private Entity block(BlockState state, float size, Vec3 foot) {
		Entity shown = Counting.block(level, state, size);
		if (shown == null) return null;
		shown.snapTo(foot.x, foot.y, foot.z, 0, 0);
		return add(shown);
	}

	@Override
	public Narration ask(Narration narration) {
		return switch (kind) {
			case SIZE -> line(narration, "phrase/little_to_big", "Put them in order, little to big!");
			case MORNING -> line(narration, "scene/mixed_up_morning", "Oh no, he's all mixed up! Can you put his morning in order?");
			default -> line(line(narration, "phrase/what_happens_first", "What happens first?"), "phrase/first_next_last", "Put them in order: first, next, last!");
		};
	}

	@Override
	protected Verdict judge(ServerPlayer player, Choice picked) {
		if (Integer.parseInt(picked.id()) != done) return Verdict.WRONG;
		return done + 1 == cards.size() ? Verdict.RIGHT : Verdict.HOLD;
	}

	private void up(Choice picked) {
		int k = Integer.parseInt(picked.id());
		Vec3 to = Vec3.atBottomCenterOf(slot(k)).add(0, 0, 0.7);
		for (Entity part : drawn.getOrDefault(picked, List.of())) part.discard();
		drawn.put(picked, draw(k, to));
		unhit(picked);
	}

	@Override
	protected void hold(ServerPlayer player, Choice picked, Narration telling) {
		done++;
		telling.say(picked.clip(), "up:" + picked.id(), p -> up(picked), CaptionLine.plain(picked.said()), 4);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		done++;
		telling.say(picked.clip(), "up:" + picked.id(), p -> up(picked), CaptionLine.plain(picked.said()), 6);
		if (kind == LogicCourse.Kind.STORY && target.equals("day")) {
			// And the sky does it too: day, then dusk, then night, and back to day.
			telling.then("sky:dusk", p -> room.present().forEach(one -> Sky.glide(one, Sky.Time.DUSK.ticks, 30)), 34)
				.then("sky:night", p -> room.present().forEach(one -> Sky.glide(one, Sky.Time.NIGHT.ticks, 30)), 40)
				.then("sky:day", p -> room.present().forEach(one -> Sky.glide(one, Sky.Time.DAY.ticks, 40)), 44);
		}
	}
}
