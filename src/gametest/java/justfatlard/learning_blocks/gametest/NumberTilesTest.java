package justfatlard.learning_blocks.gametest;

import java.util.Set;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.NumberRound;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.puzzle.Tiles;
import justfatlard.learning_blocks.subject.Numbers;
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
 * Number tiles are answered as letters are: a click on one picks nothing; broken, it goes into the
 * hand; set down on the floor, it flies back to its spot; placed in the gap on the board, it is the
 * answer. A wrong one is told and flies home to be tried again; the right one ends the round.
 */
public final class NumberTilesTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);
			session.onServer(server -> {
				Subjects.onlyForTests(Set.of("reading"));
				Narrator.note(session.player(), "mark");
				Rooms.of(session.player()).newSubjectRound(session.player(), Numbers.INSTANCE, "find", "3");
			});
			Waits.until(session, 400, "the round never settled", () -> !Rooms.of(session.player()).busy());
			session.waitTicks(60);

			SubjectRound[] round = new SubjectRound[1];
			SubjectRound.Choice[] wrong = new SubjectRound.Choice[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				round[0] = Rooms.of(player).subjectRound();
				check(round[0] instanceof NumberRound, "find made " + round[0]);
				BlockPos gap = round[0].gapForTests();
				check(gap != null && player.level().getBlockState(gap).equals(LearningBlocks.BOARD_LETTER.showing(Letter.BLANK)),
					"no gap on the board for the tile");
				wrong[0] = round[0].choices().stream().filter(c -> !c.equals(round[0].rightForTests())).findFirst().orElseThrow();

				// A click picks nothing now.
				BlockPos spot = spot(round[0], wrong[0]);
				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(spot), Direction.UP, spot, false));
				check(Narrator.log(player).stream().noneMatch(line -> line.startsWith("picked:")), "a click on a tile picked it: " + Narrator.log(player));

				// Broken: into the hand. Set down on the floor: home again.
				take(player, spot, wrong[0]);
				player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(spot.east(3)).add(0, 0.5, 0), Direction.UP, spot.east(3).below(), false));
				check(Tiles.numberOf(player.getMainHandItem()) < 0, "the tile set down on the floor stayed in hand");
			});
			Waits.until(session, 100, "the tile set down on the floor never flew home",
				() -> session.player().level().getBlockState(spot(round[0], wrong[0])).equals(wrong[0].block()));

			// Wrong, in the gap: told, and home again to try another.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				take(player, spot(round[0], wrong[0]), wrong[0]);
				placeInGap(player, round[0]);
				check(Narrator.log(player).contains("picked:numbers:" + wrong[0].id()), "the tile in the gap was not the pick: " + Narrator.log(player));
			});
			Waits.until(session, 600, "the wrong tile never went home and the gap never emptied", () -> {
				ServerPlayer player = session.player();
				return !Rooms.of(player).busy() && player.level().getBlockState(spot(round[0], wrong[0])).equals(wrong[0].block())
					&& player.level().getBlockState(round[0].gapForTests()).equals(LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
			});
			session.onServer(server -> check(Rooms.of(session.player()).subjectRound() == round[0], "a wrong tile ended the round"));

			// Right, in the gap: the round is answered.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				SubjectRound.Choice right = round[0].rightForTests();
				take(player, spot(round[0], right), right);
				placeInGap(player, round[0]);
			});
			Waits.until(session, 900, "the right tile in the gap never ended the round", () -> Rooms.of(session.player()).subjectRound() != round[0]);
			session.onServer(server -> check(Tiles.numberOf(session.player().getMainHandItem()) < 0, "a number tile was left in hand after the round"));
			session.command("/learn leave");
			session.waitTicks(20);
		});
	}

	private static BlockPos spot(SubjectRound round, SubjectRound.Choice choice) {
		return BlockPos.containing(round.hitSpotOf(choice));
	}

	private static void take(ServerPlayer player, BlockPos pos, SubjectRound.Choice choice) {
		check(player.gameMode.destroyBlock(pos), "the " + choice.id() + " tile at " + pos + " would not break");
		check(Tiles.numberOf(player.getMainHandItem()) == Integer.parseInt(choice.id()),
			"broke the " + choice.id() + " tile but hold " + player.getMainHandItem());
	}

	private static void placeInGap(ServerPlayer player, SubjectRound round) {
		BlockPos gap = round.gapForTests();
		player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(gap).add(0, 0, 0.5), Direction.SOUTH, gap, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
