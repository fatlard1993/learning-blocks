package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.puzzle.LookRound;
import justfatlard.learning_blocks.puzzle.MakeRound;
import justfatlard.learning_blocks.puzzle.NumberBobRound;
import justfatlard.learning_blocks.puzzle.PensRound;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Sampler;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Earned;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

/**
 * The Numbers course: the bands and their gates (N0a's numerals mastered, on to N0b; all of N0,
 * the stage done), the picker (a band's main kind, an extra every third round, targets in the
 * band), /learn stage for numbers; make a number for every number 1 to 10, exactly that many out
 * of the button; Bob for 1 to 6, exactly that many put; a mix-up (6 for 9) in the parent view's
 * Numbers lines; the dice earned by the last quick look mastered; and the sampler's pens, practice
 * only.
 */
public final class NumbersCourseTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("NumbersCourseTest failed", failed);
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

			// A new child: N0a, which block says it, for 1 to 5; every third round its extra, a quick look.
			check(NumberCourse.current(progress).code().equals("N0a"), "a new child is not at N0a: " + NumberCourse.describe(progress));
			List<String> kinds = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				SubjectRound round = Numbers.INSTANCE.round(player, null, null, random);
				check(round != null, "no numbers round for a new child");
				kinds.add(round.kind() + ":" + ((justfatlard.learning_blocks.puzzle.NumbersRound) round).target());
			}
			check(kinds.stream().filter(k -> k.startsWith("find:")).allMatch(k -> List.of("1", "2", "3", "4", "5").contains(k.substring(5))),
				"N0a asked a number outside 1 to 5: " + kinds);
			check(kinds.stream().anyMatch(k -> k.startsWith("look:")), "no quick look in six N0a rounds: " + kinds);
			check(kinds.stream().filter(k -> k.startsWith("find:")).count() >= 3, "N0a's main kind is not most of it: " + kinds);

			// The gate: 1 to 5 mastered, N0b; all of N0, the stage done.
			for (int n = 1; n <= 5; n++) progress.setItemCleanForTests(player, Numbers.numeral(n), Progress.masteredAt());
			check(NumberCourse.current(progress).code().equals("N0b"), "1 to 5 mastered is not N0b: " + NumberCourse.describe(progress));
			check(!NumberCourse.stageDone(progress, 0), "N0 done with N0b to go");
			for (String item : NumberCourse.band("N0b").gate()) progress.setItemCleanForTests(player, item, Progress.masteredAt());
			check(NumberCourse.stageDone(progress, 0) && NumberCourse.current(progress).code().equals("N1a"), "N0 all mastered is not N1a");

			// /learn stage for numbers.
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn stage " + player.getGameProfile().name() + " N2b");
			check(NumberCourse.current(progress).code().equals("N2b"), "/learn stage N2b did not put them there: " + NumberCourse.describe(progress));
			SubjectRound towers = Numbers.INSTANCE.round(player, "towers", null, random);
			check(towers != null && towers.item().equals("numbers:tower:diff"), "N2b's towers are not how-many-more: " + (towers == null ? null : towers.item()));
			NumberCourse.putAt(player, NumberCourse.band("N1a"));
			check(NumberCourse.current(progress).code().equals("N1a"), "putAt N1a did not");

			// A mix-up: 6 for 9, twice; the parent view says so.
			progress.itemMissed(player, Numbers.numeral(9), "6");
			progress.itemMissed(player, NumberCourse.Kind.COUNT.item("9"), "6");
			check(Numbers.mixups(progress, 3).stream().anyMatch(line -> line.startsWith("6/9 (2")), "6 for 9 twice is not a mix-up: " + Numbers.mixups(progress, 3));
			check(Numbers.INSTANCE.summary(progress).stream().anyMatch(line -> line.contains("mixes up 6/9")), "the summary has no mix-up: " + Numbers.INSTANCE.summary(progress));
			justfatlard.learning_blocks.review.ProgressView.open(player, player);
			check(justfatlard.learning_blocks.review.ProgressView.lastShown(player).stream().anyMatch(line -> line.startsWith("Numbers: N1a")),
				"the parent view has no Numbers line: " + justfatlard.learning_blocks.review.ProgressView.lastShown(player));
		});

		// Make n, for every n: exactly n out of the button.
		for (int n = 1; n <= 10; n++) {
			int at = n;
			MakeRound[] make = new MakeRound[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "make", String.valueOf(at));
				make[0] = (MakeRound) Rooms.of(player).subjectRound();
			});
			Waits.until(session, 300, "make " + at + " never settled", () -> !Rooms.of(session.player()).busy());
			session.onServer(server -> make[0].pickForTests(session.player(), make[0].rightForTests()));
			Waits.until(session, 200, "the tile for " + at + " never went in", () -> !Rooms.of(session.player()).busy());
			session.onServer(server -> make[0].pressForTests(session.player()));
			Waits.until(session, 300, "make " + at + " never made " + at, () -> make[0].out().size() == at);
			session.waitTicks(20);
			session.onServer(server -> check(make[0].out().size() == at, "make " + at + " made " + make[0].out().size()));
			Waits.until(session, 1200, "make " + at + " never finished", () -> Rooms.of(session.player()).subjectRound() != make[0] && !Rooms.of(session.player()).busy());
		}

		// Bob, for 1 to 6: exactly that many put.
		for (int n = 1; n <= 6; n++) {
			int at = n;
			NumberBobRound[] bob = new NumberBobRound[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "bob", String.valueOf(at));
				bob[0] = (NumberBobRound) Rooms.of(player).subjectRound();
			});
			Waits.until(session, 300, "Bob " + at + " never settled", () -> !Rooms.of(session.player()).busy());
			session.onServer(server -> bob[0].pickForTests(session.player(), bob[0].rightForTests()));
			Waits.until(session, 400, "Bob never put " + at, () -> bob[0].put().size() == at && bob[0].lastPut() == at);
			Waits.until(session, 1200, "Bob " + at + " never finished", () -> Rooms.of(session.player()).subjectRound() != bob[0] && !Rooms.of(session.player()).busy());
		}

		// The dice: every quick look 1 to 6 mastered but 6, one short; a quick look at 6 right earns them.
		LookRound[] look = new LookRound[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			for (int n = 1; n <= 6; n++) progress.setItemCleanForTests(player, NumberCourse.Kind.LOOK.item(String.valueOf(n)), n == 6 ? Progress.masteredAt() - 1 : Progress.masteredAt());
			check(!Earned.earned(progress, "toy:dice"), "the dice were earned already");
			Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "look", "6");
			look[0] = (LookRound) Rooms.of(player).subjectRound();
		});
		Waits.until(session, 300, "the look never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> look[0].pickForTests(session.player(), look[0].rightForTests()));
		session.waitTicks(5);
		session.onServer(server -> check(Earned.earned(Progress.of(session.player()), "toy:dice"), "the last quick look mastered did not earn the dice"));
		Waits.until(session, 1200, "the look never finished", () -> Rooms.of(session.player()).subjectRound() != look[0] && !Rooms.of(session.player()).busy());

		// The sampler: the pens, same, as practice.
		Tag[] before = new Tag[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			before[0] = Progress.savedForTests(player);
			check(Sampler.runSubject(player, Numbers.INSTANCE, "pens", "bigger") != null, "a pens question it hasn't got was not refused");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), "learn try numbers pens same");
			check(Rooms.of(player).practice() && Rooms.of(player).subjectRound() instanceof PensRound pens && pens.left() == pens.right(),
				"/learn try numbers pens same did not make same pens");
		});
		Waits.until(session, 300, "the sample never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			SubjectRound round = Rooms.of(session.player()).subjectRound();
			round.pickForTests(session.player(), round.rightForTests());
			check(Progress.savedForTests(session.player()).equals(before[0]), "a numbers sample changed the saved progress");
		});
		Waits.until(session, 900, "the sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());
		session.command("/learn leave");
		Waits.until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level()));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
