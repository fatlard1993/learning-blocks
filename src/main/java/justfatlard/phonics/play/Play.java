package justfatlard.phonics.play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.relate.Relations;
import justfatlard.phonics.relate.Thing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Toys and plushies, played with in the world. A right-click plays the thing's animation: the block
 * is hidden (its {@link #HIDDEN} state, which draws nothing) and a display of it moves in its
 * place, carrying whatever is in or on it; at the end the display goes and the block is shown,
 * exactly as it was. A block tick scheduled for just after the end shows it again whatever
 * happened in between - a chunk unloaded mid-loop, a server stopped - so a toy is never left
 * invisible.
 *
 * <p>A toy or plushie used on another puts it in, on, or onto it, by {@link Relations#fit}: the doll
 * into the crib, the owl into the car, a cap onto the owl. What is put there is an item display at
 * the block, saved with the chunk like any entity, which is both how it shows and how it is kept;
 * sneak and right-click with an empty hand takes it back out, and breaking the block drops both.
 */
public final class Play {
	private Play() {}

	/** Shown or not: a block whose display is playing in its place draws nothing. */
	public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");
	/** On a display playing a block's part. */
	static final String PLAYING = Phonics.MOD_ID + ".playing";
	/** On a display that is something put in, on or onto a block. */
	static final String CONTENTS = Phonics.MOD_ID + ".contents";
	private static final String REL = Phonics.MOD_ID + ".rel:";
	/** One play a second at most, a block. */
	private static final int EVERY = 20;

	/**
	 * A play going on: the displays playing the block (one of the whole block, or the body and each
	 * moving part; none for something bobbing in a tub), what is in it, and where it rests.
	 */
	private record Running(ServerLevel level, BlockPos pos, Anim anim, List<Piece> pieces, @Nullable Display contents, float facingYaw,
			Matrix4f rest, long started) {
		/** The first display, which stands for the rest: gone, and the play is over. */
		@Nullable Display copy() {
			return pieces.isEmpty() ? null : pieces.getFirst().display();
		}
	}

	/** One display playing a block: the whole of it (no part), its body, or one part. */
	private record Piece(Display display, @Nullable String body, Anim.@Nullable Part part) {}

	private static final List<Running> running = new ArrayList<>();
	private static final Map<String, Long> lastPlayed = new HashMap<>();

	/** For tests: the last animation started, by block. */
	public static volatile @Nullable String lastStarted;

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Play::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopAll());
	}

	private static String key(Level level, BlockPos pos) {
		return level.dimension().identifier() + "@" + pos.asLong();
	}

	/** Whether this block is playing right now. */
	public static boolean playing(Level level, BlockPos pos) {
		for (Running run : running) if (run.level() == level && run.pos().equals(pos)) return true;
		return false;
	}

	/**
	 * Play this block's animation, if it is not playing and has not played in the last second.
	 * True if it started.
	 */
	public static boolean play(ServerLevel level, BlockPos pos, BlockState state, Anim anim) {
		if (!state.hasProperty(HIDDEN) || state.getValue(HIDDEN) || playing(level, pos)) return false;
		anim.keys().sort(java.util.Comparator.comparingInt(Anim.Key::tick));
		long now = level.getGameTime();
		Long last = lastPlayed.get(key(level, pos));
		if (last != null && now - last < EVERY) return false;
		lastPlayed.put(key(level, pos), now);
		float yaw = facingYaw(state);
		List<Piece> pieces = pieces(level, pos, state, anim, yaw);
		if (pieces.isEmpty()) return false;
		Display contents = contentsAt(level, pos);
		Matrix4f rest = contents == null ? new Matrix4f() : restPose(contents, state, holdOf(level, pos, contents));
		level.setBlock(pos, state.setValue(HIDDEN, true), Block.UPDATE_CLIENTS);
		// Whatever happens before the end, this shows it again.
		level.scheduleTick(pos, state.getBlock(), anim.length() + 10);
		running.add(new Running(level, pos.immutable(), anim, pieces, contents, yaw, rest, now));
		lastStarted = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + ":" + pos.toShortString();
		step(running.getLast(), 0);
		return true;
	}

	/**
	 * The displays that play a block: one of the whole block; or, for an animation of a toy drawn in
	 * pieces, an item display of its body and one of each part, each with its piece's model.
	 */
	private static List<Piece> pieces(ServerLevel level, BlockPos pos, BlockState state, Anim anim, float facingYaw) {
		List<Piece> pieces = new ArrayList<>();
		Vec3 foot = Vec3.atBottomCenterOf(pos);
		if (anim.pieces() == null || anim.parts().isEmpty()) {
			Display.BlockDisplay copy = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (copy == null) return pieces;
			copy.setBlockState(state);
			copy.setTransformation(new com.mojang.math.Transformation(new Matrix4f().translate(-0.5F, 0, -0.5F)));
			pieces.add(new Piece(copy, null, null));
		} else {
			Display.ItemDisplay body = pieceDisplay(level, anim.pieces() + "_body");
			if (body == null) return pieces;
			pieces.add(new Piece(body, anim.pieces(), null));
			for (Anim.Part part : anim.parts()) {
				Display.ItemDisplay shown = pieceDisplay(level, anim.pieces() + "_" + part.name());
				if (shown != null) pieces.add(new Piece(shown, null, part));
			}
		}
		for (Piece piece : pieces) {
			piece.display().snapTo(foot.x, foot.y, foot.z, 0, 0);
			if (piece.display() instanceof Display.ItemDisplay) {
				piece.display().setTransformation(transformation(pieceMatrix(new Matrix4f(), facingYaw, piece.part(), 0)));
			}
			piece.display().addTag(PLAYING);
			level.addFreshEntity(piece.display());
		}
		return pieces;
	}

	private static Display.@Nullable ItemDisplay pieceDisplay(ServerLevel level, String model) {
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return null;
		ItemStack stack = new ItemStack(net.minecraft.world.item.Items.PAPER);
		stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, Phonics.id("toy_part/" + model));
		display.setItemStack(stack);
		display.setItemTransform(ItemDisplayContext.NONE);
		return display;
	}

	/**
	 * Where a piece is drawn, from the whole toy's pose: the block's model as an item draws it
	 * (about its middle), stood on the foot, turned the way the toy faces, and the part in its own
	 * pose.
	 */
	private static Matrix4f pieceMatrix(Matrix4f whole, float facingYaw, Anim.@Nullable Part part, int tick) {
		Matrix4f matrix = new Matrix4f(whole).translate(0, 0.5F, 0).rotateY(facingYaw);
		// The item display turns the model half round as it draws it, after this; a part's pose,
		// in the model's own frame, goes in turned to match.
		if (part != null) matrix.rotateY((float) Math.PI).mul(Anim.partMatrix(part, Anim.partAt(part, tick))).rotateY((float) Math.PI);
		return matrix;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Running> it = running.iterator(); it.hasNext(); ) {
			Running run = it.next();
			int at = (int) (run.level().getGameTime() - run.started());
			// Unloaded or broken mid-play: the scheduled tick (or the break) shows it again.
			if (!run.level().isLoaded(run.pos()) || run.copy() != null && run.copy().isRemoved()) {
				discard(run);
				it.remove();
				continue;
			}
			if (at > run.anim().length()) {
				finish(run);
				it.remove();
				continue;
			}
			step(run, at);
		}
	}

	/**
	 * Everything that happens at this tick: a pose to glide to, a sound, a puff. At each tick
	 * something is posed at, every display is told where to be by the next one, and glides there.
	 */
	private static void step(Running run, int at) {
		java.util.SortedSet<Integer> ticks = run.anim().poseTicks();
		if (ticks.contains(at)) {
			java.util.SortedSet<Integer> later = ticks.tailSet(at + 1);
			int next = later.isEmpty() ? at : later.first();
			int glide = Math.max(1, next - at);
			Matrix4f pose = run.anim().wholeAt(next).matrix(run.facingYaw());
			for (Piece piece : run.pieces()) {
				Matrix4f matrix = piece.display() instanceof Display.BlockDisplay
					? new Matrix4f(pose).translate(-0.5F, 0, -0.5F) : pieceMatrix(pose, run.facingYaw(), piece.part(), next);
				pose(piece.display(), matrix, glide);
			}
			if (run.contents() != null && !run.contents().isRemoved()) pose(run.contents(), new Matrix4f(pose).mul(run.rest()), glide);
		}
		for (Anim.Event event : run.anim().events()) {
			if (event.tick() != at) continue;
			Vec3 foot = Vec3.atBottomCenterOf(run.pos());
			if (event.sound() != null) run.level().playSound(null, run.pos(), event.sound(), SoundSource.BLOCKS, 0.8F, event.pitch());
			if (event.particle() != null) run.level().sendParticles(event.particle(), foot.x, foot.y + event.height(), foot.z, event.count(), 0.2, 0.15, 0.2, 0.01);
		}
	}

	private static void pose(Display display, Matrix4f matrix, int glide) {
		display.setTransformationInterpolationDelay(0);
		display.setTransformationInterpolationDuration(glide);
		display.setTransformation(transformation(matrix));
	}

	/**
	 * A pose as a move, one turn and a scale. Not {@code new Transformation(matrix)}: that splits
	 * the turn in two by a decomposition that, for an even scale, can split it differently from one
	 * pose to the next, and the game glides each half on its own, so a wheel turning on a car
	 * turning flew off between poses.
	 */
	public static com.mojang.math.Transformation transformation(Matrix4f matrix) {
		org.joml.Vector3f scale = matrix.getScale(new org.joml.Vector3f());
		Quaternionf turn = matrix.getNormalizedRotation(new Quaternionf());
		return new com.mojang.math.Transformation(matrix.getTranslation(new org.joml.Vector3f()), turn, scale, null);
	}

	/** The end: the display goes, the block shows again, whatever was in it back where it sits. */
	private static void finish(Running run) {
		if (run.contents() != null && !run.contents().isRemoved()) pose(run.contents(), new Matrix4f(run.rest()), 1);
		if (run.copy() == null) return;
		discard(run);
		show(run.level(), run.pos());
	}

	private static void discard(Running run) {
		for (Piece piece : run.pieces()) piece.display().discard();
	}

	/** The block shown again, if it is still one of ours, hidden. */
	static void show(ServerLevel level, BlockPos pos) {
		BlockState now = level.getBlockState(pos);
		if (now.hasProperty(HIDDEN) && now.getValue(HIDDEN)) level.setBlock(pos, now.setValue(HIDDEN, false), Block.UPDATE_CLIENTS);
	}

	/**
	 * The scheduled tick after a play: if it is still hidden and nothing is playing it (a chunk
	 * unloaded mid-play, a restart), show it and clear away any display left playing it.
	 */
	public static void recover(BlockState state, ServerLevel level, BlockPos pos) {
		if (playing(level, pos)) return;
		for (Entity left : level.getEntities((Entity) null, new AABB(pos).inflate(0.5), entity -> entity.entityTags().contains(PLAYING))) left.discard();
		if (state.hasProperty(HIDDEN) && state.getValue(HIDDEN)) level.setBlock(pos, state.setValue(HIDDEN, false), Block.UPDATE_CLIENTS);
		Display contents = contentsAt(level, pos);
		if (contents != null) {
			Thing.Hold hold = holdOf(level, pos, contents);
			pose(contents, restPose(contents, level.getBlockState(pos), hold), 1);
		}
	}

	private static void stopAll() {
		for (Running run : running) {
			if (run.copy() == null) continue;
			discard(run);
			if (run.level().isLoaded(run.pos())) show(run.level(), run.pos());
		}
		running.clear();
	}

	/** Its forward, as radians turned from south: a toy faces whoever put it down. */
	static float facingYaw(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return 0;
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		return (float) Math.atan2(facing.getStepX(), facing.getStepZ());
	}

	// --- Things put in, on and onto each other ---

	/** What is in or on this block, if anything: an item display at its foot. */
	public static @Nullable Display contentsAt(Level level, BlockPos pos) {
		List<Display.ItemDisplay> found = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(0.25),
			display -> display.entityTags().contains(CONTENTS) && display.blockPosition().equals(pos));
		return found.isEmpty() ? null : found.getFirst();
	}

	/** The item that is in or on this block, or empty. */
	public static ItemStack contents(Level level, BlockPos pos) {
		Display contents = contentsAt(level, pos);
		return contents instanceof Display.ItemDisplay item ? item.getItemStack() : ItemStack.EMPTY;
	}

	/** How it was put there: in, on, rides, head. */
	public static Relations.@Nullable Rel relOf(Display contents) {
		for (String tag : contents.entityTags()) if (tag.startsWith(REL)) return Relations.Rel.valueOf(tag.substring(REL.length()));
		return null;
	}

	/**
	 * A child using what they hold on a placed toy, plushie or prop: if it fits, in, on or onto it it
	 * goes, one, out of their hand. True if it went.
	 */
	public static boolean put(ServerLevel level, BlockPos pos, BlockState state, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		Thing heldThing = held.isEmpty() ? null : Relations.forItem(held.getItem());
		Thing target = Relations.forBlock(state);
		if (heldThing == null || target == null || contentsAt(level, pos) != null || playing(level, pos)) return false;
		Relations.Fit fit = Relations.fit(heldThing, target);
		if (fit == null) return false;
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return false;
		ItemStack one = held.copyWithCount(1);
		if (!player.getAbilities().instabuild) held.shrink(1);
		Vec3 foot = Vec3.atBottomCenterOf(pos);
		display.snapTo(foot.x, foot.y, foot.z, 0, 0);
		display.setItemStack(one);
		display.setItemTransform(ItemDisplayContext.NONE);
		display.addTag(CONTENTS);
		display.addTag(REL + fit.rel().name());
		pose(display, restPose(fit.rel(), state, target, fit.hold()), 0);
		level.addFreshEntity(display);
		var sound = fit.hold() != null && fit.hold().sound().isPresent()
			? BuiltInRegistries.SOUND_EVENT.getValue(fit.hold().sound().get()) : net.minecraft.sounds.SoundEvents.WOOL_PLACE;
		if (sound != null) level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.8F, 1.2F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, foot.x, foot.y + target.height() + 0.3, foot.z, 2, 0.2, 0.1, 0.2, 0);
		// Floating, in a tub: it bobs as it lands.
		if (fit.hold() != null && fit.hold().floats()) playContents(level, pos, display, Anims.FLOAT);
		lastPut = heldThing.word() + ":" + fit.rel().name().toLowerCase(java.util.Locale.ROOT) + ":" + target.word();
		return true;
	}

	/**
	 * A thing shown in, on or onto a block by the rules a child's play follows, without a hand: a
	 * scene's doll tucked into its crib. Null if it does not fit. The caller tidies it away.
	 */
	public static @Nullable Display showIn(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
		Thing held = Relations.forItem(stack.getItem());
		Thing target = Relations.forBlock(state);
		if (held == null || target == null) return null;
		Relations.Fit fit = Relations.fit(held, target);
		if (fit == null) return null;
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return null;
		Vec3 foot = Vec3.atBottomCenterOf(pos);
		display.snapTo(foot.x, foot.y, foot.z, 0, 0);
		display.setItemStack(stack.copyWithCount(1));
		display.setItemTransform(ItemDisplayContext.NONE);
		display.addTag(REL + fit.rel().name());
		pose(display, restPose(fit.rel(), state, target, fit.hold()), 0);
		level.addFreshEntity(display);
		return display;
	}

	/** For tests: the last thing put somewhere, {@code doll:in:crib}. */
	public static volatile @Nullable String lastPut;

	/** Sneak and right-click with an empty hand: what is in it comes back out, into the hand. True if anything did. */
	public static boolean takeOut(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
		Display contents = contentsAt(level, pos);
		if (!(contents instanceof Display.ItemDisplay item) || playing(level, pos)) return false;
		ItemStack stack = item.getItemStack().copy();
		contents.discard();
		if (!player.getInventory().add(stack)) {
			Vec3 at = Vec3.atCenterOf(pos);
			level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
		}
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
		return true;
	}

	/** Broken (or pushed, or blown up): what was in it drops beside it, and anything playing it goes. */
	public static void removed(ServerLevel level, BlockPos pos) {
		for (Iterator<Running> it = running.iterator(); it.hasNext(); ) {
			Running run = it.next();
			if (run.level() == level && run.pos().equals(pos)) {
				discard(run);
				it.remove();
			}
		}
		for (Entity left : level.getEntities((Entity) null, new AABB(pos).inflate(0.5), entity -> entity.entityTags().contains(PLAYING))) left.discard();
		Display contents = contentsAt(level, pos);
		if (contents instanceof Display.ItemDisplay item) {
			Vec3 at = Vec3.atCenterOf(pos);
			level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, item.getItemStack().copy()));
			contents.discard();
		}
	}

	/** A thing in a tub bobs, by itself, with nothing else moving. */
	public static void playContents(ServerLevel level, BlockPos pos, Display contents, Anim anim) {
		if (playing(level, pos)) return;
		Matrix4f rest = restPose(contents, level.getBlockState(pos), holdOf(level, pos, contents));
		running.add(new Running(level, pos.immutable(), anim, List.of(), contents, 0, rest, level.getGameTime()));
		step(running.getLast(), 0);
	}

	/** Where a thing sits, put this way on this block, as a transformation from the block's foot. */
	static Matrix4f restPose(Relations.Rel rel, BlockState state, Thing target, Thing.@Nullable Hold hold) {
		float yaw = facingYaw(state);
		float y = hold != null ? hold.y() : rel == Relations.Rel.HEAD ? target.height() - 0.05F : target.height();
		float z = hold != null ? hold.z() : 0;
		float scale = hold != null ? hold.scale() : rel == Relations.Rel.HEAD ? 0.7F : 0.6F;
		Quaternionf facing = new Quaternionf().rotateY(yaw);
		org.joml.Vector3f back = facing.transform(new org.joml.Vector3f(0, 0, -z));
		// An item drawn as a block is drawn about its middle: up by half its height to stand on y.
		return new Matrix4f().translate(back.x, y + 0.5F * scale, back.z).rotate(facing).rotateY((float) Math.PI).scale(scale);
	}

	private static Matrix4f restPose(Display contents, BlockState state, Thing.@Nullable Hold hold) {
		Relations.@Nullable Rel rel = relOf(contents);
		Thing target = Relations.forBlock(state);
		if (rel == null || target == null) return new Matrix4f();
		return restPose(rel, state, target, hold);
	}

	private static Thing.@Nullable Hold holdOf(Level level, BlockPos pos, Display contents) {
		Thing target = Relations.forBlock(level.getBlockState(pos));
		Relations.Rel rel = relOf(contents);
		return target == null || rel == null ? null : Relations.holdFor(target, rel);
	}

	/**
	 * For tests: as if this block's chunk had unloaded mid-play - the play forgotten and its display
	 * gone, the block still hidden - for the scheduled tick to put right.
	 */
	public static void dropForTests(ServerLevel level, BlockPos pos) {
		for (Iterator<Running> it = running.iterator(); it.hasNext(); ) {
			Running run = it.next();
			if (run.level() == level && run.pos().equals(pos)) {
				discard(run);
				it.remove();
			}
		}
	}

	/** For tests: whether a display is still playing this block. */
	public static boolean displayAt(ServerLevel level, BlockPos pos) {
		return !level.getEntities((Entity) null, new AABB(pos).inflate(0.5), entity -> entity.entityTags().contains(PLAYING)).isEmpty();
	}

	/** Every thing played with, said, by the one who played: its word and its little noise, as before. */
	public static void sayWord(ServerPlayer player, String word) {
		String clip = "word/" + word;
		if (justfatlard.phonics.sound.Sounds.isRecorded(clip)) {
			justfatlard.phonics.narration.Narrator.play(player, new justfatlard.phonics.narration.Narration().say(clip));
		}
	}
}
