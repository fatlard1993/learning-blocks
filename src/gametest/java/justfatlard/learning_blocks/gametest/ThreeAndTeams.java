package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import justfatlard.learning_blocks.puzzle.CountRound;
import justfatlard.learning_blocks.puzzle.PatternRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Three reading together: rounds go round all three in turn, each standing in their own place with
 * their own chest. Then two, and the team-ups of the other subjects: a Numbers count either may
 * answer, and a Logic copy built half each; right, each counts for both. The third goes through
 * the server's own disconnect handler.
 */
public final class ThreeAndTeams implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", ThreeAndTeams::run);
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		FakePlayer[] others = new FakePlayer[2];
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			for (int i = 0; i < 2; i++) {
				others[i] = StandIns.inClassroom((ServerLevel) host.level(), "Reader" + (i + 2), room.spawn().add(i == 0 ? -1.5 : 1.5, 0, 0));
				check(Rooms.join(others[i], room), "reader " + (i + 2) + " could not join");
			}
			check(room.members().size() == 3, "three readers are not all in the room: " + room.members().size());
			List<UUID> turns = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				Rooms.nextRound(host);
				turns.add(room.turn());
			}
			check(new HashSet<>(turns).size() == 3, "the turns did not go round all three: " + turns);
			check(turns.get(0).equals(turns.get(3)) && turns.get(1).equals(turns.get(4)) && turns.get(2).equals(turns.get(5)),
				"the turns did not go round in order: " + turns);
			Set<Vec3> spots = new HashSet<>();
			Set<BlockPos> chests = new HashSet<>();
			for (UUID member : room.members()) {
				spots.add(room.spawnFor(member));
				chests.add(room.prizeMarker(member));
			}
			check(spots.size() == 3 && chests.size() == 3, "three readers do not each have a place and a chest: " + spots + " " + chests);

			// The third logs out, through the server's own handler.
			ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(others[1].connection, server);
			StandIns.online(server, others[1], false);
		});
		session.waitTicks(5);
		session.onServer(server -> check(Rooms.of(session.player()).members().size() == 2, "the third reader's logout left them in the room"));

		// A Numbers team-up: either may answer, and it counts for both.
		session.onServer(server -> {
			Subjects.onlyForTests(Set.of("reading", "numbers", "logic"));
			ServerPlayer host = session.player();
			Rooms.of(host).newSubjectRound(host, Numbers.INSTANCE, "team", "6");
		});
		Waits.until(session, 900, "the count never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Room room = Rooms.of(host);
			CountRound count = (CountRound) room.subjectRound();
			ServerPlayer answerer = host.getUUID().equals(room.turn()) ? others[0] : host;
			int[] before = cleans(count.item(), host, others[0]);
			clickTile(answerer, count, count.rightForTests());
			int[] after = cleans(count.item(), host, others[0]);
			check(after[0] == before[0] + 1 && after[1] == before[1] + 1, "a team count did not count for both: " + before[0] + "," + before[1] + " to " + after[0] + "," + after[1]);
		});
		Waits.until(session, 1200, "the count never ended", () -> !(Rooms.of(session.player()).subjectRound() instanceof CountRound) && !Rooms.of(session.player()).busy());

		// A Logic copy: the turn reader builds the first half, the other the rest; it counts for both.
		session.onServer(server -> {
			ServerPlayer host = session.player();
			Rooms.of(host).newSubjectRound(host, Logic.INSTANCE, "copy", "ab");
		});
		Waits.until(session, 900, "the copy never settled", () -> !Rooms.of(session.player()).busy());
		PatternRound[] copy = new PatternRound[1];
		int[][] before = new int[1][];
		session.onServer(server -> {
			copy[0] = (PatternRound) Rooms.of(session.player()).subjectRound();
			before[0] = cleans(copy[0].item(), session.player(), others[0]);
		});
		for (int i = 0; i < 4; i++) {
			int at = i;
			session.onServer(server -> {
				ServerPlayer host = session.player();
				Room room = Rooms.of(host);
				ServerPlayer turnReader = host.getUUID().equals(room.turn()) ? host : others[0];
				ServerPlayer other = turnReader == host ? others[0] : host;
				ServerPlayer builder = at < 2 ? turnReader : other;
				ServerPlayer wrong = builder == host ? others[0] : host;
				int copied = copy[0].copied();
				clickTile(wrong, copy[0], copy[0].rightForTests());
				check(copy[0].copied() == copied, "the reader whose half it isn't put a block in");
				clickTile(builder, copy[0], copy[0].rightForTests());
			});
			Waits.until(session, 600, "copy " + at + " never went down", () -> copy[0].copied() == at + 1 && !Rooms.of(session.player()).busy()
				|| Rooms.of(session.player()).subjectRound() != copy[0]);
		}
		session.onServer(server -> {
			int[] after = cleans(copy[0].item(), session.player(), others[0]);
			check(after[0] == before[0][0] + 1 && after[1] == before[0][1] + 1, "a team copy did not count for both");
		});

		session.onServer(server -> {
			ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(others[0].connection, server);
			StandIns.online(server, others[0], false);
			Subjects.onlyForTests(Set.of("reading"));
		});
		session.waitTicks(5);
		session.command("/learn leave");
		Waits.home(session);
	}

	private static int[] cleans(String item, ServerPlayer a, ServerPlayer b) {
		return new int[] {Progress.of(a).itemClean(item), Progress.of(b).itemClean(item)};
	}

	/** A right-click on a choice, as a child's: on the block it is, or on its box. */
	private static void clickTile(ServerPlayer player, SubjectRound round, SubjectRound.Choice choice) {
		Vec3 spot = round.hitSpotOf(choice);
		if (choice.block() != null) {
			BlockPos tile = BlockPos.containing(spot);
			Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(tile).add(0, 0.5, 0), Direction.UP, tile, false));
			return;
		}
		var boxes = player.level().getEntitiesOfClass(net.minecraft.world.entity.Interaction.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(0.2));
		check(!boxes.isEmpty(), "no box for " + choice.id());
		Rooms.clickEntity(player, player.level(), boxes.getFirst());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
