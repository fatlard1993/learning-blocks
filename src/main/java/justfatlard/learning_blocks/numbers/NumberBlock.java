package justfatlard.learning_blocks.numbers;

import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Every wooden tile that is not a letter: the numbers 0 to 20 (a numeral front and back, the same
 * amount in dots on the sides; see {@code generate_numbers.py}), the symbol tiles plus, minus and
 * equals, and Logic's tick and cross. Right-clicked anywhere, a number says itself. In the
 * classroom, the room answers a click on one itself.
 */
public class NumberBlock extends Block {
	public static final int HIGHEST = 20;
	/** After the numbers, the symbol tiles: plus, minus, equals. */
	public static final int PLUS = 21;
	public static final int MINUS = 22;
	public static final int EQUALS = 23;
	/** Logic's answers: a tick (yes, the same) and a cross (no, different). */
	public static final int YES = 24;
	public static final int NO = 25;
	public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, NO);

	public NumberBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NUMBER, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NUMBER);
	}

	public BlockState showing(int number) {
		return defaultBlockState().setValue(NUMBER, Math.max(0, Math.min(NO, number)));
	}

	public static String soundOf(int number) {
		return switch (number) {
			case PLUS -> "word/plus";
			case MINUS -> "word/minus";
			case EQUALS -> "word/equals";
			case YES -> "word/same";
			case NO -> "word/different";
			default -> "number/" + number;
		};
	}

	/** Knocked loose in a round answered with number tiles: into the child's hand, as a letter goes. */
	@Override
	public void playerDestroy(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos pos, BlockState state,
			net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity, net.minecraft.world.item.ItemStack tool) {
		if (!justfatlard.learning_blocks.puzzle.Rooms.numberBroken(player, pos, state.getValue(NUMBER))) {
			super.playerDestroy(level, player, pos, state, blockEntity, tool);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel) Sounds.playAt(level, pos, soundOf(state.getValue(NUMBER)));
		return InteractionResult.SUCCESS;
	}
}
