package justfatlard.learning_blocks.subject;

import java.util.List;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * One subject: Reading, Numbers, Logic. Each has its own curriculum, rounds, mastery items and place
 * in the course; the classroom, the voice, the button, Bob, the toys, prizes, lessons and the daily
 * schedule are shared. {@link Subjects} has them all, and mixes their rounds for each child.
 *
 * <p>A subject's mastery items are named {@code <subject>:<kind>:<item>} ({@code numbers:numeral:5});
 * they are what its rounds ask, what it remembers (in the child's one {@code Memory}), and what a
 * toy can be earned by (see {@code Earned}). Reading keeps the keys it had (its words, letters,
 * sentences), which {@link #mastered} answers for as {@code reading:word:cat} and so on.
 */
public interface Subject {
	/** {@code reading}, {@code numbers}, {@code logic}: in commands, settings and item ids. */
	String id();

	/** "Reading", for people. */
	String label();

	/** The board's frame, while this subject's round is up. */
	DyeColor colour();

	/** The line said on switching to it: {@code phrase/subject_numbers}. */
	String hello();

	/** Whether it has anything to ask this child just now. */
	boolean canAsk(ServerPlayer player);

	/**
	 * The next round of it for this child: {@code kind} and {@code target} as the sampler asks, or
	 * null for its own choice. Null if there is none; Reading's rounds are made by the room itself,
	 * so Reading always says null here.
	 */
	@Nullable SubjectRound round(ServerPlayer player, @Nullable String kind, @Nullable String target, RandomSource random);

	/** Its kinds of round, for the sampler. */
	List<String> kinds();

	/** What a kind of round can ask, for the sampler. */
	List<String> targets(String kind);

	/** Its mastery items, as {@code numbers:numeral:5}. */
	List<String> items();

	/** Whether this child has mastered this item of it. */
	boolean mastered(Progress progress, String item);

	/** A line or two for the grown-ups' view: where this child is in it. */
	List<String> summary(Progress progress);
}
