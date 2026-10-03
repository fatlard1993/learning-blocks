package justfatlard.learning_blocks.gametest;

import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.util.RandomSource;

/**
 * This mod loads, its mixins bind, a world comes up with it installed, and the two things that only
 * exist once a world does are there: the classroom dimension, from the datapack, and the words,
 * from the reload listener, enough of them to make a puzzle.
 */
public final class SmokeTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> session.onServer(server -> {
			if (server.getLevel(Places.CLASSROOM) == null) {
				throw new AssertionError("the classroom dimension did not load");
			}
			if (Words.all().size() < 3) {
				throw new AssertionError("only " + Words.all().size() + " words loaded");
			}
			Puzzle puzzle = Puzzle.pick(Words.all(), RandomSource.create(1));
			if (puzzle == null) throw new AssertionError("no puzzle could be made from the shipped words");
			if (puzzle.shelf().size() < 2) {
				throw new AssertionError("a puzzle with " + puzzle.shelf().size() + " tile to try: " + puzzle);
			}

			// The written defaults read without a single complaint; a file full of mistakes reads too,
			// naming each one and keeping everything else.
			java.util.List<String> clean = justfatlard.learning_blocks.reward.PrizeConfig.load(server);
			if (!clean.isEmpty()) throw new AssertionError("the default prizes file has problems: " + clean);
			java.util.List<String> messy = justfatlard.learning_blocks.reward.PrizeConfig.use("""
				{ "word_book": "scroll", "daily_cap": "lots",
				  "events": { "new_word": { "enabled": true, "prizes": [
				    { "item": "nomod:nothing" }, { "loot_table": "nomod:nowhere" }, { "xp": -3 }, { "teleport": true },
				    { "word_book": true }, { "item": "minecraft:cookie", "count": 2 } ] } } }
				""", server);
			// The word book, gone, is skipped without a complaint: the old setting and the old prize.
			if (messy.size() != 5) throw new AssertionError("expected five problems, got " + messy);
			if (justfatlard.learning_blocks.reward.PrizeConfig.get().newWord.prizes().size() != 1) {
				throw new AssertionError("the one good prize did not survive the bad ones");
			}
			justfatlard.learning_blocks.reward.PrizeConfig.load(server);
		}));
	}
}
