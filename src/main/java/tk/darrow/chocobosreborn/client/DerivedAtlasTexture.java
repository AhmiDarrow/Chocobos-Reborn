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

	private DerivedAtlasTexture(ResourceLocation source, int[] rgb) {
		this.source = source;
		this.tint = AtlasTint.tint(rgb);
	}

	/**
	 * Make {@code derived} resolve to the recoloured copy of {@code source}. Safe to call
	 * every frame; only the first call registers (render thread, resources loaded).
	 */
	public static void ensure(ResourceLocation derived, ResourceLocation source, int[] rgb) {
		if (REGISTERED.contains(derived)) {
			return;
		}
		TextureManager textures = Minecraft.getInstance().getTextureManager();
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
		int w = image.getWidth(), h = image.getHeight();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setPixelRGBA(x, y, AtlasTint.recolorAbgr(image.getPixelRGBA(x, y), tint));
			}
		}
		if (!RenderSystem.isOnRenderThreadOrInit()) {
			RenderSystem.recordRenderCall(() -> upload(image));
		} else {
			upload(image);
		}
	}

	private void upload(NativeImage image) {
		// same as SimpleTexture without a .mcmeta: no blur, no clamp, no mipmap, image freed after upload
		TextureUtil.prepareImage(this.getId(), 0, image.getWidth(), image.getHeight());
		image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, false, false, true);
	}
}
