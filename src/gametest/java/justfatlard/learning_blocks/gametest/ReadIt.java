package justfatlard.learning_blocks.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.review.ProgressView;
import justfatlard.learning_blocks.reward.Memory;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A read-it round: cat, already read, shown whole on the board with no gap, no picture and nothing
 * said but "What does it say?"; two or three pictures on the floor that sound alike. A wrong one says
 * its own word, wiggles, and the round stays; it is remembered, with the sounds mixed up. The right
 * one: now cat is sounded out, and it happens. A word missed is due again sooner than a solid one.
 * Only words with a clear picture are read-it rounds or choices (sat is not). A missed word and
 * sound get a gentle star on My words and the Letters tab. The progress screen shows the mix-up, and
 * the export writes it as JSON. Full run:
 * {@code phonics-read-it-round.png}, {@code phonics-progress-screen.png}, {@code phonics-practise-stars.png}.
 */
public final class ReadIt implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try {
			Smoke.run(context, "learning-blocks-justfatlard", ReadIt::run);
		} finally {
			PrizeConfig.extrasOffForTests(true);
		}
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		String[] wrong = new String[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			PrizeConfig.useReadIt(true, 5, 0);
			Progress progress = Progress.of(player);
			for (String text : List.of("cat", "bat", "hat", "mat")) progress.read(player, word(text).id().toString());
			progress.read(player, word("sat").id().toString());
			check(!Rooms.startReadIt(player, word("sat")), "sat, which has no clear picture, was made a read-it round");
			check(Rooms.startReadIt(player, word("cat")), "no read-it round for cat");
			Room room = Rooms.of(player);
			check(room.readIt() && room.puzzle().target().text().equals("cat"), "the round is not a read-it round for cat");
			check(room.gaps().isEmpty(), "a read-it round has a gap: " + room.gaps());
			check(room.picture() == null, "a read-it round shows the picture over the board");
			check(room.choices().size() >= 2 && room.choices().size() <= 3, "a read-it round has " + room.choices().size() + " pictures to pick");
			check(room.choices().values().stream().anyMatch(w -> w.text().equals("cat")), "cat is not among the pictures");
			check(room.choices().values().stream().allMatch(Word::clearPicture), "a read-it picture is not a clear one: " + room.choices().values());
			check(word("cat").clearPicture() && !word("sat").clearPicture() && !word("ham").clearPicture(), "the clear-picture flags are not as written");
			wrong[0] = room.choices().values().stream().filter(w -> !w.text().equals("cat")).findFirst().orElseThrow().text();
			look(player, room);
		});
		Waits.until(session, 300, "the read-it prompt was never said",
			() -> Narrator.log(session.player()).contains("clip:phrase/what_does_it_say") && !Narrator.isPlaying(session.player()));
		session.onServer(server -> {
			List<String> log = Narrator.log(session.player());
			List<String> round = log.subList(log.lastIndexOf("read_it:cat"), log.size());
			check(!round.contains("clip:word/cat") && !round.contains("light:0") && !round.contains("clip:phrase/make_the_word"),
				"the word was said before the child acted: " + round);
		});
		shoot(session, "phonics-read-it-round");

		// Wrong: the picked picture says its word, and the round stays.
		session.onServer(server -> click(session.player(), wrong[0]));
		Waits.until(session, 300, "the wrong picture never said its word", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("clip:word/" + wrong[0]) > log.lastIndexOf("picked:" + wrong[0]) && !Rooms.of(session.player()).busy();
		});
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			List<String> log = Narrator.log(player);
			List<String> since = log.subList(log.lastIndexOf("picked:" + wrong[0]), log.size());
			check(room.readIt() && room.puzzle().target().text().equals("cat"), "a wrong pick ended the round");
			check(!since.contains("outcome") && !since.contains("light:0"), "a wrong pick did something, or sounded cat out: " + since);
			Memory memory = Progress.of(player).memory();
			Memory.Item cat = memory.item(Memory.wordKey(word("cat").id().toString()));
			check(cat != null && cat.misses() == 1 && cat.mixups().contains(wrong[0]) && cat.box() == 1,
				"the miss was not remembered: " + (cat == null ? null : cat.mixups()));
			String pair = pair("c", wrong[0].substring(0, 1));
			check(memory.confusions().stream().anyMatch(e -> e.getKey().equals(pair)), "the mix-up " + pair + " was not recorded: " + memory.confusions());
		});

		// Right: now it is sounded out, and it happens.
		session.onServer(server -> click(session.player(), "cat"));
		Waits.until(session, 600, "cat was never read out after the right pick", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("outcome") > log.lastIndexOf("picked:cat") && log.lastIndexOf("picked:cat") >= 0;
		});
		session.onServer(server -> {
			ServerPlayer player = session.player();
			List<String> log = Narrator.log(player);
			List<String> since = log.subList(log.lastIndexOf("picked:cat"), log.size());
			int at = 0;
			for (String step : List.of("light:0", "clip:phoneme/c", "clip:word/cat", "outcome")) {
				int found = since.subList(at, since.size()).indexOf(step);
				check(found >= 0, "'" + step + "' is not in the right pick's telling after " + at + ": " + since);
				at += found + 1;
			}
			// Missed, then right: due again sooner than a word got right first time, again and again.
			Progress progress = Progress.of(player);
			for (int i = 0; i < 4; i++) progress.wordRight(player, word("hat"), true, List.of());
			Memory memory = progress.memory();
			long cat = memory.dueIn(Memory.wordKey(word("cat").id().toString()));
			long hat = memory.dueIn(Memory.wordKey(word("hat").id().toString()));
			check(cat < hat, "a missed word is not due sooner than a solid one: " + cat + " against " + hat);
			check(memory.mastery(Memory.wordKey(word("hat").id().toString()), Progress.masteredAt()) == Memory.Mastery.SOLID, "hat is not solid");

			// For the child: a gentle star on what is due, on My words and the Letters tab.
			progress.roundBegan(player);
			progress.roundBegan(player);
			justfatlard.learning_blocks.review.Review.open(player, justfatlard.learning_blocks.review.Review.Tab.WORDS, null, false);
			check(justfatlard.learning_blocks.review.Review.starredShown(player).contains(word("cat").id().toString()),
				"cat, missed, is not starred on My words: " + justfatlard.learning_blocks.review.Review.starredShown(player));
			check(!justfatlard.learning_blocks.review.Review.starredShown(player).contains(word("hat").id().toString()), "hat, solid, is starred");
			justfatlard.learning_blocks.review.Review.open(player, justfatlard.learning_blocks.review.Review.Tab.LETTERS, null, false);
			check(justfatlard.learning_blocks.review.Review.starredShown(player).contains("c"), "c, mixed up, is not starred on the Letters tab: "
				+ justfatlard.learning_blocks.review.Review.starredShown(player));

			// The grown-ups' view: the mix-up is there.
			check(ProgressView.open(player, player), "the progress screen did not open");
			String line = "mixes up " + pair("c", wrong[0].substring(0, 1)).replace("|", " and ") + ": 1 time";
			check(ProgressView.lastShown(player).contains(line), "the progress screen does not show '" + line + "': " + ProgressView.lastShown(player));
		});
		session.waitTicks(10);
		shoot(session, "phonics-progress-screen");
		session.onServer(server -> justfatlard.learning_blocks.review.Review.open(session.player(), justfatlard.learning_blocks.review.Review.Tab.WORDS, null, false));
		session.waitTicks(10);
		shoot(session, "phonics-practise-stars");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Path file = ProgressView.export(player);
			check(file != null && Files.exists(file), "the export was not written");
			try {
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				check(json.getAsJsonArray("mix_ups").size() >= 1 && json.has("items") && json.has("this_week"), "the export is missing things: " + json.keySet());
			} catch (java.io.IOException e) {
				throw new AssertionError("the export could not be read", e);
			}
			PrizeConfig.extrasOffForTests(true);
		});
		session.command("/learn leave");
		Waits.until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level()));
	}

	private static String pair(String a, String b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	private static void click(ServerPlayer player, String text) {
		Room room = Rooms.of(player);
		for (Map.Entry<UUID, Word> entry : room.choices().entrySet()) {
			if (!entry.getValue().text().equals(text)) continue;
			Entity box = player.level().getEntity(entry.getKey());
			check(box != null, text + "'s click box is gone");
			Rooms.clickEntity(player, player.level(), box);
			return;
		}
		throw new AssertionError("no picture for " + text);
	}

	/** From where a child stands, looking down at the pictures and up at the board. */
	private static void look(ServerPlayer player, Room room) {
		Vec3 from = room.spawn();
		Vec3 at = Vec3.atCenterOf(room.origin().offset(0, 1, -3));
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo(player.level(), from.x, from.y, from.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
