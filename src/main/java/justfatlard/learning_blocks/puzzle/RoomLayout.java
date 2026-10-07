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
 * Where everything in a room is, from its origin, and the walls, board, button and portal that never
 * change between rounds. See {@link Room} for the picture.
 */
record RoomLayout(BlockPos origin) {
	/**
	 * The word row's height above the floor: just over a child's eye, so a title on the screen's
	 * middle sits under the word rather than across it.
	 */
	static final int WORD_Y = 2;

	/** The side walls, either side of the middle. */
	static final int HALF = 9;
	/** The board's wall in front, and the back wall behind the readers. */
	static final int FRONT = -6;
	static final int BACK = 10;
	static final int HEIGHT = justfatlard.learning_blocks.classroom.Places.WALL_HEIGHT;
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	static final BlockState BOARD = Blocks.DYED_TERRACOTTA.green().defaultBlockState();

	/**
	 * Thing {@code i} of {@code n} to click, in an arc round where the child arrives, each as far from
	 * them as the next and well apart: within a child's reach of a thing, and a click aimed at one
	 * never passes through another on the way, as it would along a straight row.
	 */
	Vec3 arcSpot(int i, int n) {
		double angle = (i - (n - 1) / 2.0) * ARC_STEP;
		return spawn().add(Math.sin(angle) * ARC, 0, -Math.cos(angle) * ARC);
	}

	static final double ARC = 2.2;
	static final double ARC_STEP = Math.toRadians(38);

	/** Where the reader whose turn it is stands: just behind the button, the board straight ahead. */
	Vec3 spawn() {
		return Vec3.atBottomCenterOf(origin.offset(0, 0, 3));
	}

	/** The most readers a room is laid out for, and where each stands: the first at the button, the rest in a row behind. */
	static final int MOST_PLACES = 6;

	Vec3 standingSpot(int place) {
		if (place <= 0) return spawn();
		int k = Math.min(place, MOST_PLACES - 1) - 1;
		double x = (k / 2 + 1) * 2.4 - 0.6;
		return Vec3.atBottomCenterOf(origin.offset(0, 0, 5)).add(k % 2 == 0 ? -x : x, 0, 0);
	}

	/** Where a teacher stands: at the back, in front of the portal, everything in view. */
	Vec3 teacherSpot() {
		return Vec3.atBottomCenterOf(origin.offset(0, 0, 7));
	}

	/**
	 * The button stands on a pedestal in the middle, right in front of the turn reader: far enough from
	 * the tile row that every tile shows over it from there.
	 */
	BlockPos pedestal() {
		return origin.offset(0, 0, 2);
	}

	BlockPos button() {
		return pedestal().above();
	}

	/** Just over the button, on the board's side: where everything a word does comes from. */
	Vec3 mouth() {
		return Vec3.atCenterOf(button()).add(0, 0.2, -0.6);
	}

	BlockPos prizeMarker() {
		return prizeMarker(0);
	}

	/** Each reader's cubby, by their place: a row of them along the left wall, facing into the room. */
	BlockPos prizeMarker(int index) {
		return origin.offset(-HALF + 1, 0, -3 + Math.min(index, MOST_PLACES - 1));
	}

	static final int PORTAL_WIDE = 2;
	static final int PORTAL_HIGH = 3;

	BlockPos portalLower() {
		return origin.offset(0, 0, BACK - 1);
	}

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

	/** Tile {@code i} of a word row {@code n} long, centred on the board. */
	BlockPos rowSpot(int i, int n) {
		return origin.offset(i - n / 2, WORD_Y, -4);
	}

	/** Whether a row starting here leaves no room for the picture to its left: the picture goes above it instead. */
	boolean pictureAbove(BlockPos first) {
		return first.getX() - origin.getX() < -HALF + 2;
	}

	BlockPos shelfSpot(int i, int m) {
		return origin.offset(2 * i - (m - 1), 0, -2);
	}

	AABB bounds() {
		return AABB.encapsulatingFullBlocks(origin.offset(-HALF, -1, FRONT), origin.offset(HALF, HEIGHT, BACK)).inflate(1.0);
	}

	boolean inRoom(BlockPos pos) {
		int x = pos.getX() - origin.getX();
		int z = pos.getZ() - origin.getZ();
		int y = pos.getY() - origin.getY();
		return Math.abs(x) < HALF && z > FRONT && z < BACK && y >= 0 && y < HEIGHT;
	}

	boolean onFloor(BlockPos pos) {
		int z = pos.getZ() - origin.getZ();
		return pos.getY() == origin.getY() && Math.abs(pos.getX() - origin.getX()) < HALF && z > FRONT && z < BACK;
	}

	Vec3 farCorner(Vec3 from) {
		double x = from.x > origin.getX() + 0.5 ? origin.getX() - 6 + 0.5 : origin.getX() + 6 + 0.5;
		double z = from.z > origin.getZ() + 0.5 ? origin.getZ() - 2 + 0.5 : origin.getZ() + 6 + 0.5;
		return new Vec3(x, origin.getY(), z);
	}

	/** The side wall away from the child, level with where a creature stands: somewhere to bonk into. */
	Vec3 wallBehind(Vec3 creature, Vec3 child) {
		double x = child.x > origin.getX() + 0.5 ? origin.getX() - HALF + 1.5 : origin.getX() + HALF - 0.5;
		return new Vec3(x, origin.getY(), creature.z);
	}

	void build(ServerLevel level) {
		level.getChunk(SectionPos.blockToSectionCoord(origin.getX()), SectionPos.blockToSectionCoord(origin.getZ()));
		for (Entity entity : level.getEntities((Entity) null, bounds(), entity -> !(entity instanceof Player))) {
			entity.discard();
		}

		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = FRONT; z <= BACK; z++) {
				for (int y = -1; y <= HEIGHT; y++) {
					at.setWithOffset(origin, x, y, z);
					// Walls, not a lid: the sky stays open, so a child's own rain and snow can fall in.
					boolean edge = Math.abs(x) == HALF || z == FRONT || z == BACK;
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

		// The button: red, on a pedestal in the middle of the room, where its presser sees everything.
		// The pedestal is only shown: the block under the button is an unlit light, which holds it up
		// and catches no click, so a click past it at the things on the floor beyond reaches them.
		level.setBlock(pedestal(), Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 0), FLAGS);
		net.minecraft.world.entity.Display.BlockDisplay shown = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level,
			net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (shown != null) {
			shown.setBlockState(Blocks.POLISHED_BLACKSTONE.defaultBlockState());
			shown.snapTo(pedestal().getX(), pedestal().getY(), pedestal().getZ(), 0, 0);
			level.addFreshEntity(shown);
		}
		level.setBlock(button(), Blocks.MANGROVE_BUTTON.defaultBlockState()
			.setValue(ButtonBlock.FACE, AttachFace.FLOOR)
			.setValue(ButtonBlock.FACING, Direction.SOUTH), FLAGS);

		// The portal home, dark and walled until the room says otherwise (Portal.refresh).
		for (BlockPos pos : portalFrame()) level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), FLAGS);
		BlockPos low = portalLower();
		for (int x = 0; x < PORTAL_WIDE; x++) {
			for (int y = 0; y < PORTAL_HIGH; y++) level.setBlock(low.offset(x, y, 0), Blocks.BARRIER.defaultBlockState(), FLAGS);
		}
	}
}
