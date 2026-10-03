package justfatlard.learning_blocks.narration;

/**
 * How wide the game's default font draws a string, in GUI pixels at scale one.
 *
 * <p>A caption is laid out on the server, one text component per word, because the server is what
 * lights each word as it is said, and a HUD overlay places its components by coordinates rather
 * than flowing them. The server has no font, so the advances are written down here: measured from
 * the game's own {@code font/ascii.png}, which is what the client draws captions with. Six pixels
 * a character, apart from the narrow ones listed, and four for a space.
 */
final class FontWidths {
	private FontWidths() {}

	static final int SPACE = 4;

	static int advance(char c) {
		return switch (c) {
			case ' ' -> SPACE;
			case '!', '\'', ',', '.', ':', ';', '|', 'i', '·' -> 2;
			case '`', 'l' -> 3;
			case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
			case '<', '>', 'f', 'k' -> 5;
			case '@', '~' -> 7;
			default -> 6;
		};
	}

	/** The drawn width: every advance, less the one pixel of spacing after the last character. */
	static int width(String text) {
		int width = 0;
		for (int i = 0; i < text.length(); i++) width += advance(text.charAt(i));
		return Math.max(0, width - 1);
	}
}
