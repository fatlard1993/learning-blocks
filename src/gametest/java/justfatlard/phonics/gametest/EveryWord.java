package justfatlard.phonics.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.puzzle.Outcome;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Word;
import justfatlard.phonics.puzzle.Words;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every shipped word: its ids resolve, a round can be built for it, and its outcome runs in a room
 * without throwing, without anything hostile turning up, without anybody getting hurt, and with
 * every block it put down gone by the next round. Then its wrong version, as when it is built but
 * was not the word asked for: the same checks, and after a few seconds nothing of it is left at all,
 * no block, creature, prop, item, effect or change of size. On the way out nothing is left of any of it.
 *
 * <p>On the full run, pictures of a few: big, fun, cot and dog, and a round with four or more loose tiles.
 */
public final class EveryWord implements FabricClientGameTest {

	private static final Set<String> PICTURED = Set.of("big", "fun", "cot", "dog", "ship", "frog", "bell");
	/** Photographed in the air, on the way from the button. */
	private static final Set<String> FLIGHT = Set.of("cot", "dog", "hat");
	/** Wrong versions photographed as they happen: a hat bonking (from behind, to see the head), a log rolling. */
	private static final Set<String> WRONG_PICTURED = Set.of("hat", "log");

	/** Every part of an outcome, a sequence's parts included. */
	private static List<Outcome> parts(Outcome outcome) {
		if (outcome instanceof Outcome.Sequence sequence) {
			List<Outcome> all = new ArrayList<>();
			for (Outcome step : sequence.steps()) all.addAll(parts(step));
			return all;
		}
		return List.of(outcome);
	}

	/** What each part leaves behind, once it has flown: an item in the pack, a hat on, a block down, a creature about. */
	private static void landed(ServerPlayer player, Word word) {
		Room room = Rooms.of(player);
		for (Outcome part : parts(word.outcome())) {
			switch (part) {
				case Outcome.GiveItem give -> check(player.getInventory().hasAnyMatching(stack -> stack.is(BuiltInRegistries.ITEM.getValue(give.item()))),
					word.id() + "'s " + give.item() + " never reached the child, at " + player.position() + (player.isPassenger() ? " riding " + player.getVehicle() : "")
						+ "; items about: " + player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
							player.getBoundingBox().inflate(8), item -> true).stream().map(item -> item.getItem() + " at " + item.position()).toList());
				case Outcome.EquipItem equip -> check(player.getItemBySlot(EquipmentSlot.valueOf(equip.slot().toUpperCase(Locale.ROOT)))
						.is(BuiltInRegistries.ITEM.getValue(equip.item())),
					word.id() + "'s " + equip.item() + " is not being worn");
				case Outcome.Effect effect -> check(player.hasEffect(BuiltInRegistries.MOB_EFFECT.get(effect.effect()).orElseThrow()),
					word.id() + "'s " + effect.effect() + " never landed");
				case Outcome.PlaceBlock place -> check(room.placed().stream().anyMatch(pos ->
						player.level().getBlockState(pos).is(BuiltInRegistries.BLOCK.getValue(place.block()))),
					word.id() + "'s " + place.block() + " never landed: " + room.placed());
				case Outcome.SpawnEntity spawn -> check(room.creatures().stream().anyMatch(entity ->
						entity.getType() == BuiltInRegistries.ENTITY_TYPE.getValue(spawn.entity())),
					word.id() + "'s " + spawn.entity() + " is not in the room");
				case Outcome.Sky sky -> {
					if (!sky.flicker()) check(justfatlard.phonics.classroom.Sky.timeOf(player) == sky.to().ticks,
						word.id() + "'s sky did not turn to " + sky.time() + ": " + justfatlard.phonics.classroom.Sky.timeOf(player));
				}
				case Outcome.Weather weather -> check(weather.weather().equals("cloud") ? justfatlard.phonics.classroom.Sky.clouds() > 0
						: justfatlard.phonics.classroom.Sky.weatherOf(player) == justfatlard.phonics.classroom.Sky.weatherNamed(weather.weather()),
					word.id() + "'s " + weather.weather() + " never came: " + justfatlard.phonics.classroom.Sky.weatherOf(player));
				case Outcome.Character character -> {
					Entity who = room.creatures().stream().filter(entity -> entity.getType() == BuiltInRegistries.ENTITY_TYPE.getValue(character.entity()))
						.reduce((a, b) -> b).orElse(null);
					check(who != null, word.id() + "'s " + character.entity() + " is not in the room");
					character.face().ifPresent(face -> check(justfatlard.phonics.feel.Feelings.of(who) == justfatlard.phonics.feel.Feelings.Face.named(face),
						word.id() + "'s " + character.entity() + " is not feeling " + face));
				}
				case Outcome.Dark dark -> check(dark.flicker() || justfatlard.phonics.classroom.Sky.isDark(player), word.id() + " did not put the lights out");
				default -> { }
			}
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "phonics-justfatlard", session -> {
			session.command("/phonics");
			session.waitTicks(40);

			List<Word> words = new ArrayList<>();
			session.onServer(server -> {
				words.addAll(Words.all());
				check(words.size() >= 40, "only " + words.size() + " words loaded");
				for (Word word : words) {
					check(word.letters() != null, word.id() + " cannot be built from single letter tiles");
					check(word.outcome().usable(), word.id() + "'s outcome names something missing, or something harmful");
					// Shipped words each have their own; the test pack's wake leans on the fallback.
					if (word.id().getNamespace().equals(justfatlard.phonics.Phonics.MOD_ID)) {
						check(word.wrong().isPresent(), word.id() + " has no wrong outcome of its own");
						check(word.wrong().get().usable(), word.id() + "'s wrong outcome names something missing, or something harmful");
					} else {
						check(word.wrongOutcome() == word.outcome(), word.id() + " has no wrong outcome, and does not fall back to its own");
					}
				}
			});

			// Four loose tiles, before any word has changed the child's size or the room.
			if (session.full()) {
				session.onServer(server -> {
					Word cat = words.stream().filter(word -> word.text().equals("cat")).findFirst().orElseThrow();
					check(Rooms.startRound(session.player(), cat), "no round for cat");
					check(Rooms.of(session.player()).puzzle().shelf().size() >= 4, "the -at round does not have four tiles or more");
				});
				session.waitTicks(10);
				shoot(session, "phonics-four-tiles");
			}

			List<BlockPos> before = new ArrayList<>();
			for (Word word : words) {
				String text = word.text();
				session.onServer(server -> {
					ServerPlayer player = session.player();
					check(Rooms.startRound(player, word), "no round could be built for " + word.id());
					Room room = Rooms.of(player);
					check(room.puzzle().shelf().size() <= justfatlard.phonics.puzzle.Puzzle.MAX_WHOLE_TILES, word.id() + " has " + room.puzzle().shelf().size() + " loose tiles");
					// Its picture over the board: an item, or its picture card.
					String card = justfatlard.phonics.puzzle.Words.pictureCard(word);
					if (card != null) {
						check(card.equals(room.pictureCard()), word.id() + "'s picture card is not over the board: " + room.pictureCard());
					} else {
						check(!word.picture().isEmpty(), word.id() + " has nothing to picture: give it an icon or a picture card");
						check(room.picture() instanceof net.minecraft.world.entity.Display.ItemDisplay display
								&& display.getItemStack().is(word.picture().getItem()),
							word.id() + "'s picture is not over the board: " + room.picture());
					}
					for (BlockPos pos : before) check(player.level().getBlockState(pos).isAir(), "a block from the last word survived the round");
					Vec3 spawn = room.spawn();
					player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.of(), Room.FACING_YAW, 0, true);
					player.setDeltaMovement(Vec3.ZERO);
					// An empty pack for each word: over a hundred words' things would fill it, and a full pack picks nothing up.
					player.getInventory().clearContent();
				});
				boolean pictured = session.full() && PICTURED.contains(text);
				if (pictured && !text.equals("fun")) {
					// Looking down at the floor in front, where beds go and creatures arrive.
					session.onServer(server -> {
						ServerPlayer player = session.player();
						player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), Room.FACING_YAW - 20, 35, true);
					});
				}
				if (pictured && text.equals("big")) {
					session.context().runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				}
				if (session.full() && FLIGHT.contains(text)) {
					// Looking at the button and the way from it, to catch the word in the air.
					session.onServer(server -> {
						ServerPlayer player = session.player();
						Room room = Rooms.of(player);
						Vec3 eye = player.getEyePosition();
						Vec3 aim = room.mouth().lerp(player.position(), 0.35).subtract(eye);
						float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
						float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
						player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), yaw, pitch, true);
					});
					session.waitTicks(2);
				}
				int[] sentBefore = new int[1];
				session.onServer(server -> {
					Room room = Rooms.of(session.player());
					sentBefore[0] = room.dispensed().size();
					// Room in the pack for what it gives: every word that gives something has filled it by now.
					var pack = session.player().getInventory();
					int free = 0;
					for (int slot = 0; slot < pack.getNonEquipmentItems().size(); slot++) if (pack.getNonEquipmentItems().get(slot).isEmpty()) free++;
					if (free < 8) pack.getNonEquipmentItems().clear();
					Rooms.runOutcome(session.player(), word);
					// It comes out of the button: whatever went first started at the button's face.
					List<justfatlard.phonics.puzzle.Dispenser.Dispensed> sent = room.dispensed();
					check(sent.size() > sentBefore[0] || sentBefore[0] >= 32, word.id() + " did not come out of the button");
					justfatlard.phonics.puzzle.Dispenser.Dispensed first = sent.get(Math.min(sentBefore[0], sent.size() - 1));
					check(first.from().distanceTo(room.mouth()) <= 1.5,
						word.id() + "'s " + first.what() + " started " + first.from().distanceTo(room.mouth()) + " from the button");
				});
				if (session.full() && FLIGHT.contains(text)) {
					session.waitTicks(5);
					shoot(session, "phonics-flight-" + text);
				}
				session.waitTicks(text.equals("fun") ? 30 : 40);
				if (pictured) shoot(session, "phonics-outcome-" + text);
				// And ends where it always did.
				session.onServer(server -> landed(session.player(), word));
				if (pictured && text.equals("big")) {
					session.context().runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				}
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					AABB box = new AABB(room.origin()).inflate(12);
					for (Entity entity : player.level().getEntities((Entity) null, box, e -> true)) {
						boolean hostile = entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
						check(!hostile, word.id() + " left something hostile in the room: " + entity);
						check(!(entity.getType() == EntityTypes.POLAR_BEAR && !((LivingEntity) entity).isBaby()),
							word.id() + " brought a grown polar bear");
						if (entity.entityTags().contains("phonics-justfatlard.spawned")) {
							check(entity.isInvulnerable(), word.id() + "'s creature can be hurt: " + entity);
						}
					}
					check(player.getHealth() >= player.getMaxHealth(), word.id() + " hurt the child: " + player.getHealth());
				});

				// Its wrong version, as when built but not asked for: over quickly, and nothing of it left.
				if (word.wrong().isEmpty()) {
					session.onServer(server -> {
						before.clear();
						before.addAll(Rooms.of(session.player()).placed());
					});
					continue;
				}
				Leftovers[] was = new Leftovers[1];
				boolean wrongPictured = session.full() && WRONG_PICTURED.contains(text);
				if (wrongPictured) {
					session.onServer(server -> {
						ServerPlayer player = session.player();
						Vec3 spawn = Rooms.of(player).spawn();
						player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.of(), Room.FACING_YAW, text.equals("log") ? 30 : 0, true);
					});
					if (text.equals("hat")) session.context().runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
					session.waitTicks(2);
				}
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					was[0] = Leftovers.of(player, room);
					int sent = room.dispensed().size();
					Rooms.runWrongOutcome(player, word);
					check(room.dispensed().size() > sent || sent >= 32,
						word.id() + "'s wrong outcome did not come out of the button");
				});
				if (wrongPictured) {
					session.waitTicks(text.equals("log") ? 22 : 16);
					shoot(session, "phonics-wrong-" + text);
					session.context().runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
					session.waitTicks(text.equals("log") ? 68 : 74);
				} else {
					// Every wrong outcome has begun by 35 ticks; from there, on as soon as it has all gone,
					// and never past the 90 it may take.
					session.waitTicks(35);
					boolean[] gone = new boolean[1];
					for (int waited = 35; waited < 90 && !gone[0]; waited += 5) {
						session.onServer(server -> gone[0] = Leftovers.of(session.player(), Rooms.of(session.player())).within(was[0], session.player()));
						if (!gone[0]) session.waitTicks(5);
					}
				}
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					Leftovers now = Leftovers.of(player, room);
					String wrong = word.id() + "'s wrong outcome ";
					check(was[0].placed().containsAll(now.placed()), wrong + "left a block down: " + now.placed());
					check(was[0].creatures().containsAll(now.creatures()), wrong + "left a creature about");
					check(now.loose() <= was[0].loose(), wrong + "left something lying in the room");
					check(now.displays() <= was[0].displays(), wrong + "left something showing in the room");
					check(now.snowballs() == 0, wrong + "left a snowball in the air");
					check(now.items() == was[0].items(), wrong + "changed what is in the pack: " + was[0].items() + " -> " + now.items());
					check(was[0].effects().containsAll(now.effects()), wrong + "left an effect on the child: " + now.effects());
					check(player.getAttribute(Attributes.SCALE).getValue() == 1.0, wrong + "left the child the wrong size");
					check(player.getHealth() >= player.getMaxHealth(), wrong + "hurt the child: " + player.getHealth());
					check(player.position().distanceTo(Vec3.atCenterOf(room.origin())) < 10, wrong + "sent the child out of the room");
					for (Entity entity : player.level().getEntities((Entity) null, new AABB(room.origin()).inflate(12), e -> true)) {
						check(!(entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER),
							wrong + "left something hostile in the room: " + entity);
					}
					before.clear();
					before.addAll(room.placed());
				});
			}

			session.command("/phonics leave");
			Waits.until(session, 300, "never went home", () -> Rooms.of(session.player()) == null);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				check(player.getAttribute(Attributes.SCALE).getValue() == 1.0, "the child came home the wrong size");
				var classroom = server.getLevel(justfatlard.phonics.classroom.Places.CLASSROOM);
				for (BlockPos pos : before) check(classroom.getBlockState(pos).isAir(), "a word's block outlived the visit at " + pos);
			});
		});
	}

	/** What a room and child have about them, to compare before and after. */
	private record Leftovers(Set<BlockPos> placed, Set<java.util.UUID> creatures, int loose, int displays, int snowballs, int items,
			Set<Object> effects) {
		/** Nothing more than there was: no block, creature, prop, item, snowball, pack item, effect or size left over. */
		boolean within(Leftovers was, ServerPlayer player) {
			return was.placed().containsAll(placed) && was.creatures().containsAll(creatures) && loose <= was.loose()
				&& displays <= was.displays() && snowballs == 0 && items == was.items() && was.effects().containsAll(effects)
				&& player.getAttribute(Attributes.SCALE).getValue() == 1.0;
		}

		static Leftovers of(ServerPlayer player, Room room) {
			AABB box = new AABB(room.origin()).inflate(12);
			var level = player.level();
			int items = 0;
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) items += player.getInventory().getItem(slot).getCount();
			Set<Object> effects = new java.util.HashSet<>();
			for (var effect : player.getActiveEffects()) effects.add(effect.getEffect());
			return new Leftovers(Set.copyOf(room.placed()),
				room.creatures().stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet()),
				level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, e -> true).size(),
				level.getEntitiesOfClass(net.minecraft.world.entity.Display.class, box, e -> true).size(),
				level.getEntitiesOfClass(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball.class, box, e -> true).size(),
				items, effects);
		}
	}

	private static void shoot(Smoke.Session session, String name) {
		session.context().takeScreenshot(TestScreenshotOptions.of(name.toLowerCase(Locale.ROOT)).disableCounterPrefix());
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
