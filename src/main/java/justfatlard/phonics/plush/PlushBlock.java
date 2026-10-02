package justfatlard.phonics.plush;

import justfatlard.phonics.narration.Narration;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
 * A plushie, put down: about half a block, facing whoever put it there. A right-click gives a soft
 * squeak and a little puff, and it says its word. Broken, it comes back as itself.
 */
public class PlushBlock extends Block {
	public static final EnumProperty<Plush> KIND = EnumProperty.create("kind", Plush.class);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);

	public PlushBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, Plush.OWL).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Picked with the middle button: this one, not whichever plushie the block's item is. */
	@Override
	protected net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, boolean data) {
		return Plushies.stack(state.getValue(KIND));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer reader) squeak(server, pos, state.getValue(KIND), reader);
		return InteractionResult.SUCCESS;
	}

	/** The last plushie squeezed, by anyone: for tests. */
	public static volatile @Nullable Plush lastSqueezed;

	/** A squeak, a puff, and its word, with its caption, for whoever squeezed it. */
	public static void squeak(ServerLevel level, BlockPos pos, Plush plush, ServerPlayer player) {
		lastSqueezed = plush;
		level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 1.0F, 1.6F);
		level.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.5F, 1.9F);
		level.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
		String word = "word/" + plush.word();
		if (Sounds.isRecorded(word)) Narrator.play(player, new Narration().say(word));
	}
}
