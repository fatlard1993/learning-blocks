package justfatlard.phonics.bob;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A thing Bob can be told to put somewhere: a creature, a plushie, something to put things in or
 * on, something to wear, or a lid. {@code ref} is what stands for it in the room: an entity type,
 * a plushie's name, a toy as {@code toy:<name>}, a block, or an item.
 */
public record Noun(String word, Kind kind, String ref, float height) {

	public enum Kind {
		/** A real creature: it can ride another, and things go on its head. */
		CREATURE,
		/** A plushie, small enough to go in things. */
		PLUSH,
		/** Something to put things in: a box, a jar, a tub. */
		CONTAINER,
		/** Something to put things on, or under: a bed, a desk, a rug. */
		SURFACE,
		/** Something worn: on a creature, it is on its head. */
		WEARABLE,
		/** A lid: on a container, it shuts it, and nothing goes in after. */
		LID
	}

	public boolean small() {
		return kind == Kind.PLUSH || kind == Kind.WEARABLE || kind == Kind.LID;
	}

	/** Has room under it: a desk, a crib, a bed's legs. */
	public boolean hasUnder() {
		return word.equals("desk") || word.equals("crib");
	}

	private static final Map<String, Noun> ALL = new LinkedHashMap<>();

	private static void add(String word, Kind kind, String ref, float height) {
		ALL.put(word, new Noun(word, kind, ref, height));
	}

	static {
		add("cat", Kind.CREATURE, "minecraft:cat", 0.7F);
		add("pig", Kind.CREATURE, "minecraft:pig", 0.9F);
		add("hen", Kind.CREATURE, "minecraft:chicken", 0.7F);
		add("dog", Kind.CREATURE, "minecraft:wolf", 0.85F);
		add("fox", Kind.CREATURE, "minecraft:fox", 0.7F);
		add("owl", Kind.PLUSH, "owl", 0.55F);
		add("duck", Kind.PLUSH, "duck", 0.6F);
		add("rat", Kind.PLUSH, "rat", 0.4F);
		add("crab", Kind.PLUSH, "crab", 0.4F);
		add("ant", Kind.PLUSH, "ant", 0.4F);
		add("bug", Kind.PLUSH, "ladybug", 0.4F);
		add("box", Kind.CONTAINER, "phonics-justfatlard:box", 0.5625F);
		add("jar", Kind.CONTAINER, "phonics-justfatlard:jar", 0.625F);
		add("tub", Kind.CONTAINER, "phonics-justfatlard:tub", 0.62F);
		add("bin", Kind.CONTAINER, "phonics-justfatlard:bin", 0.87F);
		add("pot", Kind.CONTAINER, "minecraft:flower_pot", 0.375F);
		add("crib", Kind.SURFACE, "toy:crib", 0.5F);
		add("bed", Kind.SURFACE, "minecraft:red_bed", 0.5625F);
		add("desk", Kind.SURFACE, "phonics-justfatlard:desk", 0.6875F);
		add("rug", Kind.SURFACE, "minecraft:red_carpet", 0.0625F);
		add("hat", Kind.WEARABLE, "minecraft:leather_helmet", 0.3F);
		add("cap", Kind.WEARABLE, "phonics-justfatlard:toy_cap", 0.3F);
		add("wig", Kind.WEARABLE, "phonics-justfatlard:toy_wig", 0.3F);
		add("lid", Kind.LID, "minecraft:spruce_slab", 0.12F);
	}

	public static @Nullable Noun of(String word) {
		return ALL.get(word);
	}

	public static List<Noun> all() {
		return List.copyOf(ALL.values());
	}

	/** The little words a sentence is made with. */
	public static final List<String> PREPOSITIONS = List.of("in", "on", "under");
	public static final List<String> ARTICLES = List.of("the", "a");
	public static final List<String> ADJECTIVES = List.of("big");
}
