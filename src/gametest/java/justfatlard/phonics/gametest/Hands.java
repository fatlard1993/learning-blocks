package justfatlard.phonics.gametest;

import java.util.List;

import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Tiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a child's hands do, done through the same server paths their client's packets reach: a
 * block broken by the game mode that finishes a dig, the drop walked over, a tile placed by the
 * game mode that answers a right-click. The rules the classroom enforces are all on those paths.
 */
final class Hands {
	private Hands() {}

	/** Break the tile here and pick up what it drops. True if it broke. */
	static boolean take(ServerPlayer player, BlockPos pos) {
		if (!player.gameMode.destroyBlock(pos)) return false;
		var drops = player.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
		for (ItemEntity item : drops) {
			if (Tiles.letterOf(item.getItem()) == null) continue;
			item.setNoPickUpDelay();
			item.playerTouch(player);
		}
		if (Tiles.carried(player) < 0) {
			throw new AssertionError("broke " + pos + " but carry no tile; near it: " + drops + "; game mode " + player.gameMode()
				+ "; now " + player.level().getBlockState(pos));
		}
		return true;
	}

	/** Hold the first tile carried and place it against this block's face. */
	static InteractionResult placeAgainst(ServerPlayer player, BlockPos against, Direction face) {
		int slot = Tiles.carried(player);
		if (slot < 0) throw new AssertionError("no tile to place");
		var inventory = player.getInventory();
		if (slot != inventory.getSelectedSlot()) {
			ItemStack tile = inventory.getItem(slot);
			inventory.setItem(slot, inventory.getSelectedItem());
			inventory.setItem(inventory.getSelectedSlot(), tile);
		}
		Vec3 at = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(at, face, against, false));
	}

	/** Place the carried tile into the gap, against the underscore at its back. */
	static InteractionResult placeInGap(ServerPlayer player, Room room) {
		return placeAgainst(player, room.gap().north(), Direction.SOUTH);
	}

	/** Break the loose tile the target needs, pick it up, put it in the gap; every gap, when the whole word is built. */
	static void solve(ServerPlayer player, Room room) {
		List<Integer> slots = room.puzzle().gaps();
		List<BlockPos> gaps = room.gaps();
		for (int i = 0; i < slots.size(); i++) {
			Letter tile = Letter.of(room.puzzle().target().tiles().get(slots.get(i)));
			BlockPos home = room.home(tile);
			if (!take(player, home)) throw new AssertionError("the target's tile " + tile + " at " + home + " would not break");
			placeAgainst(player, gaps.get(i).north(), Direction.SOUTH);
		}
	}
}
