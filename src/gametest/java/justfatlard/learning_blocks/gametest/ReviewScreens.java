package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.editor.Admins;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.review.Review;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Sounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * The review screens, for a player who has never been to the classroom: each opens and builds; a
 * letter says its sound, a vowel's long button its long sound; My words lists exactly the words
 * read, the operator's every-word view the unread ones too; nobody else opens the phrases. None of
 * it starts a visit. A picture of each screen on the full run.
 */
public final class ReviewScreens implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(server -> {
				ServerPlayer player = session.player();
				server.getPlayerList().deop(new NameAndId(player.getGameProfile()));
				check(!Admins.may(player), "the test player is still an operator");
				check(Progress.of(player).words().isEmpty(), "a fresh player has read words already");
			});

			// Letter sounds, by command, as a child would open it.
			session.onServer(server -> asPlayer(session.player(), "learn sounds"));
			session.waitTicks(10);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Review.openTab(player) == Review.Tab.LETTERS, "/learn sounds did not open the letters: " + Review.openTab(player));
				check(PandoricalApi.getOpenScreenId(player.getUUID()) != null, "no screen is open");
				Review.press(player, "letter:b");
				check(lastPlayed().equals("phoneme/b"), "the b tile did not say phoneme/b: " + lastPlayed());
				check(Sounds.recent().getLast().at() == null, "the letter was played at a place, not to the viewer");
				Review.press(player, "name:b");
				check(lastPlayed().equals("name/b"), "b's name button did not say name/b: " + lastPlayed());
				Review.press(player, "name:a");
				check(lastPlayed().equals("phoneme/a_long"), "a's name button did not say its long sound: " + lastPlayed());
				Review.press(player, "tab:PAIRS");
				check(Review.openTab(player) == Review.Tab.PAIRS, "the two-letter sounds tab did not open");
				Review.press(player, "pair:sh");
				check(lastPlayed().equals("phoneme/sh"), "the sh tile did not say phoneme/sh: " + lastPlayed());
				Review.press(player, "tab:LETTERS");
			});
			shoot(session, "learning-review-letters");

			// Vowels, by its tab: short then the word, and long.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Review.press(player, "tab:VOWELS");
				check(Review.openTab(player) == Review.Tab.VOWELS, "the vowels tab did not open");
				Review.press(player, "long:a");
				check(lastPlayed().equals("phoneme/a_long"), "a's long button did not say phoneme/a_long: " + lastPlayed());
				Review.press(player, "short:e");
				check(lastPlayed().equals("phoneme/e"), "e's short button did not say phoneme/e: " + lastPlayed());
			});
			Waits.until(session, 100, "the short e's word, its keyword egg, never followed", () -> lastPlayed().equals("word/egg"));
			shoot(session, "learning-review-vowels");

			// My words, with nothing read: empty, and kind about it.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Review.press(player, "tab:WORDS");
				check(Review.openTab(player) == Review.Tab.WORDS, "the my words tab did not open");
				check(Review.readShown(player).isEmpty() && Review.unreadShown(player).isEmpty(), "a fresh player's words are not empty");
			});
			shoot(session, "learning-review-words-empty");

			// Not an operator: no phrases, no every-word view, no one else's words.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(!Review.open(player, Review.Tab.PHRASES, null, false), "a child opened the phrases");
				Review.press(player, "tab:PHRASES");
				check(Review.openTab(player) == Review.Tab.WORDS, "a child's phrases tab press went through");
				asPlayer(player, "learn phrases");
				check(Review.openTab(player) == Review.Tab.WORDS, "/learn phrases worked for a child");
				check(!Review.open(player, Review.Tab.WORDS, null, true), "a child opened every word");
			});

			// Three words read: My words lists exactly those.
			List<String> read = List.of("learning-blocks-justfatlard:cat", "learning-blocks-justfatlard:hat", "learning-blocks-justfatlard:bat");
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Progress progress = Progress.of(player);
				for (String id : read) progress.read(player, id);
				check(Review.open(player, Review.Tab.WORDS, null, false), "my words would not open");
				check(Set.copyOf(Review.readShown(player)).equals(Set.copyOf(read)) && Review.readShown(player).size() == read.size(),
					"my words shows " + Review.readShown(player) + ", not " + read);
				check(Review.unreadShown(player).isEmpty(), "a child is shown unread words by name: " + Review.unreadShown(player));
				Review.press(player, "word:learning-blocks-justfatlard:cat");
				check(lastPlayed().equals("word/cat"), "clicking cat did not say it: " + lastPlayed());
				Review.press(player, "word:learning-blocks-justfatlard:fan");
				check(lastPlayed().equals("word/cat"), "an unread word could be played by a child: " + lastPlayed());
			});
			shoot(session, "learning-review-words");

			session.onServer(server -> {
				ServerPlayer player = session.player();
				Review.press(player, "close");
				check(Review.openTab(player) == null, "done did not close the review");
			});

			// An operator: every word, unread ones included, and the phrases.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Ops.op(server, player);
				asPlayer(player, "learn words all");
				check(Review.openTab(player) == Review.Tab.WORDS, "/learn words all did not open");
				int usable = Stages.usable().size();
				check(Review.readShown(player).size() == read.size(), "every word lost the read ones: " + Review.readShown(player));
				check(Review.unreadShown(player).size() == usable - read.size(),
					"every word shows " + Review.unreadShown(player).size() + " unread, not " + (usable - read.size()));
				check(Review.unreadShown(player).contains("learning-blocks-justfatlard:fan"), "fan, unread, is not in every word");
				Review.press(player, "word:learning-blocks-justfatlard:fan");
				check(lastPlayed().equals("word/fan"), "an operator could not hear an unread word: " + lastPlayed());
			});
			shoot(session, "learning-review-words-all");

			session.onServer(server -> {
				ServerPlayer player = session.player();
				asPlayer(player, "learn phrases");
				check(Review.openTab(player) == Review.Tab.PHRASES, "/learn phrases did not open for an operator");
				check(Review.phraseKeys().contains("phrase/next_level"), "next_level is not among the phrases");
				Review.press(player, "phrase:phrase/next_level");
				check(lastPlayed().equals("phrase/next_level"), "the next_level row did not play it: " + lastPlayed());
			});
			shoot(session, "learning-review-phrases");

			// A child's own Numbers and Logic tabs, seen by an operator: the child's stars, not the operator's.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				net.fabricmc.fabric.api.entity.FakePlayer child = StandIns.inClassroom(player.level(), "TestKidA", player.position());
				try {
					String three = justfatlard.learning_blocks.subject.Numbers.numeral(3);
					String pens = justfatlard.learning_blocks.logic.LogicCourse.Kind.SORT.item("kind");
					Progress.of(child).setItemCleanForTests(child, three, Progress.masteredAt());
					Progress.of(child).setItemCleanForTests(child, pens, Progress.masteredAt());
					check(!Progress.of(player).itemMastered(three), "the operator already knows three");
					check(Review.open(player, Review.Tab.NUMBERS, child, false), "the child's numbers would not open");
					check(Review.masteredShown(player).contains(three), "the numbers tab did not show the child's three: " + Review.masteredShown(player));
					Review.press(player, "tab:LOGIC");
					check(Review.openTab(player) == Review.Tab.LOGIC, "the logic tab did not open");
					check(Review.masteredShown(player).contains(pens), "the logic tab did not show the child's pens: " + Review.masteredShown(player));
					Review.press(player, "logic:0");
					check(lastPlayed().equals("phrase/put_in_pens"), "the pens game did not ask its question: " + lastPlayed());
					Review.press(player, "close");
					// The operator's own: no stars of the child's.
					check(Review.open(player, Review.Tab.LOGIC, null, false), "the operator's logic would not open");
					check(!Review.masteredShown(player).contains(pens), "the operator's logic tab shows the child's stars");
				} finally {
					StandIns.online(server, child, false);
				}
			});
			shoot(session, "learning-review-logic");

			session.onServer(server -> {
				ServerPlayer player = session.player();
				Review.press(player, "close");
				check(Visit.of(player) == null, "the review started a visit");
				server.getPlayerList().deop(new NameAndId(player.getGameProfile()));
			});
		});
	}

	private static String lastPlayed() {
		List<Sounds.Played> recent = Sounds.recent();
		return recent.isEmpty() ? "" : recent.getLast().key();
	}

	private static void asPlayer(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void shoot(Smoke.Session session, String name) {
		session.waitTicks(8);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
