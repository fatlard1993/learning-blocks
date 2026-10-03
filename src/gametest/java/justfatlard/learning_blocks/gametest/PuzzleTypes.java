package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.LetterBlock;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The last-sound and middle-vowel rounds. The gap is in the right slot, the board shows the rest of
 * the word, and every tile on the floor makes a real word in the frame; the right one reads the word
 * and does it. Picks of every kind are families of two or more. A word only one kind can reach
 * (gem, web) is still reachable. The settings: only last sound, or only first sound, and the round
 * is that kind.
 */
public final class PuzzleTypes implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);

			// Families of every kind: never fewer than two different tiles to try.
			session.onServer(server -> {
				RandomSource random = RandomSource.create(7);
				for (int stage : Stages.all()) {
					for (int i = 0; i < 12; i++) {
						Puzzle puzzle = Puzzle.pick(Words.all(), random, Set.of(), stage, Set.of(Puzzle.Kind.LAST, Puzzle.Kind.MIDDLE, Puzzle.Kind.FIRST));
						check(puzzle != null, "no puzzle at stage " + stage);
						check(puzzle.shelf().size() >= 2, "a " + puzzle.kind() + " round with one tile: " + puzzle.target().id());
						check(puzzle.gap() == switch (puzzle.kind()) {
							case FIRST -> 0;
							case LAST -> puzzle.target().tiles().size() - 1;
							case MIDDLE -> 1;
							case WHOLE, PLURAL, TEAM -> 0;
							// A big word's chunk: its first tile.
							case CHUNK -> puzzle.gaps().getFirst();
						}, "the gap of a " + puzzle.kind() + " round is at " + puzzle.gap());
						if (puzzle.kind() != Puzzle.Kind.WHOLE && puzzle.kind() != Puzzle.Kind.CHUNK) for (Letter tile : puzzle.shelf()) check(makesWord(puzzle, tile), puzzle.kind() + " round " + puzzle.target().text()
							+ ": " + tile + " makes no word");
					}
				}
				// Only reachable one way, and reachable.
				check(Puzzle.forTarget(word("chop"), Words.all(), random).kind() == Puzzle.Kind.MIDDLE, "chop is not a middle-vowel round (ch_p)");
				check(Puzzle.forTarget(word("web"), Words.all(), random).kind() == Puzzle.Kind.LAST, "web is not a last-sound round (we_)");
			});

			round(session, "map", Puzzle.Kind.LAST, 2, "phonics-round-last-sound");
			round(session, "cut", Puzzle.Kind.MIDDLE, 1, "phonics-round-middle-vowel");

			// The settings: only last sound on, and a natural round is one; only first sound, and it is that.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress progress = Progress.of(player);
				List<Word> stageOne = Stages.at(1);
				for (int i = 0; i < stageOne.size() * 3 / 5 + 1; i++) progress.read(player, stageOne.get(i).id().toString());
				PrizeConfig.useScenes(false, 4);
				PrizeConfig.useKinds(false, true, false);
				for (int i = 0; i < 3; i++) {
					Rooms.nextRound(player);
					check(Rooms.of(player).puzzle().kind() == Puzzle.Kind.LAST, "only last sound on, and a " + Rooms.of(player).puzzle().kind() + " round");
				}
				PrizeConfig.useKinds(true, false, false);
				for (int i = 0; i < 3; i++) {
					Rooms.nextRound(player);
					check(Rooms.of(player).puzzle().kind() == Puzzle.Kind.FIRST, "only first sound on, and a " + Rooms.of(player).puzzle().kind() + " round");
				}
				PrizeConfig.load(server);
			});
		});
	}

	/** A round of this kind for this word: the gap where it should be, the board the rest, and the right tile reads it. */
	private static void round(Smoke.Session session, String text, Puzzle.Kind kind, int gap, String picture) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startRound(player, word(text), kind), "no " + kind + " round for " + text);
			Room room = Rooms.of(player);
			Puzzle puzzle = room.puzzle();
			check(puzzle.kind() == kind && puzzle.gap() == gap, text + ": a " + puzzle.kind() + " round with the gap at " + puzzle.gap());
			for (Letter tile : puzzle.shelf()) check(makesWord(puzzle, tile), text + ": " + tile + " on the floor makes no word");
			check(puzzle.shelf().size() >= 2, text + ": one tile to try");
			// The gap is the right square of the row; the board shows every other letter of the word.
			BlockPos hole = room.gap();
			check(player.level().getBlockState(hole).isAir(), text + ": the gap is not empty");
			List<String> tiles = puzzle.target().tiles();
			for (int i = 0; i < tiles.size(); i++) {
				if (i == gap) continue;
				BlockPos at = hole.offset(i - gap, 0, 0);
				BlockState state = player.level().getBlockState(at);
				check(state.is(LearningBlocks.BOARD_LETTER) && state.getValue(LetterBlock.LETTER) == Letter.of(tiles.get(i)),
					text + ": the board's letter " + i + " is " + state);
			}
			Hands.solve(player, room);
			press(player, room);
		});
		session.waitTicks(10);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(picture).disableCounterPrefix());
		// Counted at the press; read out and done a moment later.
		Waits.until(session, 600, text + " was never read out", () -> Progress.of(session.player()).words().contains("learning-blocks-justfatlard:" + text)
			&& told(session));
		session.onServer(server -> {
			List<String> log = Narrator.log(session.player());
			check(log.contains("clip:word/" + text) && log.contains("outcome"), text + " was not read out and done: " + log);
		});
	}

	/** The last blend reached what the word does. */
	private static boolean told(Smoke.Session session) {
		List<String> log = Narrator.log(session.player());
		return log.lastIndexOf("outcome") > log.lastIndexOf("light:0");
	}

	private static boolean makesWord(Puzzle puzzle, Letter tile) {
		List<String> tiles = new java.util.ArrayList<>(puzzle.target().tiles());
		tiles.set(puzzle.gap(), tile.grapheme());
		return puzzle.spelled(tiles) != null;
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
