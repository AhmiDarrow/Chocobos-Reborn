package tk.darrow.chocobosreborn.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/** Chocobo armour on the vanilla tiers. Worn in the bird's armour slot; an armoured bird cannot race. */
public class ChocoboArmorItem extends Item {
	public enum Tier {
		LEATHER(3, null), IRON(5, "iron"), DIAMOND(11, "diamond"), NETHERITE(13, "diamond");

		private final int armor;
		@Nullable private final String mesh;

		Tier(int armor, @Nullable String mesh) {
			this.armor = armor;
			this.mesh = mesh;
		}

		public int armor() {
			return armor;
		}

		/** Mesh variant suffix (chocobo_armor_&lt;mesh&gt;) or null for no visual. */
		@Nullable
		public String mesh() {
			return mesh;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Tier byId(int id) {
			Tier[] v = values();
			return id < 0 || id >= v.length ? LEATHER : v[id];
		}
	}

	private final Tier tier;

	public ChocoboArmorItem(Tier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.armor", tier.armor()).withStyle(ChatFormatting.BLUE));
		tip.add(Component.translatable("chocobosreborn.tip.armor2").withStyle(ChatFormatting.DARK_GRAY));
	}
}
