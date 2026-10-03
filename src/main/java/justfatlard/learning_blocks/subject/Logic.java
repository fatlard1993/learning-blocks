package justfatlard.learning_blocks.subject;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.puzzle.PatternRound;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.SubjectRound;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Logic, as a stub: one kind, "what comes next?", a two-colour pattern turn and turn about with a
 * third colour among the choices. Its one item is {@code logic:pattern:ab}; its first level is done
 * once that is mastered. Real content comes later.
 */
public final class Logic implements Subject {
	public static final Logic INSTANCE = new Logic();
	public static final String NEXT = "next";
	public static final String AB = "logic:pattern:ab";
	/** The three colours: bright, easy to tell apart, and none of them the green of the board. */
	static final List<DyeColor> COLOURS = List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW);

	private Logic() {}

	@Override
	public String id() {
		return "logic";
	}

	@Override
	public String label() {
		return "Logic";
	}

	@Override
	public DyeColor colour() {
		return DyeColor.PURPLE;
	}

	@Override
	public String hello() {
		return "phrase/subject_logic";
	}

	@Override
	public boolean canAsk(ServerPlayer player) {
		return true;
	}

	public boolean levelDone(Progress progress) {
		return progress.itemMastered(AB);
	}

	@Override
	public @Nullable SubjectRound round(ServerPlayer player, @Nullable String kind, @Nullable String target, RandomSource random) {
		if (kind != null && !kind.equals(NEXT)) return null;
		if (target != null && !target.equals("ab")) return null;
		Room room = Rooms.of(player);
		if (room == null) return null;
		List<DyeColor> colours = new ArrayList<>(COLOURS);
		java.util.Collections.shuffle(colours, new java.util.Random(random.nextLong()));
		if (!room.practice()) Progress.of(player).useItemToday(player, AB, NEXT);
		return new PatternRound(room, colours.get(0), colours.get(1), colours.get(2), random);
	}

	@Override
	public List<String> kinds() {
		return List.of(NEXT);
	}

	@Override
	public List<String> targets(String kind) {
		return kind.equals(NEXT) ? List.of("ab") : List.of();
	}

	@Override
	public List<String> items() {
		return List.of(AB);
	}

	@Override
	public boolean mastered(Progress progress, String item) {
		return progress.itemMastered(item);
	}

	@Override
	public List<String> summary(Progress progress) {
		return List.of("Logic: what comes next (two colours turn about) " + (levelDone(progress) ? "mastered" : progress.itemClean(AB) + " clean so far"));
	}
}
