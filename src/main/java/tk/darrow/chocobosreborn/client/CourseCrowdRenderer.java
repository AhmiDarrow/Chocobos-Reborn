package tk.darrow.chocobosreborn.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.CourseStands;
import tk.darrow.chocobosreborn.race.RaceCourseLayout;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.race.TownRole;

/**
 * The crowd in the course grandstands, drawn straight from {@link CourseStands} with the
 * kin model: no entities, nothing ticking, nothing on the server or the network.
 * <p>
 * Built for a few hundred figures a frame:
 * <ul>
 * <li>only in the Square, only for a course whose island is in range, only once its
 * stands are actually built (a seat under the stand's light probe);</li>
 * <li>each stand is culled as one box (distance and frustum), then each fan (96 blocks,
 * frustum); light is sampled once per stand;</li>
 * <li>LOD: inside 40 blocks a fan sways, looks about and, while racing birds are near
 * its stand, cheers with its arms up and hops (the "birds near" test runs per stand
 * twice a second, never per fan); further out every fan shares one of two precomputed
 * poses (idle / cheering) with no per-fan animation maths, drops its eye glow past 40
 * and its cloak past 72 blocks;</li>
 * <li>draws are grouped by texture (four skins, four cloaks, four glows: at most twelve
 * render-type switches a frame), and the kin geometry is captured once into flat arrays
 * and emitted through reused matrices, so the hot loop allocates nothing.</li>
 * </ul>
 * The stand plans are computed off the render thread the first time a course comes near.
 */
public final class CourseCrowdRenderer {
	private static final double RANGE = 96.0D, RANGE2 = RANGE * RANGE;
	private static final double NEAR2 = 40.0D * 40.0D;
	private static final double CLOAK2 = 72.0D * 72.0D;
	private static final double CHEER2 = 48.0D * 48.0D;
	private static final int FULL_BRIGHT = 15728880;
	private static final TownRole[] LOOKS = {TownRole.FAN_SWARM, TownRole.FAN_CLOCK, TownRole.FAN_SPROUT, TownRole.FAN_CLAW};
	private static final String[] PARTS = {"head", "body", "cloak", "arm0", "arm1", "leg0", "leg1"};
	private static final int HEAD = 0, BODY = 1, CLOAK = 2, ARM_R = 3, ARM_L = 4;
	private static final RaceTrack[] TRACKS = RaceTrack.values();

	private static final Map<RaceTrack, Crowd> CROWDS = new EnumMap<>(RaceTrack.class);
	private static final Map<RaceTrack, CompletableFuture<Crowd>> PENDING = new EnumMap<>(RaceTrack.class);
	/** Plans that threw: the plan is deterministic, so retrying every frame only re-ran the job and the warning. */
	private static final java.util.Set<RaceTrack> FAILED = java.util.EnumSet.noneOf(RaceTrack.class);

	private static RenderType[] skinTypes, cloakTypes, glowTypes;
	private static int[] cloakColours;
	/** Captured kin geometry, per part, in the part's own space: x y z u v nx ny nz per vertex. */
	private static float[][] mesh;
	private static PartPose[] rest;
	/** The two far poses: model-space matrix per part (idle, cheering), and their normal matrices. */
	private static final Matrix4f[][] FAR = new Matrix4f[2][PARTS.length];
	private static final Matrix3f[][] FAR_N = new Matrix3f[2][PARTS.length];

	// scratch, render thread only
	private static final Matrix4f FAN = new Matrix4f();
	private static final Matrix3f FAN_N = new Matrix3f();
	private static final Matrix4f PART = new Matrix4f();
	private static final Matrix3f PART_N = new Matrix3f();
	private static final Vector3f POS = new Vector3f();
	private static final Vector3f NRM = new Vector3f();
	private static final float[] XR = new float[PARTS.length], YR = new float[PARTS.length], ZR = new float[PARTS.length];
	private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
	/**
	 * Each drawn fan's vertices as the skin pass transformed them (x y z nx ny nz), indexed by fan:
	 * the cloak and glow passes draw the same posed geometry with another texture, so they reuse it.
	 */
	private static float[] fanVerts = new float[0];
	private static int fanStride;

	private CourseCrowdRenderer() {
	}

	/** One course's crowd, flattened for the render loop. */
	private static final class Crowd {
		final int fans;
		final double[] x, y, z;
		final float[] yawRad, phase, scale;
		final byte[] look;
		final AABB[] fanBox;
		final int[] fanStand;
		final int stands;
		final AABB[] standBox;
		final int[] standFirst, standCount, probeX, probeY, probeZ;
		final double[] standCx, standCy, standCz;
		final AABB courseBox;
		// per frame
		final int[] standLight;
		final boolean[] standCheer;
		final int[][] byLook;
		final int[] byLookCount = new int[LOOKS.length];
		final byte[] lod;
		// birds near the course, refreshed twice a second
		long cheerBucket = Long.MIN_VALUE;
		double[] birds = new double[0];
		int birdCount;

		Crowd(RaceTrack track, CourseStands cs) {
			List<RaceCourseLayout.FanPost> posts = cs.fanPosts();
			List<CourseStands.Stand> st = cs.stands();
			fans = posts.size();
			x = new double[fans];
			y = new double[fans];
			z = new double[fans];
			yawRad = new float[fans];
			phase = new float[fans];
			scale = new float[fans];
			look = new byte[fans];
			fanBox = new AABB[fans];
			fanStand = new int[fans];
			lod = new byte[fans];
			for (int i = 0; i < fans; i++) {
				RaceCourseLayout.FanPost f = posts.get(i);
				x[i] = f.x();
				y[i] = f.y();
				z[i] = f.z();
				yawRad[i] = (float) Math.toRadians(180.0D - f.yaw());
				long h = mix(track.ordinal() * 0x9E3779B97F4A7C15L + i);
				look[i] = (byte) Math.floorMod(h, LOOKS.length);
				phase[i] = (float) (((h >>> 8) & 0xFFFF) / 65536.0D * Math.PI * 2.0D);
				scale[i] = 0.9F + ((h >>> 24) & 0xFF) / 255.0F * 0.14F;
				fanBox[i] = new AABB(f.x() - 0.4D, f.y() - 0.1D, f.z() - 0.4D, f.x() + 0.4D, f.y() + 2.3D, f.z() + 0.4D);
				fanStand[i] = f.stand();
			}
			stands = st.size();
			standBox = new AABB[stands];
			standFirst = new int[stands];
			standCount = new int[stands];
			probeX = new int[stands];
			probeY = new int[stands];
			probeZ = new int[stands];
			standCx = new double[stands];
			standCy = new double[stands];
			standCz = new double[stands];
			standLight = new int[stands];
			standCheer = new boolean[stands];
			for (int s = 0; s < stands; s++) {
				CourseStands.Stand b = st.get(s);
				standBox[s] = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
				standFirst[s] = b.firstFan();
				standCount[s] = b.fanCount();
				probeX[s] = b.lightX();
				probeY[s] = b.lightY();
				probeZ[s] = b.lightZ();
				standCx[s] = (b.minX() + b.maxX()) / 2.0D;
				standCy[s] = (b.minY() + b.maxY()) / 2.0D;
				standCz[s] = (b.minZ() + b.maxZ()) / 2.0D;
			}
			byLook = new int[LOOKS.length][fans];
			double rx = track.getRadiusX() + 32.0D, rz = track.getRadiusZ() + 32.0D;
			courseBox = new AABB(track.centerX() - rx, 40.0D, track.centerZ() - rz, track.centerX() + rx, 140.0D, track.centerZ() + rz);
		}
	}

	/** Game-bus listener: draw the crowd of every course in range after the entities. */
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || !Square.isSquare(level)) {
			return;
		}
		Camera camera = event.getCamera();
		Vec3 cam = camera.getPosition();
		boolean any = false;
		for (RaceTrack track : TRACKS) {
			double dx = cam.x - track.centerX(), dz = cam.z - track.centerZ();
			double reach = Math.max(track.getRadiusX(), track.getRadiusZ()) + 32.0D + RANGE;
			if (dx * dx + dz * dz > reach * reach) {
				continue;
			}
			Crowd crowd = crowd(track);
			if (crowd == null) {
				continue;
			}
			if (!any) {
				if (!ensureModel(mc)) {
					return;
				}
				any = true;
			}
			float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
			draw(crowd, level, event.getFrustum(), cam, mc.renderBuffers().bufferSource(), level.getGameTime(), partial);
		}
	}

	/** The crowd for a course, or null while its plan is still being worked out off-thread. */
	private static Crowd crowd(RaceTrack track) {
		Crowd c = CROWDS.get(track);
		if (c != null) {
			return c;
		}
		CompletableFuture<Crowd> f = PENDING.get(track);
		if (f == null) {
			if (FAILED.contains(track)) {
				return null;
			}
			PENDING.put(track, CompletableFuture.supplyAsync(() -> new Crowd(track, CourseStands.of(track)), Util.backgroundExecutor()));
			return null;
		}
		if (!f.isDone()) {
			return null;
		}
		PENDING.remove(track);
		try {
			c = f.join();
			CROWDS.put(track, c);
			return c;
		} catch (RuntimeException e) {
			ChocobosReborn.LOGGER.warn("Crowd for {} failed to plan", track, e);
			FAILED.add(track);
			return null;
		}
	}

	private static void draw(Crowd c, ClientLevel level, Frustum frustum, Vec3 cam, MultiBufferSource.BufferSource buffers,
			long gameTime, float partial) {
		refreshBirds(c, level, gameTime);
		java.util.Arrays.fill(c.byLookCount, 0);
		double cx = cam.x, cy = cam.y, cz = cam.z;
		boolean anyCheer = false;
		for (int s = 0; s < c.stands; s++) {
			AABB box = c.standBox[s];
			double ex = Math.max(0.0D, Math.max(box.minX - cx, cx - box.maxX));
			double ey = Math.max(0.0D, Math.max(box.minY - cy, cy - box.maxY));
			double ez = Math.max(0.0D, Math.max(box.minZ - cz, cz - box.maxZ));
			if (ex * ex + ey * ey + ez * ez > RANGE2 || !frustum.isVisible(box)) {
				continue;
			}
			PROBE.set(c.probeX[s], c.probeY[s] - 1, c.probeZ[s]);
			if (!level.hasChunk(PROBE.getX() >> 4, PROBE.getZ() >> 4) || level.getBlockState(PROBE).isAir()) {
				continue;   // the island is not loaded, or still wears an older plan without this stand
			}
			PROBE.setY(c.probeY[s]);
			c.standLight[s] = LevelRenderer.getLightColor(level, PROBE);
			boolean cheer = false;
			for (int b = 0; b < c.birdCount && !cheer; b++) {
				double bx = c.birds[b * 3] - c.standCx[s], by = c.birds[b * 3 + 1] - c.standCy[s], bz = c.birds[b * 3 + 2] - c.standCz[s];
				cheer = bx * bx + by * by + bz * bz <= CHEER2;
			}
			c.standCheer[s] = cheer;
			anyCheer |= cheer;
			int end = c.standFirst[s] + c.standCount[s];
			for (int i = c.standFirst[s]; i < end; i++) {
				double dx = c.x[i] - cx, dy = c.y[i] - cy, dz = c.z[i] - cz;
				double d2 = dx * dx + dy * dy + dz * dz;
				if (d2 > RANGE2 || !frustum.isVisible(c.fanBox[i])) {
					continue;
				}
				c.lod[i] = (byte) (d2 < NEAR2 ? 0 : d2 < CLOAK2 ? 1 : 2);
				int lk = c.look[i];
				c.byLook[lk][c.byLookCount[lk]++] = i;
			}
		}
		float age = (float) (gameTime % 24000L) + partial;
		int perFan = 0;
		for (float[] m : mesh) {
			perFan += m.length / 8 * 6;
		}
		fanStride = perFan;
		if (fanVerts.length < c.fans * perFan) {
			fanVerts = new float[c.fans * perFan];
		}
		// skins, then cloaks (not the far ones), then eye glow (near ones only): one render type at a time
		for (int pass = 0; pass < 3; pass++) {
			for (int lk = 0; lk < LOOKS.length; lk++) {
				int n = c.byLookCount[lk];
				if (n == 0) {
					continue;
				}
				VertexConsumer vc = null;
				int[] list = c.byLook[lk];
				for (int k = 0; k < n; k++) {
					int i = list[k];
					if (c.lod[i] > (pass == 0 ? 2 : pass == 1 ? 1 : 0)) {
						continue;
					}
					if (vc == null) {
						vc = buffers.getBuffer(pass == 0 ? skinTypes[lk] : pass == 1 ? cloakTypes[lk] : glowTypes[lk]);
					}
					int light = pass == 2 ? FULL_BRIGHT : c.standLight[c.fanStand[i]];
					int colour = pass == 1 ? cloakColours[lk] : 0xFFFFFFFF;
					if (pass == 0) {
						emitFan(c, i, vc, light, colour, c.standCheer[c.fanStand[i]], age, cx, cy, cz);
					} else {
						emitCached(i, vc, light, colour);
					}
				}
			}
		}
		buffers.endLastBatch();
	}

	/** Positions of racing birds near the course, twice a second (one entity query per course). */
	private static void refreshBirds(Crowd c, ClientLevel level, long gameTime) {
		long bucket = gameTime / 10L;
		if (bucket == c.cheerBucket) {
			return;
		}
		c.cheerBucket = bucket;
		List<ChocoboEntity> racing = level.getEntitiesOfClass(ChocoboEntity.class, c.courseBox, ChocoboEntity::racing);
		if (c.birds.length < racing.size() * 3) {
			c.birds = new double[racing.size() * 3];
		}
		for (int b = 0; b < racing.size(); b++) {
			ChocoboEntity bird = racing.get(b);
			c.birds[b * 3] = bird.getX();
			c.birds[b * 3 + 1] = bird.getY();
			c.birds[b * 3 + 2] = bird.getZ();
		}
		c.birdCount = racing.size();
	}

	/** One fan: the entity transform of a living renderer, then each part, animated up close or a shared far pose. */
	private static void emitFan(Crowd c, int i, VertexConsumer vc, int light, int colour, boolean cheer, float age,
			double cx, double cy, double cz) {
		boolean near = c.lod[i] == 0;
		float hop = 0.0F;
		if (near) {
			float local = age + c.phase[i] * 10.0F;
			pose(cheer, local, c.phase[i]);
			if (cheer) {
				hop = Math.abs((float) Math.sin(local * 0.45F + c.phase[i])) * 0.14F;   // in step with the wave
			}
		}
		float s = c.scale[i];
		FAN.translation((float) (c.x[i] - cx), (float) (c.y[i] - cy) + hop, (float) (c.z[i] - cz))
				.rotateY(c.yawRad[i])
				.scale(-s, -s, s)
				.translate(0.0F, -1.501F, 0.0F);
		FAN_N.identity().rotateY(c.yawRad[i]).scale(-1.0F, -1.0F, 1.0F);
		int far = cheer ? 1 : 0;
		int o = i * fanStride;
		for (int p = 0; p < PARTS.length; p++) {
			if (near) {
				PartPose r = rest[p];
				PART.set(FAN).translate(r.x / 16.0F, r.y / 16.0F, r.z / 16.0F).rotateZYX(ZR[p], YR[p], XR[p]);
				PART_N.set(FAN_N).rotateZYX(ZR[p], YR[p], XR[p]);
			} else {
				PART.set(FAN).mul(FAR[far][p]);
				PART_N.set(FAN_N).mul(FAR_N[far][p]);
			}
			float[] m = mesh[p];
			for (int v = 0; v < m.length; v += 8) {
				PART.transformPosition(m[v], m[v + 1], m[v + 2], POS);
				PART_N.transform(m[v + 5], m[v + 6], m[v + 7], NRM);
				vc.addVertex(POS.x, POS.y, POS.z, colour, m[v + 3], m[v + 4], OverlayTexture.NO_OVERLAY, light, NRM.x, NRM.y, NRM.z);
				fanVerts[o] = POS.x;
				fanVerts[o + 1] = POS.y;
				fanVerts[o + 2] = POS.z;
				fanVerts[o + 3] = NRM.x;
				fanVerts[o + 4] = NRM.y;
				fanVerts[o + 5] = NRM.z;
				o += 6;
			}
		}
	}

	/** A fan the skin pass already posed this frame: the same vertices again, in this pass's colour and light. */
	private static void emitCached(int i, VertexConsumer vc, int light, int colour) {
		final float[] f = fanVerts;
		int o = i * fanStride;
		for (int p = 0; p < PARTS.length; p++) {
			float[] m = mesh[p];
			for (int v = 0; v < m.length; v += 8) {
				vc.addVertex(f[o], f[o + 1], f[o + 2], colour, m[v + 3], m[v + 4], OverlayTexture.NO_OVERLAY, light,
						f[o + 3], f[o + 4], f[o + 5]);
				o += 6;
			}
		}
	}

	/**
	 * Part rotations for a fan (the kin renderer's fan pose): arms on the rail with a slow
	 * sway and a look about, or arms up and waving while the birds go by.
	 */
	private static void pose(boolean cheer, float age, float phase) {
		for (int p = 0; p < PARTS.length; p++) {
			XR[p] = rest[p].xRot;
			YR[p] = rest[p].yRot;
			ZR[p] = rest[p].zRot;
		}
		float sway = (float) Math.cos(age * 0.09F) * 0.05F + 0.05F;
		XR[CLOAK] = 0.08F + (float) Math.sin(age * 0.07F) * 0.02F;
		if (cheer) {
			float w = (float) Math.sin(age * 0.45F + phase);
			XR[ARM_R] = -2.6F + w * 0.3F;
			XR[ARM_L] = -2.6F - w * 0.3F;
			ZR[ARM_R] = -0.4F + w * 0.2F;
			ZR[ARM_L] = 0.4F - w * 0.2F;
			XR[HEAD] = -0.25F;
		} else {
			XR[ARM_R] = -0.5F;
			XR[ARM_L] = -0.5F;
			ZR[ARM_R] = sway;
			ZR[ARM_L] = -sway;
			YR[HEAD] = (float) Math.sin(age * 0.021F + phase) * 0.35F;
		}
	}

	/** Bake the kin layer once, capture each part's geometry and precompute the two far poses. */
	private static boolean ensureModel(Minecraft mc) {
		if (mesh != null) {
			return true;
		}
		ModelPart root;
		try {
			root = mc.getEntityModels().bakeLayer(KinStewardRenderer.LAYER);
		} catch (RuntimeException e) {
			return false;
		}
		float[][] captured = new float[PARTS.length][];
		PartPose[] poses = new PartPose[PARTS.length];
		PoseStack identity = new PoseStack();
		for (int p = 0; p < PARTS.length; p++) {
			ModelPart part = root.getChild(PARTS[p]);
			poses[p] = part.getInitialPose();
			part.loadPose(PartPose.ZERO);
			Capture cap = new Capture();
			part.render(identity, cap, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			part.loadPose(poses[p]);
			captured[p] = cap.toArray();
		}
		rest = poses;
		for (int f = 0; f < 2; f++) {
			pose(f == 1, 0.0F, 0.0F);
			if (f == 0) {
				YR[HEAD] = 0.0F;
			}
			for (int p = 0; p < PARTS.length; p++) {
				PartPose r = poses[p];
				FAR[f][p] = new Matrix4f().translation(r.x / 16.0F, r.y / 16.0F, r.z / 16.0F).rotateZYX(ZR[p], YR[p], XR[p]);
				FAR_N[f][p] = new Matrix3f().rotateZYX(ZR[p], YR[p], XR[p]);
			}
		}
		skinTypes = new RenderType[LOOKS.length];
		cloakTypes = new RenderType[LOOKS.length];
		glowTypes = new RenderType[LOOKS.length];
		cloakColours = new int[LOOKS.length];
		for (int lk = 0; lk < LOOKS.length; lk++) {
			skinTypes[lk] = RenderType.entityCutoutNoCull(KinStewardRenderer.skinTexture(LOOKS[lk]));
			cloakTypes[lk] = RenderType.entityCutoutNoCull(KinStewardRenderer.cloakTexture(LOOKS[lk]));
			glowTypes[lk] = KinStewardRenderer.glowType(LOOKS[lk]);
			cloakColours[lk] = 0xFF000000 | LOOKS[lk].colour();
		}
		mesh = captured;
		return true;
	}

	/** Records the vertices a model part emits (position, uv, normal), once, at start-up. */
	private static final class Capture implements VertexConsumer {
		private final List<float[]> verts = new ArrayList<>();

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
			verts.add(new float[]{x, y, z, u, v, nx, ny, nz});
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			verts.add(new float[]{x, y, z, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F});
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			if (!verts.isEmpty()) {
				float[] last = verts.get(verts.size() - 1);
				last[3] = u;
				last[4] = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float nx, float ny, float nz) {
			if (!verts.isEmpty()) {
				float[] last = verts.get(verts.size() - 1);
				last[5] = nx;
				last[6] = ny;
				last[7] = nz;
			}
			return this;
		}

		float[] toArray() {
			float[] out = new float[verts.size() * 8];
			for (int i = 0; i < verts.size(); i++) {
				System.arraycopy(verts.get(i), 0, out, i * 8, 8);
			}
			return out;
		}
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
