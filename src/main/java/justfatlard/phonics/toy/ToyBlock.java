package justfatlard.phonics.toy;

import justfatlard.phonics.narration.Narration;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A toy, put down: under a block, facing whoever put it there. A right-click gives its own little
 * noise (a toot, a ding, a squeak) and a puff, and it says its word. Broken, it comes back as itself.
 */
public class ToyBlock extends Block {
	public static final EnumProperty<Toy> KIND = EnumProperty.create("kind", Toy.class);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 11, 14);

	public ToyBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, Toy.DRUM).setValue(FACING, Direction.NORTH).setValue(justfatlard.phonics.play.Play.HIDDEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, FACING, justfatlard.phonics.play.Play.HIDDEN);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Picked with the middle button: this one, not whichever toy the block's item is. */
	@Override
	protected net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, boolean data) {
		return Toys.stack(state.getValue(KIND));
	}

	/**
	 * A right-click: played with - its own little show - saying its word; sneaking, what is in or on it comes back
	 * out. Anything carried in or on it goes along.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer reader) {
			if (player.isShiftKeyDown() && justfatlard.phonics.play.Play.takeOut(server, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND)) {
				return InteractionResult.SUCCESS;
			}
			if (justfatlard.phonics.play.Play.playing(server, pos) || state.getValue(justfatlard.phonics.play.Play.HIDDEN)) return InteractionResult.SUCCESS;
			squeak(server, pos, state.getValue(KIND), reader);
			justfatlard.phonics.play.Play.play(server, pos, state, justfatlard.phonics.play.Anims.of(state.getValue(KIND)));
		}
		return InteractionResult.SUCCESS;
	}

	/** A toy or plushie held and used on this one: in, on or onto it, if it fits. */
	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (level instanceof ServerLevel server && justfatlard.phonics.play.Play.put(server, pos, state, player, hand)) return InteractionResult.SUCCESS;
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** The tick after a play: shown again, whatever happened (see {@link justfatlard.phonics.play.Play#recover}). */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
		justfatlard.phonics.play.Play.recover(state, level, pos);
	}

	/** Gone: whatever was in or on it drops, and anything playing it stops. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		if (!level.getBlockState(pos).is(this)) justfatlard.phonics.play.Play.removed(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	/** The last toy squeezed, by anyone: for tests. */
	public static volatile @Nullable Toy lastSqueezed;

	/** Its own little noise, a puff, and its word, with its caption, for whoever squeezed it. */
	public static void squeak(ServerLevel level, BlockPos pos, Toy toy, ServerPlayer player) {
		lastSqueezed = toy;
		level.playSound(null, pos, toy.sound.event, SoundSource.BLOCKS, 0.8F, toy.sound.pitch);
		level.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
		String word = "word/" + toy.word();
		if (Sounds.isRecorded(word)) Narrator.play(player, new Narration().say(word));
	}
}
