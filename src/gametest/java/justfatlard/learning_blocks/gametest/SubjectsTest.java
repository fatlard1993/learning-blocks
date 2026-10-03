package justfatlard.learning_blocks.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.NumberRound;
import justfatlard.learning_blocks.puzzle.PatternRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Sampler;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Earned;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subjects;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Subjects. How the mix picks: alternate (skipping one that is off), blocks, weights, and a daily
 * schedule of some of each, by the commands. Then a classroom with all three on: a reading round, a
 * numbers round (with its hello and green frame; a wrong pick, then a click on the right tile),
 * and a logic round, each counted for the day and taking a lesson off. Numbers end to end: the last
 * numeral mastered finishes N0 ("ready for more", paid once) and earns a toy linked to the
 * numerals. And the sampler: a numbers round of one number and a logic round, practice only, the
 * saved progress untouched; a kind that isn't one says what is.
 */
public final class SubjectsTest implements FabricClientGameTest {
	private static final Set<String> ALL = Set.of("reading", "numbers", "logic");

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("SubjectsTest failed", failed);
				throw failed;
			} finally {
				Subjects.onlyForTests(Set.of("reading"));
				PrizeConfig.useSubjectMix(Subjects.Mix.ALTERNATE, 3);
				Earned.clearItemLinksForTests();
				Numbers.linkToys();
			}
		});
	}

	private static void run(Smoke.Session session) {
		UUID[] id = new UUID[1];
		String[] name = new String[1];

		// --- The mix, as picked ---
		session.onServer(server -> {
			ServerPlayer player = session.player();
			id[0] = player.getUUID();
			name[0] = player.getGameProfile().name();
			var random = player.getRandom();
			Subjects.onlyForTests(ALL);
			PrizeConfig.useSubjectMix(Subjects.Mix.ALTERNATE, 3);
			check(Subjects.pick(player, null, 0, random) == Subjects.READING, "alternate does not start on reading");
			check(Subjects.pick(player, "reading", 1, random) == Numbers.INSTANCE, "alternate: reading is not followed by numbers");
			check(Subjects.pick(player, "numbers", 1, random) == Logic.INSTANCE, "alternate: numbers is not followed by logic");
			check(Subjects.pick(player, "logic", 1, random) == Subjects.READING, "alternate: logic is not followed by reading");
			Subjects.onlyForTests(Set.of("reading", "logic"));
			check(Subjects.pick(player, "reading", 1, random) == Logic.INSTANCE, "alternate did not skip numbers, which is off");
			Subjects.onlyForTests(ALL);

			PrizeConfig.useSubjectMix(Subjects.Mix.BLOCKS, 2);
			check(Subjects.pick(player, "reading", 1, random) == Subjects.READING, "blocks of two: a second reading round did not come");
			check(Subjects.pick(player, "reading", 2, random) == Numbers.INSTANCE, "blocks of two: numbers did not follow two reading rounds");

			PrizeConfig.useSubjectMix(Subjects.Mix.WEIGHTS, 3);
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < 300; i++) seen.add(Subjects.pick(player, "reading", 1, random).id());
			check(seen.equals(ALL), "weights, all 1, did not pick every subject in 300 tries: " + seen);
			PrizeConfig.useSubjectMix(Subjects.Mix.ALTERNATE, 3);

			// A child's own subjects, by the command (the test override aside, it is what counts).
			command(server, "learn subjects " + name[0] + " numbers off");
			check(Boolean.FALSE.equals(Progress.of(player).subjectOn("numbers")), "/learn subjects did not turn numbers off");
			Subjects.onlyForTests(null);
			check(!Subjects.on(player, Numbers.INSTANCE) && Subjects.on(player, Logic.INSTANCE), "the child's own setting does not count");
			command(server, "learn subjects " + name[0] + " numbers default");
			check(Progress.of(player).subjectOn("numbers") == null && Subjects.on(player, Numbers.INSTANCE), "numbers did not go back to the default (on)");
			Subjects.onlyForTests(ALL);

			// A daily schedule of some of each: only those owed are picked, until each is done.
			command(server, "learn daily " + name[0] + " 3 numbers 2 logic 1");
			Schedules.Entry entry = Schedules.of(id[0]);
			check(entry != null && entry.subjects().equals(Map.of("numbers", 2, "logic", 1)) && entry.lessons() == 3,
				"/learn daily with a split did not set it: " + entry);
			check(Schedules.remaining(player) == 3, "a split of 2 + 1 does not leave 3: " + Schedules.remaining(player));
			check(Subjects.pick(player, "logic", 1, random) == Numbers.INSTANCE, "after logic, numbers (owed) did not come");
			check(Subjects.pick(player, "numbers", 1, random) == Logic.INSTANCE, "after numbers, logic (owed) did not come; reading is not owed");
			Progress.of(player).solvedToday(player, "logic");
			check(Schedules.remaining(player) == 2, "a logic round did not count: " + Schedules.remaining(player));
			check(Subjects.pick(player, "numbers", 1, random) == Numbers.INSTANCE, "logic done for today, numbers is still owed but was not picked");
			Progress.of(player).solvedToday(player, "numbers");
			Progress.of(player).solvedToday(player, "numbers");
			check(Schedules.remaining(player) == 0, "the day's split is not done: " + Schedules.remaining(player));
			check(Subjects.pick(player, "logic", 1, random) == Subjects.READING, "all owed done: the mix did not go back to alternating");
			// A plain number is any subject again.
			command(server, "learn daily " + name[0] + " 0");
			check(Schedules.of(id[0]).subjects().isEmpty(), "a plain /learn daily did not clear the split");
		});

		// --- The classroom: reading, then numbers, then logic (2 numbers and 1 logic already counted today, above) ---
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(room != null && room.puzzle() != null && room.subjectRound() == null && room.currentSubject().equals("reading"),
				"the first round is not reading: " + (room == null ? "no room" : room.currentSubject()));
			check(room.frameColour() == DyeColor.BLUE, "reading's frame is not blue: " + room.frameColour());
			Travel.setLessons(player, 5);
			Hands.solve(player, room);
			press(player, room);
		});
		Waits.until(session, 900, "a numbers round never came after reading", () -> {
			Room room = Rooms.of(session.player());
			return room.subjectRound() instanceof NumberRound && !room.busy();
		});
		shot(session, "learning-subject-numbers");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(Travel.lessons(player) == 4 && Progress.of(player).doneToday("reading") == 1, "the reading round did not count: "
				+ Travel.lessons(player) + ", " + Progress.of(player).doneToday("reading"));
			check(room.frameColour() == DyeColor.LIME, "numbers' frame is not green: " + room.frameColour());
			check(Narrator.log(player).contains("subject_hello:numbers"), "no hello on switching to numbers");
			NumberRound round = (NumberRound) room.subjectRound();
			check(round.choices().size() == 3, "a numbers round does not have three tiles: " + round.choices());
			check(round.choices().stream().allMatch(choice -> choice.block().getBlock() == justfatlard.learning_blocks.LearningBlocks.NUMBER),
				"a numbers round's choices are not number tiles");
			SubjectRound.Choice wrong = round.choices().stream().filter(choice -> !choice.equals(round.rightForTests())).findFirst().orElseThrow();
			round.pickForTests(player, wrong);
		});
		Waits.until(session, 600, "the wrong number never finished being told", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(room.subjectRound() instanceof NumberRound, "a wrong number ended the round");
			check(Narrator.log(player).contains("clip:phrase/oops_thats"), "a wrong number was not told");
			check(Travel.lessons(player) == 4, "a wrong number took a lesson off");
			// The right one, by a click on its box, as a child would.
			NumberRound round = (NumberRound) room.subjectRound();
			BlockPos tile = null;
			List<SubjectRound.Choice> choices = round.choices();
			Interaction hit = null;
			for (Interaction box : player.level().getEntitiesOfClass(Interaction.class, new AABB(player.blockPosition()).inflate(12))) {
				Vec3 at = box.position();
				BlockPos under = BlockPos.containing(at.x, at.y + 0.5, at.z);
				if (player.level().getBlockState(under).equals(round.rightForTests().block())) {
					hit = box;
					tile = under;
				}
			}
			check(hit != null, "no click box over the right tile among " + choices);
			Rooms.clickEntity(player, player.level(), hit);
			check(Narrator.log(player).contains("picked:numbers:" + round.number()), "the click on " + tile + " was not a pick");
		});
		Waits.until(session, 900, "a logic round never came after numbers", () -> {
			Room room = Rooms.of(session.player());
			return room.subjectRound() instanceof PatternRound && !room.busy();
		});
		shot(session, "learning-subject-logic");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(Travel.lessons(player) == 3 && Progress.of(player).doneToday("numbers") == 3, "the numbers round did not count: "
				+ Travel.lessons(player) + ", " + Progress.of(player).doneToday("numbers"));
			check(room.frameColour() == DyeColor.PURPLE, "logic's frame is not purple: " + room.frameColour());
			check(Narrator.log(player).contains("subject_hello:logic"), "no hello on switching to logic");
			PatternRound round = (PatternRound) room.subjectRound();
			check(round.rightForTests().block().getBlock() == net.minecraft.world.level.block.Blocks.WOOL.pick(round.next()),
				"the pattern's right answer is not the next colour");
			round.pickForTests(player, round.rightForTests());
		});
		Waits.until(session, 900, "reading never came back after logic", () -> {
			Room room = Rooms.of(session.player());
			return room.subjectRound() == null && room.currentSubject().equals("reading") && !room.busy();
		});
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(Travel.lessons(player) == 2 && Progress.of(player).doneToday("logic") == 2, "the logic round did not count: "
				+ Travel.lessons(player) + ", " + Progress.of(player).doneToday("logic"));
			check(Progress.of(player).itemClean(Logic.AB) == 1, "the pattern, right first try, is not one clean: " + Progress.of(player).itemClean(Logic.AB));
			check(room.frameColour() == DyeColor.BLUE, "back to reading, the frame is not blue");

			// Numbers end to end: every numeral of N0 mastered but 3, one short; a toy waits on all of them.
			Progress progress = Progress.of(player);
			for (int n = 0; n <= 10; n++) progress.setItemCleanForTests(player, Numbers.numeral(n), n == 3 ? Progress.masteredAt() - 1 : Progress.masteredAt());
			// Its words too, if any earn it: a toy with both needs both.
			String toy = "toy:" + justfatlard.learning_blocks.toy.Toy.values()[0].word();
			for (justfatlard.learning_blocks.puzzle.Word word : Earned.wordsFor(toy)) progress.masterForTests(player, word);
			List<String> numerals = new java.util.ArrayList<>();
			for (int n = 0; n <= 10; n++) numerals.add(Numbers.numeral(n));
			Earned.linkItems(toy, numerals);
			check(!Earned.earned(progress, toy), "the linked toy was earned before its numerals were all mastered");
			check(!Numbers.INSTANCE.levelDone(progress), "the level is done with three short");
			room.newSubjectRound(player, Numbers.INSTANCE, "find", "3");
			check(room.subjectRound() instanceof NumberRound number && number.number() == 3, "a numbers round of 3 was not made");
		});
		Waits.until(session, 300, "the numbers round of 3 never settled", () -> !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			NumberRound round = (NumberRound) Rooms.of(player).subjectRound();
			round.pickForTests(player, round.rightForTests());
			Progress progress = Progress.of(player);
			check(progress.itemMastered(Numbers.numeral(3)) && Numbers.INSTANCE.levelDone(progress), "3 right first try did not master it and the level");
			check(progress.rewardedFor("level:numbers:N0"), "the numbers level was not paid");
			check(progress.rewardedFor(Numbers.numeral(3)), "a first right answer for 3 was not new");
			check(Earned.earned(progress, "toy:" + justfatlard.learning_blocks.toy.Toy.values()[0].word()), "the toy linked to the numerals was not earned");
			check(Subjects.mastered(progress, "numbers:numeral:3") && !Subjects.mastered(progress, "numbers:count:9"), "Subjects.mastered is wrong");
		});
		Waits.until(session, 900, "the level's end never finished", () -> !Rooms.of(session.player()).busy()
			&& !(Rooms.of(session.player()).subjectRound() instanceof NumberRound number && number.number() == 3));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Narrator.log(player).contains("clip:phrase/next_level_numbers"), "\"ready for more numbers\" was not said");
		});

		// --- The sampler, by subject ---
		Tag[] before = new Tag[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			before[0] = Progress.savedForTests(player);
			check(Sampler.runSubject(player, Numbers.INSTANCE, "sing", null) != null, "a kind numbers hasn't got was not refused");
			check(Sampler.runSubject(player, Numbers.INSTANCE, "find", "42") != null, "a number find can't ask was not refused");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), "learn try numbers find 4");
			Room room = Rooms.of(player);
			check(room.practice() && room.subjectRound() instanceof NumberRound number && number.number() == 4, "/learn try numbers find 4 did not ask for 4");
			NumberRound round = (NumberRound) room.subjectRound();
			round.pickForTests(player, round.rightForTests());
			// As for Reading's samples: answered, and nothing saved changed (the ordinary round after may).
			check(Progress.savedForTests(player).equals(before[0]), "a numbers sample changed the saved progress");
		});
		Waits.until(session, 900, "the numbers sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			before[0] = Progress.savedForTests(player);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), "learn try logic");
			Room room = Rooms.of(player);
			check(room.practice() && room.subjectRound() instanceof PatternRound, "/learn try logic did not make a pattern round");
			PatternRound round = (PatternRound) room.subjectRound();
			round.pickForTests(player, round.rightForTests());
			check(Progress.savedForTests(player).equals(before[0]), "a logic sample changed the saved progress");
			// The lesson still owed from the five set above, let go, so the way home is open.
			Travel.setLessons(player, 0);
		});
		Waits.until(session, 900, "the logic sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			Subjects.onlyForTests(Set.of("reading"));
		});
		session.command("/learn leave");
		Waits.until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level()));
	}

	/** From where a child stands, looking at the board and the floor before it. */
	private static void shot(Smoke.Session session, String name) {
		if (!session.full()) return;
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 from = Rooms.of(player).spawn();
			player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), from.x, from.y, from.z, Set.of(), Room.FACING_YAW, 6, true);
		});
		session.waitTicks(20);
		session.context().takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void command(net.minecraft.server.MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
