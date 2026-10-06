package justfatlard.learning_blocks.gametest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.MakeRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SortRound;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.reward.Memory;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.PrizeFile;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a stranger's server ships: the default prizes file and none of the tests' own settings (all
 * three subjects, taking turns; letters first; the extras; one clean day a day towards mastery; no
 * lesson twice in a day). A new child with ten lessons a day plays a whole day end to end, answering
 * every round the way a child does, and is let go with "All done for today!"; then a second day.
 * Each day: every subject comes round, no lesson is given twice, and nothing is counted clean twice
 * in a day, so after two days nothing is mastered yet.
 */
public final class ShippedDefaults implements FabricClientGameTest {
	private static final int A_DAY = 10;
	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

	@Override
	public void runTest(ClientGameTestContext context) {
		LocalDate[] today = {MONDAY};
		Progress.useClock(() -> today[0]);
		try {
			Smoke.run(context, "learning-blocks-justfatlard", session -> run(session, today));
		} finally {
			Progress.useClock(null);
			Progress.lettersFirstForTests(false);
			PrizeConfig.extrasOffForTests(true);
			Memory.spreadOffForTests(true);
			Progress.repeatsForTests(true);
			Subjects.onlyForTests(Set.of("reading"));
		}
	}

	private static void run(Smoke.Session session, LocalDate[] today) {
		session.onServer(server -> {
			Progress.lettersFirstForTests(null);
			PrizeConfig.extrasOffForTests(false);
			Memory.spreadOffForTests(false);
			Progress.repeatsForTests(false);
			Subjects.onlyForTests(null);
			PrizeConfig.teamUpForTests(null);
			List<String> problems = PrizeConfig.use(PrizeFile.defaultText(), server);
			check(problems.isEmpty(), "the default prizes file did not read cleanly: " + problems);
			ServerPlayer player = session.player();
			// A new reader, as a stranger's child arrives: on the letters level.
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn stage " + player.getGameProfile().name() + " 0");
			Schedules.set(player.getUUID(), player.getGameProfile().name(), A_DAY);
		});
		Set<String> lessons = new HashSet<>();
		for (int day = 0; day < 2; day++) {
			int d = day;
			today[0] = MONDAY.plusDays(day);
			session.onServer(server -> check(Schedules.enforce(session.player()), "day " + (d + 1) + "'s lessons did not send the child in"));
			Waits.until(session, 600, "the first round of day " + (day + 1) + " never came", () -> {
				Room room = Rooms.of(session.player());
				return room != null && !room.busy() && !room.promptHeld() && (room.puzzle() != null || room.subjectRound() != null);
			});
			List<String> asked = new ArrayList<>();
			Map<String, Integer> bySubject = new HashMap<>();
			for (int lesson = 0; lesson < A_DAY; lesson++) {
				String[] key = new String[1];
				int[] done = new int[1];
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					done[0] = Progress.of(player).doneToday();
					key[0] = room.subjectRound() != null ? room.subjectRound().item()
						: "reading:" + room.puzzle().target().id() + ":" + room.puzzle().kind();
					bySubject.merge(room.currentSubject(), 1, Integer::sum);
				});
				asked.add(key[0]);
				answer(session);
				int at = lesson;
				Waits.until(session, 1500, "lesson " + (lesson + 1) + " of day " + (day + 1) + " (" + key[0] + ") was never counted",
					() -> Progress.of(session.player()).doneToday() == done[0] + 1 && !Rooms.of(session.player()).busy());
				if (at + 1 < A_DAY) Waits.until(session, 900, "no round came after lesson " + (at + 1), () -> {
					Room room = Rooms.of(session.player());
					return room != null && !room.busy() && (room.puzzle() != null || room.subjectRound() != null);
				});
			}
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(new HashSet<>(asked).size() == asked.size(), "day " + (d + 1) + " gave a lesson twice: " + asked);
				check(bySubject.keySet().containsAll(List.of("reading", "numbers", "logic")), "day " + (d + 1) + " did not take turns in all three subjects: " + bySubject);
				check(Narrator.log(player).contains("clip:phrase/done_for_today"), "day " + (d + 1) + " ended without \"All done for today!\"");
				check(!Travel.kept(player), "after the day's lessons the child is still kept in");
				Progress progress = Progress.of(player);
				for (String item : asked) {
					int clean = item.startsWith("reading:") ? -1 : progress.itemClean(item);
					check(clean <= d + 1, item + " counted clean " + clean + " times in " + (d + 1) + " days");
				}
				check(progress.masteredIds().isEmpty(), "something was mastered within two days: " + progress.masteredIds());
			});
			lessons.addAll(asked);
			session.command("/learn leave");
			Waits.home(session);
		}
		session.onServer(server -> {
			Schedules.set(session.player().getUUID(), session.player().getGameProfile().name(), 0);
			PrizeConfig.load(server);
		});
	}

	/** The round up now, answered right the way a child does it. */
	private static void answer(Smoke.Session session) {
		boolean[] sorting = new boolean[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			SubjectRound round = room.subjectRound();
			if (round instanceof SortRound) {
				sorting[0] = true;
				return;
			}
			if (round != null) {
				click(player, round, round.rightForTests());
				if (round instanceof MakeRound) press(player, room);
				return;
			}
			if (room.readIt()) {
				for (var choice : room.choices().entrySet()) {
					if (!choice.getValue().id().equals(room.puzzle().target().id())) continue;
					Rooms.clickEntity(player, player.level(), player.level().getEntity(choice.getKey()));
					return;
				}
				throw new AssertionError("no right picture for " + room.puzzle().target().text());
			}
			Hands.solve(player, room);
			press(player, room);
		});
		if (!sorting[0]) return;
		SortRound[] sort = new SortRound[1];
		session.onServer(server -> sort[0] = (SortRound) Rooms.of(session.player()).subjectRound());
		while (true) {
			boolean[] more = new boolean[1];
			session.onServer(server -> more[0] = Rooms.of(session.player()).subjectRound() == sort[0] && !sort[0].unsorted().isEmpty());
			if (!more[0]) break;
			session.onServer(server -> click(session.player(), sort[0], sort[0].unsorted().getFirst()));
			Waits.until(session, 600, "a pick never settled", () -> !Rooms.of(session.player()).busy());
			session.onServer(server -> {
				if (!sort[0].unsorted().isEmpty()) click(session.player(), sort[0], sort[0].penFor(sort[0].unsorted().getFirst()));
			});
			Waits.until(session, 900, "a sort never settled", () -> !Rooms.of(session.player()).busy() || Rooms.of(session.player()).subjectRound() != sort[0]);
		}
	}

	/** A right-click on a choice: on the block it is, its box, or for a pen, its rug. */
	private static void click(ServerPlayer player, SubjectRound round, SubjectRound.Choice choice) {
		Vec3 spot = round.hitSpotOf(choice);
		var boxes = player.level().getEntitiesOfClass(net.minecraft.world.entity.Interaction.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(0.2));
		if (!boxes.isEmpty()) {
			Rooms.clickEntity(player, player.level(), boxes.getFirst());
			return;
		}
		BlockPos block = BlockPos.containing(spot);
		if (round.choices().contains(choice) && choice.block() == null) block = block.below();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false));
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
