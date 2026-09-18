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
	/** Ticks to keep MUSIC clear after the first-place sting so the village loop does not cover it. */
	private static int stingHold;
	/** The village playlist: one track plays through, a short gap, then the next. Never two at once. */
	private static final java.util.function.Supplier<SoundEvent>[] VILLAGE = new java.util.function.Supplier[]{
			() -> ModSounds.VILLAGE_THEME.get(), () -> ModSounds.VILLAGE_NIGHT.get()};
	private static int villageIndex;
	private static int villageGap;
	/** Last course loop so a dismounted finisher does not snap to Meadow after the sting. */
	private static String lastTrackId = "c_meadow";

	private RaceMusic() {
	}

	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			stop();
			stingHold = 0;
			return;
		}
		if (stingHold > 0) {
			stingHold--;
			return;
		}
		boolean inSquare = Square.isSquare(mc.level);
		boolean racing = mc.player.getVehicle() instanceof ChocoboEntity bird && bird.racing();
		boolean onCourse = inSquare && onCourseIsland(mc.player);
		if (RaceScoring.villageLoopShouldPlay(inSquare, racing, onCourse)) {
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
		if (!RaceScoring.raceLoopShouldPlay(inSquare, racing, false, onCourse)) {
			stop();
			return;
		}
		if (current == null || currentIsVillage || current.isStopped() || !mc.getSoundManager().isActive(current)) {
			stop();
			mc.getMusicManager().stopPlaying();   // no overworld track under the race loop
			// The bird carries the course it is racing (synced), so every course plays its own loop.
			current = new Loop(ModSounds.raceLoop(courseLoopId(mc)), true);
			currentIsVillage = false;
			mc.getSoundManager().play(current);
		}
	}

	private static String courseLoopId(Minecraft mc) {
		if (mc.player != null && mc.player.getVehicle() instanceof ChocoboEntity b && b.raceTrack() >= 0) {
			lastTrackId = tk.darrow.chocobosreborn.race.RaceTrack.byId(b.raceTrack()).id();
			return lastTrackId;
		}
		tk.darrow.chocobosreborn.race.RaceTrack here = trackAt(mc.player);
		if (here != null) {
			lastTrackId = here.id();
			return lastTrackId;
		}
		return lastTrackId;
	}

	private static boolean onCourseIsland(net.minecraft.world.entity.player.Player player) {
		return trackAt(player) != null;
	}

	private static tk.darrow.chocobosreborn.race.RaceTrack trackAt(net.minecraft.world.entity.player.Player player) {
		if (player == null) {
			return null;
		}
		for (tk.darrow.chocobosreborn.race.RaceTrack t : tk.darrow.chocobosreborn.race.RaceTrack.values()) {
			if (RaceScoring.onCourseIsland(player.getX() - t.centerX(), player.getZ() - t.centerZ(),
					t.getRadiusX(), t.getRadiusZ(), 8.0D)) {
				return t;
			}
		}
		return null;
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
		if (sound.getLocation().equals(ModSounds.RACE_VICTORY.get().getLocation())) {
			stop();   // cut the course loop so the first-place sting is heard
			stingHold = 160;   // ~8s, covers the streamed stinger
			if (!(sound instanceof Loop)) {
				event.setSound(new Loop(ModSounds.RACE_VICTORY.get(), false));
			}
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
