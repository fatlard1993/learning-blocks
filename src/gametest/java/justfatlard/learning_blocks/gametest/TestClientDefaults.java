package justfatlard.learning_blocks.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * The tests draw every tick, in software, off-screen: a short view keeps each frame cheap. The
 * classroom is a few blocks across and the overworld tests stand still, so nothing tested is further.
 */
public final class TestClientDefaults implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			client.options.renderDistance().set(4);
			client.options.simulationDistance().set(5);
		});
	}
}
