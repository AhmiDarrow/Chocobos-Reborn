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
}
