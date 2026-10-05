package justfatlard.learning_blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Things to do for a player a few ticks from now: the letters of a blend one after another, the
 * word after them, and what the word does after that.
 *
 * <p>Keyed to the player by id and looked up afresh when it comes due, so a child who logs out or
 * walks out of the door halfway through a blend is simply skipped rather than acted on.
 */
public final class Later {
	private Later() {}

	private record Job(UUID player, long due, Consumer<ServerPlayer> what) {}

	private static final List<Job> jobs = new ArrayList<>();
	/** This tick's due jobs, still to run: a job cancelled by one run before it never runs. */
	private static final List<Job> due = new ArrayList<>();
	private static long now;

	public static void after(ServerPlayer player, int ticks, Consumer<ServerPlayer> what) {
		jobs.add(new Job(player.getUUID(), now + Math.max(1, ticks), what));
	}

	/** Forget everything waiting for this player, when their room closes under it. */
	public static void cancel(ServerPlayer player) {
		cancel(player.getUUID());
	}

	/** The same by id: for a player already gone, whose jobs must not wait for a new login. */
	public static void cancel(UUID id) {
		jobs.removeIf(job -> job.player().equals(id));
		due.removeIf(job -> job.player().equals(id));
	}

	public static void tick(MinecraftServer server) {
		now++;
		if (jobs.isEmpty()) return;
		due.clear();
		for (Iterator<Job> it = jobs.iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (job.due() <= now) {
				due.add(job);
				it.remove();
			}
		}
		while (!due.isEmpty()) {
			Job job = due.removeFirst();
			ServerPlayer player = server.getPlayerList().getPlayer(job.player());
			// One job failing is that job gone, never the rest of the tick's or the server.
			if (player != null && !player.isRemoved()) Safely.run("A job for " + player.getGameProfile().name(), () -> job.what().accept(player));
		}
	}

	public static void clear() {
		jobs.clear();
		due.clear();
		now = 0;
	}
}
