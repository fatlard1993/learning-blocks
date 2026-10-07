package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.integration.ChestPaint;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Sounds;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Four reading together, and a teacher. Turns go round in the order they came, a leaver dropping
 * out and a joiner going to the end; each has their own place, colour and cubby (named, with a
 * banner of their colour, Chest Utils being absent). A round is credited only to those who took
 * part: the answer to the turn reader, a lesson to the reader who pointed, nothing to those who
 * watched; one idle for two rounds hears "Can you help?" alone. Team-ups go through every pair. A
 * teacher is never given a turn or credit and has no cubby, only points, and walks out through a
 * portal shut for the children. A hung round in the big room still starts over.
 */
public final class ManyReaders implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", ManyReaders::run);
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		List<FakePlayer> readers = new ArrayList<>();
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			for (int i = 2; i <= 4; i++) {
				FakePlayer reader = StandIns.inClassroom((ServerLevel) host.level(), "Reader" + i, room.standingSpot(i - 1));
				check(Rooms.join(reader, room), "reader " + i + " could not join");
				readers.add(reader);
			}
			check(room.members().size() == 4, "four readers are not all in: " + room.members().size());
			Set<Vec3> spots = new HashSet<>();
			Set<BlockPos> cubbies = new HashSet<>();
			Set<Integer> colours = new HashSet<>();
			for (UUID member : room.members()) {
				spots.add(room.spawnFor(member));
				cubbies.add(room.prizeMarker(member));
				colours.add(room.colour(member));
			}
			check(spots.size() == 4 && cubbies.size() == 4 && colours.size() == 4, "four readers do not each have a place, a cubby and a colour: "
				+ spots + " " + cubbies + " " + colours);
			checkTurns(host, room, room.members());
		});

		// One leaves, through the server's own handler; another joins and goes to the end.
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			FakePlayer leaver = readers.remove(1);
			ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(leaver.connection, server);
			StandIns.online(server, leaver, false);
		});
		session.waitTicks(5);
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			check(room.members().size() == 3, "the leaver is still in the room: " + room.members().size());
			FakePlayer late = StandIns.inClassroom((ServerLevel) host.level(), "Reader5", room.standingSpot(4));
			check(Rooms.join(late, room), "a late reader could not join");
			readers.add(late);
			check(room.members().getLast().equals(late.getUUID()), "the late reader is not last in the turns");
			checkTurns(host, room, room.members());
			for (ServerPlayer one : everyone(host, readers)) room.refreshPrizeMarker(one);
		});

		// Each cubby named over it in its reader's colour, a banner of that colour behind it.
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			check(!ChestPaint.available(), "Chest Utils is loaded in the tests: the banner path is not what runs");
			for (ServerPlayer one : everyone(host, readers)) {
				Entity label = room.cubbyLabel(one.getUUID());
				check(label instanceof Display.TextDisplay, one.getGameProfile().name() + " has no name over their cubby");
				String text = ((Display.TextDisplay) label).getText().getString();
				check(text.equals(one.getGameProfile().name()), "the cubby says " + text + ", not " + one.getGameProfile().name());
				var banner = host.level().getBlockState(room.bannerSpot(one.getUUID()));
				check(banner.is(Blocks.WALL_BANNER.pick(room.dye(one.getUUID()))), one.getGameProfile().name() + "'s cubby has " + banner + ", not a "
					+ room.dye(one.getUUID()) + " banner");
			}
		});

		// Credit only to those who took part: the turn reader answers, one other points, two watch.
		SubjectRound[] round = new SubjectRound[1];
		session.onServer(server -> {
			Subjects.onlyForTests(Set.of("reading", "numbers"));
			ServerPlayer host = session.player();
			for (ServerPlayer one : everyone(host, readers)) Travel.setLessons(one, 5);
			Rooms.of(host).newSubjectRound(host, Numbers.INSTANCE, "find", "3");
			round[0] = Rooms.of(host).subjectRound();
			check(round[0] != null, "no numbers round");
		});
		Waits.until(session, 600, "the round never settled", () -> !Rooms.of(session.player()).busy());
		ServerPlayer[] cast = new ServerPlayer[4];
		int[][] before = new int[4][];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			List<ServerPlayer> all = everyone(host, readers);
			ServerPlayer turn = all.stream().filter(p -> p.getUUID().equals(room.turn())).findFirst().orElseThrow();
			List<ServerPlayer> rest = new ArrayList<>(all);
			rest.remove(turn);
			cast[0] = turn;
			cast[1] = rest.get(0);
			cast[2] = rest.get(1);
			cast[3] = rest.get(2);
			for (int i = 0; i < 4; i++) before[i] = tally(cast[i], round[0].item());
			BlockPos tile = BlockPos.containing(round[0].hitSpotOf(round[0].rightForTests()));
			Rooms.useBlock(cast[1], cast[1].level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(tile).add(0, 0.5, 0), Direction.UP, tile, false));
			check(room.tookPartForTests(cast[1].getUUID()), "pointing was not taking part");
			check(!room.tookPartForTests(cast[2].getUUID()) && !room.tookPartForTests(cast[3].getUUID()), "watching was taking part");
			Rooms.useBlock(turn, turn.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(tile).add(0, 0.5, 0), Direction.UP, tile, false));
		});
		session.waitTicks(5);
		session.onServer(server -> {
			String item = round[0].item();
			int[] answered = tally(cast[0], item);
			int[] pointed = tally(cast[1], item);
			check(answered[0] == before[0][0] - 1 && answered[1] == before[0][1] + 1 && answered[2] == before[0][2] + 1,
				"the answer did not count in full for the turn reader: " + str(before[0]) + " to " + str(answered));
			check(pointed[0] == before[1][0] - 1 && pointed[1] == before[1][1] + 1 && pointed[2] == before[1][2],
				"the pointer did not get the lesson alone, without the mastery: " + str(before[1]) + " to " + str(pointed));
			for (int i = 2; i < 4; i++) {
				check(java.util.Arrays.equals(tally(cast[i], item), before[i]), cast[i].getGameProfile().name() + " watched and was credited: "
					+ str(before[i]) + " to " + str(tally(cast[i], item)));
			}
		});
		Waits.until(session, 1200, "the round never ended", () -> Rooms.of(session.player()).subjectRound() != round[0] && !Rooms.of(session.player()).busy());

		// Idle for two rounds: "Can you help?" (or "Your turn to help!"), to them alone.
		long[] since = new long[1];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			since[0] = host.level().getGameTime();
			for (int i = 0; i < 2; i++) {
				// Two keep taking part, pointing at the floor; two only watch.
				for (ServerPlayer busy : List.of(cast[0], cast[1])) {
					BlockPos floor = BlockPos.containing(busy.position()).below();
					Rooms.useBlock(busy, busy.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
				}
				for (ServerPlayer one : everyone(host, readers)) Narrator.stop(one);
				Rooms.nextRound(host);
			}
		});
		// Said once the room is quiet: after the round has been asked.
		Waits.until(session, 600, "no idle reader was nudged", () -> Sounds.recent().stream().anyMatch(played -> played.gameTime() >= since[0]
			&& (played.key().equals("phrase/can_you_help") || played.key().equals("phrase/your_turn_to_help"))));
		session.onServer(server -> {
			Set<UUID> watchers = Set.of(cast[2].getUUID(), cast[3].getUUID());
			Set<UUID> told = new HashSet<>();
			for (Sounds.Played played : Sounds.recent()) {
				if (played.gameTime() < since[0] || !played.key().equals("phrase/can_you_help") && !played.key().equals("phrase/your_turn_to_help")) continue;
				told.add(played.to());
			}
			check(!told.isEmpty() && watchers.containsAll(told), "the nudge went to " + told + ", not only the idle readers " + watchers);
			for (ServerPlayer one : List.of(cast[2], cast[3])) {
				if (!told.contains(one.getUUID())) continue;
				List<String> log = Narrator.log(one);
				check(log.contains("nudge:idle"), one.getGameProfile().name() + " was nudged without the room noting it: " + log);
				if (!Sounds.isRecorded("phrase/can_you_help")) {
					check(log.contains("caption:Can you help?") || log.contains("caption:Your turn to help!"),
						"the unrecorded nudge had no caption: " + log);
				}
			}
		});

		// Team-ups go through every pair.
		session.onServer(server -> {
			ServerPlayer host = session.player();
			for (ServerPlayer one : everyone(host, readers)) Progress.of(one).setBand(one, Stages.parse("1a"));
			Set<Set<UUID>> pairs = new HashSet<>();
			for (int i = 0; i < 6; i++) {
				check(Rooms.startTeamRound(host), "team-up " + i + " did not start");
				List<UUID> team = Rooms.of(host).teamReaders();
				check(team.size() == 2, "a team-up is not a pair: " + team);
				pairs.add(Set.copyOf(team));
			}
			check(pairs.size() == 6, "six team-ups did not go through all six pairs: " + pairs);
		});
		Waits.until(session, 900, "the last team-up never settled", () -> !Rooms.of(session.player()).busy());

		// A teacher: in at the back, never a turn, only points, no cubby, never credited.
		FakePlayer[] teacher = new FakePlayer[1];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			teacher[0] = StandIns.inClassroom((ServerLevel) host.level(), "Teacher", room.teacherSpot());
			check(Travel.teach(teacher[0], host) == null, "the teacher could not come in");
			check(Rooms.of(teacher[0]) == room && room.isTeacher(teacher[0].getUUID()), "the teacher is not in the room as its teacher");
			check(room.members().size() == 4 && !room.members().contains(teacher[0].getUUID()), "the teacher became a reader");
			check(room.spawnFor(teacher[0].getUUID()).equals(room.teacherSpot()), "the teacher does not stand at the back");
			check(!room.hasCubby(teacher[0].getUUID()), "the teacher has a cubby");
			for (int i = 0; i < 5; i++) {
				Rooms.nextRound(host);
				check(!teacher[0].getUUID().equals(room.turn()), "the teacher was given a turn");
			}
			Subjects.onlyForTests(Set.of("reading", "numbers"));
			room.newSubjectRound(host, Numbers.INSTANCE, "find", "3");
			round[0] = room.subjectRound();
		});
		Waits.until(session, 600, "the teacher's round never settled", () -> !Rooms.of(session.player()).busy());
		int[] teacherBefore = new int[1];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			teacherBefore[0] = Progress.of(teacher[0]).doneToday();
			BlockPos tile = BlockPos.containing(round[0].hitSpotOf(round[0].rightForTests()));
			int pointed = room.pointed().size();
			Rooms.useBlock(teacher[0], teacher[0].level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(tile).add(0, 0.5, 0), Direction.UP, tile, false));
			check(room.pointed().size() == pointed + 1, "the teacher's click did not point");
			check(room.subjectRound() == round[0] && !room.busy(), "the teacher's click answered the round");
			check(!room.tookPartForTests(teacher[0].getUUID()), "the teacher counted as taking part");
			check(!Rooms.mayBreak(host.level(), teacher[0], tile, host.level().getBlockState(tile)), "the teacher may break the room");
			ServerPlayer turn = everyone(host, readers).stream().filter(p -> p.getUUID().equals(room.turn())).findFirst().orElseThrow();
			Rooms.useBlock(turn, turn.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(tile).add(0, 0.5, 0), Direction.UP, tile, false));
		});
		session.waitTicks(5);
		session.onServer(server -> check(Progress.of(teacher[0]).doneToday() == teacherBefore[0], "the teacher was credited"));
		Waits.until(session, 1200, "the teacher's round never ended", () -> Rooms.of(session.player()).subjectRound() != round[0] && !Rooms.of(session.player()).busy());

		// The portal shut for every child, the teacher walks out through it all the same.
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			for (ServerPlayer one : everyone(host, readers)) Travel.setLessons(one, 5);
			check(!room.portalOpen(), "the portal is open with every child kept");
			Vec3 door = room.portalOpening().getCenter();
			teacher[0].snapTo(door.x, room.teacherSpot().y, door.z - 0.6, 0, 0);
		});
		Waits.until(session, 200, "the teacher never got out", () -> Rooms.of(teacher[0]) == null);
		session.onServer(server -> {
			Room room = Rooms.of(session.player());
			check(room != null && room.members().size() == 4, "the teacher leaving closed the children's room");
			StandIns.online(server, teacher[0], false);
		});

		// A round hung in the big room, four reading: it starts over.
		session.onServer(server -> Rooms.of(session.player()).hangForTests(20 * 60 * 3));
		Waits.until(session, 200, "a hung round in the big room never started over", () -> !Rooms.of(session.player()).busy());

		session.onServer(server -> {
			for (FakePlayer reader : readers) {
				ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(reader.connection, server);
				StandIns.online(server, reader, false);
			}
			Subjects.onlyForTests(Set.of("reading"));
			Travel.setLessons(session.player(), 0);
		});
		session.waitTicks(5);
		session.command("/learn leave");
		Waits.home(session);
	}

	/** Turns go round these readers in this order, twice. */
	private static void checkTurns(ServerPlayer host, Room room, List<UUID> order) {
		List<UUID> turns = new ArrayList<>();
		for (int i = 0; i < order.size() * 2; i++) {
			Rooms.nextRound(host);
			turns.add(room.turn());
		}
		int start = order.indexOf(turns.getFirst());
		for (int i = 0; i < turns.size(); i++) {
			check(turns.get(i).equals(order.get((start + i) % order.size())), "the turns did not go round in the order they came: " + turns + " for " + order);
		}
	}

	private static List<ServerPlayer> everyone(ServerPlayer host, List<FakePlayer> readers) {
		List<ServerPlayer> all = new ArrayList<>();
		all.add(host);
		all.addAll(readers);
		return all;
	}

	/** Lessons left, rounds done today, clean answers of this item. */
	private static int[] tally(ServerPlayer player, String item) {
		return new int[] {Travel.lessons(player), Progress.of(player).doneToday(), Progress.of(player).itemClean(item)};
	}

	private static String str(int[] values) {
		return java.util.Arrays.toString(values);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
