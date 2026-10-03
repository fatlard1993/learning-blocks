package justfatlard.learning_blocks.subject;

import java.util.List;
import justfatlard.learning_blocks.puzzle.Sampler;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.puzzle.Word;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Reading: letters, words, sentences with Bob, big words. Its rounds, picker, stages and bands,
 * mastery and memory are the room's own as they always were ({@code RoundLifecycle}, {@code Stages},
 * {@code Progress}); this is how the rest of the framework sees it. Its items are
 * {@code reading:word:<word>} and {@code reading:sentence:<goal>}.
 */
public final class Reading implements Subject {
	public static final Reading INSTANCE = new Reading();

	private Reading() {}

	@Override
	public String id() {
		return "reading";
	}

	@Override
	public String label() {
		return "Reading";
	}

	@Override
	public DyeColor colour() {
		return DyeColor.BLUE;
	}

	@Override
	public String hello() {
		return "phrase/subject_reading";
	}

	@Override
	public boolean canAsk(ServerPlayer player) {
		return !Words.all().isEmpty();
	}

	@Override
	public @Nullable SubjectRound round(ServerPlayer player, @Nullable String kind, @Nullable String target, RandomSource random) {
		return null;
	}

	@Override
	public List<String> kinds() {
		return Sampler.kindNames();
	}

	@Override
	public List<String> targets(String kind) {
		Sampler.Kind found = Sampler.Kind.of(kind);
		return found == null ? List.of() : Sampler.targets(found);
	}

	@Override
	public List<String> items() {
		return Words.all().stream().filter(Word::puzzle).map(word -> "reading:word:" + word.text()).toList();
	}

	@Override
	public boolean mastered(Progress progress, String item) {
		if (item.startsWith("reading:word:")) {
			String text = item.substring("reading:word:".length());
			return Words.all().stream().filter(word -> word.text().equals(text)).findFirst().map(progress::mastered).orElse(false);
		}
		if (item.startsWith("reading:sentence:")) return progress.sentenceMastered(item.substring("reading:sentence:".length()));
		return false;
	}

	@Override
	public List<String> summary(Progress progress) {
		return List.of(Stages.describe(progress));
	}
}
