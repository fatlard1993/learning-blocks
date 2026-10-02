package justfatlard.phonics.gametest;

import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.LetterBlock;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Tiles;
import justfatlard.phonics.sound.Captions;
import justfatlard.phonics.sound.Sounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A letter says its sound when it is placed, and the room reads a word out in the right order.
 *
 * <p>Placing is checked twice: a tile put down in the overworld the ordinary way, through the item,
 * and a tile put into the board's gap. Both must play {@code phoneme/<x>} from the block itself.
 *
 * <p>Then the target word is sounded out, and the narrator's log must show, for every tile, the
 * tile lit before its phoneme is heard, the next tile only after, then the whole word, then the
 * word happening. The board is checked while the blend is running too - the tile being said is the
 * lit one - and two pictures are taken: one mid-blend, one on the caption that follows.
 */
public final class BlendNarration implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			session.onServer(server -> {
				check(Captions.size() >= 30, "only " + Captions.size() + " captions loaded");
				check(Sounds.isRecorded("phoneme/c") && Sounds.isRecorded("phrase/well_done"),
					"the recordings were not found, so the placeholders are playing");

				// A tile placed the ordinary way, out in the world.
				ServerPlayer player = session.player();
				player.setGameMode(GameType.CREATIVE);
				player.setItemInHand(InteractionHand.MAIN_HAND, Tiles.stack(Letter.Z));
				BlockPos under = player.blockPosition().offset(2, -1, 0);
				BlockPos placed = under.above();
				player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), Direction.UP, under, false)));
				check(player.level().getBlockState(placed).is(justfatlard.phonics.Phonics.LETTER), "the tile was not placed");
				check(played("phoneme/z", placed), "placing a z tile did not say phoneme/z from it: " + Sounds.recent());
				player.level().removeBlock(placed, false);
				player.getInventory().clearContent();
			});

			session.command("/phonics");
			// The first prompt waits for the client to finish loading into the room, then a moment.
			session.onServer(server -> check(Rooms.of(session.player()).promptHeld(), "the first prompt was not held for the client"));
			Waits.until(session, 200, "the held prompt was never said", () -> !Rooms.of(session.player()).promptHeld()
				&& Narrator.log(session.player()).contains("clip:phrase/make_the_word"));

			String[] tiles = new String[3];
			String[] sounds = new String[3];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(room != null && room.puzzle() != null, "no puzzle in the room");
				List<String> target = room.puzzle().target().tiles();
				for (int i = 0; i < 3; i++) tiles[i] = target.get(i);
				// Each tile as the word says it: a vowel cut from the word's own recording.
				for (int i = 0; i < 3; i++) sounds[i] = room.puzzle().target().soundOf(i);
				Hands.solve(player, room);
				check(played("phoneme/" + tiles[0], room.gap()), "a tile put in the gap did not say its sound from there");
				click(player, room.button());
			});

			// Mid-blend: the second letter being said, its tile lit and its neighbours not.
			Waits.until(session, 200, "the blend never reached the second letter",
				() -> Narrator.log(session.player()).contains("clip:" + sounds[1]));
			// A few ticks in, so the client has redrawn the tile it was sent as well as the caption.
			session.waitTicks(5);
			session.onServer(server -> {
				Room room = Rooms.of(session.player());
				BlockPos gap = room.gap();
				check(lit(session.player(), gap.east()), "the tile being said is not lit");
				check(!lit(session.player(), gap), "the tile already said is still lit");
			});
			shoot(session, "phonics-mid-blend");

			// The caption after the word has happened: "You did it!" or the word itself.
			Waits.until(session, 400, "the word never happened",
				() -> Narrator.log(session.player()).contains("outcome"));
			Waits.until(session, 200, "the room never said well done",
				() -> Narrator.log(session.player()).contains("clip:phrase/well_done"));
			session.waitTicks(22);
			shoot(session, "phonics-caption");

			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				String target = String.join("", tiles);
				int last = -1;
				for (int i = 0; i < 3; i++) {
					last = after(log, "light:" + i, last);
					last = after(log, "clip:" + sounds[i], last);
				}
				last = after(log, "clip:word/" + target, last);
				last = after(log, "outcome", last);
				after(log, "clip:phrase/well_done", last);
				check(log.contains("caption:" + tiles[0] + " · " + tiles[1]), "the caption did not build up the letters: " + log);
				check(log.contains("caption:" + target), "the caption never snapped to the whole word: " + log);
			});
		});
	}

	/** Where {@code entry} is in the log after {@code from}, failing if it is not. */
	private static int after(List<String> log, String entry, int from) {
		for (int i = from + 1; i < log.size(); i++) if (log.get(i).equals(entry)) return i;
		throw new AssertionError("'" + entry + "' is not in the log after position " + from + ": " + log);
	}

	private static boolean played(String key, BlockPos at) {
		return Sounds.recent().stream().anyMatch(p -> p.key().equals(key) && at.equals(p.at()));
	}

	private static boolean lit(ServerPlayer player, BlockPos pos) {
		var state = player.level().getBlockState(pos);
		return state.getBlock() instanceof LetterBlock && state.getValue(LetterBlock.LIT);
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		check(pos != null, "nothing to click");
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void shoot(Smoke.Session session, String name) {
		session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
