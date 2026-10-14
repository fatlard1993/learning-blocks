package justfatlard.learning_blocks.gametest;

import java.util.Collections;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every reader's cubby has its chest from the moment they come in, empty until a prize is paid; the
 * room says "You earned a prize!" once, then. Opened, it shows what is waiting and holds nothing
 * that can be moved, and the first look at something waiting says the prizes come home. After going
 * home and coming back, it is there again, empty.
 */
public final class PrizeChest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(TestPrizes::use);
			session.command("/learn");
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(player.level().getBlockState(room.prizeMarker()).is(Blocks.CHEST), "no chest in the cubby before anything was earned");
				check(room.chestContents(player).isEmpty(), "the chest shows something before anything was earned");
				Hands.solve(player, room);
				click(player, room.button());
			});
			Waits.until(session, 600, "no prize was paid", () -> Visit.of(session.player()).hasPrizes());
			Waits.until(session, 600, "the round never finished", () -> Narrator.log(session.player()).contains("clip:phrase/another_one")
				&& !Rooms.of(session.player()).busy());
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Collections.frequency(Narrator.log(player), "clip:phrase/you_earned_a_prize") == 1,
					"\"You earned a prize!\" was not said exactly once: " + Narrator.log(player));
				Room room = Rooms.of(player);
				check(room.chestContents(player).stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "the chest does not show the diamond");
				click(player, room.prizeMarker());
				check(PandoricalApi.getOpenScreenId(player.getUUID()) != null, "the chest showed nothing");
				check(player.containerMenu == player.inventoryMenu, "the chest opened a real container, whose items could be moved");
				check(Visit.of(player).prizes().size() == 1, "looking in the chest changed what is waiting");
			});
			session.waitTicks(10);
			session.onServer(server -> click(session.player(), Rooms.of(session.player()).prizeMarker()));
			session.waitTicks(40);
			session.onServer(server -> check(Collections.frequency(Narrator.log(session.player()), "clip:phrase/prizes_come_home") == 1,
				"\"Your prizes come home with you!\" was not said once: " + Narrator.log(session.player())));

			session.command("/learn leave");
			Waits.until(session, 300, "never went home", () -> Visit.of(session.player()) == null);
			session.command("/learn");
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(player.level().getBlockState(room.prizeMarker()).is(Blocks.CHEST), "no chest in the cubby on the way back in");
				check(room.chestContents(player).isEmpty(), "the chest still shows the prizes that went home");
			});
			session.command("/learn leave");
			session.waitTicks(20);
		});
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
