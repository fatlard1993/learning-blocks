package justfatlard.learning_blocks.classroom;

import java.util.Set;
import java.util.UUID;
import justfatlard.learning_blocks.Later;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.editor.PrizeSessions;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.reward.Prizes;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Going into the classroom and coming out of it, and the backstops for every way somebody might
 * cross without using the portal.
 *
 * <p>There is one way in, {@link #enter}, and one way out, {@link #leave}. Both the command and the
 * portal inside the room come through them, and every backstop ends up in one or the other, so what
 * is put aside and what is given back are decided in exactly one place each.

 */
public final class Travel {
	private Travel() {}


	/**
	 * Into this player's own room. What went wrong, said to them, or null once they are on their way.
	 *
	 * <p>The visit is written before anything is taken. If the server stops between the two, the
	 * worst case is a visit for somebody still holding their own things, which the login check
	 * hands back to them; the other order could leave a child with nothing and no record of it.
	 */
	public static @Nullable String enter(ServerPlayer player) {
		return enter(player, null);
	}

	/**
	 * Into another reader's room, to read together: from outside, the whole way in (their own
	 * stash, lessons and prizes, as ever); already in the classroom, across into it. What went
	 * wrong, or null.
	 */
	public static @Nullable String readWith(ServerPlayer player, Room room) {
		if (room.leaving()) return "That classroom is closing";
		if (Rooms.of(player) == room) return null;
		if (room.members().size() >= justfatlard.learning_blocks.reward.PrizeConfig.maxReaders()) return "That classroom is full";
		if (Visit.of(player) != null && Places.isClassroom(player.level())) {
			if (!Rooms.join(player, room)) return "That classroom is full";
			Vec3 at = room.spawnFor(player.getUUID());
			player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, Set.of(), Room.FACING_YAW, 0, true);
			Rooms.begin(player);
			return null;
		}
		return enter(player, room);
	}

	/**
	 * Into a child's room as its teacher: the ordinary way in, things put aside and given back on the
	 * way out, but no lessons to keep them, no cubby and nothing to earn. What went wrong, or null.
	 */
	public static @Nullable String teach(ServerPlayer teacher, ServerPlayer child) {
		Room room = Rooms.of(child);
		if (room == null || !Places.isClassroom(child.level())) return child.getGameProfile().name() + " isn't in the classroom";
		if (room.isTeacher(child.getUUID())) return child.getGameProfile().name() + " is teaching, not reading";
		if (room.leaving()) return "That classroom is closing";
		if (Rooms.of(teacher) == room) return null;
		if (Visit.of(teacher) != null && Places.isClassroom(teacher.level())) {
			Room own = Rooms.of(teacher);
			if (own != null && !own.isTeacher(teacher.getUUID())) return "Leave your own room first";
			Rooms.teach(teacher, room);
			Visit.set(teacher, Visit.of(teacher).withLessons(0));
			Vec3 at = room.spawnFor(teacher.getUUID());
			teacher.teleportTo((ServerLevel) teacher.level(), at.x, at.y, at.z, Set.of(), Room.FACING_YAW, 0, true);
			Rooms.begin(teacher);
			return null;
		}
		return enter(teacher, room, true);
	}

	/**
	 * Out of a shared room into their own, still in the classroom with everything they had: the
	 * pair split up. What went wrong, or null.
	 */
	public static @Nullable String readAlone(ServerPlayer player) {
		Room room = Rooms.of(player);
		if (room == null || !Places.isClassroom(player.level())) return "not in the classroom";
		if (!room.shared()) return null;
		Rooms.close(player);
		Room own = Rooms.open(player, (ServerLevel) player.level(), Plots.origin(player));
		Vec3 at = own.spawnFor(player.getUUID());
		player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, Set.of(), Room.FACING_YAW, 0, true);
		Rooms.begin(player);
		return null;
	}

	private static @Nullable String enter(ServerPlayer player, @Nullable Room joining) {
		return enter(player, joining, false);
	}

	private static @Nullable String enter(ServerPlayer player, @Nullable Room joining, boolean teaching) {
		MinecraftServer server = player.level().getServer();
		if (Visit.of(player) != null || Places.isClassroom(player.level())) return "You're already in the classroom";
		if (player.isDeadOrDying()) return "Not just now";
		if (PrizeSessions.editing(player)) return "Finish editing prizes first: /learn prizes done";
		ServerLevel classroom = server.getLevel(Places.CLASSROOM);
		if (classroom == null) {
			LearningBlocks.LOGGER.error("[{}] The classroom dimension is missing; is the mod's datapack disabled?", LearningBlocks.MOD_ID);
			return "The classroom isn't there. Ask a grown-up to check the server.";
		}

		if (player.isPassenger()) player.stopRiding();
		// A crafting table, an anvil or a furnace left open would give its grid back into the
		// classroom's empty pack, to be cleared on the way out: closed first, it goes home in the stash.
		player.closeContainer();

		Visit.set(player, teaching ? Visit.snapshot(player).withLessons(0) : Visit.snapshot(player));

		// From here, anything going wrong puts everything back: no child is left half in.
		try {
			player.getInventory().clearContent();
			Visit.clearHands(player);
			new Visit.Experience(0, 0, 0).restore(player);
			player.removeAllEffects();
			player.setGameMode(GameType.SURVIVAL);
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5);
			player.setRemainingFireTicks(0);
			player.containerMenu.broadcastChanges();

			Room room;
			if (teaching && joining != null) Rooms.teach(player, room = joining);
			else if (joining != null && Rooms.join(player, joining)) room = joining;
			else room = Rooms.open(player, classroom, Plots.origin(player));
			if (!teaching) Prizes.entered(player);
			Vec3 spawn = room.spawnFor(player.getUUID());
			var arrived = own(() -> player.teleport(new TeleportTransition(classroom, spawn, Vec3.ZERO, Room.FACING_YAW, 0, Set.of(),
				entity -> {
					if (entity instanceof ServerPlayer there) Rooms.begin(there);
				})));
			if (arrived == null) throw new IllegalStateException("the teleport into the classroom did not happen");
		} catch (Throwable failure) {
			if (failure instanceof VirtualMachineError && !(failure instanceof StackOverflowError)) throw (VirtualMachineError) failure;
			LearningBlocks.LOGGER.error("[{}] {} could not get into the classroom; everything is handed back", LearningBlocks.MOD_ID,
				player.getGameProfile().name(), failure);
			Visit visit = Visit.of(player);
			if (visit != null) justfatlard.learning_blocks.Safely.run("Handing back after a failed way in", () -> handBack(player, visit));
			return "The classroom couldn't open just now. Your things are all back.";
		}
		return null;
	}


	/**
	 * Out and home, by the portal or the command. False when they were not in.
	 *
	 * <p>A child with prizes waiting hears "You earned a prize!" in the room first, and goes home
	 * when it has been said: said before the trip, since what is playing and showing in the
	 * classroom does not follow anybody through it. The trip is on a timer, not a step of the
	 * line's narration: anything that starts another narration would stop that one, and with it
	 * the way home. From here the room starts nothing new. Going twice is harmless: with no visit
	 * left, going home does nothing.
	 */
	public static boolean leave(ServerPlayer player) {
		Visit visit = Visit.of(player);
		if (visit == null) return false;
		triedToLeave(player);
		Room room = Rooms.of(player);
		if (room != null && room.leaving(player)) {
			// Already on the way: going again is harmless, and makes sure of it.
			Later.after(player, 5, Travel::goHome);
			return true;
		}
		if (room != null) room.beginLeaving(player);
		if (Places.isClassroom(player.level()) && visit.hasPrizes()) {
			Narrator.play(player, new Narration().say("phrase/you_earned_a_prize"));
			Later.after(player, Sounds.durationTicks("phrase/you_earned_a_prize") + 5, Travel::goHome);
			return true;
		}
		goHome(player);
		return true;
	}

	/**
	 * The whole way out, in one tick: the stash back, the trip home, then the prizes where they
	 * stand, and only then the visit ended. Nothing saves in the middle of a tick,
	 * so no save ever sees the prizes both waiting and handed over, or neither.
	 */
	private static void goHome(ServerPlayer player) {
		goHome(player, false);
	}

	private static void goHome(ServerPlayer player, boolean forced) {
		// Dead or dying: nothing handed to a body survives the respawn, which does this itself.
		if (player.isDeadOrDying() || player.isRemoved()) return;
		if (wedgedForTests && !forced) return;
		Visit visit = Visit.of(player);
		if (visit == null) return;
		boolean inside = Places.isClassroom(player.level());
		giveBack(player, visit);
		if (inside) own(() -> player.teleport(home(player.level().getServer(), visit.home())));
		settle(player);
	}

	/** Home now, by whoever notices a leave taking too long, or a room that broke. */
	public static void goHomeNow(ServerPlayer player) {
		goHome(player);
	}

	/** Every ordinary way home does nothing, as if something were wedged; only {@link #forceHome} gets through. */
	public static volatile boolean wedgedForTests;

	/**
	 * Home right now, whatever keeps them in: lessons, the portal's lock, a leave that never
	 * finished. By the same way out as the portal (their things back, the trip, the prizes, the visit
	 * ended), with the prize line skipped. The lessons left on this visit go; the daily schedule
	 * does not, so a day not yet done sends them in again at their next login. False if they had
	 * no visit to end.
	 */
	public static boolean forceHome(ServerPlayer player) {
		Visit visit = Visit.of(player);
		if (visit == null) return false;
		if (visit.lessons() > 0) Visit.set(player, visit.withLessons(0));
		Narrator.stop(player);
		goHome(player, true);
		attempts.remove(player.getUUID());
		return Visit.of(player) == null;
	}


	/** How long tries to leave are remembered, how many in that time mean stuck, and how long after the last. */
	static final int TRIES_WITHIN = 30 * 20;
	static final int TRIES_STUCK = 3;
	static final int STUCK_AFTER = 10 * 20;

	/** When each child last tried to leave (the portal, a click on it, Go home, /learn leave), by game time. */
	private static final java.util.Map<java.util.UUID, java.util.ArrayDeque<Long>> attempts = new java.util.HashMap<>();

	public static volatile @Nullable String lastStuck;

	private static void triedToLeave(ServerPlayer player) {
		long now = player.level().getGameTime();
		java.util.ArrayDeque<Long> tries = attempts.computeIfAbsent(player.getUUID(), id -> new java.util.ArrayDeque<>());
		tries.addLast(now);
		while (!tries.isEmpty() && now - tries.peekFirst() > TRIES_WITHIN) tries.removeFirst();
	}

	/**
	 * Each tick in the classroom: a child free to go who has tried to leave three times in thirty
	 * seconds and is still here ten seconds after the last is stuck, whatever the cause. They go
	 * home by force, and the log says so loudly, with what the room was doing.
	 */
	public static void checkStuck(ServerPlayer player) {
		java.util.ArrayDeque<Long> tries = attempts.get(player.getUUID());
		if (tries == null || tries.isEmpty()) return;
		long now = player.level().getGameTime();
		while (!tries.isEmpty() && now - tries.peekFirst() > TRIES_WITHIN) tries.removeFirst();
		if (tries.size() < TRIES_STUCK || now - tries.peekLast() < STUCK_AFTER) return;
		if (Visit.of(player) == null || kept(player)) {
			attempts.remove(player.getUUID());
			return;
		}
		int count = tries.size();
		Room room = Rooms.of(player);
		String state = Rooms.stateForLog(player);
		lastStuck = "STUCK: " + player.getGameProfile().name() + " tried to leave " + count + " times; sent home. Room state: " + state;
		LearningBlocks.LOGGER.error("[{}] {}", LearningBlocks.MOD_ID, lastStuck);
		attempts.remove(player.getUUID());
		if (room != null) Progress.of(player).lessonNote(player, "sent home: stuck after " + count + " tries to leave");
		// Not in the middle of the classroom's own walk over its players.
		Later.after(player, 1, Travel::forceHome);
	}

	/**
	 * Everything put aside on the way in, handed back; nobody is moved, and the visit stays open for
	 * {@link #settle}. The room's own things go first, so a helmet handed out for HAT does not come
	 * home on anybody's head.
	 */
	static void giveBack(ServerPlayer player, Visit visit) {
		Rooms.close(player);

		player.getInventory().clearContent();
		Visit.clearHands(player);
		visit.giveKitBack(player);
		visit.experience().restore(player);
		player.removeAllEffects();
		for (MobEffectInstance effect : visit.effects()) player.addEffect(new MobEffectInstance(effect));
		visit.vitals().restore(player);
		GameType before = visit.savedGameMode();
		if (player.gameMode() != before) player.setGameMode(before);
		player.containerMenu.broadcastChanges();
	}

	/**
	 * The prizes waiting in the visit, handed over where the child now stands, after their own
	 * things: whatever does not fit is dropped at their feet. Then the visit is
	 * ended - last, so a visit that still exists is one whose prizes have not been given.
	 */
	static void settle(ServerPlayer player) {
		Visit visit = Visit.of(player);
		if (visit == null) return;
		for (ItemStack prize : visit.prizes()) {
			player.getInventory().placeItemBackInInventory(prize.copy(), Prediction.SERVER_ONLY);
		}
		if (visit.prizeXp() > 0) player.giveExperiencePoints(visit.prizeXp());
		player.containerMenu.broadcastChanges();
		Visit.set(player, null);
		attempts.remove(player.getUUID());
	}

	private static void handBack(ServerPlayer player, Visit visit) {
		if (player.isDeadOrDying() || player.isRemoved()) return;
		giveBack(player, visit);
		settle(player);
	}

	private static TeleportTransition home(MinecraftServer server, Visit.Home home) {
		ServerLevel level = server.getLevel(home.dimension());
		if (level == null || Places.isClassroom(home.dimension())) return spawnTrip(server);
		return new TeleportTransition(level, new Vec3(home.x(), home.y(), home.z()), Vec3.ZERO,
			home.yaw(), home.pitch(), Set.of(), TeleportTransition.DO_NOTHING);
	}

	private static TeleportTransition spawnTrip(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		return new TeleportTransition(level, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0, 0, Set.of(),
			TeleportTransition.DO_NOTHING);
	}


	/**
	 * Logging in. Someone who logged out inside with a visit carries on in a fresh room, since what
	 * the room was doing lived in memory. Someone with a visit who is somewhere else - the level
	 * was removed, another mod moved them while they were away - gets their things back where they
	 * stand. Someone inside with no visit at all has nothing to go back to and is taken to spawn.
	 *
	 * <p>Nobody on the death screen is handed anything: whatever goes into a body is lost when it
	 * respawns. {@link #respawned} takes that case.
	 */
	public static void loggedIn(ServerPlayer player) {
		Schedules.refreshName(player.getUUID(), player.getGameProfile().name());
		if (player.isDeadOrDying()) return;
		Visit visit = Visit.of(player);
		boolean inside = Places.isClassroom(player.level());
		if (visit != null && inside) {
			player.setGameMode(GameType.SURVIVAL);
			Room room = Rooms.open(player, player.level(), Plots.origin(player));
			Vec3 spawn = room.spawnFor(player.getUUID());
			player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.of(), Room.FACING_YAW, 0, true);
			Rooms.begin(player);
		} else if (visit != null) {
			int lessons = visit.lessons();
			handBack(player, visit);
			LearningBlocks.LOGGER.info("[{}] {} logged in outside the classroom with a visit open; their things are back",
				LearningBlocks.MOD_ID, player.getGameProfile().name());
			if (lessons > 0) Progress.of(player).setLessonsOwed(player, lessons);
			returnForLessons(player);
		} else if (inside) {
			own(() -> player.teleport(spawnTrip(player.level().getServer())));
		} else {
			returnForLessons(player);
		}
		// A daily schedule with lessons left today: in they go, for what is left.
		if (Visit.of(player) == null) Schedules.enforce(player);
	}


	/** The most lessons a child is kept for at once: every count up to it can be said aloud. */
	public static final int MOST_LESSONS = 20;

	/** "Fourteen more to go!", for a count that can be said; null for none left. */
	public static @Nullable String moreToGo(int left) {
		return left > 0 && left <= MOST_LESSONS ? "phrase/more_to_go_" + left : null;
	}

	public static int lessons(ServerPlayer player) {
		Visit visit = Visit.of(player);
		return visit == null ? 0 : visit.lessons();
	}

	/** Whether lessons keep this child in: some are left, and they are not an operator (unless scheduled today themselves). */
	public static boolean kept(ServerPlayer player) {
		return lessons(player) > 0 && !Schedules.mayLeave(player);
	}

	public static void setLessons(ServerPlayer player, int remaining) {
		Visit visit = Visit.of(player);
		if (visit == null) return;
		Visit.set(player, visit.withLessons(Math.min(MOST_LESSONS, remaining)));
		Rooms.lessonsChanged(player);
	}

	/**
	 * Send a child to the classroom, the ordinary way in, for this many lessons (or an ordinary
	 * visit, for null). Already there, it only sets the lessons. What went wrong, or null.
	 */
	public static @Nullable String send(ServerPlayer player, @Nullable Integer lessons) {
		// Lessons nobody could ever finish would keep the portal shut for good.
		if (lessons != null && lessons > 0 && !Rooms.puzzleAvailable(player)) {
			return "there are no words here for them to read, so lessons would never end";
		}
		if (Visit.of(player) == null) {
			String refused = enter(player);
			if (refused != null) return refused;
		}
		if (lessons != null) setLessons(player, lessons);
		if (lessons != null && lessons > 0) Progress.of(player).lessonNote(player, "sent in for " + lessons + (lessons == 1 ? " lesson" : " lessons"));
		return null;
	}

	/**
	 * Something else took a child out with lessons left - another mod's teleport, a death - and
	 * the lessons were kept on them. Their next login takes them back in; fighting a teleport in
	 * the middle of it is how a server ends up in a loop, and a login is a moment nobody is moving
	 * them anywhere else.
	 */
	private static void returnForLessons(ServerPlayer player) {
		Progress progress = Progress.of(player);
		int owed = progress.lessonsOwed();
		if (owed <= 0 || Visit.of(player) != null) return;
		if (enter(player) == null) {
			progress.setLessonsOwed(player, 0);
			setLessons(player, owed);
		}
	}

	public static void respawned(ServerPlayer player) {
		Visit visit = Visit.of(player);
		if (visit != null && !Places.isClassroom(player.level())) {
			if (visit.lessons() > 0) Progress.of(player).setLessonsOwed(player, visit.lessons());
			handBack(player, visit);
		}
	}

	private static int crossing;

	/** A trip across the classroom's edge made by the classroom itself, which nothing else may make. */
	private static <T> T own(java.util.function.Supplier<T> trip) {
		crossing++;
		try {
			return trip.get();
		} finally {
			crossing--;
		}
	}

	/**
	 * Whether a teleport may carry this player to that level: across the classroom's edge only by the
	 * classroom's own trips, since the way in and out is what puts things aside and hands them back,
	 * and what keeps a child in for lessons. Someone in the classroom with no visit may leave.
	 */
	public static boolean mayCross(ServerPlayer player, ServerLevel to) {
		if (crossing > 0) return true;
		boolean inside = Places.isClassroom(player.level());
		if (inside == Places.isClassroom(to)) return true;
		return inside && Visit.of(player) == null;
	}

	/**
	 * Moved between dimensions by something that is not this mod. Out of the classroom that way
	 * still counts as leaving it; into it with no visit is turned round.
	 *
	 * <p>Deferred a tick, never done here: this fires from the middle of {@code ServerPlayer.teleport},
	 * which finishes by sending along every player still standing in the level just left. Turning
	 * somebody round from in here puts them back in that level before that runs, the tail of the
	 * outer teleport sends them in again, and this fires again with no stack ever unwinding, into a
	 * StackOverflowError. A tick later the outer teleport is
	 * over. The player is looked up afresh, because they may have logged out or died in between.
	 */
	public static void changedLevel(ServerPlayer player, ServerLevel from, ServerLevel to) {
		boolean out = Places.isClassroom(from) && !Places.isClassroom(to);
		boolean in = !Places.isClassroom(from) && Places.isClassroom(to);
		if (!out && !in) return;
		MinecraftServer server = to.getServer();
		UUID id = player.getUUID();
		server.execute(() -> {
			ServerPlayer now = server.getPlayerList().getPlayer(id);
			if (now == null || now.isDeadOrDying()) return;
			Visit visit = Visit.of(now);
			if (out && visit != null && !Places.isClassroom(now.level())) {
				// Lessons left are kept, and the next login brings the child back for them.
				if (visit.lessons() > 0) Progress.of(now).setLessonsOwed(now, visit.lessons());
				handBack(now, visit);
			} else if (in && visit == null && Places.isClassroom(now.level())) {
				LearningBlocks.LOGGER.info("[{}] {} arrived in the classroom without using /learn; sent to spawn",
					LearningBlocks.MOD_ID, now.getGameProfile().name());
				own(() -> now.teleport(spawnTrip(server)));
			}
		});
	}

	/**
	 * Why a teleport command may not set this player down in this level, or null where it may.
	 *
	 * <p>Both ways across are refused, because both the way in and the way out do work a teleport skips: going in is
	 * where a child's things are put aside, going out is where they come back. A teleport in would
	 * put somebody in the room holding their own pack; a teleport out would run the whole leaving
	 * off the back of a command that said nothing about it. So the command fails, naming the right one.
	 */
	public static @Nullable String refuse(ServerPlayer player, ServerLevel level) {
		String who = player.getGameProfile().name();
		boolean inside = Visit.of(player) != null;
		if (inside && !Places.isClassroom(level)) {
			return who + " is in the classroom, which is holding what they carried in. Leaving is its own step: "
				+ "/learn leave, or /execute as " + who + " run learn leave";
		}
		if (!inside && Places.isClassroom(level)) {
			return "The classroom is entered with /learn, which puts a player's things aside on the way in: "
				+ "/execute as " + who + " run learn";
		}
		return null;
	}
}
