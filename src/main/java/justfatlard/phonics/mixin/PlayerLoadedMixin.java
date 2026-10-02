package justfatlard.phonics.mixin;

import justfatlard.phonics.puzzle.Rooms;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client saying it has finished loading the level: sent on joining and again after every
 * change of dimension, which is how the classroom knows the child can see and hear it yet. The
 * server's own flag for this only tracks joins and deaths, so the packet itself is the signal.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerLoadedMixin {

	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleAcceptPlayerLoad", at = @At("TAIL"))
	private void phonics$heardLoaded(ServerboundPlayerLoadedPacket packet, CallbackInfo callback) {
		Rooms.clientLoaded(player);
	}
}
