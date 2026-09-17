package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import tk.darrow.chocobosreborn.ChocobosReborn;

/**
 * Whiskerwind's sky: the same day / night panoramas as Tribal Power's March (Ahmi's
 * own art, copied into this mod's namespace), cross-faded with the time of day, with
 * the vanilla sun and moon riding over them. Registered for the Square dimension;
 * the village and every course island float in the void under it.
 */
public final class SquareSky extends DimensionSpecialEffects {
	private static final ResourceLocation[] LAYERS = {
			ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/sky/square_night.png"),
			ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/sky/square_day.png")
	};
	private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
	private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
	private static final int SEGMENTS = 96, RINGS = 48;
	private static final float[] MESH = mesh();
	private static ShaderInstance panoramaShader;

	public SquareSky() {
		super(Float.NaN, true, SkyType.NONE, false, false);
	}

	public static void registerShaders(RegisterShadersEvent event) {
		try {
			event.registerShader(new ShaderInstance(event.getResourceProvider(),
					ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "panoramic_sky"),
					DefaultVertexFormat.POSITION_TEX_COLOR), shader -> panoramaShader = shader);
		} catch (java.io.IOException error) {
			throw new java.io.UncheckedIOException("Unable to load the Whiskerwind sky shader", error);
		}
	}

	/** 1 at noon, 0 at midnight. */
	public static float dayness(long dayTime, float partial) {
		double time = (Math.floorMod(dayTime, 24000) + Math.clamp(partial, 0F, 1F)) / 24000.0;
		return (float) Math.clamp((Math.sin(time * Math.PI * 2) + 0.2) / 1.15, 0, 1);
	}

	@Override
	public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
		return color.multiply(.10 + brightness * .72, .13 + brightness * .77, .20 + brightness * .8);
	}

	@Override
	public boolean isFoggyAt(int x, int y) {
		return false;
	}

	@Override
	public float[] getSunriseColor(float time, float partial) {
		float[] base = super.getSunriseColor(time, partial);
		return base == null ? null : new float[]{.34F, .56F, .63F, base[3] * .65F};
	}

	@Override
	public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
	                         Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
		setupFog.run();
		if (isFoggy || camera.getFluidInCamera() != FogType.NONE) {
			return true;
		}
		float day = dayness(level.getDayTime(), partialTick);
		draw(level, partialTick, modelViewMatrix, new float[]{1 - day, day});
		return true;
	}

	private static void draw(ClientLevel level, float partial, Matrix4f modelView, float[] weights) {
		Matrix4f matrix = new Matrix4f(modelView).m30(0).m31(0).m32(0);
		var oldShader = RenderSystem.getShader();
		float[] oldColor = RenderSystem.getShaderColor().clone();
		int oldTexture = RenderSystem.getShaderTexture(0);
		float light = 1 - level.getRainLevel(partial) * .38F;
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(() -> panoramaShader != null ? panoramaShader : GameRenderer.getPositionTexColorShader());
		RenderSystem.setShaderColor(1, 1, 1, 1);
		try {
			float sum = 0;
			for (int layer = 0; layer < LAYERS.length; layer++) {
				float weight = Math.clamp(weights[layer], 0F, 1F);
				if (weight < .001F) {
					continue;
				}
				sum += weight;
				float alpha = weight / sum;   // exact weighted cross-fade
				RenderSystem.setShaderTexture(0, LAYERS[layer]);
				var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
				for (int i = 0; i < MESH.length; i += 5) {
					buffer.addVertex(matrix, MESH[i], MESH[i + 1], MESH[i + 2])
							.setUv(MESH[i + 3], MESH[i + 4]).setColor(light, light, light, alpha);
				}
				BufferUploader.drawWithShader(buffer.buildOrThrow());
			}
			RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			float celestial = level.getTimeOfDay(partial);
			celestial(matrix, SUN, celestial, 8, 0, 0, 1, 1, light);
			int phase = Math.floorMod(level.getMoonPhase(), 8);
			celestial(matrix, MOON, celestial + .5F, 6, (phase % 4) / 4F, (phase / 4) / 2F,
					(phase % 4 + 1) / 4F, (phase / 4 + 1) / 2F, light);
		} finally {
			RenderSystem.setShaderTexture(0, oldTexture);
			RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
			if (oldShader != null) {
				RenderSystem.setShader(() -> oldShader);
			}
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		}
	}

	private static float[] mesh() {
		float[] result = new float[SEGMENTS * RINGS * 4 * 5];
		int n = 0;
		for (int ring = 0; ring < RINGS; ring++) {
			for (int segment = 0; segment < SEGMENTS; segment++) {
				for (int corner = 0; corner < 4; corner++) {
					float u = (segment + (corner == 1 || corner == 2 ? 1 : 0)) / (float) SEGMENTS;
					float v = (ring + (corner >= 2 ? 1 : 0)) / (float) RINGS;
					double longitude = u * Math.PI * 2, latitude = v * Math.PI;
					result[n++] = (float) (Math.sin(latitude) * Math.cos(longitude) * 100);
					result[n++] = (float) (Math.cos(latitude) * 100);
					result[n++] = (float) (Math.sin(latitude) * Math.sin(longitude) * 100);
					result[n++] = u;
					result[n++] = v;
				}
			}
		}
		return result;
	}

	private static void celestial(Matrix4f matrix, ResourceLocation texture, float time, float size,
	                              float u0, float v0, float u1, float v1, float alpha) {
		double angle = time * Math.PI * 2;
		float cosine = (float) Math.cos(angle), sine = (float) Math.sin(angle);
		RenderSystem.setShaderTexture(0, texture);
		var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for (int corner = 0; corner < 4; corner++) {
			float x = (corner == 0 || corner == 3 ? -size : size);
			float vertical = corner < 2 ? -size : size;
			buffer.addVertex(matrix, x, cosine * 90 + sine * vertical, sine * 90 - cosine * vertical)
					.setUv(corner == 0 || corner == 3 ? u0 : u1, corner < 2 ? v1 : v0)
					.setColor(1F, 1F, 1F, alpha);
		}
		BufferUploader.drawWithShader(buffer.buildOrThrow());
	}
}
