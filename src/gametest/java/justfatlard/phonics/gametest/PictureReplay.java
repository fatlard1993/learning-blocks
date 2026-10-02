package justfatlard.phonics.gametest;

import java.util.Collections;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;

/**
 * Clicking the picture beside the word says the word again, "Can you make... cat?"; clicking it
 * again straight away says nothing more. Its click box goes with the picture at the next round.
 */
public final class PictureReplay implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			session.command("/phonics");
			Waits.until(session, 300, "the first prompt never came", () -> !Rooms.of(session.player()).promptHeld()
				&& Narrator.log(session.player()).contains("clip:phrase/make_the_word"));
			session.waitTicks(40);
			int[] before = new int[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Entity hit = player.level().getEntity(room.pictureHit());
				check(hit instanceof Interaction, "the picture has no click box: " + hit);
				check(hit.isInvulnerable() || !(hit instanceof net.minecraft.world.entity.LivingEntity), "the click box can be hurt");
				before[0] = Collections.frequency(Narrator.log(player), "clip:phrase/make_the_word");
				Rooms.clickEntity(player, player.level(), hit);
				Rooms.clickEntity(player, player.level(), hit);
			});
			session.waitTicks(10);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				int after = Collections.frequency(Narrator.log(player), "clip:phrase/make_the_word");
				check(after == before[0] + 1, "two quick clicks said the prompt " + (after - before[0]) + " times, not once");
				check(Collections.frequency(Narrator.log(player), "replay") == 1, "the replay was not counted once");
			});
			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				var old = room.pictureHit();
				Rooms.startRound(player, justfatlard.phonics.puzzle.Words.all().stream().filter(w -> w.text().equals("pig")).findFirst().orElseThrow());
				check(player.level().getEntity(old) == null, "the old click box was left behind");
				check(player.level().getEntitiesOfClass(Interaction.class, new AABB(room.origin()).inflate(8)).size() == 1,
					"not exactly one click box in the room");
			});
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
