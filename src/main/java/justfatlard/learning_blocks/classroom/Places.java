package justfatlard.learning_blocks.classroom;

import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The classroom dimension, and where each child's room sits in it.
 *
 * <p>One dimension, made by the datapack when the world loads, and a plot in it for every player
 * who has ever gone in. Plots are a region file apart: far past anybody's view distance, so no
 * child ever sees another's room, and each one a region file of its own, so a plot can be cleared
 * file by file while the server is down.
 */
public final class Places {
	private Places() {}

	public static final ResourceKey<Level> CLASSROOM = ResourceKey.create(Registries.DIMENSION, LearningBlocks.id("classroom"));

	/** One region file. A room is well inside one chunk. */
	public static final int SPACING = 512;
	private static final int PER_ROW = 256;
	/** Clear of the middle, where nothing ever is, so an origin never lands on a seam. */
	private static final int OFFSET = 4 * SPACING;

	public static final int FLOOR_Y = 64;
	/** How high a room's walls stand over its floor; it has no lid, for its sky. */
	public static final int WALL_HEIGHT = 6;

	public static boolean isClassroom(Level level) {
		return level.dimension().equals(CLASSROOM);
	}

	public static boolean isClassroom(ResourceKey<Level> dimension) {
		return dimension.equals(CLASSROOM);
	}

	/**
	 * The middle of a plot's room, at standing height. The middle of a chunk rather than the corner
	 * of one, so a room thirteen blocks across is one chunk to load and one to save.
	 */
	public static BlockPos origin(int plot) {
		int x = OFFSET + (plot % PER_ROW) * SPACING + 8;
		int z = OFFSET + (plot / PER_ROW) * SPACING + 8;
		return new BlockPos(x, FLOOR_Y, z);
	}
}
