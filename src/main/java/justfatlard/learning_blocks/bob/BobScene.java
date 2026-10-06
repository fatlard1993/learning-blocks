package justfatlard.learning_blocks.bob;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Where everything is: each thing in, on or under another, riding it, on its head, or on the
 * floor. Bob changes it one instruction at a time, literally: a box put in a cat goes on the
 * cat's head; a cat put on a pig rides it; a thing put under a box ends up with the box on top of
 * it; and nothing goes in a jar with its lid on.
 */
public final class BobScene {
	public enum Rel { IN, ON, UNDER, RIDES, HEAD }

	public record Place(Rel rel, String parent) {}

	private final Set<String> things = new LinkedHashSet<>();
	private final Map<String, Place> where = new LinkedHashMap<>();
	private final Set<String> big = new LinkedHashSet<>();

	public BobScene(List<String> things) {
		this.things.addAll(things);
	}

	public BobScene copy() {
		BobScene copy = new BobScene(List.copyOf(things));
		copy.where.putAll(where);
		copy.big.addAll(big);
		return copy;
	}

	public Set<String> things() {
		return java.util.Collections.unmodifiableSet(things);
	}

	public @Nullable Place where(String thing) {
		return where.get(thing);
	}

	public boolean big(String thing) {
		return big.contains(thing);
	}

	public @Nullable String lidOn(String container) {
		for (Map.Entry<String, Place> entry : where.entrySet()) {
			Noun noun = Noun.of(entry.getKey());
			if (noun != null && noun.kind() == Noun.Kind.LID && entry.getValue().rel() == Rel.ON && entry.getValue().parent().equals(container)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** Whether {@code inner} is somewhere inside or on {@code outer}, however deep. */
	private boolean within(String inner, String outer) {
		String at = inner;
		for (int i = 0; i < 16; i++) {
			Place place = where.get(at);
			if (place == null) return false;
			if (place.parent().equals(outer)) return true;
			at = place.parent();
		}
		return false;
	}

	/** Do this, exactly, by the shared rules ({@link justfatlard.learning_blocks.relate.Relations#resolve}). False if Bob could do nothing with it. */
	public boolean apply(Instruction instruction) {
		String x = instruction.x();
		String y = instruction.y();
		var placement = justfatlard.learning_blocks.relate.Relations.resolve(x, instruction.prep(), y, this::lidOn);
		if (placement == null) return false;
		things.add(x);
		things.add(y);
		if (instruction.bigX()) big.add(x);
		if (instruction.bigY()) big.add(y);
		if (placement.parent().equals(placement.child()) || within(placement.parent(), placement.child())) return false;
		where.put(placement.child(), new Place(Rel.valueOf(placement.rel().name()), placement.parent()));
		return true;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BobScene scene && where.equals(scene.where) && big.equals(scene.big);
	}

	@Override
	public int hashCode() {
		return Objects.hash(where, big);
	}

	@Override
	public String toString() {
		return where + (big.isEmpty() ? "" : " big " + big);
	}
}
