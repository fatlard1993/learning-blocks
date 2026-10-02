package justfatlard.phonics.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.Puzzle;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Stages;
import justfatlard.phonics.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One child, walked through every word of stage 1 by the same clicks a child makes, and on into
 * stage 2: every round of stage 1 asks for a word they have not read yet, and the round after the
 * last of them comes from stage 2. Then an operator puts them back at stage 1.
 */
public final class StageWalk implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			// One clean completion masters a word here, so the real-click walk through a band stays short;
			// the Mastery test covers the default of three.
			session.onServer(server -> justfatlard.phonics.reward.PrizeConfig.useMastery(1));
			session.command("/phonics");
			session.waitTicks(40);

			int[] words = new int[1];
			// Every round a new word: no scene rounds of words already read in the way.
			session.onServer(server -> {
				justfatlard.phonics.reward.PrizeConfig.useScenes(false, 4);
				words[0] = Stages.at(1).size();
			});
			check(words[0] >= 8, "stage 1 has only " + words[0] + " words");

			Set<String> asked = new HashSet<>();
			for (int round = 0; round < words[0]; round++) {
				Puzzle[] puzzle = new Puzzle[1];
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					puzzle[0] = room.puzzle();
					check(room.stage() == 1 && puzzle[0].target().stage() == 1,
						"round " + asked.size() + " is from stage " + room.stage() + " before stage 1 is done");
					String id = puzzle[0].target().id().toString();
					check(asked.add(id), "stage 1 asked for " + id + " again while other words were unread");
					check(!Progress.of(player).words().contains(id), "the room asked for a word already read: " + id);
					Hands.solve(player, room);
					click(player, room.button());
				});
				// Read: on to the next round as the room would choose it, without waiting out the cheering.
				Waits.until(session, 900, "round " + round + " was never read",
					() -> Progress.of(session.player()).words().contains(puzzle[0].target().id().toString()));
				session.onServer(server -> Rooms.nextRound(session.player()));
			}

			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(Stages.complete(Progress.of(player), 1), "every stage 1 word read and stage 1 is not complete");
				check(room.stage() == 2 && room.puzzle().target().stage() == 2,
					"the round after stage 1 is from stage " + room.stage() + ": " + room.puzzle().target().id());
			});

			// On through the stages: 2 to 4 and most of 5 read, the rest of 5 by real clicks, and the next round is stage 6.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress progress = Progress.of(player);
				for (int stage = 2; stage <= 4; stage++) for (var word : Stages.at(stage)) progress.masterForTests(player, word);
				var five = Stages.at(5);
				for (int i = 0; i < five.size() - 3; i++) progress.masterForTests(player, five.get(i));
				Rooms.nextRound(player);
			});
			for (int round = 0; round < 6; round++) {
				boolean[] done = new boolean[1];
				Puzzle[] puzzle = new Puzzle[1];
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					done[0] = room.stage() == 6;
					if (done[0]) return;
					check(room.stage() == 5, "on the way to stage 6 the room is at stage " + room.stage());
					puzzle[0] = room.puzzle();
					Hands.solve(player, room);
					click(player, room.button());
				});
				if (done[0]) break;
				Waits.until(session, 900, "a stage 5 round was never read",
					() -> Progress.of(session.player()).words().contains(puzzle[0].target().id().toString()));
				session.onServer(server -> Rooms.nextRound(session.player()));
			}
			session.onServer(server -> {
				Room room = Rooms.of(session.player());
				check(room.stage() == 6 && room.puzzle().target().stage() == 6,
					"every stage 5 word read and the room is at stage " + room.stage() + ": " + room.puzzle().target().id());
			});

			// On to magic e: 6 and 7 and most of 8 read, the rest of 8 by real clicks, and the next round is stage 9, a magic-e word.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress progress = Progress.of(player);
				for (int stage = 6; stage <= 7; stage++) for (var word : Stages.at(stage)) progress.masterForTests(player, word);
				var eight = Stages.at(8);
				for (int i = 0; i < eight.size() - 2; i++) progress.masterForTests(player, eight.get(i));
				Rooms.nextRound(player);
			});
			for (int round = 0; round < 5; round++) {
				boolean[] done = new boolean[1];
				Puzzle[] puzzle = new Puzzle[1];
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					done[0] = room.stage() == 9;
					if (done[0]) return;
					check(room.stage() == 8, "on the way to stage 9 the room is at stage " + room.stage());
					puzzle[0] = room.puzzle();
					Hands.solve(player, room);
					click(player, room.button());
				});
				if (done[0]) break;
				Waits.until(session, 900, "a stage 8 round was never read",
					() -> Progress.of(session.player()).words().contains(puzzle[0].target().id().toString()));
				session.onServer(server -> Rooms.nextRound(session.player()));
			}
			session.onServer(server -> {
				Room room = Rooms.of(session.player());
				check(room.stage() == 9 && room.puzzle().target().stage() == 9, "every stage 8 word read and the room is at stage "
					+ room.stage() + ": " + room.puzzle().target().id());
				List<String> tiles = room.puzzle().target().tiles();
				check(tiles.getLast().equals("e") && room.puzzle().target().phonemeOf(tiles.size() - 1) == null,
					"stage 9's first word is not a magic-e word: " + room.puzzle().target().id());
			});

			// And on to ng, nk and final blends: 9 and most of 10 read, the rest of 10 by real clicks, and the next round is stage 11.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress progress = Progress.of(player);
				for (var word : Stages.at(9)) progress.masterForTests(player, word);
				var ten = Stages.at(10);
				for (int i = 0; i < ten.size() - 2; i++) progress.masterForTests(player, ten.get(i));
				Rooms.nextRound(player);
			});
			for (int round = 0; round < 5; round++) {
				boolean[] done = new boolean[1];
				Puzzle[] puzzle = new Puzzle[1];
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					done[0] = room.stage() == 11;
					if (done[0]) return;
					check(room.stage() == 10, "on the way to stage 11 the room is at stage " + room.stage());
					puzzle[0] = room.puzzle();
					Hands.solve(player, room);
					click(player, room.button());
				});
				if (done[0]) break;
				Waits.until(session, 900, "a stage 10 round was never read",
					() -> Progress.of(session.player()).words().contains(puzzle[0].target().id().toString()));
				session.onServer(server -> Rooms.nextRound(session.player()));
			}
			session.onServer(server -> {
				Room room = Rooms.of(session.player());
				check(room.stage() == 11 && room.puzzle().target().stage() == 11, "every stage 10 word read and the room is at stage "
					+ room.stage() + ": " + room.puzzle().target().id());
			});

			// An operator starts them over.
			session.onServer(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				"phonics stage " + session.player().getGameProfile().name() + " 1"));
			session.onServer(server -> {
				Progress progress = Progress.of(session.player());
				check(Stages.current(progress) == 1 && progress.words().isEmpty(), "setting stage 1 did not start them over: "
					+ Stages.describe(progress));
			});
		});
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
