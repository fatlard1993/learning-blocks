package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Which round of a banded course next, as Numbers and Logic choose: the band's main kind most of the
 * time, one of its extras every third round, and now and then an earlier band's item due for
 * review; within a kind, the target least known and not asked today, never the same twice running;
 * and no lesson twice in a day while the band has another.
 */
abstract class CoursePicker<K extends Course.Kind, B extends Course.Band<K>> {
	private static final int EXTRA_EVERY = 3;
	private static final int REVIEW_ONE_IN = 5;

	private final Map<UUID, Integer> rounds = new HashMap<>();
	private final Map<UUID, String> last = new HashMap<>();
	private final Map<UUID, Integer> extra = new HashMap<>();

	protected abstract Course<K, B> course();

	protected abstract @Nullable K kindOf(String id);

	/** Every target a kind has, for the sampler's say-so. */
	protected abstract List<String> targetsOf(K kind);

	/** What a kind may ask in this band, for this child. */
	protected abstract List<String> candidates(Progress progress, K kind, B band);

	/** Whether a kind can be asked of this child just now (a Toybox to count, a reader to team up with). */
	protected boolean available(ServerPlayer player, Room room, Progress progress, K kind, B band) {
		return true;
	}

	protected abstract @Nullable SubjectRound build(Room room, ServerPlayer player, Progress progress, K kind, String target, B band, RandomSource random);

	/** The next round for this child; {@code kindId} and {@code target} as the sampler asks, or null for the course's own choice. */
	@Nullable SubjectRound pick(ServerPlayer player, @Nullable String kindId, @Nullable String target, RandomSource random) {
		Room room = Rooms.of(player);
		if (room == null) return null;
		Progress progress = Progress.of(player);
		B band = course().currentOrLast(progress);
		K kind;
		if (kindId != null) {
			kind = kindOf(kindId);
			if (kind == null) return null;
		} else {
			int count = rounds.merge(player.getUUID(), 1, Integer::sum);
			B review = review(progress, band, random);
			if (review != null) {
				band = review;
				kind = review.main();
			} else if (count % EXTRA_EVERY == 0) {
				kind = nextExtra(player, room, progress, band);
			} else {
				kind = band.main();
			}
			kind = freshKind(player, room, progress, kind, band);
		}
		if (target != null && !targetsOf(kind).contains(target)) return null;
		String chosen = target != null ? target : pickTarget(player, progress, kind, band, random);
		if (chosen == null) {
			if (kindId != null) return null;
			kind = band.main();
			chosen = pickTarget(player, progress, kind, band, random);
			if (chosen == null) return null;
		}
		SubjectRound made = build(room, player, progress, kind, chosen, band, random);
		if (made == null) return null;
		last.put(player.getUUID(), made.item());
		if (!room.practice()) progress.useItemToday(player, made.item(), kind.id());
		return made;
	}

	private @Nullable B review(Progress progress, B band, RandomSource random) {
		List<B> earlier = course().before(band);
		if (earlier.isEmpty() || random.nextInt(REVIEW_ONE_IN) != 0) return null;
		List<B> due = earlier.stream().filter(b -> b.gate().stream().anyMatch(progress::itemDue)).toList();
		return due.isEmpty() ? null : due.get(random.nextInt(due.size()));
	}

	/** The band's extras in turn, skipping any that can't be asked of this child just now. */
	private K nextExtra(ServerPlayer player, Room room, Progress progress, B band) {
		List<K> extras = band.extras();
		int start = extra.merge(player.getUUID(), 1, Integer::sum);
		for (int i = 0; i < extras.size(); i++) {
			K kind = extras.get((start + i) % extras.size());
			if (available(player, room, progress, kind, band)) return kind;
		}
		return band.main();
	}

	/** A kind whose every target was asked today gives way to another of the band's that still has one. */
	private K freshKind(ServerPlayer player, Room room, Progress progress, K kind, B band) {
		if (!allUsed(progress, kind, band)) return kind;
		List<K> others = new ArrayList<>();
		others.add(band.main());
		others.addAll(band.extras());
		for (K other : others) {
			if (other != kind && available(player, room, progress, other, band) && !allUsed(progress, other, band)) return other;
		}
		return kind;
	}

	private boolean allUsed(Progress progress, K kind, B band) {
		return candidates(progress, kind, band).stream().allMatch(t -> progress.itemUsedToday(kind.item(t)));
	}

	private @Nullable String pickTarget(ServerPlayer player, Progress progress, K kind, B band, RandomSource random) {
		List<String> options = new ArrayList<>(candidates(progress, kind, band));
		if (options.isEmpty()) return null;
		String before = last.get(player.getUUID());
		if (options.size() > 1) options.removeIf(t -> kind.item(t).equals(before));
		java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
		options.sort(Comparator.<String>comparingInt(t -> progress.itemUsedToday(kind.item(t)) ? 1 : 0)
			.thenComparingInt(t -> progress.itemClean(kind.item(t))));
		return options.getFirst();
	}
}
