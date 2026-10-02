package justfatlard.phonics.editor;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** Who may change what reading pays: operators, the same as {@code /phonics reload}. */
public final class Admins {
	private Admins() {}

	public static boolean may(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.createCommandSourceStack().permissions());
	}
}
