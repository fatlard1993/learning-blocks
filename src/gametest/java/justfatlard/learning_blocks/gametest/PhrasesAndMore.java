package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Letters;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.review.Review;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Sounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Round E: two-word phrases (big pig: one big pig; six cod: six of them), a tricky word (the room
 * says it, the child finds its card, and nothing is sounded out), big and little letters both ways
 * round (and the letters level not done until every pair is mastered, unless done before capitals),
 * and a plural (cats: three cats; dogs said with a /z/). Full run: {@code phonics-phrase-round.png},
 * {@code phonics-phrase-big-pig.png}, {@code phonics-tricky-round.png}, {@code phonics-case-big.png},
 * {@code phonics-case-little.png}, {@code phonics-plural-round.png}, {@code phonics-plural-cats.png},
 * {@code phonics-review-tricky.png}, {@code phonics-review-capitals.png}.
 */
public final class PhrasesAndMore implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				// Said here as well: a world that will not close after a failure would otherwise swallow it.
				org.slf4j.LoggerFactory.getLogger("phonics-gametest").error("PhrasesAndMore failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);

		// A phrase: big pig.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startPhrase(player, word("big"), word("pig")), "no phrase round for big pig");
			Room room = Rooms.of(player);
			check(room.phraseAdjective().text().equals("big") && room.puzzle().target().text().equals("pig"), "the phrase round is not big pig");
			look(player, room);
		});
		session.waitTicks(20);
		shoot(session, "phonics-phrase-round");
		solve(session);
		Waits.until(session, 600, "big pig never happened", () -> Narrator.log(session.player()).contains("phrase:big_pig"));
		session.waitTicks(30);
		shoot(session, "phonics-phrase-big-pig");
		waitNext(session);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			List<String> log = Narrator.log(player);
			check(log.contains("phrase:big_pig"), "big pig did not happen as a phrase: " + log);
			check(log.contains("clip:word/big") && log.contains("phrase_said"), "the phrase was not read out as two words: " + log);
			check(Rooms.of(player).creatures().stream().anyMatch(e -> e.getType() == EntityTypes.PIG && e instanceof LivingEntity living
				&& living.getAttribute(Attributes.SCALE).getBaseValue() > 1.5), "no big pig");
		});

		// Six cod: six of them.
		session.onServer(server -> check(Rooms.startPhrase(session.player(), word("six"), word("cod")), "no phrase round for six cod"));
		solveAndWait(session);
		session.waitTicks(40);
		session.onServer(server -> {
			long cod = Rooms.of(session.player()).creatures().stream().filter(e -> e.getType() == EntityTypes.COD).count();
			check(cod >= 6, "six cod brought " + cod);
			clearCreatures(session.player());
		});

		// A tricky word: said, not sounded out; its card found among others.
		String[] wrong = new String[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startTricky(player, tricky("the")), "no tricky round for the");
			Room room = Rooms.of(player);
			check(room.gaps().isEmpty() && room.choices().size() >= 2, "a tricky round has a gap, or too few cards");
			check(room.choices().values().stream().allMatch(Word::tricky), "a card is not a tricky word");
			wrong[0] = room.choices().values().stream().filter(w -> !w.text().equals("the")).findFirst().orElseThrow().text();
			look(player, room);
		});
		Waits.until(session, 300, "the tricky word was never said", () -> Narrator.log(session.player()).contains("clip:word/the")
			&& !Narrator.isPlaying(session.player()));
		shoot(session, "phonics-tricky-round");
		session.onServer(server -> click(session.player(), wrong[0]));
		Waits.until(session, 300, "the wrong card never said its word", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("clip:word/" + wrong[0]) > log.lastIndexOf("picked:" + wrong[0]) && !Rooms.of(session.player()).busy();
		});
		session.onServer(server -> click(session.player(), "the"));
		Waits.until(session, 300, "the right card was never praised", () -> {
			List<String> log = Narrator.log(session.player());
			return log.lastIndexOf("clip:phrase/well_done") > log.lastIndexOf("picked:the") && log.lastIndexOf("picked:the") >= 0;
		});
		session.onServer(server -> {
			List<String> log = Narrator.log(session.player());
			List<String> since = log.subList(log.lastIndexOf("picked:the"), log.size());
			check(since.stream().noneMatch(step -> step.startsWith("clip:phoneme/") || step.startsWith("light:0")), "a tricky word was sounded out: " + since);
			check(Progress.of(session.player()).clean(tricky("the")) == 0, "a right card after a wrong one counted clean");
			// The tab shows the tricky words open at the reader's stage: the from stage 2.
			Progress.of(session.player()).setStage(session.player(), 2, java.util.Set.of());
			Review.open(session.player(), Review.Tab.TRICKY, null, false);
			check(Review.trickyShown(session.player()).contains("the"), "the Tricky tab does not show the");
		});
		session.waitTicks(10);
		shoot(session, "phonics-review-tricky");
		session.onServer(server -> Review.press(session.player(), "close"));

		// Big and little, both ways.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			for (Letter letter : Letters.all()) progress.setLetterHits(player, letter.grapheme(), Letters.knownAfter());
			check(Rooms.startCaseRound(player, Letter.A, true), "no big-and-little round for a");
			Room room = Rooms.of(player);
			check(room.casePrompt() == Letter.A && room.letterTarget() == Letter.CAP_A, "the round does not show a and ask for A");
			check(room.puzzle().shelf().size() == 3 && room.puzzle().shelf().stream().allMatch(Letter::isCapital), "the floor is not three capitals: "
				+ room.puzzle().shelf());
			look(player, room);
		});
		session.waitTicks(20);
		shoot(session, "phonics-case-big");
		solveAndWaitCase(session);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Letters.caseHits(Progress.of(player), Letter.A) == 1, "a clean big-and-little match did not count");
			check(Narrator.log(player).contains("case_link") && Narrator.log(player).contains("clip:phoneme/a_long"), "the match did not say a's name: "
				+ Narrator.log(player));
			check(Rooms.startCaseRound(player, Letter.B, false), "no little round for B");
			Room room = Rooms.of(player);
			check(room.casePrompt() == Letter.CAP_B && room.letterTarget() == Letter.B, "the round does not show B and ask for b");
			check(room.puzzle().shelf().stream().noneMatch(Letter::isCapital) && !room.puzzle().shelf().contains(Letter.D),
				"the floor is not small letters without d: " + room.puzzle().shelf());
			look(player, room);
			// A wrong one first.
			Letter other = room.puzzle().shelf().stream().filter(l -> l != Letter.B).findFirst().orElseThrow();
			check(Hands.take(player, room.home(other)), other + " would not break");
			Hands.placeInGap(player, room);
			press(player, room);
		});
		session.waitTicks(20);
		shoot(session, "phonics-case-little");
		Waits.until(session, 300, "the wrong small letter never went home", () -> !Rooms.of(session.player()).busy()
			&& Rooms.of(session.player()).inGap() == null);
		solveAndWaitCase(session);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			check(Letters.caseHits(progress, Letter.B) == 0, "a match after a miss counted as clean");
			// The letters level waits for every pair.
			progress.setStage(player, 0, Stages.from(1));
			for (Letter letter : Letters.all()) progress.setLetterHits(player, letter.grapheme(), Letters.knownAfter());
			check(Stages.current(progress) == 0 && !Letters.complete(progress), "every sound known and no pairs, and the letters level is done");
			for (Letter letter : Letters.all()) progress.setCaseHitsForTests(player, letter, Letters.knownAfter());
			check(Letters.complete(progress) && Stages.current(progress) != 0, "every pair mastered, and the letters level is not done");
			progress.setStage(player, 1, Stages.from(1));
			Review.open(player, Review.Tab.LETTERS, null, false);
			Review.press(player, "caps");
		});
		session.waitTicks(10);
		shoot(session, "phonics-review-capitals");
		session.onServer(server -> {
			Review.press(session.player(), "caps");
			Review.press(session.player(), "close");
			// A reader who finished the letters before capitals keeps them done.
			CompoundTag saved = new CompoundTag();
			CompoundTag hits = new CompoundTag();
			for (Letter letter : Letters.all()) hits.putInt(letter.grapheme(), 3);
			saved.put("letter_hits", hits);
			saved.putInt("stage_floor", 0);
			ServerPlayer player = session.player();
			Progress.setForTests(player, Progress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
			Progress now = Progress.of(player);
			check(now.casesWaived() && Stages.current(now) != 0, "a reader past the letters level before capitals was sent back: " + Stages.describe(now));
			// Two saves without memory each get their own: the default is not one object shared and changed by all.
			Progress other = Progress.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
			check(other.memory() != now.memory() && !other.casesWaived(), "saves without memory share one");
		});

		// A plural: cats.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress.of(player).read(player, word("cat").id().toString());
			Word cats = plural("cats");
			check(Rooms.startPlural(player, cats), "no plural round for cats");
			Room room = Rooms.of(player);
			check(room.puzzle().gaps().equals(List.of(0, 3)) && room.puzzle().shelf().contains(Letter.S), "the plural round's gaps or s are wrong");
			check("phoneme/z".equals(plural("dogs").phonemeOf(3)) && "phoneme/s".equals(cats.phonemeOf(3)), "dogs does not say /z/, or cats /s/");
			check(Sounds.isRecorded("word/dogs") && Sounds.isRecorded("word/cats"), "the plurals are not recorded");
			look(player, room);
		});
		session.waitTicks(20);
		shoot(session, "phonics-plural-round");
		solve(session);
		Waits.until(session, 600, "cats never came", () ->
			Rooms.of(session.player()).creatures().stream().filter(e -> e.getType() == EntityTypes.CAT).count() >= 3);
		session.waitTicks(20);
		session.onServer(server -> {
			long cats = Rooms.of(session.player()).creatures().stream().filter(e -> e.getType() == EntityTypes.CAT).count();
			check(cats >= 3, "cats brought " + cats + " cats");
		});
		shoot(session, "phonics-plural-cats");
		session.command("/learn leave");
		Waits.home(session);
	}

	/** Solve the round and press; on once the next round has come, which is after everything this one did. */
	private static void solveAndWait(Smoke.Session session) {
		solve(session);
		waitNext(session);
	}

	private static final Puzzle[] was = new Puzzle[1];

	private static void solve(Smoke.Session session) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			was[0] = room.puzzle();
			Hands.solve(player, room);
			press(player, room);
		});
	}

	private static void waitNext(Smoke.Session session) {
		Waits.until(session, 900, "the round was never done", () -> Rooms.of(session.player()).puzzle() != was[0] && !Rooms.of(session.player()).busy());
	}

	private static void solveAndWaitCase(Smoke.Session session) {
		solveAndWait(session);
	}

	private static void clearCreatures(ServerPlayer player) {
		for (Entity creature : List.copyOf(Rooms.of(player).creatures())) creature.discard();
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
		throw new AssertionError("no card for " + text);
	}

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

	private static Word tricky(String text) {
		return Words.tricky().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no tricky word " + text));
	}

	private static Word plural(String text) {
		return Words.plurals().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no plural " + text));
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
