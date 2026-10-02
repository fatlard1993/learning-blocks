package justfatlard.phonics.gametest;

import java.util.List;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.phonics.classroom.LenientItems;
import justfatlard.phonics.classroom.Places;
import justfatlard.phonics.classroom.Travel;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.puzzle.Puzzle;
import justfatlard.phonics.puzzle.Room;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What must never go wrong, made to: the backstops (a login, a respawn, a teleport out, a refused
 * teleport) hand everything back; an outcome that throws still leaves the word counted and the
 * lesson done, and the server running; a room with nothing to ask lets locked lessons go; a stash
 * holding an item from a mod that is gone keeps it, as a stand-in that saves back as it was, and a
 * record that cannot be read at all is kept, not replaced; and a child who disconnects in the middle
 * of leaving is not stuck when they come back.
 */
public final class Hardening implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			ready(context, world);

			// The refusals, and the backstops handing things back wherever the child stands.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				check(Travel.refuse(player, server.getLevel(Places.CLASSROOM)) != null, "a teleport into the classroom was not refused");
				check(Travel.refuse(player, server.overworld()) == null, "a teleport within the overworld was refused");
				for (String backstop : List.of("login", "respawn")) {
					player.getInventory().clearContent();
					player.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 2));
					Visit.set(player, Visit.snapshot(player));
					player.getInventory().clearContent();
					if (backstop.equals("login")) Travel.loggedIn(player);
					else Travel.respawned(player);
					check(Visit.of(player) == null, "after a " + backstop + " outside the visit is still open");
					check(player.getInventory().getItem(3).is(Items.DIAMOND), "after a " + backstop + " the diamonds did not come back");
				}
				player.getInventory().clearContent();
				player.getInventory().setItem(4, new ItemStack(Items.EMERALD, 3));
				asPlayer(world, "phonics");
			});
			context.waitTicks(40);
			// Taken out by something that is not the door.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				check(Places.isClassroom(player.level()), "never went in");
				check(Travel.refuse(player, server.overworld()) != null, "a teleport out of the classroom was not refused");
				// Dying: nothing is handed to a body that is about to respawn.
				player.setHealth(0);
				Travel.goHomeNow(player);
				check(Visit.of(player) != null, "things were handed back to a dying child");
				player.setHealth(20);
				player.teleportTo(server.overworld(), 0.5, 100, 0.5, java.util.Set.of(), 0, 0, true);
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				check(Visit.of(player) == null, "a teleport out left the visit open");
				check(player.getInventory().getItem(4).is(Items.EMERALD), "a teleport out did not hand the emeralds back");
				asPlayer(world, "phonics");
			});
			context.waitTicks(40);

			// An outcome that throws: the word still counted, the lesson still done, the room still going.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				Travel.setLessons(player, 3);
				Room room = Rooms.of(player);
				room.failNextOutcomeForTests();
				Hands.solve(player, room);
				click(player, room.button());
				check(Progress.of(player).words().contains(room.puzzle().target().id().toString()), "the word was not counted at the press");
				check(Travel.lessons(player) == 2, "the lesson was not counted at the press: " + Travel.lessons(player));
			});
			Puzzle[] was = new Puzzle[1];
			world.getServer().runOnServer(server -> was[0] = Rooms.of(player(world)).puzzle());
			for (int tick = 0; tick < 900; tick++) {
				boolean[] moved = new boolean[1];
				world.getServer().runOnServer(server -> {
					Room room = Rooms.of(player(world));
					moved[0] = room.puzzle() != null && room.puzzle() != was[0] && !room.busy();
				});
				if (moved[0]) break;
				context.waitTicks(1);
			}
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				Room room = Rooms.of(player);
				check(room != null && room.puzzle() != null && room.puzzle() != was[0], "the room did not go on after an outcome threw");
				check(Travel.lessons(player) == 2, "lessons changed again: " + Travel.lessons(player));
			});

			// Nothing to ask with the door locked: after a while the lessons are let go.
			world.getServer().runOnServer(server -> Rooms.of(player(world)).dropPuzzleForTests());
			context.waitTicks(230);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				check(Travel.lessons(player) == 0, "a room with nothing to ask still holds lessons: " + Travel.lessons(player));
				check(Rooms.of(player).portalOpen(), "the door stayed locked");
			});

			// A stash holding an item this server has not got: kept, as a stand-in that saves back as it was.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
				Visit visit = Visit.of(player);
				Tag saved = Visit.CODEC.encodeStart(ops, visit).getOrThrow();
				CompoundTag missing = new CompoundTag();
				missing.putString("id", "nomod:thing");
				missing.putInt("count", 1);
				CompoundTag slot = new CompoundTag();
				slot.putInt("slot", 7);
				slot.put("item", missing);
				((ListTag) ((CompoundTag) saved).get("kit")).add(slot);
				Visit read = Visit.CODEC.parse(ops, saved).getOrThrow();
				ItemStack standIn = read.kit().stream().filter(s -> s.slot() == 7).findFirst().orElseThrow().stack();
				check(standIn.getHoverName().getString().equals("Missing item: nomod:thing"), "the missing item is not a named stand-in: " + standIn);
				check(read.kit().stream().anyMatch(s -> s.stack().is(Items.EMERALD)), "the emeralds were lost with the missing item");
				Tag again = Visit.CODEC.encodeStart(ops, read).getOrThrow();
				check(((ListTag) ((CompoundTag) again).get("kit")).stream().anyMatch(tag -> tag.equals(slot)),
					"the missing item did not save back as it was");

				// A record that cannot be read at all: kept, never replaced.
				Tag broken = StringTag.valueOf("not a record");
				Progress kept = Progress.SAVED.parse(ops, broken).getOrThrow();
				check(kept.unreadable(), "an unreadable progress was not marked as kept");
				check(Progress.SAVED.encodeStart(ops, kept).getOrThrow().equals(broken), "an unreadable progress did not save back as it was");
				check(LenientItems.rawOf(new ItemStack(Items.PAPER)) == null, "ordinary paper looks like a stand-in");
			});

			// Leaving with a prize, and gone mid-line.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				Visit.set(player, Visit.of(player).withPrizes(List.of(new ItemStack(Items.GOLD_INGOT)), 0));
				click(player, Rooms.of(player).doorLower());
			});
			context.waitTicks(12);
		}
		context.waitTicks(10);

		// Back: not stuck. In a fresh room, and the door works.
		try (TestSingleplayerContext world = save.open()) {
			ready(context, world);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				if (Visit.of(player) != null) {
					check(Rooms.of(player) != null && !Rooms.of(player).leaving(), "back in, but the room is still leaving");
					asPlayer(world, "phonics leave");
				}
			});
			for (int tick = 0; tick < 200; tick++) {
				boolean[] home = new boolean[1];
				world.getServer().runOnServer(server -> home[0] = Visit.of(player(world)) == null);
				if (home[0]) break;
				context.waitTicks(1);
			}
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(world);
				check(Visit.of(player) == null, "stuck in the classroom after disconnecting while leaving");
				check(player.getInventory().hasAnyMatching(stack -> stack.is(Items.GOLD_INGOT)), "the prize was lost");
				check(player.getInventory().hasAnyMatching(stack -> stack.is(Items.EMERALD)), "the stash was lost");
			});
		}
	}

	private static ServerPlayer player(TestSingleplayerContext world) {
		return world.getConnection().getServerPlayer();
	}

	private static void ready(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getConnection().waitForChunksRender();
		world.getServer().waitFor(server -> PandoricalApi.isAvailable(world.getConnection().getServerPlayer()));
		context.waitTicks(20);
	}

	private static void asPlayer(TestSingleplayerContext world, String command) {
		ServerPlayer player = world.getConnection().getServerPlayer();
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		Rooms.useBlock(player, player.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
