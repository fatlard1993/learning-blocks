package justfatlard.phonics.reward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

/**
 * What a child gets right and wrong, kept small: for every word and every sound, how often it was
 * tried, got first try and missed, the last few things built instead, and a Leitner box. A miss sends
 * an item to box 1, a first-try success moves it up a box, and an item is due again after a number of
 * rounds that grows with its box, so what was missed comes back soon and what is solid rarely.
 *
 * <p>Also which pairs of sounds get mixed up (b and d), and time and rounds by day, and the lessons
 * history, for the grown-ups' progress view. Keys: {@code word:<id>} and {@code tile:<grapheme>}.
 */
public final class Memory {

	/** Rounds before an item in each box (1 to 5) is due again. */
	private static final int[] INTERVAL = {0, 2, 5, 12, 30, 80};
	public static final int TOP_BOX = 5;
	/** How many of the things built instead each item keeps. */
	private static final int MIXUPS_KEPT = 5;
	private static final int DAYS_KEPT = 35;
	private static final int LESSONS_KEPT = 20;

	/** One word or sound. */
	public static final class Item {
		int attempts;
		int firstTry;
		int misses;
		int box;
		long seen;
		final List<String> mixups;

		Item(int attempts, int firstTry, int misses, int box, long seen, List<String> mixups) {
			this.attempts = attempts;
			this.firstTry = firstTry;
			this.misses = misses;
			this.box = box;
			this.seen = seen;
			this.mixups = new ArrayList<>(mixups);
		}

		static final Codec<Item> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("attempts", 0).forGetter(item -> item.attempts),
			Codec.INT.optionalFieldOf("first_try", 0).forGetter(item -> item.firstTry),
			Codec.INT.optionalFieldOf("misses", 0).forGetter(item -> item.misses),
			Codec.INT.optionalFieldOf("box", 1).forGetter(item -> item.box),
			Codec.LONG.optionalFieldOf("seen", 0L).forGetter(item -> item.seen),
			Codec.STRING.listOf().optionalFieldOf("mixups", List.of()).forGetter(item -> List.copyOf(item.mixups))
		).apply(instance, Item::new));

		public int attempts() {
			return attempts;
		}

		public int firstTry() {
			return firstTry;
		}

		public int misses() {
			return misses;
		}

		public int box() {
			return box;
		}

		public List<String> mixups() {
			return List.copyOf(mixups);
		}
	}

	/** One day's time and rounds. */
	public record Day(int seconds, int rounds, int solved) {
		static final Codec<Day> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("seconds", 0).forGetter(Day::seconds),
			Codec.INT.optionalFieldOf("rounds", 0).forGetter(Day::rounds),
			Codec.INT.optionalFieldOf("solved", 0).forGetter(Day::solved)
		).apply(instance, Day::new));
	}

	/** How well a word or a sound is known, for the grown-ups: never tried, learning, shaky, or solid. */
	public enum Mastery { NOT_SEEN, LEARNING, SHAKY, SOLID }

	final Map<String, Item> items;
	/** Mixed-up pairs, {@code b|d}, sorted within the pair, and how often. */
	final Map<String, Integer> pairs;
	long rounds;
	final Map<String, Day> days;
	final List<String> lessons;
	/**
	 * Whether this reader's stage has been set by mastery. A reader from before mastery gates is not:
	 * their stage then was by words read, and {@link Progress#of} keeps it as a floor, once.
	 */
	boolean gated;
	/** Whether this reader has been looked at for big-and-little letters, and waived them (the letters level done before). */
	boolean casesChecked;
	boolean casesWaived;
	/**
	 * The day each item last had a clean completion counted: one a day counts towards mastery, so
	 * mastering is clean on {@code mastery_clean} different days. Keys as {@link #items}, and
	 * {@code hit:<key>} for the letters level's counts.
	 */
	final Map<String, String> counted;
	/** The plushies this reader has been given, by name, and how many of each. */
	final Map<String, Integer> plushies;
	/** The day {@link #usedToday} is for, and the lessons given that day: {@code <word id>|<kind>}. */
	String today;
	final java.util.LinkedHashSet<String> usedToday;

	Memory(Map<String, Item> items, Map<String, Integer> pairs, long rounds, Map<String, Day> days, List<String> lessons, boolean gated,
			boolean casesChecked, boolean casesWaived, Map<String, String> counted, String today, List<String> usedToday,
			Map<String, Integer> plushies) {
		this.plushies = new java.util.TreeMap<>(plushies);
		this.counted = new java.util.HashMap<>(counted);
		this.today = today;
		this.usedToday = new java.util.LinkedHashSet<>(usedToday);
		this.gated = gated;
		this.casesChecked = casesChecked;
		this.casesWaived = casesWaived;
		this.items = new LinkedHashMap<>(items);
		this.pairs = new LinkedHashMap<>(pairs);
		this.rounds = rounds;
		this.days = new TreeMap<>(days);
		this.lessons = new ArrayList<>(lessons);
	}

	/** A new reader's: gated from the start. */
	static Memory fresh() {
		return new Memory(Map.of(), Map.of(), 0, Map.of(), List.of(), true, true, false, Map.of(), "", List.of(), Map.of());
	}

	/** What a save from before there was any memory reads as: not yet gated. */
	static Memory legacy() {
		return new Memory(Map.of(), Map.of(), 0, Map.of(), List.of(), false, false, false, Map.of(), "", List.of(), Map.of());
	}

	static final Codec<Memory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(Codec.STRING, Item.CODEC).optionalFieldOf("items", Map.of()).forGetter(m -> m.items),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pairs", Map.of()).forGetter(m -> m.pairs),
		Codec.LONG.optionalFieldOf("rounds", 0L).forGetter(m -> m.rounds),
		Codec.unboundedMap(Codec.STRING, Day.CODEC).optionalFieldOf("days", Map.of()).forGetter(m -> m.days),
		Codec.STRING.listOf().optionalFieldOf("lessons", List.of()).forGetter(m -> List.copyOf(m.lessons)),
		Codec.BOOL.optionalFieldOf("gated", false).forGetter(m -> m.gated),
		Codec.BOOL.optionalFieldOf("cases_checked", false).forGetter(m -> m.casesChecked),
		Codec.BOOL.optionalFieldOf("cases_waived", false).forGetter(m -> m.casesWaived),
		Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("counted", Map.of()).forGetter(m -> m.counted),
		Codec.STRING.optionalFieldOf("today", "").forGetter(m -> m.today),
		Codec.STRING.listOf().optionalFieldOf("used_today", List.of()).forGetter(m -> List.copyOf(m.usedToday)),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("plushies", Map.of()).forGetter(m -> m.plushies)
	).apply(instance, Memory::new));

	/** Whether a clean completion of this key would count today: none has yet. */
	public boolean countsToday(String key, String day) {
		return spreadOff || !day.equals(counted.get(key));
	}

	/** Count it, if it may be: true when it did. */
	boolean count(String key, String day) {
		if (!countsToday(key, day)) return false;
		counted.put(key, day);
		return true;
	}

	private static volatile boolean spreadOff;

	/** For tests: every clean completion counts, as many a day as there are. */
	public static void spreadOffForTests(boolean off) {
		spreadOff = off;
	}

	/** The lessons given today, {@code <word id>|<kind>}; a new day starts empty. */
	java.util.Set<String> usedOn(String day) {
		if (!day.equals(today)) {
			today = day;
			usedToday.clear();
		}
		return usedToday;
	}

	public static String wordKey(String id) {
		return "word:" + id;
	}

	public static String tileKey(String grapheme) {
		return "tile:" + grapheme;
	}

	/** Clean completions: right first time, nothing wrong built or picked before. */
	public int clean(String key) {
		Item item = items.get(key);
		return item == null ? 0 : item.firstTry;
	}

	/** For tests: this many clean completions, in the top box, seen long ago. */
	void master(String key, int clean) {
		Item item = items.computeIfAbsent(key, k -> new Item(0, 0, 0, 1, rounds, List.of()));
		item.firstTry = Math.max(item.firstTry, clean);
		item.attempts = Math.max(item.attempts, clean);
		item.box = TOP_BOX;
		item.seen = rounds - 200;
	}

	void setClean(String key, int clean) {
		Item item = items.computeIfAbsent(key, k -> new Item(0, 0, 0, 1, rounds, List.of()));
		item.firstTry = clean;
		item.attempts = Math.max(item.attempts, clean);
	}

	/** Forget an item entirely: a word an operator sends a child back to read again. */
	void forget(String key) {
		items.remove(key);
	}

	public @Nullable Item item(String key) {
		return items.get(key);
	}

	private Item touch(String key) {
		Item item = items.computeIfAbsent(key, k -> new Item(0, 0, 0, 1, rounds, List.of()));
		item.attempts++;
		item.seen = rounds;
		return item;
	}

	/** Right: first try moves it up a box; right after a miss leaves it where the miss put it. */
	void right(String key, boolean firstTry, String day) {
		Item item = touch(key);
		if (firstTry) {
			// Clean on as many days as mastery asks: a second clean one the same day is practice, not progress.
			if (count(key, day)) item.firstTry++;
			item.box = Math.min(TOP_BOX, item.box + 1);
		}
	}

	/**
	 * Missed, with what was chosen instead: back to box 1, and the mix-up remembered. Something
	 * already mastered only drops a box, and stays mastered: a slip is not forgetting.
	 */
	void missed(String key, @Nullable String instead, int masteredAt) {
		Item item = touch(key);
		item.misses++;
		item.box = item.firstTry >= masteredAt ? Math.max(1, item.box - 1) : 1;
		if (instead != null) {
			item.mixups.add(instead);
			while (item.mixups.size() > MIXUPS_KEPT) item.mixups.removeFirst();
		}
	}

	/** Two sounds mixed up, whichever way round. */
	void mixedUp(String a, String b) {
		if (a.equals(b)) return;
		String pair = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
		pairs.merge(pair, 1, Integer::sum);
	}

	/** Whether this item is due for another look: it has been seen, and its box's rounds have passed. */
	public boolean due(String key) {
		Item item = items.get(key);
		if (item == null || item.attempts == 0) return false;
		return rounds - item.seen >= INTERVAL[Math.max(1, Math.min(TOP_BOX, item.box))];
	}

	/** How many rounds until this item is due; 0 when due, and large for one never seen. */
	public long dueIn(String key) {
		Item item = items.get(key);
		if (item == null || item.attempts == 0) return Long.MAX_VALUE;
		return Math.max(0, INTERVAL[Math.max(1, Math.min(TOP_BOX, item.box))] - (rounds - item.seen));
	}

	/** How well it is known: solid once mastered ({@code masteredAt} clean completions). */
	public Mastery mastery(String key, int masteredAt) {
		Item item = items.get(key);
		if (item == null || item.attempts == 0) return Mastery.NOT_SEEN;
		if (item.firstTry >= masteredAt) return Mastery.SOLID;
		if (item.misses > 0 && item.box <= 2) return Mastery.SHAKY;
		return Mastery.LEARNING;
	}

	/** Mixed-up pairs, most often first. */
	public List<Map.Entry<String, Integer>> confusions() {
		List<Map.Entry<String, Integer>> list = new ArrayList<>(pairs.entrySet());
		list.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
		return list;
	}

	public long rounds() {
		return rounds;
	}

	/** A round began: the clock every due time is counted in. */
	void round(String today) {
		rounds++;
		Day day = days.getOrDefault(today, new Day(0, 0, 0));
		days.put(today, new Day(day.seconds(), day.rounds() + 1, day.solved()));
		trimDays();
	}

	void time(String today, int seconds) {
		if (seconds <= 0) return;
		Day day = days.getOrDefault(today, new Day(0, 0, 0));
		days.put(today, new Day(day.seconds() + seconds, day.rounds(), day.solved()));
		trimDays();
	}

	/** A round solved today, of any kind: what a daily schedule counts. */
	void solved(String today) {
		Day day = days.getOrDefault(today, new Day(0, 0, 0));
		days.put(today, new Day(day.seconds(), day.rounds(), day.solved() + 1));
		trimDays();
	}

	/** Rounds solved on this day. */
	public int solvedOn(String day) {
		Day found = days.get(day);
		return found == null ? 0 : found.solved();
	}

	/** For tests: rounds solved today set to this many. */
	void setSolved(String today, int solved) {
		Day day = days.getOrDefault(today, new Day(0, 0, 0));
		days.put(today, new Day(day.seconds(), day.rounds(), solved));
	}

	private void trimDays() {
		while (days.size() > DAYS_KEPT) days.remove(days.keySet().iterator().next());
	}

	public Map<String, Day> days() {
		return java.util.Collections.unmodifiableMap(days);
	}

	void lesson(String line) {
		lessons.add(line);
		while (lessons.size() > LESSONS_KEPT) lessons.removeFirst();
	}

	public List<String> lessons() {
		return List.copyOf(lessons);
	}

	public Map<String, Item> items() {
		return java.util.Collections.unmodifiableMap(items);
	}
}
