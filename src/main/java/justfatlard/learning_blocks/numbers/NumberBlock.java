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
 * A wooden number tile, like the letter tiles: a numeral on the front and back, the same amount in
 * dots on the sides (see {@code generate_numbers.py}). Right-clicked anywhere, it says its number.
 * In the classroom, the room answers a click on one itself.
 */
public class NumberBlock extends Block {
	public static final int HIGHEST = 20;
	/** After the numbers, the symbol tiles: plus, minus, equals. */
	public static final int PLUS = 21;
	public static final int MINUS = 22;
	public static final int EQUALS = 23;
	public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, EQUALS);

	public NumberBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NUMBER, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NUMBER);
	}

	public BlockState showing(int number) {
		return defaultBlockState().setValue(NUMBER, Math.max(0, Math.min(EQUALS, number)));
	}

	/** What a number says: {@code number/3}; a symbol, its word: {@code word/plus}. */
	public static String soundOf(int number) {
		return switch (number) {
			case PLUS -> "word/plus";
			case MINUS -> "word/minus";
			case EQUALS -> "word/equals";
			default -> "number/" + number;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel) Sounds.playAt(level, pos, soundOf(state.getValue(NUMBER)));
		return InteractionResult.SUCCESS;
	}
}
