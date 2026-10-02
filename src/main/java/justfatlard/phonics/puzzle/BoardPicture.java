package justfatlard.phonics.puzzle;

import java.util.List;
import java.util.UUID;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** The picture of the word asked for, beside the word on the board, and the box that hears it clicked. */
final class BoardPicture {
	private final ServerLevel level;
	private @Nullable UUID picture;
	private @Nullable UUID pictureHit;

	BoardPicture(ServerLevel level) {
		this.level = level;
	}

	/**
	 * The target's picture, over the word on the board: an item display, which nothing can knock
	 * off, take or turn, always facing whoever looks at it.
	 */
	void show(Word target, List<BlockPos> row) {
		show(target, row, false);
	}

	/** The same, over the row's middle when {@code above}: a long word leaves no room to its left. */
	void show(Word target, List<BlockPos> row, boolean above) {
		this.above = above;
		remove();
		String card = Words.pictureCard(target);
		ItemStack shown = target.picture(level.registryAccess());
		if (card != null && !row.isEmpty()) {
			showCard(card, row);
			return;
		}
		if (shown.isEmpty() || row.isEmpty()) return;
		// Beside the word, to its left - the picture, then the word - at the word's own height, well
		// clear of a caption at the top of the screen at any size.
		Vec3 at = spot(row);
		Display.ItemDisplay display = of(level, shown, at);
		if (display == null) return;
		level.addFreshEntity(display);
		picture = display.getUUID();
		// A display cannot be clicked; an interaction box the picture's size in front of it can.
		// It takes no damage and no knockback: it only reports that it was clicked.
		net.minecraft.world.entity.Interaction hit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
		if (hit == null) return;
		hit.snapTo(at.x, at.y - 0.5, at.z, 0, 0);
		hit.setWidth(1.0F);
		hit.setHeight(1.0F);
		hit.setResponse(true);
		level.addFreshEntity(hit);
		pictureHit = hit.getUUID();
	}

	/**
	 * A picture card where an item would be: drawn on a panel by Pandorical, anchored to the click
	 * box, which a card needs anyway.
	 */
	private boolean above;

	/** Where the picture goes: to the left of the word, at its height, or over its middle. */
	private Vec3 spot(List<BlockPos> row) {
		BlockPos first = row.getFirst();
		if (!above) return new Vec3(first.getX() - 1.1, first.getY() + 0.5, first.getZ() + 0.6);
		BlockPos last = row.getLast();
		return new Vec3((first.getX() + last.getX()) / 2.0 + 0.5, first.getY() + 1.6, first.getZ() + 0.6);
	}

	private void showCard(String card, List<BlockPos> row) {
		Vec3 at = spot(row);
		net.minecraft.world.entity.Interaction hit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
		if (hit == null) return;
		hit.snapTo(at.x, at.y - 0.7, at.z, 0, 0);
		hit.setWidth(1.4F);
		hit.setHeight(1.4F);
		hit.setResponse(true);
		level.addFreshEntity(hit);
		pictureHit = hit.getUUID();
		cardShown = PictureCards.show(hit, card) ? card : null;
	}

	/** The picture card showing, if one is: for tests. */
	private @Nullable String cardShown;

	@Nullable String card() {
		return pictureHit == null ? null : cardShown;
	}

	/** The picture's click box, for tests and for the click events. */
	@Nullable UUID hit() {
		return pictureHit;
	}

	/**
	 * A picture of an item, as the inventory shows it: the GUI transform - a block's three-quarter
	 * view, a flat item flat - facing whoever looks. Turned half round, because a display faces the
	 * other way from a GUI slot, and a block seen from behind at the GUI's tilt shows its underside.
	 */
	static Display.@Nullable ItemDisplay of(ServerLevel level, ItemStack shown, Vec3 at) {
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) return null;
		display.snapTo(at.x, at.y, at.z, 0, 0);
		display.setItemStack(shown.copy());
		display.setItemTransform(ItemDisplayContext.GUI);
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setTransformation(pictureTransform(shown, null, 1.8F));
		return display;
	}

	/**
	 * Turned to face the child, and a carpet tipped up besides: drawn as an inventory item draws
	 * it, a carpet is a thin edge, and tipped it is the square of cloth it is.
	 */
	private static Transformation pictureTransform(ItemStack shown, @Nullable Vector3f lift, float scale) {
		org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateY((float) Math.PI);
		if (shown.getItem() instanceof net.minecraft.world.item.BlockItem block
				&& block.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock) {
			turn.rotateX((float) Math.toRadians(CARPET_TILT));
		}
		return new Transformation(lift, turn, new Vector3f(scale, scale, scale), null);
	}

	private static final float CARPET_TILT = -50.0F;

	void remove() {
		for (UUID id : new UUID[] {picture, pictureHit}) {
			if (id == null) continue;
			Entity old = level.getEntity(id);
			if (old != null) old.discard();
		}
		picture = null;
		pictureHit = null;
		cardShown = null;
	}

	/** The picture over the board, if there is one. */
	@Nullable Entity shown() {
		return picture == null ? null : level.getEntity(picture);
	}

	/** A little bounce, as the prompt is said again. */
	void bounce(ServerPlayer player, Stage stage) {
		Entity shown = shown();
		if (shown instanceof Display.ItemDisplay display) {
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(4);
			display.setTransformation(pictureTransform(display.getItemStack(), new Vector3f(0, 0.15F, 0), 2.1F));
			stage.later(player, 6, p -> {
				display.setTransformationInterpolationDelay(0);
				display.setTransformationInterpolationDuration(6);
				display.setTransformation(pictureTransform(display.getItemStack(), null, 1.8F));
			});
		}
	}
}
