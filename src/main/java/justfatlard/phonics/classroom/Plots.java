package justfatlard.phonics.classroom;

import com.mojang.serialization.Codec;
import justfatlard.phonics.Phonics;
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
		.buildAndRegister(Phonics.id("plot"));

	private static final AttachmentType<Integer> NEXT = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.buildAndRegister(Phonics.id("next_plot"));

	/**
	 * Every plot handed out, by player, kept on the overworld with the count: one save holds both,
	 * so a crash between saves can never hand the same plot to two children.
	 */
	private static final AttachmentType<java.util.Map<String, Integer>> ASSIGNED = AttachmentRegistry.<java.util.Map<String, Integer>>builder()
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
		.buildAndRegister(Phonics.id("plots"));

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
		if (known != null) {
			if (!known.equals(own)) player.setAttached(PLOT, known);
			return known;
		}
		int next = overworld.getAttachedOrElse(NEXT, 0);
		for (int plot : assigned.values()) next = Math.max(next, plot + 1);
		// Handed out before the overworld kept them all: it is theirs, and recorded now.
		int plot = own != null && !assigned.containsValue(own) ? own : next;
		assigned.put(id, plot);
		overworld.setAttached(ASSIGNED, java.util.Map.copyOf(assigned));
		overworld.setAttached(NEXT, Math.max(next, plot + 1));
		player.setAttached(PLOT, plot);
		return plot;
	}

	public static BlockPos origin(ServerPlayer player) {
		return Places.origin(of(player));
	}
}
