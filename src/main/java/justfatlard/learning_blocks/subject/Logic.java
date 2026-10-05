package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.logic.LogicCourse.Band;
import justfatlard.learning_blocks.logic.LogicCourse.Kind;
import justfatlard.learning_blocks.puzzle.LogicBobRound;
import justfatlard.learning_blocks.puzzle.MissingRound;
import justfatlard.learning_blocks.puzzle.NotesRound;
import justfatlard.learning_blocks.puzzle.OddRound;
import justfatlard.learning_blocks.puzzle.PatternRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SameRound;
import justfatlard.learning_blocks.puzzle.SequenceRound;
import justfatlard.learning_blocks.puzzle.SortRound;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Logic: stages L0 to L3 (see {@link LogicCourse}): sorting and matching, patterns, odd one out
 * and what's missing, putting things in order, and Bob, with the room's own fun. Which round next,
 * as for Numbers: the band's main kind most of the time, one of its extras every third round, and
 * now and then an earlier band's item due for review; the target least known and not asked today.
 */
public final class Logic implements Subject {
	public static final Logic INSTANCE = new Logic();
	/** What comes next, and its first item: the kind and item the stub had. */
	public static final String NEXT = Kind.NEXT.id;
	public static final String AB = Kind.NEXT.item("ab");

	private final Map<UUID, Integer> rounds = new HashMap<>();
	private final Map<UUID, String> last = new HashMap<>();
	private final Map<UUID, Integer> extra = new HashMap<>();

	private Logic() {}

	/** The thinking toys, each earned by mastering some logic. */
	public static void linkToys() {
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:puzzle", List.of(Kind.SORT.item("kind"), Kind.SORT.item("colour"),
			Kind.SORT.item("size"), Kind.SORT.item("things")));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:maze", List.of(Kind.NEXT.item("ab"), Kind.NEXT.item("aab"), Kind.NEXT.item("abb"),
			Kind.NEXT.item("abc"), Kind.NEXT.item("grow")));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:cube", List.of(Kind.ODD.item("kind"), Kind.ODD.item("colour"), Kind.ODD.item("category"),
			Kind.MISSING.item("3"), Kind.MISSING.item("4")));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:cups", List.of(Kind.SIZE.item("3"), Kind.SIZE.item("4")));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:robot", List.of(Kind.STORY.item("seed"), Kind.STORY.item("egg"), Kind.STORY.item("day"),
			Kind.MORNING.item("morning")));
	}

	@Override
	public String id() {
		return "logic";
	}

	@Override
	public String label() {
		return "Logic";
	}

	@Override
	public DyeColor colour() {
		return DyeColor.PURPLE;
	}

	@Override
	public String hello() {
		return "phrase/subject_logic";
	}

	@Override
	public boolean canAsk(ServerPlayer player) {
		return true;
	}

	/** Whether the first stage, L0, is done. */
	public boolean levelDone(Progress progress) {
		return LogicCourse.stageDone(progress, 0);
	}

	private static Band bandOf(Progress progress) {
		Band band = LogicCourse.current(progress);
		return band == null ? LogicCourse.BANDS.getLast() : band;
	}

	@Override
	public @Nullable SubjectRound round(ServerPlayer player, @Nullable String kindId, @Nullable String target, RandomSource random) {
		Room room = Rooms.of(player);
		if (room == null) return null;
		Progress progress = Progress.of(player);
		Band band = bandOf(progress);
		Kind kind;
		if (kindId != null) {
			kind = Kind.of(kindId);
			if (kind == null) return null;
		} else {
			int count = rounds.merge(player.getUUID(), 1, Integer::sum);
			Band review = review(progress, band, random);
			if (review != null) {
				band = review;
				kind = review.main();
			} else if (count % 3 == 0 && !band.extras().isEmpty()) {
				int start = extra.merge(player.getUUID(), 1, Integer::sum);
				kind = band.extras().get(start % band.extras().size());
			} else {
				kind = band.main();
			}
		}
		if (target != null && !LogicCourse.targets(kind).contains(target)) return null;
		String chosen = target != null ? target : pickTarget(player, progress, kind, band, random);
		SubjectRound made = build(room, kind, chosen, band.stage(), random);
		if (made == null) return null;
		last.put(player.getUUID(), made.item());
		if (!room.practice()) progress.useItemToday(player, made.item(), kind.id);
		return made;
	}

	private static @Nullable Band review(Progress progress, Band band, RandomSource random) {
		List<Band> earlier = LogicCourse.before(band);
		if (earlier.isEmpty() || random.nextInt(5) != 0) return null;
		List<Band> due = earlier.stream().filter(b -> b.gate().stream().anyMatch(progress::itemDue)).toList();
		return due.isEmpty() ? null : due.get(random.nextInt(due.size()));
	}

	/** What a kind can ask in this band. */
	static List<String> candidates(Kind kind, Band band) {
		if (kind == band.main()) return band.mainTargets();
		boolean first = band.code().endsWith("a");
		return switch (kind) {
			case COPY, MISTAKE -> first ? List.of("ab", "aab") : List.of("ab", "aab", "abb", "abc");
			case NOTES -> first ? List.of("ab") : List.of("ab", "abb");
			case MISSING -> first ? List.of("3") : List.of("3", "4");
			case SIZE -> first ? List.of("3") : List.of("3", "4");
			case BOBSORT -> List.of("pigs", "cows", "sheep");
			default -> LogicCourse.targets(kind);
		};
	}

	private String pickTarget(ServerPlayer player, Progress progress, Kind kind, Band band, RandomSource random) {
		List<String> options = new ArrayList<>(candidates(kind, band));
		String before = last.get(player.getUUID());
		if (options.size() > 1) options.removeIf(t -> kind.item(t).equals(before));
		java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
		options.sort(Comparator.<String>comparingInt(t -> progress.itemUsedToday(kind.item(t)) ? 1 : 0)
			.thenComparingInt(t -> progress.itemClean(kind.item(t))));
		return options.getFirst();
	}

	private static @Nullable SubjectRound build(Room room, Kind kind, String target, int stage, RandomSource random) {
		return switch (kind) {
			case SORT -> new SortRound(room, target, stage, random);
			case SAME -> new SameRound(room, target, stage, random);
			case NEXT, MISTAKE, COPY -> new PatternRound(room, kind, target, stage, random);
			case NOTES -> new NotesRound(room, target, stage, random);
			case ODD -> new OddRound(room, target, stage, random);
			case MISSING -> new MissingRound(room, target, stage, random);
			case STORY, SIZE, MORNING -> new SequenceRound(room, kind, target, stage, random);
			case BOBSORT, BOBBIG, BOBLEFT, BOBFIX -> new LogicBobRound(room, kind, target, stage, random);
		};
	}

	@Override
	public List<String> kinds() {
		return java.util.Arrays.stream(Kind.values()).map(kind -> kind.id).toList();
	}

	@Override
	public List<String> targets(String kind) {
		Kind found = Kind.of(kind);
		return found == null ? List.of() : LogicCourse.targets(found);
	}

	@Override
	public List<String> items() {
		return LogicCourse.gateItems();
	}

	@Override
	public boolean mastered(Progress progress, String item) {
		return progress.itemMastered(item);
	}

	@Override
	public List<String> summary(Progress progress) {
		List<String> lines = new ArrayList<>();
		lines.add("Logic: " + LogicCourse.describe(progress));
		lines.add("  sorting " + count(progress, Kind.SORT) + " of 4, patterns " + count(progress, Kind.NEXT) + " of 5, odd one out "
			+ count(progress, Kind.ODD) + " of 3, in order " + (count(progress, Kind.STORY) + count(progress, Kind.SIZE) + count(progress, Kind.MORNING))
			+ " of 6 mastered");
		List<String> missed = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			for (String target : LogicCourse.targets(kind)) {
				var item = progress.memory().item(kind.item(target));
				if (item != null && item.misses() >= 2 && !progress.itemMastered(kind.item(target))) missed.add(kind.id + " " + target);
			}
		}
		if (!missed.isEmpty()) lines.add("  finds hard: " + String.join(", ", missed.subList(0, Math.min(4, missed.size()))));
		return lines;
	}

	private static int count(Progress progress, Kind kind) {
		return (int) LogicCourse.targets(kind).stream().filter(t -> progress.itemMastered(kind.item(t))).count();
	}
}
