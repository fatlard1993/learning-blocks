package justfatlard.phonics.gametest;

import java.time.LocalDate;
import java.util.List;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.puzzle.Letter;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.reward.PrizeConfig;
import justfatlard.phonics.reward.Prizes;
import justfatlard.phonics.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A payout the daily cap holds back is owed, not lost: with a cap of one, a word that earns two
 * payouts puts one in the visit and owes the other; the next day it is paid, and both come home.
 * Owed again, and then the cap taken off: the next way in pays it.
 */
public final class CapCarryOver implements FabricClientGameTest {

	private static final String CAP_OF_ONE = """
		{ "enabled": true, "daily_cap": 1, "word_book": "off", "events": {
		  "new_word": { "enabled": true, "prizes": [ { "item": "minecraft:diamond" } ] },
		  "solved": { "enabled": true, "first_try_only": false, "prizes": [ { "item": "minecraft:emerald" } ] } } }
		""";

	private static final String NO_CAP = """
		{ "enabled": true, "daily_cap": 0, "events": {
		  "new_word": { "enabled": true, "prizes": [ { "item": "minecraft:diamond" } ] },
		  "solved": { "enabled": true, "first_try_only": false, "prizes": [ { "item": "minecraft:emerald" } ] } } }
		""";

	@Override
	public void runTest(ClientGameTestContext context) {
		LocalDate[] today = {LocalDate.of(2026, 9, 1)};
		Progress.useClock(() -> today[0]);
		try {
			Smoke.run(context, "phonics-justfatlard", session -> {
				session.onServer(server -> {
					List<String> problems = PrizeConfig.use(CAP_OF_ONE, server);
					if (!problems.isEmpty()) throw new AssertionError(problems);
				});
				session.command("/phonics");
				session.waitTicks(40);
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					Hands.solve(player, room);
					click(player, room.button());
				});
				Waits.until(session, 600, "the target was never read", () -> Rooms.of(session.player()).solved() == 1);

				session.onServer(server -> {
					ServerPlayer player = session.player();
					List<ItemStack> waiting = Visit.of(player).prizes();
					check(waiting.size() == 1 && waiting.getFirst().is(Items.DIAMOND), "the first payout is not the one waiting: " + waiting);
					List<Progress.Owed> owed = Progress.of(player).owed();
					check(owed.size() == 1 && owed.getFirst().items().getFirst().is(Items.EMERALD), "the capped payout is not owed: " + owed);

					// Still today: nothing more pays.
					Prizes.payOwed(player);
					check(Visit.of(player).prizes().size() == 1, "an owed payout was paid past today's cap");

					// Tomorrow.
					today[0] = today[0].plusDays(1);
					Prizes.payOwed(player);
					check(Visit.of(player).prizes().stream().anyMatch(stack -> stack.is(Items.EMERALD)), "the owed emerald was not paid the next day");
					check(Progress.of(player).owed().isEmpty(), "the owed payout is still owed after paying");
					check(Progress.of(player).paidToday() == 1, "the owed payout did not count against the new day's cap");
				});

				session.command("/phonics leave");
				Waits.until(session, 200, "never went home", () -> Visit.of(session.player()) == null);
				session.onServer(server -> {
					var inventory = session.player().getInventory();
					check(inventory.hasAnyMatching(stack -> stack.is(Items.DIAMOND)), "the diamond did not come home");
					check(inventory.hasAnyMatching(stack -> stack.is(Items.EMERALD)), "the owed emerald did not come home");
				});

				// Owed again, then the cap taken off: the next way in pays it, nothing left owed.
				session.command("/phonics");
				session.waitTicks(40);
				session.onServer(server -> {
					ServerPlayer player = session.player();
					Room room = Rooms.of(player);
					Hands.solve(player, room);
					click(player, room.button());
				});
				Waits.until(session, 600, "the second visit's target was never read", () -> Rooms.of(session.player()).solved() == 1);
				session.onServer(server -> {
					check(!Progress.of(session.player()).owed().isEmpty(), "nothing was owed with today's cap used up");
					List<String> problems = PrizeConfig.use(NO_CAP, server);
					if (!problems.isEmpty()) throw new AssertionError(problems);
				});
				session.command("/phonics leave");
				Waits.until(session, 200, "never went home the second time", () -> Visit.of(session.player()) == null);
				session.command("/phonics");
				session.waitTicks(40);
				session.onServer(server -> {
					ServerPlayer player = session.player();
					check(Progress.of(player).owed().isEmpty(), "with no cap, what was owed is still owed: " + Progress.of(player).owed());
					check(Visit.of(player).hasPrizes(), "with no cap, what was owed was not paid on the way in");
				});
				session.command("/phonics leave");
				Waits.until(session, 200, "never went home the third time", () -> Visit.of(session.player()) == null);
			});
		} finally {
			Progress.useClock(null);
		}
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
