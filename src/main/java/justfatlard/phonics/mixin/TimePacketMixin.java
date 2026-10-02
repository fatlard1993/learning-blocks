package justfatlard.phonics.mixin;

import justfatlard.phonics.classroom.Sky;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every time packet a child in the classroom is sent carries their own sky's time (see {@link Sky}). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class TimePacketMixin {
	@ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
	private Packet<?> phonics$ownSky(Packet<?> packet) {
		if (packet instanceof ClientboundSetTimePacket time && (Object) this instanceof ServerGamePacketListenerImpl game && game.player != null) {
			return Sky.rewrite(game.player, time);
		}
		return packet;
	}
}
