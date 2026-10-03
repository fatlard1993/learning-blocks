package justfatlard.learning_blocks.gametest;

import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Visit;
import java.util.List;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.BobRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Sampler;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.review.SamplerScreen;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * The grown-ups' sampler. Every kind of round runs from {@code /learn try} (or the sampler
 * directly), is answered (the first sound one wrong first), and leaves the reader's saved
 * progress and visit (lessons and prizes) exactly as they were; a named word is the round's;
 * an unknown kind or word says what would do; a team-up alone says it needs two; the sampler
 * screen opens, and its buttons and Next run rounds.
 */
public final class SamplerTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("phonics-gametest").error("SamplerTest failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			// Lessons to keep, so a lesson taken off would show.
			justfatlard.learning_blocks.classroom.Travel.setLessons(player, 5);
			// A named word, by the command.
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), "learn try first cat");
			Room room = Rooms.of(player);
			check(room.practice() && room.puzzle().target().text().equals("cat"), "/learn try first cat did not ask for cat: "
				+ room.puzzle().target().text());
			check(Sampler.run(player, Sampler.Kind.FIRST, "xyzzy").contains("can't"), "a word that can't be a round was not refused helpfully");
			check(Sampler.Kind.of("nope") == null, "an unknown kind was found");
			check(Sampler.run(player, Sampler.Kind.TEAMUP, null).contains("two"), "a team-up alone did not say it needs two");
		});
		answer(session, true);
		Waits.until(session, 900, "the cat sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());

		for (Sampler.Kind kind : Sampler.Kind.values()) {
			if (kind == Sampler.Kind.TEAMUP) continue;
			Tag[] before = new Tag[2];
			session.onServer(server -> {
				ServerPlayer player = session.player();
				before[0] = Progress.savedForTests(player);
				before[1] = visit(player);
				String problem = Sampler.run(player, kind, null);
				check(problem == null, kind.id() + " would not run: " + problem);
				check(Rooms.of(player).practice() && Narrator.log(player).stream().anyMatch(s -> s.startsWith("sample:" + kind.id() + ":")),
					kind.id() + " is not a practice round");
			});
			answer(session, kind == Sampler.Kind.FIRST);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Progress.savedForTests(player).equals(before[0]), kind.id() + " changed the reader's saved progress");
				check(visit(player).equals(before[1]), kind.id() + " changed the reader's lessons or prizes");
			});
			// Silly sentences go on until asked to stop: back to reading.
			session.onServer(server -> {
				BobRound bob = Rooms.of(session.player()).bob();
				if (bob != null && bob.mode() == BobRound.Mode.SILLY) Rooms.startSilly(session.player());
			});
			Waits.until(session, 900, kind.id() + "'s round never ended", () -> !Rooms.of(session.player()).practice()
				&& !Rooms.of(session.player()).busy());
		}

		// The screen, its buttons and Next.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			// The sampler is for operators.
			server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
			check(justfatlard.learning_blocks.editor.Admins.may(player), "the test player is not an operator");
			check(justfatlard.pandorical.api.PandoricalApi.isAvailable(player), "Pandorical is not on the test client");
			check(SamplerScreen.open(player), "the sampler screen did not open");
			check(SamplerScreen.isOpen(player), "the sampler screen opened and closed");
			SamplerScreen.press(player, "kind:tricky");
			check(SamplerScreen.lastRan(player).equals("tricky") && Rooms.of(player).tricky() && Rooms.of(player).practice(),
				"the Tricky word button did not run one");
		});
		answer(session, false);
		Waits.until(session, 900, "the tricky sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			SamplerScreen.open(player);
			SamplerScreen.press(player, "next");
			check(Rooms.of(player).tricky() && Rooms.of(player).practice(), "Next did not run another tricky word");
		});
		answer(session, false);
		Waits.until(session, 900, "the next sample never ended", () -> !Rooms.of(session.player()).practice() && !Rooms.of(session.player()).busy());
		session.command("/learn leave");
		Waits.home(session);
	}

	/** Answer the round: the right picture or card, or the right tiles and the button; a wrong tile first, if asked. */
	private static void answer(Smoke.Session session, boolean wrongFirst) {
		// Bob's rounds: the goal's own sentence, built and told; silly sentences, any one, then back to reading.
		boolean[] silly = new boolean[1];
		boolean[] withBob = new boolean[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			BobRound bob = Rooms.of(player).bob();
			if (bob == null) return;
			withBob[0] = true;
			if (bob.mode() == BobRound.Mode.PREP) {
				check(bob.clickGoal(player, bob.prepRight()), "the right picture could not be picked");
				return;
			}
			for (List<String> row : bob.rowWords()) for (String word : row) bob.clickCard(player, word, true);
			if (bob.mode() == BobRound.Mode.SILLY) {
				silly[0] = true;
				List<String> nouns = bob.floorWords().stream().filter(w -> justfatlard.learning_blocks.bob.Noun.of(w) != null).toList();
				for (String word : List.of("put", nouns.get(0), "on", nouns.get(1))) check(bob.clickCard(player, word, false), "the silly " + word + " card would not go in");
			} else {
				for (List<String> row : bob.goal().rows()) for (String word : row) check(bob.clickCard(player, word, false), "the " + word + " card would not go in");
			}
			press(player, Rooms.of(player));
		});
		if (withBob[0]) {
			if (silly[0]) {
				Waits.until(session, 900, "the silly sentence never finished", () -> Narrator.log(session.player()).stream().anyMatch(s -> s.startsWith("silly:"))
					&& !Rooms.of(session.player()).busy());
			}
			return;
		}
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			if (room.readIt()) {
				for (Map.Entry<UUID, Word> choice : room.choices().entrySet()) {
					if (!choice.getValue().id().equals(room.puzzle().target().id())) continue;
					Entity box = player.level().getEntity(choice.getKey());
					Rooms.clickEntity(player, player.level(), box);
					return;
				}
				throw new AssertionError("no right card for " + room.puzzle().target().text());
			}
			if (wrongFirst) {
				Letter right = Letter.of(room.puzzle().target().tiles().get(room.puzzle().gaps().getFirst()));
				Letter wrong = room.puzzle().shelf().stream().filter(l -> l != right).findFirst().orElseThrow();
				check(Hands.take(player, room.home(wrong)), "the wrong tile would not break");
				Hands.placeInGap(player, room);
				press(player, room);
			}
		});
		if (wrongFirst) Waits.until(session, 600, "the wrong answer never finished", () -> !Rooms.of(session.player()).busy()
			&& Rooms.of(session.player()).inGap() == null);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			if (room.readIt()) return;
			Hands.solve(player, room);
			press(player, room);
		});
	}

	private static Tag visit(ServerPlayer player) {
		Visit visit = Visit.of(player);
		if (visit == null) return net.minecraft.nbt.EndTag.INSTANCE;
		return Visit.CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), visit).getOrThrow();
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
