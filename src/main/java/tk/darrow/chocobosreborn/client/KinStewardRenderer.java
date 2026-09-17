package tk.darrow.chocobosreborn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
 * tribe colour. Same art as Tribal Power so the kin read as one people across both mods.
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
			SKINS[i] = tex("kin_" + role.skin());
			GLOW[i] = RenderType.eyes(tex("kin_" + role.skin() + "_glow"));
			CLOAKS[i] = tex("kin_cloak_" + role.tribe());
		}
	}

	private static ResourceLocation tex(String name) {
		return ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "textures/entity/kin/" + name + ".png");
	}

	public KinStewardRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.4F);
		addLayer(new GlowLayer(this));
		addLayer(new CloakLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(KinStewardEntity entity) {
		return SKINS[Math.min(entity.role().ordinal(), SKINS.length - 1)];
	}

	/** Eyes-layer glow from the thread marks on each kin skin. */
	public static final class GlowLayer extends RenderLayer<KinStewardEntity, Model> {
		public GlowLayer(KinStewardRenderer parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffer, int light, KinStewardEntity entity, float limbSwing,
				float limbAmount, float partial, float age, float yaw, float pitch) {
			if (entity.isInvisible()) {
				return;
			}
			VertexConsumer consumer = buffer.getBuffer(GLOW[Math.min(entity.role().ordinal(), GLOW.length - 1)]);
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
			if (entity.isInvisible()) {
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

		public Model(ModelPart root) {
			this.root = root;
			this.head = root.getChild("head");
			this.body = root.getChild("body");
			this.cloak = root.getChild("cloak");
			this.rightArm = root.getChild("arm0");
			this.leftArm = root.getChild("arm1");
			this.rightLeg = root.getChild("leg0");
			this.leftLeg = root.getChild("leg1");
		}

		@Override
		public ModelPart root() {
			return root;
		}

		@Override
		public void setupAnim(KinStewardEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
			root.getAllParts().forEach(ModelPart::resetPose);
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
