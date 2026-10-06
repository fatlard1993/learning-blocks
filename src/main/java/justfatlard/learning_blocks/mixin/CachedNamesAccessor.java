package justfatlard.learning_blocks.mixin;

import java.util.Map;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The server's name cache as it stands, without asking it: its own lookup of a name it hasn't seen
 * goes out to find one, and on an offline-mode server makes up an id for any name at all.
 */
@Mixin(CachedUserNameToIdResolver.class)
public interface CachedNamesAccessor {
	@Accessor("profilesByName")
	Map<String, ?> learn$byName();
}
