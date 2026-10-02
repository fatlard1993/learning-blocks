package justfatlard.phonics.puzzle;

import java.util.List;
import justfatlard.phonics.reward.Progress;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two-word phrases: an adjective the child has read and a creature word, "big pig", "six cod". The
 * phrase happens as one thing: the creature, made big, or red, or wet, or brought six times, or
 * brought to a party, or feeling sad, glad or mad (a face in a bubble over it). A pair that
 * cannot be shown is never offered.
 */
public final class Phrases {
	private Phrases() {}

	/** The adjectives a phrase can start with, all words a child reads anyway. */
	public static final List<String> ADJECTIVES = List.of("big", "red", "wet", "fun", "six", "sad", "glad", "mad");

	/** The creature a word brings, if that is what it does. */
	static Outcome.@Nullable SpawnEntity creature(Word noun) {
		if (noun.outcome() instanceof Outcome.SpawnEntity spawn) return spawn;
		if (noun.outcome() instanceof Outcome.Sequence sequence) {
			for (Outcome step : sequence.steps()) if (step instanceof Outcome.SpawnEntity spawn) return spawn;
		}
		return null;
	}

	/** Whether this adjective and this noun make a phrase the room can show. */
	public static boolean fits(Word adjective, Word noun) {
		Outcome.SpawnEntity spawn = creature(noun);
		return ADJECTIVES.contains(adjective.text()) && spawn != null && spawn.usable();
	}

	/** The phrase happens, in this room: the creature, as the adjective says. */
	static void run(ServerPlayer player, Word adjective, Word noun, Room room) {
		Outcome.SpawnEntity spawn = creature(noun);
		if (spawn == null) {
			noun.outcome().run(player, room.dispenser);
			return;
		}
		int count = adjective.text().equals("six") ? 6 : 1;
		for (int i = 0; i < count; i++) {
			room.later(player, i * 4, p -> {
				spawn.run(p, room.dispenser);
				List<Entity> made = room.dispenser.creatures();
				if (!made.isEmpty()) change(p, adjective.text(), made.getLast(), room);
			});
		}
		justfatlard.phonics.narration.Narrator.note(player, "phrase:" + adjective.text() + "_" + noun.text());
	}


	/** One creature, as the adjective makes it. */
	private static void change(ServerPlayer player, String adjective, Entity creature, Room room) {
		switch (adjective) {
			case "big" -> {
				if (creature instanceof LivingEntity living && living.getAttribute(Attributes.SCALE) != null) {
					living.getAttribute(Attributes.SCALE).setBaseValue(1.8);
				}
			}
			case "red" -> {
				if (creature instanceof Sheep sheep) sheep.setColor(DyeColor.RED);
				for (int i = 0; i < 40; i += 8) room.later(player, i, p -> puff(creature, new DustParticleOptions(0xE02020, 1.4F)));
			}
			case "wet" -> {
				for (int i = 0; i < 60; i += 4) room.later(player, i, p -> puff(creature, ParticleTypes.FALLING_WATER));
				Vec3 at = creature.position();
				creature.level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.3F);
			}
			case "fun" -> {
				for (int i = 10; i < 50; i += 10) room.later(player, i, p -> {
					creature.setDeltaMovement(creature.getDeltaMovement().add(0, 0.4, 0));
					creature.needsSync = true;
					puff(creature, ParticleTypes.FIREWORK);
				});
			}
			case "sad" -> {
				justfatlard.phonics.feel.Feelings.show(creature, justfatlard.phonics.feel.Feelings.Face.SAD, 120);
				for (int i = 0; i < 60; i += 6) room.later(player, i, p -> puff(creature, ParticleTypes.FALLING_WATER));
			}
			case "glad" -> {
				justfatlard.phonics.feel.Feelings.show(creature, justfatlard.phonics.feel.Feelings.Face.GLAD, 120);
				for (int i = 10; i < 40; i += 10) room.later(player, i, p -> {
					creature.setDeltaMovement(creature.getDeltaMovement().add(0, 0.35, 0));
					creature.needsSync = true;
					puff(creature, ParticleTypes.HAPPY_VILLAGER);
				});
			}
			case "mad" -> {
				justfatlard.phonics.feel.Feelings.show(creature, justfatlard.phonics.feel.Feelings.Face.MAD, 120);
				for (int i = 6; i < 50; i += 12) room.later(player, i, p -> {
					creature.setDeltaMovement(creature.getDeltaMovement().add(0, 0.2, 0));
					creature.needsSync = true;
					puff(creature, ParticleTypes.ANGRY_VILLAGER);
				});
			}
			default -> { }
		}
	}

	private static void puff(Entity creature, net.minecraft.core.particles.ParticleOptions particle) {
		if (!creature.isAlive() || !(creature.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
		Vec3 at = creature.position().add(0, creature.getBbHeight() + 0.2, 0);
		level.sendParticles(particle, at.x, at.y, at.z, 6, creature.getBbWidth() * 0.4, 0.1, creature.getBbWidth() * 0.4, 0.02);
	}

	/** A phrase for a round, from the adjectives and creature words this child has read, or null. */
	static Word @Nullable [] pick(Progress progress, net.minecraft.util.RandomSource random) {
		List<Word> adjectives = new java.util.ArrayList<>();
		List<Word> nouns = new java.util.ArrayList<>();
		for (Word word : Stages.usable()) {
			if (!progress.words().contains(word.id().toString())) continue;
			if (ADJECTIVES.contains(word.text())) adjectives.add(word);
			else if (creature(word) != null && creature(word).usable()) {
				Puzzle family = Puzzle.forTarget(word, Words.all(), random, Puzzle.Kind.FIRST);
				if (family != null && family.shelf().size() >= 2) nouns.add(word);
			}
		}
		if (adjectives.isEmpty() || nouns.isEmpty()) return null;
		return new Word[] {adjectives.get(random.nextInt(adjectives.size())), nouns.get(random.nextInt(nouns.size()))};
	}
}
