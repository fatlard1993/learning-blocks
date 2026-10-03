package justfatlard.learning_blocks.classroom;

import java.util.List;
import java.util.Set;
import justfatlard.learning_blocks.Safely;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the room: nothing gets hurt, nobody gets hungry, and nobody falls out of the world.
 *
 * <p>Survival mode, set at the door, with the room's own checks on breaking and placing
 * ({@code Rooms.mayBreak}, {@code Rooms.useBlock}), is what keeps the room itself from being dug
 * up; weather is kept out by {@code LevelMixin} and {@link #tick}; these are the rest.
 */
public final class Classroom {
	private Classroom() {}

	/** How far under the floor counts as having fallen off, rather than hopping. */
	private static final int FELL = 8;

	/**
	 * Nothing in the classroom takes damage: not the child, not the cat they just read into being.
	 * An operator's {@code /kill} still works, since that is a deliberate act by a grown-up; falling
	 * out of the world does not, because the tick below catches that first and puts them back.
	 */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!Places.isClassroom(entity.level())) return true;
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypes.FELL_OUT_OF_WORLD);
	}

	/** Whether it was raining in the overworld last tick, to catch the tick it starts or stops. */
	private static boolean wasRaining;

	public static void tick(MinecraftServer server) {
		ServerLevel classroom = server.getLevel(Places.CLASSROOM);
		if (classroom == null) return;

		// The level mixin keeps the classroom's own weather off, but the overworld announces the
		// moment it starts or stops raining to every player on the server, wherever they stand.
		// That tick, each child in the classroom is told their own weather again.
		boolean raining = server.overworld().isRaining();
		boolean turned = raining != wasRaining;
		wasRaining = raining;

		for (ServerPlayer player : List.copyOf(classroom.players())) {
			Safely.run("The classroom's care of " + player.getGameProfile().name(), () -> care(player, turned));
		}
	}

	/** Their own sky, a full stomach, and back on the floor after a fall. */
	private static void care(ServerPlayer player, boolean turned) {
		ServerLevel classroom = (ServerLevel) player.level();
		if (turned) Sky.overworldTurned(player);
		Travel.checkStuck(player);
		if (!Places.isClassroom(player.level())) return;
		if (player.getFoodData().getFoodLevel() < 20) player.getFoodData().setFoodLevel(20);
		if (player.getFoodData().getSaturationLevel() < 5) player.getFoodData().setSaturation(5);

		// Under the floor, or (the room has no lid, for its sky) up over the walls on a stack of something.
		if (player.getY() < Places.FLOOR_Y - FELL || player.getY() >= Places.FLOOR_Y + Places.WALL_HEIGHT) {
			Room room = Rooms.of(player);
			Vec3 back = room != null ? room.spawn() : Vec3.atBottomCenterOf(Plots.origin(player));
			player.resetFallDistance();
			player.setDeltaMovement(Vec3.ZERO);
			player.teleportTo(classroom, back.x, back.y, back.z, Set.of(), Room.FACING_YAW, 0, true);
		}
	}
}
