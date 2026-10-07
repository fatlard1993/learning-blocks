package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Captions;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A round from start to finish: what it asks (a letter, a word of some kind, or a scene), laying
 * it out on the board and the floor, asking it, and clearing it all away for the next.
 */
final class RoundLifecycle {
	private static final int FLAGS = RoomLayout.FLAGS;
	private static final int HALF = RoomLayout.HALF;

	private final Room room;
	private final ServerLevel level;
	private final RoomLayout layout;

	RoundLifecycle(Room room, ServerLevel level, RoomLayout layout) {
		this.room = room;
		this.level = level;
		this.layout = layout;
	}

	/** What this round was asked to be, while it is being made. */
	private RoundRequest req = RoundRequest.NONE;

	void newRound(ServerPlayer asked, RoundRequest request) {
		if (room.leaving) return;
		req = request;
		nudgeIdle();
		room.tookPart.clear();
		room.credited.clear();
		Word target = request.target();
		boolean stageSet = request.stageSet();
		boolean asScene = request.asScene();
		// The round after a practice round is an ordinary one, with everybody's own progress.
		if (room.practice && !room.practiceStarting) room.endPractice();
		boolean hadRound = room.puzzle != null;
		// Reading together: the round is the next reader's, picked from their own place in the course.
		boolean forced = req.particular();
		ServerPlayer player = room.shared() ? room.takeTurn(asked, hadRound && !forced) : asked;
		if (otherSubject(player)) return;
		noteSubject(player, justfatlard.learning_blocks.subject.Subjects.READING);
		int stageBefore = room.stage;
		Stages.BandCode bandBefore = room.band;
		clearRound();
		room.round++;
		Progress progress = Progress.of(player);
		room.stage = Stages.current(progress);
		int stage = room.stage;
		Stages.Band band = Stages.currentBand(progress);
		room.band = band != null ? band.code() : null;
		if (room.shared()) {
			// Each reader's own last stage and band: the other's being different is no fresh start.
			Room.Place last = room.lastStage.get(player.getUUID());
			stageBefore = last == null ? stage : last.stage();
			bandBefore = last == null ? room.band : last.band();
		}
		room.lastStage.put(player.getUUID(), new Room.Place(stage, room.band));
		// Up a stage or a band (or put at one): a clean slate, and "You're ready for new words!" first.
		boolean freshStage = stageSet || hadRound && target == null && (stage != stageBefore || !java.util.Objects.equals(room.band, bandBefore));
		if (freshStage) resetForStage(player);
		BobRound.Mode bobMode = bobDue(target, freshStage, stage);
		if (bobMode != null) {
			BobRound bob = BobRound.make(room, level, layout, bobMode, player, req.goal(), req.mistake());
			if (bob == null && bobMode != BobRound.Mode.GOAL && bobMode != BobRound.Mode.SILLY && req.goal() == null) {
				bob = BobRound.make(room, level, layout, BobRound.Mode.GOAL, player, null, null);
			}
			if (bob != null) {
				startBob(player, progress, bob);
				return;
			}
		}
		if (target == null && !freshStage) room.sinceBob++;
		// The letters level asks for a letter, not a word; now and then, once the first letters are
		// known, a word made of nothing but letters the child knows.
		boolean wordDue = req.letter() == null && Letters.wordDue(progress, room.sinceEarlyWord);
		boolean sampling = req.team() || req.puzzle() != null;
		Puzzle early = target == null && stage == 0 && wordDue && !sampling
			? Letters.earlyWord(progress, level.getRandom(), Words.all(), room.lastEarlyWord) : null;
		boolean letterAskedFor = req.letter() != null;
		Letter letter = early == null && target == null && !sampling && (stage == 0 || req.letter() != null)
			? req.letter() != null ? req.letter() : Letters.pickTarget(progress, level.getRandom(), room.lastLetter)
			: null;
		CaseAsk caseAsk = caseDue(progress, target == null && early == null, letter != null && stage == 0 && !letterAskedFor,
			stage != 0 && !freshStage && !sampling);
		Letter caseBase = caseAsk == null ? null : caseAsk.base();
		boolean caseUp = caseAsk != null && caseAsk.up();
		if (caseBase != null) letter = caseUp ? caseBase.upper() : caseBase;
		room.casePrompt = caseBase == null ? null : caseUp ? caseBase : caseBase.upper();
		if (caseBase != null) Narrator.note(player, "case:" + (caseUp ? "big:" : "little:") + caseBase.grapheme());
		if (early != null) {
			room.sinceEarlyWord = 0;
			room.lastEarlyWord = early.target();
			Narrator.note(player, "early_word:" + early.target().text());
		} else if (letter != null) room.sinceEarlyWord++;
		Puzzle.Kind kind = req.kind();
		// A word they know asked as a little problem instead; never a new stage's first round.
		Word sceneWord = asScene && target != null && target.usableScene().isPresent() ? target
			: target == null && !freshStage && letter == null && early == null && !req.team() && req.puzzle() == null ? pickScene(progress) : null;
		if (target == null && sceneWord != null) target = sceneWord;
		// A word already read, shown whole and not said, its picture to be picked: a read-it round.
		Puzzle readIt = req.readIt() != null ? readIt(progress, req.readIt())
			: target == null && sceneWord == null && letter == null && early == null && !freshStage && !req.team() && req.puzzle() == null
				? pickReadIt(progress) : null;
		if (target == null && readIt == null && letter == null && early == null && sceneWord == null && !req.team() && req.puzzle() == null) {
			target = reviewDue(player, progress);
		}
		boolean free = target == null && sceneWord == null && letter == null && early == null && !freshStage && !req.team() && req.puzzle() == null;
		Puzzle special = specialDue(progress, stage, free && readIt == null);
		boolean isTricky = special != null && special.target().tricky();
		boolean isPlural = special != null && special.kind() == Puzzle.Kind.PLURAL;
		if (isTricky) {
			room.tricky = true;
			readIt = special;
			room.sinceTricky = 0;
			Narrator.note(player, "tricky:" + special.target().text());
		} else if (free) room.sinceTricky++;
		if (isPlural) {
			room.sincePlural = 0;
			Narrator.note(player, "plural:" + special.target().text());
		} else if (free) room.sincePlural++;
		if (special != null && !isTricky && !isPlural) {
			room.sincePhrase = 0;
			Narrator.note(player, "phrase_round:" + room.phraseAdjective.text() + "_" + special.target().text());
		} else if (free) room.sincePhrase++;
		if (special != null && !isTricky) readIt = null;
		room.readIt = readIt != null;
		if (readIt != null && !isTricky) {
			room.sinceReadIt = 0;
			Narrator.note(player, "read_it:" + readIt.target().text());
		} else if (letter == null && early == null && sceneWord == null) room.sinceReadIt++;
		Puzzle next = readIt != null ? readIt
			: special != null ? special
			: early != null ? early
			: caseBase != null ? casePuzzle(progress, caseBase, caseUp)
			: letter != null ? letterPuzzle(progress, letter)
			: target != null && kind != null ? Puzzle.forTarget(target, Words.all(), level.getRandom(), kind)
			: target != null ? Puzzle.forTarget(target, Words.all(), level.getRandom())
			: null;
		if (req.puzzle() != null) {
			if (next == null) next = req.puzzle();
		}
		if (next == null && target == null && letter == null && early == null && room.shared()) next = teamUpDue(player);
		if (next == null && target == null && letter == null && early == null) {
			Fill fill = freshPick(player, progress, stage, band);
			next = fill.puzzle();
			if (fill.readIt()) room.readIt = true;
			if (fill.scene() != null) sceneWord = fill.scene();
			if (fill.caseBase() != null) {
				letter = fill.caseUp() ? fill.caseBase().upper() : fill.caseBase();
				room.casePrompt = fill.caseUp() ? fill.caseBase() : fill.caseBase().upper();
			}
		}
		room.letterTarget = letter;
		if (letter != null) {
			room.lastLetter = letter.lower();
			room.letterPrompt = room.letterPrompt.equals("phrase/which_letter_says") ? "phrase/find_the_letter" : "phrase/which_letter_says";
		}
		boolean plainWord = next != null && letter == null && early == null && !room.readIt && special == null && sceneWord == null && !room.team
			&& room.phraseAdjective == null && (next.kind() == Puzzle.Kind.FIRST || next.kind() == Puzzle.Kind.LAST || next.kind() == Puzzle.Kind.MIDDLE);
		room.fixLetter = fixDue(player, plainWord ? next : null, target == null && !freshStage && Stages.reached(stage, Stages.FIX_FROM));
		room.puzzle = next;
		room.busy = false;
		room.repeats = 0;
		room.missedThisRound = false;
		room.touch();
		if (next == null) {
			LearningBlocks.LOGGER.warn("[{}] No usable words; {}'s room has no puzzle", LearningBlocks.MOD_ID, player.getGameProfile().name());
			return;
		}
		progress.roundBegan(player);
		room.lastTarget = next.target();
		progress.useToday(player, next.target(), lessonKind(next, sceneWord));
		if (room.team) for (ServerPlayer other : room.present()) if (other != player) Progress.of(other).useToday(other, next.target(), "team");

		layOut(player, next, sceneWord, freshStage);
	}

	/** Rounds without taking part before a reader is asked, gently and only them, to join in. */
	static final int IDLE_ROUNDS = 2;

	/** Reading together, a reader who has done nothing for a while hears, alone, that they can help. */
	private void nudgeIdle() {
		// A nudge is for the round about to start, never one carried over from rounds ago.
		room.nudgeOwed.clear();
		if (!room.shared() || room.puzzle == null && room.subjectRound == null && room.bob == null) return;
		for (ServerPlayer reader : room.present()) {
			UUID id = reader.getUUID();
			int idle = room.tookPart.contains(id) ? 0 : room.idleRounds.getOrDefault(id, 0) + 1;
			room.idleRounds.put(id, idle);
			if (idle >= IDLE_ROUNDS && idle % IDLE_ROUNDS == 0) room.nudgeOwed.put(id, idle / IDLE_ROUNDS % 2 == 1);
		}
	}

	/**
	 * A nudge owed, said once the room is quiet (the round asked, nothing playing): to that reader
	 * alone, with no name, "Can you help?" the first time and "Your turn to help!" the next.
	 */
	void nudge(ServerPlayer reader, boolean first) {
		Narrator.note(reader, "nudge:idle");
		Narrator.play(reader, SubjectRound.line(new Narration().alone(), first ? "phrase/can_you_help" : "phrase/your_turn_to_help",
			first ? "Can you help?" : "Your turn to help!"));
	}

	/**
	 * Whose subject this round is: one asked for, Reading for anything of Reading's asked for, or the
	 * mix's pick from this reader's own subjects. True if it was another subject's, and it started.
	 */
	private boolean otherSubject(ServerPlayer player) {
		justfatlard.learning_blocks.subject.Subject subject = req.subject();
		if (subject == null) subject = req.readingAsked() ? justfatlard.learning_blocks.subject.Subjects.READING
			: justfatlard.learning_blocks.subject.Subjects.pick(player, room.lastSubject.get(player.getUUID()),
				room.subjectRun.getOrDefault(player.getUUID(), 0), level.getRandom());
		// A subject round stands on a stand-in Reading puzzle, which needs a word to stand on: none loaded, none made.
		if (subject == justfatlard.learning_blocks.subject.Subjects.READING || Words.all().isEmpty()) return false;
		SubjectRound next = subject.round(player, req.subjectKind(), req.subjectTarget(), level.getRandom());
		if (next == null) return false;
		clearRound();
		room.round++;
		noteSubject(player, subject);
		startSubject(player, next);
		return true;
	}

	/** A word round on the board and the floor, its picture or its situation, and asked. */
	private void layOut(ServerPlayer player, Puzzle next, @Nullable Word sceneWord, boolean freshStage) {
		List<Letter> letters = room.tricky ? lettersOf(Words.shown(next.target())) : next.target().letters();
		if (letters == null) return;
		int n = letters.size();
		// A big word's chunks stand apart, a column between them: cat  fish.
		List<int[]> chunks = room.tricky ? List.of(new int[] {0, n}) : Words.chunkRanges(next.target());
		int columns = n + chunks.size() - 1;
		for (int i = 0; i < n; i++) {
			int before = 0;
			for (int c = 1; c < chunks.size(); c++) if (i >= chunks.get(c)[0]) before++;
			BlockPos pos = layout.rowSpot(i + before, columns);
			room.row.add(pos);
			// The gap is a real space, standing out from the board with an underscore painted at
			// its back: a tile placed against the underscore lands in it.
			if (!room.readIt && next.gaps().contains(i)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
				if (room.gapMarker == null) room.gapMarker = pos.north();
				room.gapMarkers.put(pos.immutable(), pos.north());
				// Reading together, the underscore is in its reader's colour: the turn reader's, or in a
				// team-up, the first gap the lower reader's and the last the other's.
				java.util.UUID whose = !room.shared() ? null : room.team ? teamOwner(i == next.gaps().getFirst()) : room.turn;
				if (whose != null) room.gapOwners.put(pos.immutable(), whose);
				level.setBlock(pos.north(), LearningBlocks.BOARD_LETTER.showing(whose == null ? Letter.BLANK
					: room.place(whose) < Letter.READER_BLANKS.size() ? Letter.READER_BLANKS.get(room.place(whose)) : Letter.BLANK), FLAGS);
			} else {
				level.setBlock(pos, LearningBlocks.BOARD_LETTER.showing(letters.get(i)), FLAGS);
			}
		}

		int m = next.shelf().size();
		for (int i = 0; i < m; i++) {
			BlockPos pos = layout.shelfSpot(i, m);
			room.homes.put(next.shelf().get(i), pos);
			level.setBlock(pos, LearningBlocks.LETTER.showing(next.shelf().get(i)), FLAGS);
		}

		if (room.fixLetter != null && next.gap() < room.row.size()) {
			// The wrong letter in the gap, as if put there; its place on the floor empty, for it to go home to.
			BlockPos gapPos = room.row.get(next.gap());
			level.setBlock(gapPos, LearningBlocks.LETTER.showing(room.fixLetter), FLAGS);
			BlockPos home = room.homes.get(room.fixLetter);
			if (home != null) level.setBlock(home, Blocks.AIR.defaultBlockState(), FLAGS);
		}

		if (sceneWord != null) {
			// No picture and no word said: the scene is the question.
			room.scene = new SceneRun(room.dispenser, level, sceneWord, sceneWord.usableScene().orElseThrow(), layout.sceneSpot());
			room.scene.start(player);
			room.lastScene = sceneWord;
			room.sinceScene = 0;
			Narrator.note(player, "scene:" + sceneWord.text());
		} else if (room.readIt) {
			room.sinceScene++;
			placeChoices(next.family());
		} else if (room.phraseAdjective != null) {
			room.sinceScene++;
			placeAdjective(room.phraseAdjective);
			room.picture.show(next.target(), room.row, !room.row.isEmpty() && layout.pictureAbove(room.row.getFirst()));
		} else if (room.letterTarget == null) {
			room.sinceScene++;
			room.picture.show(next.target(), room.row, !room.row.isEmpty() && layout.pictureAbove(room.row.getFirst()));
		}
		if (room.promptHeld) room.heldSince = level.getGameTime();
		else if (freshStage) {
			Narration welcome = new Narration()
				.then("next_level", this::flourish, 2)
				.say("phrase/next_level");
			for (Narration.Step step : prompt().steps()) welcome.add(step);
			Narrator.play(player, welcome);
		} else Narrator.play(player, prompt());
	}

	/**
	 * The wrong letter for a word to fix: one on the floor that makes another word of the family in
	 * the gap (cot for cat), so the board says something real that is not the picture; the one asked
	 * for, if it does. Null if the family has no such word.
	 */
	private @Nullable Letter fixLetterFor(Puzzle puzzle, @Nullable Letter wanted) {
		List<Letter> options = new ArrayList<>();
		for (Word other : puzzle.family()) {
			if (AnswerCommit.sameWord(other, puzzle.target()) || other.tiles().size() != puzzle.target().tiles().size()) continue;
			Letter in = Letter.of(other.tiles().get(puzzle.gap()));
			if (in != null && puzzle.shelf().contains(in) && !options.contains(in)) options.add(in);
		}
		if (wanted != null) return options.contains(wanted) ? wanted : null;
		return options.isEmpty() ? null : options.get(level.getRandom().nextInt(options.size()));
	}

	/** This reader's round is this subject's: the mix's count, the hello if it changed, the board's frame. */
	private void noteSubject(ServerPlayer player, justfatlard.learning_blocks.subject.Subject subject) {
		String before = room.lastSubject.put(player.getUUID(), subject.id());
		room.subjectRun.put(player.getUUID(), subject.id().equals(before) ? room.subjectRun.getOrDefault(player.getUUID(), 0) + 1 : 1);
		if (before != null && !before.equals(subject.id())) {
			room.pendingHello = subject.hello();
			Narrator.note(player, "subject_hello:" + subject.id());
		}
		room.currentSubject = subject.id();
		// A reader with one subject keeps the plain wooden frame, as the room always had.
		paintFrame(justfatlard.learning_blocks.subject.Subjects.on(player).size() > 1 ? subject.colour() : null);
	}

	/**
	 * The board's frame, the top edge across it, in the subject's colour (reading blue, numbers
	 * green, logic purple), or the plain wood for null.
	 */
	private void paintFrame(net.minecraft.world.item.@Nullable DyeColor colour) {
		if (room.frame == colour) return;
		room.frame = colour;
		net.minecraft.world.level.block.state.BlockState trim = colour == null ? Blocks.SPRUCE_PLANKS.defaultBlockState()
			: Blocks.CONCRETE.pick(colour).defaultBlockState();
		for (int x = -HALF + 1; x < HALF; x++) level.setBlock(layout.origin().offset(x, 5, -5), trim, FLAGS);
	}

	private void startSubject(ServerPlayer player, SubjectRound next) {
		room.subjectRound = next;
		room.puzzle = next.placeholder();
		room.letterTarget = null;
		room.casePrompt = null;
		room.readIt = false;
		room.team = false;
		room.tricky = false;
		room.busy = false;
		room.repeats = 0;
		room.missedThisRound = false;
		room.touch();
		Progress.of(player).roundBegan(player);
		Narrator.note(player, "subject:" + next.subject().id() + ":" + next.kind() + ":" + next.item());
		next.start(player);
		if (room.promptHeld) room.heldSince = level.getGameTime();
		else Narrator.play(player, prompt());
	}

	/** Bob's turn, asked for or due: one round in tell_bob_every, from his stage. */
	private BobRound.@Nullable Mode bobDue(@Nullable Word target, boolean freshStage, int stage) {
		BobRound.Mode asked = req.bob();
		if (asked != null) return asked;
		boolean othersAsked = req.tricky() != null || req.plural() != null || req.puzzle() != null || req.phraseAdjective() != null
			|| req.caseLetter() != null || req.letter() != null || req.readIt() != null || req.team() || req.fixLetter() != null;
		if (target != null || freshStage || othersAsked || !Stages.reached(stage, Stages.BOB_FROM) || !PrizeConfig.get().extrasOn()) return null;
		int every = PrizeConfig.tellBobEvery();
		if (every <= 0 || room.sinceBob < every - 1) return null;
		// In turn: tell Bob, read the little word, tell Bob, what's wrong.
		return switch (room.bobRounds % 4) {
			case 1 -> BobRound.Mode.PREP;
			case 3 -> PrizeConfig.whatsWrong() ? BobRound.Mode.FIX : BobRound.Mode.GOAL;
			default -> BobRound.Mode.GOAL;
		};
	}

	/** A big-and-little round: which letter, and whether the capital is the one asked for. */
	private record CaseAsk(Letter base, boolean up) {}

	/**
	 * Big and little letters, asked for or due: in the letters level once a letter's sound is
	 * mastered (most rounds, once the group's sounds all are); after it, as occasional review.
	 */
	private @Nullable CaseAsk caseDue(Progress progress, boolean open, boolean inLettersLevel, boolean reviewMayCome) {
		Letter asked = req.caseLetter();
		if (asked != null) return new CaseAsk(asked, req.caseUp());
		if (!open) return null;
		if (inLettersLevel) {
			Letter due = Letters.pickCase(progress, level.getRandom(), room.lastLetter, false);
			boolean asking = due != null && (!Letters.soundsLeft(progress) || level.getRandom().nextInt(3) == 0);
			boolean up = level.getRandom().nextBoolean();
			return asking ? new CaseAsk(due, up) : null;
		}
		if (reviewMayCome && PrizeConfig.get().extrasOn() && level.getRandom().nextInt(100) < CASE_REVIEW) {
			Letter due = Letters.pickCase(progress, level.getRandom(), null, true);
			return due == null ? null : new CaseAsk(due, level.getRandom().nextBoolean());
		}
		return null;
	}

	/** One round in review_share is a word due for another look: missed lately, or never first try. */
	private @Nullable Word reviewDue(ServerPlayer player, Progress progress) {
		if (level.getRandom().nextInt(100) >= PrizeConfig.get().reviewPercent()) return null;
		Word due = dueWord(progress);
		if (due != null) Narrator.note(player, "review:" + due.text());
		return due;
	}

	/** A tricky word, a plural or a two-word phrase, asked for or due, each from its own stage. */
	private @Nullable Puzzle specialDue(Progress progress, int stage, boolean free) {
		Puzzle special = null;
		if (req.tricky() != null) special = trickyPuzzle(req.tricky());
		else if (req.plural() != null) special = pluralPuzzle(req.plural());
		else if (req.phraseAdjective() != null) special = phrasePuzzle(req.phraseAdjective(), req.phraseNoun());
		else if (free && PrizeConfig.get().extrasOn()) {
			if (Stages.reached(stage, Stages.TRICKY_FROM) && room.sinceTricky >= TRICKY_EVERY - 1) special = pickTricky(progress);
			if (special == null && Stages.reached(stage, Stages.PLURAL_FROM) && room.sincePlural >= PLURAL_EVERY - 1) special = pickPlural(progress);
			if (special == null && Stages.reached(stage, Stages.PHRASE_FROM) && room.sincePhrase >= PHRASE_EVERY - 1) {
				Word[] pair = AdjectivePhrases.pick(progress, level.getRandom());
				if (pair != null) special = phrasePuzzle(pair[0], pair[1]);
			}
		}
		return special;
	}

	/** Reading together: a team-up, asked for or due, both building one word, a gap each. */
	private @Nullable Puzzle teamUpDue(ServerPlayer player) {
		int every = PrizeConfig.teamUpEvery();
		boolean due = req.team() || every > 0 && room.sinceTeam >= every - 1;
		Puzzle team = due ? teamPuzzle() : null;
		if (team == null) {
			room.sinceTeam++;
			return null;
		}
		room.team = true;
		room.sinceTeam = 0;
		Narrator.note(player, "team_up:" + team.target().text());
		return team;
	}

	/**
	 * A word to fix, asked for or due: the gap already holds a wrong letter that makes a real word
	 * other than the picture (cot under a cat), and the child swaps it for the right one.
	 */
	private @Nullable Letter fixDue(ServerPlayer player, @Nullable Puzzle plainWord, boolean mayCome) {
		Letter asked = req.fixLetter();
		if (plainWord == null) return null;
		int every = PrizeConfig.whatsWrongEvery();
		boolean due = asked != null || mayCome && PrizeConfig.get().extrasOn() && every > 0 && room.sinceFix >= every - 1;
		Letter wrong = due ? fixLetterFor(plainWord, asked) : null;
		if (wrong == null) {
			room.sinceFix++;
			return null;
		}
		room.sinceFix = 0;
		List<String> said = new ArrayList<>(plainWord.target().tiles());
		said.set(plainWord.gap(), wrong.grapheme());
		Narrator.note(player, "fix_it:" + plainWord.target().text() + ":" + String.join("", said));
		return wrong;
	}

	private void startBob(ServerPlayer player, Progress progress, BobRound bob) {
		room.bob = bob;
		room.puzzle = bob.placeholder();
		room.letterTarget = null;
		room.casePrompt = null;
		room.readIt = false;
		room.busy = false;
		room.repeats = 0;
		room.missedThisRound = false;
		room.touch();
		progress.roundBegan(player);
		if (bob.mode != BobRound.Mode.SILLY) {
			room.sinceBob = 0;
			room.bobRounds++;
		}
		if (bob.goal != null) {
			room.lastGoal = bob.goal;
			progress.useSentenceToday(player, bob.goal.key(), bob.mode.name().toLowerCase(java.util.Locale.ROOT));
		}
		Narrator.note(player, "bob:" + bob.mode.name().toLowerCase(java.util.Locale.ROOT)
			+ (bob.goal != null ? ":" + bob.goal.id() : !bob.prepWords.isEmpty() ? ":" + String.join("-", bob.prepWords) : bob.offered.isEmpty() ? "" : ":pick"));
		bob.start(player);
		if (room.promptHeld) room.heldSince = level.getGameTime();
		else Narrator.play(player, prompt());
	}


	/**
	 * A team-up for the readers here: a word from the lower reader's band (so both can read it),
	 * its first and last tiles both gaps, the first sound (the easier) the lower reader's. The floor
	 * has both families' tiles. Null if there is no such word just now.
	 */
	private @Nullable Puzzle teamPuzzle() {
		List<ServerPlayer> here = room.present().stream().filter(p -> !room.leavingMembers.containsKey(p.getUUID())).toList();
		if (here.size() < 2) return null;
		// Pairs in turn, so every reader teams up with every other.
		List<List<ServerPlayer>> pairs = new ArrayList<>();
		for (int i = 0; i < here.size(); i++) {
			for (int j = i + 1; j < here.size(); j++) {
				if (Stages.current(Progress.of(here.get(i))) != 0 && Stages.current(Progress.of(here.get(j))) != 0) pairs.add(List.of(here.get(i), here.get(j)));
			}
		}
		if (pairs.isEmpty()) return null;
		List<ServerPlayer> readers = new ArrayList<>(pairs.get(room.teamPairs++ % pairs.size()));
		readers.sort(java.util.Comparator.comparing(p -> {
			Stages.Band band = Stages.currentBand(Progress.of(p));
			return band == null ? new Stages.BandCode(Integer.MAX_VALUE / 100, 0) : band.code();
		}));
		ServerPlayer lower = readers.getFirst();
		Progress progress = Progress.of(lower);
		Stages.Band band = Stages.currentBand(progress);
		List<Word> from = new ArrayList<>();
		for (Word word : Stages.usable()) {
			boolean ok = band != null ? band.contains(word) : true;
			if (ok && word.tiles().size() >= 3 && !progress.wordUsedToday(word)) from.add(word);
		}
		java.util.Collections.shuffle(from, new java.util.Random(level.getRandom().nextLong()));
		from.sort(java.util.Comparator.comparingInt(progress::clean));
		if (req.teamWord() != null) {
			from.clear();
			from.add(req.teamWord());
		}
		for (Word word : from) {
			Puzzle team = teamPuzzle(word);
			if (team == null) continue;
			room.teamReaders.clear();
			room.teamReaders.add(lower.getUUID());
			room.teamReaders.add(readers.get(1).getUUID());
			return team;
		}
		return null;
	}

	/** The team-up round for this word, if it has both a first-sound and a last-sound family. */
	@Nullable Puzzle teamPuzzle(Word word) {
		Puzzle first = Puzzle.forTarget(word, Words.all(), level.getRandom(), Puzzle.Kind.FIRST);
		Puzzle last = Puzzle.forTarget(word, Words.all(), level.getRandom(), Puzzle.Kind.LAST);
		if (first == null || last == null || first.shelf().size() < 2 || last.shelf().size() < 2) return null;
		// The two answers first, then each family's other tiles in turn, no more than the floor holds.
		List<Letter> shelf = new ArrayList<>();
		for (int i : List.of(0, word.tiles().size() - 1)) {
			Letter own = Letter.of(word.tiles().get(i));
			if (own != null && !shelf.contains(own)) shelf.add(own);
		}
		for (int k = 0; k < 3 && shelf.size() < Puzzle.MAX_WHOLE_TILES; k++) {
			for (Puzzle family : List.of(first, last)) {
				if (k < family.shelf().size() && shelf.size() < Puzzle.MAX_WHOLE_TILES && !shelf.contains(family.shelf().get(k))) shelf.add(family.shelf().get(k));
			}
		}
		java.util.Collections.shuffle(shelf, new java.util.Random(level.getRandom().nextLong()));
		List<Word> family = new ArrayList<>(first.family());
		for (Word other : last.family()) if (!family.contains(other)) family.add(other);
		return new Puzzle(word, List.copyOf(family), List.copyOf(shelf), 0, Puzzle.Kind.TEAM);
	}

	/** Whose a team-up gap is: the first the lower reader's, the last the other's. */
	private java.util.@Nullable UUID teamOwner(boolean first) {
		if (room.teamReaders.size() < 2) return room.turn;
		return room.teamReaders.get(first ? 0 : 1);
	}


	/** What a round was, for "not the same lesson twice in a day": a word and this. */
	private String lessonKind(Puzzle puzzle, @Nullable Word sceneWord) {
		if (room.tricky) return "tricky";
		if (room.fixLetter != null) return "fix";
		if (room.readIt) return "read_it";
		if (sceneWord != null) return "scene";
		if (room.phraseAdjective != null) return "phrase";
		if (room.casePrompt != null) return "case";
		if (room.letterTarget != null) return "letter";
		return puzzle.kind().name().toLowerCase(java.util.Locale.ROOT);
	}

	/** The usual round, or what fills in for it; and what kind of round that makes this. */
	record Fill(@Nullable Puzzle puzzle, boolean readIt, @Nullable Word scene, @Nullable Letter caseBase, boolean caseUp) {
		static Fill of(@Nullable Puzzle puzzle) {
			return new Fill(puzzle, false, null, null, false);
		}
	}

	/**
	 * A word of the band not yet asked today. When every one has been, in this order: (1) one of
	 * them again in a kind of round it hasn't had today (another sound swapped, the whole word, read
	 * it, its situation); (2) something due for another look from earlier (a word, then a big and
	 * little pair); (3) a situation or a phrase of words already read; (4) only then a word again,
	 * and never the last one.
	 */
	Fill freshPick(ServerPlayer player, Progress progress, int stage, Stages.@Nullable Band band) {
		java.util.function.Predicate<Word> inBand = word -> band != null ? band.contains(word) : stage < 0 || word.stage() == stage;
		Puzzle fresh = Puzzle.pick(Words.all(), level.getRandom(), progress.masteredIds(), stage, kinds(progress), known(progress),
			progress::clean, room.lastTarget, word -> inBand.test(word) && !progress.wordUsedToday(word));
		if (fresh != null) return Fill.of(fresh);

		// (1) The band's words again, another kind of round: fewest clean first, not the last one.
		List<Word> again = new ArrayList<>();
		for (Word word : Stages.usable()) if (inBand.test(word) && (room.lastTarget == null || !AnswerCommit.sameWord(word, room.lastTarget))) again.add(word);
		java.util.Collections.shuffle(again, new java.util.Random(level.getRandom().nextLong()));
		again.sort(java.util.Comparator.comparingInt(progress::clean));
		List<Puzzle.Kind> kinds = new ArrayList<>(kinds(progress));
		kinds.add(Puzzle.Kind.WHOLE);
		for (Word word : again) {
			java.util.Collections.shuffle(kinds, new java.util.Random(level.getRandom().nextLong()));
			for (Puzzle.Kind kind : kinds) {
				if (progress.lessonUsedToday(word, kind.name().toLowerCase(java.util.Locale.ROOT))) continue;
				if (kind == Puzzle.Kind.WHOLE && !PrizeConfig.get().wholeWord) continue;
				Puzzle puzzle = Puzzle.forTarget(word, Words.all(), level.getRandom(), kind);
				if (puzzle != null && (kind == Puzzle.Kind.WHOLE || puzzle.shelf().size() >= 2)) {
					Narrator.note(player, "fill:kind:" + word.text() + ":" + kind.name().toLowerCase(java.util.Locale.ROOT));
					return Fill.of(puzzle);
				}
			}
			boolean read = progress.words().contains(word.id().toString());
			if (read && PrizeConfig.get().readItOn() && stage != 1 && !progress.lessonUsedToday(word, "read_it")) {
				Puzzle puzzle = readIt(progress, word);
				if (puzzle != null) {
					Narrator.note(player, "fill:read_it:" + word.text());
					return new Fill(puzzle, true, null, null, false);
				}
			}
			if (read && PrizeConfig.get().scenes && word.usableScene().isPresent() && !progress.lessonUsedToday(word, "scene")) {
				Puzzle puzzle = Puzzle.forTarget(word, Words.all(), level.getRandom());
				if (puzzle != null) {
					Narrator.note(player, "fill:scene:" + word.text());
					return new Fill(puzzle, false, word, null, false);
				}
			}
		}

		// (2) Something from before, due for another look: a word, then a big and little pair.
		Word due = dueWord(progress);
		if (due != null) {
			Puzzle puzzle = Puzzle.forTarget(due, Words.all(), level.getRandom());
			if (puzzle != null) {
				Narrator.note(player, "fill:review:" + due.text());
				return Fill.of(puzzle);
			}
		}
		Letter pair = Letters.pickCase(progress, level.getRandom(), null, true);
		if (pair != null) {
			boolean up = level.getRandom().nextBoolean();
			Narrator.note(player, "fill:case:" + pair.grapheme());
			return new Fill(casePuzzle(progress, pair, up), false, null, pair, up);
		}

		// (3) A situation or a phrase of words already read.
		Word scene = anyScene(progress);
		if (scene != null) {
			Puzzle puzzle = Puzzle.forTarget(scene, Words.all(), level.getRandom());
			if (puzzle != null) {
				Narrator.note(player, "fill:scene:" + scene.text());
				return new Fill(puzzle, false, scene, null, false);
			}
		}
		Word[] phrase = AdjectivePhrases.pick(progress, level.getRandom());
		if (phrase != null && !progress.wordUsedToday(phrase[1])) {
			Puzzle puzzle = phrasePuzzle(phrase[0], phrase[1]);
			if (puzzle != null) {
				Narrator.note(player, "fill:phrase:" + phrase[1].text());
				return Fill.of(puzzle);
			}
		}

		// (4) A word again, never the one just done.
		Narrator.note(player, "fill:repeat");
		return Fill.of(Puzzle.pick(Words.all(), level.getRandom(), progress.masteredIds(), stage, kinds(progress), known(progress),
			progress::clean, room.lastTarget, inBand));
	}


	/** One round in this many is a tricky word, once one is open; a plural; a two-word phrase. */
	static final int TRICKY_EVERY = 6;
	static final int PLURAL_EVERY = 8;
	static final int PHRASE_EVERY = 7;

	private static @Nullable List<Letter> lettersOf(List<String> tiles) {
		List<Letter> letters = new ArrayList<>();
		for (String tile : tiles) {
			Letter letter = Letter.of(tile);
			if (letter == null) return null;
			letters.add(letter);
		}
		return letters;
	}

	/**
	 * A tricky word for this child: one open at their stage (a tricky word's stage is when it opens)
	 * and not yet mastered, the fewest clean first; failing that, one due for review.
	 */
	private @Nullable Puzzle pickTricky(Progress progress) {
		List<Word> open = new ArrayList<>();
		for (Word word : Words.tricky()) if (room.stage < 0 || word.stage() <= room.stage) open.add(word);
		if (open.isEmpty()) return null;
		open.removeIf(progress::wordUsedToday);
		if (open.isEmpty()) return null;
		List<Word> learning = open.stream().filter(word -> !progress.mastered(word)).toList();
		List<Word> from = new ArrayList<>(learning.isEmpty()
			? open.stream().filter(word -> progress.memory().due(justfatlard.learning_blocks.reward.Memory.wordKey(word.id().toString()))).toList()
			: learning);
		if (from.isEmpty()) return null;
		if (from.size() > 1 && room.lastTarget != null) from.removeIf(word -> AnswerCommit.sameWord(word, room.lastTarget));
		int fewest = from.stream().mapToInt(progress::clean).min().orElse(0);
		from.removeIf(word -> progress.clean(word) > fewest);
		return trickyPuzzle(from.get(level.getRandom().nextInt(from.size())));
	}

	/** A tricky word's round: its tiles on the board, and its card and two others' on the floor. */
	private Puzzle trickyPuzzle(Word target) {
		List<Word> others = new ArrayList<>(Words.tricky());
		others.removeIf(word -> AnswerCommit.sameWord(word, target));
		// Cards that look least alike first would be too easy: a mix, the same length first where there are any.
		java.util.Collections.shuffle(others, new java.util.Random(level.getRandom().nextLong()));
		others.sort(java.util.Comparator.comparingInt(word -> Math.abs(word.text().length() - target.text().length())));
		List<Word> cards = new ArrayList<>(others.subList(0, Math.min(2, others.size())));
		cards.add(target);
		java.util.Collections.shuffle(cards, new java.util.Random(level.getRandom().nextLong()));
		return new Puzzle(target, List.copyOf(cards), List.of(), 0, Puzzle.Kind.FIRST);
	}

	/** A plural of a word this child has read, with the plurals of its family: cats, bats. */
	private @Nullable Puzzle pickPlural(Progress progress) {
		List<Word> open = new ArrayList<>();
		for (Word plural : Words.plurals()) {
			Word base = baseOf(plural);
			if (base != null && progress.words().contains(base.id().toString()) && plural.outcome().usable()) open.add(plural);
		}
		if (open.isEmpty()) return null;
		return pluralPuzzle(open.get(level.getRandom().nextInt(open.size())));
	}

	static @Nullable Word baseOf(Word plural) {
		List<String> tiles = plural.tiles().subList(0, plural.tiles().size() - 1);
		for (Word word : Words.all()) if (word.tiles().equals(tiles)) return word;
		return null;
	}

	/**
	 * A plural round: the word with its first tile and the s on the end both gaps; on the floor the
	 * first tiles of its family's plurals (cats, bats) and the s.
	 */
	private Puzzle pluralPuzzle(Word target) {
		String rime = String.join("", target.tiles().subList(1, target.tiles().size()));
		List<Word> family = new ArrayList<>();
		for (Word plural : Words.plurals()) {
			if (plural.tiles().size() == target.tiles().size() && String.join("", plural.tiles().subList(1, plural.tiles().size())).equals(rime)
				&& plural.outcome().usable()) family.add(plural);
		}
		if (!family.contains(target)) family.add(target);
		List<Letter> shelf = new ArrayList<>();
		for (Word plural : family) {
			Letter onset = Letter.of(plural.tiles().getFirst());
			if (onset != null && !shelf.contains(onset)) shelf.add(onset);
		}
		shelf.add(Letter.S);
		java.util.Collections.shuffle(shelf, new java.util.Random(level.getRandom().nextLong()));
		return new Puzzle(target, List.copyOf(family), List.copyOf(shelf), 0, Puzzle.Kind.PLURAL);
	}

	/** A phrase round: the adjective shown whole over the board's word, the noun a first-sound round of its family. */
	private @Nullable Puzzle phrasePuzzle(Word adjective, Word noun) {
		if (!AdjectivePhrases.fits(adjective, noun)) return null;
		Puzzle puzzle = Puzzle.forTarget(noun, Words.all(), level.getRandom(), Puzzle.Kind.FIRST);
		if (puzzle == null) return null;
		room.phraseAdjective = adjective;
		return puzzle;
	}

	private void placeAdjective(Word adjective) {
		List<Letter> letters = adjective.letters();
		if (letters == null) return;
		for (int i = 0; i < letters.size(); i++) {
			BlockPos pos = layout.rowSpot(i, letters.size()).above();
			level.setBlock(pos, LearningBlocks.BOARD_LETTER.showing(letters.get(i)), FLAGS);
			room.adjectiveRow.add(pos.immutable());
		}
	}


	/**
	 * A read-it round, if one is due: read-it on, {@code read_it_every} rounds since the
	 * last; a read word with a clear picture and at least one look-alike to pick instead, pictured differently.
	 */
	private @Nullable Puzzle pickReadIt(Progress progress) {
		PrizeConfig config = PrizeConfig.get();
		if (!config.readItOn() || !Stages.reached(room.stage, Stages.READ_IT_FROM) || room.sinceReadIt < config.readItEvery - 1) return null;
		List<Word> read = new ArrayList<>();
		for (Word word : Stages.usable()) {
			if (word.clearPicture() && progress.words().contains(word.id().toString()) && !progress.wordUsedToday(word)) read.add(word);
		}
		java.util.Collections.shuffle(read, new java.util.Random(level.getRandom().nextLong()));
		for (Word word : read) {
			Puzzle puzzle = readIt(progress, word);
			if (puzzle != null) return puzzle;
		}
		return null;
	}

	/**
	 * A read-it round for this word: it and two look-alikes, all words whose picture is clear (a
	 * thing a child names at once as the word). Its families' clear words first; short of two, other
	 * clear words of its stage, those sharing its first or last tile before the rest.
	 */
	private @Nullable Puzzle readIt(Progress progress, Word target) {
		net.minecraft.world.item.ItemStack own = target.picture(level.registryAccess());
		if (!target.clearPicture() || own.isEmpty() || target.letters() == null) return null;
		List<Word> others = new ArrayList<>();
		java.util.Set<net.minecraft.world.item.Item> pictured = new java.util.HashSet<>();
		pictured.add(own.getItem());
		java.util.function.Predicate<Word> take = word -> {
			if (others.size() >= 2 || !word.clearPicture() || AnswerCommit.sameWord(word, target) || others.contains(word)) return false;
			net.minecraft.world.item.ItemStack picture = word.picture(level.registryAccess());
			if (picture.isEmpty() || !pictured.add(picture.getItem())) return false;
			others.add(word);
			return true;
		};
		for (Puzzle.Kind kind : List.of(Puzzle.Kind.FIRST, Puzzle.Kind.MIDDLE, Puzzle.Kind.LAST)) {
			Puzzle family = Puzzle.forTarget(target, Words.all(), level.getRandom(), kind);
			if (family == null) continue;
			List<Word> words = new ArrayList<>(family.family());
			java.util.Collections.shuffle(words, new java.util.Random(level.getRandom().nextLong()));
			for (Word word : words) take.test(word);
		}
		if (others.size() < 2) {
			List<Word> near = new ArrayList<>();
			List<Word> rest = new ArrayList<>();
			for (Word word : Stages.at(target.stage())) {
				boolean alike = word.onset().equals(target.onset()) || word.tiles().getLast().equals(target.tiles().getLast());
				(alike ? near : rest).add(word);
			}
			java.util.Collections.shuffle(near, new java.util.Random(level.getRandom().nextLong()));
			java.util.Collections.shuffle(rest, new java.util.Random(level.getRandom().nextLong()));
			for (Word word : near) take.test(word);
			for (Word word : rest) take.test(word);
		}
		if (others.isEmpty()) return null;
		List<Word> choices = new ArrayList<>(others);
		choices.add(target);
		java.util.Collections.shuffle(choices, new java.util.Random(level.getRandom().nextLong()));
		return new Puzzle(target, List.copyOf(choices), List.of(), 0, Puzzle.Kind.FIRST);
	}

	private void placeChoices(List<Word> choices) {
		for (int i = 0; i < choices.size(); i++) {
			Word word = choices.get(i);
			BlockPos spot = layout.shelfSpot(i, choices.size());
			net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(spot).add(0, 0.9, 0);
			net.minecraft.world.entity.Display display = room.tricky ? card(word, at)
				: BoardPicture.of(level, word.picture(level.registryAccess()), at);
			if (display == null) continue;
			level.addFreshEntity(display);
			level.setBlock(spot, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), FLAGS);
			room.choiceStands.add(spot.immutable());
			net.minecraft.world.entity.Interaction hit = net.minecraft.world.entity.EntityTypes.INTERACTION.create(level,
				net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (hit == null) {
				display.discard();
				continue;
			}
			hit.snapTo(at.x, at.y - 0.9, at.z, 0, 0);
			hit.setWidth(1.0F);
			hit.setHeight(1.9F);
			hit.setResponse(true);
			level.addFreshEntity(hit);
			room.choices.put(hit.getUUID(), word);
			room.choiceDisplays.put(word.id().toString(), display.getUUID());
		}
	}

	private net.minecraft.world.entity.Display.@Nullable TextDisplay card(Word word, net.minecraft.world.phys.Vec3 at) {
		net.minecraft.world.entity.Display.TextDisplay text = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(level,
			net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (text == null) return null;
		text.snapTo(at.x, at.y - 0.3, at.z, 0, 0);
		text.setBillboardConstraints(net.minecraft.world.entity.Display.BillboardConstraints.CENTER);
		String shown = String.join("", Words.shown(word).stream().map(tile -> {
			Letter letter = Letter.of(tile);
			return letter == null ? tile : letter.shown();
		}).toList());
		text.setText(net.minecraft.network.chat.Component.literal(shown).withStyle(style -> style.withColor(0x1E2A5A).withBold(true)));
		// A card, the colour of the tiles, so the word reads as one to pick up.
		text.setBackgroundColor(0xFFF2E8C9);
		text.setTransformation(new com.mojang.math.Transformation(null, null, new org.joml.Vector3f(2.4F, 2.4F, 2.4F), null));
		return text;
	}

	void wiggle(ServerPlayer player, Word picked) {
		java.util.UUID id = room.choiceDisplays.get(picked.id().toString());
		if (id == null || !(level.getEntity(id) instanceof net.minecraft.world.entity.Display display)) return;
		float size = display instanceof net.minecraft.world.entity.Display.TextDisplay ? 2.4F : 1.8F;
		float turn = display instanceof net.minecraft.world.entity.Display.TextDisplay ? 0.0F : (float) Math.PI;
		for (int i = 0; i < 4; i++) {
			float angle = i % 2 == 0 ? 0.35F : -0.35F;
			room.later(player, i * 3, p -> {
				display.setTransformationInterpolationDelay(0);
				display.setTransformationInterpolationDuration(3);
				display.setTransformation(new com.mojang.math.Transformation(null,
					new org.joml.Quaternionf().rotateY(turn).rotateZ(angle), new org.joml.Vector3f(size, size, size), null));
			});
		}
		room.later(player, 12, p -> {
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(3);
			display.setTransformation(new com.mojang.math.Transformation(null,
				new org.joml.Quaternionf().rotateY(turn), new org.joml.Vector3f(size, size, size), null));
		});
		net.minecraft.world.phys.Vec3 at = display.position();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, at.x, at.y + 0.8, at.z, 3, 0.3, 0.1, 0.3, 1.0);
	}

	void clearChoicesOnClose() {
		clearChoices();
	}

	private void clearChoices() {
		for (java.util.UUID id : room.choices.keySet()) {
			net.minecraft.world.entity.Entity entity = level.getEntity(id);
			if (entity != null) entity.discard();
		}
		for (java.util.UUID id : room.choiceDisplays.values()) {
			net.minecraft.world.entity.Entity entity = level.getEntity(id);
			if (entity != null) entity.discard();
		}
		for (BlockPos stand : room.choiceStands) level.setBlock(stand, Blocks.AIR.defaultBlockState(), FLAGS);
		room.choices.clear();
		room.choiceDisplays.clear();
		room.choiceStands.clear();
		room.readIt = false;
	}

	/** A read word due for another look, the lowest box first: none if nothing is due. */
	private @Nullable Word dueWord(Progress progress) {
		justfatlard.learning_blocks.reward.Memory memory = progress.memory();
		Word best = null;
		int bestBox = Integer.MAX_VALUE;
		for (Word word : Stages.usable()) {
			String key = justfatlard.learning_blocks.reward.Memory.wordKey(word.id().toString());
			if (!progress.words().contains(word.id().toString()) || !memory.due(key) || progress.wordUsedToday(word)) continue;
			if (Puzzle.forTarget(word, Words.all(), level.getRandom()) == null) continue;
			int box = memory.item(key).box();
			if (box < bestBox) {
				best = word;
				bestBox = box;
			}
		}
		return best;
	}

	/**
	 * A read word with a scene, if this round is due one: {@code scene_every} rounds since the last,
	 * and scenes on. Not the same scene twice running, when there is another.
	 */
	private @Nullable Word pickScene(Progress progress) {
		PrizeConfig config = PrizeConfig.get();
		if (!config.scenes || room.sinceScene < config.sceneEvery - 1) return null;
		return anyScene(progress);
	}

	private @Nullable Word anyScene(Progress progress) {
		if (!PrizeConfig.get().scenes) return null;
		List<Word> eligible = new ArrayList<>();
		for (Word word : Stages.usable()) {
			if (word.usableScene().isPresent() && progress.words().contains(word.id().toString()) && !progress.wordUsedToday(word)
				&& Puzzle.forTarget(word, Words.all(), level.getRandom()) != null) eligible.add(word);
		}
		if (eligible.size() > 1 && room.lastScene != null) eligible.remove(room.lastScene);
		return eligible.isEmpty() ? null : eligible.get(level.getRandom().nextInt(eligible.size()));
	}

	/** The tiles this reader knows, for a whole-word round's decoys: every single letter, and the two-letter tiles learned. */
	private java.util.Set<Letter> known(Progress progress) {
		java.util.Set<Letter> known = Puzzle.singles();
		for (Letter letter : Letter.values()) if (letter.isDigraph() && Letters.known(progress, letter)) known.add(letter);
		return known;
	}

	/** The kinds of word round open to this child now: first sound always (unless switched off); last sound once most of the stage (three in five) is read; the middle vowel and the whole word each from its own stage. */
	private java.util.Set<Puzzle.Kind> kinds(Progress progress) {
		PrizeConfig config = PrizeConfig.get();
		int stage = room.stage;
		java.util.Set<Puzzle.Kind> kinds = java.util.EnumSet.noneOf(Puzzle.Kind.class);
		if (config.firstSound) kinds.add(Puzzle.Kind.FIRST);
		if (config.lastSound && (stage < 0 || Stages.readAt(progress, stage) * 5 >= Stages.at(stage).size() * 3)) kinds.add(Puzzle.Kind.LAST);
		if (config.middleVowel && Stages.reached(stage, Stages.MIDDLE_FROM)) kinds.add(Puzzle.Kind.MIDDLE);
		if (config.wholeWord && Stages.reached(stage, Stages.WHOLE_FROM)) kinds.add(Puzzle.Kind.WHOLE);
		// Big words, one chunk to build: only big words have chunks, so only their stage has these.
		kinds.add(Puzzle.Kind.CHUNK);
		if (kinds.isEmpty()) kinds.add(Puzzle.Kind.FIRST);
		return kinds;
	}

	/**
	 * A letters round: one gap, and on the floor the letter and two or three others already met.
	 * The target is the letter as a one-tile word, so the gap, the tiles and the rules are a word
	 * round's; only what is said and what the button does differ.
	 */
	private Puzzle letterPuzzle(Progress progress, Letter letter) {
		Word keyword = Letters.keyword(letter);
		Word asWord = new Word(LearningBlocks.id("letter/" + letter.grapheme()), List.of(letter.grapheme()), List.of(java.util.Optional.empty()), 0,
			keyword != null ? keyword.outcome() : new Outcome.CreaturesCome(), java.util.Optional.empty(), java.util.Optional.empty(),
			java.util.Optional.empty(), false, true, false, false, false);
		List<Letter> shelf = new ArrayList<>(Letters.distractors(progress, letter, level.getRandom()));
		shelf.add(letter);
		java.util.Collections.shuffle(shelf, new java.util.Random(level.getRandom().nextLong()));
		return new Puzzle(asWord, List.of(asWord), List.copyOf(shelf));
	}

	/** Of a hundred word rounds, how many are a big-and-little letter match, for review. */
	static final int CASE_REVIEW = 5;

	/**
	 * A big-and-little round: the letter shown on the board, small ({@code up}: find its capital) or
	 * capital (find its small letter), a gap beside it, and on the floor the answer and two others of
	 * the same kind.
	 */
	private Puzzle casePuzzle(Progress progress, Letter base, boolean up) {
		Letter shown = up ? base : base.upper();
		Letter answer = up ? base.upper() : base;
		Word asWord = new Word(LearningBlocks.id("case/" + (up ? "big/" : "little/") + base.grapheme()), List.of(shown.grapheme(), answer.grapheme()),
			List.of(java.util.Optional.empty(), java.util.Optional.empty()), 0, new Outcome.CreaturesCome(), java.util.Optional.empty(),
			java.util.Optional.empty(), java.util.Optional.empty(), false, true, false, false, false);
		List<Letter> shelf = new ArrayList<>(Letters.caseDistractors(progress, base, up, level.getRandom()));
		shelf.add(answer);
		java.util.Collections.shuffle(shelf, new java.util.Random(level.getRandom().nextLong()));
		return new Puzzle(asWord, List.of(asWord), List.copyOf(shelf), 1, Puzzle.Kind.LAST);
	}

	CaptionLine starred(Word word) {
		float seconds = Sounds.durationTicks("word/" + word.text()) / 20.0F;
		String shown = String.join("", Words.shown(word).stream().map(tile -> {
			Letter letter = Letter.of(tile);
			return letter == null ? tile : letter.shown();
		}).toList());
		return CaptionLine.timed(new Captions.Caption(seconds, List.of(new Captions.Word("\u2605", 0.0F, 0.0F), new Captions.Word(shown, 0.0F, seconds))));
	}

	private CaptionLine soundLine(String prompt, String phoneme) {
		List<Captions.Word> words = new ArrayList<>();
		Captions.Caption asked = Captions.get(prompt);
		if (asked != null) for (Captions.Word word : asked.words()) {
			words.add(new Captions.Word(word.text(), 0.0F, 0.0F));
		}
		float seconds = Sounds.durationTicks(phoneme) / 20.0F;
		words.add(new Captions.Word("♪", 0.0F, seconds));
		return CaptionLine.timed(new Captions.Caption(seconds, words));
	}

	/**
	 * The room back to how it began, for a new stage: nothing in the child's pack or on them, no
	 * creatures, no blocks a word put down, no effects, normal size, nothing in the air. Prizes
	 * waiting are untouched; they are in the visit, not the pack.
	 */
	private void resetForStage(ServerPlayer player) {
		player.getInventory().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.containerMenu.broadcastChanges();
		room.dispenser.clearForStage();
		player.removeAllEffects();
		Dispenser.unscale(player);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, layout.bounds())) item.discard();
		Narrator.note(player, "stage_reset:" + room.stage);
	}

	void flourish(ServerPlayer player) {
		Vec3 at = player.position().add(0, player.getBbHeight() + 0.8, 0);
		level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 60, 0.8, 0.6, 0.8, 0.08);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 20, 0.8, 0.6, 0.8, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** The last round's tiles, gone from the rows, the hands and the floor, and whatever a word put down. */
	void clearRound() {
		// A word's night or rain is over: a clear noon for the next.
		for (ServerPlayer one : room.present()) justfatlard.learning_blocks.classroom.Sky.reset(one);
		if (room.bob != null) {
			room.bob.clear();
			room.bob = null;
		}
		if (room.subjectRound != null) {
			room.subjectRound.clear();
			room.subjectRound = null;
		}
		room.fixLetter = null;
		room.team = false;
		room.teamReaders.clear();
		room.gapOwners.clear();
		room.gapPlacers.clear();
		endScene();
		clearChoices();
		for (BlockPos pos : room.adjectiveRow) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
		room.adjectiveRow.clear();
		room.phraseAdjective = null;
		room.tricky = false;
		room.dispenser.clearVehicles();
		room.dispenser.clearPlaced();
		room.picture.remove();
		for (BlockPos pos : room.row) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
		for (BlockPos marker : room.gapMarkers.values()) level.setBlock(marker, RoomLayout.BOARD, FLAGS);
		room.gapMarkers.clear();
		room.gapMarker = null;
		// Every tile wherever it ended up: on the floor, in the gap, in a hand, on the ground.
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -HALF + 1; x < HALF; x++) {
			for (int z = RoomLayout.FRONT + 1; z < RoomLayout.BACK; z++) {
				at.setWithOffset(layout.origin(), x, 0, z);
				if (level.getBlockState(at).is(LearningBlocks.LETTER)) level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
			}
		}
		room.row.clear();
		room.homes.clear();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(room.owner);
		if (player != null) Tiles.clear(player);
		// Every item on the ground, not just tiles: whatever the button threw that was not caught.
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, layout.bounds())) item.discard();
		room.dispenser.reserved.clear();
	}

	void endScene() {
		if (room.scene == null) return;
		room.scene.end(level.getServer().getPlayerList().getPlayer(room.owner));
		room.scene = null;
	}

	/** "Can you make... cat?", or in a scene, its line and "What can help?" */
	Narration prompt() {
		Narration prompt = new Narration();
		if (room.learningTime) {
			room.learningTime = false;
			// "Reading time first!" for a child with nothing but Reading on; "Learning time first!" for anyone else.
			ServerPlayer reader = room.turnPlayer();
			boolean readingOnly = reader != null && justfatlard.learning_blocks.subject.Subjects.on(reader).stream()
				.allMatch(subject -> subject == justfatlard.learning_blocks.subject.Subjects.READING);
			prompt.say(readingOnly ? "phrase/reading_time" : "phrase/learning_time", 2);
		}
		// A new subject: its hello first ("Numbers time!").
		if (room.pendingHello != null) {
			prompt.say(room.pendingHello, 2);
			room.pendingHello = null;
		}
		prompt.add(room.prizes(room.turn != null ? room.turn : room.owner).prizeLine());
		// Reading together: whose turn it is, or both together, first.
		if (room.shared() && room.puzzle != null) {
			if (room.team) prompt.say("phrase/team_up", 2);
			else if (room.turn != null && (room.bob == null || !room.bob.picking)) {
				String clip = "phrase/your_turn";
				float seconds = Sounds.durationTicks(clip) / 20.0F;
				prompt.say(clip, "your_turn:" + room.nameOf(room.turn), p -> {}, CaptionLine.timed(new Captions.Caption(seconds,
					List.of(new Captions.Word(room.nameOf(room.turn) + "'s turn!", 0.0F, seconds)))), 2);
			}
		}
		return ask(prompt);
	}

	Narration ask(Narration narration) {
		Puzzle puzzle = room.puzzle;
		if (puzzle == null) return narration;
		if (room.bob != null) return room.bob.ask(narration);
		if (room.subjectRound != null) return room.subjectRound.ask(narration);
		// A word to fix: the board spells something else; the picture says what it should be.
		if (room.fixLetter != null && room.letterTarget == null) return narration.say("phrase/whats_wrong");
		if (room.scene != null) return narration.say("scene/" + room.scene.word().text(), 2).say("phrase/what_can_help");
		// A tricky word: the room says it, with a star, since it is not sounded out; then find its card.
		if (room.tricky) {
			narration.say("word/" + puzzle.target().text(), "tricky_word", p -> {}, starred(puzzle.target()), Narration.GAP);
			if (justfatlard.learning_blocks.sound.Sounds.isRecorded("phrase/find_this_word")) narration.say("phrase/find_this_word");
			return narration;
		}
		// Read it: never the word, only the question.
		if (room.readIt) return narration.say("phrase/what_does_it_say", 2);
		Letter letterTarget = room.letterTarget;
		if (room.casePrompt != null && letterTarget != null) {
			// "Which one is the big..." and the letter's name: a name, since a capital is the same sound.
			return narration.say(letterTarget.isCapital() ? "phrase/find_big" : "phrase/find_little", 2)
				.say(LetterBlock.nameOf(letterTarget.lower()), "name:" + letterTarget.lower().grapheme(), p -> {},
					CaptionLine.building(List.of(letterTarget.shown()), 0), Narration.GAP);
		}
		if (letterTarget != null) {
			String phoneme = "phoneme/" + letterTarget.grapheme();
			return narration.say(room.letterPrompt, 2).say(phoneme, "sound:" + letterTarget.grapheme(), p -> {}, soundLine(room.letterPrompt, phoneme), Narration.GAP);
		}
		return narration.say("phrase/make_the_word", 2).say("word/" + puzzle.target().text());
	}
}
