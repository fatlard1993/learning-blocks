package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import justfatlard.learning_blocks.Later;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The grown-ups' sampler: any kind of round, now, in the caller's classroom, as practice. A
 * practice round is played and said and done like any other, but counts for nothing: each reader's
 * progress is a copy for its length (see {@link Progress#practice}), and nothing is credited, paid
 * or taken off. The round after it is the caller's ordinary next round.
 */
public final class Sampler {
	private Sampler() {}

	/** The kinds of round, as {@code /learn try} names them, with a label and a line for the screen. */
	public enum Kind {
		LETTERS("Letters", "Hear a sound, find its letter"),
		CASE("Big & little", "Match a capital and its small letter, either way"),
		EARLY("Early word", "A first word from the letters level"),
		FIRST("First sound", "_at: cat, bat, hat"),
		LAST("Last sound", "ma_: map, mat, man"),
		MIDDLE("Middle vowel", "c_t: cat, cot, cut"),
		WHOLE("Whole word", "Every tile a gap"),
		CHUNK("Big word chunk", "A big word with one chunk to build: cat | fish"),
		READIT("Read it", "A word shown whole; pick its picture"),
		SCENE("Situation", "A word that helps someone"),
		PHRASE("Phrase", "Two words: big pig, six cod"),
		TRICKY("Tricky word", "Said, not sounded out; find its card"),
		PLURAL("Plural", "cats: three cats"),
		TEAMUP("Team-up", "Reading together: a gap each"),
		FIXIT("Fix the word", "A word on the board with one wrong letter: cot under a cat"),
		BOB("Tell Bob", "Word cards make a sentence; Bob does exactly what it says"),
		WHATSWRONG("What's wrong", "Bob's sentence made wrong; fix one card"),
		PREP("Little words", "Owl in box, or on box: read it, pick the picture"),
		SENTENCE("Silly sentences", "Any sentence at all, and Bob does it");

		public final String label;
		public final String description;

		Kind(String label, String description) {
			this.label = label;
			this.description = description;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Kind of(String name) {
			String wanted = name.toLowerCase(Locale.ROOT);
			if (wanted.equals("together")) return TEAMUP;
			for (Kind kind : values()) if (kind.id().equals(wanted)) return kind;
			return null;
		}
	}

	public static List<String> kindNames() {
		List<String> names = new ArrayList<>();
		for (Kind kind : Kind.values()) names.add(kind.id());
		names.add("together");
		return names;
	}

	public static List<String> targets(Kind kind) {
		RandomSource random = RandomSource.create(1);
		List<String> found = new ArrayList<>();
		switch (kind) {
			case LETTERS, CASE -> {
				for (Letter letter : Letters.all()) found.add(letter.grapheme());
			}
			case EARLY -> {
				for (Word word : Stages.at(1)) if (word.tiles().size() == 3) found.add(word.text());
			}
			case FIRST, LAST, MIDDLE, WHOLE, CHUNK -> {
				Puzzle.Kind puzzle = Puzzle.Kind.valueOf(kind.name());
				for (Word word : Stages.usable()) {
					Puzzle round = Puzzle.forTarget(word, Words.all(), random, puzzle);
					if (round != null && (puzzle == Puzzle.Kind.WHOLE || puzzle == Puzzle.Kind.CHUNK || round.shelf().size() >= 2)) found.add(word.text());
				}
			}
			case READIT -> {
				for (Word word : Stages.usable()) if (word.clearPicture()) found.add(word.text());
			}
			case SCENE -> {
				for (Word word : Stages.usable()) if (word.usableScene().isPresent()) found.add(word.text());
			}
			case PHRASE -> {
				for (Word word : Stages.usable()) {
					if (AdjectivePhrases.creature(word) == null || !AdjectivePhrases.creature(word).usable()) continue;
					Puzzle round = Puzzle.forTarget(word, Words.all(), random, Puzzle.Kind.FIRST);
					if (round != null && round.shelf().size() >= 2) found.add(word.text());
				}
			}
			case TRICKY -> {
				for (Word word : Words.tricky()) found.add(word.text());
			}
			case PLURAL -> {
				for (Word word : Words.plurals()) if (word.outcome().usable()) found.add(word.text());
			}
			case FIXIT -> {
				for (Word word : Stages.usable()) {
					for (Puzzle.Kind frame : List.of(Puzzle.Kind.MIDDLE, Puzzle.Kind.FIRST, Puzzle.Kind.LAST)) {
						Puzzle round = Puzzle.forTarget(word, Words.all(), random, frame);
						if (round != null && round.family().stream().anyMatch(other -> !AnswerCommit.sameWord(other, word)
							&& other.tiles().size() == word.tiles().size())) {
							found.add(word.text());
							break;
						}
					}
				}
			}
			case BOB -> {
				for (justfatlard.learning_blocks.bob.Goal goal : justfatlard.learning_blocks.bob.Goals.all()) found.add(goal.id());
			}
			case WHATSWRONG -> {
				for (justfatlard.learning_blocks.bob.Goal goal : justfatlard.learning_blocks.bob.Goals.all()) if (goal.rows().size() == 1) found.add(goal.id());
			}
			case SENTENCE -> found.add("any");
			case PREP -> {
				for (justfatlard.learning_blocks.bob.Goal goal : justfatlard.learning_blocks.bob.Goals.all()) if (BobRound.prepPossible(goal)) found.add(goal.id());
			}
			case TEAMUP -> {
				for (Word word : Stages.usable()) {
					if (word.tiles().size() < 3) continue;
					Puzzle first = Puzzle.forTarget(word, Words.all(), random, Puzzle.Kind.FIRST);
					Puzzle last = Puzzle.forTarget(word, Words.all(), random, Puzzle.Kind.LAST);
					if (first != null && last != null && first.shelf().size() >= 2 && last.shelf().size() >= 2) found.add(word.text());
				}
			}
		}
		return found.stream().distinct().toList();
	}

	/** The last kind each grown-up sampled, for "Next". */
	private static final java.util.Map<java.util.UUID, Kind> last = new java.util.HashMap<>();

	public static @Nullable Kind lastKind(ServerPlayer player) {
		return last.get(player.getUUID());
	}

	/**
	 * A practice round of this kind, for this word or letter (or a random one), now. Not in the
	 * classroom: in they go, and the round starts once they are there. What went wrong, said
	 * helpfully, or null.
	 */
	public static @Nullable String run(ServerPlayer player, Kind kind, @Nullable String wanted) {
		List<String> valid = targets(kind);
		if (valid.isEmpty()) return "There's nothing for a " + kind.label.toLowerCase(Locale.ROOT) + " round on this server";
		String target = wanted;
		if (target != null && !valid.contains(target.toLowerCase(Locale.ROOT))) {
			return "\"" + target + "\" can't be a " + kind.label.toLowerCase(Locale.ROOT) + " round. Some that can: "
				+ String.join(", ", valid.subList(0, Math.min(8, valid.size())));
		}
		if (target == null) target = valid.get(player.getRandom().nextInt(valid.size()));
		target = target.toLowerCase(Locale.ROOT);
		last.put(player.getUUID(), kind);
		Room room = Rooms.of(player);
		if (room == null || !Places.isClassroom(player.level())) {
			String refused = Travel.enter(player);
			if (refused != null) return refused;
			String chosen = target;
			// Once they are standing in it, and its own first round is out of the way.
			Later.after(player, 60, p -> {
				String problem = run(p, kind, chosen);
				if (problem != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(problem));
			});
			return null;
		}
		if (kind == Kind.TEAMUP && !room.shared()) return "A team-up needs two reading together: /learn pair, or /learn join";
		if (room.busy() || room.leaving(player)) return "Not just now: the room is busy";
		// Practice from here: everybody's progress a copy, nothing counted.
		room.practice = true;
		room.practiceStarting = true;
		for (ServerPlayer one : room.present()) Progress.practice(one, true);
		boolean started;
		try {
			started = start(player, room, kind, target);
			// Not every word with a clear picture can be a read-it round (a big word with only a
			// picture card has no picture to stand on the floor): with none asked for, try the others.
			if (!started && wanted == null) {
				List<String> others = new java.util.ArrayList<>(valid);
				java.util.Collections.shuffle(others, new java.util.Random(player.getRandom().nextLong()));
				for (String other : others) {
					if (other.equals(target)) continue;
					if (start(player, room, kind, other)) {
						target = other;
						started = true;
						break;
					}
				}
			}
		} finally {
			room.practiceStarting = false;
		}
		if (!started) {
			room.endPractice();
			return "That round couldn't be made just now; try another";
		}
		Narrator.note(player, "sample:" + kind.id() + ":" + target);
		return null;
	}

	/**
	 * A practice round of another subject ({@code numbers}, {@code logic}): of this kind and target,
	 * or its own choice. As {@link #run}: into the classroom first if need be; nothing counted.
	 */
	public static @Nullable String runSubject(ServerPlayer player, justfatlard.learning_blocks.subject.Subject subject, @Nullable String kind,
			@Nullable String target) {
		if (kind != null && !subject.kinds().contains(kind)) {
			return "\"" + kind + "\" is not a kind of " + subject.label() + " round. Kinds: " + String.join(", ", subject.kinds());
		}
		if (target != null && !subject.targets(kind != null ? kind : subject.kinds().getFirst()).contains(target)) {
			return "\"" + target + "\" can't be asked. Some that can: " + String.join(", ", subject.targets(kind != null ? kind : subject.kinds().getFirst()));
		}
		Room room = Rooms.of(player);
		if (room == null || !Places.isClassroom(player.level())) {
			String refused = Travel.enter(player);
			if (refused != null) return refused;
			Later.after(player, 60, p -> {
				String problem = runSubject(p, subject, kind, target);
				if (problem != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(problem));
			});
			return null;
		}
		if (room.busy() || room.leaving(player)) return "Not just now: the room is busy";
		room.practice = true;
		room.practiceStarting = true;
		for (ServerPlayer one : room.present()) Progress.practice(one, true);
		try {
			room.newSubjectRound(player, subject, kind, target);
		} finally {
			room.practiceStarting = false;
		}
		if (room.subjectRound == null || room.subjectRound.subject() != subject) {
			room.endPractice();
			return "That round couldn't be made just now; try another";
		}
		Narrator.note(player, "sample:" + subject.id() + ":" + room.subjectRound.kind() + ":" + room.subjectRound.item());
		return null;
	}

	private static boolean start(ServerPlayer player, Room room, Kind kind, String target) {
		Word word = switch (kind) {
			case LETTERS, CASE -> null;
			case TRICKY -> Words.tricky().stream().filter(w -> w.text().equals(target)).findFirst().orElse(null);
			case PLURAL -> Words.plurals().stream().filter(w -> w.text().equals(target)).findFirst().orElse(null);
			default -> Stages.usable().stream().filter(w -> w.text().equals(target)).findFirst().orElse(null);
		};
		return switch (kind) {
			case LETTERS -> {
				Letter letter = Letter.of(target);
				yield letter != null && Rooms.startLetterRound(player, letter);
			}
			case CASE -> {
				Letter letter = Letter.of(target);
				yield letter != null && Rooms.startCaseRound(player, letter, player.getRandom().nextBoolean());
			}
			case EARLY -> {
				Puzzle round = word == null ? null : Puzzle.forTarget(word, Words.all(), player.getRandom(), Puzzle.Kind.FIRST);
				if (round == null) yield false;
				Narrator.stop(player);
				room.forcedPuzzle = round;
				room.newRound(player);
				Narrator.note(player, "early_word:" + word.text());
				yield room.puzzle() == round;
			}
			case FIRST, LAST, MIDDLE, WHOLE, CHUNK -> word != null && Rooms.startRound(player, word, Puzzle.Kind.valueOf(kind.name()));
			case READIT -> word != null && Rooms.startReadIt(player, word);
			case SCENE -> word != null && Rooms.startScene(player, word);
			case PHRASE -> {
				List<Word> adjectives = Stages.usable().stream().filter(w -> AdjectivePhrases.ADJECTIVES.contains(w.text())).toList();
				yield word != null && !adjectives.isEmpty()
					&& Rooms.startPhrase(player, adjectives.get(player.getRandom().nextInt(adjectives.size())), word);
			}
			case TRICKY -> word != null && Rooms.startTricky(player, word);
			case PLURAL -> word != null && Rooms.startPlural(player, word);
			case FIXIT -> word != null && Rooms.startFix(player, word, null);
			case BOB -> Rooms.startBob(player, BobRound.Mode.GOAL, justfatlard.learning_blocks.bob.Goals.byId(target), null);
			case WHATSWRONG -> Rooms.startBob(player, BobRound.Mode.FIX, justfatlard.learning_blocks.bob.Goals.byId(target), null);
			case SENTENCE -> Rooms.startBob(player, BobRound.Mode.SILLY, null, null);
			case PREP -> Rooms.startBob(player, BobRound.Mode.PREP, justfatlard.learning_blocks.bob.Goals.byId(target), null);
			case TEAMUP -> {
				if (word == null) yield false;
				room.forcedTeamWord = word;
				yield Rooms.startTeamRound(player);
			}
		};
	}
}
