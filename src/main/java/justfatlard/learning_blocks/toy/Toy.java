package justfatlard.learning_blocks.toy;

import java.util.Locale;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/** The toys: the second family to collect, place and hear, each a word, each with its own little sound. */
public enum Toy implements StringRepresentable {
	DRUM(Sound.DRUM), KITE(Sound.FLUTTER), TOP(Sound.WHIRR), RING(Sound.DING), CAP(Sound.SQUEAK), WIG(Sound.SQUEAK),
	NET(Sound.FLUTTER), PIN(Sound.CLACK), SOCK(Sound.SQUEAK), CRIB(Sound.CLACK), CAR(Sound.TOOT), VAN(Sound.TOOT),
	JET(Sound.WHOOSH), BUS(Sound.TOOT), SHIP(Sound.TOOT), DOLL(Sound.SQUEAK), BLOCK(Sound.CLACK), TRAIN(Sound.TOOT),
	TRUCK(Sound.TOOT);

	/** A toy's own little noise, as it is squeezed or pops out. */
	public enum Sound {
		SQUEAK(SoundEvents.CHICKEN_EGG, 1.9F), TOOT(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.6F),
		DING(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4F),
		CLACK(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.6F), DRUM(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0F),
		FLUTTER(SoundEvents.WOOL_PLACE, 1.4F), WHIRR(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F),
		WHOOSH(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F);

		public final SoundEvent event;
		public final float pitch;

		Sound(SoundEvent event, float pitch) {
			this.event = event;
			this.pitch = pitch;
		}
	}

	private final String name = name().toLowerCase(Locale.ROOT);
	public final Sound sound;

	Toy(Sound sound) {
		this.sound = sound;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public String word() {
		return name;
	}

	/** Worn on the head: the cap and the wig. */
	public boolean worn() {
		return this == CAP || this == WIG;
	}

	public static @Nullable Toy of(String name) {
		for (Toy toy : values()) if (toy.name.equals(name)) return toy;
		return null;
	}
}
