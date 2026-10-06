package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.bob.Goal;
import justfatlard.learning_blocks.bob.Goals;
import justfatlard.learning_blocks.classroom.Sky;
import justfatlard.learning_blocks.feel.Feelings;
import justfatlard.learning_blocks.feel.Gestures;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.play.Anim;
import justfatlard.learning_blocks.play.Anims;
import justfatlard.learning_blocks.play.Play;
import justfatlard.learning_blocks.puzzle.BobRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SceneRun;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.toy.Toy;
import justfatlard.learning_blocks.toy.ToyBlock;
import justfatlard.learning_blocks.toy.Toys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Each child's own sky and weather in the classroom, feelings in bubbles, gestures, toys
 * that move in parts, and Bob's mix of goals.
 *
 * <p>Night, rain, snow and a storm in this child's room only: another child in the classroom (a
 * fake one) keeps a clear noon, and a time packet is rewritten only for whoever is in the
 * classroom; the next round and the way out give back a clear noon. A word's character feels and
 * gestures and is gone with its time; a bubble shows and clears; every gesture puts its doer back
 * exactly; a scene's sad kitten is glad when solved; Bob nods when told; every toy drawn in parts
 * plays in pieces and comes back exactly; and Bob's goals keep props to their share. Full run:
 * {@code learning-sky-night.png}, {@code learning-sky-rain.png}, {@code learning-sky-snow.png},
 * {@code learning-sky-storm.png}, {@code learning-feel-mad.png}, {@code learning-feel-sad.png} and
 * {@code learning-feel-glad.png}, {@code learning-gestures.png}, {@code learning-bob-nod.png},
 * {@code learning-truck-tip.png}, {@code learning-toys-parts.png}.
 */
public final class SkyAndFeelings implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			try {
				run(session);
			} catch (Throwable failed) {
				LoggerFactory.getLogger("learning-blocks-gametest").error("SkyAndFeelings failed", failed);
				throw failed;
			}
		});
	}

	private static void run(Smoke.Session session) {
		session.command("/learn");
		session.waitTicks(40);
		goalMix(session);
		sky(session);
		feelings(session);
		gestures(session);
		scene(session);
		bob(session);
		session.command("/learn leave");
		// Going home waits for the prize line, as long as the voice takes to say it.
		Waits.until(session, 300, "never got home", () -> !justfatlard.learning_blocks.classroom.Places.isClassroom(session.player().level()));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			check(Sky.timeOf(player) == Sky.Time.DAY.ticks && Sky.weatherOf(player) == Sky.Weather.CLEAR, "the way out kept the classroom's sky");
			ClientboundSetTimePacket packet = new ClientboundSetTimePacket(0, Map.of());
			check(Sky.rewrite(player, packet) == packet, "a time packet outside the classroom was rewritten");
		});
		toys(session);
	}

	// --- Sky and weather ---

	private static void sky(Smoke.Session session) {
		FakePlayer[] other = new FakePlayer[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			ServerLevel classroom = (ServerLevel) player.level();
			other[0] = FakePlayer.get(classroom, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000d0d0"), "Dot"));
			other[0].snapTo(player.getX() + 64, player.getY(), player.getZ(), 0, 0);
			Sky.setTime(player, Sky.Time.NIGHT.ticks);
			check(Sky.timeOf(player) == Sky.Time.NIGHT.ticks, "the night did not take");
			var clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(Sky.CLOCK).orElseThrow();
			ClientboundSetTimePacket mine = Sky.rewrite(player, new ClientboundSetTimePacket(0, Map.of()));
			ClientboundSetTimePacket theirs = Sky.rewrite(other[0], new ClientboundSetTimePacket(0, Map.of()));
			check(mine.clockUpdates().get(clock).totalTicks() == Sky.Time.NIGHT.ticks, "the packet does not carry their night");
			check(theirs.clockUpdates().get(clock).totalTicks() == Sky.Time.DAY.ticks, "another child's sky went dark too");
			check(mine.clockUpdates().get(clock).rate() == 0.0F, "a night set at once is not standing still");
			look(player, -88);
		});
		session.waitTicks(40);
		shoot(session, "learning-sky-night");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Sky.glide(player, Sky.Time.DAY.ticks, 40);
			var clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(Sky.CLOCK).orElseThrow();
			check(Sky.rewrite(player, new ClientboundSetTimePacket(0, Map.of())).clockUpdates().get(clock).rate() > 0, "a sunrise is not moving");
			Sky.setWeather(player, Sky.Weather.RAIN);
			check(Sky.weatherOf(other[0]) == Sky.Weather.CLEAR, "another child is rained on too");
			look(player, -10);
		});
		session.waitTicks(60);
		shoot(session, "learning-sky-rain");
		session.onServer(server -> Sky.setWeather(session.player(), Sky.Weather.SNOW));
		session.waitTicks(60);
		shoot(session, "learning-sky-snow");
		session.onServer(server -> {
			Sky.setTime(session.player(), Sky.Time.DUSK.ticks);
			Sky.setWeather(session.player(), Sky.Weather.STORM);
		});
		session.waitTicks(50);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			int before = player.level().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, new AABB(player.blockPosition()).inflate(200)).size();
			Sky.flash(player);
			check(player.level().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, new AABB(player.blockPosition()).inflate(200)).size() == before,
				"a storm's flash made a real bolt");
		});
		session.waitTicks(2);
		shoot(session, "learning-sky-storm");
		// The lights out, and on again.
		session.onServer(server -> Sky.dark(session.player(), 10));
		session.waitTicks(2);
		session.onServer(server -> check(Sky.isDark(session.player()), "the lights did not go out"));
		session.waitTicks(14);
		session.onServer(server -> check(!Sky.isDark(session.player()), "the lights did not come back on"));
		// The next round: a clear noon.
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Rooms.nextRound(player);
			check(Sky.timeOf(player) == Sky.Time.DAY.ticks, "the next round kept the dusk");
			check(Sky.weatherOf(player) == Sky.Weather.CLEAR, "the next round kept the storm");
		});
		session.waitTicks(60);
		session.onServer(server -> {
			other[0].discard();
			look(session.player(), 0);
		});
	}

	// --- Feelings ---

	private static void feelings(Smoke.Session session) {
		int[] before = new int[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			before[0] = Feelings.count();
			Rooms.runOutcome(player, word("mad"));
		});
		session.waitTicks(30);
		session.onServer(server -> {
			Room room = Rooms.of(session.player());
			Entity mad = room.creatures().stream().filter(entity -> entity instanceof Villager).reduce((a, b) -> b).orElse(null);
			check(mad != null, "mad brought no one");
			check(Feelings.of(mad) == Feelings.Face.MAD, "mad's villager is not mad: " + Feelings.of(mad));
			check(Gestures.busy(mad), "mad's villager is not stomping");
		});
		shoot(session, "learning-feel-mad");
		session.waitTicks(100);
		session.onServer(server -> {
			Room room = Rooms.of(session.player());
			check(room.creatures().stream().noneMatch(entity -> entity instanceof Villager), "mad's villager outstayed its time");
			check(Feelings.count() == before[0], "a bubble outlived its villager: " + Feelings.count());
			// One shown and cleared by hand.
			Villager one = EntityTypes.VILLAGER.create(session.player().level(), EntitySpawnReason.TRIGGERED);
			Vec3 at = room.spawn().add(0, 0, -3);
			one.snapTo(at.x, at.y, at.z, 0, 0);
			one.setNoAi(true);
			session.player().level().addFreshEntity(one);
			Feelings.show(one, Feelings.Face.SLEEPY, 0);
			check(Feelings.of(one) == Feelings.Face.SLEEPY, "a bubble did not show");
			Feelings.show(one, Feelings.Face.SURPRISED, 0);
			check(Feelings.of(one) == Feelings.Face.SURPRISED && Feelings.count() == before[0] + 1, "a new feeling did not replace the last");
			Feelings.clear(one);
			check(Feelings.of(one) == null && Feelings.count() == before[0], "a bubble did not clear");
			one.discard();
		});
	}

	// --- Gestures ---

	private static void gestures(Smoke.Session session) {
		List<Villager> doers = new ArrayList<>();
		List<Vec3> homes = new ArrayList<>();
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			Gestures.Gesture[] all = Gestures.Gesture.values();
			for (int i = 0; i < all.length; i++) {
				Villager doer = EntityTypes.VILLAGER.create(player.level(), EntitySpawnReason.TRIGGERED);
				Vec3 at = Vec3.atBottomCenterOf(room.origin()).add(-5 + i, 0, -1 - (i % 2) * 1.5);
				doer.snapTo(at.x, at.y, at.z, 0, 0);
				doer.setYHeadRot(0);
				doer.setNoAi(true);
				doer.setPermanentlyInvulnerable(true);
				player.level().addFreshEntity(doer);
				doers.add(doer);
				homes.add(at);
				Gestures.run(doer, all[i]);
				Feelings.show(doer, Feelings.Face.GLAD, 80);
			}
			look(session.player(), 15);
		});
		session.waitTicks(10);
		shoot(session, "learning-gestures");
		session.waitTicks(60);
		session.onServer(server -> {
			for (int i = 0; i < doers.size(); i++) {
				Villager doer = doers.get(i);
				check(!Gestures.busy(doer), Gestures.Gesture.values()[i] + " never ended");
				check(Gestures.atHome(doer, homes.get(i), 0), Gestures.Gesture.values()[i] + " did not put its doer back: " + doer.position() + " " + doer.getYRot());
			}
			ServerLevel level = (ServerLevel) session.player().level();
			check(level.getEntities((Entity) null, new AABB(Rooms.of(session.player()).origin()).inflate(10),
				entity -> entity.entityTags().contains("learning-blocks-justfatlard.gesture_prop")).isEmpty(), "a wave's hand or a hiding bush was left behind");
			for (Villager doer : doers) doer.discard();
		});
	}

	// --- A scene: sad, then glad ---

	private static void scene(Smoke.Session session) {
		SceneRun[] run = new SceneRun[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Progress.of(player).read(player, word("hug").id().toString());
			check(Rooms.startScene(player, word("hug")), "no scene round for hug");
			run[0] = Rooms.of(player).scene();
			check(Feelings.of(run[0].character()) == Feelings.Face.SAD, "hug's kitten is not sad");
			lookAt(player, run[0].character());
		});
		Waits.until(session, 300, "hug's line was never said", () -> !Narrator.isPlaying(session.player()));
		shoot(session, "learning-feel-sad");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			Hands.solve(player, room);
			BlockPos button = room.button();
			Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
		});
		Waits.until(session, 600, "hug's scene was never solved", () -> run[0].solved() && Feelings.of(run[0].character()) == Feelings.Face.GLAD);
		session.waitTicks(24);
		shoot(session, "learning-feel-glad");
		Waits.until(session, 900, "hug's solve never said that helped", () -> Narrator.log(session.player()).contains("clip:phrase/that_helped"));
	}

	// --- Bob nods ---

	private static void bob(Smoke.Session session) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Narrator.stop(player);
			check(Rooms.startBob(player, BobRound.Mode.GOAL, Goals.byId("cat-in-box"), null), "no Tell Bob round");
		});
		session.waitTicks(10);
		Waits.until(session, 300, "Bob's ask was never said", () -> !Narrator.isPlaying(session.player()));
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Room room = Rooms.of(player);
			BobRound round = room.bob();
			for (String card : round.goal().rows().getFirst()) check(round.clickCard(player, card, false), "the " + card + " card would not go in");
			Gestures.lastStarted = null;
			BlockPos button = room.button();
			Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false));
		});
		Waits.until(session, 600, "Bob never said okay", () -> Narrator.log(session.player()).contains("clip:phrase/bob_okay"));
		session.onServer(server -> {
			Villager bob = Rooms.of(session.player()).bob().bob();
			check(bob != null && Gestures.busy(bob) && "nod:villager".equals(Gestures.lastStarted), "Bob did not nod at okay: " + Gestures.lastStarted);
			Vec3 from = bob.position().add(-2.2, 0, 2.8);
			Vec3 aim = bob.position().add(0, 1.6, 0).subtract(from.add(0, session.player().getEyeHeight(), 0));
			float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
			session.player().teleportTo((ServerLevel) session.player().level(), from.x, from.y, from.z, Set.of(), yaw, pitch, true);
		});
		session.waitTicks(4);
		shoot(session, "learning-bob-nod");
		Waits.until(session, 900, "Bob's round never finished", () -> !Rooms.of(session.player()).busy());
	}

	// --- Bob's goals: props kept to their share ---

	private static void goalMix(Smoke.Session session) {
		session.onServer(server -> {
			List<Goal> open = new ArrayList<>();
			for (Goal goal : Goals.all()) if (goal.shape() == Goal.Shape.SIMPLE) open.add(goal);
			long props = open.stream().filter(Goals::propsOnly).count();
			check(props > 0 && props < open.size(), "no mix of props and toys to pick from: " + props + " of " + open.size());
			check(!Goals.propsOnly(Goals.byId("cat-in-box")), "a written goal counts as props");
			for (int share : new int[] {20, 0}) {
				PrizeConfig.useBobPropShare(share);
				Random random = new Random(7);
				Goal last = null;
				int propCount = 0;
				int picks = 600;
				for (int i = 0; i < picks; i++) {
					Goal goal = Goals.pick(open, g -> false, g -> false, g -> 0, last, random);
					boolean prop = Goals.propsOnly(goal);
					if (prop && Goals.propsOnly(last)) throw new AssertionError("two prop-only goals in a row: " + last.key() + ", " + goal.key());
					if (prop) propCount++;
					last = goal;
				}
				check(propCount <= picks * (share + 5) / 100, "props were " + propCount + " of " + picks + " at a share of " + share);
				LoggerFactory.getLogger("learning-blocks-gametest").info("Bob's prop goals at a share of {}: {} of {}", share, propCount, picks);
			}
			PrizeConfig.useBobPropShare(20);
		});
	}

	// --- Toys that move in parts ---

	private static final List<Toy> IN_PARTS = List.of(Toy.TRUCK, Toy.BUS, Toy.RING, Toy.SOCK, Toy.KITE, Toy.JET, Toy.CAR, Toy.TRAIN);

	private static void toys(Smoke.Session session) {
		BlockPos[] origin = new BlockPos[1];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			player.setGameMode(GameType.SURVIVAL);
			ServerLevel level = (ServerLevel) player.level();
			BlockPos base = player.blockPosition().offset(0, 0, 3);
			origin[0] = base;
			for (int x = -8; x <= 8; x++) {
				for (int z = -2; z <= 6; z++) {
					level.setBlock(base.offset(x, -1, z), Blocks.CONCRETE.white().defaultBlockState(), 3);
					for (int y = 0; y <= 4; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			for (int i = 0; i < IN_PARTS.size(); i++) {
				Toy toy = IN_PARTS.get(i);
				Anim anim = Anims.of(toy);
				check(anim.pieces() != null && !anim.parts().isEmpty(), toy + " does not move in parts");
				BlockPos at = base.offset(i * 2 - 7, 0, i == 0 ? 0 : 3);
				Direction facing = i % 2 == 0 ? Direction.NORTH : Direction.EAST;
				BlockState state = Toys.TOY.defaultBlockState().setValue(ToyBlock.KIND, toy).setValue(ToyBlock.FACING, facing);
				level.setBlock(at, state, 3);
			}
			// The truck, from the side, at rest.
			Vec3 from = Vec3.atBottomCenterOf(base).add(-4.2, 0.2, -1.6);
			Vec3 aim = Vec3.atCenterOf(base.offset(-7, 0, 0)).subtract(from.add(0, player.getEyeHeight(), 0));
			float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
			player.teleportTo(level, from.x, from.y, from.z, Set.of(), yaw, pitch, true);
		});
		session.waitTicks(20);
		shoot(session, "learning-truck-rest");
		Vec3[] truckView = new Vec3[1];
		float[] truckTurn = new float[2];
		session.onServer(server -> {
			ServerPlayer player = session.player();
			truckView[0] = player.position();
			truckTurn[0] = player.getYRot();
			truckTurn[1] = player.getXRot();
			Vec3 from = Vec3.atBottomCenterOf(origin[0]).add(0, 0, -1.5);
			player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, Set.of(), 0, 24, true);
		});
		session.waitTicks(10);
		shoot(session, "learning-toys-rest");
		session.onServer(server -> session.player().teleportTo((ServerLevel) session.player().level(), truckView[0].x, truckView[0].y, truckView[0].z,
			Set.of(), truckTurn[0], truckTurn[1], true));
		session.waitTicks(5);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			BlockPos base = origin[0];
			for (int i = 0; i < IN_PARTS.size(); i++) {
				Toy toy = IN_PARTS.get(i);
				Anim anim = Anims.of(toy);
				BlockPos at = base.offset(i * 2 - 7, 0, i == 0 ? 0 : 3);
				BlockState state = level.getBlockState(at);
				check(Play.play(level, at, state, anim), toy + " would not play");
				int shown = level.getEntities((Entity) null, new AABB(at).inflate(0.5), entity -> entity.entityTags().contains("learning-blocks-justfatlard.playing")).size();
				check(shown == 1 + anim.parts().size(), toy + " plays in " + shown + " pieces, not " + (1 + anim.parts().size()));
			}
		});
		session.waitTicks(13);
		shoot(session, "learning-truck-tip");
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 from = Vec3.atBottomCenterOf(origin[0]).add(0, 0, -1.5);
			player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, Set.of(), 0, 24, true);
		});
		session.waitTicks(6);
		shoot(session, "learning-toys-parts");
		closeUp(session, origin[0], -3.5);
		session.waitTicks(4);
		shoot(session, "learning-toys-close-left");
		closeUp(session, origin[0], 4.5);
		session.waitTicks(4);
		shoot(session, "learning-toys-close-right");
		closeUp(session, origin[0], -3.5);
		session.waitTicks(16);
		shoot(session, "learning-toys-close-left-2");
		session.waitTicks(80);
		session.onServer(server -> {
			ServerLevel level = (ServerLevel) session.player().level();
			for (int i = 0; i < IN_PARTS.size(); i++) {
				Toy toy = IN_PARTS.get(i);
				BlockPos at = origin[0].offset(i * 2 - 7, 0, i == 0 ? 0 : 3);
				Direction facing = i % 2 == 0 ? Direction.NORTH : Direction.EAST;
				BlockState now = level.getBlockState(at);
				check(now.is(Toys.TOY) && now.getValue(ToyBlock.KIND) == toy && now.getValue(ToyBlock.FACING) == facing && !now.getValue(Play.HIDDEN),
					toy + " did not come back exactly: " + now);
				check(!Play.displayAt(level, at), toy + "'s pieces were left behind");
				check(level.getEntitiesOfClass(Display.class, new AABB(at).inflate(1.5), display -> true).isEmpty(), toy + " left a display about");
			}
		});
		// Each, close, from its side, half way through its play: no part adrift.
		for (int i = 0; i < IN_PARTS.size(); i++) {
			Toy toy = IN_PARTS.get(i);
			BlockPos at = origin[0].offset(i * 2 - 7, 0, i == 0 ? 0 : 3);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				ServerLevel level = (ServerLevel) player.level();
				// The bus faces east here: its door is on its right, the south. The kite, from its side, to see it lean and fly.
				Vec3 from = toy == Toy.BUS ? Vec3.atBottomCenterOf(at).add(0.6, 0, 1.8)
					: toy == Toy.KITE ? Vec3.atBottomCenterOf(at).add(1.4, 0, -2.4) : Vec3.atBottomCenterOf(at).add(1.3, 0, -1.3);
				Vec3 aim = Vec3.atBottomCenterOf(at).add(0, toy == Toy.KITE ? 0.9 : 0.4, 0).subtract(from.add(0, player.getEyeHeight(), 0));
				float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
				player.teleportTo(level, from.x, from.y, from.z, Set.of(), yaw, pitch, true);
			});
			session.waitTicks(3);
			shoot(session, "learning-part-" + toy.name().toLowerCase(java.util.Locale.ROOT) + "-rest");
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				check(Play.play(level, at, level.getBlockState(at), Anims.of(toy)), toy + " would not play again");
			});
			int third = Math.max(4, Anims.of(toy).length() / 3);
			session.waitTicks(third);
			shoot(session, "learning-part-" + toy.name().toLowerCase(java.util.Locale.ROOT));
			session.waitTicks(3);
			shoot(session, "learning-part-" + toy.name().toLowerCase(java.util.Locale.ROOT) + "-b");
			session.waitTicks(Anims.of(toy).length());
		}
	}

	/** Close, a little above, looking along the second row of toys from in front of {@code x}. */
	private static void closeUp(Smoke.Session session, BlockPos base, double x) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			Vec3 from = Vec3.atBottomCenterOf(base).add(x, 0.3, 0.2);
			player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, Set.of(), 0, 38, true);
		});
	}

	// --- Helpers ---

	private static Word word(String text) {
		return Words.all().stream().filter(w -> w.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no word " + text));
	}

	private static void look(ServerPlayer player, float pitch) {
		Vec3 from = Rooms.of(player).spawn();
		player.teleportTo((ServerLevel) player.level(), from.x, from.y, from.z, Set.of(), Room.FACING_YAW, pitch, true);
	}

	/** A little way off, looking at this one and over its head. */
	private static void lookAt(ServerPlayer player, Entity who) {
		Vec3 from = who.position().add(1.4, 0, 2.4);
		Vec3 at = who.position().add(0, who.getBbHeight() + 0.3, 0);
		Vec3 aim = at.subtract(from.add(0, player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
		player.teleportTo(player.level(), from.x, from.y, from.z, Set.of(), yaw, pitch, true);
	}

	private static void shoot(Smoke.Session session, String name) {
		if (session.full()) session.context().takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
