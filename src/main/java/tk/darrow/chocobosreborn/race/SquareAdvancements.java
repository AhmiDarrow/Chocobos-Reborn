package tk.darrow.chocobosreborn.race;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import tk.darrow.chocobosreborn.ChocobosReborn;

/** Awards the datapack advancements that use {@code minecraft:impossible} triggers. */
public final class SquareAdvancements {
	public static final ResourceLocation SQUARE = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "square");
	public static final ResourceLocation FIRST_PLACE = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "first_place");
	public static final ResourceLocation CLASS_S = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "class_s");

	private SquareAdvancements() {
	}

	public static void award(ServerPlayer player, ResourceLocation id) {
		if (player == null || player.server == null) {
			return;
		}
		AdvancementHolder adv = player.server.getAdvancements().get(id);
		if (adv == null) {
			return;
		}
		if (player.getAdvancements().getOrStartProgress(adv).isDone()) {
			return;
		}
		for (String criterion : adv.value().criteria().keySet()) {
			player.getAdvancements().award(adv, criterion);
		}
	}
}
