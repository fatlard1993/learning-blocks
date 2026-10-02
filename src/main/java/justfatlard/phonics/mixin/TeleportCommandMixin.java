package justfatlard.phonics.mixin;

import java.util.Set;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import justfatlard.phonics.classroom.Travel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The classroom is not entered or left by {@code /tp}; it has a door, and the door does work.
 *
 * <p>Failed outright rather than allowed and put right afterwards, so whoever typed it is told
 * why, and which command does it properly. The same shape as PvP Dimensions' arena rule.
 */
@Mixin(TeleportCommand.class)
public class TeleportCommandMixin {

	private static final DynamicCommandExceptionType REFUSED =
		new DynamicCommandExceptionType(reason -> Component.literal(String.valueOf(reason)));

	@Inject(method = "performTeleport", at = @At("HEAD"))
	private static void phonics$refuseAcrossTheDoor(CommandSourceStack source, Entity victim, ServerLevel level,
			double x, double y, double z, Set<Relative> relatives, float yRot, float xRot, @Nullable LookAt lookAt,
			CallbackInfo callback) throws CommandSyntaxException {
		if (!(victim instanceof ServerPlayer player)) return;
		String refused = Travel.refuse(player, level);
		if (refused != null) throw REFUSED.create(refused);
	}
}
