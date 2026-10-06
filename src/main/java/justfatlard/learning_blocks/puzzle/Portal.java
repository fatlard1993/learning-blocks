package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.ForTests;
import java.util.List;
import java.util.UUID;
import justfatlard.learning_blocks.Later;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The way home: a portal in the back wall, walked through. No clicking: a child who walks into an
 * open one goes home, which is what a child does with a doorway. Kept in for lessons, it is dark
 * with a soft wall across it and the count over it, and walking into it says how many are left.
 * Its last lesson done, it lights up with a whoosh.
 *
 * <p>Not nether portal blocks, which would send a player to the nether: a frame of blocks, an
 * invisible barrier while locked, and a pane of coloured glass drawn by a display entity while open.
 */
final class Portal {
	private static final int FLAGS = RoomLayout.FLAGS;
	/** How long a child who walked into the opening any way round stands wholly inside it before it takes them home. */
	static final int SETTLED_IN = 20;
	private final java.util.Map<UUID, Long> fullSince = new java.util.HashMap<>();
	private final java.util.Set<UUID> walkedIn = new java.util.HashSet<>();

	/** How often walking into a locked portal may say the count again. */
	private static final int BUMP_EVERY = 80;
	private static final BlockState FRAME_DARK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState FRAME_LIT = Blocks.CRYING_OBSIDIAN.defaultBlockState();

	private final ServerLevel level;
	private final RoomLayout layout;
	private @Nullable UUID sign;
	private @Nullable UUID fill;
	private boolean open;
	private final java.util.Map<UUID, Long> bumpedAt = new java.util.HashMap<>();
	private final java.util.Map<UUID, Long> enteredAt = new java.util.HashMap<>();
	private final Room room;

	Portal(Room room, ServerLevel level, RoomLayout layout) {
		this.room = room;
		this.level = level;
		this.layout = layout;
	}

	AABB opening() {
		BlockPos low = layout.portalLower();
		return new AABB(low.getX(), low.getY(), low.getZ(), low.getX() + RoomLayout.PORTAL_WIDE, low.getY() + RoomLayout.PORTAL_HIGH, low.getZ() + 1);
	}

	boolean open() {
		return open;
	}

	/**
	 * The portal as the lessons left say: dark, walled and counted while any keep the child in;
	 * open and swirling when none do. {@code flourish} is the moment the last one is done.
	 */
	void refresh(ServerPlayer player, boolean flourish) {
		// Reading together, the lock is each reader's own. The opening is walled only while it is
		// shut for everyone; a reader still kept who walks into an open one is turned back, and
		// sees their own frame dark.
		List<ServerPlayer> readers = new java.util.ArrayList<>(room.present());
		if (readers.stream().noneMatch(p -> p.getUUID().equals(player.getUUID()))) readers.add(player);
		boolean mine = !Travel.kept(player);
		open = readers.stream().anyMatch(p -> !Travel.kept(p));
		BlockPos low = layout.portalLower();
		for (int x = 0; x < RoomLayout.PORTAL_WIDE; x++) {
			for (int y = 0; y < RoomLayout.PORTAL_HIGH; y++) {
				level.setBlock(low.offset(x, y, 0), (open ? Blocks.AIR : Blocks.BARRIER).defaultBlockState(), FLAGS);
			}
		}
		for (BlockPos pos : layout.portalFrame()) level.setBlock(pos, open ? FRAME_LIT : FRAME_DARK, FLAGS);
		fill(open);

		for (ServerPlayer reader : readers) {
			boolean theirs = !Travel.kept(reader);
			// Their own view of the frame, a moment after the real blocks have gone out.
			if (theirs != open) Later.after(reader, 2, p -> showFrame(p, !Travel.kept(p)));
		}

		List<String> counts = new java.util.ArrayList<>();
		for (ServerPlayer reader : readers) {
			int left = Travel.lessons(reader);
			if (left > 0 && Travel.kept(reader)) counts.add(readers.size() > 1 ? reader.getGameProfile().name() + ": " + left + " left" : left + " left");
		}
		Display.TextDisplay count = sign == null ? null : level.getEntity(sign) instanceof Display.TextDisplay found ? found : null;
		if (!counts.isEmpty()) {
			if (count == null) {
				count = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
				if (count == null) return;
				Vec3 at = new Vec3(low.getX() + RoomLayout.PORTAL_WIDE / 2.0, low.getY() + RoomLayout.PORTAL_HIGH + 1.25, low.getZ() - 0.45);
				count.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
				count.setBillboardConstraints(Display.BillboardConstraints.CENTER);
				count.setTransformation(new Transformation(null, null, new Vector3f(1.6F, 1.6F, 1.6F), null));
				level.addFreshEntity(count);
				sign = count.getUUID();
			}
			count.setText(Component.literal(String.join("\n", counts)).withStyle(net.minecraft.ChatFormatting.GOLD));
		} else {
			if (count != null) count.discard();
			sign = null;
		}
		if (mine && flourish) {
			Vec3 at = opening().getCenter();
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z - 0.5, 40, 0.6, 0.9, 0.3, 0.3);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z - 0.3, 80, 0.6, 1.0, 0.2, 0.15);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.35F, 1.6F);
		}
	}

	/** This reader's own frame: lit if they may go, dark if not, whatever the blocks say for the others. */
	private void showFrame(ServerPlayer player, boolean lit) {
		if (Rooms.of(player) != room) return;
		for (BlockPos pos : layout.portalFrame()) {
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, lit ? FRAME_LIT : FRAME_DARK));
		}
	}

	boolean openFor(ServerPlayer player) {
		return !Travel.kept(player);
	}

	private boolean fillLit;

	/**
	 * The pane across the opening: black while locked, so it reads as shut and not as sky,
	 * and purple, glowing, while open. Null takes it down.
	 */
	private void fill(@Nullable Boolean lit) {
		Entity existing = fill == null ? null : level.getEntity(fill);
		if (lit == null || existing != null && fillLit != lit) {
			if (existing != null) existing.discard();
			fill = null;
			existing = null;
			if (lit == null) return;
		}
		if (existing != null) return;
		fillLit = lit;
		Display.BlockDisplay pane = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (pane == null) return;
		BlockPos low = layout.portalLower();
		pane.snapTo(low.getX(), low.getY(), low.getZ() + 0.45, 0.0F, 0.0F);
		pane.setBlockState((lit ? Blocks.STAINED_GLASS.purple() : Blocks.CONCRETE.black()).defaultBlockState());
		pane.setTransformation(new Transformation(null, null, new Vector3f(RoomLayout.PORTAL_WIDE, RoomLayout.PORTAL_HIGH, 0.1F), null));
		if (lit) pane.setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
		level.addFreshEntity(pane);
		fill = pane.getUUID();
	}

	/**
	 * Each tick: an open portal swirls, and a child standing in it goes home, once; a locked one
	 * walked into says how many lessons are left, now and then.
	 */
	void tick(ServerPlayer player, Room room) {
		long now = level.getGameTime();
		AABB opening = opening();
		UUID id = player.getUUID();
		if (open && openFor(player)) {
			if (now % 4 == 0 && room.present().stream().findFirst().map(first -> first == player).orElse(true)) {
				Vec3 at = opening.getCenter();
				level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z - 0.2, 6, 0.6, 1.0, 0.1, 0.4);
				if (now % 12 == 0) level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z - 0.2, 2, 0.6, 1.0, 0.1, 0.0);
			}
			// In the doorway itself, not just near it: the player's body overlapping the opening,
			// facing into it and walking forward; or, any way round (sideways, backwards), the whole
			// body inside the opening for a second, having walked there themselves. A child pushed in
			// by a word (fan), standing still or just backing up to see the board is not leaving.
			boolean walkingIn = player.getLookAngle().z > 0.2 && player.getLastClientInput().forward();
			AABB body = player.getBoundingBox();
			boolean wholly = body.minX >= opening.minX - 0.01 && body.maxX <= opening.maxX + 0.01
				&& body.minZ >= opening.minZ - 0.01 && body.maxZ <= opening.maxZ + 0.01 && body.minY >= opening.minY - 0.5;
			if (wholly) {
				fullSince.putIfAbsent(id, now);
				var input = player.getLastClientInput();
				if (input.forward() || input.backward() || input.left() || input.right()) walkedIn.add(id);
			} else {
				fullSince.remove(id);
				walkedIn.remove(id);
			}
			boolean settledIn = wholly && walkedIn.contains(id) && now - fullSince.get(id) >= SETTLED_IN;
			if ((walkingIn || settledIn) && body.intersects(opening.deflate(0.05, 0.0, 0.2)) && !room.leaving(player)) {
				Long entered = enteredAt.get(id);
				if (entered != null && now - entered < 20) return;
				enteredAt.put(id, now);
				Narrator.note(player, "portal:enter");
				LearningBlocks.LOGGER.info("[{}] {} walked through the portal home", LearningBlocks.MOD_ID, player.getGameProfile().name());
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3F, 1.8F);
				Travel.leave(player);
			}
			return;
		}
		// Open for someone else but not this reader: no wall, so they are turned back out of it.
		if (open && player.getBoundingBox().intersects(opening.deflate(0.05, 0.0, 0.2))) {
			player.teleportTo(level, player.getX(), player.getY(), opening.minZ - 0.6, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		}
		// Locked: bumping the soft wall says the count, not too often.
		Long bumped = bumpedAt.get(id);
		if (player.getBoundingBox().inflate(0.0, 0.0, 0.35).intersects(opening) && (bumped == null || now - bumped >= BUMP_EVERY)) {
			bumpedAt.put(id, now);
			int left = Travel.lessons(player);
			Narrator.note(player, "portal:bump:" + left);
			if (justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null && !room.busy && !Narrator.isPlaying(player)) {
				Narrator.play(player, new Narration().say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left)));
			}
		}
	}

	@ForTests
	@Nullable Entity sign() {
		return sign == null ? null : level.getEntity(sign);
	}

	void close() {
		if (sign != null) {
			Entity found = level.getEntity(sign);
			if (found != null) found.discard();
			sign = null;
		}
		fill(null);
	}

	static String openLine(boolean doneForToday) {
		return doneForToday ? "phrase/done_for_today" : "phrase/portal_open";
	}
}
