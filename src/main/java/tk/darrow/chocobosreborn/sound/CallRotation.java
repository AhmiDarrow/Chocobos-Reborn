package tk.darrow.chocobosreborn.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which of an instrument's calls each player plays next. Calls go round in order, so two blows in a
 * row never sound the same. Server thread only; the turn is forgotten on restart, which nobody hears.
 */
public final class CallRotation {
	private final Map<UUID, Integer> next = new HashMap<>();

	/** The call to play now, 0 until {@code calls - 1}, and moves this player on to the next one. */
	public int next(UUID player, int calls) {
		int turn = Math.floorMod(next.getOrDefault(player, 0), calls);
		next.put(player, (turn + 1) % calls);
		return turn;
	}
}
