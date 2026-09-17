package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Skinned chocobo (Meshy mesh of still-8, tools/fresh_ship.py). White vertex colours × breed atlas.
 */
public class ChocoboMeshRenderer extends EntityRenderer<ChocoboEntity> {
	private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/white.png");
	/** Chicobos are the adult mesh scaled by {@link ChocoboEntity#getAgeScale}; one mesh, one atlas set. */
	private static final ResourceLocation[] SKINS = skins("chocobo");
	/** Saddle, bridle and reins are baked into a second Meshy mesh (tools/fresh_ship.py --tag saddled). */
	private static final ResourceLocation[] SKINS_SADDLED = skins("chocobo_saddled");
	private static final java.util.Map<String, ResourceLocation[]> SKINS_ARMOR = new java.util.HashMap<>();

	/** Mesh id for this bird's outfit: armour tier mesh if it has one, else saddled, else plain. */
	private static String meshId(ChocoboEntity e) {
		var tier = e.armor();
		if (tier != null && tier.mesh() != null && WhiskerMesh.get("chocobo_armor_" + tier.mesh()) != null) {
			return "chocobo_armor_" + tier.mesh();
		}
		if (e.saddled() && WhiskerMesh.get("chocobo_saddled") != null) {
			return "chocobo_saddled";
		}
		return "chocobo";
	}

	private static ResourceLocation[] skins(String folder) {
		ChocoboColor[] colors = ChocoboColor.values();
		ResourceLocation[] out = new ResourceLocation[colors.length];
		for (int i = 0; i < colors.length; i++) {
			out[i] = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID,
					"textures/entity/" + folder + "/" + colors[i].id() + ".png");
		}
		return out;
	}
	private static final int[][] PLUMAGE = {
			{245, 184, 18}, {76, 176, 90}, {58, 143, 208}, {232, 228, 220}, {44, 42, 50},
			{232, 164, 22}, {146, 84, 214}, {232, 104, 36},
	};
	private static final int[] YELLOW = {245, 184, 18};

	private float[] bones = new float[0];
	private float[] skinPos = new float[0], skinNrm = new float[0];
	private boolean[] skinHidden = new boolean[0];

	public ChocoboMeshRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.95F;
	}

	@Override
	public ResourceLocation getTextureLocation(ChocoboEntity e) {
		int id = Math.min(e.color().getId(), SKINS.length - 1);
		String mesh = meshId(e);
		if (mesh.equals("chocobo_saddled")) {
			return SKINS_SADDLED[id];
		}
		if (mesh.startsWith("chocobo_armor_")) {
			return SKINS_ARMOR.computeIfAbsent(mesh, ChocoboMeshRenderer::skins)[id];
		}
		return SKINS[id];
	}

	@Override
	public boolean shouldRender(ChocoboEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(new AABB(e.getX() - 4, e.getY() - 1, e.getZ() - 4, e.getX() + 4, e.getY() + 5, e.getZ() + 4));
	}

	@Override
	public void render(ChocoboEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
		WhiskerMesh m = WhiskerMesh.get(meshId(e));
		if (m == null) {
			m = WhiskerMesh.get("chocobo");
		}
		if (m == null || m.clips.length == 0) {
			super.render(e, yaw, partial, ps, buf, light);
			return;
		}
		// the mesh is authored at m.height metres; the game bird stands ADULT_H
		float grow = e.getAgeScale() * (m.height > 0.1F ? ChocoboEntity.ADULT_H / m.height : 1.0F);
		this.shadowRadius = 0.95F * grow;
		WhiskerMesh.Clip idle = m.clipByName.get("idle");
		WhiskerMesh.Clip walk = m.clipByName.get("walk");
		WhiskerMesh.Clip run = m.clipByName.get("run");
		if (idle == null) {
			idle = m.clips[0];
		}
		if (walk == null) {
			walk = idle;
		}
		if (idle.frames <= 0 || idle.bones <= 0) {
			super.render(e, yaw, partial, ps, buf, light);
			return;
		}
		float time = ((float) e.tickCount + partial) / 20F;
		float spd = e.walkAnimation.speed(partial);
		float stride = e.walkAnimation.position(partial);
		float walkAmt = Mth.clamp(spd / 0.28F, 0.0F, 1.0F);
		walkAmt = walkAmt * walkAmt * (3.0F - 2.0F * walkAmt);
		float runAmt = 0.0F;
		if (run != null && run.frames > 0) {
			runAmt = Mth.clamp((spd - 0.48F) / 0.40F, 0.0F, 1.0F);
			runAmt = runAmt * runAmt * (3.0F - 2.0F * runAmt);
		}
		sampleClip(idle, time, 0.0F);
		if (walkAmt > 0.001F && walk != idle) {
			float[] idleBones = java.util.Arrays.copyOf(bones, bones.length);
			sampleClip(walk, time, stride);
			for (int i = 0; i < Math.min(bones.length, idleBones.length); i++) {
				bones[i] = Mth.lerp(walkAmt, idleBones[i], bones[i]);
			}
		}
		if (runAmt > 0.001F && run != null && run != walk) {
			float[] walkBones = java.util.Arrays.copyOf(bones, bones.length);
			sampleClip(run, time, stride);
			for (int i = 0; i < Math.min(bones.length, walkBones.length); i++) {
				bones[i] = Mth.lerp(runAmt, walkBones[i], bones[i]);
			}
		}
		java.util.Arrays.fill(hidden, false);
		if (!e.saddled()) {
			hideBone(m, "saddle");
			hideBone(m, "bridle");
		}
		if (!e.male()) {
			hideBone(m, "crest_male");
		}

		ps.pushPose();
		float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
		ps.scale(grow, grow, grow);
		PoseStack.Pose pose = ps.last();
		boolean hurt = e.hurtTime > 0;
		int overlay = OverlayTexture.pack(0, hurt);
		int[] tint = PLUMAGE[Math.min(e.color().getId(), PLUMAGE.length - 1)];
		ResourceLocation tex = getTextureLocation(e);
		VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex));
		for (WhiskerMesh.Part part : m.parts) {
			skin(part);
			emit(vc, part, pose, light, overlay, tint);
			if (hasEmit(part)) {
				emit(buf.getBuffer(RenderType.eyes(tex)), part, pose, 0xF000F0, OverlayTexture.NO_OVERLAY, null);
			}
		}
		ps.popPose();
		super.render(e, yaw, partial, ps, buf, light);
	}

	private boolean[] hidden = new boolean[0];

	private void hideBone(WhiskerMesh m, String name) {
		for (int i = 0; i < m.boneNames.length; i++) {
			if (name.equals(m.boneNames[i])) {
				if (hidden.length <= i) {
					hidden = java.util.Arrays.copyOf(hidden, m.boneNames.length);
				}
				hidden[i] = true;
				return;
			}
		}
	}

	/**
	 * Walk-animation units (vanilla advances ~1 per tick at full speed) per gait
	 * cycle. Shared by walk and run so the crossfade between them stays in phase;
	 * ~0.6 s per stride at a full dash, ~2 s at a stroll.
	 */
	private static final float STRIDE_PER_CYCLE = 12.0F;

	private void sampleClip(WhiskerMesh.Clip clip, float timeSeconds, float stride) {
		int n = clip.frames;
		// gait clips are one full stride per clip: index by stride phase, not by fps
		float f = stride > 0.001F ? (stride / STRIDE_PER_CYCLE) * n : timeSeconds * clip.fps;
		f = ((f % n) + n) % n;
		int i1 = (int) f;
		float t = f - i1;
		int i0 = (i1 - 1 + n) % n;
		int i2 = (i1 + 1) % n;
		int i3 = (i1 + 2) % n;
		int nb = clip.bones;
		if (bones.length < nb * 12) {
			bones = new float[nb * 12];
		}
		int s0 = i0 * nb * 12, s1 = i1 * nb * 12, s2 = i2 * nb * 12, s3 = i3 * nb * 12;
		float t2 = t * t;
		float t3 = t2 * t;
		for (int i = 0; i < nb * 12; i++) {
			float p0 = clip.m[s0 + i], p1 = clip.m[s1 + i], p2 = clip.m[s2 + i], p3 = clip.m[s3 + i];
			bones[i] = 0.5F * ((2.0F * p1)
					+ (-p0 + p2) * t
					+ (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
					+ (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
		}
	}

	private static boolean hasEmit(WhiskerMesh.Part p) {
		for (byte b : p.emit) {
			if (b != 0) {
				return true;
			}
		}
		return false;
	}

	private void skin(WhiskerMesh.Part p) {
		int nv = p.vertexCount;
		if (skinPos.length < nv * 3) {
			skinPos = new float[nv * 3];
			skinNrm = new float[nv * 3];
		}
		if (skinHidden.length < nv) {
			skinHidden = new boolean[nv];
		}
		for (int v = 0; v < nv; v++) {
			float x = p.pos[v * 3], y = p.pos[v * 3 + 1], z = p.pos[v * 3 + 2];
			float nx = p.normal[v * 3], ny = p.normal[v * 3 + 1], nz = p.normal[v * 3 + 2];
			float px = 0, py = 0, pz = 0, qx = 0, qy = 0, qz = 0;
			// Hidden bones (tack, the male crest): a vertex that mostly belongs to one
			// is parked far below the bird; soft-joint neighbours renormalise over the
			// visible bones instead of being dragged along with it.
			float visible = 0, best = 0;
			boolean bestHidden = false;
			for (int k = 0; k < 4; k++) {
				float w = p.weight[v * 4 + k];
				int b = p.bone[v * 4 + k];
				boolean h = b < hidden.length && hidden[b];
				if (!h) {
					visible += w;
				}
				if (w > best) {
					best = w;
					bestHidden = h;
				}
			}
			skinHidden[v] = bestHidden || visible <= 1e-6F;
			if (skinHidden[v]) {
				// triangles touching this vertex are skipped in emit(); never stretch them
				skinPos[v * 3] = x;
				skinPos[v * 3 + 1] = y;
				skinPos[v * 3 + 2] = z;
				skinNrm[v * 3] = nx;
				skinNrm[v * 3 + 1] = ny;
				skinNrm[v * 3 + 2] = nz;
				continue;
			}
			for (int k = 0; k < 4; k++) {
				float w = p.weight[v * 4 + k];
				int b = p.bone[v * 4 + k];
				if (w <= 0 || (b < hidden.length && hidden[b])) {
					continue;
				}
				w /= visible;
				int o = b * 12;
				if (o + 11 >= bones.length) {
					continue;
				}
				px += w * (bones[o] * x + bones[o + 1] * y + bones[o + 2] * z + bones[o + 3]);
				py += w * (bones[o + 4] * x + bones[o + 5] * y + bones[o + 6] * z + bones[o + 7]);
				pz += w * (bones[o + 8] * x + bones[o + 9] * y + bones[o + 10] * z + bones[o + 11]);
				qx += w * (bones[o] * nx + bones[o + 1] * ny + bones[o + 2] * nz);
				qy += w * (bones[o + 4] * nx + bones[o + 5] * ny + bones[o + 6] * nz);
				qz += w * (bones[o + 8] * nx + bones[o + 9] * ny + bones[o + 10] * nz);
			}
			float l = Mth.sqrt(qx * qx + qy * qy + qz * qz);
			if (l < 1e-6F) {
				qx = 0;
				qy = 1;
				qz = 0;
			} else {
				qx /= l;
				qy /= l;
				qz /= l;
			}
			skinPos[v * 3] = px;
			skinPos[v * 3 + 1] = py;
			skinPos[v * 3 + 2] = pz;
			skinNrm[v * 3] = qx;
			skinNrm[v * 3 + 1] = qy;
			skinNrm[v * 3 + 2] = qz;
		}
	}

	private void emit(VertexConsumer vc, WhiskerMesh.Part p, PoseStack.Pose pose, int light, int overlay, int[] tint) {
		for (int i = 0; i < p.triCount * 4; i++) {
			int tri = i / 4;
			if (i % 4 == 0 && (skinHidden[p.tri[tri * 3]] || skinHidden[p.tri[tri * 3 + 1]] || skinHidden[p.tri[tri * 3 + 2]])) {
				i += 3;   // whole quad of a hidden-bone triangle
				continue;
			}
			int v = p.tri[tri * 3 + Math.min(i % 4, 2)];
			int r = p.rgb[v * 3] & 0xFF, g = p.rgb[v * 3 + 1] & 0xFF, b = p.rgb[v * 3 + 2] & 0xFF;
			int er = p.emit[v * 3] & 0xFF, eg = p.emit[v * 3 + 1] & 0xFF, eb = p.emit[v * 3 + 2] & 0xFF;
			if (tint == null) {
				r = er;
				g = eg;
				b = eb;
			} else if (isPlumage(r, g, b)) {
				r = clamp(r * tint[0] / YELLOW[0]);
				g = clamp(g * tint[1] / Math.max(1, YELLOW[1]));
				b = clamp(b * tint[2] / Math.max(1, YELLOW[2]));
			}
			vc.addVertex(pose, skinPos[v * 3], skinPos[v * 3 + 1], skinPos[v * 3 + 2])
					.setColor(r, g, b, 255)
					.setUv(p.uv[v * 2], p.uv[v * 2 + 1])
					.setOverlay(overlay)
					.setLight(light)
					.setNormal(pose, skinNrm[v * 3], skinNrm[v * 3 + 1], skinNrm[v * 3 + 2]);
		}
	}

	private static boolean isPlumage(int r, int g, int b) {
		return r > 160 && g > 120 && b < 120 && (r - g) < 60;   // beak orange has r-g >= 70
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
