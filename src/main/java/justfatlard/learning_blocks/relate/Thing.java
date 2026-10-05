package justfatlard.learning_blocks.relate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One thing the classroom knows, as {@code learning_relations/<word>.json} describes it: what it is,
 * how it is shown and held, how tall it is, its tags, and what it holds - its affordances.
 */
public record Thing(String word, Is is, @Nullable Identifier entity, @Nullable Identifier block, Map<String, String> properties,
		@Nullable Identifier item, float height, Set<String> tags, boolean wearable, boolean lid, Map<Hold.Way, Hold> holds) {

	public enum Is { CREATURE, PLUSH, TOY, PROP, BLOCK, ITEM }

	/**
	 * A way of holding something - in, on, under, or carrying it as a rider - with what it accepts
	 * when a child does it in the world, and the pose: the foot's height above the holder's, how far
	 * back, the scale, the sound, and whether it floats (bobs, in a tub).
	 */
	public record Hold(Way way, List<String> accepts, float y, float z, float scale, Optional<Identifier> sound, boolean floats) {
		public enum Way { IN, ON, UNDER, RIDE }

		/** Whether a thing with these tags may be put here by a child: any one of the tags accepted. */
		public boolean accepts(Thing thing) {
			for (String tag : accepts) if (thing.tags().contains(tag)) return true;
			return false;
		}
	}

	public boolean creature() {
		return is == Is.CREATURE;
	}

	public boolean small() {
		return tags.contains("small");
	}

	/** Can wear a cap or a wig: a creature, a plushie, the doll. */
	public boolean wearer() {
		return tags.contains("wearer");
	}

	public @Nullable Hold hold(Hold.Way way) {
		return holds.get(way);
	}

	public boolean holds(Hold.Way way) {
		return holds.containsKey(way);
	}
}
