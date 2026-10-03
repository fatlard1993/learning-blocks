package justfatlard.learning_blocks.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Letter;
import justfatlard.learning_blocks.puzzle.Puzzle;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.Tiles;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.reward.Memory;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Reading together, with a second reader who is a stand-in player (on the server's player list, so
 * commands and lookups find her, but with no client). Join and accept; turns alternating, each
 * round from its reader's own band; the other reader's tile refused from the gap and the button,
 * though she can still knock tiles loose; credit only to the reader whose turn it is; a team-up
 * from the lower reader's band, each gap its own reader's, each crediting their own tile and both
 * the word; the portal locked for one and open for the other, one leaving and the other carrying
 * on, the first coming back into the same room; a disconnect. Full run:
 * {@code phonics-together-turn.png}, {@code phonics-together-team.png}.
 */
public final class Together implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		PrizeConfig.teamUpForTests(0);
		try {
			Smoke.run(context, "learning-blocks-justfatlard", session -> {
				try {
					run(session);
				} catch (Throwable failed) {
					LoggerFactory.getLogger("phonics-gametest").error("Together failed", failed);
					throw failed;
				}
			});
		} finally {
			PrizeConfig.teamUpForTests(null);
		}
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		FakePlayer[] ava = new FakePlayer[1];
		String[] named = new String[1];

		// Ava, a second reader, in the classroom with her own visit; she asks, Max says yes.
		session.onServer(server -> {
			ServerPlayer max = session.player();
			named[0] = max.getGameProfile().name();
			ServerLevel classroom = (ServerLevel) max.level();
			FakePlayer fake = FakePlayer.get(classroom, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000e4111"), "Ava"));
			Vec3 at = Rooms.of(max).spawn();
			fake.snapTo(at.x - 1.5, at.y, at.z, 180, 0);
			online(server, fake, true);
			Visit.set(fake, Visit.snapshot(fake));
			ava[0] = fake;
			as(fake, "learn join " + named[0]);
			as(max, "learn accept Ava");
			Room room = Rooms.of(max);
			check(Rooms.of(fake) == room && room.members().size() == 2 && room.shared(), "Ava's yes did not bring her into Max's room");
		});

		// Turns: Max at 2a, Ava at 1a; each round is the next reader's, from their own band.
		session.onServer(server -> {
			ServerPlayer max = session.player();
			FakePlayer fake = ava[0];
			Progress.of(max).setBand(max, Stages.parse("2a"));
			List<UUID> turns = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Rooms.nextRound(max);
				Room room = Rooms.of(max);
				UUID turn = room.turn();
				turns.add(turn);
				Word target = room.puzzle().target();
				Stages.Band band = Stages.currentBand(Progress.of(turn.equals(max.getUUID()) ? max : fake));
				check(band.contains(target), "a round for " + (turn.equals(max.getUUID()) ? "Max" : "Ava") + " was " + target.text()
					+ ", not from their band " + band.label());
			}
			check(!turns.get(0).equals(turns.get(1)) && turns.get(0).equals(turns.get(2)) && turns.get(1).equals(turns.get(3)),
				"turns did not alternate: " + turns);
			// Ava's turn next, for the refusals.
			Room room = Rooms.of(max);
			if (room.turn().equals(max.getUUID())) Rooms.nextRound(max);
			check(room.turn().equals(fake.getUUID()), "it is not Ava's turn");
			check(room.gaps().stream().allMatch(gap -> fake.getUUID().equals(room.gapOwner(gap))), "Ava's gap is not hers");
			look(max, room);
		});
		session.waitTicks(30);
		session.onServer(server -> check(Rooms.of(session.player()).marked().contains(ava[0].getUUID()), "no marker over Ava on her turn"));
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("phonics-together-turn").disableCounterPrefix());

		// Max, not on his turn: he can knock a tile loose and carry it, but not put it in her gap, nor press.
		session.onServer(server -> {
			ServerPlayer max = session.player();
			Room room = Rooms.of(max);
			Narrator.note(max, "mark");
			Letter answer = Letter.of(room.puzzle().target().tiles().get(room.puzzle().gaps().getFirst()));
			check(Hands.take(max, room.home(answer)), "Max could not knock a tile loose to help");
			Hands.placeInGap(max, room);
			check(room.inGap() == null && Tiles.carried(max) >= 0, "Max put a tile in Ava's gap");
			check(since(max).contains("not_your_turn"), "Max was not told it was Ava's turn: " + since(max));
			Narrator.stop(max);
			Narrator.note(max, "mark");
			press(max, room);
			check(!room.busy() && since(max).contains("not_your_turn"), "Max's press read Ava's word");
			// Ava answers: her tile, her press, her credit; nothing for Max.
			Word word = room.puzzle().target();
			int maxBefore = Progress.of(max).clean(word);
			int avaBefore = Progress.of(ava[0]).clean(word);
			Travel.setLessons(ava[0], 3);
			int maxLessons = Travel.lessons(max);
			Tiles.clear(max);
			hold(ava[0], answer);
			Hands.placeAgainst(ava[0], room.gap().north(), Direction.SOUTH);
			check(room.inGap() == answer, "Ava's tile did not go into her gap");
			press(ava[0], room);
			check(Progress.of(ava[0]).clean(word) == avaBefore + 1, "Ava's right word did not count for her");
			check(Progress.of(max).clean(word) == maxBefore, "Ava's word counted for Max");
			check(Travel.lessons(ava[0]) == 2 && Travel.lessons(max) == maxLessons, "the lesson did not come off Ava's alone");
		});
		Waits.until(session, 600, "Ava's round never ended", () -> !Rooms.of(session.player()).busy()
			&& Rooms.of(session.player()).turn() != null && Rooms.of(session.player()).turn().equals(session.player().getUUID()));

		// A team-up: a word from Ava's band (the lower), her gap the first sound, Max's the last.
		session.onServer(server -> {
			ServerPlayer max = session.player();
			FakePlayer fake = ava[0];
			check(Rooms.startTeamRound(max), "no team-up");
			Room room = Rooms.of(max);
			Word word = room.puzzle().target();
			check(room.puzzle().kind() == Puzzle.Kind.TEAM, "the team-up is not a two-gap round");
			check(Stages.currentBand(Progress.of(fake)).contains(word), "the team-up word " + word.text() + " is not in Ava's band");
			check(room.teamReaders().getFirst().equals(fake.getUUID()), "Ava, the lower reader, is not first");
			List<BlockPos> gaps = room.gaps();
			check(fake.getUUID().equals(room.gapOwner(gaps.getFirst())) && max.getUUID().equals(room.gapOwner(gaps.getLast())),
				"the gaps are not Ava's first and Max's last");
			look(max, room);
		});
		session.waitTicks(30);
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of("phonics-together-team").disableCounterPrefix());
		session.onServer(server -> {
			ServerPlayer max = session.player();
			FakePlayer fake = ava[0];
			Room room = Rooms.of(max);
			Word word = room.puzzle().target();
			List<BlockPos> gaps = room.gaps();
			Letter first = Letter.of(word.tiles().getFirst());
			Letter last = Letter.of(word.tiles().getLast());
			// Max can't fill Ava's gap.
			Tiles.clear(max);
			hold(max, first);
			Hands.placeAgainst(max, gaps.getFirst().north(), Direction.SOUTH);
			check(room.inGap(gaps.getFirst()) == null, "Max filled Ava's team-up gap");
			Tiles.clear(max);
			int maxWord = Progress.of(max).clean(word);
			int avaWord = Progress.of(fake).clean(word);
			String firstKey = Memory.tileKey(first.grapheme());
			String lastKey = Memory.tileKey(last.grapheme());
			int maxLast = clean(max, lastKey);
			int maxFirst = clean(max, firstKey);
			int avaFirst = clean(fake, firstKey);
			int avaLast = clean(fake, lastKey);
			hold(fake, first);
			Hands.placeAgainst(fake, gaps.getFirst().north(), Direction.SOUTH);
			hold(max, last);
			Hands.placeAgainst(max, gaps.getLast().north(), Direction.SOUTH);
			check(room.inGap(gaps.getFirst()) == first && room.inGap(gaps.getLast()) == last, "the team-up tiles did not go in");
			// Either may press.
			press(fake, room);
			check(Progress.of(max).clean(word) == maxWord + 1 && Progress.of(fake).clean(word) == avaWord + 1, "the word did not count for both");
			check(clean(fake, firstKey) == avaFirst + 1 && clean(max, lastKey) == maxLast + 1, "each did not get their own tile");
			if (!first.equals(last)) {
				check(clean(max, firstKey) == maxFirst && clean(fake, lastKey) == avaLast, "a reader got the other's tile");
			}
		});
		Waits.until(session, 600, "the team-up never ended", () -> !Rooms.of(session.player()).busy() && !Rooms.of(session.player()).team());

		// The portal: kept for Ava, open for Max. She is turned back; he walks out; she carries on.
		session.onServer(server -> {
			ServerPlayer max = session.player();
			FakePlayer fake = ava[0];
			Travel.setLessons(fake, 2);
			Travel.setLessons(max, 0);
			Room room = Rooms.of(max);
			check(room.portalOpenFor(max) && !room.portalOpenFor(fake), "the portal lock is not each reader's own");
			Vec3 in = room.portalOpening().getCenter();
			fake.snapTo(in.x, in.y - 1.4, in.z, 0, 0);
			fake.setLastClientInput(new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false));
		});
		session.waitTicks(5);
		session.onServer(server -> {
			FakePlayer fake = ava[0];
			Room room = Rooms.of(fake);
			check(room != null && fake.getZ() < room.portalOpening().minZ, "Ava, still kept, was not turned back from the open portal");
			ServerPlayer max = session.player();
			Vec3 in = room.portalOpening().getCenter();
			max.setLastClientInput(new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false));
			max.teleportTo((ServerLevel) max.level(), in.x, in.y - 1.4, in.z, java.util.Set.of(), 0.0F, 0.0F, true);
		});
		Waits.until(session, 200, "Max did not walk out", () -> !Places.isClassroom(session.player().level()));
		session.onServer(server -> {
			FakePlayer fake = ava[0];
			Room room = Rooms.of(fake);
			check(room != null && room.members().equals(List.of(fake.getUUID())) && !room.shared(), "Ava's room did not carry on for her alone");
			check(Visit.of(fake) != null, "Ava's visit ended with Max's");
		});
		// Max comes back: into the room on his plot, with Ava.
		session.command("/learn");
		session.waitTicks(40);
		session.onServer(server -> {
			ServerPlayer max = session.player();
			check(Rooms.of(max) != null && Rooms.of(max) == Rooms.of(ava[0]), "coming back, Max was not in the room with Ava");
			// Ava disconnects: handled as anyone's logout, and Max reads on alone.
			FakePlayer fake = ava[0];
			Rooms.forget(fake);
			online(server, fake, false);
			Visit.set(fake, null);
			Room room = Rooms.of(max);
			check(room != null && !room.shared() && room.members().equals(List.of(max.getUUID())), "Ava's logout left the room shared");
		});
		session.waitTicks(40);
		session.onServer(server -> check(Rooms.of(session.player()).puzzle() != null, "Max's room has no round after Ava went"));

		// An operator pairs them, and splits them: Ava into her own room on her own plot.
		session.onServer(server -> {
			ServerPlayer max = session.player();
			FakePlayer fake = ava[0];
			online(server, fake, true);
			Visit.set(fake, Visit.snapshot(fake));
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn pair " + named[0] + " Ava");
			check(Rooms.of(fake) != null && Rooms.of(fake) == Rooms.of(max), "/learn pair did not put them together");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "learn unpair " + named[0]);
			check(Rooms.of(fake) != null && Rooms.of(fake) != Rooms.of(max) && !Rooms.of(max).shared(), "/learn unpair did not split them");
			check(Rooms.of(fake).origin().equals(justfatlard.learning_blocks.classroom.Plots.origin(fake)), "Ava's own room is not on her plot");
			Rooms.forget(fake);
			online(server, fake, false);
			Visit.set(fake, null);
		});
		session.command("/learn leave");
		Waits.home(session);
	}

	private static int clean(ServerPlayer player, String key) {
		return Progress.of(player).memory().clean(key);
	}

	/** On (or off) the server's player list, as a joined player is: commands and lookups find her. */
	@SuppressWarnings("unchecked")
	private static void online(MinecraftServer server, ServerPlayer player, boolean on) {
		try {
			PlayerList list = server.getPlayerList();
			Field players = PlayerList.class.getDeclaredField("players");
			Field byId = PlayerList.class.getDeclaredField("playersByUUID");
			players.setAccessible(true);
			byId.setAccessible(true);
			List<ServerPlayer> all = (List<ServerPlayer>) players.get(list);
			Map<UUID, ServerPlayer> ids = (Map<UUID, ServerPlayer>) byId.get(list);
			if (on) {
				all.add(player);
				ids.put(player.getUUID(), player);
			} else {
				all.remove(player);
				ids.remove(player.getUUID());
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not put the stand-in reader on the player list", e);
		}
	}

	private static void hold(ServerPlayer player, Letter letter) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Tiles.stack(letter));
	}

	private static void as(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static List<String> since(ServerPlayer player) {
		List<String> log = Narrator.log(player);
		return log.subList(log.lastIndexOf("mark") + 1, log.size());
	}

	private static void look(ServerPlayer player, Room room) {
		Vec3 from = room.spawn();
		Vec3 at = Vec3.atCenterOf(room.origin().offset(0, 1, -3));
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
