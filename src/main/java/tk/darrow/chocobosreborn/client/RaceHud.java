package tk.darrow.chocobosreborn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.net.RiderPayloads;

/** Race standings come from the server; the stamina display follows the local predicted frame. */
public final class RaceHud {
    private static RiderPayloads.Hud standings;
    private RaceHud() {}
    public static void update(RiderPayloads.Hud value) { standings = value; }
    public static void tick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof ChocoboEntity bird) || !bird.racing() || bird.raceHeld()) {
            standings = null;
            return;
        }
        if (standings == null || standings.entityId() != bird.getId()) return;
        player.displayClientMessage(Component.translatable(bird.dashLocked() ? "chocobosreborn.race.hud_locked" : "chocobosreborn.race.hud",
                standings.lap(), standings.laps(), standings.place(), standings.field(), bird.stamina(), bird.maxStamina()), true);
    }
}
