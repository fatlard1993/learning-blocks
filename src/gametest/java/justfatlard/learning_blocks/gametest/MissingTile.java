package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.LetterBlock;
import justfatlard.learning_blocks.puzzle.Puzzle;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A tile a child knocks loose is theirs to put in the gap, and is never lost in their pack.
 *
 * <p>The child here holds hotbar slot 2, as a child does who came in with slot 2 picked: the
 * classroom empties the pack, not the choice of slot. They knock h loose (to hear it, or to use it)
 * and walk over it; then another; and use what is in their hand on the underscore, the only way a
 * child puts a tile in. A picked-up item goes to the first empty slot, not the one in hand, so a
 * dropped h would land in slot 0, out of hand: gone from the floor and impossible to put in.
 */
public final class MissingTile implements FabricClientGameTest {

	private static final int HAND = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);

			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("hat")).findFirst().orElseThrow(), Puzzle.Kind.FIRST),
					"no first-sound round for hat");
				Room room = Rooms.of(player);
				check(room.home(Letter.H) != null && room.puzzle().shelf().size() > 1, "hat's round has no h, or nothing beside it, on the floor");
				player.getInventory().clearContent();
				player.getInventory().setSelectedSlot(HAND);

				knock(player, room.home(Letter.H));
				check(Tiles.letterOf(player.getMainHandItem()) == Letter.H,
					"h, knocked loose and picked up, is not in the child's hand: " + pack(player));
			});

			// Another tile: in hand now, and h back on the floor rather than out of sight in the pack.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Letter other = room.puzzle().shelf().stream().filter(letter -> letter != Letter.H).findFirst().orElseThrow();
				knock(player, room.home(other));
				check(Tiles.letterOf(player.getMainHandItem()) == other, other + ", knocked loose and picked up, is not in the child's hand: " + pack(player));
			});

			// Long enough for anything flying home to land, and for the room to look for missing tiles twice over.
			session.waitTicks(100);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(onFloor(player, room, Letter.H) || Tiles.letterOf(player.getMainHandItem()) == Letter.H,
					"h is not on the floor or in the child's hand, so hat can't be made: " + pack(player));
				for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
					if (slot == HAND) continue;
					check(Tiles.letterOf(player.getInventory().getItem(slot)) == null, "a tile is lying in the child's pack out of hand: " + pack(player));
				}

				// h in hand, and then the child scrolls away from it (or swaps it to the other hand): out of
				// hand it is out of use, so it goes back to the floor rather than waiting in the pack.
				knock(player, room.home(Letter.H));
				check(Tiles.letterOf(player.getMainHandItem()) == Letter.H, "h is not in hand the second time: " + pack(player));
				player.getInventory().setSelectedSlot(HAND + 3);
			});
			session.waitTicks(100);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(onFloor(player, room, Letter.H), "h, scrolled away from, never came back to the floor: " + pack(player));
				for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
					check(Tiles.letterOf(player.getInventory().getItem(slot)) == null, "a tile is still in the child's pack out of hand: " + pack(player));
				}

				// And the child makes the word the only way they can: h knocked loose, used on the underscore.
				knock(player, room.home(Letter.H));
				BlockPos marker = room.gap().north();
				player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(marker).add(0, 0, 0.5), Direction.SOUTH, marker, false));
				check(room.inGap() == Letter.H, "h did not go into the gap from the child's hand: gap " + room.inGap() + ", " + pack(player));
			});
		});
	}

	/** Knock the tile here loose and walk over what it drops, as a child does: no choosing of slots. */
	private static void knock(ServerPlayer player, BlockPos pos) {
		check(pos != null && player.gameMode.destroyBlock(pos), "the tile at " + pos + " would not break");
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5))) {
			if (Tiles.letterOf(item.getItem()) == null) continue;
			item.setNoPickUpDelay();
			item.playerTouch(player);
		}
	}

	private static boolean onFloor(ServerPlayer player, Room room, Letter letter) {
		BlockPos origin = room.origin();
		for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
			BlockState state = player.level().getBlockState(origin.offset(x, 0, z));
			if (state.is(LearningBlocks.LETTER) && state.getValue(LetterBlock.LETTER) == letter) return true;
		}
		return false;
	}

	private static String pack(ServerPlayer player) {
		List<String> held = new ArrayList<>();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			Letter letter = Tiles.letterOf(player.getInventory().getItem(slot));
			if (letter != null) held.add(letter.grapheme() + " in slot " + slot);
		}
		return "hand is slot " + player.getInventory().getSelectedSlot() + ", tiles " + held;
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
