package tk.darrow.chocobosreborn.race;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** Big on-screen titles for the heat flow (countdown, go, transport). */
public final class Titles {
	private Titles() {
	}

	public static void show(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** A one-second countdown number. */
	public static void count(ServerPlayer player, int seconds) {
		show(player, Component.literal(String.valueOf(seconds)).withStyle(net.minecraft.ChatFormatting.GOLD),
				Component.empty(), 0, 18, 4);
	}
}
