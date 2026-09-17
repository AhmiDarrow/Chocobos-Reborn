package tk.darrow.chocobosreborn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import tk.darrow.chocobosreborn.ChocobosReborn;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Skinned triangle mesh (NCGB v1), same format as Ninjacat guardians.
 * Vertices in blocks, y-up, rest pose. Each clip frame is one 3x4 affine per bone.
 */
public final class WhiskerMesh {
	public static final class Part {
		public final String name;
		public final boolean textured;
		public final int vertexCount, triCount;
		public final float[] pos, normal, uv, weight;
		public final byte[] rgb, emit;
		public final short[] bone;
		public final int[] tri;

		Part(String name, boolean textured, int nv, int nt) {
			this.name = name;
			this.textured = textured;
			vertexCount = nv;
			triCount = nt;
			pos = new float[nv * 3];
			normal = new float[nv * 3];
			uv = new float[nv * 2];
			weight = new float[nv * 4];
			rgb = new byte[nv * 3];
			emit = new byte[nv * 3];
			bone = new short[nv * 4];
			tri = new int[nt * 3];
		}
	}

	public static final class Clip {
		public final String name;
		public final float fps;
		public final int frames, bones;
		public final float[] m;

		Clip(String name, float fps, int frames, int bones) {
			this.name = name;
			this.fps = fps;
			this.frames = frames;
			this.bones = bones;
			m = new float[frames * bones * 12];
		}

		public boolean loops() {
			return true;
		}
	}

	public final String[] boneNames;
	public final int[] boneParent;
	public final Part[] parts;
	public final Clip[] clips;
	public final Map<String, Clip> clipByName = new HashMap<>();
	public final float height, width;
	public final int totalVerts;

	private WhiskerMesh(String[] boneNames, int[] boneParent, Part[] parts, Clip[] clips, float height, float width) {
		this.boneNames = boneNames;
		this.boneParent = boneParent;
		this.parts = parts;
		this.clips = clips;
		this.height = height;
		this.width = width;
		int n = 0;
		for (Part p : parts) {
			n += p.vertexCount;
		}
		totalVerts = n;
		for (Clip c : clips) {
			clipByName.put(c.name, c);
		}
	}

	private static final Map<String, WhiskerMesh> CACHE = new HashMap<>();
	private static final Map<String, Boolean> FAILED = new HashMap<>();

	@Nullable
	public static WhiskerMesh get(String id) {
		WhiskerMesh m = CACHE.get(id);
		if (m != null || FAILED.containsKey(id)) {
			return m;
		}
		ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "entity/" + id + ".ncgb");
		try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(loc).open()) {
			m = parse(in.readAllBytes());
			CACHE.put(id, m);
			ChocobosReborn.LOGGER.info("Loaded mesh {}: {} parts, {} verts, {} bones", id, m.parts.length, m.totalVerts, m.boneNames.length);
		} catch (Exception e) {
			ChocobosReborn.LOGGER.error("Could not load mesh {}", id, e);
			FAILED.put(id, true);
		}
		return m;
	}

	static WhiskerMesh parse(byte[] bytes) throws IOException {
		ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (b.getInt() != 0x4247434E) {
			throw new IOException("bad magic");
		}
		b.getInt();
		int nb = b.getInt();
		String[] names = new String[nb];
		int[] parent = new int[nb];
		for (int i = 0; i < nb; i++) {
			names[i] = str(b);
			parent[i] = b.getInt();
		}
		int np = b.getInt();
		Part[] parts = new Part[np];
		for (int i = 0; i < np; i++) {
			String name = str(b);
			boolean tex = b.get() == 1;
			int nv = b.getInt();
			float[] pos = new float[nv * 3], nrm = new float[nv * 3], uv = new float[nv * 2], wt = new float[nv * 4];
			byte[] rgb = new byte[nv * 3], em = new byte[nv * 3];
			short[] bn = new short[nv * 4];
			for (int v = 0; v < nv; v++) {
				for (int k = 0; k < 3; k++) {
					pos[v * 3 + k] = b.getFloat();
				}
				for (int k = 0; k < 3; k++) {
					nrm[v * 3 + k] = b.getFloat();
				}
				uv[v * 2] = b.getFloat();
				uv[v * 2 + 1] = b.getFloat();
				b.get(rgb, v * 3, 3);
				b.get(em, v * 3, 3);
				for (int k = 0; k < 4; k++) {
					bn[v * 4 + k] = (short) (b.getShort() & 0xFFFF);
				}
				for (int k = 0; k < 4; k++) {
					wt[v * 4 + k] = b.getFloat();
				}
			}
			int nt = b.getInt();
			Part p = new Part(name, tex, nv, nt);
			System.arraycopy(pos, 0, p.pos, 0, pos.length);
			System.arraycopy(nrm, 0, p.normal, 0, nrm.length);
			System.arraycopy(uv, 0, p.uv, 0, uv.length);
			System.arraycopy(wt, 0, p.weight, 0, wt.length);
			System.arraycopy(rgb, 0, p.rgb, 0, rgb.length);
			System.arraycopy(em, 0, p.emit, 0, em.length);
			System.arraycopy(bn, 0, p.bone, 0, bn.length);
			for (int t = 0; t < nt * 3; t++) {
				p.tri[t] = b.getInt();
			}
			parts[i] = p;
		}
		int nc = b.getInt();
		Clip[] clips = new Clip[nc];
		for (int i = 0; i < nc; i++) {
			String name = str(b);
			float fps = b.getFloat();
			int nf = b.getInt();
			Clip c = new Clip(name, fps, nf, nb);
			for (int k = 0; k < c.m.length; k++) {
				c.m[k] = b.getFloat();
			}
			clips[i] = c;
		}
		float height = b.getFloat(), width = b.getFloat();
		return new WhiskerMesh(names, parent, parts, clips, height, width);
	}

	private static String str(ByteBuffer b) {
		int n = b.getShort() & 0xFFFF;
		byte[] s = new byte[n];
		b.get(s);
		return new String(s, StandardCharsets.UTF_8);
	}
}
