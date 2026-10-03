package justfatlard.learning_blocks.narration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import justfatlard.learning_blocks.sound.Captions;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Something the room says, as a list of steps played one after another: "can you make" then
 * "cat"; or each letter of a blend with its tile lit, then the word, then the word happening.
 *
 * <p>A step is a clip, an action in the world, or both. The action runs as the step begins, just
 * before its clip, so a tile is lit on the same tick its sound starts. The step then lasts as long
 * as its clip does - the length written in the clip's caption file - plus a gap, and the next one
 * begins. A step with no clip lasts only its gap: a beat for something to happen in.
 *
 * <p>Built once and handed to {@link Narrator#play}; nothing here runs by itself.
 */
public final class Narration {

	/** The pause after most clips, in ticks, so a line does not run into the next. */
	public static final int GAP = 6;

	/**
	 * @param at where the clip comes from, for a sound a child should hear from a block; null for
	 *           the room's own voice, which is heard as if beside them
	 */
	public record Step(@Nullable String clip, @Nullable BlockPos at, @Nullable Consumer<ServerPlayer> action,
			@Nullable CaptionLine line, boolean captioned, int gap, String label, @Nullable BooleanSupplier when) {

		Step(@Nullable String clip, @Nullable BlockPos at, @Nullable Consumer<ServerPlayer> action,
				@Nullable CaptionLine line, boolean captioned, int gap, String label) {
			this(clip, at, action, line, captioned, gap, label, null);
		}

		/** The same step with another clip: a variant of the line, chosen as it starts. */
		Step withClip(String other) {
			return new Step(other, at, action, line, captioned, gap, label, when);
		}

		/** Played as written, never swapped for a variant: the phrases screen checking one recording. */
		boolean exact() {
			return label.startsWith(EXACT);
		}

		int length() {
			return (clip != null ? Sounds.durationTicks(clip) : 0) + gap;
		}

		/** What the caption shows for this step: its own line, the clip's, or nothing; with its speaker's tag if not the room's. */
		@Nullable CaptionLine caption() {
			if (!captioned) return null;
			CaptionLine shown = line;
			if (shown == null && clip != null) {
				Captions.Caption caption = Captions.get(clip);
				shown = caption == null || caption.words().isEmpty() ? null : CaptionLine.timed(caption);
			}
			String speaker = speaker();
			return shown == null || speaker == null ? shown : CaptionLine.tagged(speaker + ":", shown);
		}

		/** Who says this, if not the room's own voice: {@code Bob}. */
		@Nullable String speaker() {
			if (!label.startsWith(SPEAKER)) return null;
			int end = label.indexOf('|', SPEAKER.length());
			return end < 0 ? null : label.substring(SPEAKER.length(), end);
		}
	}

	private static final String EXACT = "exact:";
	private static final String SPEAKER = "speaker:";

	/**
	 * Someone other than the room says a clip - Bob - with their name before the caption, so a child
	 * can tell the voices apart. A variant of the line is captioned as itself, tag and all.
	 */
	public Narration sayAs(String speaker, String clip, String label, @Nullable Consumer<ServerPlayer> action, int gap) {
		steps.add(new Step(clip, null, action, null, true, gap, SPEAKER + speaker + "|" + label));
		return this;
	}

	/** The same, with a caption given here: a line not recorded yet. */
	public Narration sayAs(String speaker, String clip, String label, @Nullable Consumer<ServerPlayer> action, CaptionLine line, int gap) {
		steps.add(new Step(clip, null, action, line, true, gap, SPEAKER + speaker + "|" + label));
		return this;
	}

	final List<Step> steps = new ArrayList<>();
	@Nullable Consumer<ServerPlayer> onStop;

	/** Say exactly this clip, not a variant of it. */
	public Narration sayExactly(String clip) {
		return add(new Step(clip, null, null, null, true, GAP, EXACT + clip));
	}

	/** Say a clip, with its own caption, then the usual gap. */
	public Narration say(String clip) {
		return say(clip, GAP);
	}

	public Narration say(String clip, int gap) {
		steps.add(new Step(clip, null, null, null, true, gap, clip));
		return this;
	}

	/**
	 * Say a clip only if this still holds when the narration gets to it - "You earned a prize!" only
	 * when reading the word actually paid something - and otherwise go straight on.
	 */
	public Narration sayIf(String clip, BooleanSupplier when) {
		steps.add(new Step(clip, null, null, null, true, GAP, clip, when));
		return this;
	}

	/** Say a clip from a block, so it is heard from there, with its own caption. */
	public Narration sayAt(String clip, BlockPos at) {
		steps.add(new Step(clip, at.immutable(), null, null, true, GAP, clip));
		return this;
	}

	/** Do something, then say a clip with the caption given here rather than the clip's own. */
	public Narration say(String clip, String label, Consumer<ServerPlayer> action, CaptionLine line, int gap) {
		steps.add(new Step(clip, null, action, line, true, gap, label));
		return this;
	}

	/** Do something, then say a clip with its own caption. */
	public Narration say(String clip, String label, Consumer<ServerPlayer> action, int gap) {
		steps.add(new Step(clip, null, action, null, true, gap, label));
		return this;
	}

	/** Do something and show a line with nothing said - a silent letter - for this many ticks. */
	public Narration show(String label, Consumer<ServerPlayer> action, CaptionLine line, int ticks) {
		steps.add(new Step(null, null, action, line, true, ticks, label));
		return this;
	}

	/** Do something with no clip, then wait a beat. The caption up already stays up. */
	public Narration then(String label, Consumer<ServerPlayer> action, int beat) {
		steps.add(new Step(null, null, action, null, false, beat, label));
		return this;
	}

	/**
	 * Runs once when this narration stops, whether it finished or was cut off - by leaving, by
	 * logging out, or by another narration starting - so whatever a narration set up it can undo.
	 */
	public Narration onStop(Consumer<ServerPlayer> stop) {
		this.onStop = stop;
		return this;
	}

	/** The steps, to be added to another narration. */
	public List<Step> steps() {
		return List.copyOf(steps);
	}

	/** A step taken from another narration. */
	public Narration add(Step step) {
		steps.add(step);
		return this;
	}

	public boolean isEmpty() {
		return steps.isEmpty();
	}

	/** Ticks from start to finish, for anybody who has to wait one out. */
	public int length() {
		int total = 0;
		for (Step step : steps) total += step.length();
		return total;
	}
}
