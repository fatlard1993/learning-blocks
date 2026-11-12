package justfatlard.learning_blocks.gametest;

import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.puzzle.Tiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A number tile answered the way a child answers one: knocked loose into the hand, then placed in
 * the gap on the board. A click on a carried tile picks nothing, so a test that clicks one is testing
 * a way of answering the game no longer has.
 */
final class Carry {
	private Carry() {}

	/** Whether this choice is answered by carrying rather than clicking. */
	static boolean carried(SubjectRound round, SubjectRound.Choice choice) {
		return round.carriedForTests(choice);
	}

	static void intoGap(ServerPlayer player, SubjectRound round, SubjectRound.Choice choice) {
		BlockPos spot = BlockPos.containing(round.hitSpotOf(choice));
		check(player.gameMode.destroyBlock(spot), "the " + choice.id() + " tile at " + spot + " would not break");
		check(Tiles.numberOf(player.getMainHandItem()) == Integer.parseInt(choice.id()),
			"broke the " + choice.id() + " tile but hold " + player.getMainHandItem());
		BlockPos gap = round.gapForTests();
		check(gap != null, "no gap on the board for the " + choice.id() + " tile");
		player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(gap).add(0, 0, 0.5), Direction.SOUTH, gap, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
