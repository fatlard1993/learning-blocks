package justfatlard.phonics.puzzle;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import justfatlard.phonics.Phonics;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Every word there is to read, loaded from data packs.
 *
 * <p>A word is data rather than code so that adding one is a file and no build: {@code
 * data/<namespace>/phonics_words/<word>.json}, any namespace, so another mod or a server's own data
 * pack can teach new words, and replace the ones shipped here by writing a file at the same path.
 * The shipped words are the first users of exactly that route, not a special case beside it.
 *
 * <p>Whether a word's outcome can run is not decided here but when a puzzle is picked, because
 * that depends on registries and tags that are only settled once a server is running. A word
 * naming a mob from a mod that is not installed loads fine and is simply never offered.
 *
 * <p>Modelled on Disenchanting Lectern's {@code EnchantmentProse}.
 */
public final class Words implements SimpleSynchronousResourceReloadListener {

	private static final String DIRECTORY = "phonics_words";
	private static final String SUFFIX = ".json";

	/** Replaced wholesale on reload, so a read never sees a half-built list. */
	private static volatile List<Word> words = List.of();
	private static volatile List<Word> keywords = List.of();

	/** Every word a puzzle can be made of: all but the keyword-only ones. */
	public static List<Word> all() {
		return words;
	}

	private static volatile List<Word> everything = List.of();
	private static final java.util.Map<Identifier, List<Integer>> chunks = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<Identifier> compounds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final java.util.Map<Identifier, java.util.Set<String>> earns = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The plushies and toys this word brings or shows, as {@code plush:owl} or {@code toy:car}: from
	 * its {@code icon} and {@code outcome}, or {@code "earns"} in its file, which overrides them.
	 * Mastering every word that earns a toy earns the toy.
	 */
	public static java.util.Set<String> earns(Word word) {
		return earns.getOrDefault(word.id(), java.util.Set.of());
	}

	/**
	 * A big word's chunks, {@code "chunks": [3, 3]} in its file: how many tiles each has, in order
	 * (cat|fish); null for a word of one chunk.
	 */
	public static @org.jspecify.annotations.Nullable List<Integer> chunks(Word word) {
		return chunks.get(word.id());
	}

	/** Where each chunk starts and ends, {@code [start, end)} in tiles; one, the whole word, for a word of one. */
	public static List<int[]> chunkRanges(Word word) {
		List<Integer> sizes = chunks(word);
		List<int[]> ranges = new ArrayList<>();
		if (sizes == null) {
			ranges.add(new int[] {0, word.tiles().size()});
			return ranges;
		}
		int at = 0;
		for (int size : sizes) {
			ranges.add(new int[] {at, at + size});
			at += size;
		}
		return ranges;
	}

	/** A chunk's tiles as a word, as its sound is filed: {@code fish}. */
	public static String chunkText(Word word, int[] range) {
		return String.join("", word.tiles().subList(range[0], range[1]));
	}

	/** A compound, {@code "compound": true}: two words the child already reads (cat|fish), as against syllables (rob|in). */
	public static boolean compound(Word word) {
		return compounds.contains(word.id());
	}

	/** Every word file loaded, puzzle or not: in, under, the. */
	public static List<Word> any() {
		return everything;
	}

	private static volatile List<Word> tricky = List.of();
	private static volatile List<Word> plurals = List.of();
	private static final java.util.Map<Identifier, List<String>> shownTiles = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Map<Identifier, String> pictureCards = new java.util.concurrent.ConcurrentHashMap<>();

	/** A word's picture card, {@code "picture_card"} in its file: drawn on a panel where no item shows it. */
	public static @org.jspecify.annotations.Nullable String pictureCard(Word word) {
		return pictureCards.get(word.id());
	}

	/** The tricky words, known by sight: the, said, you. */
	public static List<Word> tricky() {
		return tricky;
	}

	/** The plurals: cats, dogs. */
	public static List<Word> plurals() {
		return plurals;
	}

	/** The tiles the board shows for this word: its {@code shown} list, or its own tiles. */
	public static List<String> shown(Word word) {
		return shownTiles.getOrDefault(word.id(), word.tiles());
	}

	/** Every letter's keyword, puzzle word or not: apple, zap. */
	public static List<Word> keywords() {
		return keywords;
	}

	/** A keyword or a puzzle word by what it spells, or null. */
	public static @org.jspecify.annotations.Nullable Word spelled(String text) {
		for (Word word : keywords) if (word.text().equals(text)) return word;
		for (Word word : words) if (word.text().equals(text)) return word;
		return null;
	}

	@Override
	public Identifier getFabricId() {
		return Phonics.id(DIRECTORY);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<Word> loaded = new ArrayList<>();
		shownTiles.clear();
		pictureCards.clear();
		chunks.clear();
		compounds.clear();
		earns.clear();

		// One resource per id, already resolved across packs, so a data pack later in the order
		// replaces a word rather than colliding with it.
		for (Map.Entry<Identifier, Resource> entry
				: manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier file = entry.getKey();
			Identifier id = wordOf(file);
			if (id == null) continue;

			try (BufferedReader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				Word.Body.CODEC.parse(JsonOps.INSTANCE, json)
					.ifSuccess(body -> {
						List<String> missing = body.requires().stream().filter(mod -> !FabricLoader.getInstance().isModLoaded(mod)).toList();
						if (missing.isEmpty()) {
							loaded.add(Word.of(id, body));
							if (!body.shown().isEmpty()) shownTiles.put(id, List.copyOf(body.shown()));
							body.pictureCard().ifPresent(card -> pictureCards.put(id, card));
							readChunks(id, json, body.tiles().size());
							readEarns(id, json);
						}
						else Phonics.LOGGER.info("[{}] Leaving out word {}: it needs {}", Phonics.MOD_ID, id, missing);
					})
					.ifError(error -> Phonics.LOGGER.warn("[{}] Skipping word {}: {}", Phonics.MOD_ID, file, error.message()));
			} catch (Exception | StackOverflowError e) {
				// StackOverflowError too: a word's sequence nested in itself should cost that word, not the rest.
				// One malformed file is not worth losing every other word over.
				Phonics.LOGGER.warn("[{}] Could not read word {}", Phonics.MOD_ID, file, e);
			}
		}

		loaded.sort(Comparator.comparing(word -> word.id().toString()));
		// One word per spelling: two packs both teaching "cat" would make every cat round ambiguous.
		java.util.Map<List<String>, Word> bySpelling = new java.util.LinkedHashMap<>();
		for (Word word : loaded) {
			Word first = bySpelling.putIfAbsent(word.tiles(), word);
			if (first != null) {
				Phonics.LOGGER.warn("[{}] {} spells the same as {}, which is kept; {} is left out", Phonics.MOD_ID, word.id(), first.id(), word.id());
			}
		}
		loaded.clear();
		loaded.addAll(bySpelling.values());
		everything = List.copyOf(loaded);
		words = loaded.stream().filter(Word::puzzle).toList();
		tricky = loaded.stream().filter(Word::tricky).toList();
		plurals = loaded.stream().filter(Word::plural).toList();
		keywords = loaded.stream().filter(Word::keyword).toList();
		Phonics.LOGGER.info("[{}] Loaded {} words and {} keywords", Phonics.MOD_ID, words.size(), keywords.size());
	}

	/** A big word's {@code chunks} and {@code compound}, read beside the codec: chunks that add up to its tiles, or none. */
	private static void readChunks(Identifier id, JsonElement json, int tiles) {
		if (!json.isJsonObject()) return;
		var object = json.getAsJsonObject();
		if (object.has("compound") && object.get("compound").getAsBoolean()) compounds.add(id);
		if (!object.has("chunks") || !object.get("chunks").isJsonArray()) return;
		List<Integer> sizes = new ArrayList<>();
		for (JsonElement size : object.getAsJsonArray("chunks")) sizes.add(size.getAsInt());
		if (sizes.size() < 2 || sizes.stream().anyMatch(size -> size < 1) || sizes.stream().mapToInt(Integer::intValue).sum() != tiles) {
			Phonics.LOGGER.warn("[{}] Word {}: \"chunks\" {} does not split its {} tiles; read as one chunk", Phonics.MOD_ID, id, sizes, tiles);
			return;
		}
		chunks.put(id, List.copyOf(sizes));
	}

	/** What a word earns: {@code "earns"} if it says (a name: a plushie's, else a toy's), else what its icon and outcome name. */
	private static void readEarns(Identifier id, JsonElement json) {
		if (!json.isJsonObject()) return;
		var object = json.getAsJsonObject();
		java.util.Set<String> found = new java.util.LinkedHashSet<>();
		if (object.has("earns")) {
			JsonElement said = object.get("earns");
			List<JsonElement> names = new ArrayList<>();
			if (said.isJsonArray()) said.getAsJsonArray().forEach(names::add);
			else names.add(said);
			for (JsonElement name : names) {
				String text = name.getAsString();
				if (text.contains(":")) found.add(text);
				else if (justfatlard.phonics.plush.Plush.of(text) != null) found.add("plush:" + text);
				else if (justfatlard.phonics.toy.Toy.of(text) != null) found.add("toy:" + text);
			}
		} else {
			if (object.has("icon")) scan(object.get("icon"), found);
			if (object.has("outcome")) scan(object.get("outcome"), found);
		}
		if (!found.isEmpty()) earns.put(id, java.util.Set.copyOf(found));
	}

	private static void scan(JsonElement json, java.util.Set<String> found) {
		String ns = Phonics.MOD_ID + ":";
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			String text = json.getAsString();
			if (text.startsWith(ns + "plush_")) found.add("plush:" + text.substring((ns + "plush_").length()));
			if (text.startsWith(ns + "toy_")) found.add("toy:" + text.substring((ns + "toy_").length()));
			return;
		}
		if (json.isJsonArray()) {
			for (JsonElement each : json.getAsJsonArray()) scan(each, found);
			return;
		}
		if (!json.isJsonObject()) return;
		var object = json.getAsJsonObject();
		if (object.has("block") && object.has("properties") && object.getAsJsonObject("properties").has("kind")) {
			String block = object.get("block").getAsString();
			String kind = object.getAsJsonObject("properties").get("kind").getAsString();
			if (block.equals(ns + "plush")) found.add("plush:" + kind);
			if (block.equals(ns + "toy")) found.add("toy:" + kind);
		}
		for (var entry : object.entrySet()) if (!entry.getKey().equals("properties")) scan(entry.getValue(), found);
	}

	/** Turns {@code ns:phonics_words/cat.json} back into {@code ns:cat}. */
	private static Identifier wordOf(Identifier file) {
		String path = file.getPath();
		int start = DIRECTORY.length() + 1;
		if (path.length() <= start + SUFFIX.length()) return null;
		return Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(start, path.length() - SUFFIX.length()));
	}
}
