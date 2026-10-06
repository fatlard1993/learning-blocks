package justfatlard.learning_blocks.mixin;

import justfatlard.learning_blocks.classroom.Travel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Other mods' teleports (a teleport menu, a trip to the last death, a geode door) do not cross the
 * classroom's edge: they fail, as a teleport to nowhere does, and the child stays for their lessons.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerTeleportMixin {
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
		at = @At("HEAD"), cancellable = true)
	private void learn$stayInTheClassroom(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (Travel.mayCross(player, transition.newLevel())) return;
		player.sendSystemMessage(Component.literal("The classroom's way out is the portal"), true);
		callback.setReturnValue(null);
	}
}
