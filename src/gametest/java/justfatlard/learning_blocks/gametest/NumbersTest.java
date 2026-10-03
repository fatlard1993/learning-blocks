package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.puzzle.BedsRound;
import justfatlard.learning_blocks.puzzle.CountRound;
import justfatlard.learning_blocks.puzzle.CountSceneRound;
import justfatlard.learning_blocks.puzzle.LookRound;
import justfatlard.learning_blocks.puzzle.MakeRound;
import justfatlard.learning_blocks.puzzle.NameRound;
import justfatlard.learning_blocks.puzzle.NextRound;
import justfatlard.learning_blocks.puzzle.NumberBobRound;
import justfatlard.learning_blocks.puzzle.NumberRound;
import justfatlard.learning_blocks.puzzle.OrderRound;
import justfatlard.learning_blocks.puzzle.PensRound;
import justfatlard.learning_blocks.puzzle.PictureRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.puzzle.ToyboxRound;
import justfatlard.learning_blocks.puzzle.TowersRound;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
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
 * Every kind of Numbers round, end to end, in the classroom: made, looked at (a screenshot of
 * each on a full run, {@code learning-numbers-<kind>.png}), answered wrong where a kind has a wrong
 * answer (told, and asked again), and right (told: the chant, Bob, the beds, the blocks), counted
 * for the day and the item's memory. Which block says it, a quick look, a number's name read, how
 * many, a team-up count, make a number with the button, beds for kittens, the Toybox, both scenes,
 * Tell Bob, what's wrong, more, the pens (same, and more), the towers (taller, and how many more),
 * a number line of five and of ten, what comes after and before, the picture with that many.
 */
public final class NumbersTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("NumbersTest failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			// Reading's rounds between, so each kind is asked for by name.
			Subjects.onlyForTests(Set.of("reading"));
			Progress progress = Progress.of(player);
			progress.masterForTests(player, Words.all().stream().filter(w -> w.text().equals("six")).findFirst().orElseThrow());
			for (Plush plush : List.of(Plush.OWL, Plush.DUCK, Plush.TEDDY)) progress.ownPlushie(player, plush);
		});

		// Which block says it: a wrong tile told, then the right one, and seven things counted out.
		play(session, "find", "7", NumberRound.class, true, round -> {
			Waits.until(session, 300, "seven things never popped out", () -> round.popped().size() == 7 || gone(session, round));
		}, null);
		check(session, "clip:number/7", "the chant never said seven");

		// A quick look: the dots up, then down.
		LookRound[] look = new LookRound[1];
		play(session, "look", "4", LookRound.class, false, null, round -> look[0] = round);
		session.onServer(server -> check(Progress.of(session.player()).itemClean(NumberCourse.Kind.LOOK.item("4")) == 1, "a quick look right first try is not one clean"));

		play(session, "name", "6", NameRound.class, true, null, null);
		check(session, "clip:word/six", "the number's name was not read out once right");

		play(session, "count", "6", CountRound.class, true, round -> session.onServer(server -> check(round.things().size() == 6, "a count of six did not bring six: " + round.things().size())), null);
		check(session, "clip:phrase/count_with_me", "\"Let's count them!\" was not said");
		play(session, "team", "7", CountRound.class, false, round -> session.onServer(server -> check(round.things().size() == 7, "a team count of seven did not bring seven")), null);

		// Make a number: a wrong tile into the gap and the button, then the right one.
		session.onServer(server -> start(session.player(), "make", "4"));
		settle(session);
		shoot(session, "learning-numbers-make");
		MakeRound[] make = new MakeRound[1];
		int[] wrong = new int[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			make[0] = (MakeRound) Rooms.of(player).subjectRound();
			SubjectRound.Choice other = make[0].choices().stream().filter(c -> !c.equals(make[0].rightForTests())).findFirst().orElseThrow();
			wrong[0] = Integer.parseInt(other.id());
			make[0].pickForTests(player, other);
		});
		Waits.until(session, 200, "the tile never went in the gap", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			check(make[0].chosen() != null, "the picked tile is not in the gap");
			make[0].pressForTests(session.player());
		});
		Waits.until(session, 300, "the wrong number never came out", () -> make[0].out().size() == wrong[0]);
		Waits.until(session, 400, "the wrong make never finished", () -> !Rooms.of(session.player()).busy());
		check(session, wrong[0] > 4 ? "clip:phrase/too_many" : "clip:phrase/not_enough", "the wrong make was not told");
		session.onServer(server -> {
			check(make[0].chosen() == null && make[0].out().isEmpty(), "the wrong make was not tidied away");
			make[0].pickForTests(session.player(), make[0].rightForTests());
		});
		Waits.until(session, 200, "the right tile never went in", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> make[0].pressForTests(session.player()));
		Waits.until(session, 300, "four never came out", () -> make[0].out().size() == 4);
		waitGone(session, make[0]);

		play(session, "beds", "2", BedsRound.class, true, round -> {
			Waits.until(session, 300, "the missing beds never came", () -> round.bedsOut().size() == round.kittens() || gone(session, round));
		}, null);
		play(session, "toybox", "plushies", ToyboxRound.class, true, round -> session.onServer(server -> check(round.count() == 3, "three plushies owned, counted " + round.count())), null);
		play(session, "scene", "pigs", CountSceneRound.class, true, round -> {
			Waits.until(session, 300, "the carrots never came", () -> round.meals().size() == 3 || gone(session, round));
		}, null);
		check(session, "clip:scene/hungry_pigs", "the pigs' scene was not told");
		play(session, "scene", "cakes", CountSceneRound.class, false, null, null);

		// Bob: a wrong card, and he does exactly that; then the right one.
		NumberBobRound[] bob = new NumberBobRound[1];
		play(session, "bob", "3", NumberBobRound.class, true, round -> {
			Waits.until(session, 200, "Bob never put three", () -> round.lastPut() == 3 || gone(session, round));
		}, round -> bob[0] = round);
		check(session, "clip:phrase/bob_counted", "Bob did not say he counted them");
		play(session, "fix", "2", NumberBobRound.class, false, null, round -> check(round.lastPut() == round.wrongCard(), "What's wrong did not start with Bob's wrong go"));
		check(session, "clip:phrase/bob_did_it", "Bob did not say he did it");
		play(session, "more", "apples", NumberBobRound.class, true, round -> {
			Waits.until(session, 200, "Bob never put more", () -> round.lastPut() > round.goal() || gone(session, round));
		}, null);

		play(session, "pens", "same", PensRound.class, true, round -> session.onServer(server -> check(round.left() == round.right(), "the same pens are not the same")), null);
		check(session, "clip:phrase/the_same", "\"They're the same!\" was not said");
		play(session, "pens", "more", PensRound.class, true, null, null);
		play(session, "towers", "taller", TowersRound.class, true, null, null);
		play(session, "towers", "diff", TowersRound.class, false, round -> {
			Waits.until(session, 300, "the blocks never came", () -> round.added().size() == Math.abs(round.left() - round.right()) || gone(session, round));
		}, null);

		// A number line: one out of turn, then all in order.
		order(session, "5");
		order(session, "10");
		play(session, "next", "after", NextRound.class, true, null, null);
		play(session, "next", "before", NextRound.class, false, null, null);
		play(session, "picture", "3", PictureRound.class, true, null, null);

		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Progress.of(player).doneToday("numbers") == 22, "the numbers rounds were not all counted: " + Progress.of(player).doneToday("numbers"));
		});
		session.command("/learn leave");
		Waits.until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level()));
	}

	/** A number line of n: 2 first (out of turn), then 1 to n in order. */
	private static void order(Smoke.Session session, String n) {
		session.onServer(server -> start(session.player(), "order", n));
		settle(session);
		shoot(session, "learning-numbers-order-" + n);
		OrderRound[] round = new OrderRound[1];
		session.onServer(server -> {
			round[0] = (OrderRound) Rooms.of(session.player()).subjectRound();
			SubjectRound.Choice two = round[0].choices().stream().filter(c -> c.id().equals("2")).findFirst().orElseThrow();
			round[0].pickForTests(session.player(), two);
		});
		Waits.until(session, 300, "the one out of turn was never told", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> check(round[0].done() == 0, "a tile out of turn went up"));
		for (int i = 1; i <= Integer.parseInt(n); i++) {
			session.onServer(server -> round[0].pickForTests(session.player(), round[0].next()));
			int at = i;
			Waits.until(session, 1200, at + " never went up", () -> round[0].done() == at && !Rooms.of(session.player()).busy() || gone(session, round[0]));
		}
		waitGone(session, round[0]);
	}

	private static void start(ServerPlayer player, String kind, String target) {
		Narrator.note(player, "mark");
		Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, kind, target);
	}

	/** Out of the button and landed, and the question asked. */
	private static void settle(Smoke.Session session) {
		Waits.until(session, 400, "the round never settled", () -> !Rooms.of(session.player()).busy());
		session.waitTicks(60);
	}

	/**
	 * A kind, end to end: made, shot, a wrong answer if asked (told, the round still up), then the
	 * right one; {@code during} after the right pick, {@code before} once it is up.
	 */
	private static <T extends SubjectRound> void play(Smoke.Session session, String kind, String target, Class<T> type, boolean wrongFirst,
			Consumer<T> during, Consumer<T> before) {
		session.onServer(server -> start(session.player(), kind, target));
		settle(session);
		shoot(session, "learning-numbers-" + kind + "-" + target);
		@SuppressWarnings("unchecked")
		T[] round = (T[]) new SubjectRound[1];
		session.onServer(server -> {
			SubjectRound up = Rooms.of(session.player()).subjectRound();
			check(type.isInstance(up), kind + " " + target + " made " + up);
			round[0] = type.cast(up);
			check(up.item().startsWith("numbers:"), kind + "'s item is " + up.item());
			if (before != null) before.accept(round[0]);
		});
		if (wrongFirst) {
			session.onServer(server -> {
				SubjectRound.Choice other = round[0].choices().stream().filter(c -> !c.equals(round[0].rightForTests())).findFirst().orElseThrow();
				round[0].pickForTests(session.player(), other);
			});
			Waits.until(session, 600, kind + "'s wrong answer was never told", () -> !Rooms.of(session.player()).busy());
			session.onServer(server -> check(Rooms.of(session.player()).subjectRound() == round[0], kind + ": a wrong answer ended the round"));
		}
		session.onServer(server -> round[0].pickForTests(session.player(), round[0].rightForTests()));
		if (during != null) {
			session.waitTicks(2);
			during.accept(round[0]);
		}
		waitGone(session, round[0]);
	}

	private static boolean gone(Smoke.Session session, SubjectRound round) {
		return Rooms.of(session.player()).subjectRound() != round;
	}

	private static void waitGone(Smoke.Session session, SubjectRound round) {
		Waits.until(session, 1200, round.kind() + " never finished", () -> gone(session, round) && !Rooms.of(session.player()).busy());
	}

	/** Something in the log since the last round was started. */
	private static void check(Smoke.Session session, String entry, String failure) {
		session.onServer(server -> {
			List<String> log = Narrator.log(session.player());
			int mark = log.lastIndexOf("mark");
			check(log.subList(Math.max(0, mark), log.size()).contains(entry), failure + ": " + log.subList(Math.max(0, mark), log.size()));
		});
	}

	/** From where a child stands, a little higher, looking at the board and the floor. */
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
