package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.learning_blocks.narration.Narrator;
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
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Every kind of Logic round, end to end, in the classroom: made, shot on a full run
 * ({@code learning-logic-<kind>-<target>.png}), answered wrong first (told, the round still up) and
 * then right, counted for the day. Sorting by kind, colour, size and things (click one, then its
 * pen); same or different; what comes next (AB, AAB, ABB, ABC, growing); find the mistake; copy it;
 * a pattern of notes; which doesn't belong (it waves bye and goes); what's missing (after the
 * blink); first, next, last (seed, egg, and day, the sky playing it after); little to big; the
 * mixed-up morning; and Bob: all the pigs in the pen, the big cat in the box, the cat left of the
 * box, and the one in the wrong pen.
 */
public final class LogicTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("LogicTest failed", failed);
				throw failed;
			}
		});
	}

	private static int rounds;

	private static void run(Smoke.Session session) {
		rounds = 0;
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> Subjects.onlyForTests(Set.of("reading")));

		for (String by : List.of("kind", "colour", "size", "things")) sort(session, by);
		for (String which : List.of("same", "different")) simple(session, "same", which, SameRound.class);
		for (String type : List.of("ab", "aab", "abb", "abc", "grow")) simple(session, "next", type, PatternRound.class);
		simple(session, "mistake", "ab", PatternRound.class);
		copy(session);
		simple(session, "notes", "ab", NotesRound.class);
		simple(session, "notes", "abb", NotesRound.class);
		for (String by : List.of("kind", "colour", "category")) {
			OddRound round = (OddRound) simple(session, "odd", by, OddRound.class);
			session.onServer(server -> check(!round.shown().get(round.odd()).isAlive(), "the odd one out did not go"));
		}
		for (String n : List.of("3", "4")) missing(session, n);
		for (String story : List.of("seed", "egg", "day")) sequence(session, "story", story);
		check(session, "sky:night", "the day story's sky never played night");
		for (String n : List.of("3", "4")) sequence(session, "size", n);
		sequence(session, "morning", "morning");
		check(session, "clip:scene/mixed_up_morning", "the morning scene was not told");

		LogicBobRound sortBob = (LogicBobRound) simple(session, "bobsort", "pigs", LogicBobRound.class);
		session.onServer(server -> check(sortBob.did().equals("pigs") && sortBob.inPen() == 2, "Bob did not put the two pigs in the pen: " + sortBob.inPen()));
		LogicBobRound bigBob = (LogicBobRound) simple(session, "bobbig", "big", LogicBobRound.class);
		session.onServer(server -> check(bigBob.cat().position().distanceTo(bigBob.boxFoot()) < 1.2, "the big cat is not in the box"));
		LogicBobRound leftBob = (LogicBobRound) simple(session, "bobleft", "left", LogicBobRound.class);
		session.onServer(server -> check(leftBob.cat().getX() < leftBob.boxFoot().x - 0.5, "the cat is not on the child's left of the box"));
		simple(session, "bobfix", "pens", LogicBobRound.class);
		check(session, "clip:phrase/bob_sorted", "Bob never said all sorted");

		session.onServer(server -> check(Progress.of(session.player()).doneToday("logic") == rounds,
			"the logic rounds were not all counted: " + Progress.of(session.player()).doneToday("logic") + " of " + rounds));
		session.command("/learn leave");
		Waits.home(session);
	}

	private static SubjectRound start(Smoke.Session session, String kind, String target, Class<?> type) {
		SubjectRound[] made = new SubjectRound[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Narrator.note(player, "mark");
			Rooms.of(player).newSubjectRound(player, Logic.INSTANCE, kind, target);
			made[0] = Rooms.of(player).subjectRound();
			check(type.isInstance(made[0]), kind + " " + target + " made " + made[0]);
			check(made[0].item().startsWith("logic:"), kind + "'s item is " + made[0].item());
		});
		Waits.until(session, 600, kind + " never settled", () -> !Rooms.of(session.player()).busy());
		session.waitTicks(50);
		shoot(session, "learning-logic-" + kind + "-" + target);
		rounds++;
		return made[0];
	}

	/** A one-pick kind: a wrong pick first, then the right one. */
	private static SubjectRound simple(Smoke.Session session, String kind, String target, Class<?> type) {
		SubjectRound round = start(session, kind, target, type);
		wrongFirst(session, round, kind);
		pick(session, round, round.rightForTests());
		waitGone(session, round);
		return round;
	}

	private static void wrongFirst(Smoke.Session session, SubjectRound round, String kind) {
		session.onServer(server -> {
			SubjectRound.Choice other = round.choices().stream().filter(c -> !c.equals(round.rightForTests())).findFirst().orElseThrow();
			round.pickForTests(session.player(), other);
		});
		Waits.until(session, 900, kind + "'s wrong answer was never told", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> check(Rooms.of(session.player()).subjectRound() == round, kind + ": a wrong answer ended the round"));
	}

	private static void pick(Smoke.Session session, SubjectRound round, SubjectRound.Choice choice) {
		session.onServer(server -> round.pickForTests(session.player(), choice));
	}

	/** Sorting: one into the wrong pen first, then every one into its own. */
	private static void sort(Smoke.Session session, String by) {
		SortRound round = (SortRound) start(session, "sort", by, SortRound.class);
		session.onServer(server -> {
			SubjectRound.Choice first = round.unsorted().getFirst();
			round.pickForTests(session.player(), first);
		});
		settle(session);
		session.onServer(server -> round.pickForTests(session.player(), round.wrongPenFor(round.unsorted().getFirst())));
		settle(session);
		session.onServer(server -> check(round.sorted() == 0, "the wrong pen took it"));
		while (true) {
			boolean[] left = new boolean[1];
			session.onServer(server -> left[0] = Rooms.of(session.player()).subjectRound() == round && !round.unsorted().isEmpty());
			if (!left[0]) break;
			session.onServer(server -> round.pickForTests(session.player(), round.unsorted().getFirst()));
			settle(session);
			session.onServer(server -> {
				if (!round.unsorted().isEmpty()) round.pickForTests(session.player(), round.penFor(round.unsorted().getFirst()));
			});
			settleOrGone(session, round);
		}
		waitGone(session, round);
	}

	/** Copy it: one not next first, then the four in order. */
	private static void copy(Smoke.Session session) {
		PatternRound round = (PatternRound) start(session, "copy", "ab", PatternRound.class);
		wrongFirst(session, round, "copy");
		for (int i = 0; i < 4; i++) {
			int at = i;
			pick(session, round, round.rightForTests());
			Waits.until(session, 600, "copy " + at + " never went down", () -> round.copied() == at + 1 && !Rooms.of(session.player()).busy()
				|| Rooms.of(session.player()).subjectRound() != round);
		}
		waitGone(session, round);
	}

	private static void missing(Smoke.Session session, String n) {
		MissingRound round = (MissingRound) start(session, "missing", n, MissingRound.class);
		Waits.until(session, 300, "nothing went missing", round::hidden);
		wrongFirst(session, round, "missing");
		pick(session, round, round.rightForTests());
		waitGone(session, round);
	}

	/** Putting in order: the last one first (out of turn), then each in order. */
	private static void sequence(Smoke.Session session, String kind, String target) {
		SequenceRound round = (SequenceRound) start(session, kind, target, SequenceRound.class);
		session.onServer(server -> {
			SubjectRound.Choice last = round.choices().stream().filter(c -> !c.equals(round.next())).findFirst().orElseThrow();
			round.pickForTests(session.player(), last);
		});
		settle(session);
		session.onServer(server -> check(round.done() == 0, "a card out of turn went up"));
		int cards = round.choices().size();
		for (int i = 0; i < cards; i++) {
			int at = i;
			pick(session, round, round.next());
			Waits.until(session, 900, kind + " card " + at + " never went up", () -> round.done() == at + 1 && !Rooms.of(session.player()).busy()
				|| Rooms.of(session.player()).subjectRound() != round);
		}
		waitGone(session, round);
	}

	private static void settle(Smoke.Session session) {
		Waits.until(session, 600, "the room never settled", () -> !Rooms.of(session.player()).busy());
	}

	private static void settleOrGone(Smoke.Session session, SubjectRound round) {
		Waits.until(session, 900, "the room never settled", () -> !Rooms.of(session.player()).busy() || Rooms.of(session.player()).subjectRound() != round);
	}

	private static void waitGone(Smoke.Session session, SubjectRound round) {
		Waits.until(session, 1500, round.kind() + " never finished", () -> Rooms.of(session.player()).subjectRound() != round && !Rooms.of(session.player()).busy());
	}

	private static void check(Smoke.Session session, String entry, String failure) {
		session.onServer(server -> {
			List<String> log = Narrator.log(session.player());
			check(log.contains(entry), failure + ": " + log.subList(Math.max(0, log.size() - 40), log.size()));
		});
	}

	private static void shoot(Smoke.Session session, String name) {
		if (!session.full()) return;
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 from = Rooms.of(player).spawn();
			player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, Set.of(), Room.FACING_YAW, 14, true);
		});
		session.waitTicks(5);
		session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
