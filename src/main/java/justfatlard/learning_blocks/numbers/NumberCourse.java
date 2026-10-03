package justfatlard.learning_blocks.numbers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import justfatlard.learning_blocks.reward.Progress;
import org.jspecify.annotations.Nullable;

/**
 * The Numbers course, as far as it goes: three stages, each in two bands, like Reading's. A band
 * is finished when its gate items are all mastered (the same clean-days rule as words); a stage
 * when both its bands are. Each band has its main kind of round, asked most of the time, and its
 * extras, mixed in; earlier bands' items come back now and then for review.
 *
 * <ul>
 *   <li>N0, numbers 0 to 10: which block says it (1-5, then 0 and 6-10); quick looks; a number's
 *   name read in letter tiles.</li>
 *   <li>N1, counting to 10, with real things: how many; make a number with the button; one bed
 *   for every kitten; their own Toybox; scenes; Bob with number cards; what's wrong.</li>
 *   <li>N2, comparing and order: which pen has more; towers; a number line in order; what comes
 *   after and before; the picture with that many.</li>
 * </ul>
 *
 * <p>Items are {@code numbers:<kind>:<target>}: {@code numbers:numeral:5}, {@code numbers:count:7},
 * {@code numbers:compare:more}.
 */
public final class NumberCourse {
	private NumberCourse() {}

	/** The kinds of numbers round: their names in the sampler, and the part of each item's key after numbers:. */
	public enum Kind {
		FIND("find", "numeral"), LOOK("look", "look"), NAME("name", "name"),
		COUNT("count", "count"), MAKE("make", "make"), BEDS("beds", "beds"), TOYBOX("toybox", "toybox"), SCENE("scene", "scene"),
		BOB("bob", "bob"), FIX("fix", "fix"), TEAM("team", "team"),
		PENS("pens", "compare"), TOWERS("towers", "tower"), ORDER("order", "order"), NEXT("next", "next"), PICTURE("picture", "picture"),
		MORE("more", "more");

		public final String id;
		public final String key;

		Kind(String id, String key) {
			this.id = id;
			this.key = key;
		}

		public String item(String target) {
			return "numbers:" + key + ":" + target;
		}

		public static @Nullable Kind of(String id) {
			for (Kind kind : values()) if (kind.id.equals(id)) return kind;
			return null;
		}
	}

	/** A band: its code (N0a), its stage (0), the items that finish it, its main kind and targets, and its extras. */
	public record Band(String code, int stage, List<String> gate, Kind main, List<String> mainTargets, List<Kind> extras) {}

	private static List<String> range(int from, int to) {
		List<String> out = new ArrayList<>();
		for (int i = from; i <= to; i++) out.add(String.valueOf(i));
		return out;
	}

	private static List<String> items(Kind kind, List<String> targets) {
		return targets.stream().map(kind::item).toList();
	}

	private static List<String> join(List<String> a, List<String> b) {
		List<String> out = new ArrayList<>(a);
		out.addAll(b);
		return List.copyOf(out);
	}

	public static final List<Band> BANDS = List.of(
		new Band("N0a", 0, items(Kind.FIND, range(1, 5)), Kind.FIND, range(1, 5), List.of(Kind.LOOK)),
		new Band("N0b", 0, items(Kind.FIND, join(List.of("0"), range(6, 10))), Kind.FIND, join(List.of("0"), range(6, 10)), List.of(Kind.LOOK, Kind.NAME)),
		new Band("N1a", 1, items(Kind.COUNT, range(1, 5)), Kind.COUNT, range(1, 5), List.of(Kind.MAKE, Kind.BEDS, Kind.TOYBOX, Kind.SCENE, Kind.BOB)),
		new Band("N1b", 1, items(Kind.COUNT, range(6, 10)), Kind.COUNT, range(6, 10), List.of(Kind.MAKE, Kind.FIX, Kind.TOYBOX, Kind.SCENE, Kind.BOB, Kind.TEAM)),
		new Band("N2a", 2, join(items(Kind.PENS, List.of("more", "fewer", "same")), items(Kind.TOWERS, List.of("taller", "shorter"))),
			Kind.PENS, List.of("more", "fewer", "same"), List.of(Kind.TOWERS, Kind.ORDER, Kind.PICTURE)),
		new Band("N2b", 2, join(items(Kind.TOWERS, List.of("diff")), join(items(Kind.ORDER, List.of("5", "10")), List.of(Kind.NEXT.item("after"), Kind.NEXT.item("before")))),
			Kind.TOWERS, List.of("diff"), List.of(Kind.ORDER, Kind.NEXT, Kind.PICTURE, Kind.MORE))
	);

	/** What each kind can ask, for the picker and the sampler. */
	public static List<String> targets(Kind kind) {
		return switch (kind) {
			case FIND -> range(0, 10);
			case LOOK -> range(1, 10);
			case NAME -> List.of("6", "10");
			case COUNT, MAKE, PICTURE -> range(1, 10);
			case BEDS -> range(1, 4);
			case TOYBOX -> List.of("toys", "plushies");
			case SCENE -> List.of("pigs", "cakes");
			case BOB, FIX -> range(1, 6);
			case TEAM -> range(2, 10);
			case PENS -> List.of("more", "fewer", "same");
			case TOWERS -> List.of("taller", "shorter", "diff");
			case ORDER -> List.of("5", "10");
			case NEXT -> List.of("after", "before");
			case MORE -> List.of("apples");
		};
	}

	/** The band a child is in: the first not finished; null once all are. */
	public static @Nullable Band current(Progress progress) {
		for (Band band : BANDS) if (!finished(progress, band)) return band;
		return null;
	}

	public static boolean finished(Progress progress, Band band) {
		for (String item : band.gate()) if (!progress.itemMastered(item)) return false;
		return true;
	}

	/** Whether every band of this stage is finished. */
	public static boolean stageDone(Progress progress, int stage) {
		for (Band band : BANDS) if (band.stage() == stage && !finished(progress, band)) return false;
		return true;
	}

	public static @Nullable Band band(String code) {
		for (Band band : BANDS) if (band.code().equalsIgnoreCase(code)) return band;
		return null;
	}

	/** The bands before this one. */
	public static List<Band> before(Band band) {
		return BANDS.subList(0, BANDS.indexOf(band));
	}

	/** "N1b, counting to 10": where a child is, for people. */
	public static String describe(Progress progress) {
		Band band = current(progress);
		if (band == null) return "all of N0-N2 done";
		return band.code() + ", " + switch (band.stage()) {
			case 0 -> "numbers 0 to 10";
			case 1 -> "counting to 10";
			default -> "comparing and order";
		};
	}

	/** Every gate item, in order. */
	public static List<String> gateItems() {
		List<String> all = new ArrayList<>();
		for (Band band : BANDS) all.addAll(band.gate());
		return all;
	}

	/** For tests and /learn stage: put a child at this band, every band before it mastered and it and after not. */
	public static void putAt(net.minecraft.server.level.ServerPlayer player, Band at) {
		Progress progress = Progress.of(player);
		boolean before = true;
		for (Band band : BANDS) {
			if (band == at) before = false;
			for (String item : band.gate()) progress.setItemCleanForTests(player, item, before ? Progress.masteredAt() : 0);
		}
	}

	public static String code(Band band) {
		return band.code().toLowerCase(Locale.ROOT);
	}
}
