package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.narration.CaptionLine;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.numbers.NumberCourse;
import justfatlard.learning_blocks.relate.Relations;
import justfatlard.learning_blocks.subject.Numbers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Bob, with number cards. The sentence on the board has a number card to choose ("put _ apples in
 * box"), the goal is framed on the board's left (a box with three apples in it), and the number
 * tiles on the floor are the cards. Picked, the sentence is read out and Bob does exactly what it
 * says: three apples into the box; nine, and they pile up and spill over ("Whoa! That's a LOT!");
 * nine cats on the bed, a cat pile. Three kinds:
 * <ul>
 *   <li>Tell Bob: make the goal.</li>
 *   <li>What's wrong: Bob has already done it with the wrong card (four apples, the goal three);
 *   fix the number.</li>
 *   <li>More: "put more apples in big box", a small box with some apples beside an empty big one;
 *   any number bigger than the small box's will do.</li>
 * </ul>
 */
public final class NumberBobRound extends NumbersRound {
	/** What Bob is told to put where. */
	public enum Job {
		APPLES("apples", "in", "box"), CATS("cats", "on", "bed");

		final String things;
		final String prep;
		final String place;

		Job(String things, String prep, String place) {
			this.things = things;
			this.prep = prep;
			this.place = place;
		}
	}

	private static final int INK = 0x1E2A5A;
	private final Job job;
	/** How many the goal has (Tell Bob, What's wrong), or how many the small box has (More). */
	private final int goal;
	/** What's wrong: the card Bob was given. */
	private final int wrongCard;
	private @Nullable Villager bob;
	private Display.@Nullable TextDisplay sentence;
	private final List<Entity> put = new ArrayList<>();
	private int lastPut;

	public NumberBobRound(Room room, NumberCourse.Kind kind, int goal, int stage, RandomSource random) {
		super(room, kind, kind == NumberCourse.Kind.MORE ? "apples" : String.valueOf(goal), stage, random);
		this.job = kind == NumberCourse.Kind.BOB && random.nextBoolean() ? Job.CATS : Job.APPLES;
		this.goal = goal;
		int card = goal;
		while (card == goal) card = 1 + random.nextInt(6);
		this.wrongCard = card;
		if (kind == NumberCourse.Kind.MORE) {
			// Two that are not more and one that is.
			List<Integer> shown = new ArrayList<>(List.of(Math.max(1, goal - 1 - random.nextInt(Math.max(1, goal - 1))), goal, goal + 1 + random.nextInt(3)));
			java.util.Collections.shuffle(shown, new java.util.Random(random.nextLong()));
			for (int n : shown) {
				Choice choice = tile(n);
				choices.add(choice);
				if (n > goal) answer(choice);
			}
		} else {
			tiles(goal, 1, 9);
		}
	}

	/** Where Bob's box or bed stands; More's big box beside it. */
	private BlockPos place() {
		return layout.origin().offset(kind == NumberCourse.Kind.MORE ? -1 : -2, 0, -1);
	}

	private BlockPos smallBox() {
		return layout.origin().offset(-3, 0, -1);
	}

	/** The cards stand to the right, clear of Bob and his box. */
	@Override
	protected BlockPos spotFor(int i, int n) {
		return layout.origin().offset(1 + 2 * i, 0, -2);
	}

	private static float bigSize() {
		return 1.5F;
	}

	private BlockState placeBlock() {
		if (job == Job.CATS) return Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
		var thing = Relations.thing("box");
		BlockState box = thing == null ? null : BobStage.blockOf(thing);
		return box == null ? Blocks.BARREL.defaultBlockState() : box;
	}

	/** The words on the board, with this card in the gap ("_" for none). */
	private List<String> words(@Nullable Integer card) {
		if (kind == NumberCourse.Kind.MORE) return List.of("put", "more", "apples", "in", "big", "box");
		return List.of("put", card == null ? "_" : String.valueOf(card), job.things, job.prep, job.place);
	}

	private void showSentence(@Nullable Integer card) {
		if (sentence != null) sentence.discard();
		sentence = Counting.boardText(this, String.join(" ", words(card)), INK, 3.0F, 0.6, 0.4);
		sentence.setBackgroundColor(0xFFF2E8C9);
	}

	@Override
	protected void board() {
		showSentence(kind == NumberCourse.Kind.FIX ? wrongCard : null);
		if (kind != NumberCourse.Kind.MORE) goalPicture();
	}

	/** The goal, small and framed on the board's left: the box (or bed) and how many go with it. */
	private void goalPicture() {
		Vec3 at = Vec3.atBottomCenterOf(layout.origin().offset(-4, RoomLayout.WORD_Y, -4)).add(0.3, -0.2, 0.2);
		Display.BlockDisplay frame = Counting.block(level, Blocks.CONCRETE.white().defaultBlockState(), 1.0F);
		frame.setTransformation(new Transformation(new Vector3f(-0.9F, -0.1F, -0.3F), null, new Vector3f(1.8F, 1.6F, 0.05F), null));
		frame.snapTo(at.x, at.y, at.z, 0, 0);
		add(frame);
		Display.BlockDisplay base = Counting.block(level, placeBlock(), 0.45F);
		base.snapTo(at.x, at.y + 0.1, at.z, 0, 0);
		add(base);
		for (int i = 0; i < goal; i++) {
			Vec3 spot = at.add(((i % 3) - 1) * 0.22, 0.55 + (i / 3) * 0.22, 0.05);
			Entity thing = job == Job.CATS ? Counting.item(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CAT_SPAWN_EGG), 0.3F)
				: Counting.item(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE), 0.3F);
			if (thing == null) continue;
			thing.snapTo(spot.x, spot.y, spot.z, 0, 0);
			add(thing);
		}
	}

	@Override
	protected void started(ServerPlayer player) {
		if (kind != NumberCourse.Kind.MORE) set(place(), placeBlock());
		else {
			// The big box is the box, bigger; the small one, a box, with its apples in already.
			Display.BlockDisplay big = Counting.block(level, placeBlock(), bigSize());
			Vec3 at = Vec3.atBottomCenterOf(place());
			big.snapTo(at.x, at.y, at.z, 0, 0);
			add(big);
			set(smallBox(), placeBlock());
			for (int i = 0; i < goal; i++) {
				Entity apple = Counting.make(level, Counting.What.APPLE, 0.45F);
				Vec3 spot = slot(smallBox(), i, 1.0F);
				apple.snapTo(spot.x, spot.y, spot.z, 0, 0);
				add(apple);
			}
		}
		spawnBob();
		if (kind == NumberCourse.Kind.FIX) putNow(wrongCard);
	}

	private void spawnBob() {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) return;
		Vec3 at = Vec3.atBottomCenterOf(layout.origin().offset(-4, 0, -2));
		villager.snapTo(at.x, at.y, at.z, 0, 0);
		villager.setYHeadRot(0);
		villager.setYBodyRot(0);
		villager.setNoAi(true);
		villager.setSilent(true);
		villager.setPermanentlyInvulnerable(true);
		villager.setPersistenceRequired();
		villager.setCustomName(Component.literal("Bob"));
		villager.setCustomNameVisible(true);
		add(villager);
		bob = villager;
		justfatlard.learning_blocks.feel.Gestures.run(villager, justfatlard.learning_blocks.feel.Gestures.Gesture.WAVE);
	}

	/** Where the i-th thing goes: in the box, three across and three deep, then a layer up; past nine, spilling out onto the floor. */
	private Vec3 slot(BlockPos where, int i, float size) {
		Vec3 top = Vec3.atBottomCenterOf(where).add(0, job == Job.CATS ? 0.5625 : 0.55 * size, 0);
		if (job == Job.CATS) return top.add(0, i * 0.55, 0);
		if (i < 6) return top.add(((i % 3) - 1) * 0.25 * size, (i / 3) * 0.25, ((i / 3) % 2 == 0 ? -0.1 : 0.1) * size);
		// Spilled: round the box on the floor.
		double angle = (i - 6) * 1.3;
		return Vec3.atBottomCenterOf(where).add(Math.cos(angle) * 0.85, 0, Math.sin(angle) * 0.85);
	}

	/** Bob's things gone, and this many put now, at once (What's wrong starts with Bob's wrong go done). */
	private void putNow(int n) {
		for (int i = 0; i < n; i++) {
			Entity thing = Counting.make(level, job == Job.CATS ? Counting.What.CAT : Counting.What.APPLE, 0.45F);
			if (thing == null) continue;
			Vec3 spot = slot(place(), i, 1.0F);
			thing.snapTo(spot.x, spot.y, spot.z, 0, 0);
			add(thing);
			put.add(thing);
		}
		lastPut = n;
	}

	private void takeAway() {
		for (Entity thing : put) {
			level.sendParticles(ParticleTypes.POOF, thing.getX(), thing.getY() + 0.3, thing.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
			thing.discard();
		}
		put.clear();
	}

	@Override
	public Narration ask(Narration narration) {
		switch (kind) {
			case FIX -> line(narration, "phrase/whats_wrong", "What's wrong?");
			case MORE -> {
				line(narration, "phrase/tell_bob", "Tell Bob!");
				read(narration, words(null));
				line(narration, "phrase/how_many", "How many?");
			}
			default -> line(narration, "phrase/tell_bob", "Tell Bob!");
		}
		return narration;
	}

	@Override
	protected Verdict judge(ServerPlayer player, Choice picked) {
		int n = number(picked);
		return (kind == NumberCourse.Kind.MORE ? n > goal : n == goal) ? Verdict.RIGHT : Verdict.WRONG;
	}

	/** The card in the gap, the sentence read, and Bob does it, exactly. */
	@Override
	protected Narration act(ServerPlayer player, Choice picked) {
		int n = number(picked);
		Narration doing = new Narration();
		doing.then("card", p -> {
			if (kind != NumberCourse.Kind.MORE) showSentence(n);
		}, 4);
		if (kind != NumberCourse.Kind.MORE) read(doing, words(n));
		doing.sayAs("Bob", "phrase/bob_okay", "bob_okay", null, 4);
		doing.then("bob_does", p -> {
			takeAway();
			Counting.What what = job == Job.CATS ? Counting.What.CAT : Counting.What.APPLE;
			float size = kind == NumberCourse.Kind.MORE ? bigSize() : 1.0F;
			for (int i = 0; i < n; i++) {
				Entity thing = Counting.make(level, what, 0.45F);
				if (thing == null) continue;
				put.add(thing);
				Counting.fly(this, p, thing, slot(place(), i, size), i * 3, null);
			}
			lastPut = n;
			if (bob != null) justfatlard.learning_blocks.feel.Gestures.run(bob, justfatlard.learning_blocks.feel.Gestures.Gesture.WAVE);
		}, Counting.dispenseTicks(n, 3));
		if (n >= (kind == NumberCourse.Kind.MORE ? 8 : goal + 3)) doing.sayAs("Bob", "phrase/bob_so_many", "bob_so_many", null, 4);
		return doing;
	}

	@Override
	protected void tellRight(ServerPlayer player, Choice picked, Narration telling) {
		if (kind == NumberCourse.Kind.FIX) telling.sayAs("Bob", "phrase/bob_did_it", "bob_did_it", null, AnswerNarration.WORD_GAP);
		else telling.sayAs("Bob", "phrase/bob_counted", "bob_counted", null, AnswerNarration.WORD_GAP);
	}

	@Override
	protected void tellWrong(ServerPlayer player, Choice picked, Narration telling) {
		int n = number(picked);
		if (kind != NumberCourse.Kind.MORE && n > goal) line(telling, "phrase/too_many", "Whoa, that's too many!");
		else line(telling, "phrase/not_enough", "Hmm, not enough!");
		telling.then("undo", p -> {
			takeAway();
			showSentence(kind == NumberCourse.Kind.FIX ? wrongCard : null);
			if (kind == NumberCourse.Kind.FIX) putNow(wrongCard);
		}, 4);
	}

	/** For tests: what Bob put last, and how many things are in or round the box or on the bed. */
	public int lastPut() {
		return lastPut;
	}

	public List<Entity> put() {
		return List.copyOf(put);
	}

	public Job job() {
		return job;
	}

	public int goal() {
		return goal;
	}

	public int wrongCard() {
		return wrongCard;
	}

	public @Nullable LivingEntity bob() {
		return bob;
	}
}
