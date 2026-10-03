package justfatlard.learning_blocks.puzzle;

import java.util.List;
import java.util.Map;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Looking in the prize chest: what is waiting to go home, laid out like a chest's slots, to look at
 * and not to take. A Pandorical screen of pictures rather than a real container, so there is nothing
 * to click out and nowhere to put anything in - the prizes stay where they are safe, in the visit,
 * until the door hands them over.
 */
public final class PrizeChestScreen {
	private PrizeChestScreen() {}

	public static final String TYPE = LearningBlocks.MOD_ID + ":prize_chest";

	private static final int COLUMNS = 9;
	private static final int SLOT = 18;
	private static final int PAD = 8;

	public static void register(justfatlard.pandorical.api.ScreenApi screens) {
		screens.onAction(TYPE, "my_words", (player, data) ->
			justfatlard.learning_blocks.review.Review.open(player, justfatlard.learning_blocks.review.Review.Tab.WORDS, null, false));
	}

	public static void open(ServerPlayer player, List<ItemStack> contents) {
		if (!PandoricalApi.isAvailable(player)) return;
		int rows = Math.max(1, (contents.size() + COLUMNS - 1) / COLUMNS);
		int width = PAD * 2 + COLUMNS * SLOT;
		int height = 20 + rows * SLOT + PAD + 12 + 20;
		ScreenBuilder screen = new ScreenBuilder(TYPE).title("Your prizes").pauseGame(false).size(width, height);
		screen.panel("chest", 0, 0, width, height, Map.of(ComponentType.PROP_BORDER, "beveled"));
		screen.text("title", PAD, 7, "Your prizes");
		for (int i = 0; i < rows * COLUMNS; i++) {
			int x = PAD + (i % COLUMNS) * SLOT;
			int y = 18 + (i / COLUMNS) * SLOT;
			screen.component(new ComponentBuilder("slot" + i, ComponentType.PANEL).bounds(x, y, SLOT, SLOT)
				.prop(ComponentType.PROP_BORDER, "beveled")
				.prop(ComponentType.PROP_BORDER_LIGHT, "#FF373737").prop(ComponentType.PROP_BORDER_DARK, "#FFFFFFFF")
				.prop(ComponentType.PROP_BACKGROUND, "#FF8B8B8B"));
			if (i >= contents.size()) continue;
			ItemStack stack = contents.get(i);
			screen.component(new ComponentBuilder("item" + i, ComponentType.ITEM_ICON).bounds(x + 1, y + 1, 16, 16)
				.prop(ComponentType.PROP_ITEM_ID, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
				.prop(ComponentType.PROP_ITEM_COUNT, String.valueOf(stack.getCount()))
				.prop(ComponentType.PROP_TOOLTIP, stack.getHoverName().getString()));
		}
		screen.text("note", PAD, 20 + rows * SLOT + 3, contents.isEmpty() ? "Nothing yet" : "They come home with you");
		// Where the words read are kept now, which a child looking at their prizes may like to see.
		screen.component(new ComponentBuilder("my_words", ComponentType.BUTTON).bounds(width - PAD - 60, 20 + rows * SLOT + 14, 60, 16)
			.prop(ComponentType.PROP_LABEL, "My words"));
		PandoricalApi.screens().open(player, screen.build());
	}
}
