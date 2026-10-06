package justfatlard.learning_blocks.puzzle;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What reading a word does: the thing the word names turns up.
 *
 * <p>Data, one of these kinds, each naming what it needs by id:
 *
 * <pre>{@code
 * { "type": "spawn_entity", "entity": "minecraft:wolf", "baby": true, "tamed_to_player": true, "sitting": false,
 *   "profession": "minecraft:nitwit" }
 * { "type": "equip_item",   "item": "minecraft:leather_helmet", "slot": "head", "components": { ... } }
 * { "type": "give_item",    "item": "minecraft:snowball", "count": 10, "components": { ... } }
 * { "type": "effect",       "effect": "minecraft:speed", "duration": 200, "amplifier": 0 }
 * { "type": "place_block",  "block": "minecraft:brown_carpet", "properties": { }, "at": "feet" | "near" }
 * { "type": "scale",        "amount": 0.8, "duration": 160 }
 * { "type": "particles",    "particle": "minecraft:heart", "count": 12, "spread": 0.6, "at": "player" | "above",
 *   "sound": "minecraft:entity.player.splash" }
 * { "type": "push",         "back": 0.9, "up": 0.3 }
 * { "type": "firework_burst", "shape": "large_ball", "colors": [ 16711680, 65280 ] }
 * { "type": "creatures_come" }
 * { "type": "sequence",     "steps": [ { ... }, { ... } ] }
 * { "type": "sky",          "time": "night", "glide": 40, "flicker": false }
 * { "type": "weather",      "weather": "rain", "lasts": 0 }
 * { "type": "character",    "entity": "minecraft:villager", "face": "sad", "gesture": "nod", "lasts": 100 }
 * { "type": "dark",         "lasts": 60, "flicker": false }
 * }</pre>
 *
 * <p>Everything comes out of the room's button, dispenser-fashion, so the child sees the word they
 * read cause it: a creature launched out in a short arc, an item thrown to their feet, a hat flying to
 * their head, a block flying to its spot, a trail of sparks carrying an effect, a firework going up;
 * a sequence sends its parts out one after another. See {@link Dispenser}.
 *
 * <p>Nothing a word does can hurt anybody. A creature has to be in the {@code
 * learning-blocks-justfatlard:harmless} tag, or in {@code harmless_as_baby} and spawned as a baby, and never
 * a monster; it arrives invulnerable and the room keeps only a few. An effect has to be beneficial or
 * neutral. A placed block goes only on open floor the room is not using, and is taken away at the
 * next round and on the way out. A word whose outcome fails any of this is treated exactly like one
 * whose ids are missing: {@link #usable} says no, and no puzzle ever offers it.
 */
public sealed interface Outcome {

	/** Whether every id resolves and nothing it would do is harmful. Asked when a puzzle is picked. */
	boolean usable();

	void run(ServerPlayer player, RoomEffects room);

	Kind kind();

	TagKey<EntityType<?>> HARMLESS = TagKey.create(Registries.ENTITY_TYPE, LearningBlocks.id("harmless"));
	/** Creatures that are harmless only young: a polar bear cub, with no parent about to mind it. */
	TagKey<EntityType<?>> HARMLESS_AS_BABY = TagKey.create(Registries.ENTITY_TYPE, LearningBlocks.id("harmless_as_baby"));

	/** A particle named by its id alone is the common case: {@code "minecraft:heart"}; or a full object, such as dust with a colour. */
	static @Nullable ParticleOptions particleOf(Dynamic<?> particle) {
		JsonElement json = particle.convert(JsonOps.INSTANCE).getValue();
		if (json.isJsonPrimitive()) {
			JsonObject wrapped = new JsonObject();
			wrapped.add("type", json);
			json = wrapped;
		}
		return ParticleTypes.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
	}

	static @Nullable BlockState blockStateOf(Identifier block, Map<String, String> properties) {
		Block found = BuiltInRegistries.BLOCK.getOptional(block).orElse(null);
		if (found == null) return null;
		BlockState state = found.defaultBlockState();
		StateDefinition<Block, BlockState> definition = found.getStateDefinition();
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			Property<?> property = definition.getProperty(entry.getKey());
			if (property == null) return null;
			BlockState set = withValue(state, property, entry.getValue());
			if (set == null) return null;
			state = set;
		}
		return state;
	}

	private static <T extends Comparable<T>> @Nullable BlockState withValue(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(null);
	}

	/** The creature this names, if it is one the classroom allows: harmless, or harmless as a baby and a baby. */
	static @Nullable EntityType<?> harmlessType(Identifier entity, boolean baby) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).orElse(null);
		if (type == null || type.getCategory() == MobCategory.MONSTER) return null;
		var holder = type.builtInRegistryHolder();
		return holder.is(HARMLESS) || baby && holder.is(HARMLESS_AS_BABY) ? type : null;
	}

	/** Tag on everything a word brought, so a room can find and tidy it away. */
	String SPAWNED_TAG = LearningBlocks.MOD_ID + ".spawned";

	enum Kind implements StringRepresentable {
		SPAWN_ENTITY, EQUIP_ITEM, GIVE_ITEM, EFFECT, PLACE_BLOCK, SCALE, PARTICLES, PUSH, FIREWORK_BURST, CREATURES_COME, SEQUENCE,
		THROW_AT_PLAYER, BONK, SPIN, CREATURES_GO, ROLL, TUNE, PULL, HUT, CREATURES_DO, STRIKE, PREFER, SKY, WEATHER, CHARACTER, DARK;

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		MapCodec<? extends Outcome> codec(Codec<Outcome> self) {
			return switch (this) {
				case SPAWN_ENTITY -> SpawnEntity.CODEC;
				case EQUIP_ITEM -> EquipItem.CODEC;
				case GIVE_ITEM -> GiveItem.CODEC;
				case EFFECT -> Effect.CODEC;
				case PLACE_BLOCK -> PlaceBlock.CODEC;
				case SCALE -> Scale.CODEC;
				case PARTICLES -> Particles.CODEC;
				case PUSH -> Push.CODEC;
				case FIREWORK_BURST -> FireworkBurst.CODEC;
				case CREATURES_COME -> MapCodec.unit(new CreaturesCome());
				case CREATURES_GO -> MapCodec.unit(new CreaturesGo());
				case THROW_AT_PLAYER -> ThrowAtPlayer.CODEC;
				case BONK -> Bonk.CODEC;
				case SPIN -> Spin.CODEC;
				case ROLL -> Roll.CODEC;
				case TUNE -> Tune.CODEC;
				case PULL -> Pull.CODEC;
				case HUT -> Hut.CODEC;
				case CREATURES_DO -> CreaturesDo.CODEC;
				case STRIKE -> Strike.CODEC;
				case SKY -> Sky.CODEC;
				case WEATHER -> Weather.CODEC;
				case CHARACTER -> Character.CODEC;
				case DARK -> Dark.CODEC;
				case PREFER -> self.listOf(1, 4).fieldOf("options").xmap(Prefer::new, Prefer::options);
				case SEQUENCE -> self.listOf(1, 8).fieldOf("steps").xmap(Sequence::new, Sequence::steps);
			};
		}
	}

	Codec<Outcome> CODEC = Codec.recursive("outcome", self -> Kind.CODEC.dispatch("type", Outcome::kind, kind -> kind.codec(self)));


	/**
	 * This child's sky, theirs only: it glides on to {@code day}, {@code dusk}, {@code night} or
	 * {@code dawn} and stays so until the next round. A {@code flicker} is only a blink of it and back,
	 * for a word built by mistake.
	 */
	record Sky(String time, Optional<String> from, int glide, boolean flicker) implements Outcome {
		static final MapCodec<Sky> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("time").forGetter(Sky::time),
			Codec.STRING.optionalFieldOf("from").forGetter(Sky::from),
			Codec.intRange(0, 200).optionalFieldOf("glide", 40).forGetter(Sky::glide),
			Codec.BOOL.optionalFieldOf("flicker", false).forGetter(Sky::flicker)
		).apply(instance, Sky::new));

		@Override
		public Kind kind() {
			return Kind.SKY;
		}

		private static justfatlard.learning_blocks.classroom.Sky.@Nullable Time named(String time) {
			for (justfatlard.learning_blocks.classroom.Sky.Time one : justfatlard.learning_blocks.classroom.Sky.Time.values()) {
				if (one.name().toLowerCase(Locale.ROOT).equals(time)) return one;
			}
			return null;
		}

		public justfatlard.learning_blocks.classroom.Sky.@Nullable Time to() {
			return named(time);
		}

		@Override
		public boolean usable() {
			return named(time) != null && from.map(name -> named(name) != null).orElse(true);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			justfatlard.learning_blocks.classroom.Sky.Time to = named(time);
			if (to == null) return;
			if (flicker) {
				room.puffFromButton("sky");
				justfatlard.learning_blocks.classroom.Sky.flicker(player, to.ticks);
				return;
			}
			// From another time first, at once: the night before the sun comes up.
			justfatlard.learning_blocks.classroom.Sky.Time start = from.map(Sky::named).orElse(null);
			room.dispenseEffect(player, p -> {
				if (start != null) justfatlard.learning_blocks.classroom.Sky.setTime(p, start.ticks);
				justfatlard.learning_blocks.classroom.Sky.glide(p, to.ticks, glide);
			});
		}
	}

	/**
	 * This child's weather, theirs only: {@code rain}, {@code drizzle}, {@code snow}, {@code storm} (a
	 * rumble and a flash, never a bolt), or a {@code cloud} drifting over. It stays until the next
	 * round, or for {@code lasts} ticks if that is set.
	 */
	record Weather(String weather, int lasts) implements Outcome {
		static final MapCodec<Weather> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("weather").forGetter(Weather::weather),
			Codec.intRange(0, 20 * 60).optionalFieldOf("lasts", 0).forGetter(Weather::lasts)
		).apply(instance, Weather::new));

		@Override
		public Kind kind() {
			return Kind.WEATHER;
		}

		@Override
		public boolean usable() {
			return weather.equals("cloud") || justfatlard.learning_blocks.classroom.Sky.weatherNamed(weather) != null;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			if (weather.equals("cloud")) {
				room.puffFromButton("cloud");
				justfatlard.learning_blocks.classroom.Sky.cloud(player.level(), Vec3.atBottomCenterOf(room.origin()), lasts > 0 ? lasts : 140);
				return;
			}
			justfatlard.learning_blocks.classroom.Sky.Weather to = justfatlard.learning_blocks.classroom.Sky.weatherNamed(weather);
			if (to == null) return;
			room.dispenseEffect(player, p -> justfatlard.learning_blocks.classroom.Sky.setWeather(p, to));
			if (lasts > 0) justfatlard.learning_blocks.Later.after(player, Dispenser.FLIGHT + lasts, p -> {
				if (justfatlard.learning_blocks.classroom.Sky.weatherOf(p) == to) justfatlard.learning_blocks.classroom.Sky.setWeather(p, justfatlard.learning_blocks.classroom.Sky.Weather.CLEAR);
			});
		}
	}

	/**
	 * A character hops out of the button to stand in front of the child, feeling something (a face
	 * in a bubble over it) and doing something (a nod, a wave, a dance), then goes in a puff. A
	 * villager unless it says otherwise; harmless, like any creature a word brings.
	 */
	record Character(Identifier entity, boolean baby, Optional<Identifier> profession, Optional<String> face, Optional<String> gesture,
			int lasts) implements Outcome {
		static final MapCodec<Character> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("entity", Identifier.withDefaultNamespace("villager")).forGetter(Character::entity),
			Codec.BOOL.optionalFieldOf("baby", false).forGetter(Character::baby),
			Identifier.CODEC.optionalFieldOf("profession").forGetter(Character::profession),
			Codec.STRING.optionalFieldOf("face").forGetter(Character::face),
			Codec.STRING.optionalFieldOf("gesture").forGetter(Character::gesture),
			Codec.intRange(20, 20 * 30).optionalFieldOf("lasts", 100).forGetter(Character::lasts)
		).apply(instance, Character::new));

		@Override
		public Kind kind() {
			return Kind.CHARACTER;
		}

		@Override
		public boolean usable() {
			return harmlessType(entity, baby) != null && profession.map(BuiltInRegistries.VILLAGER_PROFESSION::containsKey).orElse(true)
				&& face.map(name -> justfatlard.learning_blocks.feel.Feelings.Face.named(name) != null).orElse(true)
				&& gesture.map(name -> justfatlard.learning_blocks.feel.Gestures.Gesture.named(name) != null).orElse(true);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			EntityType<?> type = harmlessType(entity, baby);
			if (type == null) return;
			Entity made = type.create(player.level(), EntitySpawnReason.MOB_SUMMONED);
			if (made == null) return;
			made.setPermanentlyInvulnerable(true);
			made.addTag(SPAWNED_TAG);
			if (made instanceof Mob mob) {
				mob.setPersistenceRequired();
				if (baby) mob.setBaby(true);
				for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
				// It stands still to be looked at, and takes nothing: a villager would pick up the next word's carrots.
				mob.setCanPickUpLoot(false);
			}
			if (made instanceof Villager villager && profession.isPresent()) {
				BuiltInRegistries.VILLAGER_PROFESSION.get(profession.get()).ifPresent(found -> {
					villager.setVillagerData(villager.getVillagerData().withProfession(found));
					villager.setVillagerDataFinalized(true);
				});
			}
			room.dispenseCharacter(player, made, landed -> {
				face.map(justfatlard.learning_blocks.feel.Feelings.Face::named).ifPresent(feeling -> justfatlard.learning_blocks.feel.Feelings.show(landed, feeling, lasts));
				gesture.map(justfatlard.learning_blocks.feel.Gestures.Gesture::named).ifPresent(doing -> {
					justfatlard.learning_blocks.feel.Gestures.run(landed, doing);
					// Done twice over, if there is time: a wave, then another.
					if (lasts > doing.length * 2 + 20) justfatlard.learning_blocks.Later.after(player, doing.length + 16, p -> {
						if (landed.isAlive()) justfatlard.learning_blocks.feel.Gestures.run(landed, doing);
					});
				});
			}, lasts);
		}
	}

	/** The lights go out, for this child only, then click back on; a {@code flicker} is two blinks of it. */
	record Dark(int lasts, boolean flicker) implements Outcome {
		static final MapCodec<Dark> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(4, 20 * 10).optionalFieldOf("lasts", 60).forGetter(Dark::lasts),
			Codec.BOOL.optionalFieldOf("flicker", false).forGetter(Dark::flicker)
		).apply(instance, Dark::new));

		@Override
		public Kind kind() {
			return Kind.DARK;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.puffFromButton("dark");
			if (flicker) {
				justfatlard.learning_blocks.classroom.Sky.dark(player, 4);
				justfatlard.learning_blocks.Later.after(player, 9, p -> justfatlard.learning_blocks.classroom.Sky.dark(p, 4));
				return;
			}
			justfatlard.learning_blocks.classroom.Sky.dark(player, lasts);
		}
	}


	/** What a creature a word brings may do once out. */
	List<String> ACTS = List.of("none", "flee", "spin", "circle", "bounce", "bonk", "sneeze");

	/**
	 * @param act   what it does once out: {@code none}, {@code flee} to the far corner, {@code spin}
	 *              chasing its tail, {@code circle} round the child's head (for something that flies),
	 *              {@code bounce} on the spot, {@code bonk} (off the wrong way into the wall) or {@code sneeze}
	 * @param lasts ticks before it vanishes in a puff; 0 to stay (the room keeps only a few)
	 */
	record SpawnEntity(Identifier entity, boolean baby, boolean tamedToPlayer, boolean sitting, Optional<Identifier> profession,
			String act, int lasts) implements Outcome {
		static final MapCodec<SpawnEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("entity").forGetter(SpawnEntity::entity),
			Codec.BOOL.optionalFieldOf("baby", false).forGetter(SpawnEntity::baby),
			Codec.BOOL.optionalFieldOf("tamed_to_player", false).forGetter(SpawnEntity::tamedToPlayer),
			Codec.BOOL.optionalFieldOf("sitting", false).forGetter(SpawnEntity::sitting),
			Identifier.CODEC.optionalFieldOf("profession").forGetter(SpawnEntity::profession),
			Codec.STRING.optionalFieldOf("act", "none").forGetter(SpawnEntity::act),
			Codec.intRange(0, 20 * 30).optionalFieldOf("lasts", 0).forGetter(SpawnEntity::lasts)
		).apply(instance, SpawnEntity::new));

		@Override
		public Kind kind() {
			return Kind.SPAWN_ENTITY;
		}

		private @Nullable EntityType<?> type() {
			return harmlessType(entity, baby);
		}

		@Override
		public boolean usable() {
			return type() != null && profession.map(BuiltInRegistries.VILLAGER_PROFESSION::containsKey).orElse(true)
				&& ACTS.contains(act);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			EntityType<?> type = type();
			if (type == null) return;
			ServerLevel level = player.level();
			Entity spawned = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (spawned == null) return;
			spawned.setPermanentlyInvulnerable(true);
			spawned.addTag(SPAWNED_TAG);
			if (spawned instanceof Mob mob) {
				mob.setPersistenceRequired();
				if (baby) mob.setBaby(true);
				// Nothing a word brings takes what the next word gives: a villager would pick up carrots, a fox food.
				mob.setCanPickUpLoot(false);
			}
			if (spawned instanceof TamableAnimal pet && tamedToPlayer) {
				// Not tame(): that awards "Best Friends Forever", and nothing in the classroom counts.
				pet.setTame(true, true);
				pet.setOwner(player);
				if (sitting) {
					pet.setOrderedToSit(true);
					pet.setInSittingPose(true);
				}
			}
			if (spawned instanceof Villager villager && profession.isPresent()) {
				BuiltInRegistries.VILLAGER_PROFESSION.get(profession.get()).ifPresent(found -> {
					Holder<VillagerProfession> holder = found;
					villager.setVillagerData(villager.getVillagerData().withProfession(holder));
					villager.setVillagerDataFinalized(true);
				});
			}
			room.dispenseCreature(player, spawned);
			room.act(player, spawned, act, lasts);
		}
	}

	/** Every creature the room has made hops over to the child: what a hug looks like from outside. */
	record CreaturesCome() implements Outcome {
		@Override
		public Kind kind() {
			return Kind.CREATURES_COME;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> come(p, room));
		}

		private static void come(ServerPlayer player, RoomEffects room) {
			for (Entity creature : room.creatures()) {
				Vec3 toward = player.position().subtract(creature.position());
				Vec3 flat = new Vec3(toward.x, 0, toward.z);
				if (flat.lengthSqr() > 1.0E-4) flat = flat.normalize().scale(0.35);
				creature.setDeltaMovement(flat.x, 0.42, flat.z);
				creature.needsSync = true;
				if (creature instanceof Mob mob) mob.getNavigation().moveTo(player, 1.1);
			}
		}
	}


	private static ItemStack stack(ServerPlayer player, Identifier item, int count, Optional<Dynamic<?>> components) {
		return stack(player.level().registryAccess(), item, count, components);
	}

	static ItemStack stack(net.minecraft.core.HolderLookup.Provider registries, Identifier item, int count, Optional<Dynamic<?>> components) {
		Item it = BuiltInRegistries.ITEM.getOptional(item).orElse(Items.AIR);
		ItemStack plain = new ItemStack(it, count);
		if (components.isEmpty()) return plain;
		JsonObject json = new JsonObject();
		json.addProperty("id", item.toString());
		json.addProperty("count", count);
		json.add("components", components.get().convert(JsonOps.INSTANCE).getValue());
		return ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
			.resultOrPartial(error -> LearningBlocks.LOGGER.warn("[{}] Components for {} did not read: {}", LearningBlocks.MOD_ID, item, error))
			.orElse(plain);
	}

	record EquipItem(Identifier item, String slot, int count, Optional<Dynamic<?>> components) implements Outcome {
		static final MapCodec<EquipItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(EquipItem::item),
			Codec.STRING.optionalFieldOf("slot", "head").forGetter(EquipItem::slot),
			Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(EquipItem::count),
			Codec.PASSTHROUGH.optionalFieldOf("components").forGetter(EquipItem::components)
		).apply(instance, EquipItem::new));

		@Override
		public Kind kind() {
			return Kind.EQUIP_ITEM;
		}

		private @Nullable EquipmentSlot equipment() {
			for (EquipmentSlot equipment : EquipmentSlot.values()) if (equipment.getName().equals(slot)) return equipment;
			return null;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.ITEM.containsKey(item) && equipment() != null;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			EquipmentSlot equipment = equipment();
			if (equipment == null) return;
			ItemStack worn = stack(player, item, count, components);
			room.dispenseWorn(player, worn, p -> p.setItemSlot(equipment, worn.copy()));
		}
	}

	record GiveItem(Identifier item, int count, Optional<Dynamic<?>> components) implements Outcome {
		static final MapCodec<GiveItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(GiveItem::item),
			Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(GiveItem::count),
			Codec.PASSTHROUGH.optionalFieldOf("components").forGetter(GiveItem::components)
		).apply(instance, GiveItem::new));

		@Override
		public Kind kind() {
			return Kind.GIVE_ITEM;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.ITEM.containsKey(item);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseItem(player, stack(player, item, count, components));
		}
	}


	record Effect(Identifier effect, int duration, int amplifier) implements Outcome {
		static final MapCodec<Effect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("effect").forGetter(Effect::effect),
			Codec.intRange(1, 20 * 60 * 5).optionalFieldOf("duration", 200).forGetter(Effect::duration),
			Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier)
		).apply(instance, Effect::new));

		@Override
		public Kind kind() {
			return Kind.EFFECT;
		}

		private @Nullable Holder<MobEffect> holder() {
			Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(effect).map(ref -> (Holder<MobEffect>) ref).orElse(null);
			return holder == null || holder.value().getCategory() == MobEffectCategory.HARMFUL ? null : holder;
		}

		@Override
		public boolean usable() {
			return holder() != null;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			Holder<MobEffect> holder = holder();
			if (holder != null) room.dispenseEffect(player, p -> p.addEffect(new MobEffectInstance(holder, duration, amplifier)));
		}
	}

	/** Bigger (or smaller) for a while, through the scale attribute; always put back. */
	record Scale(double amount, int duration) implements Outcome {
		static final MapCodec<Scale> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			// Down to a fifth of the size, for a word that shrinks the child for a moment.
			Codec.doubleRange(-0.8, 1.0).fieldOf("amount").forGetter(Scale::amount),
			Codec.intRange(1, 20 * 30).optionalFieldOf("duration", 160).forGetter(Scale::duration)
		).apply(instance, Scale::new));

		@Override
		public Kind kind() {
			return Kind.SCALE;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> room.scale(p, amount, duration));
		}
	}

	/** A shove, backwards from where the child is looking and a little up. The classroom hurts nobody. */
	record Push(double back, double up) implements Outcome {
		static final MapCodec<Push> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.doubleRange(-2.0, 2.0).optionalFieldOf("back", 0.0).forGetter(Push::back),
			Codec.doubleRange(0.0, 2.0).optionalFieldOf("up", 0.0).forGetter(Push::up)
		).apply(instance, Push::new));

		@Override
		public Kind kind() {
			return Kind.PUSH;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, this::shove);
		}

		private void shove(ServerPlayer player) {
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() > 1.0E-4) flat = flat.normalize();
			player.setDeltaMovement(player.getDeltaMovement().add(-flat.x * back, up, -flat.z * back));
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}


	/** @param lasts ticks after landing before it goes again in a puff of smoke; 0 to stay until the next round */
	record PlaceBlock(Identifier block, Map<String, String> properties, String at, int lasts) implements Outcome {
		static final MapCodec<PlaceBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("block").forGetter(PlaceBlock::block),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(PlaceBlock::properties),
			Codec.STRING.optionalFieldOf("at", "near").forGetter(PlaceBlock::at),
			Codec.intRange(0, 20 * 30).optionalFieldOf("lasts", 0).forGetter(PlaceBlock::lasts)
		).apply(instance, PlaceBlock::new));

		@Override
		public Kind kind() {
			return Kind.PLACE_BLOCK;
		}

		private @Nullable BlockState state() {
			return blockStateOf(block, properties);
		}

		@Override
		public boolean usable() {
			return state() != null && (at.equals("near") || at.equals("feet"));
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			BlockState state = state();
			if (state != null) room.dispenseBlock(player, state, at.equals("feet"), lasts);
		}
	}

	/**
	 * @param at     {@code player}, {@code above} their head, or {@code face}, just in front of their eyes
	 * @param repeat how many bursts, {@code every} ticks apart: a drizzle rather than a splash
	 */
	record Particles(Dynamic<?> particle, int count, double spread, String at, Optional<Identifier> sound, float pitch,
			int repeat, int every) implements Outcome {
		static final MapCodec<Particles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.PASSTHROUGH.fieldOf("particle").forGetter(Particles::particle),
			Codec.intRange(1, 200).optionalFieldOf("count", 16).forGetter(Particles::count),
			Codec.doubleRange(0.0, 4.0).optionalFieldOf("spread", 0.6).forGetter(Particles::spread),
			Codec.STRING.optionalFieldOf("at", "player").forGetter(Particles::at),
			Identifier.CODEC.optionalFieldOf("sound").forGetter(Particles::sound),
			Codec.floatRange(0.5F, 2.0F).optionalFieldOf("pitch", 1.0F).forGetter(Particles::pitch),
			Codec.intRange(1, 20).optionalFieldOf("repeat", 1).forGetter(Particles::repeat),
			Codec.intRange(1, 20).optionalFieldOf("every", 4).forGetter(Particles::every)
		).apply(instance, Particles::new));

		@Override
		public Kind kind() {
			return Kind.PARTICLES;
		}

		private @Nullable ParticleOptions options() {
			return particleOf(particle);
		}

		@Override
		public boolean usable() {
			return options() != null && sound.map(BuiltInRegistries.SOUND_EVENT::containsKey).orElse(true);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> {
				burst(p);
				for (int i = 1; i < repeat; i++) room.later(p, i * every, this::burst);
			});
		}

		private void burst(ServerPlayer player) {
			ParticleOptions options = options();
			if (options == null) return;
			ServerLevel level = player.level();
			Vec3 where = switch (at) {
				case "above" -> player.position().add(0, player.getBbHeight() + 0.8, 0);
				case "face" -> player.getEyePosition().add(player.getLookAngle().scale(0.6));
				default -> player.position().add(0, player.getBbHeight() * 0.6, 0);
			};
			level.sendParticles(options, where.x, where.y, where.z, count, spread, spread * 0.6, spread, 0.02);
			sound.flatMap(BuiltInRegistries.SOUND_EVENT::get).ifPresent(found -> {
				Holder<SoundEvent> holder = found;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), holder, SoundSource.PLAYERS, 1.0F, pitch);
			});
		}
	}

	/**
	 * A firework bursting over the child. A real rocket, because a burst is drawn only by the client
	 * watching a rocket explode; its blast is harmless here, where the classroom lets nothing be hurt.
	 * A {@code dud} goes up, fizzles out in smoke, and never bursts.
	 */
	record FireworkBurst(FireworkExplosion.Shape shape, List<Integer> colors, boolean dud) implements Outcome {
		static final MapCodec<FireworkBurst> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			FireworkExplosion.Shape.CODEC.optionalFieldOf("shape", FireworkExplosion.Shape.LARGE_BALL).forGetter(FireworkBurst::shape),
			Codec.INT.listOf().optionalFieldOf("colors", List.of(0xFF5555, 0xFFD84A, 0x55FF55, 0x55AAFF, 0xCC77FF)).forGetter(FireworkBurst::colors),
			Codec.BOOL.optionalFieldOf("dud", false).forGetter(FireworkBurst::dud)
		).apply(instance, FireworkBurst::new));

		@Override
		public Kind kind() {
			return Kind.FIREWORK_BURST;
		}

		@Override
		public boolean usable() {
			return !colors.isEmpty();
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			IntList list = new IntArrayList(colors);
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS, dud ? new Fireworks(0, List.of())
				: new Fireworks(0, List.of(new FireworkExplosion(shape, list, IntList.of(), true, true))));
			room.dispenseFirework(player, rocket, dud);
		}
	}

	/** Every creature the room has made runs off from the child: a hug turned down. */
	record CreaturesGo() implements Outcome {
		@Override
		public Kind kind() {
			return Kind.CREATURES_GO;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> room.scatterCreatures(p));
		}
	}

	/**
	 * Snowballs thrown from the button at the child, one after another. They hurt nobody and push
	 * nobody here: the classroom lets nothing take damage, and a hit that does no damage knocks
	 * nothing back. Snowballs only: anything else thrown could hatch or break something.
	 */
	record ThrowAtPlayer(int count, int every) implements Outcome {
		static final MapCodec<ThrowAtPlayer> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(1, 16).optionalFieldOf("count", 1).forGetter(ThrowAtPlayer::count),
			Codec.intRange(1, 20).optionalFieldOf("every", 3).forGetter(ThrowAtPlayer::every)
		).apply(instance, ThrowAtPlayer::new));

		@Override
		public Kind kind() {
			return Kind.THROW_AT_PLAYER;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			for (int i = 0; i < count; i++) {
				if (i == 0) room.throwSnowball(player);
				else room.later(player, i * every, room::throwSnowball);
			}
		}
	}

	/**
	 * Items fly from the button at the child's head, bonk, and fall off onto the floor, to lie there
	 * a moment and go: a hat that did not quite land.
	 */
	record Bonk(List<Identifier> items, Optional<Identifier> sound) implements Outcome {
		static final MapCodec<Bonk> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.listOf(1, 6).fieldOf("items").forGetter(Bonk::items),
			Identifier.CODEC.optionalFieldOf("sound").forGetter(Bonk::sound)
		).apply(instance, Bonk::new));

		@Override
		public Kind kind() {
			return Kind.BONK;
		}

		@Override
		public boolean usable() {
			return items.stream().allMatch(BuiltInRegistries.ITEM::containsKey)
				&& sound.map(BuiltInRegistries.SOUND_EVENT::containsKey).orElse(true);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			List<ItemStack> stacks = items.stream().map(id -> new ItemStack(BuiltInRegistries.ITEM.getValue(id))).toList();
			Holder<SoundEvent> bonk = sound.flatMap(BuiltInRegistries.SOUND_EVENT::get).<Holder<SoundEvent>>map(found -> found)
				.orElse(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(net.minecraft.sounds.SoundEvents.WOOD_HIT));
			room.bonk(player, stacks, bonk);
		}
	}

	/** The child turned round on the spot, a little dizzily, over a short while. */
	record Spin(float turns, int ticks) implements Outcome {
		static final MapCodec<Spin> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.floatRange(0.1F, 2.0F).optionalFieldOf("turns", 1.0F).forGetter(Spin::turns),
			Codec.intRange(4, 60).optionalFieldOf("ticks", 16).forGetter(Spin::ticks)
		).apply(instance, Spin::new));

		@Override
		public Kind kind() {
			return Kind.SPIN;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> room.spin(p, turns, ticks));
		}
	}

	/** Something rolls out of the button and right across the floor, and is gone at the far wall. */
	record Roll(Identifier item, int ticks) implements Outcome {
		static final MapCodec<Roll> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(Roll::item),
			Codec.intRange(10, 80).optionalFieldOf("ticks", 36).forGetter(Roll::ticks)
		).apply(instance, Roll::new));

		@Override
		public Kind kind() {
			return Kind.ROLL;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.ITEM.containsKey(item);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.roll(player, new ItemStack(BuiltInRegistries.ITEM.getValue(item)), ticks);
		}
	}

	/**
	 * A little tune on a note block's voice, over the child's head, a note at a time: a lullaby, a
	 * hum, a sad "womp womp".
	 *
	 * @param notes semitones, 0 to 24 as a note block counts them (12 is the middle)
	 */
	record Tune(String instrument, List<Integer> notes, int every) implements Outcome {
		static final MapCodec<Tune> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("instrument", "harp").forGetter(Tune::instrument),
			Codec.intRange(0, 24).listOf(1, 24).fieldOf("notes").forGetter(Tune::notes),
			Codec.intRange(2, 20).optionalFieldOf("every", 5).forGetter(Tune::every)
		).apply(instance, Tune::new));

		@Override
		public Kind kind() {
			return Kind.TUNE;
		}

		private java.util.Optional<net.minecraft.world.level.block.state.properties.NoteBlockInstrument> voice() {
			for (var voice : net.minecraft.world.level.block.state.properties.NoteBlockInstrument.values()) {
				if (voice.getSerializedName().equals(instrument) && !voice.hasCustomSound()) return java.util.Optional.of(voice);
			}
			return java.util.Optional.empty();
		}

		@Override
		public boolean usable() {
			return voice().isPresent();
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			var voice = voice().orElseThrow();
			room.dispenseEffect(player, p -> {
				for (int i = 0; i < notes.size(); i++) {
					int note = notes.get(i);
					room.later(p, i * every, q -> {
						// A drum has no pitch: every note is the same beat.
						float pitch = voice.isTunable() ? (float) Math.pow(2.0, (note - 12) / 12.0) : 1.0F;
						Vec3 at = q.position().add(0, q.getBbHeight() + 0.6, 0);
						q.level().playSound(null, at.x, at.y, at.z, voice.getSoundEvent(), SoundSource.RECORDS, 0.8F, pitch);
						q.level().sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 0, note / 24.0, 0, 0, 1.0);
					});
				}
			});
		}
	}

	/** The child pulled towards the button, as if on a rope. */
	record Pull(double strength) implements Outcome {
		static final MapCodec<Pull> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.doubleRange(0.1, 2.0).optionalFieldOf("strength", 1.0).forGetter(Pull::strength)
		).apply(instance, Pull::new));

		@Override
		public Kind kind() {
			return Kind.PULL;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> {
				Vec3 toward = room.mouth().subtract(p.position());
				Vec3 flat = new Vec3(toward.x, 0, toward.z);
				if (flat.lengthSqr() > 1.0E-4) flat = flat.normalize().scale(strength);
				p.setDeltaMovement(p.getDeltaMovement().add(flat.x, 0.25, flat.z));
				p.connection.send(new ClientboundSetEntityMotionPacket(p));
			});
		}
	}

	/**
	 * A tiny wooden hut around the child: walls with a doorway always open, a roof only over a
	 * child of ordinary size, and gone in a puff after {@code lasts} ticks. Never a trap.
	 */
	record Hut(int lasts, Identifier block) implements Outcome {
		static final MapCodec<Hut> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(10, 20 * 20).optionalFieldOf("lasts", 100).forGetter(Hut::lasts),
			Identifier.CODEC.optionalFieldOf("block", Identifier.withDefaultNamespace("oak_planks")).forGetter(Hut::block)
		).apply(instance, Hut::new));

		@Override
		public Kind kind() {
			return Kind.HUT;
		}

		@Override
		public boolean usable() {
			return blockStateOf(block, Map.of()) != null;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			BlockState walls = blockStateOf(block, Map.of());
			if (walls != null) room.dispenseEffect(player, p -> room.hut(p, p.blockPosition(), lasts, false, walls));
		}
	}

	/** Every creature the room has made does something: hearts over each, sit, spin, hop, bounce, sneeze, or freeze a moment. */
	record CreaturesDo(String what) implements Outcome {
		static final MapCodec<CreaturesDo> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("what").forGetter(CreaturesDo::what)
		).apply(instance, CreaturesDo::new));

		@Override
		public Kind kind() {
			return Kind.CREATURES_DO;
		}

		@Override
		public boolean usable() {
			return List.of("hearts", "sit", "spin", "hop", "freeze", "sneeze", "bounce").contains(what);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.dispenseEffect(player, p -> {
				for (Entity creature : room.creatures()) room.creatureDoes(p, creature, what);
			});
		}
	}

	/** A lightning rod out of the button, and a little harmless bolt of sparks onto it: zap. */
	record Strike(int lasts) implements Outcome {
		static final MapCodec<Strike> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(10, 20 * 20).optionalFieldOf("lasts", 60).forGetter(Strike::lasts)
		).apply(instance, Strike::new));

		@Override
		public Kind kind() {
			return Kind.STRIKE;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			room.strike(player, lasts);
		}
	}

	/**
	 * The first of these this server can run: a mod's thing where the mod is installed, and a
	 * vanilla one where it is not ({@code lets-cook}'s pie, else a pumpkin pie).
	 */
	record Prefer(List<Outcome> options) implements Outcome {
		@Override
		public Kind kind() {
			return Kind.PREFER;
		}

		public @Nullable Outcome chosen() {
			for (Outcome option : options) if (option.usable()) return option;
			return null;
		}

		@Override
		public boolean usable() {
			return chosen() != null;
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			Outcome option = chosen();
			if (option != null) option.run(player, room);
		}
	}

	/** Several at once, in order: a shovel and a burst of speed. */
	record Sequence(List<Outcome> steps) implements Outcome {
		@Override
		public Kind kind() {
			return Kind.SEQUENCE;
		}

		@Override
		public boolean usable() {
			return !steps.isEmpty() && steps.stream().allMatch(Outcome::usable);
		}

		@Override
		public void run(ServerPlayer player, RoomEffects room) {
			for (int i = 0; i < steps.size(); i++) {
				Outcome step = steps.get(i);
				if (i == 0) step.run(player, room);
				else room.later(player, i * Dispenser.STAGGER, p -> step.run(p, room));
			}
		}
	}
}
