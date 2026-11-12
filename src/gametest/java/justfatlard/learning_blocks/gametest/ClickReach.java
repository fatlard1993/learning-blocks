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
			// Two the child can see, neither standing in front of the other, and the second not standing
			// before a pen: with one picked, a click on an animal a pen lies behind is a click on that pen,
			// as it is meant to be. Which animals come first, and where they have hopped, varies by run.
			List<SubjectRound.Choice> left = sort.unsorted();
			ServerPlayer player = session.player();
			Vec3 eye = Rooms.of(player).spawnFor(player.getUUID()).add(0, player.getEyeHeight(), 0);
			picks[0] = left.get(0);
			picks[1] = left.get(1);
			search:
			for (SubjectRound.Choice a : left) {
				for (SubjectRound.Choice b : left) {
					if (a == b || hides(sort.thingFor(a), eye, middleOf(sort.thingFor(b))) || hides(sort.thingFor(b), eye, middleOf(sort.thingFor(a)))
						|| penBehind(player, sort, eye, middleOf(sort.thingFor(b)))) continue;
					picks[0] = a;
					picks[1] = b;
					break search;
				}
			}
		});
		clickAt(session, () -> middleOf(sort.thingFor(picks[0])));
		session.onServer(server -> check(sort.selected() == picks[0] && sort.thingFor(picks[0]).hasGlowingTag(),
			"clicking the first animal did not pick it; the crosshair was on " + aimed));
		settle(session);
		clickAt(session, () -> middleOf(sort.thingFor(picks[1])));
		session.onServer(server -> {
			check(sort.selected() == picks[1], "clicking a second animal did not switch the pick; the crosshair was on " + aimed
				+ "; first " + picks[0].id() + " at " + sort.thingFor(picks[0]).position() + ", second " + picks[1].id() + " at " + sort.thingFor(picks[1]).position()
				+ ", selected " + (sort.selected() == null ? "none" : sort.selected().id()) + ", busy " + Rooms.of(session.player()).busy());
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
		carry(session, start(session, Numbers.INSTANCE, "find", "3"), "the tile three");
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

	/**
	 * A carried tile, from where the child stands: struck loose with the attack key, then placed in the
	 * gap on the board with use. Both within reach, or a child standing there could not answer it.
	 */
	private static void carry(Smoke.Session session, SubjectRound round, String what) {
		int[] before = new int[1];
		SubjectRound.Choice[] right = new SubjectRound.Choice[1];
		session.onServer(server -> {
			before[0] = Progress.of(session.player()).itemClean(round.item());
			right[0] = round.rightForTests();
		});
		aimAt(session, () -> Vec3.atCenterOf(BlockPos.containing(round.hitSpotOf(right[0]))).add(0, 0.45, 0));
		session.context().getInput().holdKeyFor(options -> options.keyAttack, 15);
		session.waitTicks(2);
		String[] screen = new String[1];
		session.context().runOnClient(client -> screen[0] = client.gui.screen() == null ? null : client.gui.screen().getClass().getName());
		check(screen[0] == null, "striking " + what + " opened a screen: " + screen[0]);
		session.onServer(server -> check(justfatlard.learning_blocks.puzzle.Tiles.numberOf(session.player().getMainHandItem()) == Integer.parseInt(right[0].id()),
			"striking " + what + " from where the child stands did not take it into the hand; the crosshair was on " + aimed));
		// Tile in hand, up to the tile row, where the board stands just behind: the gap is out of reach
		// from where the tiles are struck, so this is where a child carrying one walks to.
		int struck = forward;
		forward = struck + 2;
		aimAt(session, () -> Vec3.atCenterOf(round.gapForTests()).add(0, 0, 0.5));
		forward = struck;
		session.context().getInput().pressKey(options -> options.keyUse);
		session.waitTicks(4);
		Waits.until(session, 900, what + " never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> check(Progress.of(session.player()).itemClean(round.item()) > before[0]
			|| Rooms.of(session.player()).subjectRound() != round, "placing " + what + " in the gap from the tile row did not answer it; the crosshair was on " + aimed));
		Waits.until(session, 1200, what + "'s round never ended", () -> Rooms.of(session.player()).subjectRound() != round
			&& !Rooms.of(session.player()).busy());
	}

	/** The pen's front rug: within a child's reach from where they stand. */
	private static Vec3 farCorner(Smoke.Session session, Vec3 pen) {
		double middle = Rooms.of(session.player()).origin().getX() + 0.5;
		return pen.add(pen.x < middle ? 1.0 : -1.0, 0.05, 1.0);
	}

	/** Whether the line from the eye through that spot runs on, within reach, onto a pen. */
	private static boolean penBehind(ServerPlayer player, SortRound sort, Vec3 eye, Vec3 spot) {
		Vec3 end = eye.add(spot.subtract(eye).normalize().scale(player.blockInteractionRange()));
		var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
			net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && sort.penAtForTests(hit.getBlockPos());
	}

	/** Whether this entity's box, with a little room, lies on the line from the eye to that spot. */
	private static boolean hides(Entity entity, Vec3 eye, Vec3 spot) {
		return entity.getBoundingBox().inflate(0.3).clip(eye, spot).isPresent();
	}

	private static Vec3 middleOf(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() / 2, 0);
	}

	/** Back on the child's spot, looking at this, and use pressed on the client. */
	private static void clickAt(Smoke.Session session, java.util.function.Supplier<Vec3> target) {
		aimAt(session, target);
		session.context().getInput().pressKey(options -> options.keyUse);
		session.waitTicks(4);
	}

	/** Back on the child's spot, looking at this, with what the client's crosshair is on noted for a failure to say. */
	private static void aimAt(Smoke.Session session, java.util.function.Supplier<Vec3> target) {
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
				+ ", reach " + client.player.blockInteractionRange() + "/" + client.player.entityInteractionRange() + ", " + client.gameMode.getPlayerMode()
				+ ", screen " + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName());
		});
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
