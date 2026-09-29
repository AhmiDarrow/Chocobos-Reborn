package tk.darrow.chocobosreborn;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import tk.darrow.chocobosreborn.block.ModBlocks;
import tk.darrow.chocobosreborn.command.ChocobosRebornCommand;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.item.ModCreativeTabs;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.loot.ModLootModifiers;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.sound.ModSounds;

@Mod(ChocobosReborn.MOD_ID)
public final class ChocobosReborn {
	public static final String MOD_ID = "chocobosreborn";
	public static final Logger LOGGER = LogUtils.getLogger();

	public ChocobosReborn(IEventBus modBus) {
		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModEntities.ENTITIES.register(modBus);
		ModSounds.SOUNDS.register(modBus);
		ModCreativeTabs.TABS.register(modBus);
		tk.darrow.chocobosreborn.menu.ModMenus.MENUS.register(modBus);
		ModLootModifiers.SERIALIZERS.register(modBus);
		modBus.addListener(ModEntities::attributes);
		modBus.addListener(ModEntities::placements);
		modBus.addListener(this::setup);
		modBus.addListener(this::gameTests);
		modBus.addListener(this::payloads);
		NeoForge.EVENT_BUS.register(RaceManager.class);
		NeoForge.EVENT_BUS.addListener(tk.darrow.chocobosreborn.race.FollowAcross::onChanged);
		NeoForge.EVENT_BUS.addListener(ChocobosRebornCommand::register);
	}

	/**
	 * The published jar leaves the tests out, but NeoForge still fires this in any development
	 * environment (another mod's workspace, a pack dev's run), so look the class up by name.
	 */
	private void gameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
		try {
			event.register(Class.forName("tk.darrow.chocobosreborn.gametest.ChocobosRebornGameTests"));
		} catch (ClassNotFoundException stripped) {
			// A player jar: nothing to register.
		}
	}

	private void payloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
		var reg = event.registrar("1");
		reg.playToClient(tk.darrow.chocobosreborn.net.AlmanacPayload.TYPE,
				tk.darrow.chocobosreborn.net.AlmanacPayload.CODEC, tk.darrow.chocobosreborn.net.AlmanacPayload::handle);
		reg.playToClient(tk.darrow.chocobosreborn.net.RacePayloads.OpenCourseSelect.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.OpenCourseSelect.CODEC,
				tk.darrow.chocobosreborn.net.RacePayloads.OpenCourseSelect::handle);
		reg.playToServer(tk.darrow.chocobosreborn.net.RacePayloads.CourseChoice.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.CourseChoice.CODEC,
				tk.darrow.chocobosreborn.net.RacePayloads.CourseChoice::handle);
		reg.playToServer(tk.darrow.chocobosreborn.net.RacePayloads.ReleaseBird.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.ReleaseBird.CODEC,
				tk.darrow.chocobosreborn.net.RacePayloads.ReleaseBird::handle);
		reg.playToServer(tk.darrow.chocobosreborn.net.RacePayloads.RenameBird.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.RenameBird.CODEC,
				tk.darrow.chocobosreborn.net.RacePayloads.RenameBird::handle);
		// Mount-bound input changes the wire format; require matching rider support.
		event.registrar("2").playToServer(tk.darrow.chocobosreborn.net.RacePayloads.RiderDash.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.RiderDash.CODEC,
				tk.darrow.chocobosreborn.net.RacePayloads.RiderDash::handle);
        var riding = event.registrar("3");
        riding.playToClient(tk.darrow.chocobosreborn.net.RiderPayloads.Hud.TYPE,
                tk.darrow.chocobosreborn.net.RiderPayloads.Hud.CODEC, tk.darrow.chocobosreborn.net.RiderPayloads.Hud::handle);
        riding.playToServer(tk.darrow.chocobosreborn.net.RiderPayloads.Input.TYPE,
                tk.darrow.chocobosreborn.net.RiderPayloads.Input.CODEC, tk.darrow.chocobosreborn.net.RiderPayloads.Input::handle);
        riding.playToClient(tk.darrow.chocobosreborn.net.RiderPayloads.Snapshot.TYPE,
                tk.darrow.chocobosreborn.net.RiderPayloads.Snapshot.CODEC, tk.darrow.chocobosreborn.net.RiderPayloads.Snapshot::handle);
		// Race movement: acknowledged teleports of a rider's bird, and tick-stamped frames of the field.
		var movement = event.registrar("4");
		movement.playToClient(tk.darrow.chocobosreborn.net.RaceMovePayloads.Teleport.TYPE,
				tk.darrow.chocobosreborn.net.RaceMovePayloads.Teleport.CODEC, tk.darrow.chocobosreborn.net.RaceMovePayloads.Teleport::handle);
		movement.playToServer(tk.darrow.chocobosreborn.net.RaceMovePayloads.TeleportAck.TYPE,
				tk.darrow.chocobosreborn.net.RaceMovePayloads.TeleportAck.CODEC, tk.darrow.chocobosreborn.net.RaceMovePayloads.TeleportAck::handle);
		event.registrar("4").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK)
				.playToClient(tk.darrow.chocobosreborn.net.RaceMovePayloads.Frame.TYPE,
						tk.darrow.chocobosreborn.net.RaceMovePayloads.Frame.CODEC, tk.darrow.chocobosreborn.net.RaceMovePayloads.Frame::handle);
		var probes = event.registrar("2").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK);
		probes.playToClient(tk.darrow.chocobosreborn.net.RacePayloads.LatencyProbe.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.LatencyProbe.CODEC, tk.darrow.chocobosreborn.net.RacePayloads.LatencyProbe::handle);
		probes.playToServer(tk.darrow.chocobosreborn.net.RacePayloads.LatencyReply.TYPE,
				tk.darrow.chocobosreborn.net.RacePayloads.LatencyReply.CODEC, tk.darrow.chocobosreborn.net.RacePayloads.LatencyReply::handle);
	}

	private void setup(FMLCommonSetupEvent event) {
		event.enqueueWork(tk.darrow.chocobosreborn.item.ChocoboSpawnEggItem::registerDispensers);
		LOGGER.info("Chocobos Reborn Village — chocobos ready.");
	}
}
