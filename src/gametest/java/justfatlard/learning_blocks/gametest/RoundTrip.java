package justfatlard.learning_blocks.gametest;

import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Tiles;
import justfatlard.learning_blocks.sound.Sounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A trip to the classroom and back hands everything back: every item in the slot it was in,
 * experience to the point, the game mode, and every effect still running.
 *
 * <p>Effects are the one PvP Dimensions loses, so they are the one most worth watching. The pack is
 * deliberately awkward - armour worn, something in the off hand, a stack partway down the pack, a
 * fraction of a level - because a restore that only puts things back "somewhere" passes a test
 * that only checks the hotbar.
 *
 * <p>In between, the target word is actually made, through the same clicks a child would make, so
 * whatever the word hands out is on the player when they leave: a hat on the head that must not
 * come home over the helmet that was there before.
 */
public final class RoundTrip implements FabricClientGameTest {

	private static final int LEVELS = 7;
	private static final int POINTS = 5;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			ItemStack[] before = new ItemStack[1];
			int[] total = new int[1];
			float[] progress = new float[1];

			session.onServer(server -> {
				TestPrizes.use(server);
				ServerPlayer player = session.player();
				player.setGameMode(GameType.SURVIVAL);
				Inventory inventory = player.getInventory();
				inventory.clearContent();
				inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
				inventory.setItem(17, new ItemStack(Items.BREAD, 7));
				inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				player.setExperienceLevels(LEVELS);
				player.giveExperiencePoints(POINTS);
				total[0] = player.totalExperience;
				progress[0] = player.experienceProgress;
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 1));
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 600, 0));
				before[0] = inventory.getItem(17).copy();
			});

			session.command("/learn");
			session.waitTicks(40);

			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Places.isClassroom(player.level()), "not taken to the classroom, but to " + player.level().dimension());
				check(Visit.of(player) != null, "no visit written");
				check(player.gameMode() == GameType.SURVIVAL, "game mode inside is " + player.gameMode());
				check(player.getInventory().isEmpty(), "carried things into the classroom");
				check(player.getActiveEffects().isEmpty(), "carried effects into the classroom");
				check(player.experienceLevel == 0 && player.totalExperience == 0, "carried experience into the classroom");
				Room room = Rooms.of(player);
				check(room != null && room.puzzle() != null, "no puzzle in the room");
			});
			shoot(session, "classroom");

			// Make the word the room asks for, the way a child would: knock the tile loose and pick
			// it up, place it in the gap, press the button.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Letter onset = room.puzzle().answer();
				check(Hands.take(player, room.home(onset)), "the loose tile would not break");
				check(Tiles.carried(player) >= 0, "the broken tile was not picked up");
			});
			shoot(session, "tile-in-hand");
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Room room = Rooms.of(player);
				Letter onset = room.puzzle().answer();
				Hands.placeInGap(player, room);
				check(player.level().getBlockState(room.gap()).getValue(justfatlard.learning_blocks.puzzle.LetterBlock.LETTER) == onset,
					"the tile did not go into the gap");
				click(player, room.button());
			});
			Waits.until(session, 600, "reading the target never counted as solved", () -> {
				Room room = Rooms.of(session.player());
				return room != null && room.solved() == 1;
			});
			shoot(session, "solved");

			// A first read of a word is a new word: a diamond, waiting in the visit and nowhere else,
			// with the chest by the door to say so, which pops up as the prize flies in.
			Waits.until(session, 300, "no prize chest by the door", () -> {
				Room room = Rooms.of(session.player());
				return room != null && session.player().level().getBlockState(room.prizeMarker()).is(net.minecraft.world.level.block.Blocks.CHEST);
			});
			session.onServer(server -> {
				ServerPlayer player = session.player();
				Visit visit = Visit.of(player);
				check(visit != null && visit.prizes().size() == 1 && visit.prizes().getFirst().is(Items.DIAMOND),
					"the prize is not waiting in the visit: " + (visit == null ? "no visit" : visit.prizes()));
				check(!player.getInventory().hasAnyMatching(stack -> stack.is(Items.DIAMOND)), "the prize went into the classroom inventory");
				Room room = Rooms.of(player);
				check(player.level().getBlockState(room.prizeMarker()).is(net.minecraft.world.level.block.Blocks.CHEST),
					"no prize chest by the door");
			});

			session.command("/learn leave");
			Waits.until(session, 200, "never went home", () -> session.player().level().dimension() == Level.OVERWORLD);
			session.waitTicks(5);

			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(player.level().dimension() == Level.OVERWORLD, "not taken home, but to " + player.level().dimension());
				check(Visit.of(player) == null, "the visit was never ended");
				check(Rooms.of(player) == null, "the room was never closed");
				check(player.gameMode() == GameType.SURVIVAL, "game mode came back as " + player.gameMode());

				Inventory inventory = player.getInventory();
				check(inventory.getItem(0).is(Items.DIAMOND_SWORD), "the sword is not in the first slot");
				check(ItemStack.matches(inventory.getItem(17), before[0]), "the bread came back as " + inventory.getItem(17));
				check(inventory.getItem(Inventory.SLOT_OFFHAND).is(Items.SHIELD), "the shield is not in the off hand");
				check(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
					"the head holds " + player.getItemBySlot(EquipmentSlot.HEAD) + ", not the iron helmet");
				int kept = 0;
				int diamonds = 0;
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if (stack.isEmpty()) continue;
					kept++;
					if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
				}
				check(diamonds == 1, diamonds + " diamonds came home, not the one prize");
				check(kept == 5, kept + " stacks came home, not the 4 taken in and the 1 earned: something from the room came out with them");
				check(Sounds.recent().stream().anyMatch(p -> Sounds.variantsOf("phrase/you_earned_a_prize").contains(p.key())),
					"nobody said \"You earned a prize!\" on the way out");

				check(player.experienceLevel == LEVELS, "experience level came back as " + player.experienceLevel);
				check(player.totalExperience == total[0], "total experience came back as " + player.totalExperience);
				check(player.experienceProgress == progress[0], "experience progress came back as " + player.experienceProgress);

				MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
				check(speed != null && speed.getAmplifier() == 1, "speed came back as " + speed);
				check(player.hasEffect(MobEffects.NIGHT_VISION), "night vision was lost");
				check(player.getActiveEffects().size() == 2, "effects came back as " + List.copyOf(player.getActiveEffects()));
			});
		});
	}

	/** A picture, on the thorough run only, for looking at the room rather than asserting on it. */
	private static void shoot(Smoke.Session session, String name) {
		if (!session.full()) return;
		session.waitTicks(20);
		session.context().takeScreenshot(TestScreenshotOptions.of("learning-" + name).disableCounterPrefix());
	}

	/** A right-click on a block, through the same event a real one goes through. */
	private static void click(ServerPlayer player, BlockPos pos) {
		check(pos != null, "nothing to click");
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
