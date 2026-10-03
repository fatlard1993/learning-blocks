package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.LetterBlock;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.sound.Sounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A word can say a tile differently from the tile's own sound. The test-only word {@code wake}, from
 * this source set's data pack, says its a long and its e not at all: sounded out, it plays c, a_long,
 * k, then nothing for the e - which is lit, and greyed in the caption - then the word. The same a
 * in the row says a_long when tapped, once wake is built, because it is in wake there; a loose a says its short sound. It is
 * a magic-e word: at the silent e the e and the a light together ({@code magic_e:1}), and the caption
 * shows the a saying its name as a capital. Wake has no recording of its own to cut from; cat has, so cat blends c, cat's own a, t.
 */
public final class SoundOverrides implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);

			Word[] wake = new Word[1];
			session.onServer(server -> {
				wake[0] = Words.all().stream().filter(word -> word.id().toString().equals("phonics-gametest:wake")).findFirst()
					.orElseThrow(() -> new AssertionError("the test word wake did not load"));
				Word word = wake[0];
				check("phoneme/w".equals(word.soundOf(0)), "w's sound in wake is " + word.soundOf(0));
				check("phoneme/a_long".equals(word.soundOf(1)), "a's sound in wake is " + word.soundOf(1));
				check(word.soundOf(3) == null, "e in wake is not silent: " + word.soundOf(3));
				check("phoneme/a".equals(LetterBlock.phoneme(LearningBlocks.LETTER.showing(Letter.A))), "a loose a is not the short a");
				Word cat = Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow();
				check("phoneme/a".equals(cat.phonemeOf(1)), "a word with no overrides does not have the tile's own sound as standard");
				// Hybrid blends: a vowel in the row is cut from the word's own recording; consonants are standard.
				check("blend/cat/1".equals(cat.soundOf(1)), "cat's a is not cut from cat: " + cat.soundOf(1));
				check("phoneme/c".equals(cat.soundOf(0)) && "phoneme/t".equals(cat.soundOf(2)), "cat's consonants are not the standard ones");

				CaptionLine line = CaptionLine.building(word.tiles(), 3, Set.of(3));
				check(line.state(line.pieces().size() - 1, 0) == CaptionLine.State.SILENT, "the silent e is not greyed in the caption");

				ServerPlayer player = session.player();
				check(Rooms.startRound(player, word), "no round could be built for wake");
				Room room = Rooms.of(player);
				// Wake has no family: it is built whole. Built, its a says a_long, because it is in wake there.
				Hands.solve(player, room);
				BlockPos a = room.gaps().get(1);
				player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(a), Direction.SOUTH, a, false));
				check(Sounds.recent().stream().anyMatch(p -> p.key().equals("phoneme/a_long") && a.equals(p.at())),
					"the a in the row in wake does not say a_long");

				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(room.button()), Direction.SOUTH, room.button(), false));
			});

			Waits.until(session, 600, "wake was never read out", () -> Narrator.log(session.player()).contains("outcome"));
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				int from = log.lastIndexOf("light:0");
				List<String> blend = log.subList(from, log.indexOf("outcome"));
				int at = 0;
				for (String step : List.of("light:0", "clip:phoneme/w", "light:1", "clip:phoneme/a_long", "light:2",
						"clip:phoneme/k", "silent:3", "magic_e:1", "caption:w · A · k · e", "clip:word/wake")) {
					int found = blend.subList(at, blend.size()).indexOf(step);
					check(found >= 0, "'" + step + "' is not in the blend after position " + at + ": " + blend);
					at += found + 1;
				}
				check(!blend.contains("clip:phoneme/e"), "the silent e was said: " + blend);
				check(!blend.contains("clip:phoneme/a"), "the a was said short in wake: " + blend);
			});

			// Cat, sounded out: c, the a from cat itself, t, then cat.
			int[] outcomes = new int[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				outcomes[0] = (int) Narrator.log(player).stream().filter("outcome"::equals).count();
				check(Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow()),
					"no round for cat");
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(room.button()), Direction.SOUTH, room.button(), false));
			});
			Waits.until(session, 600, "cat was never read out",
				() -> Narrator.log(session.player()).stream().filter("outcome"::equals).count() > outcomes[0]);
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				int from = log.lastIndexOf("light:0");
				List<String> blend = log.subList(from, log.lastIndexOf("outcome"));
				List<String> clips = blend.stream().filter(step -> step.startsWith("clip:")).toList();
				check(clips.equals(List.of("clip:phoneme/c", "clip:blend/cat/1", "clip:phoneme/t", "clip:word/cat")),
					"cat did not blend as c, cat's own a, t, cat: " + clips);
				// A loose a, broken off the floor, is still the standard short a.
				check("phoneme/a".equals(LetterBlock.phoneme(LearningBlocks.LETTER.showing(Letter.A))), "a loose a is not phoneme/a");
			});
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
