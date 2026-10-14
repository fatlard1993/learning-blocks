package justfatlard.learning_blocks;

import justfatlard.pandorical.api.ActionMenuApi;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.learning_blocks.classroom.Classroom;
import justfatlard.learning_blocks.classroom.Places;
import justfatlard.learning_blocks.classroom.Plots;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.classroom.Visit;
import justfatlard.learning_blocks.command.LearnCommand;
import justfatlard.learning_blocks.editor.PrizeEditor;
import justfatlard.learning_blocks.editor.PrizeSessions;
import justfatlard.learning_blocks.editor.ServerSettings;
import justfatlard.learning_blocks.narration.CaptionSettings;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.BoardLetterBlock;
import justfatlard.learning_blocks.puzzle.LetterBlock;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.learning_blocks.puzzle.Words;
import justfatlard.learning_blocks.review.Review;
import justfatlard.learning_blocks.reward.PrizeConfig;
import justfatlard.learning_blocks.reward.Progress;
import justfatlard.learning_blocks.sound.Captions;
import justfatlard.learning_blocks.sound.Sounds;
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
 * Learning Blocks: learning games for young children, in a classroom dimension of their own: Reading, Numbers and Logic.
 *
 * <p>Everything is wired here, in the order it has to happen: the letter block before Pandorical is
 * told about it, the attachments before any player can load, the events last.
 */
public class LearningBlocks implements ModInitializer {
	public static final String MOD_ID = "learning-blocks-justfatlard";
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

	public static final Identifier NUMBER_ID = id("number");

	public static final justfatlard.learning_blocks.numbers.NumberBlock NUMBER = new justfatlard.learning_blocks.numbers.NumberBlock(BlockBehaviour.Properties.of()
		.strength(0.2F, 3600000.0F)
		.sound(SoundType.WOOL)
		.setId(ResourceKey.create(Registries.BLOCK, NUMBER_ID)));

	public static final BlockItem NUMBER_ITEM = new BlockItem(NUMBER, new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, NUMBER_ID))
		.useBlockDescriptionPrefix());

	public static final Identifier PLUSH_ID = id("plush");

	public static final justfatlard.learning_blocks.plush.PlushBlock PLUSH = new justfatlard.learning_blocks.plush.PlushBlock(BlockBehaviour.Properties.of()
		// Breaks at one punch, like a torch: a toy is easy to pick back up.
		.instabreak()
		.sound(SoundType.WOOL)
		.noOcclusion()
		.setId(ResourceKey.create(Registries.BLOCK, PLUSH_ID)));

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BLOCK, LETTER_ID, LETTER);
		Registry.register(BuiltInRegistries.BLOCK, PLUSH_ID, PLUSH);
		justfatlard.learning_blocks.plush.Plushies.register();
		Registry.register(BuiltInRegistries.ITEM, LETTER_ID, LETTER_ITEM);
		Registry.register(BuiltInRegistries.BLOCK, BOARD_LETTER_ID, BOARD_LETTER);
		Registry.register(BuiltInRegistries.BLOCK, NUMBER_ID, NUMBER);
		Registry.register(BuiltInRegistries.ITEM, NUMBER_ID, NUMBER_ITEM);
		// The counting and thinking toys, each earned by mastering some numbers or logic.
		justfatlard.learning_blocks.subject.Numbers.linkToys();
		justfatlard.learning_blocks.subject.Logic.linkToys();

		// Pandorical content sync: the letter block, its item and every texture, model and sound
		// under assets/learning-blocks-justfatlard. Interactive, because every letter in a room answers a
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
		PandoricalApi.content().registerBlock(NUMBER_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:white_wool")
			.interactive()
			.strength(0.2F)
			.requiresCorrectTool(false));
		PandoricalApi.content().registerItem(NUMBER_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/number"));
		PandoricalApi.content().registerBlock(PLUSH_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:white_wool")
			.interactive()
			.strength(0.0F)
			.requiresCorrectTool(false));
		justfatlard.learning_blocks.props.Props.register();
		justfatlard.learning_blocks.toy.Toys.register();
		justfatlard.learning_blocks.play.Play.register();
		justfatlard.learning_blocks.classroom.Sky.register();
		justfatlard.learning_blocks.feel.Feelings.register();
		justfatlard.learning_blocks.feel.Gestures.register();
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			if (justfatlard.learning_blocks.classroom.Places.isClassroom(from)) justfatlard.learning_blocks.classroom.Sky.forget(player);
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
			if (!Rooms.relogged(handler.player)) justfatlard.learning_blocks.classroom.Sky.forget(handler.player);
		}));
		PandoricalApi.content().registerModAssets(MOD_ID);
		Sounds.init();

		// The way in, on the first page the menu key opens, so nobody has to type or look for it.
		// Each button wears one of the classroom's own toys, which Pandorical gives the client.
		PandoricalApi.actionMenus().suggestTopButton(
			ActionMenuApi.Button.runs(MOD_ID + ":toy_block", "Learning Blocks", "learn"));
		// Hearing the letters and your words again, from anywhere: safe for any child to press.
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs(MOD_ID + ":toy_drum", "Letter sounds", "learn sounds"));
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs(MOD_ID + ":toy_puzzle", "My words", "learn words"));
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs(MOD_ID + ":toy_wig", "Silly sentences", "learn silly"));
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs(MOD_ID + ":toy_bus", "Go home", "learn leave"));
		LearnCommand.describe();

		Visit.init();
		Plots.init();
		Progress.init();
		PrizeSessions.init();
		PrizeEditor.register(PandoricalApi.screens());
		Review.register(PandoricalApi.screens());
		justfatlard.learning_blocks.review.ProgressView.register(PandoricalApi.screens());
		justfatlard.learning_blocks.review.SamplerScreen.register(PandoricalApi.screens());
		justfatlard.learning_blocks.puzzle.PrizeChestScreen.register(PandoricalApi.screens());
		CaptionSettings.register();
		ServerSettings.register();

		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new justfatlard.learning_blocks.relate.Relations());
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Words());
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Captions());

		justfatlard.learning_blocks.classroom.JoinRequests.listen();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			LearnCommand.register(dispatcher));

		ServerPlayerEvents.JOIN.register(Travel::loggedIn);
		ServerPlayerEvents.JOIN.register(PrizeSessions::loggedIn);
		ServerPlayerEvents.AFTER_RESPAWN.register((old, fresh, alive) -> Travel.respawned(fresh));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(Travel::changedLevel);
		// A local connection reports its disconnect on its network thread, and a room closing touches
		// the world, which only the server thread may: from anywhere else it can wait on the server
		// for a chunk while the server waits on something else, for good. So it is handed over.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// The room they had as they went: a forget queued behind a quick re-login leaves the new one be.
			justfatlard.learning_blocks.puzzle.Room had = Rooms.of(handler.player);
			Runnable forget = () -> {
				// Already back (a quick re-login): everything belongs to the new session now.
				if (Rooms.relogged(handler.player)) return;
				Rooms.forget(handler.player, had);
				justfatlard.learning_blocks.sound.Sounds.forget(handler.player.getUUID());
				PrizeEditor.forget(handler.player.getUUID());
				Review.forget(handler.player.getUUID());
				justfatlard.learning_blocks.review.ProgressView.forget(handler.player.getUUID());
				justfatlard.learning_blocks.classroom.Schedules.forget(handler.player.getUUID());
				justfatlard.learning_blocks.classroom.JoinRequests.forget(server, handler.player.getUUID());
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
			justfatlard.learning_blocks.Safely.run("Daily schedules", () -> justfatlard.learning_blocks.classroom.Schedules.tick(server));
		});
		// Prizes name items and loot tables, which only exist once the server's registries do.
		ServerLifecycleEvents.SERVER_STARTED.register(PrizeConfig::load);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> justfatlard.learning_blocks.classroom.Schedules.load());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Later.clear();
			justfatlard.learning_blocks.classroom.JoinRequests.clear();
			justfatlard.learning_blocks.reward.Progress.forgetPractice();
			Narrator.clear();
			Rooms.clear();
			Review.clear();
			justfatlard.learning_blocks.review.ProgressView.clear();
			PrizeEditor.clear();
			justfatlard.learning_blocks.puzzle.Punches.clear();
			justfatlard.learning_blocks.sound.Sounds.forgetVariants();
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
