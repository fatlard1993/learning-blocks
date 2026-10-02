package justfatlard.phonics.gametest;

import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.LetterBlock;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Tiles;
import justfatlard.phonics.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every tile has a place on the floor and goes back to it: after a wrong word, when another tile is
 * placed into the filled gap (from the tile itself or the board beside the underscore), and when it
 * is set down anywhere else. Each time it lands as a block at its own spot, the gap is left as it
 * should be, and no copy of it is left in hand or on the ground.
 */
public final class TileHome implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			session.command("/phonics");
			session.waitTicks(40);

			// A wrong word: b in the gap makes bat, not the cat the room asks for.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				// The _at family has more words than the floor has room for: a round with b, h and m in it.
				for (int tries = 0; tries < 40; tries++) {
					check(Rooms.startRound(player, Words.all().stream().filter(w -> w.text().equals("cat")).findFirst().orElseThrow()),
						"no round for cat");
					if (Rooms.of(player).puzzle().shelf().containsAll(java.util.List.of(Letter.B, Letter.H, Letter.M))) break;
				}
				Room room = Rooms.of(player);
				check(room.puzzle().shelf().containsAll(java.util.List.of(Letter.B, Letter.H, Letter.M)), "no cat round with b, h and m");
				check(Hands.take(player, room.home(Letter.B)), "b would not break");
				Hands.placeInGap(player, room);
				check(room.inGap() == Letter.B, "b did not go into the gap");
				BlockPos button = room.button();
				Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
			});
			Waits.until(session, 600, "the wrong tile was never sent home",
				() -> Narrator.log(session.player()).contains("tile_home:b"));
			session.waitTicks(20);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(room.inGap() == null && player.level().getBlockState(room.gap()).isAir(), "the gap is not empty after a wrong word");
				// Sounded out as the word built, not the one asked for: bat's own a.
				check(Narrator.log(player).contains("clip:blend/bat/1"), "the wrong word bat was not blended with bat's a: " + Narrator.log(player));
				home(player, room, Letter.B);
			});
			Waits.until(session, 400, "the room never finished with the wrong word", () -> !Rooms.of(session.player()).busy());

			// Swapping: h into the filled gap, by using it on the tile there.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Hands.take(player, room.home(Letter.B));
				Hands.placeInGap(player, room);
				Hands.take(player, room.home(Letter.H));
				Hands.placeAgainst(player, room.gap(), Direction.SOUTH);
				check(room.inGap() == Letter.H, "using h on the tile in the gap did not swap it in: " + room.inGap());
				check(!player.level().getBlockState(room.gap().south()).is(Phonics.LETTER), "h went in front of the gap as well");
			});
			session.waitTicks(22);
			session.onServer(server -> home(session.player(), Rooms.of(session.player()), Letter.B));

			// And m, by using it on the board beside the underscore.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Hands.take(player, room.home(Letter.M));
				Hands.placeAgainst(player, room.gap().north().west(), Direction.SOUTH);
				check(room.inGap() == Letter.M, "using m on the board beside the underscore did not swap it in: " + room.inGap());
			});
			session.waitTicks(22);
			session.onServer(server -> home(session.player(), Rooms.of(session.player()), Letter.H));

			// Set down on the floor somewhere else: it goes home, and the gap is untouched.
			BlockPos[] away = new BlockPos[1];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Hands.take(player, room.home(Letter.C));
				away[0] = room.origin().offset(-2, 0, 1);
				Hands.placeAgainst(player, away[0].below(), Direction.UP);
			});
			session.waitTicks(22);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(!player.level().getBlockState(away[0]).is(Phonics.LETTER), "c stayed where it was set down");
				check(room.inGap() == Letter.M, "setting c down elsewhere changed the gap");
				home(player, room, Letter.C);
			});
		});
	}

	/** This tile is at its own place on the floor as a block, and nowhere else. */
	private static void home(ServerPlayer player, Room room, Letter letter) {
		BlockPos home = room.home(letter);
		var state = player.level().getBlockState(home);
		check(state.is(Phonics.LETTER) && state.getValue(LetterBlock.LETTER) == letter,
			letter.grapheme() + " is not back at its place: " + state);
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			check(Tiles.letterOf(player.getInventory().getItem(slot)) != letter, "a copy of " + letter.grapheme() + " is still in hand");
		}
		AABB box = new AABB(room.origin()).inflate(8);
		check(player.level().getEntitiesOfClass(ItemEntity.class, box, item -> Tiles.letterOf(item.getItem()) == letter).isEmpty(),
			"a copy of " + letter.grapheme() + " is still on the ground");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
