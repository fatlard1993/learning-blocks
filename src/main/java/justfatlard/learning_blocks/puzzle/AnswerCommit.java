package justfatlard.learning_blocks.puzzle;

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

	/** Lessons left before this answer. */
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

	/** Whether this clean completion is the one that masters the word. */
	static boolean masters(ServerPlayer player, Word word, boolean firstTry) {
		Progress progress = Progress.of(player);
		return firstTry && progress.cleanCountsToday(word) && progress.clean(word) + 1 == Progress.masteredAt();
	}

	/** A right answer: the round's tally, progress and prizes, and a lesson off. */
	void right(ServerPlayer player, String what, Runnable credit, int lessonsBefore) {
		// A practice round: nothing counted, paid or taken off; only the telling.
		if (room.practice) {
			Narrator.note(player, "practice:no_credit");
			return;
		}
		room.solved++;
		Safely.run("Crediting " + player.getGameProfile().name() + " for " + what, credit);
		Safely.run("Counting today's lessons", () -> Progress.of(player).solvedToday(player, room.currentSubject));
		if (lessonsBefore > 0) {
			Safely.run("Counting " + player.getGameProfile().name() + "'s lesson", () -> {
				Visit visit = Visit.of(player);
				if (visit != null) Visit.set(player, visit.withLessons(lessonsBefore - 1));
				// The door waits for "All done!" to open; until then it shows the count.
				if (lessonsBefore - 1 > 0) room.refreshDoor(player, false);
				else Progress.of(player).lessonNote(player, "lessons all done");
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
