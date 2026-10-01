package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.KinStewardEntity;
import tk.darrow.chocobosreborn.race.TownRole;

/**
 * Tribal Power kin: the masked, hooded, cloaked humanoid from Tribal Power's
 * Blender roster (geometry in {@link ChocobosRebornClient#kinLayer}, 256x256 skins
 * per kin role) plus its eyes-glow layer and a tribe cloak overlay tinted with the
 * tribe colour. Class C named rivals use classic 64x64 player skins on a player model.
 */
public class KinStewardRenderer extends MobRenderer<KinStewardEntity, KinStewardRenderer.Model> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "kin_steward"), "main");
	private static final ResourceLocation[] SKINS = new ResourceLocation[TownRole.values().length];
	private static final RenderType[] GLOW = new RenderType[TownRole.values().length];
	private static final ResourceLocation[] CLOAKS = new ResourceLocation[TownRole.values().length];

	static {
		for (TownRole role : TownRole.values()) {
			int i = role.ordinal();
			if (role.playerJockey()) {
				SKINS[i] = jockeyTex(role.skin());
				GLOW[i] = null;
				CLOAKS[i] = SKINS[i];
			} else {
				SKINS[i] = kinTex("kin_" + role.skin());
				GLOW[i] = RenderType.eyes(kinTex("kin_" + role.skin() + "_glow"));
				CLOAKS[i] = kinTex("kin_cloak_" + role.tribe());
			}
		}
	}

	/** Skin of a kin role (also drawn by {@link CourseCrowdRenderer} for the crowd in the stands). */
	public static ResourceLocation skinTexture(TownRole role) {
		return SKINS[role.ordinal()];
	}

	/** Tribe cloak overlay of a kin role, tinted with {@link TownRole#colour()}. */
	public static ResourceLocation cloakTexture(TownRole role) {
		return CLOAKS[role.ordinal()];
	}

	/** Eyes-layer glow of a kin role; {@code null} for player-skin jockeys. */
	public static RenderType glowType(TownRole role) {
		return GLOW[role.ordinal()];
	}

	private static ResourceLocation kinTex(String name) {
		return ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/entity/kin/" + name + ".png");
	}

	private static ResourceLocation jockeyTex(String name) {
		return ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/entity/jockey/" + name + ".png");
	}

	private final PlayerModel<KinStewardEntity> playerModel;

	public KinStewardRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.4F);
		this.playerModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
		addLayer(new GlowLayer(this));
		addLayer(new CloakLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(KinStewardEntity entity) {
		return SKINS[Math.min(entity.role().ordinal(), SKINS.length - 1)];
	}

	@Override
	public void render(KinStewardEntity entity, float entityYaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffer, int packedLight) {
		if (entity.role().playerJockey()) {
			renderPlayerJockey(entity, entityYaw, partialTicks, pose, buffer, packedLight);
			return;
		}
		super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
	}

	/** Classic Steve-model jockey for Ahmi / Risika (64x64 skins under textures/entity/jockey/). */
	private void renderPlayerJockey(KinStewardEntity entity, float entityYaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffer, int packedLight) {
		pose.pushPose();
		float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		float netHead = headYaw - bodyYaw;
		float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
		float limbSwing = entity.walkAnimation.position(partialTicks);
		float limbAmount = entity.walkAnimation.speed(partialTicks);
		float age = entity.tickCount + partialTicks;

		playerModel.young = entity.isBaby();
		playerModel.riding = entity.isPassenger();
		playerModel.crouching = false;
		playerModel.setupAnim(entity, limbSwing, limbAmount, age, netHead, headPitch);
		if (entity.isPassenger()) {
			// hands on the reins, matching the kin saddle pose
			playerModel.rightArm.xRot = -0.85F;
			playerModel.leftArm.xRot = -0.85F;
			playerModel.rightArm.zRot = 0.1F;
			playerModel.leftArm.zRot = -0.1F;
		}

		pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.translate(0.0F, -1.501F, 0.0F);

		ResourceLocation tex = getTextureLocation(entity);
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
		playerModel.renderToBuffer(pose, consumer, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0), 0xFFFFFFFF);
		pose.popPose();

		if (this.shouldShowName(entity)) {
			this.renderNameTag(entity, entity.getDisplayName(), pose, buffer, packedLight, partialTicks);
		}
	}

	/** Eyes-layer glow from the thread marks on each kin skin. */
	public static final class GlowLayer extends RenderLayer<KinStewardEntity, Model> {
		public GlowLayer(KinStewardRenderer parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffer, int light, KinStewardEntity entity, float limbSwing,
				float limbAmount, float partial, float age, float yaw, float pitch) {
			if (entity.isInvisible() || entity.role().playerJockey()) {
				return;
			}
			RenderType glow = GLOW[Math.min(entity.role().ordinal(), GLOW.length - 1)];
			if (glow == null) {
				return;
			}
			VertexConsumer consumer = buffer.getBuffer(glow);
			getParentModel().renderToBuffer(pose, consumer, 15728880, LivingEntityRenderer.getOverlayCoords(entity, 0), 0xFFFFFFFF);
		}
	}

	/** The same model again with the tribe cloak overlay, coloured by the tribe. */
	public static final class CloakLayer extends RenderLayer<KinStewardEntity, Model> {
		public CloakLayer(KinStewardRenderer parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffer, int light, KinStewardEntity entity, float limbSwing,
				float limbAmount, float partial, float age, float yaw, float pitch) {
			if (entity.isInvisible() || entity.role().playerJockey()) {
				return;
			}
			TownRole role = entity.role();
			VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(CLOAKS[Math.min(role.ordinal(), CLOAKS.length - 1)]));
			int colour = 0xFF000000 | role.colour();
			getParentModel().renderToBuffer(pose, consumer, light, LivingEntityRenderer.getOverlayCoords(entity, 0), colour);
		}
	}

	public static class Model extends HierarchicalModel<KinStewardEntity> {
		private final ModelPart root;
		private final ModelPart head;
		private final ModelPart body;
		private final ModelPart cloak;
		private final ModelPart rightArm;
		private final ModelPart leftArm;
		private final ModelPart rightLeg;
		private final ModelPart leftLeg;
		/** Every part, flattened once: getAllParts() builds a tree of streams on each call (per kin per frame). */
		private final ModelPart[] allParts;

		public Model(ModelPart root) {
			this.root = root;
			this.head = root.getChild("head");
			this.body = root.getChild("body");
			this.cloak = root.getChild("cloak");
			this.rightArm = root.getChild("arm0");
			this.leftArm = root.getChild("arm1");
			this.rightLeg = root.getChild("leg0");
			this.leftLeg = root.getChild("leg1");
			this.allParts = root.getAllParts().toArray(ModelPart[]::new);
		}

		@Override
		public ModelPart root() {
			return root;
		}

		@Override
		public void setupAnim(KinStewardEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
			for (ModelPart part : allParts) {
				part.resetPose();
			}
			head.yRot = yaw * Mth.DEG_TO_RAD;
			head.xRot = pitch * Mth.DEG_TO_RAD;
			float swing = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbAmount;
			rightLeg.xRot = swing;
			leftLeg.xRot = -swing;
			rightArm.xRot = -swing * 0.8F;
			leftArm.xRot = swing * 0.8F;
			rightArm.zRot += Mth.cos(age * 0.09F) * 0.05F + 0.05F;
			leftArm.zRot -= Mth.cos(age * 0.09F) * 0.05F + 0.05F;
			body.y = 0;
			cloak.xRot = 0.08F + limbAmount * 0.35F + Mth.sin(age * 0.07F) * 0.02F;
			if (entity.isPassenger()) {
				// in the saddle: legs forward and apart round the bird, hands on the reins, cloak trailing
				rightLeg.xRot = -1.45F;
				leftLeg.xRot = -1.45F;
				rightLeg.zRot = -0.3F;
				leftLeg.zRot = 0.3F;
				rightArm.xRot = -0.85F;
				leftArm.xRot = -0.85F;
				rightArm.zRot = 0.1F;
				leftArm.zRot = -0.1F;
				cloak.xRot = 0.7F;
				return;
			}
			if (entity.role().resident() && entity.cheering()) {
				// a resident at the overlook rail while a heat is on: arms up, bouncing
				float w = Mth.sin(age * 0.45F + entity.getId());
				rightArm.xRot = -2.6F + w * 0.3F;
				leftArm.xRot = -2.6F - w * 0.3F;
				body.y = -Math.abs(w) * 1.5F;
				return;
			}
			switch (entity.role()) {
				case STEWARD -> rightArm.xRot -= 0.35F;                     // elder leaning on a staff
				case BOOKIE -> {                                            // tallying: hands up in front
					rightArm.xRot = -0.9F + Mth.sin(age * 0.2F) * 0.1F;
					leftArm.xRot = -0.7F;
				}
				case TACK, TREATS -> {                                      // working the stall
					rightArm.xRot = -0.6F + Mth.sin(age * 0.25F) * 0.25F;
					leftArm.xRot = -0.6F - Mth.sin(age * 0.25F) * 0.25F;
				}
				case FAN_SWARM, FAN_CLOCK, FAN_SPROUT, FAN_CLAW -> {
					boolean heat = entity.cheering();
					if (heat) {                                             // arms up, waving
						float w = Mth.sin(age * 0.45F + entity.getId());
						rightArm.xRot = -2.6F + w * 0.3F;
						leftArm.xRot = -2.6F - w * 0.3F;
						rightArm.zRot = -0.4F + w * 0.2F;
						leftArm.zRot = 0.4F - w * 0.2F;
						body.y = -Math.abs(w) * 1.5F;
					} else {                                                // leaning on the rail
						rightArm.xRot = -0.5F;
						leftArm.xRot = -0.5F;
					}
				}
				default -> {
				}
			}
		}
	}
}
