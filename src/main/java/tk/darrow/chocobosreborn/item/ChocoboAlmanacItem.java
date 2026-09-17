package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.ledger.BirdRecord;
import tk.darrow.chocobosreborn.ledger.ChocoboLedger;
import tk.darrow.chocobosreborn.net.AlmanacPayload;

/**
 * The Chocobo Almanac: the mod's book. Use it anywhere to open the guide and
 * the player's own stable (stats, race record, genes, family line).
 */
public class ChocoboAlmanacItem extends Item {
	public ChocoboAlmanacItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			ChocoboLedger ledger = ChocoboLedger.get(server);
			// loaded birds first, so the page shows what the player just fed
			for (ChocoboEntity bird : server.getEntitiesOfClass(ChocoboEntity.class,
					sp.getBoundingBox().inflate(128.0D), b -> b.isTame() && sp.getUUID().equals(b.getOwnerUUID()))) {
				ledger.update(bird);
			}
			CompoundTag data = new CompoundTag();
			ListTag list = new ListTag();
			for (BirdRecord r : ledger.forOwner(sp.getUUID())) {
				list.add(r.save());
			}
			data.put("Birds", list);
			data.putUUID("Player", sp.getUUID());
			PacketDistributor.sendToPlayer(sp, new AlmanacPayload(data));
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.almanac").withStyle(ChatFormatting.GRAY));
	}
}
