package justfatlard.learning_blocks.relate;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.learning_blocks.LearningBlocks;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where one thing goes relative to another: the one engine behind Bob's literal sentences, his
 * goals, the preposition rounds, and a child's toy play in the world.
 *
 * <p>Things and their affordances are data ({@code learning_relations/<word>.json}, written by
 * {@code generate_relations.py}). {@link #resolve} reads a sentence's "put X in/on/under Y" as
 * literally as Bob does: in something that holds things, it goes in (onto its lid if the lid is
 * on); in or on a creature, onto its head, or riding it if it is a creature too; in or on a
 * vehicle, riding it; a wearable on a wearer is worn; under something with no room under it, that
 * thing ends up on top of this one. {@link #fit} is the gentler world version: whether a child
 * holding one thing can put it in, on or onto another, and where it sits.
 */
public final class Relations implements SimpleSynchronousResourceReloadListener {

	public enum Rel { IN, ON, UNDER, RIDES, HEAD }

	public record Placement(String child, Rel rel, String parent) {}

	private static final String DIRECTORY = "learning_relations";
	private static volatile Map<String, Thing> things = Map.of();

	public static @Nullable Thing thing(String word) {
		return things.get(word);
	}

	public static List<Thing> all() {
		return List.copyOf(things.values());
	}

	public static @Nullable Thing forItem(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		for (Thing thing : things.values()) if (id.equals(thing.item()) && thing.is() != Thing.Is.CREATURE) return canonical(thing);
		return null;
	}

	public static @Nullable Thing forBlock(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		for (Thing thing : things.values()) {
			if (!id.equals(thing.block())) continue;
			boolean matches = true;
			for (Map.Entry<String, String> property : thing.properties().entrySet()) {
				var found = state.getProperties().stream().filter(p -> p.getName().equals(property.getKey())).findFirst();
				if (found.isEmpty() || !valueName(state, found.get()).equals(property.getValue())) matches = false;
			}
			if (matches) return canonical(thing);
		}
		return null;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/** The ladybug and the bug are one thing: the one named for itself. */
	private static Thing canonical(Thing thing) {
		if (thing.item() == null) return thing;
		for (Thing other : things.values()) {
			if (thing.item().equals(other.item()) && other.item().getPath().endsWith("_" + other.word())) return other;
		}
		return thing;
	}

	/**
	 * "Put x {prep} y", exactly as Bob reads it. {@code lidOn} says which lid, if any, is on a
	 * container now. Null when nothing can be done: a thing into itself, or a word that is no thing.
	 */
	public static @Nullable Placement resolve(String x, String prep, String y, Function<String, @Nullable String> lidOn) {
		Thing tx = thing(x);
		Thing ty = thing(y);
		if (tx == null || ty == null || x.equals(y)) return null;
		switch (prep) {
			case "in" -> {
				if (ty.holds(Thing.Hold.Way.IN)) {
					String lid = lidOn.apply(y);
					// The lid is on: it goes on the lid, which is as far in as it can go.
					if (lid != null && !lid.equals(x)) return new Placement(x, Rel.ON, lid);
					return new Placement(x, Rel.IN, y);
				}
				if (ty.creature()) return new Placement(x, tx.creature() ? Rel.RIDES : Rel.HEAD, y);
				if (ty.holds(Thing.Hold.Way.RIDE)) return new Placement(x, Rel.RIDES, y);
				if (ty.wearable()) return new Placement(x, Rel.IN, y);
				return new Placement(x, Rel.ON, y);
			}
			case "on" -> {
				if (ty.creature()) return new Placement(x, tx.creature() ? Rel.RIDES : Rel.HEAD, y);
				if (tx.wearable() && ty.wearer()) return new Placement(x, Rel.HEAD, y);
				if (ty.holds(Thing.Hold.Way.RIDE)) return new Placement(x, Rel.RIDES, y);
				return new Placement(x, Rel.ON, y);
			}
			case "under" -> {
				if (ty.holds(Thing.Hold.Way.UNDER)) return new Placement(x, Rel.UNDER, y);
				// Under something with no room under it: that thing ends up on top of this one.
				if (tx.creature()) return new Placement(y, ty.creature() ? Rel.RIDES : Rel.HEAD, x);
				if (ty.wearable() && tx.wearer()) return new Placement(y, Rel.HEAD, x);
				if (tx.holds(Thing.Hold.Way.RIDE)) return new Placement(y, Rel.RIDES, x);
				return new Placement(y, Rel.ON, x);
			}
			default -> {
				return null;
			}
		}
	}

	/** In the world: what a child holding {@code held} does to {@code target} by using it on it, if anything. */
	public record Fit(Rel rel, Thing.@Nullable Hold hold) {}

	public static @Nullable Fit fit(Thing held, Thing target) {
		if (held.word().equals(target.word()) && held.is() == target.is()) return null;
		if (held.wearable() && target.wearer()) return new Fit(Rel.HEAD, null);
		for (Thing.Hold.Way way : List.of(Thing.Hold.Way.RIDE, Thing.Hold.Way.IN, Thing.Hold.Way.ON)) {
			Thing.Hold hold = target.hold(way);
			if (hold != null && hold.accepts(held)) {
				return new Fit(way == Thing.Hold.Way.RIDE ? Rel.RIDES : way == Thing.Hold.Way.IN ? Rel.IN : Rel.ON, hold);
			}
		}
		return null;
	}

	public static Thing.@Nullable Hold holdFor(Thing parent, Rel rel) {
		return switch (rel) {
			case IN -> parent.hold(Thing.Hold.Way.IN);
			case ON -> parent.hold(Thing.Hold.Way.ON);
			case UNDER -> parent.hold(Thing.Hold.Way.UNDER);
			case RIDES -> parent.hold(Thing.Hold.Way.RIDE);
			case HEAD -> null;
		};
	}


	@Override
	public Identifier getFabricId() {
		return LearningBlocks.id(DIRECTORY);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, Thing> loaded = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> entry : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			String path = entry.getKey().getPath();
			String word = path.substring(DIRECTORY.length() + 1, path.length() - ".json".length());
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				loaded.put(word, read(word, JsonParser.parseReader(reader).getAsJsonObject()));
			} catch (Exception e) {
				LearningBlocks.LOGGER.warn("[{}] Could not read thing {}", LearningBlocks.MOD_ID, entry.getKey(), e);
			}
		}
		things = java.util.Collections.unmodifiableMap(loaded);
		LearningBlocks.LOGGER.info("[{}] Loaded {} things that go in, on and under each other", LearningBlocks.MOD_ID, loaded.size());
		listeners.forEach(Runnable::run);
	}

	private static final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Run after every reload: whatever is made from the things (Bob's goals) made again. */
	public static void onReload(Runnable listener) {
		listeners.add(listener);
	}

	private static Thing read(String word, JsonObject json) {
		Thing.Is is = Thing.Is.valueOf(json.get("is").getAsString().toUpperCase(Locale.ROOT));
		Map<String, String> properties = new LinkedHashMap<>();
		if (json.has("properties")) for (var e : json.getAsJsonObject("properties").entrySet()) properties.put(e.getKey(), e.getValue().getAsString());
		LinkedHashSet<String> tags = new LinkedHashSet<>();
		if (json.has("tags")) for (JsonElement tag : json.getAsJsonArray("tags")) tags.add(tag.getAsString());
		Map<Thing.Hold.Way, Thing.Hold> holds = new EnumMap<>(Thing.Hold.Way.class);
		if (json.has("holds")) {
			for (var e : json.getAsJsonObject("holds").entrySet()) {
				Thing.Hold.Way way = Thing.Hold.Way.valueOf(e.getKey().toUpperCase(Locale.ROOT));
				JsonObject h = e.getValue().getAsJsonObject();
				List<String> accepts = new ArrayList<>();
				if (h.has("accepts")) for (JsonElement tag : h.getAsJsonArray("accepts")) accepts.add(tag.getAsString());
				holds.put(way, new Thing.Hold(way, List.copyOf(accepts), number(h, "y", 0), number(h, "z", 0), number(h, "scale", 0.6F),
					h.has("sound") ? Optional.of(Identifier.parse(h.get("sound").getAsString())) : Optional.empty(),
					h.has("float") && h.get("float").getAsBoolean()));
			}
		}
		return new Thing(word, is, id(json, "entity"), id(json, "block"), Map.copyOf(properties), id(json, "item"), number(json, "height", 0.5F),
			java.util.Collections.unmodifiableSet(tags), json.has("wearable") && json.get("wearable").getAsBoolean(),
			json.has("lid") && json.get("lid").getAsBoolean(), java.util.Collections.unmodifiableMap(holds));
	}

	private static @Nullable Identifier id(JsonObject json, String key) {
		return json.has(key) ? Identifier.parse(json.get(key).getAsString()) : null;
	}

	private static float number(JsonObject json, String key, float fallback) {
		return json.has(key) ? json.get(key).getAsFloat() : fallback;
	}
}
