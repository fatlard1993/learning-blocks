package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.Plushies;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.Toys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * "What's missing?": three (or four) things up on the board, "Look carefully!"; the lights go out
 * for a blink (the child's own sky shade), and when they come back one is gone. Three to pick from
 * on the floor: the missing one, one still there, and one that never was. Right: it is back.
 */
public final class MissingRound extends LogicRound {
	/** What things there are, by their word. */
	private record Thing(String word, ItemStack stack) {}

	private final List<Thing> set = new ArrayList<>();
	private final int gone;
	private final List<Entity> up = new ArrayList<>();
	private boolean hidden;
	private final List<Thing> offered = new ArrayList<>();

	public MissingRound(Room room, String count, int stage, RandomSource random) {
		super(room, LogicCourse.Kind.MISSING, count, stage, random);
		List<Thing> pool = new ArrayList<>(List.of(new Thing("owl", Plushies.stack(Plush.OWL)), new Thing("duck", Plushies.stack(Plush.DUCK)),
			new Thing("car", Toys.stack(Toy.CAR)), new Thing("drum", Toys.stack(Toy.DRUM)), new Thing("kite", Toys.stack(Toy.KITE)),
			new Thing("hen", new ItemStack(Items.EGG)), new Thing("bun", new ItemStack(Items.BREAD))));
		pool.removeIf(thing -> thing.word().equals("hen"));
		Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		int n = Integer.parseInt(count);
		set.addAll(pool.subList(0, n));
		Thing never = pool.get(n);
		this.gone = random.nextInt(n);
		Thing still = set.get((gone + 1) % n);
		offered.addAll(List.of(set.get(gone), still, never));
		Collections.shuffle(offered, new java.util.Random(random.nextLong()));
		for (Thing thing : offered) {
			Choice choice = new Choice(thing.word(), null, "word/" + thing.word(), thing.word());
			choices.add(choice);
			if (thing == set.get(gone)) answer(choice);
		}
	}

	private Vec3 boardSpot(int i) {
		return Vec3.atBottomCenterOf(layout.origin().offset(0, 1, -4)).add((i - (set.size() - 1) / 2.0) * 1.7, 0, 0.3);
	}

	private @Nullable Entity showUp(int i) {
		Entity shown = Counting.item(level, set.get(i).stack(), 1.2F);
		if (shown == null) return null;
		Vec3 at = boardSpot(i);
		shown.snapTo(at.x, at.y, at.z, 0, 0);
		return add(shown);
	}

	@Override
	protected void board() {
		for (int i = 0; i < set.size(); i++) up.add(showUp(i));
	}

	@Override
	protected void stand(Choice choice, BlockPos spot) {
		Thing thing = offered.get(choices.indexOf(choice));
		Entity shown = Counting.item(level, thing.stack(), 1.1F);
		if (shown == null) return;
		Vec3 at = Vec3.atBottomCenterOf(spot);
		shown.snapTo(at.x, at.y, at.z, 0, 0);
		add(shown);
	}

	/** Whether the one has gone yet, for tests. */
	public boolean hidden() {
		return hidden;
	}

	@Override
	public Narration ask(Narration narration) {
		if (!hidden) {
			line(narration, "phrase/look_carefully", "Look carefully!");
			narration.then("looking", p -> {}, 50);
			narration.then("dark", p -> {
				for (ServerPlayer one : room.present()) justfatlard.learning_blocks.classroom.Sky.dark(one, 16);
			}, 6);
			narration.then("gone", p -> {
				Entity it = up.get(gone);
				if (it != null) it.discard();
				hidden = true;
			}, 14);
		}
		return line(narration, "phrase/whats_missing", "What's missing?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say(picked.clip(), "right:" + picked.id(), p -> {
			Entity back = showUp(gone);
			if (back != null) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, back.getX(), back.getY() + 0.8, back.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}, CaptionLine.plain(picked.said() + "!"), AnswerNarration.WORD_GAP);
	}

	public int gone() {
		return gone;
	}
}
