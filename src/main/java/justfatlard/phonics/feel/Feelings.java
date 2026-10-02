package justfatlard.phonics.feel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.mojang.math.Transformation;
import justfatlard.phonics.Phonics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Feelings, shown: a little speech bubble with a face in it, over a creature, a character, a
 * plushie or Bob. It pops up, rides along over whoever is feeling it, and shrinks away when its
 * time is up or they are gone. One at a time over each: a new feeling replaces the last.
 *
 * <p>A bubble is an item display of paper with its model swapped for {@code emote/<face>} (drawn by
 * {@code generate_emotes.py}), so there is no item to register. Nothing is left behind: every
 * bubble is tracked here, and one met in the world that is not (saved with a chunk when the server
 * stopped) is taken away as it loads.
 */
public final class Feelings {
	private Feelings() {}

	public enum Face {
		GLAD, SAD, MAD, SLEEPY, SURPRISED, SCARED, LOVE, COLD, HOT;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Face named(String name) {
			for (Face face : values()) if (face.id().equals(name)) return face;
			return null;
		}
	}

	static final String TAG = Phonics.MOD_ID + ".emote";
	/** How big a bubble is, a block across at 1. */
	private static final float SIZE = 0.7F;
	private static final int POP = 4;

	private record Shown(Display.ItemDisplay display, @Nullable Entity over, String face, long started, long until) {}

	private static final Map<UUID, Shown> shown = new HashMap<>();

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Feelings::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !shown.containsKey(entity.getUUID())) entity.discard();
		});
	}

	/** Paper drawn as one of the emote pictures: a feeling, or the waving hand. */
	public static ItemStack picture(String name) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Phonics.id("emote/" + name));
		return stack;
	}

	/** A feeling over this creature or character, for {@code lasts} ticks (0 for as long as it is there). */
	public static Display.@Nullable ItemDisplay show(Entity over, Face face, int lasts) {
		clear(over);
		if (!(over.level() instanceof ServerLevel level)) return null;
		Display.ItemDisplay bubble = make(level, over.position().add(0, over.getBbHeight(), 0), face);
		if (bubble == null) return null;
		track(bubble, over, face, lasts);
		level.addFreshEntity(bubble);
		bubble.startRiding(over, true, false);
		return bubble;
	}

	/** A feeling over a place, a plushie's: {@code at} is the top of it. */
	public static Display.@Nullable ItemDisplay showAt(ServerLevel level, Vec3 at, Face face, int lasts) {
		Display.ItemDisplay bubble = make(level, at, face);
		if (bubble == null) return null;
		track(bubble, null, face, lasts);
		level.addFreshEntity(bubble);
		return bubble;
	}

	private static Display.@Nullable ItemDisplay make(ServerLevel level, Vec3 at, Face face) {
		Display.ItemDisplay bubble = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (bubble == null) return null;
		bubble.snapTo(at.x, at.y, at.z, 0, 0);
		bubble.setItemStack(picture(face.id()));
		bubble.setItemTransform(ItemDisplayContext.GUI);
		bubble.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		bubble.setTransformation(pose(0.05F));
		bubble.addTag(TAG);
		// Added by the caller once it is tracked: one met in the world untracked is a leftover, and goes.
		return bubble;
	}

	private static void track(Display.ItemDisplay bubble, @Nullable Entity over, Face face, int lasts) {
		long now = bubble.level().getGameTime();
		shown.put(bubble.getUUID(), new Shown(bubble, over, face.id(), now, lasts > 0 ? now + lasts : Long.MAX_VALUE));
	}

	/** Up off the top of its owner a little, at this size. */
	private static Transformation pose(float scale) {
		return new Transformation(new Vector3f(0, 0.45F, 0), null, new Vector3f(scale * SIZE), null);
	}

	/** Whatever this creature is feeling, gone. */
	public static void clear(Entity over) {
		for (Iterator<Shown> it = shown.values().iterator(); it.hasNext(); ) {
			Shown one = it.next();
			if (one.over() != null && one.over().getUUID().equals(over.getUUID())) {
				one.display().discard();
				it.remove();
			}
		}
	}

	/** What this creature is feeling, or null. For tests, and for a scene to know. */
	public static @Nullable Face of(Entity over) {
		for (Shown one : shown.values()) {
			if (one.over() != null && one.over().getUUID().equals(over.getUUID()) && one.display().isAlive()) return Face.named(one.face());
		}
		return null;
	}

	/** Every bubble showing, in any room. For tests. */
	public static int count() {
		return (int) shown.values().stream().filter(one -> one.display().isAlive()).count();
	}

	private static void tick(MinecraftServer server) {
		List<UUID> gone = new ArrayList<>();
		for (Map.Entry<UUID, Shown> entry : shown.entrySet()) {
			Shown one = entry.getValue();
			Display.ItemDisplay bubble = one.display();
			long now = bubble.level().getGameTime();
			if (bubble.isRemoved() || one.over() != null && (one.over().isRemoved() || !bubble.isPassenger())) {
				bubble.discard();
				gone.add(entry.getKey());
				continue;
			}
			if (now - one.started() == 1) grow(bubble, 1.0F, POP);
			if (now == one.until()) grow(bubble, 0.05F, 3);
			if (one.until() != Long.MAX_VALUE && now >= one.until() + 3) {
				bubble.discard();
				gone.add(entry.getKey());
			}
		}
		gone.forEach(shown::remove);
	}

	private static void grow(Display.ItemDisplay bubble, float scale, int ticks) {
		bubble.setTransformationInterpolationDelay(0);
		bubble.setTransformationInterpolationDuration(ticks);
		bubble.setTransformation(pose(scale));
	}

	private static void clearAll() {
		for (Shown one : shown.values()) one.display().discard();
		shown.clear();
	}
}
