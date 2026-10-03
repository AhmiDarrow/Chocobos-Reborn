package tk.darrow.chocobosreborn.net;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Collects each racing bird's frame as it ticks and, once the server tick is over, sends every
 * player one {@link RaceMovePayloads.Frames} with the birds near them (never the bird they drive
 * themselves: their own client simulates it). Server thread only.
 */
public final class RaceFrameSender {
	/** Beyond this a player is not tracking the bird anyway (view distance). */
	public static final double RANGE = 192.0D;

	private record Pending(RaceMovePayloads.Frame frame, Entity driver) {}

	private static final Map<ServerLevel, List<Pending>> PENDING = new IdentityHashMap<>();

	private RaceFrameSender() {}

	/** A racing bird's position this tick; {@code driver} is the player driving it, or null. */
	public static void add(ServerLevel level, RaceMovePayloads.Frame frame, Entity driver) {
		PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(frame, driver));
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		if (PENDING.isEmpty()) {
			return;
		}
		double range = RANGE * RANGE;
		for (Map.Entry<ServerLevel, List<Pending>> e : PENDING.entrySet()) {
			List<Pending> birds = e.getValue();
			if (birds.isEmpty()) {
				continue;
			}
			for (ServerPlayer p : e.getKey().players()) {
				if (!p.connection.hasChannel(RaceMovePayloads.Frames.TYPE)) {
					continue;
				}
				List<RaceMovePayloads.Frame> near = new ArrayList<>(birds.size());
				int tick = birds.get(0).frame().tick();
				for (Pending b : birds) {
					RaceMovePayloads.Frame f = b.frame();
					if (b.driver() != p && p.distanceToSqr(f.x(), f.y(), f.z()) <= range) {
						near.add(f);
					}
				}
				if (!near.isEmpty()) {
					PacketDistributor.sendToPlayer(p, new RaceMovePayloads.Frames(tick, near));
				}
			}
			birds.clear();
		}
	}
}
