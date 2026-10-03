package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Every shipped word's picture, made exactly as the room makes it, laid out in a grid with the word
 * under each, for looking over in one go. Every word must have one. Picture on the full run only.
 */
public final class Pictures implements FabricClientGameTest {

	private static final int COLUMNS = 9;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(server -> {
				ServerPlayer player = session.player();
				List<Word> words = new ArrayList<>();
				for (Word word : Words.all()) if (word.id().getNamespace().equals("learning-blocks-justfatlard")) words.add(word);
				words.sort((a, b) -> a.stage() != b.stage() ? Integer.compare(a.stage(), b.stage()) : a.text().compareTo(b.text()));
				Vec3 base = player.position().add(0, 0, -12);
				int rows = (words.size() + COLUMNS - 1) / COLUMNS;
				for (int i = 0; i < words.size(); i++) {
					Word word = words.get(i);
					String card = justfatlard.learning_blocks.puzzle.Words.pictureCard(word);
					check(card != null || !word.picture().isEmpty(), word.id() + " has no picture");
					double x = base.x + (i % COLUMNS - (COLUMNS - 1) / 2.0) * 2.2;
					double y = base.y + 1.2 + (rows - 1 - i / COLUMNS) * 2.5;
					Display.ItemDisplay picture = card != null ? null : Room.pictureOf(player.level(), word.picture(server.registryAccess()), new Vec3(x, y + 0.35, base.z));
					if (picture != null) player.level().addFreshEntity(picture);
					Display.TextDisplay label = EntityTypes.TEXT_DISPLAY.create(player.level(), EntitySpawnReason.TRIGGERED);
					if (label != null) {
						label.snapTo(x, y - 0.95, base.z, 0, 0);
						label.setTransformation(new com.mojang.math.Transformation(null, null, new org.joml.Vector3f(2.0F, 2.0F, 2.0F), null));
						label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
						label.setText(Component.literal(word.text()));
						player.level().addFreshEntity(label);
						// A word with a picture card: the card, standing on its label.
						if (card != null) justfatlard.learning_blocks.puzzle.PictureCards.show(label, card);
					}
				}
				player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), 180.0F, -22.0F, true);
			});
			session.waitTicks(30);
			if (session.full()) {
				// Without the HUD, which would cover the bottom row.
				session.context().runOnClient(client -> {
					if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
				});
				session.waitTicks(2);
				session.context().takeScreenshot(TestScreenshotOptions.of("phonics-pictures").disableCounterPrefix());
				session.context().runOnClient(client -> {
					if (client.gui.hud.isHidden()) client.gui.hud.toggle();
				});
			}
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
