package justfatlard.learning_blocks.editor;

import java.util.List;
import com.google.gson.JsonPrimitive;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SettingsApi;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.server.level.ServerPlayer;

/**
 * The prize settings that are settings-shaped - on or off, a number, one of three - on Phonics'
 * page of Pandorical's settings screen, under its server heading, for operators.
 *
 * <p>Backed by the prizes file itself, through the same change the editor makes, so there is still
 * one place the value lives. Pandorical's settings have no way to open a mod's own screen, so the
 * item lists, loot tables and the rest stay in {@code /learn prizes}; the description here says so.
 */
public final class ServerSettings {
	private ServerSettings() {}

	public static void register() {
		SettingsApi.Group group = PandoricalApi.settings().serverGroup(LearningBlocks.MOD_ID, "Learning Blocks");
		group.toggle("prizes_enabled", "Prizes", true)
			.describe("Everything reading pays. The rest is in /learn prizes")
			.backedBy(player -> PrizeEditor.readBool("enabled", true), (player, on) -> write(player, "enabled", new JsonPrimitive(on)));
		group.number("prizes_daily_cap", "Payouts a day (0 for no cap)", 0, 200, 1, 0)
			.backedBy(player -> PrizeEditor.readInt("daily_cap", 0), (player, cap) -> write(player, "daily_cap", new JsonPrimitive(cap)));
		group.toggle("prizes_points", "Points", false)
			.backedBy(player -> PrizeEditor.readBool("points.enabled", false), (player, on) -> write(player, "points.enabled", new JsonPrimitive(on)));
	}

	private static void write(ServerPlayer player, String path, JsonPrimitive value) {
		List<String> problems = PrizeEditor.change(player.level().getServer(), tree -> justfatlard.learning_blocks.reward.PrizeFile.set(tree, path, value));
		if (problems == null) LearningBlocks.LOGGER.warn("[{}] {} could not change {} in prizes.json", LearningBlocks.MOD_ID, player.getGameProfile().name(), path);
	}
}
