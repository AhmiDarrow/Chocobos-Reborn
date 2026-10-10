package tk.darrow.chocobosreborn.race;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * Race-day life in Whiskerwind: the inn bell rings the two-minute and one-minute
 * calls and the start, the townsfolk know when a heat is live (they go to the
 * overlook and cheer), the winners' board keeps the last ranked winner of each
 * class, and a rider's ranked win lights fireworks over the plaza.
 */
public final class TownLife {
	/** A heat this close (or running) sends the townsfolk to the overlook. */
	private static final int WATCH_LEAD_SECONDS = 90;
	/** Two-minute and one-minute calls, and the start peal one second out (at 0 the timetable has already started the heat and dropped it). */
	private static final int[] CALLS = {120, 60, 1};
	private static final RaceClass[] CLASSES = RaceClass.values(), NONE = new RaceClass[0];

	private static long liveTick = Long.MIN_VALUE;
	private static boolean live;
	private static boolean cheer;
	private static final Map<RaceClass, long[]> RUNG = new EnumMap<>(RaceClass.class);
	private static int strokes;
	private static long nextStroke;
	/** A winner was recorded while the board's chunk was unloaded (the riders are out on a course island). */
	private static boolean boardPending;
	/** A rider's win lights the plaza until this game time, once someone is there to see it. */
	private static long fireworksUntil = Long.MIN_VALUE;
	/** How long a rider's win keeps its fireworks waiting for the plaza to load (teardown brings riders home). */
	private static final long FIREWORKS_WAIT = 600L;

	private TownLife() {
	}

	/** Server stop: nothing carried into the next server in this JVM. */
	public static void reset() {
		liveTick = Long.MIN_VALUE;
		live = false;
		cheer = false;
		RUNG.clear();
		strokes = 0;
		boardPending = false;
		fireworksUntil = Long.MIN_VALUE;
	}

	/** A heat is called within 90 s, or one is running. Cached once a second. */
	public static boolean heatLive(ServerLevel square) {
		refresh(square);
		return live;
	}

	/** The last ten seconds of a call, or a heat under way: the watchers cheer. */
	public static boolean cheering(ServerLevel square) {
		refresh(square);
		return cheer;
	}

	private static void refresh(ServerLevel square) {
		long now = square.getGameTime();
		if (now / 20L == liveTick) {
			return;
		}
		liveTick = now / 20L;
		boolean running = RaceManager.anyRunning();
		boolean soon = false, close = false;
		for (RaceClass rc : HeatSchedule.anyPending() ? CLASSES : NONE) {
			HeatSchedule.Heat heat = HeatSchedule.pending(rc);
			if (heat != null) {
				int s = HeatSchedule.secondsLeft(heat, now);
				soon |= s <= WATCH_LEAD_SECONDS;
				close |= s <= 10;
			}
		}
		live = running || soon;
		cheer = running || close;
	}

	/** Server tick (Square level): bell strokes. Does nothing while the Square is empty. */
	public static void tick(ServerLevel square) {
		if (square.players().isEmpty()) {
			return;
		}
		long now = square.getGameTime();
		if (now % 20L == 0L) {
			flushWinner(square, now);
		}
		if (!HeatSchedule.anyPending()) {
			RUNG.clear();   // what the per-class pass below does with nothing on the timetable
			if (strokes <= 0) {
				return;
			}
		}
		for (RaceClass rc : HeatSchedule.anyPending() ? CLASSES : NONE) {
			HeatSchedule.Heat heat = HeatSchedule.pending(rc);
			if (heat == null) {
				RUNG.remove(rc);
				continue;
			}
			long[] rung = RUNG.computeIfAbsent(rc, k -> new long[]{Long.MIN_VALUE, 0L});
			if (rung[0] != heat.startTick()) {
				rung[0] = heat.startTick();
				rung[1] = 0L;
			}
			int s = HeatSchedule.secondsLeft(heat, now);
			for (int i = 0; i < CALLS.length; i++) {
				if (s <= CALLS[i] && (rung[1] & (1L << i)) == 0L) {
					// skip calls already past when the heat was first seen (no triple peal on a late join)
					rung[1] |= (1L << i);
					if (s > CALLS[i] - 3) {
						strokes = Math.max(strokes, i == CALLS.length - 1 ? 5 : 3);
						nextStroke = now;
					}
				}
			}
		}
		if (strokes > 0 && now >= nextStroke) {
			strokes--;
			nextStroke = now + 16L;
			BlockPos bell = bellPos();
			if (square.isLoaded(bell)) {
				square.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.9F + 0.05F * (strokes % 3));
			}
		}
	}

	/** The bell in the inn's tower (see {@link VillageBuildings#inn}). */
	static BlockPos bellPos() {
		return new BlockPos(VillageLayout.INN_X + 9, SquareBuilder.GROUND_Y + 15, VillageLayout.INN_Z);
	}

	// ------------------------------------------------------ winners' board

	/** A ranked heat is over: remember its winner, update the board, and light the sky if a rider won. */
	public static void recordWinner(ServerLevel square, RaceClass rc, String rider, String bird, String trackId,
	                                double seconds, boolean human) {
		CompoundTag w = new CompoundTag();
		w.putString("Rider", rider);
		w.putString("Bird", bird);
		w.putString("Track", trackId);
		w.putDouble("Seconds", seconds);
		SquareData.get(square).setWinner(rc.getId(), w);
		// the heat ends with every rider on a course island far from the plaza: the board and the fireworks wait
		// until their chunks are loaded again (they were skipped for good before)
		boardPending = true;
		if (human) {
			fireworksUntil = square.getGameTime() + FIREWORKS_WAIT;
		}
		flushWinner(square, square.getGameTime());
	}

	private static void flushWinner(ServerLevel square, long now) {
		if (boardPending && writeBoard(square)) {
			boardPending = false;
		}
		if (fireworksUntil != Long.MIN_VALUE) {
			if (now > fireworksUntil) {
				fireworksUntil = Long.MIN_VALUE;
			} else if (fireworks(square)) {
				fireworksUntil = Long.MIN_VALUE;
			}
		}
	}

	/** Write every sign on the winners' board from the saved winners; false (nothing written) while its chunk is unloaded. */
	static boolean writeBoard(ServerLevel square) {
		int[][] spots = VillageDistrict.boardSigns();
		if (!square.isLoaded(new BlockPos(spots[0][0], spots[0][1], spots[0][2]))) {
			return false;
		}
		SquareData data = SquareData.get(square);
		for (int c = 0; c < 4; c++) {
			RaceClass rc = RaceClass.byId(c);
			CompoundTag w = data.winner(c);
			Component head = Component.translatable("chocobosreborn.board.class", rc.name());
			Component[] lines;
			if (w.isEmpty()) {
				lines = new Component[]{head, Component.translatable("chocobosreborn.board.none.0"),
						Component.translatable("chocobosreborn.board.none.1"), Component.empty()};
			} else {
				int s = (int) Math.round(w.getDouble("Seconds"));
				lines = new Component[]{head, Component.literal(w.getString("Rider")),
						Component.translatable("chocobosreborn.board.on", birdName(w.getString("Bird"))),
						Component.translatable("chocobosreborn.track." + w.getString("Track")).append(" " + HeatSchedule.clock(s))};
			}
			put(square, spots[c], lines);
		}
		put(square, spots[4], new Component[]{Component.translatable("chocobosreborn.board.title.0"),
				Component.translatable("chocobosreborn.board.title.1"), Component.empty(), Component.empty()});
		return true;
	}

	/** A named bird's name, or "#key" for an unnamed bird's colour, translated on the client. */
	private static Component birdName(String stored) {
		return stored.startsWith("#") ? Component.translatable(stored.substring(1)) : Component.literal(stored);
	}

	private static void put(ServerLevel square, int[] at, Component[] lines) {
		BlockPos pos = new BlockPos(at[0], at[1], at[2]);
		SquareBuilder.set(square, at[0], at[1], at[2], "oak_wall_sign[facing=north]");
		if (square.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SquareBuilder.writeSign(sign, lines);
		}
	}

	/** Five rockets over the plaza in the tribe colours, if anyone is there to see them (false: not loaded yet). */
	static boolean fireworks(ServerLevel square) {
		BlockPos centre = new BlockPos(VillageLayout.PX, SquareBuilder.GROUND_Y + 1, VillageLayout.PZ);
		if (!square.areEntitiesLoaded(ChunkPos.asLong(centre))) {
			return false;
		}
		int[] colours = {0xF2C12E, 0x3C8DDE, 0x4F9A5A, 0xE0662A, 0xFFFFFF};
		for (int i = 0; i < 5; i++) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			FireworkExplosion burst = new FireworkExplosion(i % 2 == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
					IntList.of(colours[i], colours[(i + 2) % colours.length]), IntList.of(0xFFF6D0), true, i == 4);
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + i % 2, List.of(burst)));
			double a = i * Math.PI * 2.0D / 5.0D;
			FireworkRocketEntity e = new FireworkRocketEntity(square, centre.getX() + 0.5D + Math.cos(a) * 6.0D,
					centre.getY(), centre.getZ() + 0.5D + Math.sin(a) * 6.0D, rocket);
			square.addFreshEntity(e);
		}
		return true;
	}
}
