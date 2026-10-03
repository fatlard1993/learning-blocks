package justfatlard.learning_blocks.gametest;

import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.PrizeFile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/** Test helpers for the prize editor: an operator, and the file put back as it started. */
final class Ops {
	private Ops() {}

	static void op(MinecraftServer server, ServerPlayer player) {
		server.getPlayerList().op(new NameAndId(player.getGameProfile()));
	}

	/** The run's config folder is shared by every test, so each one that writes it puts it back. */
	static void resetFile(MinecraftServer server) {
		try {
			PrizeFile.write(PrizeFile.defaults());
		} catch (Exception e) {
			throw new AssertionError("could not put the prizes file back", e);
		}
		PrizeConfig.load(server);
	}
}
