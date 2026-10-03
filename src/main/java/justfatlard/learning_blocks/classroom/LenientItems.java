package justfatlard.learning_blocks.classroom;

import java.util.function.Function;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Saved things that must survive a missing mod. One item this server cannot read - a modded item
 * whose mod was removed - must never take a child's whole stash, prizes or history with it.
 *
 * <p>An item that will not decode becomes a stand-in, a piece of paper named "Missing item: <id>",
 * carrying what was saved, untouched; saved again, it is written back exactly as it was, so the
 * item returns the day its mod does. A whole record that will not decode is kept as it was saved,
 * and written back so, rather than replaced with an empty one.
 */
public final class LenientItems {
	private LenientItems() {}

	/** Where a stand-in keeps the item it stands for, as it was saved. */
	public static final String RAW = "phonics_missing_item";

	public static final Codec<ItemStack> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<Pair<ItemStack, T>> read = ItemStack.CODEC.decode(ops, input);
			if (read.isSuccess()) return read;
			Tag raw = new Dynamic<>(ops, input).convert(NbtOps.INSTANCE).getValue();
			String id = raw instanceof CompoundTag compound ? compound.getStringOr("id", "?") : "?";
			LearningBlocks.LOGGER.warn("[{}] A saved item could not be read ({}); kept as a stand-in until it can be: {}", LearningBlocks.MOD_ID,
				read.error().map(error -> error.message()).orElse("unknown"), raw);
			return DataResult.success(Pair.of(standIn(id, raw), ops.empty()));
		}

		@Override
		public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
			Tag raw = rawOf(stack);
			if (raw != null) return DataResult.success(NbtOps.INSTANCE.convertTo(ops, raw));
			return ItemStack.CODEC.encode(stack, ops, prefix);
		}
	};

	/** A piece of paper for an item this server cannot make, holding it as it was saved. */
	public static ItemStack standIn(String id, Tag raw) {
		ItemStack paper = new ItemStack(Items.PAPER);
		paper.set(DataComponents.CUSTOM_NAME, Component.literal("Missing item: " + id));
		CompoundTag data = new CompoundTag();
		data.put(RAW, raw.copy());
		paper.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		return paper;
	}

	/** The saved item a stand-in holds, or null for an ordinary item. */
	public static @Nullable Tag rawOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !stack.is(Items.PAPER)) return null;
		CompoundTag tag = data.copyTag();
		return tag.contains(RAW) ? tag.get(RAW) : null;
	}

	/**
	 * A record codec that never loses what was saved: if the record will not decode, {@code broken}
	 * makes a stand-in record holding the saved data, and {@code rawOf} hands that data back to be
	 * written again as it was.
	 */
	public static <A> Codec<A> keeping(Codec<A> inner, String what, Function<Tag, A> broken, Function<A, @Nullable Tag> rawOf) {
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
				DataResult<Pair<A, T>> read;
				try {
					read = inner.decode(ops, input);
				} catch (RuntimeException | StackOverflowError e) {
					read = DataResult.error(() -> String.valueOf(e));
				}
				if (read.isSuccess()) return read;
				Tag raw = new Dynamic<>(ops, input).convert(NbtOps.INSTANCE).getValue();
				LearningBlocks.LOGGER.error("[{}] {} could not be read, and is kept exactly as saved until it can be ({}): {}", LearningBlocks.MOD_ID,
					what, read.error().map(error -> error.message()).orElse("unknown"), raw);
				return DataResult.success(Pair.of(broken.apply(raw), ops.empty()));
			}

			@Override
			public <T> DataResult<T> encode(A value, DynamicOps<T> ops, T prefix) {
				Tag raw = rawOf.apply(value);
				if (raw != null) return DataResult.success(NbtOps.INSTANCE.convertTo(ops, raw));
				return inner.encode(value, ops, prefix);
			}
		};
	}
}
