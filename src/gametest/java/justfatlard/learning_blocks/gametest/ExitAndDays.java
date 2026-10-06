package justfatlard.learning_blocks.gametest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Memory;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * The way home and the shape of a day. An open portal walked into sends a child home with no click;
 * a locked one stops them and says the count; "Go home" (/learn leave) refuses with the count while
 * kept and works once released; a villager in the room is the classroom's, and no other listener sees
 * the click. In a day no word is asked twice while the band has others, and then the room fills in
 * (another kind of round first); a second clean completion the same day doesn't count towards mastery
 * and the next day's does, for letters too. Offline names resolve through the server's profile cache.
 * Full run: {@code learning-portal-locked.png}, {@code learning-portal-open.png}.
 */
public final class ExitAndDays implements FabricClientGameTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
	/** Whether a listener after the classroom's ever saw a click on a room villager. */
	private static volatile boolean otherSawIt;
	private static boolean listening;

	@Override
	public void runTest(ClientGameTestContext context) {
		LocalDate[] today = {DAY};
		Progress.useClock(() -> today[0]);
		Progress.repeatsForTests(false);
		Memory.spreadOffForTests(false);
		try {
			Smoke.run(context, "learning-blocks-justfatlard", session -> {
				try {
					run(session, today);
				} catch (Throwable failed) {
					LoggerFactory.getLogger("learning-blocks-gametest").error("ExitAndDays failed", failed);
					throw failed;
				}
			});
		} finally {
			Progress.useClock(null);
			Progress.repeatsForTests(true);
			Memory.spreadOffForTests(true);
		}
	}

	private static void run(Smoke.Session session, LocalDate[] today) {
		if (!listening) {
			listening = true;
			UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
				if (entity instanceof Villager && Places.isClassroom(level)) otherSawIt = true;
				return InteractionResult.PASS;
			});
		}
		String[] named = new String[1];
		session.onServer(server -> named[0] = session.player().getGameProfile().name());
		String name = named[0];

		// An ordinary visit: the portal is open, and walking in goes home. No click.
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(room.portalOpen() && room.portalSign() == null, "an ordinary visit's portal is not open");
			Narrator.note(player, "mark");
		});
		lookAtPortal(session);
		session.waitTicks(30);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("learning-portal-open").disableCounterPrefix());
		// Backed into it, facing the board, or standing in it facing in without walking: not leaving.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = Rooms.of(player).portalOpening().getCenter();
			player.teleportTo(player.level(), at.x, at.y - 1.4, at.z, java.util.Set.of(), 180.0F, 0.0F, true);
		});
		session.waitTicks(20);
		session.onServer(server -> check(Places.isClassroom(session.player().level()), "backing into the portal, facing the board, sent them home"));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 at = Rooms.of(player).portalOpening().getCenter();
			player.setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY);
			player.teleportTo(player.level(), at.x, at.y - 1.4, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		});
		session.waitTicks(20);
		session.onServer(server -> check(Places.isClassroom(session.player().level()), "standing still in the portal sent them home"));
		session.onServer(server -> walkInto(session.player()));
		Waits.until(session, 200, "walking into the open portal did not go home", () -> !Places.isClassroom(session.player().level()));
		session.onServer(server -> check(Visit.of(session.player()) == null, "home, but the visit is still open"));

		// Kept for two: dark, walled, counted; walking into it says "two more to go" and keeps them.
		session.onServer(server -> console(server, "learn send " + name + " 2"));
		session.waitTicks(60);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			check(Places.isClassroom(player.level()) && !room.portalOpen() && room.portalSign() != null, "sent for two, the portal is not locked");
			Narrator.stop(player);
			Narrator.note(player, "mark");
			walkInto(player);
		});
		Waits.until(session, 100, "bumping the locked portal said nothing", () -> since(session.player()).contains("portal:bump:2"));
		lookAtPortal(session);
		session.waitTicks(20);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("learning-portal-locked").disableCounterPrefix());
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Places.isClassroom(player.level()), "a locked portal let them through");
			// "Go home" on the action menu is /learn leave: refused, and said, while kept.
			Narrator.stop(player);
			Narrator.note(player, "mark");
			asPlayer(player, "learn leave");
		});
		session.waitTicks(10);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Places.isClassroom(player.level()) && Visit.of(player) != null, "Go home let them out with lessons left");
			check(since(player).contains("clip:phrase/more_to_go_2"), "Go home did not say the count: " + since(player));

			// A villager in the room: the click is the classroom's, and no other listener hears it.
			Villager villager = EntityTypes.VILLAGER.create(player.level(), EntitySpawnReason.TRIGGERED);
			Vec3 at = Rooms.of(player).spawn().add(1, 0, -1);
			villager.snapTo(at.x, at.y, at.z, 0, 0);
			player.level().addFreshEntity(villager);
			otherSawIt = false;
			InteractionResult main = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
			InteractionResult off = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, villager, null);
			check(main != InteractionResult.PASS && off != InteractionResult.PASS, "a room villager's click passed on: " + main + ", " + off);
			check(!otherSawIt, "another mod's listener heard a click on a room villager");
			villager.discard();

			console(server, "learn release " + name);
			asPlayer(player, "learn leave");
		});
		Waits.until(session, 200, "Go home did not work once released", () -> !Places.isClassroom(session.player().level()));

		// A day's lessons, on a day of their own (the visits above used some of today's):
		// no word twice while the band has others, then another kind of round.
		today[0] = DAY.plusDays(1);
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			Stages.Band band = Stages.currentBand(progress);
			check(band != null && band.label().equals("1a"), "the reader is not at 1a: " + Stages.describe(progress));
			List<String> asked = new ArrayList<>();
			asked.add(Rooms.of(player).puzzle().target().text());
			for (int i = 1; i < band.words().size(); i++) {
				Rooms.nextRound(player);
				asked.add(Rooms.of(player).puzzle().target().text());
			}
			check(asked.stream().distinct().count() == band.words().size(), "a word came twice while 1a had others: " + asked);
			Narrator.note(player, "mark");
			String last = asked.getLast();
			Rooms.nextRound(player);
			Room room = Rooms.of(player);
			List<String> log = since(player);
			check(log.stream().anyMatch(step -> step.startsWith("fill:kind:") || step.startsWith("fill:read_it:") || step.startsWith("fill:scene:")),
				"with 1a used up, the next round was not a word again in another kind: " + log);
			check(!room.puzzle().target().text().equals(last), "the fill repeated the last word");
			String used = room.puzzle().target().id() + "|" + room.puzzle().kind().name().toLowerCase(java.util.Locale.ROOT);
			check(progress.usedToday().contains(used) && progress.usedToday().size() == band.words().size() + 1,
				"today's lessons are not recorded: " + progress.usedToday());

			// One clean completion a day counts.
			Word sat = Words.all().stream().filter(w -> w.text().equals("sat")).findFirst().orElseThrow();
			int before = progress.clean(sat);
			progress.wordRight(player, sat, true, List.of());
			progress.wordRight(player, sat, true, List.of());
			check(progress.clean(sat) == before + 1, "two clean the same day counted " + (progress.clean(sat) - before));
			check(!progress.cleanCountsToday(sat), "a second clean completion today would count");
			int hits = progress.letterHits("m");
			progress.hitLetter(player, "m");
			progress.hitLetter(player, "m");
			check(progress.letterHits("m") == hits + 1, "two clean letter matches the same day counted twice");
		});
		// The next day: it counts again, and the day's lessons start over.
		today[0] = DAY.plusDays(2);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress progress = Progress.of(player);
			Word sat = Words.all().stream().filter(w -> w.text().equals("sat")).findFirst().orElseThrow();
			int before = progress.clean(sat);
			check(progress.cleanCountsToday(sat) && progress.usedToday().isEmpty(), "a new day did not start over");
			progress.wordRight(player, sat, true, List.of());
			check(progress.clean(sat) == before + 1, "the next day's clean completion did not count");
		});

		// Offline names: the server's profile cache.
		session.onServer(server -> {
			UUID testKid = UUID.fromString("00000000-0000-0000-0000-00000000b3e7");
			UUID sam = UUID.fromString("00000000-0000-0000-0000-0000000005a7");
			server.services().nameToIdCache().add(new NameAndId(testKid, "TestKidA"));
			server.services().nameToIdCache().add(new NameAndId(sam, "Sam2019"));
			console(server, "learn daily TestKidA 3");
			Schedules.Entry entry = Schedules.of(testKid);
			check(entry != null && entry.name().equals("TestKidA") && entry.lessons() == 3, "an offline cached name was not scheduled: " + entry);
			console(server, "learn daily " + sam + " 2");
			check(Schedules.of(sam) != null && Schedules.of(sam).name().equals("Sam2019"), "set by UUID, the real name was not kept: " + Schedules.of(sam));
			// A name the cache has never seen is refused, never given a made-up id (the live server is offline-mode).
			console(server, "learn daily NeverJoinedKid 2");
			check(Schedules.all().values().stream().noneMatch(e -> e.name().equalsIgnoreCase("NeverJoinedKid")),
				"a name nobody has was scheduled under a made-up id");
			console(server, "learn daily TestKidA off");
			console(server, "learn daily Sam2019 off");
			check(!Schedules.of(testKid).enabled() && !Schedules.of(sam).enabled(), "the schedules did not turn off by name");
			ServerPlayer player = session.player();
			Schedules.set(player.getUUID(), "SomeOldName", 0);
			Schedules.refreshName(player.getUUID(), player.getGameProfile().name());
			check(Schedules.of(player.getUUID()).name().equals(player.getGameProfile().name()), "joining did not refresh the stored name");
		});
		session.command("/learn leave");
		Waits.home(session);
	}

	/** The log since the last mark. */
	private static List<String> since(ServerPlayer player) {
		List<String> log = Narrator.log(player);
		return log.subList(log.lastIndexOf("mark") + 1, log.size());
	}

	/** Stand in the portal's opening. */
	private static void walkInto(ServerPlayer player) {
		Vec3 at = Rooms.of(player).portalOpening().getCenter();
		// Facing into it (south) and walking forward, as a child walking in does.
		player.setLastClientInput(new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false));
		player.teleportTo(player.level(), at.x, at.y - 1.4, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
	}

	private static void lookAtPortal(Smoke.Session session) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			// Facing the back wall (south, yaw 0), a few steps from the portal.
			Vec3 from = room.spawn().add(0.5, 0, -2.5);
			player.teleportTo(player.level(), from.x, from.y, from.z, java.util.Set.of(), 0.0F, -10.0F, true);
		});
	}

	private static void console(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	private static void asPlayer(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
