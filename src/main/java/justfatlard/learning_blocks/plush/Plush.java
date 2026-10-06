package justfatlard.learning_blocks.plush;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/** The plushies: soft toys to collect, place and hear, each a word. */
public enum Plush implements StringRepresentable {
	OWL, DUCK, ANT, RAT, OX, SHARK, CRAB, WORM, MOTH, YAK, TEDDY, SNAIL, SEAL, MOUSE, SNAKE, SKUNK, HAWK, LADYBUG, MOLE, CROW, PUG, DEER, WHALE, FLY, BEETLE, MICE,
	BOBCAT, CATFISH, STARFISH, CHIPMUNK, PUFFIN, ROBIN, LOBSTER, HEDGEHOG;

	private final String name = name().toLowerCase(Locale.ROOT);

	@Override
	public String getSerializedName() {
		return name;
	}

	public String word() {
		return name;
	}

	public static @Nullable Plush of(String name) {
		for (Plush plush : values()) if (plush.name.equals(name)) return plush;
		return null;
	}
}
