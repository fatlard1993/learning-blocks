package justfatlard.learning_blocks.narration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import justfatlard.learning_blocks.sound.Captions;

/**
 * One line of caption: the words on it, and which of them is being said at a given moment.
 *
 * <p>Two kinds. A timed line comes from a clip's caption file and lights each word from its start
 * to its end, karaoke fashion. A fixed line is built by the room - the letters of a blend so far,
 * {@code c · a} - and lights one piece for as long as it is up.
 */
public final class CaptionLine {

	public enum State { FUTURE, CURRENT, PAST, SEPARATOR, SILENT }

	/** One piece of text on the line. Times are seconds into the clip; a fixed piece has none. */
	public record Piece(String text, float start, float end, boolean separator, boolean silent) {
		Piece(String text, float start, float end, boolean separator) {
			this(text, start, end, separator, false);
		}
	}

	private final List<Piece> pieces;
	private final int fixed;

	private CaptionLine(List<Piece> pieces, int fixed) {
		this.pieces = List.copyOf(pieces);
		this.fixed = fixed;
	}

	public static CaptionLine timed(Captions.Caption caption) {
		List<Piece> pieces = new ArrayList<>();
		for (Captions.Word word : caption.words()) pieces.add(new Piece(word.text(), word.start(), word.end(), false));
		return new CaptionLine(pieces, -1);
	}

	/**
	 * The letters of a blend as far as it has got, with a dot between each, the newest lit:
	 * {@code c · a · t}.
	 */
	public static CaptionLine building(List<String> letters, int upTo) {
		return building(letters, upTo, Set.of());
	}

	/** The same, with the letters silent in this word shown greyed: there, and not said. */
	public static CaptionLine building(List<String> letters, int upTo, Set<Integer> silent) {
		List<Piece> pieces = new ArrayList<>();
		int lit = -1;
		for (int i = 0; i <= upTo && i < letters.size(); i++) {
			if (i > 0) pieces.add(new Piece("·", 0, 0, true));
			if (i == upTo) lit = pieces.size();
			pieces.add(new Piece(letters.get(i), 0, 0, false, silent.contains(i)));
		}
		return new CaptionLine(pieces, lit);
	}

	/**
	 * Words of a sentence, read one at a time, with the one being read lit and the rest of the
	 * sentence there to see: {@code put the CAT in the box}.
	 */
	public static CaptionLine sentence(List<String> words, int lit) {
		List<Piece> pieces = new ArrayList<>();
		for (String word : words) pieces.add(new Piece(word, 0, 0, false));
		return new CaptionLine(pieces, lit);
	}

	/** A line nobody has recorded yet, shown whole and lit for as long as it is up. */
	public static CaptionLine plain(String text) {
		List<Piece> pieces = new ArrayList<>();
		for (String word : text.split(" ")) pieces.add(new Piece(word, 0.0F, 3600.0F, false));
		return new CaptionLine(pieces, -1);
	}

	public static CaptionLine tagged(String tag, CaptionLine line) {
		List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece(tag, 0, 0, true));
		pieces.addAll(line.pieces);
		return new CaptionLine(pieces, line.fixed < 0 ? -1 : line.fixed + 1);
	}

	public List<Piece> pieces() {
		return pieces;
	}

	public boolean isEmpty() {
		return pieces.isEmpty();
	}

	public State state(int index, float seconds) {
		Piece piece = pieces.get(index);
		if (piece.separator()) return State.SEPARATOR;
		if (piece.silent()) return State.SILENT;
		if (fixed >= 0) return index == fixed ? State.CURRENT : index < fixed ? State.PAST : State.FUTURE;
		if (seconds < piece.start()) return State.FUTURE;
		// Lit until the next word starts, rather than dark in the gaps between words.
		float until = index + 1 < pieces.size() ? pieces.get(index + 1).start() : piece.end();
		return seconds < Math.max(until, piece.end()) ? State.CURRENT : State.PAST;
	}

	public String text() {
		StringBuilder out = new StringBuilder();
		for (Piece piece : pieces) {
			if (!out.isEmpty()) out.append(' ');
			out.append(piece.text());
		}
		return out.toString();
	}
}
