package justfatlard.learning_blocks.migrate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

/**
 * From when this mod was Phonics: everything saved under {@code phonics-justfatlard} carries over
 * to {@code learning-blocks-justfatlard}, and nothing is dropped on the way.
 *
 * <ul>
 * <li>Blocks and items answer to their old ids too (Fabric's registry aliases), so a plushie, a toy,
 * a letter tile or a prop placed in a world, or held in an inventory, a chest or a shulker box,
 * loads as itself.
 * <li>Each player attachment and the overworld's is registered under its old id as well; whatever
 * is found there is moved to the new one the moment it can be (a player as they join, the
 * overworld when the server starts), every old id written inside it renamed, and the old one
 * removed. Anything that will not read under the new id is moved exactly as it was.
 * <li>{@code config/phonics/} is copied to {@code config/learning-blocks/} if there is no new
 * folder yet, the old one kept as a backup.
 * </ul>
 */
public final class OldNames {
	private OldNames() {}

	public static final String OLD = "phonics-justfatlard";

	public static Identifier old(String path) {
		return Identifier.fromNamespaceAndPath(OLD, path);
	}

	/** The classroom as it was saved before the rename; a dimension that no longer exists. */
	public static final String OLD_CLASSROOM = OLD + ":classroom";
	private static final java.util.Set<java.util.UUID> inOldClassroom = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static void savedInOldClassroom(net.minecraft.server.level.ServerPlayer player) {
		inOldClassroom.add(player.getUUID());
	}

	/** Whether this player was saved inside the old classroom; asking forgets it. */
	public static boolean takeSavedInOldClassroom(net.minecraft.server.level.ServerPlayer player) {
		return inOldClassroom.remove(player.getUUID());
	}

	/** Every block and item of ours, also by its old id. Call once everything is registered. */
	public static void aliasRegistries() {
		int blocks = alias(BuiltInRegistries.BLOCK);
		int items = alias(BuiltInRegistries.ITEM);
		LearningBlocks.LOGGER.info("[{}] {} blocks and {} items also answer to their old {} ids", LearningBlocks.MOD_ID, blocks, items, OLD);
	}

	private static <T> int alias(Registry<T> registry) {
		int count = 0;
		for (Identifier id : List.copyOf(registry.keySet())) {
			if (!id.getNamespace().equals(LearningBlocks.MOD_ID)) continue;
			((net.fabricmc.fabric.api.event.registry.FabricRegistry) registry).addAlias(old(id.getPath()), id);
			count++;
		}
		return count;
	}

	/** Old ids, written anywhere inside a saved value, as new ones. */
	public static String renamed(String text) {
		return text.replace(OLD + ":", LearningBlocks.MOD_ID + ":");
	}

	/**
	 * What was saved under the old attachment, moved to the new one with the old ids inside it
	 * renamed: through its own codec to JSON and back. If it will not go through (or come back),
	 * it moves as it was, so nothing is lost. If the new one is already there, the old is left
	 * where it is and said so: neither is thrown away.
	 */
	public static <T> boolean move(AttachmentTarget target, AttachmentType<T> old, AttachmentType<T> now, Codec<T> codec,
			HolderLookup.Provider registries, String what) {
		T value = target.getAttached(old);
		if (value == null) return false;
		if (target.getAttached(now) != null) {
			LearningBlocks.LOGGER.warn("[{}] {} has both an old and a new {}; the new one is kept, the old one left in place", LearningBlocks.MOD_ID, what, old.identifier().getPath());
			return false;
		}
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		T moved = value;
		DataResult<JsonElement> written = codec.encodeStart(ops, value);
		if (written.result().isPresent()) {
			JsonElement json = JsonParser.parseString(renamed(written.result().get().toString()));
			DataResult<T> read = codec.parse(ops, json);
			if (read.result().isPresent()) moved = read.result().get();
			else LearningBlocks.LOGGER.warn("[{}] {}'s {} did not read back with its ids renamed ({}); moved as it was", LearningBlocks.MOD_ID, what,
				old.identifier().getPath(), read.error().map(Object::toString).orElse("?"));
		} else {
			LearningBlocks.LOGGER.warn("[{}] {}'s {} could not be written out to rename its ids; moved as it was", LearningBlocks.MOD_ID, what, old.identifier().getPath());
		}
		target.setAttached(now, moved);
		target.removeAttached(old);
		LearningBlocks.LOGGER.info("[{}] Moved {}'s {} to {}", LearningBlocks.MOD_ID, what, old.identifier(), now.identifier());
		return true;
	}

	/** Everything a player has saved under the old name, moved: as they join, before anything reads it. */
	public static void migratePlayer(net.minecraft.server.level.ServerPlayer player) {
		justfatlard.learning_blocks.reward.Progress.migrate(player);
		justfatlard.learning_blocks.classroom.Visit.migrate(player);
		justfatlard.learning_blocks.classroom.Plots.migrate(player);
		justfatlard.learning_blocks.editor.PrizeSessions.migrate(player);
		justfatlard.learning_blocks.narration.CaptionSettings.migrate(player);
	}

	/** config/phonics/ to config/learning-blocks/, if there is no new folder yet. The old one is left as a backup. */
	public static void copyConfig() {
		copyConfig(FabricLoader.getInstance().getConfigDir());
	}

	/** The same, in this config folder. True if it copied anything. */
	public static boolean copyConfig(Path root) {
		Path from = root.resolve("phonics");
		Path to = root.resolve("learning-blocks");
		if (Files.exists(to) || !Files.isDirectory(from)) return false;
		try (var walk = Files.walk(from)) {
			for (Path file : walk.toList()) {
				Path target = to.resolve(from.relativize(file).toString());
				if (Files.isDirectory(file)) Files.createDirectories(target);
				else Files.copy(file, target);
			}
			LearningBlocks.LOGGER.info("[{}] Copied the old settings from {} to {}; the old folder is kept as a backup and can be deleted",
				LearningBlocks.MOD_ID, from, to);
			return true;
		} catch (IOException e) {
			LearningBlocks.LOGGER.error("[{}] Could not copy the old settings from {} to {}: {}", LearningBlocks.MOD_ID, from, to, e.getMessage());
			return false;
		}
	}
}
