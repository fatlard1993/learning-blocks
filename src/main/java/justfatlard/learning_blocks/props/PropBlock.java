package justfatlard.learning_blocks.props;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A prop put down: a bin, a bathtub, a cardboard box. It faces whoever put it there and takes up
 * only its own shape.
 */
public class PropBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final VoxelShape north;

	/** {@code box} in model units, as the model has it facing north: x0, y0, z0, x1, y1, z1. */
	public PropBlock(Properties properties, double[] box) {
		super(properties);
		this.north = Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return rotate(north, state.getValue(FACING));
	}

	@Override
	protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
			BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (level instanceof net.minecraft.server.level.ServerLevel server && justfatlard.learning_blocks.play.Play.put(server, pos, state, player, hand)) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
			net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
		if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return net.minecraft.world.InteractionResult.SUCCESS;
		if (player.isShiftKeyDown() && justfatlard.learning_blocks.play.Play.takeOut(server, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND)) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		var contents = justfatlard.learning_blocks.play.Play.contentsAt(server, pos);
		if (contents == null) return net.minecraft.world.InteractionResult.PASS;
		justfatlard.learning_blocks.play.Play.playContents(server, pos, contents, justfatlard.learning_blocks.play.Anims.FLOAT);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
		if (!level.getBlockState(pos).is(this)) justfatlard.learning_blocks.play.Play.removed(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	/** The north-facing shape turned to face this way, as the blockstate turns the model. */
	static VoxelShape rotate(VoxelShape shape, Direction facing) {
		VoxelShape[] out = {Shapes.empty()};
		shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
			VoxelShape box = switch (facing) {
				case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
				case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
				case WEST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
				default -> Shapes.box(x0, y0, z0, x1, y1, z1);
			};
			out[0] = Shapes.or(out[0], box);
		});
		return out[0];
	}
}
