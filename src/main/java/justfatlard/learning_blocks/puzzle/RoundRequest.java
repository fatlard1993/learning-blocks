package justfatlard.learning_blocks.puzzle;

import org.jspecify.annotations.Nullable;

/**
 * A round asked for rather than picked: by the sampler, an operator, a test, or the room itself (a
 * stage set, a situation). Every part is optional; {@link #NONE} is the room's own pick. Read once,
 * by the round it makes, and gone with it.
 */
record RoundRequest(
		@Nullable Word target,
		boolean stageSet,
		boolean asScene,
		justfatlard.learning_blocks.subject.@Nullable Subject subject,
		@Nullable String subjectKind,
		@Nullable String subjectTarget,
		@Nullable Letter letter,
		Puzzle.@Nullable Kind kind,
		@Nullable Word tricky,
		@Nullable Word plural,
		@Nullable Word phraseAdjective,
		@Nullable Word phraseNoun,
		@Nullable Letter caseLetter,
		boolean caseUp,
		@Nullable Word readIt,
		@Nullable Puzzle puzzle,
		@Nullable Word teamWord,
		boolean team,
		BobRound.@Nullable Mode bob,
		justfatlard.learning_blocks.bob.@Nullable Goal goal,
		justfatlard.learning_blocks.bob.Goals.@Nullable Mistake mistake,
		@Nullable Letter fixLetter) {
	static final RoundRequest NONE = new RoundRequest(null, false, false, null, null, null, null, null, null, null, null, null, null, false, null, null, null, false, null, null, null, null);

	/** A particular round asked for: it is the asker's own, not the next reader's turn. */
	boolean particular() {
		return target != null || stageSet || asScene || tricky != null || plural != null || puzzle != null || phraseAdjective != null
			|| caseLetter != null || letter != null || readIt != null || bob != null;
	}

	/** Whether anything of Reading's was asked for, which makes the round Reading's. */
	boolean readingAsked() {
		return particular() || team || fixLetter != null;
	}

	RoundRequest withTarget(@Nullable Word target) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withStageSet(boolean stageSet) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withAsScene(boolean asScene) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withSubject(justfatlard.learning_blocks.subject.@Nullable Subject subject) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withSubjectKind(@Nullable String subjectKind) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withSubjectTarget(@Nullable String subjectTarget) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withLetter(@Nullable Letter letter) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withKind(Puzzle.@Nullable Kind kind) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withTricky(@Nullable Word tricky) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withPlural(@Nullable Word plural) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withPhraseAdjective(@Nullable Word phraseAdjective) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withPhraseNoun(@Nullable Word phraseNoun) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withCaseLetter(@Nullable Letter caseLetter) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withCaseUp(boolean caseUp) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withReadIt(@Nullable Word readIt) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withPuzzle(@Nullable Puzzle puzzle) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withTeamWord(@Nullable Word teamWord) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withTeam(boolean team) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withBob(BobRound.@Nullable Mode bob) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withGoal(justfatlard.learning_blocks.bob.@Nullable Goal goal) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withMistake(justfatlard.learning_blocks.bob.Goals.@Nullable Mistake mistake) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}

	RoundRequest withFixLetter(@Nullable Letter fixLetter) {
		return new RoundRequest(target, stageSet, asScene, subject, subjectKind, subjectTarget, letter, kind, tricky, plural, phraseAdjective, phraseNoun, caseLetter, caseUp, readIt, puzzle, teamWord, team, bob, goal, mistake, fixLetter);
	}
}
