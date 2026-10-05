package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jspecify.annotations.Nullable;

/**
 * One word a child can read, as it is written in {@code data/<namespace>/learning_words/<word>.json}:
 *
 * <pre>{@code
 * { "tiles": ["c", "a", "t"], "stage": 1, "outcome": { "type": "spawn_entity", "entity": "minecraft:cat" } }
 * }</pre>
 *
 * <p>Tiles are graphemes, not letters: {@code ["sh", "i", "p"]} is three tiles, because "sh" is one
 * sound. The first tile is the onset, the rest the rime, and words sharing a rime are a family -
 * cat, bat, hat - which is what a puzzle is made from. The stage says when in a child's progress
 * the word belongs.
 *
 * <p>A tile says its own sound in a word unless the word says otherwise. {@code sounds}, the same
 * length as {@code tiles}, names another sound for a tile - {@code "a_long"}, the letter's name, for
 * the a in cake - or {@code "silent"} for one that says nothing, the e on the end; {@code null}
 * keeps the tile's own. The override is the word's: the same tile loose on the floor says its own
 * sound, because it is not in cake there.
 *
 * <pre>{@code
 * { "tiles": ["c", "a", "k", "e"], "sounds": [null, "a_long", null, "silent"], "stage": 6, "outcome": { ... } }
 * }</pre>
 *
 * @param id     the file's id, {@code learning-blocks-justfatlard:cat}; its path is the word, and names its sound
 * @param sounds one per tile: another sound, {@code "silent"}, or empty for the tile's own
 * @param icon   the item pictured on the board while this word is asked for; see {@link #picture()}
 * @param wrong  what the word does when built but not asked for: something funny that is still
 *               plainly that word, over in a couple of seconds; see {@link #wrongOutcome()}
 * @param scene  a little problem this word fixes, asked instead of the word once it has been read;
 *               see {@link Scene}
 * @param keyword also a letter's keyword - "zap" for z - said and done at the end of its chant in
 *               the letters level; see {@link Letters}
 * @param clearPicture {@code "picture": "clear"}: a concrete thing whose picture a five-year-old names at
 *               once as this word (cat, bell, ship), so a read-it round may show it; see {@link RoundLifecycle}
 * @param tricky  a tricky word (the, said, you), known by sight rather than sounded out: asked by
 *               finding its card, never blended; {@code "shown"} can give the tiles the board shows
 *               (["cap_i"] for I) where they differ from the recorded spelling
 * @param plural  a plural (cats, dogs): its own round, the word and an s on the end, once the word without
 *               it has been read
 * @param puzzle  whether it is a puzzle word at all: false for a keyword and nothing else ("apple"),
 *               which is never offered, never in a family and never in My words
 *
 * <p>A file may also say {@code "requires": ["poopsmith"]}: mod ids without which the word is left
 * out altogether, for a word that only makes sense with that mod's things. A word that merely
 * prefers a mod's thing uses a {@code prefer} outcome instead, which falls back to vanilla.
 */
public record Word(Identifier id, List<String> tiles, List<Optional<String>> sounds, int stage, Outcome outcome,
		Optional<Identifier> icon, Optional<Outcome> wrong, Optional<Scene> scene, boolean keyword, boolean puzzle, boolean clearPicture,
		boolean tricky, boolean plural) {

	/** The sound override that says nothing. */
	public static final String SILENT = "silent";

	/** A list entry that may be JSON null: read as absent. */
	private static final Codec<Optional<String>> MAYBE = Codec.PASSTHROUGH.xmap(
		dynamic -> dynamic.asString().result().filter(text -> !text.isBlank()),
		value -> new Dynamic<>(JsonOps.INSTANCE, value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE)));

	/** The file body; the id comes from where the file is. */
	public record Body(List<String> tiles, Optional<List<Optional<String>>> sounds, int stage, Outcome outcome,
			Optional<Identifier> icon, Optional<Outcome> wrong, Optional<Scene> scene, boolean keyword, boolean puzzle,
			List<String> requires, String picture, boolean tricky, boolean plural, List<String> shown, Optional<String> pictureCard) {
		public static final Codec<Body> CODEC = RecordCodecBuilder.<Body>create(instance -> instance.group(
			Codec.STRING.listOf(1, 8).fieldOf("tiles").forGetter(Body::tiles),
			MAYBE.listOf().optionalFieldOf("sounds").forGetter(Body::sounds),
			Codec.intRange(1, 99).optionalFieldOf("stage", 1).forGetter(Body::stage),
			Outcome.CODEC.fieldOf("outcome").forGetter(Body::outcome),
			Identifier.CODEC.optionalFieldOf("icon").forGetter(Body::icon),
			Outcome.CODEC.optionalFieldOf("wrong").forGetter(Body::wrong),
			Scene.CODEC.optionalFieldOf("scene").forGetter(Body::scene),
			Codec.BOOL.optionalFieldOf("keyword", false).forGetter(Body::keyword),
			Codec.BOOL.optionalFieldOf("puzzle", true).forGetter(Body::puzzle),
			Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(Body::requires),
			Codec.STRING.optionalFieldOf("picture", "").forGetter(Body::picture),
			Codec.BOOL.optionalFieldOf("tricky", false).forGetter(Body::tricky),
			Codec.BOOL.optionalFieldOf("plural", false).forGetter(Body::plural),
			Codec.STRING.listOf().optionalFieldOf("shown", List.of()).forGetter(Body::shown),
			Codec.STRING.optionalFieldOf("picture_card").forGetter(Body::pictureCard)
		).apply(instance, Body::new)).validate(body -> body.sounds().isEmpty() || body.sounds().get().size() == body.tiles().size()
			? DataResult.success(body)
			: DataResult.error(() -> "\"sounds\" has " + body.sounds().get().size() + " entries for " + body.tiles().size() + " tiles"));
	}

	public static Word of(Identifier id, Body body) {
		List<String> tiles = new ArrayList<>();
		for (String tile : body.tiles()) tiles.add(tile.toLowerCase(java.util.Locale.ROOT));
		List<Optional<String>> sounds = new ArrayList<>();
		for (int i = 0; i < tiles.size(); i++) {
			int at = i;
			sounds.add(body.sounds().flatMap(list -> list.get(at)).map(sound -> sound.toLowerCase(java.util.Locale.ROOT)));
		}
		return new Word(id, List.copyOf(tiles), List.copyOf(sounds), body.stage(), body.outcome(), body.icon(), body.wrong(), body.scene(), body.keyword(), body.puzzle(),
			body.picture().equals("clear"), body.tricky(), body.plural());
	}

	/**
	 * What happens when this word is built but was not the one asked for: the file's {@code wrong}
	 * outcome, cheekier, if it has one this server can run, and otherwise the ordinary one.
	 */
	public Outcome wrongOutcome() {
		return wrong.filter(Outcome::usable).orElse(outcome);
	}

	/** Its scene, if it has one this server can play. */
	public Optional<Scene> usableScene() {
		return scene.filter(Scene::usable);
	}

	/**
	 * The picture on the board while this is the word asked for: the {@code icon} item if the file
	 * names one this server has, otherwise what the outcome brings - the creature's spawn egg, the
	 * item given or worn, the block put down - and empty for a word with nothing to show.
	 */
	public ItemStack picture() {
		return picture(null);
	}

	/** The same, and with the server's registries, as the outcome makes it: a cap in its colour. */
	public ItemStack picture(net.minecraft.core.HolderLookup.@Nullable Provider registries) {
		if (icon.isPresent()) {
			Optional<Item> named = BuiltInRegistries.ITEM.getOptional(icon.get());
			if (named.isPresent()) return new ItemStack(named.get());
		}
		return pictureOf(outcome, registries);
	}

	private static ItemStack pictureOf(Outcome outcome, net.minecraft.core.HolderLookup.@Nullable Provider registries) {
		return switch (outcome) {
			case Outcome.GiveItem give when registries != null && give.components().isPresent() ->
				Outcome.stack(registries, give.item(), 1, give.components());
			case Outcome.EquipItem equip when registries != null && equip.components().isPresent() ->
				Outcome.stack(registries, equip.item(), 1, equip.components());
			case Outcome.SpawnEntity spawn -> BuiltInRegistries.ENTITY_TYPE.getOptional(spawn.entity())
				.flatMap(SpawnEggItem::byId).map(egg -> new ItemStack(egg.value())).orElse(ItemStack.EMPTY);
			case Outcome.GiveItem give -> BuiltInRegistries.ITEM.getOptional(give.item()).map(ItemStack::new).orElse(ItemStack.EMPTY);
			case Outcome.EquipItem equip -> BuiltInRegistries.ITEM.getOptional(equip.item()).map(ItemStack::new).orElse(ItemStack.EMPTY);
			case Outcome.PlaceBlock place -> BuiltInRegistries.BLOCK.getOptional(place.block())
				.map(block -> new ItemStack(block.asItem())).orElse(ItemStack.EMPTY);
			case Outcome.Prefer prefer -> prefer.chosen() == null ? ItemStack.EMPTY : pictureOf(prefer.chosen(), registries);
			case Outcome.Sequence sequence -> sequence.steps().stream().map(step -> pictureOf(step, registries))
				.filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
			default -> ItemStack.EMPTY;
		};
	}

	/**
	 * The sound tile {@code index} makes when this word is sounded out: null when it is silent here;
	 * otherwise, for a vowel, the tile cut from this word's own recording ({@code blend/cat/1}) if there is one,
	 * which is already the sound this word gives it; otherwise its {@link #phonemeOf standard sound}.
	 */
	public @Nullable String soundOf(int index) {
		String phoneme = phonemeOf(index);
		if (phoneme == null) return null;
		// Only a vowel, or a vowel team, is cut from the word: qu, with its u, is a consonant sound.
		Letter tile = Letter.of(tiles.get(index));
		if (tile == null || !tile.isVowel()) return phoneme;
		String cut = "blend/" + text() + "/" + index;
		return Sounds.isRecorded(cut) ? cut : phoneme;
	}

	/**
	 * The standard sound tile {@code index} makes in this word - {@code phoneme/c}, {@code phoneme/a_long}
	 * - or null when it is silent here.
	 */
	public @Nullable String phonemeOf(int index) {
		Optional<String> override = index < sounds.size() ? sounds.get(index) : Optional.empty();
		if (override.isPresent()) return override.get().equals(SILENT) ? null : "phoneme/" + override.get();
		return "phoneme/" + tiles.get(index);
	}

	/** The word itself, as its sound is filed: {@code cat}. */
	public String text() {
		return String.join("", tiles);
	}

	public String onset() {
		return tiles.getFirst();
	}

	/** Everything after the onset, which is what a family shares: {@code at}. */
	public String rime() {
		return String.join("", tiles.subList(1, tiles.size()));
	}

	public List<String> rimeTiles() {
		return tiles.subList(1, tiles.size());
	}

	/**
	 * The tiles as letter blocks can show them, or null where one of them is a grapheme no single
	 * letter tile can show ("sh"), which this room cannot build yet.
	 */
	public @Nullable List<Letter> letters() {
		List<Letter> letters = new ArrayList<>();
		for (String tile : tiles) {
			Letter letter = Letter.of(tile);
			if (letter == null) return null;
			letters.add(letter);
		}
		return letters;
	}
}
