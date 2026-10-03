package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SceneRun;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The situations the new words brought: each set up (its characters, vines, flowers, puddle,
 * fence), solved by the right word (everyone sits, the vines fall, the flowers bloom, the puddle is
 * gone, he is pulled free, the child can hop), and gone at the next round. Pop's scene wants the
 * pinata mod, and is not offered without it. A picture of each before and after.
 */
public final class MoreScenes implements FabricClientGameTest {

	/** How many characters each scene has. */
	private static final Map<String, Integer> CHARACTERS = Map.ofEntries(
		Map.entry("sit", 2), Map.entry("fed", 3), Map.entry("hop", 0), Map.entry("cut", 1), Map.entry("wet", 0), Map.entry("hum", 1),
		Map.entry("nap", 1), Map.entry("hut", 1), Map.entry("mop", 0), Map.entry("rod", 1), Map.entry("vet", 1), Map.entry("tug", 1),
		Map.entry("ship", 1), Map.entry("fish", 1), Map.entry("bone", 1), Map.entry("tree", 1), Map.entry("sheep", 1), Map.entry("milk", 1), Map.entry("hay", 1));

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				justfatlard.learning_blocks.reward.PrizeConfig.useScenes(false, 4);
				boolean pinata = BuiltInRegistries.BLOCK.containsKey(Identifier.parse("pinata:pinata"));
				check(word("pop").usableScene().isPresent() == pinata, "pop's scene is " + (pinata ? "not " : "") + "offered with pinata " + (pinata ? "" : "not ") + "installed");
				for (String text : CHARACTERS.keySet()) {
					check(word(text).usableScene().isPresent(), text + " has no usable scene");
					Progress.of(player).read(player, word(text).id().toString());
				}
			});
			for (String text : List.of("sit", "fed", "hop", "cut", "wet", "hum", "nap", "hut", "mop", "rod", "vet", "tug", "ship", "fish", "bone", "tree", "sheep", "milk", "hay")) {
				SceneRun[] run = new SceneRun[1];
				session.onServer(server -> {
					ServerPlayer player = session.player();
					check(Rooms.startScene(player, word(text)), "no scene round for " + text);
					run[0] = Rooms.of(player).scene();
					SceneRun scene = run[0];
					check(scene.characters().size() == CHARACTERS.get(text), text + " has " + scene.characters().size() + " characters");
					for (Entity who : scene.characters()) check(who.isInvulnerable(), text + "'s " + who + " can be hurt");
					check(!text.equals("cut") || scene.wrapCount() > 0, "cut's villager is not tangled");
					check(!Set.of("wet", "mop").contains(text) || scene.unchangedCount() > 0, text + "'s blocks are not waiting to change");
					look(player);
				});
				Waits.until(session, 300, text + "'s line was never said", () -> !Narrator.isPlaying(session.player()));
				shoot(session, "phonics-scene-" + text + "-before");
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					Hands.solve(player, room);
					press(player, room);
				});
				Waits.until(session, 900, text + "'s solve never said that helped", () -> {
					List<String> log = Narrator.log(session.player());
					return log.lastIndexOf("clip:phrase/that_helped") > log.lastIndexOf("scene:" + text);
				});
				session.onServer(server -> {
					ServerPlayer player = session.player();
					SceneRun scene = run[0];
					check(scene.solved() && scene.ambientCount() == 0, text + "'s trouble did not stop");
					check(scene.wrapCount() == 0, text + "'s vines are still on");
					check(scene.unchangedCount() == 0, text + "'s blocks did not change");
					if (text.equals("sit")) {
						for (Entity who : scene.characters()) check(who instanceof TamableAnimal pet && pet.isInSittingPose(), "a pup did not sit");
					}
					if (text.equals("tug")) {
						Entity who = scene.character();
						check(who.getY() >= Rooms.of(player).origin().getY() - 0.05, "the villager is still stuck: " + who.getY());
					}
					if (text.equals("hop")) {
						check(player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST), "the child cannot hop the fence");
					}
					check(player.getHealth() >= player.getMaxHealth(), text + " hurt the child");
				});
				shoot(session, "phonics-scene-" + text + "-after");
				session.onServer(server -> Rooms.startRound(session.player(), word("cat")));
				session.waitTicks(2);
				session.onServer(server -> {
					Room room = Rooms.of(session.player());
					check(run[0].alive().isEmpty(), text + "'s scene left " + run[0].alive());
					check(room.scene() == null, "an asked-for round is still a scene");
					check(room.placed().isEmpty(), text + " left blocks: " + room.placed());
					session.player().removeAllEffects();
				});
			}
			session.command("/learn leave");
			Waits.home(session);
		});
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	/** From the back of the room, looking at the scene. */
	private static void look(ServerPlayer player) {
		Room room = Rooms.of(player);
		Vec3 from = room.spawn().add(0.5, 0, 1.2);
		Vec3 at = Vec3.atBottomCenterOf(room.origin().offset(-2, 0, 0)).add(0, 1.2, 0);
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
