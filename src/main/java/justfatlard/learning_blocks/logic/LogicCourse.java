package justfatlard.learning_blocks.logic;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.reward.Progress;
import org.jspecify.annotations.Nullable;

/**
 * The Logic course, as far as it goes: four stages, each in two bands, as Numbers' are. A band is
 * finished when its gate items are mastered; a stage when both its bands are.
 *
 * <ul>
 *   <li>L0, matching and sorting: animals into their pens (by kind, by colour, by size), toys and
 *   plushies into the box and the crib; same or different.</li>
 *   <li>L1, patterns: what comes next (AB, AAB, ABB, ABC, growing), find the mistake, copy it, a
 *   pattern of notes.</li>
 *   <li>L2, odd one out and what's missing.</li>
 *   <li>L3, sequencing: first, next, last (a seed, an egg, a day), little to big, a mixed-up
 *   morning.</li>
 * </ul>
 * Bob joins in throughout: put all the pigs in the pen, the big cat in the box, the cat left of the
 * box, and which one he put in the wrong pen.
 *
 * <p>Items are {@code logic:<kind>:<target>}: {@code logic:sort:kind}, {@code logic:next:abc}.
 */
public final class LogicCourse {
	private LogicCourse() {}

	public enum Kind {
		SORT("sort"), SAME("same"), NEXT("next"), MISTAKE("mistake"), COPY("copy"), NOTES("notes"), ODD("odd"), MISSING("missing"),
		STORY("story"), SIZE("size"), MORNING("morning"), BOBSORT("bobsort"), BOBBIG("bobbig"), BOBLEFT("bobleft"), BOBFIX("bobfix");

		public final String id;

		Kind(String id) {
			this.id = id;
		}

		public String item(String target) {
			return "logic:" + id + ":" + target;
		}

		public static @Nullable Kind of(String id) {
			for (Kind kind : values()) if (kind.id.equals(id)) return kind;
			return null;
		}
	}

	public record Band(String code, int stage, List<String> gate, Kind main, List<String> mainTargets, List<Kind> extras) {}

	private static List<String> items(Kind kind, String... targets) {
		List<String> out = new ArrayList<>();
		for (String target : targets) out.add(kind.item(target));
		return out;
	}

	private static List<String> join(List<String> a, List<String> b) {
		List<String> out = new ArrayList<>(a);
		out.addAll(b);
		return List.copyOf(out);
	}

	public static final List<Band> BANDS = List.of(
		new Band("L0a", 0, join(items(Kind.SORT, "kind"), items(Kind.SAME, "same", "different")), Kind.SORT, List.of("kind"), List.of(Kind.SAME)),
		new Band("L0b", 0, items(Kind.SORT, "colour", "size", "things"), Kind.SORT, List.of("colour", "size", "things"), List.of(Kind.SAME, Kind.BOBSORT)),
		new Band("L1a", 1, items(Kind.NEXT, "ab", "aab", "abb"), Kind.NEXT, List.of("ab", "aab", "abb"), List.of(Kind.COPY, Kind.NOTES)),
		new Band("L1b", 1, join(items(Kind.NEXT, "abc", "grow"), join(items(Kind.MISTAKE, "ab"), items(Kind.COPY, "ab"))), Kind.NEXT, List.of("abc", "grow"),
			List.of(Kind.MISTAKE, Kind.COPY, Kind.NOTES, Kind.BOBFIX)),
		new Band("L2a", 2, items(Kind.ODD, "kind", "colour"), Kind.ODD, List.of("kind", "colour"), List.of(Kind.MISSING, Kind.BOBBIG)),
		new Band("L2b", 2, join(items(Kind.ODD, "category"), items(Kind.MISSING, "3", "4")), Kind.ODD, List.of("category"), List.of(Kind.MISSING, Kind.BOBLEFT)),
		new Band("L3a", 3, join(items(Kind.STORY, "seed", "egg"), items(Kind.SIZE, "3")), Kind.STORY, List.of("seed", "egg"), List.of(Kind.SIZE)),
		new Band("L3b", 3, join(items(Kind.STORY, "day"), join(items(Kind.SIZE, "4"), items(Kind.MORNING, "morning"))), Kind.STORY, List.of("day"),
			List.of(Kind.SIZE, Kind.MORNING))
	);

	public static List<String> targets(Kind kind) {
		return switch (kind) {
			case SORT -> List.of("kind", "colour", "size", "things");
			case SAME -> List.of("same", "different");
			case NEXT -> List.of("ab", "aab", "abb", "abc", "grow");
			case MISTAKE, COPY -> List.of("ab", "aab", "abb", "abc");
			case NOTES -> List.of("ab", "abb");
			case ODD -> List.of("kind", "colour", "category");
			case MISSING -> List.of("3", "4");
			case STORY -> List.of("seed", "egg", "day");
			case SIZE -> List.of("3", "4");
			case MORNING -> List.of("morning");
			case BOBSORT -> List.of("pigs", "cows", "sheep");
			case BOBBIG -> List.of("big", "little");
			case BOBLEFT -> List.of("left", "right");
			case BOBFIX -> List.of("pens");
		};
	}

	public static @Nullable Band current(Progress progress) {
		for (Band band : BANDS) if (!finished(progress, band)) return band;
		return null;
	}

	public static boolean finished(Progress progress, Band band) {
		for (String item : band.gate()) if (!progress.itemMastered(item)) return false;
		return true;
	}

	public static boolean stageDone(Progress progress, int stage) {
		for (Band band : BANDS) if (band.stage() == stage && !finished(progress, band)) return false;
		return true;
	}

	public static @Nullable Band band(String code) {
		for (Band band : BANDS) if (band.code().equalsIgnoreCase(code)) return band;
		return null;
	}

	public static List<Band> before(Band band) {
		return BANDS.subList(0, BANDS.indexOf(band));
	}

	public static String describe(Progress progress) {
		Band band = current(progress);
		if (band == null) return "all of L0-L3 done";
		return band.code() + ", " + switch (band.stage()) {
			case 0 -> "matching and sorting";
			case 1 -> "patterns";
			case 2 -> "odd one out and what's missing";
			default -> "sequencing";
		};
	}

	public static List<String> gateItems() {
		List<String> all = new ArrayList<>();
		for (Band band : BANDS) all.addAll(band.gate());
		return all;
	}

	/** For tests and /learn stage: every band before this one mastered, it and after not. */
	public static void putAt(net.minecraft.server.level.ServerPlayer player, Band at) {
		Progress progress = Progress.of(player);
		boolean before = true;
		for (Band band : BANDS) {
			if (band == at) before = false;
			for (String item : band.gate()) progress.setItemCleanForTests(player, item, before ? Progress.masteredAt() : 0);
		}
	}
}
