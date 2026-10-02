package justfatlard.phonics.puzzle;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * A little problem the room acts out, which one word fixes: a villager in the rain, and a hat.
 * Written in a word's file as {@code scene}:
 *
 * <pre>{@code
 * "scene": {
 *   "line": "Oh no! He's getting wet!",
 *   "setup": [ { "type": "character" }, { "type": "ambient", "id": "rain", "particle": "minecraft:falling_water", "at": "over" } ],
 *   "solve": [ { "type": "give", "item": "minecraft:leather_helmet", "then": "wear" } ]
 * }
 * }</pre>
 *
 * <p>{@code line} is said instead of "Can you make...", recorded as {@code scene/<word>}. {@code setup}
 * is in the room while the scene lasts; {@code solve} is what the word does to it, one step after
 * another. After the solve, whatever was wrong stops by itself: the rain, the snow, the dark, the
 * thought bubbles, the pacing about, and the character is pleased.
 *
 * @param line  the spoken setup, which the sound generator records
 * @param setup what is in the room during the scene
 * @param solve what the right word does in it
 */
public record Scene(String line, List<Step> setup, List<Step> solve) {

	public static final Codec<Scene> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("line").forGetter(Scene::line),
		Step.CODEC.listOf(0, 12).optionalFieldOf("setup", List.of()).forGetter(Scene::setup),
		Step.CODEC.listOf(0, 8).optionalFieldOf("solve", List.of()).forGetter(Scene::solve)
	).apply(instance, Scene::new));

	/** Whether everything named is here: creatures harmless, items, blocks, particles and sounds all known. */
	public boolean usable() {
		return setup.stream().allMatch(Step::usable) && solve.stream().allMatch(Step::usable)
			&& setup.stream().filter(step -> step instanceof Character).count() <= SceneRun.MAX_CHARACTERS;
	}

	public Optional<Character> character() {
		return setup.stream().filter(step -> step instanceof Character).map(step -> (Character) step).findFirst();
	}

	/** How long the solve takes, step after step, before the room says "That helped!". */
	public int solveTicks() {
		int ticks = 0;
		for (Step step : solve) ticks += step.ticks();
		return ticks + SceneRun.FINISH;
	}

	enum Kind implements StringRepresentable {
		CHARACTER, AMBIENT, BUBBLE, DIM, LITTER, BLOCK, GIVE, PLACE, BURST, LIGHT,
		WRAP, RING, PROP, POSE, VISIT, TUNE, EFFECT, HUT;

		static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		MapCodec<? extends Step> codec() {
			return switch (this) {
				case CHARACTER -> Character.CODEC;
				case AMBIENT -> Ambient.CODEC;
				case BUBBLE -> Bubble.CODEC;
				case DIM -> MapCodec.unit(new Dim());
				case LITTER -> Litter.CODEC;
				case BLOCK -> Block.CODEC;
				case GIVE -> Give.CODEC;
				case PLACE -> Place.CODEC;
				case BURST -> Burst.CODEC;
				case LIGHT -> MapCodec.unit(new Light());
				case WRAP -> Wrap.CODEC;
				case RING -> Ring.CODEC;
				case PROP -> Prop.CODEC;
				case POSE -> Pose.CODEC;
				case VISIT -> Visit.CODEC;
				case TUNE -> Tune.CODEC;
				case EFFECT -> Effect.CODEC;
				case HUT -> Hut.CODEC;
			};
		}
	}

	public sealed interface Step permits Character, Ambient, Bubble, Dim, Litter, Block, Give, Place, Burst, Light,
			Wrap, Ring, Prop, Pose, Visit, Tune, Effect, Hut {
		Codec<Step> CODEC = Kind.CODEC.dispatch("type", Step::kind, Kind::codec);

		Kind kind();

		boolean usable();

		/** How long it takes in a solve before the next step. */
		default int ticks() {
			return 6;
		}
	}

	private static boolean known(Optional<Identifier> sound) {
		return sound.map(BuiltInRegistries.SOUND_EVENT::containsKey).orElse(true);
	}

	// --- Setup ---

	/**
	 * Who the scene is about, standing on the floor to the left, facing the child: a villager unless
	 * it says otherwise, and never anything that could hurt anyone. It does not wander off or trade.
	 *
	 * @param act {@code still}, {@code wander} (pacing to and fro, lost), {@code shiver}, {@code bounce}
	 *            (hopping on the spot, too excited), {@code stuck} (sunk to the waist in the floor) or
	 *            {@code hungry} (looking over at the button now and then, where food might come from)
	 */
	public record Character(Identifier entity, boolean baby, String act) implements Step {
		static final MapCodec<Character> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("entity", Identifier.withDefaultNamespace("villager")).forGetter(Character::entity),
			Codec.BOOL.optionalFieldOf("baby", false).forGetter(Character::baby),
			Codec.STRING.optionalFieldOf("act", "still").forGetter(Character::act)
		).apply(instance, Character::new));

		@Override
		public Kind kind() {
			return Kind.CHARACTER;
		}

		@Override
		public boolean usable() {
			return Outcome.harmlessType(entity, baby) != null && List.of("still", "wander", "shiver", "bounce", "stuck", "hungry").contains(act);
		}
	}

	/**
	 * Particles, and now and then a sound, kept up over the character (or the middle of the room)
	 * until the scene is solved: rain from a cloud, snowflakes, sad drips.
	 *
	 * @param at    {@code over} (a cloud's height), {@code head}, {@code face} or {@code body}
	 * @param drift on solving, whether it drifts off sideways rather than just stopping
	 */
	public record Ambient(String id, Dynamic<?> particle, int count, double spread, String at, int every,
			Optional<Identifier> sound, float pitch, int soundEvery, boolean drift) implements Step {
		static final MapCodec<Ambient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("id", "ambient").forGetter(Ambient::id),
			Codec.PASSTHROUGH.fieldOf("particle").forGetter(Ambient::particle),
			Codec.intRange(1, 40).optionalFieldOf("count", 4).forGetter(Ambient::count),
			Codec.doubleRange(0.0, 2.0).optionalFieldOf("spread", 0.3).forGetter(Ambient::spread),
			Codec.STRING.optionalFieldOf("at", "head").forGetter(Ambient::at),
			Codec.intRange(1, 40).optionalFieldOf("every", 4).forGetter(Ambient::every),
			Identifier.CODEC.optionalFieldOf("sound").forGetter(Ambient::sound),
			Codec.floatRange(0.5F, 2.0F).optionalFieldOf("pitch", 1.0F).forGetter(Ambient::pitch),
			Codec.intRange(10, 200).optionalFieldOf("sound_every", 40).forGetter(Ambient::soundEvery),
			Codec.BOOL.optionalFieldOf("drift", false).forGetter(Ambient::drift)
		).apply(instance, Ambient::new));

		@Override
		public Kind kind() {
			return Kind.AMBIENT;
		}

		@Override
		public boolean usable() {
			return Outcome.particleOf(particle) != null && known(sound) && List.of("over", "head", "face", "body", "beside").contains(at);
		}
	}

	/** A thought over the character's head, bobbing: "Zzz", "?". */
	public record Bubble(String text, int color) implements Step {
		static final MapCodec<Bubble> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("text").forGetter(Bubble::text),
			Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Bubble::color)
		).apply(instance, Bubble::new));

		@Override
		public Kind kind() {
			return Kind.BUBBLE;
		}

		@Override
		public boolean usable() {
			return !text.isBlank();
		}
	}

	/** The room goes dim for the child, until the scene is solved or the round ends. */
	public record Dim() implements Step {
		@Override
		public Kind kind() {
			return Kind.DIM;
		}

		@Override
		public boolean usable() {
			return true;
		}
	}

	/** Bits and bobs lying about the floor, to look at, not to pick up. */
	public record Litter(List<Identifier> items) implements Step {
		static final MapCodec<Litter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.listOf(1, 8).fieldOf("items").forGetter(Litter::items)
		).apply(instance, Litter::new));

		@Override
		public Kind kind() {
			return Kind.LITTER;
		}

		@Override
		public boolean usable() {
			return items.stream().allMatch(BuiltInRegistries.ITEM::containsKey);
		}
	}

	/**
	 * Blocks that are part of the scene: the water cauldron he is looking at, a puddle, dead bushes.
	 * {@code at} is where, as squares east and south of the scene's spot and, optionally, up (beside
	 * the character by default); {@code becomes}, what they turn into when it is solved (air to be gone).
	 */
	public record Block(Identifier block, Map<String, String> properties, List<List<Integer>> at, Optional<Identifier> becomes) implements Step {
		static final MapCodec<Block> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("block").forGetter(Block::block),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(Block::properties),
			Codec.intRange(-1, 3).listOf(2, 3).listOf(1, 6).optionalFieldOf("at", List.of(List.of(1, 0))).forGetter(Block::at),
			Identifier.CODEC.optionalFieldOf("becomes").forGetter(Block::becomes)
		).apply(instance, Block::new));

		@Override
		public Kind kind() {
			return Kind.BLOCK;
		}

		@Override
		public boolean usable() {
			return Outcome.blockStateOf(block, properties) != null && becomes.map(BuiltInRegistries.BLOCK::containsKey).orElse(true);
		}
	}

	/** Items wound round the character: vines he is tangled in. */
	public record Wrap(Identifier item, int count) implements Step {
		static final MapCodec<Wrap> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(Wrap::item),
			Codec.intRange(1, 8).optionalFieldOf("count", 4).forGetter(Wrap::count)
		).apply(instance, Wrap::new));

		@Override
		public Kind kind() {
			return Kind.WRAP;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.ITEM.containsKey(item);
		}
	}

	/** A ring of blocks round the scene's spot, a little fence with something inside. */
	public record Ring(Identifier block) implements Step {
		static final MapCodec<Ring> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("block").forGetter(Ring::block)
		).apply(instance, Ring::new));

		@Override
		public Kind kind() {
			return Kind.RING;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.BLOCK.containsKey(block);
		}
	}

	/** Something to look at on the scene's spot, turning slowly: the treasure inside the fence. */
	public record Prop(Identifier item) implements Step {
		static final MapCodec<Prop> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(Prop::item)
		).apply(instance, Prop::new));

		@Override
		public Kind kind() {
			return Kind.PROP;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.ITEM.containsKey(item);
		}
	}

	// --- Solve ---

	/** Every character in the scene settles: {@code sit} or {@code lie} (a pet lying down). */
	public record Pose(String pose) implements Step {
		static final MapCodec<Pose> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("pose").forGetter(Pose::pose)
		).apply(instance, Pose::new));

		@Override
		public Kind kind() {
			return Kind.POSE;
		}

		@Override
		public boolean usable() {
			return List.of("sit", "lie").contains(pose);
		}
	}

	/**
	 * Someone arrives from the button to help, and stays: a bee to the flowers ({@code over}), a
	 * cleric to the poorly pet ({@code beside}), a fish landing by the pond ({@code front}).
	 */
	public record Visit(Identifier entity, boolean baby, Optional<Identifier> profession, String to) implements Step {
		static final MapCodec<Visit> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("entity").forGetter(Visit::entity),
			Codec.BOOL.optionalFieldOf("baby", false).forGetter(Visit::baby),
			Identifier.CODEC.optionalFieldOf("profession").forGetter(Visit::profession),
			Codec.STRING.optionalFieldOf("to", "beside").forGetter(Visit::to)
		).apply(instance, Visit::new));

		@Override
		public Kind kind() {
			return Kind.VISIT;
		}

		@Override
		public boolean usable() {
			return Outcome.harmlessType(entity, baby) != null && List.of("beside", "over", "front").contains(to)
				&& profession.map(BuiltInRegistries.VILLAGER_PROFESSION::containsKey).orElse(true);
		}

		@Override
		public int ticks() {
			return Dispenser.FLIGHT + 6;
		}
	}

	/** A little tune over the character, as the word outcome plays one: a lullaby. */
	public record Tune(String instrument, List<Integer> notes, int every) implements Step {
		static final MapCodec<Tune> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("instrument", "harp").forGetter(Tune::instrument),
			Codec.intRange(0, 24).listOf(1, 24).fieldOf("notes").forGetter(Tune::notes),
			Codec.intRange(2, 20).optionalFieldOf("every", 6).forGetter(Tune::every)
		).apply(instance, Tune::new));

		@Override
		public Kind kind() {
			return Kind.TUNE;
		}

		@Override
		public boolean usable() {
			return SceneRun.voice(instrument) != null;
		}

		@Override
		public int ticks() {
			return notes.size() * every + 4;
		}
	}

	/** An effect on the child: jump boost to hop the fence. */
	public record Effect(Identifier effect, int duration, int amplifier) implements Step {
		static final MapCodec<Effect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("effect").forGetter(Effect::effect),
			Codec.intRange(1, 20 * 60).optionalFieldOf("duration", 200).forGetter(Effect::duration),
			Codec.intRange(0, 3).optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier)
		).apply(instance, Effect::new));

		@Override
		public Kind kind() {
			return Kind.EFFECT;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.MOB_EFFECT.get(effect).map(found -> found.value().isBeneficial()).orElse(false);
		}
	}

	/** A tiny hut built round the scene's spot, the character inside, to stay the rest of the round. */
	public record Hut(int lasts) implements Step {
		static final MapCodec<Hut> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(20, 20 * 60).optionalFieldOf("lasts", 300).forGetter(Hut::lasts)
		).apply(instance, Hut::new));

		@Override
		public Kind kind() {
			return Kind.HUT;
		}

		@Override
		public boolean usable() {
			return true;
		}

		@Override
		public int ticks() {
			return 10;
		}
	}

	/**
	 * An item flies from the button to the character (to every one, with {@code all}), and then:
	 * {@code wear} (on their head), {@code hold}, {@code eat}, {@code drink}, {@code snip} (the vines
	 * round them fall away), {@code pull} (a stuck one is pulled free) or {@code soak} (the puddle
	 * is soaked up).
	 */
	public record Give(Identifier item, String then, boolean all) implements Step {
		static final MapCodec<Give> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(Give::item),
			Codec.STRING.optionalFieldOf("then", "hold").forGetter(Give::then),
			Codec.BOOL.optionalFieldOf("all", false).forGetter(Give::all)
		).apply(instance, Give::new));

		@Override
		public Kind kind() {
			return Kind.GIVE;
		}

		@Override
		public boolean usable() {
			return BuiltInRegistries.ITEM.containsKey(item) && List.of("wear", "hold", "eat", "drink", "snip", "pull", "soak").contains(then);
		}

		@Override
		public int ticks() {
			return Dispenser.FLIGHT + (then.equals("eat") || then.equals("drink") ? SceneRun.MUNCH : 6);
		}
	}

	/**
	 * A block flies from the button to the character's side (or the middle of the scene), and then:
	 * {@code sleep} (the character lies down on it, for a bed), {@code absorb} (the litter is pulled
	 * into it) or {@code none}.
	 */
	public record Place(Identifier block, Map<String, String> properties, String then) implements Step {
		static final MapCodec<Place> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("block").forGetter(Place::block),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(Place::properties),
			Codec.STRING.optionalFieldOf("then", "none").forGetter(Place::then)
		).apply(instance, Place::new));

		@Override
		public Kind kind() {
			return Kind.PLACE;
		}

		@Override
		public boolean usable() {
			return Outcome.blockStateOf(block, properties) != null && List.of("sleep", "absorb", "none").contains(then);
		}

		@Override
		public int ticks() {
			return Dispenser.FLIGHT + (then.equals("absorb") ? SceneRun.ABSORB : 8);
		}
	}

	/** Particles all at once around the character, with a sound: hearts, a warm glow, a cool breeze. */
	public record Burst(Dynamic<?> particle, int count, double spread, Optional<Identifier> sound, float pitch) implements Step {
		static final MapCodec<Burst> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.PASSTHROUGH.fieldOf("particle").forGetter(Burst::particle),
			Codec.intRange(1, 200).optionalFieldOf("count", 20).forGetter(Burst::count),
			Codec.doubleRange(0.0, 3.0).optionalFieldOf("spread", 0.5).forGetter(Burst::spread),
			Identifier.CODEC.optionalFieldOf("sound").forGetter(Burst::sound),
			Codec.floatRange(0.5F, 2.0F).optionalFieldOf("pitch", 1.0F).forGetter(Burst::pitch)
		).apply(instance, Burst::new));

		@Override
		public Kind kind() {
			return Kind.BURST;
		}

		@Override
		public boolean usable() {
			return Outcome.particleOf(particle) != null && known(sound);
		}

		@Override
		public int ticks() {
			return 12;
		}
	}

	/** The dim lifts now, rather than at the end of the solve. */
	public record Light() implements Step {
		@Override
		public Kind kind() {
			return Kind.LIGHT;
		}

		@Override
		public boolean usable() {
			return true;
		}
	}
}
