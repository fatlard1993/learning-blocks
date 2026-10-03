package justfatlard.learning_blocks.classroom;

import java.util.ArrayList;
import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A child's time in the classroom, kept on the player: where they came from, and everything that
 * was set aside at the door so it could be handed back at the other one.
 *
 * <p>On the player, persistent and carried through deaths, because the pack and its stash must
 * never disagree: the player's own save holds both, written together, so a crash between the two
 * doors can lose neither and double neither. It is written before anything is taken, so a visit
 * that exists is always a visit with somewhere to put things back from.
 *
 * <p>Effects are part of it. PvP Dimensions, which this is slimmed down from, puts a pack and a
 * level aside and clears effects on the way out; a child who went in with a potion running should
 * come out with it still running.
 *
 * <p>Prizes earned inside wait here too, never in the classroom inventory: they are saved in the
 * same record as the stash, so there is no moment at which a prize exists in two places or none.
 * They are handed over only after the stash is back, at home, by whichever way the visit ends.
 */
public record Visit(Home home, List<Slot> kit, Experience experience, String gameMode, List<MobEffectInstance> effects,
		List<ItemStack> prizes, int prizeXp, int lessons, Vitals vitals) {

	/** The cursor's slot in a kit: what was held on the pointer at the door. */
	public static final int CURSOR = -1;
	/** The 2x2 crafting grid's first slot in a kit; the other three follow, downwards. */
	public static final int CRAFTING = -2;

	/**
	 * Health, hunger, air and burning at the door, handed back at the other one, so a visit is not
	 * a free heal. A negative health is "not saved": visits begun before this was kept.
	 */
	public record Vitals(float health, int food, float saturation, int air, int fire) {
		public static final Vitals NONE = new Vitals(-1, 20, 5, 300, 0);
		static final Codec<Vitals> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("health").forGetter(Vitals::health),
			Codec.INT.fieldOf("food").forGetter(Vitals::food),
			Codec.FLOAT.fieldOf("saturation").forGetter(Vitals::saturation),
			Codec.INT.fieldOf("air").forGetter(Vitals::air),
			Codec.INT.fieldOf("fire").forGetter(Vitals::fire)
		).apply(instance, Vitals::new));

		public static Vitals of(ServerPlayer player) {
			return new Vitals(player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
				player.getAirSupply(), player.getRemainingFireTicks());
		}

		public void restore(ServerPlayer player) {
			if (health < 0) return;
			player.setHealth(Math.max(1.0F, Math.min(health, player.getMaxHealth())));
			player.getFoodData().setFoodLevel(food);
			player.getFoodData().setSaturation(saturation);
			player.setAirSupply(air);
			player.setRemainingFireTicks(fire);
		}
	}

	/** Where to put somebody back. */
	public record Home(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
		static final Codec<Home> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Home::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(Home::x),
			Codec.DOUBLE.fieldOf("y").forGetter(Home::y),
			Codec.DOUBLE.fieldOf("z").forGetter(Home::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(Home::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(Home::pitch)
		).apply(instance, Home::new));

		public static Home of(ServerPlayer player) {
			return new Home(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
				player.getYRot(), player.getXRot());
		}
	}

	/** One inventory slot and what was in it, armour and off hand included. */
	public record Slot(int slot, ItemStack stack) {
		public static final Codec<Slot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("slot").forGetter(Slot::slot),
			// One item this server cannot read never takes the rest of the stash with it.
			LenientItems.CODEC.fieldOf("item").forGetter(Slot::stack)
		).apply(instance, Slot::new));
	}

	public record Experience(int levels, float progress, int total) {
		static final Codec<Experience> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("levels").forGetter(Experience::levels),
			Codec.FLOAT.fieldOf("progress").forGetter(Experience::progress),
			Codec.INT.fieldOf("total").forGetter(Experience::total)
		).apply(instance, Experience::new));

		public static Experience of(ServerPlayer player) {
			return new Experience(player.experienceLevel, player.experienceProgress, player.totalExperience);
		}

		/**
		 * Set outright rather than given, and sent outright: the game only resends experience when
		 * the total changes, and a restore that lands on the same total as the room's (nought, for
		 * somebody who went in with a fraction of a level) would otherwise leave the bar wrong.
		 */
		public void restore(ServerPlayer player) {
			player.experienceLevel = levels;
			player.experienceProgress = progress;
			player.totalExperience = total;
			player.connection.send(new ClientboundSetExperiencePacket(progress, total, levels));
		}
	}

	public static final Codec<Visit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Home.CODEC.fieldOf("home").forGetter(Visit::home),
		Slot.CODEC.listOf().fieldOf("kit").forGetter(Visit::kit),
		Experience.CODEC.fieldOf("experience").forGetter(Visit::experience),
		Codec.STRING.fieldOf("game_mode").forGetter(Visit::gameMode),
		MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(Visit::effects),
		// Absent on visits begun before prizes existed, which have none waiting.
		LenientItems.CODEC.listOf().optionalFieldOf("prizes", List.of()).forGetter(Visit::prizes),
		Codec.INT.optionalFieldOf("prize_xp", 0).forGetter(Visit::prizeXp),
		// Lessons an operator sent this child for, still to do before the door opens.
		Codec.INT.optionalFieldOf("lessons", 0).forGetter(Visit::lessons),
		Vitals.CODEC.optionalFieldOf("vitals", Vitals.NONE).forGetter(Visit::vitals)
	).apply(instance, Visit::new));

	public static final AttachmentType<Visit> ATTACHMENT = AttachmentRegistry.<Visit>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.buildAndRegister(LearningBlocks.id("visit"));

	/** The same, as saved before the rename; moved to the new one as the player joins. */
	private static final AttachmentType<Visit> OLD_ATTACHMENT = AttachmentRegistry.<Visit>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.buildAndRegister(justfatlard.learning_blocks.migrate.OldNames.old("visit"));

	public static void migrate(ServerPlayer player) {
		justfatlard.learning_blocks.migrate.OldNames.move(player, OLD_ATTACHMENT, ATTACHMENT, CODEC, player.level().registryAccess(),
			player.getGameProfile().name());
	}

	/** Loads the class, which is what registers the attachment; it must happen at init. */
	public static void init() {}

	public static @Nullable Visit of(ServerPlayer player) {
		return player.getAttached(ATTACHMENT);
	}

	public static void set(ServerPlayer player, @Nullable Visit visit) {
		if (visit == null) player.removeAttached(ATTACHMENT);
		else player.setAttached(ATTACHMENT, visit);
	}

	/** Everything this player is carrying, wearing and under, as it is right now. */
	public static Visit snapshot(ServerPlayer player) {
		List<Slot> kit = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) kit.add(new Slot(slot, stack.copy()));
		}
		// What is on the pointer, and in the little crafting grid, is theirs too.
		ItemStack carried = player.containerMenu.getCarried();
		if (!carried.isEmpty()) kit.add(new Slot(CURSOR, carried.copy()));
		var grid = player.inventoryMenu.getCraftSlots();
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (!stack.isEmpty()) kit.add(new Slot(CRAFTING - i, stack.copy()));
		}
		List<MobEffectInstance> effects = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) effects.add(new MobEffectInstance(effect));
		return new Visit(Home.of(player), List.copyOf(kit), Experience.of(player),
			player.gameMode().getName(), List.copyOf(effects), List.of(), 0, 0, Vitals.of(player));
	}

	/** This visit, with more waiting to be handed over at home. */
	public Visit withPrizes(List<ItemStack> more, int moreXp) {
		List<ItemStack> all = new ArrayList<>(prizes);
		for (ItemStack stack : more) if (!stack.isEmpty()) all.add(stack.copy());
		return new Visit(home, kit, experience, gameMode, effects, List.copyOf(all), prizeXp + moreXp, lessons, vitals);
	}

	/** This visit, with this many lessons still to do before the door opens. */
	public Visit withLessons(int remaining) {
		return new Visit(home, kit, experience, gameMode, effects, prizes, prizeXp, Math.max(0, remaining), vitals);
	}

	public boolean hasPrizes() {
		return !prizes.isEmpty() || prizeXp > 0;
	}

	/** The game mode to hand back, or survival if the saved name no longer means anything. */
	public GameType savedGameMode() {
		return GameType.byName(gameMode, GameType.SURVIVAL);
	}

	/** Lay the kit back where it came from; whatever finds its slot taken goes wherever there is room. */
	public void giveKitBack(ServerPlayer player) {
		lay(player, kit);
	}

	/** Every slot of this player's inventory that holds anything, as it is now. */
	public static List<Slot> pack(ServerPlayer player) {
		List<Slot> slots = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) slots.add(new Slot(slot, stack.copy()));
		}
		return List.copyOf(slots);
	}

	/** Empty the pointer and the crafting grid, once they are in the kit. */
	public static void clearHands(ServerPlayer player) {
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.inventoryMenu.getCraftSlots().clearContent();
		player.inventoryMenu.slotsChanged(player.inventoryMenu.getCraftSlots());
	}

	/** Lay these slots out in the inventory; whatever finds its slot taken goes wherever there is room. */
	public static void lay(ServerPlayer player, List<Slot> slots) {
		Inventory inventory = player.getInventory();
		for (Slot slot : slots) {
			ItemStack stack = slot.stack().copy();
			if (slot.slot() >= 0 && slot.slot() < inventory.getContainerSize() && inventory.getItem(slot.slot()).isEmpty()) {
				inventory.setItem(slot.slot(), stack);
			} else {
				inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
	}
}
