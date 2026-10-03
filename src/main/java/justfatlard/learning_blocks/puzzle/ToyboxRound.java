package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.plush.Plushies;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.toy.Toys;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Counting what the child owns: their own toys (or plushies) from the Toybox pop out of the button
 * and stand in rows, "How many toys have you got?" Collecting matters. Only when they have between
 * one and ten of them.
 */
public final class ToyboxRound extends NumbersRound {
	private final List<ItemStack> owned;
	private final List<Entity> shown = new ArrayList<>();

	public ToyboxRound(Room room, String which, List<ItemStack> owned, int stage, RandomSource random) {
		super(room, NumberCourse.Kind.TOYBOX, which, stage, random);
		this.owned = List.copyOf(owned);
		tiles(owned.size(), 1, Math.max(5, Math.min(10, owned.size() + 2)));
	}

	/** One of each kind they have, toys or plushies, at most ten: what a round counts. */
	public static List<ItemStack> owned(Progress progress, String which) {
		List<ItemStack> out = new ArrayList<>();
		if (which.equals("plushies")) {
			for (var entry : progress.plushies().entrySet()) if (entry.getValue() > 0) out.add(Plushies.stack(entry.getKey()));
		} else {
			for (var entry : progress.toys().entrySet()) if (entry.getValue() > 0) out.add(Toys.stack(entry.getKey()));
		}
		return out;
	}

	@Override
	protected void board() {}

	@Override
	protected void started(ServerPlayer player) {
		for (int i = 0; i < owned.size(); i++) {
			Entity thing = Counting.item(level, owned.get(i), 0.75F);
			if (thing == null) continue;
			shown.add(thing);
			Vec3 to = Counting.spot(layout, i, owned.size());
			Counting.fly(this, player, thing, to, i * 4, null);
		}
	}

	@Override
	public Narration ask(Narration narration) {
		return target.equals("plushies") ? line(narration, "phrase/how_many_plushies", "How many plushies have you got?")
			: line(narration, "phrase/how_many_toys", "How many toys have you got?");
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		line(telling, "phrase/count_with_me", "Let's count them!");
		Counting.chant(telling, owned.size(), shown, false);
		telling.say(NumberBlock.soundOf(owned.size()), "right:" + owned.size(), p -> {},
			CaptionLine.plain(Numbers.word(owned.size()) + " " + target + "!"), AnswerNarration.WORD_GAP);
	}

	public int count() {
		return owned.size();
	}
}
