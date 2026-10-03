package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A counting scene, with exactly one answer that fits. "The three pigs are hungry, but there's
 * only one carrot!" How many more carrots? Or: "Each villager wants a cake!", some villagers, fewer
 * cakes. The right number: the missing ones pop out of the button, one to each, and everyone is
 * happy.
 */
public final class CountSceneRound extends NumbersRound {
	private final int creatures;
	private final int have;
	private final Counting.What who;
	private final Counting.What food;
	private final List<Entity> crowd = new ArrayList<>();
	private final List<Entity> meals = new ArrayList<>();

	public CountSceneRound(Room room, String scene, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.SCENE, scene, stage, random);
		if (scene.equals("pigs")) {
			// The line says three pigs and one carrot.
			creatures = 3;
			have = 1;
			who = Counting.What.PIG;
			food = Counting.What.CARROT;
		} else {
			creatures = 2 + random.nextInt(4);
			have = 1 + random.nextInt(creatures - 1);
			who = Counting.What.VILLAGER;
			food = Counting.What.CAKE;
		}
		tiles(creatures - have, 1, 5);
	}

	public int more() {
		return creatures - have;
	}

	private Vec3 spot(int i) {
		return Vec3.atBottomCenterOf(layout.origin()).add((i - (creatures - 1) / 2.0) * 1.2, 0, 0.2);
	}

	private Vec3 mealSpot(int i) {
		return spot(i).add(0, 0, 0.8);
	}

	@Override
	protected void board() {}

	@Override
	protected void started(ServerPlayer player) {
		for (int i = 0; i < creatures; i++) {
			Entity one = Counting.make(level, who, 0.55F);
			if (one == null) continue;
			Vec3 at = spot(i);
			one.snapTo(at.x, at.y, at.z, 0, 0);
			add(one);
			crowd.add(one);
		}
		for (int i = 0; i < have; i++) {
			Entity meal = Counting.make(level, food, 0.55F);
			if (meal == null) continue;
			Vec3 at = mealSpot(i);
			meal.snapTo(at.x, at.y, at.z, 0, 0);
			add(meal);
			meals.add(meal);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		if (target.equals("pigs")) line(narration, "scene/hungry_pigs", "The three pigs are hungry, but there's only one carrot!");
		else line(narration, "scene/cake_for_everyone", "Each villager wants a cake!");
		line(narration, "phrase/how_many_more", "How many more");
		return narration.say(food.clip(), "what:" + food.plural, p -> {}, CaptionLine.plain(food.plural + "?"), Narration.GAP);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		int more = creatures - have;
		telling.then("serve", p -> {
			for (int i = have; i < creatures; i++) {
				Entity meal = Counting.make(level, food, 0.55F);
				if (meal == null) continue;
				meals.add(meal);
				int which = i;
				Counting.fly(this, p, meal, mealSpot(i), (i - have) * 5, landed -> {
					Entity one = which < crowd.size() ? crowd.get(which) : null;
					if (one != null) level.sendParticles(ParticleTypes.HEART, one.getX(), one.getY() + one.getBbHeight() + 0.2, one.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
				});
			}
		}, Counting.dispenseTicks(more, 5));
		telling.say(NumberBlock.soundOf(more), "right:" + more, p -> {}, CaptionLine.plain(Numbers.word(more) + " more " + food.plural + "!"), AnswerNarration.WORD_GAP);
	}

	public List<Entity> meals() {
		return List.copyOf(meals);
	}

	public int creatures() {
		return creatures;
	}
}
