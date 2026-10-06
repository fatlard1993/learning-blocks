package justfatlard.learning_blocks.review;

import justfatlard.learning_blocks.ForTests;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import justfatlard.pandorical.api.ComponentBuilder;

import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.editor.Admins;
import justfatlard.learning_blocks.editor.Ui;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Letters;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Captions;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Places to hear the sounds and words again, from anywhere: the letters, the vowels short and long,
 * the words a child has read, and for operators every recorded phrase. One Pandorical screen with a
 * tab for each; a click plays the clip to the viewer alone, with its caption, and writes what was
 * said on the screen too.
 *
 * <p>Nothing here reads or changes a visit. In a classroom a click waits for the room: while the
 * room is saying something, the review does not talk over it.
 */
public final class Review {
	private Review() {}

	public static final String TYPE = LearningBlocks.MOD_ID + ":review";

	public enum Tab {
		LETTERS("Letters"), PAIRS("Two-letter"), VOWELS("Vowels"), TRICKY("Tricky"), TOYBOX("Toybox"), WORDS("My words"), NUMBERS("123"), LOGIC("Logic"), PHRASES("Phrases");

		final String label;

		Tab(String label) {
			this.label = label;
		}

		/** The phrases are for operators checking recordings, not for children. */
		boolean forOperators() {
			return this == PHRASES;
		}
	}

	/**
	 * What a viewer has open: whose words, and whether every word the server has is shown. The
	 * words drawn are kept for tests: read ones, and the unread ones shown in their place.
	 */
	private record State(Tab tab, UUID owner, String ownerName, boolean all, String screenId,
			List<String> read, List<String> unread) {}

	private record Scroll(String id, int y, int height, Map<String, String> props, List<ComponentDef> children) {}

	private static final Map<UUID, State> states = new HashMap<>();
	/** Viewers whose narrator is playing something the review started, which another click may cut short. */
	private static final Set<UUID> talking = new HashSet<>();

	private static final int WIDTH = 316;
	private static final int PAD = Ui.PAD;
	private static final int INNER = WIDTH - 2 * PAD;
	private static final int TOP = 42;

	private static final List<Letter> VOWELS = List.of(Letter.A, Letter.E, Letter.I, Letter.O, Letter.U);
	public static void register(ScreenApi screens) {
		screens.onActionFallback(TYPE, Review::pressed);
		screens.onClose(TYPE, player -> states.remove(player.getUUID()));
	}

	public static void clear() {
		states.clear();
		capitals.clear();
		starred.clear();
		talking.clear();
	}

	public static void forget(UUID player) {
		states.remove(player);
		capitals.remove(player);
		starred.remove(player);
		talking.remove(player);
	}

	/**
	 * Open the review on this tab for this viewer, about {@code owner}'s words (their own if null).
	 * False if they cannot see it: no Pandorical, or an operator's tab or view asked for by anybody else.
	 */
	public static boolean open(ServerPlayer viewer, Tab tab, @Nullable ServerPlayer owner, boolean all) {
		if (!PandoricalApi.isAvailable(viewer)) return false;
		boolean operator = Admins.may(viewer);
		if (!operator && (tab.forOperators() || all || owner != null && owner != viewer)) return false;
		ServerPlayer whose = owner != null ? owner : viewer;
		show(viewer, tab, whose.getUUID(), whose.getGameProfile().name(), all);
		return true;
	}

	@ForTests
	public static void press(ServerPlayer viewer, String componentId) {
		pressed(viewer, Map.of(ScreenApi.FALLBACK_COMPONENT_ID_KEY, componentId));
	}

	public static @Nullable Tab openTab(ServerPlayer viewer) {
		State state = states.get(viewer.getUUID());
		return state == null ? null : state.tab();
	}

	@ForTests
	public static List<String> readShown(ServerPlayer viewer) {
		State state = states.get(viewer.getUUID());
		return state == null ? List.of() : state.read();
	}

	@ForTests
	public static List<String> unreadShown(ServerPlayer viewer) {
		State state = states.get(viewer.getUUID());
		return state == null ? List.of() : state.unread();
	}


	private static void show(ServerPlayer viewer, Tab tab, UUID owner, String ownerName, boolean all) {
		boolean operator = Admins.may(viewer);
		ServerPlayer whose = viewer.level().getServer().getPlayerList().getPlayer(owner);
		if (whose == null) {
			// Gone offline with the screen open: back to the viewer's own.
			whose = viewer;
			owner = viewer.getUUID();
			ownerName = viewer.getGameProfile().name();
		}
		starring.clear();
		trickyShowing.clear();
		toyboxShowing.clear();
		toyboxHints.clear();
		masteredShowing.clear();
		List<ComponentBuilder> parts = new ArrayList<>();
		List<String> read = new ArrayList<>();
		List<String> unread = new ArrayList<>();

		List<Tab> tabs = new ArrayList<>();
		boolean numbers = justfatlard.learning_blocks.subject.Subjects.on(whose, justfatlard.learning_blocks.subject.Numbers.INSTANCE);
		boolean logic = justfatlard.learning_blocks.subject.Subjects.on(whose, justfatlard.learning_blocks.subject.Logic.INSTANCE);
		for (Tab each : Tab.values()) {
			if (each.forOperators() && !operator || each == Tab.NUMBERS && !numbers || each == Tab.LOGIC && !logic) continue;
			tabs.add(each);
		}
		int tabW = (INNER - 2 * (tabs.size() - 1)) / tabs.size();
		for (int i = 0; i < tabs.size(); i++) {
			// The more tabs, the narrower: long labels go short so they fit.
			String label = tabs.size() >= 8 ? switch (tabs.get(i)) {
				case LETTERS -> "abc";
				case PAIRS -> "Pairs";
				case VOWELS -> "aeiou";
				case TOYBOX -> "Toys";
				case WORDS -> "Words";
				case PHRASES -> "Lines";
				default -> tabs.get(i).label;
			} : tabs.size() >= 6 ? switch (tabs.get(i)) {
				case PAIRS -> "Pairs";
				case WORDS -> "Words";
				case PHRASES -> "Lines";
				default -> tabs.get(i).label;
			} : tabs.size() >= 5 && tabs.get(i) == Tab.PAIRS ? "2-letter" : tabs.get(i).label;
			ComponentBuilder button = Ui.button("tab:" + tabs.get(i).name(), PAD + i * (tabW + 2), 20, tabW, Ui.BUTTON - 2, label);
			if (tabs.get(i) == tab) button.prop(ComponentType.PROP_STYLE, "pressed");
			parts.add(button);
		}

		ScreenBuilder screen = new ScreenBuilder(TYPE).title(tab.label).pauseGame(false);
		List<Scroll> scrolls = new ArrayList<>();
		int bottom = switch (tab) {
			case LETTERS -> letters(parts, whose);
			case PAIRS -> pairs(parts, whose);
			case VOWELS -> vowels(parts);
			case TRICKY -> tricky(parts, whose);
			case TOYBOX -> toybox(parts, whose);
			case NUMBERS -> numbers(parts, whose);
			case LOGIC -> logic(parts, whose);
			case WORDS -> words(viewer, scrolls, parts, owner, ownerName, all, operator, read, unread);
			case PHRASES -> phrases(scrolls, parts);
		};

		int height = bottom + 26;
		parts.add(Ui.faint("said_label", PAD, bottom + 8, "Heard:"));
		parts.add(Ui.text("said", PAD + 34, bottom + 8, ""));
		parts.add(Ui.button("close", WIDTH - PAD - 44, bottom + 3, 44, Ui.BUTTON - 2, "Done"));

		screen.size(WIDTH, height);
		screen.panel("dialog", 0, 0, WIDTH, height, Ui.panel());
		screen.component(Ui.icon("title_icon", PAD, 3, "minecraft:book", 0.75F));
		String shown = tab == Tab.NUMBERS ? "Numbers" : tab.label;
		String title = owner.equals(viewer.getUUID()) ? shown : ownerName + ": " + shown;
		screen.component(Ui.text("title", PAD + 16, 7, title));
		for (ComponentBuilder part : parts) screen.component(part);
		// After the panel, which would otherwise be drawn over them.
		for (Scroll scroll : scrolls) screen.scrollPanel(scroll.id(), PAD, scroll.y(), INNER, scroll.height(), scroll.props(), scroll.children());
		states.put(viewer.getUUID(), new State(tab, owner, ownerName, all, screen.screenId(), List.copyOf(read), List.copyOf(unread)));
		starred.put(viewer.getUUID(), List.copyOf(starring));
		trickyShown.put(viewer.getUUID(), List.copyOf(trickyShowing));
		toyboxShown.put(viewer.getUUID(), List.copyOf(toyboxShowing));
		toyboxHinted.put(viewer.getUUID(), List.copyOf(toyboxHints));
		masteredShown.put(viewer.getUUID(), List.copyOf(masteredShowing));
		PandoricalApi.screens().open(viewer, screen.build());
	}

	private static String tileTexture(Letter letter) {
		return LearningBlocks.MOD_ID + ":textures/block/letter_" + letter.grapheme() + ".png";
	}

	private static void tile(List<ComponentBuilder> parts, String id, Letter letter, int x, int y, int size, @Nullable String tooltip) {
		ComponentBuilder button = Ui.button(id, x, y, size, size, "");
		if (tooltip != null) button.prop(ComponentType.PROP_TOOLTIP, tooltip);
		parts.add(button);
		parts.add(new ComponentBuilder(id + "_face", ComponentType.SPRITE).bounds(x + 2, y + 2, size - 4, size - 4)
			.prop(ComponentType.PROP_TEXTURE, tileTexture(letter)));
	}

	private static final int NAME_ROW = 12;

	/** A due item's tooltip: gentle, and never a count of anything got wrong. */
	static final String PRACTISE = "Let's practise this one!";

	@ForTests
	private static final List<String> starring = new ArrayList<>();
	private static final Map<UUID, List<String>> starred = new HashMap<>();

	@ForTests
	public static List<String> starredShown(ServerPlayer viewer) {
		return starred.getOrDefault(viewer.getUUID(), List.of());
	}

	/**
	 * Clean completions as little pips in a card's top corner, filled as they come, up to the
	 * mastery count (five at most); mastered, a gold border round the card.
	 */
	private static void pips(List<ComponentDef> cards, String id, int clean, int x, int y) {
		int at = Progress.masteredAt();
		int shown = Math.min(at, 5);
		for (int i = 0; i < shown; i++) {
			boolean filled = i < Math.min(clean, shown) || clean >= at;
			cards.add(new ComponentBuilder("pip:" + id + ":" + i, ComponentType.PANEL).bounds(x + CARD_W - 6 - (shown - 1 - i) * 6, y + 3, 4, 4)
				.prop(ComponentType.PROP_BACKGROUND, filled ? "#FFFFC832" : "#FF8A8A8A").prop(ComponentType.PROP_BORDER, "flat")
				.prop(ComponentType.PROP_BORDER_COLOR, "#FF5A4A20").build());
		}
		if (clean >= at) {
			cards.add(new ComponentBuilder("mastered:" + id, ComponentType.PANEL).bounds(x, y, CARD_W, CARD_H - 2)
				.prop(ComponentType.PROP_BACKGROUND, "#00000000").prop(ComponentType.PROP_BORDER, "flat")
				.prop(ComponentType.PROP_BORDER_COLOR, "#FFFFC832").build());
			masteredShowing.add(id);
		}
	}

	private static final List<String> masteredShowing = new ArrayList<>();
	private static final Map<UUID, List<String>> masteredShown = new HashMap<>();

	public static List<String> masteredShown(ServerPlayer viewer) {
		return masteredShown.getOrDefault(viewer.getUUID(), List.of());
	}

	private static final java.util.Set<UUID> capitals = new java.util.HashSet<>();

	private static void casePips(List<ComponentBuilder> parts, Letter letter, int hits, int x, int y, int size) {
		int at = Progress.masteredAt();
		int shown = Math.min(at, 5);
		for (int i = 0; i < shown; i++) {
			boolean filled = i < Math.min(hits, shown) || hits >= at;
			parts.add(new ComponentBuilder("casepip:" + letter.grapheme() + ":" + i, ComponentType.PANEL)
				.bounds(x + size - 6 - (shown - 1 - i) * 5, y + size - 6, 4, 4)
				.prop(ComponentType.PROP_BACKGROUND, filled ? "#FFFFC832" : "#FF8A8A8A").prop(ComponentType.PROP_BORDER, "flat")
				.prop(ComponentType.PROP_BORDER_COLOR, "#FF5A4A20"));
		}
	}

	/**
	 * Tricky words, known by sight: every one open at this child's stage, to hear, with its clean
	 * completions as pips; the rest still to come, counted and not shown.
	 */
	private static int tricky(List<ComponentBuilder> parts, ServerPlayer viewer) {
		Progress progress = Progress.of(viewer);
		int stage = justfatlard.learning_blocks.puzzle.Stages.current(progress);
		int columns = 5;
		int width = (INNER - (columns - 1) * 4) / columns;
		int i = 0;
		int later = 0;
		for (Word word : justfatlard.learning_blocks.puzzle.Words.tricky()) {
			if (stage >= 0 && word.stage() > stage) {
				later++;
				continue;
			}
			int x = PAD + (i % columns) * (width + 4);
			int y = TOP + (i / columns) * 26;
			String shown = String.join("", justfatlard.learning_blocks.puzzle.Words.shown(word).stream().map(tile -> {
				Letter letter = Letter.of(tile);
				return letter == null ? tile : letter.shown();
			}).toList());
			parts.add(Ui.button("tricky:" + word.id(), x, y, width, 22, shown).prop(ComponentType.PROP_TOOLTIP, "Hear " + shown));
			int hits = progress.clean(word);
			int at = Progress.masteredAt();
			for (int p = 0; p < Math.min(at, 5); p++) {
				boolean filled = p < Math.min(hits, 5) || hits >= at;
				parts.add(new ComponentBuilder("trickypip:" + word.id() + ":" + p, ComponentType.PANEL).bounds(x + width - 6 - p * 5, y + 2, 4, 4)
					.prop(ComponentType.PROP_BACKGROUND, filled ? "#FFFFC832" : "#FF8A8A8A").prop(ComponentType.PROP_BORDER, "flat")
					.prop(ComponentType.PROP_BORDER_COLOR, "#FF5A4A20"));
			}
			trickyShowing.add(word.text());
			i++;
		}
		int bottom = TOP + ((i + columns - 1) / columns) * 26;
		parts.add(Ui.faint("hint", PAD, bottom + 2, i == 0 ? "Tricky words come from stage 2." : "Words to know by how they look."
			+ (later > 0 ? " " + later + " more to come." : "")));
		return bottom + 12;
	}

	/**
	 * The toybox: two shelves, the plushies and the toys, each the whole set, the ones this child has
	 * in colour (with how many, if more than one; a click says the word), the rest as a "?" still to
	 * find, and how many they have of each.
	 */
	private static int toybox(List<ComponentBuilder> parts, ServerPlayer viewer) {
		Progress progress = Progress.of(viewer);
		var plushies = progress.plushies();
		var toys = progress.toys();
		int y = shelf(parts, progress, "plush", "Plushies", TOP, java.util.Arrays.stream(justfatlard.learning_blocks.plush.Plush.values())
			.map(p -> new Shelved(p.word(), "plush_" + p.word(), plushies.getOrDefault(p, 0))).toList());
		y = shelf(parts, progress, "toy", "Toys", y + 4, java.util.Arrays.stream(justfatlard.learning_blocks.toy.Toy.values())
			.map(t -> new Shelved(t.word(), "toy_" + t.word(), toys.getOrDefault(t, 0))).toList());
		String count = "Plushies " + plushies.size() + " of " + justfatlard.learning_blocks.plush.Plush.values().length
			+ " · Toys " + toys.size() + " of " + justfatlard.learning_blocks.toy.Toy.values().length;
		parts.add(Ui.faint("hint", PAD, y + 2, count));
		parts.add(Ui.faint("hint2", PAD, y + 12, "Master words, numbers and puzzles to earn toys!"));
		toyboxCounted.put(viewer.getUUID(), count);
		return y + 22;
	}

	private record Shelved(String word, String item, int count) {}

	/**
	 * One shelf: its name, then the set in rows. One not had yet shows what earns it: the word to
	 * master under its "?", and pips for how near it is. Returns the y below it.
	 */
	private static int shelf(List<ComponentBuilder> parts, Progress progress, String family, String name, int top, List<Shelved> all) {
		parts.add(Ui.faint("shelf:" + family, PAD, top, name));
		top += 11;
		int columns = 10;
		int cell = (INNER - (columns - 1) * 3) / columns;
		for (int i = 0; i < all.size(); i++) {
			Shelved one = all.get(i);
			int x = PAD + (i % columns) * (cell + 3);
			int y = top + (i / columns) * (cell + 3);
			String id = family + ":" + one.word();
			if (one.count() > 0) {
				parts.add(Ui.button(id, x, y, cell, cell, "").prop(ComponentType.PROP_TOOLTIP, "Hear " + one.word()));
				parts.add(Ui.icon(family + "icon:" + one.word(), x + (cell - 24) / 2, y + (cell - 24) / 2, LearningBlocks.MOD_ID + ":" + one.item(), 1.5F));
				if (one.count() > 1) parts.add(new ComponentBuilder(family + "count:" + one.word(), ComponentType.TEXT).bounds(x + cell - 14, y + cell - 10, 13, 9)
					.prop(ComponentType.PROP_TEXT, "x" + one.count()).prop(ComponentType.PROP_COLOR, "#FFFFFFFF").prop(ComponentType.PROP_SHADOW, "true"));
				toyboxShowing.add(id);
			} else {
				List<justfatlard.learning_blocks.puzzle.Word> earnBy = justfatlard.learning_blocks.reward.Earned.wordsFor(family + ":" + one.word());
				String words = String.join(", ", earnBy.stream().map(justfatlard.learning_blocks.puzzle.Word::text).toList());
				parts.add(Ui.button(family + "secret:" + one.word(), x, y, cell, cell, "?").prop(ComponentType.PROP_ENABLED, "false")
					.prop(ComponentType.PROP_TOOLTIP, earnBy.isEmpty() ? "Still to find" : "Master: " + words));
				if (!earnBy.isEmpty()) {
					String first = earnBy.getFirst().text() + (earnBy.size() > 1 ? "+" : "");
					parts.add(new ComponentBuilder(family + "earn:" + one.word(), ComponentType.TEXT).bounds(x, y + cell - 9, cell, 8)
						.prop(ComponentType.PROP_TEXT, first).prop(ComponentType.PROP_ALIGN, "center").prop(ComponentType.PROP_COLOR, "#FFE8E0C0")
						.prop(ComponentType.PROP_SCALE, "0.75"));
					// Pips: the clean days of the word furthest from mastered.
					int at = Progress.masteredAt();
					int clean = earnBy.stream().mapToInt(progress::clean).min().orElse(0);
					for (int pip = 0; pip < Math.min(at, 5); pip++) {
						parts.add(new ComponentBuilder(family + "pip:" + one.word() + ":" + pip, ComponentType.PANEL).bounds(x + 2 + pip * 4, y + 2, 3, 3)
							.prop(ComponentType.PROP_BACKGROUND, pip < clean ? "#FFFFC832" : "#FF5A5A5A").prop(ComponentType.PROP_BORDER, "flat"));
					}
					toyboxHints.add(family + ":" + one.word() + "=" + words);
				}
			}
		}
		return top + ((all.size() + columns - 1) / columns) * (cell + 3);
	}

	private static final List<String> toyboxShowing = new ArrayList<>();
	private static final List<String> toyboxHints = new ArrayList<>();
	private static final Map<UUID, List<String>> toyboxHinted = new HashMap<>();

	@ForTests
	public static List<String> toyboxHints(ServerPlayer viewer) {
		return toyboxHinted.getOrDefault(viewer.getUUID(), List.of());
	}
	private static final Map<UUID, List<String>> toyboxShown = new HashMap<>();
	private static final Map<UUID, String> toyboxCounted = new HashMap<>();

	/**
	 * What the Toybox tab showed this viewer in colour, as {@code plush:<name>} and
	 * {@code toy:<name>}, and its count line: for tests.
	 */
	public static List<String> toyboxShown(ServerPlayer viewer) {
		return toyboxShown.getOrDefault(viewer.getUUID(), List.of());
	}

	public static String toyboxCount(ServerPlayer viewer) {
		return toyboxCounted.getOrDefault(viewer.getUUID(), "");
	}

	private static final List<String> trickyShowing = new ArrayList<>();
	private static final Map<UUID, List<String>> trickyShown = new HashMap<>();

	@ForTests
	public static List<String> trickyShown(ServerPlayer viewer) {
		return trickyShown.getOrDefault(viewer.getUUID(), List.of());
	}

	/** Worth a star: due for another look, and still shaky (missed lately, or not yet right first time much). */
	private static boolean practise(Progress progress, String key) {
		var item = progress.memory().item(key);
		return item != null && item.box() <= 2 && progress.memory().due(key);
	}

	private static ComponentBuilder star(String id, int x, int y) {
		starring.add(id.substring("star:".length()));
		return new ComponentBuilder(id, ComponentType.TEXT).bounds(x, y, 9, 9)
			.prop(ComponentType.PROP_TEXT, "\u2605").prop(ComponentType.PROP_COLOR, "#FFFFC832").prop(ComponentType.PROP_SHADOW, "true");
	}

	public static String nameClip(Letter letter) {
		return "aeiou".contains(letter.grapheme()) ? "phoneme/" + letter.grapheme() + "_long" : "name/" + letter.grapheme();
	}

	private static int letters(List<ComponentBuilder> parts, ServerPlayer viewer) {
		Progress progress = Progress.of(viewer);
		boolean caps = capitals.contains(viewer.getUUID());
		parts.add(Ui.button("caps", WIDTH - PAD - 60, 4, 60, 14, caps ? "a b c" : "A B C")
			.prop(ComponentType.PROP_TOOLTIP, caps ? "Show the little letters" : "Show the big letters"));
		int size = 30;
		int gap = 3;
		int columns = 9;
		int left = PAD + (INNER - (columns * size + (columns - 1) * gap)) / 2;
		int i = 0;
		for (Letter letter : Letter.values()) {
			if (!letter.isSingle()) continue;
			int x = left + (i % columns) * (size + gap);
			int y = TOP + (i / columns) * (size + gap + NAME_ROW);
			boolean known = justfatlard.learning_blocks.puzzle.Letters.known(progress, letter);
			boolean practise = practise(progress, justfatlard.learning_blocks.reward.Memory.tileKey(letter.grapheme()));
			tile(parts, (caps ? "capital:" : "letter:") + letter.grapheme(), caps ? letter.upper() : letter, x, y, size,
				practise ? PRACTISE : known ? "You know this one!" : null);
			if (caps) casePips(parts, letter, justfatlard.learning_blocks.puzzle.Letters.caseHits(progress, letter), x, y, size);
			if (practise) parts.add(star("star:" + letter.grapheme(), x + 1, y + 1));
			// A letter learned in the letters level: a tick in its corner.
			if (known) parts.add(new ComponentBuilder("known:" + letter.grapheme(), ComponentType.TEXT).bounds(x + size - 9, y + 1, 9, 9)
				.prop(ComponentType.PROP_TEXT, "\u2714").prop(ComponentType.PROP_COLOR, "#FF2FB344").prop(ComponentType.PROP_SHADOW, "true"));
			// The letter's name, under it: "bee" for b, as a sound is not a name.
			// With the big letters showing, the button shows the pair, big and little: the tile already says the name.
			parts.add(Ui.button("name:" + letter.grapheme(), x, y + size + 1, size, NAME_ROW - 1, caps ? letter.upper().shown() + letter.shown() : "Name")
				.prop(ComponentType.PROP_TOOLTIP, "Hear its name"));
			i++;
		}
		int rows = (i + columns - 1) / columns;
		int bottom = TOP + rows * (size + gap + NAME_ROW);
		int known = justfatlard.learning_blocks.puzzle.Letters.knownLetters(progress).size();
		parts.add(Ui.faint("hint", PAD, bottom + 2, known > 0 ? "Click a letter to hear its sound. \u2714 " + known + " known."
			: "Click a letter to hear its sound."));
		return bottom + 12;
	}

	/** The word a two-letter tile is shown with: the earliest word that uses it, or null while none does. */
	static @Nullable Word pairWord(Letter pair) {
		Word best = null;
		for (Word word : Stages.usable()) {
			if (!word.tiles().contains(pair.grapheme())) continue;
			if (best == null || word.stage() < best.stage() || word.stage() == best.stage() && word.text().compareTo(best.text()) < 0) best = word;
		}
		return best;
	}

	private static int pairs(List<ComponentBuilder> parts, ServerPlayer viewer) {
		Progress progress = Progress.of(viewer);
		int size = 24;
		int gap = 3;
		int columns = 10;
		int pitch = size + gap + 10;
		int width = (INNER - (columns - 1) * gap) / columns;
		int i = 0;
		for (Letter pair : Letter.values()) {
			if (!pair.isDigraph()) continue;
			int x = PAD + (i % columns) * (width + gap);
			int y = TOP + (i / columns) * (pitch + 4);
			int tileX = x + (width - size) / 2;
			boolean known = justfatlard.learning_blocks.puzzle.Letters.known(progress, pair);
			boolean practise = practise(progress, justfatlard.learning_blocks.reward.Memory.tileKey(pair.grapheme()));
			tile(parts, "pair:" + pair.grapheme(), pair, tileX, y, size, practise ? PRACTISE : known ? "You know this one!" : null);
			if (practise) parts.add(star("star:" + pair.grapheme(), tileX + 1, y + 1));
			if (known) parts.add(new ComponentBuilder("pairknown:" + pair.grapheme(), ComponentType.TEXT).bounds(tileX + size - 9, y + 1, 9, 9)
				.prop(ComponentType.PROP_TEXT, "\u2714").prop(ComponentType.PROP_COLOR, "#FF2FB344").prop(ComponentType.PROP_SHADOW, "true"));
			Word word = pairWord(pair);
			if (word != null) {
				// The word in small letters over its button, whole: the button is narrow, and a word cut short is no use to a child.
				parts.add(Ui.button("pairword:" + pair.grapheme(), x, y + size + 1, width, 12, "")
					.prop(ComponentType.PROP_TOOLTIP, "Hear " + word.text()));
				parts.add(new ComponentBuilder("pairlabel:" + pair.grapheme(), ComponentType.TEXT).bounds(x, y + size + 3, width, 10)
					.scale(0.75F).prop(ComponentType.PROP_TEXT, word.text()).prop(ComponentType.PROP_ALIGN, "center")
					.prop(ComponentType.PROP_COLOR, "#FFFFFFFF").prop(ComponentType.PROP_SHADOW, "true"));
			}
			i++;
		}
		int rows = (i + columns - 1) / columns;
		int bottom = TOP + rows * (pitch + 4);
		parts.add(Ui.faint("hint", PAD, bottom, "Two or three letters, one sound. Click one to hear it."));
		return bottom + 10;
	}

	private static int numbers(List<ComponentBuilder> parts, ServerPlayer whose) {
		justfatlard.learning_blocks.reward.Progress progress = justfatlard.learning_blocks.reward.Progress.of(whose);
		int columns = 3;
		int w = (INNER - 2 * (columns - 1)) / columns;
		int row = Ui.BUTTON + 2;
		for (int n = 0; n <= justfatlard.learning_blocks.numbers.NumberBlock.HIGHEST; n++) {
			int i = n == 0 ? justfatlard.learning_blocks.numbers.NumberBlock.HIGHEST : n - 1;
			// Dots up to ten, as on the tiles' sides; past that, too many to count on a button.
			String dots = n >= 1 && n <= 10 ? "  " + "\u25CF".repeat(n) : "";
			boolean mastered = progress.itemMastered(justfatlard.learning_blocks.subject.Numbers.numeral(n));
			if (mastered) masteredShowing.add(justfatlard.learning_blocks.subject.Numbers.numeral(n));
			parts.add(Ui.button("number:" + n, PAD + (i % columns) * (w + 2), TOP + (i / columns) * row, w, Ui.BUTTON,
				(mastered ? "\u2605 " : "") + n + dots));
		}
		int bottom = TOP + (justfatlard.learning_blocks.numbers.NumberBlock.HIGHEST / columns + 1) * row;
		parts.add(Ui.faint("hint", PAD, bottom, "Click one to hear it. A star is one you know."));
		return bottom + 10;
	}

	private record LogicGame(String label, String clip, String asks, List<String> items) {}

	private static List<String> gate(LogicCourse.Kind kind, @Nullable String target) {
		List<String> items = new ArrayList<>();
		for (LogicCourse.Band band : LogicCourse.BANDS) {
			for (String item : band.gate()) {
				if (item.startsWith("logic:" + kind.id + ":") && (target == null || item.equals(kind.item(target)))) items.add(item);
			}
		}
		return items;
	}

	private static final List<LogicGame> LOGIC_GAMES = List.of(
		new LogicGame("Pens", "phrase/put_in_pens", "Put the animals in their pens!", gate(LogicCourse.Kind.SORT, "kind")),
		new LogicGame("Colours", "phrase/sort_by_colour", "Sort them by colour!", gate(LogicCourse.Kind.SORT, "colour")),
		new LogicGame("Big, little", "phrase/sort_by_size", "Big ones here, little ones there!", gate(LogicCourse.Kind.SORT, "size")),
		new LogicGame("Tidy up", "phrase/toys_in_box", "Toys in the box, plushies in the crib!", gate(LogicCourse.Kind.SORT, "things")),
		new LogicGame("Same?", "phrase/same_or_different", "Same or different?", gate(LogicCourse.Kind.SAME, null)),
		new LogicGame("Next", "phrase/what_comes_next", "What comes next?", gate(LogicCourse.Kind.NEXT, null)),
		new LogicGame("Mistake", "phrase/find_the_mistake", "Oops, one is wrong! Can you find it?", gate(LogicCourse.Kind.MISTAKE, null)),
		new LogicGame("Copy it", "phrase/copy_it", "Can you make one just like it?", gate(LogicCourse.Kind.COPY, null)),
		new LogicGame("Odd one", "phrase/which_doesnt_belong", "Which one doesn't belong?", gate(LogicCourse.Kind.ODD, null)),
		new LogicGame("Missing", "phrase/whats_missing", "What's missing?", gate(LogicCourse.Kind.MISSING, null)),
		new LogicGame("In order", "phrase/first_next_last", "Put them in order: first, next, last!", gate(LogicCourse.Kind.STORY, null)),
		new LogicGame("Little to big", "phrase/little_to_big", "Put them in order, little to big!", gate(LogicCourse.Kind.SIZE, null)),
		new LogicGame("Morning", "phrase/what_happens_first", "What happens first?", gate(LogicCourse.Kind.MORNING, null)));

	private static int logic(List<ComponentBuilder> parts, ServerPlayer whose) {
		justfatlard.learning_blocks.reward.Progress progress = justfatlard.learning_blocks.reward.Progress.of(whose);
		int columns = 2;
		int w = (INNER - 2 * (columns - 1)) / columns;
		int row = Ui.BUTTON + 2;
		for (int i = 0; i < LOGIC_GAMES.size(); i++) {
			LogicGame game = LOGIC_GAMES.get(i);
			StringBuilder stars = new StringBuilder();
			for (String item : game.items()) {
				boolean mastered = progress.itemMastered(item);
				stars.append(mastered ? "\u2605" : "\u2606");
				if (mastered) masteredShowing.add(item);
			}
			parts.add(Ui.button("logic:" + i, PAD + (i % columns) * (w + 2), TOP + (i / columns) * row, w, Ui.BUTTON,
				game.label() + "  " + stars));
		}
		int bottom = TOP + ((LOGIC_GAMES.size() + columns - 1) / columns) * row;
		parts.add(Ui.faint("hint", PAD, bottom, "Click one to hear it. A star is one you know."));
		return bottom + 10;
	}

	private static int vowels(List<ComponentBuilder> parts) {
		int row = 30;
		parts.add(Ui.faint("short_head", PAD + 34, TOP - 2, "Short"));
		parts.add(Ui.faint("long_head", PAD + 184, TOP - 2, "Long"));
		for (int i = 0; i < VOWELS.size(); i++) {
			Letter vowel = VOWELS.get(i);
			int y = TOP + 8 + i * row;
			String v = vowel.grapheme();
			tile(parts, "vowel:" + v, vowel, PAD, y, 26, null);
			parts.add(Ui.button("short:" + v, PAD + 34, y + 3, 50, Ui.BUTTON, v + " short"));
			Word example = example(vowel);
			if (example != null) {
				String item = pictureItem(example);
				if (item != null) parts.add(Ui.icon("short_pic:" + v, PAD + 92, y + 5, item, 1.0F));
				parts.add(Ui.text("short_word:" + v, PAD + 112, y + 9, example.text()));
			}
			parts.add(Ui.button("long:" + v, PAD + 184, y + 3, 50, Ui.BUTTON, v + " long"));
		}
		int bottom = TOP + 8 + VOWELS.size() * row;
		parts.add(Ui.faint("hint", PAD, bottom, "Long-vowel words are coming later."));
		return bottom + 10;
	}

	/**
	 * The word a short vowel is shown with: its keyword, the word the letters chant ends on (apple,
	 * egg, ink, on, up), so a child meets one anchor word per vowel; failing that, the first word
	 * the server has with the vowel in the middle.
	 */
	static @Nullable Word example(Letter vowel) {
		Word keyword = Letters.keyword(vowel);
		if (keyword != null) return keyword;
		List<Word> candidates = new ArrayList<>();
		for (Word word : Stages.usable()) {
			if (word.tiles().size() < 2 || !word.tiles().get(1).equals(vowel.grapheme())) continue;
			if (!("phoneme/" + vowel.grapheme()).equals(word.phonemeOf(1))) continue;
			if (!word.id().getNamespace().equals(LearningBlocks.MOD_ID)) continue;
			candidates.add(word);
		}
		candidates.sort((a, b) -> a.stage() != b.stage() ? Integer.compare(a.stage(), b.stage()) : a.text().compareTo(b.text()));
		return candidates.isEmpty() ? null : candidates.getFirst();
	}

	private static @Nullable String pictureItem(Word word) {
		ItemStack picture = word.picture();
		return picture.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(picture.getItem()).toString();
	}

	/**
	 * A picture for a word, on a screen: its item, or for a carpet (which an item icon draws as a thin
	 * sliver) the square of cloth itself.
	 */
	private static ComponentBuilder picture(String id, Word word, int x, int y) {
		String item = pictureItem(word);
		if (item != null && item.endsWith("_carpet")) {
			String cloth = item.replace("moss_carpet", "moss_block").replace("_carpet", "_wool");
			String texture = cloth.substring(0, cloth.indexOf(':') + 1) + "textures/block/" + cloth.substring(cloth.indexOf(':') + 1) + ".png";
			return new ComponentBuilder(id, ComponentType.SPRITE).bounds(x + 2, y + 2, 20, 20).prop(ComponentType.PROP_TEXTURE, texture);
		}
		return Ui.icon(id, x + 4, y + 4, item != null ? item : "minecraft:paper", 1.5F);
	}

	private static final int CARD_W = 56;
	private static final int CARD_H = 38;
	private static final int UNIT = 20;
	private static final int WORD_UNITS = 6;

	private static int words(ServerPlayer viewer, List<Scroll> scrolls, List<ComponentBuilder> parts, UUID owner, String ownerName,
			boolean all, boolean operator, List<String> read, List<String> unread) {
		ServerPlayer whose = viewer.level().getServer().getPlayerList().getPlayer(owner);
		Progress progress = whose != null ? Progress.of(whose) : null;
		Set<String> done = progress != null ? progress.words() : Set.of();

		// Stage by stage: every word read, and the unread ones of the stage being played now as
		// question marks, so a child sees how many are left to find without being told what they are.
		// An operator's every-word view shows every word, unread ones in grey.
		// By band, 1a, 1b: the unread ones of the band being played now as question marks.
		Stages.Band currentBand = progress != null ? Stages.currentBand(progress) : null;
		Stages.BandCode current = currentBand != null ? currentBand.code() : null;
		Map<Stages.BandCode, List<Word>> byStage = new TreeMap<>();
		Map<Stages.BandCode, Integer> hidden = new TreeMap<>();
		Map<Stages.BandCode, String> labels = new HashMap<>();
		for (Stages.Band band : Stages.bands()) labels.put(band.code(), band.label());
		for (Word word : Stages.usable()) {
			boolean isRead = done.contains(word.id().toString());
			Stages.Band band = Stages.bandOf(word);
			Stages.BandCode code = band != null ? band.code() : Stages.BandCode.stageStart(word.stage());
			if (isRead || all) byStage.computeIfAbsent(code, s -> new ArrayList<>()).add(word);
			else if (code.equals(current)) hidden.merge(code, 1, Integer::sum);
		}
		for (Stages.BandCode stage : hidden.keySet()) byStage.computeIfAbsent(stage, s -> new ArrayList<>());
		for (List<Word> list : byStage.values()) list.sort((a, b) -> a.text().compareTo(b.text()));

		if (operator) {
			parts.add(Ui.button("all", WIDTH - PAD - 90, 4, 90, 14, all ? "Just read" : "Every word")
				.prop(ComponentType.PROP_TOOLTIP, all ? "Show only the words read" : "Show every word the server has"));
		}

		List<ComponentDef> cards = new ArrayList<>();
		int units = 0;
		int perRow = INNER / (CARD_W + 2);
		for (Map.Entry<Stages.BandCode, List<Word>> entry : byStage.entrySet()) {
			Stages.BandCode stage = entry.getKey();
			List<Word> list = entry.getValue();
			int secret = all ? 0 : hidden.getOrDefault(stage, 0);
			cards.add(Ui.text("stage:" + stage.packed(), 2, units * UNIT + 6, "Stage " + labels.getOrDefault(stage, String.valueOf(stage.stage()))
				+ (secret > 0 ? "  (" + secret + " more to find)" : "")).build());
			units++;
			int count = list.size() + secret;
			for (int i = 0; i < count; i++) {
				int x = (i % perRow) * (CARD_W + 2);
				int y = (units + (i / perRow) * 2) * UNIT;
				if (i < list.size()) {
					Word word = list.get(i);
					String id = word.id().toString();
					boolean isRead = done.contains(id);
					(isRead ? read : unread).add(id);
					boolean practise = isRead && progress != null && practise(progress, justfatlard.learning_blocks.reward.Memory.wordKey(id));
					cards.add(Ui.button("word:" + id, x, y, CARD_W, CARD_H - 2, "", practise ? PRACTISE : null).build());
					if (practise) cards.add(star("star:" + id, x + 2, y + 1).build());
					if (isRead && progress != null) pips(cards, id, progress.clean(word), x, y);
					cards.add(picture("pic:" + id, word, x + (CARD_W - 24) / 2, y - 2).build());
					cards.add(new ComponentBuilder("label:" + id, ComponentType.TEXT).bounds(x, y + 25, CARD_W, 10)
						.prop(ComponentType.PROP_TEXT, word.text()).prop(ComponentType.PROP_ALIGN, "center")
						.prop(ComponentType.PROP_SHADOW, "true")
						.prop(ComponentType.PROP_COLOR, isRead ? "#FFFFFFFF" : "#FFA0A0A0").build());
				} else {
					String id = "secret:" + stage + ":" + i;
					cards.add(Ui.button(id, x, y, CARD_W, CARD_H - 2, "?").prop(ComponentType.PROP_ENABLED, "false").build());
				}
			}
			units += ((count + perRow - 1) / perRow) * 2;
		}

		int listY = TOP;
		if (cards.isEmpty()) {
			boolean self = owner.equals(viewer.getUUID());
			parts.add(Ui.text("empty", PAD, listY + 8, self ? "No words yet!" : ownerName + " has not read any words yet."));
			if (self) parts.add(Ui.faint("empty_hint", PAD, listY + 22, "Read a word in the classroom and it will be here."));
			return listY + WORD_UNITS * UNIT;
		}
		scrolls.add(new Scroll("cards", listY, WORD_UNITS * UNIT, Map.of(
			ComponentType.PROP_ITEM_HEIGHT, String.valueOf(UNIT),
			ComponentType.PROP_VISIBLE_ITEMS, String.valueOf(WORD_UNITS),
			ComponentType.PROP_TOTAL_ITEMS, String.valueOf(Math.max(units, 1)),
			ComponentType.PROP_SHOW_SCROLLBAR, String.valueOf(units > WORD_UNITS),
			ComponentType.PROP_SCROLL_OFFSET, "0",
			ComponentType.PROP_BACKGROUND, "#00000000"), cards));
		return listY + WORD_UNITS * UNIT;
	}

	private static final int PHRASE_ROW = 22;
	private static final int PHRASE_ROWS = 7;

	public static List<String> phraseKeys() {
		Set<String> keys = new TreeSet<>();
		for (String key : Captions.keys()) if (key.startsWith("phrase/")) keys.add(key);
		for (String key : Sounds.recorded()) if (key.startsWith("phrase/")) keys.add(key);
		Set<String> variants = new java.util.HashSet<>();
		for (String key : keys) {
			List<String> of = Sounds.variantsOf(key);
			if (of.size() > 1) variants.addAll(of.subList(1, of.size()));
		}
		List<String> ordered = new ArrayList<>();
		for (String key : keys) {
			if (variants.contains(key)) continue;
			ordered.addAll(Sounds.variantsOf(key));
		}
		return List.copyOf(ordered);
	}

	private static String phraseLabel(String key) {
		String name = key.substring("phrase/".length());
		int at = name.lastIndexOf("_v");
		boolean variant = at > 0 && Sounds.variantsOf(key.substring(0, "phrase/".length() + at)).contains(key);
		return variant ? "    " + name.substring(at + 1) : name;
	}

	private static int phrases(List<Scroll> scrolls, List<ComponentBuilder> parts) {
		List<String> keys = phraseKeys();
		List<ComponentDef> rows = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			int y = i * PHRASE_ROW;
			Captions.Caption caption = Captions.get(key);
			String seconds = String.format(Locale.ROOT, "%.1fs", Sounds.durationTicks(key) / 20.0F);
			rows.add(Ui.button("phrase:" + key, 0, y + 1, 30, PHRASE_ROW - 3, "Play").build());
			rows.add(Ui.text("key:" + key, 36, y + 2, phraseLabel(key)
				+ (Sounds.isRecorded(key) ? "" : "  (placeholder)")).build());
			rows.add(Ui.faint("len:" + key, INNER - 44, y + 2, seconds).build());
			rows.add(Ui.faint("cap:" + key, 36, y + 11, Ui.clip(captionText(caption, key), 44)).build());
		}
		scrolls.add(new Scroll("rows", TOP, PHRASE_ROWS * PHRASE_ROW, Map.of(
			ComponentType.PROP_ITEM_HEIGHT, String.valueOf(PHRASE_ROW),
			ComponentType.PROP_VISIBLE_ITEMS, String.valueOf(PHRASE_ROWS),
			ComponentType.PROP_TOTAL_ITEMS, String.valueOf(Math.max(keys.size(), 1)),
			ComponentType.PROP_SHOW_SCROLLBAR, String.valueOf(keys.size() > PHRASE_ROWS),
			ComponentType.PROP_SCROLL_OFFSET, "0",
			ComponentType.PROP_BACKGROUND, "#00000000"), rows));
		if (keys.isEmpty()) parts.add(Ui.faint("empty", PAD, TOP + 8, "No phrases found."));
		return TOP + PHRASE_ROWS * PHRASE_ROW;
	}

	private static String captionText(Captions.@Nullable Caption caption, String key) {
		if (caption == null || caption.words().isEmpty()) return "(no caption)";
		StringBuilder text = new StringBuilder();
		for (Captions.Word word : caption.words()) {
			if (!text.isEmpty()) text.append(' ');
			text.append(word.text());
		}
		return text.toString();
	}


	private static void pressed(ServerPlayer viewer, Map<String, String> data) {
		String id = data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY);
		State state = states.get(viewer.getUUID());
		if (id == null || state == null) return;
		boolean operator = Admins.may(viewer);

		if (id.equals("close")) {
			states.remove(viewer.getUUID());
			PandoricalApi.screens().close(viewer, state.screenId());
		} else if (id.startsWith("tab:")) {
			Tab tab;
			try {
				tab = Tab.valueOf(id.substring("tab:".length()));
			} catch (IllegalArgumentException e) {
				return;
			}
			if (tab.forOperators() && !operator) return;
			show(viewer, tab, state.owner(), state.ownerName(), state.all() && operator);
		} else if (id.equals("caps")) {
			if (!capitals.remove(viewer.getUUID())) capitals.add(viewer.getUUID());
			show(viewer, state.tab(), state.owner(), state.ownerName(), state.all());
		} else if (id.startsWith("capital:")) {
			Letter letter = Letter.of(id.substring("capital:".length()));
			if (letter != null) say(viewer, state, List.of(nameClip(letter)), letter.upper().shown());
		} else if (id.startsWith("plush:")) {
			justfatlard.learning_blocks.plush.Plush plush = justfatlard.learning_blocks.plush.Plush.of(id.substring("plush:".length()));
			if (plush != null) say(viewer, state, List.of("word/" + plush.word()), plush.word());
		} else if (id.startsWith("toy:")) {
			justfatlard.learning_blocks.toy.Toy toy = justfatlard.learning_blocks.toy.Toy.of(id.substring("toy:".length()));
			if (toy != null) say(viewer, state, List.of("word/" + toy.word()), toy.word());
		} else if (id.startsWith("tricky:")) {
			net.minecraft.resources.Identifier wordId = net.minecraft.resources.Identifier.tryParse(id.substring("tricky:".length()));
			for (Word word : justfatlard.learning_blocks.puzzle.Words.tricky()) {
				if (word.id().equals(wordId)) say(viewer, state, List.of("word/" + word.text()), word.text());
			}
		} else if (id.equals("all")) {
			if (operator) show(viewer, state.tab(), state.owner(), state.ownerName(), !state.all());
		} else if (id.startsWith("pair:")) {
			Letter pair = Letter.of(id.substring("pair:".length()));
			if (pair != null && pair.isDigraph()) say(viewer, state, List.of("phoneme/" + pair.grapheme()), pair.grapheme());
		} else if (id.startsWith("pairword:")) {
			Letter pair = Letter.of(id.substring("pairword:".length()));
			Word word = pair != null ? pairWord(pair) : null;
			if (word != null) say(viewer, state, List.of("phoneme/" + pair.grapheme(), "word/" + word.text()), pair.grapheme() + " ... " + word.text());
		} else if (id.startsWith("name:")) {
			Letter letter = Letter.of(id.substring("name:".length()));
			if (letter != null) say(viewer, state, List.of(nameClip(letter)), letter.grapheme().toUpperCase(Locale.ROOT));
		} else if (id.startsWith("letter:") || id.startsWith("vowel:")) {
			Letter letter = Letter.of(id.substring(id.indexOf(':') + 1));
			if (letter != null) say(viewer, state, List.of("phoneme/" + letter.grapheme()), letter.grapheme());
		} else if (id.startsWith("number:")) {
			try {
				int n = Integer.parseInt(id.substring("number:".length()));
				say(viewer, state, List.of(justfatlard.learning_blocks.numbers.NumberBlock.soundOf(n)), justfatlard.learning_blocks.subject.Numbers.word(n));
			} catch (NumberFormatException ignored) {
				// Not a number button.
			}
		} else if (id.startsWith("logic:")) {
			try {
				LogicGame game = LOGIC_GAMES.get(Integer.parseInt(id.substring("logic:".length())));
				say(viewer, state, List.of(game.clip()), game.asks());
			} catch (NumberFormatException | IndexOutOfBoundsException ignored) {
				// Not one of the games.
			}
		} else if (id.startsWith("short:")) {
			Letter vowel = Letter.of(id.substring("short:".length()));
			if (vowel == null) return;
			Word example = example(vowel);
			List<String> clips = new ArrayList<>(List.of("phoneme/" + vowel.grapheme()));
			if (example != null) clips.add("word/" + example.text());
			say(viewer, state, clips, vowel.grapheme() + (example != null ? " ... " + example.text() : ""));
		} else if (id.startsWith("long:")) {
			Letter vowel = Letter.of(id.substring("long:".length()));
			if (vowel != null) say(viewer, state, List.of("phoneme/" + vowel.grapheme() + "_long"), vowel.grapheme().toUpperCase(Locale.ROOT));
		} else if (id.startsWith("word:")) {
			String wordId = id.substring("word:".length());
			if (!state.read().contains(wordId) && !(operator && state.unread().contains(wordId))) return;
			for (Word word : Stages.usable()) {
				if (word.id().toString().equals(wordId)) say(viewer, state, List.of("word/" + word.text()), word.text());
			}
		} else if (id.startsWith("phrase:")) {
			String key = id.substring("phrase:".length());
			if (operator && key.startsWith("phrase/")) say(viewer, state, List.of(key), captionText(Captions.get(key), key));
		}
	}

	/**
	 * Play these clips to the viewer, one after another, with their captions, and write what was said
	 * on the screen. In a classroom, not while the room is talking.
	 */
	private static void say(ServerPlayer viewer, State state, List<String> clips, String said) {
		UUID id = viewer.getUUID();
		if (Rooms.of(viewer) != null && Narrator.isPlaying(viewer) && !talking.contains(id)) return;
		Narration narration = new Narration();
		for (String clip : clips) {
			if (clip.startsWith("phoneme/")) {
				String shown = clip.substring("phoneme/".length()).replace("_long", "");
				narration.say(clip, "review:" + clip, player -> {}, CaptionLine.building(List.of(shown), 0), Narration.GAP);
			} else {
				// The clip clicked, not a variant of it: the phrases screen is for checking each one.
				narration.sayExactly(clip);
			}
		}
		narration.onStop(player -> talking.remove(player.getUUID()));
		Narrator.play(viewer, narration);
		talking.add(id);
		PandoricalApi.screens().update(viewer, state.screenId(),
			List.of(new ComponentUpdateBuilder("said").prop(ComponentType.PROP_TEXT, Ui.clip(said, 36)).build()));
	}
}
