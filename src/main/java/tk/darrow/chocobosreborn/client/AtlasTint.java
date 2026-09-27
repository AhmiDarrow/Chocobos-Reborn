package tk.darrow.chocobosreborn.client;

/**
 * The breed recolour of tools/paint_albedo.py ({@code recolor_plumage}), texel for texel:
 * a plumage texel (yellow feathers; beak, eyes, legs and tack are left alone) becomes the
 * breed colour scaled by its luminance relative to the yellow. Float32 throughout, in
 * the same order as the numpy code, so a derived atlas matches the Python one.
 * No Minecraft types, so it is unit-tested against known texels.
 */
public final class AtlasTint {
	private static final float YELLOW_LUMA = luma((float) (245 / 255.0), (float) (184 / 255.0), (float) (18 / 255.0));

	private AtlasTint() {
	}

	private static float luma(float r, float g, float b) {
		return 0.2126F * r + 0.7152F * g + 0.0722F * b;
	}

	/** Same guard as {@code paint_albedo.plumage_mask} / the renderer: beak orange has r-g >= 70. */
	public static boolean isPlumage(int r, int g, int b) {
		return r > 160 && g > 120 && b < 120 && (r - g) < 70;
	}

	/** Breed colour as the float32 channels numpy multiplies with. */
	public static float[] tint(int[] rgb) {
		return new float[]{(float) (rgb[0] / 255.0), (float) (rgb[1] / 255.0), (float) (rgb[2] / 255.0)};
	}

	/**
	 * Recolour one texel; returns 0xRRGGBB. Non-plumage texels come back unchanged.
	 */
	public static int recolor(int ri, int gi, int bi, float[] tint) {
		return recolor(ri, gi, bi, tint, isPlumage(ri, gi, bi));
	}

	private static int recolor(int ri, int gi, int bi, float[] tint, boolean plumage) {
		float r = ri / 255.0F, g = gi / 255.0F, b = bi / 255.0F;
		if (!plumage) {
			return ri << 16 | gi << 8 | bi;
		}
		float scale = luma(r, g, b) / YELLOW_LUMA;
		scale = Math.min(1.45F, Math.max(0.5F, scale));
		return channel(tint[0] * scale) << 16 | channel(tint[1] * scale) << 8 | channel(tint[2] * scale);
	}

	/** Recolour from an immutable source mask so dark feather pinholes do not survive.
	 * Two bounded passes fill warm pixels surrounded by feathers; orange, neutral
	 * eye pixels and continuous leather regions cannot seed the fill. ABGR in/out.
	 */
	public static void recolorPixels(int[] pixels, int width, int height, float[] tint) {
		boolean[] mask = new boolean[pixels.length], warm = new boolean[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			int r = pixels[i] & 255, g = pixels[i] >> 8 & 255, b = pixels[i] >> 16 & 255;
			mask[i] = isPlumage(r, g, b);
			warm[i] = r > 80 && g > 60 && r >= g && r * 100 < g * 145 && b * 100 < g * 70;
		}
		for (int pass = 0; pass < 2; pass++) {
			boolean[] next = mask.clone();
			for (int y = 1; y < height - 1; y++) {
				for (int x = 1; x < width - 1; x++) {
					int i = y * width + x;
					if (mask[i] || !warm[i]) continue;
					int neighbours = 0;
					for (int dy = -1; dy <= 1; dy++) {
						for (int dx = -1; dx <= 1; dx++) {
							if (mask[i + dy * width + dx]) neighbours++;
						}
					}
					next[i] = neighbours >= 5;
				}
			}
			mask = next;
		}
		for (int i = 0; i < pixels.length; i++) {
			int p = pixels[i];
			int rgb = recolor(p & 255, p >> 8 & 255, p >> 16 & 255, tint, mask[i]);
			pixels[i] = p & 0xFF000000 | (rgb & 255) << 16 | (rgb >> 8 & 255) << 8 | rgb >> 16 & 255;
		}
	}

	/** {@link #recolor} on a NativeImage texel (ABGR packed: red in the low byte); alpha kept. */
	public static int recolorAbgr(int abgr, float[] tint) {
		int rgb = recolor(abgr & 0xFF, abgr >> 8 & 0xFF, abgr >> 16 & 0xFF, tint);
		return abgr & 0xFF000000 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF;
	}

	private static int channel(float v) {
		v = Math.min(1.0F, Math.max(0.0F, v));
		return (int) (v * 255.0F + 0.5F);
	}
}
