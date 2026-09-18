package tk.darrow.chocobosreborn.client;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Java breed recolour reproduces tools/paint_albedo.recolor_plumage. */
class AtlasTintTest {
	private static final int[] GREEN = {76, 176, 90};
	private static final int[] BLACK = {44, 42, 50};

	@Test
	void beakEyesAndLegsAreLeftAlone() {
		float[] t = AtlasTint.tint(GREEN);
		assertEquals(0xE66A0F, AtlasTint.recolor(230, 106, 15, t), "beak orange (r-g >= 70)");
		assertEquals(0x1A1A1A, AtlasTint.recolor(26, 26, 26, t), "pupil");
		assertEquals(0xFFFFFF, AtlasTint.recolor(255, 255, 255, t), "eye white");
		assertEquals(0x5A3A24, AtlasTint.recolor(90, 58, 36, t), "leg brown");
	}

	@Test
	void plumageTakesTheBreedColourByLuminance() {
		float[] t = AtlasTint.tint(GREEN);
		// values from paint_albedo.recolor_plumage on the same texels
		// (the palette yellow itself sits on the r-g < 60 guard after the float32 round trip: untouched)
		assertEquals(0xF5B812, AtlasTint.recolor(245, 184, 18, t));
		assertEquals(0x3E904A, AtlasTint.recolor(200, 150, 20, t), "shadowed feather");
		assertEquals(0x56C766, AtlasTint.recolor(255, 210, 60, t), "lit feather");
		assertEquals(0x1D1B21, AtlasTint.recolor(161, 121, 0, AtlasTint.tint(BLACK)), "black, darkest plumage clamps at 0.5");
		assertTrue(AtlasTint.isPlumage(200, 150, 20));
		assertTrue(!AtlasTint.isPlumage(230, 106, 15), "beak");
	}

	@Test
	void abgrPackingKeepsAlphaAndSwapsChannels() {
		float[] t = AtlasTint.tint(GREEN);
		// NativeImage packs ABGR: (200,150,20) plumage with alpha 0xFF -> 0x3E904A as ABGR 0xFF4A903E
		assertEquals(0xFF4A903E, AtlasTint.recolorAbgr(0xFF1496C8, t));
		// a beak texel comes back untouched, alpha included
		assertEquals(0x800F6AE6, AtlasTint.recolorAbgr(0x800F6AE6, t));
	}

	/**
	 * Full-atlas check against the Python derivation. Set CR_DERIVED_DIR to a folder of
	 * {@code <variant>/<breed>.png} written by paint_albedo.recolor_plumage over the
	 * shipped yellow atlases (skipped when unset).
	 */
	@Test
	void matchesPythonDerivation() throws Exception {
		String dir = System.getenv("CR_DERIVED_DIR");
		Assumptions.assumeTrue(dir != null && new File(dir).isDirectory(), "CR_DERIVED_DIR not set");
		String[][] breeds = {{"green", "76,176,90"}, {"blue", "58,143,208"}, {"white", "232,228,220"}, {"black", "44,42,50"}, {"gold", "232,164,22"}};
		for (String variant : new String[]{"chocobo", "chocobo_saddled", "chocobo_armor_iron", "chocobo_armor_diamond"}) {
			BufferedImage yellow = ImageIO.read(Path.of("src/main/resources/assets/chocobosreborn/textures/entity", variant, "yellow.png").toFile());
			for (String[] breed : breeds) {
				File ref = Path.of(dir, variant, breed[0] + ".png").toFile();
				if (!ref.isFile()) {
					continue;
				}
				BufferedImage expected = ImageIO.read(ref);
				String[] c = breed[1].split(",");
				float[] tint = AtlasTint.tint(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
				int off = 0, maxDiff = 0;
				for (int y = 0; y < yellow.getHeight(); y++) {
					for (int x = 0; x < yellow.getWidth(); x++) {
						int s = yellow.getRGB(x, y);
						int got = AtlasTint.recolor(s >> 16 & 0xFF, s >> 8 & 0xFF, s & 0xFF, tint);
						int want = expected.getRGB(x, y) & 0xFFFFFF;
						if (got != want) {
							off++;
							int d = Math.max(Math.abs((got >> 16 & 0xFF) - (want >> 16 & 0xFF)),
									Math.max(Math.abs((got >> 8 & 0xFF) - (want >> 8 & 0xFF)), Math.abs((got & 0xFF) - (want & 0xFF))));
							maxDiff = Math.max(maxDiff, d);
						}
					}
				}
				System.out.println("ATLAS " + variant + "/" + breed[0] + ": " + off + " texels differ, max channel diff " + maxDiff);
				assertTrue(maxDiff <= 1, variant + "/" + breed[0] + " max diff " + maxDiff);
			}
		}
	}
}
