package justfatlard.learning_blocks.plush;

import java.util.EnumMap;
import java.util.Map;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Plushies as items: one item for each, {@code plush_<name>}, all putting down the one plush block. */
public final class Plushies {
	private Plushies() {}

	private static final Map<Plush, PlushItem> items = new EnumMap<>(Plush.class);

	public static void register() {
		for (Plush plush : Plush.values()) {
			Identifier id = LearningBlocks.id("plush_" + plush.word());
			PlushItem item = Registry.register(BuiltInRegistries.ITEM, id, new PlushItem(LearningBlocks.PLUSH, plush, new Item.Properties()
				.stacksTo(16).setId(ResourceKey.create(Registries.ITEM, id))));
			items.put(plush, item);
			PandoricalApi.content().registerItem(id.toString(), new ItemRegistration().model(LearningBlocks.MOD_ID + ":item/plush_" + plush.word()).maxStackSize(16));
		}
	}

	public static PlushItem item(Plush plush) {
		return items.get(plush);
	}

	public static ItemStack stack(Plush plush) {
		return new ItemStack(items.get(plush));
	}
}
