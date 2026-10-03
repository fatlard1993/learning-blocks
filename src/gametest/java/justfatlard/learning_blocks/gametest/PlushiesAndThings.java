package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.PlushBlock;
import justfatlard.learning_blocks.plush.PlushItem;
import justfatlard.learning_blocks.plush.Plushies;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.review.Review;
import justfatlard.learning_blocks.reward.Prize;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.things.Things;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.ToyBlock;
import justfatlard.learning_blocks.toy.ToyItem;
import justfatlard.learning_blocks.toy.Toys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Rounds G and G2: every plushie, toy and prop is registered, with its art in the mod; a placed
 * plushie squeaks and a placed toy says its word when right-clicked, and both break back into
 * themselves; random_new plushie and toy prizes give one not yet owned, and a toybox prize a new
 * one of either; the Toybox tab counts both shelves; a plushie prize comes home; the new words'
 * pictures and outcomes (a toy pops out, the cap goes on); and a picture card on the board. Full
 * run: {@code phonics-plushies-tab.png}, {@code phonics-card-moon.png},
 * {@code phonics-outcome-owl-plush.png}, {@code phonics-outcome-jar.png},
 * {@code phonics-outcome-hug.png}, {@code phonics-outcome-cap.png}, {@code phonics-outcome-top.png}.
 */
public final class PlushiesAndThings implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("phonics-gametest").error("PlushiesAndThings failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		int[] heardBefore = new int[1];
		BlockPos[] drumAt = new BlockPos[1];
		// Registered, with art.
		session.onServer(server -> {
			for (Plush plush : Plush.values()) {
				Item item = Plushies.item(plush);
				check(BuiltInRegistries.ITEM.getKey(item).getPath().equals("plush_" + plush.word()), "plush_" + plush.word() + " is not registered");
				for (String path : List.of("models/block/plush_" + plush.word() + ".json", "textures/block/plush_" + plush.word() + ".png",
						"items/plush_" + plush.word() + ".json")) {
					check(asset(path), plush.word() + " has no " + path);
				}
			}
			for (String name : Things.all().keySet()) {
				check(asset("items/" + name + ".json"), name + " has no item definition");
			}
			check(asset("textures/card/moon.png"), "no moon card");
			check(Things.all().size() == 10, "props: " + Things.all().keySet());
			for (Toy toy : Toy.values()) {
				check(BuiltInRegistries.ITEM.getKey(Toys.item(toy)).getPath().equals("toy_" + toy.word()), "toy_" + toy.word() + " is not registered");
				for (String path : List.of("models/block/toy_" + toy.word() + ".json", "textures/block/toy_" + toy.word() + ".png",
						"items/toy_" + toy.word() + ".json")) {
					check(asset(path), toy.word() + " has no " + path);
				}
			}
		});

		// A plushie put down: right-click squeaks it; broken, it is itself again.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			player.setGameMode(GameType.SURVIVAL);
			BlockPos at = player.blockPosition().offset(0, 0, 2);
			level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(at, LearningBlocks.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, Plush.OWL).setValue(PlushBlock.FACING, Direction.NORTH), 3);
			PlushBlock.lastSqueezed = null;
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
			check(PlushBlock.lastSqueezed == Plush.OWL, "right-clicking the owl did not squeeze it");
			check(justfatlard.learning_blocks.sound.Sounds.isRecorded("sfx/plush_squish"), "the plushie squish is not found under sounds/sfx/");
			check(justfatlard.learning_blocks.sound.Sounds.recent().stream().anyMatch(played -> played.key().equals("sfx/plush_squish") && at.equals(played.at())),
				"squeezing the owl did not play its squish: " + justfatlard.learning_blocks.sound.Sounds.recent());
			// Broken mid-squish, it is the owl again, once, and nothing else.
			check(justfatlard.learning_blocks.play.Play.playing(level, at), "the owl is not squishing");
			Drops.expect(player, at, java.util.Map.of("plush_owl", 1), "the owl, broken mid-squish,");
			// Every plushie, at rest: itself, once, and nothing else.
			for (Plush kind : Plush.values()) {
				level.setBlock(at, LearningBlocks.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, kind).setValue(PlushBlock.FACING, Direction.NORTH), 3);
				Drops.expect(player, at, java.util.Map.of("plush_" + kind.name().toLowerCase(java.util.Locale.ROOT), 1), "the " + kind + " plushie");
			}
			// Every prop: itself, once.
			for (String prop : justfatlard.learning_blocks.things.Things.all().keySet()) {
				level.setBlock(at, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(LearningBlocks.id(prop)).defaultBlockState(), 3);
				Drops.expect(player, at, java.util.Map.of(prop, 1), "the " + prop);
			}

			// A toy too: squeezed, it says its word; broken, it is itself again.
			level.setBlock(at, Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, Toy.DRUM).setValue(ToyBlock.FACING, Direction.NORTH), 3);
			ToyBlock.lastSqueezed = null;
			int heard = Narrator.log(player).size();
			player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
			check(ToyBlock.lastSqueezed == Toy.DRUM, "right-clicking the drum did not squeeze it");
			heardBefore[0] = heard;
			drumAt[0] = at;
		});
		session.waitTicks(30);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos at = drumAt[0];
			List<String> said = Narrator.log(player);
			check(said.subList(Math.min(heardBefore[0], said.size()), said.size()).contains("clip:word/drum"), "the drum did not say its word: " + said);
			Drops.expect(player, at, java.util.Map.of("toy_drum", 1), "the drum");
		});

		// Plushie prizes.
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Set<Plush> got = EnumSet.noneOf(Plush.class);
			List<ItemStack> items = new ArrayList<>();
			Prize random = new Prize.Plushie(Prize.Plushie.RANDOM_NEW);
			for (int i = 0; i < 7; i++) {
				List<ItemStack> one = new ArrayList<>();
				random.resolve(player, one, new int[1]);
				check(one.size() == 1, "random_new gave " + one.size());
				Plush plush = PlushItem.kindOf(one.getFirst());
				check(got.add(plush), "random_new gave " + plush + " twice while others were missing");
				items.addAll(one);
			}
			new Prize.Plushie("owl").resolve(player, items, new int[1]);
			check(Progress.of(player).plushies().get(Plush.OWL) >= 1, "a named plushie was not owned");
			// Toy prizes: random_new gives one not yet owned; a named one is that one.
			Set<Toy> toys = EnumSet.noneOf(Toy.class);
			for (int i = 0; i < 3; i++) {
				List<ItemStack> one = new ArrayList<>();
				new Prize.Toy(Prize.Plushie.RANDOM_NEW).resolve(player, one, new int[1]);
				check(one.size() == 1, "toy random_new gave " + one.size());
				Toy toy = ToyItem.kindOf(one.getFirst());
				check(toys.add(toy), "toy random_new gave " + toy + " twice while others were missing");
			}
			new Prize.Toy("drum").resolve(player, new ArrayList<>(), new int[1]);
			check(Progress.of(player).toys().getOrDefault(Toy.DRUM, 0) >= 1, "a named toy was not owned");
			// The toybox: a plushie or a toy, always a new one while any are missing.
			int before = Progress.of(player).plushies().size() + Progress.of(player).toys().size();
			for (int i = 0; i < 4; i++) {
				List<ItemStack> one = new ArrayList<>();
				new Prize.Toybox().resolve(player, one, new int[1]);
				check(one.size() == 1 && (ToyItem.kindOf(one.getFirst()) != null || PlushItem.kindOf(one.getFirst()) != null), "toybox gave " + one);
			}
			check(Progress.of(player).plushies().size() + Progress.of(player).toys().size() == before + 4, "a toybox prize was not new");
			int owned = Progress.of(player).plushies().size();
			int toysOwned = Progress.of(player).toys().size();
			Review.open(player, Review.Tab.TOYBOX, null, false);
			String count = "Plushies " + owned + " of " + Plush.values().length + " · Toys " + toysOwned + " of " + Toy.values().length;
			check(Review.toyboxCount(player).equals(count), "the tab counts " + Review.toyboxCount(player) + ", not " + count);
			check(Review.toyboxShown(player).size() == owned + toysOwned, "the tab shows " + Review.toyboxShown(player));
			// Into the visit, as a payout is: they come home.
			Visit.set(player, Visit.of(player).withPrizes(List.of(items.getFirst().copy()), 0));
		});
		session.waitTicks(20);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("phonics-plushies-tab").disableCounterPrefix());
		session.onServer(server -> Review.press(session.player(), "close"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			// Every plushie, then one more: a duplicate.
			for (int i = 0; i < Plush.values().length; i++) new Prize.Plushie(Prize.Plushie.RANDOM_NEW).resolve(player, new ArrayList<>(), new int[1]);
			check(Progress.of(player).plushies().size() == Plush.values().length, "random_new never filled the set: " + Progress.of(player).plushies());
		});

		// A picture card on the board.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Word moon = word("moon");
			check(Rooms.startRound(player, moon), "no round for moon");
			check("moon".equals(Rooms.of(player).pictureCard()), "the moon card is not on the board: " + Rooms.of(player).pictureCard());
		});
		session.waitTicks(30);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("phonics-card-moon").disableCounterPrefix());

		// New outcomes.
		for (String text : List.of("owl", "jar", "hug", "cap", "top")) {
			session.onServer(server -> Rooms.runOutcome(session.player(), word(text)));
			session.waitTicks(40);
			if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("phonics-outcome-" + text + (text.equals("owl") ? "-plush" : ""))
				.disableCounterPrefix());
		}
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(room.placed().stream().anyMatch(pos -> player.level().getBlockState(pos).is(LearningBlocks.PLUSH)), "the owl plushie did not pop out");
			check(room.placed().stream().anyMatch(pos -> player.level().getBlockState(pos).is(Toys.TOY)
				&& player.level().getBlockState(pos).getValue(ToyBlock.KIND) == Toy.TOP), "the top did not pop out");
			check(ToyItem.kindOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) == Toy.CAP, "cap did not put the toy cap on");
		});
		session.command("/learn leave");
		// Going home waits for the prize line, as long as the voice takes to say it.
		Waits.until(session, 300, "still in the classroom", () -> !Places.isClassroom(session.player().level()));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			boolean home = false;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) home |= PlushItem.kindOf(player.getInventory().getItem(i)) != null;
			check(home, "the plushie prize did not come home");
		});
	}

	private static boolean asset(String path) {
		return PlushiesAndThings.class.getResource("/assets/" + LearningBlocks.MOD_ID + "/" + path) != null;
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
