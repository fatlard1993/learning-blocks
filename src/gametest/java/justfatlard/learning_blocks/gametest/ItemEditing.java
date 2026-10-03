package justfatlard.learning_blocks.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.editor.PrizeSessions;
import justfatlard.learning_blocks.reward.Prize;
import justfatlard.learning_blocks.reward.PrizeConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

/**
 * Editing a prize list by holding it: the operator's own pack is put aside and comes back exactly,
 * even across a disconnect in the middle, and what they held - a named, enchanted sword among it -
 * is the list, components and all.
 *
 * <p>Its own world rather than the shared harness's, because it has to leave and come back: the
 * world is closed with the session open and reopened from its save.
 */
public final class ItemEditing implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		List<ItemStack> before = new ArrayList<>();
		TestWorldSave save;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			world.getConnection().waitForChunksRender();
			world.getServer().waitFor(server -> PandoricalApi.isAvailable(world.getConnection().getServerPlayer()));
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				Ops.op(server, player);
				Ops.resetFile(server);
				player.setGameMode(GameType.CREATIVE);
				Inventory inventory = player.getInventory();
				inventory.clearContent();
				inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
				inventory.setItem(17, new ItemStack(Items.BREAD, 7));
				inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) before.add(inventory.getItem(slot).copy());

				check(PrizeSessions.start(player, "events.new_word.prizes", "EVENTS") == null, "the session would not start");
				check(inventory.getItem(0).is(Items.COOKIE) && inventory.getItem(0).getCount() == 1,
					"the new word prizes were not laid out: " + inventory.getItem(0));
				check(!inventory.hasAnyMatching(stack -> stack.is(Items.BREAD)), "the operator's own pack is still in hand");
				check(Travel.enter(player) != null, "the classroom let somebody in with a pack put aside for editing");

				// What they choose to hold: a named, enchanted sword and some apples.
				inventory.clearContent();
				ItemStack star = new ItemStack(Items.GOLDEN_SWORD);
				star.set(DataComponents.CUSTOM_NAME, Component.literal("Star Reader"));
				star.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
				inventory.setItem(3, star);
				inventory.setItem(9, new ItemStack(Items.APPLE, 5));
			});
			context.waitTicks(10);
		}
		context.waitTicks(10);

		// Back in, the session still open, and nothing lost while they were gone.
		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			world.getServer().waitFor(server -> PandoricalApi.isAvailable(world.getConnection().getServerPlayer()));
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				check(PrizeSessions.editing(player), "the session did not survive the disconnect");
				check(player.getInventory().getItem(3).is(Items.GOLDEN_SWORD), "what they were holding did not survive the disconnect");

				check(PrizeSessions.done(player) == null, "done refused");
				Inventory inventory = player.getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					// Not ItemStack.matches: the world was closed and reopened, and a stack's default
					// components are bound to the server that made it, so two identical stacks from two
					// server starts compare unequal. The item, the count and what was changed on it are
					// what the pack is.
					ItemStack now = inventory.getItem(slot);
					ItemStack was = before.get(slot);
					check(now.getItem() == was.getItem() && now.getCount() == was.getCount()
							&& now.getComponentsPatch().equals(was.getComponentsPatch()),
						"slot " + slot + " came back as " + inventory.getItem(slot) + " " + inventory.getItem(slot).getComponentsPatch()
							+ ", not " + before.get(slot) + " " + before.get(slot).getComponentsPatch());
				}
				check(!PrizeSessions.editing(player), "the session never ended");

				List<ItemStack> items = new ArrayList<>();
				for (Prize prize : PrizeConfig.get().newWord.prizes()) if (prize instanceof Prize.Items it) items.add(it.stack());
				check(items.size() == 2, "the list is not the two stacks held: " + items);
				ItemStack sword = items.stream().filter(stack -> stack.is(Items.GOLDEN_SWORD)).findFirst().orElse(ItemStack.EMPTY);
				check("Star Reader".equals(sword.getHoverName().getString()), "the sword lost its name: " + sword);
				check(!sword.getEnchantments().isEmpty(), "the sword lost its enchantment: " + sword);
				check(items.stream().anyMatch(stack -> stack.is(Items.APPLE) && stack.getCount() == 5), "the apples did not make the list");
				Ops.resetFile(server);
			});
			context.waitTicks(10);
		}
		context.waitTicks(10);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
