package justfatlard.learning_blocks.gametest;

import java.util.function.BooleanSupplier;
import justfatlard.pandorical.gametest.Smoke;

/** Waiting a tick at a time for something on the server to become true. */
final class Waits {
	private Waits() {}

	/**
	 * Gone home after /learn leave, which waits for the prize line, however long the voice takes to
	 * say it; or, kept for lessons still owed, told so and staying.
	 */
	static void home(Smoke.Session session) {
		until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level())
			|| justfatlard.learning_blocks.classroom.Travel.lessons(session.player()) > 0);
	}

	static void until(Smoke.Session session, int maxTicks, String failure, BooleanSupplier condition) {
		boolean[] done = new boolean[1];
		for (int tick = 0; tick <= maxTicks; tick++) {
			session.onServer(server -> done[0] = condition.getAsBoolean());
			if (done[0]) return;
			session.waitTicks(1);
		}
		throw new AssertionError(failure + " (after " + maxTicks + " ticks)");
	}
}
