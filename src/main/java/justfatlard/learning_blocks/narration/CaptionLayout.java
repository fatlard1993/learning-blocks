package justfatlard.learning_blocks.narration;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a caption's words go on a window this wide: on as few lines as fit, each within the window,
 * at the size asked for. A line too long for the window wraps; only when {@link #MAX_LINES} lines
 * still do not hold it, or one word alone is wider than the window, is the size brought down.
 *
 * @param scale      the size the words are drawn at: the one asked for, unless it had to come down
 * @param lines      the words on each line, by index, in order
 * @param lineWidths each line's drawn width, in GUI pixels
 */
public record CaptionLayout(float scale, List<List<Integer>> lines, List<Float> lineWidths) {

	/** From the window's edges to the caption's panel, each side. */
	static final int EDGE = 4;
	static final int PAD_X = 10;
	static final int PAD_Y = 5;
	static final int TEXT_HEIGHT = 9;
	static final int LINE_GAP = 3;
	public static final int MAX_LINES = 3;
	private static final float STEP = 0.25F;
	private static final float SMALLEST = 1.0F;

	public static int budget(int windowWidth) {
		return windowWidth - 2 * EDGE - 2 * PAD_X;
	}

	public static CaptionLayout fit(List<String> pieces, float wanted, int windowWidth) {
		int budget = budget(windowWidth);
		int[] widths = new int[pieces.size()];
		for (int i = 0; i < widths.length; i++) widths[i] = FontWidths.width(pieces.get(i));
		for (float scale = wanted; ; scale -= STEP) {
			if (scale <= SMALLEST) return wrap(widths, SMALLEST, budget, Integer.MAX_VALUE);
			CaptionLayout layout = wrap(widths, scale, budget, MAX_LINES);
			if (layout != null) return layout;
		}
	}

	/** Words onto lines, greedily; null if a word is wider than a line or more than {@code most} lines are needed. */
	private static CaptionLayout wrap(int[] widths, float scale, int budget, int most) {
		List<List<Integer>> lines = new ArrayList<>();
		List<Float> lineWidths = new ArrayList<>();
		List<Integer> line = new ArrayList<>();
		float width = 0;
		for (int i = 0; i < widths.length; i++) {
			float word = widths[i] * scale;
			if (word > budget && scale > SMALLEST) return null;
			float added = line.isEmpty() ? word : width + FontWidths.SPACE * scale + word;
			if (!line.isEmpty() && added > budget) {
				lines.add(line);
				lineWidths.add(width);
				line = new ArrayList<>();
				added = word;
			}
			line.add(i);
			width = added;
		}
		if (!line.isEmpty()) {
			lines.add(line);
			lineWidths.add(width);
		}
		if (lines.size() > most) return null;
		return new CaptionLayout(scale, lines, lineWidths);
	}

	public float widest() {
		float widest = 0;
		for (float width : lineWidths) widest = Math.max(widest, width);
		return widest;
	}

	public int panelWidth() {
		return Math.round(widest()) + 2 * PAD_X;
	}

	public int panelHeight() {
		int lineHeight = Math.round(TEXT_HEIGHT * scale);
		return lines.size() * lineHeight + (lines.size() - 1) * LINE_GAP + 2 * PAD_Y;
	}

	public boolean fits(int windowWidth) {
		return panelWidth() + 2 * EDGE <= windowWidth;
	}
}
