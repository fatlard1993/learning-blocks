package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Every subject, and how a child's rounds are shared among theirs.
 *
 * <p>Which are on: the server's defaults ({@code subjects} in prizes.json, all on), or a child's
 * own where a grown-up set it ({@code /learn subjects}). How they mix: {@code alternate} (one round
 * of each in turn, skipping any with nothing to ask), {@code blocks} (a few of one, then the next),
 * or {@code weights} (each by chance, as weighted). A daily schedule asking for some of each comes
 * first: while any of a child's subjects still has rounds owed today, only those are picked.
 */
public final class Subjects {
	private Subjects() {}

	public static final Subject READING = Reading.INSTANCE;
	public static final List<Subject> ALL = List.of(Reading.INSTANCE, Numbers.INSTANCE, Logic.INSTANCE);

	public enum Mix {
		ALTERNATE, BLOCKS, WEIGHTS;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Mix of(String name) {
			for (Mix mix : values()) if (mix.id().equals(name)) return mix;
			return ALTERNATE;
		}
	}

	public static @Nullable Subject of(String id) {
		for (Subject subject : ALL) if (subject.id().equals(id)) return subject;
		return null;
	}

	public static List<String> ids() {
		return ALL.stream().map(Subject::id).toList();
	}

	private static volatile @Nullable Set<String> onlyForTests;

	/** For tests: only these subjects, whatever the defaults and the child's own say; null to stop. */
	public static void onlyForTests(@Nullable Set<String> ids) {
		onlyForTests = ids;
	}

	/** Whether this subject is on for this child. */
	public static boolean on(ServerPlayer player, Subject subject) {
		Set<String> only = onlyForTests;
		if (only != null) return only.contains(subject.id());
		Boolean own = Progress.of(player).subjectOn(subject.id());
		return own != null ? own : PrizeConfig.subjectOn(subject.id());
	}

	/** This child's subjects that are on. */
	public static List<Subject> on(ServerPlayer player) {
		return ALL.stream().filter(subject -> on(player, subject)).toList();
	}

	/** Rounds of each subject a daily schedule still asks of this child today; empty where it asks for any subject. */
	public static Map<String, Integer> stillNeeded(ServerPlayer player) {
		Map<String, Integer> asks = Schedules.perSubjectToday(player);
		Map<String, Integer> left = new LinkedHashMap<>();
		Progress progress = Progress.of(player);
		for (Map.Entry<String, Integer> ask : asks.entrySet()) {
			int owed = ask.getValue() - progress.doneToday(ask.getKey());
			if (owed > 0) left.put(ask.getKey(), owed);
		}
		return left;
	}

	/**
	 * The subject of this child's next round: of those on and with something to ask (and owed today,
	 * where the schedule says), by the mix, after {@code last} which has had {@code run} rounds in a
	 * row. Reading if nothing else.
	 */
	public static Subject pick(ServerPlayer player, @Nullable String last, int run, RandomSource random) {
		List<Subject> candidates = new ArrayList<>();
		for (Subject subject : on(player)) if (subject.canAsk(player)) candidates.add(subject);
		if (candidates.isEmpty()) return READING;
		Map<String, Integer> owed = stillNeeded(player);
		if (!owed.isEmpty()) {
			List<Subject> owing = candidates.stream().filter(subject -> owed.containsKey(subject.id())).toList();
			if (!owing.isEmpty()) candidates = owing;
		}
		if (candidates.size() == 1) return candidates.getFirst();
		Subject before = last == null ? null : of(last);
		return switch (PrizeConfig.subjectMix()) {
			case BLOCKS -> before != null && candidates.contains(before) && run < PrizeConfig.subjectBlock() ? before : after(candidates, before);
			case WEIGHTS -> weighted(candidates, random);
			case ALTERNATE -> after(candidates, before);
		};
	}

	/** The next of these after this one, in the subjects' order, round again; the first if none. */
	private static Subject after(List<Subject> candidates, @Nullable Subject before) {
		if (before == null) return candidates.getFirst();
		int from = ALL.indexOf(before);
		for (int step = 1; step <= ALL.size(); step++) {
			Subject next = ALL.get((from + step) % ALL.size());
			if (candidates.contains(next)) return next;
		}
		return candidates.getFirst();
	}

	private static Subject weighted(List<Subject> candidates, RandomSource random) {
		int total = 0;
		for (Subject subject : candidates) total += Math.max(0, PrizeConfig.subjectWeight(subject.id()));
		if (total <= 0) return candidates.get(random.nextInt(candidates.size()));
		int roll = random.nextInt(total);
		for (Subject subject : candidates) {
			roll -= Math.max(0, PrizeConfig.subjectWeight(subject.id()));
			if (roll < 0) return subject;
		}
		return candidates.getLast();
	}

	/** Whether this child has mastered an item of any subject: {@code numbers:numeral:5}, {@code reading:word:cat}. */
	public static boolean mastered(Progress progress, String item) {
		int colon = item.indexOf(':');
		Subject subject = colon < 0 ? null : of(item.substring(0, colon));
		return subject != null && subject.mastered(progress, item);
	}
}
