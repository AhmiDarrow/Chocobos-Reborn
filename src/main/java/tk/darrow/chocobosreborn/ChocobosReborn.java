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
		NeoForge.EVENT_BUS.addListener(ChocobosRebornCommand::register);
	}

	private void gameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
		event.register(tk.darrow.chocobosreborn.gametest.ChocobosRebornGameTests.class);
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
	}

	private void setup(FMLCommonSetupEvent event) {
		LOGGER.info("Chocobos Reborn Village — chocobos ready.");
	}
}
