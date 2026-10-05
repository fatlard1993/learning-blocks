package justfatlard.learning_blocks.puzzle;

import com.mojang.math.Transformation;
import justfatlard.learning_blocks.feel.Gestures;
import justfatlard.learning_blocks.relate.Relations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** What every Bob round shares: Bob himself, the box he puts things in, and the frame round a goal. */
final class BobProps {
	private BobProps() {}

	/** The ink and the card of a word card. */
	static final int INK = 0x1E2A5A;
	static final int CARD = 0xFFF2E8C9;

	/**
	 * Bob, standing here facing this way, waving hello: a villager who does nothing of his own, can't
	 * be hurt, and picks nothing up (a prize thrown near him stays a prize). Not yet added: the caller
	 * tags and adds him.
	 */
	static @Nullable Villager bob(ServerLevel level, Vec3 at, float yaw) {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) return null;
		villager.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		villager.setYHeadRot(yaw);
		villager.setYBodyRot(yaw);
		villager.setNoAi(true);
		villager.setSilent(true);
		villager.setPermanentlyInvulnerable(true);
		villager.setPersistenceRequired();
		villager.setCanPickUpLoot(false);
		villager.setCustomName(Component.literal("Bob"));
		villager.setCustomNameVisible(true);
		return villager;
	}

	/** Once he is in the world: hello. */
	static void wave(Villager bob) {
		Gestures.run(bob, Gestures.Gesture.WAVE);
	}

	/** The box Bob puts things in: the prop, or a barrel if the relations have none. */
	static BlockState box() {
		var thing = Relations.thing("box");
		BlockState state = thing == null ? null : BobStage.blockOf(thing);
		return state == null ? Blocks.BARREL.defaultBlockState() : state;
	}

	/** A white frame on the board's left, for a goal shown small in front of it; not yet added. */
	static Display.@Nullable BlockDisplay goalFrame(ServerLevel level, Vec3 at) {
		Display.BlockDisplay frame = Counting.block(level, Blocks.CONCRETE.white().defaultBlockState(), 1.0F);
		if (frame == null) return null;
		frame.setTransformation(new Transformation(new Vector3f(-0.9F, -0.1F, -0.3F), null, new Vector3f(1.8F, 1.6F, 0.05F), null));
		frame.snapTo(at.x, at.y, at.z, 0, 0);
		return frame;
	}
}
