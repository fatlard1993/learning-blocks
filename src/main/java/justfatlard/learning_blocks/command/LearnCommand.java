package justfatlard.learning_blocks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import justfatlard.learning_blocks.puzzle.Stages;
import justfatlard.learning_blocks.review.Review;
import justfatlard.learning_blocks.reward.Progress;
import net.minecraft.commands.arguments.EntityArgument;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.editor.PrizeEditor;
import justfatlard.learning_blocks.editor.PrizeSessions;
import justfatlard.learning_blocks.reward.PrizeConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /learn} to go to the classroom, {@code /learn leave} to come home. Anybody may use
 * either: the classroom is for everyone, and the door is the same door for everybody.
 * {@code /learn reload}, for operators, reads the prizes file again, and {@code /learn prizes}
 * opens the editor that writes it. {@code /learn stage <player> [n]} shows or sets where a child is.
 * {@code /learn send <player> [lessons]} sends a child in, the door locked until the lessons are
 * done; {@code lessons} and {@code release} report and clear them. {@code sounds}, {@code vowels},
 * {@code words} and (for operators) {@code phrases} open the review screens.
 */
public final class LearnCommand {
	private LearnCommand() {}

	/** The main command; and its old name, kept as another way in to every subcommand. */
	public static final String NAME = "learn";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> learn = dispatcher.register(Commands.literal(NAME)
			.executes(LearnCommand::enter)
			.then(Commands.literal("leave").executes(LearnCommand::leave))
			// Reading together: ask to join someone, and their yes or no.
			.then(Commands.literal("join").then(Commands.argument("player", EntityArgument.player()).executes(LearnCommand::join)))
			.then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player()).executes(context -> answer(context, true))))
			.then(Commands.literal("decline").then(Commands.argument("player", EntityArgument.player()).executes(context -> answer(context, false))))
			// The grown-ups' sampler: any kind of round, now, as practice.
			.then(Commands.literal("try")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.executes(context -> withPlayer(context, viewer -> justfatlard.learning_blocks.review.SamplerScreen.open(viewer) ? null
					: "The sampler screen needs Pandorical on your game; /learn try <kind> [word] works without it"))
				.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
						java.util.stream.Stream.concat(justfatlard.learning_blocks.puzzle.Sampler.kindNames().stream(),
							justfatlard.learning_blocks.subject.Subjects.ids().stream().filter(id -> !id.equals("reading"))).toList(), builder))
					.executes(context -> sample(context, null, null))
					.then(Commands.argument("word", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((context, builder) -> {
							String first = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind");
							var subject = justfatlard.learning_blocks.subject.Subjects.of(first);
							if (subject != null && subject != justfatlard.learning_blocks.subject.Subjects.READING) {
								return net.minecraft.commands.SharedSuggestionProvider.suggest(subject.kinds(), builder);
							}
							var kind = justfatlard.learning_blocks.puzzle.Sampler.Kind.of(first);
							return net.minecraft.commands.SharedSuggestionProvider.suggest(kind == null ? List.of() : justfatlard.learning_blocks.puzzle.Sampler.targets(kind), builder);
						})
						.executes(context -> sample(context, com.mojang.brigadier.arguments.StringArgumentType.getString(context, "word"), null))
						// Another subject's: /learn try numbers find 3.
						.then(Commands.argument("target", com.mojang.brigadier.arguments.StringArgumentType.word())
							.suggests((context, builder) -> {
								var subject = justfatlard.learning_blocks.subject.Subjects.of(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind"));
								return net.minecraft.commands.SharedSuggestionProvider.suggest(subject == null ? List.of()
									: subject.targets(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "word")), builder);
							})
							.executes(context -> sample(context, com.mojang.brigadier.arguments.StringArgumentType.getString(context, "word"),
								com.mojang.brigadier.arguments.StringArgumentType.getString(context, "target")))))))
			.then(Commands.literal("pair")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("other", EntityArgument.player()).executes(LearnCommand::pair))))
			.then(Commands.literal("unpair")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player()).executes(LearnCommand::unpair)))
			.then(Commands.literal("sounds").executes(context -> review(context, Review.Tab.LETTERS, null, false)))
			.then(Commands.literal("vowels").executes(context -> review(context, Review.Tab.VOWELS, null, false)))
			.then(Commands.literal("silly").executes(context -> withPlayer(context, justfatlard.learning_blocks.puzzle.Rooms::silly)))
			.then(Commands.literal("toybox").executes(context -> review(context, Review.Tab.TOYBOX, null, false)))
			.then(Commands.literal("plushies").executes(context -> review(context, Review.Tab.TOYBOX, null, false)))
			.then(Commands.literal("words")
				.executes(context -> review(context, Review.Tab.WORDS, null, false))
				.then(Commands.literal("all")
					.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
					.executes(context -> review(context, Review.Tab.WORDS, null, true)))
				.then(Commands.argument("player", EntityArgument.player())
					.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
					.executes(context -> review(context, Review.Tab.WORDS, EntityArgument.getPlayer(context, "player"), false))))
			.then(Commands.literal("phrases")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.executes(context -> review(context, Review.Tab.PHRASES, null, false)))
			.then(Commands.literal("reload")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.executes(LearnCommand::reload))
			.then(Commands.literal("send")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> send(context, null))
					.then(Commands.argument("lessons", IntegerArgumentType.integer(1, justfatlard.learning_blocks.classroom.Travel.MOST_LESSONS))
						.executes(context -> send(context, IntegerArgumentType.getInteger(context, "lessons"))))))
			.then(Commands.literal("lessons")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player()).executes(LearnCommand::lessons)))
			.then(Commands.literal("home")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player()).executes(LearnCommand::home)))
			// Plots of players long gone, given back to be handed out again.
			.then(Commands.literal("plots")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.literal("prune").then(Commands.argument("days", IntegerArgumentType.integer(1, 3650)).executes(context -> {
					int days = IntegerArgumentType.getInteger(context, "days");
					int retired = justfatlard.learning_blocks.classroom.Plots.prune(context.getSource().getServer(), days);
					context.getSource().sendSuccess(() -> Component.literal("Gave back " + retired + (retired == 1 ? " plot" : " plots")
						+ " of players not seen in " + days + " days"), true);
					return 1;
				}))))
			// A lost plushie or toy back: only one the child has earned.
			.then(Commands.literal("give")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("thing", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(earnedThings(EntityArgument.getPlayer(context, "player")), builder))
						.executes(LearnCommand::give))))
			.then(Commands.literal("release")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player()).executes(LearnCommand::release)))
			.then(Commands.literal("subjects")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> subjects(context, null))
					.then(Commands.argument("settings", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
							justfatlard.learning_blocks.subject.Subjects.ids().stream().flatMap(id -> java.util.stream.Stream.of(id + " on", id + " off", id + " default")).toList(), builder))
						.executes(context -> subjects(context, com.mojang.brigadier.arguments.StringArgumentType.getString(context, "settings"))))))
			.then(Commands.literal("daily")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("reader", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(readerNames(context.getSource()), builder))
					.executes(context -> daily(context, null, null))
					.then(Commands.literal("off").executes(context -> daily(context, null, 0)))
					.then(Commands.literal("with").then(Commands.argument("other", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
							java.util.stream.Stream.concat(readerNames(context.getSource()).stream(), java.util.stream.Stream.of("nobody")).toList(), builder))
						.executes(LearnCommand::dailyWith)))
					.then(Commands.argument("lessons", IntegerArgumentType.integer(0, justfatlard.learning_blocks.classroom.Travel.MOST_LESSONS))
						.executes(context -> daily(context, null, IntegerArgumentType.getInteger(context, "lessons")))
						// Some of each subject: /learn daily Sam 10 reading 5 numbers 5.
						.then(Commands.argument("split", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
							.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								justfatlard.learning_blocks.subject.Subjects.ids().stream().map(id -> id + " 5").toList(), builder))
							.executes(LearnCommand::dailySplit)))
					.then(Commands.argument("weekday", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
							List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"), builder))
						.then(Commands.argument("count", IntegerArgumentType.integer(0, justfatlard.learning_blocks.classroom.Travel.MOST_LESSONS))
							.executes(context -> daily(context, com.mojang.brigadier.arguments.StringArgumentType.getString(context, "weekday"),
								IntegerArgumentType.getInteger(context, "count")))))))
			.then(Commands.literal("progress")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> withPlayer(context, viewer -> {
						ServerPlayer reader = playerOrOffline(context, "their progress opens when they're on");
						if (reader == null) return "";
						return justfatlard.learning_blocks.review.ProgressView.open(viewer, reader) ? null
							: "This needs the Pandorical mod on your game; /learn progress <player> export writes it to a file";
					}))
					.then(Commands.literal("export").executes(LearnCommand::export))))
			.then(Commands.literal("stage")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(LearnCommand::showStage)
					.then(Commands.argument("stage", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((context, builder) -> {
							builder.suggest("0");
							for (String label : Stages.labels()) builder.suggest(label);
							for (var band : justfatlard.learning_blocks.numbers.NumberCourse.BANDS) builder.suggest(band.code());
							for (var band : justfatlard.learning_blocks.logic.LogicCourse.BANDS) builder.suggest(band.code());
							return builder.buildFuture();
						})
						.executes(LearnCommand::setStage))))
			.then(Commands.literal("prizes")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.executes(context -> withPlayer(context, player -> {
					if (!PandoricalApi.isAvailable(player)) return "The prize editor needs Pandorical on your client; the file is config/learning-blocks/prizes.json";
					PrizeEditor.show(player, "GENERAL");
					return null;
				}))
				.then(Commands.literal("done").executes(context -> withPlayer(context, PrizeSessions::done)))
				.then(Commands.literal("cancel").executes(context -> withPlayer(context, PrizeSessions::cancel)))));
	}

	/** Everyone a daily schedule can be about: who is online, and who already has one. */
	private static List<String> readerNames(CommandSourceStack source) {
		java.util.Set<String> names = new java.util.TreeSet<>();
		for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) names.add(online.getGameProfile().name());
		for (justfatlard.learning_blocks.classroom.Schedules.Entry entry : justfatlard.learning_blocks.classroom.Schedules.all().values()) {
			if (!entry.name().isEmpty()) names.add(entry.name());
		}
		return List.copyOf(names);
	}

	/**
	 * A reader named for a schedule: online by name, or already scheduled by name (so an operator
	 * can change it while they are away), or a UUID. Null if none.
	 */
	private static java.util.Map.@Nullable Entry<java.util.UUID, String> reader(CommandSourceStack source, String name) {
		ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
		if (online != null) return java.util.Map.entry(online.getUUID(), online.getGameProfile().name());
		for (var entry : justfatlard.learning_blocks.classroom.Schedules.all().entrySet()) {
			if (entry.getValue().name().equalsIgnoreCase(name)) return java.util.Map.entry(entry.getKey(), entry.getValue().name());
		}
		try {
			java.util.UUID id = java.util.UUID.fromString(name);
			// By UUID: the real name, when the server knows it.
			String known = source.getServer().services().nameToIdCache().get(id).map(net.minecraft.server.players.NameAndId::name).orElse(name);
			return java.util.Map.entry(id, known);
		} catch (IllegalArgumentException e) {
			// Offline, and never scheduled: the server's own cache of names (usercache.json).
			return cached(source.getServer(), name);
		}
	}

	/**
	 * A name the server's profile cache already holds (someone who has joined), as its UUID and the
	 * name as it is spelled; null for any other. Never a lookup: on an offline-mode server that would
	 * make up an id for a name nobody has.
	 */
	static java.util.Map.@Nullable Entry<java.util.UUID, String> cached(net.minecraft.server.MinecraftServer server, String name) {
		if (name.isEmpty() || name.startsWith("@")) return null;
		var cache = server.services().nameToIdCache();
		if (!(cache instanceof justfatlard.learning_blocks.mixin.CachedNamesAccessor known)
			|| !known.learn$byName().containsKey(name.toLowerCase(java.util.Locale.ROOT))) return null;
		return cache.get(name)
			.map(found -> java.util.Map.entry(found.id(), found.name())).orElse(null);
	}

	/**
	 * The player named, online; or, for a name the server knows of someone offline, null with a
	 * message saying so and what to do, rather than "no player was found".
	 */
	private static @Nullable ServerPlayer playerOrOffline(CommandContext<CommandSourceStack> context, String offline) {
		try {
			return EntityArgument.getPlayer(context, "player");
		} catch (CommandSyntaxException e) {
			String raw = null;
			for (var node : context.getNodes()) if (node.getNode().getName().equals("player")) raw = node.getRange().get(context.getInput());
			var who = raw == null ? null : reader(context.getSource(), raw);
			context.getSource().sendFailure(Component.literal(who != null ? who.getValue() + " is offline: " + offline : e.getMessage()));
			return null;
		}
	}

	/** Every plushie and toy this child has earned or been given, as {@code owl}, {@code car}: what /learn give may give back. */
	private static List<String> earnedThings(ServerPlayer player) {
		Progress progress = Progress.of(player);
		List<String> out = new java.util.ArrayList<>();
		for (var plush : justfatlard.learning_blocks.plush.Plush.values()) {
			if (progress.plushies().getOrDefault(plush, 0) > 0 || justfatlard.learning_blocks.reward.Earned.earned(progress, "plush:" + plush.word())) out.add(plush.word());
		}
		for (var toy : justfatlard.learning_blocks.toy.Toy.values()) {
			if (progress.toys().getOrDefault(toy, 0) > 0 || justfatlard.learning_blocks.reward.Earned.earned(progress, "toy:" + toy.word())) out.add(toy.word());
		}
		return out;
	}

	/** One the child has earned, given again: into their pack at home, or with their prizes if they're in the classroom. */
	private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		String thing = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "thing").toLowerCase(java.util.Locale.ROOT);
		if (!earnedThings(player).contains(thing)) {
			context.getSource().sendFailure(Component.literal(player.getGameProfile().name() + " hasn't earned a " + thing + "; they can have: "
				+ String.join(", ", earnedThings(player))));
			return 0;
		}
		var plush = justfatlard.learning_blocks.plush.Plush.of(thing);
		net.minecraft.world.item.ItemStack stack = plush != null ? justfatlard.learning_blocks.plush.Plushies.stack(plush)
			: justfatlard.learning_blocks.toy.Toys.stack(justfatlard.learning_blocks.toy.Toy.of(thing));
		justfatlard.learning_blocks.classroom.Visit visit = justfatlard.learning_blocks.classroom.Visit.of(player);
		if (visit != null) {
			justfatlard.learning_blocks.classroom.Visit.set(player, visit.withPrizes(List.of(stack), 0));
			justfatlard.learning_blocks.puzzle.Rooms.prizeAdded(player, List.of(stack), 0);
		} else if (!player.getInventory().add(stack)) player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), stack);
		context.getSource().sendSuccess(() -> Component.literal("Gave " + player.getGameProfile().name() + " their " + thing
			+ (visit != null ? "; it comes home with their prizes" : "")), true);
		return 1;
	}

	/** "Can I learn with you?": put to the other child as a question with Accept and Deny. */
	private static int join(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer guest = context.getSource().getPlayerOrException();
		ServerPlayer host = EntityArgument.getPlayer(context, "player");
		String refused = justfatlard.learning_blocks.classroom.JoinRequests.ask(guest, host);
		if (refused != null) {
			context.getSource().sendFailure(Component.literal(refused));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Asked " + host.getGameProfile().name() + "; waiting for an answer"), false);
		return 1;
	}

	/** The one asked says yes (in they come) or no. */
	private static int answer(CommandContext<CommandSourceStack> context, boolean yes) throws CommandSyntaxException {
		ServerPlayer host = context.getSource().getPlayerOrException();
		ServerPlayer guest = EntityArgument.getPlayer(context, "player");
		String refused = justfatlard.learning_blocks.classroom.JoinRequests.answer(host, guest.getUUID(), yes);
		if (refused != null) {
			context.getSource().sendFailure(Component.literal(refused));
			return 0;
		}
		return 1;
	}

	/** A practice round of a kind, now, in the caller's classroom. */
	private static int sample(CommandContext<CommandSourceStack> context, @Nullable String word, @Nullable String target) {
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind");
		// Another subject: /learn try numbers [kind [target]].
		var subject = justfatlard.learning_blocks.subject.Subjects.of(name);
		if (subject != null && subject != justfatlard.learning_blocks.subject.Subjects.READING) {
			return withPlayer(context, player -> justfatlard.learning_blocks.puzzle.Sampler.runSubject(player, subject, word, target));
		}
		if (target != null) {
			context.getSource().sendFailure(Component.literal("Reading rounds take one word: /learn try " + name + " <word>"));
			return 0;
		}
		var kind = justfatlard.learning_blocks.puzzle.Sampler.Kind.of(name);
		if (kind == null) {
			context.getSource().sendFailure(Component.literal("No kind of round called \"" + name + "\". Kinds: "
				+ String.join(", ", justfatlard.learning_blocks.puzzle.Sampler.kindNames())));
			return 0;
		}
		return withPlayer(context, player -> justfatlard.learning_blocks.puzzle.Sampler.run(player, kind, word));
	}

	/** Two readers in one room (operators): the first's, in the classroom if they weren't. */
	private static int pair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer first = EntityArgument.getPlayer(context, "player");
		ServerPlayer second = EntityArgument.getPlayer(context, "other");
		if (first == second) {
			context.getSource().sendFailure(Component.literal("Pick two different readers"));
			return 0;
		}
		var room = justfatlard.learning_blocks.puzzle.Rooms.of(first);
		if (room == null) {
			String refused = Travel.enter(first);
			room = justfatlard.learning_blocks.puzzle.Rooms.of(first);
			if (refused != null || room == null) {
				context.getSource().sendFailure(Component.literal(first.getGameProfile().name() + ": " + (refused != null ? refused : "couldn't go in")));
				return 0;
			}
		}
		String refused = Travel.readWith(second, room);
		if (refused != null) {
			context.getSource().sendFailure(Component.literal(second.getGameProfile().name() + ": " + refused));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(first.getGameProfile().name() + " and " + second.getGameProfile().name()
			+ " are reading together"), true);
		return 1;
	}

	/** A shared room split up (operators): everyone but the one whose plot it is gets their own. */
	private static int unpair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		var room = justfatlard.learning_blocks.puzzle.Rooms.of(player);
		if (room == null || !room.shared()) {
			context.getSource().sendFailure(Component.literal(player.getGameProfile().name() + " isn't reading with anyone"));
			return 0;
		}
		java.util.List<ServerPlayer> movers = new java.util.ArrayList<>();
		for (java.util.UUID id : room.members()) {
			ServerPlayer one = context.getSource().getServer().getPlayerList().getPlayer(id);
			if (one != null && !justfatlard.learning_blocks.classroom.Plots.origin(one).equals(room.origin())) movers.add(one);
		}
		// Nobody's own plot (its owner went home): all but the first move.
		if (movers.size() == room.members().size()) movers.removeFirst();
		for (ServerPlayer one : movers) Travel.readAlone(one);
		context.getSource().sendSuccess(() -> Component.literal("Reading on their own again"), true);
		return 1;
	}

	/** Daily reading together: these two start paired when both are on (or "nobody"). */
	private static int dailyWith(CommandContext<CommandSourceStack> context) {
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "reader");
		String otherName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "other");
		var who = reader(context.getSource(), name);
		if (who == null) {
			context.getSource().sendFailure(Component.literal("I don't know a player called " + name + " yet; have them join once"));
			return 0;
		}
		if (otherName.equalsIgnoreCase("nobody")) {
			justfatlard.learning_blocks.classroom.Schedules.setTogether(who.getKey(), who.getValue(), null, "");
			context.getSource().sendSuccess(() -> Component.literal(who.getValue() + " reads on their own at daily reading time"), true);
			return 1;
		}
		var other = reader(context.getSource(), otherName);
		if (other == null || other.getKey().equals(who.getKey())) {
			context.getSource().sendFailure(Component.literal(other == null ? "I don't know a player called " + otherName + " yet; have them join once"
				: "Pick two different readers"));
			return 0;
		}
		justfatlard.learning_blocks.classroom.Schedules.setTogether(who.getKey(), who.getValue(), other.getKey(), other.getValue());
		context.getSource().sendSuccess(() -> Component.literal(who.getValue() + " and " + other.getValue() + " read together at daily reading time"), true);
		return 1;
	}

	/** Show, set or change a daily schedule. */
	private static int daily(CommandContext<CommandSourceStack> context, @Nullable String weekday, @Nullable Integer lessons) {
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "reader");
		var who = reader(context.getSource(), name);
		if (who == null) {
			context.getSource().sendFailure(Component.literal("I don't know a player called " + name + " yet; have them join once"));
			return 0;
		}
		java.util.UUID id = who.getKey();
		String shown = who.getValue();
		if (weekday != null) {
			java.time.DayOfWeek day = justfatlard.learning_blocks.classroom.Schedules.weekday(weekday);
			if (day == null) {
				context.getSource().sendFailure(Component.literal(weekday + " is not a day of the week"));
				return 0;
			}
			justfatlard.learning_blocks.classroom.Schedules.setWeekday(id, shown, day, lessons);
		} else if (lessons != null) {
			justfatlard.learning_blocks.classroom.Schedules.set(id, shown, lessons);
		}
		var entry = justfatlard.learning_blocks.classroom.Schedules.of(id);
		ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(id);
		StringBuilder text = new StringBuilder(shown + ": ");
		if (entry == null || !entry.enabled()) text.append("no daily reading");
		else {
			text.append(entry.lessons()).append(" lessons a day");
			for (var override : entry.weekdays().entrySet()) {
				text.append(", ").append(override.getKey().name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(override.getValue());
			}
			int today = justfatlard.learning_blocks.classroom.Schedules.required(id, Progress.date());
			if (online != null) text.append(". Today: ").append(Progress.of(online).doneToday()).append(" of ").append(today).append(" done");
			else text.append(". Today asks for ").append(today);
		}
		context.getSource().sendSuccess(() -> Component.literal(text.toString()), lessons != null);
		return 1;
	}

	/** Which subjects a child has on: shown, or set ("numbers off", "logic on", "reading default"). */
	private static int subjects(CommandContext<CommandSourceStack> context, @Nullable String settings) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		Progress progress = Progress.of(player);
		if (settings != null) {
			String[] words = settings.trim().split("\\s+");
			if (words.length % 2 != 0) {
				context.getSource().sendFailure(Component.literal("Say each subject and on, off or default: reading on numbers off"));
				return 0;
			}
			for (int i = 0; i < words.length; i += 2) {
				var subject = justfatlard.learning_blocks.subject.Subjects.of(words[i].toLowerCase(java.util.Locale.ROOT));
				String value = words[i + 1].toLowerCase(java.util.Locale.ROOT);
				if (subject == null || !List.of("on", "off", "default").contains(value)) {
					context.getSource().sendFailure(Component.literal("\"" + words[i] + " " + words[i + 1] + "\": subjects are "
						+ String.join(", ", justfatlard.learning_blocks.subject.Subjects.ids()) + ", each on, off or default"));
					return 0;
				}
				progress.setSubjectOn(player, subject.id(), value.equals("default") ? null : value.equals("on"));
			}
		}
		StringBuilder text = new StringBuilder(player.getGameProfile().name() + "'s subjects: ");
		List<String> parts = new java.util.ArrayList<>();
		for (var subject : justfatlard.learning_blocks.subject.Subjects.ALL) {
			Boolean own = progress.subjectOn(subject.id());
			boolean on = justfatlard.learning_blocks.subject.Subjects.on(player, subject);
			parts.add(subject.label() + " " + (on ? "on" : "off") + (own == null ? " (the server's default)" : ""));
		}
		text.append(String.join(", ", parts)).append(". Mixed: ").append(justfatlard.learning_blocks.reward.PrizeConfig.subjectMix().id());
		if (justfatlard.learning_blocks.subject.Subjects.on(player).isEmpty()) {
			text.append(". With every subject off, ").append(player.getGameProfile().name())
				.append(" still gets Reading; to pause them, /learn daily ").append(player.getGameProfile().name()).append(" off");
		}
		List<String> clash = justfatlard.learning_blocks.classroom.Schedules.askedButOff(player);
		if (!clash.isEmpty()) {
			text.append(". Their daily schedule asks for ").append(String.join(" and ", clash))
				.append(", which is off: those lessons aren't asked for until it's on again");
		}
		context.getSource().sendSuccess(() -> Component.literal(text.toString()), settings != null);
		return 1;
	}

	/** Some of each subject a day: /learn daily Sam 10 reading 5 numbers 5. */
	private static int dailySplit(CommandContext<CommandSourceStack> context) {
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "reader");
		var who = reader(context.getSource(), name);
		if (who == null) {
			context.getSource().sendFailure(Component.literal("I don't know a player called " + name + " yet; have them join once"));
			return 0;
		}
		String[] words = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "split").trim().split("\\s+");
		java.util.Map<String, Integer> split = new java.util.LinkedHashMap<>();
		try {
			if (words.length % 2 != 0) throw new IllegalArgumentException();
			for (int i = 0; i < words.length; i += 2) {
				var subject = justfatlard.learning_blocks.subject.Subjects.of(words[i].toLowerCase(java.util.Locale.ROOT));
				if (subject == null) throw new IllegalArgumentException();
				split.put(subject.id(), Math.max(0, Integer.parseInt(words[i + 1])));
			}
		} catch (IllegalArgumentException e) {
			context.getSource().sendFailure(Component.literal("Say each subject and how many: reading 5 numbers 5"));
			return 0;
		}
		int lessons = IntegerArgumentType.getInteger(context, "lessons");
		int sum = split.values().stream().mapToInt(Integer::intValue).sum();
		if (sum > justfatlard.learning_blocks.classroom.Travel.MOST_LESSONS) {
			context.getSource().sendFailure(Component.literal("At most " + justfatlard.learning_blocks.classroom.Travel.MOST_LESSONS + " lessons a day in all"));
			return 0;
		}
		justfatlard.learning_blocks.classroom.Schedules.setSubjects(who.getKey(), who.getValue(), split);
		String detail = split.entrySet().stream().map(each -> each.getValue() + " " + each.getKey()).collect(java.util.stream.Collectors.joining(" + "));
		// A subject asked for that the child has off: those lessons can't be done, so say so.
		ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(who.getKey());
		List<String> off = online == null ? List.of() : justfatlard.learning_blocks.classroom.Schedules.askedButOff(online);
		context.getSource().sendSuccess(() -> Component.literal(who.getValue() + ": " + detail + " a day"
			+ (sum != lessons ? " (" + sum + " in all, not " + lessons + ")" : "")
			+ (off.isEmpty() ? "" : ". " + String.join(" and ", off) + " is off for them, so those aren't asked for until it's on (/learn subjects)")), true);
		return 1;
	}

	/** A reader's progress, as JSON under config/learning-blocks/reports, for a grown-up to look at outside the game. */
	private static int export(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer reader = EntityArgument.getPlayer(context, "player");
		java.nio.file.Path file = justfatlard.learning_blocks.review.ProgressView.export(reader);
		if (file == null) {
			context.getSource().sendFailure(Component.literal("Could not write " + reader.getGameProfile().name() + "'s progress; see the log"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(reader.getGameProfile().name() + "'s progress is in " + file), false);
		return 1;
	}

	/** Send a child in for a number of lessons, or an ordinary visit; already in, set the lessons. */
	private static int send(CommandContext<CommandSourceStack> context, @Nullable Integer lessons) throws CommandSyntaxException {
		ServerPlayer player = playerOrOffline(context, "they can be sent when they're on; /learn daily sets lessons that wait for them");
		if (player == null) return 0;
		String refused = Travel.send(player, lessons);
		if (refused != null) {
			context.getSource().sendFailure(Component.literal(player.getGameProfile().name() + ": " + refused));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + " is in the classroom"
			+ (lessons == null ? "" : " for " + lessons + (lessons == 1 ? " lesson" : " lessons"))), true);
		return 1;
	}

	private static int lessons(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = playerOrOffline(context, "their lessons show when they're on; /learn daily shows their schedule");
		if (player == null) return 0;
		int left = Travel.lessons(player);
		int owed = Progress.of(player).lessonsOwed();
		context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + ": "
			+ (left > 0 ? left + " lessons left" : owed > 0 ? owed + " lessons owed, from when they were taken out" : "no lessons set")), false);
		return left + owed;
	}

	/**
	 * Home now, whatever keeps them in: the safe way out, forced. Their lessons for this visit go;
	 * their daily reading stays, so the reply says how to turn that off too.
	 */
	private static int home(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		String name = player.getGameProfile().name();
		int lessons = Travel.lessons(player);
		if (!Travel.forceHome(player)) {
			context.getSource().sendFailure(Component.literal(name + " isn't in the classroom"));
			return 0;
		}
		String by = context.getSource().getTextName();
		justfatlard.learning_blocks.LearningBlocks.LOGGER.info("[{}] {} sent {} home by force ({} lessons left were cleared)", justfatlard.learning_blocks.LearningBlocks.MOD_ID, by, name, lessons);
		Progress.of(player).lessonNote(player, "sent home by " + by + (lessons > 0 ? ", " + lessons + " lessons cleared" : ""));
		boolean daily = justfatlard.learning_blocks.classroom.Schedules.of(player.getUUID()) != null;
		context.getSource().sendSuccess(() -> Component.literal("Sent " + name + " home."
			+ (lessons > 0 ? " The " + lessons + (lessons == 1 ? " lesson" : " lessons") + " left on this visit are cleared." : "")
			+ (daily ? " Their daily reading isn't: if today's isn't done, they'll be sent in again at their next login (/learn daily "
				+ name + " off turns it off)." : "")
			+ " /learn release " + name + " clears any lessons still owed."), true);
		return 1;
	}

	private static int release(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = playerOrOffline(context, "nothing keeps them in while they're away; /learn daily <name> off turns off their daily reading");
		if (player == null) return 0;
		int had = Travel.lessons(player) + Progress.of(player).lessonsOwed();
		Travel.setLessons(player, 0);
		Progress.of(player).setLessonsOwed(player, 0);
		if (had > 0) Progress.of(player).lessonNote(player, had + " lessons released by an operator");
		context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + " has no lessons left; the door is open"), true);
		return 1;
	}

	private static int showStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + ": "
			+ Stages.describe(Progress.of(player)) + "; numbers " + justfatlard.learning_blocks.numbers.NumberCourse.describe(Progress.of(player))
			+ "; logic " + justfatlard.learning_blocks.logic.LogicCourse.describe(Progress.of(player))
			+ ". Stages here: 0 (letters), " + String.join(", ", Stages.labels()) + "; numbers N0a to N2b; logic L0a to L3b"), false);
		return 1;
	}

	/**
	 * Put a player at a stage, or a band of one ("1b"): the ones below count as done, and every
	 * word from it up is read again. Stage 1 starts them over. What those words paid stays paid.
	 */
	private static int setStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		String asked = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "stage");
		Progress progress = Progress.of(player);
		// Numbers' bands, N0a to N2b: the ones before mastered, it and those after not.
		var numbersBand = justfatlard.learning_blocks.numbers.NumberCourse.band(asked);
		// Logic's bands, L0a to L3b, the same way.
		var logicBand = justfatlard.learning_blocks.logic.LogicCourse.band(asked);
		if (logicBand != null) {
			justfatlard.learning_blocks.logic.LogicCourse.putAt(player, logicBand);
			context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + "'s logic is now at "
				+ justfatlard.learning_blocks.logic.LogicCourse.describe(progress)), true);
			return 1;
		}
		if (numbersBand != null) {
			justfatlard.learning_blocks.numbers.NumberCourse.putAt(player, numbersBand);
			context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + "'s numbers are now at "
				+ justfatlard.learning_blocks.numbers.NumberCourse.describe(progress)), true);
			return 1;
		}
		if (asked.equals("0")) progress.setStage(player, 0, Stages.from(0));
		else {
			Stages.Band band = Stages.parse(asked);
			if (band == null) {
				context.getSource().sendFailure(Component.literal("No stage " + asked + " here. Stages here: 0 (letters), " + String.join(", ", Stages.labels())));
				return 0;
			}
			progress.setBand(player, band);
		}
		// In the classroom now: a clean slate and the new stage's first round, straight away.
		justfatlard.learning_blocks.puzzle.Room room = justfatlard.learning_blocks.puzzle.Rooms.of(player);
		if (room != null) {
			justfatlard.learning_blocks.narration.Narrator.stop(player);
			room.stageSet(player);
		}
		context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + " is now at "
			+ Stages.describe(progress) + "; the next round is from there"), true);
		return 1;
	}

	/** Open a review screen for the player who typed it. */
	private static int review(CommandContext<CommandSourceStack> context, Review.Tab tab, @Nullable ServerPlayer subject, boolean all) {
		return withPlayer(context, player -> {
			if (!PandoricalApi.isAvailable(player)) return "This needs the Pandorical mod on your game";
			return Review.open(player, tab, subject, all) ? null : "You can't open that";
		});
	}

	/** Run something for the player who typed it; what it says went wrong is their failure line. */
	private static int withPlayer(CommandContext<CommandSourceStack> context, java.util.function.Function<ServerPlayer, String> what) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Only a player can do that"));
			return 0;
		}
		String problem = what.apply(player);
		if (problem != null) {
			// Empty: whatever went wrong has said so already.
			if (!problem.isEmpty()) context.getSource().sendFailure(Component.literal(problem));
			return 0;
		}
		return 1;
	}

	private static int enter(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Only a player can go to the classroom"));
			return 0;
		}
		if (justfatlard.learning_blocks.classroom.Places.isClassroom(player.level())) {
			justfatlard.learning_blocks.puzzle.Rooms.sayQuietly(player, "phrase/already_here");
			return 1;
		}
		String refused = Travel.enter(player);
		if (refused != null) {
			context.getSource().sendFailure(Component.literal(refused));
			return 0;
		}
		return 1;
	}

	/** Read config/learning-blocks/prizes.json again, and say what was wrong with it, if anything. */
	private static int reload(CommandContext<CommandSourceStack> context) {
		List<String> problems = PrizeConfig.load(context.getSource().getServer());
		if (problems.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("Prizes reloaded from " + PrizeConfig.file()), true);
			return 1;
		}
		context.getSource().sendSuccess(() -> Component.literal("Prizes reloaded with " + problems.size()
			+ (problems.size() == 1 ? " problem" : " problems") + ", each skipped:"), true);
		for (String problem : problems) context.getSource().sendSystemMessage(Component.literal(" - " + problem));
		return problems.size();
	}

	private static int leave(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player != null && Travel.kept(player)) {
			int left = Travel.lessons(player);
			// Said too, for a child who can't read it yet.
			if (justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null && justfatlard.learning_blocks.puzzle.Rooms.of(player) != null && !justfatlard.learning_blocks.narration.Narrator.isPlaying(player)) {
				justfatlard.learning_blocks.narration.Narrator.play(player, new justfatlard.learning_blocks.narration.Narration().say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left)));
			}
			context.getSource().sendFailure(Component.literal("Not yet: " + left + (left == 1 ? " lesson" : " lessons") + " to go"));
			return 0;
		}
		if (player == null || !Travel.leave(player)) {
			context.getSource().sendFailure(Component.literal("You're not in the classroom"));
			return 0;
		}
		return 1;
	}
}
