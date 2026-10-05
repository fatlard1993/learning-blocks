package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A prize is seen arriving: it leaves the button as the room says "You earned a prize!", flies
 * across the room, and drops into the chest, which pops into place first. Nothing that can be
 * picked up is made. Over the chest, the haul shows what is waiting; going home takes it away.
 */
public final class VisiblePrizes implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(TestPrizes::use);
			session.command("/learn");
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				click(player, room.button());
			});
			Waits.until(session, 600, "the prize line was never said",
				() -> Narrator.log(session.player()).contains("clip:phrase/you_earned_a_prize"));
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				// From the front of the room, looking back at the chest.
				Vec3 from = Vec3.atBottomCenterOf(room.origin().offset(-1, 0, -1));
				Vec3 aim = Vec3.atCenterOf(room.prizeMarker()).add(0, 1.0, 0).subtract(from.add(0, player.getEyeHeight(), 0));
				float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
				player.teleportTo(player.level(), from.x, from.y, from.z, Set.of(), yaw, pitch, true);
				check(player.level().getBlockState(room.prizeMarker()).is(net.minecraft.world.level.block.Blocks.CHEST),
					"the chest did not pop up for the first prize");
			});
			session.waitTicks(20);
			if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("learning-prize-flight").disableCounterPrefix());
			Waits.until(session, 200, "the prize never landed in the chest", () -> !Rooms.of(session.player()).prizeFlights().isEmpty());
			session.waitTicks(20);
			if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("learning-prize-haul").disableCounterPrefix());
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				justfatlard.learning_blocks.puzzle.PrizeShowcase.PrizeFlight flight = room.prizeFlights().getFirst();
				check(flight.stack().is(Items.DIAMOND), "what flew was not the diamond: " + flight.stack());
				check(flight.from().distanceTo(room.mouth()) < 0.5, "the prize did not start at the button: " + flight.from());
				check(flight.to().distanceTo(Vec3.atCenterOf(room.prizeMarker())) < 1.0, "the prize did not end at the chest: " + flight.to());
				AABB box = new AABB(room.origin()).inflate(12);
				check(player.level().getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(Items.DIAMOND)).isEmpty(),
					"a diamond that could be picked up is lying about");
				check(!player.getInventory().hasAnyMatching(stack -> stack.is(Items.DIAMOND)), "the diamond went into the pack, not the chest");
				List<net.minecraft.world.item.ItemStack> waiting = Visit.of(player).prizes();
				List<net.minecraft.world.item.ItemStack> haul = room.haulShown();
				check(haul.size() == waiting.size() && haul.stream().allMatch(stack -> stack.is(Items.DIAMOND)),
					"the haul shows " + haul + ", not what is waiting: " + waiting);
			});
			List<Display> haul = new java.util.ArrayList<>();
			session.onServer(server -> haul.addAll(Rooms.of(session.player()).haulEntities()));
			session.command("/learn leave");
			Waits.until(session, 300, "never went home", () -> Visit.of(session.player()) == null);
			session.onServer(server -> {
				check(!haul.isEmpty() && haul.stream().allMatch(display -> display.isRemoved()), "the haul outlived the visit");
				check(session.player().getInventory().hasAnyMatching(stack -> stack.is(Items.DIAMOND)), "the diamond did not come home");
			});
		});
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
