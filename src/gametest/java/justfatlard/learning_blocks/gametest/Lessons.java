package justfatlard.learning_blocks.gametest;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A child sent in for lessons stays until they are done: the door is iron with the count over it,
 * the door and {@code /learn leave} do nothing, the count survives logging out, and after the
 * last solve "All done! The door is open!" is said and the door works. An ordinary visit's door is
 * open; an operator can release a child, and is never kept in themselves.
 */
public final class Lessons implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			ready(context, world);

			// An ordinary visit: the wooden door, open.
			world.getServer().runOnServer(server -> asPlayer(world, "phonics"));
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				Room room = Rooms.of(player);
				check(room.portalOpen(), "an ordinary visit's door is not the wooden one");
				check(room.doorSign() == null, "an ordinary visit has a lessons count over the door");
				check(!Travel.kept(player), "an ordinary visit keeps the child in");
			});
			world.getServer().runOnServer(server -> asPlayer(world, "learn leave"));
			context.waitTicks(80);

			// Sent for two.
			world.getServer().runOnServer(server -> console(server, "learn send " + name(world) + " 2"));
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				check(Places.isClassroom(player.level()), "send did not take the child in");
				locked(player, 2);
				asPlayer(world, "learn leave");
				click(player, Rooms.of(player).doorLower());
			});
			context.waitTicks(30);
			world.getServer().runOnServer(server -> check(Visit.of(world.getConnection().getServerPlayer()) != null,
				"the child got out with lessons left"));
		}
		context.waitTicks(10);

		// Logged out and back in: still two.
		try (TestSingleplayerContext world = save.open()) {
			ready(context, world);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				check(Places.isClassroom(player.level()), "back from logging out, but not in the classroom");
				locked(player, 2);
			});

			for (int lesson = 0; lesson < 2; lesson++) {
				Puzzle[] was = new Puzzle[1];
				world.getServer().runOnServer(server -> {
					ServerPlayer player = world.getConnection().getServerPlayer();
					Room room = Rooms.of(player);
					was[0] = room.puzzle();
					Hands.solve(player, room);
					click(player, room.button());
				});
				int round = lesson;
				for (int tick = 0; tick < 900; tick++) {
					boolean[] moved = new boolean[1];
					world.getServer().runOnServer(server -> {
						Room room = Rooms.of(world.getConnection().getServerPlayer());
						moved[0] = room.puzzle() != was[0] && !room.busy();
					});
					if (moved[0]) break;
					context.waitTicks(1);
				}
				if (round == 0) {
					world.getServer().runOnServer(server -> locked(world.getConnection().getServerPlayer(), 1));
				}
			}
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				check(Narrator.log(player).contains("clip:phrase/portal_open"), "\"All done! The portal is open!\" was not said: " + Narrator.log(player));
				check(Narrator.log(player).contains("clip:phrase/more_to_go_1"), "\"One more to go!\" was not said after the first lesson");
				Room room = Rooms.of(player);
				check(Travel.lessons(player) == 0, "lessons left after both were done: " + Travel.lessons(player));
				check(room.portalOpen(), "the door did not open after the lessons");
				check(room.doorSign() == null, "the count is still over the open door");
				click(player, room.doorLower());
			});
			for (int tick = 0; tick < 300; tick++) {
				boolean[] out = new boolean[1];
				world.getServer().runOnServer(server -> out[0] = Visit.of(world.getConnection().getServerPlayer()) == null);
				if (out[0]) break;
				context.waitTicks(1);
			}
			world.getServer().runOnServer(server -> check(Visit.of(world.getConnection().getServerPlayer()) == null,
				"the open door did not take the child home"));

			// Released by an operator.
			world.getServer().runOnServer(server -> console(server, "learn send " + name(world) + " 3"));
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				locked(player, 3);
				console(server, "learn release " + name(world));
				check(!Travel.kept(player) && Rooms.of(player).portalOpen(),
					"release did not open the door");

				// An operator is never kept in.
				console(server, "learn send " + name(world) + " 2");
				Ops.op(server, player);
				check(!Travel.kept(player), "an operator is kept in by lessons");
				asPlayer(world, "learn leave");
			});
			context.waitTicks(80);
			world.getServer().runOnServer(server -> check(Visit.of(world.getConnection().getServerPlayer()) == null,
				"an operator could not leave with lessons left"));
		}
		context.waitTicks(10);
	}

	private static void ready(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getConnection().waitForChunksRender();
		world.getServer().waitFor(server -> PandoricalApi.isAvailable(world.getConnection().getServerPlayer()));
		context.waitTicks(20);
	}

	/** Locked for this many: the iron door, the count over it, and kept in. */
	private static void locked(ServerPlayer player, int left) {
		Room room = Rooms.of(player);
		check(Travel.lessons(player) == left, "lessons left are " + Travel.lessons(player) + ", not " + left);
		check(Travel.kept(player), "the child is not kept in with lessons left");
		check(!room.portalOpen(), "the door is not locked with lessons left");
		check(room.doorSign() instanceof Display.TextDisplay sign && sign.getText().getString().equals(left + " left"),
			"the door does not say " + left + " left: " + room.doorSign());
	}

	private static String name(TestSingleplayerContext world) {
		return world.getConnection().getServerPlayer().getGameProfile().name();
	}

	private static void console(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	/** Run a command as the player; call on the server thread. */
	private static void asPlayer(TestSingleplayerContext world, String command) {
		ServerPlayer player = world.getConnection().getServerPlayer();
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
