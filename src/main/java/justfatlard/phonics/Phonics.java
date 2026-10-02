package justfatlard.phonics;

import justfatlard.pandorical.api.ActionMenuApi;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.phonics.classroom.Classroom;
import justfatlard.phonics.classroom.Places;
import justfatlard.phonics.classroom.Plots;
import justfatlard.phonics.classroom.Travel;
import justfatlard.phonics.classroom.Visit;
import justfatlard.phonics.command.PhonicsCommand;
import justfatlard.phonics.editor.PrizeEditor;
import justfatlard.phonics.editor.PrizeSessions;
import justfatlard.phonics.editor.ServerSettings;
import justfatlard.phonics.narration.CaptionSettings;
import justfatlard.phonics.narration.Narrator;
import justfatlard.phonics.puzzle.BoardLetterBlock;
import justfatlard.phonics.puzzle.LetterBlock;
import justfatlard.phonics.puzzle.Rooms;
import justfatlard.phonics.puzzle.Words;
import justfatlard.phonics.review.Review;
import justfatlard.phonics.reward.PrizeConfig;
import justfatlard.phonics.reward.Progress;
import justfatlard.phonics.sound.Captions;
import justfatlard.phonics.sound.Sounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phonics: early-reading puzzles for young children, in a classroom dimension of their own.
 *
 * <p>Everything is wired here, in the order it has to happen: the letter block before Pandorical is
 * told about it, the attachments before any player can load, the events last.
 */
public class Phonics implements ModInitializer {
	public static final String MOD_ID = "phonics-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final Identifier LETTER_ID = id("letter");
	private static final ResourceKey<Block> LETTER_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, LETTER_ID);
	private static final ResourceKey<Item> LETTER_ITEM_KEY = ResourceKey.create(Registries.ITEM, LETTER_ID);

	/**
	 * One block for every letter, told apart by its {@code letter} state. Broken by hand in about
	 * a third of a second, the way a child knocks a tile loose: hardness 0.2 is six ticks.
	 */
	public static final LetterBlock LETTER = new LetterBlock(BlockBehaviour.Properties.of()
		.strength(0.2F, 3600000.0F)
		.sound(SoundType.WOOL)
		.setId(LETTER_BLOCK_KEY));

	public static final Identifier BOARD_LETTER_ID = id("board_letter");

	/** The letters of the word the board gives, and the gap's underscore: part of the room. */
	public static final BoardLetterBlock BOARD_LETTER = new BoardLetterBlock(BlockBehaviour.Properties.of()
		.strength(-1.0F, 3600000.0F)
		.sound(SoundType.WOOL)
		.noLootTable()
		.setId(ResourceKey.create(Registries.BLOCK, BOARD_LETTER_ID)));

	public static final BlockItem LETTER_ITEM = new BlockItem(LETTER, new Item.Properties()
		.setId(LETTER_ITEM_KEY)
		.useBlockDescriptionPrefix());

	public static final Identifier PLUSH_ID = id("plush");

	/** A plushie: a soft toy, any of the set by its {@code kind}, to keep, place and hear. */
	public static final justfatlard.phonics.plush.PlushBlock PLUSH = new justfatlard.phonics.plush.PlushBlock(BlockBehaviour.Properties.of()
		.strength(0.3F, 1.0F)
		.sound(SoundType.WOOL)
		.noOcclusion()
		.setId(ResourceKey.create(Registries.BLOCK, PLUSH_ID)));

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BLOCK, LETTER_ID, LETTER);
		Registry.register(BuiltInRegistries.BLOCK, PLUSH_ID, PLUSH);
		justfatlard.phonics.plush.Plushies.register();
		Registry.register(BuiltInRegistries.ITEM, LETTER_ID, LETTER_ITEM);
		Registry.register(BuiltInRegistries.BLOCK, BOARD_LETTER_ID, BOARD_LETTER);

		// Pandorical content sync: the letter block, its item and every texture, model and sound
		// under assets/phonics-justfatlard. Interactive, because every letter in a room answers a
		// click, and a stand-in that thinks otherwise predicts placing whatever is in the hand.
		// No strength(): the server block is unbreakable (-1), and -1 is also the value that means
		// "inherit the base's", so there is no way to say so. Adventure mode keeps it moot.
		// Wool underneath: the stand-in plays its base's place, break and step sounds on top of the
		// letter's own voice, and wool's are a soft thud where planks are a clunk over the phoneme.
		// Not interactive: a tile is broken and placed the ordinary way, and a stand-in that thinks
		// a click is handled never predicts the placement. The client digs it at the server's pace.
		PandoricalApi.content().registerBlock(LETTER_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:white_wool")
			.strength(0.2F)
			.requiresCorrectTool(false));
		// The board's letters stand on bedrock underneath, which the client knows it cannot break.
		PandoricalApi.content().registerBlock(BOARD_LETTER_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:bedrock"));
		PandoricalApi.content().registerItem(LETTER_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/letter"));
		// Plushies: soft, small, and a right-click squeaks.
		PandoricalApi.content().registerBlock(PLUSH_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:white_wool")
			.interactive()
			.strength(0.3F)
			.requiresCorrectTool(false));
		justfatlard.phonics.things.Things.register();
		justfatlard.phonics.toy.Toys.register();
		PandoricalApi.content().registerModAssets(MOD_ID);
		Sounds.init();

		// The way in, as a button on the server's own action menu, so nobody has to type.
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs("minecraft:book", "Phonics", "phonics"));
		PandoricalApi.commandHelp().describe("/phonics", "Go to your phonics classroom.");
		// Hearing the letters and your words again, from anywhere: safe for any child to press.
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs("minecraft:note_block", "Letter sounds", "phonics sounds"));
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs("minecraft:written_book", "My words", "phonics words"));
		// A second way out of the classroom, always there: refused, with the count, while lessons keep a child in.
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs("minecraft:villager_spawn_egg", "Silly sentences", "phonics silly"));
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs("minecraft:ender_pearl", "Go home", "phonics leave"));
		PandoricalApi.commandHelp().describe("/phonics sounds", "Hear every letter's sound.");
		PandoricalApi.commandHelp().describe("/phonics vowels", "Hear the vowels, short and long.");
		PandoricalApi.commandHelp().describe("/phonics words", "See and hear the words you have read.");
		PandoricalApi.commandHelp().describe("/phonics words all", "Every word the server has, read or not.");
		PandoricalApi.commandHelp().describe("/phonics words <player>", "The words another player has read.");
		PandoricalApi.commandHelp().describe("/phonics phrases", "Hear every recorded phrase, with its caption and length.");
		// Operators only: Brigadier leaves the command out of the list anybody else's client is sent,
		// so the mods screen offers it, and its Add button, to operators alone.
		PandoricalApi.commandHelp().describe("/phonics prizes", "Change what reading earns: events, items, loot.");
		PandoricalApi.commandHelp().describe("/phonics send <player> [lessons]",
			"Send a child to the classroom; with lessons, the door stays shut until they are done.");
		PandoricalApi.commandHelp().describe("/phonics lessons <player>", "How many lessons a child has left.");
		PandoricalApi.commandHelp().describe("/phonics release <player>", "Clear a child's lessons, opening the door.");
		PandoricalApi.commandHelp().describe("/phonics reload", "Read config/phonics/prizes.json again after a hand edit.");
		PandoricalApi.commandHelp().describe("/phonics leave",
			"Go home from the classroom, with everything you went in with.");

		Visit.init();
		Plots.init();
		Progress.init();
		PrizeSessions.init();
		PrizeEditor.register(PandoricalApi.screens());
		Review.register(PandoricalApi.screens());
		justfatlard.phonics.review.ProgressView.register(PandoricalApi.screens());
		justfatlard.phonics.review.SamplerScreen.register(PandoricalApi.screens());
		justfatlard.phonics.puzzle.PrizeChestScreen.register(PandoricalApi.screens());
		CaptionSettings.register();
		ServerSettings.register();

		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Words());
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Captions());

		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			PhonicsCommand.register(dispatcher));

		ServerPlayerEvents.JOIN.register(Travel::loggedIn);
		ServerPlayerEvents.JOIN.register(PrizeSessions::loggedIn);
		ServerPlayerEvents.AFTER_RESPAWN.register((old, fresh, alive) -> Travel.respawned(fresh));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(Travel::changedLevel);
		// A local connection reports its disconnect on its network thread, and a room closing touches
		// the world, which only the server thread may: from anywhere else it can wait on the server
		// for a chunk while the server waits on something else, for good. So it is handed over.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// The room they had as they went: a forget queued behind a quick re-login leaves the new one be.
			justfatlard.phonics.puzzle.Room had = Rooms.of(handler.player);
			Runnable forget = () -> {
				Rooms.forget(handler.player, had);
				justfatlard.phonics.sound.Sounds.forget(handler.player.getUUID());
				PrizeEditor.forget(handler.player.getUUID());
				Review.forget(handler.player.getUUID());
				justfatlard.phonics.review.ProgressView.forget(handler.player.getUUID());
				justfatlard.phonics.classroom.Schedules.forget(handler.player.getUUID());
			};
			if (server.isSameThread()) forget.run();
			else server.execute(forget);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Classroom::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Later.tick(server);
			Narrator.tick(server);
			Rooms.tick(server);
			Classroom.tick(server);
			justfatlard.phonics.Safely.run("Daily schedules", () -> justfatlard.phonics.classroom.Schedules.tick(server));
		});
		// Prizes name items and loot tables, which only exist once the server's registries do.
		ServerLifecycleEvents.SERVER_STARTED.register(PrizeConfig::load);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> justfatlard.phonics.classroom.Schedules.load());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Later.clear();
			justfatlard.phonics.reward.Progress.forgetPractice();
			Narrator.clear();
			Rooms.clear();
			Review.clear();
			justfatlard.phonics.review.ProgressView.clear();
			PrizeEditor.clear();
			justfatlard.phonics.puzzle.Punches.clear();
			justfatlard.phonics.sound.Sounds.forgetVariants();
		});

		UseBlockCallback.EVENT.register(Rooms::useBlock);
		// Before other mods' listeners: what a child clicks in the classroom is the classroom's, and a
		// room villager must not open another mod's dialogue or a trade.
		net.minecraft.resources.Identifier first = id("classroom_first");
		UseEntityCallback.EVENT.addPhaseOrdering(first, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		AttackEntityCallback.EVENT.addPhaseOrdering(first, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> Rooms.useEntity(player, level, hand, entity));
		AttackEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> Rooms.clickEntity(player, level, entity));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> Rooms.mayBreak(level, player, pos, state));

		// What Pandorical offers a player on arrival - the brief, what changed, the action menus -
		// waits while they are in the classroom: a child in the middle of a word does not need a
		// tray of notices about the server. It arrives when they come out.
		PandoricalApi.heldWhile(player -> Places.isClassroom(player.level()));
		UseItemCallback.EVENT.register(Rooms::useItem);

		LOGGER.info("[{}] Loaded", MOD_ID);
	}
}
