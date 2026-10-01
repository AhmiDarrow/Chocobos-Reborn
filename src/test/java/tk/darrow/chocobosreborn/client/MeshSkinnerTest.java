package tk.darrow.chocobosreborn.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The folded-pose skinner matches skinning in model space followed by the pose transform. */
class MeshSkinnerTest {
	private static WhiskerMesh load() throws Exception {
		return WhiskerMesh.parse(Files.readAllBytes(Path.of("src/main/resources/assets/chocobosreborn/entity/chocobo.ncgb")));
	}

	/** Rotation about y by {@code a}, uniform scale {@code s}, then a translation: rows of the 3x4 pose. */
	private static float[] pose(float a, float s, float tx, float ty, float tz) {
		float c = (float) Math.cos(a), n = (float) Math.sin(a);
		return new float[]{c * s, 0, n * s, tx, 0, s, 0, ty, -n * s, 0, c * s, tz};
	}

	/** Normal matrix of that pose: the rotation only (uniform scale drops out after normalising). */
	private static float[] normal(float a) {
		float c = (float) Math.cos(a), n = (float) Math.sin(a);
		return new float[]{c, 0, n, 0, 1, 0, -n, 0, c};
	}

	@Test
	void foldedPoseMatchesReference() throws Exception {
		WhiskerMesh m = load();
		WhiskerMesh.Part p = m.parts[0];
		WhiskerMesh.Clip idle = m.clipByName.get("idle");
		int nb = idle.bones;
		float[] bones = new float[nb * 12];
		System.arraycopy(idle.m, 37 * nb * 12, bones, 0, nb * 12);
		float[] pose = pose(0.7F, 1.44F, 1.5F, -2.0F, 7.0F);
		float[] normal = normal(0.7F);
		boolean[] hidden = new boolean[m.boneNames.length];
		hidden[6] = true;   // crest_male
		MeshSkinner s = new MeshSkinner();
		s.composeRows(bones, nb, pose, normal);
		s.skin(p, hidden, true);
		int checked = 0;
		Random rnd = new Random(1);
		for (int i = 0; i < 3000; i++) {
			int v = rnd.nextInt(p.vertexCount);
			if (s.hidden[v]) {
				continue;
			}
			float x = p.pos[v * 3], y = p.pos[v * 3 + 1], z = p.pos[v * 3 + 2];
			float nx = p.normal[v * 3], ny = p.normal[v * 3 + 1], nz = p.normal[v * 3 + 2];
			float px = 0, py = 0, pz = 0, qx = 0, qy = 0, qz = 0, visible = 0;
			for (int k = 0; k < 4; k++) {
				if (!hidden[p.bone[v * 4 + k]]) {
					visible += p.weight[v * 4 + k];
				}
			}
			for (int k = 0; k < 4; k++) {
				float w = p.weight[v * 4 + k] / visible;
				int b = p.bone[v * 4 + k];
				if (w <= 0 || hidden[b]) {
					continue;
				}
				int o = b * 12;
				px += w * (bones[o] * x + bones[o + 1] * y + bones[o + 2] * z + bones[o + 3]);
				py += w * (bones[o + 4] * x + bones[o + 5] * y + bones[o + 6] * z + bones[o + 7]);
				pz += w * (bones[o + 8] * x + bones[o + 9] * y + bones[o + 10] * z + bones[o + 11]);
				qx += w * (bones[o] * nx + bones[o + 1] * ny + bones[o + 2] * nz);
				qy += w * (bones[o + 4] * nx + bones[o + 5] * ny + bones[o + 6] * nz);
				qz += w * (bones[o + 8] * nx + bones[o + 9] * ny + bones[o + 10] * nz);
			}
			float rx = pose[0] * px + pose[1] * py + pose[2] * pz + pose[3];
			float ry = pose[4] * px + pose[5] * py + pose[6] * pz + pose[7];
			float rz = pose[8] * px + pose[9] * py + pose[10] * pz + pose[11];
			float mx = normal[0] * qx + normal[1] * qy + normal[2] * qz;
			float my = normal[3] * qx + normal[4] * qy + normal[5] * qz;
			float mz = normal[6] * qx + normal[7] * qy + normal[8] * qz;
			float l = (float) Math.sqrt(mx * mx + my * my + mz * mz);
			int u = p.posIndex[v] * 3;
			assertEquals(rx, s.pos[u], 1e-3, "x of " + v);
			assertEquals(ry, s.pos[u + 1], 1e-3, "y of " + v);
			assertEquals(rz, s.pos[u + 2], 1e-3, "z of " + v);
			assertEquals(mx / l, s.nrm[v * 3], 1e-3, "nx of " + v);
			assertEquals(my / l, s.nrm[v * 3 + 1], 1e-3, "ny of " + v);
			assertEquals(mz / l, s.nrm[v * 3 + 2], 1e-3, "nz of " + v);
			checked++;
		}
		assertTrue(checked > 2500);
		assertEquals(0, p.emissiveTri.length);
	}

	/** Each LOD is well under the one before, still skins cleanly, and keeps unit normals and the bird's extent. */
	@Test
	void lodsDecimateAndSkin() throws Exception {
		WhiskerMesh m = load();
		WhiskerMesh.Clip run = m.clipByName.get("run");
		int nb = run.bones;
		float[] bones = new float[nb * 12];
		System.arraycopy(run.m, 5 * nb * 12, bones, 0, nb * 12);
		boolean[] hidden = new boolean[m.boneNames.length];
		hidden[6] = true;
		int prev = m.parts[0].triCount;
		float[] full = extent(m.parts[0]);
		for (int level = 1; level <= WhiskerMesh.lodLevels(); level++) {
			WhiskerMesh.Part p = m.parts(level)[0];
			System.out.printf("LOD %d: %d tris (%.0f%% of full), %d skinned positions%n",
					level, p.triCount, 100.0 * p.triCount / m.parts[0].triCount, p.uniqueCount);
			assertTrue(p.triCount < prev * 0.7, "LOD " + level + " cuts triangles: " + p.triCount + " vs " + prev);
			assertTrue(p.triCount > 1500, "LOD " + level + " keeps a bird: " + p.triCount);
			float[] e = extent(p);
			for (int k = 0; k < 3; k++) {
				assertEquals(full[k], e[k], m.height * 0.08, "extent axis " + k);
			}
			MeshSkinner s = new MeshSkinner();
			s.composeRows(bones, nb, pose(0.4F, 1.3F, 0, 0, 0), normal(0.4F));
			s.skin(p, hidden, true);
			for (int v = 0; v < p.vertexCount; v++) {
				float nx = s.nrm[v * 3], ny = s.nrm[v * 3 + 1], nz = s.nrm[v * 3 + 2];
				assertEquals(1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-3, "unit normal " + v);
				int u = p.posIndex[v] * 3;
				assertTrue(Float.isFinite(s.pos[u]) && Float.isFinite(s.pos[u + 1]) && Float.isFinite(s.pos[u + 2]));
			}
			assertTrue(m.parts(level) == m.parts(level), "built once");
			prev = p.triCount;
		}
	}

	private static float[] extent(WhiskerMesh.Part p) {
		float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int v = 0; v < p.vertexCount; v++) {
			for (int k = 0; k < 3; k++) {
				lo[k] = Math.min(lo[k], p.pos[v * 3 + k]);
				hi[k] = Math.max(hi[k], p.pos[v * 3 + k]);
			}
		}
		return new float[]{hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]};
	}

	@Test
	void fastPathMatchesHiddenPathWithNothingHidden() throws Exception {
		WhiskerMesh m = load();
		WhiskerMesh.Part p = m.parts[0];
		WhiskerMesh.Clip run = m.clipByName.get("run");
		int nb = run.bones;
		float[] bones = new float[nb * 12];
		System.arraycopy(run.m, 11 * nb * 12, bones, 0, nb * 12);
		boolean[] hidden = new boolean[m.boneNames.length];
		MeshSkinner a = new MeshSkinner(), b = new MeshSkinner();
		a.composeRows(bones, nb, pose(0.2F, 1.0F, 0, 0, 0), normal(0.2F));
		b.composeRows(bones, nb, pose(0.2F, 1.0F, 0, 0, 0), normal(0.2F));
		a.skin(p, hidden, false);
		b.skin(p, hidden, true);
		for (int i = 0; i < p.uniqueCount * 3; i++) {
			assertEquals(a.pos[i], b.pos[i], 1e-5);
		}
		for (int i = 0; i < p.vertexCount * 3; i++) {
			assertEquals(a.nrm[i], b.nrm[i], 1e-5);
		}
		assertTrue(p.uniqueCount < p.vertexCount / 4, "flat-shaded mesh shares positions: " + p.uniqueCount);
	}

	/**
	 * The slot pass blends each slot's normal matrices once; the reference transforms every vertex
	 * normal by each influence. Positions are the same bits; normals agree to float rounding and
	 * almost never land in another of the vertex format's 1/127 steps. Prints the time per bird.
	 */
	@Test
	void slotNormalBlendMatchesPerInfluenceAndTimes() throws Exception {
		WhiskerMesh m = load();
		WhiskerMesh.Clip walk = m.clipByName.get("walk");
		int nb = walk.bones;
		float[] bones = new float[nb * 12];
		boolean[] hidden = new boolean[m.boneNames.length];
		for (int i = 0; i < m.boneNames.length; i++) {
			hidden[i] = m.boneNames[i].equals("saddle") || m.boneNames[i].equals("bridle");   // an unsaddled bird
		}
		float[] pose = pose(0.9F, 1.0F, 0.4F, -1.2F, 6.0F), normal = normal(0.9F);
		for (int level = 0; level <= WhiskerMesh.lodLevels(); level++) {
			WhiskerMesh.Part[] parts = m.parts(level);
			MeshSkinner now = new MeshSkinner();
			PerInfluenceSkinner ref = new PerInfluenceSkinner();
			long flips = 0, comps = 0;
			double worst = 0;
			for (int frame = 0; frame < walk.frames; frame += 7) {
				System.arraycopy(walk.m, frame * nb * 12, bones, 0, nb * 12);
				now.composeRows(bones, nb, pose, normal);
				ref.composeRows(bones, nb, pose, normal);
				for (WhiskerMesh.Part p : parts) {
					now.skin(p, hidden, true);
					ref.skin(p, hidden, true);
					for (int i = 0; i < p.uniqueCount * 3; i++) {
						assertEquals(Float.floatToIntBits(ref.pos[i]), Float.floatToIntBits(now.pos[i]), "position bits " + i);
					}
					for (int v = 0; v < p.vertexCount; v++) {
						assertEquals(ref.hidden[v], now.hidden[v], "hidden " + v);
						for (int k = 0; k < 3; k++) {
							float a = ref.nrm[v * 3 + k], b = now.nrm[v * 3 + k];
							worst = Math.max(worst, Math.abs(a - b));
							comps++;
							// the entity vertex format stores a normal component as (byte) (n * 127)
							if ((int) (Math.max(-1.0F, Math.min(1.0F, a)) * 127.0F) != (int) (Math.max(-1.0F, Math.min(1.0F, b)) * 127.0F)) {
								flips++;
							}
						}
					}
				}
			}
			assertTrue(worst < 1e-5, "normals agree to rounding: " + worst);
			assertTrue(flips * 1000 < comps, "stored normals almost never change: " + flips + " of " + comps);
			System.arraycopy(walk.m, 0, bones, 0, nb * 12);
			now.composeRows(bones, nb, pose, normal);
			ref.composeRows(bones, nb, pose, normal);
			long tRef = 0, tNow = 0;
			int rounds = level == 0 ? 300 : 900;
			for (int round = 0; round < 2; round++) {   // the first round warms the JIT
				tRef = time(() -> { for (WhiskerMesh.Part p : parts) ref.skin(p, hidden, true); }, rounds);
				tNow = time(() -> { for (WhiskerMesh.Part p : parts) now.skin(p, hidden, true); }, rounds);
			}
			int verts = 0;
			for (WhiskerMesh.Part p : parts) {
				verts += p.vertexCount;
			}
			System.out.printf("skin LOD %d (%d vertices): per influence %.1f us, slot blend %.1f us per bird (%.2fx); "
							+ "normal max diff %.2e, stored-normal steps changed %d of %d%n",
					level, verts, tRef / 1000.0, tNow / 1000.0, (double) tRef / tNow, worst, flips, comps);
		}
	}

	/** Median nanoseconds of {@code r} over {@code n} runs. */
	private static long time(Runnable r, int n) {
		long[] t = new long[n];
		for (int i = 0; i < n; i++) {
			long t0 = System.nanoTime();
			r.run();
			t[i] = System.nanoTime() - t0;
		}
		java.util.Arrays.sort(t);
		return t[n / 2];
	}

	/** The skinner as it was before the slot pass blended normal matrices: the reference for output and timing. */
	static final class PerInfluenceSkinner {
		/** Composed bone matrices: 3x4 for positions and 3x3 for normals, per bone. */
		private float[] posMat = new float[0], nrmMat = new float[0];
		/**
		 * Skinned output for the current part, in view space: positions per unique slot
		 * ({@code pos[Part.posIndex[v] * 3]}), normals per vertex.
		 */
		float[] pos = new float[0], nrm = new float[0];
		/** True for a vertex owned by a hidden bone; triangles touching it are skipped. */
		boolean[] hidden = new boolean[0];
		private boolean[] slotHidden = new boolean[0];
		private float[] slotScale = new float[0];
		private int bones;

		private final float[] poseRows = new float[12], normalRows = new float[9];

		/**
		 * Fold the pose (top three rows of the model-view matrix, row-major 3x4, and the 3x3
		 * normal matrix) into the sampled bone matrices ({@code nb} 3x4 row-major affines).
		 */
		void composeRows(float[] bone, int nb, float[] pose, float[] normal) {
			bones = nb;
			if (posMat.length < nb * 12) {
				posMat = new float[nb * 12];
				nrmMat = new float[nb * 9];
			}
			for (int b = 0; b < nb; b++) {
				int o = b * 12;
				for (int i = 0; i < 3; i++) {
					float p0 = pose[i * 4], p1 = pose[i * 4 + 1], p2 = pose[i * 4 + 2], p3 = pose[i * 4 + 3];
					for (int j = 0; j < 3; j++) {
						posMat[o + i * 4 + j] = p0 * bone[o + j] + p1 * bone[o + 4 + j] + p2 * bone[o + 8 + j];
					}
					posMat[o + i * 4 + 3] = p0 * bone[o + 3] + p1 * bone[o + 7] + p2 * bone[o + 11] + p3;
					float n0 = normal[i * 3], n1 = normal[i * 3 + 1], n2 = normal[i * 3 + 2];
					for (int j = 0; j < 3; j++) {
						nrmMat[b * 9 + i * 3 + j] = n0 * bone[o + j] + n1 * bone[o + 4 + j] + n2 * bone[o + 8 + j];
					}
				}
			}
		}

		/**
		 * Skin {@code p} with the composed matrices: each unique position once, then every
		 * vertex normal. {@code hiddenBone} marks bones whose geometry is not drawn (tack, the
		 * male crest); pass {@code anyHidden} false to take the fast path (weights are
		 * normalised at load, so no rescale).
		 */
		void skin(WhiskerMesh.Part p, boolean[] hiddenBone, boolean anyHidden) {
			int nv = p.vertexCount, nu = p.uniqueCount;
			if (pos.length < nu * 3) {
				pos = new float[nu * 3];
			}
			if (nrm.length < nv * 3) {
				nrm = new float[nv * 3];
			}
			if (hidden.length < nv) {
				hidden = new boolean[nv];
			}
			if (slotHidden.length < nu) {
				slotHidden = new boolean[nu];
				slotScale = new float[nu];
			}
			final float[] pm = posMat, nm = nrmMat, src = p.upos, srcN = p.normal, wt = p.uweight;
			final short[] bn = p.ubone;
			final byte[] cnt = p.ucount;
			final int[] slot = p.posIndex;
			final int nb = bones;
			for (int u = 0; u < nu; u++) {
				float x = src[u * 3], y = src[u * 3 + 1], z = src[u * 3 + 2];
				float rescale = 1.0F;
				boolean hide = false;
				if (anyHidden) {
					// A vertex that mostly belongs to a hidden bone is dropped; soft-joint
					// neighbours renormalise over the visible bones instead of being dragged along.
					float visible = 0, best = 0;
					for (int k = 0; k < 4; k++) {
						float w = wt[u * 4 + k];
						int b = bn[u * 4 + k];
						boolean h = b < hiddenBone.length && hiddenBone[b];
						if (!h) {
							visible += w;
						}
						if (w > best) {
							best = w;
							hide = h;
						}
					}
					hide |= visible <= 1e-6F;
					rescale = hide ? 1.0F : 1.0F / visible;
				}
				slotHidden[u] = hide;
				slotScale[u] = rescale;
				if (hide) {
					pos[u * 3] = x;
					pos[u * 3 + 1] = y;
					pos[u * 3 + 2] = z;
					continue;
				}
				float px = 0, py = 0, pz = 0;
				for (int k = 0, kn = cnt[u]; k < kn; k++) {
					float w = wt[u * 4 + k];
					int b = bn[u * 4 + k];
					if (b >= nb || (anyHidden && b < hiddenBone.length && hiddenBone[b])) {
						continue;
					}
					w *= rescale;
					int o = b * 12;
					px += w * (pm[o] * x + pm[o + 1] * y + pm[o + 2] * z + pm[o + 3]);
					py += w * (pm[o + 4] * x + pm[o + 5] * y + pm[o + 6] * z + pm[o + 7]);
					pz += w * (pm[o + 8] * x + pm[o + 9] * y + pm[o + 10] * z + pm[o + 11]);
				}
				pos[u * 3] = px;
				pos[u * 3 + 1] = py;
				pos[u * 3 + 2] = pz;
			}
			for (int v = 0; v < nv; v++) {
				int u = slot[v];
				float nx = srcN[v * 3], ny = srcN[v * 3 + 1], nz = srcN[v * 3 + 2];
				boolean hide = slotHidden[u];
				hidden[v] = hide;
				if (hide) {
					nrm[v * 3] = nx;
					nrm[v * 3 + 1] = ny;
					nrm[v * 3 + 2] = nz;
					continue;
				}
				float rescale = slotScale[u];
				float qx = 0, qy = 0, qz = 0;
				for (int k = 0, kn = cnt[u]; k < kn; k++) {
					float w = wt[u * 4 + k];
					int b = bn[u * 4 + k];
					if (b >= nb || (anyHidden && b < hiddenBone.length && hiddenBone[b])) {
						continue;
					}
					w *= rescale;
					int n = b * 9;
					qx += w * (nm[n] * nx + nm[n + 1] * ny + nm[n + 2] * nz);
					qy += w * (nm[n + 3] * nx + nm[n + 4] * ny + nm[n + 5] * nz);
					qz += w * (nm[n + 6] * nx + nm[n + 7] * ny + nm[n + 8] * nz);
				}
				float l2 = qx * qx + qy * qy + qz * qz;
				if (l2 < 1e-12F) {
					qx = 0;
					qy = 1;
					qz = 0;
				} else {
					float inv = 1.0F / (float) Math.sqrt(l2);
					qx *= inv;
					qy *= inv;
					qz *= inv;
				}
				nrm[v * 3] = qx;
				nrm[v * 3 + 1] = qy;
				nrm[v * 3 + 2] = qz;
			}
		}
}
}
