package justfatlard.learning_blocks.feel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Doing words, done by a creature or a character that stands still for it (one with no mind of its
 * own: a scene's character, Bob, a word's visitor): a nod, a shake of the head, a wave, a bow, a
 * dance, ducking down, a jump, a spin, hiding behind a bush, a shiver, a stomp. Each is a few
 * seconds of head and body turned and the body moved about where it stands, worked out tick by
 * tick; at the end it is put back exactly where and how it was. A wave holds up a hand; hiding
 * brings a bush to hide behind; both go with the gesture.
 */
public final class Gestures {
	private Gestures() {}

	public enum Gesture {
		NOD(16), SHAKE(16), WAVE(32), BOW(26), DANCE(40), DUCK(30), JUMP(20), SPIN(20), HIDE(64), SHIVER(40), STOMP(30);

		public final int length;

		Gesture(int length) {
			this.length = length;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Gesture named(String name) {
			for (Gesture gesture : values()) if (gesture.id().equals(name)) return gesture;
			return null;
		}
	}

	static final String PROP = LearningBlocks.MOD_ID + ".gesture_prop";
	/** Every prop out now: one met in the world that is not (saved with a chunk mid-gesture) is a leftover. */
	private static final java.util.Set<java.util.UUID> props = new java.util.HashSet<>();

	/** How far from where it stands and how it is turned, at a tick: right and forward its own, turns in degrees. */
	private record Pose(double right, double up, double forward, float turn, float headTurn, float headDown) {
		static final Pose STILL = new Pose(0, 0, 0, 0, 0, 0);
	}

	private record Running(Entity who, Gesture gesture, Vec3 home, float yaw, float pitch, long started, List<Entity> props) {}

	private static final List<Running> running = new ArrayList<>();

	/** For tests: the last gesture started, {@code nod:villager}. */
	public static volatile @Nullable String lastStarted;

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Gestures::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Running run : running) finish(run);
			running.clear();
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(PROP) && !props.contains(entity.getUUID())) entity.discard();
		});
	}

	/** This one does this, starting now; anything it was already doing ends first, put back. */
	public static void run(Entity who, Gesture gesture) {
		stop(who);
		if (!(who.level() instanceof ServerLevel level)) return;
		float yaw = who instanceof LivingEntity living ? living.getYHeadRot() : who.getYRot();
		Running run = new Running(who, gesture, who.position(), yaw, who.getXRot(), level.getGameTime(), new ArrayList<>());
		running.add(run);
		lastStarted = gesture.id() + ":" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(who.getType()).getPath();
		begin(level, run);
	}

	/** Whether this one is in the middle of a gesture. */
	public static boolean busy(Entity who) {
		for (Running run : running) if (run.who().getUUID().equals(who.getUUID())) return true;
		return false;
	}

	/** Whatever this one is doing, ended now, and it put back. */
	public static void stop(Entity who) {
		for (Iterator<Running> it = running.iterator(); it.hasNext(); ) {
			Running run = it.next();
			if (run.who().getUUID().equals(who.getUUID())) {
				finish(run);
				it.remove();
			}
		}
	}

	private static void begin(ServerLevel level, Running run) {
		Entity who = run.who();
		Vec3 head = who.position().add(0, who.getBbHeight() + 0.2, 0);
		boolean villager = who instanceof Villager;
		switch (run.gesture()) {
			case NOD -> sound(who, villager ? SoundEvents.VILLAGER_YES : null, 1.0F);
			case SHAKE -> sound(who, villager ? SoundEvents.VILLAGER_NO : null, 1.0F);
			case WAVE -> {
				Display.ItemDisplay hand = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
				if (hand != null) {
					Vec3 side = right(run.yaw()).scale(who.getBbWidth() * 0.5 + 0.3);
					Vec3 at = who.position().add(side).add(0, who.getBbHeight() * 0.8, 0);
					hand.snapTo(at.x, at.y, at.z, 0, 0);
					hand.setItemStack(Feelings.picture("hand"));
					hand.setItemTransform(ItemDisplayContext.GUI);
					hand.setBillboardConstraints(Display.BillboardConstraints.CENTER);
					hand.setTransformation(handPose(0));
					hand.addTag(PROP);
					props.add(hand.getUUID());
					level.addFreshEntity(hand);
					run.props().add(hand);
				}
				sound(who, villager ? SoundEvents.VILLAGER_AMBIENT : null, 1.2F);
			}
			case HIDE -> {
				Display.BlockDisplay bush = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
				if (bush != null) {
					Vec3 front = forward(run.yaw()).scale(who.getBbWidth() * 0.5 + 0.45);
					Vec3 at = who.position().add(front);
					bush.snapTo(at.x, at.y, at.z, 0, 0);
					bush.setBlockState(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
					float tall = bushHeight(who);
					bush.setTransformation(new Transformation(new Vector3f(-0.6F, 0, -0.6F), null, new Vector3f(1.2F, tall, 1.2F), null));
					bush.addTag(PROP);
					props.add(bush.getUUID());
					level.addFreshEntity(bush);
					run.props().add(bush);
				}
				level.sendParticles(ParticleTypes.POOF, who.getX(), who.getY() + 0.5, who.getZ(), 8, 0.4, 0.3, 0.4, 0.01);
				sound(who, SoundEvents.AZALEA_LEAVES_PLACE, 1.0F);
			}
			case STOMP -> level.sendParticles(ParticleTypes.ANGRY_VILLAGER, head.x, head.y, head.z, 2, 0.2, 0.1, 0.2, 0.0);
			case DANCE -> level.sendParticles(ParticleTypes.NOTE, head.x, head.y + 0.3, head.z, 0, 0.3, 0, 0, 1.0);
			case SHIVER -> sound(who, SoundEvents.PLAYER_HURT_FREEZE, 1.4F);
			default -> { }
		}
	}

	private static float bushHeight(Entity who) {
		return (float) Math.max(1.0, Math.min(1.4, who.getBbHeight() * 0.7));
	}

	private static Transformation handPose(float roll) {
		return new Transformation(null, new Quaternionf().rotateZ((float) Math.toRadians(roll)), new Vector3f(0.55F), null);
	}

	private static void sound(Entity who, @Nullable SoundEvent sound, float pitch) {
		// A silent one (Bob, who has a voice of his own) makes none of the noises either.
		if (sound != null && !who.isSilent()) who.level().playSound(null, who.getX(), who.getY(), who.getZ(), sound, SoundSource.NEUTRAL, 0.8F, pitch);
	}

	private static Vec3 forward(float yaw) {
		return Vec3.directionFromRotation(0, yaw);
	}

	private static Vec3 right(float yaw) {
		return Vec3.directionFromRotation(0, yaw + 90.0F).scale(-1);
	}

	/** Where it is and how it is turned at tick {@code t} of this gesture. */
	private static Pose pose(Running run, int t) {
		Entity who = run.who();
		double wave = Math.sin(Math.PI * 2 * t / 8.0);
		return switch (run.gesture()) {
			case NOD -> new Pose(0, 0, 0, 0, 0, (float) (28 * Math.abs(Math.sin(Math.PI * t / 8.0))));
			case SHAKE -> new Pose(0, 0, 0, 0, (float) (35 * wave), 0);
			case WAVE -> new Pose(0, 0, 0, (float) (8 * Math.sin(Math.PI * 2 * t / 16.0)), 0, -8);
			case BOW -> {
				double down = t < 6 ? t / 6.0 : t < 18 ? 1 : Math.max(0, 1 - (t - 18) / 6.0);
				yield new Pose(0, -0.1 * down, 0.12 * down, 0, 0, (float) (70 * down));
			}
			case DANCE -> new Pose(0.1 * Math.sin(Math.PI * 2 * t / 20.0), 0.3 * Math.abs(Math.sin(Math.PI * t / 5.0)), 0,
				(float) (30 * Math.sin(Math.PI * 2 * t / 10.0)), 0, (float) (-10 * Math.sin(Math.PI * 2 * t / 10.0)));
			case DUCK -> {
				double down = t < 4 ? t / 4.0 : t < 22 ? 1 : Math.max(0, 1 - (t - 22) / 6.0);
				yield new Pose(0, -Math.min(0.6, who.getBbHeight() * 0.4) * down, 0, 0, 0, (float) (30 * down));
			}
			case JUMP -> new Pose(0, 0.75 * Math.abs(Math.sin(Math.PI * t / 10.0)), 0, 0, 0, -10);
			case SPIN -> new Pose(0, 0.1 * Math.sin(Math.PI * t / 20.0), 0, 360.0F * t / Gesture.SPIN.length, 0, 0);
			case HIDE -> {
				double hidden = -(Math.max(0.1, who.getBbHeight() - bushHeight(who) + 0.15));
				double peek = hidden + Math.min(0.45, who.getBbHeight() * 0.3);
				double up;
				float look = 0;
				if (t < 8) up = hidden * t / 8.0;
				else if (t < 28) up = hidden;
				else if (t < 32) up = hidden + (peek - hidden) * (t - 28) / 4.0;
				else if (t < 44) {
					up = peek;
					look = (float) (30 * Math.sin(Math.PI * 2 * (t - 32) / 12.0));
				} else if (t < 48) up = peek + (hidden - peek) * (t - 44) / 4.0;
				else if (t < 54) up = hidden;
				else up = hidden * Math.max(0, 1 - (t - 54) / 8.0);
				yield new Pose(0, up, 0, 0, look, 0);
			}
			case SHIVER -> t % 2 == 0 ? new Pose(t % 4 == 0 ? 0.04 : -0.04, 0, 0, t % 4 == 0 ? 5 : -5, 0, 0) : Pose.STILL;
			case STOMP -> new Pose(0, 0.18 * Math.abs(Math.sin(Math.PI * t / 5.0)), 0, 0, 0, 15);
		};
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Running> it = running.iterator(); it.hasNext(); ) {
			Running run = it.next();
			Entity who = run.who();
			if (who.isRemoved()) {
				for (Entity prop : run.props()) {
					prop.discard();
					props.remove(prop.getUUID());
				}
				it.remove();
				continue;
			}
			int t = (int) (who.level().getGameTime() - run.started());
			if (t >= run.gesture().length) {
				finish(run);
				it.remove();
				continue;
			}
			apply(run, pose(run, t));
			ServerLevel level = (ServerLevel) who.level();
			Vec3 head = run.home().add(0, who.getBbHeight() + 0.3, 0);
			switch (run.gesture()) {
				case WAVE -> {
					if (t % 4 == 0 && !run.props().isEmpty() && run.props().getFirst() instanceof Display hand) {
						hand.setTransformationInterpolationDelay(0);
						hand.setTransformationInterpolationDuration(4);
						hand.setTransformation(handPose(t % 8 == 0 ? 28 : -28));
					}
				}
				case DANCE -> {
					if (t % 8 == 0) level.sendParticles(ParticleTypes.NOTE, head.x, head.y + 0.3, head.z, 0, (t / 8) / 6.0, 0, 0, 1.0);
					if (t % 10 == 0) sound(who, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0F + (t / 10) * 0.2F);
				}
				case STOMP -> {
					if (t % 10 == 0 && t > 0) {
						sound(who, SoundEvents.WOOD_STEP, 0.8F);
						level.sendParticles(ParticleTypes.ANGRY_VILLAGER, head.x, head.y, head.z, 1, 0.2, 0.1, 0.2, 0.0);
					}
				}
				case JUMP -> {
					if (t == 9 || t == 19) sound(who, SoundEvents.SLIME_JUMP_SMALL, 1.4F);
				}
				case HIDE -> {
					if (t == 28) sound(who, SoundEvents.AZALEA_LEAVES_STEP, 1.2F);
				}
				default -> { }
			}
		}
	}

	private static void apply(Running run, Pose pose) {
		Entity who = run.who();
		Vec3 at = run.home().add(right(run.yaw()).scale(pose.right())).add(forward(run.yaw()).scale(pose.forward())).add(0, pose.up(), 0);
		float yaw = run.yaw() + pose.turn();
		who.snapTo(at.x, at.y, at.z, yaw, run.pitch() + pose.headDown());
		who.setYHeadRot(yaw + pose.headTurn());
		if (who instanceof LivingEntity living) living.setYBodyRot(yaw);
		who.setDeltaMovement(Vec3.ZERO);
	}

	/** Put back exactly where and how it was, and whatever it brought gone. */
	private static void finish(Running run) {
		Entity who = run.who();
		for (Entity prop : run.props()) {
			if (prop instanceof Display.BlockDisplay && who.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.POOF, prop.getX(), prop.getY() + 0.6, prop.getZ(), 8, 0.4, 0.3, 0.4, 0.01);
			}
			prop.discard();
			props.remove(prop.getUUID());
		}
		if (who.isRemoved()) return;
		Vec3 home = run.home();
		who.snapTo(home.x, home.y, home.z, run.yaw(), run.pitch());
		who.setYHeadRot(run.yaw());
		if (who instanceof LivingEntity living) living.setYBodyRot(run.yaw());
	}

	/** For tests: whether this one is back exactly where it started, and turned as it was. */
	public static boolean atHome(Entity who, Vec3 home, float yaw) {
		return who.position().distanceTo(home) < 1.0E-4 && Math.abs(net.minecraft.util.Mth.wrapDegrees(who.getYRot() - yaw)) < 0.01F;
	}
}
