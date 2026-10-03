package justfatlard.learning_blocks.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * TEN built when the room asked for HEN: the button throws snowballs at the child, real snowball
 * entities flying at them, ten of them, and not one snowball ends up in the pack or on the floor.
 * The child is not hurt. Full run: {@code phonics-wrong-ten.png}, mid-throw.
 */
public final class TenWrong implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.command("/learn");
			session.waitTicks(40);

			int[] sentBefore = new int[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("hen")).findFirst().orElseThrow()),
					"no round for hen");
				Room room = Rooms.of(player);
				check(room.puzzle().shelf().contains(Letter.T), "t is not on the floor in the -en round: " + room.puzzle().shelf());
				check(Hands.take(player, room.home(Letter.T)), "t would not break");
				Hands.placeInGap(player, room);
				check(room.inGap() == Letter.T, "t did not go into the gap");
				sentBefore[0] = room.dispensed().size();
				BlockPos button = room.button();
				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
			});

			// Watch the air: snowballs, heading for the child.
			Set<UUID> seen = new HashSet<>();
			boolean[] toward = new boolean[1];
			boolean[] shot = new boolean[1];
			for (int tick = 0; tick < 400 && seen.size() < 10; tick++) {
				session.onServer(server -> {
					ServerPlayer player = session.player();
					AABB box = new AABB(Rooms.of(player).origin()).inflate(12);
					for (Snowball ball : player.level().getEntitiesOfClass(Snowball.class, box, ball -> true)) {
						if (!seen.add(ball.getUUID())) continue;
						Vec3 to = player.getEyePosition().subtract(ball.position());
						if (ball.getDeltaMovement().dot(to) > 0) toward[0] = true;
					}
				});
				session.waitTicks(1);
				// Photographed a few throws in, with several in the air.
				if (session.full() && seen.size() >= 3 && !shot[0]) {
					shot[0] = true;
					session.context().takeScreenshot(TestScreenshotOptions.of("phonics-wrong-ten").disableCounterPrefix());
				}
			}
			check(seen.size() == 10, "expected ten snowballs thrown, saw " + seen.size());
			check(toward[0], "no snowball was flying at the child");

			session.waitTicks(40);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				long thrown = room.dispensed().stream().skip(Math.min(sentBefore[0], room.dispensed().size()))
					.filter(sent -> sent.what().equals("snowball")).count();
				check(thrown == 10 || sentBefore[0] + 10 > 32, "the button logged " + thrown + " snowballs, not ten");
				check(!player.getInventory().hasAnyMatching(stack -> stack.is(Items.SNOWBALL)), "a snowball ended up in the pack");
				check(!player.containerMenu.getCarried().is(Items.SNOWBALL), "a snowball ended up in hand");
				AABB box = new AABB(room.origin()).inflate(12);
				check(player.level().getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(Items.SNOWBALL)).isEmpty(),
					"a snowball item is lying in the room");
				check(player.level().getEntitiesOfClass(Snowball.class, box, ball -> true).isEmpty(), "a snowball is still in the air");
				check(player.getHealth() >= player.getMaxHealth(), "the snowballs hurt the child: " + player.getHealth());
			});
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
