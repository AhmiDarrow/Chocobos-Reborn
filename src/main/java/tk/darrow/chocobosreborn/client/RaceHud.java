package tk.darrow.chocobosreborn.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.net.RiderPayloads;

/**
 * Race standings come from the server; the stamina display follows the local predicted frame.
 * Drawn as its own HUD layer where the action bar sits, not through the action bar: sending it there every tick
 * overwrote the server's own action-bar lines (lap, off-road countdown, shortcut warnings) within one tick. While
 * one of those is showing the standings move up a line.
 */
public final class RaceHud {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "race_hud");
    /** Vanilla shows an action-bar message for 60 ticks. */
    private static final int OVERLAY_TICKS = 60;
    private static RiderPayloads.Hud standings;
    /** The line drawn, rebuilt only when a number in it changes. */
    private static Component line;
    private static int lineKey = Integer.MIN_VALUE;
    private static int overlayTicks;

    private RaceHud() {}

    public static void update(RiderPayloads.Hud value) { standings = value; }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, LAYER, RaceHud::render);
    }

    /** Another action-bar message (the server's, or another mod's): keep out of its way while it shows. */
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (event.isOverlay()) {
            overlayTicks = OVERLAY_TICKS;
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        if (overlayTicks > 0) {
            overlayTicks--;
        }
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof ChocoboEntity bird) || !bird.racing() || bird.raceHeld()) {
            standings = null;
            line = null;
            return;
        }
        if (standings == null || standings.entityId() != bird.getId()) {
            line = null;
            return;
        }
        boolean locked = bird.dashLocked();
        int key = java.util.Objects.hash(standings.lap(), standings.laps(), standings.place(), standings.field(),
                bird.stamina(), bird.maxStamina(), locked);
        if (line == null || key != lineKey) {
            lineKey = key;
            line = Component.translatable(locked ? "chocobosreborn.race.hud_locked" : "chocobosreborn.race.hud",
                    standings.lap(), standings.laps(), standings.place(), standings.field(), bird.stamina(), bird.maxStamina());
        }
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Component text = line;
        Minecraft mc = Minecraft.getInstance();
        if (text == null || mc.options.hideGui) {
            return;
        }
        int y = g.guiHeight() - 68 - (overlayTicks > 0 ? 12 : 0);
        g.drawCenteredString(mc.font, text, g.guiWidth() / 2, y - 4, 0xFFFFFFFF);
    }
}
