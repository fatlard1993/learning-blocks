package justfatlard.phonics.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.plush.Plush;
import justfatlard.phonics.plush.PlushBlock;
import justfatlard.phonics.plush.Plushies;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.LetterBlock;
import justfatlard.phonics.things.ThingBlock;
import justfatlard.phonics.things.Things;
import justfatlard.phonics.toy.Toy;
import justfatlard.phonics.toy.ToyBlock;
import justfatlard.phonics.toy.Toys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The plushies and the toys, for looking at: every plushie placed in rows on a plain
 * floor, from the front and the side, and as items; every toy placed, with the props, and every toy as an item; the cap and the
 * wig worn. Full run: {@code phonics-plush-placed.png}, {@code phonics-plush-side.png},
 * {@code phonics-plush-icons.png}, {@code phonics-toys-placed.png}, {@code phonics-props-placed.png}, {@code phonics-toys-icons.png},
 * {@code phonics-toys-worn.png}, {@code phonics-toys-wig.png}; then the letter tiles,
 * {@code phonics-tiles-placed.png} and {@code phonics-tiles-side.png}.
 */
public final class PlushArt implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", PlushArt::run);
	}

	private static void run(Smoke.Session session) {
		BlockPos[] origin = new BlockPos[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = player.blockPosition();
			origin[0] = base;
			for (int x = -7; x <= 7; x++) {
				for (int z = -3; z <= 26; z++) {
					level.setBlock(base.offset(x, -1, z), Blocks.CONCRETE.white().defaultBlockState(), 3);
					for (int y = 0; y <= 4; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			Plush[] all = Plush.values();
			for (int i = 0; i < all.length; i++) {
				BlockPos at = base.offset((i % 9) - 4, 0, 4 + (i / 9) * 2);
				level.setBlock(at, Phonics.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, all[i]).setValue(PlushBlock.FACING, Direction.NORTH), 3);
			}
			player.getInventory().clearContent();
			for (int i = 0; i < all.length; i++) player.getInventory().setItem(9 + i, Plushies.stack(all[i]));
			look(player, base.offset(0, 0, -1), 0.0F, 32.0F);
		});
		session.waitTicks(60);
		shoot(session, "phonics-plush-placed");
		session.onServer(server -> look(session.player(), origin[0].offset(5, 0, 1), 40.0F, 30.0F));
		session.waitTicks(30);
		shoot(session, "phonics-plush-side");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "phonics-plush-icons");
		session.context().setScreen(() -> null);

		// The big-word plushies, round H's, in a row of their own near the camera.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = origin[0];
			List<Plush> big = List.of(Plush.BOBCAT, Plush.CATFISH, Plush.STARFISH, Plush.CHIPMUNK, Plush.PUFFIN, Plush.ROBIN, Plush.LOBSTER, Plush.HEDGEHOG);
			for (int i = 0; i < big.size(); i++) {
				level.setBlock(base.offset(i - 4, 0, 1), Phonics.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, big.get(i)).setValue(PlushBlock.FACING, Direction.NORTH), 3);
			}
			player.getInventory().clearContent();
			for (int i = 0; i < big.size(); i++) player.getInventory().setItem(9 + i, Plushies.stack(big.get(i)));
			look(player, base.offset(0, 0, -2), 0.0F, 38.0F);
		});
		session.waitTicks(30);
		shoot(session, "phonics-plush-big");
		session.onServer(server -> look(session.player(), origin[0].offset(-3, 0, -1), -35.0F, 35.0F));
		session.waitTicks(20);
		shoot(session, "phonics-plush-big-side");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "phonics-plush-big-icons");
		session.context().setScreen(() -> null);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			for (int i = 0; i < 8; i++) level.setBlock(origin[0].offset(i - 4, 0, 1), Blocks.AIR.defaultBlockState(), 3);
		});

		// The toys: placed in rows further on, the props beside them, and every toy in the inventory.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = origin[0].offset(0, 0, 10);
			Toy[] toys = Toy.values();
			for (int i = 0; i < toys.length; i++) {
				BlockPos at = base.offset((i % 9) - 4, 0, 2 + (i / 9) * 2);
				level.setBlock(at, Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, toys[i]).setValue(ToyBlock.FACING, Direction.NORTH), 3);
			}
			int x = -5;
			for (Item item : Things.all().values()) {
				level.setBlock(base.offset(x, 0, 8), ((BlockItem) item).getBlock().defaultBlockState().setValue(ThingBlock.FACING, Direction.NORTH), 3);
				x++;
			}
			player.getInventory().clearContent();
			for (int i = 0; i < toys.length; i++) player.getInventory().setItem(9 + i, Toys.stack(toys[i]));
			look(player, base.offset(0, 0, -1), 0.0F, 30.0F);
		});
		session.waitTicks(40);
		shoot(session, "phonics-toys-placed");
		session.onServer(server -> look(session.player(), origin[0].offset(0, 1, 13), 0.0F, 25.0F));
		session.waitTicks(20);
		shoot(session, "phonics-props-placed");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "phonics-toys-icons");
		session.context().setScreen(() -> null);
		// Worn: the cap, seen from the front.
		session.onServer(server -> session.player().setItemSlot(EquipmentSlot.HEAD, Toys.stack(Toy.CAP)));
		session.context().runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		session.waitTicks(20);
		shoot(session, "phonics-toys-worn");
		session.onServer(server -> session.player().setItemSlot(EquipmentSlot.HEAD, Toys.stack(Toy.WIG)));
		session.waitTicks(10);
		shoot(session, "phonics-toys-wig");
		session.context().runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		session.onServer(server -> session.player().setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));

		// The letter tiles, as wooden blocks: singles, a lit one, a digraph, igh, a capital, the
		// gaps; the board's own letters behind; the ABC block beside them.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = origin[0].offset(0, 0, 21);
			Letter[] front = {Letter.C, Letter.A, Letter.T, Letter.SH, Letter.IGH, Letter.CAP_B, Letter.BLANK_BLUE, Letter.BLANK};
			for (int i = 0; i < front.length; i++) {
				level.setBlock(base.offset(3 - i, 0, 2), Phonics.LETTER.defaultBlockState().setValue(LetterBlock.LETTER, front[i])
					.setValue(LetterBlock.LIT, front[i] == Letter.A), 3);
			}
			Letter[] board = {Letter.D, Letter.O, Letter.G, Letter.EE, Letter.CAP_A};
			for (int i = 0; i < board.length; i++) {
				level.setBlock(base.offset(2 - i, 1, 4), Phonics.BOARD_LETTER.defaultBlockState().setValue(LetterBlock.LETTER, board[i]), 3);
			}
			level.setBlock(base.offset(-5, 0, 2), Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, Toy.BLOCK).setValue(ToyBlock.FACING, Direction.NORTH), 3);
			look(player, base.offset(0, 0, -2), 0.0F, 22.0F);
		});
		session.waitTicks(30);
		shoot(session, "phonics-tiles-placed");
		session.onServer(server -> look(session.player(), origin[0].offset(-4, 0, 20), -35.0F, 22.0F));
		session.waitTicks(20);
		shoot(session, "phonics-tiles-side");
	}

	private static void look(ServerPlayer player, BlockPos at, float yaw, float pitch) {
		Vec3 eye = Vec3.atBottomCenterOf(at);
		player.teleportTo((ServerLevel) player.level(), eye.x, eye.y, eye.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}
}
