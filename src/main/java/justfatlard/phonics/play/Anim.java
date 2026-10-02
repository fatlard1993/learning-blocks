package justfatlard.phonics.play;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * One play animation: the whole toy moving, as key poses a few ticks apart that the client glides
 * between, with sounds and puffs along the way. Poses are the toy's own: x to its right, y up, z
 * the way it faces; turns in degrees (yaw round y, pitch nose up, roll to its right); a scale that
 * can squash. Everything moves about the middle of the toy's foot, so a squash stays on the floor.
 */
public final class Anim {
	/** A pose at a tick. */
	public record Key(int tick, float x, float y, float z, float yaw, float pitch, float roll, float sx, float sy, float sz) {
		/** This pose, in the world, for a toy facing {@code facingYaw} radians (its forward turned from south). */
		Matrix4f matrix(float facingYaw) {
			Quaternionf facing = new Quaternionf().rotateY(facingYaw);
			org.joml.Vector3f moved = facing.transform(new org.joml.Vector3f(x, y, z));
			return new Matrix4f()
				.translate(moved)
				.rotate(facing)
				.rotate(new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(-pitch)).rotateZ((float) Math.toRadians(roll)))
				.scale(sx, sy, sz)
				.rotate(new Quaternionf(facing).conjugate());
		}
	}

	/** Something heard or seen at a tick: a sound, a puff of particles at a height over the toy, or a line said. */
	public record Event(int tick, @Nullable SoundEvent sound, float pitch, @Nullable ParticleOptions particle, int count, float height) {}

	final List<Key> keys = new ArrayList<>();
	final List<Event> events = new ArrayList<>();

	public static Anim of() {
		return new Anim().key(0, 0, 0, 0);
	}

	public int length() {
		int last = 0;
		for (Key key : keys) last = Math.max(last, key.tick());
		for (Event event : events) last = Math.max(last, event.tick());
		return last;
	}

	public List<Key> keys() {
		return keys;
	}

	public List<Event> events() {
		return events;
	}

	public Anim key(int tick, float x, float y, float z) {
		return key(tick, x, y, z, 0, 0, 0);
	}

	public Anim key(int tick, float x, float y, float z, float yaw, float pitch, float roll) {
		return key(tick, x, y, z, yaw, pitch, roll, 1, 1, 1);
	}

	public Anim key(int tick, float x, float y, float z, float yaw, float pitch, float roll, float sx, float sy, float sz) {
		keys.add(new Key(tick, x, y, z, yaw, pitch, roll, sx, sy, sz));
		return this;
	}

	/** A squash or stretch, standing still. */
	public Anim squash(int tick, float wide, float tall) {
		return key(tick, 0, 0, 0, 0, 0, 0, wide, tall, wide);
	}

	public Anim sound(int tick, SoundEvent sound, float pitch) {
		events.add(new Event(tick, sound, pitch, null, 0, 0));
		return this;
	}

	public Anim puff(int tick, ParticleOptions particle, int count, float height) {
		events.add(new Event(tick, null, 1, particle, count, height));
		return this;
	}

	/** Back to rest, so the block it hands back to is exactly where it started. */
	public Anim rest(int tick) {
		return key(tick, 0, 0, 0);
	}

	/** A puff of the default kind. */
	public Anim poof(int tick) {
		return puff(tick, ParticleTypes.POOF, 4, 0.4F);
	}
}
