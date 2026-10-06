package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.VanillaHudElement;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Whose room is whose, while they are in it, and the clicks that reach one. In memory only: a room
 * is rebuilt from nothing on every way in; what must survive a restart (the plot, and the things
 * put aside on the way in) is on the player.
 */
public final class Rooms {
	private Rooms() {}

	private static final Map<UUID, Room> rooms = new HashMap<>();

	static {
		// Reading together, what the room says one reader hears, the others hear too.
		Narrator.hearAlong(player -> {
			Room room = rooms.get(player.getUUID());
			return room == null ? List.of() : room.others(player);
		});
	}

	/** What a child's room is doing, in one line for the log: what to look at when somebody is stuck. */
	public static String stateForLog(ServerPlayer player) {
		Room room = of(player);
		var visit = justfatlard.learning_blocks.classroom.Visit.of(player);
		String common = "lessons=" + justfatlard.learning_blocks.classroom.Travel.lessons(player)
			+ " visit=" + (visit != null) + " prizesWaiting=" + (visit != null && visit.hasPrizes())
			+ " narration=" + (justfatlard.learning_blocks.narration.Narrator.isPlaying(player) ? "playing" : "quiet")
			+ " at=" + player.blockPosition().toShortString();
		if (room == null) return "no room; " + common;
		Puzzle puzzle = room.puzzle();
		String round = room.subjectRound != null ? room.subjectRound.subject().id() + ":" + room.subjectRound.kind() + ":" + room.subjectRound.item()
			: room.bob() != null ? "bob:" + room.bob().mode() : room.scene() != null ? "scene:" + room.scene().word().text()
			: puzzle == null ? "none" : puzzle.kind() + ":" + puzzle.target().text();
		return "round=" + round + " busy=" + room.busy() + " leaving=" + room.leaving(player) + " leavingFor=" + room.leavingFor(player)
			+ " portalOpen=" + room.portalOpenFor(player) + " readers=" + room.present().size() + " " + common;
	}

	public static @Nullable Room of(ServerPlayer player) {
		return rooms.get(player.getUUID());
	}

	/** Build this player's room fresh at their plot, closing any they had. The round starts on arrival. */
	public static Room open(ServerPlayer player, ServerLevel level, BlockPos origin) {
		// Someone still reading in the room on this plot (a guest whose host went home and came back): in with them.
		Room there = at(level, origin);
		if (there != null && there != rooms.get(player.getUUID()) && !there.present().isEmpty() && !there.leaving()) {
			// It's their own plot: in they come, even to a room already full of their guests.
			if (!join(player, there)) {
				close(player);
				rooms.put(player.getUUID(), there);
				there.addMember(player);
			}
			return there;
		}
		close(player);
		Room room = new Room(player, level, origin);
		room.build();
		rooms.put(player.getUUID(), room);
		return room;
	}

	/** Whether a room is open at this spot just now: a plot in use can't be given away. */
	public static boolean standing(ServerLevel level, BlockPos origin) {
		return at(level, origin) != null;
	}

	static @Nullable Room at(ServerLevel level, BlockPos origin) {
		for (Room room : rooms.values()) if (room.level == level && room.origin().equals(origin)) return room;
		return null;
	}

	/**
	 * Into another reader's room, out of their own if they had one: reading together. Up to
	 * {@link Room#MOST_READERS}; false if it is full.
	 */
	public static boolean join(ServerPlayer player, Room room) {
		if (rooms.get(player.getUUID()) == room) return true;
		if (room.members.size() >= Room.MOST_READERS && !room.members.contains(player.getUUID())) return false;
		close(player);
		rooms.put(player.getUUID(), room);
		room.addMember(player);
		return true;
	}

	static java.util.Collection<Room> all() {
		java.util.Set<Room> each = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		each.addAll(rooms.values());
		return each;
	}

	/** The first round, once the child is standing in the room to hear it. */
	public static void begin(ServerPlayer player) {
		Room room = of(player);
		if (room == null) return;
		if (room.shared() && room.puzzle != null) {
			// Joining a room already reading: no new round, just their own chest and portal.
			PandoricalApi.hud().hideVanillaElements(player, LearningBlocks.MOD_ID,
				List.of(VanillaHudElement.HELD_ITEM_TOOLTIP, VanillaHudElement.OVERLAY_MESSAGE));
			room.refreshPrizeMarker(player);
			room.refreshHaul(player);
			room.refreshPortal(player, false);
			return;
		}
		// The item name that pops up over the hotbar sits where the caption does, and the caption
		// is the one worth reading.
		PandoricalApi.hud().hideVanillaElements(player, LearningBlocks.MOD_ID,
			List.of(VanillaHudElement.HELD_ITEM_TOOLTIP, VanillaHudElement.OVERLAY_MESSAGE));
		room.holdPrompt();
		room.newRound(player);
		room.refreshPrizeMarker(player);
		room.refreshHaul(player);
		room.refreshPortal(player, false);
	}

	/** The client has loaded the level it is in: a room waiting to speak can, shortly. */
	public static void clientLoaded(ServerPlayer player) {
		Room room = of(player);
		if (room != null) room.clientLoaded();
	}

	public static void lessonsChanged(ServerPlayer player) {
		Room room = of(player);
		if (room != null) room.refreshPortal(player, false);
	}

	/** A payout, put aside for this child: it flies into the chest when the room next gets to say so. */
	public static void prizeAdded(ServerPlayer player, java.util.List<net.minecraft.world.item.ItemStack> items, int xp) {
		Room room = of(player);
		if (room == null) return;
		room.prizeAdded(player, items, xp);
	}

	public static void prizesChanged(ServerPlayer player) {
		Room room = of(player);
		if (room != null) {
			room.refreshPrizeMarker(player);
			room.refreshHaul(player);
		}
	}

	public static boolean startRound(ServerPlayer player, Word target) {
		Room room = of(player);
		if (room == null || Puzzle.forTarget(target, Words.all(), player.getRandom()) == null) return false;
		Narrator.stop(player);
		room.newRound(player, target);
		return true;
	}

	@ForTests
	public static boolean startRound(ServerPlayer player, Word target, Puzzle.Kind kind) {
		Room room = of(player);
		Puzzle puzzle = Puzzle.forTarget(target, Words.all(), player.getRandom(), kind);
		if (room == null || puzzle == null || puzzle.family().size() < 2) return false;
		Narrator.stop(player);
		room.newRound(player, target, kind);
		return true;
	}

	@ForTests
	public static boolean startLetterRound(ServerPlayer player, Letter letter) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newLetterRound(player, letter);
		return true;
	}

	@ForTests
	public static void nextRound(ServerPlayer player) {
		Room room = of(player);
		if (room == null) return;
		Narrator.stop(player);
		room.newRound(player);
	}

	/** "Learning time first!" before this child's next prompt: they were sent in by their daily schedule. */
	public static void learningTime(ServerPlayer player) {
		Room room = of(player);
		if (room != null) room.learningTime = true;
	}

	@ForTests
	public static boolean startTeamRound(ServerPlayer player) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newTeamRound(player);
		return room.team();
	}

	@ForTests
	public static boolean startTricky(ServerPlayer player, Word word) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newTricky(player, word);
		return room.tricky();
	}

	@ForTests
	public static boolean startPlural(ServerPlayer player, Word plural) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newPlural(player, plural);
		return room.puzzle() != null && room.puzzle().kind() == Puzzle.Kind.PLURAL;
	}

	@ForTests
	public static boolean startPhrase(ServerPlayer player, Word adjective, Word noun) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newPhrase(player, adjective, noun);
		return room.phraseAdjective() != null;
	}

	@ForTests
	public static boolean startCaseRound(ServerPlayer player, Letter letter, boolean up) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newCaseRound(player, letter, up);
		return room.casePrompt() != null;
	}

	public static boolean startBob(ServerPlayer player, BobRound.Mode mode, justfatlard.learning_blocks.bob.@Nullable Goal goal,
			justfatlard.learning_blocks.bob.Goals.@Nullable Mistake mistake) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newBob(player, mode, goal, mistake);
		return room.bob != null && room.bob.mode == mode;
	}

	/**
	 * Silly sentences from the action menu: from stage 5 (when Bob comes in), into the classroom
	 * first if not there, and pressed again, back to reading. What went wrong, said kindly, or null.
	 */
	public static @Nullable String silly(ServerPlayer player) {
		justfatlard.learning_blocks.reward.Progress progress = justfatlard.learning_blocks.reward.Progress.of(player);
		int stage = Stages.current(progress);
		if (stage >= 0 && stage < Stages.BOB_FROM) {
			// Said, not written: the child pressing this button can't read the reason yet.
			sayQuietly(player, "phrase/bob_comes_later");
			return null;
		}
		if (of(player) == null || !Places.isClassroom(player.level())) {
			String refused = justfatlard.learning_blocks.classroom.Travel.enter(player);
			if (refused != null) return refused;
			justfatlard.learning_blocks.Later.after(player, 60, p -> startSilly(p));
			return null;
		}
		return startSilly(player) ? null : "Not just now: the room is busy";
	}

	private static final java.util.Set<justfatlard.learning_blocks.narration.Narration> QUIET =
		java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** A line said to a child, never across a round's telling; it may cut off another such line. */
	public static void sayQuietly(ServerPlayer player, String clip) {
		Room room = of(player);
		if (room != null && (room.busy || room.leaving)) return;
		var playing = Narrator.playing(player);
		if (playing != null && !QUIET.contains(playing)) return;
		var line = new justfatlard.learning_blocks.narration.Narration().say(clip);
		QUIET.add(line);
		Narrator.play(player, line);
	}

	/** Bob's silly sentences, now: from the action menu. Not while a word is being read out. */
	public static boolean startSilly(ServerPlayer player) {
		Room room = of(player);
		if (room == null || room.busy || room.leaving) return false;
		if (room.bob != null && room.bob.mode == BobRound.Mode.SILLY) {
			// Again: back to reading.
			Narrator.stop(player);
			room.newRound(player);
			return true;
		}
		return startBob(player, BobRound.Mode.SILLY, null, null);
	}

	/** A word to fix: this word on the board with this wrong letter in it, of any kind of round that makes it a family word. False if no kind does. */
	public static boolean startFix(ServerPlayer player, Word target, @Nullable Letter wrong) {
		Room room = of(player);
		if (room == null) return false;
		for (Puzzle.Kind kind : List.of(Puzzle.Kind.MIDDLE, Puzzle.Kind.FIRST, Puzzle.Kind.LAST)) {
			Puzzle puzzle = Puzzle.forTarget(target, Words.all(), player.getRandom(), kind);
			if (puzzle == null) continue;
			Letter found = null;
			for (Word other : puzzle.family()) {
				if (AnswerCommit.sameWord(other, target) || other.tiles().size() != target.tiles().size()) continue;
				Letter in = Letter.of(other.tiles().get(puzzle.gap()));
				if (in != null && puzzle.shelf().contains(in) && (wrong == null || in == wrong)) found = in;
			}
			if (found == null) continue;
			Narrator.stop(player);
			room.newFix(player, target, kind, found);
			return room.fixLetter != null;
		}
		return false;
	}

	@ForTests
	public static boolean startChunk(ServerPlayer player, Word target, int start) {
		Room room = of(player);
		if (room == null || Puzzle.buildableChunks(target).stream().noneMatch(range -> range[0] == start)) return false;
		Narrator.stop(player);
		room.forcedPuzzle = Puzzle.chunk(target, start, player.getRandom(), Puzzle.singles(), Words.all());
		room.newRound(player);
		return room.puzzle() != null && room.puzzle().kind() == Puzzle.Kind.CHUNK && room.puzzle().target().id().equals(target.id());
	}

	/** A read-it round for this word. False if it has no look-alike to pick instead. */
	@ForTests
	public static boolean startReadIt(ServerPlayer player, Word target) {
		Room room = of(player);
		if (room == null) return false;
		Narrator.stop(player);
		room.newReadIt(player, target);
		return room.readIt();
	}

	@ForTests
	public static boolean startScene(ServerPlayer player, Word target) {
		Room room = of(player);
		if (room == null || target.usableScene().isEmpty() || Puzzle.forTarget(target, Words.all(), player.getRandom()) == null) return false;
		Narrator.stop(player);
		room.newScene(player, target);
		return true;
	}

	@ForTests
	public static void runOutcome(ServerPlayer player, Word word) {
		Room room = of(player);
		if (room != null) word.outcome().run(player, room.dispenser);
	}

	/** A word's cheeky version, as when it is built but was not the word asked for. */
	@ForTests
	public static void runWrongOutcome(ServerPlayer player, Word word) {
		Room room = of(player);
		if (room != null) word.wrongOutcome().run(player, room.dispenser);
	}

	/**
	 * A click on an entity in the classroom: the picture's click box replays the word, by right or
	 * left click. Anything else passes on as usual.
	 */
	public static InteractionResult clickEntity(Player player, Level level, net.minecraft.world.entity.Entity entity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !Places.isClassroom(level)) {
			return InteractionResult.PASS;
		}
		Room room = of(serverPlayer);
		if (room == null) return InteractionResult.PASS;
		// Bob's cards, and reading together the goals to pick from.
		if (room.bob != null && room.bob.click(serverPlayer, entity)) return InteractionResult.SUCCESS;
		// Another subject's choices: the one picked is the answer.
		if (room.subjectRound != null && room.subjectRound.click(serverPlayer, entity)) return InteractionResult.SUCCESS;
		// A read-it round's pictures: the one picked is the answer.
		Word picked = room.choiceOf(entity);
		if (picked != null) {
			// Reading together, the pick is the turn reader's.
			if (!room.mayPress(serverPlayer)) {
				room.notYourTurn(serverPlayer, room.turn);
				return InteractionResult.SUCCESS;
			}
			room.answers.pick(serverPlayer, picked);
			return InteractionResult.SUCCESS;
		}
		// The picture's click box, or a scene's character: either asks again.
		boolean picture = room.pictureHit() != null && room.pictureHit().equals(entity.getUUID());
		if (!picture && !room.isSceneCharacter(entity)) return InteractionResult.PASS;
		room.replay(serverPlayer);
		return InteractionResult.SUCCESS;
	}

	/**
	 * A right-click on an entity in the classroom, either hand. The room's own clicks first (a
	 * picture, a card, a scene's character); then anything else in the classroom is the classroom's
	 * too, so no other mod opens a villager's dialogue or a trade here. Only things to ride - a
	 * boat, a minecart - are left to the game. An operator in creative is left alone.
	 */
	public static InteractionResult useEntity(Player player, Level level, InteractionHand hand, net.minecraft.world.entity.Entity entity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer) || !Places.isClassroom(level) || player.isCreative()) {
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			InteractionResult result = clickEntity(player, level, entity);
			if (result != InteractionResult.PASS) return result;
		}
		if (entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity) return InteractionResult.PASS;
		return InteractionResult.SUCCESS;
	}

	/** Whether this child can be given a round at all: letters to find, or words to read. */
	public static boolean puzzleAvailable(ServerPlayer player) {
		justfatlard.learning_blocks.reward.Progress progress = justfatlard.learning_blocks.reward.Progress.of(player);
		int stage = Stages.current(progress);
		if (stage == 0) return true;
		return Puzzle.pick(Words.all(), player.getRandom(), progress.words(), stage, java.util.EnumSet.allOf(Puzzle.Kind.class)) != null;
	}

	/** How long a leave may take before the room sees it through itself: the prize line, and some. */
	static final int LEAVE_AT_MOST = 100;

	public static void tick(MinecraftServer server) {
		java.util.Set<Room> ticked = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Map.Entry<UUID, Room> entry : new ArrayList<>(rooms.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !Places.isClassroom(player.level())) continue;
			Room room = entry.getValue();
			if (rooms.get(entry.getKey()) != room) continue;
			// A leave that should long since have taken them home: take them now.
			if (room.leavingFor(player) > LEAVE_AT_MOST) {
				LearningBlocks.LOGGER.warn("[{}] {} was still inside {} ticks after leaving; sent home", LearningBlocks.MOD_ID,
					player.getGameProfile().name(), room.leavingFor(player));
				Safely.run("Sending " + player.getGameProfile().name() + " home after a stalled leave",
					() -> justfatlard.learning_blocks.classroom.Travel.goHomeNow(player));
				continue;
			}
			boolean fine = Safely.run(player.getGameProfile().name() + "'s room", () -> {
				room.tickMember(player);
				// The room's own tick once, for whoever's turn it is.
				if (ticked.add(room)) {
					ServerPlayer lead = room.turnPlayer();
					room.tick(lead != null ? lead : player);
				}
			});
			if (!fine) {
				// A room that cannot tick is closed, and everyone in it sent home with everything,
				// rather than left in a classroom that no longer answers.
				List<ServerPlayer> inside = room.present();
				for (ServerPlayer one : inside) rooms.remove(one.getUUID(), room);
				rooms.remove(entry.getKey(), room);
				Safely.run("Closing " + player.getGameProfile().name() + "'s broken room", room::close);
				for (ServerPlayer one : inside.isEmpty() ? List.of(player) : inside) {
					Safely.run("Sending " + one.getGameProfile().name() + " home from a broken room",
						() -> justfatlard.learning_blocks.classroom.Travel.goHomeNow(one));
				}
			}
		}
	}

	public static void close(ServerPlayer player) {
		Room room = rooms.remove(player.getUUID());
		if (room != null) {
			// Reading together: the others keep the room; only this reader goes from it.
			if (room.members.size() > 1 && room.members.contains(player.getUUID())) room.removeMember(player);
			else room.close();
			PandoricalApi.hud().restoreVanillaElements(player, LearningBlocks.MOD_ID);
		}
	}

	/**
	 * Logged out: the room is forgotten along with what it made. The blocks stay, and are rebuilt
	 * when they log back in, whichever way that goes.
	 */
	public static void forget(ServerPlayer player) {
		forget(player, of(player));
	}

	/**
	 * Logged out: this room, if it is still theirs, closed. The room is the one they had when they
	 * went: a forget that runs after they have logged back in never closes the fresh room.
	 */
	public static void forget(ServerPlayer player, @org.jspecify.annotations.Nullable Room had) {
		if (relogged(player)) return;
		Narrator.forget(player);
		justfatlard.learning_blocks.Later.cancel(player.getUUID());
		Room now = rooms.get(player.getUUID());
		if (now == null || now != had) return;
		close(player);
	}

	/**
	 * Whether this player object is an old session's: the same player is online again as a new one
	 * (a disconnect handled after a quick re-login). Its forgets must leave the new session's
	 * narration, timers and room alone.
	 */
	public static boolean relogged(ServerPlayer player) {
		var server = player.level().getServer();
		if (server == null) return false;
		ServerPlayer current = server.getPlayerList().getPlayer(player.getUUID());
		return current != null && current != player;
	}

	public static void clear() {
		rooms.clear();
	}

	/**
	 * A right-click on a block in the classroom. The room's own controls first - the portal, the button,
	 * the prize chest, a word's bed or chest - then a tile being placed, which may go only in the gap
	 * or on the room's floor. Nothing else is used on a block here: the classroom is survival, so a
	 * child can knock tiles loose and set them down, and every other way a held item changes the
	 * world is refused. An operator in creative is left alone.
	 */
	public static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !Places.isClassroom(level)) {
			return InteractionResult.PASS;
		}
		Room room = of(serverPlayer);
		ItemStack held = serverPlayer.getItemInHand(hand);
		if (serverPlayer.isCreative()) {
			InteractionResult result = room == null ? null : room.use(serverPlayer, hit.getBlockPos(), hand);
			return result != null ? result : InteractionResult.PASS;
		}
		if (room != null) {
			InteractionResult result = room.use(serverPlayer, hit.getBlockPos(), hand);
			if (result != null) return result;
			Letter tile = Tiles.letterOf(held);
			if (hand == InteractionHand.MAIN_HAND && tile != null) {
				// The room places tiles itself: into the gap (swapping out what was there), or, set
				// down anywhere else in the room, straight back to its own place on the floor.
				if (room.busy()) return InteractionResult.FAIL;
				BlockPlaceContext place = new BlockPlaceContext(new UseOnContext(serverPlayer, hand, hit));
				BlockPos target = place.getClickedPos();
				BlockPos gap = room.aimedGap(hit.getBlockPos(), target);
				if (gap != null && !room.mayPlace(serverPlayer, gap)) {
					// Reading together: someone else's gap. They keep the tile, and hear whose it is.
					room.notYourTurn(serverPlayer, room.gapOwner(gap));
					serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, target));
					serverPlayer.containerMenu.broadcastChanges();
					return InteractionResult.FAIL;
				}
				if (gap == null && !(room.inRoom(target) && place.canPlace())) return InteractionResult.FAIL;
				held.shrink(1);
				serverPlayer.containerMenu.broadcastChanges();
				if (gap != null) room.putInGap(serverPlayer, tile, gap);
				else room.sendHome(serverPlayer, tile, net.minecraft.world.phys.Vec3.atCenterOf(target));
				// The client guessed the tile would sit where it aimed; tell it what is really there.
				serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, target));
				return InteractionResult.SUCCESS;
			}
		}
		return held.isEmpty() ? InteractionResult.PASS : InteractionResult.FAIL;
	}

	public static boolean mayBreak(Level level, Player player, BlockPos pos, BlockState state) {
		if (level.isClientSide() || !Places.isClassroom(level) || player.isCreative()) return true;
		Room room = player instanceof ServerPlayer serverPlayer ? of(serverPlayer) : null;
		if (room == null) return false;
		// Someone else's gap is theirs to empty: helping fetch tiles is fine, answering for them is not.
		if (room.gapOwners.containsKey(pos) && !room.mayPlace((ServerPlayer) player, pos)) return false;
		return room.mayBreak(pos, state);
	}

	/**
	 * A tile in the hand says its sound when used in the air. Buckets and spawn eggs do nothing here:
	 * a bucket used in the air still finds the floor, and water or a creature is not the classroom's
	 * to have.
	 */
	public static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !Places.isClassroom(level)) {
			return InteractionResult.PASS;
		}
		ItemStack held = serverPlayer.getItemInHand(hand);
		if (!serverPlayer.isCreative() && (held.getItem() instanceof BucketItem || held.getItem() instanceof SpawnEggItem
			|| held.getItem() instanceof net.minecraft.world.item.EggItem)) {
			return InteractionResult.FAIL;
		}
		Letter letter = Tiles.letterOf(held);
		if (letter == null) return InteractionResult.PASS;
		Room room = of(serverPlayer);
		if (room != null && room.busy()) return InteractionResult.SUCCESS;
		Narrator.play(serverPlayer, new Narration().say(LetterBlock.soundOf(letter)));
		return InteractionResult.SUCCESS;
	}
}
