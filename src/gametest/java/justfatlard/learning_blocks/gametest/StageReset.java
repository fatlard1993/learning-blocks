package justfatlard.learning_blocks.gametest;

import java.util.Collections;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Moving up a stage is a clean slate. With a pack of things, a hat on, a cat about, a mat down and
 * the child grown, reading the last word of stage 1 empties the pack, sends the cat off, takes the
 * mat up, puts the child back to size, says "You're ready for new words!", and starts stage 2. An
 * operator setting the stage does the same.
 */
public final class StageReset implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);
			Word[] last = new Word[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				List<Word> stageOne = Stages.at(1);
				last[0] = stageOne.stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow();
				Progress progress = Progress.of(player);
				for (Word word : stageOne) if (!word.equals(last[0])) progress.masterForTests(player, word);
				// Cat one clean completion short of mastered: the click below masters it, and the stage with it.
				progress.setCleanForTests(player, last[0], Progress.masteredAt() - 1);
				check(Rooms.startRound(player, last[0]), "no round for cat");

				// A room full of what stage 1 left behind.
				for (String text : List.of("cat", "mat", "big")) {
					Rooms.runOutcome(player, Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow());
				}
				player.getInventory().setItem(5, new ItemStack(Items.COOKIE, 3));
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				player.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, new ItemStack(Items.BREAD));
			});
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(!room.creatures().isEmpty(), "the cat never arrived");
				check(!room.placed().isEmpty(), "the mat never landed");
				check(player.getAttribute(Attributes.SCALE).getValue() > 1.0, "the child never grew");
				Hands.solve(player, room);
				BlockPos button = room.button();
				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
			});
			Waits.until(session, 900, "\"You're ready for new words!\" was never said",
				() -> Narrator.log(session.player()).contains("clip:phrase/next_level"));
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(player.getInventory().isEmpty(), "the pack was not emptied for the new stage");
				check(room.creatures().isEmpty(), "creatures from stage 1 are still about");
				check(room.placed().isEmpty(), "blocks from stage 1 are still down");
				check(player.getAttribute(Attributes.SCALE).getValue() == 1.0, "the child is still grown");
				check(player.getActiveEffects().isEmpty(), "effects from stage 1 are still on");
				check(room.stage() == 2 && room.puzzle().target().stage() == 2, "the new round is not from stage 2");
				List<String> log = Narrator.log(player);
				check(!log.subList(log.lastIndexOf("clip:word/cat"), log.size()).contains("clip:phrase/another_one"),
					"\"Here comes another one!\" was said as well as the new-stage line");
			});
			Waits.until(session, 300, "the new stage's prompt never came",
				() -> {
					List<String> log = Narrator.log(session.player());
					return log.lastIndexOf("clip:phrase/make_the_word") > log.lastIndexOf("clip:phrase/next_level");
				});

			// An operator puts them back at stage 1: the same clean slate and line.
			session.onServer(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				"learn stage " + session.player().getGameProfile().name() + " 1"));
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Collections.frequency(Narrator.log(player), "clip:phrase/next_level") == 2,
					"setting the stage did not say \"You're ready for new words!\": " + Narrator.log(player));
				check(Rooms.of(player).stage() == 1, "setting stage 1 did not start a stage 1 round");
			});
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
