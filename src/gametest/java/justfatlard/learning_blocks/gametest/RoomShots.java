package justfatlard.learning_blocks.gametest;

import java.util.Set;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The room as laid out for many readers, seen from every place a reader stands, from the button,
 * from the teacher's place, and from above. Screenshots on the full run; on a smoke run, only that
 * every place is inside the room, on the floor, and clear of the button and each other.
 */
public final class RoomShots implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", RoomShots::run);
	}

	private static final ArmorStand[] stands = new ArmorStand[6];

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			ServerLevel level = (ServerLevel) player.level();
			check(Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow()), "no cat round");
			// From place 0, every tile on the shelf is in sight, over the button.
			Vec3 eye = room.standingSpot(0).add(0, player.getEyeHeight(), 0);
			for (var letter : room.puzzle().shelf()) {
				BlockPos tile = room.home(letter);
				for (double up : new double[] {0.15, 0.5, 0.85}) {
					Vec3 aim = Vec3.atBottomCenterOf(tile).add(0, up, 0);
					var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, aim, net.minecraft.world.level.ClipContext.Block.OUTLINE,
						net.minecraft.world.level.ClipContext.Fluid.NONE, player));
					check(hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(tile),
						"from place 0 the shelf tile " + letter + " is hidden by " + level.getBlockState(hit.getBlockPos()) + " at " + hit.getBlockPos());
				}
			}
			for (int place = 0; place < 6; place++) {
				Vec3 spot = room.standingSpot(place);
				BlockPos under = BlockPos.containing(spot);
				check(level.getBlockState(under).isAir() && level.getBlockState(under.below()).isSolid(), "place " + place + " is not open floor: " + spot);
				Vec3 button = Vec3.atBottomCenterOf(room.button());
				double flat = Math.hypot(spot.x - button.x, spot.z - button.z);
				check(flat >= 0.9, "place " + place + " is on top of the button: " + flat);
				if (place == 0) check(flat <= 2.5, "the button is out of easy reach from place 0: " + flat);
				for (int other = 0; other < place; other++) {
					check(spot.distanceTo(room.standingSpot(other)) >= 1.5, "places " + other + " and " + place + " are on top of each other");
				}
				// Stand-ins, so the pictures show a full room; and a cubby each.
				ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
				stand.snapTo(spot.x, spot.y, spot.z, 180, 0);
				stand.addTag("learning-blocks-justfatlard.spawned");
				level.addFreshEntity(stand);
				stands[place] = stand;
				level.setBlock(room.cubby(place), Blocks.CHEST.defaultBlockState(), 3);
			}
		});
		session.waitTicks(20);
		for (int place = 0; place < 6; place++) {
			int at = place;
			// The stand-in in this place steps aside while its picture is taken.
			session.onServer(server -> stands[at].setInvisible(true));
			standAt(session, () -> Rooms.of(session.player()).standingSpot(at), 180, 12);
			shoot(session, "learning-room-place-" + place);
			session.onServer(server -> stands[at].setInvisible(false));
		}
		session.onServer(server -> stands[0].setInvisible(true));
		standAt(session, () -> Rooms.of(session.player()).teacherSpot(), 180, 10);
		shoot(session, "learning-room-teacher");
		standAt(session, () -> Rooms.of(session.player()).spawn().add(0, 0, -0.6), 180, 30);
		shoot(session, "learning-room-button");
		standAt(session, () -> Vec3.atBottomCenterOf(Rooms.of(session.player()).origin()).add(7, 4.5, 8.5), 140, 35);
		shoot(session, "learning-room-above");
		// Five more readers, so every cubby is someone's: named, with their banner.
		java.util.List<net.fabricmc.fabric.api.entity.FakePlayer> readers = new java.util.ArrayList<>();
		session.onServer(server -> {
			ServerPlayer player = session.player();
			var room = Rooms.of(player);
			for (int i = 1; i < 6; i++) {
				var reader = StandIns.inClassroom((ServerLevel) player.level(), "Reader" + (i + 1), room.teacherSpot().add(0, -3, 0));
				check(Rooms.join(reader, room), "reader " + (i + 1) + " could not join");
				readers.add(reader);
			}
			room.markCubbiesForTests();
		});
		standAt(session, () -> Vec3.atBottomCenterOf(Rooms.of(session.player()).origin()).add(-2.5, 0, -0.5), 90, 12);
		shoot(session, "learning-room-cubbies");
		session.onServer(server -> {
			for (var reader : readers) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(reader.connection, server);
				StandIns.online(server, reader, false);
			}
		});
		session.waitTicks(5);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			for (int place = 0; place < 6; place++) player.level().setBlock(Rooms.of(player).cubby(place), Blocks.AIR.defaultBlockState(), 3);
		});
		session.command("/learn leave");
		Waits.home(session);
	}

	private static void standAt(Smoke.Session session, java.util.function.Supplier<Vec3> where, float yaw, float pitch) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = where.get();
			boolean up = at.y > Rooms.of(player).origin().getY() + 1;
			player.setGameMode(up ? net.minecraft.world.level.GameType.SPECTATOR : net.minecraft.world.level.GameType.SURVIVAL);
			player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, Set.of(), yaw, pitch, true);
		});
		session.waitTicks(5);
	}

	private static void shoot(Smoke.Session session, String name) {
		session.waitTicks(10);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
