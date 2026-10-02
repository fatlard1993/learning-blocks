package justfatlard.phonics.toy;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** One toy in the hand: an item of its own, so it has its own name and icon, put down as that kind. */
public class ToyItem extends BlockItem {
	private final Toy kind;

	public ToyItem(Block block, Toy kind, Properties properties) {
		super(block, properties);
		this.kind = kind;
	}

	public Toy kind() {
		return kind;
	}

	public static @Nullable Toy kindOf(ItemStack stack) {
		return stack.getItem() instanceof ToyItem toy ? toy.kind : null;
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		return state == null ? null : state.setValue(ToyBlock.KIND, kind);
	}
}
