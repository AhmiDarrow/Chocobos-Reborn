package tk.darrow.chocobosreborn.race;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.ledger.ChocoboLedger;

/**
 * The broker's book. Ride an owned bird up and click: it is offered. Sneak-click
 * to offer it as a gift. A second player who offers a bird swaps with the oldest
 * other offer; a second player on foot can take a gifted bird.
 */
public final class TradeDesk {
	public record Offer(UUID owner, String name, UUID bird, boolean gift, long tick) {
	}

	private static final Map<UUID, Offer> OFFERS = new LinkedHashMap<>();
	private static final long EXPIRY_TICKS = 20L * 60L * 5L;

	private TradeDesk() {
	}

	public static void clear() {
		OFFERS.clear();
	}

	public static void tick(ServerLevel level) {
		if (OFFERS.isEmpty() || level.getGameTime() % 100 != 0) {
			return;
		}
		OFFERS.values().removeIf(o -> level.getGameTime() - o.tick() > EXPIRY_TICKS
				|| level.getServer().getPlayerList().getPlayer(o.owner()) == null);
	}

	@Nullable
	private static Offer oldestOther(UUID player, boolean giftOnly) {
		for (Offer o : OFFERS.values()) {
			if (!o.owner().equals(player) && (!giftOnly || o.gift())) {
				return o;
			}
		}
		return null;
	}

	/** Player interacted with the broker. */
	public static void interact(ServerPlayer player, boolean sneaking) {
		ServerLevel level = player.serverLevel();
		ChocoboEntity mine = player.getVehicle() instanceof ChocoboEntity b && b.isOwnedBy(player) && !b.racing() ? b : null;
		if (mine == null) {
			Offer gift = oldestOther(player.getUUID(), true);
			if (gift == null) {
				player.displayClientMessage(Component.translatable("chocobosreborn.trade.hint"), true);
				return;
			}
			ChocoboEntity bird = level.getEntity(gift.bird()) instanceof ChocoboEntity c ? c : null;
			if (bird == null) {
				OFFERS.remove(gift.owner());
				player.displayClientMessage(Component.translatable("chocobosreborn.trade.gone"), true);
				return;
			}
			OFFERS.remove(gift.owner());
			transfer(level, bird, player);
			player.displayClientMessage(Component.translatable("chocobosreborn.trade.received", gift.name()), false);
			ServerPlayer giver = level.getServer().getPlayerList().getPlayer(gift.owner());
			if (giver != null) {
				giver.displayClientMessage(Component.translatable("chocobosreborn.trade.gave", player.getName().getString()), false);
			}
			return;
		}
		Offer other = oldestOther(player.getUUID(), false);
		if (other != null) {
			ChocoboEntity theirs = level.getEntity(other.bird()) instanceof ChocoboEntity c ? c : null;
			ServerPlayer them = level.getServer().getPlayerList().getPlayer(other.owner());
			if (theirs == null || them == null) {
				OFFERS.remove(other.owner());
				player.displayClientMessage(Component.translatable("chocobosreborn.trade.gone"), true);
				return;
			}
			OFFERS.remove(other.owner());
			OFFERS.remove(player.getUUID());
			player.stopRiding();
			if (them.getVehicle() == theirs) {
				them.stopRiding();
			}
			transfer(level, mine, them);
			transfer(level, theirs, player);
			player.displayClientMessage(Component.translatable("chocobosreborn.trade.swapped", other.name()), false);
			them.displayClientMessage(Component.translatable("chocobosreborn.trade.swapped", player.getName().getString()), false);
			return;
		}
		OFFERS.put(player.getUUID(), new Offer(player.getUUID(), player.getName().getString(), mine.getUUID(), sneaking,
				level.getGameTime()));
		player.displayClientMessage(Component.translatable(sneaking ? "chocobosreborn.trade.offered_gift" : "chocobosreborn.trade.offered"), false);
	}

	private static void transfer(ServerLevel level, ChocoboEntity bird, ServerPlayer to) {
		bird.setOwnerUUID(to.getUUID());
		bird.setTame(true, false);
		bird.setOrderedToSit(false);
		ChocoboLedger.get(level).update(bird);
	}
}
