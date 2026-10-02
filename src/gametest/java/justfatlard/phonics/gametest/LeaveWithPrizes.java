package justfatlard.phonics.gametest;

import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.narration.Narration;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The live bug: with a prize waiting, the door says "You earned a prize!" and then goes home. When
 * something else started a narration mid-line, the way home went with the line, and the child
 * could never leave. Now: prizes waiting, the door clicked, a room line started over the top, a new
 * round asked for, and still the child is home with the diamond, and the room started nothing new.
 */
public final class LeaveWithPrizes implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			session.onServer(TestPrizes::use);
			session.command("/phonics");
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Hands.solve(player, room);
				click(player, room.button());
			});
			Waits.until(session, 600, "the round never finished", () -> Narrator.log(session.player()).contains("clip:phrase/another_one")
				&& !Rooms.of(session.player()).busy());
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Visit.of(player).hasPrizes(), "no prize is waiting");
				click(player, Rooms.of(player).doorLower());
			});
			Waits.until(session, 100, "the door never said the prize line", () -> {
				java.util.List<String> log = Narrator.log(session.player());
				return log.lastIndexOf("clip:phrase/you_earned_a_prize") > log.lastIndexOf("clip:phrase/another_one");
			});
			session.onServer(server -> {
				ServerPlayer player = session.player();
				// Mid-line: another narration, and a new round asked for, as a re-prompt or a tile would.
				Narrator.play(player, new Narration().say("phrase/make_the_word"));
				Room room = Rooms.of(player);
				check(room != null && room.leaving(), "the room does not know the child is leaving");
				Rooms.nextRound(player);
				check(Rooms.of(player).puzzle() != null, "the room lost its puzzle");
			});
			Waits.until(session, 200, "the child never got home", () -> Visit.of(session.player()) == null);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(player.getInventory().hasAnyMatching(stack -> stack.is(Items.DIAMOND)), "the prize did not come home");
				check(Rooms.of(player) == null, "the room is still open");
			});
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
