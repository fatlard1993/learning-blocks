package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.Later;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One child's classroom, built in code, and the puzzle running in it.
 *
 * <p>Laid out facing north from where the child arrives:
 *
 * <pre>
 *   z=-5   [ chalkboard, green, the width of the room ]
 *   z=-4        _  a  t          (button)       the word, just above eye height
 *   z=-2     c     b     h                      the loose tiles, standing on the floor
 *   z=+3           @                            where the child arrives, looking at the board
 *   z=+5          [portal]                      home
 * </pre>
 *
 * <p>Everything the room says goes through {@link Narrator}, with a caption; nothing goes to chat.
 * The walls, ceiling and all but the chalkboard are barrier, so the room is open to the sky and
 * nothing gets out of it.
 *
 * <p>The room lives in memory; the blocks are the only thing saved, and they are rebuilt from
 * nothing every time anybody comes in, so a room is never found half-way through a round that
 * nobody remembers.
 *
 * <p>This class holds the round's state and its timing; the parts are their own classes:
 * {@link RoomLayout} where things are, {@link RoundLifecycle} a round from start to end,
 * {@link Interaction} a child's hands, {@link AnswerNarration} and {@link AnswerCommit} the button,
 * {@link Dispenser} what words do, {@link PrizeShowcase} the prize chest, {@link Portal} the way home,
 * {@link BoardPicture} the picture.
 */
public final class Room {
	public static final float FACING_YAW = 180.0F;

	/** Ticks of doing nothing before the room asks again. */
	private static final int IDLE = 20 * 20;
	/** How many times it asks again soon after the child stops; then only now and then. */
	private static final int MAX_REPEATS = 2;
	/** After the asking-again runs out, a quiet nudge this often while the child is still away. */
	static final int NUDGE = 20 * 60 * 3;
	/** Busy longer than any answer's telling takes: the round is stuck, and starts over. */
	static final int BUSY_AT_MOST = 20 * 60 * 2;
	private static final int TILE_CHECK = 40;
	private static final int REPLAY_EVERY = 30;
	/** Ticks after the client says it has loaded before the room speaks. */
	private static final int SETTLE = 20;
	/** If the client never says it has loaded, the room speaks anyway after this. */
	private static final int HOLD_AT_MOST = 20 * 8;
	/** How long a room may have nothing to ask while the portal is locked for lessons. */
	static final int STRANDED_AT_MOST = 200;

	/** Whose room it is: the one whose plot it stands on, or, once they have gone, whoever is still reading in it. */
	UUID owner;
	final ServerLevel level;
	final RoomLayout layout;

	final Dispenser dispenser;
	/** Each reader's prize chest, by player: one beside the portal each. */
	final Map<UUID, PrizeShowcase> showcases = new LinkedHashMap<>();
	final Portal portal;
	final BoardPicture picture;
	final RoundLifecycle rounds;
	final Interaction interaction;
	final AnswerNarration answers;

	@Nullable Puzzle puzzle;
	/** A round of another subject than Reading, while one is up (see {@link SubjectRound}). */
	@Nullable SubjectRound subjectRound;
	String currentSubject = "reading";
	/** Each reader's last subject here, and how many rounds of it in a row: for the mix. */
	final Map<UUID, String> lastSubject = new LinkedHashMap<>();
	final Map<UUID, Integer> subjectRun = new LinkedHashMap<>();
	/** A subject's hello, said before the next prompt: the subject just changed. */
	@Nullable String pendingHello;
	/** The sampler's or a test's asked-for round of another subject: which, what kind, what. */
	justfatlard.learning_blocks.subject.@Nullable Subject forcedSubject;
	@Nullable String forcedSubjectKind;
	@Nullable String forcedSubjectTarget;
	net.minecraft.world.item.@Nullable DyeColor frame;

	public @Nullable SubjectRound subjectRound() {
		return subjectRound;
	}

	public net.minecraft.world.item.@Nullable DyeColor frameColour() {
		return frame;
	}

	public String currentSubject() {
		return currentSubject;
	}

	public void newSubjectRound(ServerPlayer player, justfatlard.learning_blocks.subject.Subject subject, @Nullable String kind, @Nullable String target) {
		forcedSubject = subject;
		forcedSubjectKind = kind;
		forcedSubjectTarget = target;
		rounds.newRound(player, null, false, false);
	}
	@Nullable SceneRun scene;
	@Nullable Letter letterTarget;
	@Nullable Letter lastLetter;
	@Nullable Letter forcedLetter;
	Puzzle.@Nullable Kind forcedKind;
	/** Letters rounds since the last word round in the letters level, and that word. */
	int sinceEarlyWord;
	@Nullable Word lastEarlyWord;
	/** A tricky-word round (read-it style, with cards); a phrase round's adjective and its row; counters; and tests' asked-for rounds. */
	boolean tricky;
	/** The band the child is at, null if none: a change is a fresh start, as a stage's is. */
	Stages.@Nullable BandCode band;
	@Nullable Word phraseAdjective;
	final List<BlockPos> adjectiveRow = new ArrayList<>();
	int sinceTricky;
	int sincePlural;
	int sincePhrase;
	@Nullable Word forcedTricky;
	@Nullable Word forcedPlural;
	Word @Nullable [] forcedPhrase;

	/** A big-and-little round: the letter on the board; null in any other round. And a test's asked-for one. */
	@Nullable Letter casePrompt;
	@Nullable Letter forcedCase;
	boolean forcedCaseUp;

	/** Sent in by the daily schedule: "Learning time first!" before the next prompt. */
	boolean learningTime;
	/** The word the last round asked for, so the next is not the same. */
	@Nullable Word lastTarget;
	/** A read-it round: the word shown whole, and its picture to pick; and what each click box stands for. */
	boolean readIt;
	int sinceReadIt;
	@Nullable Word forcedReadIt;
	final Map<UUID, Word> choices = new LinkedHashMap<>();
	final Map<String, UUID> choiceDisplays = new LinkedHashMap<>();
	final List<BlockPos> choiceStands = new ArrayList<>();
	private final long openedAt;
	/** "Which letter says..." or "Can you find the letter that says...", taking turns round by round. */
	String letterPrompt = "phrase/which_letter_says";
	/** On the way out: the room starts nothing new, so nothing can talk over the goodbye or hold the child. */
	boolean leaving;
	private long leavingSince;
	/** Rounds since the last scene, this visit: one round in {@code scene_every} is a scene. */
	int sinceScene;
	@Nullable Word lastScene;
	final List<BlockPos> row = new ArrayList<>();
	/** Where each loose tile lives when it is not in a hand or the gap. */
	final Map<Letter, BlockPos> homes = new LinkedHashMap<>();
	/** The board block behind the gap, showing the underscore, put back to board at the next round. */
	@Nullable BlockPos gapMarker;
	/** Every gap in the row and the underscore behind it: one, or all of them when the whole word is built. */
	final Map<BlockPos, BlockPos> gapMarkers = new LinkedHashMap<>();
	boolean busy;
	int solved;
	/** How many words this child has sounded out since coming in. */
	int blends;
	final java.util.Set<String> magicSaid = new java.util.HashSet<>();
	private long lastActive;
	private long lastPrompted;
	int repeats;
	/** When the room went busy, or {@link Long#MIN_VALUE} while it isn't. */
	private long busySince = Long.MIN_VALUE;
	/** Whether a word other than the target was built this round: a solve after that is not first try. */
	boolean missedThisRound;
	/** The stage this round was picked from, or -1 for everything. */
	int stage;
	/** Counts rounds, so something still flying when a round ends does not land in the next. */
	int round;
	/** Whether the child has looked in the prize chest this visit, which is when it tells them how prizes work. */
	boolean chestOpened;
	private long lastReplay = -1000;
	/**
	 * The first prompt, held until the child's client has finished loading into the room and a
	 * moment more: said while they are still looking at a loading screen, it is said to nobody.
	 */
	boolean promptHeld;
	long heldSince;
	private long loadedAt = -1;
	/** The next outcome throws, as a broken one would. */
	boolean failNextOutcome;
	/** When the room last had no puzzle, while lessons were owed; -1 while it has one. */
	private long noPuzzleSince = -1;

	Room(ServerPlayer owner, ServerLevel level, BlockPos origin) {
		this.owner = owner.getUUID();
		this.level = level;
		this.layout = new RoomLayout(origin);
		this.dispenser = new Dispenser(this, level, layout);
		this.members.add(this.owner);
		this.showcases.put(this.owner, new PrizeShowcase(this, level, layout, dispenser, layout.prizeMarker(0)));
		this.portal = new Portal(this, level, layout);
		this.picture = new BoardPicture(level);
		this.rounds = new RoundLifecycle(this, level, layout);
		this.interaction = new Interaction(this, level, layout);
		this.answers = new AnswerNarration(this, level);
		this.openedAt = level.getGameTime();
	}


	public BlockPos origin() {
		return layout.origin();
	}

	/** Where a child is set down: far enough back to see the board and the tiles at once. */
	public Vec3 spawn() {
		return layout.spawn();
	}

	@ForTests
	public List<BlockPos> row() {
		return List.copyOf(row);
	}

	public BlockPos button() {
		return layout.button();
	}

	public BlockPos prizeMarker() {
		return layout.prizeMarker();
	}

	public BlockPos portalLower() {
		return layout.portalLower();
	}

	boolean inRoom(BlockPos pos) {
		return layout.inRoom(pos);
	}

	void build() {
		layout.build(level);
		frame = null;
	}

	/** Whether the round's word row or loose tiles use this square. */
	boolean holds(BlockPos pos) {
		return homes.containsValue(pos) || row.contains(pos);
	}


	public @Nullable Puzzle puzzle() {
		return puzzle;
	}

	public int solved() {
		return solved;
	}

	/** Whether the room is in the middle of reading a word out. */
	public boolean busy() {
		return busy;
	}

	/** The stage this round is from, or -1 once every stage is done. */
	public int stage() {
		return stage;
	}

	public @Nullable Letter letterTarget() {
		return letterTarget;
	}

	@ForTests
	public @Nullable SceneRun scene() {
		return scene;
	}

	/** Whether this is the scene's character, which a click asks about again. */
	boolean isSceneCharacter(Entity entity) {
		return scene != null && scene.isCharacter(entity);
	}

	public @Nullable BlockPos gap() {
		return row.isEmpty() ? null : row.get(Math.min(gapIndex(), row.size() - 1));
	}

	public List<BlockPos> gaps() {
		return List.copyOf(gapMarkers.keySet());
	}

	private int gapIndex() {
		return puzzle == null ? 0 : puzzle.gap();
	}

	/** Where this loose tile stands when it is on the floor, or null if this round has no such tile. */
	public @Nullable BlockPos home(Letter letter) {
		return homes.get(letter);
	}

	/** The letter a child has put in the gap, or null while it is empty; the first gap, when there are several. */
	public @Nullable Letter inGap() {
		return inGap(gap());
	}

	public @Nullable Letter inGap(@Nullable BlockPos gap) {
		return interaction.inGap(gap);
	}


	/** A leave is under way: no new rounds, prompts, tile sounds or lines from here. */
	public void beginLeaving(ServerPlayer player) {
		prizes(player.getUUID()).leaving(player);
		// Reading together, the others carry on: only this reader is on the way out.
		if (present().stream().anyMatch(other -> !other.getUUID().equals(player.getUUID()))) {
			leavingMembers.putIfAbsent(player.getUUID(), level.getGameTime());
			return;
		}
		leaving = true;
		leavingSince = level.getGameTime();
	}

	public boolean leaving() {
		return leaving;
	}

	/** Whether this reader is on the way out: the room closing, or them leaving it to the others. */
	public boolean leaving(ServerPlayer player) {
		return leaving || leavingMembers.containsKey(player.getUUID());
	}

	public long leavingFor() {
		return leaving ? level.getGameTime() - leavingSince : 0;
	}

	public long leavingFor(ServerPlayer player) {
		Long since = leavingMembers.get(player.getUUID());
		return since != null ? level.getGameTime() - since : leavingFor();
	}


	/** Everyone reading in this room, the owner first; at most {@link #MOST_READERS}, and a plot's owner coming back on top of that. */
	final List<UUID> members = new ArrayList<>();
	static final int MOST_READERS = 3;
	@Nullable UUID turn;
	final Map<UUID, Long> leavingMembers = new LinkedHashMap<>();
	/** Where a reader was when their last round was picked: a change is their fresh start. */
	record Place(int stage, Stages.@Nullable BandCode band) {}

	final Map<UUID, Place> lastStage = new java.util.HashMap<>();
	boolean team;
	/** Whose each gap is, in a shared room; and who put the tile in it. */
	final Map<BlockPos, UUID> gapOwners = new LinkedHashMap<>();
	final Map<BlockPos, UUID> gapPlacers = new LinkedHashMap<>();
	int sinceTeam;
	/**
	 * A practice round (the grown-ups' sampler): nothing it does is counted, and each reader's
	 * progress is a copy for its length. {@code practiceStarting} while the sample itself is being set
	 * up, so the round it makes is not taken as the one after.
	 */
	boolean practice;
	boolean practiceStarting;
	/** A sampler's ready-made round, used as is; and a sampler's team-up word. */
	@Nullable Puzzle forcedPuzzle;
	@Nullable Word forcedTeamWord;

	void endPractice() {
		if (!practice) return;
		practice = false;
		for (UUID id : members) {
			ServerPlayer one = level.getServer().getPlayerList().getPlayer(id);
			if (one != null) justfatlard.learning_blocks.reward.Progress.practice(one, false);
		}
	}

	public boolean practice() {
		return practice;
	}
	/** A team-up's two readers, the lower first; and a test's asked-for team-up. */
	final List<UUID> teamReaders = new ArrayList<>();
	boolean forcedTeam;

	@ForTests
	void newTeamRound(ServerPlayer player) {
		forcedTeam = true;
		rounds.newRound(player, null, false, false);
	}

	/** The little marker over each reader whose round it is, in their colour, by player. */
	private final Map<UUID, UUID> markerShown = new LinkedHashMap<>();

	/**
	 * Reading together: a small arrow in the reader's colour over whoever's round it is (both, in a
	 * team-up), kept over their head as they move; none alone.
	 */
	private void markers() {
		java.util.Set<UUID> want = new java.util.HashSet<>();
		if (shared() && puzzle != null) {
			if (team) want.addAll(teamReaders);
			else if (turn != null) want.add(turn);
		}
		for (Map.Entry<UUID, UUID> entry : new ArrayList<>(markerShown.entrySet())) {
			ServerPlayer reader = level.getServer().getPlayerList().getPlayer(entry.getKey());
			Entity shown = level.getEntity(entry.getValue());
			if (!want.contains(entry.getKey()) || reader == null || Rooms.of(reader) != this) {
				if (shown != null) shown.discard();
				markerShown.remove(entry.getKey());
			}
		}
		for (UUID id : want) {
			ServerPlayer reader = level.getServer().getPlayerList().getPlayer(id);
			if (reader == null || Rooms.of(reader) != this) continue;
			Vec3 over = reader.position().add(0, reader.getBbHeight() + 0.55, 0);
			Entity shown = markerShown.containsKey(id) ? level.getEntity(markerShown.get(id)) : null;
			if (shown == null) {
				Display.TextDisplay arrow = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
				if (arrow == null) continue;
				arrow.snapTo(over.x, over.y, over.z, 0, 0);
				arrow.setBillboardConstraints(Display.BillboardConstraints.CENTER);
				arrow.setText(net.minecraft.network.chat.Component.literal("\u25BC").withStyle(style -> style.withColor(colour(id)).withBold(true)));
				arrow.setBackgroundColor(0);
				arrow.setTransformation(new com.mojang.math.Transformation(null, null, new org.joml.Vector3f(1.4F, 1.4F, 1.4F), null));
				level.addFreshEntity(arrow);
				markerShown.put(id, arrow.getUUID());
			} else if (shown.position().distanceToSqr(over) > 0.0025) {
				shown.teleportTo(over.x, over.y, over.z);
			}
		}
	}

	@ForTests
	public java.util.Set<UUID> marked() {
		return java.util.Set.copyOf(markerShown.keySet());
	}

	private void clearMarkers() {
		for (UUID shown : markerShown.values()) {
			Entity found = level.getEntity(shown);
			if (found != null) found.discard();
		}
		markerShown.clear();
	}

	@ForTests
	public List<UUID> teamReaders() {
		return List.copyOf(teamReaders);
	}

	public List<UUID> members() {
		return List.copyOf(members);
	}

	public boolean shared() {
		return members.size() > 1;
	}

	public @Nullable UUID turn() {
		return turn;
	}

	public boolean team() {
		return team;
	}

	/** Whose gap this is in a shared room, or null for anyone's. */
	public @Nullable UUID gapOwner(BlockPos gap) {
		return gapOwners.get(gap);
	}

	List<ServerPlayer> present() {
		List<ServerPlayer> here = new ArrayList<>();
		for (UUID id : members) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null && !player.isRemoved() && Rooms.of(player) == this) here.add(player);
		}
		return here;
	}

	/** The others in the room with this reader: who hears along with them. */
	List<ServerPlayer> others(ServerPlayer player) {
		if (!shared()) return List.of();
		return present().stream().filter(other -> !other.getUUID().equals(player.getUUID())).toList();
	}

	/** A reader's place in the room, for their colour and their chest: 0, 1 or 2. */
	int place(UUID player) {
		return Math.max(0, members.indexOf(player));
	}

	static final int[] COLOURS = {0x2B6CE0, 0xF08A1E, 0x2FA844};

	public int colour(UUID player) {
		return COLOURS[Math.min(place(player), COLOURS.length - 1)];
	}

	/** Where a reader is set down: side by side, the owner in the middle. */
	public Vec3 spawnFor(UUID player) {
		int at = place(player);
		return layout.spawn().add(switch (at) {
			case 0 -> 0;
			case 1 -> -1.5;
			case 2 -> 1.5;
			default -> -3.0;
		}, 0, 0);
	}

	/** This reader's prize chest; a new reader's is made as they come in. */
	PrizeShowcase prizes(UUID player) {
		return showcases.computeIfAbsent(player, id -> new PrizeShowcase(this, level, layout, dispenser, layout.prizeMarker(place(id))));
	}

	@Nullable UUID chestOwner(BlockPos pos) {
		for (Map.Entry<UUID, PrizeShowcase> entry : showcases.entrySet()) if (entry.getValue().prizeMarker().equals(pos)) return entry.getKey();
		return null;
	}

	void addMember(ServerPlayer player) {
		if (!members.contains(player.getUUID())) members.add(player.getUUID());
		prizes(player.getUUID());
		Narrator.note(player, "joined");
		for (ServerPlayer one : present()) refreshPortal(one, false);
	}

	/**
	 * A reader leaves this room to the others: their chest goes with them, the room passes to
	 * whoever is left if it was theirs, and a round that was theirs gives way to the next reader's.
	 */
	void removeMember(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean theirs = id.equals(turn) || gapOwners.containsValue(id);
		justfatlard.learning_blocks.reward.Progress.practice(player, false);
		members.remove(id);
		leavingMembers.remove(id);
		lastStage.remove(id);
		PrizeShowcase chest = showcases.remove(id);
		if (chest != null) chest.close();
		Later.cancel(player);
		Narrator.silence(player);
		int seconds = (int) ((level.getGameTime() - openedAt) / 20);
		Safely.run("Counting time in the classroom", () -> justfatlard.learning_blocks.reward.Progress.of(player).timeSpent(player, seconds));
		if (id.equals(owner) && !members.isEmpty()) owner = members.getFirst();
		if (id.equals(turn)) turn = null;
		List<ServerPlayer> left = present();
		for (ServerPlayer one : left) refreshPortal(one, false);
		if (theirs && !left.isEmpty() && !leaving) {
			// Their round, or half of it: the next reader's round instead.
			busy = false;
			ServerPlayer next = left.getFirst();
			Later.after(next, 10, p -> {
				if (Rooms.of(p) == this && !leaving) newRound(p);
			});
		}
	}

	/**
	 * Whose round this is: alone, the reader asked for; together, the next reader along when
	 * {@code next}, else whoever's turn it already is.
	 */
	ServerPlayer takeTurn(ServerPlayer asked, boolean next) {
		List<ServerPlayer> here = present().stream().filter(p -> !leavingMembers.containsKey(p.getUUID())).toList();
		if (here.size() < 2) {
			turn = null;
			return here.isEmpty() ? asked : here.getFirst();
		}
		int at = -1;
		for (int i = 0; i < here.size(); i++) if (here.get(i).getUUID().equals(turn)) at = i;
		ServerPlayer chosen = at < 0 ? here.getFirst() : next ? here.get((at + 1) % here.size()) : here.get(at);
		turn = chosen.getUUID();
		return chosen;
	}

	/** The reader whose round it is, or the owner's, or whoever is here. */
	@Nullable ServerPlayer turnPlayer() {
		List<ServerPlayer> here = present();
		for (ServerPlayer one : here) if (one.getUUID().equals(turn)) return one;
		for (ServerPlayer one : here) if (one.getUUID().equals(owner)) return one;
		return here.isEmpty() ? null : here.getFirst();
	}

	/** Whether this reader may put a tile in this gap: theirs, or anyone's alone. */
	boolean mayPlace(ServerPlayer player, BlockPos gap) {
		UUID whose = gapOwners.get(gap);
		return whose == null || whose.equals(player.getUUID()) || !present().stream().anyMatch(p -> p.getUUID().equals(whose));
	}

	/** Whether this reader may press the button: alone, in a team-up, or on their turn. */
	boolean mayPress(ServerPlayer player) {
		if (!shared() || team || turn == null) return true;
		return turn.equals(player.getUUID()) || present().stream().noneMatch(p -> p.getUUID().equals(turn));
	}

	/** Ticks between one reader's pointing puffs, so mashing a button makes a few, not a flood. */
	private static final int POINT_EVERY = 5;
	private final Map<UUID, Long> pointedAt = new java.util.HashMap<>();
	/** The round each reader was last told it isn't their turn: once a round is enough. */
	private final Map<UUID, Integer> toldInRound = new java.util.HashMap<>();
	private final List<Vec3> pointed = new ArrayList<>();

	/**
	 * Reading together, a reader whose turn it isn't points: a puff in their colour where they clicked
	 * or punched, seen by everyone in the room, and a soft ping. It changes nothing in the round.
	 */
	void point(ServerPlayer player, Vec3 at) {
		long now = level.getGameTime();
		Long last = pointedAt.get(player.getUUID());
		if (last != null && now - last < POINT_EVERY && now >= last) return;
		pointedAt.put(player.getUUID(), now);
		int colour = COLOURS[Math.min(place(player.getUUID()), COLOURS.length - 1)];
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(colour, 1.2F), at.x, at.y, at.z, 10, 0.12, 0.12, 0.12, 0.0);
		level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.25F, 2.0F);
		pointed.add(at);
		if (pointed.size() > 32) pointed.removeFirst();
	}

	/** Where readers have pointed lately, oldest first. */
	@ForTests
	public List<Vec3> pointed() {
		return List.copyOf(pointed);
	}

	/** A reader tried what isn't theirs to do just now: they point there, and hear whose turn it is once a round. */
	void notYourTurn(ServerPlayer player, @Nullable UUID whose, Vec3 at) {
		point(player, at);
		Integer told = toldInRound.get(player.getUUID());
		if (told != null && told == round) return;
		toldInRound.put(player.getUUID(), round);
		if (busy || Narrator.isPlaying(player)) return;
		String name = nameOf(whose);
		Narrator.note(player, "not_your_turn");
		float seconds = justfatlard.learning_blocks.sound.Sounds.durationTicks("phrase/not_your_turn") / 20.0F;
		Narrator.play(player, new justfatlard.learning_blocks.narration.Narration().say("phrase/not_your_turn", "not_your_turn", p -> {},
			justfatlard.learning_blocks.narration.CaptionLine.timed(new justfatlard.learning_blocks.sound.Captions.Caption(seconds,
				List.of(new justfatlard.learning_blocks.sound.Captions.Word(team ? "That's " + name + "'s spot!" : "It's " + name + "'s turn!", 0.0F, seconds)))),
			justfatlard.learning_blocks.narration.Narration.GAP));
	}

	String nameOf(@Nullable UUID player) {
		ServerPlayer found = player == null ? null : level.getServer().getPlayerList().getPlayer(player);
		return found == null ? "their" : found.getGameProfile().name();
	}


	void newRound(ServerPlayer player) {
		newRound(player, null);
	}

	/** A round from this child's stage, a word they have not read first; or for exactly this target. */
	void newRound(ServerPlayer player, @Nullable Word target) {
		rounds.newRound(player, target, false, false);
	}

	@ForTests
	void newRound(ServerPlayer player, Word target, Puzzle.Kind kind) {
		forcedKind = kind;
		rounds.newRound(player, target, false, false);
	}

	@ForTests
	void newTricky(ServerPlayer player, Word word) {
		forcedTricky = word;
		rounds.newRound(player, null, false, false);
	}

	void newPlural(ServerPlayer player, Word plural) {
		forcedPlural = plural;
		rounds.newRound(player, null, false, false);
	}

	void newPhrase(ServerPlayer player, Word adjective, Word noun) {
		forcedPhrase = new Word[] {adjective, noun};
		rounds.newRound(player, null, false, false);
	}

	@ForTests
	public boolean tricky() {
		return tricky;
	}

	public @Nullable Word phraseAdjective() {
		return phraseAdjective;
	}

	/** A big-and-little round for this letter, finding the capital ({@code up}) or the small one. */
	@ForTests
	void newCaseRound(ServerPlayer player, Letter letter, boolean up) {
		forcedCase = letter.lower();
		forcedCaseUp = up;
		rounds.newRound(player, null, false, false);
	}

	@ForTests
	public @Nullable Letter casePrompt() {
		return casePrompt;
	}

	@ForTests
	void newLetterRound(ServerPlayer player, Letter letter) {
		forcedLetter = letter;
		rounds.newRound(player, null, false, false);
	}

	/** A read-it round for exactly this word, if it has a look-alike to pick instead. */
	@ForTests
	void newReadIt(ServerPlayer player, Word target) {
		forcedReadIt = target;
		rounds.newRound(player, null, false, false);
	}

	public boolean readIt() {
		return readIt;
	}


	/** A round with Bob: telling him, fixing what he did, or silly sentences; null in any other round. */
	@Nullable BobRound bob;
	/** The last goal Bob was given, so the next is another. */
	justfatlard.learning_blocks.bob.@Nullable Goal lastGoal;
	/** Rounds since the last of Bob's, and how many of his this visit: every third a what's-wrong. */
	int sinceBob;
	int bobRounds;
	BobRound.@Nullable Mode forcedBob;
	justfatlard.learning_blocks.bob.@Nullable Goal forcedGoal;
	justfatlard.learning_blocks.bob.Goals.@Nullable Mistake forcedMistake;
	/** A word to fix: the wrong letter in its gap, or null; rounds since the last; a test's asked-for wrong letter. */
	@Nullable Letter fixLetter;
	int sinceFix;
	@Nullable Letter forcedFixLetter;

	void newBob(ServerPlayer player, BobRound.Mode mode, justfatlard.learning_blocks.bob.@Nullable Goal goal,
			justfatlard.learning_blocks.bob.Goals.@Nullable Mistake mistake) {
		forcedBob = mode;
		forcedGoal = goal;
		forcedMistake = mistake;
		rounds.newRound(player, null, false, false);
	}

	@ForTests
	public @Nullable BobRound bob() {
		return bob;
	}

	@ForTests
	public @Nullable Letter fixLetter() {
		return fixLetter;
	}

	void newFix(ServerPlayer player, Word target, Puzzle.Kind kind, Letter wrong) {
		forcedFixLetter = wrong;
		forcedKind = kind;
		rounds.newRound(player, target, false, false);
	}

	/** The word each click box on the floor stands for, in a read-it round. */
	@ForTests
	public Map<UUID, Word> choices() {
		return java.util.Collections.unmodifiableMap(choices);
	}

	@Nullable Word choiceOf(Entity entity) {
		return choices.get(entity.getUUID());
	}

	/** A scene round for exactly this word, which must have a scene. */
	void newScene(ServerPlayer player, Word target) {
		rounds.newRound(player, target, false, true);
	}

	/**
	 * An operator moved this child's stage: the same clean slate as moving up one, and the new
	 * stage's first round.
	 */
	public void stageSet(ServerPlayer player) {
		rounds.newRound(player, null, true, false);
	}

	/** Hold the first round's prompt until the child's client has loaded; see {@link #clientLoaded}. */
	void holdPrompt() {
		promptHeld = true;
		loadedAt = -1;
	}

	void clientLoaded() {
		loadedAt = level.getGameTime();
	}

	public boolean promptHeld() {
		return promptHeld;
	}

	/**
	 * The picture was clicked: say the word it stands for again, "Can you make... cat?", with a
	 * little bounce. Not while a word is being read out, and at most every {@link #REPLAY_EVERY}
	 * ticks, so a child clicking away does not pile the prompt up.
	 */
	void replay(ServerPlayer player) {
		long now = level.getGameTime();
		if (puzzle == null || busy || leaving || promptHeld || now - lastReplay < REPLAY_EVERY) return;
		lastReplay = now;
		touch();
		Narrator.note(player, "replay");
		Narrator.play(player, rounds.prompt());
		if (scene != null) return;
		picture.bounce(player, dispenser);
	}

	public @Nullable UUID pictureHit() {
		return picture.hit();
	}

	@ForTests
	public @Nullable String pictureCard() {
		return picture.card();
	}

	@ForTests
	public @Nullable Entity picture() {
		return picture.shown();
	}

	public static Display.@Nullable ItemDisplay pictureOf(ServerLevel level, ItemStack shown, Vec3 at) {
		return BoardPicture.of(level, shown, at);
	}

	/**
	 * Asked again when a child has done nothing for a while, and only a couple of times a round: a
	 * reminder, not a nag. Anything they do starts the wait over.
	 */
	void tick(ServerPlayer player) {
		if (scene != null) scene.tick(player);
		if (leaving) return;
		markers();
		if (promptHeld) {
			long now = level.getGameTime();
			boolean settled = loadedAt >= 0 && now - loadedAt >= SETTLE;
			if (settled || now - heldSince >= HOLD_AT_MOST) {
				promptHeld = false;
				// A child who is already busy with the word needs no asking.
				if (!busy && !Narrator.isPlaying(player)) Narrator.play(player, rounds.prompt());
				touch();
			}
			return;
		}
		for (PrizeShowcase chest : showcases.values()) chest.tick();
		if (puzzle != null && !busy && level.getGameTime() % TILE_CHECK == 0) interaction.keepSolvable(player);
		if (unstick(player)) return;
		if (puzzle == null || busy || Narrator.isPlaying(player)) return;
		long now = level.getGameTime();
		if (now - Math.max(lastActive, lastPrompted) < (repeats < MAX_REPEATS ? IDLE : NUDGE)) return;
		repeats++;
		lastPrompted = now;
		Narrator.play(player, idlePrompt());
	}

	/**
	 * What the room says to a child who has gone quiet: the question again, or, when the answer is
	 * already in place and only the button is left, "Press the button!".
	 */
	private Narration idlePrompt() {
		boolean placed = subjectRound != null ? subjectRound.waitingForButton()
			: bob == null && scene == null && !readIt && !gaps().isEmpty() && gaps().stream().allMatch(gap -> inGap(gap) != null);
		if (placed) return new Narration().say("phrase/press_the_button");
		return rounds.prompt();
	}

	/**
	 * A round that can't go on: busy far longer than any telling takes, or a subject round with
	 * nothing left to pick. It starts over with a new round, so no child (kept for lessons or not)
	 * is left in a room that can't be answered. True if it did.
	 */
	private boolean unstick(ServerPlayer player) {
		long now = level.getGameTime();
		if (!busy) busySince = Long.MIN_VALUE;
		else if (busySince == Long.MIN_VALUE) busySince = now;
		boolean hung = busy && now - busySince >= BUSY_AT_MOST;
		boolean dead = !busy && subjectRound != null && !subjectRound.answerable();
		if (!hung && !dead) return false;
		Travel.lastStuck = "STUCK: " + player.getGameProfile().name() + "'s round " + (hung ? "has been busy for " + (now - busySince) + " ticks"
			: "has nothing left to pick") + "; a new round starts. Room state: " + Rooms.stateForLog(player);
		LearningBlocks.LOGGER.error("[{}] {}", LearningBlocks.MOD_ID, Travel.lastStuck);
		Narrator.stop(player);
		busy = false;
		busySince = Long.MIN_VALUE;
		rounds.newRound(player, null, false, false);
		return true;
	}

	/** Busy since this long ago, as a narration that never finished would leave it. */
	public void hangForTests(int ticksAgo) {
		busy = true;
		busySince = level.getGameTime() - ticksAgo;
	}

	void tickMember(ServerPlayer player) {
		if (leaving(player)) return;
		portal.tick(player, this);
		if (leaving(player)) return;
		strandedLessons(player);
	}

	public void failNextOutcomeForTests() {
		failNextOutcome = true;
	}

	/** The room loses its puzzle, as it would with nothing it could ask. */
	public void dropPuzzleForTests() {
		rounds.clearRound();
		puzzle = null;
	}

	/** No puzzle and the portal locked for lessons: after a while, the lessons are let go, so nobody is shut in. */
	private void strandedLessons(ServerPlayer player) {
		int lessons = Travel.lessons(player);
		if (puzzle != null || lessons <= 0) {
			noPuzzleSince = -1;
			return;
		}
		long now = level.getGameTime();
		if (noPuzzleSince < 0) noPuzzleSince = now;
		if (now - noPuzzleSince < STRANDED_AT_MOST) return;
		justfatlard.learning_blocks.classroom.Travel.lastStuck = "STUCK: " + player.getGameProfile().name() + " is kept for " + lessons
			+ " lessons, but the room has had nothing to ask for " + (now - noPuzzleSince) + " ticks; the lessons are released. Room state: "
			+ Rooms.stateForLog(player);
		LearningBlocks.LOGGER.error("[{}] {}", LearningBlocks.MOD_ID, justfatlard.learning_blocks.classroom.Travel.lastStuck);
		Travel.setLessons(player, 0);
		justfatlard.learning_blocks.reward.Progress.of(player).lessonNote(player, lessons + " lessons let go: nothing to ask");
		noPuzzleSince = -1;
	}

	/** The child did something: the waiting starts over, and the room may ask again soon if they stop. */
	void touch() {
		lastActive = level.getGameTime();
		repeats = 0;
	}

	/** Closing: everything the room made goes, and nothing waits on it or speaks for it any more. */
	void close() {
		endPractice();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			Later.cancel(player);
			Narrator.silence(player);
			int seconds = (int) ((level.getGameTime() - openedAt) / 20);
			Safely.run("Counting time in the classroom", () -> justfatlard.learning_blocks.reward.Progress.of(player).timeSpent(player, seconds));
		}
		rounds.clearChoicesOnClose();
		if (subjectRound != null) {
			subjectRound.clear();
			subjectRound = null;
		}
		if (bob != null) {
			bob.clear();
			bob = null;
		}
		dispenser.clearAll();
		rounds.endScene();
		for (PrizeShowcase chest : showcases.values()) chest.close();
		picture.remove();
		portal.close();
		clearMarkers();
		round++;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, layout.bounds())) item.discard();
		if (player != null) Dispenser.unscale(player);
	}


	/** A right-click on a block in this room, or null where the block is none of the room's business. */
	@Nullable InteractionResult use(ServerPlayer player, BlockPos pos, InteractionHand hand) {
		return interaction.use(player, pos, hand);
	}

	@Nullable String rowSound(BlockPos pos) {
		return interaction.rowSound(pos);
	}

	boolean mayBreak(BlockPos pos, BlockState state) {
		return interaction.mayBreak(pos, state);
	}

	@Nullable BlockPos aimedGap(BlockPos hit, BlockPos target) {
		return interaction.aimedGap(hit, target);
	}

	void putInGap(ServerPlayer player, Letter letter) {
		BlockPos gap = gap();
		if (gap != null) interaction.putInGap(player, letter, gap);
	}

	void putInGap(ServerPlayer player, Letter letter, BlockPos gap) {
		interaction.putInGap(player, letter, gap);
	}

	void sendHome(ServerPlayer player, Letter letter, Vec3 from) {
		interaction.sendHome(player, letter, from);
	}

	void intoHand(ServerPlayer player, Letter letter) {
		interaction.intoHand(player, letter);
	}


	public void refreshPrizeMarker(ServerPlayer player) {
		prizes(player.getUUID()).refreshPrizeMarker(player);
	}

	void prizeAdded(ServerPlayer player, List<ItemStack> items, int xp) {
		prizes(player.getUUID()).prizeAdded(items, xp);
	}

	void refreshHaul(ServerPlayer player) {
		prizes(player.getUUID()).refreshHaul(player);
	}

	@ForTests
	public List<PrizeShowcase.PrizeFlight> prizeFlights() {
		return prizes(owner).prizeFlights();
	}

	@ForTests
	public List<PrizeShowcase.PrizeFlight> prizeFlights(UUID player) {
		return prizes(player).prizeFlights();
	}

	@ForTests
	public BlockPos prizeMarker(UUID player) {
		return prizes(player).prizeMarker();
	}

	@ForTests
	public List<ItemStack> haulShown() {
		return prizes(owner).haulShown();
	}

	public List<Display> haulEntities() {
		return prizes(owner).haulEntities();
	}

	/** What the prize chest shows: the prizes waiting, and experience as a bottle. */
	public List<ItemStack> chestContents(ServerPlayer player) {
		return prizes(player.getUUID()).chestContents(player);
	}

	void refreshPortal(ServerPlayer player, boolean flourish) {
		portal.refresh(player, flourish);
	}

	@ForTests
	public boolean portalOpen() {
		return portal.open();
	}

	@ForTests
	public boolean portalOpenFor(ServerPlayer player) {
		return portal.openFor(player);
	}

	@ForTests
	public net.minecraft.world.phys.AABB portalOpening() {
		return portal.opening();
	}

	@ForTests
	public @Nullable Entity portalSign() {
		return portal.sign();
	}


	/**
	 * Run this later for the child, but only while this room and this round are still going: a
	 * block in the air when the round ends does not land in the next one.
	 */
	void later(ServerPlayer player, int ticks, java.util.function.Consumer<ServerPlayer> what) {
		int now = round;
		Later.after(player, ticks, p -> {
			if (round == now && Rooms.of(p) == this) what.accept(p);
		});
	}

	public List<Entity> creatures() {
		return dispenser.creatures();
	}

	@ForTests
	public List<BlockPos> placed() {
		return dispenser.placed();
	}

	public Vec3 mouth() {
		return layout.mouth();
	}

	@ForTests
	public long sends() {
		return dispenser.sends();
	}

	/** What the button has sent out lately, oldest first. */
	@ForTests
	public List<Dispenser.Dispensed> dispensed() {
		return dispenser.dispensed();
	}
}
