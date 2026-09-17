package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.item.ModItems;

/**
 * The duel master's book: open challenges (course + optional GP stake) waiting
 * for a second rider. A challenger's stake is taken when the challenge is
 * posted and returned if it is withdrawn or expires.
 */
public final class DuelDesk {
	public record Challenge(UUID challenger, String name, RaceTrack track, int stake, long postedTick) {
	}

	private static final Map<UUID, Challenge> OPEN = new LinkedHashMap<>();
	private static final long EXPIRY_TICKS = 20L * 60L * 5L;

	private DuelDesk() {
	}

	public static void clear() {
		OPEN.clear();
	}

	@Nullable
	public static Challenge mine(UUID player) {
		return OPEN.get(player);
	}

	/** Challenges from other players, oldest first. */
	public static List<Challenge> others(UUID player) {
		List<Challenge> out = new ArrayList<>();
		for (Challenge c : OPEN.values()) {
			if (!c.challenger().equals(player)) {
				out.add(c);
			}
		}
		return out;
	}

	/** Post a challenge; takes the stake from the challenger's GP. */
	public static boolean post(ServerPlayer player, RaceTrack track, int stake) {
		if (OPEN.containsKey(player.getUUID())) {
			withdraw(player);
		}
		if (stake > 0 && !takeGp(player, stake)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.duel.no_gp", stake), true);
			return false;
		}
		OPEN.put(player.getUUID(), new Challenge(player.getUUID(), player.getName().getString(), track, stake,
				player.level().getGameTime()));
		player.displayClientMessage(Component.translatable("chocobosreborn.duel.posted",
				Component.translatable("chocobosreborn.track." + track.id()), stake), false);
		return true;
	}

	public static void withdraw(ServerPlayer player) {
		Challenge c = OPEN.remove(player.getUUID());
		if (c != null && c.stake() > 0) {
			giveGp(player, c.stake());
			player.displayClientMessage(Component.translatable("chocobosreborn.duel.withdrawn", c.stake()), true);
		}
	}

	/** Drop challenges whose poster left or that sat too long. */
	public static void tick(ServerLevel level) {
		if (OPEN.isEmpty() || level.getGameTime() % 100 != 0) {
			return;
		}
		for (Challenge c : new ArrayList<>(OPEN.values())) {
			ServerPlayer poster = level.getServer().getPlayerList().getPlayer(c.challenger());
			boolean gone = poster == null || !Square.isSquare(poster.level());
			if (gone || level.getGameTime() - c.postedTick() > EXPIRY_TICKS) {
				OPEN.remove(c.challenger());
				if (poster != null && c.stake() > 0) {
					giveGp(poster, c.stake());
					poster.displayClientMessage(Component.translatable("chocobosreborn.duel.withdrawn", c.stake()), true);
				}
			}
		}
	}

	/** A second rider accepts the oldest open challenge: takes their stake and starts the heat. */
	public static boolean accept(ServerPlayer acceptor, ChocoboEntity bird) {
		List<Challenge> open = others(acceptor.getUUID());
		if (open.isEmpty()) {
			return false;
		}
		Challenge c = open.get(0);
		ServerPlayer challenger = acceptor.server.getPlayerList().getPlayer(c.challenger());
		if (challenger == null || !(challenger.getVehicle() instanceof ChocoboEntity theirs)
				|| !Square.isSquare(challenger.level())) {
			acceptor.displayClientMessage(Component.translatable("chocobosreborn.duel.challenger_away", c.name()), true);
			return false;
		}
		if (c.stake() > 0 && !takeGp(acceptor, c.stake())) {
			acceptor.displayClientMessage(Component.translatable("chocobosreborn.duel.no_gp", c.stake()), true);
			return false;
		}
		OPEN.remove(c.challenger());
		if (!RaceManager.startDuel(challenger, theirs, acceptor, bird, c.track(), c.stake())) {
			// could not start: everyone gets their GP back
			if (c.stake() > 0) {
				giveGp(challenger, c.stake());
				giveGp(acceptor, c.stake());
			}
			return false;
		}
		return true;
	}

	static boolean takeGp(ServerPlayer player, int amount) {
		int have = 0;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.GP.get())) {
				have += s.getCount();
			}
		}
		if (have < amount) {
			return false;
		}
		int left = amount;
		for (ItemStack s : player.getInventory().items) {
			if (left > 0 && s.is(ModItems.GP.get())) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		return true;
	}

	static void giveGp(ServerPlayer player, int amount) {
		for (int n : RaceCurrency.stacks(amount, 64)) {
			ItemStack st = new ItemStack(ModItems.GP.get(), n);
			if (!player.getInventory().add(st)) {
				player.drop(st, false);
			}
		}
	}
}
