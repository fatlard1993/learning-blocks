package justfatlard.learning_blocks.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tests draw every tick, in software, off-screen: a short view keeps each frame cheap. The
 * classroom is a few blocks across and the overworld tests stand still, so nothing tested is further.
 *
 * <p>Each test's name goes in the log as it starts and as it ends, with how long it took, so a run's
 * log says what it covered and where it stopped.
 */
public final class TestClientDefaults implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("learning-blocks-gametest");
	private static Object running;
	private static long since;

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			client.options.renderDistance().set(4);
			client.options.simulationDistance().set(5);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			var now = FabricClientGameTestRunner.currentlyRunningGameTest;
			Object test = now == null ? null : now.getEntrypoint();
			if (test == running) return;
			long at = System.currentTimeMillis();
			if (running != null) LOG.info("[test] finished {} in {}s", running.getClass().getSimpleName(), (at - since) / 1000);
			if (test != null) LOG.info("[test] running {}", test.getClass().getSimpleName());
			running = test;
			since = at;
		});
	}
}
