package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.system.MemoryUtil;
import tk.darrow.chocobosreborn.ChocobosReborn;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Birds skinned on the graphics card. Each mesh part is uploaded once, in its rest pose, to a
 * static vertex buffer; a frame then sends only the posed bones (18 affines, the same matrices
 * {@link MeshSkinner} composes) and draws. The CPU path skinned and wrote ~93k vertices per near
 * bird per frame, 5 to 30 ms of the render thread in a full race field.
 *
 * <p>The CPU path stays for what this one does not cover: a glowing bird (the outline pass needs
 * the buffered quads), emissive parts, a GUI preview, a shader pack (Iris/Oculus replace the
 * entity pipeline), and {@code -Dchocobosreborn.cpuBirds=true}. Any failure here switches to it
 * for the rest of the session.
 */
public final class GpuBirds {
	private GpuBirds() {
	}

	/** Kill switch: every bird on the CPU path. */
	static final boolean DISABLED = Boolean.getBoolean("chocobosreborn.cpuBirds");
	/** The race harness draws one frame each way to compare the two paths. */
	public static volatile boolean forceCpu;
	/** Bone slots in the shader ({@code Bones[384]} is 32 3x4 affines); a mesh with more takes the CPU path. */
	static final int MAX_BONES = 32;

	private static final VertexFormatElement BONE_IDS = VertexFormatElement.register(VertexFormatElement.findNextId(), 0,
			VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.GENERIC, 4);
	private static final VertexFormatElement BONE_WEIGHTS = VertexFormatElement.register(VertexFormatElement.findNextId(), 0,
			VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
	/** Rest-pose vertex: position, colour (alpha flags plumage), uv, normal, four bones and their weights. 48 bytes. */
	static final VertexFormat FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION)
			.add("Color", VertexFormatElement.COLOR)
			.add("UV0", VertexFormatElement.UV0)
			.add("Normal", VertexFormatElement.NORMAL)
			.padding(1)
			.add("BoneIds", BONE_IDS)
			.add("BoneWeights", BONE_WEIGHTS)
			.build();

	private static ShaderInstance shader;
	private static Uniform bonesUniform, visibleUniform, tintUniform, overlayUniform, lightUniform;
	private static boolean failed, announced;
	private static final Map<WhiskerMesh.Part, VertexBuffer> BUFFERS = new IdentityHashMap<>();
	private static final float[] BONES = new float[MAX_BONES * 12];
	private static final float[] VISIBLE = new float[MAX_BONES];

	/** Parts drawn on the graphics card (the race harness reports it). */
	static final java.util.concurrent.atomic.LongAdder DRAWN = new java.util.concurrent.atomic.LongAdder();

	static void registerShaders(RegisterShadersEvent event) {
		if (DISABLED) {
			return;
		}
		try {
			event.registerShader(new ShaderInstance(event.getResourceProvider(),
					ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "skinned_bird"), FORMAT), GpuBirds::adopt);
		} catch (Exception error) {
			// a driver that cannot compile it (uniform limits) keeps the CPU path rather than crashing the load
			ChocobosReborn.LOGGER.error("Bird skinning shader unavailable; birds are skinned on the CPU", error);
			shader = null;
		}
	}

	private static void adopt(ShaderInstance loaded) {
		shader = loaded;
		bonesUniform = loaded.getUniform("Bones");
		visibleUniform = loaded.getUniform("BoneVisible");
		tintUniform = loaded.getUniform("PlumageTint");
		overlayUniform = loaded.getUniform("OverlayUV");
		lightUniform = loaded.getUniform("LightUV");
		if (bonesUniform == null || visibleUniform == null || tintUniform == null || overlayUniform == null || lightUniform == null) {
			ChocobosReborn.LOGGER.error("Bird skinning shader is missing uniforms; birds are skinned on the CPU");
			shader = null;
		}
	}

	/** Whether a bird with {@code bones} bones can be drawn here this frame. */
	static boolean usable(int bones) {
		return !DISABLED && !forceCpu && !failed && shader != null && bones <= MAX_BONES && !shaderPackInUse();
	}

	/**
	 * Draw one part now. {@code composed} is {@link MeshSkinner#composed()}: the entity pose folded
	 * into each bone, so the vertex shader lands every vertex where the CPU path would. Returns
	 * false (and turns the path off) on any failure; the caller then draws the part on the CPU.
	 */
	static boolean draw(WhiskerMesh.Part part, RenderType type, float[] composed, int nb, boolean[] hiddenBone,
			int[] tint, int[] yellow, int light, int overlay) {
		try {
			VertexBuffer vbo = BUFFERS.get(part);
			if (vbo == null) {
				vbo = upload(part);
				BUFFERS.put(part, vbo);
			}
			System.arraycopy(composed, 0, BONES, 0, nb * 12);
			for (int b = 0; b < MAX_BONES; b++) {
				VISIBLE[b] = b < nb && !(b < hiddenBone.length && hiddenBone[b]) ? 1.0F : 0.0F;
			}
			bonesUniform.set(BONES);
			visibleUniform.set(VISIBLE);
			tintUniform.set(tint[0] / (float) Math.max(1, yellow[0]), tint[1] / (float) Math.max(1, yellow[1]),
					tint[2] / (float) Math.max(1, yellow[2]));
			overlayUniform.set(overlay & 0xFFFF, overlay >>> 16);
			lightUniform.set(light & 0xFFFF, light >>> 16);
			type.setupRenderState();
			try {
				vbo.bind();
				vbo.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
				VertexBuffer.unbind();
			} finally {
				type.clearRenderState();
			}
			DRAWN.increment();
			if (!announced) {
				announced = true;
				ChocobosReborn.LOGGER.info("Birds are skinned on the graphics card");
			}
			return true;
		} catch (Throwable error) {
			failed = true;
			ChocobosReborn.LOGGER.error("Bird skinning on the graphics card failed; birds are skinned on the CPU from now on", error);
			return false;
		}
	}

	/** One vertex per triangle corner, in the rest pose, with the unique slot's sorted weights (MeshSkinner reads the same). */
	private static VertexBuffer upload(WhiskerMesh.Part p) {
		int n = p.triCount * 3;
		int stride = FORMAT.getVertexSize();
		ByteBufferBuilder bytes = new ByteBufferBuilder(n * stride);
		try {
			long base = bytes.reserve(n * stride);
			for (int i = 0; i < n; i++) {
				int v = p.tri[i], u = p.posIndex[v];
				long a = base + (long) i * stride;
				MemoryUtil.memPutFloat(a, p.upos[u * 3]);
				MemoryUtil.memPutFloat(a + 4, p.upos[u * 3 + 1]);
				MemoryUtil.memPutFloat(a + 8, p.upos[u * 3 + 2]);
				int r = p.rgb[v * 3] & 0xFF, g = p.rgb[v * 3 + 1] & 0xFF, b = p.rgb[v * 3 + 2] & 0xFF;
				MemoryUtil.memPutByte(a + 12, (byte) r);
				MemoryUtil.memPutByte(a + 13, (byte) g);
				MemoryUtil.memPutByte(a + 14, (byte) b);
				MemoryUtil.memPutByte(a + 15, (byte) (AtlasTint.isPlumage(r, g, b) ? 255 : 0));
				MemoryUtil.memPutFloat(a + 16, p.uv[v * 2]);
				MemoryUtil.memPutFloat(a + 20, p.uv[v * 2 + 1]);
				MemoryUtil.memPutByte(a + 24, normalByte(p.normal[v * 3]));
				MemoryUtil.memPutByte(a + 25, normalByte(p.normal[v * 3 + 1]));
				MemoryUtil.memPutByte(a + 26, normalByte(p.normal[v * 3 + 2]));
				MemoryUtil.memPutByte(a + 27, (byte) 0);
				int count = p.ucount[u];
				for (int k = 0; k < 4; k++) {
					boolean used = k < count;
					MemoryUtil.memPutByte(a + 28 + k, (byte) (used ? p.ubone[u * 4 + k] : 0));
					MemoryUtil.memPutFloat(a + 32 + k * 4L, used ? p.uweight[u * 4 + k] : 0.0F);
				}
			}
			ByteBufferBuilder.Result result = bytes.build();
			if (result == null) {
				throw new IllegalStateException("empty bird mesh part " + p.name);
			}
			MeshData mesh = new MeshData(result, new MeshData.DrawState(FORMAT, n, n, VertexFormat.Mode.TRIANGLES,
					VertexFormat.IndexType.least(n)));
			VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
			vbo.bind();
			vbo.upload(mesh);   // closes the mesh data
			VertexBuffer.unbind();
			ChocobosReborn.LOGGER.debug("Uploaded bird part {}: {} corners, {} KiB", p.name, n, (long) n * stride / 1024);
			return vbo;
		} finally {
			bytes.close();
		}
	}

	private static byte normalByte(float f) {
		return (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, f)) * 127.0F);
	}

	private static final boolean SHADER_MOD = ModList.get() != null
			&& (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus"));
	private static java.lang.reflect.Method packInUse;
	private static Object irisApi;
	private static boolean irisLooked;

	/** A shader pack replaces the entity pipeline; a custom core shader would not match it. */
	private static boolean shaderPackInUse() {
		if (!SHADER_MOD) {
			return false;
		}
		if (!irisLooked) {
			irisLooked = true;
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				irisApi = api.getMethod("getInstance").invoke(null);
				packInUse = api.getMethod("isShaderPackInUse");
			} catch (ReflectiveOperationException | LinkageError ignored) {
				packInUse = null;
			}
		}
		if (packInUse == null) {
			return true;   // cannot tell: the CPU path is always right
		}
		try {
			return (Boolean) packInUse.invoke(irisApi);
		} catch (ReflectiveOperationException error) {
			return true;
		}
	}
}
