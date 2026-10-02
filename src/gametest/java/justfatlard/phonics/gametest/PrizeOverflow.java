package justfatlard.phonics.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A child who comes in with every slot full still gets every prize: the pack comes back exactly
 * as it was, and what will not fit is on the ground at their feet at home - once, not twice.
 */
public final class PrizeOverflow implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			List<ItemStack> packed = new ArrayList<>();

			session.onServer(server -> {
				TestPrizes.use(server);
				ServerPlayer player = session.player();
				player.setGameMode(GameType.SURVIVAL);
				Inventory inventory = player.getInventory();
				inventory.clearContent();
				for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
					inventory.setItem(slot, new ItemStack(slot % 2 == 0 ? Items.COBBLESTONE : Items.OAK_LOG, 64));
				}
				inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.TORCH, 64));
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) packed.add(inventory.getItem(slot).copy());
			});

			session.command("/phonics");
			session.waitTicks(40);

			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				check(room != null && room.puzzle() != null, "no puzzle in the room");
				Hands.solve(player, room);
				click(player, room.button());
			});
			Waits.until(session, 600, "the target was never read", () -> {
				Visit visit = Visit.of(session.player());
				return visit != null && visit.hasPrizes();
			});

			session.command("/phonics leave");
			Waits.until(session, 200, "never went home", () -> session.player().level().dimension() == Level.OVERWORLD);
			session.waitTicks(5);

			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Visit.of(player) == null, "the visit was never ended");
				Inventory inventory = player.getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack expected = slot < packed.size() ? packed.get(slot) : ItemStack.EMPTY;
					check(ItemStack.matches(inventory.getItem(slot), expected),
						"slot " + slot + " came back as " + inventory.getItem(slot) + ", not " + expected);
				}

				List<ItemEntity> dropped = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
				long diamonds = dropped.stream().filter(item -> item.getItem().is(Items.DIAMOND)).mapToInt(item -> item.getItem().getCount()).sum();
				check(diamonds == 1, diamonds + " diamonds at their feet, not the one prize: " + dropped);
			});
		});
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		check(pos != null, "nothing to click");
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
