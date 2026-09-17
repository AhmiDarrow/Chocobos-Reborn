package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Writes a top-down map of every course to build/track_maps/<id>.png (road,
 * features, detours, stand, decoration) so a layout change can be eyeballed
 * without launching the game. Also a smoke test that every plan renders.
 */
class CourseMapDumpTest {
	@Test
	void dumpsEveryCourseMap() throws Exception {
		File dir = new File("build/track_maps");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
			for (RaceCourseLayout.Cell c : layout.blocks().keySet()) {
				minX = Math.min(minX, c.x());
				maxX = Math.max(maxX, c.x());
				minZ = Math.min(minZ, c.z());
				maxZ = Math.max(maxZ, c.z());
			}
			int w = maxX - minX + 3, h = maxZ - minZ + 3;
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			int[][] top = new int[w][h];
			for (int[] row : top) {
				java.util.Arrays.fill(row, Integer.MIN_VALUE);
			}
			for (Map.Entry<RaceCourseLayout.Cell, String> e : layout.blocks().entrySet()) {
				RaceCourseLayout.Cell c = e.getKey();
				int px = c.x() - minX + 1, pz = c.z() - minZ + 1;
				if (c.y() < top[px][pz]) {
					continue;
				}
				top[px][pz] = c.y();
				img.setRGB(px, pz, colour(e.getValue(), track));
			}
			for (RaceCourseLayout.Tile t : layout.road()) {
				int px = t.x() - minX + 1, pz = t.z() - minZ + 1;
				int rgb = img.getRGB(px, pz);
				if (rgb == 0) {
					img.setRGB(px, pz, 0x505050);
				}
			}
			for (RaceCourseLayout.FanPost f : layout.fanPosts()) {
				img.setRGB((int) Math.floor(f.x()) - minX + 1, (int) Math.floor(f.z()) - minZ + 1, 0xFF00FF);
			}
			var s = track.stallPos(0, 6);
			img.setRGB((int) Math.floor(s.x()) - minX + 1, (int) Math.floor(s.z()) - minZ + 1, 0x00FFFF);
			ImageIO.write(img, "png", new File(dir, track.id() + ".png"));
		}
	}

	private static int colour(String block, RaceTrack track) {
		String b = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
		if (b.startsWith("chocobosreborn:boost_pad")) {
			return 0xFFE040;
		}
		if (b.equals("water")) {
			return 0x2060FF;
		}
		if (b.equals("lava")) {
			return 0xFF6000;
		}
		if (b.equals("mud")) {
			return 0x6B4A2B;
		}
		if (b.equals("white_concrete") || b.equals("black_concrete")) {
			return 0xE0E0E0;
		}
		if (b.equals(track.theme().road) || b.endsWith("_concrete")) {
			return 0x8A8A8A;
		}
		if (b.equals(track.theme().kerbA)) {
			return 0xD03030;
		}
		if (b.equals(track.theme().kerbB)) {
			return 0xF0F0F0;
		}
		if (b.equals(track.theme().wall)) {
			return 0xB0A080;
		}
		if (b.equals(track.theme().ground)) {
			return 0x3E8E3E;
		}
		if (b.equals(track.theme().base)) {
			return 0x7A6A5A;
		}
		if (b.equals(track.theme().lamp)) {
			return 0xFFFFA0;
		}
		if (b.contains("stairs")) {
			return 0xC8B860;
		}
		if (b.contains("leaves") || b.contains("log") || b.contains("cactus") || b.contains("stem")) {
			return 0x206020;
		}
		return 0x9060A0;
	}
}
