package justfatlard.learning_blocks.sound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Every sound the mod makes, through one place, by one kind of name.
 *
 * <p>Three kinds, and nothing else: {@code phoneme/<x>} for the sound a letter makes, {@code
 * word/<w>} for a whole word said aloud, and {@code phrase/<p>} for what the room says to a child
 * ("well done", "try again"). Every caller names one of those and has no idea what plays.
 *
 * <p>That is the seam real recordings drop into. A file at {@code
 * assets/learning-blocks-justfatlard/sounds/<key>.ogg} - {@code sounds/phoneme/c.ogg}, {@code
 * sounds/word/cat.ogg} - is found at start-up, a {@code sounds.json} naming each one is written for
 * it and handed to Pandorical beside the file itself, and from then on that key plays the recording.
 * A mod that ships its own {@code sounds.json} keeps it, and whatever it names counts as recorded.
 * Nothing is registered in {@code BuiltInRegistries.SOUND_EVENT}: a recording is played as a direct
 * holder by id, which the client resolves from the synced pack, so a vanilla registry sync never
 * learns of it.
 *
 * <p>Any key without a recording plays a placeholder on the note block, so the room works and can
 * be heard before a single line is recorded: consonants on the harp, vowels on the bell, each letter
 * its own pitch, so a child can already tell them apart.
 */
public final class Sounds {
	private Sounds() {}

	private static final String SOUNDS_DIR = "assets/" + LearningBlocks.MOD_ID + "/sounds";
	private static final String SOUNDS_JSON = "assets/" + LearningBlocks.MOD_ID + "/sounds.json";

	private static Set<String> recorded = Set.of();

	/**
	 * Find the recordings, and write and register the {@code sounds.json} that names them. Call
	 * after {@code registerModAssets}, so a generated file is never mistaken for a shipped one.
	 */
	public static void init() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(LearningBlocks.MOD_ID).orElse(null);
		if (mod == null) return;

		Set<String> found = new TreeSet<>();
		JsonObject shipped = null;
		for (Path root : mod.getRootPaths()) {
			Path shippedJson = root.resolve(SOUNDS_JSON);
			if (Files.isRegularFile(shippedJson)) {
				try {
					shipped = JsonParser.parseString(Files.readString(shippedJson, StandardCharsets.UTF_8)).getAsJsonObject();
				} catch (IOException | RuntimeException e) {
					LearningBlocks.LOGGER.warn("[{}] Could not read {}: {}", LearningBlocks.MOD_ID, SOUNDS_JSON, e.getMessage());
				}
			}
			Path dir = root.resolve(SOUNDS_DIR);
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> walk = Files.walk(dir)) {
				walk.filter(Files::isRegularFile)
					.map(file -> dir.relativize(file).toString().replace('\\', '/'))
					.filter(path -> path.endsWith(".ogg"))
					.map(path -> path.substring(0, path.length() - ".ogg".length()))
					.forEach(found::add);
			} catch (IOException e) {
				LearningBlocks.LOGGER.warn("[{}] Could not look through {}: {}", LearningBlocks.MOD_ID, SOUNDS_DIR, e.getMessage());
			}
		}

		if (shipped != null) {
			recorded = Set.copyOf(shipped.keySet());
		} else if (!found.isEmpty()) {
			JsonObject generated = new JsonObject();
			for (String key : found) {
				JsonArray files = new JsonArray();
				files.add(LearningBlocks.MOD_ID + ":" + key);
				JsonObject entry = new JsonObject();
				entry.add("sounds", files);
				generated.add(key, entry);
			}
			PandoricalApi.content().registerAsset(SOUNDS_JSON, generated.toString().getBytes(StandardCharsets.UTF_8));
			recorded = Set.copyOf(found);
		}
		findVariants();
		LearningBlocks.LOGGER.info("[{}] {} recorded sounds; everything else plays a placeholder", LearningBlocks.MOD_ID, recorded.size());
	}

	private static final java.util.regex.Pattern VARIANT = java.util.regex.Pattern.compile("^(phrase/.+)_v(\\d+)$");
	private static Map<String, List<String>> variants = Map.of();
	/** What is left to say of each line's variants, per player: shuffled, and dealt out one at a time. */
	private static final Map<UUID, Map<String, java.util.Deque<String>>> bags = new java.util.HashMap<>();
	private static final Map<UUID, Map<String, String>> lastSaid = new java.util.HashMap<>();

	private static void findVariants() {
		Map<String, List<String>> found = new java.util.TreeMap<>();
		for (String key : recorded) {
			java.util.regex.Matcher matcher = VARIANT.matcher(key);
			if (matcher.matches()) found.computeIfAbsent(matcher.group(1), base -> new ArrayList<>()).add(key);
		}
		Map<String, List<String>> all = new java.util.HashMap<>();
		for (Map.Entry<String, List<String>> entry : found.entrySet()) {
			List<String> options = new ArrayList<>();
			options.add(entry.getKey());
			entry.getValue().sort(java.util.Comparator.comparingInt(key -> Integer.parseInt(key.substring(key.lastIndexOf("_v") + 2))));
			options.addAll(entry.getValue());
			all.put(entry.getKey(), List.copyOf(options));
		}
		variants = Map.copyOf(all);
	}

	public static List<String> variantsOf(String key) {
		return variants.getOrDefault(key, List.of(key));
	}

	/**
	 * The way this player hears the line this time: any of it and its variants, dealt from a
	 * shuffled bag so every one comes round, and never the one they heard last time.
	 */
	public static synchronized String variant(ServerPlayer player, String key) {
		List<String> options = variants.get(key);
		if (options == null || options.size() < 2) return key;
		java.util.Deque<String> bag = bags.computeIfAbsent(player.getUUID(), id -> new java.util.HashMap<>())
			.computeIfAbsent(key, k -> new java.util.ArrayDeque<>());
		Map<String, String> last = lastSaid.computeIfAbsent(player.getUUID(), id -> new java.util.HashMap<>());
		if (bag.isEmpty()) {
			List<String> shuffled = new ArrayList<>(options);
			java.util.Collections.shuffle(shuffled, new java.util.Random(player.getRandom().nextLong()));
			// A fresh bag never starts with the one that ended the last.
			if (shuffled.getFirst().equals(last.get(key))) java.util.Collections.swap(shuffled, 0, shuffled.size() - 1);
			bag.addAll(shuffled);
		}
		String chosen = bag.removeFirst();
		last.put(key, chosen);
		return chosen;
	}

	public static synchronized void forget(UUID player) {
		bags.remove(player);
		lastSaid.remove(player);
	}

	public static synchronized void forgetVariants() {
		bags.clear();
		lastSaid.clear();
	}

	public static Set<String> recorded() {
		return recorded;
	}

	public static boolean isRecorded(String key) {
		return recorded.contains(key);
	}

	public static void phoneme(ServerPlayer player, String grapheme) {
		play(player, "phoneme/" + grapheme);
	}

	public static void word(ServerPlayer player, String word) {
		play(player, "word/" + word);
	}

	public static void phrase(ServerPlayer player, String phrase) {
		play(player, "phrase/" + phrase);
	}

	/**
	 * How long this clip lasts, in ticks, rounded up: from its caption file, which is where the
	 * length of every recording is written down. A clip without one is given a length by kind,
	 * which is about right for the placeholders.
	 */
	public static int durationTicks(String key) {
		Captions.Caption caption = Captions.get(key);
		if (caption != null) return Math.max(1, (int) Math.ceil(caption.duration() * 20.0F));
		if (key.startsWith("phoneme/")) return 10;
		if (key.startsWith("word/")) return 16;
		return 24;
	}

	/** Something that was played, for a test to look back at: where from, for a block's; and to whom, for the room's voice. */
	public record Played(String key, @Nullable BlockPos at, long gameTime, java.util.@Nullable UUID to) {}

	private static final int RECENT = 64;
	private static final Deque<Played> recent = new ArrayDeque<>();

	/** What has been played lately, oldest first: the hook a test listens through. */
	public static synchronized List<Played> recent() {
		return List.copyOf(recent);
	}

	private static void remember(String key, @Nullable BlockPos at, long gameTime) {
		remember(key, at, gameTime, null);
	}

	private static synchronized void remember(String key, @Nullable BlockPos at, long gameTime, java.util.@Nullable UUID to) {
		recent.addLast(new Played(key, at, gameTime, to));
		while (recent.size() > RECENT) recent.removeFirst();
	}

	/** The room's voice, to this child only and as if beside them: a room is theirs. */
	public static void play(ServerPlayer player, String key) {
		Resolved sound = resolve(key);
		remember(key, null, player.level().getGameTime(), player.getUUID());
		player.connection.send(new ClientboundSoundPacket(sound.sound(), SoundSource.VOICE,
			player.getX(), player.getEyeY(), player.getZ(), 1.0F, sound.pitch(), player.getRandom().nextLong()));
	}

	/**
	 * A sound from a block, heard by everybody near it and from where it is: a tile being placed,
	 * broken, punched or picked up.
	 */
	public static void playAt(Level level, BlockPos pos, String key) {
		if (level.isClientSide()) return;
		Resolved sound = resolve(key);
		remember(key, pos.immutable(), level.getGameTime());
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound.sound(), SoundSource.VOICE,
			1.0F, sound.pitch());
	}

	public static final String PLUSH_SQUISH = "sfx/plush_squish";

	/**
	 * A sound effect from a block (an {@code sfx/} key), heard by everybody near it, a little higher or
	 * lower each time (0.95 to 1.05) so it never sounds the same twice. No caption.
	 */
	public static void effectAt(Level level, BlockPos pos, String key) {
		if (level.isClientSide()) return;
		Resolved sound = resolve(key);
		remember(key, pos.immutable(), level.getGameTime());
		float pitch = sound.pitch() * (0.95F + level.getRandom().nextFloat() * 0.1F);
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound.sound(), SoundSource.BLOCKS, 1.0F, pitch);
	}

	private record Resolved(Holder<SoundEvent> sound, float pitch) {}

	private static Resolved resolve(String key) {
		if (isRecorded(key)) return new Resolved(Holder.direct(SoundEvent.createVariableRangeEvent(LearningBlocks.id(key))), 1.0F);
		Placeholder stand = placeholder(key);
		return new Resolved(stand.sound(), stand.pitch());
	}

	private record Placeholder(Holder<SoundEvent> sound, float pitch) {}

	private static Placeholder placeholder(String key) {
		int slash = key.indexOf('/');
		String kind = slash < 0 ? key : key.substring(0, slash);
		String name = slash < 0 ? "" : key.substring(slash + 1);
		return switch (kind) {
			case "phoneme" -> phonemePlaceholder(name);
			case "word" -> new Placeholder(SoundEvents.NOTE_BLOCK_CHIME, note(Math.floorMod(name.hashCode(), 25)));
			case "phrase" -> switch (name) {
				case "well_done", "well_done_reading" -> new Placeholder(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), 1.0F);
				case "try_again" -> new Placeholder(SoundEvents.NOTE_BLOCK_BASS, note(6));
				default -> new Placeholder(SoundEvents.NOTE_BLOCK_PLING, note(12));
			};
			default -> new Placeholder(SoundEvents.NOTE_BLOCK_HAT, note(12));
		};
	}

	/** Vowels ring, consonants pluck, and each letter has a pitch of its own across two octaves. */
	private static Placeholder phonemePlaceholder(String grapheme) {
		char first = grapheme.isEmpty() ? 'a' : Character.toLowerCase(grapheme.charAt(0));
		int index = Math.floorMod(first - 'a', 26);
		boolean vowel = "aeiou".indexOf(first) >= 0;
		return new Placeholder(vowel ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_HARP, note(index % 25));
	}

	/** A note block's pitch for a note from 0 to 24, as the game's own note block works it out. */
	private static float note(int note) {
		return (float) Math.pow(2.0, (note - 12) / 12.0);
	}
}
