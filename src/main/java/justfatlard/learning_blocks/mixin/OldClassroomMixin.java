package justfatlard.learning_blocks.mixin;

import justfatlard.learning_blocks.migrate.OldNames;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player saved inside the classroom from before the rename: that dimension is gone, so the game
 * puts them in the overworld where the classroom's coordinates are. Noted as they load, for the
 * login backstop to take them home properly (see {@code Travel.loggedIn}).
 */
@Mixin(ServerPlayer.class)
public abstract class OldClassroomMixin {

	@Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
	private void learn$noteOldClassroom(ValueInput input, CallbackInfo callback) {
		if (input.getString("Dimension").map(OldNames.OLD_CLASSROOM::equals).orElse(false)) {
			OldNames.savedInOldClassroom((ServerPlayer) (Object) this);
		}
	}
}
