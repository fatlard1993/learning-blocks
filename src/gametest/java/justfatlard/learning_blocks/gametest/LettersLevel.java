package justfatlard.learning_blocks.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Letters;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.review.Review;
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

/**
 * The letters level, stage 0. A new reader starts there. A round is one gap, no picture, three tiles
 * from the first group, and "Which letter says..." with the sound. The right letter is chanted -
 * sound, sound, keyword - and the keyword happens; another letter says its own sound, goes home, and
 * nothing cheeky happens. A letter is known after two first-try matches, and a match after a miss
 * does not count. A group known opens the next, whose rounds draw on both. Every letter known, the
 * next round is stage 1, with the new stage's reset and line; and an operator can move a reader
 * back to letters or on to words. The review screen ticks the letters known. Two tiles that say the
 * same sound (c, k) are never on the floor together. Once the first group is known, some rounds are
 * words made only of known letters, asked by the first sound, with three known tiles.
 */
public final class LettersLevel implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Progress.lettersFirstForTests(true);
		try {
			Smoke.run(context, "learning-blocks-justfatlard", session -> run(session));
		} finally {
			Progress.lettersFirstForTests(false);
		}
	}

	private static void run(Smoke.Session session) {
		Set<Letter> first = Set.copyOf(Letters.GROUPS.get(0));
		session.onServer(server -> {
			for (Letter letter : Letters.all()) check(Letters.keyword(letter) != null, letter + " has no keyword");
			check(Stages.current(Progress.of(session.player())) == 0, "a new reader does not start on letters");
		});

		// The first round: one gap, no picture, three tiles from the first group; the sound asked for.
		session.command("/learn");
		Waits.until(session, 300, "the letter was never asked for", () -> {
			Room room = Rooms.of(session.player());
			return room != null && room.letterTarget() != null
				&& Narrator.log(session.player()).contains("clip:phoneme/" + room.letterTarget().grapheme());
		});
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(room.stage() == 0, "the room is at stage " + room.stage());
			check(first.contains(room.letterTarget()), "the first round asks for " + room.letterTarget() + ", not a first-group letter");
			check(room.puzzle().target().tiles().size() == 1, "the row is not one gap");
			check(room.picture() == null, "a letters round shows a picture");
			check(room.puzzle().shelf().size() == 3 && first.containsAll(room.puzzle().shelf()),
				"the floor is not three first-group letters: " + room.puzzle().shelf());
			List<String> log = Narrator.log(player);
			int asked = Math.max(log.lastIndexOf("clip:phrase/which_letter_says"), log.lastIndexOf("clip:phrase/find_the_letter"));
			check(asked >= 0 && asked < log.lastIndexOf("clip:phoneme/" + room.letterTarget().grapheme()),
				"the prompt is not which-letter then the sound: " + log);
			check(!log.contains("clip:phrase/make_the_word"), "a letters round asked to make a word");
		});
		shoot(session, "phonics-letters-round");

		// Right, first try: the chant, the keyword happens, and the letter counts once.
		Letter[] target = new Letter[1];
		int[] sent = new int[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			target[0] = room.letterTarget();
			sent[0] = room.dispensed().size();
			Hands.solve(player, room);
			press(player, room);
		});
		Waits.until(session, 600, "the right letter was never chanted", () -> Narrator.log(session.player()).contains("clip:phrase/well_done"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			String g = target[0].grapheme();
			Word keyword = Letters.keyword(target[0]);
			List<String> log = Narrator.log(player);
			// The first press of the visit: its chant runs from the first lit tile to what the keyword did.
			List<String> clips = log.subList(log.indexOf("light:0"), log.indexOf("outcome")).stream()
				.filter(step -> step.startsWith("clip:")).toList();
			check(clips.equals(List.of("clip:phoneme/" + g, "clip:phoneme/" + g, "clip:word/" + keyword.text())),
				"the chant was not " + g + ", " + g + ", " + keyword.text() + ": " + clips);
			check(log.indexOf("outcome") < log.lastIndexOf("clip:phrase/well_done"), "the keyword did not happen before well done");
			check(Rooms.of(player).dispensed().size() > sent[0] || sent[0] >= 32, "the keyword's outcome did not come out of the button");
			check(Progress.of(player).letterHits(g) == 1, "a first-try match did not count: " + Progress.of(player).letterHits(g));
		});

		// Wrong: that letter's sound, home it goes, asked again, and nothing cheeky; then right, not first try.
		// Two letters from the first group the first round did not ask for, fresh for the checks below.
		Letter[] missed = new Letter[1];
		Letter[] learned = new Letter[1];
		session.onServer(server -> {
			List<Letter> others = Letters.GROUPS.get(0).stream().filter(letter -> letter != target[0]).toList();
			missed[0] = others.get(0);
			learned[0] = others.get(1);
		});
		Letter[] wrong = new Letter[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startLetterRound(player, missed[0]), "no letters round for " + missed[0]);
			Room room = Rooms.of(player);
			wrong[0] = room.puzzle().shelf().stream().filter(letter -> letter != missed[0]).findFirst().orElseThrow();
			sent[0] = room.dispensed().size();
			check(Hands.take(player, room.home(wrong[0])), wrong[0] + " would not break");
			Hands.placeInGap(player, room);
			press(player, room);
		});
		Waits.until(session, 600, "the wrong letter never went home", () -> Narrator.log(session.player()).contains("tile_home:" + wrong[0].grapheme()));
		Waits.until(session, 300, missed[0] + " was never asked for again", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("clip:phoneme/" + missed[0].grapheme()) > log.lastIndexOf("tile_home:" + wrong[0].grapheme());
		});
		Waits.until(session, 200, "the room never finished asking again", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			List<String> log = Narrator.log(player);
			List<String> since = log.subList(log.lastIndexOf("light:0"), log.size());
			check(since.contains("clip:phoneme/" + wrong[0].grapheme()), "the wrong letter did not say its own sound: " + since);
			check(!since.contains("outcome"), "a wrong letter did something: " + since);
			// Prizes still flying to the chest from the round before are not the letter's doing.
			List<justfatlard.learning_blocks.puzzle.Dispenser.Dispensed> since2 = Rooms.of(player).dispensed();
			check(since2.subList(Math.min(sent[0], since2.size()), since2.size()).stream().allMatch(d -> d.what().startsWith("prize")),
				"something came out of the button for a wrong letter: " + since2);
			Room room = Rooms.of(player);
			Hands.solve(player, room);
			press(player, room);
		});
		Waits.until(session, 600, missed[0] + " was never chanted after the miss", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("clip:phrase/well_done") > log.lastIndexOf("tile_home:" + wrong[0].grapheme());
		});
		session.onServer(server -> check(Progress.of(session.player()).letterHits(missed[0].grapheme()) == 0, "a match after a miss counted as first try"));

		// Mastered after mastery_clean first-try matches (3 by default).
		int need = Letters.knownAfter();
		for (int i = 0; i < need; i++) {
			int round = i;
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startLetterRound(player, learned[0]), "no letters round for " + learned[0]);
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				press(player, room);
			});
			Waits.until(session, 600, learned[0] + " was never chanted", () -> Progress.of(session.player()).letterHits(learned[0].grapheme()) > round);
			session.onServer(server -> check(Letters.known(Progress.of(session.player()), learned[0]) == (round == need - 1),
				"after " + (round + 1) + " first-try matches " + learned[0] + " is " + (round == need - 1 ? "not " : "") + "known"));
		}

		// The first group known: the second opens, and its rounds draw on both.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			for (Letter letter : first) {
				progress.setLetterHits(player, letter.grapheme(), Letters.knownAfter());
				progress.setCaseHitsForTests(player, letter, Letters.knownAfter());
			}
			check(Letters.currentGroup(progress) == 1, "the first group known did not open the second");
			// Letters only, for this part: the word rounds that come in now are checked after.
			justfatlard.learning_blocks.reward.PrizeConfig.useLetterWords(0);
			Set<Letter> met = new HashSet<>(first);
			met.addAll(Letters.GROUPS.get(1));
			boolean second = false;
			for (int i = 0; i < 8; i++) {
				Rooms.nextRound(player);
				Room room = Rooms.of(player);
				check(met.contains(room.letterTarget()), "a round asks for " + room.letterTarget() + ", beyond the first two groups");
				check(met.containsAll(room.puzzle().shelf()), "the floor has a letter not yet met: " + room.puzzle().shelf());
				second |= Letters.GROUPS.get(1).contains(room.letterTarget());
			}
			check(second, "eight rounds and none from the second group");

			// C and k say the same sound: never both on the floor when the sound is the question.
			for (int i = 0; i < 40; i++) {
				List<Letter> others = Letters.distractors(progress, Letter.C, player.level().getRandom());
				check(!others.contains(Letter.K), "c was asked for with k on the floor too: " + others);
			}

			// Early blending: with the first group known, one round in three is a word of known letters.
			justfatlard.learning_blocks.reward.PrizeConfig.useLetterWords(3);
			Set<Letter> known = Letters.knownLetters(progress);
			boolean word = false;
			for (int i = 0; i < 6 && !word; i++) {
				Rooms.nextRound(player);
				Room room = Rooms.of(player);
				if (room.letterTarget() != null) continue;
				word = true;
				check(room.puzzle().kind() == justfatlard.learning_blocks.puzzle.Puzzle.Kind.FIRST, "a letters-level word is not a first-sound round");
				check(room.puzzle().shelf().size() == 3, "a letters-level word has " + room.puzzle().shelf().size() + " tiles, not 3");
				check(known.containsAll(room.puzzle().shelf()), "a letters-level word's tiles are not all known: " + room.puzzle().shelf() + " of " + known);
				check(known.containsAll(room.puzzle().target().letters()), "a letters-level word uses a letter not known: " + room.puzzle().target().text());
				check(room.picture() != null, "a letters-level word has no picture");
				check(Narrator.log(player).contains("early_word:" + room.puzzle().target().text()), "the word round was not noted");
			}
			check(word, "six rounds after the first group and no word round");
			check(Stages.current(progress) == 0, "a word round moved the reader off letters");
			Review.open(player, Review.Tab.LETTERS, null, false);
		});
		session.waitTicks(10);
		shoot(session, "phonics-review-letters-known");
		session.onServer(server -> Review.press(session.player(), "close"));

		// Every letter known but z: the last match finishes the level, and the next round is words.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			for (Letter letter : Letters.all()) {
				progress.setLetterHits(player, letter.grapheme(), letter == Letter.Z ? Letters.knownAfter() - 1 : Letters.knownAfter());
				progress.setCaseHitsForTests(player, letter, Letters.knownAfter());
			}
			check(Rooms.startLetterRound(player, Letter.Z), "no letters round for z");
			Room room = Rooms.of(player);
			Hands.solve(player, room);
			press(player, room);
		});
		Waits.until(session, 900, "the last letter never led on to words", () -> {
			Room room = Rooms.of(session.player());
			return room != null && room.stage() == 1 && room.letterTarget() == null;
		});
		session.onServer(server -> check(Narrator.log(session.player()).contains("stage_reset:1"), "moving on to words did not reset the room"));
		Waits.until(session, 100, "moving on to words did not say so", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("clip:phrase/next_level") > log.lastIndexOf("clip:word/zap");
		});

		// An operator: back to letters, and on to words again.
		session.onServer(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
			"learn stage " + session.player().getGameProfile().name() + " 0"));
		session.onServer(server -> {
			Room room = Rooms.of(session.player());
			check(room.stage() == 0 && room.letterTarget() != null, "stage 0 did not start the letters again");
			check(Letters.knownLetters(Progress.of(session.player())).isEmpty(), "stage 0 kept the letters known");
		});
		session.onServer(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
			"learn stage " + session.player().getGameProfile().name() + " 1"));
		session.onServer(server -> {
			Room room = Rooms.of(session.player());
			check(room.stage() == 1 && room.letterTarget() == null, "stage 1 did not skip the letters");
		});
		session.command("/learn leave");
		session.waitTicks(20);
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
