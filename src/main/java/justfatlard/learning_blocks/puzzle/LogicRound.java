package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.logic.LogicCourse;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.subject.Logic;
import justfatlard.learning_blocks.subject.Subject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A round of Logic: its kind and target and the stage it counts towards. The kinds are in their
 * own classes; {@link Logic} picks them.
 */
public abstract class LogicRound extends SubjectRound {
	protected final LogicCourse.Kind kind;
	protected final String target;
	protected final int stage;
	protected final RandomSource random;
	private @Nullable Choice answer;

	protected LogicRound(Room room, LogicCourse.Kind kind, String target, int stage, RandomSource random) {
		super(room);
		this.kind = kind;
		this.target = target;
		this.stage = stage;
		this.random = random;
	}

	@Override
	public Subject subject() {
		return Logic.INSTANCE;
	}

	@Override
	public String kind() {
		return kind.id;
	}

	public String target() {
		return target;
	}

	@Override
	public String item() {
		return kind.item(target);
	}

	@Override
	protected Choice answer() {
		return answer;
	}

	protected void answer(Choice answer) {
		this.answer = answer;
	}

	@Override
	protected boolean levelDone(Progress progress) {
		return LogicCourse.stageDone(progress, stage);
	}

	@Override
	protected String levelKey() {
		return "logic:L" + stage;
	}

	@Override
	protected String nextLevelLine() {
		return switch (stage) {
			case 0 -> "phrase/next_level_matching";
			case 1 -> "phrase/next_level_patterns";
			case 2 -> "phrase/next_level_odd_one";
			default -> "phrase/next_level_sequencing";
		};
	}

	@Override
	protected String nextLevelWords() {
		return switch (stage) {
			case 0 -> "You can sort and match! Ready for more!";
			case 1 -> "You know your patterns! Ready for more!";
			case 2 -> "You can spot the odd one out! Ready for more!";
			default -> "You can put things in order! Ready for more thinking!";
		};
	}

	/** A colour's word, said with its caption. */
	protected static Narration colour(Narration narration, DyeColor colour, String tag) {
		return narration.say("word/" + colour.getSerializedName(), tag, p -> {}, CaptionLine.plain(colour.getSerializedName()), 3);
	}

	/** A thing hops where it is, with a puff: picked, or put. */
	protected void hop(@Nullable Entity thing, net.minecraft.server.level.ServerPlayer player) {
		if (thing == null || !thing.isAlive()) return;
		Vec3 at = thing.position();
		thing.teleportTo(at.x, at.y + 0.25, at.z);
		justfatlard.learning_blocks.Later.after(player, 3, p -> {
			if (thing.isAlive()) thing.teleportTo(at.x, at.y, at.z);
		});
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + thing.getBbHeight() + 0.2, at.z, 3, 0.2, 0.1, 0.2, 0.0);
	}

	/** Moves a thing to a new spot: a little hop there. */
	protected void move(@Nullable Entity thing, Vec3 to) {
		if (thing == null || !thing.isAlive()) return;
		level.sendParticles(ParticleTypes.POOF, thing.getX(), thing.getY() + 0.3, thing.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
		thing.teleportTo(to.x, to.y, to.z);
		level.sendParticles(ParticleTypes.POOF, to.x, to.y + 0.3, to.z, 4, 0.15, 0.1, 0.15, 0.01);
	}
}
