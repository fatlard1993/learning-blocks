package justfatlard.phonics.gametest;

import justfatlard.phonics.reward.Progress;
import net.fabricmc.api.ModInitializer;

/**
 * The tests were written for readers who start on words: every test world's player is new, and new
 * readers start on the letters level by default. So the tests start them on words, and the one
 * about the letters level turns letters on for itself. Read-it rounds and review are off in the
 * same way; the test about them turns them on.
 */
public final class TestDefaults implements ModInitializer {
	@Override
	public void onInitialize() {
		Progress.lettersFirstForTests(false);
		// Read-it rounds and review picks would change the round a test asked for: off unless a test turns them on.
		justfatlard.phonics.reward.PrizeConfig.extrasOffForTests(true);
		// Mastery one clean completion a day, and no word twice in a day: most tests ask for more
		// than that in a few minutes. ExitAndDays turns both on.
		justfatlard.phonics.reward.Memory.spreadOffForTests(true);
		Progress.repeatsForTests(true);
	}
}
