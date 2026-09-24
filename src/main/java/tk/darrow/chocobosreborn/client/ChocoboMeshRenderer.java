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

import java.util.HashMap;
import java.util.Map;

/**
 * Skinned chocobo (Meshy mesh of still-8, tools/fresh_ship.py). White vertex colours × breed atlas.
 * The 31k-triangle bird is skinned on the CPU every frame: the pose is folded into the bone
 * matrices ({@link MeshSkinner}) and vertex colours are cached per breed, so the per-vertex
 * work is one weighted transform and one raw emit.
 */
public class ChocoboMeshRenderer extends EntityRenderer<ChocoboEntity> {
	private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/white.png");
	/** Chicobos are the adult mesh scaled by {@link ChocoboEntity#getAgeScale}; one mesh, one atlas set. */
	private static final ResourceLocation[] SKINS = skins("chocobo");
	/** Saddle, bridle and reins are baked into a second Meshy mesh (tools/fresh_ship.py --tag saddled). */
	private static final ResourceLocation[] SKINS_SADDLED = skins("chocobo_saddled");
	private static final Map<String, ResourceLocation[]> SKINS_ARMOR = new HashMap<>();

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

	/** Sampled bone matrices for the frame and two scratch copies for the gait crossfade. */
	private float[] bones = new float[0], idleBones = new float[0], walkBones = new float[0];
	private final MeshSkinner skinner = new MeshSkinner();
	private boolean[] hidden = new boolean[0];
	private boolean anyHidden;
	/** Packed vertex colours per part and breed (the tint never changes per frame). */
	private static final Map<WhiskerMesh.Part, int[][]> COLORS = new HashMap<>();
	private static final Map<WhiskerMesh.Part, int[]> GLOW = new HashMap<>();

	public ChocoboMeshRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.95F;
	}

	@Override
	public ResourceLocation getTextureLocation(ChocoboEntity e) {
		return texture(e, meshId(e));
	}

	private static ResourceLocation texture(ChocoboEntity e, String mesh) {
		int id = Math.min(e.color().getId(), SKINS.length - 1);
		ResourceLocation[] set;
		if (mesh.equals("chocobo_saddled")) {
			set = SKINS_SADDLED;
		} else if (mesh.startsWith("chocobo_armor_")) {
			set = SKINS_ARMOR.computeIfAbsent(mesh, ChocoboMeshRenderer::skins);
		} else {
			set = SKINS;
		}
		if (ChocoboColor.values()[id].derivedAtlas()) {
			// the jar ships yellow, End and Nether; the solid breeds are recoloured from yellow at load
			DerivedAtlasTexture.ensure(set[id], set[ChocoboColor.YELLOW.getId()], PLUMAGE[id]);
		}
		return set[id];
	}

	@Override
	public boolean shouldRender(ChocoboEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(new AABB(e.getX() - 4, e.getY() - 1, e.getZ() - 4, e.getX() + 4, e.getY() + 5, e.getZ() + 4));
	}

	@Override
	public void render(ChocoboEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
		String mesh = meshId(e);
		WhiskerMesh m = WhiskerMesh.get(mesh);
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
		int nb = idle.bones;
		sampleClip(idle, time, 0.0F);
		if (walkAmt > 0.001F && walk != idle) {
			idleBones = swap(idleBones, bones, nb * 12);
			sampleClip(walk, time, stride);
			for (int i = 0; i < nb * 12; i++) {
				bones[i] = Mth.lerp(walkAmt, idleBones[i], bones[i]);
			}
		}
		if (runAmt > 0.001F && run != null && run != walk) {
			walkBones = swap(walkBones, bones, nb * 12);
			sampleClip(run, time, stride);
			for (int i = 0; i < nb * 12; i++) {
				bones[i] = Mth.lerp(runAmt, walkBones[i], bones[i]);
			}
		}
		if (hidden.length != m.boneNames.length) {
			hidden = new boolean[m.boneNames.length];
		}
		java.util.Arrays.fill(hidden, false);
		anyHidden = false;
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
		skinner.compose(bones, nb, pose.pose(), pose.normal());
		boolean hurt = e.hurtTime > 0;
		int overlay = OverlayTexture.pack(0, hurt);
		int breed = Math.min(e.color().getId(), PLUMAGE.length - 1);
		ResourceLocation tex = texture(e, mesh);
		// triangles save the duplicated fourth vertex; the quad type keeps the outline for a glowing bird
		boolean quads = e.isCurrentlyGlowing();
		VertexConsumer vc = buf.getBuffer(quads ? RenderType.entityCutoutNoCull(tex) : ModRenderTypes.entityCutoutNoCullTriangles(tex));
		for (WhiskerMesh.Part part : m.parts(lodLevel(e))) {
			skinner.skin(part, hidden, anyHidden);
			emit(vc, part, colors(part, breed), light, overlay, quads);
			if (part.emissiveTri.length > 0) {
				emitGlow(buf.getBuffer(RenderType.eyes(tex)), part, glow(part));
			}
		}
		ps.popPose();
		super.render(e, yaw, partial, ps, buf, light);
	}

	/**
	 * Camera distance (blocks, per metre of bird) beyond which each coarser LOD takes over.
	 * The LOD cells ({@link WhiskerMesh#LOD_CELL}) are sized so a facet stays under two
	 * pixels at 1080p there: a full race field is ~750k vertices a frame without them.
	 */
	private static final double[] LOD_DISTANCE = {16.0D, 40.0D};

	private int lodLevel(ChocoboEntity e) {
		// a GUI preview (the almanac's breed row) is never in the level and sits at the origin,
		// far from the camera, yet is drawn at full size on screen
		if (!e.isAddedToLevel()) {
			return 0;
		}
		float age = e.getAgeScale();
		double d2 = this.entityRenderDispatcher.distanceToSqr(e) / Math.max(0.09D, (double) age * age);
		int level = 0;
		while (level < LOD_DISTANCE.length && d2 > LOD_DISTANCE[level] * LOD_DISTANCE[level]) {
			level++;
		}
		return level;
	}

	/** Copy {@code n} floats of {@code src} into {@code dst} (grown if needed) and return it. */
	private static float[] swap(float[] dst, float[] src, int n) {
		if (dst.length < n) {
			dst = new float[n];
		}
		System.arraycopy(src, 0, dst, 0, n);
		return dst;
	}

	private void hideBone(WhiskerMesh m, String name) {
		for (int i = 0; i < m.boneNames.length; i++) {
			if (name.equals(m.boneNames[i])) {
				hidden[i] = true;
				anyHidden = true;
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

	/** Packed ARGB vertex colours of a part for one breed: plumage-coloured vertices take the breed tint. */
	private static int[] colors(WhiskerMesh.Part p, int breed) {
		int[][] perBreed = COLORS.computeIfAbsent(p, k -> new int[PLUMAGE.length][]);
		int[] out = perBreed[breed];
		if (out == null) {
			int[] tint = PLUMAGE[breed];
			out = new int[p.vertexCount];
			for (int v = 0; v < p.vertexCount; v++) {
				int r = p.rgb[v * 3] & 0xFF, g = p.rgb[v * 3 + 1] & 0xFF, b = p.rgb[v * 3 + 2] & 0xFF;
				if (isPlumage(r, g, b)) {
					r = clamp(r * tint[0] / YELLOW[0]);
					g = clamp(g * tint[1] / Math.max(1, YELLOW[1]));
					b = clamp(b * tint[2] / Math.max(1, YELLOW[2]));
				}
				out[v] = 0xFF000000 | r << 16 | g << 8 | b;
			}
			perBreed[breed] = out;
		}
		return out;
	}

	private static int[] glow(WhiskerMesh.Part p) {
		return GLOW.computeIfAbsent(p, k -> {
			int[] out = new int[p.vertexCount];
			for (int v = 0; v < p.vertexCount; v++) {
				out[v] = 0xFF000000 | (p.emit[v * 3] & 0xFF) << 16 | (p.emit[v * 3 + 1] & 0xFF) << 8 | (p.emit[v * 3 + 2] & 0xFF);
			}
			return out;
		});
	}

	private void emit(VertexConsumer vc, WhiskerMesh.Part p, int[] color, int light, int overlay, boolean quads) {
		final int[] tri = p.tri;
		final boolean[] hide = skinner.hidden;
		for (int t = 0; t < p.triCount; t++) {
			int a = tri[t * 3], b = tri[t * 3 + 1], c = tri[t * 3 + 2];
			if (hide[a] || hide[b] || hide[c]) {
				continue;   // a hidden-bone triangle is never stretched
			}
			vertex(vc, p, a, color[a], light, overlay);
			vertex(vc, p, b, color[b], light, overlay);
			vertex(vc, p, c, color[c], light, overlay);
			if (quads) {
				vertex(vc, p, c, color[c], light, overlay);   // vanilla entity render types are quads
			}
		}
	}

	private void emitGlow(VertexConsumer vc, WhiskerMesh.Part p, int[] color) {
		final int[] tri = p.tri;
		final boolean[] hide = skinner.hidden;
		for (int t : p.emissiveTri) {
			int a = tri[t * 3], b = tri[t * 3 + 1], c = tri[t * 3 + 2];
			if (hide[a] || hide[b] || hide[c]) {
				continue;
			}
			vertex(vc, p, a, color[a], 0xF000F0, OverlayTexture.NO_OVERLAY);
			vertex(vc, p, b, color[b], 0xF000F0, OverlayTexture.NO_OVERLAY);
			vertex(vc, p, c, color[c], 0xF000F0, OverlayTexture.NO_OVERLAY);
			vertex(vc, p, c, color[c], 0xF000F0, OverlayTexture.NO_OVERLAY);
		}
	}

	private void vertex(VertexConsumer vc, WhiskerMesh.Part p, int v, int color, int light, int overlay) {
		final float[] pos = skinner.pos, nrm = skinner.nrm;
		int u = p.posIndex[v] * 3;
		vc.addVertex(pos[u], pos[u + 1], pos[u + 2])
				.setColor(color)
				.setUv(p.uv[v * 2], p.uv[v * 2 + 1])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2]);
	}

	private static boolean isPlumage(int r, int g, int b) {
		return r > 160 && g > 120 && b < 120 && (r - g) < 60;   // beak orange has r-g >= 70
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
