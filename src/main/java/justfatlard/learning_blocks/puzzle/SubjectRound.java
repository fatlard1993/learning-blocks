package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
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
 * blocks on the floor to pick from by a click or a punch. The right one: it is said, the room
 * cheers, and it counts as any round does (the item's memory and mastery, prizes, the lesson, the
 * day's count, a toy earned); another: it says what it is, wiggles, and the question is asked
 * again. Everything else a round is - the turn, the door, the prompts, the idle replays - is the
 * room's, as for Reading's rounds; the room holds a stand-in puzzle meanwhile, as for Bob's.
 */
public abstract class SubjectRound {
	/** Something to pick: a block on a stand, what it says, and what the caption shows. */
	public record Choice(String id, BlockState block, String clip, String said) {}

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

	/** Whose round it is. */
	public abstract Subject subject();

	/** The kind of round, for the log and the sampler. */
	public abstract String kind();

	/** The item it asks: {@code numbers:numeral:3}. */
	public abstract String item();

	/** The right answer among the choices. */
	protected abstract Choice answer();

	/** The board: what the question shows. */
	protected abstract void board();

	/** The question, said. */
	public abstract Narration ask(Narration narration);

	/** Whether this child has finished the subject's level: its "ready for more" moment, once. */
	protected abstract boolean levelDone(Progress progress);

	/** The level's key, for the stage_complete prize paid once: {@code numbers:1}. */
	protected abstract String levelKey();

	/** The "ready for more" line, said when the level is done. */
	protected abstract String nextLevelLine();

	/** Its words, for the caption while it is unrecorded. */
	protected abstract String nextLevelWords();

	/** After the right answer: anything it shows (the pattern's gap filled). */
	protected void showRight(ServerPlayer player, Choice right) {}

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

	/** Laid out: the board, and the choices on stands, each with a box that hears it picked. */
	void start(ServerPlayer player) {
		board();
		for (int i = 0; i < choices.size(); i++) {
			Choice choice = choices.get(i);
			BlockPos spot = layout.shelfSpot(i, choices.size());
			set(spot, choice.block());
			net.minecraft.world.entity.Interaction hit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
			if (hit == null) continue;
			Vec3 at = Vec3.atBottomCenterOf(spot);
			hit.snapTo(at.x, at.y, at.z, 0, 0);
			hit.setWidth(1.1F);
			hit.setHeight(1.2F);
			hit.setResponse(true);
			hit.addTag(TAG);
			level.addFreshEntity(hit);
			hits.put(hit.getUUID(), choice);
			made.add(hit.getUUID());
		}
	}

	/** A block the round put down, to be taken up again with it. */
	protected void set(BlockPos pos, BlockState state) {
		level.setBlock(pos, state, RoomLayout.FLAGS);
		placed.add(pos.immutable());
	}

	/** An entity the round made, to go with it. */
	protected void made(Entity entity) {
		entity.addTag(TAG);
		made.add(entity.getUUID());
	}

	/** Where a choice stands. */
	protected @Nullable BlockPos spotOf(Choice choice) {
		int i = choices.indexOf(choice);
		return i < 0 ? null : layout.shelfSpot(i, choices.size());
	}

	/** A click or punch on one of the choices' boxes: true if it was one. */
	boolean click(ServerPlayer player, Entity entity) {
		Choice picked = hits.get(entity.getUUID());
		if (picked == null) return false;
		if (!room.mayPress(player)) {
			room.notYourTurn(player, room.turn);
			return true;
		}
		pick(player, picked);
		return true;
	}

	/** The right choice. For tests. */
	public Choice rightForTests() {
		return answer();
	}

	/** A choice picked, as a click on it does. For tests. */
	public void pickForTests(ServerPlayer player, Choice choice) {
		pick(player, choice);
	}

	private void pick(ServerPlayer player, Choice picked) {
		if (room.busy || room.leaving) return;
		room.touch();
		Narrator.note(player, "picked:" + subject().id() + ":" + picked.id());
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		Narration telling = new Narration();
		Progress progress = Progress.of(player);
		String item = item();
		if (picked.equals(answer())) {
			boolean firstTry = !room.missedThisRound;
			int lessonsBefore = AnswerCommit.lessonsBefore(player);
			boolean justMastered = !room.practice && firstTry && progress.itemCountsToday(item) && progress.itemClean(item) + 1 == Progress.masteredAt();
			boolean wasDone = levelDone(progress);
			room.answers.commit().right(player, subject().id() + " " + picked.id(), () -> {
				Progress.of(player).itemRight(player, item, firstTry);
				Prizes.itemSolved(player, item, firstTry);
				Earned.checkItem(player, Progress.of(player), item);
			}, lessonsBefore);
			boolean nowDone = !room.practice && !wasDone && levelDone(Progress.of(player));
			if (nowDone) Safely.run("A level of " + subject().id() + " done", () -> Prizes.levelComplete(player, levelKey()));
			happened[0] = true;
			telling.say(picked.clip(), "right:" + picked.id(), p -> showRight(p, picked), CaptionLine.plain(picked.said()), AnswerNarration.WORD_GAP);
			if (nowDone) line(telling.then("next_level", p -> room.rounds.flourish(p), 2), nextLevelLine(), nextLevelWords());
			// Not "a new word!": a new item's prizes still come, in the prize line.
			room.answers.closing(player, telling, lessonsBefore, false, false, justMastered);
		} else {
			room.answers.commit().wrong(player);
			if (!room.practice) Safely.run("Remembering a miss", () -> Progress.of(player).itemMissed(player, item, picked.id()));
			BlockPos at = spotOf(picked);
			telling.then("wiggle", p -> wiggle(at), 2)
				.say("phrase/oops_thats", 2)
				.say(picked.clip(), "said:" + picked.id(), p -> {}, CaptionLine.plain(picked.said()), AnswerNarration.WORD_GAP);
			ask(telling);
		}
		room.answers.finish(player, telling, happened, roundAt);
	}

	/** A friendly little hop and a puff at the one picked that was not it. */
	private void wiggle(@Nullable BlockPos at) {
		if (at == null) return;
		Vec3 top = Vec3.atCenterOf(at).add(0, 0.6, 0);
		level.sendParticles(ParticleTypes.NOTE, top.x, top.y + 0.3, top.z, 3, 0.3, 0.1, 0.3, 1.0);
		level.playSound(null, at, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.4F);
	}

	/** Everything it put down and made, gone. */
	void clear() {
		for (UUID id : made) {
			Entity entity = level.getEntity(id);
			if (entity != null) entity.discard();
		}
		for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), RoomLayout.FLAGS);
		made.clear();
		placed.clear();
		hits.clear();
		for (Entity entity : level.getEntities((Entity) null, layout.bounds(), entity -> entity.entityTags().contains(TAG))) entity.discard();
	}
}
