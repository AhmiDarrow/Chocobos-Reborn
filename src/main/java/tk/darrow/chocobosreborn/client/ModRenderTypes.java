package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Render types for the skinned birds. */
public final class ModRenderTypes extends RenderType {
	private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
		super(name, format, mode, size, crumbling, sort, setup, clear);
	}

	/**
	 * Vanilla's {@code entityCutoutNoCull} state drawn as triangles: a 31k-triangle bird
	 * pushes 93k vertices instead of 124k (entity quads need a fourth, duplicated vertex).
	 * No outline pass, so a glowing bird falls back to the quad type.
	 */
	private static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT_NO_CULL_TRIANGLES = Util.memoize(tex ->
			create("chocobosreborn_entity_cutout_no_cull_triangles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 1536, true, false,
					CompositeState.builder()
							.setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
							.setTextureState(new TextureStateShard(tex, false, false))
							.setTransparencyState(NO_TRANSPARENCY)
							.setCullState(NO_CULL)
							.setLightmapState(LIGHTMAP)
							.setOverlayState(OVERLAY)
							.createCompositeState(false)));

	public static RenderType entityCutoutNoCullTriangles(ResourceLocation tex) {
		return ENTITY_CUTOUT_NO_CULL_TRIANGLES.apply(tex);
	}
}
