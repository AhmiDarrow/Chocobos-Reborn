package tk.darrow.chocobosreborn.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * CPU skinning for a {@link WhiskerMesh.Part}. The pose (model-view + scale) is folded
 * into the bone matrices once per frame, so every vertex is transformed exactly once
 * into view space and emitted raw; vanilla's per-vertex {@code addVertex(pose, ...)}
 * transform is skipped. Pure arrays, no Minecraft types, so it can be timed in a test.
 */
final class MeshSkinner {
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

	/** Fold a JOML pose into the sampled bone matrices. */
	void compose(float[] bone, int nb, Matrix4f pose, Matrix3f normal) {
		for (int i = 0; i < 3; i++) {
			for (int k = 0; k < 4; k++) {
				poseRows[i * 4 + k] = pose.get(k, i);   // JOML's get(column, row)
			}
			for (int k = 0; k < 3; k++) {
				normalRows[i * 3 + k] = normal.get(k, i);
			}
		}
		composeRows(bone, nb, poseRows, normalRows);
	}

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
