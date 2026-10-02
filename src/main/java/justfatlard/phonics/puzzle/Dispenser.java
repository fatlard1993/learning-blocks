package justfatlard.phonics.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.math.Transformation;
import justfatlard.phonics.Later;
import justfatlard.phonics.Phonics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The button, as a dispenser: everything a word or a scene does to the room, and what it left
 * there - the creatures it brought, the blocks it put down, the things it only showed for a moment
 * - so the room can tidy it all away. What an outcome sees of it is {@link Stage}.
 */
public final class Dispenser implements Stage {
	/** How many creatures a room keeps before the oldest goes. */
	private static final int MAX_SPAWNED = 8;
	private static final int FLAGS = RoomLayout.FLAGS;
	private static final int HALF = RoomLayout.HALF;
	private static final int HEIGHT = RoomLayout.HEIGHT;

	private final Room room;
	private final ServerLevel level;
	private final RoomLayout layout;
	private final BlockPos origin;

	final List<UUID> spawned = new ArrayList<>();
	private long scaledUntil;
	/** Things a word only shows for a moment, a rolling log or a bonking hat: gone with the room if not before. */
	private final List<Entity> props = new ArrayList<>();
	/** Blocks a word put down, taken up at the next round and on the way out. */
	final List<BlockPos> placed = new ArrayList<>();
	/** Floor a block is on its way to, which nothing else may take meanwhile. */
	final java.util.Set<BlockPos> reserved = new java.util.HashSet<>();
	/** Where each thing the button sent out started, for tests. */
	private final List<Dispensed> dispensed = new ArrayList<>();

	Dispenser(Room room, ServerLevel level, RoomLayout layout) {
		this.room = room;
		this.level = level;
		this.layout = layout;
		this.origin = layout.origin();
	}

	@Override
	public BlockPos origin() {
		return origin;
	}

	@Override
	public Vec3 spawn() {
		return layout.spawn();
	}

	private BlockPos button() {
		return layout.button();
	}

	private BlockPos prizeMarker() {
		return layout.prizeMarker();
	}

	private BlockPos doorLower() {
		return layout.doorLower();
	}

	@Override
	public void later(ServerPlayer player, int ticks, java.util.function.Consumer<ServerPlayer> what) {
		room.later(player, ticks, what);
	}

	// --- What words do to the room ---

	/** The creatures words have brought that are still here. */
	@Override
	public List<Entity> creatures() {
		List<Entity> found = new ArrayList<>();
		for (UUID id : spawned) {
			Entity entity = level.getEntity(id);
			if (entity != null && entity.isAlive()) found.add(entity);
		}
		return found;
	}

	/** Positions a word has put a block at, for tests. */
	public List<BlockPos> placed() {
		return List.copyOf(placed);
	}

	/**
	 * Put a block down: at the child's feet if asked and the floor there is free, otherwise on the
	 * nearest free floor in front of them. Never on the board, the word, the loose tiles, the button,
	 * the door, the prize chest, where creatures arrive or where a child is set down. A bed takes
	 * two free squares side by side.
	 */
	void place(ServerPlayer player, BlockState state, boolean feet) {
		BlockPos chosen = spotFor(player, state, feet);
		if (chosen != null) placeAt(chosen, state);
	}

	@Nullable BlockPos spotFor(ServerPlayer player, BlockState state, boolean feet) {
		boolean bed = state.getBlock() instanceof BedBlock;
		BlockPos chosen = null;
		BlockPos standing = player.blockPosition();
		if (feet && !bed && free(standing, player, true)) chosen = standing;
		if (chosen == null) {
			Vec3 look = player.getLookAngle();
			Vec3 aim = player.position().add(look.x * 2, 0, look.z * 2);
			double best = Double.MAX_VALUE;
			for (int x = -4; x <= 4; x++) {
				for (int z = -1; z <= 3; z++) {
					BlockPos pos = origin.offset(x, 0, z);
					if (!free(pos, player, false) || bed && !free(pos.east(), player, false)) continue;
					double distance = Vec3.atBottomCenterOf(pos).distanceToSqr(aim);
					if (distance < best) {
						best = distance;
						chosen = pos;
					}
				}
			}
		}
		if (chosen == null) Phonics.LOGGER.info("[{}] No free floor for {}", Phonics.MOD_ID, state);
		return chosen;
	}

	@Override
	public void placeAt(BlockPos chosen, BlockState state) {
		if (state.getBlock() instanceof BedBlock) {
			BlockState foot = state.setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT);
			level.setBlock(chosen, foot, FLAGS);
			level.setBlock(chosen.east(), foot.setValue(BedBlock.PART, BedPart.HEAD), FLAGS);
			placed.add(chosen.immutable());
			placed.add(chosen.east().immutable());
		} else {
			level.setBlock(chosen, state, FLAGS);
			placed.add(chosen.immutable());
		}
	}

	/** Open floor the room is not using, with nothing standing in it (except the child, at their feet). */
	private boolean free(BlockPos pos, ServerPlayer player, boolean feet) {
		int x = pos.getX() - origin.getX();
		int z = pos.getZ() - origin.getZ();
		if (pos.getY() != origin.getY() || Math.abs(x) > 4 || z < -1 || z > 3) return false;
		if (!level.getBlockState(pos).isAir() || reserved.contains(pos)) return false;
		if (room.holds(pos) || pos.equals(prizeMarker())) return false;
		if (room.scene() != null && room.scene().occupies(pos)) return false;
		if (BlockPos.containing(spawn()).equals(pos) || layout.creatureSpot().equals(pos)) return false;
		if (!feet && (pos.equals(player.blockPosition()) || pos.equals(player.blockPosition().above()))) return false;
		return level.getEntities((Entity) null, new AABB(pos), entity -> !(entity instanceof Player)).isEmpty();
	}

	void clearPlaced() {
		for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
		placed.clear();
	}

	private static final Identifier SCALE_ID = Phonics.id("word_scale");

	/** Bigger for a while: a modifier on the scale attribute, taken off when the time is up or they leave. */
	@Override
	public void scale(ServerPlayer player, double amount, int duration) {
		AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
		if (attribute == null) return;
		attribute.removeModifier(SCALE_ID);
		attribute.addTransientModifier(new AttributeModifier(SCALE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		long until = level.getGameTime() + duration;
		scaledUntil = until;
		// A later size replaces this one, and ends on its own time, not this one's.
		Later.after(player, duration, p -> {
			if (scaledUntil == until) unscale(p);
		});
	}

	static void unscale(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
		if (attribute != null) attribute.removeModifier(SCALE_ID);
	}

	// --- The button, as a dispenser ---

	/** Something the button sent out, and where it started. */
	public record Dispensed(String what, Vec3 from) {}

	/** How long anything takes to get from the button to the child: under a second. */
	static final int FLIGHT = 14;
	/** Between the parts of a sequence, each out of the button in turn. */
	static final int STAGGER = 8;
	private static final double ITEM_GRAVITY = 0.04;

	@Override
	public Vec3 mouth() {
		return layout.mouth();
	}

	/** What the button has sent out lately, oldest first: for tests. */
	public List<Dispensed> dispensed() {
		return List.copyOf(dispensed);
	}

	void sent(String what, Vec3 from) {
		dispensed.add(new Dispensed(what, from));
		while (dispensed.size() > 32) dispensed.removeFirst();
	}

	/** The button's click and puff, logged as sending this out: for what a scene throws. */
	@Override
	public void puffFromButton(String what) {
		puff();
		sent(what, mouth());
	}

	/** The click and puff of something leaving the button. */
	void puff() {
		Vec3 at = mouth();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.02);
	}

	/**
	 * A creature, out of the button and in a short arc toward the child: launched from low in front of
	 * the board, never back at it, and the walls keep it in.
	 */
	@Override
	public void dispenseCreature(ServerPlayer player, Entity creature) {
		puff();
		Vec3 start = new Vec3(button().getX() + 0.5, origin.getY() + 1.3, button().getZ() + 1.3);
		creature.snapTo(start.x, start.y, start.z, player.getYRot() + 180.0F, 0.0F);
		Vec3 toward = player.position().subtract(start);
		Vec3 flat = new Vec3(toward.x, 0, toward.z);
		flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(0.28) : new Vec3(0, 0, 0.28);
		// Always away from the board, whatever the child is doing.
		if (flat.z < 0.1) flat = new Vec3(flat.x, 0, 0.1);
		creature.setDeltaMovement(flat.x, 0.32, flat.z);
		level.addFreshEntity(creature);
		creature.needsSync = true;
		sent("creature", start);
		keep(creature);
	}

	@Override
	public void dispenseCharacter(ServerPlayer player, Entity character, java.util.function.Consumer<Entity> landed, int lasts) {
		puff();
		Vec3 from = mouth();
		Vec3 to = standingFor(player);
		Vec3 toward = player.position().subtract(to);
		float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
		if (character instanceof Mob mob) mob.setNoAi(true);
		character.setNoGravity(true);
		character.snapTo(from.x, from.y, from.z, yaw, 0.0F);
		level.addFreshEntity(character);
		sent("character", from);
		keep(character);
		for (int i = 1; i <= FLIGHT; i++) {
			double t = i / (double) FLIGHT;
			Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 1.2, 0);
			boolean last = i == FLIGHT;
			Later.after(player, i, p -> {
				if (!character.isAlive()) return;
				character.snapTo(at.x, at.y, at.z, yaw, 0.0F);
				character.setYHeadRot(yaw);
				if (character instanceof net.minecraft.world.entity.LivingEntity living) living.setYBodyRot(yaw);
				if (last) {
					level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 6, 0.3, 0.05, 0.3, 0.01);
					landed.accept(character);
				}
			});
		}
		if (lasts > 0) {
			Later.after(player, FLIGHT + lasts, p -> {
				if (!character.isAlive()) return;
				level.sendParticles(ParticleTypes.POOF, character.getX(), character.getY() + character.getBbHeight() / 2, character.getZ(),
					12, 0.3, 0.3, 0.3, 0.02);
				justfatlard.phonics.feel.Feelings.clear(character);
				character.discard();
				spawned.remove(character.getUUID());
			});
		}
	}

	/** Where a character stands to be seen: on the floor a little in front of the child, toward the board, inside the walls. */
	private Vec3 standingFor(ServerPlayer player) {
		double x = Math.max(origin.getX() + 0.5 - 3.5, Math.min(origin.getX() + 0.5 + 3.5, player.getX()));
		double z = Math.max(origin.getZ() + 0.5 - 3.0, Math.min(origin.getZ() + 0.5 + 2.0, player.getZ() - 2.2));
		return new Vec3(x, origin.getY(), z);
	}

	/** An item thrown from the button to land at the child's feet, picked up the ordinary way. */
	@Override
	public void dispenseItem(ServerPlayer player, ItemStack stack) {
		puff();
		ItemEntity item = throwItem(Vec3.atBottomCenterOf(player.blockPosition()).add(0, 0.3, 0), stack);
		item.setPickUpDelay(FLIGHT - 4);
		level.addFreshEntity(item);
		sent("item", item.position());
	}

	/** Something worn: it flies from the button to the child and is on them when it arrives. */
	@Override
	public void dispenseWorn(ServerPlayer player, ItemStack stack, java.util.function.Consumer<ServerPlayer> wear) {
		puff();
		ItemEntity visual = throwItem(player.position().add(0, player.getBbHeight(), 0), stack);
		visual.setNeverPickUp();
		level.addFreshEntity(visual);
		sent("worn", visual.position());
		later(player, FLIGHT, p -> {
			visual.discard();
			wear.accept(p);
		});
	}

	/**
	 * A block flies from the button to its spot and lands there. The spot is chosen and held as it
	 * leaves, so nothing else takes it on the way; the block is placed, and tracked, when it lands.
	 */
	void dispenseBlock(ServerPlayer player, BlockState state, boolean feet) {
		dispenseBlock(player, state, feet, 0);
	}

	/** The same, gone again in a puff of smoke {@code lasts} ticks after it lands, if not 0. */
	@Override
	public void dispenseBlock(ServerPlayer player, BlockState state, boolean feet, int lasts) {
		BlockPos spot = spotFor(player, state, feet);
		if (spot == null) return;
		puff();
		reserved.add(spot);
		if (state.getBlock() instanceof BedBlock) reserved.add(spot.east());
		ItemStack shown = new ItemStack(state.getBlock().asItem());
		ItemEntity visual = throwItem(Vec3.atBottomCenterOf(spot).add(0, 0.2, 0), shown.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.BRICK) : shown);
		visual.setNeverPickUp();
		level.addFreshEntity(visual);
		sent("block", visual.position());
		later(player, FLIGHT, p -> {
			visual.discard();
			reserved.remove(spot);
			reserved.remove(spot.east());
			if (!level.getBlockState(spot).isAir()) return;
			placeAt(spot, state);
			if (lasts > 0) later(p, lasts, q -> vanishBlock(spot, state));
		});
	}

	/** A block a word put down, gone in smoke: both halves, for a bed. */
	private void vanishBlock(BlockPos spot, BlockState state) {
		List<BlockPos> parts = state.getBlock() instanceof BedBlock ? List.of(spot, spot.east()) : List.of(spot);
		for (BlockPos part : parts) {
			if (!placed.contains(part)) continue;
			Vec3 at = Vec3.atCenterOf(part);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.02);
			level.setBlock(part, Blocks.AIR.defaultBlockState(), FLAGS);
			placed.remove(part);
		}
		Vec3 at = Vec3.atCenterOf(spot);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.6F);
	}

	/**
	 * Something that happens to the child - a potion, a push, a burst of hearts: a trail of sparks
	 * from the button to them, and it happens when the trail arrives.
	 */
	@Override
	public void dispenseEffect(ServerPlayer player, java.util.function.Consumer<ServerPlayer> arrive) {
		puff();
		Vec3 from = mouth();
		Vec3 to = player.position().add(0, player.getBbHeight() * 0.6, 0);
		sent("effect", from);
		int steps = FLIGHT - 2;
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 0.9, 0);
			later(player, i, p -> level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.03, 0.03, 0.03, 0.0));
		}
		later(player, steps, arrive);
	}

	/** A firework, from the button, up; a dud fizzles out in smoke instead of bursting. */
	@Override
	public void dispenseFirework(ServerPlayer player, ItemStack rocket, boolean dud) {
		puff();
		Vec3 from = mouth();
		net.minecraft.world.entity.projectile.FireworkRocketEntity entity =
			new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, from.x, from.y, from.z, rocket);
		level.addFreshEntity(entity);
		prop(entity);
		sent("firework", from);
		if (dud) {
			later(player, 8, p -> {
				Vec3 at = entity.position();
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.01);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.8F);
				entity.discard();
			});
		}
	}

	// --- What a word does when it was not the word asked for ---

	/**
	 * What a creature does once out: runs off to the far corner, chases its tail, or circles the
	 * child's head; and, if {@code lasts}, vanishes in a puff after that long.
	 */
	@Override
	public void act(ServerPlayer player, Entity creature, String act, int lasts) {
		switch (act) {
			case "flee" -> later(player, 12, p -> {
				if (creature instanceof Mob mob) {
					Vec3 corner = layout.farCorner(p.position());
					mob.getNavigation().moveTo(corner.x, corner.y, corner.z, 1.6);
				}
			});
			case "spin" -> {
				for (int i = 10; i < 50; i += 2) {
					later(player, i, p -> {
						float yaw = creature.getYRot() + 40.0F;
						creature.setYRot(yaw);
						creature.setYHeadRot(yaw);
						creature.setYBodyRot(yaw);
					});
				}
			}
			case "bounce" -> {
				// Boing, boing, boing: happy hops on the spot.
				for (int i = 10; i < 50; i += 10) later(player, i, p -> hop(creature, 0.42));
			}
			case "bonk" -> {
				// Off the wrong way, into the wall, bonk: a little dizzy, and no harm done.
				later(player, 8, p -> {
					if (creature instanceof Mob mob) {
						Vec3 wall = layout.wallBehind(creature.position(), p.position());
						mob.getNavigation().moveTo(wall.x, wall.y, wall.z, 1.4);
					}
				});
				later(player, 40, p -> dizzy(creature, SoundEvents.WOOD_HIT));
			}
			case "sneeze" -> later(player, 12, p -> sneeze(creature));
			case "circle" -> {
				for (int i = 8; i < 56; i++) {
					int tick = i;
					later(player, i, p -> {
						double angle = tick * 0.35;
						Vec3 head = p.position().add(Math.cos(angle) * 1.3, p.getBbHeight() + 0.3, Math.sin(angle) * 1.3);
						creature.snapTo(head.x, head.y, head.z, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
						creature.setDeltaMovement(Vec3.ZERO);
					});
				}
			}
			default -> { }
		}
		if (lasts > 0) {
			later(player, lasts, p -> {
				if (!creature.isAlive()) return;
				level.sendParticles(ParticleTypes.POOF, creature.getX(), creature.getY() + creature.getBbHeight() / 2, creature.getZ(),
					12, 0.3, 0.3, 0.3, 0.02);
				creature.discard();
				spawned.remove(creature.getUUID());
			});
		}
	}

	/** A creature does something where it stands: hearts over it, sits, spins round, or hops. */
	@Override
	public void creatureDoes(ServerPlayer player, Entity creature, String what) {
		Vec3 over = creature.position().add(0, creature.getBbHeight() + 0.4, 0);
		switch (what) {
			case "hearts" -> level.sendParticles(ParticleTypes.HEART, over.x, over.y, over.z, 5, 0.3, 0.2, 0.3, 0.0);
			case "sit" -> {
				if (creature instanceof net.minecraft.world.entity.TamableAnimal pet) {
					pet.setOrderedToSit(true);
					pet.setInSittingPose(true);
				}
				level.sendParticles(ParticleTypes.POOF, creature.getX(), creature.getY() + 0.2, creature.getZ(), 4, 0.2, 0.05, 0.2, 0.01);
			}
			case "spin" -> {
				for (int i = 0; i < 20; i += 2) {
					later(player, i, p -> {
						float yaw = creature.getYRot() + 36.0F;
						creature.setYRot(yaw);
						creature.setYHeadRot(yaw);
					});
				}
			}
			case "hop" -> {
				creature.setDeltaMovement(creature.getDeltaMovement().add(0, 0.45, 0));
				creature.needsSync = true;
			}
			case "sneeze" -> sneeze(creature);
			case "bounce" -> {
				for (int i = 0; i < 30; i += 10) later(player, i, p -> hop(creature, 0.42));
			}
			case "freeze" -> {
				// Still as a statue for two seconds, with a little frost; then off again.
				if (creature instanceof Mob mob && !mob.isNoAi()) {
					mob.setNoAi(true);
					later(player, 40, p -> mob.setNoAi(false));
				}
				level.sendParticles(ParticleTypes.SNOWFLAKE, over.x, over.y, over.z, 10, 0.3, 0.3, 0.3, 0.0);
			}
			default -> { }
		}
	}

	private void hop(Entity creature, double up) {
		if (!creature.isAlive()) return;
		creature.setDeltaMovement(creature.getDeltaMovement().add(0, up, 0));
		creature.needsSync = true;
	}

	/** Ah-choo: a puff at the nose, the sneeze, and a little jump with it. */
	private void sneeze(Entity creature) {
		if (!creature.isAlive()) return;
		Vec3 nose = creature.getEyePosition().add(creature.getLookAngle().scale(0.4));
		level.sendParticles(ParticleTypes.POOF, nose.x, nose.y, nose.z, 8, 0.1, 0.1, 0.1, 0.02);
		level.playSound(null, nose.x, nose.y, nose.z, SoundEvents.PANDA_SNEEZE, SoundSource.NEUTRAL, 0.8F, 1.3F);
		hop(creature, 0.3);
	}

	/** A bump and stars round the head: a slapstick bonk. */
	private void dizzy(Entity creature, net.minecraft.sounds.SoundEvent sound) {
		if (!creature.isAlive()) return;
		Vec3 head = creature.position().add(0, creature.getBbHeight() + 0.3, 0);
		level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 10, 0.25, 0.1, 0.25, 0.1);
		level.playSound(null, head.x, head.y, head.z, sound, SoundSource.NEUTRAL, 0.8F, 1.4F);
		hop(creature, 0.25);
	}

	/**
	 * A lightning rod out of the button, and a little bolt of sparks down onto it when it lands: the
	 * rod glows and crackles a moment, and is gone again. Drawn with particles, not real lightning,
	 * so there is no thunder, no fire and nothing anybody could be hurt by.
	 */
	@Override
	public void strike(ServerPlayer player, int lasts) {
		BlockState rod = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("lightning_rod")).defaultBlockState();
		BlockPos spot = spotFor(player, rod, false);
		if (spot == null) return;
		puff();
		reserved.add(spot);
		ItemEntity visual = throwItem(Vec3.atBottomCenterOf(spot).add(0, 0.2, 0), new ItemStack(rod.getBlock().asItem()));
		visual.setNeverPickUp();
		level.addFreshEntity(visual);
		sent("strike", visual.position());
		later(player, FLIGHT, p -> {
			visual.discard();
			reserved.remove(spot);
			if (!level.getBlockState(spot).isAir()) return;
			placeAt(spot, rod);
			Vec3 tip = Vec3.atBottomCenterOf(spot).add(0, 1.0, 0);
			for (int i = 0; i < 6; i++) {
				int step = i;
				later(p, 6 + step, q -> {
					double y = tip.y + 3.0 - step * 0.5;
					double x = tip.x + (step % 2 == 0 ? 0.15 : -0.15);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, tip.z, 12, 0.05, 0.25, 0.05, 0.0);
					level.sendParticles(ParticleTypes.END_ROD, x, y, tip.z, 2, 0.02, 0.2, 0.02, 0.0);
				});
			}
			later(p, 12, q -> {
				BlockState now = level.getBlockState(spot);
				if (now.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock) level.setBlock(spot, now.setValue(net.minecraft.world.level.block.LightningRodBlock.POWERED, true), FLAGS);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 40, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.35F, 1.6F);
			});
			if (lasts > 0) later(p, 12 + lasts, q -> vanishBlock(spot, rod));
		});
	}

	/**
	 * A tiny hut of planks around a spot: the eight squares round it, two high, with the side towards
	 * the room's door left open, and a roof only when the child fits under it. Only into empty room
	 * squares; gone in a puff after {@code lasts} ticks, and at the next round whatever happens.
	 */
	void hut(ServerPlayer player, BlockPos at, int lasts) {
		hut(player, at, lasts, false);
	}

	/** The same, for a scene, which may build over its own squares. */
	@Override
	public void hut(ServerPlayer player, BlockPos at, int lasts, boolean forScene) {
		hut(player, at, lasts, forScene, Blocks.OAK_PLANKS.defaultBlockState());
	}

	@Override
	public void hut(ServerPlayer player, BlockPos at, int lasts, boolean forScene, BlockState planks) {
		List<BlockPos> built = new ArrayList<>();
		boolean roof = player.getBbHeight() <= 1.9F;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				// The doorway: towards the door, always open.
				if (dx == 0 && dz == 1) continue;
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos pos = at.offset(dx, dy, dz);
					if (hutMay(pos, forScene)) {
						placeAt(pos, planks);
						built.add(pos.immutable());
					}
				}
			}
		}
		if (roof) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = at.offset(dx, 2, dz);
					if (hutMay(pos, forScene)) {
						placeAt(pos, planks);
						built.add(pos.immutable());
					}
				}
			}
		}
		Vec3 centre = Vec3.atCenterOf(at.above());
		level.sendParticles(ParticleTypes.POOF, centre.x, centre.y, centre.z, 20, 1.0, 0.8, 1.0, 0.02);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		later(player, lasts, p -> {
			for (BlockPos pos : built) vanishBlock(pos, planks);
		});
	}

	/** An empty square inside the room that is none of the room's own: the board, the tiles, the button, the door. */
	private boolean hutMay(BlockPos pos, boolean forScene) {
		int x = pos.getX() - origin.getX();
		int z = pos.getZ() - origin.getZ();
		if (Math.abs(x) >= HALF - 1 || Math.abs(z) >= HALF - 1 || pos.getY() < origin.getY() || pos.getY() >= origin.getY() + HEIGHT - 1) return false;
		if (!level.getBlockState(pos).isAir() || reserved.contains(pos) || room.holds(pos)) return false;
		if (pos.equals(prizeMarker()) || pos.equals(doorLower()) || pos.equals(doorLower().above())) return false;
		return forScene || room.scene() == null || !room.scene().occupies(pos);
	}

	/** Every creature runs off from the child, with a hop. */
	@Override
	public void scatterCreatures(ServerPlayer player) {
		for (Entity creature : creatures()) {
			Vec3 away = creature.position().subtract(player.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(0.4) : new Vec3(0.4, 0, 0);
			creature.setDeltaMovement(flat.x, 0.35, flat.z);
			creature.needsSync = true;
			if (creature instanceof Mob mob) {
				Vec3 corner = layout.farCorner(player.position());
				mob.getNavigation().moveTo(corner.x, corner.y, corner.z, 1.5);
			}
		}
	}

	/** One snowball out of the button, at the child. */
	@Override
	public void throwSnowball(ServerPlayer player) {
		puff();
		Vec3 from = mouth();
		net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball =
			new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(level, from.x, from.y, from.z,
				new ItemStack(net.minecraft.world.item.Items.SNOWBALL));
		Vec3 to = player.getEyePosition().subtract(0, 0.3, 0).subtract(from);
		ball.shoot(to.x, to.y + to.horizontalDistance() * 0.12, to.z, 0.9F, 3.0F);
		level.addFreshEntity(ball);
		sent("snowball", from);
	}

	/** Things fly at the child's head, bonk, and tumble off to lie on the floor a moment. */
	@Override
	public void bonk(ServerPlayer player, List<ItemStack> stacks, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
		puff();
		Vec3 head = player.position().add(0, player.getBbHeight() + 0.1, 0);
		for (int i = 0; i < stacks.size(); i++) {
			ItemEntity visual = throwItem(head, stacks.get(i));
			visual.setNeverPickUp();
			level.addFreshEntity(visual);
			prop(visual);
			sent("bonk", visual.position());
			double spread = (i - (stacks.size() - 1) / 2.0) * 0.18;
			later(player, FLIGHT, p -> {
				level.playSound(null, head.x, head.y, head.z, sound, SoundSource.PLAYERS, 0.8F, 1.3F);
				level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 6, 0.2, 0.1, 0.2, 0.1);
				visual.setDeltaMovement(0.18 + spread, 0.28, 0.12 - spread);
				visual.needsSync = true;
			});
			later(player, FLIGHT + 40, p -> visual.discard());
		}
	}

	/** The child turned round, a little at a time. */
	@Override
	public void spin(ServerPlayer player, float turns, int ticks) {
		float step = 360.0F * turns / ticks;
		java.util.Set<net.minecraft.world.entity.Relative> keep = java.util.EnumSet.of(
			net.minecraft.world.entity.Relative.X, net.minecraft.world.entity.Relative.Y, net.minecraft.world.entity.Relative.Z,
			net.minecraft.world.entity.Relative.Y_ROT, net.minecraft.world.entity.Relative.X_ROT,
			net.minecraft.world.entity.Relative.DELTA_X, net.minecraft.world.entity.Relative.DELTA_Y, net.minecraft.world.entity.Relative.DELTA_Z);
		for (int i = 0; i < ticks; i++) {
			later(player, i, p -> p.teleportTo(p.level(), 0, 0, 0, keep, step, 0, false));
		}
	}

	/** Something rolls out of the button and across the floor, turning over, and is gone at the wall. */
	@Override
	public void roll(ServerPlayer player, ItemStack stack, int ticks) {
		puff();
		Display.ItemDisplay rolling = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (rolling == null) return;
		// Out of the button's mouth, a drop to the floor below it, and then the roll.
		Vec3 mouth = mouth();
		Vec3 from = new Vec3(button().getX() + 0.5, origin.getY() + 0.5, origin.getZ() - 0.5);
		Vec3 to = new Vec3(origin.getX() - 4.5, origin.getY() + 0.5, origin.getZ() - 0.5);
		rolling.snapTo(mouth.x, mouth.y, mouth.z, 0, 0);
		rolling.setItemStack(stack.copy());
		rolling.setItemTransform(ItemDisplayContext.FIXED);
		rolling.setPosRotInterpolationDuration(2);
		level.addFreshEntity(rolling);
		prop(rolling);
		sent("roll", mouth);
		int drop = 6;
		for (int i = 1; i <= drop; i++) {
			Vec3 at = mouth.lerp(from, i / (double) drop);
			later(player, i, p -> rolling.snapTo(at.x, at.y, at.z, 0, 0));
		}
		for (int i = 1; i <= ticks; i++) {
			double t = i / (double) ticks;
			Vec3 at = from.lerp(to, t);
			float turn = (float) (t * from.distanceTo(to) * 2);
			later(player, drop + i, p -> {
				rolling.snapTo(at.x, at.y, at.z, 0, 0);
				rolling.setTransformationInterpolationDelay(0);
				rolling.setTransformationInterpolationDuration(1);
				rolling.setTransformation(new Transformation(null, new org.joml.Quaternionf().rotateZ(turn), new Vector3f(1.0F, 1.0F, 1.0F), null));
			});
		}
		later(player, drop + ticks + 1, p -> {
			level.sendParticles(ParticleTypes.POOF, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.02);
			rolling.discard();
		});
	}

	/** An item entity at the button, thrown so that it comes down about here after the flight. */
	ItemEntity throwItem(Vec3 target, ItemStack stack) {
		return throwItem(mouth(), target, stack, FLIGHT);
	}

	/** An item entity thrown from here to come down about there, this many ticks later. */
	@Override
	public ItemEntity throwItem(Vec3 from, Vec3 target, ItemStack stack, int ticks) {
		ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, stack.copy());
		double t = ticks;
		Vec3 delta = target.subtract(from);
		// Straight-line horizontal speed, and enough upward speed to arc over and come down; the
		// game's drag makes it land a little short, which is at the child's feet rather than past.
		item.setDeltaMovement(delta.x / t * 1.1, (delta.y + 0.5 * ITEM_GRAVITY * t * t) / t, delta.z / t * 1.1);
		return item;
	}

	void prop(Entity made) {
		props.removeIf(Entity::isRemoved);
		props.add(made);
	}

	void clearProps() {
		props.forEach(Entity::discard);
		props.clear();
	}

	void keep(Entity made) {
		spawned.add(made.getUUID());
		while (spawned.size() > MAX_SPAWNED) {
			Entity oldest = level.getEntity(spawned.removeFirst());
			if (oldest != null) oldest.discard();
		}
	}

	// --- Tidying away ---

	/** A boat or cart a word brought goes with its round, like a placed block: left, it drifts into later rounds and wedges against the board. */
	void clearVehicles() {
		for (java.util.Iterator<UUID> it = spawned.iterator(); it.hasNext(); ) {
			Entity entity = level.getEntity(it.next());
			if (entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity vehicle) {
				vehicle.ejectPassengers();
				vehicle.discard();
				it.remove();
			}
		}
	}

	/** Every creature gone in a puff, and everything else a word left: a new stage's clean slate. */
	void clearForStage() {
		for (UUID id : spawned) {
			Entity entity = level.getEntity(id);
			if (entity == null) continue;
			level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
			entity.discard();
		}
		spawned.clear();
		clearProps();
		clearPlaced();
		reserved.clear();
	}

	/** Everything a word left, gone: the room is closing. */
	void clearAll() {
		for (UUID id : spawned) {
			Entity entity = level.getEntity(id);
			if (entity != null) entity.discard();
		}
		spawned.clear();
		clearProps();
		clearPlaced();
		reserved.clear();
	}
}
