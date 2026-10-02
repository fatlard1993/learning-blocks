package justfatlard.phonics.things;

import java.util.LinkedHashMap;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Props words need that Minecraft has not got and are not toys: a bin, a bathtub, a cardboard
 * box, a jar, a fan, a mop, corn, a mug, a desk, a fork, to put down. Their art is made by
 * {@code generate_things.py}; the names here must match it.
 */
public final class Things {
	private Things() {}

	/** Blocks: their shape facing north, the vanilla block a client without the mod sees, and their feel. */
	private record Spec(double[] box, String base, SoundType sound, float strength) {}

	private static final Map<String, Spec> BLOCKS = new LinkedHashMap<>();
	static {
		BLOCKS.put("bin", new Spec(new double[] {3, 0, 3, 13, 14, 13}, "minecraft:light_gray_concrete", SoundType.METAL, 0.5F));
		BLOCKS.put("tub", new Spec(new double[] {0.5, 0, 2, 15.5, 10, 14}, "minecraft:white_concrete", SoundType.STONE, 0.6F));
		BLOCKS.put("box", new Spec(new double[] {2, 0, 2, 14, 9, 14}, "minecraft:sandstone", SoundType.WOOD, 0.4F));
		BLOCKS.put("jar", new Spec(new double[] {5, 0, 5, 11, 10, 11}, "minecraft:glass", SoundType.GLASS, 0.3F));
		BLOCKS.put("fan", new Spec(new double[] {4.5, 0, 5, 11.5, 15, 11}, "minecraft:white_concrete", SoundType.METAL, 0.4F));
		BLOCKS.put("mop", new Spec(new double[] {5, 0, 5, 11, 16, 11}, "minecraft:white_wool", SoundType.WOOL, 0.3F));
		BLOCKS.put("corn", new Spec(new double[] {1, 0, 5, 13.6, 5, 11}, "minecraft:hay_block", SoundType.GRASS, 0.2F));
		BLOCKS.put("mug", new Spec(new double[] {5, 0, 5, 13, 7, 11}, "minecraft:white_concrete", SoundType.STONE, 0.3F));
		BLOCKS.put("desk", new Spec(new double[] {1, 0, 2.5, 15, 11, 13.5}, "minecraft:oak_planks", SoundType.WOOD, 0.6F));
		BLOCKS.put("fork", new Spec(new double[] {2, 0, 2, 14, 1.2, 14}, "minecraft:iron_block", SoundType.METAL, 0.2F));
	}

	private static final Map<String, Item> registered = new LinkedHashMap<>();

	public static void register() {
		for (Map.Entry<String, Spec> entry : BLOCKS.entrySet()) {
			Identifier id = Phonics.id(entry.getKey());
			Spec spec = entry.getValue();
			ThingBlock block = Registry.register(BuiltInRegistries.BLOCK, id, new ThingBlock(BlockBehaviour.Properties.of()
				.strength(spec.strength(), 2.0F).sound(spec.sound()).noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, id)), spec.box()));
			Item item = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
			registered.put(entry.getKey(), item);
			BlockRegistration registration = new BlockRegistration().baseBlock(spec.base()).strength(spec.strength()).requiresCorrectTool(false);
			PandoricalApi.content().registerBlock(id.toString(), registration);
			PandoricalApi.content().registerItem(id.toString(), new ItemRegistration().model(Phonics.MOD_ID + ":item/" + entry.getKey()));
		}
	}

	/** Every thing's item, by name: for tests and the review. */
	public static Map<String, Item> all() {
		return java.util.Collections.unmodifiableMap(registered);
	}
}
