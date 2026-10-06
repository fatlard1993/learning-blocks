package justfatlard.learning_blocks.gametest;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The daily reading schedule. Scheduled for two a day and joining with none done: sent in for two,
 * "Reading time first!", the portal locked; two solved and "All done for today!" and the portal open.
 * The next day asks for two again, less what was already done that day. A weekday set to none asks
 * for nothing. The time zone moves the day's boundary. An operator is never kept in, unless scheduled
 * themselves. The schedule is read back from its file. A day turning while playing sends nobody in by
 * default.
 */
public final class DailySchedule implements FabricClientGameTest {

	private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);

	@Override
	public void runTest(ClientGameTestContext context) {
		LocalDate[] today = {WEDNESDAY};
		Progress.useClock(() -> today[0]);
		try {
			Smoke.run(context, "learning-blocks-justfatlard", session -> run(session, today));
		} finally {
			Progress.useClock(null);
			Schedules.useZone(ZoneId.systemDefault());
		}
	}

	private static void run(Smoke.Session session, LocalDate[] today) {
		UUID[] id = new UUID[1];
		String[] name = new String[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			id[0] = player.getUUID();
			name[0] = player.getGameProfile().name();
			Schedules.set(id[0], name[0], 2);
			// The file, read again as a restart would: the same schedule.
			Schedules.load();
			check(Schedules.of(id[0]) != null && Schedules.of(id[0]).lessons() == 2 && Schedules.of(id[0]).enabled(), "the schedule did not survive a reload");
			Travel.loggedIn(player);
			check(Visit.of(player) != null && Travel.lessons(player) == 2, "joining with none done did not send them in for two: " + Travel.lessons(player));
			check(Travel.kept(player), "a scheduled child is not kept in");
		});
		Waits.until(session, 400, "\"Reading time first!\" was never said", () -> Narrator.log(session.player()).contains("clip:phrase/reading_time"));
		for (int lesson = 0; lesson < 2; lesson++) {
			int left = 1 - lesson;
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.of(player) != null && Rooms.of(player).puzzle() != null, "no round");
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				press(player, room);
			});
			Waits.until(session, 900, "a lesson was never counted", () -> Travel.lessons(session.player()) == left && !Rooms.of(session.player()).busy());
		}
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Narrator.log(player).contains("clip:phrase/done_for_today"), "\"All done for today!\" was not said: " + Narrator.log(player));
			check(!Narrator.log(player).contains("clip:phrase/portal_open"), "the ordinary portal line was said instead");
			check(Rooms.of(player).portalOpen(), "the portal did not open");
			check(Progress.of(player).doneToday() == 2, "today's count is " + Progress.of(player).doneToday());
			asPlayer(player, "learn leave");
		});
		Waits.until(session, 300, "never got home", () -> Visit.of(session.player()) == null);

		// The next day: two again, less one done earlier that day.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			today[0] = WEDNESDAY.plusDays(1);
			check(Schedules.remaining(player) == 2, "the next day does not ask for two again");
			Progress.of(player).setDoneTodayForTests(player, 1);
			Travel.loggedIn(player);
			check(Travel.lessons(player) == 1, "a round done earlier today did not count: " + Travel.lessons(player));
			release(server, name[0]);
			asPlayer(player, "learn leave");
		});
		Waits.until(session, 300, "never got home from Thursday", () -> Visit.of(session.player()) == null);

		// Thursday set to none: nothing asked.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Schedules.setWeekday(id[0], name[0], DayOfWeek.THURSDAY, 0);
			Progress.of(player).setDoneTodayForTests(player, 0);
			Travel.loggedIn(player);
			check(Visit.of(player) == null, "a weekday with no reading sent them in anyway");

			// The day's boundary is the time zone's.
			Progress.useInstant(() -> Instant.parse("2026-10-01T03:00:00Z"));
			Schedules.useZone(ZoneId.of("America/Los_Angeles"));
			check(Progress.today().equals("2026-09-30"), "3am UTC is not still the 30th in Los Angeles: " + Progress.today());
			Schedules.useZone(ZoneId.of("UTC"));
			check(Progress.today().equals("2026-10-01"), "3am UTC is not the 1st in UTC: " + Progress.today());
			Schedules.useZone(ZoneId.systemDefault());
			Progress.useClock(() -> today[0]);

			// A day turning while they play: nobody is sent in by default.
			today[0] = WEDNESDAY.plusDays(7);
			Schedules.rememberDay(player, WEDNESDAY.plusDays(6).toString());
		});
		session.waitTicks(120);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Visit.of(player) == null, "a new day while playing sent them in without enforce_on_rollover");

			// An operator: never kept in without a schedule; kept in with one.
			Ops.op(server, player);
			Schedules.set(id[0], name[0], 0);
			Travel.loggedIn(player);
			check(Visit.of(player) == null, "an operator with no schedule was sent in");
			Schedules.set(id[0], name[0], 1);
			Travel.loggedIn(player);
			check(Visit.of(player) != null && Travel.kept(player), "an operator who scheduled themselves was not kept in");
			Schedules.set(id[0], name[0], 0);
			check(!Travel.kept(player), "an operator with the schedule off is still kept in");
			server.getPlayerList().deop(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
			release(server, name[0]);
			asPlayer(player, "learn leave");
		});
		Waits.until(session, 300, "the operator never got home", () -> Visit.of(session.player()) == null);
	}

	private static void release(MinecraftServer server, String name) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn release " + name);
	}

	private static void asPlayer(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
