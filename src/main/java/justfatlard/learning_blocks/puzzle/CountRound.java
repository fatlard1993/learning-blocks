package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * "How many pigs?": creatures pop out of the button and stand in rows of five; the child counts
 * and picks the tile. On the answer, "Let's count them!", and each glows in turn as it is counted,
 * the way the blend lights the letter tiles. Reading together, a team-up: two kinds of creature,
 * "you count these, and you count those! How many altogether?"
 */
public final class CountRound extends NumbersRound {
	private final int n;
	private final Counting.What what;
	private final Counting.What other;
	/** Of the n, how many are the first kind: all of them, unless it is a team-up. */
	private final int first;
	private final List<Entity> things = new ArrayList<>();

	public CountRound(Room room, int n, boolean team, int lo, int hi, int stage, RandomSource random) {
		super(room, team ? NumberCourse.Kind.TEAM : NumberCourse.Kind.COUNT, String.valueOf(n), stage, random);
		this.n = n;
		List<Counting.What> creatures = new ArrayList<>(Counting.What.CREATURES);
		java.util.Collections.shuffle(creatures, new java.util.Random(random.nextLong()));
		this.what = creatures.get(0);
		this.other = creatures.get(1);
		this.first = team && n >= 2 ? 1 + random.nextInt(n - 1) : n;
		tiles(n, Math.max(1, lo), hi);
	}

	public int count() {
		return n;
	}

	/** The creatures out, in counting order: for tests. */
	public List<Entity> things() {
		return List.copyOf(things);
	}

	@Override
	protected void board() {}

	@Override
	protected void started(ServerPlayer player) {
		List<Vec3> spots = new ArrayList<>();
		for (int i = 0; i < n; i++) spots.add(Counting.spot(layout, i, n));
		things.addAll(Counting.dispense(this, player, what, first, spots.subList(0, first), 4, null, 0.5F));
		if (first < n) {
			List<Entity> more = new ArrayList<>();
			for (int i = first; i < n; i++) {
				Entity thing = Counting.make(level, other, 0.5F);
				if (thing == null) continue;
				more.add(thing);
				Counting.fly(this, player, thing, spots.get(i), i * 4, null);
			}
			things.addAll(more);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		if (first < n) return line(narration, "phrase/team_count", "You count these, and you count those! How many altogether?");
		line(narration, "phrase/how_many", "How many");
		return narration.say(what.clip(), "what:" + what.plural, p -> {}, CaptionLine.plain(what.plural + "?"), Narration.GAP);
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		line(telling, "phrase/count_with_me", "Let's count them!");
		Counting.chant(telling, n, things, false);
		telling.say(NumberBlock.soundOf(n), "right:" + n, p -> {}, CaptionLine.plain(Numbers.word(n) + " " + (first < n ? "altogether!" : what.plural + "!")), AnswerNarration.WORD_GAP);
	}
}
