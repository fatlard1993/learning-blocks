package justfatlard.learning_blocks.puzzle;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * What a letter block shows: a letter, a two- or three-letter tile, a capital, or the gap.
 *
 * <p>One enum and one block with a state, so a room can turn a gap into a tile and back with
 * nothing but a state change.
 */
public enum Letter implements StringRepresentable {
	A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
	BLANK,
	/**
	 * Two letters that make one sound, one tile: consonant digraphs and doubles, qu, and the vowel
	 * teams the later stages use. After the blank, so the single letters keep their places.
	 */
	SH, CH, TH, CK, QU, LL, SS, FF, ZZ, EE, EA, OA, AI, OO, IE, GG,
	/** R-controlled vowels, ow and ou, ay, igh, oi and oy, ng and nk: the later stages' tiles. */
	AR, OR, UR, IR, ER, OW, OU, AY, IGH, OI, OY, NG, NK,
	/** The capitals, for matching big and little letters in the letters level. A capital says its letter's name. */
	CAP_A, CAP_B, CAP_C, CAP_D, CAP_E, CAP_F, CAP_G, CAP_H, CAP_I, CAP_J, CAP_K, CAP_L, CAP_M, CAP_N, CAP_O, CAP_P, CAP_Q, CAP_R,
	CAP_S, CAP_T, CAP_U, CAP_V, CAP_W, CAP_X, CAP_Y, CAP_Z,
	/** A gap's underscore in a reader's colour, when two or three read together: whose gap it is. */
	BLANK_BLUE, BLANK_ORANGE, BLANK_GREEN,
	/** Later tiles, after everything whose place in the list is counted on: aw (hawk), wh (whale), le (beetle). */
	AW, WH, LE,
	DGE;

	public static final java.util.List<Letter> READER_BLANKS = java.util.List.of(BLANK_BLUE, BLANK_ORANGE, BLANK_GREEN);

	/** Tiles with a consonant letter in them that still say a vowel sound: red, and cut from the word like a vowel. */
	private static final java.util.Set<String> VOWEL_SOUNDS = java.util.Set.of("ar", "or", "ur", "ir", "er", "ow", "ou", "ay", "igh", "oi", "oy", "aw");

	private final String name = name().toLowerCase(Locale.ROOT);

	@Override
	public String getSerializedName() {
		return name;
	}

	public boolean isLetter() {
		return !name.startsWith("blank");
	}

	public boolean isSingle() {
		return isLetter() && name.length() == 1;
	}

	public boolean isDigraph() {
		return name.length() >= 2 && isLetter() && !isCapital();
	}

	public boolean isCapital() {
		return name.startsWith("cap_");
	}

	public Letter upper() {
		return isSingle() ? values()[CAP_A.ordinal() + ordinal()] : this;
	}

	public Letter lower() {
		return isCapital() ? values()[ordinal() - CAP_A.ordinal()] : this;
	}

	/** What the tile shows, as a child would write it: "a", "sh", "A". */
	public String shown() {
		return isCapital() ? lower().name.toUpperCase(Locale.ROOT) : name;
	}

	public boolean isVowel() {
		if (isCapital()) return lower().isVowel();
		return isLetter() && (VOWEL_SOUNDS.contains(name) || name.chars().allMatch(c -> "aeiou".indexOf(c) >= 0));
	}

	public String grapheme() {
		return name;
	}

	/** The tile for a grapheme - a letter, or two that make one sound - or null for anything no tile shows. */
	public static @Nullable Letter of(String grapheme) {
		if (grapheme == null) return null;
		if (grapheme.startsWith("cap_") && grapheme.length() == 5) {
			Letter small = of(grapheme.substring(4));
			return small != null && small.isSingle() ? small.upper() : null;
		}
		if (grapheme.length() == 2 || grapheme.length() == 3) {
			for (Letter letter : values()) if (letter.isDigraph() && letter.name.equals(grapheme.toLowerCase(Locale.ROOT))) return letter;
			return null;
		}
		if (grapheme.length() != 1) return null;
		char c = Character.toLowerCase(grapheme.charAt(0));
		if (c < 'a' || c > 'z') return null;
		return values()[c - 'a'];
	}
}
