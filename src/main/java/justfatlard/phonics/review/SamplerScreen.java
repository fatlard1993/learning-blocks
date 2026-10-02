package justfatlard.phonics.review;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.phonics.Phonics;
import justfatlard.phonics.editor.Admins;
import justfatlard.phonics.editor.Ui;
import justfatlard.phonics.puzzle.Sampler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The grown-ups' sampler screen: a button for each kind of round, each running a practice round of
 * that kind with a random word, and "Next" for another of the last kind. Operators only.
 */
public final class SamplerScreen {
	private SamplerScreen() {}

	public static final String TYPE = Phonics.MOD_ID + ":sampler";
	private static final int WIDTH = 316;
	private static final int PAD = Ui.PAD;
	private static final int COLUMNS = 3;
	private static final int CELL_W = (WIDTH - 2 * PAD - (COLUMNS - 1) * 4) / COLUMNS;

	private static final Map<UUID, String> open = new HashMap<>();
	/** The kind each viewer last ran from the screen: for tests. */
	private static final Map<UUID, String> ran = new HashMap<>();

	public static void register(ScreenApi api) {
		api.onActionFallback(TYPE, (player, data) -> press(player, data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY)));
		api.onClose(TYPE, player -> open.remove(player.getUUID()));
	}

	/** The screen, for an operator with Pandorical; false otherwise. */
	public static boolean open(ServerPlayer viewer) {
		if (!Admins.may(viewer) || !PandoricalApi.isAvailable(viewer)) return false;
		Sampler.Kind[] kinds = Sampler.Kind.values();
		int rows = (kinds.length + COLUMNS - 1) / COLUMNS;
		int top = 22;
		int height = top + rows * (Ui.BUTTON + 4) + 34;
		ScreenBuilder screen = new ScreenBuilder(TYPE).title("Try a lesson").pauseGame(false);
		screen.size(WIDTH, height);
		screen.panel("dialog", 0, 0, WIDTH, height, Ui.panel());
		screen.component(Ui.icon("title_icon", PAD, 3, "minecraft:spyglass", 0.75F));
		screen.component(Ui.text("title", PAD + 16, 7, "Try a lesson (practice: nothing counts)"));
		for (int i = 0; i < kinds.length; i++) {
			int x = PAD + (i % COLUMNS) * (CELL_W + 4);
			int y = top + (i / COLUMNS) * (Ui.BUTTON + 4);
			screen.component(Ui.button("kind:" + kinds[i].id(), x, y, CELL_W, Ui.BUTTON, kinds[i].label, kinds[i].description));
		}
		Sampler.Kind last = Sampler.lastKind(viewer);
		screen.component(Ui.button("next", PAD, height - 26, 120, Ui.BUTTON - 2,
			last == null ? "Next" : "Next: " + last.label, "Another of the last kind, another word"));
		screen.component(Ui.button("close", WIDTH - PAD - 44, height - 26, 44, Ui.BUTTON - 2, "Done"));
		open.put(viewer.getUUID(), screen.screenId());
		PandoricalApi.screens().open(viewer, screen.build());
		return true;
	}

	/** A button: a kind, "next", or done. Public for tests, which press it the way a click does. */
	public static void press(ServerPlayer player, String id) {
		if (id == null || !Admins.may(player)) return;
		Sampler.Kind kind = id.equals("next") ? Sampler.lastKind(player)
			: id.startsWith("kind:") ? Sampler.Kind.of(id.substring("kind:".length())) : null;
		String screenId = open.remove(player.getUUID());
		if (screenId != null) PandoricalApi.screens().close(player, screenId);
		if (kind == null) return;
		ran.put(player.getUUID(), kind.id());
		String problem = Sampler.run(player, kind, null);
		if (problem != null) player.sendSystemMessage(Component.literal(problem));
	}

	public static String lastRan(ServerPlayer player) {
		return ran.getOrDefault(player.getUUID(), "");
	}

	public static boolean isOpen(ServerPlayer player) {
		return open.containsKey(player.getUUID());
	}
}
