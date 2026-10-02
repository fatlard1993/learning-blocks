package justfatlard.phonics.toy;

import java.util.EnumMap;
import java.util.Map;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.phonics.Phonics;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Toys: one block, {@code toy}, any of the set by its {@code kind}, and an item for each,
 * {@code toy_<name>}. The cap and the wig are worn as well. Their art is made by
 * {@code generate_toys.py}; the names must match it.
 */
public final class Toys {
	private Toys() {}

	public static final Identifier TOY_ID = Phonics.id("toy");

	public static final ToyBlock TOY = new ToyBlock(BlockBehaviour.Properties.of()
		// Breaks at one punch, like a torch.
		.instabreak()
		.sound(SoundType.WOOD)
		.noOcclusion()
		.setId(ResourceKey.create(Registries.BLOCK, TOY_ID)));

	private static final Map<Toy, ToyItem> items = new EnumMap<>(Toy.class);

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, TOY_ID, TOY);
		PandoricalApi.content().registerBlock(TOY_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:oak_planks")
			.interactive()
			.strength(0.0F)
			.requiresCorrectTool(false));
		for (Toy toy : Toy.values()) {
			Identifier id = Phonics.id("toy_" + toy.word());
			Item.Properties properties = new Item.Properties().stacksTo(16).setId(ResourceKey.create(Registries.ITEM, id));
			if (toy.worn()) properties = properties.equippable(EquipmentSlot.HEAD).stacksTo(1);
			ToyItem item = Registry.register(BuiltInRegistries.ITEM, id, new ToyItem(TOY, toy, properties));
			items.put(toy, item);
			PandoricalApi.content().registerItem(id.toString(), new ItemRegistration().model(Phonics.MOD_ID + ":item/toy_" + toy.word())
				.maxStackSize(toy.worn() ? 1 : 16));
		}
	}

	public static ToyItem item(Toy toy) {
		return items.get(toy);
	}

	public static ItemStack stack(Toy toy) {
		return new ItemStack(items.get(toy));
	}
}
