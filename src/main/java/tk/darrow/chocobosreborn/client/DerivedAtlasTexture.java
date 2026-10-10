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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/**
 * A breed atlas derived at load from the shipped yellow one ({@link AtlasTint}), so the
 * jar carries one 1024x1024 atlas per mesh for the five solid breeds instead of six.
 * Registered under the path the renderer would have read the file from; the texture
 * manager re-runs {@link #load} on every resource reload, so it follows resource packs
 * that replace the yellow atlas.
 */
public final class DerivedAtlasTexture extends AbstractTexture {
	private static final Set<ResourceLocation> REGISTERED = new HashSet<>();
	/**
	 * Atlases being derived off the render thread (decode, recolour, eyelids: tens of ms each), keyed by the
	 * location they will be registered under. {@link #load} takes the finished image instead of deriving it.
	 */
	private static final Map<ResourceLocation, CompletableFuture<NativeImage>> PREPARED = new ConcurrentHashMap<>();

	private final ResourceLocation self;
	private final ResourceLocation source;
	private final float[] tint;
	private final ResourceLocation eyelids;
	private final boolean recolorSource;
	private final boolean yellowLids;

	private DerivedAtlasTexture(ResourceLocation self, ResourceLocation source, int[] rgb, @Nullable ResourceLocation eyelids,
	                            boolean recolorSource) {
		this.self = self;
		this.source = source;
		this.tint = AtlasTint.tint(rgb);
		this.eyelids = eyelids;
		this.recolorSource = recolorSource;
		this.yellowLids = rgb[0] == 245 && rgb[1] == 184 && rgb[2] == 18;
	}

	/**
	 * A closed-eye variant, derived off the render thread. True once it can be drawn; until then (the first blink of
	 * a breed) the caller keeps the eyes open rather than stalling a frame on the copy.
	 */
	public static boolean ensureBlink(ResourceLocation derived, ResourceLocation source, ResourceLocation eyelids, int[] rgb, boolean recolorSource) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		if (REGISTERED.contains(derived) && textures.getTexture(derived, null) != null) {
			return true;
		}
		if (textures.getTexture(derived, null) == null) {
			REGISTERED.remove(derived);   // a reload dropped it (a failed load): make it again
			CompletableFuture<NativeImage> ready = PREPARED.get(derived);
			if (ready == null) {
				prepare(derived, source, eyelids, rgb, recolorSource);
				return false;
			}
			if (!ready.isDone()) {
				return false;
			}
			textures.register(derived, new DerivedAtlasTexture(derived, source, rgb, eyelids, recolorSource));
		}
		REGISTERED.add(derived);
		return true;
	}

	/** Start deriving {@code derived} on a background thread (nothing if it is registered or already under way). */
	public static void prepare(ResourceLocation derived, ResourceLocation source, @Nullable ResourceLocation eyelids, int[] rgb,
	                           boolean recolorSource) {
		if (REGISTERED.contains(derived) || PREPARED.containsKey(derived)) {
			return;
		}
		DerivedAtlasTexture recipe = new DerivedAtlasTexture(derived, source, rgb, eyelids, recolorSource);
		ResourceManager manager = Minecraft.getInstance().getResourceManager();
		PREPARED.put(derived, CompletableFuture.supplyAsync(() -> {
			try {
				return recipe.build(manager);
			} catch (IOException error) {
				throw new java.io.UncheckedIOException(error);
			}
		}, Util.backgroundExecutor()));
	}

	/** Body atlas of a recoloured breed, derived ahead of its first frame (see {@link #prepare}). */
	public static void prepare(ResourceLocation derived, ResourceLocation source, int[] rgb) {
		prepare(derived, source, null, rgb, true);
	}

	/** Leaving the world: drop images nobody drew (native memory), and what is still being derived. */
	public static void clearPrepared() {
		for (CompletableFuture<NativeImage> f : PREPARED.values()) {
			f.thenAccept(NativeImage::close);
		}
		PREPARED.clear();
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
			// waits for a derivation already under way (see prepare) rather than starting a second one
			textures.register(derived, new DerivedAtlasTexture(derived, source, rgb, null, true));
		}
		REGISTERED.add(derived);
	}

	@Override
	public void load(ResourceManager manager) throws IOException {
		NativeImage image = null;
		CompletableFuture<NativeImage> ready = PREPARED.remove(self);
		if (ready != null) {
			try {
				image = ready.join();
			} catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failed) {
				image = null;   // derive it here instead (and report that error if it repeats)
			}
		}
		if (image == null) {
			image = build(manager);
		}
		NativeImage done = image;
		if (!RenderSystem.isOnRenderThreadOrInit()) {
			RenderSystem.recordRenderCall(() -> upload(done));
		} else {
			upload(done);
		}
	}

	/** Read the source atlas and derive this texture's pixels (any thread). */
	private NativeImage build(ResourceManager manager) throws IOException {
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
		return image;
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
