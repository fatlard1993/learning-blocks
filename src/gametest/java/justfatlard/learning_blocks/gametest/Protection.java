package justfatlard.learning_blocks.gametest;

import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Tiles;
import justfatlard.learning_blocks.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The classroom is survival so that tiles can be knocked loose and set down, and nothing else:
 * the floor, the board and its letters don't break; a tile goes only in the gap or on the floor; no
 * other block is placed; nothing hurts. A tile left on the floor or on the ground is gone at the next
 * round, and a tile the round needs that has gone missing comes back.
 */
public final class Protection implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);
			BlockPos[] onFloor = new BlockPos[1];
			Letter[] needed = new Letter[1];

			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(player.gameMode() == GameType.SURVIVAL, "the classroom is " + player.gameMode() + ", not survival");
				check(Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow()),
					"no round for cat");
				Room room = Rooms.of(player);
				BlockPos floor = room.origin().offset(2, -1, 1);
				check(!player.gameMode.destroyBlock(floor), "the floor broke");
				check(!player.level().getBlockState(floor).isAir(), "the floor is gone");
				check(!player.gameMode.destroyBlock(room.gap().east()), "a letter on the board broke");
				check(!player.gameMode.destroyBlock(room.gap().north()), "the underscore at the back of the gap broke");
				check(!player.gameMode.destroyBlock(room.button()), "the button broke");

				needed[0] = room.puzzle().answer();
				check(Hands.take(player, room.home(needed[0])), "a loose tile would not break");
				check(Tiles.carried(player) >= 0, "the broken tile did not drop and get picked up");

				// Not against the board away from the gap, and not up in the air.
				Hands.placeAgainst(player, room.gap().north().east(2), Direction.SOUTH);
				check(!player.level().getBlockState(room.gap().north().east(2).south()).is(LearningBlocks.LETTER), "a tile went on the board beside the word");
				// On the floor it is taken, and sent straight home, so nothing stays where it was set.
				onFloor[0] = room.origin().offset(2, 0, 1);
				Hands.placeAgainst(player, onFloor[0].below(), Direction.UP);
				check(!player.level().getBlockState(onFloor[0]).is(LearningBlocks.LETTER), "a tile stayed where it was set down");
				check(Tiles.carried(player) < 0, "setting a tile down left it in hand");

				// Nothing else is placed, whatever it is.
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG));
				BlockPos log = room.origin().offset(-2, 0, 1);
				player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(log.below()).add(0, 0.5, 0), Direction.UP, log.below(), false));
				check(player.level().getBlockState(log).isAir(), "a log was placed in the classroom");
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

				// A tile thrown on the ground.
				player.drop(Tiles.stack(Letter.Z), false, net.minecraft.util.Prediction.SERVER_ONLY);
			});

			// The next round: the floor tile, the dropped tile and the one in hand are gone.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow());
				check(!player.level().getBlockState(onFloor[0]).is(LearningBlocks.LETTER), "a tile left on the floor outlived the round");
				AABB room = new AABB(Rooms.of(player).origin()).inflate(8);
				check(player.level().getEntitiesOfClass(ItemEntity.class, room, item -> Tiles.letterOf(item.getItem()) != null).isEmpty(),
					"a dropped tile outlived the round");
				check(Tiles.carried(player) < 0, "a tile in hand outlived the round");

				// The tile the round needs, gone: knocked loose and thrown away.
				Room fresh = Rooms.of(player);
				needed[0] = fresh.puzzle().answer();
				Hands.take(player, fresh.home(needed[0]));
				Tiles.clear(player);
			});
			Waits.until(session, 120, "the missing tile never came back", () -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				BlockPos home = room.home(needed[0]);
				return player.level().getBlockState(home).is(LearningBlocks.LETTER);
			});
			session.onServer(server -> check(session.player().getHealth() >= session.player().getMaxHealth(), "the child got hurt"));
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
