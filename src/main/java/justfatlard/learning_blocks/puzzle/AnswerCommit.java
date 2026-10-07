package justfatlard.learning_blocks.puzzle;

import java.util.UUID;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.reward.Prizes;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * An answer counted the moment the button is pressed, before anything is shown: progress, prizes and
 * a lesson off for a right one, a miss for a wrong one. Each part on its own, so one going wrong (a
 * loot table that throws) never costs the others; what the room then shows is only the telling.
 */
final class AnswerCommit {
	private final Room room;

	AnswerCommit(Room room) {
		this.room = room;
	}

	/** The same word: the same tiles, whatever file or pack it came from. */
	static boolean sameWord(@Nullable Word a, @Nullable Word b) {
		return a != null && b != null && a.tiles().equals(b.tiles());
	}

	static int lessonsBefore(ServerPlayer player) {
		// A practice round takes no lesson off.
		Room room = Rooms.of(player);
		if (room != null && room.practice) return 0;
		return Travel.lessons(player);
	}

	/**
	 * Mastering the last unmastered word of the band: the next round is a new band, which says so
	 * itself, so "Here comes another one!" gives way to "You're ready for new words!".
	 */
	static boolean lastOfStage(ServerPlayer player, @Nullable Word word, Word target, int stage, boolean firstTry) {
		// The letters level has no stage of words to finish: its word rounds are extras.
		if (word == null || !sameWord(word, target) || stage <= 0 || !firstTry) return false;
		Progress progress = Progress.of(player);
		if (progress.mastered(word) || !progress.cleanCountsToday(word) || progress.clean(word) + 1 < Progress.masteredAt()) return false;
		Stages.Band band = Stages.currentBand(progress);
		if (band == null || !band.contains(word)) return false;
		return band.words().stream().allMatch(w -> sameWord(w, word) || progress.mastered(w));
	}

	static boolean masters(ServerPlayer player, Word word, boolean firstTry) {
		Progress progress = Progress.of(player);
		return firstTry && progress.cleanCountsToday(word) && progress.clean(word) + 1 == Progress.masteredAt();
	}

	void right(ServerPlayer player, String what, Runnable credit, int lessonsBefore) {
		// A practice round: nothing counted, paid or taken off; only the telling.
		if (room.practice) {
			Narrator.note(player, "practice:no_credit");
			return;
		}
		room.solved++;
		room.credited.add(player.getUUID());
		Safely.run("Crediting " + player.getGameProfile().name() + " for " + what, credit);
		// Those who helped - pointed, or did something of it - have the lesson too, though not the mastery.
		room.later(player, 1, p -> helpers());
		Safely.run("Counting today's lessons", () -> Progress.of(player).solvedToday(player, room.currentSubject));
		if (lessonsBefore > 0) {
			Safely.run("Counting " + player.getGameProfile().name() + "'s lesson", () -> {
				Visit visit = Visit.of(player);
				if (visit != null) Visit.set(player, visit.withLessons(lessonsBefore - 1));
				// The portal waits for "All done!" to open; until then it shows the count.
				if (lessonsBefore - 1 > 0) room.refreshPortal(player, false);
				else Progress.of(player).lessonNote(player, "lessons all done");
			});
		}
	}

	/** A lesson for every reader who took part in this round and was not credited for answering it. */
	private void helpers() {
		for (ServerPlayer other : room.present()) {
			UUID id = other.getUUID();
			if (!room.tookPart.contains(id) || room.credited.contains(id)) continue;
			room.credited.add(id);
			int before = lessonsBefore(other);
			Safely.run("Counting a helper's lesson", () -> {
				Progress.of(other).solvedToday(other, room.currentSubject);
				Progress.of(other).lessonNote(other, "helped with a round");
				if (before > 0) {
					Visit visit = Visit.of(other);
					if (visit != null) Visit.set(other, visit.withLessons(before - 1));
					room.refreshPortal(other, before == 1);
				}
			});
		}
	}

	/** A wrong answer: the next right one this round is not first try. */
	void wrong(ServerPlayer player) {
		room.missedThisRound = true;
		if (room.practice) return;
		Safely.run("Counting a miss", () -> Prizes.missed(player));
	}
}
