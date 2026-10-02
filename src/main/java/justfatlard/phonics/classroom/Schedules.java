package justfatlard.phonics.classroom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.phonics.Later;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.editor.Admins;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.reward.Progress;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Daily reading: for a child an operator has scheduled, a number of lessons to finish each day before
 * playing. Kept in {@code config/phonics/schedules.json}, by player UUID with the name alongside, so it
 * can be set while they are offline:
 *
 * <pre>{@code
 * { "timezone": "America/Los_Angeles", "enforce_on_rollover": false,
 *   "players": { "<uuid>": { "name": "Sam", "enabled": true, "lessons": 5, "weekdays": { "saturday": 0 } } } }
 * }</pre>
 *
 * <p>A scheduled child joining with fewer rounds solved today than the day asks for is sent into the
 * classroom by the ordinary {@code /phonics send} path, for what is left; rounds solved earlier the
 * same day, visits of their own included, count. A new day starting while they play is enforced at
 * their next login, or with {@code enforce_on_rollover}, after a minute's notice. The time zone is what
 * "a day" means here, and for the prize cap and the daily visit too.
 */
public final class Schedules {
	private Schedules() {}

	/** One child's schedule. */
	public record Entry(String name, boolean enabled, int lessons, Map<DayOfWeek, Integer> weekdays) {
		/** How many lessons this day of the week asks for. */
		int on(DayOfWeek day) {
			if (!enabled) return 0;
			return Math.max(0, weekdays.getOrDefault(day, lessons));
		}
	}

	private static volatile ZoneId zone = ZoneId.systemDefault();
	private static volatile boolean enforceOnRollover;
	private static final Map<UUID, Entry> entries = new LinkedHashMap<>();
	/** The day each online player was last checked against, for the rollover. */
	private static final Map<UUID, String> seenDay = new HashMap<>();
	/** Whose rollover send is on its way, so it is not queued twice. */
	private static final java.util.Set<UUID> pending = new java.util.HashSet<>();

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("phonics").resolve("schedules.json");
	}

	/** The time zone a day starts in. */
	public static ZoneId zone() {
		return zone;
	}

	/** For tests: a different time zone, as the file would set it. */
	public static void useZone(ZoneId zoneId) {
		zone = zoneId;
	}

	public static void useRollover(boolean enforce) {
		enforceOnRollover = enforce;
	}

	// --- The file ---

	/** Read the file; a missing file is no schedules, in the server's own time zone. */
	public static synchronized void load() {
		entries.clear();
		together.clear();
		zone = ZoneId.systemDefault();
		enforceOnRollover = false;
		Path file = file();
		if (!Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (root.has("timezone")) {
				try {
					zone = ZoneId.of(root.get("timezone").getAsString());
				} catch (RuntimeException e) {
					Phonics.LOGGER.warn("[{}] schedules.json: unknown timezone {}; using the server's", Phonics.MOD_ID, root.get("timezone"));
				}
			}
			enforceOnRollover = root.has("enforce_on_rollover") && root.get("enforce_on_rollover").getAsBoolean();
			JsonObject players = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
			for (Map.Entry<String, JsonElement> player : players.entrySet()) {
				try {
					JsonObject json = player.getValue().getAsJsonObject();
					Map<DayOfWeek, Integer> weekdays = new EnumMap<>(DayOfWeek.class);
					if (json.has("weekdays")) {
						for (Map.Entry<String, JsonElement> day : json.getAsJsonObject("weekdays").entrySet()) {
							DayOfWeek dow = weekday(day.getKey());
							if (dow != null) weekdays.put(dow, day.getValue().getAsInt());
						}
					}
					if (json.has("together_with")) together.put(UUID.fromString(player.getKey()), UUID.fromString(json.get("together_with").getAsString()));
					entries.put(UUID.fromString(player.getKey()), new Entry(json.has("name") ? json.get("name").getAsString() : "",
						!json.has("enabled") || json.get("enabled").getAsBoolean(), json.has("lessons") ? json.get("lessons").getAsInt() : 0, weekdays));
				} catch (RuntimeException e) {
					Phonics.LOGGER.warn("[{}] schedules.json: skipping {}: {}", Phonics.MOD_ID, player.getKey(), e.getMessage());
				}
			}
		} catch (IOException | RuntimeException e) {
			Phonics.LOGGER.error("[{}] Could not read {}; no daily schedules until it reads", Phonics.MOD_ID, file, e);
		}
	}

	private static synchronized void save() {
		JsonObject root = new JsonObject();
		root.addProperty("timezone", zone.getId());
		root.addProperty("enforce_on_rollover", enforceOnRollover);
		JsonObject players = new JsonObject();
		for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
			Entry schedule = entry.getValue();
			JsonObject json = new JsonObject();
			json.addProperty("name", schedule.name());
			json.addProperty("enabled", schedule.enabled());
			json.addProperty("lessons", schedule.lessons());
			if (!schedule.weekdays().isEmpty()) {
				JsonObject days = new JsonObject();
				for (Map.Entry<DayOfWeek, Integer> day : schedule.weekdays().entrySet()) days.addProperty(day.getKey().name().toLowerCase(Locale.ROOT), day.getValue());
				json.add("weekdays", days);
			}
			UUID with = together.get(entry.getKey());
			if (with != null) json.addProperty("together_with", with.toString());
			players.add(entry.getKey().toString(), json);
		}
		root.add("players", players);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
		} catch (IOException e) {
			Phonics.LOGGER.error("[{}] Could not write {}", Phonics.MOD_ID, file(), e);
		}
	}

	/** A weekday by its English name, or null. */
	public static @Nullable DayOfWeek weekday(String name) {
		try {
			return DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// --- Setting ---

	public static synchronized @Nullable Entry of(UUID player) {
		return entries.get(player);
	}

	/** Every schedule, for the editor. */
	public static synchronized Map<UUID, Entry> all() {
		return Map.copyOf(entries);
	}

	/** Who reads with whom at daily reading time, both ways round. */
	private static final Map<UUID, UUID> together = new HashMap<>();

	/** Who this child reads with at daily reading time, or null. */
	public static synchronized @Nullable UUID together(UUID player) {
		return together.get(player);
	}

	/** These two read together at daily reading time (both get a schedule entry if they had none); null partner for nobody. */
	public static synchronized void setTogether(UUID player, String name, @Nullable UUID other, String otherName) {
		UUID was = together.remove(player);
		if (was != null) together.remove(was, player);
		if (other != null) {
			together.put(player, other);
			together.put(other, player);
			entries.putIfAbsent(other, new Entry(otherName, false, 0, new EnumMap<>(DayOfWeek.class)));
		}
		entries.putIfAbsent(player, new Entry(name, false, 0, new EnumMap<>(DayOfWeek.class)));
		save();
	}

	/** A scheduled child joined: the name kept beside their UUID is the one they use now. */
	public static synchronized void refreshName(UUID player, String name) {
		Entry old = entries.get(player);
		if (old == null || old.name().equals(name)) return;
		entries.put(player, new Entry(name, old.enabled(), old.lessons(), old.weekdays()));
		save();
	}

	/** Lessons a day for this child; 0 turns the schedule off. */
	public static synchronized void set(UUID player, String name, int lessons) {
		Entry old = entries.get(player);
		entries.put(player, new Entry(name, lessons > 0, Math.max(0, lessons), old == null ? new EnumMap<>(DayOfWeek.class) : old.weekdays()));
		save();
	}

	/** A different number on one day of the week (0 for none that day); on, if it was off. */
	public static synchronized void setWeekday(UUID player, String name, DayOfWeek day, int lessons) {
		Entry old = entries.get(player);
		Map<DayOfWeek, Integer> weekdays = new EnumMap<>(DayOfWeek.class);
		if (old != null) weekdays.putAll(old.weekdays());
		weekdays.put(day, Math.max(0, lessons));
		entries.put(player, new Entry(name, true, old == null ? 0 : old.lessons(), weekdays));
		save();
	}

	/** Weekends free, or the same as other days: for the editor's toggle. */
	public static synchronized void setWeekendsOff(UUID player, String name, boolean off) {
		Entry old = entries.get(player);
		Map<DayOfWeek, Integer> weekdays = new EnumMap<>(DayOfWeek.class);
		if (old != null) weekdays.putAll(old.weekdays());
		if (off) {
			weekdays.put(DayOfWeek.SATURDAY, 0);
			weekdays.put(DayOfWeek.SUNDAY, 0);
		} else {
			weekdays.remove(DayOfWeek.SATURDAY);
			weekdays.remove(DayOfWeek.SUNDAY);
		}
		entries.put(player, new Entry(name, old != null && old.enabled(), old == null ? 0 : old.lessons(), weekdays));
		save();
	}

	public static boolean weekendsOff(UUID player) {
		Entry entry = of(player);
		return entry != null && Integer.valueOf(0).equals(entry.weekdays().get(DayOfWeek.SATURDAY))
			&& Integer.valueOf(0).equals(entry.weekdays().get(DayOfWeek.SUNDAY));
	}

	// --- Today ---

	/** Lessons this child's schedule asks for on this date; 0 if they have none. */
	public static int required(UUID player, LocalDate date) {
		Entry entry = of(player);
		return entry == null ? 0 : entry.on(date.getDayOfWeek());
	}

	public static int requiredToday(ServerPlayer player) {
		return required(player.getUUID(), Progress.date());
	}

	/** Lessons still to do today: the day's number, less the rounds already solved today. */
	public static int remaining(ServerPlayer player) {
		return Math.max(0, requiredToday(player) - Progress.of(player).doneToday());
	}

	/** Whether today's schedule asks this child for anything: the only thing that keeps an operator in. */
	public static boolean scheduledToday(ServerPlayer player) {
		return requiredToday(player) > 0;
	}

	/** Today's reading met, where a schedule asks for any: the door opens with "All done for today!". */
	public static boolean metToday(ServerPlayer player) {
		return scheduledToday(player) && remaining(player) == 0;
	}

	/**
	 * A scheduled child with reading left today, not in the classroom: sent in for what is left, by the
	 * ordinary door, and "Reading time first!" before the first word. True if they were.
	 */
	public static boolean enforce(ServerPlayer player) {
		seenDay.put(player.getUUID(), Progress.today());
		int left = remaining(player);
		if (left <= 0 || Visit.of(player) != null || player.isDeadOrDying()) return false;
		String refused = Travel.send(player, left);
		if (refused != null) {
			Phonics.LOGGER.warn("[{}] {}'s daily reading could not start: {}", Phonics.MOD_ID, player.getGameProfile().name(), refused);
			return false;
		}
		// Paired for daily reading: in with their partner, if they are already reading.
		UUID with = together(player.getUUID());
		ServerPlayer partner = with == null ? null : player.level().getServer().getPlayerList().getPlayer(with);
		Room theirs = partner == null ? null : Rooms.of(partner);
		if (theirs != null && !theirs.leaving()) {
			String moved = Travel.readWith(player, theirs);
			if (moved != null) Phonics.LOGGER.info("[{}] {} could not join {} for daily reading: {}", Phonics.MOD_ID,
				player.getGameProfile().name(), partner.getGameProfile().name(), moved);
		}
		Rooms.readingTime(player);
		Progress.of(player).lessonNote(player, "daily reading: " + left + " to go today");
		Phonics.LOGGER.info("[{}] {} has {} of today's {} lessons to read first", Phonics.MOD_ID, player.getGameProfile().name(), left,
			requiredToday(player));
		return true;
	}

	/**
	 * Now and then: a day that turned while a scheduled child plays. By default nothing happens until
	 * their next login; with {@code enforce_on_rollover}, a minute's notice, then in they go.
	 */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 0) return;
		String today = Progress.today();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String seen = seenDay.put(player.getUUID(), today);
			if (seen == null || seen.equals(today) || !enforceOnRollover) continue;
			if (remaining(player) <= 0 || Visit.of(player) != null || !pending.add(player.getUUID())) continue;
			player.sendSystemMessage(Component.literal("It's a new day! Reading time starts in a minute."));
			Later.after(player, 20 * 60, p -> {
				pending.remove(p.getUUID());
				enforce(p);
			});
		}
	}

	/** The day turned for this player: for tests of the rollover. */
	public static void rememberDay(ServerPlayer player, String day) {
		seenDay.put(player.getUUID(), day);
	}

	public static void forget(UUID player) {
		seenDay.remove(player);
		pending.remove(player);
	}

	/** Whether an operator is exempt from being kept in: always, unless scheduled today. */
	public static boolean mayLeave(ServerPlayer player) {
		return Admins.may(player) && !scheduledToday(player);
	}
}
