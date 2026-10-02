package justfatlard.phonics.gametest;

import java.util.function.BooleanSupplier;
import justfatlard.pandorical.gametest.Smoke;

/** Waiting a tick at a time for something on the server to become true. */
final class Waits {
	private Waits() {}

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
