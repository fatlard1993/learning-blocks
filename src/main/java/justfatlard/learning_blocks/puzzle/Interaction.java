package justfatlard.learning_blocks.puzzle;

import java.util.UUID;

import java.util.ArrayList;
import java.util.List;
import justfatlard.learning_blocks.LearningBlocks;
import justfatlard.learning_blocks.classroom.Travel;
import justfatlard.learning_blocks.narration.Narration;
import justfatlard.learning_blocks.narration.Narrator;
import justfatlard.learning_blocks.sound.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A child's hands in the room: what a right-click does to the portal, the chest, the button and the
 * tiles; tiles into and out of the gaps, and home to their places on the floor; and a round kept
 * finishable when a tile goes missing.
 */
final class Interaction {
	private static final int FLAGS = RoomLayout.FLAGS;
	private static final int HALF = RoomLayout.HALF;

	private final Room room;
	private final ServerLevel level;
	private final RoomLayout layout;

	Interaction(Room room, ServerLevel level, RoomLayout layout) {
		this.room = room;
		this.level = level;
		this.layout = layout;
	}

	/**
	 * A right-click on a block in this room, or null where the block is none of the room's business.
	 * Tiles are not picked up or put down here: they are broken and placed the ordinary way, and
	 * {@link Room#mayBreak} and {@link Room#aimedGap} say where. A letter right-clicked with nothing in
	 * hand says its sound.
	 */
	@Nullable InteractionResult use(ServerPlayer player, BlockPos pos, InteractionHand hand) {
		if (layout.isPortal(pos)) {
			// Walking through is the way home (Portal.tick); a click on it works too.
			if (Travel.kept(player)) {
				int left = Travel.lessons(player);
				if (hand == InteractionHand.MAIN_HAND && !room.busy && !room.leaving && justfatlard.learning_blocks.classroom.Travel.moreToGo(left) != null) {
					Narrator.play(player, new Narration().say(justfatlard.learning_blocks.classroom.Travel.moreToGo(left)));
				}
				return InteractionResult.SUCCESS;
			}
			if (hand == InteractionHand.MAIN_HAND && !room.leaving) Travel.leave(player);
			return InteractionResult.SUCCESS;
		}
		if (room.dispenser.placed.contains(pos)) {
			// A word's bed, chest or composter is to look at, not to sleep in or open.
			return InteractionResult.SUCCESS;
		}
		UUID chestOwner = room.chestOwner(pos);
		if (chestOwner != null && level.getBlockState(pos).is(Blocks.CHEST)) {
			// A chest to look in, not to take from: what is waiting, shown; it all comes home on the way
			// out. The first look each visit says so.
			if (hand == InteractionHand.MAIN_HAND) {
				// Each reader's own chest shows their own prizes, whoever looks.
				ServerPlayer whose = level.getServer().getPlayerList().getPlayer(chestOwner);
				PrizeChestScreen.open(player, room.chestContents(whose != null ? whose : player));
				// Said once a visit, when nothing else is being read out; a look mid-word does not use it up.
				if (!room.chestOpened && !room.busy && !room.leaving) {
					Narrator.play(player, new Narration().say("phrase/prizes_come_home"));
					room.chestOpened = true;
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (pos.equals(layout.button())) {
			if (hand == InteractionHand.MAIN_HAND) {
				// Reading together, the button is the turn reader's, so the faster one can't answer for the other.
				// Silly sentences are anybody's.
				boolean anyone = room.bob != null && room.bob.mode == BobRound.Mode.SILLY;
				if (!anyone && !room.mayPress(player)) room.notYourTurn(player, room.turn);
				else room.answers.press(player);
			}
			return InteractionResult.PASS;
		}
		if (room.letterTarget != null && pos.equals(room.gapMarker) && hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
			room.replay(player);
			return InteractionResult.SUCCESS;
		}
		BlockState state = level.getBlockState(pos);
		// A tile in hand is being placed against the letter, which Rooms decides; an empty hand hears it.
		if (!(state.getBlock() instanceof LetterBlock) || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return null;
		if (room.busy || room.leaving) return InteractionResult.SUCCESS;
		room.touch();
		String sound = rowSound(pos);
		if (sound == null) sound = LetterBlock.phoneme(state);
		if (sound != null && !sound.isEmpty()) Narrator.play(player, new Narration().sayAt(sound, pos));
		return InteractionResult.SUCCESS;
	}

	@Nullable Letter inGap(@Nullable BlockPos gap) {
		if (gap == null) return null;
		BlockState state = level.getBlockState(gap);
		if (!state.is(LearningBlocks.LETTER)) return null;
		Letter letter = state.getValue(LetterBlock.LETTER);
		return letter.isLetter() ? letter : null;
	}

	/**
	 * The sound a letter in the word row makes as part of the word: "" for one silent in it, null
	 * for a position that is not in the row. The row is the word the gap spells, or the target while
	 * it spells none: its letters say what that word says, a vowel cut from its own recording.
	 */
	@Nullable String rowSound(BlockPos pos) {
		int index = room.row.indexOf(pos);
		Puzzle puzzle = room.puzzle;
		// A tricky word is not sounded out: any of its letters says the whole word.
		if (room.tricky && index >= 0) return "word/" + puzzle.target().text();
		// A big-and-little round's letters say what the tile itself says: a small letter its sound, a capital its name.
		if (room.casePrompt != null && index >= 0) {
			Letter letter = inGap(pos);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (letter == null && state.getBlock() instanceof LetterBlock) letter = state.getValue(LetterBlock.LETTER);
			return letter == null ? null : LetterBlock.soundOf(letter);
		}
		if (index < 0 || puzzle == null) return null;
		Word word = puzzle.target();
		// The row as it stands: the word the gaps spell, if they spell one, or else the target.
		List<String> tiles = built();
		Word spelled = tiles != null ? puzzle.spelled(tiles) : null;
		if (room.gapMarkers.containsKey(pos)) {
			Letter letter = inGap(pos);
			if (letter == null) return null;
			String sound = spelled != null ? spelled.soundOf(index) : "phoneme/" + letter.grapheme();
			return sound == null ? "" : sound;
		}
		String sound = (spelled != null ? spelled : word).soundOf(index);
		return sound == null ? "" : sound;
	}

	boolean mayBreak(BlockPos pos, BlockState state) {
		if (!state.is(LearningBlocks.LETTER) || room.busy) return false;
		if (room.gapMarkers.containsKey(pos)) return true;
		return layout.onFloor(pos);
	}

	/**
	 * Whether a tile used on this block, aimed at this spot, is going into the gap: aimed at the gap,
	 * or used on the tile already in it, the underscore behind it, or the board around the underscore.
	 */
	@Nullable BlockPos aimedGap(BlockPos hit, BlockPos target) {
		for (java.util.Map.Entry<BlockPos, BlockPos> entry : room.gapMarkers.entrySet()) {
			BlockPos gap = entry.getKey();
			BlockPos marker = entry.getValue();
			if (target.equals(gap) || hit.equals(gap) || hit.equals(marker) || hit.equals(marker.above()) || hit.equals(marker.below())) return gap;
		}
		// The board beside the first underscore: the one gap of a single-gap round.
		if (room.gapMarkers.size() == 1 && room.gapMarker != null && hit.equals(room.gapMarker.west())) return room.gap();
		return null;
	}

	@Nullable List<String> built() {
		Puzzle puzzle = room.puzzle;
		if (puzzle == null) return null;
		List<String> tiles = new ArrayList<>(puzzle.target().tiles());
		List<Integer> gaps = puzzle.gaps();
		for (int i : gaps) {
			if (i >= room.row.size()) return null;
			Letter letter = inGap(room.row.get(i));
			if (letter == null) return null;
			tiles.set(i, letter.grapheme());
		}
		return tiles;
	}

	/**
	 * A tile into the gap, saying its sound there. One already in it goes back to its place on the
	 * floor: placing into a filled gap swaps them.
	 */
	void putInGap(ServerPlayer player, Letter letter, BlockPos gap) {
		Letter old = inGap(gap);
		level.setBlock(gap, LearningBlocks.LETTER.showing(letter), FLAGS);
		room.gapPlacers.put(gap.immutable(), player.getUUID());
		Sounds.playAt(level, gap, LetterBlock.soundOf(letter));
		room.touch();
		if (old != null) sendHome(player, old, Vec3.atCenterOf(gap));
	}

	void returnGapTile(ServerPlayer player) {
		for (BlockPos gap : room.gapMarkers.keySet()) {
			Letter old = inGap(gap);
			if (old == null) continue;
			level.setBlock(gap, Blocks.AIR.defaultBlockState(), FLAGS);
			sendHome(player, old, Vec3.atCenterOf(gap));
			Narrator.note(player, "tile_home:" + old.grapheme());
		}
	}

	/**
	 * A tile flies from where it is back to its own place on the floor, lands there as a block and
	 * says its sound. The floor's layout never gets scrambled: a tile set down anywhere but the gap
	 * comes straight back here. One whose place is taken by its own copy is not doubled.
	 */
	void sendHome(ServerPlayer player, Letter letter, Vec3 from) {
		java.util.Set<BlockPos> reserved = room.dispenser.reserved;
		BlockPos home = room.homes.get(letter);
		BlockState there = home == null ? null : level.getBlockState(home);
		if (there != null && there.is(LearningBlocks.LETTER) && there.getValue(LetterBlock.LETTER) == letter) return;
		BlockPos spot = home != null && there.isAir() && !reserved.contains(home) ? home
			: room.dispenser.spotFor(player, LearningBlocks.LETTER.showing(letter), false);
		if (spot == null) return;
		reserved.add(spot);
		ItemEntity visual = room.dispenser.throwItem(from, Vec3.atBottomCenterOf(spot).add(0, 0.2, 0), Tiles.stack(letter), Dispenser.FLIGHT);
		visual.setNeverPickUp();
		level.addFreshEntity(visual);
		room.later(player, Dispenser.FLIGHT, p -> {
			visual.discard();
			reserved.remove(spot);
			if (!level.getBlockState(spot).isAir()) return;
			level.setBlock(spot, LearningBlocks.LETTER.showing(letter), FLAGS);
			Sounds.playAt(level, spot, LetterBlock.soundOf(letter));
		});
	}

	/**
	 * A tile knocked out of reach, dropped and left to vanish, or otherwise gone must not leave a
	 * round that cannot be finished. If the tile the target needs is nowhere - not in the gap, not on
	 * the floor, not on the ground, not in the child's pack or on their cursor - it comes back where
	 * it started, or on the nearest free floor.
	 */
	void keepSolvable(ServerPlayer player) {
		for (Letter letter : room.homes.keySet()) {
			if (present(player, letter)) continue;
			BlockPos home = room.homes.get(letter);
			if (home != null && level.getBlockState(home).isAir() && !room.dispenser.reserved.contains(home)) {
				level.setBlock(home, LearningBlocks.LETTER.showing(letter), FLAGS);
			} else {
				BlockPos spot = room.dispenser.spotFor(player, LearningBlocks.LETTER.showing(letter), false);
				if (spot == null) continue;
				level.setBlock(spot, LearningBlocks.LETTER.showing(letter), FLAGS);
			}
			LearningBlocks.LOGGER.info("[{}] {}'s {} tile had gone missing; put back", LearningBlocks.MOD_ID, player.getGameProfile().name(), letter.grapheme());
		}
	}

	/** Whether this tile is anywhere: gap, floor, ground, pack, cursor, or on its way home. */
	private boolean present(ServerPlayer player, Letter letter) {
		for (BlockPos gap : room.gapMarkers.keySet()) if (inGap(gap) == letter) return true;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -HALF + 1; x < HALF; x++) {
			for (int z = -HALF + 1; z < HALF; z++) {
				BlockState state = level.getBlockState(at.setWithOffset(layout.origin(), x, 0, z));
				if (state.is(LearningBlocks.LETTER) && state.getValue(LetterBlock.LETTER) == letter) return true;
			}
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, layout.bounds())) {
			if (Tiles.letterOf(item.getItem()) == letter) return true;
		}
		if (Tiles.letterOf(player.containerMenu.getCarried()) == letter) return true;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (Tiles.letterOf(inventory.getItem(slot)) == letter) return true;
		}
		return false;
	}
}
