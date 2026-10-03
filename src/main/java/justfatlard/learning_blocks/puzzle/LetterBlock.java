package justfatlard.learning_blocks.puzzle;

import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * A wooden letter tile. The block does nothing by itself: what a click on one means depends on
 * where in a room it stands, so the room answers clicks, and this only has to look right: a
 * letter, and whether it is lit.
 */
public class LetterBlock extends Block {
	public static final EnumProperty<Letter> LETTER = EnumProperty.create("letter", Letter.class);
	/** Lit while its sound is being said, so a child can see which letter they are hearing. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public LetterBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LETTER, Letter.BLANK).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LETTER, LIT);
	}

	public BlockState showing(Letter letter) {
		return defaultBlockState().setValue(LETTER, letter);
	}

	/**
	 * The sound this tile makes, by the grapheme it shows rather than its letters: a tile for "sh"
	 * says /sh/, not /s/ then /h/. Every letter-block sound comes through here, so a multi-letter
	 * tile only has to report its grapheme for all of them to be right. Null for the gap.
	 */
	public static @Nullable String phoneme(BlockState state) {
		if (!(state.getBlock() instanceof LetterBlock)) return null;
		Letter letter = state.getValue(LETTER);
		if (letter.isCapital()) return nameOf(letter.lower());
		return letter.isLetter() ? "phoneme/" + letter.grapheme() : null;
	}

	/** What this tile says: its sound, or its name for a capital. */
	public static String soundOf(Letter letter) {
		return letter.isCapital() ? nameOf(letter.lower()) : "phoneme/" + letter.grapheme();
	}

	/** A letter's name: a vowel's long sound, which is its name, and a consonant's own recording. */
	public static String nameOf(Letter letter) {
		return letter.isVowel() ? "phoneme/" + letter.grapheme() + "_long" : "name/" + letter.grapheme();
	}

	/** Placed by anybody, anywhere: it says its sound from where it now stands. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		String sound = phoneme(state);
		if (sound != null) Sounds.playAt(level, pos, sound);
	}

	/**
	 * Broken by a player: its sound, as it goes - unless the punch that began the break said it a
	 * moment ago, since a quick break is one knock on the tile and should be one sound.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		String sound = phoneme(state);
		if (sound != null && !Punches.saidLately(pos, level.getGameTime())) Sounds.playAt(level, pos, sound);
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Blown up: the same. */
	@Override
	public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
		super.wasExploded(level, pos, explosion);
		String sound = phoneme(level.getBlockState(pos));
		if (sound != null) Sounds.playAt(level, pos, sound);
	}

	public BlockState showing(Letter letter, boolean lit) {
		return defaultBlockState().setValue(LETTER, letter).setValue(LIT, lit);
	}
}
