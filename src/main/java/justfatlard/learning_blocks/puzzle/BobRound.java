package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.bob.BobScene;
import justfatlard.learning_blocks.bob.Goal;
import justfatlard.learning_blocks.bob.Goals;
import justfatlard.learning_blocks.bob.Instruction;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Prizes;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A round with Bob, the very literal helper. Word cards on the floor, a sentence row (or two) on
 * the board; a card clicked goes into the row, clicked again comes home. The button reads the
 * sentence back a word at a time, and Bob does exactly what it says - a box put in a cat goes on
 * the cat's head - on his stage in front of the board, with the goal shown framed to the left.
 *
 * <p>Three kinds. Tell Bob: make the goal. What's wrong: the sentence is already there and Bob has
 * already done it, wrong; change the card that is wrong. Silly sentences: any sentence at all, and
 * Bob does it, one after another, for fun. Reading together, Tell Bob is two readers': one picks
 * which of two goals Bob should make, and the other tells him.
 */
public final class BobRound {
	public enum Mode { GOAL, FIX, SILLY, PREP }

	/** How long Bob takes over each sentence. */
	private static final int ACT_TICKS = 24;
	private static final float CARD_SIZE = 1.25F;
	/**
	 * The sentence row's cards: as big as the floor's look from where the child stands, which is
	 * twice as far away, so about twice the size. A row too long for the board wraps.
	 */
	static final float ROW_SIZE = 2.8F;
	/** A text display's letter, at size 1, about this wide (bold): a pixel is a fortieth of a block. */
	private static final double LETTER = 5.6 / 40;
	private static final double ROW_PAD = 0.3;
	private static final double ROW_GAP = 0.3;
	/** The widest a line of the row may be: clear of the button on the right. */
	private static final double LINE_WIDTH = 7.4;
	private static final double LINE_HEIGHT = 0.95;
	/** An empty slot is as wide as a three-letter word. */
	private static final int BLANK_LETTERS = 3;
	private static final double CARD_SPACING = 1.6;
	private static final int PER_FLOOR_ROW = 6;
	private static final int SILLY_SLOTS = 8;
	private static final int CARD = 0xFFF2E8C9;
	private static final int CARD_LIT = 0xFFFFE45E;
	private static final int INK = 0x1E2A5A;
	/** The Silly sentences prompt and others not yet recorded: their captions, for until they are. */
	static final String TELL_BOB = "phrase/tell_bob";
	static final String PICK_FOR_BOB = "phrase/pick_for_bob";
	static final String SILLY = "phrase/silly_sentences";

	/** One word card: where it lives on the floor, and where it is now (a row and slot, or home). */
	static final class Card {
		final String word;
		final Display.TextDisplay display;
		final Interaction hit;
		final Vec3 home;
		int row = -1;
		int slot = -1;

		Card(String word, Display.TextDisplay display, Interaction hit, Vec3 home) {
			this.word = word;
			this.display = display;
			this.hit = hit;
			this.home = home;
		}

		boolean placed() {
			return row >= 0;
		}
	}

	private final Room room;
	private final ServerLevel level;
	private final RoomLayout layout;
	final Mode mode;
	/** What Bob is to make: in a together round, the one picked once it is. */
	@Nullable Goal goal;
	/** Reading together: the two goals the other reader picks between, until they pick. */
	final List<Goal> offered = new ArrayList<>();
	final Goals.@Nullable Wrong wrong;
	final List<Card> cards = new ArrayList<>();
	/** Each row's slots: the card in it, or null. */
	final List<Card[]> rows = new ArrayList<>();
	private final List<Display.TextDisplay> blanks = new ArrayList<>();
	private final List<Entity> extras = new ArrayList<>();
	private final List<Interaction> pickHits = new ArrayList<>();
	final BobStage stage;
	final List<BobStage> pictures = new ArrayList<>();
	/**
	 * A preposition round: the words on the board ("owl in box"), and two pictures of the same
	 * two things, one in and one on (or under); which picture is the words.
	 */
	final List<String> prepWords = new ArrayList<>();
	final List<BobScene> prepScenes = new ArrayList<>();
	int prepRight = -1;
	/** What is on Bob's stage now. */
	BobScene acted;
	@Nullable Villager bob;
	/** Reading together: who builds, and who picks the goal; null alone. */
	@Nullable UUID builder;
	@Nullable UUID picker;
	/** Reading together, the goal not yet picked. */
	boolean picking;

	private BobRound(Room room, ServerLevel level, RoomLayout layout, Mode mode, @Nullable Goal goal, Goals.@Nullable Wrong wrong) {
		this.room = room;
		this.level = level;
		this.layout = layout;
		this.mode = mode;
		this.goal = goal;
		this.wrong = wrong;
		this.stage = new BobStage(level, stageSpot(), 1.0F);
		this.acted = goal != null ? goal.start() : new BobScene(List.of());
	}

	// --- Where things are ---

	private Vec3 stageSpot() {
		return Vec3.atBottomCenterOf(layout.origin()).add(1.0, 0, -2.4);
	}

	private Vec3 pictureSpot(int which) {
		return Vec3.atBottomCenterOf(layout.origin()).add(which == 0 ? -3.4 : 1.2, 0, -2.4);
	}

	private Vec3 bobSpot() {
		return Vec3.atBottomCenterOf(layout.origin()).add(4.3, 0, -2.6);
	}

	/** How wide a card for this word is in the row, or an empty slot. */
	private static double rowWidth(@Nullable String word) {
		return (word == null ? BLANK_LETTERS : Math.max(2, word.length())) * LETTER * ROW_SIZE + ROW_PAD;
	}

	/**
	 * Where every slot of every row is, as the row now stands: the cards in it each as wide as
	 * its word, the empty slots as wide as a short one, flowing left to right like writing and
	 * wrapping onto the next line at the board's width; each sentence starts a line of its own.
	 * The lines are centred, the first near the top of the board.
	 */
	private List<Vec3[]> slotSpots() {
		List<Vec3[]> spots = new ArrayList<>();
		List<double[]> lineOf = new ArrayList<>();
		List<Double> lineWidths = new ArrayList<>();
		int line = -1;
		for (Card[] row : rows) {
			Vec3[] these = new Vec3[row.length];
			double[] at = new double[row.length * 2];
			double x = Double.MAX_VALUE;
			for (int i = 0; i < row.length; i++) {
				double width = rowWidth(row[i] == null ? null : row[i].word);
				if (x == Double.MAX_VALUE || x + width > LINE_WIDTH) {
					line++;
					lineWidths.add(0.0);
					x = 0;
				}
				at[2 * i] = line;
				at[2 * i + 1] = x + width / 2;
				x += width + ROW_GAP;
				lineWidths.set(line, x - ROW_GAP);
			}
			spots.add(these);
			lineOf.add(at);
		}
		double centre = layout.origin().getX() - 0.6;
		double top = layout.origin().getY() + 2.9;
		for (int r = 0; r < rows.size(); r++) {
			double[] at = lineOf.get(r);
			for (int i = 0; i < rows.get(r).length; i++) {
				int l = (int) at[2 * i];
				double x = centre - lineWidths.get(l) / 2 + at[2 * i + 1];
				spots.get(r)[i] = new Vec3(x, top - l * LINE_HEIGHT, layout.origin().getZ() - 3.82);
			}
		}
		return spots;
	}

	/** Everything in the row where it now belongs: after a card comes or goes, the rest close up or make room. */
	private void relayout(boolean glide) {
		List<Vec3[]> spots = slotSpots();
		int b = 0;
		for (int r = 0; r < rows.size(); r++) {
			Card[] row = rows.get(r);
			for (int i = 0; i < row.length; i++) {
				Vec3 spot = spots.get(r)[i];
				if (b < blanks.size()) {
					Display.TextDisplay blank = blanks.get(b++);
					blank.setPosRotInterpolationDuration(glide ? 5 : 0);
					blank.teleportTo(spot.x, spot.y, spot.z - 0.04);
				}
				if (row[i] != null) fly(row[i], spot, ROW_SIZE, glide);
			}
		}
	}

	/** Card {@code i} of {@code m} on the floor: a row or two between the child and Bob's stage. */
	private Vec3 floorSpot(int i, int m) {
		int rowsOf = (m + PER_FLOOR_ROW - 1) / PER_FLOOR_ROW;
		int row = i / PER_FLOOR_ROW;
		int inRow = row < rowsOf - 1 ? PER_FLOOR_ROW : m - row * PER_FLOOR_ROW;
		int at = i % PER_FLOOR_ROW;
		double x = layout.origin().getX() + 0.5 + (at - (inRow - 1) / 2.0) * CARD_SPACING;
		double z = layout.origin().getZ() + (row == 0 ? 0.0 : 1.0);
		return new Vec3(x, layout.origin().getY() + 0.3, z);
	}

	// --- Making a round ---

	/** A goal round (or a together one, picked from two), a what's-wrong round, or silly sentences. Null if none can be made. */
	static @Nullable BobRound make(Room room, ServerLevel level, RoomLayout layout, Mode mode, ServerPlayer builder, @Nullable Goal wanted,
			Goals.@Nullable Mistake mistake) {
		Progress progress = Progress.of(builder);
		java.util.Random random = new java.util.Random(level.getRandom().nextLong());
		java.util.function.Predicate<String> readable = wanted != null ? word -> true : readable(progress, room.stage);
		if (mode == Mode.SILLY) return new BobRound(room, level, layout, mode, null, null);
		if (mode == Mode.PREP) return makePrep(room, level, layout, readable, wanted, random);
		List<Goal> open = Goals.open(readable, goal -> progress.sentenceMastered(goal.key()));
		if (mode == Mode.FIX) open = open.stream().filter(goal -> goal.rows().size() == 1).toList();
		Goal goal = wanted != null ? wanted : Goals.pick(open, g -> progress.sentenceMastered(g.key()), g -> progress.sentenceUsedToday(g.key()),
			g -> progress.sentenceClean(g.key()), room.lastGoal, random);
		if (goal == null) return null;
		Goals.Wrong wrong = null;
		if (mode == Mode.FIX) {
			wrong = Goals.wrong(goal, mistake, readable, random);
			if (wrong == null) return null;
		}
		BobRound round = new BobRound(room, level, layout, mode, goal, wrong);
		// Reading together: the other reader picks which of two goals Bob should make.
		List<ServerPlayer> here = room.present();
		if (mode == Mode.GOAL && room.shared() && here.size() >= 2 && wanted == null) {
			List<Goal> others = new ArrayList<>(open);
			others.remove(goal);
			others.removeIf(other -> other.rows().size() != goal.rows().size());
			if (!others.isEmpty()) {
				round.offered.add(goal);
				round.offered.add(others.get(random.nextInt(others.size())));
				java.util.Collections.shuffle(round.offered, random);
				round.builder = builder.getUUID();
				for (ServerPlayer one : here) if (!one.getUUID().equals(builder.getUUID())) round.picker = one.getUUID();
				round.picking = true;
				round.goal = null;
			}
		}
		return round;
	}

	/**
	 * A preposition round from what this child reads: a small thing and a thing it can be in, on or
	 * under in two different ways; or the one asked for ({@code wanted}'s first sentence, the other
	 * way picked to differ). Null if no such pair is readable yet.
	 */
	private static @Nullable BobRound makePrep(Room room, ServerLevel level, RoomLayout layout, java.util.function.Predicate<String> readable,
			@Nullable Goal wanted, java.util.Random random) {
		List<String[]> options = new ArrayList<>();
		List<justfatlard.learning_blocks.relate.Thing> things = justfatlard.learning_blocks.relate.Relations.all();
		if (wanted != null) {
			Instruction step = wanted.steps().getFirst();
			for (String other : justfatlard.learning_blocks.bob.Noun.PREPOSITIONS) {
				if (!other.equals(step.prep()) && prepDiffers(step.x(), step.prep(), other, step.y())) options.add(new String[] {step.x(), step.prep(), other, step.y()});
			}
		} else {
			for (justfatlard.learning_blocks.relate.Thing x : things) {
				if (!x.small() || x.creature() || !readable.test(x.word())) continue;
				for (justfatlard.learning_blocks.relate.Thing y : things) {
					if (y == x || !readable.test(y.word())) continue;
					for (String[] pair : List.of(new String[] {"in", "on"}, new String[] {"on", "under"}, new String[] {"in", "under"})) {
						if (!readable.test(pair[0]) || !readable.test(pair[1]) || !prepDiffers(x.word(), pair[0], pair[1], y.word())) continue;
						options.add(new String[] {x.word(), pair[0], pair[1], y.word()});
						options.add(new String[] {x.word(), pair[1], pair[0], y.word()});
					}
				}
			}
		}
		if (options.isEmpty()) return null;
		String[] chosen = options.get(random.nextInt(options.size()));
		BobRound round = new BobRound(room, level, layout, Mode.PREP, null, null);
		round.prepWords.addAll(List.of(chosen[0], chosen[1], chosen[3]));
		BobScene right = new BobScene(List.of(chosen[0], chosen[3]));
		right.apply(new Instruction(chosen[0], false, chosen[1], chosen[3], false));
		BobScene other = new BobScene(List.of(chosen[0], chosen[3]));
		other.apply(new Instruction(chosen[0], false, chosen[2], chosen[3], false));
		round.prepRight = random.nextInt(2);
		round.prepScenes.add(round.prepRight == 0 ? right : other);
		round.prepScenes.add(round.prepRight == 0 ? other : right);
		return round;
	}

	/** Whether x one way y and x the other way y look different, each with x the one moved: in the box, on the box. */
	private static boolean prepDiffers(String x, String one, String other, String y) {
		var a = justfatlard.learning_blocks.relate.Relations.resolve(x, one, y, word -> null);
		var b = justfatlard.learning_blocks.relate.Relations.resolve(x, other, y, word -> null);
		return a != null && b != null && a.child().equals(x) && b.child().equals(x) && a.rel() != b.rel()
			&& a.rel() != justfatlard.learning_blocks.relate.Relations.Rel.HEAD && b.rel() != justfatlard.learning_blocks.relate.Relations.Rel.HEAD;
	}

	/** The key a preposition round is remembered by: {@code read: owl in box}. */
	String prepKey() {
		return "read: " + String.join(" ", prepWords);
	}

	/**
	 * Words a child can be given: nouns and big once read; the little words once their stage is
	 * reached (put from 5, a from 4, under from 12), or read.
	 */
	static java.util.function.Predicate<String> readable(Progress progress, int stage) {
		return word -> {
			Word found = find(word);
			if (found == null) return false;
			if (progress.words().contains(found.id().toString())) return true;
			boolean little = justfatlard.learning_blocks.bob.Noun.PREPOSITIONS.contains(word) || justfatlard.learning_blocks.bob.Noun.ARTICLES.contains(word)
				|| word.equals("put");
			return little && (stage < 0 || found.stage() <= stage);
		};
	}

	/** What this reader can be given: everything, in a grown-up's practice round. */
	private java.util.function.Predicate<String> readableFor(ServerPlayer player) {
		return room.practice ? word -> find(word) != null : readable(Progress.of(player), room.stage);
	}

	static @Nullable Word find(String text) {
		for (Word word : Words.all()) if (word.text().equals(text)) return word;
		for (Word word : Words.any()) if (word.text().equals(text)) return word;
		return null;
	}

	/** The round's stand-in puzzle, so everything that asks "is there a round?" is told yes. */
	Puzzle placeholder() {
		Word anchor = null;
		Goal shown = goal != null ? goal : offered.isEmpty() ? null : offered.getFirst();
		if (shown != null) anchor = find(shown.things().getFirst());
		if (!prepWords.isEmpty()) anchor = find(prepWords.getFirst());
		if (anchor == null) anchor = find("cat");
		if (anchor == null) anchor = Words.all().getFirst();
		return new Puzzle(anchor, List.of(anchor), List.of(), 0, Puzzle.Kind.WHOLE);
	}

	/** Lay it all out: Bob, his stage, the goal (or two to pick from), the rows and the cards. */
	void start(ServerPlayer player) {
		spawnBob();
		switch (mode) {
			case GOAL -> {
				if (picking) {
					for (int i = 0; i < offered.size(); i++) showPicture(offered.get(i), i, true);
				} else showPicture(goal, 0, false);
				Goal shape = goal != null ? goal : offered.getFirst();
				for (List<String> row : shape.rows()) addRow(row.size());
				List<String> words = new ArrayList<>(shape.cards());
				if (picking) for (String word : offered.getLast().cards()) if (!words.contains(word)) words.add(word);
				words.addAll(Goals.decoys(shape, readableFor(player), picking ? 1 : 1 + level.getRandom().nextInt(2),
					new java.util.Random(level.getRandom().nextLong())));
				dealCards(words);
				stage.show(acted, false);
			}
			case FIX -> {
				showPicture(goal, 0, false);
				List<String> row = wrong.row();
				addRow(row.size());
				List<String> words = new ArrayList<>(row);
				words.addAll(wrong.needed());
				words.addAll(Goals.decoys(goal, readableFor(player), 1, new java.util.Random(level.getRandom().nextLong())));
				dealCards(words);
				// The wrong sentence already in the row, and already done.
				for (int i = 0; i < row.size(); i++) {
					for (Card card : cards) {
						if (!card.placed() && card.word.equals(row.get(i))) {
							put(card, 0, i, false);
							break;
						}
					}
				}
				acted = goal.start();
				Instruction step = Instruction.parse(row);
				if (step != null) acted.apply(step);
				stage.show(acted, false);
			}
			case PREP -> {
				// The words on the board, read not built: cards in the row, not to be clicked.
				addRow(prepWords.size());
				dealCards(prepWords);
				for (int i = 0; i < prepWords.size(); i++) {
					for (Card card : cards) {
						if (!card.placed() && card.word.equals(prepWords.get(i))) {
							put(card, 0, i, false);
							break;
						}
					}
				}
				for (int i = 0; i < prepScenes.size(); i++) showScene(prepScenes.get(i), i, true);
			}
			case SILLY -> {
				addRow(SILLY_SLOTS);
				List<String> words = new ArrayList<>(List.of("put", "in", "on"));
				java.util.function.Predicate<String> readable = readableFor(player);
				for (String little : List.of("under", "the", "a", "big")) if (readable.test(little)) words.add(little);
				List<String> nouns = new ArrayList<>();
				for (justfatlard.learning_blocks.bob.Noun noun : justfatlard.learning_blocks.bob.Noun.all()) if (readable.test(noun.word())) nouns.add(noun.word());
				java.util.Collections.shuffle(nouns, new java.util.Random(level.getRandom().nextLong()));
				// A creature and something to put it in or on, always, then whatever else fits.
				nouns.sort(java.util.Comparator.comparingInt(word -> {
					var kind = justfatlard.learning_blocks.bob.Noun.of(word).kind();
					return kind == justfatlard.learning_blocks.bob.Noun.Kind.CREATURE ? 0 : kind == justfatlard.learning_blocks.bob.Noun.Kind.CONTAINER ? 1 : 2;
				}));
				for (String noun : nouns) if (words.size() < 12) words.add(noun);
				dealCards(words);
			}
		}
	}

	private void spawnBob() {
		Vec3 at = bobSpot();
		Vec3 toward = layout.spawn().subtract(at);
		Villager villager = BobProps.bob(level, at, (float) Math.toDegrees(Math.atan2(-toward.x, toward.z)));
		if (villager == null) return;
		villager.addTag(BobStage.TAG);
		level.addFreshEntity(villager);
		bob = villager;
		BobProps.wave(villager);
	}

	/** A goal, framed, small: what Bob is to make. Reading together, a click box over each to pick it by. */
	private void showPicture(@Nullable Goal shown, int which, boolean pickable) {
		if (shown == null) return;
		showScene(shown.end(), which, pickable);
	}

	/** A scene, framed, small, at picture spot {@code which}; pickable with a click box over it. */
	private void showScene(BobScene shown, int which, boolean pickable) {
		Vec3 spot = pictureSpot(which);
		BobStage picture = new BobStage(level, spot.add(0, 0.07, 0), 0.6F);
		picture.show(shown, false);
		pictures.add(picture);
		// The frame: a gold edge round a pale mat, so it reads as a picture of what to make.
		frame(spot, 2.6F, 1.4F);
		if (pickable) {
			Interaction hit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
			if (hit != null) {
				hit.snapTo(spot.x, spot.y, spot.z, 0, 0);
				hit.setWidth(2.4F);
				hit.setHeight(1.4F);
				hit.setResponse(true);
				hit.addTag(BobStage.TAG);
				level.addFreshEntity(hit);
				pickHits.add(hit);
			}
		}
	}

	private void frame(Vec3 centre, float wide, float deep) {
		addBlock(centre.add(-wide / 2, 0, -deep / 2), new Vector3f(wide, 0.06F, deep), Blocks.CONCRETE.white().defaultBlockState());
		float edge = 0.12F;
		addBlock(centre.add(-wide / 2 - edge, 0, -deep / 2 - edge), new Vector3f(wide + 2 * edge, 0.1F, edge), Blocks.GOLD_BLOCK.defaultBlockState());
		addBlock(centre.add(-wide / 2 - edge, 0, deep / 2), new Vector3f(wide + 2 * edge, 0.1F, edge), Blocks.GOLD_BLOCK.defaultBlockState());
		addBlock(centre.add(-wide / 2 - edge, 0, -deep / 2), new Vector3f(edge, 0.1F, deep), Blocks.GOLD_BLOCK.defaultBlockState());
		addBlock(centre.add(wide / 2, 0, -deep / 2), new Vector3f(edge, 0.1F, deep), Blocks.GOLD_BLOCK.defaultBlockState());
	}

	private void addBlock(Vec3 at, Vector3f size, net.minecraft.world.level.block.state.BlockState state) {
		Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return;
		display.snapTo(at.x, at.y, at.z, 0, 0);
		display.setBlockState(state);
		display.setTransformation(new com.mojang.math.Transformation(null, null, size, null));
		display.addTag(BobStage.TAG);
		level.addFreshEntity(display);
		extras.add(display);
	}

	private void addRow(int n) {
		int row = rows.size();
		rows.add(new Card[n]);
		for (int i = 0; i < n; i++) {
			Display.TextDisplay blank = text("___", layout.mouth(), ROW_SIZE, 0x55FFFFFF, 0x8A8A8A);
			if (blank != null) blanks.add(blank);
		}
		relayout(false);
	}

	private void dealCards(List<String> words) {
		List<String> shuffled = new ArrayList<>(words);
		java.util.Collections.shuffle(shuffled, new java.util.Random(level.getRandom().nextLong()));
		for (int i = 0; i < shuffled.size(); i++) {
			Vec3 home = floorSpot(i, shuffled.size());
			Display.TextDisplay display = text(shuffled.get(i), home, CARD_SIZE, CARD, INK);
			if (display == null) continue;
			Interaction hit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
			if (hit == null) {
				display.discard();
				continue;
			}
			hit.snapTo(home.x, home.y - 0.1, home.z, 0, 0);
			hit.setWidth(1.1F);
			hit.setHeight(0.75F);
			hit.setResponse(true);
			hit.addTag(BobStage.TAG);
			level.addFreshEntity(hit);
			cards.add(new Card(shuffled.get(i), display, hit, home));
		}
	}

	private Display.@Nullable TextDisplay text(String shown, Vec3 at, float size, int background, int ink) {
		Display.TextDisplay text = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (text == null) return null;
		text.snapTo(at.x, at.y, at.z, 0, 0);
		text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		text.setText(Component.literal(shown).withStyle(style -> style.withColor(ink).withBold(true)));
		text.setBackgroundColor(background);
		text.setTransformation(new com.mojang.math.Transformation(null, null, new Vector3f(size, size, size), null));
		text.addTag(BobStage.TAG);
		level.addFreshEntity(text);
		return text;
	}

	// --- Hands ---

	/** A click on one of this round's things: a card, or reading together a goal to pick. True if it was one. */
	boolean click(ServerPlayer player, Entity entity) {
		int picked = pickHits.indexOf(entity);
		if (picked >= 0 && mode == Mode.PREP) {
			if (!room.mayPress(player)) {
				room.notYourTurn(player, room.turn);
				return true;
			}
			pickPrep(player, picked);
			return true;
		}
		if (picked >= 0) {
			if (!picking) return true;
			if (picker != null && !picker.equals(player.getUUID())) {
				room.notYourTurn(player, picker);
				return true;
			}
			pick(player, picked);
			return true;
		}
		Card card = null;
		for (Card one : cards) if (one.hit == entity || one.display == entity) card = one;
		if (card == null) return false;
		if (room.busy || room.leaving) return true;
		// A preposition round's words are to read, not to move: a click says the word.
		if (mode == Mode.PREP) {
			Narrator.play(player, new Narration().say("word/" + card.word, "card_word", p -> {}, CaptionLine.sentence(List.of(card.word), 0), Narration.GAP));
			return true;
		}
		if (picking) {
			room.notYourTurn(player, picker);
			return true;
		}
		if (mode != Mode.SILLY && !room.mayPress(player)) {
			room.notYourTurn(player, room.turn);
			return true;
		}
		room.touch();
		if (card.placed()) home(card, true);
		else {
			int[] free = firstFree();
			if (free == null) {
				Narrator.play(player, new Narration().say("phrase/hmm"));
				return true;
			}
			put(card, free[0], free[1], true);
		}
		Narrator.note(player, "card:" + card.word + (card.placed() ? ":" + card.row + ":" + card.slot : ":home"));
		Narrator.play(player, new Narration().say("word/" + card.word, "card_word", p -> {}, CaptionLine.sentence(List.of(card.word), 0), Narration.GAP));
		return true;
	}

	private int @Nullable [] firstFree() {
		for (int r = 0; r < rows.size(); r++) {
			Card[] row = rows.get(r);
			for (int i = 0; i < row.length; i++) if (row[i] == null) return new int[] {r, i};
		}
		return null;
	}

	/** A card into a slot, flying there. */
	void put(Card card, int row, int slot, boolean glide) {
		if (card.placed()) rows.get(card.row)[card.slot] = null;
		rows.get(row)[slot] = card;
		card.row = row;
		card.slot = slot;
		relayout(glide);
	}

	/** A card back to its place on the floor. */
	void home(Card card, boolean glide) {
		if (card.placed()) rows.get(card.row)[card.slot] = null;
		boolean wasPlaced = card.placed();
		card.row = -1;
		card.slot = -1;
		fly(card, card.home, CARD_SIZE, glide);
		if (wasPlaced) relayout(glide);
	}

	private void fly(Card card, Vec3 to, float size, boolean glide) {
		card.display.setPosRotInterpolationDuration(glide ? 5 : 0);
		card.display.setTransformationInterpolationDelay(0);
		card.display.setTransformationInterpolationDuration(glide ? 5 : 0);
		card.display.setTransformation(new com.mojang.math.Transformation(null, null, new Vector3f(size, size, size), null));
		card.display.teleportTo(to.x, to.y, to.z);
		card.hit.teleportTo(to.x, to.y - 0.1, to.z);
		boolean big = size >= ROW_SIZE;
		card.hit.setWidth((float) (big ? rowWidth(card.word) : 1.1));
		card.hit.setHeight(big ? 0.8F : 0.75F);
	}

	/** Every card in a row goes home: after a sentence that wasn't it. */
	void allHome() {
		for (Card card : cards) if (card.placed()) home(card, true);
	}

	private void light(int row, int slot) {
		for (Card card : cards) card.display.setBackgroundColor(card.row == row && card.slot == slot ? CARD_LIT : CARD);
	}

	private void unlight() {
		for (Card card : cards) card.display.setBackgroundColor(CARD);
	}

	/** Reading together: the other reader picks a goal, and the builder tells Bob. */
	private void pick(ServerPlayer player, int which) {
		goal = offered.get(which);
		picking = false;
		Narrator.note(player, "bob_picked:" + goal.id());
		for (BobStage picture : pictures) picture.clear();
		pictures.clear();
		for (Entity extra : extras) extra.discard();
		extras.clear();
		for (Interaction hit : pickHits) hit.discard();
		pickHits.clear();
		showPicture(goal, 0, false);
		acted = goal.start();
		stage.show(acted, false);
		room.lastGoal = goal;
		Progress.of(player).useSentenceToday(player, goal.key(), "bob");
		ServerPlayer builds = builder == null ? null : level.getServer().getPlayerList().getPlayer(builder);
		Narrator.play(player, room.rounds.prompt());
		if (builds != null) Progress.of(builds).useSentenceToday(builds, goal.key(), "bob");
	}

	// --- Asking ---

	/** What the room says to ask for it, and to ask again. */
	Narration ask(Narration narration) {
		switch (mode) {
			case GOAL -> {
				if (picking) {
					String name = room.nameOf(picker);
					return narration.say(PICK_FOR_BOB, "pick_for_bob", p -> {}, CaptionLine.plain(name + ", pick what Bob should make!"), Narration.GAP);
				}
				return narration.say(TELL_BOB, "tell_bob", p -> {}, CaptionLine.plain("Tell Bob what to do!"), Narration.GAP);
			}
			case FIX -> {
				readRows(narration, rowsAsWords());
				return narration.say("phrase/whats_wrong");
			}
			case PREP -> {
				return narration.say("phrase/what_does_it_say", 2);
			}
			default -> {
				return narration.say(SILLY, "silly_sentences", p -> {}, CaptionLine.plain("Tell Bob something silly!"), Narration.GAP);
			}
		}
	}

	/** The rows as their words, a card at a time; a row with a gap is cut short there. */
	List<List<String>> rowsAsWords() {
		List<List<String>> out = new ArrayList<>();
		for (Card[] row : rows) {
			List<String> words = new ArrayList<>();
			for (Card card : row) {
				if (card == null) break;
				words.add(card.word);
			}
			out.add(words);
		}
		return out;
	}

	private boolean full() {
		for (Card[] row : rows) for (Card card : row) if (card == null) return false;
		return true;
	}

	/** Each row read out, its card lit as each word is said, the sentence in the caption. */
	private void readRows(Narration narration, List<List<String>> words) {
		for (int r = 0; r < words.size(); r++) {
			List<String> row = words.get(r);
			for (int i = 0; i < row.size(); i++) {
				int rr = r;
				int ii = i;
				narration.say("word/" + row.get(i), "read:" + r + ":" + i, p -> light(rr, ii), CaptionLine.sentence(row, i), 3);
			}
			narration.then("unlight", p -> unlight(), 6);
		}
	}

	// --- The button ---

	void press(ServerPlayer player) {
		if (room.busy || room.leaving) return;
		room.touch();
		if (picking || mode == Mode.PREP) {
			room.replay(player);
			return;
		}
		List<List<String>> built = rowsAsWords();
		if (mode == Mode.SILLY) {
			pressSilly(player, built.getFirst());
			return;
		}
		if (!full()) {
			Narrator.note(player, "partial");
			Narrator.play(player, new Narration().say("phrase/hmm"));
			return;
		}
		Goal target = goal;
		if (target == null) return;
		boolean right = target.madeBy(built);
		String said = String.join(" / ", built.stream().map(row -> String.join(" ", row)).toList());
		Narrator.note(player, "told_bob:" + said + (right ? ":right" : ":wrong"));
		boolean firstTry = !room.missedThisRound;
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		Narration telling = new Narration();
		readRows(telling, built);
		telling.sayAs("Bob", "phrase/bob_okay", "bob_okay", p -> nod(), 2);
		telling.then("act", p -> act(p, built), ACT_TICKS * built.size());
		telling.sayAs("Bob", "phrase/bob_did_it", "bob_did_it", null, Narration.GAP);
		if (right) {
			List<ServerPlayer> readers = new ArrayList<>();
			readers.add(player);
			ServerPlayer other = picker == null ? null : level.getServer().getPlayerList().getPlayer(picker);
			if (other != null && !other.getUUID().equals(player.getUUID())) readers.add(other);
			int lessonsBefore = AnswerCommit.lessonsBefore(player);
			boolean justMastered = firstTry && Progress.of(player).sentenceCountsToday(target.key())
				&& Progress.of(player).sentenceClean(target.key()) + 1 == Progress.masteredAt();
			boolean fresh = !Progress.of(player).rewardedFor(Progress.sentenceKey(target.key()));
			for (ServerPlayer reader : readers) {
				int before = reader == player ? lessonsBefore : AnswerCommit.lessonsBefore(reader);
				room.answers.commit().right(reader, "telling Bob " + said, () -> {
					Progress.of(reader).sentenceRight(reader, target.key(), firstTry);
					Progress.of(reader).lessonNote(reader, "told Bob: " + said);
					Prizes.sentenceSolved(reader, target.key(), firstTry);
				}, before);
				if (reader != player && before == 1) room.refreshDoor(reader, true);
			}
			happened[0] = true;
			telling.sayAs("Bob", "phrase/bob_hooray", "bob_hooray", p -> hooray(), 2);
			for (ServerPlayer reader : readers) if (reader != player) telling.add(room.prizes(reader.getUUID()).prizeLineFor(reader));
			room.answers.closing(player, telling, lessonsBefore, fresh, false, justMastered);
		} else {
			room.answers.commit().wrong(player);
			Safely.run("Remembering a sentence missed", () -> Progress.of(player).sentenceMissed(player, target.key(), said));
			telling.then("bob_puzzled", p -> puzzled(), 2).say("phrase/not_quite", 4);
			if (mode == Mode.GOAL) {
				telling.then("cards_home", p -> allHome(), 10)
					.then("reset", p -> reset(), 10);
			}
		}
		room.answers.finish(player, telling, happened, roundAt);
	}

	private void pressSilly(ServerPlayer player, List<String> words) {
		if (words.isEmpty()) {
			Narrator.play(player, new Narration().say("phrase/hmm"));
			return;
		}
		Instruction step = Instruction.parse(words);
		Narrator.note(player, "silly:" + String.join(" ", words) + (step == null ? ":nonsense" : ""));
		Narration telling = new Narration();
		readRows(telling, List.of(words));
		if (step == null) {
			telling.then("shrug", p -> shrug(), 10).say("phrase/hmm");
		} else {
			telling.sayAs("Bob", "phrase/bob_okay", "bob_okay", p -> nod(), 2);
			telling.then("act", p -> actSilly(step), ACT_TICKS);
			telling.sayAs("Bob", "phrase/bob_did_it", "bob_did_it", null, Narration.GAP);
			telling.say("phrase/giggle");
			// A silly sentence is a lesson only if the grown-ups say so, and never towards mastery.
			if (PrizeConfig.sillyCounts()) {
				int lessonsBefore = AnswerCommit.lessonsBefore(player);
				room.answers.commit().right(player, "a silly sentence", () -> Progress.of(player).lessonNote(player, "silly sentence: " + String.join(" ", words)),
					lessonsBefore);
			}
		}
		telling.onStop(p -> {
			room.busy = false;
			unlight();
			room.touch();
			room.refreshDoor(p, false);
		});
		room.busy = true;
		Narrator.play(player, telling);
	}

	/**
	 * A picture picked in a preposition round. The right one: the words read out, a card lit at a
	 * time, and it counts. The other: "Hmm, try again", and the round stays.
	 */
	private void pickPrep(ServerPlayer player, int which) {
		if (room.busy || room.leaving) return;
		room.touch();
		boolean right = which == prepRight;
		Narrator.note(player, "prep_pick:" + which + (right ? ":right" : ":wrong"));
		Narration telling = new Narration();
		boolean[] happened = new boolean[1];
		int roundAt = room.round;
		if (right) {
			boolean firstTry = !room.missedThisRound;
			int lessonsBefore = AnswerCommit.lessonsBefore(player);
			String key = prepKey();
			boolean justMastered = firstTry && Progress.of(player).sentenceCountsToday(key) && Progress.of(player).sentenceClean(key) + 1 == Progress.masteredAt();
			boolean fresh = !Progress.of(player).rewardedFor(Progress.sentenceKey(key));
			room.answers.commit().right(player, "reading " + String.join(" ", prepWords), () -> {
				Progress.of(player).sentenceRight(player, key, firstTry);
				Prizes.sentenceSolved(player, key, firstTry);
			}, lessonsBefore);
			happened[0] = true;
			readRows(telling, List.of(prepWords));
			room.answers.closing(player, telling, lessonsBefore, fresh, false, justMastered);
		} else {
			room.answers.commit().wrong(player);
			Safely.run("Remembering a preposition missed", () -> Progress.of(player).sentenceMissed(player, prepKey(), "picked the other"));
			telling.say("phrase/hmm").say("phrase/try_again");
		}
		room.answers.finish(player, telling, happened, roundAt);
	}

	/** Bob does it: each sentence in turn, from the start, a beat apart. */
	private void act(ServerPlayer player, List<List<String>> built) {
		if (goal == null) return;
		acted = goal.start();
		stage.show(acted, true);
		for (int i = 0; i < built.size(); i++) {
			Instruction step = Instruction.parse(built.get(i));
			int delay = i * ACT_TICKS + 4;
			room.later(player, delay, p -> {
				if (step == null || !acted.apply(step)) shrug();
				else {
					busyBob();
					stage.show(acted, true);
				}
			});
		}
	}

	private void actSilly(Instruction step) {
		// Too much on the stage: it starts again from nothing.
		if (acted.things().size() >= 6 && (!acted.things().contains(step.x()) || !acted.things().contains(step.y()))) {
			acted = new BobScene(List.of());
			stage.clear();
		}
		if (!acted.apply(step)) shrug();
		else {
			busyBob();
			stage.show(acted, true);
		}
	}

	/** Back to how it started: after a sentence that didn't make it. */
	private void reset() {
		if (goal == null) return;
		acted = goal.start();
		stage.show(acted, true);
	}

	private void busyBob() {
		if (bob == null) return;
		Vec3 at = stage.anchor();
		level.playSound(null, bob.getX(), bob.getY(), bob.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.8, at.z, 12, 1.0, 0.4, 0.4, 0.0);
	}

	/** Bob nods: okay, he'll do it. */
	private void nod() {
		if (bob != null) justfatlard.learning_blocks.feel.Gestures.run(bob, justfatlard.learning_blocks.feel.Gestures.Gesture.NOD);
	}

	/** That wasn't it: Bob shakes his head, puzzled. */
	private void puzzled() {
		if (bob == null) return;
		justfatlard.learning_blocks.feel.Gestures.run(bob, justfatlard.learning_blocks.feel.Gestures.Gesture.SHAKE);
		justfatlard.learning_blocks.feel.Feelings.show(bob, justfatlard.learning_blocks.feel.Feelings.Face.SURPRISED, 40);
	}

	private void shrug() {
		if (bob == null) return;
		justfatlard.learning_blocks.feel.Gestures.run(bob, justfatlard.learning_blocks.feel.Gestures.Gesture.SHAKE);
		level.playSound(null, bob.getX(), bob.getY(), bob.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.CLOUD, bob.getX(), bob.getY() + 2.2, bob.getZ(), 6, 0.2, 0.1, 0.2, 0.01);
	}

	private void hooray() {
		if (bob == null) return;
		justfatlard.learning_blocks.feel.Feelings.show(bob, justfatlard.learning_blocks.feel.Feelings.Face.GLAD, 60);
		justfatlard.learning_blocks.feel.Gestures.run(bob, justfatlard.learning_blocks.feel.Gestures.Gesture.JUMP);
		level.playSound(null, bob.getX(), bob.getY(), bob.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.9F, 1.1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, bob.getX(), bob.getY() + 1.8, bob.getZ(), 16, 0.4, 0.4, 0.4, 0.0);
	}

	// --- For tests ---

	public Mode mode() {
		return mode;
	}

	public @Nullable Goal goal() {
		return goal;
	}

	public List<Goal> offered() {
		return List.copyOf(offered);
	}

	/** A preposition round's words, and which of the two pictures is them. */
	public List<String> prepWords() {
		return List.copyOf(prepWords);
	}

	public int prepRight() {
		return prepRight;
	}

	/** Whether this one-sentence goal makes a preposition round: some other little word puts the same thing somewhere else. */
	public static boolean prepPossible(Goal goal) {
		if (goal.rows().size() != 1) return false;
		Instruction step = goal.steps().getFirst();
		if (step.bigX() || step.bigY()) return false;
		for (String other : justfatlard.learning_blocks.bob.Noun.PREPOSITIONS) {
			if (!other.equals(step.prep()) && prepDiffers(step.x(), step.prep(), other, step.y())) return true;
		}
		return false;
	}

	public boolean picking() {
		return picking;
	}

	/** The words on the cards still on the floor. */
	public List<String> floorWords() {
		return cards.stream().filter(card -> !card.placed()).map(card -> card.word).toList();
	}

	/** The rows as they stand, each cut short at its first gap. */
	public List<List<String>> rowWords() {
		return rowsAsWords();
	}

	/** What is on Bob's stage. */
	public BobScene acted() {
		return acted;
	}

	/** A thing on Bob's stage, as an entity. */
	public @Nullable Entity onStage(String thing) {
		return stage.entity(thing);
	}

	public @Nullable Villager bob() {
		return bob;
	}

	/** A click on a card with this word, on the floor ({@code fromRow} false) or in a row, as a child's would be. */
	public boolean clickCard(ServerPlayer player, String word, boolean fromRow) {
		for (Card card : cards) {
			if (card.word.equals(word) && card.placed() == fromRow) return click(player, card.hit);
		}
		return false;
	}

	/** Reading together: a click on goal {@code which} of the two, as a child's would be. */
	public boolean clickGoal(ServerPlayer player, int which) {
		return which < pickHits.size() && click(player, pickHits.get(which));
	}

	// --- Tidying away ---

	void clear() {
		stage.clear();
		for (BobStage picture : pictures) picture.clear();
		pictures.clear();
		for (Card card : cards) {
			card.display.discard();
			card.hit.discard();
		}
		cards.clear();
		for (Display.TextDisplay blank : blanks) blank.discard();
		blanks.clear();
		for (Entity extra : extras) extra.discard();
		extras.clear();
		for (Interaction hit : pickHits) hit.discard();
		pickHits.clear();
		if (bob != null) bob.discard();
		bob = null;
		// Anything a stage made that slipped through: by its tag.
		for (Entity entity : level.getEntities((Entity) null, layout.bounds(), entity -> entity.entityTags().contains(BobStage.TAG))) entity.discard();
	}
}
