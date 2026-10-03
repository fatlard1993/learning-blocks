package justfatlard.learning_blocks.puzzle;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Picture;
import justfatlard.learning_blocks.LearningBlocks;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Picture cards: a word's picture drawn on a little panel by Pandorical, for words Minecraft has
 * no item or block for (the moon, a sad face). The pictures are small pixel-art PNGs in the mod,
 * {@code textures/card/<name>.png}, read once and kept.
 *
 * <p>A card is anchored to an entity and lives as long as it does; only players with Pandorical
 * see it.
 */
public final class PictureCards {
	private PictureCards() {}

	private static final Map<String, Picture> cache = new HashMap<>();

	/** The card's picture, standing on the anchor's feet, a block across, facing south; null if there is no such card. */
	public static synchronized @Nullable Picture of(String name) {
		if (cache.containsKey(name)) return cache.get(name);
		Picture picture = load(name);
		cache.put(name, picture);
		return picture;
	}

	private static @Nullable Picture load(String name) {
		String path = "/assets/" + LearningBlocks.MOD_ID + "/textures/card/" + name + ".png";
		try (InputStream in = PictureCards.class.getResourceAsStream(path)) {
			if (in == null) {
				LearningBlocks.LOGGER.warn("[{}] No picture card {}", LearningBlocks.MOD_ID, path);
				return null;
			}
			BufferedImage image = javax.imageio.ImageIO.read(in);
			int w = image.getWidth();
			int h = image.getHeight();
			List<Integer> palette = new ArrayList<>();
			palette.add(0x00000000);
			Map<Integer, Integer> index = new HashMap<>();
			byte[] cells = new byte[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = image.getRGB(x, y);
					if ((argb >>> 24) == 0) continue;
					Integer at = index.get(argb);
					if (at == null) {
						if (palette.size() >= 256) at = 0;
						else {
							at = palette.size();
							palette.add(argb);
							index.put(argb, at);
						}
					}
					cells[y * w + x] = (byte) (int) at;
				}
			}
			int[] colours = palette.stream().mapToInt(Integer::intValue).toArray();
			return new Picture(w, h, colours, cells, new Picture.Pose(0, 0, 0, 0, 0, 1.4F, 1.4F), 0xFFF2E8C9, 0.04F);
		} catch (Exception e) {
			LearningBlocks.LOGGER.error("[{}] Could not read the picture card {}", LearningBlocks.MOD_ID, path, e);
			return null;
		}
	}

	/** Show this card on the anchor. False if it could not be. */
	public static boolean show(Entity anchor, String name) {
		Picture picture = of(name);
		if (picture == null) return false;
		PandoricalApi.pictures().show(anchor, picture);
		return true;
	}
}
