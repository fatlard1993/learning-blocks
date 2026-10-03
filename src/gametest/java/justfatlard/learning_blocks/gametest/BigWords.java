package justfatlard.learning_blocks.gametest;

import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Sampler;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Round H: big words. catfish is two chunks, cat|fish, a compound; robin, rob|in, is not. Stage 14
 * is in two bands, the compounds first. A chunk round for catfish: the row in two parts with a
 * column between them, fish to build; built, each chunk is sounded out and said as its own word,
 * then the chunks slide together and catfish is said. The whole word, both chunks to build, keeps
 * the column. hedge|hog has its dge on one tile; cupcake, eight columns with the gap, stays clear of
 * the button with its picture over it. A big word read-it round; the sampler's chunk kind. Full run:
 * {@code phonics-big-chunk.png}, {@code phonics-big-joined.png}, {@code phonics-big-whole.png},
 * {@code phonics-big-hedgehog.png}, {@code phonics-big-cupcake.png}, {@code phonics-big-read-it.png}.
 */
public final class BigWords implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("phonics-gametest").error("BigWords failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		// The words, and their bands.
		session.onServer(server -> {
			check(List.of(3, 3).equals(Words.chunks(word("catfish"))) && Words.compound(word("catfish")), "catfish is not cat|fish, a compound");
			check(List.of(3, 2).equals(Words.chunks(word("robin"))) && !Words.compound(word("robin")), "robin is not rob|in, two syllables");
			check(Words.chunks(word("cat")) == null, "cat has chunks");
			check(word("hedgehog").letters().contains(Letter.DGE), "hedgehog has no dge tile");
			List<Stages.Band> bands = Stages.bandsOf(14);
			check(bands.size() == 2, "stage 14 is in " + bands.size() + " bands");
			check(bands.getFirst().words().stream().allMatch(Words::compound), "14a is not all compounds: " + bands.getFirst().words());
			for (String text : List.of("robin", "puffin", "lobster", "chipmunk")) check(bands.get(1).contains(word(text)), text + " is not in 14b");
			check(Sampler.targets(Sampler.Kind.CHUNK).contains("catfish"), "the sampler can't make a chunk round of catfish");
		});

		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress.of(player).setBand(player, Stages.parse("14a"));
			look(player, Rooms.of(player));
		});

		// Catfish, fish to build: two chunks with a column between them.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startChunk(player, word("catfish"), 3), "no chunk round for catfish");
			Room room = Rooms.of(player);
			Puzzle puzzle = room.puzzle();
			check(puzzle.gaps().equals(List.of(3, 4, 5)), "the gaps are not fish's: " + puzzle.gaps());
			List<BlockPos> row = rowOf(room);
			check(row.get(3).getX() - row.get(2).getX() == 2, "no column between cat and fish: " + row);
			check(row.get(1).getX() - row.get(0).getX() == 1 && row.get(5).getX() - row.get(4).getX() == 1, "a chunk's tiles are not together: " + row);
			check(puzzle.shelf().containsAll(List.of(Letter.F, Letter.I, Letter.SH)), "the floor lacks fish's tiles: " + puzzle.shelf());
		});
		session.waitTicks(30);
		shoot(session, "phonics-big-chunk");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			List<BlockPos> gaps = room.gaps();
			List<Letter> fish = List.of(Letter.F, Letter.I, Letter.SH);
			for (int i = 0; i < 3; i++) player.level().setBlock(gaps.get(i), LearningBlocks.LETTER.showing(fish.get(i)), 3);
			mark(player);
			press(player, room);
		});
		Waits.until(session, 600, "catfish was never joined and said", () -> since(session.player()).contains("clip:word/catfish"));
		session.onServer(server -> {
			List<String> log = since(session.player());
			int cat = log.indexOf("chunk:cat");
			int fish = log.indexOf("chunk:fish");
			int joined = log.indexOf("joined");
			int whole = log.indexOf("clip:word/catfish");
			check(cat >= 0 && cat < fish && fish < joined && joined < whole, "not cat, fish, joined, catfish in that order: " + log);
			check(log.indexOf("clip:word/cat") < fish && log.contains("clip:word/fish"), "the chunks were not said as words: " + log);
			List<BlockPos> row = rowOf(Rooms.of(session.player()));
			for (int i = 1; i < row.size(); i++) check(row.get(i).getX() - row.get(i - 1).getX() == 1, "the chunks did not slide together: " + row);
		});
		shoot(session, "phonics-big-joined");
		Waits.until(session, 600, "the catfish round never moved on", () -> !Rooms.of(session.player()).busy());

		// The whole word, both chunks to build: the column is still there, as a hint.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startRound(player, word("catfish"), Puzzle.Kind.WHOLE), "no whole-word round for catfish");
			Room room = Rooms.of(player);
			check(room.gaps().size() == 6, "catfish whole has " + room.gaps().size() + " gaps");
			List<BlockPos> row = rowOf(room);
			check(row.get(3).getX() - row.get(2).getX() == 2, "the whole word lost its chunk column");
		});
		session.waitTicks(30);
		shoot(session, "phonics-big-whole");

		// Hedge|hog: hedge to build, its dge one tile.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startChunk(player, word("hedgehog"), 0), "no chunk round for hedge in hedgehog");
			check(Rooms.of(player).puzzle().shelf().contains(Letter.DGE), "no dge tile on the floor");
		});
		session.waitTicks(30);
		shoot(session, "phonics-big-hedgehog");

		// Cupcake: eight columns, clear of the button, the picture over the word.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startChunk(player, word("cupcake"), 3), "no chunk round for cake in cupcake");
			Room room = Rooms.of(player);
			List<BlockPos> row = rowOf(room);
			check(row.getLast().getX() < room.button().getX(), "cupcake runs into the button: " + row.getLast() + " vs " + room.button());
			check(row.getFirst().getX() > room.origin().getX() - 6, "cupcake runs into the wall: " + row.getFirst());
			check(room.pictureHit() != null, "cupcake has no picture");
			net.minecraft.world.entity.Entity hit = ((ServerLevel) player.level()).getEntity(room.pictureHit());
			check(hit != null && hit.getY() > row.getFirst().getY() + 0.5, "cupcake's picture is not over the word");
		});
		session.waitTicks(30);
		shoot(session, "phonics-big-cupcake");

		// A read-it round with a big word.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			for (Word word : Stages.at(14)) progress.read(player, word.id().toString());
			check(Rooms.startReadIt(player, word("catfish")), "no read-it round for catfish");
			check(Rooms.of(player).choices().size() >= 2, "a big-word read-it has " + Rooms.of(player).choices().size() + " pictures");
		});
		session.waitTicks(30);
		shoot(session, "phonics-big-read-it");
		session.command("/learn leave");
		session.waitTicks(30);
	}

	/** The row's positions, a tile at a time, as the board shows them. */
	private static List<BlockPos> rowOf(Room room) {
		return room.row();
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void mark(ServerPlayer player) {
		Narrator.note(player, "mark");
	}

	private static List<String> since(ServerPlayer player) {
		List<String> log = Narrator.log(player);
		return log.subList(log.lastIndexOf("mark") + 1, log.size());
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void look(ServerPlayer player, Room room) {
		Vec3 from = room.spawn();
		Vec3 at = Vec3.atCenterOf(room.origin().offset(0, 2, -4));
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))) + 8;
		player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
