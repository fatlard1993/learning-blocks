package justfatlard.learning_blocks.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.classroom.Schedules;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.migrate.OldNames;
import justfatlard.learning_blocks.plush.Plush;
import justfatlard.learning_blocks.plush.PlushBlock;
import justfatlard.learning_blocks.plush.Plushies;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.LoggerFactory;

/**
 * From Phonics to Learning Blocks, against a copy of the live server's own player files (kept in
 * {@code .rounds/live-copy/}, never committed: the test says it is skipped where that is absent).
 * Max's saved progress (every word, the memory, what has paid, his owed lessons) comes through
 * under the new names; an old-id plushie and letter tile in his inventory load as the new items; an
 * old-id plushie in a chunk's palette reads as the new block; his 10-a-day schedule and the prizes
 * file come across from {@code config/phonics/}; and Ava, saved inside the old classroom with a
 * visit open (a diamond in the stash, an emerald waiting), logs in at home with both and the visit
 * ended.
 */
public final class Migration implements FabricClientGameTest {

	private static final UUID MAX = UUID.fromString("00000000-0000-3000-8000-00000000000a");
	private static final UUID AVA = UUID.fromString("00000000-0000-3000-8000-00000000000b");

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "learning-blocks-justfatlard", session -> session.onServer(server -> {
			try {
				run(server);
			} catch (IOException e) {
				throw new AssertionError("could not read or write the copied player files", e);
			}
		}));
	}

	private static void run(MinecraftServer server) throws IOException {
		Path live = liveCopy();
		if (live == null) {
			LoggerFactory.getLogger("learning-blocks-gametest").warn("Migration skipped: no .rounds/live-copy/ here");
			return;
		}
		ServerLevel overworld = server.overworld();
		Path players = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
		Files.createDirectories(players);
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());

		// Max, as saved live, with an old-id plushie and letter tile put in his pack.
		CompoundTag max = NbtIo.readCompressed(live.resolve("players/" + MAX + ".dat"), NbtAccounter.unlimitedHeap());
		ListTag inventory = max.getListOrEmpty("Inventory");
		CompoundTag owl = new CompoundTag();
		owl.putByte("Slot", (byte) 30);
		owl.putString("id", OldNames.OLD + ":plush_owl");
		owl.putInt("count", 1);
		inventory.add(owl);
		CompoundTag letter = new CompoundTag();
		letter.putByte("Slot", (byte) 31);
		letter.putString("id", OldNames.OLD + ":letter");
		letter.putInt("count", 3);
		inventory.add(letter);
		max.put("Inventory", inventory);
		CompoundTag oldProgress = max.getCompoundOrEmpty("fabric:attachments").getCompoundOrEmpty(OldNames.OLD + ":progress");
		int oldWords = oldProgress.getListOrEmpty("words").size();
		check(oldWords > 20, "Max's live progress has only " + oldWords + " words");
		NbtIo.writeCompressed(max, players.resolve(MAX + ".dat"));

		// Ava, saved inside the old classroom, mid-visit: a diamond in the stash, an emerald waiting.
		FakePlayer stand = FakePlayer.get(overworld, new GameProfile(UUID.randomUUID(), "Stand"));
		stand.getInventory().clearContent();
		stand.getInventory().add(new ItemStack(Items.DIAMOND, 2));
		stand.snapTo(8.5, overworld.getMinY() + 120, 8.5, 0, 0);
		Visit visit = Visit.snapshot(stand).withPrizes(List.of(new ItemStack(Items.EMERALD)), 0);
		CompoundTag ava = NbtIo.readCompressed(live.resolve("players/" + AVA + ".dat"), NbtAccounter.unlimitedHeap());
		CompoundTag attachments = ava.getCompoundOrEmpty("fabric:attachments");
		attachments.put(OldNames.OLD + ":visit", Visit.CODEC.encodeStart(ops, visit).getOrThrow());
		ava.put("fabric:attachments", attachments);
		ava.putString("Dimension", OldNames.OLD_CLASSROOM);
		ListTag pos = new ListTag();
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(2056.5));
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(64.0));
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(2059.5));
		ava.put("Pos", pos);
		ava.put("Inventory", new ListTag());
		NbtIo.writeCompressed(ava, players.resolve(AVA + ".dat"));

		// Max loads and joins.
		FakePlayer max = load(server, MAX, "TestKidA");
		OldNames.migratePlayer(max);
		Progress progress = Progress.of(max);
		check(progress.words().size() == oldWords, "Max has " + progress.words().size() + " words read, not " + oldWords);
		check(progress.words().contains(LearningBlocks.MOD_ID + ":cat"), "Max's words are not under the new name: " + progress.words());
		check(progress.words().stream().noneMatch(word -> word.startsWith(OldNames.OLD)), "an old word id is left in Max's progress");
		check(progress.lessonsOwed() == 9, "Max's owed lessons are " + progress.lessonsOwed() + ", not 9");
		String written = Progress.SAVED.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), progress).getOrThrow().toString();
		check(!written.contains(OldNames.OLD + ":"), "Max's progress still names the old id somewhere: " + written);
		check(written.contains("used_today") && written.contains("stages_paid"), "Max's memory or payouts did not come through: " + written);
		ItemStack plush = max.getInventory().getItem(30);
		ItemStack tile = max.getInventory().getItem(31);
		check(plush.is(Plushies.item(Plush.OWL)), "the old-id owl plushie in Max's pack loaded as " + plush);
		check(tile.is(LearningBlocks.LETTER_ITEM) && tile.getCount() == 3, "the old-id letter tiles in Max's pack loaded as " + tile);

		// An old-id plushie in a chunk's palette: chunks read their blocks with the full block state codec.
		CompoundTag saved = new CompoundTag();
		saved.putString("Name", OldNames.OLD + ":plush");
		CompoundTag properties = new CompoundTag();
		properties.putString("kind", "owl");
		properties.putString("facing", "east");
		saved.put("Properties", properties);
		// 26's chunk sections write a block state as {id, Properties}.
		CompoundTag inChunk = saved.copy();
		inChunk.remove("Name");
		inChunk.putString("id", OldNames.OLD + ":plush");
		BlockState block = BlockState.CODEC.parse(ops, inChunk).getOrThrow();
		check(block.is(LearningBlocks.PLUSH) && block.getValue(PlushBlock.KIND) == Plush.OWL, "an old-id plushie in a chunk reads as " + block);
		// Not covered: a structure template's palette (NbtUtils.readBlockState, by registry lookup)
		// does not see the aliases, and reads an old-id block as air. Nothing of ours is saved that way.
		check(BuiltInRegistries.ITEM.getValue(OldNames.old("toy_car")) != Items.AIR, "the old toy car id does not resolve");

		// The settings: config/phonics/ copied across, Max's 10 a day and the prizes file read from it.
		Path scratch = FabricLoader.getInstance().getConfigDir().resolve("migration-test");
		Path oldConfig = scratch.resolve("phonics");
		Files.createDirectories(oldConfig);
		for (String name : List.of("prizes.json", "schedules.json")) {
			Files.copy(live.resolve("config-phonics").resolve(name), oldConfig.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		java.nio.file.Path newConfig = scratch.resolve("learning-blocks");
		if (Files.exists(newConfig)) {
			for (String name : List.of("prizes.json", "schedules.json")) Files.deleteIfExists(newConfig.resolve(name));
			Files.deleteIfExists(newConfig);
		}
		check(OldNames.copyConfig(scratch), "config/phonics/ was not copied");
		check(Files.exists(oldConfig.resolve("schedules.json")), "the old settings folder was not kept");
		JsonObject schedules = JsonParser.parseString(Files.readString(newConfig.resolve("schedules.json"))).getAsJsonObject();
		check(schedules.toString().contains(MAX.toString()), "Max's schedule did not come across: " + schedules);
		// Read by the mod itself from where it reads its settings, then put back.
		Path realSchedules = Schedules.file();
		Path realPrizes = PrizeConfig.file();
		byte[] keptSchedules = Files.exists(realSchedules) ? Files.readAllBytes(realSchedules) : null;
		byte[] keptPrizes = Files.exists(realPrizes) ? Files.readAllBytes(realPrizes) : null;
		try {
			Files.createDirectories(realSchedules.getParent());
			Files.copy(newConfig.resolve("schedules.json"), realSchedules, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.copy(newConfig.resolve("prizes.json"), realPrizes, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Schedules.load();
			check(Schedules.of(MAX) != null && Schedules.of(MAX).lessons() == 10, "Max's schedule is not 10 a day: " + Schedules.of(MAX));
			List<String> problems = PrizeConfig.load(server);
			check(problems.isEmpty(), "the live prizes file read with problems: " + problems);
		} finally {
			if (keptSchedules != null) Files.write(realSchedules, keptSchedules); else Files.deleteIfExists(realSchedules);
			if (keptPrizes != null) Files.write(realPrizes, keptPrizes); else Files.deleteIfExists(realPrizes);
			Schedules.load();
			PrizeConfig.load(server);
		}

		// Ava logs in: home, the diamond and the emerald, the visit over.
		FakePlayer ava = load(server, AVA, "TestKidB");
		OldNames.migratePlayer(ava);
		check(Visit.of(ava) != null, "Ava's old visit did not come through");
		Travel.loggedIn(ava);
		check(Visit.of(ava) == null, "Ava's visit is still open");
		check(ava.getInventory().countItem(Items.DIAMOND) == 2, "Ava's stash did not come back");
		check(ava.getInventory().countItem(Items.EMERALD) == 1, "Ava's waiting prize did not come home");
		check(ava.position().distanceTo(new net.minecraft.world.phys.Vec3(8.5, overworld.getMinY() + 120, 8.5)) < 1.0,
			"Ava is not home, but at " + ava.position());
		LoggerFactory.getLogger("learning-blocks-gametest").info("Migration: Max {} words, Ava home with {}", progress.words().size(),
			ava.getInventory().countItem(Items.DIAMOND));
		max.discard();
		ava.discard();
		stand.discard();
	}

	/** A player read from their saved file as the game reads one joining: their own data, into a stand-in. */
	private static FakePlayer load(MinecraftServer server, UUID id, String name) {
		FakePlayer player = FakePlayer.get(server.overworld(), new GameProfile(id, name));
		CompoundTag data = server.getPlayerList().loadPlayerData(new NameAndId(id, name)).orElseThrow(() -> new AssertionError("no saved data for " + name));
		player.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), data));
		return player;
	}

	/** The copy of the live data, found by walking up from where the game runs; null where there is none. */
	private static Path liveCopy() {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path live = at.resolve(".rounds/live-copy");
			if (Files.isDirectory(live.resolve("players"))) return live;
		}
		return null;
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
