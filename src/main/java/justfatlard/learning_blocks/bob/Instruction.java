package justfatlard.learning_blocks.bob;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One thing told to Bob: put (the/a) (big) X in/on/under (the/a) (big) Y. Read exactly as written:
 * Bob does what it says, which is the point.
 */
public record Instruction(String x, boolean bigX, String prep, String y, boolean bigY) {

	public static @Nullable Instruction parse(List<String> words) {
		int at = 0;
		if (words.size() < 4 || !words.getFirst().equals("put")) return null;
		at++;
		Object[] first = noun(words, at);
		if (first == null) return null;
		at = (int) first[2];
		if (at >= words.size() || !Noun.PREPOSITIONS.contains(words.get(at))) return null;
		String prep = words.get(at++);
		Object[] second = noun(words, at);
		if (second == null || (int) second[2] != words.size()) return null;
		return new Instruction((String) first[0], (boolean) first[1], prep, (String) second[0], (boolean) second[1]);
	}

	/** (the/a) (big) noun from here: the noun, whether big, and where it ends. */
	private static Object @Nullable [] noun(List<String> words, int at) {
		if (at < words.size() && Noun.ARTICLES.contains(words.get(at))) at++;
		boolean big = false;
		if (at < words.size() && Noun.ADJECTIVES.contains(words.get(at))) {
			big = true;
			at++;
		}
		if (at >= words.size() || Noun.of(words.get(at)) == null) return null;
		return new Object[] {words.get(at), big, at + 1};
	}

	public List<String> words(boolean articles) {
		List<String> out = new java.util.ArrayList<>();
		out.add("put");
		if (articles) out.add("the");
		if (bigX) out.add("big");
		out.add(x);
		out.add(prep);
		if (articles) out.add("the");
		if (bigY) out.add("big");
		out.add(y);
		return out;
	}
}
