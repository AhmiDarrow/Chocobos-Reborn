package tk.darrow.chocobosreborn.client;

import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ModEntities;

@Mod(value = ChocobosReborn.MOD_ID, dist = Dist.CLIENT)
public final class ChocobosRebornClient {
	public ChocobosRebornClient(IEventBus modBus) {
		modBus.addListener(this::layers);
		modBus.addListener(this::renderers);
		modBus.addListener(this::screens);
		modBus.addListener(SquareSky::registerShaders);
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) ->
				event.register(tk.darrow.chocobosreborn.race.Square.DIMENSION.location(), new SquareSky()));
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RaceMusic::onClientTick);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RaceMusic::onPlaySound);
		// parse the plain and saddled birds while the world loads, not as the first one (often a
		// whole saddled race field) comes into view
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
				(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) -> {
					WhiskerMesh.get("chocobo");
					WhiskerMesh.get("chocobo_saddled");
				});
		tk.darrow.chocobosreborn.net.AlmanacPayload.CLIENT_OPENER = data -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.screen instanceof AlmanacScreen open) {
				open.reload(data);
			} else {
				mc.setScreen(new AlmanacScreen(data));
			}
		};
		tk.darrow.chocobosreborn.net.RacePayloads.OpenCourseSelect.CLIENT_OPENER = CourseSelectScreen::open;
		tk.darrow.chocobosreborn.entity.ChocoboEntity.LOCAL_RIDER = bird -> {
			var p = net.minecraft.client.Minecraft.getInstance().player;
			return p != null && bird.getControllingPassenger() == p && !p.isShiftKeyDown();
		};
	}

	private void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(KinStewardRenderer.LAYER, ChocobosRebornClient::kinLayer);
	}

	private void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
		event.register(tk.darrow.chocobosreborn.menu.ModMenus.CHOCOBO.get(), ChocoboInventoryScreen::new);
	}

	private void renderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntities.CHOCOBO.get(), ChocoboMeshRenderer::new);
		event.registerEntityRenderer(ModEntities.KIN_STEWARD.get(), KinStewardRenderer::new);
	}

	/** Tribal Power kin geometry (roster "tribal_kin", 256x256 atlas): skull, mask plate, hood, two braids, torso, belt, cloak, limbs. */
	private static LayerDefinition kinLayer() {
		var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
		var root = mesh.getRoot();
		root.addOrReplaceChild("head", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
				.texOffs(64, 0).addBox(-4.5F, -8.5F, -5.0F, 9.0F, 9.0F, 1.0F)
				.texOffs(128, 0).addBox(-4.5F, -9.0F, -4.5F, 9.0F, 5.0F, 9.0F)
				.texOffs(192, 0).addBox(-4.5F, -2.0F, 3.5F, 1.0F, 6.0F, 1.0F)
				.texOffs(0, 32).addBox(3.5F, -2.0F, 3.5F, 1.0F, 6.0F, 1.0F),
				net.minecraft.client.model.geom.PartPose.offset(0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("body", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(64, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
				.texOffs(128, 32).addBox(-4.5F, 7.0F, -2.5F, 9.0F, 2.0F, 5.0F),
				net.minecraft.client.model.geom.PartPose.offset(0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("cloak", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(192, 32).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 14.0F, 2.0F),
				net.minecraft.client.model.geom.PartPose.offset(0.0F, 0.0F, 2.2F));
		root.addOrReplaceChild("arm0", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(0, 64).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				net.minecraft.client.model.geom.PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("arm1", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(64, 64).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				net.minecraft.client.model.geom.PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("leg0", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(128, 64).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				net.minecraft.client.model.geom.PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("leg1", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
				.texOffs(192, 64).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				net.minecraft.client.model.geom.PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}
}
