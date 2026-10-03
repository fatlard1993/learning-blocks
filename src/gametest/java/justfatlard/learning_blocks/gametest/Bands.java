package justfatlard.learning_blocks.gametest;

import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.review.ProgressView;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Long stages split into bands by the letters their words need: 1a is words of s a t p i n alone,
 * and its rounds ask only those while the tiles on the floor still come from all of stage 1;
 * mastering a band opens the next with "You're ready for new words!" and pays stage_complete;
 * {@code /learn stage 1b} puts a reader at a band; the parent view heads its words by band.
 * The band table is logged. Full run: {@code phonics-bands-progress.png}.
 */
public final class Bands implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("phonics-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LOGGER.error("Bands failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);

		session.onServer(server -> {
			int mastery = Progress.masteredAt();
			for (Stages.Band band : Stages.bands()) {
				LOGGER.info("[band] {} | {} words | {} rounds | {}", band.label(), band.words().size(), band.words().size() * mastery,
					String.join(" ", band.words().stream().map(Word::text).toList()));
			}
			List<String> labels = Stages.labels();
			check(labels.containsAll(List.of("1a", "1b", "1c", "2a", "2b")), "stages 1 and 2 are not in bands: " + labels);
			for (Stages.Band band : Stages.bands()) {
				check(band.words().size() >= 4 || band.of() == 1, "band " + band.label() + " is tiny: " + band.words().size());
				check(band.of() > 1 || band.words().size() * mastery <= Stages.LONG_STAGE || band.stage() <= 2,
					"stage " + band.label() + " is long and not split: " + band.words().size());
			}
			Stages.Band first = Stages.parse("1a");
			for (Word word : first.words()) {
				check(word.tiles().stream().allMatch("satpin"::contains), "1a has " + word.text() + ", which needs more than s a t p i n");
			}

			// A reader at 1a is asked only 1a's words; the tiles on the floor still come from all of stage 1.
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			check(Stages.currentBand(progress) == first, "a new stage 1 reader is not at 1a: " + Stages.describe(progress));
			check(Stages.describe(progress).startsWith("stage 1a"), "describe does not say 1a: " + Stages.describe(progress));
			boolean wider = false;
			for (int i = 0; i < 12; i++) {
				Rooms.nextRound(player);
				Room room = Rooms.of(player);
				Word target = room.puzzle().target();
				check(first.contains(target), "a 1a reader was asked " + target.text());
				wider |= room.puzzle().family().stream().anyMatch(word -> !first.contains(word));
			}
			check(wider, "no 1a round had a family word from a later band: the floor is only 1a's tiles");
		});

		// The last 1a word mastered: on to 1b, said, and paid.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			Stages.Band first = Stages.parse("1a");
			Word last = first.words().getLast();
			for (Word word : first.words()) if (!word.equals(last)) progress.masterForTests(player, word);
			progress.setCleanForTests(player, last, Progress.masteredAt() - 1);
			check(Rooms.startRound(player, last), "no round for " + last.text());
			Room room = Rooms.of(player);
			Hands.solve(player, room);
			press(player, room);
		});
		Waits.until(session, 900, "1b never opened with \"You're ready for new words!\"",
			() -> Narrator.log(session.player()).contains("clip:phrase/next_level"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			check(Stages.currentBand(progress) == Stages.parse("1b"), "mastering 1a did not open 1b: " + Stages.describe(progress));
			check(Stages.current(progress) == 1, "1b is not stage 1");
			check(progress.paidForTests(Stages.parse("1a").code()), "1a did not pay stage_complete");
			check(Stages.parse("1b").contains(Rooms.of(player).puzzle().target()), "the round after 1a is not a 1b word");

			// Put at a band: 2b, straight in; and back to stage 1, at 1a again.
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				"learn stage " + player.getGameProfile().name() + " 2b");
			check(Stages.currentBand(progress) == Stages.parse("2b") && progress.bandFloor() == Stages.parse("2b").code(),
				"/learn stage 2b did not put them at 2b: " + Stages.describe(progress));
			check(Stages.parse("2b").contains(Rooms.of(player).puzzle().target()), "the round at 2b is not a 2b word");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				"learn stage " + player.getGameProfile().name() + " 1b");
			check(Stages.currentBand(progress) == Stages.parse("1b"), "/learn stage 1b did not put them at 1b: " + Stages.describe(progress));
			check(ProgressView.open(player, player), "the progress screen did not open");
			List<String> shown = ProgressView.lastShown(player);
			check(shown.stream().anyMatch(line -> line.startsWith("Stage 1a (")) && shown.stream().anyMatch(line -> line.startsWith("Stage 2b (")),
				"the parent view does not head words by band: " + shown);
		});
		session.waitTicks(10);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("phonics-bands-progress").disableCounterPrefix());
		session.command("/learn leave");
		session.waitTicks(20);
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
