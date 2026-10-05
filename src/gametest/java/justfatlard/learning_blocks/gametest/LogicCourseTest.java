package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.puzzle.LogicRound;
import justfatlard.learning_blocks.puzzle.OddRound;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Sampler;
import justfatlard.learning_blocks.puzzle.SortRound;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Earned;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

/**
 * The Logic course: a new child at L0a (sorting by kind mostly, same or different every third
 * round); the gates to L0b and on, the stage done; /learn stage L2b and what it asks; the parent
 * view's Logic lines; the puzzle pieces earned by the last sort mastered; and the sampler's odd one
 * out, practice only.
 */
public final class LogicCourseTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("LogicCourseTest failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Subjects.onlyForTests(Set.of("reading"));
			Progress progress = Progress.of(player);
			var random = player.getRandom();
			check(LogicCourse.current(progress).code().equals("L0a"), "a new child is not at L0a: " + LogicCourse.describe(progress));
			List<String> kinds = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				SubjectRound round = Logic.INSTANCE.round(player, null, null, random);
				check(round != null, "no logic round for a new child");
				kinds.add(round.kind() + ":" + ((LogicRound) round).target());
			}
			check(kinds.stream().filter(k -> k.startsWith("sort:")).allMatch(k -> k.equals("sort:kind")), "L0a sorted by something other than kind: " + kinds);
			check(kinds.stream().anyMatch(k -> k.startsWith("same:")), "no same-or-different in six L0a rounds: " + kinds);

			for (String item : LogicCourse.band("L0a").gate()) progress.setItemCleanForTests(player, item, Progress.masteredAt());
			check(LogicCourse.current(progress).code().equals("L0b"), "L0a mastered is not L0b");
			for (String item : LogicCourse.band("L0b").gate()) progress.setItemCleanForTests(player, item, Progress.masteredAt());
			check(LogicCourse.stageDone(progress, 0) && LogicCourse.current(progress).code().equals("L1a"), "L0 mastered is not L1a");

			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn stage " + player.getGameProfile().name() + " L2b");
			check(LogicCourse.current(progress).code().equals("L2b"), "/learn stage L2b did not put them there: " + LogicCourse.describe(progress));
			SubjectRound odd = Logic.INSTANCE.round(player, "odd", null, random);
			check(odd != null && odd.item().equals("logic:odd:category"), "L2b's odd one out is not by category: " + (odd == null ? null : odd.item()));

			check(Logic.INSTANCE.summary(progress).getFirst().startsWith("Logic: L2b"), "the summary: " + Logic.INSTANCE.summary(progress));
			justfatlard.learning_blocks.review.ProgressView.open(player, player);
			check(justfatlard.learning_blocks.review.ProgressView.lastShown(player).stream().anyMatch(line -> line.startsWith("Logic: L2b")),
				"the parent view has no Logic line: " + justfatlard.learning_blocks.review.ProgressView.lastShown(player));

			// The puzzle pieces: every sort mastered but things, one short.
			for (String by : List.of("kind", "colour", "size", "things")) {
				progress.setItemCleanForTests(player, LogicCourse.Kind.SORT.item(by), by.equals("things") ? Progress.masteredAt() - 1 : Progress.masteredAt());
			}
			check(!Earned.earned(progress, "toy:puzzle"), "the puzzle pieces were earned already");
			Rooms.of(player).newSubjectRound(player, Logic.INSTANCE, "sort", "things");
		});
		Waits.until(session, 600, "the sort never settled", () -> !Rooms.of(session.player()).busy());
		session.waitTicks(40);
		SortRound[] sort = new SortRound[1];
		session.onServer(server -> sort[0] = (SortRound) Rooms.of(session.player()).subjectRound());
		while (true) {
			boolean[] more = new boolean[1];
			session.onServer(server -> more[0] = Rooms.of(session.player()).subjectRound() == sort[0] && !sort[0].unsorted().isEmpty());
			if (!more[0]) break;
			session.onServer(server -> sort[0].pickForTests(session.player(), sort[0].unsorted().getFirst()));
			Waits.until(session, 600, "never settled", () -> !Rooms.of(session.player()).busy());
			session.onServer(server -> {
				if (!sort[0].unsorted().isEmpty()) sort[0].pickForTests(session.player(), sort[0].penFor(sort[0].unsorted().getFirst()));
			});
			Waits.until(session, 900, "never settled", () -> !Rooms.of(session.player()).busy() || Rooms.of(session.player()).subjectRound() != sort[0]);
		}
		session.onServer(server -> check(Earned.earned(Progress.of(session.player()), "toy:puzzle"), "the last sort mastered did not earn the puzzle pieces"));
		Waits.until(session, 1500, "the sort never finished", () -> Rooms.of(session.player()).subjectRound() != sort[0] && !Rooms.of(session.player()).busy());

		// The sampler: an odd one out by colour, as practice.
		Tag[] before = new Tag[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			before[0] = Progress.savedForTests(player);
			check(Sampler.runSubject(player, Logic.INSTANCE, "odd", "smell") != null, "an odd one out it hasn't got was not refused");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), "learn try logic odd colour");
			check(Rooms.of(player).practice() && Rooms.of(player).subjectRound() instanceof OddRound, "/learn try logic odd colour made no odd one out");
		});
		Waits.until(session, 600, "the sample never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			SubjectRound round = Rooms.of(session.player()).subjectRound();
			round.pickForTests(session.player(), round.rightForTests());
			check(Progress.savedForTests(session.player()).equals(before[0]), "a logic sample changed the saved progress");
		});
		Waits.until(session, 900, "the sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());
		session.command("/learn leave");
		Waits.home(session);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
