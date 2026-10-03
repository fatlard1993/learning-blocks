package justfatlard.learning_blocks.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Plushies and toys earned by mastering their words: owl by mastering owl, the teddy by mastering
 * hug, the car by mastering car. A toy's words are the puzzle words that bring or show it (see
 * {@link Words#earns}); when the last of them is mastered, the toy is earned, once, ever, and comes
 * as a prize, {@code toy_mastered}. A reader who had mastered them all before this existed earns
 * theirs on their next way in.
 */
public final class Earned {
	private Earned() {}

	private static volatile List<Word> linkedFor;
	private static volatile Map<String, List<Word>> links = Map.of();

	/** Every plushie and toy, {@code plush:owl}, {@code toy:car}, with the words that earn it; empty for one no word earns. */
	public static Map<String, List<Word>> links() {
		List<Word> now = Words.all();
		if (now != linkedFor) {
			Map<String, List<Word>> made = new LinkedHashMap<>();
			for (justfatlard.learning_blocks.plush.Plush plush : justfatlard.learning_blocks.plush.Plush.values()) made.put("plush:" + plush.word(), new ArrayList<>());
			for (justfatlard.learning_blocks.toy.Toy toy : justfatlard.learning_blocks.toy.Toy.values()) made.put("toy:" + toy.word(), new ArrayList<>());
			for (Word word : now) {
				if (!word.puzzle()) continue;
				for (String key : Words.earns(word)) if (made.containsKey(key)) made.get(key).add(word);
			}
			Map<String, List<Word>> frozen = new LinkedHashMap<>();
			made.forEach((key, words) -> frozen.put(key, List.copyOf(words)));
			links = java.util.Collections.unmodifiableMap(frozen);
			linkedFor = now;
		}
		return links;
	}

	/**
	 * A plushie or toy earned by mastering items of another subject: {@code toy:block} by
	 * {@code numbers:numeral:1} to {@code :5}, say. Kept beside the word links; a toy with both needs both.
	 */
	private static final Map<String, List<String>> itemLinks = new java.util.concurrent.ConcurrentHashMap<>();

	/** This toy is earned (also) by mastering these items of a subject. */
	public static void linkItems(String key, List<String> items) {
		itemLinks.put(key, List.copyOf(items));
	}

	/** For tests: the item links gone. */
	public static void clearItemLinksForTests() {
		itemLinks.clear();
	}

	public static List<String> itemsFor(String key) {
		return itemLinks.getOrDefault(key, List.of());
	}

	/** Earn whatever this item's mastery now earns: every toy linked to it whose items (and words) are all mastered. */
	public static void checkItem(ServerPlayer player, Progress progress, String item) {
		PrizeConfig.Event event = PrizeConfig.toyMastered();
		if (!PrizeConfig.get().enabled || !event.enabled()) return;
		for (Map.Entry<String, List<String>> link : itemLinks.entrySet()) {
			if (!link.getValue().contains(item) || earned(progress, link.getKey())) continue;
			if (!link.getValue().stream().allMatch(each -> justfatlard.learning_blocks.subject.Subjects.mastered(progress, each))) continue;
			if (!wordsFor(link.getKey()).stream().allMatch(progress::mastered)) continue;
			progress.rewarded.add(earnedKey(link.getKey()));
			progress.save(player);
			LearningBlocks.LOGGER.info("[{}] {} mastered {} and earned {}", LearningBlocks.MOD_ID, player.getGameProfile().name(), link.getValue(), link.getKey());
			Prizes.toyMastered(player, progress, link.getKey(), event);
		}
	}

	public static List<Word> wordsFor(String key) {
		return links().getOrDefault(key, List.of());
	}

	public static String earnedKey(String key) {
		return "earned:" + key;
	}

	public static boolean earned(Progress progress, String key) {
		return progress.rewardedFor(earnedKey(key));
	}

	/**
	 * Earn whatever is now earned: every toy whose words are all mastered and not yet earned - only
	 * those with {@code read} among their words, when it is given; all of them, for the backfill.
	 */
	public static void check(ServerPlayer player, Progress progress, @Nullable Word read) {
		PrizeConfig.Event event = PrizeConfig.toyMastered();
		if (!PrizeConfig.get().enabled || !event.enabled()) return;
		for (Map.Entry<String, List<Word>> link : links().entrySet()) {
			List<Word> words = link.getValue();
			if (words.isEmpty() || earned(progress, link.getKey())) continue;
			if (read != null && words.stream().noneMatch(word -> word.id().equals(read.id()))) continue;
			if (!words.stream().allMatch(progress::mastered)) continue;
			if (!itemsFor(link.getKey()).stream().allMatch(each -> justfatlard.learning_blocks.subject.Subjects.mastered(progress, each))) continue;
			progress.rewarded.add(earnedKey(link.getKey()));
			progress.save(player);
			LearningBlocks.LOGGER.info("[{}] {} mastered {} and earned {}", LearningBlocks.MOD_ID, player.getGameProfile().name(),
				words.stream().map(Word::text).toList(), link.getKey());
			Prizes.toyMastered(player, progress, link.getKey(), event);
		}
	}
}
