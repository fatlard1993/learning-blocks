package justfatlard.learning_blocks.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.learning_blocks.puzzle.PensRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SortRound;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The things a child clicks in Logic and Numbers rounds, clicked as a child clicks them: standing
 * where the room sets them down (two steps nearer, for tiles on the floor and Bob's pens by the
 * board), looking at the thing, and pressing use on their own game, so the reach and the size of
 * every box are what a child gets. An animal is picked by clicking the animal,
 * a second animal switches the pick, and a pen takes it from a click near its rug's corner.
 */
public final class ClickReach implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", ClickReach::run);
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);

		// Sorting: two animals clicked in turn, the second the pick, then a click near its pen's corner.
		SortRound sort = (SortRound) start(session, Logic.INSTANCE, "sort", "kind");
		session.waitTicks(40);
		SubjectRound.Choice[] picks = new SubjectRound.Choice[2];
		session.onServer(server -> {
			List<SubjectRound.Choice> left = sort.unsorted();
			picks[0] = left.get(0);
			picks[1] = left.get(1);
		});
		clickAt(session, () -> middleOf(sort.thingFor(picks[0])));
		session.onServer(server -> check(sort.selected() == picks[0] && sort.thingFor(picks[0]).hasGlowingTag(),
			"clicking the first animal did not pick it; the crosshair was on " + aimed));
		settle(session);
		clickAt(session, () -> middleOf(sort.thingFor(picks[1])));
		session.onServer(server -> {
			check(sort.selected() == picks[1], "clicking a second animal did not switch the pick; the crosshair was on " + aimed);
			check(sort.thingFor(picks[1]).hasGlowingTag() && !sort.thingFor(picks[0]).hasGlowingTag(), "the glow did not move to the new pick");
		});
		settle(session);
		clickAt(session, () -> farCorner(session, sort.hitSpotOf(sort.penFor(picks[1]))));
		session.onServer(server -> check(sort.sorted() == 1, "a click near the pen's corner did not put the animal in; the crosshair was on " + aimed));
		settle(session);

		// Odd one out: the odd one clicked from where the child stands.
		answer(session, start(session, Logic.INSTANCE, "odd", "kind"), "the odd one out");
		// Bob's misplaced animal, in its pen.
		// Bob's pens stand back by the board: a child steps up to them, as to the tiles.
		forward = 2;
		answer(session, start(session, Logic.INSTANCE, "bobfix", "pens"), "the animal in the wrong pen");
		forward = 0;
		// Numbers: a number tile, clicked as the block it is; a pen, by its rug; and a picture.
		// Tiles stand on the floor before the board, as letter tiles do: a child steps up to them.
		forward = 2;
		answer(session, start(session, Numbers.INSTANCE, "find", "3"), "the tile three");
		forward = 0;
		// Numbers: a pen, clicked by its rug, and a picture.
		answer(session, start(session, Numbers.INSTANCE, "pens", "more"), "the pen with more");
		answer(session, start(session, Numbers.INSTANCE, "picture", "3"), "the picture with three");

		session.command("/learn leave");
		Waits.home(session);
	}

	private static SubjectRound start(Smoke.Session session, justfatlard.learning_blocks.subject.Subject subject, String kind, String target) {
		SubjectRound[] round = new SubjectRound[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			room.newSubjectRound(player, subject, kind, target);
			round[0] = room.subjectRound();
			check(round[0] != null, "no " + kind + " round");
		});
		settle(session);
		session.waitTicks(20);
		return round[0];
	}

	/** Its right answer clicked through its box: it counts. */
	private static void answer(Smoke.Session session, SubjectRound round, String what) {
		int[] before = new int[1];
		session.onServer(server -> before[0] = Progress.of(session.player()).itemClean(round.item()));
		clickAt(session, () -> {
			SubjectRound.Choice right = round.rightForTests();
			Vec3 at = round.hitSpotOf(right);
			// Near the box's edge for a pen, its middle for anything else.
			if (round instanceof PensRound) return farCorner(session, at);
			// A tile is a block: its top. Anything else, the middle of its box.
			return right.block() != null ? Vec3.atCenterOf(BlockPos.containing(at)).add(0, 0.45, 0) : at.add(0, 0.5, 0);
		});
		Waits.until(session, 900, what + " never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> check(Progress.of(session.player()).itemClean(round.item()) > before[0]
			|| Rooms.of(session.player()).subjectRound() != round, "clicking " + what + " from where the child stands did not answer it; the crosshair was on " + aimed));
		Waits.until(session, 1200, what + "'s round never ended", () -> Rooms.of(session.player()).subjectRound() != round
			&& !Rooms.of(session.player()).busy());
	}

	/** The pen's front rug: within a child's reach from where they stand. */
	private static Vec3 farCorner(Smoke.Session session, Vec3 pen) {
		double middle = Rooms.of(session.player()).origin().getX() + 0.5;
		return pen.add(pen.x < middle ? 1.0 : -1.0, 0.05, 1.0);
	}

	private static Vec3 middleOf(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() / 2, 0);
	}

	/** Back on the child's spot, looking at this, and use pressed on the client. */
	private static void clickAt(Smoke.Session session, java.util.function.Supplier<Vec3> target) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			Vec3 spawn = room.spawnFor(player.getUUID()).add(0, 0, -forward);
			Vec3 eye = spawn.add(0, player.getEyeHeight(), 0);
			Vec3 aim = target.get().subtract(eye);
			float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
			player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.of(), yaw, pitch, true);
		});
		session.waitTicks(3);
		session.context().runOnClient(client -> {
			var hit = client.hitResult;
			aimed = hit == null ? "nothing" : hit instanceof net.minecraft.world.phys.EntityHitResult entity
				? "entity " + entity.getEntity().getType() + " at " + entity.getEntity().position()
				: hit.getType() + " at " + hit.getLocation();
			aimed += ", looking " + client.player.getYRot() + "/" + client.player.getXRot() + " from " + client.player.position()
				+ ", reach " + client.player.blockInteractionRange() + "/" + client.player.entityInteractionRange() + ", " + client.gameMode.getPlayerMode();
		});
		session.context().getInput().pressKey(options -> options.keyUse);
		session.waitTicks(4);
	}

	/** How far in front of where the room sets the child down they stand to click. */
	private static int forward;

	/** What the child's game had under the crosshair at the last click, for a failure to say. */
	private static volatile String aimed = "";

	private static void settle(Smoke.Session session) {
		Waits.until(session, 900, "the room never settled", () -> !Rooms.of(session.player()).busy());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
