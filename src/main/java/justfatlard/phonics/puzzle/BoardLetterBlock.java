package justfatlard.phonics.puzzle;

/**
 * A letter painted into the board: the part of the word that is given, and the underscore at the
 * back of the gap. The same letters and states as a tile, and the same sounds when punched, but
 * part of the room - unbreakable, and never an item.
 *
 * <p>A block of its own rather than a state of the tile, because whether a block can be broken is
 * decided on the client before the server is asked: a tile the client thinks it can break is one it
 * shows cracking and breaking and then puts back when the server refuses. This one the client
 * knows it cannot break, so it never starts.
 */
public class BoardLetterBlock extends LetterBlock {
	public BoardLetterBlock(Properties properties) {
		super(properties);
	}
}
