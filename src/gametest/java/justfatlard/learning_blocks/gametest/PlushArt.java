package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.PlushBlock;
import justfatlard.learning_blocks.plush.Plushies;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.LetterBlock;
import justfatlard.learning_blocks.props.PropBlock;
import justfatlard.learning_blocks.props.Props;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.ToyBlock;
import justfatlard.learning_blocks.toy.Toys;
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
 * wig worn. Full run: {@code learning-plush-placed.png}, {@code learning-plush-side.png},
 * {@code learning-plush-icons.png}, {@code learning-toys-placed.png}, {@code learning-props-placed.png}, {@code learning-toys-icons.png},
 * {@code learning-toys-worn.png}, {@code learning-toys-wig.png}; then the letter tiles,
 * {@code learning-tiles-placed.png} and {@code learning-tiles-side.png}.
 */
public final class PlushArt implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", PlushArt::run);
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
				level.setBlock(at, LearningBlocks.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, all[i]).setValue(PlushBlock.FACING, Direction.NORTH), 3);
			}
			player.getInventory().clearContent();
			for (int i = 0; i < all.length; i++) player.getInventory().setItem(9 + i, Plushies.stack(all[i]));
			look(player, base.offset(0, 0, -1), 0.0F, 32.0F);
		});
		session.waitTicks(60);
		shoot(session, "learning-plush-placed");
		session.onServer(server -> look(session.player(), origin[0].offset(5, 0, 1), 40.0F, 30.0F));
		session.waitTicks(30);
		shoot(session, "learning-plush-side");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "learning-plush-icons");
		session.context().setScreen(() -> null);

		// The big-word plushies, in a row of their own near the camera.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = origin[0];
			List<Plush> big = List.of(Plush.BOBCAT, Plush.CATFISH, Plush.STARFISH, Plush.CHIPMUNK, Plush.PUFFIN, Plush.ROBIN, Plush.LOBSTER, Plush.HEDGEHOG);
			for (int i = 0; i < big.size(); i++) {
				level.setBlock(base.offset(i - 4, 0, 1), LearningBlocks.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, big.get(i)).setValue(PlushBlock.FACING, Direction.NORTH), 3);
			}
			player.getInventory().clearContent();
			for (int i = 0; i < big.size(); i++) player.getInventory().setItem(9 + i, Plushies.stack(big.get(i)));
			look(player, base.offset(0, 0, -2), 0.0F, 38.0F);
		});
		session.waitTicks(30);
		shoot(session, "learning-plush-big");
		session.onServer(server -> look(session.player(), origin[0].offset(-3, 0, -1), -35.0F, 35.0F));
		session.waitTicks(20);
		shoot(session, "learning-plush-big-side");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "learning-plush-big-icons");
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
			for (Item item : Props.all().values()) {
				level.setBlock(base.offset(x, 0, 8), ((BlockItem) item).getBlock().defaultBlockState().setValue(PropBlock.FACING, Direction.NORTH), 3);
				x++;
			}
			player.getInventory().clearContent();
			for (int i = 0; i < toys.length; i++) player.getInventory().setItem(9 + i, Toys.stack(toys[i]));
			look(player, base.offset(0, 0, -1), 0.0F, 30.0F);
		});
		session.waitTicks(40);
		shoot(session, "learning-toys-placed");
		session.onServer(server -> look(session.player(), origin[0].offset(0, 1, 13), 0.0F, 25.0F));
		session.waitTicks(20);
		shoot(session, "learning-props-placed");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "learning-toys-icons");
		session.context().setScreen(() -> null);
		// Worn: the cap, seen from the front.
		session.onServer(server -> session.player().setItemSlot(EquipmentSlot.HEAD, Toys.stack(Toy.CAP)));
		session.context().runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		session.waitTicks(20);
		shoot(session, "learning-toys-worn");
		session.onServer(server -> session.player().setItemSlot(EquipmentSlot.HEAD, Toys.stack(Toy.WIG)));
		session.waitTicks(10);
		shoot(session, "learning-toys-wig");
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
				level.setBlock(base.offset(3 - i, 0, 2), LearningBlocks.LETTER.defaultBlockState().setValue(LetterBlock.LETTER, front[i])
					.setValue(LetterBlock.LIT, front[i] == Letter.A), 3);
			}
			Letter[] board = {Letter.D, Letter.O, Letter.G, Letter.EE, Letter.CAP_A};
			for (int i = 0; i < board.length; i++) {
				level.setBlock(base.offset(2 - i, 1, 4), LearningBlocks.BOARD_LETTER.defaultBlockState().setValue(LetterBlock.LETTER, board[i]), 3);
			}
			level.setBlock(base.offset(-5, 0, 2), Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, Toy.BLOCK).setValue(ToyBlock.FACING, Direction.NORTH), 3);
			look(player, base.offset(0, 0, -2), 0.0F, 22.0F);
		});
		session.waitTicks(30);
		shoot(session, "learning-tiles-placed");
		session.onServer(server -> look(session.player(), origin[0].offset(-4, 0, 20), -35.0F, 22.0F));
		session.waitTicks(20);
		shoot(session, "learning-tiles-side");

		// Close: the ant (two button eyes), the puffin (its tall flat beak), the yak and the hedgehog,
		// front and side, and as items.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = origin[0].offset(0, 0, 17);
			for (int x = -6; x <= 6; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 2; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
			Plush[] close = {Plush.ANT, Plush.PUFFIN, Plush.YAK, Plush.HEDGEHOG};
			for (int i = 0; i < close.length; i++) {
				level.setBlock(base.offset(i * 3 - 4, 0, 0), LearningBlocks.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, close[i]).setValue(PlushBlock.FACING, Direction.NORTH), 3);
			}
			player.getInventory().clearContent();
			for (int i = 0; i < close.length; i++) player.getInventory().setItem(9 + i, Plushies.stack(close[i]));
			look(player, base.offset(0, 0, -2), 0.0F, 32.0F);
		});
		session.waitTicks(30);
		shoot(session, "learning-plush-close-front");
		// Side on: the puffin from its left, the ant from its right.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = Vec3.atBottomCenterOf(origin[0].offset(-1, 0, 17)).add(-1.5, 0, 0);
			player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, java.util.Set.of(), -90.0F, 42.0F, true);
		});
		session.waitTicks(20);
		shoot(session, "learning-plush-close-side");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = Vec3.atBottomCenterOf(origin[0].offset(-4, 0, 17)).add(1.5, 0, 0);
			player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, java.util.Set.of(), 90.0F, 42.0F, true);
		});
		session.waitTicks(20);
		shoot(session, "learning-plush-close-side-ant");
		session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		session.waitTicks(20);
		shoot(session, "learning-plush-close-icons");
		session.context().setScreen(() -> null);

		// The counting toys (Numbers) and the thinking toys (Logic): each close, three-quarter on;
		// then each set as items.
		Toy[][] sets = {{Toy.ABACUS, Toy.DICE, Toy.DOMINOES, Toy.SORTER, Toy.SCALE}, {Toy.PUZZLE, Toy.CUPS, Toy.MAZE, Toy.CUBE, Toy.ROBOT}};
		String[] setNames = {"counting", "thinking"};
		for (int set = 0; set < sets.length; set++) {
			Toy[] shown = sets[set];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				ServerLevel level = (ServerLevel) player.level();
				BlockPos base = origin[0].offset(0, 0, 17);
				for (int x = -6; x <= 6; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 2; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				for (int i = 0; i < shown.length; i++) {
					level.setBlock(base.offset(6 - i * 2, 0, 0), Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, shown[i]).setValue(ToyBlock.FACING, Direction.NORTH), 3);
				}
				player.getInventory().clearContent();
				for (int i = 0; i < shown.length; i++) player.getInventory().setItem(9 + i, Toys.stack(shown[i]));
			});
			for (int i = 0; i < shown.length; i++) {
				int at = i;
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Vec3 toy = Vec3.atBottomCenterOf(origin[0].offset(6 - at * 2, 0, 17));
					// In front and a little to the toy's right, looking down at it: a spectator, so the eye can be below head height.
					player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
					Vec3 eye = toy.add(-0.55, 0.62, -0.95);
					player.teleportTo((ServerLevel) player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, java.util.Set.of(), -31.0F, 28.0F, true);
				});
				session.waitTicks(20);
				shoot(session, "learning-toy-" + shown[i].getSerializedName());
			}
			session.onServer(server -> session.player().setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
			session.waitTicks(5);
			session.context().setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			session.waitTicks(20);
			shoot(session, "learning-toys-" + setNames[set] + "-icons");
			session.context().setScreen(() -> null);
		}
	}

	private static void look(ServerPlayer player, BlockPos at, float yaw, float pitch) {
		Vec3 eye = Vec3.atBottomCenterOf(at);
		player.teleportTo((ServerLevel) player.level(), eye.x, eye.y, eye.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}
}
