package justfatlard.learning_blocks.classroom;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Each child's own sky in the classroom: day, dusk, night with the moon and stars, dawn; and their
 * own weather: rain, a drizzle, snow, a storm. Only theirs: another child in another room keeps a
 * clear noon.
 *
 * <p>The classroom keeps time by a clock of its own ({@code learning-blocks-justfatlard:classroom}), its
 * sky drawn from timelines on that clock alone. Every time packet a classroom player is sent has that
 * clock rewritten to their own time, frozen there; nobody else's packet is touched. The weather is
 * the game's own rain, sent to the one player: the classroom never has weather of its own (see
 * {@code LevelMixin}). A storm's thunder is a sound and a flash, a lightning bolt only that player
 * is told about, far off, that never exists in the world. Snow is the room's biome turned cold for
 * a while. Everything goes back to a clear noon at the next round and on the way out.
 */
public final class Sky {
	private Sky() {}

	public static final ResourceKey<WorldClock> CLOCK = ResourceKey.create(Registries.WORLD_CLOCK, LearningBlocks.id("classroom"));
	public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, LearningBlocks.id("classroom"));
	public static final ResourceKey<Biome> SNOWY = ResourceKey.create(Registries.BIOME, LearningBlocks.id("classroom_snowy"));

	/** Times of day on the classroom clock, in ticks. */
	public enum Time {
		DAY(6000), DAWN(23300), DUSK(12700), NIGHT(18000);

		public final long ticks;

		Time(long ticks) {
			this.ticks = ticks;
		}
	}

	public enum Weather { CLEAR, DRIZZLE, RAIN, SNOW, STORM }

	/** A child's sky: where it stands, or (while {@code ticks} last from {@code started}) where it is gliding to. */
	private record Clock(long from, long to, long started, int ticks) {
		long now(long gameTime) {
			if (ticks <= 0 || gameTime - started >= ticks) return to;
			return from + (to - from) * Math.max(0, gameTime - started) / ticks;
		}

		float rate(long gameTime) {
			return ticks <= 0 || gameTime - started >= ticks ? 0.0F : (to - from) / (float) ticks;
		}
	}

	private static final Map<UUID, Clock> times = new HashMap<>();
	private static final Map<UUID, Weather> weathers = new HashMap<>();
	/** Rain and thunder as last sent, eased toward the weather, nought to one. */
	private static final Map<UUID, float[]> shown = new HashMap<>();

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(Sky::pauseClassroomClock);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Sky::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Drifting one : clouds) one.display().discard();
			clouds.clear();
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(CLOUD) && clouds.stream().noneMatch(one -> one.display() == entity)) entity.discard();
		});
	}

	private static @Nullable Holder<WorldClock> clock(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(CLOCK).map(holder -> (Holder<WorldClock>) holder).orElse(null);
	}

	/** The classroom's own clock stands at noon on the server: nothing there runs by it but the sky, which is each child's own. */
	private static void pauseClassroomClock(MinecraftServer server) {
		Holder<WorldClock> clock = clock(server);
		if (clock == null) {
			LearningBlocks.LOGGER.warn("[{}] No classroom clock: the classroom's sky will not change", LearningBlocks.MOD_ID);
			return;
		}
		server.clockManager().setTotalTicks(clock, Time.DAY.ticks);
		server.clockManager().setPaused(clock, true);
	}

	/** This child's time in the classroom, in ticks, where it is headed: noon unless a word says otherwise. */
	public static long timeOf(ServerPlayer player) {
		Clock clock = times.get(player.getUUID());
		return clock == null ? Time.DAY.ticks : Math.floorMod(clock.to(), 24000L);
	}

	public static Weather weatherOf(ServerPlayer player) {
		return weathers.getOrDefault(player.getUUID(), Weather.CLEAR);
	}

	/** This child's sky turns to this time of day at once, theirs only. */
	public static void setTime(ServerPlayer player, long ticks) {
		glide(player, ticks, 0);
	}

	/**
	 * This child's sky glides on to this time of day over {@code ticks}, always forward (night
	 * comes on through dusk, and the sun comes up through dawn). Their game is told once where it
	 * starts and how fast it goes, and draws the rest itself; then told it has stopped.
	 */
	public static void glide(ServerPlayer player, long to, int ticks) {
		long gameTime = player.level().getGameTime();
		long from = currentTime(player);
		long ahead = Math.floorMod(to - from, 24000L);
		times.put(player.getUUID(), new Clock(from, from + ahead, gameTime, ahead == 0 ? 0 : ticks));
		send(player);
		if (ticks > 0 && ahead != 0) justfatlard.learning_blocks.Later.after(player, ticks + 1, Sky::send);
	}

	private static long currentTime(ServerPlayer player) {
		Clock clock = times.get(player.getUUID());
		return clock == null ? Time.DAY.ticks : clock.now(player.level().getGameTime());
	}

	private static ClockNetworkState stateOf(ServerPlayer player) {
		Clock clock = times.get(player.getUUID());
		long gameTime = player.level().getGameTime();
		return clock == null ? new ClockNetworkState(Time.DAY.ticks, 0.0F, 0.0F) : new ClockNetworkState(clock.now(gameTime), 0.0F, clock.rate(gameTime));
	}

	private static void send(ServerPlayer player) {
		Holder<WorldClock> clock = clock(player.level().getServer());
		if (clock == null || !Places.isClassroom(player.level())) return;
		player.connection.send(new ClientboundSetTimePacket(player.level().getGameTime(), Map.of(clock, stateOf(player))));
	}

	/**
	 * A time packet on its way to a player: in the classroom, the classroom's clock is theirs,
	 * frozen. Anyone anywhere else is sent it as it was.
	 */
	public static ClientboundSetTimePacket rewrite(ServerPlayer player, ClientboundSetTimePacket packet) {
		if (!Places.isClassroom(player.level())) return packet;
		Holder<WorldClock> clock = clock(player.level().getServer());
		if (clock == null) return packet;
		Map<Holder<WorldClock>, ClockNetworkState> updates = new HashMap<>(packet.clockUpdates());
		updates.put(clock, stateOf(player));
		return new ClientboundSetTimePacket(packet.gameTime(), updates);
	}

	/** A blink of another time of day and back, twice: a word built by mistake. */
	public static void flicker(ServerPlayer player, long to) {
		long was = timeOf(player);
		boolean had = times.containsKey(player.getUUID());
		for (int i = 0; i < 4; i++) {
			boolean blink = i % 2 == 0;
			justfatlard.learning_blocks.Later.after(player, 1 + i * 5, p -> {
				if (blink) setTime(p, to);
				else if (had) setTime(p, was);
				else {
					times.remove(p.getUUID());
					send(p);
				}
			});
		}
	}

	public static @Nullable Weather weatherNamed(String name) {
		for (Weather one : Weather.values()) if (one.name().toLowerCase(java.util.Locale.ROOT).equals(name)) return one;
		return null;
	}

	static final String CLOUD = LearningBlocks.MOD_ID + ".cloud";
	private record Drifting(net.minecraft.world.entity.Display display, double[] puff, long started, long until) {}
	private static final java.util.List<Drifting> clouds = new java.util.ArrayList<>();

	/**
	 * A fat white cloud drifting over a room, high over the walls, from one side to the other, and
	 * gone: block displays, told once where to drift and how long it takes.
	 */
	public static void cloud(ServerLevel level, Vec3 floor, int lasts) {
		double[][] puffs = {{0, 0, 0, 2.2, 1.0, 1.6}, {1.2, 0.5, 0.2, 1.8, 1.0, 1.4}, {-1.0, 0.3, 0.3, 1.6, 0.9, 1.2}, {0.4, 0.9, 0.1, 1.4, 0.8, 1.2},
			{2.2, 0.1, 0.1, 1.2, 0.8, 1.0}};
		long now = level.getGameTime();
		for (double[] puff : puffs) {
			net.minecraft.world.entity.Display.BlockDisplay piece = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (piece == null) continue;
			piece.snapTo(floor.x - 7, floor.y + 8.5, floor.z - 1.5, 0, 0);
			piece.setBlockState(net.minecraft.world.level.block.Blocks.WOOL.white().defaultBlockState());
			piece.setTransformation(cloudPose(puff, 0));
			piece.addTag(CLOUD);
			clouds.add(new Drifting(piece, puff, now, now + lasts));
			level.addFreshEntity(piece);
		}
	}

	private static com.mojang.math.Transformation cloudPose(double[] puff, double along) {
		return new com.mojang.math.Transformation(new org.joml.Vector3f((float) (puff[0] + along - puff[3] / 2), (float) puff[1], (float) (puff[2] - puff[5] / 2)),
			null, new org.joml.Vector3f((float) puff[3], (float) puff[4], (float) puff[5]), null);
	}

	/** Clouds drifting over any room. For tests. */
	public static int clouds() {
		return (int) clouds.stream().filter(one -> one.display().isAlive()).count();
	}

	/** This child's weather turns to this, theirs only; snow turns their room's air cold. */
	public static void setWeather(ServerPlayer player, Weather weather) {
		Weather before = weathers.put(player.getUUID(), weather);
		if (!Places.isClassroom(player.level())) return;
		if ((weather == Weather.SNOW) != (before == Weather.SNOW)) chill(player, weather == Weather.SNOW);
		if (weather != Weather.CLEAR && (before == null || before == Weather.CLEAR)) {
			player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
		}
	}

	/** Back to a clear noon, the sun coming up quickly if it was dark: the next round. */
	public static void reset(ServerPlayer player) {
		if (timeOf(player) != Time.DAY.ticks) glide(player, Time.DAY.ticks, 30);
		if (weathers.getOrDefault(player.getUUID(), Weather.CLEAR) != Weather.CLEAR) setWeather(player, Weather.CLEAR);
		light(player);
	}

	/** Gone from the classroom: what they are sent outside is the world's own sky. */
	public static void forget(ServerPlayer player) {
		if (weathers.get(player.getUUID()) == Weather.SNOW) chill(player, false);
		if (!player.hasDisconnected()) light(player);
		darkUntil.remove(player.getUUID());
		times.remove(player.getUUID());
		weathers.remove(player.getUUID());
		shown.remove(player.getUUID());
	}

	static final String DARK_OVERLAY = LearningBlocks.MOD_ID + ":dark";
	private static final String DARK_COLOUR = "#E0000006";
	/** When each child's lights come back on, by game time. */
	private static final Map<UUID, Long> darkUntil = new HashMap<>();

	/**
	 * The lights go out for this child, theirs only, for {@code lasts} ticks: a dark shade over their
	 * whole screen (Pandorical draws it; without it they see nothing), and a click when it lifts.
	 */
	public static void dark(ServerPlayer player, int lasts) {
		if (!justfatlard.pandorical.api.PandoricalApi.isAvailable(player)) return;
		justfatlard.pandorical.api.Viewport window = justfatlard.pandorical.api.PandoricalApi.screens().viewport(player);
		justfatlard.pandorical.api.HudBuilder overlay = new justfatlard.pandorical.api.HudBuilder(DARK_OVERLAY).anchor("top_left").offset(0, 0);
		overlay.component(new justfatlard.pandorical.api.ComponentBuilder("shade", justfatlard.pandorical.api.ComponentType.PANEL)
			.bounds(-4, -4, window.width() + 8, window.height() + 8)
			.prop(justfatlard.pandorical.api.ComponentType.PROP_BACKGROUND, DARK_COLOUR)
			.prop(justfatlard.pandorical.api.ComponentType.PROP_BORDER, "flat")
			.prop(justfatlard.pandorical.api.ComponentType.PROP_BORDER_COLOR, DARK_COLOUR));
		justfatlard.pandorical.api.PandoricalApi.hud().show(player, overlay.build());
		darkUntil.put(player.getUUID(), player.level().getGameTime() + lasts);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	/** Whether this child's lights are out. For tests. */
	public static boolean isDark(ServerPlayer player) {
		return darkUntil.containsKey(player.getUUID());
	}

	private static void light(ServerPlayer player) {
		if (darkUntil.remove(player.getUUID()) == null) return;
		justfatlard.pandorical.api.PandoricalApi.hud().hide(player, DARK_OVERLAY);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	/** The overworld just started or stopped raining, and told everyone: this child is told their own weather again. */
	public static void overworldTurned(ServerPlayer player) {
		Weather weather = weatherOf(player);
		player.connection.send(new ClientboundGameEventPacket(weather == Weather.CLEAR ? ClientboundGameEventPacket.STOP_RAINING : ClientboundGameEventPacket.START_RAINING, 0.0F));
		float[] sky = shown.getOrDefault(player.getUUID(), new float[2]);
		player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, sky[0]));
		player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, sky[1]));
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, Long> entry : new HashMap<>(darkUntil).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) darkUntil.remove(entry.getKey());
			else if (player.level().getGameTime() >= entry.getValue()) light(player);
		}
		for (java.util.Iterator<Drifting> it = clouds.iterator(); it.hasNext(); ) {
			Drifting one = it.next();
			long now = one.display().level().getGameTime();
			if (one.display().isRemoved() || now >= one.until()) {
				one.display().discard();
				it.remove();
			} else if (now == one.started() + 2) {
				one.display().setTransformationInterpolationDelay(0);
				one.display().setTransformationInterpolationDuration((int) (one.until() - now));
				one.display().setTransformation(cloudPose(one.puff(), 14));
			}
		}
		if (server.getTickCount() % 4 != 0) return;
		for (Map.Entry<UUID, Weather> entry : new HashMap<>(weathers).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !Places.isClassroom(player.level())) continue;
			Weather weather = entry.getValue();
			float rain = switch (weather) {
				case CLEAR -> 0.0F;
				case DRIZZLE -> 0.35F;
				default -> 1.0F;
			};
			float thunder = weather == Weather.STORM ? 1.0F : 0.0F;
			float[] sky = shown.computeIfAbsent(entry.getKey(), id -> new float[2]);
			boolean changed = sky[0] != rain || sky[1] != thunder;
			sky[0] = ease(sky[0], rain);
			sky[1] = ease(sky[1], thunder);
			if (changed) {
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, sky[0]));
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, sky[1]));
			}
			if (weather == Weather.CLEAR && sky[0] == 0.0F) {
				// Stopped, then the levels: the game takes "stopped" to mean full rain, about to fade.
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F));
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F));
				weathers.remove(entry.getKey());
				shown.remove(entry.getKey());
			}
			if (weather == Weather.STORM && sky[1] > 0.8F && player.getRandom().nextInt(20) == 0) flash(player);
		}
	}

	private static float ease(float from, float to) {
		return Math.abs(to - from) <= 0.1F ? to : from + Math.signum(to - from) * 0.1F;
	}

	/** Thunder: a rumble, and a flash in this child's sky from a bolt far off that only they are told of. Nothing is struck. */
	public static void flash(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			Vec3 far = player.position().add(player.getRandom().nextInt(2) == 0 ? -80 : 80, 40, -90);
			bolt.snapTo(far.x, far.y, far.z);
			bolt.setVisualOnly(true);
			player.connection.send(new ClientboundAddEntityPacket(bolt, 0, BlockPos.containing(far)));
			int id = bolt.getId();
			justfatlard.learning_blocks.Later.after(player, 12, p -> p.connection.send(new ClientboundRemoveEntitiesPacket(id)));
		}
		player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.LIGHTNING_BOLT_THUNDER), SoundSource.WEATHER,
			player.getX(), player.getY() + 10, player.getZ(), 0.7F, 0.8F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
	}

	/** The room's air, cold for snow or mild for rain: its biome, round its middle. */
	private static void chill(ServerPlayer player, boolean cold) {
		justfatlard.learning_blocks.puzzle.Room room = justfatlard.learning_blocks.puzzle.Rooms.of(player);
		if (room == null) return;
		paint((ServerLevel) player.level(), room.origin(), cold ? SNOWY : BIOME);
	}

	/** A room's biome, all of it: the classroom's, which rains, or its cold one, which snows. */
	public static void paint(ServerLevel level, BlockPos origin, ResourceKey<Biome> biome) {
		var holder = level.registryAccess().lookupOrThrow(Registries.BIOME).get(biome);
		if (holder.isEmpty()) return;
		net.minecraft.server.commands.FillBiomeCommand.fill(level, origin.offset(-8, -1, -8), origin.offset(8, 8, 8), holder.get());
	}
}
