package justfatlard.learning_blocks.narration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.server.level.ServerPlayer;

/**
 * The caption at the bottom of the screen: big, one text component per word, each word lit while
 * it is being said.
 *
 * <p>Laid out here rather than on the client because a HUD overlay places components where it is
 * told and nothing flows. Each word is measured with {@link FontWidths}, and drawn at scale with
 * its bounds centred where the scaled word belongs, because Pandorical scales a component about
 * the middle of its bounds.
 *
 * <p>A line wider than the player's window wraps onto more lines ({@link CaptionLayout}), each word
 * still lit on its own; the size comes down only when a few lines are not enough.
 *
 * <p>After the first show, only colours change, and only when a word's state does, so a line is
 * one show and a handful of small updates however long it is said for.
 */
final class CaptionHud {
	private CaptionHud() {}

	static final String OVERLAY = LearningBlocks.MOD_ID + ":caption";

	/** Up from the bottom of the screen: clear of the hotbar, the hearts and the item name. */
	private static final int ABOVE_BOTTOM = 62;
	/** Raised: in the band between the letters on the floor and the board, which a small caption fits. */
	private static final int RAISED = 108;
	private static final int BELOW_TOP = 14;
	private static final int PAD_Y = CaptionLayout.PAD_Y;
	private static final int TEXT_HEIGHT = CaptionLayout.TEXT_HEIGHT;

	private static final String BACKGROUND = "#A0101418";
	private static final int FUTURE = 0xFF8C8C8C;
	private static final int CURRENT = 0xFFFFD84A;
	private static final int PAST = 0xFFFFFFFF;
	private static final int SEPARATOR = 0xFF8C8C8C;
	/** A letter written and not said: dimmer than any word still to come. */
	private static final int SILENT = 0xFF5E5E5E;

	private record Shown(CaptionLine line, CaptionLine.State[] states) {}

	private static final Map<UUID, Shown> shown = new HashMap<>();

	static void show(ServerPlayer player, CaptionLine line, float seconds) {
		if (line.isEmpty() || !CaptionSettings.shown(player)) {
			hide(player);
			return;
		}
		String position = CaptionSettings.position(player);
		List<CaptionLine.Piece> pieces = line.pieces();
		int n = pieces.size();
		List<String> texts = new ArrayList<>();
		for (CaptionLine.Piece piece : pieces) texts.add(piece.text());
		// Fitted to this player's own window: a line too wide for it wraps, word by word.
		CaptionLayout layout = CaptionLayout.fit(texts, CaptionSettings.scale(player), PandoricalApi.screens().viewport(player).width());
		float scale = layout.scale();

		float height = TEXT_HEIGHT * scale;
		int lineHeight = Math.round(height);
		int panelWidth = layout.panelWidth();
		int panelHeight = layout.panelHeight();
		int oneLine = lineHeight + 2 * PAD_Y;

		// bottom_center puts the overlay's x = 0 at the middle of the screen; top_center centres the
		// overlay by its own width, so there the line is laid out from 0 instead. From the top, more
		// lines grow down; from below, they grow up, so the bottom edge stays clear of the hotbar.
		boolean top = position.equals("top");
		int shift = top ? panelWidth / 2 : 0;
		int down = top ? 0 : oneLine - panelHeight;
		HudBuilder overlay = top
			? new HudBuilder(OVERLAY).anchor("top_center").offset(0, BELOW_TOP)
			: new HudBuilder(OVERLAY).anchor("bottom_center").offset(0, position.equals("bottom") ? ABOVE_BOTTOM : RAISED);
		overlay.component(new ComponentBuilder("panel", ComponentType.PANEL)
			.bounds(shift - panelWidth / 2, down, panelWidth, panelHeight)
			.prop(ComponentType.PROP_BACKGROUND, BACKGROUND)
			.prop(ComponentType.PROP_BORDER, "flat")
			.prop(ComponentType.PROP_BORDER_COLOR, BACKGROUND));

		CaptionLine.State[] states = new CaptionLine.State[n];
		for (int row = 0; row < layout.lines().size(); row++) {
			float middleY = down + PAD_Y + row * (lineHeight + CaptionLayout.LINE_GAP) + height / 2.0F;
			float left = -layout.lineWidths().get(row) / 2.0F;
			for (int i : layout.lines().get(row)) {
				int width = FontWidths.width(pieces.get(i).text());
				float scaled = width * scale;
				float middleX = left + scaled / 2.0F;
				states[i] = line.state(i, seconds);
				overlay.component(new ComponentBuilder(pieceId(i), ComponentType.TEXT)
					.bounds(shift + Math.round(middleX - width / 2.0F), Math.round(middleY - TEXT_HEIGHT / 2.0F), width + 1, TEXT_HEIGHT)
					.scale(scale)
					.prop(ComponentType.PROP_TEXT, pieces.get(i).text())
					// No shadow under a separator: a dot's shadow, three times the size, is a dark square
					// beside it rather than depth.
					.prop(ComponentType.PROP_SHADOW, pieces.get(i).separator() ? "false" : "true")
					.prop(ComponentType.PROP_INTERP_TICKS, "2")
					.color(ComponentType.PROP_COLOR, colour(states[i])));
				left += scaled + FontWidths.SPACE * scale;
			}
		}

		// Taken down first, so the line goes back on top of any other overlay (a scene's dimmed
		// room): an overlay put up again keeps its old place in the drawing order.
		if (shown.containsKey(player.getUUID())) PandoricalApi.hud().hide(player, OVERLAY);
		PandoricalApi.hud().show(player, overlay.build());
		shown.put(player.getUUID(), new Shown(line, states));
	}

	static void advance(ServerPlayer player, float seconds) {
		Shown current = shown.get(player.getUUID());
		if (current == null) return;
		List<ComponentUpdate> updates = new ArrayList<>();
		for (int i = 0; i < current.states().length; i++) {
			CaptionLine.State now = current.line().state(i, seconds);
			if (now == current.states()[i]) continue;
			current.states()[i] = now;
			updates.add(new ComponentUpdateBuilder(pieceId(i)).color(ComponentType.PROP_COLOR, colour(now)).build());
		}
		if (!updates.isEmpty()) PandoricalApi.hud().update(player, OVERLAY, updates);
	}

	static boolean showing(ServerPlayer player) {
		return shown.containsKey(player.getUUID());
	}

	static void hide(ServerPlayer player) {
		if (shown.remove(player.getUUID()) != null) PandoricalApi.hud().hide(player, OVERLAY);
	}

	static void forget(UUID player) {
		shown.remove(player);
	}

	static void clear() {
		shown.clear();
	}

	private static String pieceId(int index) {
		return "w" + index;
	}

	private static int colour(CaptionLine.State state) {
		return switch (state) {
			case FUTURE -> FUTURE;
			case CURRENT -> CURRENT;
			case PAST -> PAST;
			case SEPARATOR -> SEPARATOR;
			case SILENT -> SILENT;
		};
	}
}
