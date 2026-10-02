package justfatlard.phonics.play;

import java.util.EnumMap;
import java.util.Map;
import justfatlard.phonics.toy.Toy;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * What each toy does when played with, and the plushie squish. Most move as one, the whole body:
 * the van's bounce, the top's spin, the pin tipping over and springing back. A toy whose play needs
 * a moving part is drawn in pieces (see {@code generate_toys.py}), and its part moves too: the
 * truck's bed tips, the bus's doors fold open, the rings come off one by one and go back, the sock
 * puppet's mouth flaps, the kite's tail waves, the jet's propeller and the car's and train's wheels
 * spin. Part pivots are in the model's units, matching where generate_toys.py draws them. Every
 * one ends exactly where it began.
 */
public final class Anims {
	private Anims() {}

	private static SoundEvent note(net.minecraft.core.Holder<SoundEvent> holder) {
		return holder.value();
	}

	private static final Map<Toy, Anim> TOYS = new EnumMap<>(Toy.class);

	/** Plushies all squish alike: down, up, a little settle, soft. */
	public static final Anim SQUISH = Anim.of()
		.squash(3, 1.18F, 0.68F).squash(7, 0.9F, 1.2F).squash(11, 1.06F, 0.94F).squash(15, 0.98F, 1.02F).rest(18)
		.sound(0, SoundEvents.WOOL_HIT, 1.6F).sound(3, SoundEvents.SLIME_SQUISH_SMALL, 1.6F).puff(7, ParticleTypes.HEART, 2, 0.9F);

	static {
		// car: zooms a little loop ahead and to the left, nose round the bend, and parks back.
		Anim car = Anim.of().sound(0, note(SoundEvents.NOTE_BLOCK_BIT), 1.4F).sound(3, note(SoundEvents.NOTE_BLOCK_BIT), 1.4F);
		int steps = 8;
		for (int i = 1; i <= steps; i++) {
			double a = 2 * Math.PI * i / steps;
			float x = (float) (0.7 * (1 - Math.cos(a)));
			float z = (float) (0.7 * Math.sin(a));
			car.key(2 + i * 3, x, 0, z, (float) Math.toDegrees(a), 0, 0);
		}
		car.rest(2 + steps * 3 + 2).sound(2 + steps * 3, note(SoundEvents.NOTE_BLOCK_BIT), 1.8F);
		// Its wheels turn all the way round the loop.
		wheels(car.pieces("car"), new float[] {3.6F, 12.4F}, 1.1F, new float[] {4.5F, 11.5F}, 2, 26, -40);
		TOYS.put(Toy.CAR, car);
		// van: honk-honk and bounces on its springs.
		Anim van = Anim.of().sound(0, note(SoundEvents.NOTE_BLOCK_DIDGERIDOO), 1.2F).sound(5, note(SoundEvents.NOTE_BLOCK_DIDGERIDOO), 1.2F);
		for (int i = 0; i < 4; i++) van.key(2 + i * 6, 0, 0.18F, 0, 0, 0, 0, 0.95F, 1.08F, 0.95F).key(5 + i * 6, 0, 0, 0, 0, 0, 0, 1.06F, 0.92F, 1.06F);
		TOYS.put(Toy.VAN, van.rest(28));
		// bus: beep beep, its doors fold open (pssht) and shut, and it rocks off on its wheels.
		Anim bus = Anim.of().pieces("bus").sound(0, note(SoundEvents.NOTE_BLOCK_BELL), 1.0F).sound(4, note(SoundEvents.NOTE_BLOCK_BELL), 1.0F)
			.sound(6, SoundEvents.PISTON_EXTEND, 1.6F).sound(20, SoundEvents.PISTON_CONTRACT, 1.6F);
		bus.part("door0", 12.5F, 1.4F, 2.0F).turn(6, 0, 0, 0, 0, 0, 0).turn(10, 0, 0, 0, 0, 78, 0).turn(20, 0, 0, 0, 0, 78, 0).turn(24, 0, 0, 0, 0, 0, 0);
		bus.part("door1", 12.5F, 1.4F, 5.6F).turn(6, 0, 0, 0, 0, 0, 0).turn(10, 0, 0, 0, 0, -78, 0).turn(20, 0, 0, 0, 0, -78, 0).turn(24, 0, 0, 0, 0, 0, 0);
		bus.key(24, 0, 0, 0);
		for (int i = 0; i < 2; i++) bus.key(27 + i * 6, 0, 0, 0, 0, 0, 8).key(30 + i * 6, 0, 0, 0, 0, 0, -8);
		TOYS.put(Toy.BUS, bus.rest(40));
		// truck: a rumble, and its dump bed tips up on its hinge at the back, the load sliding out, and
		// settles back down.
		Anim truck = Anim.of().pieces("truck").sound(0, SoundEvents.GRAVEL_BREAK, 0.8F);
		for (int i = 0; i < 3; i++) truck.key(1 + i * 2, 0, 0.02F, 0).key(2 + i * 2, 0, 0, 0);
		truck.part("bed", 8, 1.2F, 15).turn(4, 0, 0, 0, 0, 0, 0).turn(12, 0, 0, 0, 42, 0, 0).turn(22, 0, 0, 0, 42, 0, 0).turn(30, 0, 0, 0, 0, 0, 0);
		truck.sound(12, SoundEvents.GRAVEL_BREAK, 0.6F).sound(15, SoundEvents.GRAVEL_FALL, 0.8F).puff(18, ParticleTypes.POOF, 3, 0.1F)
			.sound(30, SoundEvents.WOOD_HIT, 0.8F);
		TOYS.put(Toy.TRUCK, truck.rest(32));
		// train: chugs forward and back, puffing smoke, with a toot.
		Anim train = Anim.of().sound(0, note(SoundEvents.NOTE_BLOCK_FLUTE), 0.9F).sound(6, note(SoundEvents.NOTE_BLOCK_FLUTE), 1.2F);
		for (int i = 0; i < 4; i++) train.puff(i * 6, ParticleTypes.CAMPFIRE_COSY_SMOKE, 1, 1.0F);
		train.key(10, 0, 0.04F, 0.7F).key(14, 0, 0, 0.8F).key(26, 0, 0.04F, -0.3F).key(30, 0, 0, -0.35F).rest(36);
		// Its wheels roll it forward and back: forward as it goes, and back again as it backs up.
		train.pieces("train");
		float[] trainXs = {3.4F, 12.6F};
		float[] trainZs = {3.6F, 7.6F, 12.0F};
		for (int w = 0; w < 6; w++) {
			train.part("wheel" + w, trainXs[w % 2], 1.2F, trainZs[w / 2]);
			for (int t = 4; t <= 14; t++) train.turn(t, 0, 0, 0, -(t - 4) * 45, 0, 0);
			for (int t = 15; t <= 30; t++) train.turn(t, 0, 0, 0, -450 + (t - 14) * 28.125F, 0, 0);
		}
		TOYS.put(Toy.TRAIN, train);
		// jet: lifts off, circles once overhead, banked, with a whoosh, and lands.
		Anim jet = Anim.of().sound(0, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F).key(6, 0, 1.0F, 0.4F, 0, 25, 0);
		for (int i = 1; i <= 8; i++) {
			double a = 2 * Math.PI * i / 8;
			jet.key(6 + i * 3, (float) (-0.9 * (1 - Math.cos(a))), 1.4F, (float) (0.4 + 0.9 * Math.sin(a)), (float) -Math.toDegrees(a), 0, -25);
		}
		jet.sound(15, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F).key(34, 0, 0.6F, 0.3F, 0, -15, 0).key(40, 0, 0, 0, 0, 0, 0).rest(42);
		// Its propeller whirs all the while.
		jet.pieces("jet").part("prop", 8, 3.5F, -0.2F).spin('z', 0, 38, 45);
		TOYS.put(Toy.JET, jet);
		// ship: bobs and rocks on the waves, a horn and splashes.
		Anim ship = Anim.of().sound(0, note(SoundEvents.NOTE_BLOCK_DIDGERIDOO), 0.6F);
		for (int i = 0; i < 4; i++) ship.key(3 + i * 7, 0, 0.12F, 0, 0, 4, 8).key(7 + i * 7, 0, 0, 0, 0, -4, -8).puff(5 + i * 7, ParticleTypes.SPLASH, 6, 0.1F);
		ship.sound(8, SoundEvents.BOAT_PADDLE_WATER, 1.0F);
		TOYS.put(Toy.SHIP, ship.rest(34));
		// top: spins fast with a whirr, wobbles, and settles.
		Anim top = Anim.of().sound(0, note(SoundEvents.NOTE_BLOCK_CHIME), 1.6F);
		for (int i = 1; i <= 12; i++) top.key(i * 2, 0, 0.05F, 0, i * 120, 0, 0);
		top.sound(12, note(SoundEvents.NOTE_BLOCK_CHIME), 1.9F).key(28, 0, 0.02F, 0, 1440 + 60, 0, 10).key(32, 0, 0, 0, 1440, 0, -8).key(36, 0, 0, 0, 1440, 0, 4);
		TOYS.put(Toy.TOP, top.rest(40));
		// drum: bounces on each beat.
		Anim drum = Anim.of();
		for (int i = 0; i < 4; i++) {
			drum.sound(i * 5, note(SoundEvents.NOTE_BLOCK_BASEDRUM), 1.0F).squash(i * 5 + 1, 1.08F, 0.88F).key(i * 5 + 3, 0, 0.12F, 0);
		}
		TOYS.put(Toy.DRUM, drum.rest(22));
		// kite: rises and flutters on the breeze, and floats back down.
		Anim kite = Anim.of().sound(0, SoundEvents.PHANTOM_FLAP, 1.6F);
		for (int i = 0; i < 6; i++) kite.key(4 + i * 4, 0, 0.3F + Math.min(i, 3) * 0.3F, -0.2F * Math.min(i, 3), 0, 10, i % 2 == 0 ? 16 : -16);
		kite.sound(14, SoundEvents.PHANTOM_FLAP, 1.8F).key(36, 0, 0.3F, -0.1F, 0, 0, 6).rest(42);
		// Its tail waves on the wind, under it.
		kite.pieces("kite").part("tail", 8, 7.8F, 8);
		for (int i = 0; i < 12; i++) kite.turn(3 + i * 3, 0, 0, 0, 0, 0, i % 2 == 0 ? 24 : -24);
		kite.turn(40, 0, 0, 0, 0, 0, 0);
		TOYS.put(Toy.KITE, kite);
		// stacking rings: off the post they come, the top one first, up and over into a pile beside
		// it; then back on, the big one first, till the stack is as it was.
		Anim ring = Anim.of().pieces("ring");
		float[] bottoms = {1.0F, 2.6F, 4.2F, 5.8F, 7.4F};
		float[] dings = {1.0F, 1.12F, 1.26F, 1.4F, 1.6F};
		int back = 38;
		for (int k = 4; k >= 0; k--) {
			int j = 4 - k;
			int off = 2 + 6 * j;
			int on = back + 6 * k;
			float up = 12.4F - bottoms[k];
			float pile = j * 1.6F - bottoms[k];
			ring.part("ring" + k, 8, bottoms[k], 8)
				.turn(off, 0, 0, 0, 0, 0, 0).turn(off + 2, 0, up, 0, 0, 0, 0).turn(off + 4, 11, up, 0, 0, 0, 0).turn(off + 6, 11, pile, 0, 0, 0, 0)
				.turn(on, 11, pile, 0, 0, 0, 0).turn(on + 2, 11, up, 0, 0, 0, 0).turn(on + 4, 0, up, 0, 0, 0, 0).turn(on + 6, 0, 0, 0, 0, 0, 0);
			ring.sound(off + 6, SoundEvents.WOOD_HIT, 1.3F).sound(on + 6, note(SoundEvents.NOTE_BLOCK_BELL), dings[k]);
		}
		TOYS.put(Toy.RING, ring.key(back + 30, 0, 0, 0).rest(back + 32));
		// bowling pin: wobbles, tips over with a clatter, and springs back up.
		TOYS.put(Toy.PIN, Anim.of().key(3, 0, 0, 0, 0, 0, 10).key(6, 0, 0, 0, 0, 0, -10).key(12, 0.2F, 0, 0, 0, 0, 88)
			.sound(12, SoundEvents.WOOD_HIT, 0.8F).sound(13, SoundEvents.WOOD_HIT, 1.1F).key(20, 0.2F, 0, 0, 0, 0, 88).key(25, 0, 0.15F, 0, 0, 0, -8)
			.sound(24, SoundEvents.CHICKEN_EGG, 1.4F).key(28, 0, 0, 0, 0, 0, 4).rest(31));
		// doll: a little dance, side to side with hops, and a wave.
		Anim doll = Anim.of().sound(0, note(SoundEvents.NOTE_BLOCK_HARP), 1.2F);
		for (int i = 0; i < 4; i++) doll.key(3 + i * 5, 0, 0.15F, 0, i % 2 == 0 ? 30 : -30, 0, i % 2 == 0 ? 6 : -6).key(5 + i * 5, 0, 0, 0, 0, 0, 0)
			.sound(3 + i * 5, note(SoundEvents.NOTE_BLOCK_HARP), 1.2F + i * 0.2F);
		TOYS.put(Toy.DOLL, doll.rest(26));
		// ABC block: tumbles end over end, showing its faces, and lands.
		Anim block = Anim.of();
		for (int i = 1; i <= 4; i++) block.key(i * 4, 0, 0.35F * (i % 2), 0, 0, i * 90, 0).sound(i * 4, SoundEvents.WOOD_HIT, 1.2F);
		TOYS.put(Toy.BLOCK, block.rest(20));
		// sock puppet: its mouth flaps open and shut as it says its word, bobbing as it talks.
		Anim sock = Anim.of().pieces("sock").sound(0, SoundEvents.CHICKEN_EGG, 1.7F);
		sock.part("jaw", 8, 8.4F, 6.5F);
		for (int i = 0; i < 6; i++) {
			sock.turn(2 + i * 4, 0, 0, 0, -30, 0, 0).turn(4 + i * 4, 0, 0, 0, 0, 0, 0).sound(2 + i * 4, SoundEvents.WOOL_HIT, 1.4F + (i % 3) * 0.15F);
			sock.key(2 + i * 4, 0, 0.06F, 0, 0, 4, 0).key(4 + i * 4, 0, 0, 0);
		}
		TOYS.put(Toy.SOCK, sock.rest(28));
		// crib: rocks gently, with a lullaby.
		Anim crib = Anim.of();
		float[] lullaby = {0.8F, 0.9F, 0.7F, 0.8F};
		for (int i = 0; i < 4; i++) crib.key(4 + i * 8, 0, 0, 0, 0, 0, i % 2 == 0 ? 7 : -7).sound(i * 8, note(SoundEvents.NOTE_BLOCK_CHIME), lullaby[i]);
		TOYS.put(Toy.CRIB, crib.rest(38));
		// net: swishes, and there's a butterfly in it.
		TOYS.put(Toy.NET, Anim.of().sound(0, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F).key(3, 0, 0.1F, 0, 50, 0, 0).key(6, 0, 0.1F, 0, -50, 0, 0)
			.sound(6, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F).key(9, 0, 0.1F, 0, 30, 0, 0).puff(10, ParticleTypes.SPORE_BLOSSOM_AIR, 6, 1.0F)
			.puff(10, ParticleTypes.HAPPY_VILLAGER, 3, 1.0F).sound(10, note(SoundEvents.NOTE_BLOCK_CHIME), 1.8F).rest(16));
		// cap and wig, put down: spin on their stand.
		for (Toy worn : new Toy[] {Toy.CAP, Toy.WIG}) {
			Anim spin = Anim.of().sound(0, SoundEvents.WOOL_HIT, 1.4F);
			for (int i = 1; i <= 6; i++) spin.key(i * 3, 0, 0.05F, 0, i * 120, 0, 0);
			TOYS.put(worn, spin.rest(22));
		}
	}

	/** A toy's wheels, a part each (wheel0 left and wheel1 right at the first z...), turning round their middles together. */
	private static void wheels(Anim anim, float[] xs, float y, float[] zs, int from, int to, float degreesPerTick) {
		for (int w = 0; w < xs.length * zs.length; w++) anim.part("wheel" + w, xs[w % 2], y, zs[w / 2]).spin('x', from, to, degreesPerTick);
	}

	/** This toy's play. */
	public static Anim of(Toy toy) {
		return TOYS.getOrDefault(toy, SQUISH);
	}

	/** What a thing in a tub does: floats, bobbing. */
	public static final Anim FLOAT = Anim.of().key(4, 0, 0.06F, 0, 0, 0, 5).key(10, 0, -0.02F, 0, 0, 0, -5).key(16, 0, 0.05F, 0, 0, 0, 4)
		.key(22, 0, 0, 0, 0, 0, -2).puff(4, ParticleTypes.SPLASH, 4, 0.4F).sound(0, SoundEvents.PLAYER_SPLASH, 1.6F).rest(26);
}
