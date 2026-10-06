package justfatlard.learning_blocks.classroom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.Later;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.puzzle.Room;
import justfatlard.learning_blocks.puzzle.Rooms;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * "Can I learn with you?": a request to join another child's classroom, put to them as a question
 * with Accept and Deny (Pandorical's notice tray), and said aloud, since the child asked may not
 * read yet. One request at a time between two children, a short wait before the same child may
 * ask anyone again, and requests that lapse, or whose asker goes, are taken back.
 */
public final class JoinRequests {
	private JoinRequests() {}

	public static final String NOTICE_KIND = LearningBlocks.MOD_ID + ":join";
	static final long ASK_FOR = 20 * 60;
	static final long COOLDOWN = 20 * 30;

	private static final Map<UUID, Map<UUID, Long>> asked = new HashMap<>();
	private static final Map<UUID, Long> lastAsked = new HashMap<>();

	private static String noticeId(UUID guest) {
		return "join-" + guest;
	}

	/** Ask: null if it was put to them, or why not. */
	public static synchronized @Nullable String ask(ServerPlayer guest, ServerPlayer host) {
		long now = host.level().getGameTime();
		prune(now);
		Room room = Rooms.of(host);
		if (host == guest) return "That's you!";
		if (room == null) return host.getGameProfile().name() + " isn't learning just now";
		if (Rooms.of(guest) == room) return "You're already learning together";
		if (room.members().size() >= 3) return host.getGameProfile().name() + "'s classroom is full";
		Map<UUID, Long> theirs = asked.computeIfAbsent(host.getUUID(), id -> new HashMap<>());
		if (theirs.containsKey(guest.getUUID())) return "You've asked " + host.getGameProfile().name() + "; waiting for an answer";
		Long last = lastAsked.get(guest.getUUID());
		if (last != null && now - last < COOLDOWN) return "Wait a little before asking again";
		theirs.put(guest.getUUID(), now + ASK_FOR);
		lastAsked.put(guest.getUUID(), now);
		String who = guest.getGameProfile().name();
		if (PandoricalApi.isAvailable(host)) {
			PandoricalApi.notices().offer(host, new NoticeApi.Notice(noticeId(guest.getUUID()), NOTICE_KIND, "minecraft:book",
				who + " would like to learn with you",
				List.of(new NoticeApi.Choice("accept", "minecraft:lime_dye", "Accept"), new NoticeApi.Choice("deny", "minecraft:barrier", "Deny")),
				(int) (ASK_FOR / 20)));
		} else {
			// No tray on their game: the same question in chat, the asker by id, which a name can't spoof.
			host.sendSystemMessage(Component.literal(who + " would like to learn with you. ")
				.append(Component.literal("[Accept]").withStyle(style -> style.withColor(0x2FB344).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/learn accept " + guest.getUUID()))))
				.append(Component.literal(" "))
				.append(Component.literal("[Deny]").withStyle(style -> style.withColor(0xC0392B).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/learn decline " + guest.getUUID())))));
		}
		say(host, "phrase/wants_to_learn_with_you");
		return null;
	}

	/** The one asked answers: null if done, or why not. */
	public static synchronized @Nullable String answer(ServerPlayer host, UUID guestId, boolean yes) {
		MinecraftServer server = host.level().getServer();
		Map<UUID, Long> theirs = asked.get(host.getUUID());
		Long until = theirs == null ? null : theirs.remove(guestId);
		if (PandoricalApi.isAvailable(host)) PandoricalApi.notices().withdraw(host, NOTICE_KIND, noticeId(guestId));
		ServerPlayer guest = server.getPlayerList().getPlayer(guestId);
		if (until == null || host.level().getGameTime() > until || guest == null) return "Nobody is asking just now";
		if (!yes) {
			guest.sendSystemMessage(Component.literal(host.getGameProfile().name() + " said not just now"));
			return null;
		}
		Room room = Rooms.of(host);
		String refused = room == null ? "you're not learning just now" : Travel.readWith(guest, room);
		if (refused != null) return refused;
		guest.sendSystemMessage(Component.literal("Learning with " + host.getGameProfile().name() + "!"));
		// Said to both once the guest is standing in the room to hear it.
		Later.after(guest, 40, p -> {
			say(p, "phrase/friend_joined");
			ServerPlayer h = p.level().getServer().getPlayerList().getPlayer(host.getUUID());
			if (h != null) say(h, "phrase/friend_joined");
		});
		return null;
	}

	/** Said only when nothing else is being said: a request never cuts across a round. */
	private static void say(ServerPlayer player, String clip) {
		Room room = Rooms.of(player);
		if (room != null && room.busy() || Narrator.isPlaying(player)) return;
		Narrator.play(player, new Narration().say(clip));
	}

	public static void listen() {
		PandoricalApi.notices().onChoice(NOTICE_KIND, (host, noticeId, choiceId) -> {
			UUID guest = guestOf(noticeId);
			if (guest == null) return;
			String refused = answer(host, guest, "accept".equals(choiceId));
			if (refused != null) host.sendSystemMessage(Component.literal(refused));
		});
		PandoricalApi.notices().onExpiry(NOTICE_KIND, (host, noticeId) -> {
			UUID guest = guestOf(noticeId);
			synchronized (JoinRequests.class) {
				Map<UUID, Long> theirs = asked.get(host.getUUID());
				if (theirs != null && guest != null) theirs.remove(guest);
			}
		});
	}

	private static @Nullable UUID guestOf(String noticeId) {
		try {
			return noticeId.startsWith("join-") ? UUID.fromString(noticeId.substring(5)) : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void prune(long now) {
		for (Map<UUID, Long> theirs : asked.values()) theirs.values().removeIf(until -> until < now);
		asked.values().removeIf(Map::isEmpty);
		lastAsked.values().removeIf(at -> now - at > COOLDOWN);
	}

	/** Logged out: their requests, asked and asking, gone, and the question taken back from whoever was asked. */
	public static synchronized void forget(MinecraftServer server, UUID player) {
		asked.remove(player);
		lastAsked.remove(player);
		for (Map.Entry<UUID, Map<UUID, Long>> entry : asked.entrySet()) {
			if (entry.getValue().remove(player) == null) continue;
			ServerPlayer host = server.getPlayerList().getPlayer(entry.getKey());
			if (host != null && PandoricalApi.isAvailable(host)) PandoricalApi.notices().withdraw(host, NOTICE_KIND, noticeId(player));
		}
	}

	public static synchronized boolean waiting(UUID host, UUID guest) {
		Map<UUID, Long> theirs = asked.get(host);
		return theirs != null && theirs.containsKey(guest);
	}

	public static synchronized void clear() {
		asked.clear();
		lastAsked.clear();
	}
}
