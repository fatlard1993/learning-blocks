package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.numbers.NumberCourse.Band;
import justfatlard.learning_blocks.numbers.NumberCourse.Kind;
import justfatlard.learning_blocks.puzzle.BedsRound;
import justfatlard.learning_blocks.puzzle.CountRound;
import justfatlard.learning_blocks.puzzle.CountSceneRound;
import justfatlard.learning_blocks.puzzle.LookRound;
import justfatlard.learning_blocks.puzzle.MakeRound;
import justfatlard.learning_blocks.puzzle.NameRound;
import justfatlard.learning_blocks.puzzle.NextRound;
import justfatlard.learning_blocks.puzzle.NumberBobRound;
import justfatlard.learning_blocks.puzzle.NumberRound;
import justfatlard.learning_blocks.puzzle.OrderRound;
import justfatlard.learning_blocks.puzzle.PensRound;
import justfatlard.learning_blocks.puzzle.PictureRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.puzzle.ToyboxRound;
import justfatlard.learning_blocks.puzzle.TowersRound;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Memory;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Numbers: stages N0 to N2 (see {@link NumberCourse}), on wooden number tiles, with the room's own
 * fun - creatures and things out of the button, counted and glowing, scenes, Bob with number
 * cards, the child's own Toybox. Which round next: the band's main kind most of the time, one of
 * its extras every third round, and now and then an earlier band's item due for review; within a
 * kind, the target least known and not asked today, never the same twice running.
 */
public final class Numbers implements Subject {
	public static final Numbers INSTANCE = new Numbers();

	private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
		"eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
		"plus", "minus", "equals", "same", "different"};

	/** Each child's numbers rounds so far this session, and their last item: for the mix and no repeats. */
	private final Map<UUID, Integer> rounds = new HashMap<>();
	private final Map<UUID, String> last = new HashMap<>();
	private final Map<UUID, Integer> extra = new HashMap<>();

	private Numbers() {}

	public static String numeral(int n) {
		return Kind.FIND.item(String.valueOf(n));
	}

	public static String word(int n) {
		return n >= 0 && n < WORDS.length ? WORDS[n] : String.valueOf(n);
	}

	/** The counting toys, each earned by mastering some numbers. */
	public static void linkToys() {
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:sorter", items(Kind.FIND, 0, 10));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:dice", items(Kind.LOOK, 1, 6));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:abacus", items(Kind.COUNT, 1, 5));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:dominoes", items(Kind.COUNT, 6, 10));
		justfatlard.learning_blocks.reward.Earned.linkItems("toy:scale", List.of(Kind.PENS.item("more"), Kind.PENS.item("fewer"), Kind.PENS.item("same"),
			Kind.TOWERS.item("taller"), Kind.TOWERS.item("shorter")));
	}

	private static List<String> items(Kind kind, int from, int to) {
		List<String> out = new ArrayList<>();
		for (int i = from; i <= to; i++) out.add(kind.item(String.valueOf(i)));
		return out;
	}

	@Override
	public String id() {
		return "numbers";
	}

	@Override
	public String label() {
		return "Numbers";
	}

	@Override
	public DyeColor colour() {
		return DyeColor.LIME;
	}

	@Override
	public String hello() {
		return "phrase/subject_numbers";
	}

	@Override
	public boolean canAsk(ServerPlayer player) {
		return true;
	}

	/** Whether the first stage, N0, is done. */
	public boolean levelDone(Progress progress) {
		return NumberCourse.stageDone(progress, 0);
	}

	/** The band a round is made for: the child's, or the last band once everything is done. */
	private static Band bandOf(Progress progress) {
		Band band = NumberCourse.current(progress);
		return band == null ? NumberCourse.BANDS.getLast() : band;
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
			} else if (count % 3 == 0) {
				kind = nextExtra(player, room, progress, band);
			} else {
				kind = band.main();
			}
		}
		String chosen = target != null ? target : pickTarget(player, progress, kind, band, random);
		if (chosen == null) {
			if (kindId != null) return null;
			kind = band.main();
			chosen = pickTarget(player, progress, kind, band, random);
			if (chosen == null) return null;
		}
		if (target != null && !NumberCourse.targets(kind).contains(target)) return null;
		SubjectRound made = build(room, player, progress, kind, chosen, band, random);
		if (made == null) return null;
		last.put(player.getUUID(), made.item());
		if (!room.practice()) progress.useItemToday(player, made.item(), kind.id);
		return made;
	}

	/** Now and then (one in five), an earlier band's item that is due again: its band, or null. */
	private static @Nullable Band review(Progress progress, Band band, RandomSource random) {
		List<Band> earlier = NumberCourse.before(band);
		if (earlier.isEmpty() || random.nextInt(5) != 0) return null;
		List<Band> due = earlier.stream().filter(b -> b.gate().stream().anyMatch(progress::itemDue)).toList();
		return due.isEmpty() ? null : due.get(random.nextInt(due.size()));
	}

	/** The band's extras in turn, skipping any that can't be asked of this child just now. */
	private Kind nextExtra(ServerPlayer player, Room room, Progress progress, Band band) {
		List<Kind> extras = band.extras();
		int start = extra.merge(player.getUUID(), 1, Integer::sum);
		for (int i = 0; i < extras.size(); i++) {
			Kind kind = extras.get((start + i) % extras.size());
			if (available(player, room, progress, kind, band)) return kind;
		}
		return band.main();
	}

	private static boolean available(ServerPlayer player, Room room, Progress progress, Kind kind, Band band) {
		return switch (kind) {
			case TOYBOX -> ownedCount(progress, "toys") >= 1 || ownedCount(progress, "plushies") >= 1;
			case NAME -> !readNumberWords(progress, band).isEmpty();
			case TEAM -> room.shared();
			default -> true;
		};
	}

	private static int ownedCount(Progress progress, String which) {
		int n = ToyboxRound.owned(progress, which).size();
		return n <= 10 ? n : 0;
	}

	/** The number words this child has read in Reading ("six", "ten"), as numbers, within the band. */
	private static List<String> readNumberWords(Progress progress, Band band) {
		List<String> out = new ArrayList<>();
		for (String n : NumberCourse.targets(Kind.NAME)) {
			String text = word(Integer.parseInt(n));
			Word read = Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElse(null);
			if (read != null && progress.words().contains(read.id().toString())) out.add(n);
		}
		return out;
	}

	/** What a kind can ask in this band. */
	static List<String> candidates(Progress progress, Kind kind, Band band) {
		boolean first = band.code().endsWith("a");
		return switch (kind) {
			case FIND, COUNT -> kind == band.main() ? band.mainTargets() : range(1, first ? 5 : 10);
			case LOOK -> range(1, first ? 3 : 6);
			case NAME -> readNumberWords(progress, band);
			case MAKE, PICTURE -> range(first ? 1 : 3, first ? 5 : 10);
			case BEDS -> range(1, first ? 2 : 4);
			case TOYBOX -> {
				List<String> out = new ArrayList<>();
				if (ownedCount(progress, "toys") >= 1) out.add("toys");
				if (ownedCount(progress, "plushies") >= 1) out.add("plushies");
				yield out;
			}
			case BOB, FIX -> range(first ? 1 : 3, first ? 4 : 6);
			case TEAM -> range(first ? 2 : 5, first ? 5 : 10);
			case PENS -> List.of("more", "fewer", "same");
			case TOWERS -> kind == band.main() ? band.mainTargets() : List.of("taller", "shorter");
			case ORDER -> first ? List.of("5") : List.of("5", "10");
			default -> NumberCourse.targets(kind);
		};
	}

	private static List<String> range(int from, int to) {
		List<String> out = new ArrayList<>();
		for (int i = from; i <= to; i++) out.add(String.valueOf(i));
		return out;
	}

	/** The target least known and not asked today, never the last one again; null if there is none. */
	private @Nullable String pickTarget(ServerPlayer player, Progress progress, Kind kind, Band band, RandomSource random) {
		List<String> options = new ArrayList<>(candidates(progress, kind, band));
		if (options.isEmpty()) return null;
		String before = last.get(player.getUUID());
		if (options.size() > 1) options.removeIf(t -> kind.item(t).equals(before));
		java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
		options.sort(Comparator.<String>comparingInt(t -> progress.itemUsedToday(kind.item(t)) ? 1 : 0)
			.thenComparingInt(t -> progress.itemClean(kind.item(t))));
		return options.getFirst();
	}

	/** The round itself. */
	private static @Nullable SubjectRound build(Room room, ServerPlayer player, Progress progress, Kind kind, String target, Band band, RandomSource random) {
		int stage = band.stage();
		boolean first = band.code().endsWith("a");
		int lo = band.code().equals("N0a") ? 1 : 0;
		int hi = band.code().equals("N0a") ? 5 : 10;
		try {
			return switch (kind) {
				case FIND -> new NumberRound(room, Integer.parseInt(target), lo, hi, stage, random);
				case LOOK -> new LookRound(room, Integer.parseInt(target), 1, first && stage == 0 ? 5 : 10, stage, random);
				case NAME -> new NameRound(room, Integer.parseInt(target), 0, 10, stage, random);
				case COUNT -> new CountRound(room, Integer.parseInt(target), false, 1, 10, stage, random);
				case TEAM -> new CountRound(room, Integer.parseInt(target), true, 1, 10, stage, random);
				case MAKE -> new MakeRound(room, Integer.parseInt(target), 1, 10, stage, random);
				case BEDS -> new BedsRound(room, Integer.parseInt(target), stage, random);
				case TOYBOX -> {
					var owned = ToyboxRound.owned(progress, target);
					yield owned.isEmpty() || owned.size() > 10 ? null : new ToyboxRound(room, target, owned, stage, random);
				}
				case SCENE -> new CountSceneRound(room, target, stage, random);
				case BOB, FIX -> new NumberBobRound(room, kind, Integer.parseInt(target), stage, random);
				case MORE -> new NumberBobRound(room, kind, 2 + random.nextInt(3), stage, random);
				case PENS -> new PensRound(room, target, stage, random);
				case TOWERS -> new TowersRound(room, target, stage, random);
				case ORDER -> new OrderRound(room, Integer.parseInt(target), stage, random);
				case NEXT -> new NextRound(room, target, target.equals("after") ? random.nextInt(10) : 1 + random.nextInt(10), stage, random);
				case PICTURE -> new PictureRound(room, Integer.parseInt(target), stage, random);
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public List<String> kinds() {
		return java.util.Arrays.stream(Kind.values()).map(kind -> kind.id).toList();
	}

	@Override
	public List<String> targets(String kind) {
		Kind found = Kind.of(kind);
		return found == null ? List.of() : NumberCourse.targets(found);
	}

	@Override
	public List<String> items() {
		return NumberCourse.gateItems();
	}

	@Override
	public boolean mastered(Progress progress, String item) {
		return progress.itemMastered(item);
	}

	@Override
	public List<String> summary(Progress progress) {
		List<String> lines = new ArrayList<>();
		lines.add("Numbers: " + NumberCourse.describe(progress));
		lines.add("  numerals " + masteredOf(progress, items(Kind.FIND, 0, 10)) + " of 11, counting " + masteredOf(progress, items(Kind.COUNT, 1, 10))
			+ " of 10, comparing " + masteredOf(progress, List.of(Kind.PENS.item("more"), Kind.PENS.item("fewer"), Kind.PENS.item("same"),
				Kind.TOWERS.item("taller"), Kind.TOWERS.item("shorter"), Kind.TOWERS.item("diff"))) + " of 6 mastered");
		List<String> mixups = mixups(progress, 3);
		if (!mixups.isEmpty()) lines.add("  mixes up " + String.join(", ", mixups));
		return lines;
	}

	private static int masteredOf(Progress progress, List<String> items) {
		return (int) items.stream().filter(progress::itemMastered).count();
	}

	/** Numbers picked for others most often, as "6/9: 3 times", whichever way round. */
	public static List<String> mixups(Progress progress, int most) {
		Map<String, Integer> pairs = new LinkedHashMap<>();
		for (Map.Entry<String, Memory.Item> entry : progress.memory().items().entrySet()) {
			String key = entry.getKey();
			if (!key.startsWith("numbers:")) continue;
			String asked = key.substring(key.lastIndexOf(':') + 1);
			if (!asked.chars().allMatch(Character::isDigit)) continue;
			for (String picked : entry.getValue().mixups()) {
				if (!picked.chars().allMatch(Character::isDigit) || picked.equals(asked)) continue;
				int a = Integer.parseInt(asked);
				int b = Integer.parseInt(picked);
				pairs.merge(Math.min(a, b) + "/" + Math.max(a, b), 1, Integer::sum);
			}
		}
		return pairs.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(most)
			.map(e -> e.getKey() + " (" + e.getValue() + (e.getValue() == 1 ? " time)" : " times)")).toList();
	}
}
