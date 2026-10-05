package justfatlard.learning_blocks.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.bob.BobScene;
import justfatlard.learning_blocks.bob.Goal;
import justfatlard.learning_blocks.bob.Goals;
import justfatlard.learning_blocks.bob.Instruction;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.BobRound;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.review.ProgressView;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Round F: sentences with Bob. Bob reads literally: put box in cat puts the box on the cat's head,
 * put cat on pig has the cat riding the pig, and the lid on first then the cat in gives a cat on
 * the lid. Tell Bob: the cards make the sentence, the button reads it back and Bob does it; wrong,
 * "not quite" and the cards fly home; right, the sentence counts towards mastery. What's wrong: the
 * sentence is already there and done wrong; one card swapped and Bob redoes it. Two sentences, the
 * lid first: the cat sits on the lid; in order, right. Silly sentences:
 * anything at all, and Bob does it, counting for nothing. A word to fix: cot on the board under a
 * cat, the o swapped for an a. Reading together: one reader picks the goal from two, the other
 * tells Bob, and both are credited. The progress screen has a Sentences section. Full run:
 * {@code learning-bob-goal.png}, {@code learning-bob-row.png}, {@code learning-bob-box-on-cat.png}, {@code learning-bob-cat-in-box.png},
 * {@code learning-bob-cat-rides-pig.png}, {@code learning-bob-hat-on-dog.png},
 * {@code learning-bob-whats-wrong.png}, {@code learning-bob-cat-on-lid.png}, {@code learning-fix-cot.png}, {@code learning-bob-pick.png}.
 */
public final class TellBob implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("TellBob failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		// Bob reads literally.
		session.onServer(server -> {
			Goal catInBox = Goals.byId("cat-in-box");
			check(catInBox != null && catInBox.madeBy(List.of(List.of("put", "cat", "in", "box"))), "put cat in box does not make cat-in-box");
			BobScene boxInCat = catInBox.start();
			boxInCat.apply(Instruction.parse(List.of("put", "box", "in", "cat")));
			check(boxInCat.where("box") != null && boxInCat.where("box").rel() == BobScene.Rel.HEAD, "put box in cat did not put the box on the cat's head");
			BobScene riding = new BobScene(List.of("cat", "pig"));
			riding.apply(Instruction.parse(List.of("put", "cat", "on", "pig")));
			check(riding.where("cat").rel() == BobScene.Rel.RIDES, "put cat on pig is not the cat riding");
			Goal lidded = Goals.byId("cat-in-box+lid-on-box");
			check(lidded.madeBy(List.of(List.of("put", "cat", "in", "box"), List.of("put", "lid", "on", "box"))), "the recipe in order did not make it");
			check(!lidded.madeBy(List.of(List.of("put", "lid", "on", "box"), List.of("put", "cat", "in", "box"))),
				"the lid first and the cat in second still made it: the cat should be on the lid");
			check(Goals.all().stream().map(Goal::id).distinct().count() == Goals.all().size(), "two goals have one id");
			for (Goals.Mistake mistake : Goals.Mistake.values()) {
				Goals.Wrong wrong = Goals.wrong(catInBox, mistake, word -> true, new java.util.Random(1));
				check(wrong != null && wrong.mistake() == mistake && !catInBox.madeBy(List.of(wrong.row())), "no " + mistake + " mistake for cat-in-box");
			}
		});

		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			progress.setBand(player, Stages.parse("5a"));
			for (String text : List.of("cat", "box", "hat", "dog", "pig")) progress.read(player, word(text).id().toString());
			look(player, Rooms.of(player));
		});

		// Tell Bob: put cat in box. Built the wrong way round first.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startBob(player, BobRound.Mode.GOAL, Goals.byId("cat-in-box"), null), "no Tell Bob round for cat-in-box");
			BobRound bob = Rooms.of(player).bob();
			check(bob.floorWords().containsAll(List.of("put", "cat", "in", "box")), "the floor lacks a card: " + bob.floorWords());
			check(bob.floorWords().size() >= 5, "no decoy on the floor: " + bob.floorWords());
			check(bob.bob() != null && bob.onStage("cat") != null && bob.onStage("box") != null, "Bob, the cat or the box is not on the stage");
		});
		Waits.until(session, 300, "Tell Bob was never asked", () -> Narrator.log(session.player()).contains("tell_bob") && !Narrator.isPlaying(session.player()));
		shoot(session, "learning-bob-goal");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			for (String card : List.of("put", "box", "in", "cat")) check(bob.clickCard(player, card, false), "the " + card + " card would not go in");
			check(bob.rowWords().getFirst().equals(List.of("put", "box", "in", "cat")), "the row is not put box in cat: " + bob.rowWords());
			// From where the child stands, looking at the board: the row as big as the floor's cards look.
			lookAt(player, Rooms.of(player), Vec3.atCenterOf(Rooms.of(player).origin().offset(0, 3, -4)));
		});
		session.waitTicks(15);
		shoot(session, "learning-bob-row");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			look(player, Rooms.of(player));
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 600, "Bob never did put box in cat", () -> since(session.player()).contains("clip:phrase/bob_did_it"));
		session.onServer(server -> {
			BobRound bob = Rooms.of(session.player()).bob();
			BobScene.Place box = bob.acted().where("box");
			check(box != null && box.rel() == BobScene.Rel.HEAD && box.parent().equals("cat"), "the box is not on the cat's head: " + bob.acted());
			Entity cat = bob.onStage("cat");
			Entity boxShown = bob.onStage("box");
			check(boxShown.getY() > cat.getY() + 0.4, "the box is not up on the cat: box at " + boxShown.position() + ", cat at " + cat.position());
		});
		shoot(session, "learning-bob-box-on-cat");
		Waits.until(session, 400, "not quite was never said, or the cards never came home", () -> {
			BobRound bob = Rooms.of(session.player()).bob();
			return since(session.player()).contains("clip:phrase/not_quite") && !Rooms.of(session.player()).busy() && bob.rowWords().getFirst().isEmpty();
		});
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(since(player).contains("told_bob:put box in cat:wrong"), "the wrong sentence was not judged wrong: " + since(player));
			BobRound bob = Rooms.of(player).bob();
			check(bob.acted().where("box") == null, "the stage was not set back after not quite: " + bob.acted());
			check(Progress.of(player).sentenceClean("put cat in box") == 0, "a wrong sentence counted");
			for (String card : List.of("put", "cat", "in", "box")) check(bob.clickCard(player, card, false), "the " + card + " card would not go in");
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 600, "Bob never did put cat in box", () -> since(session.player()).contains("clip:phrase/bob_did_it"));
		shoot(session, "learning-bob-cat-in-box");
		Waits.until(session, 600, "put cat in box was never cheered", () -> since(session.player()).contains("clip:phrase/bob_hooray"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(since(player).contains("told_bob:put cat in box:right"), "put cat in box was not right: " + since(player));
			// Right after a miss: remembered, but not a clean one.
			var item = Progress.of(player).memory().item(Progress.sentenceKey("put cat in box"));
			check(item != null && item.attempts() >= 2 && item.misses() == 1, "put cat in box was not remembered as tried twice, missed once");
			check(Progress.of(player).sentenceClean("put cat in box") == 0, "right after a miss counted as clean");
		});
		Waits.until(session, 600, "the round never moved on", () -> !Rooms.of(session.player()).busy() && Rooms.of(session.player()).bob() == null);

		// Silly sentences: put cat on pig, and the cat rides the pig; put hat on dog.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startBob(player, BobRound.Mode.SILLY, null, null), "no silly sentences");
			BobRound bob = Rooms.of(player).bob();
			check(bob.floorWords().containsAll(List.of("put", "on", "cat", "pig", "hat", "dog")), "the silly floor lacks a card: " + bob.floorWords());
			for (String card : List.of("put", "cat", "on", "pig")) check(bob.clickCard(player, card, false), "the " + card + " card would not go in");
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 600, "Bob never did put cat on pig", () -> since(session.player()).contains("clip:phrase/bob_did_it"));
		session.waitTicks(10);
		session.onServer(server -> {
			BobRound bob = Rooms.of(session.player()).bob();
			Entity cat = bob.onStage("cat");
			Entity pig = bob.onStage("pig");
			check(cat != null && pig != null && cat.getVehicle() == pig, "the cat is not riding the pig: " + bob.acted());
		});
		shoot(session, "learning-bob-cat-rides-pig");
		Waits.until(session, 300, "the silly sentence never finished", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			for (String card : List.of("put", "cat", "on", "pig")) check(bob.clickCard(player, card, true), "the " + card + " card would not come home");
			for (String card : List.of("put", "hat", "on", "dog")) check(bob.clickCard(player, card, false), "the " + card + " card would not go in");
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 600, "Bob never did put hat on dog", () -> since(session.player()).contains("clip:phrase/bob_did_it"));
		session.waitTicks(10);
		session.onServer(server -> {
			BobRound bob = Rooms.of(session.player()).bob();
			BobScene.Place hat = bob.acted().where("hat");
			check(hat != null && hat.rel() == BobScene.Rel.HEAD && hat.parent().equals("dog"), "the hat is not on the dog: " + bob.acted());
			check(bob.onStage("hat").getY() > bob.onStage("dog").getY() + 0.4, "the hat is not up on the dog's head");
			check(bob.acted().where("cat") != null, "the cat stopped riding: the stage should keep what was done");
		});
		shoot(session, "learning-bob-hat-on-dog");
		Waits.until(session, 300, "the silly sentence never finished", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> check(Progress.of(session.player()).memory().item(Progress.sentenceKey("put hat on dog")) == null,
			"a silly sentence counted towards mastery"));

		// What's wrong: put cat on box, already done; on swapped for in.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startBob(player, BobRound.Mode.FIX, Goals.byId("cat-in-box"), Goals.Mistake.LITTLE_WORD), "no what's-wrong round");
			BobRound bob = Rooms.of(player).bob();
			List<String> row = bob.rowWords().getFirst();
			check(row.size() == 4 && row.get(1).equals("cat") && row.get(3).equals("box") && !row.get(2).equals("in"), "the row is not cat _ box wrong: " + row);
			check(!bob.acted().equals(Goals.byId("cat-in-box").end())
				&& (bob.acted().where("cat") != null || bob.acted().where("box") != null), "the wrong sentence was not already done: " + bob.acted());
			check(bob.floorWords().contains("in"), "the in card is not on the floor: " + bob.floorWords());
		});
		Waits.until(session, 400, "what's wrong was never asked", () -> Narrator.log(session.player()).contains("clip:phrase/whats_wrong") && !Narrator.isPlaying(session.player()));
		shoot(session, "learning-bob-whats-wrong");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			String wrongWord = bob.rowWords().getFirst().get(2);
			check(bob.clickCard(player, wrongWord, true), "the wrong card would not come out");
			check(bob.clickCard(player, "in", false), "the in card would not go in");
			check(bob.rowWords().getFirst().equals(List.of("put", "cat", "in", "box")), "the fixed row is " + bob.rowWords());
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 800, "the fixed sentence was never cheered", () -> since(session.player()).contains("clip:phrase/bob_hooray"));
		session.onServer(server -> check(Progress.of(session.player()).sentenceClean("put cat in box") == 1, "the fixed sentence, first try, did not count as clean"));
		Waits.until(session, 600, "the round never moved on", () -> !Rooms.of(session.player()).busy() && Rooms.of(session.player()).bob() == null);

		// Two sentences in a row, the lid first: the cat ends up on the lid.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startBob(player, BobRound.Mode.GOAL, Goals.byId("cat-in-box+lid-on-box"), null), "no two-sentence round");
			BobRound bob = Rooms.of(player).bob();
			check(bob.rowWords().size() == 2, "a two-sentence goal has " + bob.rowWords().size() + " rows");
			for (String card : List.of("put", "lid", "on", "box", "put", "cat", "in", "box")) check(bob.clickCard(player, card, false), "the " + card + " card would not go in");
			check(bob.rowWords().equals(List.of(List.of("put", "lid", "on", "box"), List.of("put", "cat", "in", "box"))), "the rows are " + bob.rowWords());
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 800, "Bob never did the lid first", () -> since(session.player()).contains("clip:phrase/bob_did_it"));
		session.waitTicks(10);
		session.onServer(server -> {
			BobRound bob = Rooms.of(session.player()).bob();
			BobScene.Place cat = bob.acted().where("cat");
			check(cat != null && cat.rel() == BobScene.Rel.ON && cat.parent().equals("lid"), "the cat is not on the lid: " + bob.acted());
		});
		shoot(session, "learning-bob-cat-on-lid");
		Waits.until(session, 600, "the lid-first round never settled", () -> !Rooms.of(session.player()).busy() && since(session.player()).contains("clip:phrase/not_quite"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			for (String card : List.of("put", "cat", "in", "box", "put", "lid", "on", "box")) check(bob.clickCard(player, card, false), "the " + card + " card would not go in");
			mark(player);
			press(player, Rooms.of(player));
		});
		Waits.until(session, 800, "the recipe in order was never cheered", () -> since(session.player()).contains("clip:phrase/bob_hooray"));
		Waits.until(session, 600, "the recipe round never moved on", () -> !Rooms.of(session.player()).busy() && Rooms.of(session.player()).bob() == null);

		// A word to fix: cot under a cat.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Rooms.startFix(player, word("cat"), Letter.O), "no word to fix for cat with o");
			Room room = Rooms.of(player);
			check(room.fixLetter() == Letter.O && room.inGap() == Letter.O, "the gap does not hold the wrong o: " + room.inGap());
			check(room.puzzle().target().text().equals("cat"), "the word to fix is not cat");
		});
		Waits.until(session, 400, "what's wrong was never asked for cot", () -> since(session.player()).contains("clip:phrase/whats_wrong") && !Narrator.isPlaying(session.player()));
		shoot(session, "learning-fix-cot");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			mark(player);
			player.level().setBlock(room.gap(), LearningBlocks.LETTER.showing(Letter.A), 3);
			press(player, room);
		});
		Waits.until(session, 600, "cat, fixed, was never cheered", () -> since(session.player()).contains("clip:phrase/well_done"));

		// The progress screen: a Sentences section.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ProgressView.open(player, player);
			List<String> shown = ProgressView.lastShown(player);
			check(shown.stream().anyMatch(line -> line.startsWith("Sentences:")), "the progress screen has no Sentences section: " + shown);
			check(shown.stream().anyMatch(line -> line.startsWith("put cat in box")), "put cat in box is not on the progress screen");
		});
		session.waitTicks(10);
		session.context().setScreen(() -> null);

		// Reading together: Ava picks the goal, Max tells Bob, both are credited.
		FakePlayer[] ava = new FakePlayer[1];
		session.onServer(server -> {
			ServerPlayer max = session.player();
			ServerLevel classroom = (ServerLevel) max.level();
			FakePlayer fake = FakePlayer.get(classroom, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000b0b01"), "Ava"));
			Vec3 at = Rooms.of(max).spawn();
			fake.snapTo(at.x - 1.5, at.y, at.z, 180, 0);
			online(server, fake, true);
			Visit.set(fake, Visit.snapshot(fake));
			ava[0] = fake;
			as(fake, "learn join " + max.getGameProfile().name());
			as(max, "learn accept Ava");
			check(Rooms.of(fake) == Rooms.of(max), "Ava did not join Max");
			Progress.of(fake).setBand(fake, Stages.parse("5a"));
		});
		session.waitTicks(20);
		session.onServer(server -> {
			ServerPlayer max = session.player();
			Room room = Rooms.of(max);
			// Max builds, whoever's turn it would have been.
			for (int i = 0; i < 3 && !max.getUUID().equals(room.turn()); i++) Rooms.nextRound(max);
			Narrator.stop(max);
			check(Rooms.startBob(max, BobRound.Mode.GOAL, null, null), "no Tell Bob round reading together");
			BobRound bob = room.bob();
			check(bob.picking() && bob.offered().size() == 2, "Ava was not given two goals to pick from: " + bob.offered());
			mark(max);
			bob.clickGoal(max, 0);
			check(bob.picking(), "Max picked his own goal");
		});
		Waits.until(session, 300, "the pick was never asked", () -> Narrator.log(session.player()).contains("pick_for_bob") && !Narrator.isPlaying(session.player()));
		shoot(session, "learning-bob-pick");
		session.onServer(server -> {
			ServerPlayer max = session.player();
			BobRound bob = Rooms.of(max).bob();
			check(bob.clickGoal(ava[0], 1), "Ava could not pick");
			check(!bob.picking() && bob.goal() == bob.offered().get(1), "Ava's pick is not the goal");
			for (String card : bob.goal().rows().getFirst()) check(bob.clickCard(max, card, false), "the " + card + " card would not go in");
			mark(max);
			press(max, Rooms.of(max));
		});
		Waits.until(session, 800, "the together sentence was never cheered", () -> since(session.player()).contains("clip:phrase/bob_hooray"));
		session.onServer(server -> {
			ServerPlayer max = session.player();
			String key = Rooms.of(max).bob().goal().key();
			check(Progress.of(max).sentenceClean(key) >= 1 && Progress.of(ava[0]).sentenceClean(key) >= 1, "both readers were not credited for " + key);
		});
		Waits.until(session, 600, "the together round never moved on", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			FakePlayer fake = ava[0];
			Rooms.forget(fake);
			online(server, fake, false);
			Visit.set(fake, null);
		});
		session.command("/learn leave");
		Waits.home(session);
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
		lookAt(player, room, Vec3.atCenterOf(room.origin().offset(0, 0, -2)).add(0, 0.6, 0));
	}

	private static void lookAt(ServerPlayer player, Room room, Vec3 at) {
		Vec3 from = room.spawn();
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	@SuppressWarnings("unchecked")
	private static void online(MinecraftServer server, ServerPlayer player, boolean on) {
		try {
			PlayerList list = server.getPlayerList();
			Field players = PlayerList.class.getDeclaredField("players");
			Field byId = PlayerList.class.getDeclaredField("playersByUUID");
			players.setAccessible(true);
			byId.setAccessible(true);
			List<ServerPlayer> all = (List<ServerPlayer>) players.get(list);
			Map<UUID, ServerPlayer> ids = (Map<UUID, ServerPlayer>) byId.get(list);
			if (on) {
				all.add(player);
				ids.put(player.getUUID(), player);
			} else {
				all.remove(player);
				ids.remove(player.getUUID());
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not put the stand-in reader on the player list", e);
		}
	}

	private static void as(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
