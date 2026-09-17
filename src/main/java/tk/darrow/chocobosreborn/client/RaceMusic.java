package tk.darrow.chocobosreborn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.sound.ModSounds;

/** Plays the course loop while the local player is racing in the Square, and the village theme otherwise. */
public final class RaceMusic {
	@Nullable
	private static Loop current;
	private static boolean currentIsVillage;
	/** The village playlist: one track plays through, a short gap, then the next. Never two at once. */
	private static final java.util.function.Supplier<SoundEvent>[] VILLAGE = new java.util.function.Supplier[]{
			() -> ModSounds.VILLAGE_THEME.get(), () -> ModSounds.VILLAGE_NIGHT.get()};
	private static int villageIndex;
	private static int villageGap;

	private RaceMusic() {
	}

	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			stop();
			return;
		}
		boolean inSquare = Square.isSquare(mc.level);
		boolean racing = mc.player.getVehicle() instanceof ChocoboEntity bird && bird.racing();
		if (RaceScoring.villageLoopShouldPlay(inSquare, racing)) {
			if (current != null && currentIsVillage && !current.isStopped() && mc.getSoundManager().isActive(current)) {
				return;   // still playing
			}
			if (current != null && !currentIsVillage) {
				stop();   // back from a heat: the course loop ends, the playlist resumes
			} else if (current != null) {
				current = null;   // the track played through: a short silence, then the next one
				villageIndex = (villageIndex + 1) % VILLAGE.length;
				villageGap = 120;
			}
			if (villageGap > 0) {
				villageGap--;
				return;
			}
			mc.getMusicManager().stopPlaying();
			current = new Loop(VILLAGE[villageIndex].get(), false);
			currentIsVillage = true;
			mc.getSoundManager().play(current);
			return;
		}
		if (!RaceScoring.raceLoopShouldPlay(inSquare, racing, false)) {
			stop();
			return;
		}
		if (current == null || currentIsVillage || current.isStopped() || !mc.getSoundManager().isActive(current)) {
			stop();
			mc.getMusicManager().stopPlaying();   // no overworld track under the race loop
			// The bird carries the course it is racing (synced), so every course plays its own loop.
			String trackId = "c_meadow";
			if (mc.player.getVehicle() instanceof ChocoboEntity b) {
				trackId = (b.raceTrack() >= 0 ? tk.darrow.chocobosreborn.race.RaceTrack.byId(b.raceTrack())
						: tk.darrow.chocobosreborn.race.RaceTrack.forClass(b.raceClass(), 0)).id();
			}
			current = new Loop(ModSounds.raceLoop(trackId), true);
			currentIsVillage = false;
			mc.getSoundManager().play(current);
		}
	}

	private static void stop() {
		if (current != null) {
			current.stopNow();
			current = null;
		}
	}

	/** Vanilla's music manager keeps trying to start its own music: while ours plays in the Square, nothing else may use the MUSIC channel. */
	public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
		SoundInstance sound = event.getSound();
		if (sound == null || sound instanceof Loop || sound.getSource() != SoundSource.MUSIC) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && Square.isSquare(mc.level)) {
			event.setSound(null);
		}
	}

	static final class Loop extends AbstractTickableSoundInstance {
		Loop(SoundEvent event, boolean loop) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = loop;
			this.delay = 0;
			this.volume = 0.8F;
			this.relative = true;
		}

		@Override
		public void tick() {
		}

		void stopNow() {
			stop();
		}
	}
}
