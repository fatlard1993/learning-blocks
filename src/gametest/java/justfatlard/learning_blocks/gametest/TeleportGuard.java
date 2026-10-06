package justfatlard.learning_blocks.gametest;

import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

/**
 * Another mod's teleport (made the way a teleport menu makes it) does not take a child out of the
 * classroom or bring anyone in; the portal and /learn still do.
 */
public final class TeleportGuard implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", TeleportGuard::run);
	}

	private static void run(Smoke.Session session) {
		String[] name = new String[1];
		session.onServer(server -> name[0] = session.player().getGameProfile().name());
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn send " + name[0] + " 3");
			check(Travel.kept(player), "sent for three, the child is not kept");
			ServerLevel overworld = server.overworld();
			boolean moved = player.teleportTo(overworld, 0.5, 100, 0.5, Set.<Relative>of(), 0, 0, true);
			check(!moved, "another mod's teleport out reported success");
			check(Places.isClassroom(player.level()), "another mod's teleport took the child out of the classroom");
			check(Visit.of(player) != null && Travel.lessons(player) == 3, "the visit or its lessons changed");
		});
		session.waitTicks(10);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Places.isClassroom(player.level()) && Visit.of(player) != null, "a tick later, the child is out after all");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn home " + name[0]);
			check(!Places.isClassroom(player.level()) && Visit.of(player) == null, "/learn home did not send them home");
		});
		session.waitTicks(20);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel classroom = server.getLevel(Places.CLASSROOM);
			check(classroom != null, "no classroom level");
			player.teleportTo(classroom, 0.5, 70, 0.5, Set.<Relative>of(), 0, 0, true);
			check(!Places.isClassroom(player.level()), "another mod's teleport carried someone into the classroom");
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
