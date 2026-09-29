package tk.darrow.chocobosreborn.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.race.*;

/** Development only: real remote player, ordinary RaceSession, no race-progress teleports. */
@EventBusSubscriber(modid = ChocobosReborn.MOD_ID)
public final class RaceHarnessServer {
    private static final String[] PROFILES = {"baseline", "120ms", "300ms", "jitter", "burst"};
    private static final int[] DELAY = {0, 60, 150, 60, 60};
    private static int scenario, wait, ticks;
    private static ChocoboEntity bird;
    private static RaceSession session;
    private static boolean setup, complete;
    private static final Path OUT = Path.of(System.getProperty("chocobosreborn.harness.output", "build/latency/results"));

    private static void append(String file, String line) {
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve(file), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!"server".equals(System.getProperty("chocobosreborn.harness")) || complete || RaceHarnessCrowd.active()) return;
        var server = event.getServer();
        ServerPlayer player = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.getGameProfile().getName().equals("LatencyRider")).findFirst().orElse(null);
        if (player == null) return;
        if (!setup) {
            setup = true;
            player.setGameMode(GameType.SURVIVAL);
            RaceManager.enterSquareOnFoot(player);
            var level = player.serverLevel();
            bird = ModEntities.CHOCOBO.get().create(level);
            bird.moveTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z, 0, 0);
            bird.setAge(0);
            RaceHarnessBirds.dress(bird, RaceTrack.valueOf(System.getProperty("chocobosreborn.harness.track", "C_MEADOW")));
            bird.setSaddledForPreview(true);
            bird.tame(player);
            bird.setOrderedToSit(false);
            level.addFreshEntity(bird);
            wait = 400;
            configure();
        }
        if (session == null) {
            if (--wait > 0) return;
            player.startRiding(bird, true);
            RaceTrack course = RaceTrack.valueOf(System.getProperty("chocobosreborn.harness.track", "C_MEADOW"));
            if (!RaceManager.startRace(player, course, false)) {
                append("events.jsonl", "{\"error\":\"race_start_failed\"}");
                append("done.txt", "failed");
                complete = true;
                return;
            }
            session = RaceManager.sessionOf(player.getUUID());
            ticks = 0;
            append("events.jsonl", "{\"event\":\"start\",\"profile\":\"" + PROFILES[scenario] + "\"}");
        }
        ticks++;
        if (ticks % 10 == 0) {
            append("server.csv", String.format(Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.4f,%d,%b,%b,%s,%d",
                    PROFILES[scenario], ticks, player.connection.latency(), bird.getX(), bird.getY(), bird.getZ(),
                    bird.stamina(), bird.dashLocked(), bird.raceHeld(), session.progressReport(), tk.darrow.chocobosreborn.net.RaceLatency.millis(player)));
        }
        if (!session.live() || ticks > 3600) {
            boolean timeout = session.live();
            String report = session.progressReport();
            if (timeout) session.abort();
            append("events.jsonl", "{\"event\":\"end\",\"profile\":\"" + PROFILES[scenario]
                    + "\",\"ticks\":" + ticks + ",\"timeout\":" + timeout + ",\"report\":\"" + report + "\"}");
            session = null;
            if (++scenario >= PROFILES.length) {
                append("done.txt", "complete");
                complete = true;
            } else {
                wait = 400;
                configure();
            }
        }
    }

    private static void configure() {
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("network.json"), "{\"delay_ms\":" + DELAY[scenario]
                    + ",\"jitter_ms\":" + (scenario == 3 ? 40 : 0)
                    + ",\"burst_ms\":" + (scenario == 4 ? 600 : 0) + "}");
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
}
