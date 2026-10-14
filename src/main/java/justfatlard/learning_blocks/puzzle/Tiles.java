package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.numbers.NumberBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * A letter or number tile as something a child carries: the block's item, wearing the letter or
 * number in its block state so it draws as that tile in the hand.
 */
public final class Tiles {
	private Tiles() {}

	public static ItemStack stack(Letter letter) {
		ItemStack stack = new ItemStack(LearningBlocks.LETTER_ITEM);
		stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LetterBlock.LETTER, letter));
		stack.set(DataComponents.ITEM_NAME, Component.literal(letter.grapheme()));
		return stack;
	}

	public static @Nullable Letter letterOf(ItemStack stack) {
		if (!stack.is(LearningBlocks.LETTER_ITEM)) return null;
		BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
		Letter letter = state == null ? null : state.get(LetterBlock.LETTER);
		return letter != null && letter.isLetter() ? letter : null;
	}

	public static ItemStack stack(int number) {
		ItemStack stack = new ItemStack(LearningBlocks.NUMBER_ITEM);
		stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(NumberBlock.NUMBER, number));
		stack.set(DataComponents.ITEM_NAME, Component.literal(String.valueOf(number)));
		return stack;
	}

	/** The number on a number tile in hand, 0 to 20; -1 for anything else, the symbol tiles included. */
	public static int numberOf(ItemStack stack) {
		if (!stack.is(LearningBlocks.NUMBER_ITEM)) return -1;
		BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
		Integer number = state == null ? null : state.get(NumberBlock.NUMBER);
		return number != null && number <= NumberBlock.HIGHEST ? number : -1;
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

	public static void clear(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isTile(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY);
		}
		if (isTile(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
		player.containerMenu.broadcastChanges();
	}

	private static boolean isTile(ItemStack stack) {
		return letterOf(stack) != null || numberOf(stack) >= 0;
	}
}
