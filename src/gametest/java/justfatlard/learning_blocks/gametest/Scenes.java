package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SceneRun;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.PrizeConfig;
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
 * Situation puzzles. None for a child who has read nothing; once hat is read, a scene round is the
 * hat scene: its props are up, its line is said and neither the picture nor "Can you make..." is.
 * A wrong word does its own thing while the rain keeps falling; the right one runs the solve and
 * "That helped!", and the next round leaves nothing of the scene. Every scene in turn, solved, with
 * a picture before and after. The dimmed room comes back on at the round's end and on the way out.
 * The rate setting: one round in three is a scene, and none when scenes are off.
 */
public final class Scenes implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(60);

			// Nothing read: no scene, however often they are due.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				PrizeConfig.useScenes(true, 1);
				check(Progress.of(player).words().isEmpty(), "a fresh player has read words");
				Rooms.nextRound(player);
				check(Rooms.of(player).scene() == null, "a scene came up with nothing read");
			});

			// Hat read: the hat scene, and no picture or "Can you make...".
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress.of(player).read(player, "learning-blocks-justfatlard:hat");
				Rooms.nextRound(player);
				Room room = Rooms.of(player);
				SceneRun scene = room.scene();
				check(scene != null && scene.word().text().equals("hat"), "the round after reading hat is not the hat scene: " + scene);
				check(room.picture() == null, "a scene round shows the picture");
				check(scene.character() != null && scene.character().isAlive() && scene.character().isInvulnerable(),
					"the scene's villager is not there, or can be hurt");
				check(scene.ambientCount() == 2, "the rain cloud is not up: " + scene.ambientCount());
				look(player);
			});
			Waits.until(session, 200, "the scene's line was never said",
				() -> Narrator.log(session.player()).contains("clip:phrase/what_can_help"));
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				List<String> round = log.subList(log.lastIndexOf("scene:hat"), log.size());
				check(round.indexOf("clip:scene/hat") >= 0 && round.indexOf("clip:scene/hat") < round.indexOf("clip:phrase/what_can_help"),
					"the scene line did not come before what can help: " + round);
				check(!round.contains("clip:phrase/make_the_word"), "a scene round asked \"Can you make...\": " + round);
			});

			// A wrong word: its own outcome, while the scene goes on, and then asked again.
			// Whichever other tile the floor has: the _at family is bigger than the floor.
			String[] wrong = new String[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Letter other = room.puzzle().shelf().stream().filter(letter -> letter != Letter.H).findFirst().orElseThrow();
				wrong[0] = "tile_home:" + other.grapheme();
				check(Hands.take(player, room.home(other)), other + " would not break");
				Hands.placeInGap(player, room);
				press(player, room);
			});
			Waits.until(session, 600, "the wrong word was never done",
				() -> Narrator.log(session.player()).contains(wrong[0]));
			Waits.until(session, 400, "the scene was never asked again after the wrong word", () -> {
				List<String> log = Narrator.log(session.player());
				return log.lastIndexOf("clip:scene/hat") > log.lastIndexOf(wrong[0]);
			});
			session.onServer(server -> {
				SceneRun scene = Rooms.of(session.player()).scene();
				check(scene != null && !scene.solved() && scene.word().text().equals("hat"), "the wrong word ended the scene");
				check(scene.character() != null && scene.character().isAlive() && scene.ambientCount() == 2, "the rain stopped for a wrong word");
				check(!Narrator.log(session.player()).contains("solve"), "a wrong word solved the scene");
			});

			// Every scene in turn, and each solved; the hat one first.
			List<Word> scenes = new ArrayList<>();
			session.onServer(server -> {
				// The first ten; MoreScenes has the rest.
				Set<String> first = Set.of("hat", "lit", "bun", "cot", "hot", "map", "bin", "hug", "fan", "cup");
				for (Word word : Stages.usable()) if (word.usableScene().isPresent() && first.contains(word.text())) scenes.add(word);
				check(scenes.size() >= 8, "only " + scenes.size() + " scenes load");
				scenes.sort((a, b) -> a.text().equals("hat") ? -1 : b.text().equals("hat") ? 1 : a.text().compareTo(b.text()));
				for (Word word : scenes) Progress.of(session.player()).read(session.player(), word.id().toString());
			});
			for (Word word : scenes) {
				String text = word.text();
				SceneRun[] run = new SceneRun[1];
				if (!text.equals("hat")) {
					session.onServer(server -> {
						check(Rooms.startScene(session.player(), word), "no scene round for " + text);
						look(session.player());
					});
				}
				session.onServer(server -> {
					run[0] = Rooms.of(session.player()).scene();
					check(run[0] != null && run[0].word().equals(word), text + "'s scene did not start");
					check(!text.equals("lit") || run[0].dimmed(), "lit's room is not dim");
					check(!text.equals("bin") || run[0].litterCount() > 0, "bin's floor is not messy");
				});
				Waits.until(session, 200, text + "'s line was never said", () -> !Narrator.isPlaying(session.player()));
				shoot(session, "phonics-scene-" + text + "-before");
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					Hands.solve(player, room);
					press(player, room);
				});
				// The log keeps only its last lines: "that helped" after this round's own start.
				Waits.until(session, 800, text + "'s solve never said that helped", () -> {
					List<String> log = Narrator.log(session.player());
					return log.lastIndexOf("clip:phrase/that_helped") > log.lastIndexOf("scene:" + text);
				});
				session.onServer(server -> {
					List<String> log = Narrator.log(session.player());
					check(log.lastIndexOf("solve") >= 0 && log.lastIndexOf("solve") < log.lastIndexOf("clip:phrase/that_helped"),
						text + ": the solve did not come before that helped");
					check(run[0].solved() && run[0].ambientCount() == 0, text + "'s trouble did not stop");
					check(!run[0].dimmed(), text + "'s room is still dim after the solve");
					check(run[0].litterCount() == 0, text + "'s litter is still about");
					check(session.player().getHealth() >= session.player().getMaxHealth(), text + " hurt the child");
				});
				shoot(session, "phonics-scene-" + text + "-after");
				// The next round: nothing of this scene is left.
				session.onServer(server -> Rooms.startRound(session.player(),
					Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow()));
				session.waitTicks(2);
				session.onServer(server -> {
					check(run[0].alive().isEmpty(), text + "'s scene left " + run[0].alive());
					check(Rooms.of(session.player()).scene() == null, "an asked-for round is still a scene");
					Room room = Rooms.of(session.player());
					for (BlockPos pos : room.placed()) check(false, text + " left a block at " + pos);
				});
			}

			// Dimmed, then the round ends before it is solved; and dimmed, then home.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Word lit = Words.all().stream().filter(w -> w.text().equals("lit")).findFirst().orElseThrow();
				check(Rooms.startScene(player, lit), "no lit scene");
				SceneRun scene = Rooms.of(player).scene();
				check(scene.dimmed(), "the lit scene did not dim");
				Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow());
				check(!scene.dimmed(), "the room stayed dim past the round's end");
				check(Rooms.startScene(player, lit), "no second lit scene");
			});
			SceneRun[] leaving = new SceneRun[1];
			session.onServer(server -> leaving[0] = Rooms.of(session.player()).scene());
			session.command("/learn leave");
			Waits.until(session, 300, "never went home", () -> Rooms.of(session.player()) == null);
			session.onServer(server -> {
				check(!leaving[0].dimmed() && leaving[0].alive().isEmpty(), "the dark or the scene came home");
			});

			// The rate: one round in three, and none when off.
			session.command("/learn");
			session.waitTicks(60);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				PrizeConfig.useScenes(true, 3);
				List<Boolean> rounds = new ArrayList<>();
				for (int i = 0; i < 9; i++) {
					Rooms.nextRound(player);
					rounds.add(Rooms.of(player).scene() != null);
				}
				check(rounds.stream().filter(b -> b).count() == 3, "one in three is not three scenes in nine rounds: " + rounds);
				int first = rounds.indexOf(true);
				check(rounds.get(first + 3) && rounds.get(first + 6), "scenes are not every third round: " + rounds);
				PrizeConfig.useScenes(false, 1);
				for (int i = 0; i < 4; i++) {
					Rooms.nextRound(player);
					check(Rooms.of(player).scene() == null, "a scene came up with scenes off");
				}
				PrizeConfig.load(server);
			});
			session.command("/learn leave");
			Waits.until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level()));
		});
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	/** From the back of the room, looking at the scene: the character, what is over its head, and the floor around it. */
	private static void look(ServerPlayer player) {
		Room room = Rooms.of(player);
		Vec3 from = room.spawn().add(0.5, 0, 1.2);
		Vec3 at = Vec3.atBottomCenterOf(room.origin().offset(-2, 0, 0)).add(0, 1.4, 0);
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo(player.level(), from.x, from.y, from.z, Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
