package justfatlard.learning_blocks.reward;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * One thing a child can be given: items, experience, or a roll of a loot table.
 *
 * <p>No commands, on purpose: a prize is something that turns up in a pack, and a command can do
 * anything at all with the server's authority. Anything a prize names that this server has not got
 * - an item from a mod that is not installed, a loot table nobody defined - is left out when the
 * file is read, with a line in the log, exactly as a word naming a missing mob is.
 */
public sealed interface Prize {

	record Items(ItemStack stack) implements Prize {}

	record Experience(int points) implements Prize {}

	record Loot(ResourceKey<LootTable> table) implements Prize {}

	/** A plushie: one in particular, or {@link #RANDOM_NEW}, one the child has not got yet. */
	record Plushie(String which) implements Prize {
		public static final String RANDOM_NEW = "random_new";
	}

	record Toy(String which) implements Prize {}

	/** From the whole toybox: {@code random_new}, a plushie or a toy the child has not got yet. */
	record Toybox() implements Prize {}

	/**
	 * What this prize comes to, right now, for this child: concrete stacks and experience points,
	 * ready to be put aside in their visit. A loot table is rolled here, once, so what is waiting
	 * is exactly what will be handed over.
	 */
	default void resolve(ServerPlayer player, List<ItemStack> items, int[] experience) {
		switch (this) {
			case Items it -> items.add(it.stack().copy());
			case Experience xp -> experience[0] += xp.points();
			case Plushie plushie -> {
				justfatlard.learning_blocks.plush.Plush plush = Progress.of(player).choosePlushie(plushie.which(), player.getRandom());
				if (plush != null) {
					items.add(justfatlard.learning_blocks.plush.Plushies.stack(plush));
					Progress.of(player).ownPlushie(player, plush);
				}
			}
			case Toy wanted -> giveToy(player, wanted.which(), items);
			case Toybox ignored -> {
				if (Progress.of(player).toyboxPicksToy(player.getRandom())) giveToy(player, Plushie.RANDOM_NEW, items);
				else new Plushie(Plushie.RANDOM_NEW).resolve(player, items, experience);
			}
			case Loot loot -> {
				// A loot table that throws as it is rolled costs this roll, nothing else: never the
				// credit, the other prizes, or the lesson.
				try {
					LootTable table = player.level().getServer().reloadableRegistries().getLootTable(loot.table());
					LootParams params = new LootParams.Builder(player.level())
						.withParameter(LootContextParams.ORIGIN, player.position())
						.withParameter(LootContextParams.THIS_ENTITY, player)
						.create(LootContextParamSets.GIFT);
					items.addAll(table.getRandomItems(params));
				} catch (RuntimeException e) {
					LearningBlocks.LOGGER.error("[{}] The loot table {} failed as it was rolled for {}; left out", LearningBlocks.MOD_ID,
						loot.table().identifier(), player.getGameProfile().name(), e);
				}
			}
		}
	}

	private static void giveToy(ServerPlayer player, String which, List<ItemStack> items) {
		justfatlard.learning_blocks.toy.Toy toy = Progress.of(player).chooseToy(which, player.getRandom());
		if (toy != null) {
			items.add(justfatlard.learning_blocks.toy.Toys.stack(toy));
			Progress.of(player).ownToy(player, toy);
		}
	}

	/**
	 * Read a prize list, logging and leaving out whatever is wrong with it.
	 *
	 * @param where the path in the file, for the log line
	 */
	static List<Prize> list(@Nullable JsonElement json, String where, MinecraftServer server, List<String> problems) {
		List<Prize> prizes = new ArrayList<>();
		if (json == null || json.isJsonNull()) return prizes;
		if (!json.isJsonArray()) {
			problems.add(where + ": expected a list of prizes");
			return prizes;
		}
		int index = 0;
		for (JsonElement element : json.getAsJsonArray()) {
			Prize prize = one(element, where + "[" + index++ + "]", server, problems);
			if (prize != null) prizes.add(prize);
		}
		return prizes;
	}

	private static @Nullable Prize one(JsonElement element, String where, MinecraftServer server, List<String> problems) {
		if (!element.isJsonObject()) {
			problems.add(where + ": a prize is an object, like {\"item\": \"minecraft:cookie\"}");
			return null;
		}
		JsonObject json = element.getAsJsonObject();
		try {
			if (json.has("item")) {
				Identifier id = Identifier.parse(json.get("item").getAsString());
				if (!BuiltInRegistries.ITEM.containsKey(id)) {
					problems.add(where + ": no item " + id + " on this server; skipped");
					return null;
				}
				JsonObject stack = new JsonObject();
				stack.addProperty("id", id.toString());
				stack.addProperty("count", json.has("count") ? json.get("count").getAsInt() : 1);
				if (json.has("components")) stack.add("components", json.get("components"));
				DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
				return ItemStack.CODEC.parse(ops, stack)
					.mapOrElse(it -> (Prize) new Items(it), error -> {
						problems.add(where + ": " + error.message() + "; skipped");
						return null;
					});
			}
			if (json.has("xp")) {
				int points = json.get("xp").getAsInt();
				if (points <= 0) {
					problems.add(where + ": xp must be more than nought; skipped");
					return null;
				}
				return new Experience(points);
			}
			if (json.has("loot_table")) {
				ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(json.get("loot_table").getAsString()));
				boolean exists = server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
					.flatMap(tables -> tables.get(key)).isPresent();
				if (!exists) {
					problems.add(where + ": no loot table " + key.identifier() + " on this server; skipped");
					return null;
				}
				return new Loot(key);
			}
			if (json.has("plush")) {
				String which = json.get("plush").getAsString();
				if (!which.equals(Plushie.RANDOM_NEW) && justfatlard.learning_blocks.plush.Plush.of(which) == null) {
					problems.add(where + ": no plushie called " + which + "; skipped");
					return null;
				}
				return new Plushie(which);
			}
			if (json.has("toy")) {
				String which = json.get("toy").getAsString();
				if (!which.equals(Plushie.RANDOM_NEW) && justfatlard.learning_blocks.toy.Toy.of(which) == null) {
					problems.add(where + ": no toy called " + which + "; skipped");
					return null;
				}
				return new Toy(which);
			}
			if (json.has("toybox")) {
				String which = json.get("toybox").getAsString();
				if (!which.equals(Plushie.RANDOM_NEW)) {
					problems.add(where + ": a toybox prize is always random_new, not " + which + "; skipped");
					return null;
				}
				return new Toybox();
			}
			// The word book is gone (the My words screen replaced it): an old entry is quietly skipped.
			if (json.has("word_book")) return null;
		} catch (RuntimeException e) {
			problems.add(where + ": " + e.getMessage() + "; skipped");
			return null;
		}
		problems.add(where + ": a prize needs one of item, xp, loot_table, plush, toy or toybox; skipped");
		LearningBlocks.LOGGER.debug("[{}] Unrecognised prize {}", LearningBlocks.MOD_ID, json);
		return null;
	}
}
