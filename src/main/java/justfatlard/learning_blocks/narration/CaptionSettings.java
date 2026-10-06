package justfatlard.learning_blocks.narration;

import java.util.LinkedHashMap;
import java.util.Map;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SettingsApi;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.server.level.ServerPlayer;

/**
 * Each player's own say over the captions, on Learning Blocks' page of Pandorical's settings: whether they
 * show, how big, and where.
 *
 * <p>"Top" is the default, chosen from the pictures of all three. At the bottom, where captions
 * usually go, the line sits over the letter tiles on the floor; raised, it sits over the word on the
 * board, since at a large size the band between the two is narrower than the line; at the top it
 * covers only sky and the board's frame, whatever the size. Raised is still offered, for a small
 * caption, which does fit in that band.
 */
public final class CaptionSettings {
	private CaptionSettings() {}

	private static SettingsApi.Setting<Boolean> shown;
	private static SettingsApi.Setting<String> size;
	private static SettingsApi.Setting<String> position;

	public static void register() {
		SettingsApi.Group group = PandoricalApi.settings().group(LearningBlocks.MOD_ID, "Learning Blocks");
		shown = group.toggle("captions", "Captions", true)
			.describe("The words the classroom says, written large as they are said");
		Map<String, String> sizes = new LinkedHashMap<>();
		sizes.put("small", "Small");
		sizes.put("medium", "Medium");
		sizes.put("large", "Large");
		size = group.choice("caption_size", "Caption size", sizes, "large").shownWhen(shown, true);
		Map<String, String> places = new LinkedHashMap<>();
		places.put("bottom", "Bottom");
		places.put("raised", "Raised");
		places.put("top", "Top");
		position = group.choice("caption_position", "Caption position", places, "top")
			.describe("Top keeps the line off the board and the letters on the floor").shownWhen(shown, true);
	}

	static boolean shown(ServerPlayer player) {
		return shown == null || shown.get(player);
	}

	static float scale(ServerPlayer player) {
		return scaleOf(size == null ? "large" : size.get(player));
	}

	public static float scaleOf(String chosen) {
		return switch (chosen) {
			case "small" -> 2.0F;
			case "medium" -> 2.5F;
			default -> 3.0F;
		};
	}

	static String position(ServerPlayer player) {
		return position == null ? "top" : position.get(player);
	}

	/** For tests, and for anybody changing it from elsewhere: the same as the settings screen. */
	public static void set(ServerPlayer player, boolean on, String chosenSize, String chosenPosition) {
		shown.set(player, on);
		size.set(player, chosenSize);
		position.set(player, chosenPosition);
	}
}
