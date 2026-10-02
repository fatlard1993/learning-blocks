package justfatlard.phonics.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.Puzzle;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Word;
import justfatlard.phonics.puzzle.Words;
import justfatlard.phonics.reward.Progress;
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
 * Two-letter tiles and building the whole word. Every tile has its texture, lit and not, its model
 * and its place in the blockstate. A digraph word blends the digraph's own sound once, never its
 * letters', and using it first try counts towards knowing it. A whole-word round is every slot a gap
 * and the word's tiles with decoys only from tiles the child knows; a row not full is only "hmm";
 * a build that spells nothing goes home; the word built reads out and happens.
 */
public final class WholeWord implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		// Textures, models and blockstate entries for every tile, from the mod's own resources.
		ClassLoader loader = WholeWord.class.getClassLoader();
		String blockstate;
		try (var in = loader.getResourceAsStream("assets/phonics-justfatlard/blockstates/letter.json")) {
			check(in != null, "no letter blockstate");
			blockstate = new String(in.readAllBytes());
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		for (Letter letter : Letter.values()) {
			String g = letter.grapheme();
			for (String path : List.of("textures/block/letter_" + g + ".png", "textures/block/letter_" + g + "_lit.png",
					"models/block/letter_" + g + ".json", "models/block/letter_" + g + "_lit.json")) {
				check(loader.getResource("assets/phonics-justfatlard/" + path) != null, "no " + path);
			}
			check(blockstate.contains("\"letter=" + g + ",lit=false\""), "the blockstate has no " + g);
		}

		Smoke.run(context, "phonics-justfatlard", session -> {
			session.command("/phonics");
			session.waitTicks(40);

			// A digraph word: sh said once, as sh.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				justfatlard.phonics.reward.PrizeConfig.useScenes(false, 4);
				check(Rooms.startRound(player, word("ship"), Puzzle.Kind.FIRST), "no first-sound round for ship");
				Room room = Rooms.of(player);
				check(room.puzzle().answer() == Letter.SH, "ship's gap wants " + room.puzzle().answer() + ", not the sh tile");
				Hands.solve(player, room);
				press(player, room);
			});
			session.waitTicks(12);
			shoot(session, "phonics-digraph-tile");
			Waits.until(session, 600, "ship was never read out", () -> Progress.of(session.player()).words().contains("phonics-justfatlard:ship")
				&& told(session));
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				List<String> clips = log.subList(log.indexOf("light:0"), log.indexOf("light:all")).stream().filter(step -> step.startsWith("clip:")).toList();
				check(clips.size() == 3 && clips.getFirst().equals("clip:phoneme/sh"), "ship did not blend sh, i, p: " + clips);
				check(!clips.contains("clip:phoneme/s") && !clips.contains("clip:phoneme/h"), "ship blended s and h: " + clips);
				check(Progress.of(session.player()).letterHits("sh") == 1, "reading ship first try did not count towards sh");
			});

			// A whole-word round: every slot a gap, decoys only from what the child knows.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, word("bell"), Puzzle.Kind.WHOLE), "no whole-word round for bell");
				Room room = Rooms.of(player);
				Puzzle puzzle = room.puzzle();
				check(puzzle.kind() == Puzzle.Kind.WHOLE && room.gaps().size() == 3, "bell's round has " + room.gaps().size() + " gaps");
				for (BlockPos gap : room.gaps()) check(player.level().getBlockState(gap).isAir(), "a gap is not empty");
				List<Letter> decoys = new ArrayList<>(puzzle.shelf());
				for (String tile : puzzle.target().tiles()) decoys.remove(Letter.of(tile));
				check(!decoys.isEmpty() && decoys.size() <= 2, "bell has " + decoys.size() + " decoys");
				for (Letter decoy : decoys) check(decoy.isSingle(), "a decoy the child has not learned: " + decoy);
				// One tile in, and the button: only "hmm".
				check(Hands.take(player, room.home(Letter.B)), "b would not break");
				Hands.placeAgainst(player, room.gaps().getFirst().north(), Direction.SOUTH);
				press(player, room);
				List<String> log = Narrator.log(player);
				int partial = log.lastIndexOf("partial");
				check(partial >= 0 && partial + 1 < log.size() && log.get(partial + 1).equals("clip:phrase/hmm")
					&& log.subList(partial, log.size()).stream().noneMatch(step -> step.startsWith("light:")), "a part-built row was not refused: " + log);
			});
			session.waitTicks(10);
			shoot(session, "phonics-whole-word-round");
			Waits.until(session, 100, "the hmm never ended", () -> !Narrator.isPlaying(session.player()));

			// A build that spells nothing: b, a decoy, ll; home it all goes.
			Letter[] decoy = new Letter[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				decoy[0] = room.puzzle().shelf().stream().filter(tile -> !room.puzzle().target().tiles().contains(tile.grapheme())).findFirst().orElseThrow();
				check(Hands.take(player, room.home(decoy[0])), decoy[0] + " would not break");
				Hands.placeAgainst(player, room.gaps().get(1).north(), Direction.SOUTH);
				check(Hands.take(player, room.home(Letter.LL)), "ll would not break");
				Hands.placeAgainst(player, room.gaps().get(2).north(), Direction.SOUTH);
				press(player, room);
			});
			Waits.until(session, 600, "the nonsense build never went home", () -> {
				Room room = Rooms.of(session.player());
				return !room.busy() && room.gaps().stream().allMatch(gap -> session.player().level().getBlockState(gap).isAir());
			});

			// The whole word, built: read out, and it happens.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				press(player, room);
			});
			Waits.until(session, 600, "bell was never read out", () -> Progress.of(session.player()).words().contains("phonics-justfatlard:bell")
				&& told(session));
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				check(log.contains("clip:word/bell") && log.contains("outcome"), "bell was not read out and done: " + log);
			});
			session.command("/phonics leave");
			session.waitTicks(20);
		});
	}

	/** The last blend reached what the word does: the answer is counted at the press, the telling comes after. */
	private static boolean told(Smoke.Session session) {
		List<String> log = Narrator.log(session.player());
		return log.lastIndexOf("outcome") > log.lastIndexOf("light:0");
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
