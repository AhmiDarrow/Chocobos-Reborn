package tk.darrow.chocobosreborn.net;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import tk.darrow.chocobosreborn.race.RaceManager;

/** Fresh RTT for live races. Network-thread echoes avoid charging render/server tick scheduling as wire latency. */
public final class RaceLatency {
    private static final ConcurrentHashMap<UUID, LatencyWindow> WINDOWS = new ConcurrentHashMap<>();
    private RaceLatency() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        WINDOWS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null || RaceManager.sessionOf(id) == null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RaceManager.sessionOf(player.getUUID()) == null || !player.connection.hasChannel(RacePayloads.LatencyProbe.TYPE)) continue;
            long now = System.nanoTime();
            LatencyWindow window = WINDOWS.computeIfAbsent(player.getUUID(), id -> new LatencyWindow());
            if (window.begin(now)) PacketDistributor.sendToPlayer(player, new RacePayloads.LatencyProbe(now));
        }
    }

    public static void reply(ServerPlayer player, long nonce) {
        LatencyWindow window = WINDOWS.get(player.getUUID());
        if (window != null) window.reply(nonce, System.nanoTime());
    }

    public static int millis(ServerPlayer player) {
        LatencyWindow window = WINDOWS.get(player.getUUID());
        return window == null ? player.connection.latency() : window.millis(System.nanoTime(), player.connection.latency());
    }
}
