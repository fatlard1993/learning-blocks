package justfatlard.learning_blocks.mixin;

import justfatlard.learning_blocks.classroom.Places;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nothing picked up in the classroom counts towards advancements or unlocks recipes.
 *
 * <p>The hat HAT puts on a child's head is a leather helmet, and holding one unlocks a recipe, which
 * pops a "New Recipe Unlocked" toast over the room. More to the point, nothing from the classroom
 * comes home with a child, and an unlocked recipe or an advancement is something that would. What
 * is handed back at the door on the way out does not count either, since it was theirs already.
 */
@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryChangeTriggerMixin {

	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("HEAD"), cancellable = true)
	private void learn$nothingCountsInClass(ServerPlayer player, Inventory inventory, ItemStack changed, CallbackInfo callback) {
		if (Places.isClassroom(player.level())) callback.cancel();
	}
}
