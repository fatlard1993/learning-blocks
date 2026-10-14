package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.reward.Earned;
import justfatlard.learning_blocks.reward.Prizes;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Subject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A round of a subject other than Reading: a question on the board and said aloud, and a few
 * things to pick from by a click or a punch - blocks on the floor, by default, or whatever a kind
 * stands there (pictures, pens, towers). The right one: it is told, the room cheers, and it counts
 * as any round does (the item's memory and mastery, prizes, the lesson, the day's count, a toy
 * earned); another: it is told what it is, wiggles, and the question is asked again. A kind can
 * hold a pick without a verdict (a tile put in a gap, the next number put in a row) and give the
 * verdict later, from the button. A kind whose choices are number tiles has them broken loose, carried
 * and placed in its gap, as letters are: the tile put there is the pick. Everything else a round is - the turn, the portal, the prompts,
 * the idle replays - is the room's, as for Reading's rounds; the room holds a stand-in puzzle
 * meanwhile, as for Bob's.
 */
public abstract class SubjectRound {
	/** Something to pick: a block on a stand (or null, for a kind that stands something else there), what it says, and what the caption shows. */
	public record Choice(String id, @Nullable BlockState block, String clip, String said) {}

	/** What a pick comes to: right, wrong, or held for later (a tile put in a gap, the next one of a row). */
	protected enum Verdict { RIGHT, WRONG, HOLD }

	/** On everything a subject's round makes, so what slips through (a restart mid-round) is swept up with the next. */
	static final String TAG = justfatlard.learning_blocks.LearningBlocks.MOD_ID + ".subject";

	protected final Room room;
	protected final ServerLevel level;
	protected final RoomLayout layout;
	protected final List<Choice> choices = new ArrayList<>();
	private final Map<UUID, Choice> hits = new LinkedHashMap<>();
	private final List<BlockPos> placed = new ArrayList<>();
	private final List<UUID> made = new ArrayList<>();

	protected SubjectRound(Room room) {
		this.room = room;
		this.level = room.level;
		this.layout = room.layout;
	}

	public abstract Subject subject();

	public abstract String kind();

	public abstract String item();

	/** The right answer among the choices (one of them, where several would do). */
	protected abstract Choice answer();

	protected abstract void board();

	public abstract Narration ask(Narration narration);

	/** Whether this child has finished the subject's level: its "ready for more" moment, once. */
	protected abstract boolean levelDone(Progress progress);

	/** The level's key, for the stage_complete prize paid once: {@code numbers:N0}. */
	protected abstract String levelKey();

	/** The "ready for more" line, said when the level is done. */
	protected abstract String nextLevelLine();

	/** Its words, for the caption while it is unrecorded. */
	protected abstract String nextLevelWords();

	/** What this pick comes to: by default, right if it is the answer. */
	protected Verdict judge(ServerPlayer player, Choice picked) {
		return picked.equals(answer()) ? Verdict.RIGHT : Verdict.WRONG;
	}

	/** What happens on a pick before its verdict is told (Bob does what the card says): nothing, by default. */
	protected @Nullable Narration act(ServerPlayer player, Choice picked) {
		return null;
	}

	/** A held pick: what it does and says (the tile into the gap). */
	protected void hold(ServerPlayer player, Choice picked, Narration telling) {}

	/** The right answer told: by default, the choice says itself, and anything it shows is shown. */
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		telling.say(picked.clip(), "right:" + picked.id(), p -> showRight(p, picked), CaptionLine.plain(picked.said()), AnswerNarration.WORD_GAP);
	}

	/** A wrong answer told: by default, a wiggle, "oops, that's" and what it is; then the question again. */
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		BlockPos at = spotOf(picked);
		telling.then("wiggle", p -> wiggle(at), 2)
			.say("phrase/oops_thats", 2)
			.say(picked.clip(), "said:" + picked.id(), p -> {}, CaptionLine.plain(picked.said()), AnswerNarration.WORD_GAP);
	}

	/** After the right answer: anything it shows (the pattern's gap filled). */
	protected void showRight(ServerPlayer player, Choice right) {}

	/** The button pressed in this round: true if the round answers it itself; otherwise it asks again. */
	protected boolean press(ServerPlayer player) {
		return false;
	}

	/** Where choice {@code i} of {@code n} stands: on the floor in front of the board, by default. */
	protected BlockPos spotFor(int i, int n) {
		return layout.shelfSpot(i, n);
	}

	/** How big the box that hears a choice picked is: a block, by default. */
	protected float hitWidth(Choice choice) {
		return 1.1F;
	}

	protected float hitHeight(Choice choice) {
		return 1.5F;
	}

	/** Where a choice's box stands: on its spot, by default; a kind whose things stand off the block grid says where. */
	protected Vec3 hitSpot(Choice choice, int i, int n) {
		return Vec3.atBottomCenterOf(spotFor(i, n));
	}

	/** What stands at a choice's spot: its block, by default. */
	protected void stand(Choice choice, BlockPos spot) {
		if (choice.block() != null) set(spot, choice.block());
	}

	/** A line said, captioned with its words while it is still unrecorded (its placeholder plays). */
	protected static Narration line(Narration narration, String clip, String words) {
		if (justfatlard.learning_blocks.sound.Sounds.isRecorded(clip)) return narration.say(clip, 2);
		return narration.say(clip, "line:" + clip, p -> {}, CaptionLine.plain(words), 2);
	}

	public List<Choice> choices() {
		return List.copyOf(choices);
	}

	/** A stand-in for the room's puzzle, so everything that asks whether there is a round is told yes. */
	Puzzle placeholder() {
		Word anchor = Words.all().stream().filter(word -> word.text().equals("cat")).findFirst().orElse(Words.all().getFirst());
		return new Puzzle(anchor, List.of(anchor), List.of(), 0, Puzzle.Kind.WHOLE);
	}

	void start(ServerPlayer player) {
		board();
		BlockPos gap = tileGap();
		if (gap != null && choices.stream().anyMatch(this::carried) && level.getBlockState(gap).isAir()) {
			set(gap, justfatlard.learning_blocks.LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
		}
		for (int i = 0; i < choices.size(); i++) {
			Choice choice = choices.get(i);
			BlockPos spot = spotFor(i, choices.size());
			stand(choice, spot);
			if (!boxed(choice)) continue;
			net.minecraft.world.entity.Interaction hit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
			if (hit == null) continue;
			Vec3 at = hitSpot(choice, i, choices.size());
			hit.snapTo(at.x, at.y, at.z, 0, 0);
			hit.setWidth(hitWidth(choice));
			hit.setHeight(hitHeight(choice));
			hit.setResponse(true);
			hit.addTag(TAG);
			level.addFreshEntity(hit);
			hits.put(hit.getUUID(), choice);
			made.add(hit.getUUID());
		}
		started(player);
	}

	/** After the board and the choices: anything else a kind sets going (creatures out of the button). */
	protected void started(ServerPlayer player) {}

	protected void set(BlockPos pos, BlockState state) {
		level.setBlock(pos, state, RoomLayout.FLAGS);
		placed.add(pos.immutable());
	}

	protected <T extends Entity> T add(T entity) {
		made(entity);
		level.addFreshEntity(entity);
		return entity;
	}

	protected void made(Entity entity) {
		entity.addTag(TAG);
		made.add(entity.getUUID());
	}

	/**
	 * Whether a choice gets a box at all. A box past a child's reach for things (three blocks) stops a
	 * click there, rather than letting it through to the block behind: a choice as big as a pen is
	 * better clicked as its own blocks ({@link #choiceAt}), which reach further.
	 */
	protected boolean boxed(Choice choice) {
		return choice.block() == null;
	}

	/** A choice's box made this size: smaller to let a click past it, back to its own after. */
	protected void resizeHit(Choice choice, float width, float height) {
		for (Map.Entry<UUID, Choice> entry : hits.entrySet()) {
			if (!entry.getValue().equals(choice)) continue;
			if (level.getEntity(entry.getKey()) instanceof net.minecraft.world.entity.Interaction box) {
				box.setWidth(width);
				box.setHeight(height);
			}
		}
	}

	/** A choice's box gone: it can't be picked again (a tile put in a row). */
	private final java.util.Set<Choice> retired = new java.util.HashSet<>();

	protected void unhit(Choice choice) {
		retired.add(choice);
		owned.values().removeIf(choice::equals);
		hits.entrySet().removeIf(entry -> {
			if (!entry.getValue().equals(choice)) return false;
			Entity box = level.getEntity(entry.getKey());
			if (box != null) box.discard();
			return true;
		});
	}

	protected @Nullable BlockPos spotOf(Choice choice) {
		int i = choices.indexOf(choice);
		return i < 0 ? null : spotFor(i, choices.size());
	}

	/** Where this choice's box stands. */
	@ForTests
	public Vec3 hitSpotOf(Choice choice) {
		return hitSpot(choice, choices.indexOf(choice), choices.size());
	}

	/** A thing that stands for a choice: a click on it counts as one on the choice's box. */
	protected void clickable(Entity thing, Choice choice) {
		owned.put(thing.getUUID(), choice);
	}

	private final Map<UUID, Choice> owned = new LinkedHashMap<>();

	boolean click(ServerPlayer player, Entity entity) {
		Choice picked = hits.get(entity.getUUID());
		if (picked == null) picked = owned.get(entity.getUUID());
		if (picked == null) return false;
		return clickChoice(player, past(player, picked), entity.position().add(0, entity.getBbHeight() / 2, 0));
	}

	/** What a click on this choice's box was really aimed at: the choice itself, unless a kind knows better. */
	protected Choice past(ServerPlayer player, Choice clicked) {
		return clicked;
	}

	/** The block a choice is made of, where a click on the block itself counts too: a pen's rug, past a box's reach. */
	protected @Nullable Choice choiceAt(BlockPos pos) {
		for (int i = 0; i < choices.size(); i++) {
			Choice choice = choices.get(i);
			if (choice.block() != null && !carried(choice) && !retired.contains(choice) && spotFor(i, choices.size()).equals(pos)
				&& level.getBlockState(pos).equals(choice.block())) return choice;
		}
		return null;
	}

	boolean clickBlock(ServerPlayer player, BlockPos pos, Vec3 at) {
		Choice picked = choiceAt(pos);
		return picked != null && clickChoice(player, picked, at);
	}

	private boolean clickChoice(ServerPlayer player, Choice picked, Vec3 at) {
		java.util.UUID whose = whoseTurn();
		if (whose != null ? !whose.equals(player.getUUID()) : !teamUp() && !room.mayPress(player)) {
			room.notYourTurn(player, whose != null ? whose : room.turn, at);
			return true;
		}
		pick(player, picked);
		return true;
	}

	/** Whether this choice is a tile broken loose, carried and placed in the gap, rather than clicked. */
	protected boolean carried(Choice choice) {
		return false;
	}

	/** Where a carried tile is placed to answer with it: an underscore on the board, put there if nothing else is. */
	protected @Nullable BlockPos tileGap() {
		return null;
	}

	/** Whether a tile used on {@code hit}, aimed at {@code target}, is going into the gap: the gap itself or the board round it. */
	protected boolean aimedAtGap(BlockPos hit, BlockPos target) {
		BlockPos gap = tileGap();
		if (gap == null) return false;
		if (target.equals(gap) || hit.equals(gap)) return true;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
			if (side != net.minecraft.core.Direction.SOUTH && hit.equals(gap.relative(side)) && !level.getBlockState(hit).isAir()) return true;
		}
		return false;
	}

	/** The number in the gap, and where: placed there and not yet settled, so it can be taken back out or sent home. */
	private @Nullable BlockPos tileAt;
	private int tileIn = -1;

	private @Nullable Choice carriedChoice(int number) {
		for (Choice choice : choices) if (carried(choice) && !retired.contains(choice) && tileNumber(choice) == number) return choice;
		return null;
	}

	private static int tileNumber(Choice choice) {
		BlockState block = choice.block();
		return block != null && block.hasProperty(NumberBlock.NUMBER) ? block.getValue(NumberBlock.NUMBER) : -1;
	}

	private boolean tileInGap(BlockPos pos) {
		return tileIn >= 0 && pos.equals(tileAt) && pos.equals(tileGap());
	}

	/** Whether this block is a carried tile a child may knock loose: on its own spot, or in the gap and not yet settled. */
	boolean mayBreak(BlockPos pos, BlockState state) {
		if (room.busy || !state.is(justfatlard.learning_blocks.LearningBlocks.NUMBER)) return false;
		if (tileInGap(pos)) return true;
		Choice choice = carriedChoice(state.getValue(NumberBlock.NUMBER));
		return choice != null && pos.equals(spotOf(choice)) && state.equals(choice.block());
	}

	/**
	 * A tile knocked loose, into the child's hand, as a letter goes. One already in hand goes back to its
	 * spot; anything else in hand moves into the pack. Out of the gap, the gap is empty again.
	 */
	void intoHand(ServerPlayer player, BlockPos from, int number) {
		if (tileInGap(from)) {
			tileAt = null;
			tileIn = -1;
			set(from, justfatlard.learning_blocks.LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
			leftGap();
		}
		net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
		int hand = inventory.getSelectedSlot();
		net.minecraft.world.item.ItemStack held = inventory.getItem(hand);
		inventory.setItem(hand, Tiles.stack(number));
		int had = Tiles.numberOf(held);
		if (had >= 0) sendHome(player, had, player.getEyePosition());
		else if (Tiles.letterOf(held) == null && !held.isEmpty() && !inventory.add(held)) player.drop(held, false, net.minecraft.util.Prediction.SERVER_ONLY);
		player.containerMenu.broadcastChanges();
	}

	/** A held pick taken back out of the gap by hand (the tile in Make's gap): forget it. */
	protected void leftGap() {}

	/**
	 * A carried tile placed in the gap: it sits there, and is the pick. One already there goes home. A
	 * wrong one flies home again once it has been told, so it can be tried again. False if it is not
	 * this child's turn, and the tile stays in their hand.
	 */
	boolean place(ServerPlayer player, int number, Vec3 at) {
		Choice picked = carriedChoice(number);
		BlockPos gap = tileGap();
		if (picked == null || gap == null) return false;
		java.util.UUID whose = whoseTurn();
		if (whose != null ? !whose.equals(player.getUUID()) : !teamUp() && !room.mayPress(player)) {
			room.notYourTurn(player, whose != null ? whose : room.turn, at);
			return false;
		}
		int old = tileInGap(gap) ? tileIn : -1;
		set(gap, justfatlard.learning_blocks.LearningBlocks.NUMBER.showing(number));
		tileAt = gap.immutable();
		tileIn = number;
		if (old >= 0 && old != number) sendHome(player, old, Vec3.atCenterOf(gap));
		Verdict verdict = judge(player, picked);
		pick(player, picked);
		if (verdict == Verdict.WRONG) {
			room.later(player, WRONG_TILE_HOME, p -> {
				if (tileAt == null || tileIn != number || !level.getBlockState(tileAt).equals(justfatlard.learning_blocks.LearningBlocks.NUMBER.showing(number))) return;
				returnGap(p);
			});
		}
		return true;
	}

	@ForTests
	public @Nullable BlockPos gapForTests() {
		return tileGap();
	}

	/** Long enough for "Oops, that's..." to start over the tile before it goes. */
	private static final int WRONG_TILE_HOME = 30;

	/** The tile in the gap, home to its spot, and the underscore back. */
	protected void returnGap(ServerPlayer player) {
		if (tileAt == null || tileIn < 0) return;
		BlockPos at = tileAt;
		int number = tileIn;
		tileAt = null;
		tileIn = -1;
		set(at, justfatlard.learning_blocks.LearningBlocks.BOARD_LETTER.showing(Letter.BLANK));
		sendHome(player, number, Vec3.atCenterOf(at));
	}

	/** A carried tile flies from where it is back to its spot on the floor and lands there; one already home is not doubled. */
	void sendHome(ServerPlayer player, int number, Vec3 from) {
		Choice choice = carriedChoice(number);
		BlockPos home = choice == null ? null : spotOf(choice);
		if (home == null || level.getBlockState(home).equals(choice.block()) || room.dispenser.reserved.contains(home)) return;
		room.dispenser.reserved.add(home);
		net.minecraft.world.entity.item.ItemEntity visual = room.dispenser.throwItem(from, Vec3.atBottomCenterOf(home).add(0, 0.2, 0), Tiles.stack(number), Dispenser.FLIGHT);
		visual.setNeverPickUp();
		level.addFreshEntity(visual);
		made(visual);
		room.later(player, Dispenser.FLIGHT, p -> {
			visual.discard();
			room.dispenser.reserved.remove(home);
			if (room.subjectRound != this || !level.getBlockState(home).isAir()) return;
			level.setBlock(home, choice.block(), RoomLayout.FLAGS);
			justfatlard.learning_blocks.sound.Sounds.playAt(level, home, NumberBlock.soundOf(number));
		});
	}

	/**
	 * Every carried tile somewhere it can be used. One in a pack but not in the hand goes home, as a
	 * letter does; one that is nowhere - not on its spot, in the gap, in a hand, on the ground or on
	 * its way home - comes back to its spot.
	 */
	void keepTiles() {
		if (choices.stream().noneMatch(this::carried)) return;
		java.util.Set<Integer> found = new java.util.HashSet<>();
		if (tileAt != null && tileIn >= 0) found.add(tileIn);
		for (ServerPlayer one : room.present()) {
			net.minecraft.world.entity.player.Inventory inventory = one.getInventory();
			boolean moved = false;
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				int number = Tiles.numberOf(inventory.getItem(slot));
				if (number < 0) continue;
				if (slot == inventory.getSelectedSlot()) {
					found.add(number);
					continue;
				}
				inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
				sendHome(one, number, one.getEyePosition());
				moved = true;
			}
			int cursor = Tiles.numberOf(one.containerMenu.getCarried());
			if (cursor >= 0) found.add(cursor);
			if (moved) one.containerMenu.broadcastChanges();
		}
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, layout.bounds())) {
			int number = Tiles.numberOf(item.getItem());
			if (number >= 0) found.add(number);
		}
		for (Choice choice : choices) {
			if (!carried(choice) || retired.contains(choice) || found.contains(tileNumber(choice))) continue;
			BlockPos home = spotOf(choice);
			if (home == null || level.getBlockState(home).equals(choice.block()) || room.dispenser.reserved.contains(home)) continue;
			if (level.getBlockState(home).isAir()) level.setBlock(home, choice.block(), RoomLayout.FLAGS);
		}
	}

	/** Whether this round is a team-up: right, it counts for every reader in the room. */
	protected boolean teamUp() {
		return false;
	}

	/** Reading together, whose pick it is when a kind decides that itself (a team-up's halves); null for the room's turn. */
	protected java.util.@Nullable UUID whoseTurn() {
		return null;
	}

	/** Whether the answer is in place and only the button is left (a tile in Make's gap). */
	protected boolean waitingForButton() {
		return false;
	}

	boolean answerable() {
		for (UUID id : hits.keySet()) if (level.getEntity(id) != null) return true;
		for (int i = 0; i < choices.size(); i++) {
			Choice choice = choices.get(i);
			if (boxed(choice) || retired.contains(choice)) continue;
			if (carried(choice)) return true;
			if (choice.block() == null || level.getBlockState(spotFor(i, choices.size())).equals(choice.block())) return true;
		}
		return false;
	}

	/** Every choice's box gone, as a chunk unloading or another mod might take them. */
	public void loseChoicesForTests() {
		for (UUID id : hits.keySet()) {
			Entity box = level.getEntity(id);
			if (box != null) box.discard();
		}
		for (Choice choice : choices) if (!boxed(choice)) retired.add(choice);
	}

	void pressed(ServerPlayer player) {
		if (room.busy || room.leaving) return;
		room.touch();
		if (!press(player)) room.replay(player);
	}

	public Choice rightForTests() {
		return answer();
	}

	public void pickForTests(ServerPlayer player, Choice choice) {
		pick(player, choice);
	}

	public void pressForTests(ServerPlayer player) {
		pressed(player);
	}

	/** Readers who picked a part of this round: in a team-up, each of them built some of it. */
	private final java.util.Set<java.util.UUID> pickers = new java.util.HashSet<>();

	private void pick(ServerPlayer player, Choice picked) {
		if (room.busy || room.leaving) return;
		room.touch();
		pickers.add(player.getUUID());
		Narrator.note(player, "picked:" + subject().id() + ":" + picked.id());
		Verdict verdict = judge(player, picked);
		if (verdict == Verdict.HOLD) {
			Narration telling = new Narration();
			hold(player, picked, telling);
			room.answers.finish(player, telling, new boolean[1], room.round);
			return;
		}
		conclude(player, picked, verdict == Verdict.RIGHT, act(player, picked));
	}

	/**
	 * The verdict: right (counted, told, and on to the next round) or wrong (told, and asked again).
	 * {@code before}, if given, is said first: what the button did with a held pick.
	 */
	protected void conclude(ServerPlayer player, Choice picked, boolean right, @Nullable Narration before) {
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		Narration telling = new Narration();
		if (before != null) for (Narration.Step step : before.steps()) telling.add(step);
		Progress progress = Progress.of(player);
		String item = item();
		if (right) {
			boolean firstTry = !room.missedThisRound;
			int lessonsBefore = AnswerCommit.lessonsBefore(player);
			boolean justMastered = !room.practice && firstTry && progress.itemCountsToday(item) && progress.itemClean(item) + 1 == Progress.masteredAt();
			boolean wasDone = levelDone(progress);
			room.answers.commit().right(player, subject().id() + " " + picked.id(), () -> {
				Progress.of(player).itemRight(player, item, firstTry);
				Prizes.itemSolved(player, item, firstTry);
				Earned.checkItem(player, Progress.of(player), item);
			}, lessonsBefore);
			// A team-up counts in full for each reader who built a part of it; others who helped have the lesson.
			if (teamUp() && room.shared()) {
				for (ServerPlayer other : room.present()) {
					if (other == player || !pickers.contains(other.getUUID())) continue;
					int otherBefore = AnswerCommit.lessonsBefore(other);
					room.answers.commit().right(other, subject().id() + " " + picked.id() + " together", () -> {
						Progress.of(other).itemRight(other, item, firstTry);
						Prizes.itemSolved(other, item, firstTry);
						Earned.checkItem(other, Progress.of(other), item);
					}, otherBefore);
					if (otherBefore == 1) room.refreshPortal(other, true);
					telling.add(room.prizes(other.getUUID()).prizeLineFor(other));
				}
				telling.say("phrase/great_teamwork", 2);
			}
			boolean nowDone = !room.practice && !wasDone && levelDone(Progress.of(player));
			if (nowDone) Safely.run("A level of " + subject().id() + " done", () -> Prizes.levelComplete(player, levelKey()));
			happened[0] = true;
			tellRight(player, picked, telling);
			if (nowDone) line(telling.then("next_level", p -> room.rounds.flourish(p), 2), nextLevelLine(), nextLevelWords());
			// Not "a new word!": a new item's prizes still come, in the prize line.
			room.answers.closing(player, telling, lessonsBefore, false, false, justMastered);
		} else {
			room.answers.commit().wrong(player);
			if (!room.practice) Safely.run("Remembering a miss", () -> Progress.of(player).itemMissed(player, item, picked.id()));
			tellWrong(player, picked, telling);
			ask(telling);
		}
		room.answers.finish(player, telling, happened, roundAt);
	}

	protected void wiggle(@Nullable BlockPos at) {
		if (at == null) return;
		Vec3 top = Vec3.atCenterOf(at).add(0, 0.6, 0);
		level.sendParticles(ParticleTypes.NOTE, top.x, top.y + 0.3, top.z, 3, 0.3, 0.1, 0.3, 1.0);
		level.playSound(null, at, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.4F);
	}

	void clear() {
		for (ServerPlayer one : room.present()) {
			net.minecraft.world.entity.player.Inventory inventory = one.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				if (Tiles.numberOf(inventory.getItem(slot)) >= 0) inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
			}
			one.containerMenu.broadcastChanges();
		}
		tileAt = null;
		tileIn = -1;
		for (UUID id : made) {
			Entity entity = level.getEntity(id);
			if (entity != null) entity.discard();
		}
		for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), RoomLayout.FLAGS);
		made.clear();
		placed.clear();
		hits.clear();
		owned.clear();
		for (Entity entity : level.getEntities((Entity) null, layout.bounds(), entity -> entity.entityTags().contains(TAG))) entity.discard();
	}
}
