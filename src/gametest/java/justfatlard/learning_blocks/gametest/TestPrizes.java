package justfatlard.learning_blocks.gametest;

import java.util.List;
import justfatlard.learning_blocks.reward.PrizeConfig;
import net.minecraft.server.MinecraftServer;

/** A prizes file the tests can count on: a diamond for each new word. The old word book setting is left in, and ignored. */
final class TestPrizes {
	private TestPrizes() {}

	static final String DIAMOND_PER_NEW_WORD = """
		{
		  // Comments too, since the real file has them.
		  "enabled": true,
		  "daily_cap": 0,
		  "word_book": "plain",
		  "events": {
		    "new_word": { "enabled": true, "prizes": [ { "item": "minecraft:diamond", "count": 1 } ] }
		  }
		}
		""";

	static void use(MinecraftServer server) {
		List<String> problems = PrizeConfig.use(DIAMOND_PER_NEW_WORD, server);
		if (!problems.isEmpty()) throw new AssertionError("the test prizes did not read cleanly: " + problems);
	}
}
