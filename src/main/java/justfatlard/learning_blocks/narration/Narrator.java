package justfatlard.learning_blocks.narration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Plays narrations: one at a time per player, on the server tick.
 *
 * <p>Starting a narration stops the one before, so the room can always just say the next thing.
 * Leaving the classroom and logging out stop it too. Stopping runs the narration's {@code onStop},
 * so a blend cut off halfway still puts its tiles out.
 *
 * <p>The caption follows along: each step with a caption puts its line up as it starts and lights
 * its words by the clip's timings as the ticks go by; a line is taken down a short while after it
 * ends unless another has replaced it.
 *
 * <p>What was done is written to a short per-player log - {@code light:0}, {@code clip:phoneme/c},
 * {@code outcome} - which is how a test can tell the order things happened in without listening.
 */
public final class Narrator {
	private Narrator() {}

	/** How long a caption stays up after its line ends, if nothing replaces it. */
	private static final int CAPTION_HOLD = 14;
	private static final int LOG_SIZE = 64;

	private static final class Running {
		final Narration narration;
		int index = -1;
		int tick;
		int length;

		Running(Narration narration) {
			this.narration = narration;
		}
	}

	private static final Map<UUID, Running> running = new HashMap<>();

	/**
	 * Who else hears what a player hears: the others reading in the same room. Set by the rooms.
	 * Only the sound and the caption go to them; what a narration does, it does once.
	 */
	private static volatile java.util.function.Function<ServerPlayer, List<ServerPlayer>> companions = player -> List.of();

	public static void hearAlong(java.util.function.Function<ServerPlayer, List<ServerPlayer>> who) {
		companions = who;
	}

	/** This player and everyone hearing along with them. */
	private static List<ServerPlayer> all(ServerPlayer player) {
		List<ServerPlayer> others = companions.apply(player);
		if (others.isEmpty()) return List.of(player);
		List<ServerPlayer> everyone = new ArrayList<>();
		everyone.add(player);
		everyone.addAll(others);
		return everyone;
	}
	/** Ticks left before each player's caption comes down. */
	private static final Map<UUID, Integer> hideIn = new HashMap<>();
	private static final Map<UUID, Deque<String>> logs = new HashMap<>();

	/** Stop whatever this player is hearing and start this. */
	public static void play(ServerPlayer player, Narration narration) {
		stop(player);
		if (narration.isEmpty()) return;
		Running run = new Running(narration);
		running.put(player.getUUID(), run);
		if (!Safely.run("Starting narration for " + player.getGameProfile().name(), () -> advance(player, run))) drop(player, run);
	}

	public static boolean isPlaying(ServerPlayer player) {
		return running.containsKey(player.getUUID());
	}

	/** Stop this player's narration, if any, running its {@code onStop}. The caption is left to fade. */
	public static void stop(ServerPlayer player) {
		Running run = running.remove(player.getUUID());
		if (run == null) return;
		if (run.narration.onStop != null) Safely.run("Stopping a narration", () -> run.narration.onStop.accept(player));
		if (CaptionHud.showing(player)) hideIn.put(player.getUUID(), CAPTION_HOLD);
	}

	/** Stop, and take the caption down now: the child is leaving the room. */
	public static void silence(ServerPlayer player) {
		stop(player);
		hideIn.remove(player.getUUID());
		CaptionHud.hide(player);
	}

	/** Logged out: nothing is sent, nothing is kept. */
	public static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		Running run = running.remove(id);
		if (run != null && run.narration.onStop != null) Safely.run("Stopping a narration on logout", () -> run.narration.onStop.accept(player));
		hideIn.remove(id);
		CaptionHud.forget(id);
		logs.remove(id);
	}

	public static void clear() {
		Sounds.forgetVariants();
		running.clear();
		hideIn.clear();
		logs.clear();
		CaptionHud.clear();
	}

	/** What happened for this player lately, oldest first. */
	public static List<String> log(ServerPlayer player) {
		Deque<String> log = logs.get(player.getUUID());
		return log == null ? List.of() : List.copyOf(log);
	}

	/** Write something to this player's log; the room's own actions call this. */
	public static void note(ServerPlayer player, String what) {
		Deque<String> log = logs.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		log.addLast(what);
		while (log.size() > LOG_SIZE) log.removeFirst();
	}

	public static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, Integer> entry : new ArrayList<>(hideIn.entrySet())) {
			int left = entry.getValue() - 1;
			if (left > 0) {
				entry.setValue(left);
				continue;
			}
			hideIn.remove(entry.getKey());
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) for (ServerPlayer one : all(player)) if (one == player || !running.containsKey(one.getUUID())) CaptionHud.hide(one);
		}

		for (UUID id : new ArrayList<>(running.keySet())) {
			Running run = running.get(id);
			if (run == null) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				running.remove(id);
				continue;
			}
			run.tick++;
			boolean fine = Safely.run("Narration for " + player.getGameProfile().name(), () -> {
				if (run.tick >= run.length) {
					advance(player, run);
				} else {
					for (ServerPlayer one : all(player)) if (CaptionHud.showing(one)) CaptionHud.advance(one, run.tick / 20.0F);
				}
			});
			if (!fine) drop(player, run);
		}
	}

	/** A narration that failed: gone, its stop still run so nothing waits on it for ever. */
	private static void drop(ServerPlayer player, Running run) {
		running.remove(player.getUUID(), run);
		if (run.narration.onStop != null) Safely.run("Stopping a failed narration", () -> run.narration.onStop.accept(player));
	}

	/** Start the next step, or finish. */
	private static void advance(ServerPlayer player, Running run) {
		UUID id = player.getUUID();
		Narration.Step previous = run.index >= 0 ? run.narration.steps.get(run.index) : null;
		if (previous != null && previous.caption() != null) {
			for (ServerPlayer one : all(player)) if (CaptionHud.showing(one)) CaptionHud.advance(one, Float.MAX_VALUE);
		}

		run.index++;
		if (run.index >= run.narration.steps.size()) {
			running.remove(id);
			if (run.narration.onStop != null) Safely.run("Finishing a narration", () -> run.narration.onStop.accept(player));
			if (CaptionHud.showing(player)) hideIn.put(id, CAPTION_HOLD);
			return;
		}

		Narration.Step step = run.narration.steps.get(run.index);
		Narration.Step asking = step;
		boolean[] wanted = {true};
		if (step.when() != null) Safely.run("A narration step's condition", () -> wanted[0] = asking.when().getAsBoolean());
		if (!wanted[0]) {
			// Nothing to say here after all: straight on to the next step.
			advance(player, run);
			return;
		}
		// A line with variants is said one way or another, never twice running the same way; the
		// step takes the chosen one's own length and caption. The log keeps the line's own name.
		String written = step.clip();
		if (written != null && !step.exact()) {
			String chosen = Sounds.variant(player, written);
			if (!chosen.equals(written)) step = step.withClip(chosen);
		}
		run.tick = 0;
		run.length = step.length();

		if (step.action() != null) {
			note(player, step.label());
			Narration.Step acting = step;
			Safely.run("A narration step '" + step.label() + "'", () -> acting.action().accept(player));
			// The action may have started a narration of its own, which replaces this one.
			if (running.get(id) != run) return;
		}

		if (step.clip() == null && step.line() != null) {
			// Nothing to say, something to read: a silent letter.
			hideIn.remove(id);
			note(player, "caption:" + step.line().text());
			for (ServerPlayer one : all(player)) CaptionHud.show(one, step.line(), 0.0F);
		}
		if (step.clip() != null) {
			note(player, "clip:" + written);
			if (!step.clip().equals(written)) note(player, "variant:" + step.clip());
			Narration.Step saying = step;
			Safely.run("Playing " + step.clip(), () -> {
				if (saying.at() != null) Sounds.playAt(player.level(), saying.at(), saying.clip());
				else for (ServerPlayer one : all(player)) Sounds.play(one, saying.clip());
			});
			CaptionLine line = step.caption();
			if (line != null) {
				hideIn.remove(id);
				note(player, "caption:" + line.text());
				for (ServerPlayer one : all(player)) CaptionHud.show(one, line, 0.0F);
			} else if (CaptionHud.showing(player)) {
				// A clip with nothing to read: the last line should not sit there claiming it.
				hideIn.put(id, 1);
			}
		}
	}
}
