package justfatlard.learning_blocks.gametest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.google.gson.JsonObject;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.editor.PrizeEditor;
import justfatlard.learning_blocks.reward.Prize;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.PrizeFile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The prize editor and the file are one thing: a press through the same path a click takes changes
 * the file, the file reloads to the config the screen shows, and a hand edit reloads into both.
 *
 * <p>Every kind of control is pressed once - a toggle, a number, the experience,
 * adding a milestone, and picking and removing a loot table - and the file is checked to still carry
 * its comments. On the full run each tab is photographed.
 */
public final class EditorRoundTrip implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Ops.op(server, player);
				Ops.resetFile(server);

				PrizeEditor.press(player, "next:daily_cap");
				check(json(server).get("daily_cap").getAsInt() == 1, "the file's daily cap did not step from no cap to 1");
				check(PrizeConfig.get().dailyCap == 1, "the config did not reload to the file's cap");

				PrizeEditor.press(player, "t:events.solved.enabled");
				check(PrizeConfig.get().solved.enabled(), "turning Word read on did not reach the config");

				PrizeEditor.press(player, "add:events.milestone.every");
				check(PrizeConfig.get().milestones.size() == 4 && PrizeConfig.get().milestones.getLast().at() == 150,
					"adding a milestone did not add one at 150 words: " + PrizeConfig.get().milestones);
				PrizeEditor.press(player, "rm:events.milestone.every.3");
				check(PrizeConfig.get().milestones.size() == 3, "removing the milestone did not remove it");

				PrizeEditor.press(player, "next:xp:events.new_word.prizes");
				check(PrizeConfig.get().newWord.prizes().stream().anyMatch(p -> p instanceof Prize.Experience xp && xp.points() == 1),
					"stepping new word's experience did not give it 1 point: " + PrizeConfig.get().newWord.prizes());

				PrizeEditor.press(player, "lootadd:events.new_word.prizes");
				PrizeEditor.press(player, "loot_search", java.util.Map.of("text", "cat_morning"));
				PrizeEditor.press(player, "loot:0");
				check(PrizeConfig.get().newWord.prizes().stream().anyMatch(p -> p instanceof Prize.Loot loot
						&& loot.table().identifier().toString().equals("minecraft:gameplay/cat_morning_gift")),
					"picking the cat's morning gift did not add it: " + PrizeConfig.get().newWord.prizes());
				var list = PrizeFile.array(json(server), "events.new_word.prizes");
				int index = -1;
				for (int i = 0; i < list.size(); i++) if (list.get(i).getAsJsonObject().has("loot_table")) index = i;
				PrizeEditor.press(player, "lootrm:events.new_word.prizes#" + index);
				check(PrizeConfig.get().newWord.prizes().stream().noneMatch(p -> p instanceof Prize.Loot),
					"removing the loot table did not remove it");

				String text = read();
				check(text.contains("// Everything below, on or off at once."), "saving from the menu lost the file's comments");

				// A hand edit, then a reload: the config and the tree the screen reads both follow.
				write(text.replace("\"daily_cap\": 1", "\"daily_cap\": 7"));
				PrizeConfig.load(server);
				check(PrizeConfig.get().dailyCap == 7, "a hand edit did not reload");
				check(PrizeEditor.readInt("daily_cap", -1) == 7, "the editor does not see the hand edit");
			});

			if (session.full()) {
				for (String tab : new String[] {"GENERAL", "EVENTS", "MILESTONES", "THRESHOLDS"}) {
					session.onServer(server -> PrizeEditor.press(session.player(), "tab:" + tab));
					session.waitTicks(10);
					session.context().takeScreenshot(TestScreenshotOptions.of("phonics-editor-" + tab.toLowerCase(java.util.Locale.ROOT))
						.disableCounterPrefix());
				}
				session.onServer(server -> PrizeEditor.press(session.player(), "lootadd:events.new_word.prizes"));
				session.waitTicks(10);
				session.context().takeScreenshot(TestScreenshotOptions.of("phonics-editor-loot-picker").disableCounterPrefix());
				session.onServer(server -> PrizeEditor.press(session.player(), "loot_cancel"));
				session.onServer(server -> PrizeEditor.press(session.player(), "close"));
			}

			session.onServer(Ops::resetFile);
		});
	}

	private static JsonObject json(MinecraftServer server) {
		try {
			return PrizeFile.read();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static String read() {
		try {
			return Files.readString(PrizeFile.path(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static void write(String text) {
		try {
			Files.writeString(PrizeFile.path(), text, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
