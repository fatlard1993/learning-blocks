package justfatlard.phonics.reward;

import java.util.ArrayList;
import java.util.List;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Word;
import justfatlard.phonics.puzzle.Words;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What the room tells this when a child reads, and what it pays for.
 *
 * <p>Every payout goes into the child's visit - saved beside the things put aside at the door - and
 * is handed over at home on the way out. Nothing is given inside, so nothing earned can be lost in
 * the classroom, dropped into the void, or carried out twice.
 *
 * <p>The daily cap counts payouts, not items: a word that pays a cookie and a streak that pays
 * experience on the same read are two. Nothing past the cap is lost. It is resolved as it is earned
 * and kept in {@link Progress} as owed, then paid on a later day, oldest first, within that day's
 * cap. Anything that pays once - a stage, a milestone, a threshold - counts as paid once it is either
 * in the visit or owed, since both are certain to arrive.
 */
public final class Prizes {
	private Prizes() {}

	/** Whether reading this word would be this child's first time: what "That's a new word!" says. */
	public static boolean isNewWord(ServerPlayer player, Word word) {
		PrizeConfig config = PrizeConfig.get();
		return config.enabled && config.newWord.enabled() && !Progress.of(player).rewarded.contains(word.id().toString());
	}

	/** Came into the classroom. */
	public static void entered(ServerPlayer player) {
		PrizeConfig config = PrizeConfig.get();
		Progress progress = Progress.of(player);
		String today = Progress.today();
		boolean firstToday = !today.equals(progress.lastVisit);
		progress.lastVisit = today;
		progress.save(player);
		if (!config.enabled) return;
		payOwed(player);
		if (firstToday) award(player, progress, "daily_visit", config.dailyVisit);
		// Toys whose words were mastered before toys were earned this way: theirs now.
		Earned.check(player, progress, null);
		payThresholds(player, progress);
	}

	/**
	 * What the cap held back on earlier days, oldest first, for as long as today's cap allows, into
	 * the visit. Nothing happens outside a visit: owed prizes wait for the next one.
	 */
	public static void payOwed(ServerPlayer player) {
		PrizeConfig config = PrizeConfig.get();
		Progress progress = Progress.of(player);
		progress.rollDay();
		boolean paidAny = false;
		List<ItemStack> paidItems = new ArrayList<>();
		int paidXp = 0;
		while (!progress.owed.isEmpty() && (config.dailyCap == 0 || progress.paidToday < config.dailyCap)) {
			Visit visit = Visit.of(player);
			if (visit == null) break;
			Progress.Owed owed = progress.owed.removeFirst();
			Visit.set(player, visit.withPrizes(owed.items(), owed.xp()));
			paidItems.addAll(owed.items());
			paidXp += owed.xp();
			progress.paidToday++;
			paidAny = true;
			Phonics.LOGGER.info("[{}] {} was paid {}, held back by an earlier day's cap", Phonics.MOD_ID,
				player.getGameProfile().name(), owed.why());
		}
		progress.save(player);
		if (paidAny) Rooms.prizeAdded(player, paidItems, paidXp);
	}

	/**
	 * Read the word the room asked for.
	 *
	 * @param firstTry no other word was built this round before it
	 */
	public static void solved(ServerPlayer player, Word word, boolean firstTry) {
		PrizeConfig config = PrizeConfig.get();
		Progress progress = Progress.of(player);
		progress.words.add(word.id().toString());
		boolean fresh = progress.rewarded.add(word.id().toString());
		progress.streak++;
		// Two-letter tiles are learned by using them: each first-try word counts once for each.
		if (firstTry) {
			for (String tile : new java.util.LinkedHashSet<>(word.tiles())) if (tile.length() == 2) progress.hit(tile);
		}
		progress.save(player);
		if (!config.enabled) return;
		payOwed(player);

		if (fresh) award(player, progress, "new_word", config.newWord);
		if (firstTry || !config.solvedFirstTryOnly) award(player, progress, "solved", config.solved);
		if (progress.streak % config.streakLength == 0) award(player, progress, "streak", config.streak);
		// Not only on a fresh word: a stage payout that failed (a full cap is not failing) is tried again.
		// Per band, 1a, 1b: more often than per stage, which is the point.
		justfatlard.phonics.puzzle.Stages.Band band = justfatlard.phonics.puzzle.Stages.bandOf(word);
		if (config.stageComplete.enabled() && band != null && !paid(progress, band) && justfatlard.phonics.puzzle.Stages.complete(progress, band)) {
			if (award(player, progress, "stage_complete", config.stageComplete)) progress.stagesPaid.add(band.code());
		}
		if (fresh && config.milestonesEnabled) {
			for (PrizeConfig.Mark mark : config.milestones) {
				if (progress.rewarded.size() >= mark.at() && !progress.milestonesPaid.contains(mark.at())) {
					if (pay(player, progress, "milestone " + mark.at(), mark.prizes())) progress.milestonesPaid.add(mark.at());
				}
			}
		}
		Earned.check(player, progress, word);
		payThresholds(player, progress);
		progress.save(player);
	}

	/**
	 * A plushie or a toy earned by mastering its words: the toy itself, and whatever else the event
	 * pays. One a child already has from an older random prize stays theirs, and nothing more comes.
	 */
	static void toyMastered(ServerPlayer player, Progress progress, String key, PrizeConfig.Event event) {
		String name = key.substring(key.indexOf(':') + 1);
		boolean plush = key.startsWith("plush:");
		boolean owned = plush ? progress.plushies().keySet().stream().anyMatch(p -> p.word().equals(name))
			: progress.toys().keySet().stream().anyMatch(t -> t.word().equals(name));
		List<Prize> prizes = new ArrayList<>();
		if (!owned) prizes.add(plush ? new Prize.Plushie(name) : new Prize.Toy(name));
		prizes.addAll(event.prizes());
		if (prizes.isEmpty()) return;
		if (pay(player, progress, "toy_mastered " + key, prizes)) progress.points += event.points();
		progress.save(player);
	}

	/**
	 * Found the letter the room asked for. First try, it counts towards knowing it. It pays as a
	 * word does: the first time for each letter is new, and the whole level done is its stage done.
	 */
	public static void letterSolved(ServerPlayer player, justfatlard.phonics.puzzle.Letter letter, boolean firstTry) {
		PrizeConfig config = PrizeConfig.get();
		Progress progress = Progress.of(player);
		String g = letter.grapheme();
		if (firstTry) progress.hit(g);
		boolean fresh = progress.rewarded.add("letter:" + g);
		progress.streak++;
		progress.save(player);
		if (!config.enabled) return;
		payOwed(player);

		if (fresh) award(player, progress, "new_word", config.newWord);
		if (firstTry || !config.solvedFirstTryOnly) award(player, progress, "solved", config.solved);
		if (progress.streak % config.streakLength == 0) award(player, progress, "streak", config.streak);
		if (config.stageComplete.enabled() && !progress.stagesPaid.contains(0) && justfatlard.phonics.puzzle.Letters.complete(progress)) {
			if (award(player, progress, "stage_complete", config.stageComplete)) progress.stagesPaid.add(0);
		}
		payThresholds(player, progress);
		progress.save(player);
	}

	/**
	 * Matched a letter's big and little. It pays as a letter found does - new the first time, the
	 * streak, and the letters level done when this was its last pair - and counts nowhere else.
	 */
	public static void caseSolved(ServerPlayer player, justfatlard.phonics.puzzle.Letter letter, boolean firstTry) {
		PrizeConfig config = PrizeConfig.get();
		Progress progress = Progress.of(player);
		boolean fresh = progress.rewarded.add(justfatlard.phonics.puzzle.Letters.caseKey(letter));
		progress.streak++;
		progress.save(player);
		if (!config.enabled) return;
		payOwed(player);
		if (fresh) award(player, progress, "new_word", config.newWord);
		if (firstTry || !config.solvedFirstTryOnly) award(player, progress, "solved", config.solved);
		if (progress.streak % config.streakLength == 0) award(player, progress, "streak", config.streak);
		if (config.stageComplete.enabled() && !progress.stagesPaid.contains(0) && justfatlard.phonics.puzzle.Letters.complete(progress)) {
			if (award(player, progress, "stage_complete", config.stageComplete)) progress.stagesPaid.add(0);
		}
		payThresholds(player, progress);
		progress.save(player);
	}

	/**
	 * Told Bob a sentence that made the goal. It pays as a word does - new the first time, the
	 * streak - and counts towards no stage: sentences are their own thing.
	 */
	public static void sentenceSolved(ServerPlayer player, String goal, boolean firstTry) {
		PrizeConfig config = PrizeConfig.get();
		Progress progress = Progress.of(player);
		boolean fresh = progress.rewarded.add(Progress.sentenceKey(goal));
		progress.streak++;
		progress.save(player);
		if (!config.enabled) return;
		payOwed(player);
		if (fresh) award(player, progress, "new_word", config.newWord);
		if (firstTry || !config.solvedFirstTryOnly) award(player, progress, "solved", config.solved);
		if (progress.streak % config.streakLength == 0) award(player, progress, "streak", config.streak);
		payThresholds(player, progress);
		progress.save(player);
	}

	/** Built a word that was not the one asked for: the streak starts over. */
	public static void missed(ServerPlayer player) {
		Progress progress = Progress.of(player);
		progress.streak = 0;
		progress.save(player);
	}

	/**
	 * Whether a band has paid: its own code, or, from a save before bands, its whole stage (kept as
	 * the plain stage number, which no band code is).
	 */
	private static boolean paid(Progress progress, justfatlard.phonics.puzzle.Stages.Band band) {
		return progress.stagesPaid.contains(band.code()) || band.stage() > 0 && progress.stagesPaid.contains(band.stage());
	}

	/** An event's points and prizes; true when it has paid, or had nothing to pay. */
	private static boolean award(ServerPlayer player, Progress progress, String why, PrizeConfig.Event event) {
		if (!event.enabled()) return false;
		boolean paid = event.prizes().isEmpty() || pay(player, progress, why, event.prizes());
		// Points come with a payout that happened, not one that failed.
		if (paid) progress.points += event.points();
		progress.save(player);
		return paid;
	}

	/** Every threshold reached and not yet paid, lowest first, until the cap says stop. */
	private static void payThresholds(ServerPlayer player, Progress progress) {
		PrizeConfig config = PrizeConfig.get();
		if (!config.pointsEnabled) return;
		for (PrizeConfig.Mark mark : config.thresholds) {
			if (progress.points < mark.at() || progress.thresholdsPaid.contains(mark.at())) continue;
			if (!pay(player, progress, "points " + mark.at(), mark.prizes())) break;
			progress.thresholdsPaid.add(mark.at());
		}
		progress.save(player);
	}

	/**
	 * Put a payout aside in the child's visit, or owe it if today's cap is spent. False only when
	 * there is no visit to earn it in, which the room never allows.
	 */
	private static boolean pay(ServerPlayer player, Progress progress, String why, List<Prize> prizes) {
		PrizeConfig config = PrizeConfig.get();
		progress.rollDay();
		Visit visit = Visit.of(player);
		if (visit == null) {
			Phonics.LOGGER.warn("[{}] {} earned {} outside a visit; not paid", Phonics.MOD_ID, player.getGameProfile().name(), why);
			return false;
		}
		List<ItemStack> items = new ArrayList<>();
		int[] experience = new int[1];
		for (Prize prize : prizes) prize.resolve(player, items, experience);
		if (config.dailyCap > 0 && progress.paidToday >= config.dailyCap) {
			progress.owed.add(new Progress.Owed(why, List.copyOf(items), experience[0]));
			progress.save(player);
			Phonics.LOGGER.info("[{}] {} has had {} payouts today; {} is owed for a later day", Phonics.MOD_ID,
				player.getGameProfile().name(), progress.paidToday, why);
			return true;
		}
		Visit.set(player, visit.withPrizes(items, experience[0]));
		progress.paidToday++;
		progress.save(player);
		Phonics.LOGGER.info("[{}] {} earned {}: {} stacks, {} xp", Phonics.MOD_ID, player.getGameProfile().name(), why,
			items.size(), experience[0]);
		Rooms.prizeAdded(player, items, experience[0]);
		return true;
	}
}
