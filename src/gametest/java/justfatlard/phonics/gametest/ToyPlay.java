package justfatlard.phonics.gametest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.bob.Goal;
import justfatlard.phonics.bob.Goals;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.play.Anims;
import justfatlard.phonics.play.Play;
import justfatlard.phonics.plush.Plush;
import justfatlard.phonics.plush.PlushBlock;
import justfatlard.phonics.plush.Plushies;
import justfatlard.phonics.puzzle.BobRound;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Stages;
import justfatlard.phonics.puzzle.Word;
import justfatlard.phonics.puzzle.Words;
import justfatlard.phonics.relate.Relations;
import justfatlard.phonics.review.Review;
import justfatlard.phonics.reward.Earned;
import justfatlard.phonics.reward.PrizeFile;
import justfatlard.phonics.reward.Prizes;
import justfatlard.phonics.reward.Progress;
import justfatlard.phonics.toy.Toy;
import justfatlard.phonics.toy.ToyBlock;
import justfatlard.phonics.toy.ToyItem;
import justfatlard.phonics.toy.Toys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Round G3: toys earned by mastering their words; toys and plushies played with in the world; the
 * relations engine behind toy combos and Bob.
 *
 * <p>The links: teddy by hug, the ladybug by bug, owl by owl, the car by car, and every toy and
 * plushie earnable. Mastering owl earns the owl once, and only once; a reader who mastered duck
 * before earns it on the way in. New prize files earn toys by mastery and give no random toybox
 * prize. The Toybox tab hints at the word for one not had. Every toy has a play that hides its
 * block, runs, and shows it again exactly; the plushie squish too; one whose chunk unloads mid-play
 * is put right by its scheduled tick. Toys, plushies and props break at a punch. The doll goes in
 * the crib and back out; the owl rides the car through its loop; the cap goes on the owl; breaking
 * a loaded toy drops both; contents are saved as an entity and load back the same. Bob's goals are
 * made from the things; a preposition round, owl in box or on it, is read and picked. Full run:
 * {@code phonics-play-car-*.png} (a contact sheet's frames), {@code phonics-play-squish-*.png},
 * {@code phonics-play-doll-crib.png}, {@code phonics-play-cap-owl.png}, {@code phonics-prep-round.png},
 * {@code phonics-toybox-hints.png}.
 */
public final class ToyPlay implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("phonics-gametest").error("ToyPlay failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		// The links, from the words.
		session.onServer(server -> {
			check(texts(Earned.wordsFor("plush:teddy")).equals(List.of("hug")), "teddy is not earned by hug: " + Earned.wordsFor("plush:teddy"));
			check(texts(Earned.wordsFor("plush:ladybug")).equals(List.of("bug")), "the ladybug is not earned by bug");
			check(texts(Earned.wordsFor("plush:owl")).equals(List.of("owl")), "the owl is not earned by owl");
			check(texts(Earned.wordsFor("toy:car")).equals(List.of("car")), "the car is not earned by car");
			List<String> unearnable = Earned.links().entrySet().stream().filter(e -> e.getValue().isEmpty()).map(java.util.Map.Entry::getKey).toList();
			check(unearnable.isEmpty(), "toys no word earns: " + unearnable);
			StringBuilder table = new StringBuilder();
			Earned.links().forEach((key, words) -> table.append(key).append(" <- ").append(texts(words)).append("; "));
			LoggerFactory.getLogger("phonics-gametest").info("[earns] {}", table);
			// New prize files: toys by mastery, no random toybox prize.
			JsonObject defaults = PrizeFile.defaults();
			JsonObject events = defaults.getAsJsonObject("events");
			check(events.getAsJsonObject("toy_mastered").get("enabled").getAsBoolean(), "toy_mastered is not on in a new file");
			check(!events.getAsJsonObject("stage_complete").toString().contains("toybox"), "a new file's stage_complete still pays a random toy");
			check(!events.getAsJsonObject("milestone").toString().contains("toybox"), "a new file's milestones still pay a random toy");
			// The relations: Bob's goals made from the things.
			List<Goal> goals = Goals.all();
			check(goals.size() > 100, "only " + goals.size() + " goals for Bob");
			for (String id : List.of("doll-in-crib", "owl-in-car", "duck-in-tub", "cap-on-owl", "ant-in-jar")) {
				Goal goal = Goals.byId(id);
				check(goal != null && goal.madeBy(goal.rows()), "goal " + id + " is not made, or not solvable");
			}
			check(Relations.resolve("doll", "in", "crib", w -> null).rel() == Relations.Rel.IN, "the doll does not go in the crib");
			check(Relations.resolve("owl", "in", "car", w -> null).rel() == Relations.Rel.RIDES, "the owl does not ride the car");
			check(Relations.resolve("cap", "on", "owl", w -> null).rel() == Relations.Rel.HEAD, "the cap is not worn by the owl");
			check(Relations.resolve("box", "in", "cat", w -> null).rel() == Relations.Rel.HEAD, "box in cat is no longer the box on its head");
		});

		session.command("/phonics");
		session.waitTicks(40);
		// Earning: owl by owl, once.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			Word owl = word("owl");
			progress.masterForTests(player, owl);
			Prizes.solved(player, owl, true);
			check(progress.plushies().getOrDefault(Plush.OWL, 0) == 1, "mastering owl did not earn the owl: " + progress.plushies());
			check(Earned.earned(progress, "plush:owl"), "the owl is not marked earned");
			Prizes.solved(player, owl, true);
			check(progress.plushies().getOrDefault(Plush.OWL, 0) == 1, "the owl was earned twice");
			check(Visit.of(player).prizes().stream().anyMatch(stack -> stack.is(Plushies.item(Plush.OWL))), "the owl is not waiting in the prize chest");
			// The backfill: duck mastered before, earned on the way in.
			progress.masterForTests(player, word("duck"));
			Prizes.entered(player);
			check(progress.plushies().getOrDefault(Plush.DUCK, 0) == 1, "duck, mastered before, was not earned on the way in");
			// The Toybox tab's hints.
			Review.open(player, Review.Tab.TOYBOX, null, false);
			check(Review.toyboxHints(player).contains("plush:teddy=hug"), "the Toybox does not say hug earns the teddy: " + Review.toyboxHints(player));
		});
		session.waitTicks(20);
		shoot(session, "phonics-toybox-hints");
		session.context().setScreen(() -> null);
		session.command("/phonics leave");
		session.waitTicks(40);

		// Play in the world, on a floor of our own.
		BlockPos[] origin = new BlockPos[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			player.setGameMode(GameType.SURVIVAL);
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = player.blockPosition().offset(0, 0, 3);
			origin[0] = base;
			for (int x = -6; x <= 6; x++) {
				for (int z = -2; z <= 8; z++) {
					level.setBlock(base.offset(x, -1, z), Blocks.CONCRETE.white().defaultBlockState(), 3);
					for (int y = 0; y <= 4; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			// Break at a punch: toys, plushies, props.
			for (BlockState state : List.of(Toys.TOY.defaultBlockState(), Phonics.PLUSH.defaultBlockState(),
					net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Phonics.id("box")).defaultBlockState())) {
				check(state.getDestroySpeed(level, base) == 0.0F, state + " does not break at a punch");
			}
			// Every toy plays and comes back exactly.
			Toy[] toys = Toy.values();
			for (int i = 0; i < toys.length; i++) {
				BlockPos at = base.offset((i % 10) - 5, 0, 2 + (i / 10) * 3);
				BlockState state = Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, toys[i]).setValue(ToyBlock.FACING, Direction.NORTH);
				level.setBlock(at, state, 3);
				check(Play.play(level, at, state, Anims.of(toys[i])), toys[i] + " would not play");
				check(level.getBlockState(at).getValue(Play.HIDDEN), toys[i] + " is not hidden while it plays");
				check(Play.displayAt(level, at), toys[i] + " has no display playing it");
				check(!Play.play(level, at, level.getBlockState(at), Anims.of(toys[i])), toys[i] + " played twice at once");
			}
			BlockPos plush = base.offset(3, 0, 8);
			BlockState owl = Phonics.PLUSH.defaultBlockState().setValue(PlushBlock.KIND, Plush.OWL).setValue(PlushBlock.FACING, Direction.NORTH);
			level.setBlock(plush, owl, 3);
			check(Play.play(level, plush, owl, Anims.SQUISH), "the owl would not squish");
			look(player, base.offset(0, 0, 1), 0, 35);
		});
		for (int frame = 0; frame < 4; frame++) {
			session.waitTicks(5);
			shoot(session, "phonics-play-all-" + frame);
		}
		// Past the end of the longest play (the rings, off and back on, take over three seconds).
		int longest = 0;
		for (Toy toy : Toy.values()) longest = Math.max(longest, Anims.of(toy).length());
		session.waitTicks(Math.max(50, longest - 20 + 20));
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			BlockPos base = origin[0];
			Toy[] toys = Toy.values();
			for (int i = 0; i < toys.length; i++) {
				BlockPos at = base.offset((i % 10) - 5, 0, 2 + (i / 10) * 3);
				BlockState now = level.getBlockState(at);
				check(now.is(Toys.TOY) && now.getValue(ToyBlock.KIND) == toys[i] && now.getValue(ToyBlock.FACING) == Direction.NORTH
					&& !now.getValue(Play.HIDDEN), toys[i] + " did not come back exactly: " + now);
				check(!Play.displayAt(level, at), toys[i] + "'s display was left behind");
			}
			check(!level.getBlockState(base.offset(3, 0, 8)).getValue(Play.HIDDEN), "the owl did not come back from its squish");
			// Unloaded mid-play: the scheduled tick shows it again.
			BlockPos car = base.offset(-4, 0, 7);
			BlockState carState = Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, Toy.CAR).setValue(ToyBlock.FACING, Direction.NORTH);
			level.setBlock(car, carState, 3);
			Play.play(level, car, carState, Anims.of(Toy.CAR));
			Play.dropForTests(level, car);
			check(level.getBlockState(car).getValue(Play.HIDDEN), "the car is not still hidden after its play was dropped");
		});
		session.waitTicks(Anims.of(Toy.CAR).length() + 20);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			BlockPos car = origin[0].offset(-4, 0, 7);
			check(!level.getBlockState(car).getValue(Play.HIDDEN), "the car was left hidden after its play was dropped mid-way");
			check(!Play.displayAt(level, car), "the dropped play's display was left");
		});

		// A car loop, frame by frame, from close: the contact sheet.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos car = origin[0].offset(-4, 0, 7);
			// The owl rides it.
			player.setItemInHand(InteractionHand.MAIN_HAND, Plushies.stack(Plush.OWL));
			check(Play.put(level, car, level.getBlockState(car), player, InteractionHand.MAIN_HAND), "the owl would not get in the car");
			check("owl:rides:car".equals(Play.lastPut), "the owl did not ride the car: " + Play.lastPut);
			check(player.getMainHandItem().isEmpty(), "the owl is still in the hand");
			look(player, car.offset(0, 0, -3), 0, 28);
		});
		session.waitTicks(25);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			BlockPos car = origin[0].offset(-4, 0, 7);
			check(Play.play(level, car, level.getBlockState(car), Anims.of(Toy.CAR)), "the loaded car would not play");
		});
		for (int frame = 0; frame < 6; frame++) {
			session.waitTicks(5);
			shoot(session, "phonics-play-car-" + frame);
		}
		session.waitTicks(20);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			BlockPos car = origin[0].offset(-4, 0, 7);
			check(!level.getBlockState(car).getValue(Play.HIDDEN) && Play.contentsAt(level, car) != null, "after its loop the car or the owl is not back");
			// Saved and loaded, the owl in the car is the same.
			Display contents = Play.contentsAt(level, car);
			var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
			contents.saveWithoutId(saved);
			Display.ItemDisplay loaded = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
			loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
			check(loaded.getItemStack().is(Plushies.item(Plush.OWL)) && loaded.entityTags().containsAll(contents.entityTags()),
				"the owl in the car did not save and load the same");
		});

		// The squish, frame by frame.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos owl = origin[0].offset(3, 0, 8);
			look(player, owl.offset(0, 0, -2), 0, 30);
		});
		session.waitTicks(25);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			BlockPos owl = origin[0].offset(3, 0, 8);
			Play.play(level, owl, level.getBlockState(owl), Anims.SQUISH);
		});
		for (int frame = 0; frame < 4; frame++) {
			session.waitTicks(3);
			shoot(session, "phonics-play-squish-" + frame);
		}
		session.waitTicks(20);

		// The doll in the crib, and back out; the cap on the owl; breaking a loaded toy drops both.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos crib = origin[0].offset(-2, 0, 6);
			level.setBlock(crib, Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, Toy.CRIB).setValue(ToyBlock.FACING, Direction.NORTH), 3);
			player.setItemInHand(InteractionHand.MAIN_HAND, Toys.stack(Toy.DOLL));
			check(Play.put(level, crib, level.getBlockState(crib), player, InteractionHand.MAIN_HAND) && "doll:in:crib".equals(Play.lastPut),
				"the doll did not go in the crib: " + Play.lastPut);
			BlockPos owl = origin[0].offset(3, 0, 8);
			player.setItemInHand(InteractionHand.MAIN_HAND, Toys.stack(Toy.CAP));
			check(Play.put(level, owl, level.getBlockState(owl), player, InteractionHand.MAIN_HAND) && "cap:head:owl".equals(Play.lastPut),
				"the cap did not go on the owl: " + Play.lastPut);
			look(player, crib.offset(0, 0, -2), 0, 40);
		});
		session.waitTicks(20);
		shoot(session, "phonics-play-doll-crib");
		session.onServer(server -> look(session.player(), origin[0].offset(3, 0, 6), 0, 35));
		session.waitTicks(20);
		shoot(session, "phonics-play-cap-owl");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel level = (ServerLevel) player.level();
			BlockPos crib = origin[0].offset(-2, 0, 6);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			check(Play.takeOut(level, crib, player, InteractionHand.MAIN_HAND), "the doll would not come out");
			check(Play.contentsAt(level, crib) == null, "the doll is still in the crib");
			check(player.getInventory().contains(stack -> ToyItem.kindOf(stack) == Toy.DOLL), "the doll did not come back to the hand");
			// Breaking the loaded car drops the car and the owl.
			BlockPos car = origin[0].offset(-4, 0, 7);
			Drops.expect(player, car, java.util.Map.of("toy_car", 1, "plush_owl", 1), "the car with the owl in it");
			check(Play.contentsAt(level, car) == null, "the owl's display was left where the car was");
			// The crib with the doll tucked in: the crib and the doll, and nothing else.
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(justfatlard.phonics.toy.Toys.item(Toy.DOLL)));
			check(Play.put(level, crib, level.getBlockState(crib), player, InteractionHand.MAIN_HAND), "the doll would not go back in the crib");
			Drops.expect(player, crib, java.util.Map.of("toy_crib", 1, "toy_doll", 1), "the crib with the doll in it");
			// Every toy, at rest and mid-play: itself, once, and nothing else.
			for (Toy kind : Toy.values()) {
				// Each on a square of its own: a square plays once a second at most.
				BlockPos spot = origin[0].offset(kind.ordinal() % 10 - 5, 0, kind.ordinal() < 10 ? -1 : -2);
				String id = "toy_" + kind.name().toLowerCase(java.util.Locale.ROOT);
				BlockState state = justfatlard.phonics.toy.Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, kind).setValue(ToyBlock.FACING, Direction.NORTH);
				level.setBlock(spot, state, 3);
				Drops.expect(player, spot, java.util.Map.of(id, 1), "the " + kind);
				level.setBlock(spot, state, 3);
				check(Play.play(level, spot, state, Anims.of(kind)), kind + " would not play");
				Drops.expect(player, spot, java.util.Map.of(id, 1), "the " + kind + ", broken mid-play,");
				check(!Play.displayAt(level, spot), kind + "'s playing pieces were left behind");
			}
		});

		// A preposition round: owl in box, or on it.
		session.command("/phonics");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress.of(player).setBand(player, Stages.parse("5a"));
			Goal owlInBox = Goals.byId("owl-in-box");
			check(owlInBox != null && BobRound.prepPossible(owlInBox), "owl in box can't be a preposition round");
			check(Rooms.startBob(player, BobRound.Mode.PREP, owlInBox, null), "no preposition round");
			BobRound bob = Rooms.of(player).bob();
			check(bob.prepWords().equals(List.of("owl", "in", "box")), "the words are " + bob.prepWords());
			look(player, Rooms.of(player).spawn(), Rooms.of(player).origin().offset(0, 1, -2));
		});
		Waits.until(session, 300, "the preposition round was never asked", () -> Narrator.log(session.player()).contains("clip:phrase/what_does_it_say")
			&& !Narrator.isPlaying(session.player()));
		shoot(session, "phonics-prep-round");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			Narrator.note(player, "mark");
			check(bob.clickGoal(player, 1 - bob.prepRight()), "the wrong picture could not be clicked");
		});
		Waits.until(session, 300, "the wrong picture was never answered", () -> since(session.player()).contains("prep_pick:" + (1 - Rooms.of(session.player()).bob().prepRight()) + ":wrong")
			&& !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			check(bob.clickGoal(player, bob.prepRight()), "the right picture could not be clicked");
		});
		Waits.until(session, 600, "the right picture was never cheered", () -> since(session.player()).contains("clip:phrase/well_done"));
		session.onServer(server -> check(Progress.of(session.player()).memory().item(Progress.sentenceKey("read: owl in box")) != null,
			"the preposition round was not remembered"));
		Waits.until(session, 600, "the preposition round never moved on", () -> !Rooms.of(session.player()).busy());
		session.command("/phonics leave");
		session.waitTicks(30);
	}

	private static List<String> texts(List<Word> words) {
		List<String> out = new ArrayList<>();
		for (Word word : words) out.add(word.text());
		return out;
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static List<String> since(ServerPlayer player) {
		List<String> log = Narrator.log(player);
		return log.subList(log.lastIndexOf("mark") + 1, log.size());
	}

	private static void look(ServerPlayer player, BlockPos at, float yaw, float pitch) {
		Vec3 eye = Vec3.atBottomCenterOf(at);
		player.teleportTo((ServerLevel) player.level(), eye.x, eye.y, eye.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void look(ServerPlayer player, Vec3 from, BlockPos target) {
		Vec3 at = Vec3.atCenterOf(target);
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
