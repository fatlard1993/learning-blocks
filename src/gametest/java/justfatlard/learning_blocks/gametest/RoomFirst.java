package justfatlard.learning_blocks.gametest;

import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Plots;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.puzzle.RoomBuilds;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * A room goes up before its child goes in: on a plot nobody has used, the walls, floor and board
 * are all there the tick they arrive, put up a slice a tick rather than all at once. A way in
 * dropped while the room goes up (the child logged out) leaves them where they were, holding
 * everything, and the next way in still works.
 */
public final class RoomFirst implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(server -> {
				ServerPlayer player = session.player();
				player.setGameMode(GameType.SURVIVAL);
				RoomBuilds.knockDown(server.getLevel(Places.CLASSROOM), Plots.origin(player));
				RoomBuilds.mostSetInATick = 0;
			});
			arrive(session, true);
			session.onServer(server -> check(RoomBuilds.mostSetInATick == RoomBuilds.SLICE,
				"the room did not go up a slice at a time: at most " + RoomBuilds.mostSetInATick + " blocks in a tick"));

			session.command("/learn leave");
			Waits.until(session, 300, "never went home", () -> !Places.isClassroom(session.player().level()));
			session.waitTicks(5);

			// Dropped part way: nobody moved, nothing taken, nothing left waiting.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				ServerLevel classroom = server.getLevel(Places.CLASSROOM);
				RoomBuilds.knockDown(classroom, Plots.origin(player));
				player.getInventory().clearContent();
				player.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
				check(Travel.enter(player) == null, "the way in was refused");
				check(RoomBuilds.waiting(player), "a plot nobody has used was ready on the spot");
				check(Travel.enter(player) == null, "asked again while the room goes up, the way in was refused");
				check(!Places.isClassroom(player.level()) && Visit.of(player) == null, "let in before the room was up");
				check(player.getInventory().countItem(Items.APPLE) == 3, "things were put aside before the room was up");
				RoomBuilds.drop(player.getUUID());
				check(!RoomBuilds.waiting(player), "the dropped room is still waited on");
			});
			session.waitTicks(20);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(!Places.isClassroom(player.level()) && Visit.of(player) == null && Rooms.of(player) == null,
					"a dropped way in still took them in");
				check(player.getInventory().countItem(Items.APPLE) == 3, "a dropped way in lost their things");
				// A wait on the room left by something other than a way in (a split from a shared room
				// they then walked out of): a way in asked for alongside it still takes them in.
				RoomBuilds.knockDown(server.getLevel(Places.CLASSROOM), Plots.origin(player));
				RoomBuilds.request(player, server.getLevel(Places.CLASSROOM), Plots.origin(player), p -> {});
				check(RoomBuilds.waiting(player), "the leftover wait was ready on the spot");
				check(Travel.enter(player) == null, "the way in beside a leftover wait was refused");
			});

			arrive(session, false);
			session.onServer(server -> check(session.player().getInventory().isEmpty(), "carried things into the classroom"));
			session.command("/learn leave");
			Waits.until(session, 300, "never went home the second time", () -> !Places.isClassroom(session.player().level()));
			session.waitTicks(5);
			session.onServer(server -> check(session.player().getInventory().countItem(Items.APPLE) == 3, "the apples did not come home"));
		});
	}

	/** /learn (or a way in already asked for), then watched tick by tick: the walls are up the first tick they stand inside. */
	private static void arrive(Smoke.Session session, boolean learn) {
		if (learn) session.command("/learn");
		boolean[] in = new boolean[1];
		String[] gap = new String[1];
		for (int tick = 0; tick <= 200 && !in[0]; tick++) {
			session.onServer(server -> {
				ServerPlayer player = session.player();
				in[0] = Places.isClassroom(player.level());
				if (in[0]) gap[0] = RoomBuilds.shellGap((ServerLevel) player.level(), Plots.origin(player));
			});
			if (!in[0]) session.waitTicks(1);
		}
		check(in[0], "never got into the classroom");
		check(gap[0] == null, "arrived before the room was up: " + gap[0]);
		session.onServer(server -> check(Rooms.of(session.player()) != null, "no room on arrival"));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
