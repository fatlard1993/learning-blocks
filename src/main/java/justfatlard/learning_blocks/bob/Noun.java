package justfatlard.learning_blocks.bob;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.relate.Relations;
import justfatlard.learning_blocks.relate.Thing;
import org.jspecify.annotations.Nullable;

/**
 * A thing Bob can be told to put somewhere, seen as Bob's sentences need it: what kind of thing it
 * is. The thing itself, and everything it affords, is {@link Thing}, from {@link Relations}: Bob
 * and toy play share it.
 */
public record Noun(String word, Kind kind, Thing thing) {

	public enum Kind {
		/** A real creature: it can ride another, and things go on its head. */
		CREATURE,
		/** Something small to put places: a plushie, a toy, a prop. */
		PLUSH,
		/** Something to put things in: a box, a jar, a tub, a crib. */
		CONTAINER,
		/** Something to put things on, or under: a bed, a desk, a rug. */
		SURFACE,
		/** Something to ride: a toy car, a ship. */
		VEHICLE,
		/** Something worn: on a creature or a plushie, it is on its head. */
		WEARABLE,
		/** A lid: on a container, it shuts it, and nothing goes in after. */
		LID
	}

	public float height() {
		return thing.height();
	}

	public boolean small() {
		return thing.small() || kind == Kind.WEARABLE || kind == Kind.LID;
	}

	public boolean hasUnder() {
		return thing.holds(Thing.Hold.Way.UNDER);
	}

	private static Kind kindOf(Thing thing) {
		if (thing.creature()) return Kind.CREATURE;
		if (thing.lid()) return Kind.LID;
		if (thing.wearable()) return Kind.WEARABLE;
		if (thing.holds(Thing.Hold.Way.IN)) return Kind.CONTAINER;
		if (thing.holds(Thing.Hold.Way.RIDE)) return Kind.VEHICLE;
		if (thing.holds(Thing.Hold.Way.ON) || thing.holds(Thing.Hold.Way.UNDER)) return Kind.SURFACE;
		return Kind.PLUSH;
	}

	public static @Nullable Noun of(String word) {
		Thing thing = Relations.thing(word);
		return thing == null ? null : new Noun(word, kindOf(thing), thing);
	}

	public static List<Noun> all() {
		List<Noun> all = new ArrayList<>();
		for (Thing thing : Relations.all()) all.add(new Noun(thing.word(), kindOf(thing), thing));
		return all;
	}

	public static final List<String> PREPOSITIONS = List.of("in", "on", "under");
	public static final List<String> ARTICLES = List.of("the", "a");
	public static final List<String> ADJECTIVES = List.of("big");
}
