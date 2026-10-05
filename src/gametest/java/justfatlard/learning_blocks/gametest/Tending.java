package justfatlard.learning_blocks.gametest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import justfatlard.learning_blocks.classroom.JoinRequests;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Plots;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.MakeRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.review.ProgressView;
import justfatlard.learning_blocks.reward.Earned;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Prizes;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.ToyItem;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.slf4j.LoggerFactory;

/**
 * The release review's fixes, each tried: milestones count what is learned in every subject and
 * are checked on every subject's path; Bob picks nothing up; any count of lessons up to twenty is
 * said; a child who put the answer in place and went quiet hears "Press the button!"; a round with
 * nothing left to pick, or busy far too long, starts over by itself; a daily schedule asks only for
 * subjects that are on; Numbers' toys are caught up on the way in; /learn give gives back only
 * what was earned; the progress export is named by id inside the reports folder; a squeeze never
 * talks over a round; plots in use are never given away; a join request is asked once at a time,
 * with a wait between; the plot's owner comes back into a room full of guests; and a forget from an
 * old session, through the real disconnect handler, leaves the new session's room and timers be.
 */
public final class Tending implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("Tending failed", failed);
				throw failed;
			}
		});
	}

	private static final String MILESTONE_AT_ONE = """
		{
		  "enabled": true,
		  "daily_cap": 0,
		  "events": {
		    "milestone": { "enabled": true, "every": [ { "words": 1, "prizes": [ { "item": "minecraft:diamond", "count": 1 } ] } ] }
		  }
		}
		""";

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);

		// Milestones: a letter is not a thing learned; a numeral is, and pays the milestone on Numbers' path.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(PrizeConfig.use(MILESTONE_AT_ONE, server).isEmpty(), "the milestone prizes did not read");
			Progress progress = Progress.of(player);
			int before = Prizes.learned(progress);
			Prizes.letterSolved(player, Letter.A, true);
			check(Prizes.learned(progress) == before, "a letter counted as a thing learned");
			check(diamonds(player) == 0, "a letter paid the milestone");
			Prizes.itemSolved(player, Numbers.numeral(4), true);
			check(Prizes.learned(progress) == before + 1, "a numeral did not count as a thing learned");
			check(diamonds(player) == 1, "a numeral did not pay the milestone");
		});

		// Bob picks nothing up.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "bob", "3");
			List<Villager> bobs = player.level().getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(12),
				villager -> villager.hasCustomName() && villager.getCustomName().getString().equals("Bob"));
			check(!bobs.isEmpty() && bobs.stream().noneMatch(Villager::canPickUpLoot), "Bob can pick things up");
		});
		settle(session);

		// Fifteen lessons left: said, not written.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Travel.setLessons(player, 15);
			Narrator.stop(player);
			Narrator.note(player, "mark");
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "learn leave");
		});
		session.waitTicks(10);
		session.onServer(server -> {
			check(since(session.player()).contains("clip:phrase/more_to_go_15"), "fifteen to go was not said: " + since(session.player()));
			Travel.setLessons(session.player(), 0);
		});

		// The answer in place, then quiet: "Press the button!".
		MakeRound[] make = new MakeRound[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "make", "3");
			make[0] = (MakeRound) Rooms.of(player).subjectRound();
		});
		settle(session);
		session.onServer(server -> make[0].pickForTests(session.player(), make[0].rightForTests()));
		settle(session);
		session.onServer(server -> Narrator.note(session.player(), "mark"));
		Waits.until(session, 900, "the quiet child was never told to press the button", () -> since(session.player()).contains("clip:phrase/press_the_button"));

		// Nothing left to pick: a new round, by itself.
		SubjectRound[] dead = new SubjectRound[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Narrator.stop(player);
			Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "find", "2");
			dead[0] = Rooms.of(player).subjectRound();
		});
		settle(session);
		session.onServer(server -> dead[0].loseChoicesForTests());
		Waits.until(session, 200, "a round with nothing to pick never started over", () -> Rooms.of(session.player()).subjectRound() != dead[0]);
		session.onServer(server -> check(Travel.lastStuck != null && Travel.lastStuck.contains("nothing left to pick"), "no STUCK line: " + Travel.lastStuck));
		settle(session);
		// Busy far too long: the same.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Narrator.stop(player);
			Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "find", "3");
			dead[0] = Rooms.of(player).subjectRound();
		});
		settle(session);
		session.onServer(server -> {
			// The prompt done with first: its ending would set the room to not busy.
			Narrator.stop(session.player());
			Rooms.of(session.player()).hangForTests(20 * 60 * 3);
		});
		Waits.until(session, 200, "a round busy far too long never started over", () -> Rooms.of(session.player()).subjectRound() != dead[0]);
		session.onServer(server -> check(Travel.lastStuck.contains("busy"), "no busy STUCK line: " + Travel.lastStuck));
		settle(session);

		// Squeezes: never over the room's own line, but one squeeze's word gives way to the next.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(!Rooms.of(player).busy(), "the room was busy before the squeezes");
			Narrator.play(player, new justfatlard.learning_blocks.narration.Narration().say("word/cat"));
			Narrator.note(player, "mark");
			Rooms.sayQuietly(player, "word/owl");
			check(!since(player).contains("clip:word/owl"), "a squeeze talked over the room's line");
			Narrator.stop(player);
			Rooms.sayQuietly(player, "word/owl");
			Rooms.sayQuietly(player, "word/drum");
			List<String> said = since(player);
			check(said.contains("clip:word/owl") && said.contains("clip:word/drum"), "one squeeze did not give way to the next: " + said);
			Narrator.stop(player);
		});

		// A squeeze while the room is telling something says nothing over it.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			room.hangForTests(0);
			Narrator.note(player, "mark");
			Rooms.sayQuietly(player, "word/owl");
			check(!since(player).contains("clip:word/owl"), "a squeeze talked over a busy room");
		});
		session.onServer(server -> {
			// Back to an ordinary round.
			ServerPlayer player = session.player();
			Narrator.stop(player);
			Rooms.of(player).newSubjectRound(player, Numbers.INSTANCE, "find", "4");
		});
		settle(session);

		// A schedule asks only for subjects that are on.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Schedules.setSubjects(player.getUUID(), player.getGameProfile().name(), Map.of("reading", 1, "numbers", 2));
			check(Schedules.perSubjectToday(player).keySet().equals(Set.of("reading")), "numbers, off, is still asked for: " + Schedules.perSubjectToday(player));
			check(Schedules.askedButOff(player).equals(List.of("numbers")), "numbers is not named as asked but off");
			Schedules.set(player.getUUID(), player.getGameProfile().name(), 0);
		});

		// Numbers' toys caught up on the way in; /learn give only what was earned; the export by id.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			NumberCourse.putAt(player, NumberCourse.band("N1a"));
			check(!Earned.earned(progress, "toy:sorter"), "the sorter was earned before the way in");
			Prizes.entered(player);
			check(Earned.earned(progress, "toy:sorter"), "the sorter, its numerals all mastered, was not caught up on the way in");
			long sorters = Visit.of(player).prizes().stream().filter(stack -> ToyItem.kindOf(stack) == Toy.SORTER).count();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn give " + player.getGameProfile().name() + " sorter");
			check(Visit.of(player).prizes().stream().filter(stack -> ToyItem.kindOf(stack) == Toy.SORTER).count() == sorters + 1, "/learn give sorter gave nothing");
			int all = Visit.of(player).prizes().size();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn give " + player.getGameProfile().name() + " robot");
			check(Visit.of(player).prizes().size() == all, "/learn give gave a toy that was never earned");
			Path file = ProgressView.export(player);
			check(file != null && file.getFileName().toString().startsWith(player.getUUID().toString())
				&& file.getParent().getFileName().toString().equals("reports"), "the export is not named by id in reports: " + file);
			check(Plots.prune(server, 1) == 0, "a plot in use was given away");
		});

		// A join request: once at a time, a wait before asking again.
		FakePlayer[] guests = new FakePlayer[4];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			ServerLevel classroom = (ServerLevel) host.level();
			Room room = Rooms.of(host);
			for (int i = 0; i < 4; i++) guests[i] = StandIns.inClassroom(classroom, "Guest" + i, room.spawn().add(-1 - i, 0, 0));
			check(JoinRequests.ask(guests[3], host) == null, "a first request was refused");
			check(JoinRequests.waiting(host.getUUID(), guests[3].getUUID()), "the request is not waiting");
			check(JoinRequests.ask(guests[3], host) != null, "a second request while the first waits was put");
			check(JoinRequests.answer(host, guests[3].getUUID(), false) == null, "no could not be said");
			check(JoinRequests.ask(guests[3], host) != null, "asking again at once was not made to wait");
			// Two guests in with the owner: the room is full.
			for (int i = 0; i < 2; i++) check(Travel.readWith(guests[i], room) == null, "guest " + i + " could not join");
			check(room.members().size() == 3, "three readers did not fill the room: " + room.members().size());
		});
		// The owner goes home, a third guest takes the place, and the owner comes back: into the full room, not stranded.
		Room[] guestsRoom = new Room[1];
		session.onServer(server -> {
			guestsRoom[0] = Rooms.of(session.player());
			Travel.leave(session.player());
		});
		Waits.until(session, 300, "the owner never got home", () -> !Places.isClassroom(session.player().level()));
		session.onServer(server -> {
			check(Rooms.of(guests[0]) == guestsRoom[0], "the guests lost their room when the owner went");
			check(Travel.readWith(guests[2], guestsRoom[0]) == null, "a third guest could not take the owner's place");
			check(guestsRoom[0].members().size() == 3, "the room is not full of guests: " + guestsRoom[0].members());
		});
		session.command("/learn");
		Waits.until(session, 300, "the owner never came back in", () -> Rooms.of(session.player()) != null);
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			check(room == Rooms.of(guests[0]) && room.members().size() == 4, "the owner did not come back into the full room: " + room.members());
			for (int i = 0; i < 4; i++) {
				Rooms.forget(guests[i]);
				StandIns.online(server, guests[i], false);
			}
		});
		settle(session);

		// An old session's forget, through the real disconnect handler, after a "re-login": the room and its timers stay.
		boolean[] ran = new boolean[1];
		Room[] before = new Room[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			before[0] = Rooms.of(player);
			justfatlard.learning_blocks.Later.after(player, 30, p -> ran[0] = true);
			FakePlayer old = FakePlayer.get((ServerLevel) player.level(), player.getGameProfile());
			check(Rooms.relogged(old), "an old session's player is not seen as one");
			net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(old.connection, server);
		});
		session.waitTicks(40);
		session.onServer(server -> {
			check(ran[0], "an old session's disconnect cancelled the new session's timer");
			check(Rooms.of(session.player()) == before[0], "an old session's disconnect closed the new session's room");
		});

		session.command("/learn leave");
		Waits.home(session);
		session.onServer(server -> Subjects.onlyForTests(Set.of("reading")));
	}

	private static int diamonds(ServerPlayer player) {
		Visit visit = Visit.of(player);
		return visit == null ? 0 : visit.prizes().stream().filter(stack -> stack.is(net.minecraft.world.item.Items.DIAMOND)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
	}

	private static List<String> since(ServerPlayer player) {
		List<String> log = Narrator.log(player);
		int mark = log.lastIndexOf("mark");
		return log.subList(Math.max(0, mark), log.size());
	}

	private static void settle(Smoke.Session session) {
		Waits.until(session, 900, "the room never settled", () -> !Rooms.of(session.player()).busy());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
