package justfatlard.phonics.mixin;

import justfatlard.phonics.puzzle.Punches;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A left click, heard on the server. See {@link Punches} for why this and not the attack-block
 * event, which an adventure-mode client never sends.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PunchMixin {

	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("TAIL"))
	private void phonics$punchSpeaks(ServerboundPunchPacket packet, CallbackInfo callback) {
		Punches.punched(player);
	}
}
