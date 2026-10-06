package justfatlard.learning_blocks.plush;

import justfatlard.learning_blocks.ForTests;
import justfatlard.learning_blocks.sound.Sounds;
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
		registerDefaultState(stateDefinition.any().setValue(KIND, Plush.OWL).setValue(FACING, Direction.NORTH).setValue(justfatlard.learning_blocks.play.Play.HIDDEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, FACING, justfatlard.learning_blocks.play.Play.HIDDEN);
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

	/**
	 * A right-click: played with - a soft squish down and bounce up - saying its word; sneaking, what is in or on it comes back
	 * out. Anything carried in or on it goes along.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer reader) {
			if (player.isShiftKeyDown() && justfatlard.learning_blocks.play.Play.takeOut(server, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND)) {
				return InteractionResult.SUCCESS;
			}
			if (justfatlard.learning_blocks.play.Play.playing(server, pos) || state.getValue(justfatlard.learning_blocks.play.Play.HIDDEN)) return InteractionResult.SUCCESS;
			squeak(server, pos, state.getValue(KIND), reader);
			if (justfatlard.learning_blocks.play.Play.play(server, pos, state, justfatlard.learning_blocks.play.Anims.SQUISH)) {
				// A squeeze, and it loves you back.
				justfatlard.learning_blocks.relate.Thing thing = justfatlard.learning_blocks.relate.Relations.forBlock(state);
				float height = thing != null ? thing.height() : 0.6F;
				justfatlard.learning_blocks.feel.Feelings.showAt(server, net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).add(0, height, 0),
					server.getRandom().nextInt(3) == 0 ? justfatlard.learning_blocks.feel.Feelings.Face.GLAD : justfatlard.learning_blocks.feel.Feelings.Face.LOVE, 30);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (level instanceof ServerLevel server && justfatlard.learning_blocks.play.Play.put(server, pos, state, player, hand)) return InteractionResult.SUCCESS;
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** The tick after a play: shown again, whatever happened (see {@link justfatlard.learning_blocks.play.Play#recover}). */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
		justfatlard.learning_blocks.play.Play.recover(state, level, pos);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		if (!level.getBlockState(pos).is(this)) justfatlard.learning_blocks.play.Play.removed(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	@ForTests
	public static volatile @Nullable Plush lastSqueezed;

	public static void squeak(ServerLevel level, BlockPos pos, Plush plush, ServerPlayer player) {
		lastSqueezed = plush;
		// The squish, as it squashes down; then its word.
		Sounds.effectAt(level, pos, Sounds.PLUSH_SQUISH);
		level.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
		String word = "word/" + plush.word();
		// Never across a round's telling: a squeeze mid-answer would cut it off.
		if (Sounds.isRecorded(word)) justfatlard.learning_blocks.puzzle.Rooms.sayQuietly(player, word);
	}
}
