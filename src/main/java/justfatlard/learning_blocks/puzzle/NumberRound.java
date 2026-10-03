package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberBlock;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Numbers;
import justfatlard.learning_blocks.subject.Subject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * "Which block says three?": the Numbers stub's one kind, as the letters level asks for a letter.
 * The number is said, and its dots are up on the board; on the floor stand three number tiles, the
 * right one and two others, to pick from.
 */
public final class NumberRound extends SubjectRound {
	private final int target;
	private final Choice answer;

	public NumberRound(Room room, int target, List<Integer> others, RandomSource random) {
		super(room);
		this.target = target;
		List<Integer> shown = new ArrayList<>(others);
		shown.add(target);
		Collections.shuffle(shown, new java.util.Random(random.nextLong()));
		Choice right = null;
		for (int n : shown) {
			Choice choice = new Choice(String.valueOf(n), LearningBlocks.NUMBER.showing(n), NumberBlock.soundOf(n), Numbers.word(n));
			choices.add(choice);
			if (n == target) right = choice;
		}
		this.answer = right;
	}

	@Override
	public Subject subject() {
		return Numbers.INSTANCE;
	}

	@Override
	public String kind() {
		return Numbers.FIND;
	}

	@Override
	public String item() {
		return Numbers.numeral(target);
	}

	public int target() {
		return target;
	}

	@Override
	protected Choice answer() {
		return answer;
	}

	@Override
	protected void board() {
		// The number's dots, big, in the middle of the board: what it says, to count.
		Display.TextDisplay dots = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (dots == null) return;
		Vec3 at = Vec3.atCenterOf(layout.rowSpot(0, 1)).add(0, 0.1, 0.45);
		dots.snapTo(at.x, at.y, at.z, 0, 0);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < target; i++) text.append(i > 0 && i % 5 == 0 ? "\n" : "").append("●");
		dots.setText(Component.literal(text.toString()).withStyle(style -> style.withColor(TextColor.fromRgb(0xC0392B))));
		dots.setBackgroundColor(0);
		dots.setTransformation(new com.mojang.math.Transformation(null, null, new Vector3f(4.0F, 4.0F, 4.0F), null));
		made(dots);
		level.addFreshEntity(dots);
	}

	@Override
	public Narration ask(Narration narration) {
		return line(narration, "phrase/which_block_says", "Which block says")
			.say(NumberBlock.soundOf(target), "number:" + target, p -> {}, CaptionLine.plain(Numbers.word(target)), Narration.GAP);
	}

	@Override
	protected boolean levelDone(Progress progress) {
		return Numbers.INSTANCE.levelDone(progress);
	}

	@Override
	protected String levelKey() {
		return "numbers:1";
	}

	@Override
	protected String nextLevelLine() {
		return "phrase/next_level_numbers";
	}

	@Override
	protected String nextLevelWords() {
		return "You know one to five! Ready for more numbers!";
	}
}
