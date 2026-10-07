package justfatlard.learning_blocks.integration;

import java.lang.reflect.Method;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;

/**
 * Chest Utils, when it is loaded, paints a reader's cubby chest in their colour. Reached by
 * reflection on its class name, so this mod needs nothing of it to build or run: without it (or
 * with a Chest Utils that has moved its class) {@link #available()} is false and the cubby is
 * marked another way.
 */
public final class ChestPaint {
	private static final String CLASS = "justfatlard.chest_utils.block.DyedChests";
	private static Method get;
	private static Method paint;
	private static Method strip;
	private static boolean looked;

	/** For tests: as if Chest Utils were not there. */
	public static volatile boolean offForTests;

	private ChestPaint() {
	}

	public static boolean available() {
		if (offForTests) return false;
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("chest-utils")) {
				try {
					Class<?> dyed = Class.forName(CLASS);
					get = dyed.getMethod("get", ServerLevel.class);
					paint = dyed.getMethod("paint", ServerLevel.class, BlockPos.class, String.class);
					strip = dyed.getMethod("strip", ServerLevel.class, BlockPos.class);
				} catch (ReflectiveOperationException | LinkageError missing) {
					LearningBlocks.LOGGER.warn("[{}] Chest Utils is loaded but its chest paint isn't where it was; cubbies get banners", LearningBlocks.MOD_ID);
					get = null;
				}
			}
		}
		return get != null;
	}

	/** Paint the chest here this colour; false if it could not be. */
	public static boolean paint(ServerLevel level, BlockPos pos, DyeColor colour) {
		if (!available()) return false;
		try {
			paint.invoke(get.invoke(null, level), level, pos, colour.getSerializedName());
			return true;
		} catch (ReflectiveOperationException | RuntimeException failed) {
			LearningBlocks.LOGGER.warn("[{}] Painting a cubby failed", LearningBlocks.MOD_ID, failed);
			return false;
		}
	}

	/** Forget the paint on the chest here, as it goes. */
	public static void strip(ServerLevel level, BlockPos pos) {
		if (!available()) return;
		try {
			strip.invoke(get.invoke(null, level), level, pos);
		} catch (ReflectiveOperationException | RuntimeException failed) {
			LearningBlocks.LOGGER.warn("[{}] Unpainting a cubby failed", LearningBlocks.MOD_ID, failed);
		}
	}
}
