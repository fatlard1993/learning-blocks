package justfatlard.phonics.plush;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** One plushie in the hand: an item of its own, so it has its own name and icon, put down as that kind. */
public class PlushItem extends BlockItem {
	private final Plush kind;

	public PlushItem(Block block, Plush kind, Properties properties) {
		super(block, properties);
		this.kind = kind;
	}

	public Plush kind() {
		return kind;
	}

	public static @Nullable Plush kindOf(ItemStack stack) {
		return stack.getItem() instanceof PlushItem plush ? plush.kind : null;
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		return state == null ? null : state.setValue(PlushBlock.KIND, kind);
	}
}
