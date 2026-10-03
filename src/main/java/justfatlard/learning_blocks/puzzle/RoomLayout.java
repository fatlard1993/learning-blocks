package justfatlard.learning_blocks.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where everything in a room is, from its origin, and the walls, board, button and door that never
 * change between rounds. See {@link Room} for the picture.
 */
record RoomLayout(BlockPos origin) {
	/**
	 * The word row's height above the floor: just over a child's eye, so a title on the screen's
	 * middle sits under the word rather than across it.
	 */
	static final int WORD_Y = 2;

	static final int HALF = 6;
	static final int HEIGHT = justfatlard.learning_blocks.classroom.Places.WALL_HEIGHT;
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	static final BlockState BOARD = Blocks.DYED_TERRACOTTA.green().defaultBlockState();

	/** Where a child is set down: far enough back to see the board and the tiles at once. */
	Vec3 spawn() {
		return Vec3.atBottomCenterOf(origin.offset(0, 0, 3));
	}

	/** The button that reads the word out. */
	BlockPos button() {
		return origin.offset(3, WORD_Y, -4);
	}

	/** Just out from the button's face, where everything a word does comes from. */
	Vec3 mouth() {
		return Vec3.atCenterOf(button()).add(0, 0, 0.7);
	}

	/** Where the prize chest stands while something is waiting to be taken home: beside the door. */
	BlockPos prizeMarker() {
		return prizeMarker(0);
	}

	/** Each reader's chest in a shared room, by their place in it: either side of the portal, then further out. */
	BlockPos prizeMarker(int index) {
		return switch (index) {
			case 0 -> origin.offset(2, 0, 4);
			case 1 -> origin.offset(-2, 0, 4);
			default -> origin.offset(4, 0, 4);
		};
	}

	static final int PORTAL_WIDE = 2;
	static final int PORTAL_HIGH = 3;

	/** The bottom left of the portal home's opening, in the back wall. */
	BlockPos portalLower() {
		return origin.offset(0, 0, 5);
	}

	/** The same, by its old name: where the door was. */
	BlockPos doorLower() {
		return portalLower();
	}

	/** The portal's frame: a post either side and a lintel. */
	java.util.List<BlockPos> portalFrame() {
		java.util.List<BlockPos> frame = new java.util.ArrayList<>();
		BlockPos low = portalLower();
		for (int y = 0; y <= PORTAL_HIGH; y++) {
			frame.add(low.offset(-1, y, 0));
			frame.add(low.offset(PORTAL_WIDE, y, 0));
		}
		for (int x = 0; x < PORTAL_WIDE; x++) frame.add(low.offset(x, PORTAL_HIGH, 0));
		return frame;
	}

	/** Whether this block is the portal: its frame or its opening. */
	boolean isPortal(BlockPos pos) {
		BlockPos low = portalLower();
		int x = pos.getX() - low.getX();
		int y = pos.getY() - low.getY();
		return pos.getZ() == low.getZ() && x >= -1 && x <= PORTAL_WIDE && y >= 0 && y <= PORTAL_HIGH;
	}

	BlockPos creatureSpot() {
		return origin.offset(-3, 0, 1);
	}

	/** Where a scene's character stands: on the floor to the left, in view from where a child stands. */
	BlockPos sceneSpot() {
		return origin.offset(-3, 0, 0);
	}

	/**
	 * Tile {@code i} of a word row {@code n} long, centred on the board; a row long enough to reach
	 * the button moves left, clear of it.
	 */
	BlockPos rowSpot(int i, int n) {
		int last = n - 1 - n / 2;
		int shift = Math.max(0, last - (button().getX() - origin.getX() - 1));
		return origin.offset(i - n / 2 - shift, WORD_Y, -4);
	}

	/** Whether a row starting here leaves no room for the picture to its left: the picture goes above it instead. */
	boolean pictureAbove(BlockPos first) {
		return first.getX() - origin.getX() < -HALF + 2;
	}

	/** Loose tile {@code i} of {@code m} on the floor, spread out in a line. */
	BlockPos shelfSpot(int i, int m) {
		return origin.offset(2 * i - (m - 1), 0, -2);
	}

	AABB bounds() {
		return AABB.encapsulatingFullBlocks(origin.offset(-HALF, -1, -HALF), origin.offset(HALF, HEIGHT, HALF)).inflate(1.0);
	}

	/** Inside the room's walls, where a tile could be set down. */
	boolean inRoom(BlockPos pos) {
		int x = pos.getX() - origin.getX();
		int z = pos.getZ() - origin.getZ();
		int y = pos.getY() - origin.getY();
		return Math.abs(x) < HALF && Math.abs(z) < HALF && y >= 0 && y < HEIGHT;
	}

	/** Standing height, inside the walls. */
	boolean onFloor(BlockPos pos) {
		return pos.getY() == origin.getY() && Math.abs(pos.getX() - origin.getX()) < HALF && Math.abs(pos.getZ() - origin.getZ()) < HALF;
	}

	/** The corner of the floor furthest from here. */
	Vec3 farCorner(Vec3 from) {
		double x = from.x > origin.getX() + 0.5 ? origin.getX() - 4 + 0.5 : origin.getX() + 4 + 0.5;
		double z = from.z > origin.getZ() + 0.5 ? origin.getZ() - 1 + 0.5 : origin.getZ() + 4 + 0.5;
		return new Vec3(x, origin.getY(), z);
	}

	/** The side wall away from the child, level with where a creature stands: somewhere to bonk into. */
	Vec3 wallBehind(Vec3 creature, Vec3 child) {
		double x = child.x > origin.getX() + 0.5 ? origin.getX() - 5 + 0.5 : origin.getX() + 5 + 0.5;
		return new Vec3(x, origin.getY(), creature.z);
	}

	/** Everything that does not change between rounds, on cleared ground. */
	void build(ServerLevel level) {
		level.getChunk(SectionPos.blockToSectionCoord(origin.getX()), SectionPos.blockToSectionCoord(origin.getZ()));
		for (Entity entity : level.getEntities((Entity) null, bounds(), entity -> !(entity instanceof Player))) {
			entity.discard();
		}

		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				for (int y = -1; y <= HEIGHT; y++) {
					at.setWithOffset(origin, x, y, z);
					// Walls, not a lid: the sky stays open, so a child's own rain and snow can fall in.
					boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
					BlockState state;
					if (edge) state = Blocks.BARRIER.defaultBlockState();
					else if (y == -1) state = Blocks.BIRCH_PLANKS.defaultBlockState();
					else if (z == -5 && y <= 4) state = Blocks.DYED_TERRACOTTA.green().defaultBlockState();
					else if (z == -5 && y == 5) state = Blocks.SPRUCE_PLANKS.defaultBlockState();
					else state = Blocks.AIR.defaultBlockState();
					level.setBlock(at, state, FLAGS);
				}
			}
		}

		// The room's air: the classroom's own biome, which can rain or (turned cold) snow.
		justfatlard.learning_blocks.classroom.Sky.paint(level, origin, justfatlard.learning_blocks.classroom.Sky.BIOME);

		level.setBlock(button(), Blocks.STONE_BUTTON.defaultBlockState()
			.setValue(ButtonBlock.FACE, AttachFace.WALL)
			.setValue(ButtonBlock.FACING, Direction.SOUTH), FLAGS);

		// The portal home, dark and walled until the room says otherwise (Portal.refresh).
		for (BlockPos pos : portalFrame()) level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), FLAGS);
		BlockPos low = portalLower();
		for (int x = 0; x < PORTAL_WIDE; x++) {
			for (int y = 0; y < PORTAL_HIGH; y++) level.setBlock(low.offset(x, y, 0), Blocks.BARRIER.defaultBlockState(), FLAGS);
		}
	}
}
