package justfatlard.phonics.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Viewport;
import justfatlard.phonics.Phonics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A scene being played in a room: who is in it, what is falling on them, what is lying about, and
 * whether the lights are down; then the solve, and everything back to normal. Everything it makes
 * is its own and goes when the scene ends: at the next round, on leaving, on logging out.
 */
public final class SceneRun {
	/** After the last solve step: the trouble stops and the character cheers up. */
	static final int FINISH = 20;
	/** Eating or drinking, after the item arrives. */
	static final int MUNCH = 30;
	/** The litter pulled into the composter. */
	static final int ABSORB = 24;
	/** Characters in one scene: the one it is about, and up to two more beside. */
	static final int MAX_CHARACTERS = 3;
	/** Where each character stands, from the scene's spot: the first on it, the others in front and behind. */
	private static final int[][] SLOTS = {{0, 0}, {0, 1}, {0, -1}};

	static final String DIM_OVERLAY = Phonics.MOD_ID + ":dim";
	private static final String DIM_COLOUR = "#B8000008";

	private final Stage room;
	private final ServerLevel level;
	private final Word word;
	private final Scene scene;
	/** Where the first character stands, or the middle of a scene without one; the two squares east are kept for its bed or block. */
	private final BlockPos spot;

	private final List<Entity> characters = new ArrayList<>();
	private final Map<Entity, String> acts = new HashMap<>();
	private final Map<Entity, Vec3> homes = new HashMap<>();
	private float facing;
	private final List<Scene.Ambient> ambients = new ArrayList<>();
	private final List<Display.TextDisplay> bubbles = new ArrayList<>();
	private final List<Display.ItemDisplay> litter = new ArrayList<>();
	private final List<Display.ItemDisplay> wraps = new ArrayList<>();
	private final List<Display.ItemDisplay> props = new ArrayList<>();
	/** Scene blocks and what each becomes when the scene is solved. */
	private final Map<BlockPos, BlockState> becomes = new HashMap<>();
	/** Every entity the scene made, the characters included, to be gone with it. */
	private final List<Entity> made = new ArrayList<>();
	private final Set<BlockPos> occupied = new LinkedHashSet<>();
	private int setupBubbles;
	private boolean dimmed;
	private boolean everDimmed;
	private boolean solved;
	private long ticks;

	SceneRun(Stage room, ServerLevel level, Word word, Scene scene, BlockPos spot) {
		this.room = room;
		this.level = level;
		this.word = word;
		this.scene = scene;
		this.spot = spot;
		occupied.add(spot);
		occupied.add(spot.east());
		occupied.add(spot.east(2));
		for (int[] slot : SLOTS) occupied.add(spot.offset(slot[0], 0, slot[1]));
	}

	public Word word() {
		return word;
	}

	/** The one the scene is about. */
	public @Nullable Entity character() {
		return characters.isEmpty() ? null : characters.getFirst();
	}

	public List<Entity> characters() {
		return List.copyOf(characters);
	}

	public boolean dimmed() {
		return dimmed;
	}

	public boolean solved() {
		return solved;
	}

	/** What is still going on: ambients, bubbles, litter, vines. For tests. */
	public int ambientCount() {
		return ambients.size();
	}

	public int bubbleCount() {
		return (int) bubbles.stream().filter(Entity::isAlive).count();
	}

	public int litterCount() {
		return (int) litter.stream().filter(Entity::isAlive).count();
	}

	public int wrapCount() {
		return (int) wraps.stream().filter(Entity::isAlive).count();
	}

	/** Scene blocks still waiting to become something else. For tests. */
	public int unchangedCount() {
		int left = 0;
		for (Map.Entry<BlockPos, BlockState> entry : becomes.entrySet()) {
			if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) left++;
		}
		return left;
	}

	/** Everything the scene made that is still in the world. For tests. */
	public List<Entity> alive() {
		return made.stream().filter(Entity::isAlive).toList();
	}

	boolean isCharacter(Entity entity) {
		for (Entity character : characters) if (character.getUUID().equals(entity.getUUID())) return true;
		return false;
	}

	/** The squares the scene keeps for itself: where its characters stand, beside them, its blocks. */
	boolean occupies(BlockPos pos) {
		return occupied.contains(pos);
	}

	static @Nullable NoteBlockInstrument voice(String name) {
		for (NoteBlockInstrument voice : NoteBlockInstrument.values()) {
			if (voice.getSerializedName().equals(name) && !voice.hasCustomSound()) return voice;
		}
		return null;
	}

	// --- Setting it up ---

	void start(ServerPlayer player) {
		for (Scene.Step step : scene.setup()) {
			switch (step) {
				case Scene.Character who -> {
					if (characters.size() < MAX_CHARACTERS) spawnCharacter(who, characters.size());
				}
				case Scene.Ambient ambient -> ambients.add(ambient);
				case Scene.Bubble bubble -> {
					bubble(bubble);
					setupBubbles++;
				}
				case Scene.Dim dim -> dim(player);
				case Scene.Litter items -> scatter(items);
				case Scene.Block block -> blocks(block);
				case Scene.Ring ring -> ring(ring);
				case Scene.Prop prop -> prop(prop);
				case Scene.Wrap wrap -> wrap(wrap);
				case Scene.Feel feel -> feel(feel);
				case Scene.Gesture gesture -> gesture(gesture);
				case Scene.Sky sky -> sky(player, sky);
				case Scene.Weather weather -> weather(player, weather);
				default -> { }
			}
		}
		// A villager just arrived says hello, if it is standing still to.
		Entity first = character();
		if (first instanceof Villager && acts.getOrDefault(first, "still").equals("still")) {
			room.later(player, 8, p -> {
				if (first.isAlive() && !solved) justfatlard.phonics.feel.Gestures.run(first, justfatlard.phonics.feel.Gestures.Gesture.WAVE);
			});
		}
		Vec3 at = Vec3.atBottomCenterOf(spot);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1.0, at.z, 16, 0.4, 0.6, 0.4, 0.02);
	}

	private void feel(Scene.Feel feel) {
		justfatlard.phonics.feel.Feelings.Face face = justfatlard.phonics.feel.Feelings.Face.named(feel.face());
		if (face == null) return;
		List<Entity> who = feel.all() ? characters : characters.isEmpty() ? List.of() : List.of(characters.getFirst());
		for (Entity one : who) {
			Display.ItemDisplay bubble = justfatlard.phonics.feel.Feelings.show(one, face, 0);
			if (bubble != null) made.add(bubble);
		}
	}

	private void gesture(Scene.Gesture gesture) {
		justfatlard.phonics.feel.Gestures.Gesture named = justfatlard.phonics.feel.Gestures.Gesture.named(gesture.gesture());
		if (named == null) return;
		List<Entity> who = gesture.all() ? characters : characters.isEmpty() ? List.of() : List.of(characters.getFirst());
		for (Entity one : who) {
			// It stands still for it: whatever it was up to stops.
			acts.put(one, "still");
			Vec3 home = homes.get(one);
			if (home != null) one.snapTo(home.x, home.y, home.z, one.getYRot(), 0.0F);
			justfatlard.phonics.feel.Gestures.run(one, named);
		}
	}

	private void sky(ServerPlayer player, Scene.Sky sky) {
		justfatlard.phonics.classroom.Sky.Time time = sky.named();
		if (time != null) justfatlard.phonics.classroom.Sky.glide(player, time.ticks, 30);
	}

	private void weather(ServerPlayer player, Scene.Weather weather) {
		justfatlard.phonics.classroom.Sky.Weather named = justfatlard.phonics.classroom.Sky.weatherNamed(weather.weather());
		if (named != null) justfatlard.phonics.classroom.Sky.setWeather(player, named);
	}

	private @Nullable Entity create(net.minecraft.resources.Identifier id, boolean baby) {
		EntityType<?> type = Outcome.harmlessType(id, baby);
		if (type == null) return null;
		Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (entity == null) return null;
		entity.setPermanentlyInvulnerable(true);
		entity.addTag(Outcome.SPAWNED_TAG);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			mob.setNoAi(true);
			if (baby) mob.setBaby(true);
			for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
		}
		return entity;
	}

	private void spawnCharacter(Scene.Character who, int slot) {
		Entity entity = create(who.entity(), who.baby());
		if (entity == null) return;
		Vec3 at = Vec3.atBottomCenterOf(spot.offset(SLOTS[slot][0], 0, SLOTS[slot][1]));
		Vec3 toward = room.spawn().subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
		if (slot == 0) facing = yaw;
		Vec3 stands = who.act().equals("stuck") ? at.add(0, -0.9, 0) : at;
		entity.snapTo(stands.x, stands.y, stands.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		if (entity instanceof LivingEntity living) living.setYBodyRot(yaw);
		level.addFreshEntity(entity);
		characters.add(entity);
		made.add(entity);
		acts.put(entity, who.act());
		homes.put(entity, at);
	}

	private void bubble(Scene.Bubble bubble) {
		Display.TextDisplay text = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (text == null) return;
		Vec3 at = head().add(0, bubbleLift(), 0);
		text.snapTo(at.x, at.y, at.z, 0, 0);
		text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		text.setText(Component.literal(bubble.text()).withStyle(style -> style.withColor(TextColor.fromRgb(bubble.color())).withBold(true)));
		text.setTransformation(new Transformation(null, null, new Vector3f(1.8F, 1.8F, 1.8F), null));
		level.addFreshEntity(text);
		bubbles.add(text);
		made.add(text);
	}

	/** How far over the head a thought bubble floats: higher in a scene with feelings, clear of the face in its bubble. */
	private double bubbleLift() {
		boolean feels = scene.setup().stream().anyMatch(step -> step instanceof Scene.Feel) || scene.solve().stream().anyMatch(step -> step instanceof Scene.Feel);
		return feels ? 1.35 : 0.55;
	}

	private static final double[][] LITTER_AT = {{-1.3, 0.3}, {0.2, 0.7}, {1.2, 0.1}, {-0.5, 1.4}, {0.9, 1.5}, {-1.8, 1.2}, {1.7, 0.9}, {-0.2, 0.0}};

	private void scatter(Scene.Litter items) {
		BlockPos origin = room.origin();
		for (int i = 0; i < items.items().size() * 2 && i < LITTER_AT.length; i++) {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(items.items().get(i % items.items().size())));
			Display.ItemDisplay piece = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (piece == null) continue;
			piece.snapTo(origin.getX() + 0.5 + LITTER_AT[i][0], origin.getY() + 0.03, origin.getZ() + 0.5 + LITTER_AT[i][1], 0, 0);
			piece.setItemStack(stack);
			piece.setItemTransform(ItemDisplayContext.FIXED);
			// Lying flat, each at its own angle, as things dropped do.
			piece.setTransformation(new Transformation(null,
				new Quaternionf().rotateY((float) Math.toRadians(i * 67)).rotateX((float) Math.toRadians(-90)),
				new Vector3f(0.55F, 0.55F, 0.55F), null));
			level.addFreshEntity(piece);
			litter.add(piece);
			made.add(piece);
		}
	}

	private void blocks(Scene.Block block) {
		BlockState state = Outcome.blockStateOf(block.block(), block.properties());
		if (state == null) return;
		BlockState after = block.becomes().map(id -> BuiltInRegistries.BLOCK.getValue(id).defaultBlockState()).orElse(null);
		for (List<Integer> offset : block.at()) {
			BlockPos pos = spot.offset(offset.get(0), offset.size() > 2 ? offset.get(2) : 0, offset.get(1));
			if (!level.getBlockState(pos).isAir()) continue;
			room.placeAt(pos, state);
			occupied.add(pos);
			if (after != null) becomes.put(pos, after);
		}
	}

	private void ring(Scene.Ring ring) {
		BlockState state = BuiltInRegistries.BLOCK.getValue(ring.block()).defaultBlockState();
		List<BlockPos> placed = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos pos = spot.offset(dx, 0, dz);
				if (!level.getBlockState(pos).isAir()) continue;
				room.placeAt(pos, state);
				occupied.add(pos);
				placed.add(pos);
			}
		}
		// Joined up, as a fence is when it is built by hand.
		for (BlockPos pos : placed) {
			level.setBlock(pos, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, pos), 2);
		}
	}

	private void prop(Scene.Prop prop) {
		Display.ItemDisplay shown = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (shown == null) return;
		Vec3 at = Vec3.atBottomCenterOf(spot).add(0, 0.6, 0);
		shown.snapTo(at.x, at.y, at.z, 0, 0);
		shown.setItemStack(new ItemStack(BuiltInRegistries.ITEM.getValue(prop.item())));
		shown.setItemTransform(ItemDisplayContext.GROUND);
		shown.setTransformation(new Transformation(null, null, new Vector3f(1.2F, 1.2F, 1.2F), null));
		level.addFreshEntity(shown);
		props.add(shown);
		made.add(shown);
	}

	private void wrap(Scene.Wrap wrap) {
		Entity who = character();
		if (who == null) return;
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(wrap.item()));
		for (int i = 0; i < wrap.count(); i++) {
			double angle = Math.PI * 2 * i / wrap.count();
			Display.ItemDisplay piece = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (piece == null) continue;
			double height = who.getBbHeight() * (0.3 + 0.45 * (i % 3) / 2.0);
			Vec3 at = who.position().add(Math.cos(angle) * 0.38, height, Math.sin(angle) * 0.38);
			piece.snapTo(at.x, at.y, at.z, 0, 0);
			piece.setItemStack(stack.copy());
			piece.setItemTransform(ItemDisplayContext.FIXED);
			piece.setTransformation(new Transformation(null, new Quaternionf().rotateY((float) (-angle + Math.PI / 2)),
				new Vector3f(0.7F, 0.7F, 0.7F), null));
			level.addFreshEntity(piece);
			wraps.add(piece);
			made.add(piece);
		}
	}

	private void dim(ServerPlayer player) {
		if (!PandoricalApi.isAvailable(player)) return;
		Viewport window = PandoricalApi.screens().viewport(player);
		HudBuilder overlay = new HudBuilder(DIM_OVERLAY).anchor("top_left").offset(0, 0);
		overlay.component(new ComponentBuilder("shade", ComponentType.PANEL)
			.bounds(-4, -4, window.width() + 8, window.height() + 8)
			.prop(ComponentType.PROP_BACKGROUND, DIM_COLOUR)
			.prop(ComponentType.PROP_BORDER, "flat")
			.prop(ComponentType.PROP_BORDER_COLOR, DIM_COLOUR));
		PandoricalApi.hud().show(player, overlay.build());
		dimmed = true;
		everDimmed = true;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.4F);
	}

	private void undim(@Nullable ServerPlayer player) {
		if (!dimmed) return;
		dimmed = false;
		if (player == null) return;
		PandoricalApi.hud().hide(player, DIM_OVERLAY);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	// --- While it lasts ---

	private Vec3 base() {
		Entity who = character();
		return who != null ? who.position() : Vec3.atBottomCenterOf(spot);
	}

	private double height() {
		Entity who = character();
		return who != null ? who.getBbHeight() : 1.0;
	}

	private Vec3 head() {
		return base().add(0, height() + 0.2, 0);
	}

	private Vec3 headOf(Entity who) {
		return who.position().add(0, who.getBbHeight() + 0.2, 0);
	}

	private Vec3 where(String at) {
		return switch (at) {
			case "over" -> base().add(0, height() + 1.1, 0);
			case "face" -> {
				Vec3 look = Vec3.directionFromRotation(0, facing).scale(0.35);
				yield base().add(look.x, height() * 0.85, look.z);
			}
			case "body" -> base().add(0, height() * 0.5, 0);
			case "beside" -> Vec3.atBottomCenterOf(spot.east()).add(0, 0.4, 0);
			default -> head();
		};
	}

	void tick(ServerPlayer player) {
		ticks++;
		for (Scene.Ambient ambient : ambients) {
			if (ticks % ambient.every() == 0) {
				ParticleOptions options = Outcome.particleOf(ambient.particle());
				Vec3 at = where(ambient.at());
				double spread = ambient.spread();
				if (options != null) level.sendParticles(options, at.x, at.y, at.z, ambient.count(), spread, ambient.at().equals("over") ? 0.08 : spread * 0.5, spread, 0.0);
			}
			if (ambient.sound().isPresent() && ticks % ambient.soundEvery() == 5) {
				BuiltInRegistries.SOUND_EVENT.get(ambient.sound().get()).ifPresent(sound -> {
					Vec3 at = base();
					level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 0.6F, ambient.pitch());
				});
			}
		}
		for (Entity who : characters) {
			if (!who.isAlive()) continue;
			Vec3 home = homes.get(who);
			float yaw = who.getYRot();
			switch (acts.getOrDefault(who, "still")) {
				case "wander" -> {
					// Pacing to and fro across the scene's squares, turning at each end.
					double phase = ticks / 22.0;
					double x = home.x + 1.0 + Math.sin(phase) * 1.1;
					float turn = Math.cos(phase) >= 0 ? -90.0F : 90.0F;
					who.snapTo(x, home.y, home.z, turn, 0.0F);
					who.setYHeadRot(turn + (float) Math.sin(ticks / 5.0) * 30.0F);
				}
				case "shiver" -> {
					if (ticks % 2 == 0) {
						double jitter = (ticks % 4 == 0 ? 0.04 : -0.04);
						who.snapTo(home.x + jitter, home.y, home.z, facing + (float) (jitter * 150), 0.0F);
						who.setYHeadRot(facing + (float) (jitter * 150));
					}
				}
				case "bounce" -> {
					// Too excited to keep still: hopping, each a little out of step with the others.
					double lift = Math.abs(Math.sin((ticks + characters.indexOf(who) * 5) / 4.0)) * 0.45;
					who.snapTo(home.x, home.y + lift, home.z, yaw + 6.0F, 0.0F);
					who.setYHeadRot(yaw + 6.0F);
				}
				case "stuck" -> who.snapTo(home.x, home.y - 0.9, home.z, yaw, 0.0F);
				case "hungry" -> {
					// A long look over at the button, where food might come from, and back.
					boolean looking = ticks % 60 >= 30;
					Vec3 toward = room.mouth().subtract(who.position());
					float at = looking ? (float) Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90.0F : facing;
					who.setYHeadRot(at);
					who.setYBodyRot(at);
				}
				default -> { }
			}
		}
		if (ticks % 10 == 0) {
			// Thought bubbles bob gently, and follow a pacing character.
			boolean up = ticks % 20 == 0;
			for (Display.TextDisplay bubble : bubbles) {
				bubble.setTransformationInterpolationDelay(0);
				bubble.setTransformationInterpolationDuration(10);
				bubble.setTransformation(new Transformation(new Vector3f(0, up ? 0.12F : 0.0F, 0), null, new Vector3f(1.8F, 1.8F, 1.8F), null));
			}
			Entity who = character();
			if (who != null && acts.getOrDefault(who, "still").equals("wander")) for (Display.TextDisplay bubble : bubbles) {
				Vec3 at = head().add(0, bubbleLift(), 0);
				bubble.snapTo(at.x, at.y, at.z, 0, 0);
			}
		}
		if (ticks % 20 == 0) {
			float angle = (float) ((ticks / 20) % 4 * (Math.PI / 2));
			for (Display.ItemDisplay prop : props) {
				prop.setTransformationInterpolationDelay(0);
				prop.setTransformationInterpolationDuration(20);
				prop.setTransformation(new Transformation(null, new Quaternionf().rotateY(angle), new Vector3f(1.2F, 1.2F, 1.2F), null));
			}
		}
	}

	// --- Solving it ---

	/** The right word: each solve step in turn, then everything back to normal. */
	void solve(ServerPlayer player) {
		solved = true;
		int at = 0;
		for (Scene.Step step : scene.solve()) {
			if (at == 0) run(player, step);
			else room.later(player, at, p -> run(p, step));
			at += step.ticks();
		}
		room.later(player, Math.max(1, at), this::finish);
	}

	private void run(ServerPlayer player, Scene.Step step) {
		switch (step) {
			case Scene.Give give -> {
				List<Entity> to = give.all() && !characters.isEmpty() ? characters : java.util.Collections.singletonList(character());
				for (int i = 0; i < to.size(); i++) {
					Entity who = to.get(i);
					if (i == 0) give(player, give, who);
					else room.later(player, i * 4, p -> give(p, give, who));
				}
			}
			case Scene.Place place -> place(player, place);
			case Scene.Burst burst -> {
				ParticleOptions options = Outcome.particleOf(burst.particle());
				Vec3 at = where("body");
				if (options != null) level.sendParticles(options, at.x, at.y, at.z, burst.count(), burst.spread(), burst.spread(), burst.spread(), 0.05);
				burst.sound().flatMap(BuiltInRegistries.SOUND_EVENT::get).ifPresent(sound ->
					level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1.0F, burst.pitch()));
			}
			case Scene.Bubble bubble -> bubble(bubble);
			case Scene.Light light -> undim(player);
			case Scene.Pose pose -> {
				for (Entity who : characters) {
					acts.put(who, "still");
					Vec3 home = homes.get(who);
					who.snapTo(home.x, home.y, home.z, who.getYRot(), 0.0F);
					if (who instanceof TamableAnimal pet) {
						pet.setOrderedToSit(true);
						pet.setInSittingPose(true);
					}
					level.sendParticles(ParticleTypes.POOF, who.getX(), who.getY() + 0.2, who.getZ(), 4, 0.2, 0.05, 0.2, 0.01);
				}
			}
			case Scene.Visit visit -> visit(player, visit);
			case Scene.Tune tune -> {
				NoteBlockInstrument voice = voice(tune.instrument());
				if (voice == null) return;
				for (int i = 0; i < tune.notes().size(); i++) {
					int note = tune.notes().get(i);
					room.later(player, i * tune.every(), p -> {
						float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
						Vec3 at = head().add(0, 0.4, 0);
						level.playSound(null, at.x, at.y, at.z, voice.getSoundEvent(), SoundSource.RECORDS, 0.9F, pitch);
						level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 0, note / 24.0, 0, 0, 1.0);
					});
				}
			}
			case Scene.Effect effect -> BuiltInRegistries.MOB_EFFECT.get(effect.effect()).ifPresent(found ->
				player.addEffect(new MobEffectInstance(found, effect.duration(), effect.amplifier())));
			case Scene.Hut hut -> room.hut(player, spot, hut.lasts(), true);
			case Scene.Feel feel -> feel(feel);
			case Scene.Gesture gesture -> gesture(gesture);
			case Scene.Sky sky -> sky(player, sky);
			case Scene.Weather weather -> weather(player, weather);
			default -> { }
		}
	}

	private void give(ServerPlayer player, Scene.Give give, @Nullable Entity who) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(give.item()));
		room.puffFromButton("scene");
		Vec3 target = who != null ? headOf(who) : Vec3.atCenterOf(spot);
		ItemEntity flying = room.throwItem(room.mouth(), target, stack, Dispenser.FLIGHT);
		flying.setNeverPickUp();
		level.addFreshEntity(flying);
		made.add(flying);
		room.later(player, Dispenser.FLIGHT, p -> {
			flying.discard();
			Vec3 at = who != null ? headOf(who) : Vec3.atCenterOf(spot);
			switch (give.then()) {
				case "wear" -> {
					Display.ItemDisplay hat = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
					if (hat == null) return;
					hat.snapTo(at.x, at.y - 0.1, at.z, 0, 0);
					hat.setItemStack(stack);
					hat.setItemTransform(ItemDisplayContext.GUI);
					hat.setBillboardConstraints(Display.BillboardConstraints.CENTER);
					hat.setTransformation(new Transformation(null, new Quaternionf().rotateY((float) Math.PI), new Vector3f(0.75F, 0.75F, 0.75F), null));
					level.addFreshEntity(hat);
					made.add(hat);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
				case "eat", "drink" -> {
					hold(who, stack);
					boolean eat = give.then().equals("eat");
					for (int i = 0; i < MUNCH - 6; i += 4) {
						room.later(p, i, q -> {
							Vec3 mouth = who != null ? who.position().add(0, who.getBbHeight() * 0.85, 0) : at;
							level.playSound(null, mouth.x, mouth.y, mouth.z, eat ? SoundEvents.GENERIC_EAT.value() : SoundEvents.GENERIC_DRINK.value(),
								SoundSource.NEUTRAL, 0.8F, 1.0F + level.getRandom().nextFloat() * 0.2F);
							if (eat) {
								ParticleOptions crumbs = crumbs(give.item().toString());
								if (crumbs != null) level.sendParticles(crumbs, mouth.x, mouth.y, mouth.z, 6, 0.1, 0.05, 0.1, 0.05);
							}
						});
					}
					if (eat) room.later(p, MUNCH - 4, q -> {
						hold(who, ItemStack.EMPTY);
						if (give.all() && who != null) {
							Vec3 over = headOf(who).add(0, 0.2, 0);
							level.sendParticles(ParticleTypes.HEART, over.x, over.y, over.z, 3, 0.2, 0.1, 0.2, 0.0);
						}
					});
				}
				case "snip" -> {
					hold(who, stack);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
					// The vines drop away and are gone.
					for (Display.ItemDisplay piece : wraps) {
						Vec3 from = piece.position();
						for (int i = 1; i <= 10; i++) {
							Vec3 down = from.add(0, -from.y + room.origin().getY() + 0.05, 0);
							Vec3 step = from.lerp(down, i / 10.0);
							room.later(p, i, q -> piece.snapTo(step.x, step.y, step.z, 0, 0));
						}
						room.later(p, 14, q -> {
							level.sendParticles(ParticleTypes.POOF, piece.getX(), piece.getY(), piece.getZ(), 4, 0.1, 0.1, 0.1, 0.01);
							piece.discard();
						});
					}
				}
				case "pull" -> {
					hold(who, stack);
					if (who != null) {
						Vec3 home = homes.get(who);
						acts.put(who, "still");
						level.playSound(null, at.x, at.y, at.z, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.0F, 0.8F);
						for (int i = 1; i <= 10; i++) {
							double y = home.y - 0.9 + 0.9 * i / 10.0 + Math.sin(Math.PI * i / 10.0) * 0.5;
							room.later(p, i, q -> who.snapTo(home.x, y, home.z, who.getYRot(), 0.0F));
						}
					}
				}
				case "soak" -> {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0F, 1.0F);
					swap(p);
				}
				default -> hold(who, stack);
			}
		});
	}

	private static @Nullable ParticleOptions crumbs(String item) {
		JsonObject json = new JsonObject();
		json.addProperty("type", "minecraft:item");
		json.addProperty("item", item);
		return Outcome.particleOf(new Dynamic<>(JsonOps.INSTANCE, json));
	}

	private void hold(@Nullable Entity who, ItemStack stack) {
		if (who instanceof Mob mob) mob.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
	}

	private void visit(ServerPlayer player, Scene.Visit visit) {
		Entity helper = create(visit.entity(), visit.baby());
		if (helper == null) return;
		if (helper instanceof Villager villager && visit.profession().isPresent()) {
			BuiltInRegistries.VILLAGER_PROFESSION.get(visit.profession().get()).ifPresent(found -> {
				villager.setVillagerData(villager.getVillagerData().withProfession(found));
				villager.setVillagerDataFinalized(true);
			});
		}
		Vec3 from = room.mouth();
		Vec3 to = switch (visit.to()) {
			case "over" -> Vec3.atBottomCenterOf(spot).add(0, 1.3, 0);
			case "front" -> Vec3.atBottomCenterOf(spot.east()).add(0, 0.05, 0);
			default -> Vec3.atBottomCenterOf(spot.east(2));
		};
		Vec3 toward = room.spawn().subtract(to);
		float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
		room.puffFromButton("scene");
		helper.snapTo(from.x, from.y, from.z, yaw, 0.0F);
		level.addFreshEntity(helper);
		made.add(helper);
		for (int i = 1; i <= Dispenser.FLIGHT; i++) {
			double t = i / (double) Dispenser.FLIGHT;
			Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 1.2, 0);
			room.later(player, i, p -> {
				helper.snapTo(at.x, at.y, at.z, yaw, 0.0F);
				helper.setYHeadRot(yaw);
			});
		}
		room.later(player, Dispenser.FLIGHT, p -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y + 0.8, to.z, 8, 0.3, 0.3, 0.3, 0.0));
	}

	private void place(ServerPlayer player, Scene.Place place) {
		BlockState state = Outcome.blockStateOf(place.block(), place.properties());
		if (state == null) return;
		BlockPos target = character() != null ? spot.east() : spot;
		room.puffFromButton("scene");
		ItemEntity flying = room.throwItem(room.mouth(), Vec3.atBottomCenterOf(target).add(0, 0.3, 0), new ItemStack(state.getBlock().asItem()), Dispenser.FLIGHT);
		flying.setNeverPickUp();
		level.addFreshEntity(flying);
		made.add(flying);
		room.later(player, Dispenser.FLIGHT, p -> {
			flying.discard();
			if (!level.getBlockState(target).isAir()) return;
			room.placeAt(target, state);
			Vec3 at = Vec3.atCenterOf(target);
			level.playSound(null, at.x, at.y, at.z, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
			switch (place.then()) {
				case "sleep" -> {
					if (character() instanceof LivingEntity living && state.getBlock() instanceof BedBlock) {
						acts.put(living, "still");
						living.startSleeping(target.east());
					}
				}
				case "absorb" -> absorb(p, at);
				default -> {
					// Tucked in: the toy in the block, by the same rules as a child's play.
					if (place.then().startsWith("tuck:")) {
						net.minecraft.resources.Identifier item = net.minecraft.resources.Identifier.parse(place.then().substring("tuck:".length()));
						Entity tucked = justfatlard.phonics.play.Play.showIn(level, target, level.getBlockState(target),
							new ItemStack(BuiltInRegistries.ITEM.getValue(item)));
						if (tucked != null) made.add(tucked);
					}
				}
			}
		});
	}

	private void absorb(ServerPlayer player, Vec3 into) {
		int steps = ABSORB - 6;
		for (Display.ItemDisplay piece : litter) {
			Vec3 from = piece.position();
			for (int i = 1; i <= steps; i++) {
				double t = i / (double) steps;
				Vec3 at = from.lerp(into.add(0, 0.6, 0), t).add(0, Math.sin(Math.PI * t) * 0.8, 0);
				room.later(player, i, p -> piece.snapTo(at.x, at.y, at.z, 0, 0));
			}
			room.later(player, steps + 1, p -> piece.discard());
		}
		room.later(player, steps + 1, p -> {
			level.sendParticles(ParticleTypes.COMPOSTER, into.x, into.y + 0.6, into.z, 20, 0.3, 0.2, 0.3, 0.0);
			level.playSound(null, into.x, into.y, into.z, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1.0F, 1.0F);
		});
	}

	/** The scene's blocks become what they become when it is solved: flowers bloom, a puddle is gone. */
	private void swap(ServerPlayer player) {
		for (Map.Entry<BlockPos, BlockState> entry : becomes.entrySet()) {
			BlockPos pos = entry.getKey();
			if (level.getBlockState(pos).equals(entry.getValue())) continue;
			level.setBlock(pos, entry.getValue(), 3);
			Vec3 at = Vec3.atCenterOf(pos);
			level.sendParticles(entry.getValue().isAir() ? ParticleTypes.SPLASH : ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 10, 0.3, 0.2, 0.3, 0.0);
		}
	}

	/** The trouble stops, and the character is pleased: whatever the solve did, the scene ends well. */
	private void finish(ServerPlayer player) {
		for (Scene.Ambient ambient : ambients) {
			if (!ambient.drift()) continue;
			ParticleOptions options = Outcome.particleOf(ambient.particle());
			if (options == null) continue;
			// The cloud drifts away sideways, thinning as it goes.
			Vec3 from = where(ambient.at());
			for (int i = 1; i <= 10; i++) {
				int step = i;
				room.later(player, step * 2, p -> level.sendParticles(options, from.x - step * 0.35, from.y + step * 0.08, from.z,
					Math.max(1, ambient.count() - step / 3), ambient.spread(), 0.05, ambient.spread(), 0.0));
			}
		}
		ambients.clear();
		swap(player);
		for (Entity who : characters) {
			String act = acts.getOrDefault(who, "still");
			acts.put(who, "still");
			if (who instanceof LivingEntity living && living.isSleeping()) continue;
			if (who instanceof TamableAnimal pet && pet.isInSittingPose()) continue;
			Vec3 home = homes.get(who);
			if (!act.equals("still") || who.position().y < home.y - 0.1) who.snapTo(home.x, home.y, home.z, who.getYRot(), 0.0F);
		}
		Entity main = character();
		if (main != null && !(main instanceof LivingEntity living && living.isSleeping())) {
			Vec3 home = homes.get(main);
			main.snapTo(home.x, home.y, home.z, facing, 0.0F);
			main.setYHeadRot(facing);
		}
		// Thoughts from before the solve go; one the solve brought ("Zzz" over a sleeper) stays.
		List<Display.TextDisplay> before = new ArrayList<>(bubbles);
		for (int i = 0; i < Math.min(setupBubbles, before.size()); i++) {
			Display.TextDisplay bubble = before.get(i);
			level.sendParticles(ParticleTypes.POOF, bubble.getX(), bubble.getY(), bubble.getZ(), 6, 0.1, 0.1, 0.1, 0.01);
			bubble.discard();
			bubbles.remove(bubble);
		}
		setupBubbles = 0;
		undim(player);
		Vec3 at = head();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 16, 0.5, 0.4, 0.5, 0.0);
		SoundEvent cheer = main instanceof Villager ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.AMETHYST_BLOCK_CHIME;
		level.playSound(null, at.x, at.y, at.z, cheer, SoundSource.NEUTRAL, 1.0F, 1.1F);
		// Everyone glad now (unless the solve left a thought over them, a sleeper's Zzz), and a nod of
		// thanks from the one it was about, if awake.
		boolean thinking = scene.solve().stream().anyMatch(step -> step instanceof Scene.Bubble);
		for (Entity who : characters) {
			if (!who.isAlive() || thinking || who instanceof LivingEntity living && living.isSleeping()) continue;
			Display.ItemDisplay glad = justfatlard.phonics.feel.Feelings.show(who, justfatlard.phonics.feel.Feelings.Face.GLAD, 80);
			if (glad != null) made.add(glad);
		}
		if (main != null && !(main instanceof LivingEntity living && living.isSleeping())) {
			justfatlard.phonics.feel.Gestures.run(main, justfatlard.phonics.feel.Gestures.Gesture.NOD);
		}
	}

	// --- Ending ---

	/** Everything the scene made, gone, and the lights back on. */
	void end(@Nullable ServerPlayer player) {
		for (Entity entity : made) {
			if (entity instanceof LivingEntity living && living.isSleeping()) living.stopSleeping();
			justfatlard.phonics.feel.Gestures.stop(entity);
			entity.discard();
		}
		made.clear();
		characters.clear();
		bubbles.clear();
		litter.clear();
		wraps.clear();
		props.clear();
		ambients.clear();
		undim(player);
		// Once dimmed, the shade is taken down on the way out whatever else happened: it must never outlive the scene.
		if (player != null && everDimmed) PandoricalApi.hud().hide(player, DIM_OVERLAY);
	}
}
