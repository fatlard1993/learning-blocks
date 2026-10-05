package justfatlard.learning_blocks.sound;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * What each clip says and when, loaded from data packs: the timing the karaoke captions follow,
 * and the length a narration waits before the next clip.
 *
 * <p>One file per clip, beside it by key: {@code data/<ns>/learning_captions/phoneme/c.json} for
 * {@code phoneme/c}. {@code generate_sounds.py} writes them from the same speech timestamps it cuts
 * the audio by, so the two cannot disagree:
 *
 * <pre>{@code { "duration": 1.56, "words": [ { "text": "You", "start": 0.52, "end": 0.64 }, ... ] } }</pre>
 *
 * <p>Times are seconds from the start of the clip. A line that opens with a laugh or a gasp has
 * silence where the laugh is, so its first word starts late, and the caption waits for it.
 *
 * <p>Server data rather than a client asset, because the server is what runs the timeline: it is
 * the one that has to know how long "Can you make..." takes before it says "cat".
 */
public final class Captions implements SimpleSynchronousResourceReloadListener {

	public record Word(String text, float start, float end) {
		static final Codec<Word> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("text").forGetter(Word::text),
			Codec.FLOAT.fieldOf("start").forGetter(Word::start),
			Codec.FLOAT.fieldOf("end").forGetter(Word::end)
		).apply(instance, Word::new));
	}

	public record Caption(float duration, List<Word> words) {
		public static final Codec<Caption> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.floatRange(0.0F, 60.0F).fieldOf("duration").forGetter(Caption::duration),
			Word.CODEC.listOf().optionalFieldOf("words", List.of()).forGetter(Caption::words)
		).apply(instance, Caption::new));
	}

	private static final String DIRECTORY = "learning_captions";
	private static final String SUFFIX = ".json";

	/** Keyed as sounds are, {@code phrase/well_done}; replaced wholesale on reload. */
	private static volatile Map<String, Caption> captions = Map.of();

	public static @Nullable Caption get(String key) {
		return captions.get(key);
	}

	/** Every clip with a caption: {@code phrase/make_the_word}, {@code word/cat}. */
	public static java.util.Set<String> keys() {
		return captions.keySet();
	}

	public static int size() {
		return captions.size();
	}

	@Override
	public Identifier getFabricId() {
		return LearningBlocks.id(DIRECTORY);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, Caption> loaded = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry
				: manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier file = entry.getKey();
			// Only this mod's clips: a caption is keyed like a sound, and sounds are played from
			// this namespace alone.
			if (!file.getNamespace().equals(LearningBlocks.MOD_ID)) continue;
			String path = file.getPath();
			String key = path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				Caption.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
					.ifSuccess(caption -> loaded.put(key, caption))
					.ifError(error -> LearningBlocks.LOGGER.warn("[{}] Skipping caption {}: {}", LearningBlocks.MOD_ID, file, error.message()));
			} catch (Exception e) {
				LearningBlocks.LOGGER.warn("[{}] Could not read caption {}", LearningBlocks.MOD_ID, file, e);
			}
		}
		captions = Map.copyOf(loaded);
		LearningBlocks.LOGGER.info("[{}] Loaded {} captions", LearningBlocks.MOD_ID, captions.size());
	}
}
