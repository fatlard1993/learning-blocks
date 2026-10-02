package justfatlard.phonics.puzzle;

import justfatlard.phonics.Phonics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * A letter tile as something a child carries: the letter block's item, wearing the letter in its
 * block state so it draws as that letter in the hand.
 */
public final class Tiles {
	private Tiles() {}

	public static ItemStack stack(Letter letter) {
		ItemStack stack = new ItemStack(Phonics.LETTER_ITEM);
		stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LetterBlock.LETTER, letter));
		stack.set(DataComponents.ITEM_NAME, Component.literal(letter.grapheme()));
		return stack;
	}

	public static @Nullable Letter letterOf(ItemStack stack) {
		if (!stack.is(Phonics.LETTER_ITEM)) return null;
		BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
		Letter letter = state == null ? null : state.get(LetterBlock.LETTER);
		return letter != null && letter.isLetter() ? letter : null;
	}

	/** The inventory slot of the tile this child is carrying, or -1. */
	public static int carried(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		if (letterOf(inventory.getItem(selected)) != null) return selected;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (letterOf(inventory.getItem(slot)) != null) return slot;
		}
		return -1;
	}

	/** Take every tile out of this child's inventory, and off their cursor. */
	public static void clear(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (letterOf(inventory.getItem(slot)) != null) inventory.setItem(slot, ItemStack.EMPTY);
		}
		if (letterOf(player.containerMenu.getCarried()) != null) player.containerMenu.setCarried(ItemStack.EMPTY);
		player.containerMenu.broadcastChanges();
	}
}
