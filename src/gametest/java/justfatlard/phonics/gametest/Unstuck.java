package justfatlard.phonics.gametest;

import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.classroom.Places;
import justfatlard.phonics.classroom.Schedules;
import justfatlard.phonics.classroom.Travel;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Never stuck. {@code /phonics home} sends a kept child home with their things and their waiting
 * prize, clears the visit's lessons and leaves their daily schedule as it was. The stuck detector
 * sends home a child free to go who tried to leave three times and is still in (every ordinary way
 * out wedged for the test), and logs what the room was doing; it leaves alone a child kept for
 * lessons, and one not trying to leave. The portal takes a child who walked in backwards and
 * stood wholly in it for a second, and not one pushed into it.
 */
public final class Unstuck implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			try {
				run(session);
			} finally {
				Travel.wedgedForTests = false;
			}
		});
	}

	private static void run(Smoke.Session session) {
		String[] name = new String[1];
		// /phonics home: kept for three, a diamond in the stash, a prize waiting, a daily schedule.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			name[0] = player.getGameProfile().name();
			player.getInventory().clearContent();
			player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
			Schedules.set(player.getUUID(), name[0], 5);
		});
		session.command("/phonics");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			console(server, "phonics send " + name[0] + " 3");
			Visit.set(player, Visit.of(player).withPrizes(List.of(new ItemStack(Items.EMERALD, 1)), 0));
			check(Travel.kept(player), "sent for three, the child is not kept");
		});
		session.waitTicks(10);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			StringBuilder reply = new StringBuilder();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(new Listener(reply)), "phonics home " + name[0]);
			check(!Places.isClassroom(player.level()) && Visit.of(player) == null, "/phonics home did not send them home");
			check(player.getInventory().countItem(Items.DIAMOND) == 2, "their diamonds did not come home");
			check(player.getInventory().countItem(Items.EMERALD) == 1, "their prize did not come home");
			check(Schedules.of(player.getUUID()) != null && Schedules.of(player.getUUID()).lessons() == 5, "the daily schedule was changed");
			check(reply.toString().contains("Sent " + name[0] + " home.") && reply.toString().contains("daily"), "the reply was: " + reply);
			Schedules.set(player.getUUID(), name[0], 0);
		});
		session.waitTicks(20);

		// Stuck: free to go, every ordinary way out wedged, three tries.
		session.command("/phonics");
		session.waitTicks(40);
		session.onServer(server -> {
			Travel.wedgedForTests = true;
			Travel.lastStuck = null;
		});
		for (int i = 0; i < 3; i++) {
			session.command("/phonics leave");
			session.waitTicks(15);
		}
		session.onServer(server -> check(Places.isClassroom(session.player().level()), "the wedge did not hold"));
		Waits.until(session, 400, "the stuck child was never sent home", () -> !Places.isClassroom(session.player().level()));
		session.onServer(server -> {
			String line = Travel.lastStuck;
			check(line != null && line.contains("tried to leave 3 times") && line.contains("leaving=") && line.contains("narration=")
				&& line.contains("round=") && line.contains("portalOpen="), "the stuck line has no room state: " + line);
			check(Visit.of(session.player()) == null, "stuck and sent home, but the visit is still open");
			LoggerFactory.getLogger("phonics-gametest").info("stuck line: {}", line);
		});

		// Kept for lessons: tries to leave are refused, and nobody is forced out.
		session.command("/phonics");
		session.waitTicks(40);
		session.onServer(server -> {
			Travel.lastStuck = null;
			console(server, "phonics send " + name[0] + " 2");
		});
		session.waitTicks(10);
		for (int i = 0; i < 3; i++) {
			session.command("/phonics leave");
			session.waitTicks(15);
		}
		session.waitTicks(260);
		session.onServer(server -> check(Places.isClassroom(session.player().level()) && Travel.lastStuck == null, "a child kept for lessons was sent out"));
		// Not trying to leave: nothing, however long.
		session.onServer(server -> console(server, "phonics release " + name[0]));
		session.waitTicks(260);
		session.onServer(server -> {
			check(Places.isClassroom(session.player().level()) && Travel.lastStuck == null, "a child not trying to leave was sent out");
			Travel.wedgedForTests = false;
		});

		// The portal: pushed in, standing still, not leaving; walked in backwards and stood a second, home.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = Rooms.of(player).portalOpening().getCenter();
			player.setLastClientInput(Input.EMPTY);
			player.teleportTo(player.level(), at.x, at.y - 1.4, at.z - 1.2, Set.of(), 180.0F, 0.0F, true);
			player.setDeltaMovement(0, 0.1, 0.9);
			player.needsSync = true;
		});
		session.waitTicks(40);
		session.onServer(server -> check(Places.isClassroom(session.player().level()), "pushed into the portal, they were sent home"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = Rooms.of(player).portalOpening().getCenter();
			player.setLastClientInput(new Input(false, true, false, false, false, false, false));
			player.teleportTo(player.level(), at.x, at.y - 1.4, at.z, Set.of(), 180.0F, 0.0F, true);
		});
		session.waitTicks(10);
		session.onServer(server -> check(Places.isClassroom(session.player().level()), "half a second backed into the portal sent them home"));
		Waits.until(session, 100, "walking in backwards and standing a second did not go home", () -> !Places.isClassroom(session.player().level()));
	}

	private static void console(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	/** A command source that keeps what it is told. */
	private record Listener(StringBuilder heard) implements net.minecraft.commands.CommandSource {
		@Override
		public void sendSystemMessage(Component message) {
			heard.append(message.getString());
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
