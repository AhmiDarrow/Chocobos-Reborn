package tk.darrow.chocobosreborn.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.*;
import tk.darrow.chocobosreborn.entity.*;
import tk.darrow.chocobosreborn.net.RaceLatency;
import tk.darrow.chocobosreborn.race.*;

/** Actual integrated host and delayed guests in ordinary six-bird races, or three remote clients on a
 * dedicated server ({@code -Dchocobosreborn.harness=hub}, tools/hub_race_harness.py). Never shipped. */
@EventBusSubscriber(modid = ChocobosReborn.MOD_ID)
public final class RaceHarnessPair {
    private static final Path OUT = Path.of(System.getProperty("chocobosreborn.harness.output", "build/latency-pair/results"));
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final RaceTrack[] TRACKS = Arrays.stream(System.getProperty("chocobosreborn.harness.tracks",
            String.join(",", Arrays.stream(RaceTrack.values()).map(Enum::name).toList())).split(",")).map(RaceTrack::valueOf).toArray(RaceTrack[]::new);
    private static boolean opened, setup, done;
    private static int course, wait = 400, ticks;
    private static RaceSession session;
    private static final boolean FULL_FIELD = Boolean.parseBoolean(System.getProperty("chocobosreborn.harness.fullField", "true"));
    // -Dchocobosreborn.harness.riders=2 leaves four of the six slots to the real AI
    private static final String[] NAMES = FULL_FIELD
            ? Arrays.copyOf(new String[]{"LatencyHost", "LatencyGuest", "LatencyGuest2"},
                    Math.max(2, Math.min(3, Integer.getInteger("chocobosreborn.harness.riders", 3))))
            : new String[]{"LatencyHost", "LatencyGuest"};
    private static final ChocoboEntity[] birds = new ChocoboEntity[NAMES.length];
    private static final double[] furthest = new double[NAMES.length];
    private static final int[] stalled = new int[NAMES.length];
    private static long tickStart;

    private static boolean active() {
        String mode = System.getProperty("chocobosreborn.harness");
        return "host".equals(mode) || "hub".equals(mode);
    }

    @SubscribeEvent
    public static void beforeTick(ServerTickEvent.Pre event) {
        if (active()) tickStart = System.nanoTime();
    }

    private static void write(String file, String line) {
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve(file), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!active() || done) return;
        var server = event.getServer();
        // a dedicated server (hub mode) already listens on its own port
        if (!opened && server.isDedicatedServer()) opened = true;
        if (!opened) {
            try {
                // The integrated server stays an actual host, but its test listener is loopback-only.
                server.setUsesAuthentication(false);
                server.getConnection().startTcpServerListener(java.net.InetAddress.getByName("127.0.0.1"), 25578);
                // Match publishServer's no-pause behavior without its wildcard listener/LAN broadcast.
                // Development names are available here; this class is excluded from release jars.
                var port = server.getClass().getDeclaredField("publishedPort");
                port.setAccessible(true);
                port.setInt(server, 25578);
                opened = true;
                write("listening.txt", "127.0.0.1:25578");
            } catch (java.io.IOException | ReflectiveOperationException e) { throw new RuntimeException(e); }
        }
        ServerPlayer[] players = Arrays.stream(NAMES).map(server.getPlayerList()::getPlayerByName).toArray(ServerPlayer[]::new);
        if (Arrays.stream(players).anyMatch(Objects::isNull)) {
            if (setup) {
                write("events.jsonl", JSON.toJson(Map.of("event", "error", "track", TRACKS[course].name(), "reason", "participant_disconnected")));
                if (session != null && session.live()) session.abort();
                done = true;
                write("done.txt", "failed");
            }
            return;
        }
        if (!setup) {
            for (int i = 0; i < players.length; i++) birds[i] = prepare(players[i]);
            setup = true;
        }
        if (session == null) {
            if (--wait > 0) return;
            RaceTrack track = TRACKS[course];
            for (int i = 0; i < players.length; i++) {
                RaceHarnessBirds.dress(birds[i], track);
                players[i].startRiding(birds[i], true);
            }
            if (FULL_FIELD) {
                // The same ordinary six-slot session used by the heat timetable, including its real AI.
                var gridPlayers = new ArrayList<>(Arrays.asList(players));
                var gridBirds = new ArrayList<>(Arrays.asList(birds));
                int rotation = Integer.getInteger("chocobosreborn.harness.gridRotation", 0);
                Collections.rotate(gridPlayers, rotation);
                Collections.rotate(gridBirds, rotation);
                String rivals = System.getProperty("chocobosreborn.harness.rivals");
                session = rivals == null
                        ? new RaceSession(players[0].serverLevel(), track, false, gridPlayers, gridBirds, false, 0)
                        : new RaceSession(players[0].serverLevel(), track, false, gridPlayers, gridBirds, false, 0, Boolean.parseBoolean(rivals));
                try {
                    var register = RaceManager.class.getDeclaredMethod("addSession", RaceSession.class);
                    register.setAccessible(true);
                    register.invoke(null, session);
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            } else {
                if (!RaceManager.startDuel(players[0], birds[0], players[1], birds[1], track, 0)) {
                    write("events.jsonl", JSON.toJson(Map.of("event", "error", "track", track.name(), "reason", "start_failed")));
                    done = true;
                    write("done.txt", "failed");
                    return;
                }
                session = RaceManager.sessionOf(players[0].getUUID());
            }
            ticks = 0;
            Arrays.fill(furthest, 0);
            Arrays.fill(stalled, 0);
            write("events.jsonl", JSON.toJson(Map.of("event", "start", "track", track.name(), "class", track.getRaceClass().name(), "humans", players.length, "field", session.fieldSize(), "laps", track.getLaps())));
        }
        ticks++;
        boolean stuck = false;
        for (int i = 0; i < players.length; i++) {
            ChocoboEntity b = birds[i];
            RaceSession.Timing timing = session.timing(players[i].getUUID());
            if (ticks % 10 == 0) {
                write("server.csv", String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%.4f,%.4f,%.4f,%d,%b,%b,%d,%d",
                        TRACKS[course].name(), players[i].getGameProfile().getName(), ticks, players[i].connection.latency(),
                        RaceLatency.millis(players[i]), b.getX(), b.getY(), b.getZ(), b.stamina(), b.dashLocked(),
                        b.raceHeld(), timing.laps(), timing.place()));
            }
            if (session.running() && timing.place() == 0 && Double.isNaN(timing.creditedTick())) {
                double progress = timing.laps() + TRACKS[course].progressAt(b.getX(), b.getZ());
                if (progress > furthest[i] + 0.003) { furthest[i] = progress; stalled[i] = 0; }
                else stalled[i]++;
                stuck |= stalled[i] > 600;
            }
        }
        if (ticks % 10 == 0) {
            // the original keys unchanged; "ai" (added) is where each AI bird is and what it is doing
            var line = new LinkedHashMap<String, Object>();
            line.put("server_tick_ms", (System.nanoTime() - tickStart) / 1_000_000.0);
            line.put("track", TRACKS[course].name());
            line.put("finished", session.finished());
            line.put("field", session.fieldSize());
            line.put("progress", session.progressReport());
            line.put("ticks", ticks);
            line.put("ai", session.aiReport());
            write("field.jsonl", JSON.toJson(line));
        }
        if (!session.live() || stuck || ticks > 11000) {
            var result = new LinkedHashMap<String, Object>();
            result.put("event", "end");
            result.put("track", TRACKS[course].name());
            result.put("ticks", ticks);
            result.put("stuck", stuck);
            result.put("timeout", ticks > 11000);
            result.put("field", session.fieldSize());
            result.put("finished", session.finished());
            result.put("fieldProgress", session.progressReport());
            for (int i = 0; i < players.length; i++) {
                result.put(i == 0 ? "host" : i == 1 ? "guest" : "guest2", resultTiming(session.timing(players[i].getUUID())));
            }
            write("events.jsonl", JSON.toJson(result));
            if (session.live()) session.abort();
            session = null;
            if (++course == TRACKS.length) {
                done = true;
                write("done.txt", "complete");
            } else wait = 100;
        }
    }

    private static Map<String, Object> resultTiming(RaceSession.Timing t) {
        var result = new LinkedHashMap<String, Object>();
        result.put("laps", t.laps());
        result.put("place", t.place());
        result.put("forfeited", t.forfeited());
        result.put("observedTick", Double.isFinite(t.observedTick()) ? t.observedTick() : null);
        result.put("creditedTick", Double.isFinite(t.creditedTick()) ? t.creditedTick() : null);
        result.put("startLatencyMs", t.startLatencyMs());
        result.put("finishLatencyMs", t.finishLatencyMs());
        return result;
    }

    private static ChocoboEntity prepare(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        RaceManager.enterSquareOnFoot(player);
        var level = player.serverLevel();
        ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
        bird.moveTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z, 0, 0);
        bird.setAge(0);
        RaceHarnessBirds.dress(bird, TRACKS[0]);
        bird.setSaddledForPreview(true);
        bird.tame(player);
        bird.setOrderedToSit(false);
        level.addFreshEntity(bird);
        return bird;
    }
}
