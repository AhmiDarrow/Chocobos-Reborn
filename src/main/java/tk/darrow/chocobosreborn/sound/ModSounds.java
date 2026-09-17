package tk.darrow.chocobosreborn.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tk.darrow.chocobosreborn.ChocobosReborn;

public final class ModSounds {
	public static final DeferredRegister<SoundEvent> SOUNDS =
			DeferredRegister.create(Registries.SOUND_EVENT, ChocobosReborn.MOD_ID);

	public static final DeferredHolder<SoundEvent, SoundEvent> KWEH = sound("entity.chocobo.kweh");
	public static final DeferredHolder<SoundEvent, SoundEvent> WARK = sound("entity.chocobo.wark");
	public static final DeferredHolder<SoundEvent, SoundEvent> KWEH_FOLLOW = sound("entity.chocobo.kweh_follow");
	public static final DeferredHolder<SoundEvent, SoundEvent> KWEH_STAY = sound("entity.chocobo.kweh_stay");
	public static final DeferredHolder<SoundEvent, SoundEvent> KWEH_WANDER = sound("entity.chocobo.kweh_wander");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_DASH = sound("music.race.chocobo_dash");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_GALLOP = sound("music.race.chocobo_race_gallop");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_ADVENTURE = sound("music.race.gallop_of_adventure");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_HEROES = sound("music.race.gallop_of_heroes");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_RUNE = sound("music.race.rune_dash");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_DRAGON = sound("music.race.speed_of_the_dragon");
	public static final DeferredHolder<SoundEvent, SoundEvent> RACE_VICTORY = sound("music.race.victory_stinger");
	/** Whiskerwind's village theme (Ahmi's licensed "Woodland Pastoral"), looped while in the Square off the course. */
	public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGE_THEME = sound("music.village.woodland_pastoral");
	/** Whiskerwind after dark (Ahmi's licensed "Village Night Song"). */
	public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGE_NIGHT = sound("music.village.village_night_song");

	/** Race loop for a track id (see RaceScoring.raceLoopKey). */
	public static SoundEvent raceLoop(String trackId) {
		return switch (tk.darrow.chocobosreborn.race.RaceScoring.raceLoopKey(trackId)) {
			case "chocobo_race_gallop" -> RACE_GALLOP.get();
			case "gallop_of_adventure" -> RACE_ADVENTURE.get();
			case "rune_dash" -> RACE_RUNE.get();
			case "gallop_of_heroes" -> RACE_HEROES.get();
			case "speed_of_the_dragon" -> RACE_DRAGON.get();
			default -> RACE_DASH.get();
		};
	}

	private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
		return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
				ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, path)));
	}

	private ModSounds() {
	}
}
