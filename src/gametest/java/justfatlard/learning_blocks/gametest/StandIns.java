package justfatlard.learning_blocks.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import justfatlard.learning_blocks.classroom.Visit;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;

/** Stand-in readers for tests that need more than one: fabric FakePlayers put on the player list, as a joined player is. */
final class StandIns {
	private StandIns() {}

	/** A stand-in in the classroom beside this spot, on the player list, with a visit of her own. */
	static FakePlayer inClassroom(ServerLevel classroom, String name, Vec3 near) {
		FakePlayer fake = FakePlayer.get(classroom, new GameProfile(UUID.nameUUIDFromBytes(("stand-in:" + name).getBytes()), name));
		fake.snapTo(near.x, near.y, near.z, 180, 0);
		online(classroom.getServer(), fake, true);
		Visit.set(fake, Visit.snapshot(fake));
		return fake;
	}

	/** On (or off) the server's player list: commands and lookups find them. */
	@SuppressWarnings("unchecked")
	static void online(MinecraftServer server, ServerPlayer player, boolean on) {
		try {
			PlayerList list = server.getPlayerList();
			Field players = PlayerList.class.getDeclaredField("players");
			Field byId = PlayerList.class.getDeclaredField("playersByUUID");
			players.setAccessible(true);
			byId.setAccessible(true);
			List<ServerPlayer> all = (List<ServerPlayer>) players.get(list);
			Map<UUID, ServerPlayer> ids = (Map<UUID, ServerPlayer>) byId.get(list);
			if (on) {
				all.add(player);
				ids.put(player.getUUID(), player);
			} else {
				all.remove(player);
				ids.remove(player.getUUID());
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not put the stand-in on the player list", e);
		}
	}
}
