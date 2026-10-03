package justfatlard.learning_blocks.editor;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.reward.PrizeFile;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A prize list's items are made by holding them, the way PvP Dimensions makes kits: the operator's
 * own pack is put aside, the list is laid out in its place, they arrange it from the creative menu
 * or from chests and say done, and whatever they are holding is the list. Their own pack comes back.
 *
 * <p>Items keep everything about them - a name, enchantments, a dye - because they are written with
 * the game's own item codec, components and all.
 *
 * <p>The pack put aside is kept on the player, persistent and through deaths, like a classroom
 * visit's: a crash or a disconnect in the middle of editing loses nothing, and the next login says
 * how to finish. Editing and the classroom exclude each other both ways, since both put the pack
 * aside and a player has only the one.
 *
 * <p>Only the list's item entries are replaced. Experience and loot tables stay, and
 * so does an item this server cannot make - a modded item from a mod not installed - which was never
 * laid out and so cannot have been removed by the person holding the list.
 */
public final class PrizeSessions {
	private PrizeSessions() {}

	public record Editing(String prizes, String tab, List<Visit.Slot> stash) {
		static final Codec<Editing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("prizes").forGetter(Editing::prizes),
			Codec.STRING.optionalFieldOf("tab", "EVENTS").forGetter(Editing::tab),
			Visit.Slot.CODEC.listOf().fieldOf("stash").forGetter(Editing::stash)
		).apply(instance, Editing::new));
	}

	private static final AttachmentType<Editing> ATTACHMENT = AttachmentRegistry.<Editing>builder()
		.persistent(Editing.CODEC)
		.copyOnDeath()
		.buildAndRegister(LearningBlocks.id("editing_prizes"));

	/** The same, as saved before the rename; moved to the new one as the player joins. */
	private static final AttachmentType<Editing> OLD_ATTACHMENT = AttachmentRegistry.<Editing>builder()
		.persistent(Editing.CODEC)
		.copyOnDeath()
		.buildAndRegister(justfatlard.learning_blocks.migrate.OldNames.old("editing_prizes"));

	public static void migrate(ServerPlayer player) {
		justfatlard.learning_blocks.migrate.OldNames.move(player, OLD_ATTACHMENT, ATTACHMENT, Editing.CODEC, player.level().registryAccess(),
			player.getGameProfile().name());
	}

	/** Loads the class, which is what registers the attachment; it must happen at init. */
	public static void init() {}

	public static boolean editing(ServerPlayer player) {
		return player.getAttached(ATTACHMENT) != null;
	}

	public static @Nullable Editing of(ServerPlayer player) {
		return player.getAttached(ATTACHMENT);
	}

	/** Put the pack aside and lay out the list at this path. What went wrong, or null. */
	public static @Nullable String start(ServerPlayer player, String prizes, String tab) {
		if (!Admins.may(player)) return "Only operators can change prizes";
		if (Visit.of(player) != null) return "Leave the classroom first; prizes are made outside";
		if (editing(player)) return "You're already editing a prize list; finish that first";
		MinecraftServer server = player.level().getServer();
		JsonArray list;
		try {
			list = PrizeFile.array(PrizeFile.read(), prizes);
		} catch (Exception e) {
			return "The prizes file could not be read: " + e.getMessage();
		}

		// Written before anything is taken, as the classroom's visit is.
		player.setAttached(ATTACHMENT, new Editing(prizes, tab, Visit.pack(player)));
		player.getInventory().clearContent();
		List<Visit.Slot> laid = new ArrayList<>();
		int slot = 0;
		for (ItemStack stack : items(list, server)) laid.add(new Visit.Slot(slot++, stack));
		Visit.lay(player, laid);
		player.containerMenu.broadcastChanges();

		player.sendSystemMessage(line("Editing the prizes for " + PrizeEditor.describe(prizes)
			+ ". Hold every item that should be given, from the creative menu or from chests; where it sits"
			+ " doesn't matter. Names and enchantments are kept. ")
			.append(button("Done", "/learn prizes done")).append(" ").append(button("Cancel", "/learn prizes cancel")));
		return null;
	}

	/** What they hold becomes the list's items; their own pack comes back. */
	public static @Nullable String done(ServerPlayer player) {
		Editing editing = of(player);
		if (editing == null) return "You aren't editing any prizes";
		MinecraftServer server = player.level().getServer();
		List<ItemStack> held = new ArrayList<>();
		for (Visit.Slot slot : Visit.pack(player)) held.add(slot.stack());
		List<String> problems = PrizeEditor.change(server, tree -> {
			JsonArray list = PrizeFile.array(tree, editing.prizes());
			JsonArray kept = new JsonArray();
			for (JsonObject entry : entries(held, server)) kept.add(entry);
			for (JsonElement old : list) if (!laidOut(old)) kept.add(old);
			PrizeFile.set(tree, editing.prizes(), kept);
		});
		restore(player, editing);
		player.sendSystemMessage(line(problems == null ? "The prizes file could not be written; nothing changed"
			: "Saved " + held.size() + (held.size() == 1 ? " item" : " items") + " for " + PrizeEditor.describe(editing.prizes())));
		if (PrizeEditor.canShow(player)) PrizeEditor.show(player, editing.tab());
		return null;
	}

	public static @Nullable String cancel(ServerPlayer player) {
		Editing editing = of(player);
		if (editing == null) return "You aren't editing any prizes";
		restore(player, editing);
		player.sendSystemMessage(line("Nothing changed"));
		return null;
	}

	/** The pack exactly as it was put aside, and the session over. */
	private static void restore(ServerPlayer player, Editing editing) {
		player.getInventory().clearContent();
		Visit.lay(player, editing.stash());
		player.containerMenu.broadcastChanges();
		player.removeAttached(ATTACHMENT);
	}

	public static void loggedIn(ServerPlayer player) {
		if (!editing(player)) return;
		player.sendSystemMessage(line("You were in the middle of editing prizes; your own pack is put aside. ")
			.append(button("Done", "/learn prizes done")).append(" ").append(button("Cancel", "/learn prizes cancel")));
	}

	// --- Items and their entries ---

	/** The item entries of a prize list, as stacks; entries this server cannot make are left out. */
	static List<ItemStack> items(JsonArray list, MinecraftServer server) {
		DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		List<ItemStack> stacks = new ArrayList<>();
		for (JsonElement element : list) {
			if (!laidOut(element)) continue;
			JsonObject entry = element.getAsJsonObject();
			JsonObject stack = new JsonObject();
			stack.add("id", entry.get("item"));
			stack.addProperty("count", entry.has("count") ? entry.get("count").getAsInt() : 1);
			if (entry.has("components")) stack.add("components", entry.get("components"));
			ItemStack.CODEC.parse(ops, stack).ifSuccess(stacks::add);
		}
		return stacks;
	}

	/** Stacks as prize entries, {@code {"item", "count", "components"}}. */
	static List<JsonObject> entries(List<ItemStack> stacks, MinecraftServer server) {
		DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		List<JsonObject> entries = new ArrayList<>();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) continue;
			ItemStack.CODEC.encodeStart(ops, stack).ifSuccess(json -> {
				JsonObject encoded = json.getAsJsonObject();
				JsonObject entry = new JsonObject();
				entry.add("item", encoded.get("id"));
				entry.addProperty("count", stack.getCount());
				if (encoded.has("components")) entry.add("components", encoded.get("components"));
				entries.add(entry);
			});
		}
		return entries;
	}

	/** An item entry this server can make, which is to say one that was laid out to be edited. */
	static boolean laidOut(JsonElement element) {
		if (!element.isJsonObject() || !element.getAsJsonObject().has("item")) return false;
		Identifier id = Identifier.tryParse(element.getAsJsonObject().get("item").getAsString());
		return id != null && BuiltInRegistries.ITEM.containsKey(id);
	}

	private static MutableComponent line(String words) {
		return Component.literal(words).withStyle(ChatFormatting.YELLOW);
	}

	private static MutableComponent button(String label, String command) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}
}
