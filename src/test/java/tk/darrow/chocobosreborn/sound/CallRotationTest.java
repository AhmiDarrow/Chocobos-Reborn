package tk.darrow.chocobosreborn.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CallRotationTest {
	@Test
	void eachBlowIsTheNextCallAndWrapsAround() {
		CallRotation calls = new CallRotation();
		UUID player = UUID.randomUUID();
		int[] heard = new int[9];
		for (int i = 0; i < heard.length; i++) {
			heard[i] = calls.next(player, 4);
		}
		assertEquals("[0, 1, 2, 3, 0, 1, 2, 3, 0]", java.util.Arrays.toString(heard));
	}

	@Test
	void playersKeepTheirOwnTurn() {
		CallRotation calls = new CallRotation();
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		calls.next(a, 4);
		calls.next(a, 4);
		assertEquals(0, calls.next(b, 4));
		assertEquals(2, calls.next(a, 4));
	}
}
