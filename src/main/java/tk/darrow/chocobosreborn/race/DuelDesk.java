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

	/** A posted stake is written to {@link SquareData} so a crash hands it back (as owed GP) on the next load. */
	private static void hold(net.minecraft.server.MinecraftServer server, UUID player, int amount) {
		ServerLevel square = Square.level(server);
		if (square != null) {
			SquareData.get(square).holdStake(player, amount);
		}
	}

	/** Take a challenge off the book and drop its stake from the saved record; the caller settles the GP. */
	@Nullable
	private static Challenge remove(net.minecraft.server.MinecraftServer server, UUID player) {
		Challenge c = OPEN.remove(player);
		if (c != null && c.stake() > 0) {
			ServerLevel square = Square.level(server);
			if (square != null) {
				SquareData.get(square).releaseStake(player, c.stake());
			}
		}
		return c;
	}

	/** Server stop: give back stakes still sitting in the book. */
	public static void refundAndClear(net.minecraft.server.MinecraftServer server) {
		for (Challenge c : new ArrayList<>(OPEN.values())) {
			remove(server, c.challenger());
			if (c.stake() > 0) {
				ServerPlayer p = server.getPlayerList().getPlayer(c.challenger());
				if (p != null) {
					giveGp(p, c.stake());
				} else {
					RaceManager.oweGp(server, c.challenger(), c.stake());
				}
			}
		}
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
		if (HeatSchedule.entered(player.getUUID()) || RaceManager.sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.wait"), true);
			return false;
		}
		int posted = 0;
		Challenge prev = OPEN.get(player.getUUID());
		if (prev != null) {
			posted = prev.stake();
		}
		if (stake > 0 && countGp(player) + posted < stake) {
			player.displayClientMessage(Component.translatable("chocobosreborn.duel.no_gp", stake), true);
			return false;
		}
		if (prev != null) {
			remove(player.server, player.getUUID());
		}
		int extra = stake - posted;
		if (extra > 0) {
			takeGp(player, extra);
		} else if (extra < 0) {
			giveGp(player, -extra);
		}
		OPEN.put(player.getUUID(), new Challenge(player.getUUID(), player.getName().getString(), track, stake,
				player.level().getGameTime()));
		hold(player.server, player.getUUID(), stake);
		player.displayClientMessage(Component.translatable("chocobosreborn.duel.posted",
				Component.translatable("chocobosreborn.track." + track.id()), stake), false);
		return true;
	}

	public static void withdraw(ServerPlayer player) {
		Challenge c = remove(player.server, player.getUUID());
		if (c != null && c.stake() > 0) {
			giveGp(player, c.stake());
			player.displayClientMessage(Component.translatable("chocobosreborn.duel.withdrawn", c.stake()), true);
		}
	}

	/** Take a posted challenge off the book without returning the GP: it is now the duel pot (the session holds it). */
	public static void consume(net.minecraft.server.MinecraftServer server, UUID player) {
		remove(server, player);
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
				remove(level.getServer(), c.challenger());
				if (c.stake() > 0) {
					if (poster != null) {
						giveGp(poster, c.stake());
						poster.displayClientMessage(Component.translatable("chocobosreborn.duel.withdrawn", c.stake()), true);
					} else {
						RaceManager.oweGp(level.getServer(), c.challenger(), c.stake());
					}
				}
			}
		}
	}

	/** A second rider accepts the oldest challenge their bird may enter. */
	public static boolean accept(ServerPlayer acceptor, ChocoboEntity bird) {
		int needGp = 0;
		for (Challenge c : others(acceptor.getUUID())) {
			ServerPlayer challenger = acceptor.server.getPlayerList().getPlayer(c.challenger());
			if (challenger == null || !(challenger.getVehicle() instanceof ChocoboEntity theirs)
					|| !Square.isSquare(challenger.level())) {
				continue;
			}
			if (RaceManager.sessionOf(challenger.getUUID()) != null
					|| RaceManager.sessionOf(acceptor.getUUID()) != null
					|| HeatSchedule.entered(challenger.getUUID()) || HeatSchedule.entered(acceptor.getUUID())
					|| RaceManager.trackBusy(c.track())) {
				continue;
			}
			if (!RaceScoring.mayEnterCourse(bird.raceClass().getId(), c.track().getRaceClass().getId())
					|| !RaceScoring.mayEnterCourse(theirs.raceClass().getId(), c.track().getRaceClass().getId())) {
				continue;
			}
			if (c.stake() > 0 && !takeGp(acceptor, c.stake())) {
				needGp = Math.max(needGp, c.stake());
				continue;
			}
			if (!RaceManager.startDuel(challenger, theirs, acceptor, bird, c.track(), c.stake())) {
				if (c.stake() > 0) {
					giveGp(acceptor, c.stake());
				}
				continue;
			}
			remove(acceptor.server, c.challenger());   // normally already consumed by startDuel
			withdraw(acceptor);
			acceptor.displayClientMessage(Component.translatable("chocobosreborn.duel.accepting", c.name(),
					Component.translatable("chocobosreborn.track." + c.track().id()), c.stake()), false);
			return true;
		}
		if (needGp > 0) {
			acceptor.displayClientMessage(Component.translatable("chocobosreborn.duel.no_gp", needGp), true);
		}
		return false;
	}

	static int countGp(ServerPlayer player) {
		int have = 0;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.GP.get())) {
				have += s.getCount();
			}
		}
		for (ItemStack s : player.getInventory().offhand) {
			if (s.is(ModItems.GP.get())) {
				have += s.getCount();
			}
		}
		return have;
	}

	static boolean takeGp(ServerPlayer player, int amount) {
		if (countGp(player) < amount) {
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
		for (ItemStack s : player.getInventory().offhand) {
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
				net.minecraft.world.entity.item.ItemEntity dropped = player.drop(st, false);
				if (dropped != null && Square.isSquare(player.level())) {
					dropped.teleportTo(Square.ARRIVAL.x, Square.ARRIVAL.y + 0.5D, Square.ARRIVAL.z);
				}
			}
		}
	}
}
