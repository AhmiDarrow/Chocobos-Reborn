package tk.darrow.chocobosreborn.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RacePoint;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.RacerLine;

/** Drives only ordinary client movement input and yaw; physics and packets remain real. */
@EventBusSubscriber(modid = ChocobosReborn.MOD_ID, value = Dist.CLIENT)
public final class RaceHarnessClient {
    private static int ticks, run;
    private static boolean held;
    private static double hint = Double.NaN;
    /** This bot's lane, taken from its grid stall, so three bots do not pile onto the centre line. */
    private static double botLane;
    private static int passTicks;
    private static double passSide;
    private static final java.util.concurrent.atomic.AtomicInteger corrections = new java.util.concurrent.atomic.AtomicInteger();
    private static int correctionBase;
    private static RaceTrack activeTrack;
    private static long lastSampleNanos;
    /** Ticks pressed against a wall without getting anywhere; at {@link RacerLine#STUCK_TICKS} the bot recovers. */
    private static int stuckTicks;
    /** Ticks left of a recovery: steering back toward the centre line after the jump. */
    private static int recoverTicks;
    private static double lastX = Double.NaN, lastZ = Double.NaN;
    private static int localBoostTicks(ChocoboEntity bird) {
        try {
            var field = ChocoboEntity.class.getDeclaredField("localBoostTicks");
            field.setAccessible(true);
            return field.getInt(bird);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    /** Signed lane of a point at progress t: positive = inside the loop, like {@link RaceTrack#pointAtLane}. */
    private static double laneOf(RaceTrack track, double t, double x, double z) {
        RacePoint c = track.pointAt(t);
        double[] tg = track.tangent(t);
        return (x - c.x()) * -tg[1] + (z - c.z()) * tg[0];
    }

    private static boolean enabled() {
        String mode = System.getProperty("chocobosreborn.harness");
        return "client".equals(mode) || "host".equals(mode);
    }

    @SubscribeEvent
    public static void connected(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!enabled()) return;
        event.getConnection().channel().pipeline().addBefore("packet_handler", "race_harness_metrics",
                new io.netty.channel.ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object message) throws Exception {
                        if (message instanceof net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket) corrections.incrementAndGet();
                        super.channelRead(ctx, message);
                    }
                });
    }

    @SubscribeEvent
    public static void stopWhenDone(ClientTickEvent.Post event) {
        if (enabled() && Files.exists(Path.of(System.getProperty("chocobosreborn.harness.output"), "done.txt"))) {
            Minecraft.getInstance().stop();
        }
    }
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (!(event.getEntity().getVehicle() instanceof ChocoboEntity bird) || !bird.racing()) {
            if (activeTrack != null && ticks > 0 && !held) {
                try {
                    var out = Path.of(System.getProperty("chocobosreborn.harness.output"));
                    Files.createDirectories(out);
                    String result = new com.google.gson.Gson().toJson(java.util.Map.of("run", run, "track", activeTrack.name(),
                            "ticks", ticks, "vehicle_corrections", corrections.get() - correctionBase));
                    Files.writeString(out.resolve("client-end-" + mc.getUser().getName() + ".jsonl"), result + "\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
            }
            activeTrack = null;
            mc.options.keySprint.setDown(false);
            hint = Double.NaN;
            return;
        }
        if (bird.raceHeld()) {
            if (!held) run++;
            ticks = 0;
            lastSampleNanos = 0;
            hint = Double.NaN;
            stuckTicks = recoverTicks = 0;
            lastX = lastZ = Double.NaN;
        } else {
            if (held) correctionBase = corrections.get();
            ticks++;
        }
        held = bird.raceHeld();
        RaceTrack track = RaceTrack.byId(bird.raceTrack());
        activeTrack = track;
        double progress = Double.isNaN(hint) ? track.progressAt(bird.getX(), bird.getZ())
                : track.progressAt(bird.getX(), bird.getZ(), hint);
        hint = progress;
        if (bird.raceHeld() && ticks == 0) botLane = RacerLine.clampLane(laneOf(track, progress, bird.getX(), bird.getZ()));
        double aim = botLane;
        // steer round a solid racer directly ahead in this lane (racer contact would bump us)
        if (passTicks > 0) passTicks--;
        else {
            for (ChocoboEntity other : mc.level.getEntitiesOfClass(ChocoboEntity.class, bird.getBoundingBox().inflate(10.0D, 2.0D, 10.0D),
                    e -> e != bird && e.contactSolid())) {
                double ot = track.progressAt(other.getX(), other.getZ(), progress);
                double ahead = (ot - progress - Math.floor(ot - progress + 0.5D)) * track.lapLength();
                double otherLane = laneOf(track, ot, other.getX(), other.getZ());
                if (ahead > 1.0D && ahead < 10.0D && Math.abs(otherLane - botLane) < 2.0D) {
                    passSide = RacerLine.passSide(botLane, otherLane);
                    passTicks = 40;
                    break;
                }
            }
        }
        if (passTicks > 0) aim = RacerLine.clampLane(botLane + passSide * RacerLine.PASS_OFFSET);
        // pinned on a rail or a rim: jump, and steer back toward the centre line for a second
        boolean jump = false;
        double moved = Double.isNaN(lastX) ? 1.0D : Math.hypot(bird.getX() - lastX, bird.getZ() - lastZ);
        lastX = bird.getX();
        lastZ = bird.getZ();
        stuckTicks = bird.raceHeld() ? 0 : RacerLine.stuckStep(stuckTicks, bird.horizontalCollision, bird.onGround(), moved);
        if (stuckTicks >= RacerLine.STUCK_TICKS && recoverTicks == 0) {
            recoverTicks = RacerLine.RECOVER_TICKS;
            stuckTicks = 0;
            jump = true;
            try {
                Path out = Path.of(System.getProperty("chocobosreborn.harness.output"));
                Files.createDirectories(out);
                Files.writeString(out.resolve("recover-" + mc.getUser().getName() + ".csv"), String.format(Locale.ROOT,
                        "%d,%d,%s,%.4f,%.4f,%.4f,%.6f,%.3f,%.2f%n", run, ticks, track.name(), bird.getX(), bird.getY(), bird.getZ(),
                        progress, laneOf(track, progress, bird.getX(), bird.getZ()), bird.getYRot()),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        }
        if (recoverTicks > 0) {
            recoverTicks--;
            aim = 0.0D;
        }
        // the same line the AI drives for this colour (RaceTrack#steerLaneAt): round every feature
        // it cannot cross (a Black round lava, a Green round water), out to the band's outside lane
        // before the opening and back after it, never through the rail either side
        double lap = track.lapLength();
        var colour = bird.color();
        double speed = bird.getDeltaMovement().horizontalDistance();
        double ahead = Math.max(5.0D, speed * 6.0D);
        if (Math.abs(track.steerLaneAt(progress, aim, colour, true) - aim) > 1e-6
                || Math.abs(track.steerLaneAt(progress + ahead / lap, aim, colour, true) - aim) > 1e-6) {
            ahead = 4.0D;   // a connector's own tangent, not a chord through its corner
        }
        double aimT = progress + ahead / lap;
        double lane = track.steerLaneAt(aimT, aim, colour, true);
        RacePoint target = track.pointAtLane(aimT, lane);
        event.getEntity().setYRot((float) Math.toDegrees(Math.atan2(-(target.x() - bird.getX()), target.z() - bird.getZ())));
        event.getEntity().setXRot(0);
        // brake for the swing into a detour as the AI does (RacerLine#connectorSpeed). Part
        // throttle, not none: a bird off the throttle has no grip to turn with and ran straight
        // on (A_MACHETE's bog rejoin, all three bots out past the rail).
        double connector = track.connectorAhead(progress, colour, true, RacerLine.CONNECTOR_BRAKE_LEAD);
        double cap = Double.isNaN(connector) ? Double.POSITIVE_INFINITY : RacerLine.connectorSpeed(connector);
        boolean braking = speed > cap;
        var input = event.getInput();
        input.forwardImpulse = braking ? (float) Math.max(0.3D, cap / speed) : 1;
        input.leftImpulse = 0;
        input.up = true;
        input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
        input.jumping = jump;
        mc.options.keySprint.setDown(!braking && ticks % 400 < 240);
        if (ticks > 0 && track.name().equals(System.getProperty("chocobosreborn.harness.traceTrack", ""))) {
            try {
                var pads = new java.util.ArrayList<String>();
                for (var pos : net.minecraft.core.BlockPos.betweenClosed(bird.blockPosition().offset(-3, -3, -3),
                        bird.blockPosition().offset(3, 2, 3))) {
                    if (mc.level.getBlockState(pos).is(tk.darrow.chocobosreborn.block.ModBlocks.BOOST_PAD.get()))
                        pads.add(pos.getX() + ":" + pos.getY() + ":" + pos.getZ());
                }
                var motion = bird.getDeltaMovement();
                Files.writeString(Path.of(System.getProperty("chocobosreborn.harness.output"), "trace-" + mc.getUser().getName() + ".csv"),
                        String.format(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%b,%b,%s%n",
                                track.name(), ticks, bird.getX(), bird.getY(), bird.getZ(), motion.x, motion.y, motion.z,
                                localBoostTicks(bird), bird.onGround(), bird.horizontalCollision, String.join(";", pads)),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        }
        if (ticks > 0 && ticks % 10 == 0) {
            try {
                Path out = Path.of(System.getProperty("chocobosreborn.harness.output"));
                Files.createDirectories(out);
                String name = mc.getUser().getName();
                long now = System.nanoTime();
                Files.writeString(out.resolve("performance-" + name + ".csv"), String.format(Locale.ROOT,
                        "%s,%d,%d,%.3f%n", track.name(), ticks, mc.getFps(),
                        lastSampleNanos == 0 ? 0.0 : (now - lastSampleNanos) / 1_000_000.0),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                lastSampleNanos = now;
                for (ChocoboEntity seen : mc.level.getEntitiesOfClass(ChocoboEntity.class,
                        bird.getBoundingBox().inflate(128), ChocoboEntity::racing)) {
                    Files.writeString(out.resolve("visible-field-" + name + ".csv"), String.format(Locale.ROOT,
                            "%s,%d,%d,%.4f,%.4f,%.4f,%b%n", track.name(), ticks, seen.getId(),
                            seen.getX(), seen.getY(), seen.getZ(), seen == bird),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                String file = name.equals("LatencyRider") ? "client.csv" : "client-" + name + ".csv";
                Files.writeString(out.resolve(file), String.format(Locale.ROOT,
                        "%d,%d,%.4f,%.4f,%.4f,%.6f,%d,%b,%b,%s,%d,%.4f,%b,%b,%.2f,%d,%d%n", run, ticks, bird.getX(), bird.getY(), bird.getZ(),
                        progress, bird.stamina(), bird.dashLocked(), bird.boosting(), track.name(), corrections.get() - correctionBase, bird.getDeltaMovement().horizontalDistance(), bird.onGround(), bird.horizontalCollision, bird.getYRot(), localBoostTicks(bird), bird.tickCount), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        }
        if (ticks == 200 || (ticks > 0 && ticks % 600 == 0)) {
            Screenshot.grab(mc.gameDirectory, track.name() + "-" + run + "-" + ticks + ".png", mc.getMainRenderTarget(), c -> {});
        }
    }
}
