package justfatlard.learning_blocks.puzzle;

import java.util.HashMap;
import java.util.Map;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Hitting a letter makes its sound.
 *
 * <p>Heard through the left-click punch packet, which a client sends with every swing in every game
 * mode, rather than the attack-block event, which an adventure-mode client with an empty hand never
 * sends. A punch looks where the player is looking, and if that is a letter, it speaks.
 *
 * <p>Two rhythms. A tile that breaks speaks once, on the click that starts the break: holding the
 * button sends a punch every tick, and those are the same knock. The break itself stays quiet when
 * the click spoke a moment before, so a quick break is one sound, not two. A letter that cannot be
 * broken - the board's - speaks again every {@link #COOLDOWN} ticks while held, so holding it
 * repeats the sound at a steady pace rather than machine-gunning it.
 *
 * <p>A letter in the word row speaks the way the word says it; a loose tile its own sound.
 */
public final class Punches {
	private Punches() {}

	private static final long COOLDOWN = 6;
	/** A break this soon after the click that started it is the same knock. */
	private static final long SAME_KNOCK = 12;

	private record Heard(long punched, long said) {}

	private static final Map<BlockPos, Heard> heard = new HashMap<>();

	public static void clear() {
		heard.clear();
	}

	public static void punched(ServerPlayer player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) return;
		BlockPos pos = blockHit.getBlockPos().immutable();
		Room pointing = Rooms.of(player);
		if (pointing != null && pointing.shared() && !pointing.mayPress(player)) pointing.point(player, blockHit.getLocation());
		// A punch on a block a round's choice is made of picks it, as a right-click does.
		if (pointing != null && pointing.subjectRound != null && pointing.subjectRound.clickBlock(player, pos, blockHit.getLocation())) return;
		BlockState state = player.level().getBlockState(pos);
		if (!(state.getBlock() instanceof LetterBlock)) return;
		String sound = soundAt(player, pos, state);
		if (sound == null) return;

		long now = player.level().getGameTime();
		Heard last = heard.get(pos);
		boolean breakable = !(state.getBlock() instanceof BoardLetterBlock);
		boolean fresh = last == null || now - last.punched() > 2 || now < last.punched();
		boolean speak = breakable ? fresh : last == null || now - last.said() >= COOLDOWN || now < last.said();
		if (heard.size() > 256) heard.clear();
		heard.put(pos, new Heard(now, speak ? now : last.said()));
		if (speak) Sounds.playAt(player.level(), pos, sound);
	}

	/** Whether this position spoke so recently that breaking it now would be saying it twice. */
	static boolean saidLately(BlockPos pos, long now) {
		Heard last = heard.get(pos);
		return last != null && now >= last.said() && now - last.said() < SAME_KNOCK;
	}

	private static @Nullable String soundAt(ServerPlayer player, BlockPos pos, BlockState state) {
		Room room = Rooms.of(player);
		if (room != null) {
			String inRow = room.rowSound(pos);
			if (inRow != null) return inRow.isEmpty() ? null : inRow;
		}
		return LetterBlock.phoneme(state);
	}
}
