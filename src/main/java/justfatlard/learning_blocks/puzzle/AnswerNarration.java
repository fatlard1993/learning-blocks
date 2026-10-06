package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.reward.Prizes;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Sounds;
import justfatlard.learning_blocks.Safely;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The button pressed: the word sounded out a tile at a time, each tile lit as it is said, then the
 * whole word, then what it does, then what the room thinks of it. The answer is counted by
 * {@link AnswerCommit} at the press; everything here is the telling.
 */
final class AnswerNarration {
	private static final int FLAGS = RoomLayout.FLAGS;
	/** Ticks after each letter of a blend, so each tile stays lit long enough to see. */
	private static final int PHONEME_GAP = 8;
	/** How long a silent letter stays lit, with nothing said: long enough to be seen. */
	private static final int SILENT_BEAT = 10;
	private static final int CHUNK_GAP = 10;
	private static final int JOIN_BEAT = 10;
	static final int WORD_GAP = 6;
	/** A beat for the word to happen in before the room says anything about it. */
	private static final int OUTCOME_BEAT = 20;

	private final Room room;
	private final ServerLevel level;
	private final AnswerCommit commit;

	AnswerCommit commit() {
		return commit;
	}

	AnswerNarration(Room room, ServerLevel level) {
		this.room = room;
		this.level = level;
		this.commit = new AnswerCommit(room);
	}

	void press(ServerPlayer player) {
		Puzzle current = room.puzzle;
		if (current == null || room.busy || room.leaving) return;
		// Bob's round: the sentence read back, and Bob does it.
		if (room.bob != null) {
			room.bob.press(player);
			return;
		}
		// Another subject's round is answered by picking a block; the button asks again.
		if (room.subjectRound != null) {
			room.subjectRound.pressed(player);
			return;
		}
		room.touch();
		// A read-it round is answered by picking a picture; the button asks again.
		if (room.readIt) {
			room.replay(player);
			return;
		}
		Letter gap = room.inGap();
		if (gap == null && current.kind() != Puzzle.Kind.WHOLE && current.kind() != Puzzle.Kind.CHUNK) {
			Narrator.play(player, new Narration().say("phrase/pick_a_letter"));
			return;
		}
		if (room.letterTarget != null) {
			pressLetter(player, gap);
			return;
		}
		List<String> tiles = room.interaction.built();
		if (tiles == null) {
			// A row not yet full cannot be sounded out: just "hmm".
			Narrator.note(player, "partial");
			Narrator.play(player, new Narration().say("phrase/hmm"));
			return;
		}
		String built = String.join("", tiles);
		Word word = current.spelled(tiles);
		boolean newWord = AnswerCommit.sameWord(word, current.target()) && Prizes.isNewWord(player, word);
		int lessonsBefore = AnswerCommit.lessonsBefore(player);
		boolean firstTry = !room.missedThisRound;
		boolean lastOfStage = AnswerCommit.lastOfStage(player, word, current.target(), room.stage, firstTry);
		boolean justMastered = word != null && AnswerCommit.masters(player, word, firstTry);
		boolean inScene = room.scene != null;
		// If this narration is cut short once the word has happened, the round still moves on.
		boolean[] happened = new boolean[1];
		int roundAt = room.round;

		Narration blend = new Narration();
		// "Let's sound it out!" the first time only: after that the child knows what is coming.
		if (room.blends++ == 0) blend.say("phrase/sound_it_out");
		Word adjective = room.phraseAdjective;
		if (adjective != null) {
			blend.say("word/" + adjective.text(), "light:adjective", p -> lightAdjective(true),
				CaptionLine.building(List.of(adjective.text()), 0), WORD_GAP);
			blend.then("unlight_adjective", p -> lightAdjective(false), 2);
		}
		soundOut(blend, tiles, word, current);
		if (adjective != null && word != null) {
			blend.say("word/" + adjective.text(), "light:phrase", p -> lightAdjective(true),
				CaptionLine.building(List.of(adjective.text(), word.text()), 0), 2)
				.say("word/" + word.text(), "phrase_said", p -> {}, CaptionLine.building(List.of(adjective.text(), word.text()), 1), WORD_GAP);
		}

		if (word == null) {
			// Only reachable once words can be built from outside a family; kept honest anyway.
			Safely.run("Remembering a miss", () -> Progress.of(player).wordMissed(player, current.target(), tiles));
			blend.then("unlight", p -> unlight(), 4).say("phrase/hmm").say("phrase/try_again")
				// Nothing it spells: the tiles go home to try again.
				.then("tile_back", room.interaction::returnGapTile, Dispenser.FLIGHT + 4);
		} else if (AnswerCommit.sameWord(word, current.target()) && room.team) {
			// A team-up: the word is both readers', and each slot its own reader's.
			List<ServerPlayer> readers = teamReaders(player);
			for (ServerPlayer reader : readers) {
				List<Integer> own = ownSlots(reader, current);
				int before = reader == player ? lessonsBefore : AnswerCommit.lessonsBefore(reader);
				commit.right(reader, "reading " + word.text() + " together", () -> {
					Progress.of(reader).wordRight(reader, word, firstTry, own);
					Prizes.solved(reader, word, firstTry);
				}, before);
				// The other reader's last lesson: their own way out opens too.
				if (reader != player && before == 1) room.refreshPortal(reader, true);
			}
			happened[0] = true;
			blend.say("phrase/great_teamwork", 2);
			for (ServerPlayer reader : readers) if (reader != player) blend.add(room.prizes(reader.getUUID()).prizeLineFor(reader));
			tellRight(player, blend, word, current, lessonsBefore, newWord, lastOfStage, inScene, justMastered);
		} else if (AnswerCommit.sameWord(word, current.target())) {
			// Counted now, before anything is shown: what follows is only the telling.
			commit.right(player, "reading " + word.text(), () -> {
				// Remembered first, so a stage mastered by this very word pays as done.
				Progress.of(player).wordRight(player, word, firstTry, current.gaps());
				Prizes.solved(player, word, firstTry);
			}, lessonsBefore);
			happened[0] = true;
			tellRight(player, blend, word, current, lessonsBefore, newWord, lastOfStage, inScene, justMastered);
		} else if (room.team) {
			// A team-up built wrong: the miss is whoever put the wrong tile in, quietly; no blame said.
			commit.wrong(player);
			java.util.Set<ServerPlayer> missed = new java.util.LinkedHashSet<>();
			List<BlockPos> gapPositions = room.gaps();
			for (int k = 0; k < gapPositions.size() && k < current.gaps().size(); k++) {
				int at = current.gaps().get(k);
				if (at >= tiles.size() || tiles.get(at).equals(current.target().tiles().get(at))) continue;
				java.util.UUID placer = room.gapPlacers.get(gapPositions.get(k));
				ServerPlayer who = placer == null ? null : level.getServer().getPlayerList().getPlayer(placer);
				missed.add(who != null ? who : player);
			}
			for (ServerPlayer who : missed) {
				Safely.run("Remembering a miss", () -> Progress.of(who).wordMissed(who, current.target(), tiles));
				if (who != player) Safely.run("Counting a miss", () -> Prizes.missed(who));
			}
			blend.then("unlight", p -> unlight(), 4)
				.say("phrase/oops_thats", 2)
				.say("word/" + built)
				.then("outcome", p -> outcome(p, word, false), OUTCOME_BEAT)
				.then("tile_back", room.interaction::returnGapTile, Dispenser.FLIGHT + 4);
			room.rounds.ask(blend);
		} else {
			commit.wrong(player);
			Safely.run("Remembering a miss", () -> Progress.of(player).wordMissed(player, current.target(), tiles));
			blend.then("unlight", p -> unlight(), 4)
				.say("phrase/oops_thats", 2)
				.say("word/" + built)
				.then("outcome", p -> outcome(p, word, false), OUTCOME_BEAT)
				// The wrong tile goes back to its place on the floor, so the gap is empty to try again.
				.then("tile_back", room.interaction::returnGapTile, Dispenser.FLIGHT + 4);
			room.rounds.ask(blend);
		}
		finish(player, blend, happened, roundAt);
	}

	/**
	 * A picture picked in a read-it round. The right one: now the word is sounded out, as the check,
	 * and it happens as any right word does. Another: that one says its own word and wiggles, and the
	 * question is asked again; nothing cheeky, since this is a check of reading.
	 */
	void pick(ServerPlayer player, Word picked) {
		Puzzle current = room.puzzle;
		if (current == null || !room.readIt || room.busy || room.leaving) return;
		room.touch();
		Narrator.note(player, "picked:" + picked.text());
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		Narration blend = new Narration();
		if (room.tricky) {
			pickTricky(player, picked, current, blend, happened);
			finish(player, blend, happened, roundAt);
			return;
		}
		if (AnswerCommit.sameWord(picked, current.target())) {
			Word word = current.target();
			boolean firstTry = !room.missedThisRound;
			int lessonsBefore = AnswerCommit.lessonsBefore(player);
			boolean newWord = Prizes.isNewWord(player, word);
			boolean lastOfStage = AnswerCommit.lastOfStage(player, word, word, room.stage, firstTry);
			boolean justMastered = AnswerCommit.masters(player, word, firstTry);
			commit.right(player, "reading " + word.text(), () -> {
				Progress.of(player).wordRight(player, word, firstTry, List.of());
				Prizes.solved(player, word, firstTry);
			}, lessonsBefore);
			happened[0] = true;
			soundOut(blend, word.tiles(), word, current);
			tellRight(player, blend, word, current, lessonsBefore, newWord, lastOfStage, false, justMastered);
		} else {
			commit.wrong(player);
			Safely.run("Remembering a miss", () -> Progress.of(player).wordMissed(player, current.target(), picked.tiles()));
			blend.then("wiggle", p -> room.rounds.wiggle(p, picked), 2)
				.say("phrase/oops_thats", 2)
				.say("word/" + picked.text());
			room.rounds.ask(blend);
		}
		finish(player, blend, happened, roundAt);
	}

	/**
	 * A tricky word's card picked. The right one: the word said again, starred, a sparkle, and on to
	 * the next; never sounded out, since it can't be. Another: that card says its word and wiggles.
	 */
	private void pickTricky(ServerPlayer player, Word picked, Puzzle current, Narration blend, boolean[] happened) {
		Word word = current.target();
		if (AnswerCommit.sameWord(picked, word)) {
			boolean firstTry = !room.missedThisRound;
			int lessonsBefore = AnswerCommit.lessonsBefore(player);
			boolean newWord = Prizes.isNewWord(player, word);
			boolean justMastered = AnswerCommit.masters(player, word, firstTry);
			commit.right(player, "reading " + word.text(), () -> {
				Progress.of(player).wordRight(player, word, firstTry, List.of());
				Prizes.solved(player, word, firstTry);
			}, lessonsBefore);
			happened[0] = true;
			blend.say("word/" + word.text(), "light:all", p -> {
					light(-1);
					sparkle(p);
				}, room.rounds.starred(word), WORD_GAP)
				.say("phrase/well_done", 2);
			if (justMastered) blend.say("phrase/mastered");
			if (newWord) blend.say("phrase/new_word");
			blend.add(room.prizes(player.getUUID()).prizeLine());
			int left = lessonsBefore - 1;
			if (lessonsBefore > 0 && justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null) blend.say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left));
			if (lessonsBefore == 1) blend.say(portalLine(player)).then("door_open", p -> room.refreshPortal(p, true), 10);
			blend.say("phrase/another_one").then("next_round", room::newRound, 0);
		} else {
			commit.wrong(player);
			Safely.run("Remembering a miss", () -> Progress.of(player).wordMissed(player, word, picked.tiles()));
			blend.then("wiggle", p -> room.rounds.wiggle(p, picked), 2)
				.say("phrase/oops_thats", 2)
				.say("word/" + picked.text());
			room.rounds.ask(blend);
		}
	}

	/**
	 * The word in the row, sounded out: each tile as the word says it, lit as it is said, then the
	 * whole word. {@code word} is what the tiles spell, or null for something no word spells.
	 */
	private void soundOut(Narration blend, List<String> tiles, @org.jspecify.annotations.Nullable Word word, Puzzle current) {
		String built = String.join("", tiles);
		// Each tile as the word says it: its own sound, the word's override, or silence, which is
		// a lit tile and a greyed letter and nothing to hear.
		// A built word the family does not know takes the tile's own sound in the gap and the
		// target's for the rest.
		Word saying = word != null ? word : current.target();
		List<String> sounds = new ArrayList<>();
		java.util.Set<Integer> silent = new java.util.HashSet<>();
		for (int i = 0; i < tiles.size(); i++) {
			String sound = current.gaps().contains(i) && word == null ? "phoneme/" + tiles.get(i) : saying.soundOf(i);
			sounds.add(sound);
			if (sound == null) silent.add(i);
		}
		// A vowel saying its name is shown as its capital in the caption: A, the letter's name.
		List<String> shown = new ArrayList<>(tiles);
		for (int i = 0; i < tiles.size(); i++) {
			String phoneme = saying.phonemeOf(i);
			if (word != null && phoneme != null && phoneme.endsWith("_long")) shown.set(i, tiles.get(i).toUpperCase(java.util.Locale.ROOT));
		}
		int magicVowel = word != null ? magicVowel(word) : -1;
		// A big word: each chunk sounded out and said as its own little word, cat... fish...; then,
		// built right, the chunks slide together and the whole word is said.
		List<int[]> chunks = Words.chunkRanges(saying);
		if (chunks.size() < 2 || saying.tiles().size() != tiles.size()) chunks = List.of(new int[] {0, tiles.size()});
		for (int k = 0; k < chunks.size(); k++) {
			int[] chunk = chunks.get(k);
			List<String> chunkShown = shown.subList(chunk[0], chunk[1]);
			java.util.Set<Integer> chunkSilent = new java.util.HashSet<>();
			for (int i : silent) if (i >= chunk[0] && i < chunk[1]) chunkSilent.add(i - chunk[0]);
			for (int i = chunk[0]; i < chunk[1]; i++) {
				int tile = i;
				String sound = sounds.get(i);
				CaptionLine line = chunks.size() > 1 ? CaptionLine.building(chunkShown, i - chunk[0], chunkSilent) : CaptionLine.building(shown, i, silent);
				if (sound == null && magicVowel >= 0 && i == tiles.size() - 1) {
					// Magic e: the silent e lights up with the vowel it makes say its name, and a sparkle joins them.
					blend.show("silent:" + i, p -> magicE(p, magicVowel, tile), line, SILENT_BEAT + 6);
				} else if (sound == null) blend.show("silent:" + i, p -> light(tile), line, SILENT_BEAT);
				else blend.say(sound, "light:" + i, p -> light(tile), line, PHONEME_GAP);
			}
			if (chunks.size() > 1) {
				String text = String.join("", tiles.subList(chunk[0], chunk[1]));
				int from = chunk[0];
				int to = chunk[1];
				blend.say("word/" + text, "chunk:" + text, p -> lightRange(from, to), CaptionLine.building(List.of(text), 0), CHUNK_GAP);
			}
		}
		if (chunks.size() > 1 && word != null && AnswerCommit.sameWord(word, current.target())) {
			blend.then("join", p -> join(p, Words.chunkRanges(word)), JOIN_BEAT);
		}
		blend.say("word/" + built, "light:all", p -> light(-1), WORD_GAP);
		if (magicVowel >= 0) {
			// Once a visit for each vowel, when it is recorded: "The e makes the a say its name!"
			String phrase = "phrase/magic_e_" + tiles.get(magicVowel);
			if (Sounds.isRecorded(phrase) && room.magicSaid.add(tiles.get(magicVowel))) blend.say(phrase);
		}
	}

	private List<ServerPlayer> teamReaders(ServerPlayer presser) {
		List<ServerPlayer> readers = new java.util.ArrayList<>();
		for (ServerPlayer one : room.present()) if (room.teamReaders.contains(one.getUUID())) readers.add(one);
		if (!readers.contains(presser)) readers.add(presser);
		return readers;
	}

	private List<Integer> ownSlots(ServerPlayer reader, Puzzle current) {
		List<Integer> own = new java.util.ArrayList<>();
		List<BlockPos> gapPositions = room.gaps();
		for (int k = 0; k < gapPositions.size() && k < current.gaps().size(); k++) {
			if (reader.getUUID().equals(room.gapOwners.get(gapPositions.get(k)))) own.add(current.gaps().get(k));
		}
		return own;
	}

	private void tellRight(ServerPlayer player, Narration blend, Word word, Puzzle current, int lessonsBefore, boolean newWord, boolean lastOfStage,
			boolean inScene, boolean justMastered) {
		String target = current.target().text();
		blend.then("outcome", p -> outcome(p, word, true), inScene ? current.target().usableScene().map(Scene::solveTicks).orElse(OUTCOME_BEAT) : OUTCOME_BEAT)
			.say(inScene ? "phrase/that_helped" : "word/" + target);
		closing(player, blend, lessonsBefore, newWord, lastOfStage, justMastered);
	}

	/**
	 * How every right answer ends: "Well done!", mastered if it was, a new word, the prize, the
	 * lessons left, the way home if that was the last, and the next round.
	 */
	void closing(ServerPlayer player, Narration blend, int lessonsBefore, boolean newWord, boolean lastOfStage, boolean justMastered) {
		blend.say("phrase/well_done", 2);
		if (justMastered) blend.then("mastered", this::sparkle, 2).say("phrase/mastered");
		if (newWord) blend.say("phrase/new_word");
		blend.add(room.prizes(player.getUUID()).prizeLine());
		int left = lessonsBefore - 1;
		if (lessonsBefore > 0 && justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null) blend.say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left));
		if (lessonsBefore == 1) {
			blend.say(portalLine(player)).then("door_open", p -> room.refreshPortal(p, true), 10);
		}
		if (!lastOfStage) blend.say("phrase/another_one");
		blend
			.then("next_round", room::newRound, 0);
	}

	/**
	 * The button in the letters level. The right letter: the alphabet chant - its sound, its sound,
	 * its keyword - and the keyword happens. Another letter says its own sound, gently, goes home,
	 * and the sound is asked for again: no mischief here, where nothing should feel like a mistake.
	 */
	private void pressLetter(ServerPlayer player, Letter gap) {
		Letter target = room.letterTarget;
		if (target == null) return;
		if (room.casePrompt != null) {
			pressCase(player, gap, target, room.casePrompt);
			return;
		}
		String g = gap.grapheme();
		boolean right = gap == target;
		boolean firstTry = !room.missedThisRound;
		int lessonsBefore = AnswerCommit.lessonsBefore(player);
		Progress progress = Progress.of(player);
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		Narration chant = new Narration();
		if (right) {
			Word keyword = Letters.keyword(gap);
			List<String> said = keyword != null ? List.of(g, g, keyword.text()) : List.of(g, g);
			chant.say("phoneme/" + g, "light:0", p -> light(0), CaptionLine.building(said, 0), PHONEME_GAP);
			chant.say("phoneme/" + g, "light:0", p -> light(0), CaptionLine.building(said, 1), PHONEME_GAP);
			if (keyword != null) chant.say("word/" + keyword.text(), "light:all", p -> light(-1), CaptionLine.building(said, 2), WORD_GAP);
			else chant.then("flourish", room.rounds::flourish, 10);
			// The level done with this one: the next round is words, which says so itself.
			// One clean match a day counts: a second today masters nothing.
			boolean counts = firstTry && progress.hitCountsToday(g);
			boolean letterMastered = counts && progress.letterHits(g) + 1 == Letters.knownAfter();
			boolean lastOfLevel = counts && progress.letterHits(g) + 1 >= Letters.knownAfter()
				&& Letters.all().stream().allMatch(l -> l == gap || Letters.known(progress, l));
			commit.right(player, "finding " + g, () -> {
				Prizes.letterSolved(player, gap, firstTry);
				progress.letterAnswered(player, g, g, firstTry);
			}, lessonsBefore);
			happened[0] = true;
			chant.then("outcome", p -> {
					unlight();
					if (keyword != null) keyword.outcome().run(p, room.dispenser);
				}, OUTCOME_BEAT)
				.say("phrase/well_done", 2);
			if (letterMastered) chant.then("mastered", this::sparkle, 2).say("phrase/mastered");
			chant.add(room.prizes(player.getUUID()).prizeLine());
			int left = lessonsBefore - 1;
			if (lessonsBefore > 0 && justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null) chant.say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left));
			if (lessonsBefore == 1) chant.say(portalLine(player)).then("door_open", p -> room.refreshPortal(p, true), 10);
			if (!lastOfLevel) chant.say("phrase/another_one");
			chant.then("next_round", room::newRound, 0);
		} else {
			commit.wrong(player);
			Safely.run("Remembering a miss", () -> progress.letterAnswered(player, target.grapheme(), g, false));
			chant.say("phoneme/" + g, "light:0", p -> light(0), CaptionLine.building(List.of(g), 0), PHONEME_GAP)
				.then("unlight", p -> unlight(), 4)
				.say("phrase/hmm")
				.then("tile_back", room.interaction::returnGapTile, Dispenser.FLIGHT + 4);
			room.rounds.ask(chant);
		}
		finish(player, chant, happened, roundAt);
	}

	/** However the telling ends, the room is free again, and a counted answer's round moves on. */
	void finish(ServerPlayer player, Narration telling, boolean[] happened, int roundAt) {
		telling.onStop(p -> {
			room.busy = false;
			unlight();
			room.touch();
			room.refreshPortal(p, false);
			if (happened[0] && room.round == roundAt && !room.leaving) room.later(p, 1, room::newRound);
		});
		room.busy = true;
		Narrator.play(player, telling);
	}

	/**
	 * The vowel a final silent e makes say its name - the a in cake - or -1 if this is no such word:
	 * the last tile a silent e, and the vowel before it saying its long sound.
	 */
	static int magicVowel(Word word) {
		List<String> tiles = word.tiles();
		int last = tiles.size() - 1;
		if (last < 2 || !tiles.get(last).equals("e") || word.phonemeOf(last) != null) return -1;
		for (int i = last - 1; i >= 0; i--) {
			String phoneme = word.phonemeOf(i);
			if (phoneme != null && phoneme.equals("phoneme/" + tiles.get(i) + "_long")) return i;
		}
		return -1;
	}

	private void magicE(ServerPlayer player, int vowel, int e) {
		for (int i = 0; i < room.row.size(); i++) setLit(room.row.get(i), i == vowel || i == e);
		if (e >= room.row.size()) return;
		net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(room.row.get(e)).add(0, 0.2, 0.6);
		net.minecraft.world.phys.Vec3 to = net.minecraft.world.phys.Vec3.atCenterOf(room.row.get(vowel)).add(0, 0.2, 0.6);
		for (int step = 0; step <= 8; step++) {
			double t = step / 8.0;
			net.minecraft.world.phys.Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 0.6, 0);
			room.later(player, step, p -> level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.0));
		}
		room.later(player, 9, p -> {
			net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(room.row.get(vowel)).add(0, 0.2, 0.6);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, at.x, at.y, at.z, 12, 0.3, 0.3, 0.1, 0.0);
			level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.6F);
		});
		Narrator.note(player, "magic_e:" + vowel);
	}

	/**
	 * A big-and-little round. Right: the letter's name, the two tiles lit together with a sparkle
	 * between them ("A... a!"), and on to the next. Another tile says its own name, goes home, and
	 * the question is asked again, gently, as a letters-level miss is.
	 */
	private void pressCase(ServerPlayer player, Letter gap, Letter target, Letter shown) {
		boolean right = gap == target;
		boolean firstTry = !room.missedThisRound;
		int lessonsBefore = AnswerCommit.lessonsBefore(player);
		Progress progress = Progress.of(player);
		Letter base = target.lower();
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		Narration chant = new Narration();
		if (right) {
			boolean counts = firstTry && progress.hitCountsToday(Letters.caseKey(base));
			boolean mastered = counts && Letters.caseHits(progress, base) + 1 == Letters.knownAfter();
			boolean lastOfLevel = room.stage == 0 && counts && Letters.caseHits(progress, base) + 1 >= Letters.knownAfter()
				&& Letters.all().stream().allMatch(l -> (l == base || Letters.caseKnown(progress, l)) && Letters.known(progress, l));
			commit.right(player, "matching " + shown.shown() + " and " + target.shown(), () -> {
				progress.caseAnswered(player, base, true, firstTry);
				Prizes.caseSolved(player, base, firstTry);
			}, lessonsBefore);
			happened[0] = true;
			List<String> pair = List.of(shown.shown(), target.shown());
			String name = LetterBlock.nameOf(base);
			chant.say(name, "light:0", p -> light(0), CaptionLine.building(pair, 0), PHONEME_GAP);
			chant.say(name, "light:all", p -> caseLink(p), CaptionLine.building(pair, 1), WORD_GAP);
			chant.say("phrase/well_done", 2);
			if (mastered) chant.then("mastered", this::sparkle, 2).say("phrase/mastered");
			chant.add(room.prizes(player.getUUID()).prizeLine());
			int left = lessonsBefore - 1;
			if (lessonsBefore > 0 && justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null) chant.say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left));
			if (lessonsBefore == 1) chant.say(portalLine(player)).then("door_open", p -> room.refreshPortal(p, true), 10);
			if (!lastOfLevel) chant.say("phrase/another_one");
			chant.then("next_round", room::newRound, 0);
		} else {
			commit.wrong(player);
			Safely.run("Remembering a miss", () -> progress.caseAnswered(player, base, false, false));
			chant.say(LetterBlock.nameOf(gap.lower()), "light:1", p -> light(1), CaptionLine.building(List.of(gap.shown()), 0), PHONEME_GAP)
				.then("unlight", p -> unlight(), 4)
				.say("phrase/hmm")
				.then("tile_back", room.interaction::returnGapTile, Dispenser.FLIGHT + 4);
			room.rounds.ask(chant);
		}
		finish(player, chant, happened, roundAt);
	}

	private void caseLink(ServerPlayer player) {
		light(-1);
		if (room.row.size() < 2) return;
		net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(room.row.get(0)).add(0, 0.2, 0.6);
		net.minecraft.world.phys.Vec3 to = net.minecraft.world.phys.Vec3.atCenterOf(room.row.get(1)).add(0, 0.2, 0.6);
		for (int step = 0; step <= 6; step++) {
			double t = step / 6.0;
			net.minecraft.world.phys.Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 0.5, 0);
			room.later(player, step, p -> level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.0));
		}
		level.playSound(null, to.x, to.y, to.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.4F);
		Narrator.note(player, "case_link");
	}

	private static String portalLine(ServerPlayer player) {
		return Portal.openLine(justfatlard.learning_blocks.classroom.Schedules.metToday(player));
	}

	private void sparkle(ServerPlayer player) {
		net.minecraft.world.entity.Entity shown = room.picture();
		net.minecraft.world.phys.Vec3 at = shown != null ? shown.position()
			: room.row.isEmpty() ? player.position() : net.minecraft.world.phys.Vec3.atCenterOf(room.row.getFirst()).add(0, 0.6, 0.6);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, at.x, at.y + 0.3, at.z, 20, 0.4, 0.4, 0.2, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 8, 0.3, 0.3, 0.2, 0.02);
		level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.8F);
		Narrator.note(player, "mastered");
	}

	private void light(int index) {
		for (int i = 0; i < room.row.size(); i++) setLit(room.row.get(i), index < 0 || i == index);
	}

	private void lightRange(int from, int to) {
		for (int i = 0; i < room.row.size(); i++) setLit(room.row.get(i), i >= from && i < to);
	}

	/**
	 * A big word's chunks slide together: each tile after the first chunk moves along into the gap
	 * between them, with a click and a puff, and the row is one word.
	 */
	private void join(ServerPlayer player, List<int[]> chunks) {
		for (int c = 1; c < chunks.size(); c++) {
			for (int i = chunks.get(c)[0]; i < chunks.get(c)[1] && i < room.row.size(); i++) {
				BlockPos from = room.row.get(i);
				BlockPos to = from.west(c);
				BlockState state = level.getBlockState(from);
				level.setBlock(to, state, FLAGS);
				level.setBlock(from, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), FLAGS);
				room.row.set(i, to.immutable());
			}
		}
		BlockPos middle = room.row.get(chunks.get(1)[0]);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, middle.getX(), middle.getY() + 0.5, middle.getZ() + 1.0, 8, 0.3, 0.3, 0.1, 0.01);
		level.playSound(null, middle, net.minecraft.sounds.SoundEvents.PISTON_CONTRACT, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.6F);
		Narrator.note(player, "joined");
	}

	private void lightAdjective(boolean lit) {
		for (BlockPos pos : room.adjectiveRow) setLit(pos, lit);
	}

	private void unlight() {
		for (BlockPos pos : room.row) setLit(pos, false);
		lightAdjective(false);
	}

	private void setLit(BlockPos pos, boolean lit) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof LetterBlock && state.getValue(LetterBlock.LIT) != lit) {
			level.setBlock(pos, state.setValue(LetterBlock.LIT, lit), FLAGS);
		}
	}

	/** The word happens: the cat turns up, the hat lands. Only the showing; the answer is already counted. */
	private void outcome(ServerPlayer player, Word word, boolean target) {
		unlight();
		if (room.failNextOutcome) {
			room.failNextOutcome = false;
			throw new IllegalStateException("an outcome made to fail, for a test");
		}
		SceneRun scene = room.scene;
		if (target && scene != null && AnswerCommit.sameWord(scene.word(), word)) {
			// In a scene the right word fixes the problem, which is its own way of happening.
			Narrator.note(player, "solve");
			scene.solve(player);
			return;
		}
		// A phrase: the adjective and the word together, whichever word was built, friendly either way.
		if (room.phraseAdjective != null && AdjectivePhrases.fits(room.phraseAdjective, word)) {
			AdjectivePhrases.run(player, room.phraseAdjective, word, room);
			return;
		}
		// Built but not the word asked for: the word's cheeky version, if it has one.
		(target ? word.outcome() : word.wrongOutcome()).run(player, room.dispenser);
	}
}
