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
		return r > 160 && g > 120 && b < 120 && (r - g) < 60;
	}

	/** Breed colour as the float32 channels numpy multiplies with. */
	public static float[] tint(int[] rgb) {
		return new float[]{(float) (rgb[0] / 255.0), (float) (rgb[1] / 255.0), (float) (rgb[2] / 255.0)};
	}

	/**
	 * Recolour one texel; returns 0xRRGGBB. Non-plumage texels come back unchanged.
	 */
	public static int recolor(int ri, int gi, int bi, float[] tint) {
		float r = ri / 255.0F, g = gi / 255.0F, b = bi / 255.0F;
		// the mask is taken on the float32 round trip, like the numpy code
		if (!isPlumage((int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F))) {
			return ri << 16 | gi << 8 | bi;
		}
		float scale = luma(r, g, b) / YELLOW_LUMA;
		scale = Math.min(1.45F, Math.max(0.5F, scale));
		return channel(tint[0] * scale) << 16 | channel(tint[1] * scale) << 8 | channel(tint[2] * scale);
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
