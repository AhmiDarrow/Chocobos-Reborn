package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * A breed atlas derived at load from the shipped yellow one ({@link AtlasTint}), so the
 * jar carries one 1024x1024 atlas per mesh for the five solid breeds instead of six.
 * Registered under the path the renderer would have read the file from; the texture
 * manager re-runs {@link #load} on every resource reload, so it follows resource packs
 * that replace the yellow atlas.
 */
public final class DerivedAtlasTexture extends AbstractTexture {
	private static final Set<ResourceLocation> REGISTERED = new HashSet<>();

	private final ResourceLocation source;
	private final float[] tint;
	private final ResourceLocation eyelids;
	private final boolean recolorSource;
	private final boolean yellowLids;

	private DerivedAtlasTexture(ResourceLocation source, int[] rgb) {
		this(source, rgb, null, true);
	}

	private DerivedAtlasTexture(ResourceLocation source, int[] rgb, ResourceLocation eyelids, boolean recolorSource) {
		this.source = source;
		this.tint = AtlasTint.tint(rgb);
		this.eyelids = eyelids;
		this.recolorSource = recolorSource;
		this.yellowLids = rgb[0] == 245 && rgb[1] == 184 && rgb[2] == 18;
	}

	/** Cache a closed-eye variant once; no image processing occurs per render frame. */
	public static void ensureBlink(ResourceLocation derived, ResourceLocation source, ResourceLocation eyelids, int[] rgb, boolean recolorSource) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		if (REGISTERED.contains(derived) && textures.getTexture(derived, null) != null) return;
		if (textures.getTexture(derived, null) == null) {
			textures.register(derived, new DerivedAtlasTexture(source, rgb, eyelids, recolorSource));
		}
		REGISTERED.add(derived);
	}

	/**
	 * Make {@code derived} resolve to the recoloured copy of {@code source}. Safe to call
	 * every frame; only the first call registers (render thread, resources loaded).
	 */
	public static void ensure(ResourceLocation derived, ResourceLocation source, int[] rgb) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		// a load that failed is stored as the missing texture until a resource reload drops it: then register again
		if (REGISTERED.contains(derived) && textures.getTexture(derived, null) != null) {
			return;
		}
		if (textures.getTexture(derived, null) == null) {
			textures.register(derived, new DerivedAtlasTexture(source, rgb));
		}
		REGISTERED.add(derived);
	}

	@Override
	public void load(ResourceManager manager) throws IOException {
		NativeImage image;
		try (InputStream in = manager.getResourceOrThrow(source).open()) {
			image = NativeImage.read(in);
		}
		try {
			derive(manager, image);
		} catch (IOException | RuntimeException ex) {
			image.close();   // 4 MB of native memory otherwise
			throw ex;
		}
		if (!RenderSystem.isOnRenderThreadOrInit()) {
			RenderSystem.recordRenderCall(() -> upload(image));
		} else {
			upload(image);
		}
	}

	private void derive(ResourceManager manager, NativeImage image) throws IOException {
		int w = image.getWidth(), h = image.getHeight();
		int[] pixels = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) pixels[y * w + x] = image.getPixelRGBA(x, y);
		}
		if (recolorSource) AtlasTint.recolorPixels(pixels, w, h, tint);
		if (eyelids != null) {
			var resource = manager.getResource(eyelids);
			if (resource.isPresent()) {
				try (InputStream in = resource.get().open(); NativeImage mask = NativeImage.read(in)) {
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							int lid = mask.getPixelRGBA(x * mask.getWidth() / w, y * mask.getHeight() / h);
							if ((lid >>> 24) != 0) pixels[y * w + x] = yellowLids ? lid : AtlasTint.recolorAbgr(lid, tint);
						}
					}
				}
			}
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setPixelRGBA(x, y, pixels[y * w + x]);
			}
		}
	}

	private void upload(NativeImage image) {
		// same as SimpleTexture without a .mcmeta: no blur, no clamp, no mipmap, image freed after upload
		TextureUtil.prepareImage(this.getId(), 0, image.getWidth(), image.getHeight());
		image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, false, false, true);
	}
}
