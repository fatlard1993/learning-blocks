package justfatlard.phonics.puzzle;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a word's outcome, or a scene, may do to the room it happens in: send things out of the
 * button, put blocks down, move creatures and the child about. Nothing about puzzles, tiles or
 * rounds; {@link Dispenser} is the room's.
 */
public interface Stage {
	/** Where the room's floor is centred. */
	BlockPos origin();

	/** Where a child is set down, looking at the board. */
	Vec3 spawn();

	/** Just out from the button's face, where everything a word does comes from. */
	Vec3 mouth();

	/** Later, for this child, only while this room and this round are still going. */
	void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> what);

	/** The creatures words have brought that are still here. */
	List<Entity> creatures();

	void dispenseCreature(ServerPlayer player, Entity creature);

	/**
	 * A character, out of the button in a hop to stand still in front of the child, facing them:
	 * no mind of its own, so it can feel and gesture. {@code landed} when it is down; gone in a puff
	 * after {@code lasts}.
	 */
	void dispenseCharacter(ServerPlayer player, Entity character, java.util.function.Consumer<Entity> landed, int lasts);

	void dispenseItem(ServerPlayer player, ItemStack stack);

	void dispenseWorn(ServerPlayer player, ItemStack stack, Consumer<ServerPlayer> wear);

	void dispenseBlock(ServerPlayer player, BlockState state, boolean feet, int lasts);

	void dispenseEffect(ServerPlayer player, Consumer<ServerPlayer> arrive);

	void dispenseFirework(ServerPlayer player, ItemStack rocket, boolean dud);

	/** The button's click and puff, logged as sending this out. */
	void puffFromButton(String what);

	/** A block put down here, and taken up again at the next round. */
	void placeAt(BlockPos at, BlockState state);

	ItemEntity throwItem(Vec3 from, Vec3 target, ItemStack stack, int ticks);

	void act(ServerPlayer player, Entity creature, String act, int lasts);

	void creatureDoes(ServerPlayer player, Entity creature, String what);

	void scatterCreatures(ServerPlayer player);

	void hut(ServerPlayer player, BlockPos at, int lasts, boolean forScene);

	/** The same, of these blocks: a wool tent, a brick house. */
	void hut(ServerPlayer player, BlockPos at, int lasts, boolean forScene, BlockState walls);

	void throwSnowball(ServerPlayer player);

	void bonk(ServerPlayer player, List<ItemStack> stacks, Holder<SoundEvent> sound);

	void spin(ServerPlayer player, float turns, int ticks);

	void roll(ServerPlayer player, ItemStack stack, int ticks);

	void scale(ServerPlayer player, double amount, int duration);

	/** A lightning rod out of the button, struck by a little bolt of sparks, gone after {@code lasts}. */
	void strike(ServerPlayer player, int lasts);
}
