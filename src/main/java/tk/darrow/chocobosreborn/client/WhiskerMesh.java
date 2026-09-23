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
		/** Triangles with an emissive vertex; the glow pass draws only these (empty for every shipped bird). */
		public int[] emissiveTri = new int[0];
		/**
		 * The mesh is flat shaded, so ~6 vertices share each position (and its skin weights):
		 * {@code posIndex[v]} is the vertex's slot in {@code upos} / {@code uweight} / {@code ubone},
		 * and positions are skinned once per slot.
		 */
		public int[] posIndex;
		public int uniqueCount;
		public float[] upos, uweight;
		public short[] ubone;
		/** Influences per slot: {@code uweight} is sorted descending, zero weights trimmed. */
		public byte[] ucount;

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

	/**
	 * Clustering cells for the distance LODs, as a fraction of the mesh height: LOD 1 is
	 * about 3.5 cm on an adult bird, LOD 2 about 9 cm. See {@link #decimate}.
	 */
	static final float[] LOD_CELL = {1.0F / 64.0F, 1.0F / 26.0F};

	public final String[] boneNames;
	public final int[] boneParent;
	public final Part[] parts;
	/** {@code lods[level - 1]}: decimated copies of {@link #parts}, built on first use. */
	private final Part[][] lods = new Part[LOD_CELL.length][];
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

	/** Parts for LOD {@code level}: 0 is the full mesh, higher is coarser (clamped to the coarsest). */
	public Part[] parts(int level) {
		if (level <= 0) {
			return parts;
		}
		int i = Math.min(level, lods.length) - 1;
		Part[] out = lods[i];
		if (out == null) {
			out = new Part[parts.length];
			for (int k = 0; k < parts.length; k++) {
				out[k] = decimate(parts[k], LOD_CELL[i] * Math.max(0.1F, height));
			}
			lods[i] = out;
		}
		return out;
	}

	public static int lodLevels() {
		return LOD_CELL.length;
	}

	/**
	 * Vertex clustering: rest-pose positions that share a {@code cell}-sized box and a
	 * dominant bone collapse onto the first of them (whose skin weights they take), and
	 * triangles left degenerate or duplicated are dropped. Keying on the bone keeps
	 * parts that move apart (a leg against the belly) from being welded together. The
	 * surviving faces keep their uvs and colours and get a normal from their new shape,
	 * so the bird stays flat shaded, just in bigger facets.
	 */
	static Part decimate(Part p, float cell) {
		int nu = p.uniqueCount;
		int[] rep = new int[nu];
		Map<Long, Integer> clusters = new HashMap<>(nu * 2);
		float inv = 1.0F / cell;
		for (int u = 0; u < nu; u++) {
			long cx = (long) Math.floor(p.upos[u * 3] * inv) & 0xFFFF;
			long cy = (long) Math.floor(p.upos[u * 3 + 1] * inv) & 0xFFFF;
			long cz = (long) Math.floor(p.upos[u * 3 + 2] * inv) & 0xFFFF;
			long bone = p.ubone[u * 4] & 0xFFFFL;
			long key = cx | cy << 16 | cz << 32 | bone << 48;
			Integer r = clusters.putIfAbsent(key, u);
			rep[u] = r == null ? u : r;
		}
		int[] keep = new int[p.triCount];
		int kept = 0;
		java.util.Set<Long> seen = new java.util.HashSet<>(p.triCount * 2);
		for (int t = 0; t < p.triCount; t++) {
			int a = rep[p.posIndex[p.tri[t * 3]]], b = rep[p.posIndex[p.tri[t * 3 + 1]]], c = rep[p.posIndex[p.tri[t * 3 + 2]]];
			if (a == b || b == c || a == c) {
				continue;
			}
			// the same face twice (either winding: the birds draw without culling)
			int lo = Math.min(a, Math.min(b, c)), hi = Math.max(a, Math.max(b, c)), mid = a + b + c - lo - hi;
			if (!seen.add((long) lo << 42 | (long) mid << 21 | hi)) {
				continue;
			}
			keep[kept++] = t;
		}
		Part out = new Part(p.name, p.textured, kept * 3, kept);
		for (int k = 0; k < kept; k++) {
			int t = keep[k];
			float[] fp = new float[9];
			for (int j = 0; j < 3; j++) {
				int src = p.tri[t * 3 + j], dst = k * 3 + j, u = rep[p.posIndex[src]];
				System.arraycopy(p.upos, u * 3, out.pos, dst * 3, 3);
				System.arraycopy(p.upos, u * 3, fp, j * 3, 3);
				System.arraycopy(p.uweight, u * 4, out.weight, dst * 4, 4);
				System.arraycopy(p.ubone, u * 4, out.bone, dst * 4, 4);
				System.arraycopy(p.uv, src * 2, out.uv, dst * 2, 2);
				System.arraycopy(p.rgb, src * 3, out.rgb, dst * 3, 3);
				System.arraycopy(p.emit, src * 3, out.emit, dst * 3, 3);
				out.tri[dst] = dst;
			}
			faceNormal(p, t, fp, out.normal, k * 3);
		}
		finish(out);
		return out;
	}

	/** Normal of the clustered face, turned to agree with the original; the original's if the face is a sliver. */
	private static void faceNormal(Part p, int t, float[] f, float[] dst, int v0) {
		float ex = f[3] - f[0], ey = f[4] - f[1], ez = f[5] - f[2];
		float gx = f[6] - f[0], gy = f[7] - f[1], gz = f[8] - f[2];
		float nx = ey * gz - ez * gy, ny = ez * gx - ex * gz, nz = ex * gy - ey * gx;
		float ox = 0, oy = 0, oz = 0;
		for (int j = 0; j < 3; j++) {
			int v = p.tri[t * 3 + j];
			ox += p.normal[v * 3];
			oy += p.normal[v * 3 + 1];
			oz += p.normal[v * 3 + 2];
		}
		float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (l < 1e-9F) {
			nx = ox;
			ny = oy;
			nz = oz;
			l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (l < 1e-9F) {
				nx = 0;
				ny = 1;
				nz = 0;
				l = 1;
			}
		} else if (nx * ox + ny * oy + nz * oz < 0) {
			l = -l;
		}
		for (int j = 0; j < 3; j++) {
			dst[(v0 + j) * 3] = nx / l;
			dst[(v0 + j) * 3 + 1] = ny / l;
			dst[(v0 + j) * 3 + 2] = nz / l;
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
			for (int level = 1; level <= lodLevels(); level++) {
				m.parts(level);   // now, with the load, not as the first bird runs out of range mid-race
			}
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
			finish(p);
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

	/** Normalise the weights (the fast skinning path assumes they sum to 1) and list the emissive triangles. */
	static void finish(Part p) {
		for (int v = 0; v < p.vertexCount; v++) {
			float sum = 0;
			for (int k = 0; k < 4; k++) {
				sum += Math.max(0.0F, p.weight[v * 4 + k]);
			}
			for (int k = 0; k < 4; k++) {
				p.weight[v * 4 + k] = sum > 1e-6F ? Math.max(0.0F, p.weight[v * 4 + k]) / sum : (k == 0 ? 1.0F : 0.0F);
			}
		}
		boolean[] glows = new boolean[p.vertexCount];
		int lit = 0;
		for (int v = 0; v < p.vertexCount; v++) {
			glows[v] = p.emit[v * 3] != 0 || p.emit[v * 3 + 1] != 0 || p.emit[v * 3 + 2] != 0;
		}
		int[] tris = new int[p.triCount];
		for (int t = 0; t < p.triCount; t++) {
			if (glows[p.tri[t * 3]] || glows[p.tri[t * 3 + 1]] || glows[p.tri[t * 3 + 2]]) {
				tris[lit++] = t;
			}
		}
		p.emissiveTri = java.util.Arrays.copyOf(tris, lit);
		dedupePositions(p);
	}

	/** Group vertices with the same position, bones and weights so each is skinned once. */
	private static void dedupePositions(Part p) {
		int nv = p.vertexCount;
		Map<PosKey, Integer> slots = new HashMap<>(nv * 2);
		p.posIndex = new int[nv];
		float[] upos = new float[nv * 3], uwt = new float[nv * 4];
		short[] ubn = new short[nv * 4];
		int n = 0;
		for (int v = 0; v < nv; v++) {
			PosKey key = new PosKey(p, v);
			Integer slot = slots.get(key);
			if (slot == null) {
				slot = n++;
				slots.put(key, slot);
				System.arraycopy(p.pos, v * 3, upos, slot * 3, 3);
				System.arraycopy(p.weight, v * 4, uwt, slot * 4, 4);
				System.arraycopy(p.bone, v * 4, ubn, slot * 4, 4);
			}
			p.posIndex[v] = slot;
		}
		p.uniqueCount = n;
		p.upos = java.util.Arrays.copyOf(upos, n * 3);
		p.uweight = java.util.Arrays.copyOf(uwt, n * 4);
		p.ubone = java.util.Arrays.copyOf(ubn, n * 4);
		p.ucount = new byte[n];
		for (int u = 0; u < n; u++) {
			// heaviest influence first, so the skinner stops at the first zero weight
			for (int i = 1; i < 4; i++) {
				for (int j = i; j > 0 && p.uweight[u * 4 + j] > p.uweight[u * 4 + j - 1]; j--) {
					float w = p.uweight[u * 4 + j];
					p.uweight[u * 4 + j] = p.uweight[u * 4 + j - 1];
					p.uweight[u * 4 + j - 1] = w;
					short b = p.ubone[u * 4 + j];
					p.ubone[u * 4 + j] = p.ubone[u * 4 + j - 1];
					p.ubone[u * 4 + j - 1] = b;
				}
			}
			int c = 0;
			while (c < 4 && p.uweight[u * 4 + c] > 0) {
				c++;
			}
			p.ucount[u] = (byte) c;
		}
	}

	private record PosKey(float x, float y, float z, short b0, short b1, short b2, short b3, float w0, float w1, float w2, float w3) {
		PosKey(Part p, int v) {
			this(p.pos[v * 3], p.pos[v * 3 + 1], p.pos[v * 3 + 2],
					p.bone[v * 4], p.bone[v * 4 + 1], p.bone[v * 4 + 2], p.bone[v * 4 + 3],
					p.weight[v * 4], p.weight[v * 4 + 1], p.weight[v * 4 + 2], p.weight[v * 4 + 3]);
		}
	}

	private static String str(ByteBuffer b) {
		int n = b.getShort() & 0xFFFF;
		byte[] s = new byte[n];
		b.get(s);
		return new String(s, StandardCharsets.UTF_8);
	}
}
