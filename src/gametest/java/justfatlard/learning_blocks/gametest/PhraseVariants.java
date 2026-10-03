package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.sound.Captions;
import justfatlard.learning_blocks.sound.Sounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * A line with variants is said a different way each time: twenty "well done"s never say the same one
 * twice running, and every one of the five turns up. Each variant has its own caption. Said through
 * the narrator, the log keeps the line's own name and the variant beside it.
 */
public final class PhraseVariants implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> {
			session.onServer(server -> {
				ServerPlayer player = session.player();
				List<String> options = Sounds.variantsOf("phrase/well_done");
				check(options.size() == 5, "well_done has " + options.size() + " variants, not 5: " + options);
				for (String option : options) check(Captions.get(option) != null, option + " has no caption");
				List<String> said = new ArrayList<>();
				for (int i = 0; i < 20; i++) said.add(Sounds.variant(player, "phrase/well_done"));
				for (int i = 1; i < said.size(); i++) check(!said.get(i).equals(said.get(i - 1)), "the same well done twice running: " + said);
				check(new HashSet<>(said).equals(new HashSet<>(options)), "not every well done turned up: " + said);
				check(Sounds.variant(player, "phrase/hmm").equals("phrase/hmm"), "a line with no variants was changed");
				Narrator.play(player, new Narration().say("phrase/well_done"));
				List<String> log = Narrator.log(player);
				check(log.contains("clip:phrase/well_done"), "the log lost the line's own name: " + log);
				check(options.contains(Sounds.recent().getLast().key()), "what was played is not a well done: " + Sounds.recent().getLast());
			});
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
