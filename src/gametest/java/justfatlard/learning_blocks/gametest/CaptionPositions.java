package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLayout;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.CaptionSettings;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.sound.Captions;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * The caption at each place a player can put it, photographed in the classroom, and turned off.
 * Pictures on the full run only; the settings are exercised on every run. Every caption, at every
 * size, fits the narrowest window the game allows; "next level", the longest-looking line, wraps.
 */
public final class CaptionPositions implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			// Every caption the mod has fits the smallest window the game allows, at the largest size:
			// wrapped onto more lines where it must, and smaller only where lines cannot do it.
			session.onServer(server -> {
				int narrowest = justfatlard.pandorical.api.Viewport.LEAST.width();
				int checked = 0;
				for (String key : Captions.keys()) {
					Captions.Caption caption = Captions.get(key);
					if (caption == null || caption.words().isEmpty()) continue;
					List<String> words = new ArrayList<>();
					for (CaptionLine.Piece piece : CaptionLine.timed(caption).pieces()) words.add(piece.text());
					for (String size : new String[] {"small", "medium", "large"}) {
						CaptionLayout layout = CaptionLayout.fit(words, CaptionSettings.scaleOf(size), narrowest);
						check(layout.fits(narrowest), key + " at " + size + " is " + layout.panelWidth() + " wide in a " + narrowest + " window");
						check(layout.lines().size() <= CaptionLayout.MAX_LINES, key + " at " + size + " takes " + layout.lines().size() + " lines");
						if (layout.scale() < CaptionSettings.scaleOf(size)) {
							LearningBlocks.LOGGER.info("[{}] {} at {} is drawn smaller to fit: {}", LearningBlocks.MOD_ID, key, size, layout.scale());
						}
					}
					checked++;
				}
				check(checked > 50, "only " + checked + " captions checked");
				// "Wow! You're ready for new words!" at large is wider than the window: two lines, not smaller.
				Captions.Caption next = Captions.get("phrase/next_level");
				check(next != null, "no caption for next_level");
				List<String> words = new ArrayList<>();
				for (CaptionLine.Piece piece : CaptionLine.timed(next).pieces()) words.add(piece.text());
				CaptionLayout layout = CaptionLayout.fit(words, CaptionSettings.scaleOf("large"), narrowest);
				check(layout.lines().size() == 2 && layout.scale() == CaptionSettings.scaleOf("large"),
					"next_level at large should wrap onto two lines: " + layout);
			});

			session.command("/learn");
			session.waitTicks(80);
			session.onServer(server -> {
				CaptionSettings.set(session.player(), true, "large", "top");
				Narrator.play(session.player(), new Narration().say("phrase/next_level"));
			});
			session.waitTicks(30);
			if (session.full()) {
				session.context().takeScreenshot(TestScreenshotOptions.of("learning-caption-next-level").disableCounterPrefix());
			}
			session.onServer(server -> {
				CaptionSettings.set(session.player(), true, "large", "bottom");
				Narrator.play(session.player(), new Narration().say("phrase/next_level"));
			});
			session.waitTicks(30);
			if (session.full()) {
				session.context().takeScreenshot(TestScreenshotOptions.of("learning-caption-next-level-bottom").disableCounterPrefix());
			}
			for (String position : new String[] {"bottom", "raised", "top"}) {
				session.onServer(server -> {
					CaptionSettings.set(session.player(), true, "large", position);
					Narrator.play(session.player(), new Narration().say("phrase/sound_it_out"));
				});
				session.waitTicks(16);
				if (session.full()) {
					session.context().takeScreenshot(TestScreenshotOptions.of("learning-caption-" + position).disableCounterPrefix());
				}
			}
			session.onServer(server -> {
				CaptionSettings.set(session.player(), false, "large", "top");
				Narrator.play(session.player(), new Narration().say("phrase/sound_it_out"));
			});
			session.waitTicks(10);
			session.onServer(server -> CaptionSettings.set(session.player(), true, "large", "top"));
			session.command("/learn leave");
			Waits.home(session);
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
