package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import tk.darrow.chocobosreborn.race.ChocoboWhistle;
import tk.darrow.chocobosreborn.sound.CallRotation;
import tk.darrow.chocobosreborn.sound.ModSounds;

/**
 * Chocobo Whistle: calls the player's own tame chocobos to them, from any
 * distance and any dimension. Stay and Wander answer too. A bird already
 * in a heat, on a lead, or under another rider does not.
 */
public class ChocoboWhistleItem extends Item {
	private static final int COOLDOWN = 60;
	/** When no bird answered: long enough that holding the button cannot drone the whistle. */
	private static final int BREATH = 20;
	private static final CallRotation CALLS = new CallRotation();

	public ChocoboWhistleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (sp.isSpectator() || !sp.isAlive()) {
			return InteractionResultHolder.fail(stack);
		}
		// The whistle sounds whether or not a bird hears it; each blow is the next call in turn.
		var call = ModSounds.WHISTLE_CALLS.get(CALLS.next(sp.getUUID(), ModSounds.WHISTLE_CALLS.size()));
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), call.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
		sp.getCooldowns().addCooldown(this, ChocoboWhistle.blow(sp) ? COOLDOWN : BREATH);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.whistle").withStyle(ChatFormatting.GRAY));
	}
}
