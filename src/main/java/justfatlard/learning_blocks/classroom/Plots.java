package justfatlard.learning_blocks.classroom;

import com.mojang.serialization.Codec;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which plot is whose.
 *
 * <p>A child keeps the same plot for good, carried on the player through deaths, and the next plot
 * to hand out is kept on the overworld, which every server has and never unloads. Handing them out
 * in order rather than hashing a UUID is what guarantees no two children ever share a room.
 */
public final class Plots {
	private Plots() {}

	private static final AttachmentType<Integer> PLOT = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.copyOnDeath()
		.buildAndRegister(LearningBlocks.id("plot"));

	private static final AttachmentType<Integer> NEXT = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.buildAndRegister(LearningBlocks.id("next_plot"));

	/**
	 * Every plot handed out, by player, kept on the overworld with the count: one save holds both,
	 * so a crash between saves can never hand the same plot to two children.
	 */
	private static final AttachmentType<java.util.Map<String, Integer>> ASSIGNED = AttachmentRegistry.<java.util.Map<String, Integer>>builder()
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
		.buildAndRegister(LearningBlocks.id("plots"));

	/** When each player last came in, as a day number: for retiring the plots of those long gone. */
	private static final AttachmentType<java.util.Map<String, Long>> SEEN = AttachmentRegistry.<java.util.Map<String, Long>>builder()
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
		.buildAndRegister(LearningBlocks.id("plots_seen"));

	/** Plots given back by retiring, handed out again before new ones. */
	private static final AttachmentType<java.util.List<Integer>> FREE = AttachmentRegistry.<java.util.List<Integer>>builder()
		.persistent(Codec.INT.listOf())
		.buildAndRegister(LearningBlocks.id("plots_free"));

	/** Loads the class, which is what registers the attachments; it must happen at init. */
	public static void init() {}

	/**
	 * This player's plot, handed out the first time it is asked for. The next plot is past every
	 * plot ever handed out, as the overworld and the players themselves remember them.
	 */
	public static int of(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		java.util.Map<String, Integer> assigned = new java.util.HashMap<>(overworld.getAttachedOrElse(ASSIGNED, java.util.Map.of()));
		String id = player.getUUID().toString();
		Integer known = assigned.get(id);
		Integer own = player.getAttached(PLOT);
		seen(overworld, id);
		if (known != null) {
			if (!known.equals(own)) player.setAttached(PLOT, known);
			return known;
		}
		int next = overworld.getAttachedOrElse(NEXT, 0);
		for (int plot : assigned.values()) next = Math.max(next, plot + 1);
		java.util.List<Integer> free = new java.util.ArrayList<>(overworld.getAttachedOrElse(FREE, java.util.List.of()));
		free.removeIf(assigned::containsValue);
		// Their own plot from before, if nobody has it now; else one given back; else a new one.
		int plot = own != null && !assigned.containsValue(own) ? own : !free.isEmpty() ? free.removeFirst() : next;
		free.remove((Integer) plot);
		overworld.setAttached(FREE, java.util.List.copyOf(free));
		assigned.put(id, plot);
		overworld.setAttached(ASSIGNED, java.util.Map.copyOf(assigned));
		overworld.setAttached(NEXT, Math.max(next, plot + 1));
		player.setAttached(PLOT, plot);
		return plot;
	}

	private static void seen(ServerLevel overworld, String id) {
		java.util.Map<String, Long> seen = new java.util.HashMap<>(overworld.getAttachedOrElse(SEEN, java.util.Map.of()));
		long today = justfatlard.learning_blocks.reward.Progress.date().toEpochDay();
		if (Long.valueOf(today).equals(seen.get(id))) return;
		seen.put(id, today);
		overworld.setAttached(SEEN, java.util.Map.copyOf(seen));
	}

	/**
	 * Plots of everyone not seen in this many days given back, to be handed out again: the
	 * classroom is rebuilt fresh for whoever gets one next. Someone who comes back gets a plot
	 * again (their old one, if it is still free). How many were retired.
	 */
	public static int prune(net.minecraft.server.MinecraftServer server, int days) {
		ServerLevel overworld = server.overworld();
		java.util.Map<String, Integer> assigned = new java.util.HashMap<>(overworld.getAttachedOrElse(ASSIGNED, java.util.Map.of()));
		java.util.Map<String, Long> seen = new java.util.HashMap<>(overworld.getAttachedOrElse(SEEN, java.util.Map.of()));
		java.util.List<Integer> free = new java.util.ArrayList<>(overworld.getAttachedOrElse(FREE, java.util.List.of()));
		long today = justfatlard.learning_blocks.reward.Progress.date().toEpochDay();
		int retired = 0;
		for (String id : java.util.List.copyOf(assigned.keySet())) {
			Long last = seen.get(id);
			// Never seen since this was kept: counted from now, not retired at once.
			if (last == null) {
				seen.put(id, today);
				continue;
			}
			if (today - last < days) continue;
			if (server.getPlayerList().getPlayer(java.util.UUID.fromString(id)) != null) continue;
			ServerLevel classroom = server.getLevel(Places.CLASSROOM);
			if (classroom != null && justfatlard.learning_blocks.puzzle.Rooms.standing(classroom, Places.origin(assigned.get(id)))) continue;
			free.add(assigned.remove(id));
			seen.remove(id);
			retired++;
		}
		overworld.setAttached(ASSIGNED, java.util.Map.copyOf(assigned));
		overworld.setAttached(SEEN, java.util.Map.copyOf(seen));
		overworld.setAttached(FREE, java.util.List.copyOf(free));
		return retired;
	}

	public static BlockPos origin(ServerPlayer player) {
		return Places.origin(of(player));
	}
}
