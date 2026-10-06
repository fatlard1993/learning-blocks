package justfatlard.learning_blocks.reward;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A child's reading so far, for good: every word they have ever read, their streak, their points,
 * and what has already been paid, so nothing pays twice.
 *
 * <p>Kept apart from the {@code Visit}, which ends on the way out; this outlives every visit, and
 * every death. Mutable, and written back with {@link #save} after each change.
 */
public final class Progress {

	/**
	 * A payout the daily cap held back: what it would have given, resolved when it was earned, so
	 * what arrives later is exactly what was earned.
	 */
	public record Owed(String why, List<ItemStack> items, int xp) {
		static final Codec<Owed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("why").forGetter(Owed::why),
			justfatlard.learning_blocks.classroom.LenientItems.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Owed::items),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(Owed::xp)
		).apply(instance, Owed::new));
	}

	/**
	 * Word ids read, in the order they were first read: what the stages and My words go by.
	 * An operator setting a child back a stage takes words out of this, so they are read again.
	 */
	final Set<String> words;
	/**
	 * Word ids that have ever paid "new word". Never taken out of: a word read again after a
	 * stage reset is not new, and pays nothing twice.
	 */
	final Set<String> rewarded;
	/** The lowest stage this child may be at: 0 (letters first) for a new reader, 1 for one from before letters, or what an operator set. */
	int stageFloor;
	/** A band an operator put this child at, {@link justfatlard.learning_blocks.puzzle.Stages.BandCode#packed}: nothing below it is asked. */
	int bandFloor;
	final java.util.Map<String, Integer> letterHits;
	/**
	 * Lessons still owed from a visit something else ended - another mod's teleport - kept here
	 * because the visit is gone, and taken back into the classroom at the next login.
	 */
	int lessonsOwed;
	int streak;
	int points;
	final Set<Integer> thresholdsPaid;
	final Set<Integer> milestonesPaid;
	final Set<Integer> stagesPaid;
	String day;
	int paidToday;
	String lastVisit;
	final Memory memory;
	final List<Owed> owed;
	/**
	 * What was saved, when it could not be read: kept, and written back exactly as it was, so a
	 * child's history is never replaced with an empty one. While set, nothing new is saved over it.
	 */
	private net.minecraft.nbt.@org.jspecify.annotations.Nullable Tag broken;

	private Progress(List<String> words, int streak, int points, List<Integer> thresholdsPaid, List<Integer> milestonesPaid,
			List<Integer> stagesPaid, String day, int paidToday, String lastVisit, List<Owed> owed,
			List<String> rewarded, int stageFloor, int lessonsOwed, java.util.Map<String, Integer> letterHits, Memory memory, int bandFloor) {
		this.memory = memory;
		this.bandFloor = Math.max(0, bandFloor);
		this.words = new LinkedHashSet<>(words);
		// Saved before the two were kept apart: everything read then had paid, or could have.
		this.rewarded = new LinkedHashSet<>(rewarded.isEmpty() ? words : rewarded);
		this.stageFloor = Math.max(0, stageFloor);
		this.letterHits = new java.util.TreeMap<>(letterHits);
		this.lessonsOwed = Math.max(0, lessonsOwed);
		this.streak = streak;
		this.points = points;
		this.thresholdsPaid = new TreeSet<>(thresholdsPaid);
		this.milestonesPaid = new TreeSet<>(milestonesPaid);
		this.stagesPaid = new TreeSet<>(stagesPaid);
		this.day = day;
		this.paidToday = paidToday;
		this.lastVisit = lastVisit;
		this.owed = new ArrayList<>(owed);
	}

	private static Progress fresh() {
		return new Progress(List.of(), 0, 0, List.of(), List.of(), List.of(), "", 0, "", List.of(), List.of(),
			startsOnLetters() ? 0 : 1, 0, java.util.Map.of(), Memory.fresh(), 0);
	}

	/**
	 * Which subjects this child has on (where a grown-up has said, over the server's defaults), and
	 * the rounds of each solved today, for a daily schedule that asks for some of each.
	 */
	public static final class SubjectState {
		final java.util.Map<String, Boolean> on;
		String day;
		final java.util.Map<String, Integer> done;

		SubjectState(java.util.Map<String, Boolean> on, String day, java.util.Map<String, Integer> done) {
			this.on = new java.util.TreeMap<>(on);
			this.day = day;
			this.done = new java.util.TreeMap<>(done);
		}

		static SubjectState fresh() {
			return new SubjectState(java.util.Map.of(), "", java.util.Map.of());
		}

		static final Codec<SubjectState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("on", java.util.Map.of()).forGetter(s -> java.util.Map.copyOf(s.on)),
			Codec.STRING.optionalFieldOf("day", "").forGetter(s -> s.day),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("done", java.util.Map.of()).forGetter(s -> java.util.Map.copyOf(s.done))
		).apply(instance, SubjectState::new));
	}

	SubjectState subjects = SubjectState.fresh();

	private static final com.mojang.serialization.MapCodec<Progress> FIELDS = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.STRING.listOf().optionalFieldOf("words", List.of()).forGetter(p -> new ArrayList<>(p.words)),
		Codec.INT.optionalFieldOf("streak", 0).forGetter(p -> p.streak),
		Codec.INT.optionalFieldOf("points", 0).forGetter(p -> p.points),
		Codec.INT.listOf().optionalFieldOf("thresholds_paid", List.of()).forGetter(p -> new ArrayList<>(p.thresholdsPaid)),
		Codec.INT.listOf().optionalFieldOf("milestones_paid", List.of()).forGetter(p -> new ArrayList<>(p.milestonesPaid)),
		Codec.INT.listOf().optionalFieldOf("stages_paid", List.of()).forGetter(p -> new ArrayList<>(p.stagesPaid)),
		Codec.STRING.optionalFieldOf("day", "").forGetter(p -> p.day),
		Codec.INT.optionalFieldOf("paid_today", 0).forGetter(p -> p.paidToday),
		Codec.STRING.optionalFieldOf("last_visit", "").forGetter(p -> p.lastVisit),
		// "book_words" and "has_book" in older saves belonged to the word book, which is gone: ignored.
		Owed.CODEC.listOf().optionalFieldOf("owed", List.of()).forGetter(p -> List.copyOf(p.owed)),
		Codec.STRING.listOf().optionalFieldOf("rewarded", List.of()).forGetter(p -> new ArrayList<>(p.rewarded)),
		Codec.INT.optionalFieldOf("stage_floor", 1).forGetter(p -> p.stageFloor),
		Codec.INT.optionalFieldOf("lessons_owed", 0).forGetter(p -> p.lessonsOwed),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("letter_hits", java.util.Map.of()).forGetter(p -> java.util.Map.copyOf(p.letterHits)),
		// Not optionalFieldOf("memory", Memory.legacy()): that one default would be shared, and changed, by every save without memory.
		Memory.CODEC.optionalFieldOf("memory").forGetter(p -> java.util.Optional.of(p.memory)),
		Codec.INT.optionalFieldOf("band_floor", 0).forGetter(p -> p.bandFloor)
	).apply(instance, (words, streak, points, thresholds, milestones, stages, day, paidToday, lastVisit, owed, rewarded, stageFloor,
		lessonsOwed, letterHits, memory, bandFloor) -> new Progress(words, streak, points, thresholds, milestones, stages, day, paidToday,
		lastVisit, owed, rewarded, stageFloor, lessonsOwed, letterHits, memory.orElseGet(Memory::legacy), bandFloor)));

	public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		FIELDS.forGetter(p -> p),
		SubjectState.CODEC.optionalFieldOf("subjects").forGetter(p -> java.util.Optional.of(p.subjects))
	).apply(instance, (progress, subjects) -> {
		progress.subjects = subjects.orElseGet(SubjectState::fresh);
		return progress;
	}));

	/** As saved: a record that cannot be read is kept as it was, never replaced. */
	public static final Codec<Progress> SAVED = justfatlard.learning_blocks.classroom.LenientItems.keeping(CODEC, "A child's reading progress", raw -> {
		Progress kept = fresh();
		kept.broken = raw;
		return kept;
	}, progress -> progress.broken);

	private static final AttachmentType<Progress> ATTACHMENT = AttachmentRegistry.<Progress>builder()
		.persistent(SAVED)
		.copyOnDeath()
		.buildAndRegister(LearningBlocks.id("progress"));

	/** Loads the class, which is what registers the attachment; it must happen at init. */
	public static void init() {}

	/**
	 * This child's progress. Absent - a reader never seen - is a fresh start. Present but unreadable
	 * is not absent: it is kept as saved, played from a fresh start for now, and never saved over.
	 */
	public static Progress of(ServerPlayer player) {
		// A practice round (the grown-ups' sampler): a copy that everything may change and nothing keeps.
		Progress copy = practice.get(player.getUUID());
		if (copy != null) return copy;
		Progress progress = player.getAttached(ATTACHMENT);
		if (progress == null) {
			progress = fresh();
			player.setAttached(ATTACHMENT, progress);
		}
		if (progress.broken == null && !progress.memory.casesChecked) {
			// Capitals came in after this reader: one who had every sound already keeps the letters level done.
			progress.memory.casesChecked = true;
			Progress reader = progress;
			progress.memory.casesWaived = justfatlard.learning_blocks.puzzle.Letters.all().stream()
				.allMatch(letter -> justfatlard.learning_blocks.puzzle.Letters.known(reader, letter));
			progress.save(player);
		}
		if (progress.broken == null && !progress.memory.gated && !justfatlard.learning_blocks.puzzle.Words.all().isEmpty()) {
			progress.gate();
			progress.save(player);
		}
		if (progress.broken != null && !progress.warned) {
			progress.warned = true;
			LearningBlocks.LOGGER.error("[{}] {}'s saved progress could not be read. It is kept untouched; this session starts fresh and "
				+ "saves nothing over it. Fix or remove the mod whose data it holds, and it will read again.", LearningBlocks.MOD_ID,
				player.getGameProfile().name());
		}
		return progress;
	}

	private boolean warned;

	public boolean unreadable() {
		return broken != null;
	}

	public void save(ServerPlayer player) {
		if (practice.get(player.getUUID()) == this) return;
		player.setAttached(ATTACHMENT, this);
	}

	private static final java.util.Map<java.util.UUID, Progress> practice = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * A practice round starts (or ends) for this reader: from here until it ends, {@link #of} is a
	 * copy of their progress, so whatever the round does to it is forgotten, and their own is never
	 * changed or saved.
	 */
	public static void practice(ServerPlayer player, boolean on) {
		if (!on) {
			practice.remove(player.getUUID());
			return;
		}
		if (practice.containsKey(player.getUUID())) return;
		Progress real = of(player);
		Progress copy = CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, real).flatMap(tag -> CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag))
			.result().orElse(null);
		if (copy != null) practice.put(player.getUUID(), copy);
	}

	public static boolean practicing(ServerPlayer player) {
		return practice.containsKey(player.getUUID());
	}

	public static void forgetPractice() {
		practice.clear();
	}

	public static net.minecraft.nbt.Tag savedForTests(ServerPlayer player) {
		Progress real = player.getAttached(ATTACHMENT);
		return real == null ? net.minecraft.nbt.EndTag.INSTANCE : CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, real).getOrThrow();
	}

	public Memory memory() {
		return memory;
	}

	/** Whether big-and-little pairs are waived for the letters level: a reader who had finished it before they came in. */
	public boolean casesWaived() {
		return memory.casesWaived;
	}

	public void caseAnswered(ServerPlayer player, justfatlard.learning_blocks.puzzle.Letter letter, boolean right, boolean firstTry) {
		String key = justfatlard.learning_blocks.puzzle.Letters.caseKey(letter);
		if (right && firstTry) hit(key);
		if (right) memory.right(key, firstTry, today());
		else memory.missed(key, null, masteredAt());
		save(player);
	}

	public void setCaseHitsForTests(ServerPlayer player, justfatlard.learning_blocks.puzzle.Letter letter, int hits) {
		letterHits.put(justfatlard.learning_blocks.puzzle.Letters.caseKey(letter), hits);
		save(player);
	}

	public static int masteredAt() {
		return Math.max(1, PrizeConfig.get().masteryClean);
	}

	public int clean(justfatlard.learning_blocks.puzzle.Word word) {
		return memory.clean(Memory.wordKey(word.id().toString()));
	}

	public boolean mastered(justfatlard.learning_blocks.puzzle.Word word) {
		return clean(word) >= masteredAt();
	}

	public Set<String> masteredIds() {
		Set<String> ids = new LinkedHashSet<>();
		int at = masteredAt();
		for (java.util.Map.Entry<String, Memory.Item> entry : memory.items.entrySet()) {
			if (entry.getKey().startsWith("word:") && entry.getValue().firstTry >= at) ids.add(entry.getKey().substring(5));
		}
		return ids;
	}

	public void masterForTests(ServerPlayer player, justfatlard.learning_blocks.puzzle.Word word) {
		words.add(word.id().toString());
		rewarded.add(word.id().toString());
		memory.master(Memory.wordKey(word.id().toString()), masteredAt());
		save(player);
	}

	public void setCleanForTests(ServerPlayer player, justfatlard.learning_blocks.puzzle.Word word, int clean) {
		words.add(word.id().toString());
		rewarded.add(word.id().toString());
		memory.setClean(Memory.wordKey(word.id().toString()), clean);
		save(player);
	}

	public void solvedToday(ServerPlayer player) {
		memory.solved(today());
		save(player);
	}

	public int doneToday() {
		return memory.solvedOn(today());
	}

	public void setDoneTodayForTests(ServerPlayer player, int done) {
		memory.setSolved(today(), done);
		save(player);
	}

	/**
	 * A reader from before mastery gates: the stage they had reached by words read is kept as their
	 * floor, so nobody is sent back on update; the stages before it count as done. Once.
	 */
	void gate() {
		if (memory.gated) return;
		memory.gated = true;
		if (stageFloor == 0 && startsOnLetters()) return;
		int reached = -1;
		java.util.List<Integer> all = justfatlard.learning_blocks.puzzle.Stages.all();
		for (int stage : all) {
			if (stage < stageFloor) continue;
			boolean done = justfatlard.learning_blocks.puzzle.Stages.at(stage).stream().allMatch(w -> words.contains(w.id().toString()));
			if (!done) {
				reached = stage;
				break;
			}
		}
		// Everything read: past the last stage, where everything is open.
		if (reached < 0) reached = all.isEmpty() ? stageFloor : all.getLast() + 1;
		if (reached > stageFloor) {
			LearningBlocks.LOGGER.info("[{}] A reader from before mastery gates keeps stage {}", LearningBlocks.MOD_ID, reached);
			stageFloor = reached;
		}
	}

	public static void setForTests(ServerPlayer player, Progress progress) {
		player.setAttached(ATTACHMENT, progress);
	}

	/**
	 * A word read right: the word, and the tiles the child chose (the gaps), each up a box if first try.
	 */
	public void wordRight(ServerPlayer player, justfatlard.learning_blocks.puzzle.Word word, boolean firstTry, java.util.List<Integer> chosen) {
		memory.right(Memory.wordKey(word.id().toString()), firstTry, today());
		for (int i : chosen) if (i < word.tiles().size()) memory.right(Memory.tileKey(word.tiles().get(i)), firstTry, today());
		save(player);
	}

	/**
	 * A word built wrong: {@code built} instead of the target. The word goes back to box 1 with what
	 * was built, and every tile that differs is a mix-up of that sound with the one put in its place.
	 */
	public void wordMissed(ServerPlayer player, justfatlard.learning_blocks.puzzle.Word target, java.util.List<String> built) {
		memory.missed(Memory.wordKey(target.id().toString()), String.join("", built), masteredAt());
		List<String> tiles = target.tiles();
		if (tiles.size() == built.size()) {
			for (int i = 0; i < tiles.size(); i++) {
				if (tiles.get(i).equals(built.get(i))) continue;
				memory.missed(Memory.tileKey(tiles.get(i)), built.get(i), masteredAt());
				memory.mixedUp(tiles.get(i), built.get(i));
			}
		}
		save(player);
	}

	public void letterAnswered(ServerPlayer player, String target, String picked, boolean firstTry) {
		if (target.equals(picked)) memory.right(Memory.tileKey(target), firstTry, today());
		else {
			memory.missed(Memory.tileKey(target), picked, masteredAt());
			memory.mixedUp(target, picked);
		}
		save(player);
	}

	public void roundBegan(ServerPlayer player) {
		memory.round(today());
		save(player);
	}

	public void timeSpent(ServerPlayer player, int seconds) {
		memory.time(today(), seconds);
		save(player);
	}

	public void lessonNote(ServerPlayer player, String what) {
		memory.lesson(today() + ": " + what);
		save(player);
	}

	public Set<String> words() {
		return Set.copyOf(words);
	}

	public int lessonsOwed() {
		return lessonsOwed;
	}

	public void setLessonsOwed(ServerPlayer player, int lessons) {
		lessonsOwed = Math.max(0, lessons);
		save(player);
	}

	public int stageFloor() {
		return stageFloor;
	}

	public boolean paid(justfatlard.learning_blocks.puzzle.Stages.BandCode band) {
		return stagesPaid.contains(band.packed());
	}

	public void markPaid(justfatlard.learning_blocks.puzzle.Stages.BandCode band) {
		stagesPaid.add(band.packed());
	}

	public justfatlard.learning_blocks.puzzle.Stages.BandCode bandFloor() {
		return justfatlard.learning_blocks.puzzle.Stages.BandCode.unpacked(bandFloor);
	}

	public void setBand(ServerPlayer player, justfatlard.learning_blocks.puzzle.Stages.Band band) {
		setStage(player, band.stage(), justfatlard.learning_blocks.puzzle.Stages.from(band));
		bandFloor = band.code().packed();
		save(player);
	}

	/**
	 * Whether a reader seen for the first time starts on letters: the setting, unless a test has
	 * said otherwise. Readers from before letters have a saved floor of 1 and stay where they are.
	 */
	public static boolean startsOnLetters() {
		return lettersFirstOverride != null ? lettersFirstOverride : PrizeConfig.get().lettersFirst;
	}

	private static @org.jspecify.annotations.Nullable Boolean lettersFirstOverride;

	public static void lettersFirstForTests(@org.jspecify.annotations.Nullable Boolean first) {
		lettersFirstOverride = first;
	}

	public int letterHits(String grapheme) {
		return letterHits.getOrDefault(grapheme, 0);
	}

	public void hitLetter(ServerPlayer player, String grapheme) {
		hit(grapheme);
		save(player);
	}

	void hit(String key) {
		if (memory.count("hit:" + key, today())) letterHits.merge(key, 1, Integer::sum);
	}

	public boolean hitCountsToday(String key) {
		return memory.countsToday("hit:" + key, today());
	}

	public boolean cleanCountsToday(justfatlard.learning_blocks.puzzle.Word word) {
		return memory.countsToday(Memory.wordKey(word.id().toString()), today());
	}

	public java.util.Map<justfatlard.learning_blocks.plush.Plush, Integer> plushies() {
		java.util.Map<justfatlard.learning_blocks.plush.Plush, Integer> owned = new java.util.EnumMap<>(justfatlard.learning_blocks.plush.Plush.class);
		for (var entry : memory.plushies.entrySet()) {
			justfatlard.learning_blocks.plush.Plush plush = justfatlard.learning_blocks.plush.Plush.of(entry.getKey());
			if (plush != null && entry.getValue() > 0) owned.put(plush, entry.getValue());
		}
		return owned;
	}

	public void ownPlushie(ServerPlayer player, justfatlard.learning_blocks.plush.Plush plush) {
		memory.plushies.merge(plush.word(), 1, Integer::sum);
		save(player);
	}

	/**
	 * Which plushie a prize gives: the one it names, or for {@code random_new} one this reader
	 * hasn't got (any, once they have them all).
	 */
	public justfatlard.learning_blocks.plush.@org.jspecify.annotations.Nullable Plush choosePlushie(String which, net.minecraft.util.RandomSource random) {
		justfatlard.learning_blocks.plush.Plush named = justfatlard.learning_blocks.plush.Plush.of(which);
		if (named != null) return named;
		java.util.List<justfatlard.learning_blocks.plush.Plush> missing = new java.util.ArrayList<>();
		for (justfatlard.learning_blocks.plush.Plush plush : justfatlard.learning_blocks.plush.Plush.values()) {
			if (memory.plushies.getOrDefault(plush.word(), 0) == 0) missing.add(plush);
		}
		java.util.List<justfatlard.learning_blocks.plush.Plush> from = missing.isEmpty() ? java.util.List.of(justfatlard.learning_blocks.plush.Plush.values()) : missing;
		return from.get(random.nextInt(from.size()));
	}

	public java.util.Map<justfatlard.learning_blocks.toy.Toy, Integer> toys() {
		java.util.Map<justfatlard.learning_blocks.toy.Toy, Integer> owned = new java.util.EnumMap<>(justfatlard.learning_blocks.toy.Toy.class);
		for (var entry : memory.plushies.entrySet()) {
			if (!entry.getKey().startsWith(TOY)) continue;
			justfatlard.learning_blocks.toy.Toy toy = justfatlard.learning_blocks.toy.Toy.of(entry.getKey().substring(TOY.length()));
			if (toy != null && entry.getValue() > 0) owned.put(toy, entry.getValue());
		}
		return owned;
	}

	private static final String TOY = "toy:";

	public void ownToy(ServerPlayer player, justfatlard.learning_blocks.toy.Toy toy) {
		memory.plushies.merge(TOY + toy.word(), 1, Integer::sum);
		save(player);
	}

	public justfatlard.learning_blocks.toy.@org.jspecify.annotations.Nullable Toy chooseToy(String which, net.minecraft.util.RandomSource random) {
		justfatlard.learning_blocks.toy.Toy named = justfatlard.learning_blocks.toy.Toy.of(which);
		if (named != null) return named;
		java.util.List<justfatlard.learning_blocks.toy.Toy> missing = new java.util.ArrayList<>();
		for (justfatlard.learning_blocks.toy.Toy toy : justfatlard.learning_blocks.toy.Toy.values()) {
			if (memory.plushies.getOrDefault(TOY + toy.word(), 0) == 0) missing.add(toy);
		}
		java.util.List<justfatlard.learning_blocks.toy.Toy> from = missing.isEmpty() ? java.util.List.of(justfatlard.learning_blocks.toy.Toy.values()) : missing;
		return from.get(random.nextInt(from.size()));
	}

	/**
	 * A {@code toybox} prize: a plushie or a toy this reader hasn't got, every missing one across both
	 * shelves equally likely; once both shelves are full, any of either. True for a toy.
	 */
	public boolean toyboxPicksToy(net.minecraft.util.RandomSource random) {
		int plushiesMissing = justfatlard.learning_blocks.plush.Plush.values().length - plushies().size();
		int toysMissing = justfatlard.learning_blocks.toy.Toy.values().length - toys().size();
		if (plushiesMissing + toysMissing == 0) {
			plushiesMissing = justfatlard.learning_blocks.plush.Plush.values().length;
			toysMissing = justfatlard.learning_blocks.toy.Toy.values().length;
		}
		return random.nextInt(plushiesMissing + toysMissing) >= plushiesMissing;
	}

	public boolean rewardedFor(String key) {
		return rewarded.contains(key);
	}


	public int itemClean(String key) {
		return memory.clean(key);
	}

	public boolean itemMastered(String key) {
		return itemClean(key) >= masteredAt();
	}

	public boolean itemCountsToday(String key) {
		return memory.countsToday(key, today());
	}

	public void itemRight(ServerPlayer player, String key, boolean firstTry) {
		memory.right(key, firstTry, today());
		save(player);
	}

	public void itemMissed(ServerPlayer player, String key, String picked) {
		memory.missed(key, picked, masteredAt());
		save(player);
	}

	public boolean itemDue(String key) {
		return memory.due(key);
	}

	public void useItemToday(ServerPlayer player, String key, String kind) {
		memory.usedOn(today()).add(key + "|" + kind);
		save(player);
	}

	public boolean itemUsedToday(String key) {
		String prefix = key + "|";
		return !repeatsForTests && memory.usedOn(today()).stream().anyMatch(used -> used.startsWith(prefix));
	}

	public void setItemCleanForTests(ServerPlayer player, String key, int clean) {
		memory.setClean(key, clean);
		save(player);
	}

	public java.lang.@org.jspecify.annotations.Nullable Boolean subjectOn(String subject) {
		return subjects.on.get(subject);
	}

	public void setSubjectOn(ServerPlayer player, String subject, java.lang.@org.jspecify.annotations.Nullable Boolean on) {
		if (on == null) subjects.on.remove(subject);
		else subjects.on.put(subject, on);
		save(player);
	}

	public void solvedToday(ServerPlayer player, String subject) {
		rollSubjectDay();
		subjects.done.merge(subject, 1, Integer::sum);
		solvedToday(player);
	}

	public int doneToday(String subject) {
		rollSubjectDay();
		return subjects.done.getOrDefault(subject, 0);
	}

	private void rollSubjectDay() {
		if (subjects.day.equals(today())) return;
		subjects.day = today();
		subjects.done.clear();
	}

	public static String sentenceKey(String goal) {
		return "sentence:" + goal;
	}

	public int sentenceClean(String goal) {
		return memory.clean(sentenceKey(goal));
	}

	public boolean sentenceMastered(String goal) {
		return sentenceClean(goal) >= masteredAt();
	}

	public boolean sentenceCountsToday(String goal) {
		return memory.countsToday(sentenceKey(goal), today());
	}

	public void sentenceRight(ServerPlayer player, String goal, boolean firstTry) {
		memory.right(sentenceKey(goal), firstTry, today());
		save(player);
	}

	public void sentenceMissed(ServerPlayer player, String goal, String built) {
		memory.missed(sentenceKey(goal), built, masteredAt());
		save(player);
	}

	public void useSentenceToday(ServerPlayer player, String goal, String kind) {
		memory.usedOn(today()).add(sentenceKey(goal) + "|" + kind);
		save(player);
	}

	public boolean sentenceUsedToday(String goal) {
		String prefix = sentenceKey(goal) + "|";
		return !repeatsForTests && memory.usedOn(today()).stream().anyMatch(used -> used.startsWith(prefix));
	}

	public void setSentenceCleanForTests(ServerPlayer player, String goal, int clean) {
		memory.setClean(sentenceKey(goal), clean);
		save(player);
	}

	public java.util.Set<String> usedToday() {
		return java.util.Collections.unmodifiableSet(memory.usedOn(today()));
	}

	public boolean wordUsedToday(justfatlard.learning_blocks.puzzle.Word word) {
		if (repeatsForTests) return false;
		String prefix = word.id() + "|";
		return memory.usedOn(today()).stream().anyMatch(lesson -> lesson.startsWith(prefix));
	}

	public boolean lessonUsedToday(justfatlard.learning_blocks.puzzle.Word word, String kind) {
		if (repeatsForTests) return false;
		return memory.usedOn(today()).contains(word.id() + "|" + kind);
	}

	private static volatile boolean repeatsForTests;

	/** For tests: a word may come round again the same day, as before sessions; most tests ask more than a band has. */
	public static void repeatsForTests(boolean allowed) {
		repeatsForTests = allowed;
	}

	public void useToday(ServerPlayer player, justfatlard.learning_blocks.puzzle.Word word, String kind) {
		memory.usedOn(today()).add(word.id() + "|" + kind);
		save(player);
	}

	public void setLetterHits(ServerPlayer player, String grapheme, int hits) {
		letterHits.put(grapheme, hits);
		save(player);
	}

	/**
	 * Put this child at a stage: nothing below it is asked of them, and every word from it up is
	 * forgotten as read, so the stage is played again rather than skipped as already done. What
	 * those words paid stays paid.
	 */
	public void setStage(ServerPlayer player, int stage, Set<String> forget) {
		stageFloor = Math.max(0, stage);
		bandFloor = 0;
		// Back to the letters: they are learned again from the start.
		if (stage == 0) letterHits.clear();
		words.removeAll(forget);
		for (String id : forget) memory.forget(Memory.wordKey(id));
		streak = 0;
		save(player);
	}

	public boolean read(ServerPlayer player, String id) {
		boolean added = words.add(id);
		save(player);
		return added;
	}

	public List<String> wordsInOrder() {
		return List.copyOf(words);
	}

	public int streak() {
		return streak;
	}

	public int points() {
		return points;
	}

	public List<Owed> owed() {
		return List.copyOf(owed);
	}

	public int paidToday() {
		rollDay();
		return paidToday;
	}

	private static @org.jspecify.annotations.Nullable Supplier<LocalDate> clock;
	private static Supplier<java.time.Instant> now = java.time.Instant::now;

	public static void useClock(@org.jspecify.annotations.Nullable Supplier<LocalDate> date) {
		clock = date;
	}

	public static void useInstant(Supplier<java.time.Instant> instant) {
		now = instant;
		clock = null;
	}

	/** Today's date, in the schedules' time zone: what "a day" means for the cap, daily visits and schedules. */
	public static LocalDate date() {
		return clock != null ? clock.get() : LocalDate.ofInstant(now.get(), justfatlard.learning_blocks.classroom.Schedules.zone());
	}

	public static String today() {
		return date().toString();
	}

	void rollDay() {
		String now = today();
		if (!now.equals(day)) {
			day = now;
			paidToday = 0;
		}
	}
}
