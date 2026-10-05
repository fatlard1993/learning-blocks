package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.Later;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * What the numbers rounds count with, and how: real things (creatures, apples, carrots, cakes,
 * wooden blocks, the child's own toys) made and flown out of the button to stand in rows of five,
 * as a ten-frame does; the counting chant, "five! one, two, three, four, five!", each one glowing
 * as it is counted, as the blend lights the letter tiles; and the board's big numerals and dots.
 */
final class Counting {
	private Counting() {}

	/** Something to count: a creature, an item, or a block, and its word, said as a plural. */
	enum What {
		PIG(EntityTypes.PIG, null, null, "pigs"), COW(EntityTypes.COW, null, null, "cows"), CHICKEN(EntityTypes.CHICKEN, null, null, "chickens"),
		SHEEP(EntityTypes.SHEEP, null, null, "sheep"), CAT(EntityTypes.CAT, null, null, "cats"), KITTEN(EntityTypes.CAT, null, null, "kittens"),
		VILLAGER(EntityTypes.VILLAGER, null, null, "villagers"),
		APPLE(null, Items.APPLE, null, "apples"), CARROT(null, Items.CARROT, null, "carrots"),
		CAKE(null, null, Blocks.CAKE.defaultBlockState(), "cakes"), BLOCK(null, null, Blocks.OAK_PLANKS.defaultBlockState(), "blocks");

		final @Nullable EntityType<?> creature;
		final net.minecraft.world.item.@Nullable Item item;
		final @Nullable BlockState block;
		final String plural;

		What(@Nullable EntityType<?> creature, net.minecraft.world.item.@Nullable Item item, @Nullable BlockState block, String plural) {
			this.creature = creature;
			this.item = item;
			this.block = block;
			this.plural = plural;
		}

		String clip() {
			return "word/" + plural;
		}

		/** The creatures, for counting rounds that count creatures. */
		static final List<What> CREATURES = List.of(PIG, COW, CHICKEN, SHEEP, CAT);
	}

	/** One of them, made (not yet added): a still, unhurtable creature, or a display. */
	static @Nullable Entity make(ServerLevel level, What what, float size) {
		if (what.creature != null) {
			Entity entity = what.creature.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (entity == null) return null;
			entity.setPermanentlyInvulnerable(true);
			entity.setSilent(true);
			if (entity instanceof Mob mob) {
				mob.setPersistenceRequired();
				mob.setNoAi(true);
				mob.setCanPickUpLoot(false);
				for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
			}
			if (what == What.KITTEN && entity instanceof AgeableMob young) young.setBaby(true);
			// Toy-sized, so ten fit on the floor and none hides the board.
			if (entity instanceof net.minecraft.world.entity.LivingEntity living && living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) != null) {
				living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(Math.min(1.0F, size));
			}
			return entity;
		}
		if (what.item != null) return item(level, new ItemStack(what.item), size);
		return block(level, what.block, size);
	}

	/** An item, shown standing up and turning to face whoever looks. */
	static Display.@Nullable ItemDisplay item(ServerLevel level, ItemStack stack, float size) {
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return null;
		display.setItemStack(stack);
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
		display.setTransformation(new Transformation(new Vector3f(0, 0.3F * size + 0.05F, 0), null, new Vector3f(size, size, size), null));
		return display;
	}

	/** A block, standing on its foot. */
	static Display.@Nullable BlockDisplay block(ServerLevel level, @Nullable BlockState state, float size) {
		if (state == null) return null;
		Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return null;
		display.setBlockState(state);
		display.setTransformation(new Transformation(new Vector3f(-0.5F * size, 0, -0.5F * size), null, new Vector3f(size, size, size), null));
		return display;
	}

	/** Where thing {@code i} of {@code n} stands to be counted: rows of five across the floor, in front of the child. */
	static Vec3 spot(RoomLayout layout, int i, int n) {
		int inRow = Math.min(5, n);
		int row = i / 5;
		int col = i % 5;
		int rows = (n + 4) / 5;
		double x = (col - (inRow - 1) / 2.0) * 1.15;
		// Near the child, low enough not to hide the tiles behind.
		double z = 0.4 + row * 0.9;
		return Vec3.atBottomCenterOf(layout.origin()).add(x, 0, z);
	}

	/**
	 * Out of the button, one after another, each in an arc to its spot; {@code landed} as each lands.
	 * The round keeps them, so they go with it. Returns them, in counting order.
	 */
	static List<Entity> dispense(SubjectRound round, ServerPlayer player, What what, int n, List<Vec3> spots, int stagger, @Nullable Consumer<Entity> landed) {
		return dispense(round, player, what, n, spots, stagger, landed, 0.7F);
	}

	/** The same, at this size. */
	static List<Entity> dispense(SubjectRound round, ServerPlayer player, What what, int n, List<Vec3> spots, int stagger, @Nullable Consumer<Entity> landed, float size) {
		List<Entity> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Entity thing = make(round.level, what, size);
			if (thing == null) continue;
			out.add(thing);
			Vec3 to = spots.get(i);
			int delay = i * stagger;
			fly(round, player, thing, to, delay, landed);
		}
		return out;
	}

	/** One thing out of the button after {@code delay} ticks, in an arc to {@code to}. */
	static void fly(SubjectRound round, ServerPlayer player, Entity thing, Vec3 to, int delay, @Nullable Consumer<Entity> landed) {
		ServerLevel level = round.level;
		Vec3 from = round.layout.mouth();
		thing.setNoGravity(true);
		float yaw = 0.0F;
		thing.snapTo(from.x, from.y - 0.4, from.z, yaw, 0);
		if (thing instanceof Display display) display.setPosRotInterpolationDuration(2);
		Later.after(player, Math.max(1, delay), p -> {
			if (round.room.subjectRound != round) return;
			round.add(thing);
			Vec3 mouth = round.layout.mouth();
			level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.8F, 1.3F);
			level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 4, 0.08, 0.08, 0.08, 0.02);
			int flight = Dispenser.FLIGHT;
			for (int t = 1; t <= flight; t++) {
				double f = t / (double) flight;
				Vec3 at = from.add(0, -0.4, 0).lerp(to, f).add(0, Math.sin(Math.PI * f) * 1.1, 0);
				boolean last = t == flight;
				Later.after(p, t, q -> {
					if (!thing.isAlive()) return;
					if (thing instanceof Display) thing.teleportTo(at.x, at.y, at.z);
					else thing.snapTo(at.x, at.y, at.z, yaw, 0);
					thing.setYHeadRot(yaw);
					if (thing instanceof net.minecraft.world.entity.LivingEntity living) living.setYBodyRot(yaw);
					if (last) {
						level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 3, 0.15, 0.05, 0.15, 0.01);
						if (landed != null) landed.accept(thing);
					}
				});
			}
		});
	}

	/** How long {@code n} things take to all be out and landed. */
	static int dispenseTicks(int n, int stagger) {
		return Math.max(0, n - 1) * stagger + Dispenser.FLIGHT + 2;
	}

	static void glow(@Nullable Entity entity, boolean on) {
		if (entity == null || !entity.isAlive()) return;
		entity.setGlowingTag(on);
		if (on) {
			Vec3 at = entity.position().add(0, entity.getBbHeight() + 0.3, 0);
			((ServerLevel) entity.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 4, 0.2, 0.15, 0.2, 0.0);
		}
	}

	/**
	 * The counting chant: the number, then one, two, three... each lighting the next thing as it is
	 * said, and all lights out after. "five! one, two, three, four, five!"
	 */
	static Narration chant(Narration narration, int total, List<Entity> things, boolean sayTotalFirst) {
		List<String> words = new ArrayList<>();
		if (sayTotalFirst) words.add(Numbers.word(total) + "!");
		for (int i = 1; i <= total; i++) words.add(Numbers.word(i) + (i == total ? "!" : ","));
		int at = 0;
		if (sayTotalFirst) {
			int shown = at;
			narration.say(NumberBlock.soundOf(total), "chant:" + total, p -> {}, CaptionLine.building(words, shown), 4);
			at++;
		}
		for (int i = 1; i <= total; i++) {
			int which = i - 1;
			int shown = at;
			narration.say(NumberBlock.soundOf(i), "count:" + i, p -> glow(which < things.size() ? things.get(which) : null, true),
				CaptionLine.building(words, shown), 3);
			at++;
		}
		narration.then("counted", p -> {
			for (Entity thing : things) glow(thing, false);
		}, 4);
		return narration;
	}

	/** Big text on the board, in the middle (or at {@code x} along it): a numeral, a word, dots. */
	static Display.TextDisplay boardText(SubjectRound round, String text, int colour, float size, double x, double y) {
		Display.TextDisplay display = EntityTypes.TEXT_DISPLAY.create(round.level, EntitySpawnReason.TRIGGERED);
		Vec3 at = Vec3.atCenterOf(round.layout.origin().offset(0, RoomLayout.WORD_Y, -4)).add(x, y, 0.45);
		display.snapTo(at.x, at.y, at.z, 0, 0);
		display.setText(Component.literal(text).withStyle(style -> style.withColor(TextColor.fromRgb(colour))));
		display.setBackgroundColor(0);
		display.setTransformation(new Transformation(null, null, new Vector3f(size, size, size), null));
		return round.add(display);
	}

	/** Dots, as a die's face or ten-frames: one string, rows of five. */
	static String dots(int n) {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < n; i++) text.append(i > 0 && i % 5 == 0 ? "\n" : "").append("●");
		return text.toString();
	}

	/** The number tile's dotted side shown big on the board: a block display of the tile, turned to show its side. */
	static Display.BlockDisplay boardDots(SubjectRound round, int n, float size) {
		Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(round.level, EntitySpawnReason.TRIGGERED);
		display.setBlockState(justfatlard.learning_blocks.LearningBlocks.NUMBER.showing(n));
		// Low on the board, under the caption, in full view.
		Vec3 at = Vec3.atBottomCenterOf(round.layout.origin().offset(0, 1, -4)).add(0, -0.2, 0.2);
		display.snapTo(at.x, at.y, at.z, 0, 0);
		// Turned a quarter so its east side, the dots, faces the room; centred on its spot.
		org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateY((float) (Math.PI / 2));
		Vector3f half = new Vector3f(-0.5F * size, 0, -0.5F * size);
		turn.transform(half);
		display.setTransformation(new Transformation(half, turn, new Vector3f(size, size, size), null));
		return round.add(display);
	}
}
