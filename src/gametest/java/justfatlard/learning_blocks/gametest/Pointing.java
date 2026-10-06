package justfatlard.learning_blocks.gametest;

import java.util.Set;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reading together, the reader whose turn it isn't points: a click on a tile, or on one of a
 * Numbers round's choices, makes a puff where they clicked and changes nothing in the round; a
 * mash of clicks makes only a few.
 */
public final class Pointing implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", Pointing::run);
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		FakePlayer[] other = new FakePlayer[1];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			other[0] = StandIns.inClassroom((ServerLevel) host.level(), "Pointer", Rooms.of(host).spawn().add(-1.5, 0, 0));
			check(Rooms.join(other[0], Rooms.of(host)) && Rooms.of(host).shared(), "the second reader did not join");
			Rooms.nextRound(host);
			check(Rooms.of(host).turn() != null, "a shared room has no turn");
		});
		session.waitTicks(10);

		// A tile on the floor, clicked out of turn: a puff where it was clicked, and the round as it was.
		session.onServer(server -> {
			ServerPlayer pointer = offTurn(session.player(), other[0]);
			Room room = Rooms.of(pointer);
			Puzzle before = room.puzzle();
			Letter tile = before.shelf().getFirst();
			BlockPos at = room.home(tile);
			Vec3 spot = Vec3.atCenterOf(at).add(0, 0.5, 0);
			int pointed = room.pointed().size();
			Rooms.useBlock(pointer, pointer.level(), InteractionHand.MAIN_HAND, new BlockHitResult(spot, Direction.UP, at, false));
			check(room.pointed().size() == pointed + 1 && room.pointed().getLast().distanceTo(spot) < 0.01,
				"no puff where the tile was clicked: " + room.pointed());
			// Mashed: still the one puff.
			for (int i = 0; i < 5; i++) Rooms.useBlock(pointer, pointer.level(), InteractionHand.MAIN_HAND, new BlockHitResult(spot, Direction.UP, at, false));
			check(room.pointed().size() == pointed + 1, "mashing made a flood of puffs: " + room.pointed().size());
			check(room.puzzle() == before && room.inGap() == null, "pointing changed the round");
		});
		session.waitTicks(10);

		// A Numbers choice clicked out of turn: a puff on it, and nothing answered.
		SubjectRound[] round = new SubjectRound[1];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Subjects.onlyForTests(Set.of("reading", "numbers"));
			Rooms.of(host).newSubjectRound(host, Numbers.INSTANCE, "find", "3");
			round[0] = Rooms.of(host).subjectRound();
			check(round[0] != null, "no numbers round");
		});
		Waits.until(session, 600, "the round never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer host = session.player();
			ServerPlayer pointer = offTurn(host, other[0]);
			Room room = Rooms.of(host);
			BlockPos tile = BlockPos.containing(round[0].hitSpotOf(round[0].rightForTests()));
			Vec3 spot = Vec3.atCenterOf(tile).add(0, 0.5, 0);
			int pointed = room.pointed().size();
			int clean = Progress.of(pointer).itemClean(round[0].item()) + Progress.of(host).itemClean(round[0].item());
			Rooms.useBlock(pointer, pointer.level(), InteractionHand.MAIN_HAND, new BlockHitResult(spot, Direction.UP, tile, false));
			check(room.pointed().size() == pointed + 1 && room.pointed().getLast().distanceTo(spot) < 0.01, "no puff on the choice clicked out of turn");
			check(room.subjectRound() == round[0] && !room.busy(), "pointing answered the round");
			check(Progress.of(pointer).itemClean(round[0].item()) + Progress.of(host).itemClean(round[0].item()) == clean, "pointing counted");
		});

		session.onServer(server -> {
			StandIns.online(server, other[0], false);
			Rooms.forget(other[0]);
			Subjects.onlyForTests(Set.of("reading"));
		});
		session.command("/learn leave");
		Waits.home(session);
	}

	/** Whichever of the two it isn't the turn of. */
	private static ServerPlayer offTurn(ServerPlayer host, ServerPlayer other) {
		return host.getUUID().equals(Rooms.of(host).turn()) ? other : host;
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
