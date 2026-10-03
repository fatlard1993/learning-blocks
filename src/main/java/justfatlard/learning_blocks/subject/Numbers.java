package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.puzzle.NumberRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Numbers, as a stub: one kind, "which block says three?", for the numerals one to five on wooden
 * number tiles, mirroring the letters level. Its items are {@code numbers:numeral:<n>}; its first
 * level is done once one to five are mastered. Real content comes later.
 */
public final class Numbers implements Subject {
	public static final Numbers INSTANCE = new Numbers();
	public static final String FIND = "find";
	/** The first level's numerals. */
	static final int FIRST = 1;
	static final int LAST = 5;

	private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
		"eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};

	/** The number each child was last asked, so it is not asked twice running. */
	private final Map<UUID, Integer> last = new HashMap<>();

	private Numbers() {}

	public static String numeral(int n) {
		return "numbers:numeral:" + n;
	}

	public static String word(int n) {
		return n >= 0 && n < WORDS.length ? WORDS[n] : String.valueOf(n);
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

	/** One to five, all mastered. */
	public boolean levelDone(Progress progress) {
		for (int n = FIRST; n <= LAST; n++) if (!progress.itemMastered(numeral(n))) return false;
		return true;
	}

	@Override
	public @Nullable SubjectRound round(ServerPlayer player, @Nullable String kind, @Nullable String target, RandomSource random) {
		if (kind != null && !kind.equals(FIND)) return null;
		Room room = Rooms.of(player);
		if (room == null) return null;
		Progress progress = Progress.of(player);
		int n;
		if (target != null) {
			try {
				n = Integer.parseInt(target);
			} catch (NumberFormatException e) {
				return null;
			}
			if (n < FIRST || n > LAST) return null;
		} else {
			// The fewest clean first, not one asked today if there is another, never the last one twice.
			List<Integer> options = new ArrayList<>();
			for (int i = FIRST; i <= LAST; i++) options.add(i);
			Integer before = last.get(player.getUUID());
			if (before != null) options.remove(before);
			java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
			options.sort(Comparator.<Integer>comparingInt(i -> progress.itemUsedToday(numeral(i)) ? 1 : 0)
				.thenComparingInt(i -> progress.itemClean(numeral(i))));
			n = options.getFirst();
		}
		last.put(player.getUUID(), n);
		List<Integer> others = new ArrayList<>();
		for (int i = FIRST; i <= LAST; i++) if (i != n) others.add(i);
		java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
		if (!room.practice()) progress.useItemToday(player, numeral(n), FIND);
		return new NumberRound(room, n, others.subList(0, 2), random);
	}

	@Override
	public List<String> kinds() {
		return List.of(FIND);
	}

	@Override
	public List<String> targets(String kind) {
		if (!kind.equals(FIND)) return List.of();
		List<String> all = new ArrayList<>();
		for (int i = FIRST; i <= LAST; i++) all.add(String.valueOf(i));
		return all;
	}

	@Override
	public List<String> items() {
		List<String> all = new ArrayList<>();
		for (int i = FIRST; i <= LAST; i++) all.add(numeral(i));
		return all;
	}

	@Override
	public boolean mastered(Progress progress, String item) {
		return progress.itemMastered(item);
	}

	@Override
	public List<String> summary(Progress progress) {
		int mastered = 0;
		for (String item : items()) if (progress.itemMastered(item)) mastered++;
		return List.of("Numbers: " + mastered + " of " + items().size() + " numerals mastered (1 to 5)" + (levelDone(progress) ? ", level done" : ""));
	}
}
