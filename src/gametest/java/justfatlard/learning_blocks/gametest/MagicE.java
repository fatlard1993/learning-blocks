package justfatlard.learning_blocks.gametest;

import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Outcome;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Stages 9 and 10, and the other mods' words. Cake, built: c, its own long a, k, then the silent e
 * lighting up with the a ({@code magic_e:1}) while the caption shows the a as its capital, then the
 * word, and a cake appears. Boat: a vowel-team round, _oat, with the oa tile in the row and b, g
 * and c on the floor. A word needing a mod that is not here is left out (the test pack's zob, and
 * poop and cone without their mods), and a word preferring a missing mod's thing falls back to
 * vanilla (the test pack's zub, bean). Full run: {@code phonics-magic-e-blend.png},
 * {@code phonics-vowel-team-round.png}, {@code phonics-outcome-cake.png}, {@code phonics-outcome-goat.png}.
 */
public final class MagicE implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);

			// The mods' words: left out without the mod, or back to vanilla.
			session.onServer(server -> {
				check(Words.all().stream().noneMatch(w -> w.id().toString().equals("phonics-gametest:zob")), "a word needing a missing mod was loaded");
				check(Words.all().stream().anyMatch(w -> w.text().equals("poop")) == FabricLoader.getInstance().isModLoaded("poopsmith"),
					"poop is loaded exactly when poopsmith is");
				check(Words.all().stream().anyMatch(w -> w.text().equals("cone")) == FabricLoader.getInstance().isModLoaded("lets-cook-justfatlard"),
					"cone is loaded exactly when lets-cook is");
				Word zub = word("zub");
				check(zub.outcome().usable() && zub.picture().is(Items.APPLE), "a word preferring a missing mod's item did not fall back: " + zub.picture());
				if (!FabricLoader.getInstance().isModLoaded("cloud-kingdoms-justfatlard")) {
					check(word("bean").picture().is(Items.COCOA_BEANS), "bean did not fall back to cocoa beans: " + word("bean").picture());
					check(((Outcome.Prefer) word("bean").outcome()).chosen() instanceof Outcome.GiveItem give
						&& give.item().getPath().equals("cocoa_beans"), "bean's outcome is not the vanilla one");
				}
				check(word("egg").stage() == 6 && word("egg").letters().get(1) == Letter.GG, "egg is not a stage 6 word with the gg tile");
			});

			// Cake: the magic e.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, word("cake")), "no round for cake");
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				press(player, room);
			});
			Waits.until(session, 400, "the magic e never lit", () -> Narrator.log(session.player()).contains("magic_e:1"));
			session.waitTicks(4);
			shoot(session, "phonics-magic-e-blend");
			Waits.until(session, 600, "cake was never read out", () -> {
				List<String> log = Narrator.log(session.player());
				return log.lastIndexOf("outcome") > log.lastIndexOf("light:0");
			});
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				List<String> blend = log.subList(log.lastIndexOf("light:0"), log.lastIndexOf("outcome"));
				int at = 0;
				for (String step : List.of("clip:phoneme/c", "clip:blend/cake/1", "clip:phoneme/k", "silent:3", "magic_e:1",
						"caption:c · A · k · e", "clip:word/cake")) {
					int found = blend.subList(at, blend.size()).indexOf(step);
					check(found >= 0, "'" + step + "' is not in cake's blend after position " + at + ": " + blend);
					at += found + 1;
				}
				check(!blend.contains("clip:phoneme/e") && !blend.contains("clip:phoneme/a"), "cake's e was said, or its a said short: " + blend);
			});
			session.waitTicks(30);
			session.onServer(server -> {
				Room room = Rooms.of(session.player());
				check(room.placed().stream().anyMatch(pos -> session.player().level().getBlockState(pos).is(Blocks.CAKE)
					|| !session.player().level().getBlockState(pos).isAir()), "cake put nothing down: " + room.placed());
			});
			shoot(session, "phonics-outcome-cake");

			// Boat: a vowel-team round.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, word("boat"), Puzzle.Kind.FIRST), "no first-sound round for boat");
				Room room = Rooms.of(player);
				Puzzle puzzle = room.puzzle();
				check(puzzle.target().letters().get(1) == Letter.OA, "boat's middle tile is not oa");
				check(puzzle.shelf().containsAll(List.of(Letter.B, Letter.G, Letter.C)), "the _oat floor is not b, g and c: " + puzzle.shelf());
				BlockPos oa = room.gap().east();
				check(player.level().getBlockState(oa).toString().contains("letter=oa"), "the board does not show oa: " + player.level().getBlockState(oa));
			});
			session.waitTicks(10);
			shoot(session, "phonics-vowel-team-round");

			// A new creature: a baby goat.
			session.onServer(server -> Rooms.runOutcome(session.player(), word("goat")));
			session.waitTicks(30);
			session.onServer(server -> check(Rooms.of(session.player()).creatures().stream()
				.anyMatch(e -> e.getType() == net.minecraft.world.entity.EntityTypes.GOAT), "goat brought no goat"));
			shoot(session, "phonics-outcome-goat");

			session.command("/learn leave");
			session.waitTicks(20);
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
