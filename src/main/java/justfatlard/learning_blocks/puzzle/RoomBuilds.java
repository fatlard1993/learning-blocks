package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import justfatlard.learning_blocks.ForTests;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.Safely;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * A child's room put up before they are let into it. Its chunks are asked for and load in the
 * background; then its shell goes up a slice a tick, every block already right passed over; and
 * only then does whatever was waiting on it run (the trip in, most often). Somebody whose room is
 * already standing, chunks loaded, gets it on the spot.
 *
 * <p>Nothing here is saved. A build dropped part way (they logged out, the server stopped) leaves
 * a part-built shell on a plot nobody stands on and nothing waiting on it; the next build there
 * starts from whatever it finds.
 */
public final class RoomBuilds {
	private RoomBuilds() {}

	/** The most blocks set in one tick. */
	public static final int SLICE = 400;
	/** How long the chunks have to load before the rest is built the slow way, all at once. */
	static final int WAIT_AT_MOST = 200;

	/** Holds the chunks while a room goes up, lapsing by itself should a build ever be dropped without letting go. */
	private static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, LearningBlocks.id("room"),
		new TicketType(WAIT_AT_MOST + 100L, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

	private static final class Build {
		final ServerPlayer player;
		final ServerLevel level;
		final RoomLayout layout;
		final List<ChunkPos> chunks;
		final List<Consumer<ServerPlayer>> then = new ArrayList<>();
		final long asked;
		final int[] next = new int[1];
		long stepped = -1;

		Build(ServerPlayer player, ServerLevel level, BlockPos origin) {
			this.player = player;
			this.level = level;
			this.layout = new RoomLayout(origin);
			this.chunks = layout.chunks();
			this.asked = level.getServer().getTickCount();
		}
	}

	private static final Map<UUID, Build> builds = new HashMap<>();

	@ForTests
	public static volatile int mostSetInATick;

	/** Loads the class, which registers the ticket type: it must happen at init. */
	public static void init() {}

	/**
	 * The room at this origin made ready for this player, then {@code then}. True if that has
	 * already happened, before this returns; false while it is on its way. Asked again for the same
	 * room while it is on its way, {@code then} waits along with the rest; for another, the first is
	 * dropped.
	 */
	public static boolean request(ServerPlayer player, ServerLevel level, BlockPos origin, Consumer<ServerPlayer> then) {
		Build build = builds.get(player.getUUID());
		if (build != null && (build.player != player || build.level != level || !build.layout.origin().equals(origin))) {
			drop(player.getUUID());
			build = null;
		}
		if (build == null) {
			build = new Build(player, level, origin);
			builds.put(player.getUUID(), build);
			for (ChunkPos chunk : build.chunks) level.getChunkSource().addTicketWithRadius(TICKET, chunk, 1);
		}
		build.then.add(then);
		step(build);
		return !builds.containsKey(player.getUUID());
	}

	/** Whether this player's room is on its way. */
	public static boolean waiting(ServerPlayer player) {
		Build build = builds.get(player.getUUID());
		return build != null && build.player == player;
	}

	/** Their room no longer wanted: nothing waiting on it runs. */
	public static void drop(UUID player) {
		Build build = builds.remove(player);
		if (build != null) release(build);
	}

	public static void tick(MinecraftServer server) {
		if (builds.isEmpty()) return;
		for (Build build : new ArrayList<>(builds.values())) {
			if (builds.get(build.player.getUUID()) != build) continue;
			if (server.getPlayerList().getPlayer(build.player.getUUID()) != build.player || build.player.isRemoved()) {
				drop(build.player.getUUID());
				continue;
			}
			if (!Safely.run(() -> "Building " + build.player.getGameProfile().name() + "'s room", () -> step(build))) drop(build.player.getUUID());
		}
	}

	/** The first wall, floor or board block of the room at this origin not in place, or null once all are (a round writes on the board, and colours the frame over it). */
	@ForTests
	public static @org.jspecify.annotations.Nullable String shellGap(ServerLevel level, BlockPos origin) {
		RoomLayout layout = new RoomLayout(origin);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < RoomLayout.CELLS; i++) {
			net.minecraft.world.level.block.state.BlockState state = layout.shell(i, at);
			if (state.isAir() || state.is(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)) continue;
			net.minecraft.world.level.block.state.BlockState found = level.getBlockState(at);
			if (found != state && !(state == RoomLayout.BOARD && found.is(LearningBlocks.BOARD_LETTER))) return state + " wanted at " + at.subtract(origin).toShortString() + " from the middle, " + found + " there";
		}
		return null;
	}

	/** The room at this origin gone back to empty air, as a plot nobody has used. */
	@ForTests
	public static void knockDown(ServerLevel level, BlockPos origin) {
		RoomLayout layout = new RoomLayout(origin);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < RoomLayout.CELLS; i++) {
			layout.shell(i, at);
			level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), RoomLayout.FLAGS);
		}
	}

	public static void clear() {
		builds.clear();
		mostSetInATick = 0;
	}

	private static void step(Build build) {
		// Once a tick, though asked for twice in it; server ticks, which go on under /tick freeze.
		long now = build.level.getServer().getTickCount();
		if (build.stepped == now) return;
		build.stepped = now;
		// A room already open here with readers in it is left as it is: they are in the middle of a round.
		Room standing = Rooms.at(build.level, build.layout.origin());
		boolean inUse = standing != null && !standing.present().isEmpty() && !standing.leaving();
		if (!inUse) {
			boolean loaded = build.chunks.stream().allMatch(chunk -> build.level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null
				&& build.level.areEntitiesLoaded(ChunkPos.pack(chunk.x(), chunk.z())));
			boolean late = now - build.asked > WAIT_AT_MOST;
			if (!loaded && !late) return;
			if (!loaded) LearningBlocks.LOGGER.warn("[{}] {}'s room was still loading after {} ticks; built at once", LearningBlocks.MOD_ID,
				build.player.getGameProfile().name(), WAIT_AT_MOST);
			int set = build.layout.raise(build.level, build.next, loaded ? SLICE : RoomLayout.CELLS);
			if (loaded) mostSetInATick = Math.max(mostSetInATick, set);
			if (build.next[0] < RoomLayout.CELLS) return;
		}
		builds.remove(build.player.getUUID(), build);
		try {
			for (Consumer<ServerPlayer> then : build.then) {
				Safely.run(() -> "Letting " + build.player.getGameProfile().name() + " into their room", () -> then.accept(build.player));
			}
		} finally {
			release(build);
		}
	}

	private static void release(Build build) {
		for (ChunkPos chunk : build.chunks) build.level.getChunkSource().removeTicketWithRadius(TICKET, chunk, 1);
	}
}
