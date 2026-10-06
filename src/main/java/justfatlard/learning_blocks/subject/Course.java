package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * A subject's course in bands, as Numbers' and Logic's are: each band has the items that finish
 * it (its gate), a main kind of round with its targets, and its extras; a stage is done when all
 * its bands are. Reading's course is its own ({@code Stages}), built from the word files.
 */
public final class Course<K extends Course.Kind, B extends Course.Band<K>> {
	/** A kind of round: its name in the sampler, and the mastery item each target makes. */
	public interface Kind {
		String id();

		String item(String target);
	}

	public interface Band<K> {
		String code();

		int stage();

		List<String> gate();

		K main();

		List<String> mainTargets();

		List<K> extras();
	}

	private final List<B> bands;

	public Course(List<B> bands) {
		this.bands = List.copyOf(bands);
	}

	public List<B> bands() {
		return bands;
	}

	/** The first band not finished; null once all are. */
	public @Nullable B current(Progress progress) {
		for (B band : bands) if (!finished(progress, band)) return band;
		return null;
	}

	/** The band a round is made for: the child's, or the last once everything is done. */
	public B currentOrLast(Progress progress) {
		B band = current(progress);
		return band == null ? bands.getLast() : band;
	}

	public boolean finished(Progress progress, B band) {
		for (String item : band.gate()) if (!progress.itemMastered(item)) return false;
		return true;
	}

	public boolean stageDone(Progress progress, int stage) {
		for (B band : bands) if (band.stage() == stage && !finished(progress, band)) return false;
		return true;
	}

	public @Nullable B band(String code) {
		for (B band : bands) if (band.code().equalsIgnoreCase(code)) return band;
		return null;
	}

	public List<B> before(B band) {
		return bands.subList(0, bands.indexOf(band));
	}

	public List<String> gateItems() {
		List<String> all = new ArrayList<>();
		for (B band : bands) all.addAll(band.gate());
		return all;
	}

	/** Put a child at this band: every band before it mastered, it and after not (/learn stage, and tests). */
	public void putAt(ServerPlayer player, B at) {
		Progress progress = Progress.of(player);
		boolean before = true;
		for (B band : bands) {
			if (band == at) before = false;
			for (String item : band.gate()) progress.setItemCleanForTests(player, item, before ? Progress.masteredAt() : 0);
		}
	}
}
