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

/** Drives only ordinary client movement input and yaw; physics and packets remain real. */
@EventBusSubscriber(modid = ChocobosReborn.MOD_ID, value = Dist.CLIENT)
public final class RaceHarnessClient {
    private static int ticks, run;
    private static boolean held;
    private static double hint = Double.NaN;
    private static final java.util.concurrent.atomic.AtomicInteger corrections = new java.util.concurrent.atomic.AtomicInteger();
    private static int correctionBase;
    private static RaceTrack activeTrack;
    private static long lastSampleNanos;
    private static int localBoostTicks(ChocoboEntity bird) {
        try {
            var field = ChocoboEntity.class.getDeclaredField("localBoostTicks");
            field.setAccessible(true);
            return field.getInt(bird);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
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
        RacePoint target = track.pointAt(progress + Math.max(5.0D, bird.getDeltaMovement().horizontalDistance() * 6.0D) / track.lapLength());
        event.getEntity().setYRot((float) Math.toDegrees(Math.atan2(-(target.x() - bird.getX()), target.z() - bird.getZ())));
        event.getEntity().setXRot(0);
        var input = event.getInput();
        input.forwardImpulse = 1;
        input.leftImpulse = 0;
        input.up = true;
        input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
        mc.options.keySprint.setDown(ticks % 400 < 240);
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
