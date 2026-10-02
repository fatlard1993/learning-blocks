package justfatlard.phonics.puzzle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.bob.BobScene;
import justfatlard.phonics.bob.Noun;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BobScene} made real on the classroom floor: the creatures as creatures (standing still,
 * unhurtable), everything else as a display of its own block, and where each is - in a box, on a
 * bed, on a cat's head, riding a pig - as near as the game can show it. A creature that rides
 * another really rides it. Shown again after every change, everything glides (or, a creature,
 * pops) to its new place.
 */
final class BobStage {
	/** On everything a stage makes, so it can all be found and tidied away. */
	static final String TAG = Phonics.MOD_ID + ".bob";
	/** How far apart the things standing on the floor are, at full size. */
	private static final double SPACING = 1.35;
	/** How much bigger "big" is. */
	static final float BIG = 1.5F;
	private static final int GLIDE = 6;

	private final ServerLevel level;
	private final Vec3 anchor;
	private final float scale;
	private final Map<String, Entity> made = new LinkedHashMap<>();
	private final Map<String, Vec3> at = new LinkedHashMap<>();

	BobStage(ServerLevel level, Vec3 anchor, float scale) {
		this.level = level;
		this.anchor = anchor;
		this.scale = scale;
	}

	Vec3 anchor() {
		return anchor;
	}

	/** The thing's entity, for tests: a creature, or a display. */
	@Nullable Entity entity(String thing) {
		return made.get(thing);
	}

	/** Where the thing was last put, its foot. */
	@Nullable Vec3 at(String thing) {
		return at.get(thing);
	}

	Map<String, Entity> made() {
		return java.util.Collections.unmodifiableMap(made);
	}

	/** Show the scene: everything in it made if it is not already, and put where it now is. */
	void show(BobScene scene, boolean glide) {
		for (Entity entity : made.values()) {
			entity.stopRiding();
			entity.ejectPassengers();
		}
		// Anything gone from the scene goes from the floor.
		for (String thing : new ArrayList<>(made.keySet())) {
			if (!scene.things().contains(thing)) made.remove(thing).discard();
		}
		List<String> roots = new ArrayList<>();
		for (String thing : scene.things()) if (scene.where(thing) == null) roots.add(thing);
		double spacing = SPACING * scale;
		for (int i = 0; i < roots.size(); i++) {
			double x = anchor.x + (i - (roots.size() - 1) / 2.0) * spacing;
			place(scene, roots.get(i), new Vec3(x, anchor.y, anchor.z), scale, glide, 0);
		}
	}

	/** Everything this stage made, gone. */
	void clear() {
		for (Entity entity : made.values()) {
			entity.ejectPassengers();
			entity.discard();
		}
		made.clear();
		at.clear();
	}

	private void place(BobScene scene, String thing, Vec3 base, float size, boolean glide, int depth) {
		if (depth > 8) return;
		Noun noun = Noun.of(thing);
		if (noun == null) return;
		float own = size * (scene.big(thing) ? BIG : 1.0F);
		Entity entity = made.computeIfAbsent(thing, t -> make(noun));
		if (entity == null) {
			made.remove(thing);
			return;
		}
		move(entity, base, own, glide);
		at.put(thing, base);
		boolean creature = noun.kind() == Noun.Kind.CREATURE;
		float height = creature ? creatureHeight(noun) : noun.height();
		for (String child : scene.things()) {
			BobScene.Place place = scene.where(child);
			if (place == null || !place.parent().equals(thing)) continue;
			Noun c = Noun.of(child);
			if (c == null) continue;
			boolean childCreature = c.kind() == Noun.Kind.CREATURE;
			// Where the parent says a thing sits in, on or under it, if it says; otherwise a fair guess.
			var hold = justfatlard.phonics.relate.Relations.holdFor(noun.thing(), justfatlard.phonics.relate.Relations.Rel.valueOf(place.rel().name()));
			float childSize = hold == null ? 0 : childCreature ? Math.max(hold.scale() * 1.25F, 0.75F) : hold.scale();
			Vec3 seat = hold == null ? null : base.add(0, hold.y() * own, hold.z() * own);
			switch (place.rel()) {
				case IN -> place(scene, child, seat != null ? seat : base.add(0, 0.06 * own, 0),
					own * (hold != null ? childSize : childCreature ? 0.75F : 0.6F), glide, depth + 1);
				case ON -> place(scene, child, seat != null ? seat : base.add(0, height * own, 0),
					hold != null ? own * childSize : childCreature || noun.kind() == Noun.Kind.SURFACE ? size : own * 0.8F, glide, depth + 1);
				case UNDER -> place(scene, child, seat != null ? seat : base, own * (hold != null ? childSize : 0.55F), glide, depth + 1);
				case HEAD -> place(scene, child, base.add(0, height * own * 0.92, 0), own * 0.6F, glide, depth + 1);
				case RIDES -> {
					place(scene, child, seat != null ? seat : base.add(0, height * own * 0.8, 0), own * (hold != null ? childSize : 0.8F), glide, depth + 1);
					Entity rider = made.get(child);
					if (rider != null && childCreature && creature) rider.startRiding(entity, true, false);
				}
			}
		}
	}

	private static float creatureHeight(Noun noun) {
		EntityType<?> type = noun.thing().entity() == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(noun.thing().entity()).orElse(null);
		return type == null ? noun.height() : type.getDimensions().height();
	}

	private void move(Entity entity, Vec3 to, float size, boolean glide) {
		if (entity instanceof Display display) {
			display.setPosRotInterpolationDuration(glide ? GLIDE : 0);
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(glide ? GLIDE : 0);
			boolean flat = display instanceof Display.ItemDisplay;
			display.setTransformation(new com.mojang.math.Transformation(
				flat ? new Vector3f(0, 0.3F * size, 0) : new Vector3f(-0.5F * size, 0, -0.5F * size), null,
				new Vector3f(size, size, size), null));
			if (display.position().distanceToSqr(to) > 1.0E-4) display.teleportTo(to.x, to.y, to.z);
			return;
		}
		if (entity instanceof LivingEntity living && living.getAttribute(Attributes.SCALE) != null) {
			living.getAttribute(Attributes.SCALE).setBaseValue(size);
		}
		if (entity.position().distanceToSqr(to) > 1.0E-4) {
			if (glide) level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.3, entity.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
			entity.snapTo(to.x, to.y, to.z, 0.0F, 0.0F);
			entity.setYHeadRot(0.0F);
			if (entity instanceof LivingEntity living) living.setYBodyRot(0.0F);
			if (glide) level.sendParticles(ParticleTypes.POOF, to.x, to.y + 0.3, to.z, 6, 0.2, 0.2, 0.2, 0.01);
		}
	}

	private @Nullable Entity make(Noun noun) {
		Entity entity;
		Vec3 start = anchor;
		if (noun.kind() == Noun.Kind.CREATURE) {
			EntityType<?> type = noun.thing().entity() == null ? null : Outcome.harmlessType(noun.thing().entity(), false);
			entity = type == null ? null : type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (entity == null) return null;
			entity.setPermanentlyInvulnerable(true);
			entity.setNoGravity(true);
			entity.setSilent(true);
			if (entity instanceof Mob mob) {
				mob.setPersistenceRequired();
				mob.setNoAi(true);
				for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
			}
		} else if (noun.thing().block() == null && noun.thing().item() != null) {
			Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (display == null) return null;
			display.setItemStack(new ItemStack(BuiltInRegistries.ITEM.getValue(noun.thing().item())));
			display.setItemTransform(ItemDisplayContext.FIXED);
			display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
			entity = display;
		} else {
			BlockState state = blockOf(noun);
			if (state == null) return null;
			Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (display == null) return null;
			display.setBlockState(state);
			entity = display;
		}
		entity.addTag(TAG);
		entity.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
		level.addFreshEntity(entity);
		return entity;
	}

	/** The block each thing is shown as, turned to face the child. */
	static @Nullable BlockState blockOf(Noun noun) {
		return blockOf(noun.thing());
	}

	/** The block a thing is, its properties set and turned to face south. */
	static @Nullable BlockState blockOf(justfatlard.phonics.relate.Thing thing) {
		if (thing.block() == null) return null;
		Block block = BuiltInRegistries.BLOCK.getOptional(thing.block()).orElse(null);
		if (block == null || block == Blocks.AIR) return null;
		BlockState state = block.defaultBlockState();
		for (var property : thing.properties().entrySet()) state = with(state, property.getKey(), property.getValue());
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		return state;
	}

	private static BlockState with(BlockState state, String name, String value) {
		for (var property : state.getProperties()) if (property.getName().equals(name)) return set(state, property, value);
		return state;
	}

	private static <T extends Comparable<T>> BlockState set(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
		return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
	}

}
