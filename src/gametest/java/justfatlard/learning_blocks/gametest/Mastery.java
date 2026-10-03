package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Letters;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.review.Review;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mastery and the gates between stages. A word takes three clean completions (right first time) to
 * master; a round with a miss before the right answer adds nothing and takes nothing away; the third
 * says "You've got it!". A stage opens the next only when every word in it is mastered, with the
 * new stage's line. A mastered word leaves the ordinary picks (and the same word is never asked twice
 * running) but comes back as review. Letters use the same count. An old letter_known_after becomes
 * mastery_clean. A reader from before gates keeps their stage. My words shows pips and the mastered
 * border. Full run: {@code phonics-mastery-pips.png}.
 */
public final class Mastery implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try {
			Smoke.run(context, "learning-blocks-justfatlard", Mastery::run);
		} finally {
			PrizeConfig.extrasOffForTests(true);
		}
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);

		session.onServer(server -> {
			check(Progress.masteredAt() == 3 && Letters.knownAfter() == 3, "mastery is not 3 for words and letters: " + Progress.masteredAt()
				+ ", " + Letters.knownAfter());
			check(PrizeConfig.masteryIn("{ \"puzzles\": { \"letter_known_after\": 5 } }") == 5, "letter_known_after 5 did not become mastery_clean 5");
			check(PrizeConfig.masteryIn("{ \"puzzles\": { \"letter_known_after\": 2 } }") == 3, "the old default 2 did not become the new default 3");
			check(PrizeConfig.masteryIn("{ \"puzzles\": { \"mastery_clean\": 4, \"letter_known_after\": 2 } }") == 4, "mastery_clean was overwritten");
		});

		// A miss first: right, but not clean.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startRound(player, word("cat")), "no round for cat");
			Room room = Rooms.of(player);
			Letter wrong = room.puzzle().shelf().stream().filter(l -> l != Letter.C).findFirst().orElseThrow();
			check(Hands.take(player, room.home(wrong)), wrong + " would not break");
			Hands.placeInGap(player, room);
			press(player, room);
		});
		Waits.until(session, 600, "the wrong tile never went home", () -> !Rooms.of(session.player()).busy()
			&& Rooms.of(session.player()).inGap() == null && Narrator.log(session.player()).stream().anyMatch(s -> s.startsWith("tile_home:")));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			Hands.solve(player, room);
			press(player, room);
		});
		waitTold(session);
		session.onServer(server -> check(Progress.of(session.player()).clean(word("cat")) == 0, "a right answer after a miss counted as clean"));

		// Three clean: mastered, and "You've got it!" on the third.
		for (int i = 1; i <= 3; i++) {
			int round = i;
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, word("cat")), "no round for cat");
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				press(player, room);
			});
			waitTold(session);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress progress = Progress.of(player);
				check(progress.clean(word("cat")) == round, "after " + round + " clean rounds cat has " + progress.clean(word("cat")));
				check(progress.mastered(word("cat")) == (round == 3), "cat mastered is wrong after " + round + " clean rounds");
			});
		}
		Waits.until(session, 300, "\"You've got it!\" was never said", () -> Narrator.log(session.player()).contains("clip:phrase/mastered"));

		// The gate: every stage 1 word but one mastered, and the stage holds; the last mastered, and it opens.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			Word last = word("bat");
			for (Word word : Stages.at(1)) if (!word.equals(last)) progress.masterForTests(player, word);
			progress.setCleanForTests(player, last, 2);
			check(Stages.current(progress) == 1, "stage 1 is done with bat unmastered: " + Stages.describe(progress));
			check(Rooms.startRound(player, last), "no round for bat");
			Room room = Rooms.of(player);
			Hands.solve(player, room);
			press(player, room);
		});
		Waits.until(session, 900, "the gate never opened with \"You're ready for new words!\"",
			() -> Narrator.log(session.player()).contains("clip:phrase/next_level"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.of(player).stage() == 2 && Stages.current(Progress.of(player)) == 2, "the stage after mastering stage 1 is not 2");

			// Mastered words leave the picks: all of stage 2 but two, and only those two come up, never twice running.
			// (Situations, which ask read words as little problems, are off for this.)
			PrizeConfig.useScenes(false, 4);
			Progress progress = Progress.of(player);
			List<Word> two = Stages.at(2);
			List<String> open = new ArrayList<>();
			for (Word word : two) {
				if (open.size() < 2 && word.text().matches("pig|dig|big|jig|fin|bin|win|sit|lit|hit")) open.add(word.id().toString());
				else progress.masterForTests(player, word);
			}
			String previous = null;
			for (int i = 0; i < 10; i++) {
				Rooms.nextRound(player);
				String target = Rooms.of(player).puzzle().target().id().toString();
				check(open.contains(target), "a mastered word was picked: " + target + ", open " + open);
				check(!target.equals(previous), target + " was asked twice running");
				previous = target;
			}
			// Review brings mastered words back.
			PrizeConfig.useReadIt(false, 5, 90);
			boolean reviewed = false;
			for (int i = 0; i < 12 && !reviewed; i++) {
				Rooms.nextRound(player);
				String target = Rooms.of(player).puzzle().target().id().toString();
				reviewed = !open.contains(target) && Narrator.log(player).stream().anyMatch(s -> s.startsWith("review:"));
			}
			check(reviewed, "no mastered word came back as review");
			PrizeConfig.extrasOffForTests(true);

			Review.open(player, Review.Tab.WORDS, null, false);
			check(Review.masteredShown(player).contains(word("cat").id().toString()), "My words does not show cat as mastered: " + Review.masteredShown(player));
		});
		session.waitTicks(10);
		shoot(session, "phonics-mastery-pips");
		session.onServer(server -> Review.press(session.player(), "close"));

		// A reader from before gates: stages 1 to 3 read (not mastered) keeps them at stage 4.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			CompoundTag saved = new CompoundTag();
			ListTag read = new ListTag();
			for (int stage = 1; stage <= 3; stage++) for (Word word : Stages.at(stage)) read.add(StringTag.valueOf(word.id().toString()));
			saved.put("words", read);
			saved.putInt("stage_floor", 1);
			Progress legacy = Progress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			Progress.setForTests(player, legacy);
			Progress now = Progress.of(player);
			check(now.stageFloor() == 4 && Stages.current(now) == 4, "a reader from before gates was moved: " + Stages.describe(now));
		});
		session.command("/learn leave");
		session.waitTicks(20);
	}

	private static void waitTold(Smoke.Session session) {
		Waits.until(session, 600, "the word was never read out", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("outcome") > log.lastIndexOf("light:0") && !Rooms.of(session.player()).busy();
		});
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
