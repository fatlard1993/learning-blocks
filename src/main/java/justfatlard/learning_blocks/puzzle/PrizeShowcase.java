package justfatlard.learning_blocks.puzzle;

import java.util.ArrayList;
import java.util.List;
import com.mojang.math.Transformation;
import justfatlard.learning_blocks.Later;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.narration.Narration;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The prize chest by the door, and prizes seen arriving in it: each one earned flies out of the
 * button into the chest as "You earned a prize!" is said, and over the chest a little haul shows
 * what is waiting to go home. On the way out the haul lifts off and goes to the child.
 */
public final class PrizeShowcase {
	private static final int FLAGS = RoomLayout.FLAGS;

	private final Room room;
	private final ServerLevel level;
	private final RoomLayout layout;
	private final Dispenser dispenser;

	/** A prize was just added and the room has not said so yet. */
	private boolean prizeNews;

	/** Where this reader's chest stands: each reader in a shared room has their own. */
	private final net.minecraft.core.BlockPos marker;

	PrizeShowcase(Room room, ServerLevel level, RoomLayout layout, Dispenser dispenser, net.minecraft.core.BlockPos marker) {
		this.marker = marker;
		this.room = room;
		this.level = level;
		this.layout = layout;
		this.dispenser = dispenser;
	}

	net.minecraft.core.BlockPos prizeMarker() {
		return marker;
	}

	private Vec3 mouth() {
		return layout.mouth();
	}

	private void puff() {
		dispenser.puff();
	}

	private void sent(String what, Vec3 from) {
		dispenser.sent(what, from);
	}

	/** Show the prize chest if this child has prizes waiting, and take it away if not. */
	void refreshPrizeMarker(ServerPlayer player) {
		Visit visit = Visit.of(player);
		boolean waiting = visit != null && visit.hasPrizes();
		BlockState now = level.getBlockState(prizeMarker());
		if (waiting && !now.is(Blocks.CHEST)) {
			level.setBlock(prizeMarker(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), FLAGS);
			Vec3 at = Vec3.atCenterOf(prizeMarker());
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.4, at.z, 16, 0.4, 0.4, 0.4, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.4F);
		} else if (!waiting && now.is(Blocks.CHEST)) {
			level.setBlock(prizeMarker(), Blocks.AIR.defaultBlockState(), FLAGS);
			clearHaul();
		}
	}

	/**
	 * Something was just earned: the next thing the room says includes "You earned a prize!", once,
	 * and as it is said the prize flies from the button into the chest. See {@link #takePrizeNews}.
	 */
	void prizeAdded(List<ItemStack> items, int xp) {
		prizeNews = true;
		// A plushie or a toy they hadn't got: "Ooh! You got a new plushie!" or "Ooh! A new toy!"
		// instead of the usual line.
		ServerPlayer owner = null;
		for (var entry : room.showcases.entrySet()) if (entry.getValue() == this) owner = level.getServer().getPlayerList().getPlayer(entry.getKey());
		if (owner != null) {
			var progress = justfatlard.learning_blocks.reward.Progress.of(owner);
			var owned = progress.plushies();
			var toys = progress.toys();
			for (ItemStack stack : items) {
				justfatlard.learning_blocks.plush.Plush plush = justfatlard.learning_blocks.plush.PlushItem.kindOf(stack);
				if (plush != null && owned.getOrDefault(plush, 0) == 1) newsLine = "phrase/new_plushie";
				justfatlard.learning_blocks.toy.Toy toy = justfatlard.learning_blocks.toy.ToyItem.kindOf(stack);
				if (toy != null && toys.getOrDefault(toy, 0) == 1) newsLine = "phrase/new_toy";
			}
		}
		for (ItemStack stack : items) if (!stack.isEmpty()) toDeliver.add(stack.copy());
		xpToDeliver += xp;
	}

	/** "You earned a prize!", if there is news, with the prizes flying in as it is said. */
	Narration.Step prizeLine() {
		return new Narration.Step(newsLine != null ? newsLine : "phrase/you_earned_a_prize", null, this::deliverPrizes, null, true, Narration.GAP,
			"prizes", this::takePrizeNews);
	}

	/** The line for a plushie or a toy they hadn't got; null, the usual line. */
	private @org.jspecify.annotations.Nullable String newsLine;

	/** The same for a reader who is not the one the narration is for: a team-up's other half. */
	Narration.Step prizeLineFor(ServerPlayer owner) {
		return new Narration.Step(newsLine != null ? newsLine : "phrase/you_earned_a_prize", null, p -> deliverPrizes(owner), null, true, Narration.GAP,
			"prizes", this::takePrizeNews);
	}

	// --- Prizes, seen arriving ---

	/** How long a prize takes from the button to the chest. */
	static final int PRIZE_FLIGHT = 22;
	/** Between one prize leaving the button and the next. */
	static final int PRIZE_GAP = 6;

	/** A prize that flew into the chest: what, and from where to where. For tests. */
	public record PrizeFlight(ItemStack stack, Vec3 from, Vec3 to) {}

	private final List<ItemStack> toDeliver = new ArrayList<>();
	private int xpToDeliver;
	/** Earned and not yet landed: left out of the haul until they arrive. */
	private final List<ItemStack> flying = new ArrayList<>();
	private final List<Entity> flights = new ArrayList<>();
	private final List<PrizeFlight> landed = new ArrayList<>();
	/** The little display over the chest of what is waiting to go home. */
	private final List<Display> haul = new ArrayList<>();
	private final List<ItemStack> haulShown = new ArrayList<>();
	private int lidOpen;

	public List<PrizeFlight> prizeFlights() {
		return List.copyOf(landed);
	}

	/** What the haul over the chest shows, for tests. */
	public List<ItemStack> haulShown() {
		return List.copyOf(haulShown);
	}

	public List<Display> haulEntities() {
		return List.copyOf(haul);
	}

	/** Later, for as long as this room is open, whatever round it is by then. */
	private void soon(ServerPlayer player, int ticks, java.util.function.Consumer<ServerPlayer> what) {
		Later.after(player, ticks, p -> {
			if (Rooms.of(p) == room) what.accept(p);
		});
	}

	private Vec3 chestTop() {
		return Vec3.atCenterOf(prizeMarker()).add(0, 0.55, 0);
	}

	/** The prizes earned since the last time, out of the button and into the chest, one after another. */
	private void deliverPrizes(ServerPlayer player) {
		List<ItemStack> items = new ArrayList<>(toDeliver);
		int xp = xpToDeliver;
		toDeliver.clear();
		xpToDeliver = 0;
		flying.addAll(items);
		// No chest yet: it pops into place first, and the prizes follow it in.
		boolean popped = !level.getBlockState(prizeMarker()).is(Blocks.CHEST);
		refreshPrizeMarker(player);
		int start = popped ? 10 : 0;
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			soon(player, start + i * PRIZE_GAP, p -> flyPrize(p, stack));
		}
		if (xp > 0) soon(player, start + items.size() * PRIZE_GAP, this::flyXp);
		int done = start + Math.max(items.size(), 1) * PRIZE_GAP + PRIZE_FLIGHT + 12;
		soon(player, done, p -> refreshHaul(p));
	}

	private void flyPrize(ServerPlayer player, ItemStack stack) {
		Display.ItemDisplay prize = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (prize == null) return;
		puff();
		Vec3 from = mouth();
		Vec3 to = chestTop();
		sent("prize", from);
		prize.snapTo(from.x, from.y, from.z, 0, 0);
		prize.setItemStack(stack.copyWithCount(1));
		prize.setItemTransform(ItemDisplayContext.GUI);
		prize.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		prize.setTransformation(new Transformation(null, new org.joml.Quaternionf().rotateY((float) Math.PI), new Vector3f(0.6F, 0.6F, 0.6F), null));
		prize.setPosRotInterpolationDuration(2);
		level.addFreshEntity(prize);
		flights.add(prize);
		for (int i = 1; i <= PRIZE_FLIGHT; i++) {
			double t = i / (double) PRIZE_FLIGHT;
			Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 1.6, 0);
			soon(player, i, p -> prize.snapTo(at.x, at.y, at.z, 0, 0));
		}
		soon(player, PRIZE_FLIGHT - 5, p -> openLid());
		soon(player, PRIZE_FLIGHT, p -> {
			prize.discard();
			flights.remove(prize);
			for (int i = 0; i < flying.size(); i++) {
				if (ItemStack.matches(flying.get(i), stack)) {
					flying.remove(i);
					break;
				}
			}
			landed.add(new PrizeFlight(stack.copy(), from, to));
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y + 0.2, to.z, 10, 0.3, 0.2, 0.3, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 0.2, to.z, 6, 0.2, 0.2, 0.2, 0.02);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.4F);
			if (stack.getCount() > 1) countOver(p, stack.getCount());
			refreshHaul(p);
			soon(p, 12, q -> closeLid());
		});
	}

	/** Experience: a stream of green sparks from the button into the chest. */
	private void flyXp(ServerPlayer player) {
		puff();
		Vec3 from = mouth();
		Vec3 to = chestTop();
		sent("prize_xp", from);
		net.minecraft.core.particles.DustParticleOptions spark = new net.minecraft.core.particles.DustParticleOptions(0x9CFF3A, 1.3F);
		for (int i = 1; i <= PRIZE_FLIGHT; i++) {
			double t = i / (double) PRIZE_FLIGHT;
			Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 1.6, 0);
			soon(player, i, p -> level.sendParticles(spark, at.x, at.y, at.z, 4, 0.08, 0.08, 0.08, 0.0));
		}
		soon(player, PRIZE_FLIGHT - 5, p -> openLid());
		soon(player, PRIZE_FLIGHT, p -> {
			level.playSound(null, to.x, to.y, to.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.2F);
			refreshHaul(p);
			soon(p, 12, q -> closeLid());
		});
	}

	private void openLid() {
		if (!level.getBlockState(prizeMarker()).is(Blocks.CHEST)) return;
		if (lidOpen++ == 0) {
			level.blockEvent(prizeMarker(), Blocks.CHEST, 1, 1);
			Vec3 at = Vec3.atCenterOf(prizeMarker());
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.1F);
		}
	}

	private void closeLid() {
		if (lidOpen <= 0) return;
		if (--lidOpen == 0 && level.getBlockState(prizeMarker()).is(Blocks.CHEST)) {
			level.blockEvent(prizeMarker(), Blocks.CHEST, 1, 0);
			Vec3 at = Vec3.atCenterOf(prizeMarker());
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.1F);
		}
	}

	/** "×2" over the chest for a moment, for a stack. */
	private void countOver(ServerPlayer player, int count) {
		Display.TextDisplay text = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (text == null) return;
		Vec3 at = chestTop().add(0, 0.7, 0);
		text.snapTo(at.x, at.y, at.z, 0, 0);
		text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		text.setText(Component.literal("\u00d7" + count).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
		text.setTransformation(new Transformation(null, null, new Vector3f(1.4F, 1.4F, 1.4F), null));
		level.addFreshEntity(text);
		flights.add(text);
		soon(player, 24, p -> {
			text.discard();
			flights.remove(text);
		});
	}

	/**
	 * Over the chest, the haul so far: up to three of what is waiting, slowly turning, and "+N" for
	 * the rest. What is still on its way is not in it yet.
	 */
	void refreshHaul(ServerPlayer player) {
		clearHaul();
		Visit visit = Visit.of(player);
		if (visit == null || !level.getBlockState(prizeMarker()).is(Blocks.CHEST)) return;
		List<ItemStack> waiting = new ArrayList<>();
		for (ItemStack stack : visit.prizes()) waiting.add(stack.copy());
		List<ItemStack> notHere = new ArrayList<>(flying);
		notHere.addAll(toDeliver);
		for (ItemStack still : notHere) {
			for (int i = 0; i < waiting.size(); i++) {
				if (ItemStack.matches(waiting.get(i), still)) {
					waiting.remove(i);
					break;
				}
			}
		}
		if (visit.prizeXp() > 0 && xpToDeliver == 0) waiting.add(new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE));
		int shown = Math.min(3, waiting.size());
		Vec3 over = chestTop().add(0, 0.75, 0);
		for (int i = 0; i < shown; i++) {
			Display.ItemDisplay icon = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (icon == null) continue;
			double x = over.x + (i - (shown - 1) / 2.0) * 0.45;
			icon.snapTo(x, over.y, over.z, 0, 0);
			icon.setItemStack(waiting.get(i).copyWithCount(1));
			icon.setItemTransform(ItemDisplayContext.GROUND);
			icon.setTransformation(new Transformation(null, null, new Vector3f(0.7F, 0.7F, 0.7F), null));
			level.addFreshEntity(icon);
			haul.add(icon);
			haulShown.add(waiting.get(i).copy());
		}
		if (waiting.size() > shown) {
			Display.TextDisplay more = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (more != null) {
				more.snapTo(over.x, over.y + 0.4, over.z, 0, 0);
				more.setBillboardConstraints(Display.BillboardConstraints.CENTER);
				more.setText(Component.literal("+" + (waiting.size() - shown)).withStyle(net.minecraft.ChatFormatting.GOLD));
				level.addFreshEntity(more);
				haul.add(more);
			}
		}
	}

	void clearHaul() {
		haul.forEach(Entity::discard);
		haul.clear();
		haulShown.clear();
	}

	/** The haul turns slowly, a quarter turn every two seconds, drawn smoothly between. */
	private void turnHaul() {
		float angle = (float) ((level.getGameTime() / 40) % 4) * (float) (Math.PI / 2);
		for (Display display : haul) {
			if (!(display instanceof Display.ItemDisplay)) continue;
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(40);
			display.setTransformation(new Transformation(null, new org.joml.Quaternionf().rotateY(angle + (float) (Math.PI / 2)),
				new Vector3f(0.7F, 0.7F, 0.7F), null));
		}
	}

	/** On the way home: the haul lifts off the chest and goes to the child. */
	void haulHome(ServerPlayer player) {
		List<Display> going = new ArrayList<>(haul);
		haul.clear();
		haulShown.clear();
		for (Display display : going) {
			Vec3 from = display.position();
			for (int i = 1; i <= 12; i++) {
				double t = i / 12.0;
				int tick = i;
				soon(player, tick, p -> {
					Vec3 to = p.position().add(0, 1.0, 0);
					Vec3 at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 0.8, 0);
					display.snapTo(at.x, at.y, at.z, 0, 0);
				});
			}
			soon(player, 13, p -> display.discard());
			flights.add(display);
		}
	}

	/** Whether there is prize news to tell, clearing it: asked by the narration as it gets there. */
	boolean takePrizeNews() {
		boolean news = prizeNews;
		prizeNews = false;
		newsLine = null;
		return news;
	}

	/** What the prize chest shows: the prizes waiting, and experience as a bottle. */
	public List<ItemStack> chestContents(ServerPlayer player) {
		List<ItemStack> shown = new ArrayList<>();
		Visit visit = Visit.of(player);
		if (visit == null) return shown;
		for (ItemStack stack : visit.prizes()) shown.add(stack.copy());
		if (visit.prizeXp() > 0) shown.add(new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, visit.prizeXp()));
		return shown;
	}

	/** Now and then a sparkle over the chest, so it is noticed from across the room, and the haul turning. */
	void tick() {
		if (level.getGameTime() % 40 == 0 && !haul.isEmpty()) turnHaul();
		if (level.getGameTime() % 20 == 0 && level.getBlockState(prizeMarker()).is(Blocks.CHEST)) {
			Vec3 at = Vec3.atCenterOf(prizeMarker());
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.6, at.z, 4, 0.3, 0.2, 0.3, 0.0);
		}
	}

	/** On the way out: the haul, if any, goes home with the child. */
	void leaving(ServerPlayer player) {
		if (!haul.isEmpty()) haulHome(player);
	}

	/** Everything in the air and over the chest gone, and the chest with it. */
	void close() {
		clearHaul();
		flights.forEach(Entity::discard);
		flights.clear();
		if (level.getBlockState(prizeMarker()).is(Blocks.CHEST)) level.setBlock(prizeMarker(), Blocks.AIR.defaultBlockState(), FLAGS);
	}
}
