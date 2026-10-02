package justfatlard.phonics.mixin;

import justfatlard.phonics.classroom.Places;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No weather in the classroom's own world: each child's is their own, sent to them alone.
 *
 * <p>A datapack dimension shares the overworld's weather, and a dimension type has no switch for
 * it: the game decides from skylight and ceiling alone, the End being the one dimension it names.
 * So the classroom is named here the same way. With this false, the level never rains, never
 * thunders, and never tells a child arriving that it is raining.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

	@Inject(method = "canHaveWeather", at = @At("HEAD"), cancellable = true)
	private void phonics$noWeatherInClass(CallbackInfoReturnable<Boolean> callback) {
		// The server's level only: each child is told of their own weather (see Sky), and their game draws it.
		if ((Object) this instanceof net.minecraft.server.level.ServerLevel && Places.isClassroom((Level) (Object) this)) callback.setReturnValue(false);
	}
}
