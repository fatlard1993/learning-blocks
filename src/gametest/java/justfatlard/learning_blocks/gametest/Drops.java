package justfatlard.learning_blocks.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Exactly what breaking a block drops, as a survival child breaks it: every item that turned up
 * around it, by id and count, and nothing else. The drops are taken away after counting.
 */
final class Drops {
	private Drops() {}

	/** Break it the way a child's dig does and count what came out. */
	static Map<String, Integer> breakAndCount(ServerPlayer player, BlockPos pos) {
		ServerLevel level = (ServerLevel) player.level();
		AABB around = new AABB(pos).inflate(2.5);
		Set<UUID> before = new HashSet<>();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around)) before.add(item.getUUID());
		if (!player.gameMode.destroyBlock(pos)) throw new AssertionError(level.getBlockState(pos) + " at " + pos + " would not break");
		Map<String, Integer> got = new TreeMap<>();
		List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, around, item -> !before.contains(item.getUUID()));
		for (ItemEntity item : dropped) {
			got.merge(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath(), item.getItem().getCount(), Integer::sum);
			item.discard();
		}
		return got;
	}

	/** Break it and check it dropped exactly this, and nothing else. */
	static void expect(ServerPlayer player, BlockPos pos, Map<String, Integer> want, String what) {
		Map<String, Integer> got = breakAndCount(player, pos);
		if (!got.equals(new TreeMap<>(want))) throw new AssertionError(what + " dropped " + got + ", not exactly " + want);
	}
}
