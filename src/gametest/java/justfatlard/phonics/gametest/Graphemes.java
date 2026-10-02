package justfatlard.phonics.gametest;

import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.Puzzle;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Word;
import justfatlard.phonics.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Round D's tiles and stages. The igh tile is one tile of three letters: light is l, igh, t, and
 * sounded out it says igh once (cut from light itself, or the standard igh), never i, g or h. An
 * r-controlled round, star, with the ar tile. Then three new outcomes: a crown, a wool tent, a raft.
 * Full run: {@code phonics-igh-tile.png}, {@code phonics-r-controlled-round.png},
 * {@code phonics-outcome-{crown,tent,raft}.png}, {@code phonics-review-two-letter.png}.
 */
public final class Graphemes implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			session.command("/phonics");
			session.waitTicks(40);

			session.onServer(server -> {
				ServerPlayer player = session.player();
				for (String g : List.of("ar", "or", "ur", "ir", "er", "ow", "ou", "ay", "igh", "oi", "oy", "ng", "nk")) {
					Letter letter = Letter.of(g);
					check(letter != null && letter.isDigraph() && letter.grapheme().equals(g), g + " is not a tile");
				}
				check(Letter.IGH.isVowel() && Letter.AR.isVowel() && !Letter.NG.isVowel(), "the vowel-sound tiles are not red, or ng is");
				Word light = word("light");
				check(light.letters().equals(List.of(Letter.L, Letter.IGH, Letter.T)), "light is not l, igh, t: " + light.letters());
				check(Rooms.startRound(player, light), "no round for light");
				Room room = Rooms.of(player);
				Hands.solve(player, room);
			});
			session.waitTicks(10);
			shoot(session, "phonics-igh-tile");
			session.onServer(server -> press(session.player(), Rooms.of(session.player())));
			Waits.until(session, 600, "light was never read out", () -> {
				List<String> log = Narrator.log(session.player());
				return log.lastIndexOf("outcome") > log.lastIndexOf("light:0");
			});
			session.onServer(server -> {
				List<String> log = Narrator.log(session.player());
				List<String> clips = log.subList(log.lastIndexOf("light:0"), log.lastIndexOf("light:all")).stream().filter(s -> s.startsWith("clip:")).toList();
				check(clips.size() == 3 && clips.get(0).equals("clip:phoneme/l") && clips.get(2).equals("clip:phoneme/t")
					&& (clips.get(1).equals("clip:blend/light/1") || clips.get(1).equals("clip:phoneme/igh")), "light did not blend l, igh, t: " + clips);
				check(!clips.contains("clip:phoneme/i") && !clips.contains("clip:phoneme/g") && !clips.contains("clip:phoneme/h"), "igh was said as letters: " + clips);
			});

			// An r-controlled round: star, with the ar tile.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(Rooms.startRound(player, word("star")), "no round for star");
				Room room = Rooms.of(player);
				Puzzle puzzle = room.puzzle();
				check(puzzle.target().letters().contains(Letter.AR), "star has no ar tile");
				look(player, room);
			});
			session.waitTicks(20);
			shoot(session, "phonics-r-controlled-round");

			// Three new outcomes.
			for (String text : List.of("crown", "tent", "raft")) {
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Rooms.startRound(player, word(text));
					Rooms.runOutcome(player, word(text));
				});
				session.waitTicks(40);
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					switch (text) {
						case "crown" -> check(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "crown put no crown on");
						case "tent" -> check(room.placed().stream().anyMatch(pos -> player.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.WOOL)),
							"tent put up no wool");
						default -> check(room.creatures().stream().anyMatch(e -> e.getType() == net.minecraft.world.entity.EntityTypes.BAMBOO_RAFT), "raft brought no raft");
					}
				});
				if (text.equals("crown")) session.context().runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
				shoot(session, "phonics-outcome-" + text);
				if (text.equals("crown")) session.context().runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			}
			// Every tile on the Two-letter screen, fitting.
			session.onServer(server -> justfatlard.phonics.review.Review.open(session.player(), justfatlard.phonics.review.Review.Tab.PAIRS, null, false));
			session.waitTicks(10);
			shoot(session, "phonics-review-two-letter");
			session.onServer(server -> justfatlard.phonics.review.Review.press(session.player(), "close"));
			session.command("/phonics leave");
			session.waitTicks(20);
		});
	}

	private static void look(ServerPlayer player, Room room) {
		Vec3 from = room.spawn();
		Vec3 at = Vec3.atCenterOf(room.origin().offset(0, 1, -3));
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo(player.level(), from.x, from.y, from.z, java.util.Set.of(), yaw, pitch, true);
	}

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void press(ServerPlayer player, Room room) {
		BlockPos button = room.button();
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
