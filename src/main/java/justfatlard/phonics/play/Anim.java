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
 *
 * <p>A toy drawn in pieces ({@link #pieces}: its body and named parts, from {@code generate_toys.py})
 * can move a part as well: the truck's bed tipping, a wheel turning. A part's poses are in the
 * model's own units (sixteen to a block, as the model is drawn, its front north) about a pivot of
 * its own: moved by x, y, z and turned about the model's x, y and z axes, in degrees, carried along
 * by whatever the whole toy is doing.
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

	/** A part's pose at a tick: moved, in model units, and turned about its pivot round the model's axes. */
	public record Turn(int tick, float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
		public Turn(int tick, float x, float y, float z, float rx, float ry, float rz) {
			this(tick, x, y, z, rx, ry, rz, 1, 1, 1);
		}
	}

	/** A part of a toy drawn in pieces, its pivot in model units, and its poses. */
	public record Part(String name, float px, float py, float pz, List<Turn> turns) {}

	final List<Key> keys = new ArrayList<>();
	final List<Event> events = new ArrayList<>();
	final java.util.Map<String, Part> parts = new java.util.LinkedHashMap<>();
	@Nullable String pieces;
	private @Nullable Part current;

	public static Anim of() {
		return new Anim().key(0, 0, 0, 0);
	}

	public int length() {
		int last = 0;
		for (Key key : keys) last = Math.max(last, key.tick());
		for (Event event : events) last = Math.max(last, event.tick());
		for (Part part : parts.values()) for (Turn turn : part.turns()) last = Math.max(last, turn.tick());
		return last;
	}

	/** Drawn in pieces, as {@code toy_part/<name>_body} and {@code toy_part/<name>_<part>}. */
	public Anim pieces(String toy) {
		pieces = toy;
		return this;
	}

	public @Nullable String pieces() {
		return pieces;
	}

	public java.util.Collection<Part> parts() {
		return parts.values();
	}

	/** The part the poses after this are for, with its pivot (model units); it starts at rest. */
	public Anim part(String name, float px, float py, float pz) {
		current = parts.computeIfAbsent(name, key -> new Part(key, px, py, pz, new ArrayList<>(List.of(new Turn(0, 0, 0, 0, 0, 0, 0)))));
		return this;
	}

	/** The current part, at a tick: moved by x, y, z (model units) and turned about its pivot. */
	public Anim turn(int tick, float x, float y, float z, float rx, float ry, float rz) {
		if (current == null) throw new IllegalStateException("a part's pose with no part named");
		current.turns().add(new Turn(tick, x, y, z, rx, ry, rz));
		return this;
	}

	/** The same, stretched along its own axes too (a kite's string paying out). */
	public Anim turn(int tick, float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
		if (current == null) throw new IllegalStateException("a part's pose with no part named");
		current.turns().add(new Turn(tick, x, y, z, rx, ry, rz, sx, sy, sz));
		return this;
	}

	/** The current part spinning round one axis ({@code 'x'}, {@code 'y'} or {@code 'z'}) from one tick to another, a step every tick. */
	public Anim spin(char axis, int from, int to, float degreesPerTick) {
		for (int tick = from; tick <= to; tick++) {
			float turned = (tick - from) * degreesPerTick;
			turn(tick, 0, 0, 0, axis == 'x' ? turned : 0, axis == 'y' ? turned : 0, axis == 'z' ? turned : 0);
		}
		// Back to rest from the nearest whole turn, so it ends exactly as drawn.
		float last = (to - from) * degreesPerTick;
		float home = Math.round(last / 360.0F) * 360.0F;
		return turn(to + 2, 0, 0, 0, axis == 'x' ? home : 0, axis == 'y' ? home : 0, axis == 'z' ? home : 0);
	}

	/** The whole toy's pose at any tick, between its keys. */
	Key wholeAt(int tick) {
		Key before = null;
		Key after = null;
		for (Key key : keys) {
			if (key.tick() <= tick && (before == null || key.tick() >= before.tick())) before = key;
			if (key.tick() >= tick && (after == null || key.tick() < after.tick())) after = key;
		}
		if (before == null) return after != null ? after : new Key(tick, 0, 0, 0, 0, 0, 0, 1, 1, 1);
		if (after == null || after.tick() == before.tick()) return before;
		float t = (tick - before.tick()) / (float) (after.tick() - before.tick());
		return new Key(tick, lerp(before.x(), after.x(), t), lerp(before.y(), after.y(), t), lerp(before.z(), after.z(), t),
			lerp(before.yaw(), after.yaw(), t), lerp(before.pitch(), after.pitch(), t), lerp(before.roll(), after.roll(), t),
			lerp(before.sx(), after.sx(), t), lerp(before.sy(), after.sy(), t), lerp(before.sz(), after.sz(), t));
	}

	/** A part's pose at any tick, between its poses. */
	static Turn partAt(Part part, int tick) {
		Turn before = null;
		Turn after = null;
		for (Turn turn : part.turns()) {
			if (turn.tick() <= tick && (before == null || turn.tick() >= before.tick())) before = turn;
			if (turn.tick() >= tick && (after == null || turn.tick() < after.tick())) after = turn;
		}
		if (before == null) return after != null ? after : new Turn(tick, 0, 0, 0, 0, 0, 0);
		if (after == null || after.tick() == before.tick()) return before;
		float t = (tick - before.tick()) / (float) (after.tick() - before.tick());
		return new Turn(tick, lerp(before.x(), after.x(), t), lerp(before.y(), after.y(), t), lerp(before.z(), after.z(), t),
			lerp(before.rx(), after.rx(), t), lerp(before.ry(), after.ry(), t), lerp(before.rz(), after.rz(), t),
			lerp(before.sx(), after.sx(), t), lerp(before.sy(), after.sy(), t), lerp(before.sz(), after.sz(), t));
	}

	/** A part's pose as a transformation of the model, centred as an item draws it (a block's middle at nought). */
	public static Matrix4f partMatrix(Part part, Turn turn) {
		org.joml.Vector3f pivot = new org.joml.Vector3f(part.px() / 16 - 0.5F, part.py() / 16 - 0.5F, part.pz() / 16 - 0.5F);
		return new Matrix4f()
			.translate(pivot.x + turn.x() / 16, pivot.y + turn.y() / 16, pivot.z + turn.z() / 16)
			.rotateX((float) Math.toRadians(turn.rx())).rotateY((float) Math.toRadians(turn.ry())).rotateZ((float) Math.toRadians(turn.rz()))
			.scale(turn.sx(), turn.sy(), turn.sz())
			.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	/** Every tick something new is posed at: the whole toy's keys and every part's. */
	java.util.SortedSet<Integer> poseTicks() {
		java.util.SortedSet<Integer> ticks = new java.util.TreeSet<>();
		for (Key key : keys) ticks.add(key.tick());
		for (Part part : parts.values()) for (Turn turn : part.turns()) ticks.add(turn.tick());
		return ticks;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
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
