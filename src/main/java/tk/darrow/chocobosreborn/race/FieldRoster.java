package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

/**
 * Named AI regulars for Whiskerwind heats. Each class has a home roster;
 * five of C, B and A also guest one class up. Teiyo and Jolo are not in this
 * pool: they are the bookie's named rivals from Class B on.
 */
public final class FieldRoster {
	public record Entry(String id, String name, RaceClass home, boolean crossUp, ChocoboColor color) {
		public boolean eligible(RaceClass heat) {
			if (heat == home) {
				return true;
			}
			return crossUp && home != RaceClass.S && home.next() == heat;
		}
	}

	private static final Entry[] ALL = {
			// Class C: pad-runners. First five also appear on the B card.
			e("biggs", "Biggs", RaceClass.C, true, ChocoboColor.YELLOW),
			e("wedge", "Wedge", RaceClass.C, true, ChocoboColor.YELLOW),
			e("broden", "Broden", RaceClass.C, true, ChocoboColor.YELLOW),
			e("kunsel", "Kunsel", RaceClass.C, true, ChocoboColor.YELLOW),
			e("denzel", "Denzel", RaceClass.C, true, ChocoboColor.YELLOW),
			e("marlene", "Marlene", RaceClass.C, false, ChocoboColor.YELLOW),
			e("priscilla", "Priscilla", RaceClass.C, false, ChocoboColor.YELLOW),
			e("johnny", "Johnny", RaceClass.C, false, ChocoboColor.YELLOW),
			e("godo", "Godo", RaceClass.C, false, ChocoboColor.YELLOW),
			e("shera", "Shera", RaceClass.C, false, ChocoboColor.YELLOW),
			e("ward", "Ward", RaceClass.C, false, ChocoboColor.YELLOW),
			e("zone", "Zone", RaceClass.C, false, ChocoboColor.YELLOW),
			e("rin", "Rin", RaceClass.C, false, ChocoboColor.YELLOW),
			e("jessie", "Jessie", RaceClass.C, false, ChocoboColor.YELLOW),
			e("billy", "Billy", RaceClass.C, false, ChocoboColor.YELLOW),
			e("selphie", "Selphie", RaceClass.C, false, ChocoboColor.YELLOW),

			// Class B. First five also appear on the A card.
			e("yuffie", "Yuffie", RaceClass.B, true, ChocoboColor.GREEN),
			e("barret", "Barret", RaceClass.B, true, ChocoboColor.GREEN),
			e("cid", "Cid", RaceClass.B, true, ChocoboColor.YELLOW),
			e("vincent", "Vincent", RaceClass.B, true, ChocoboColor.BLACK),
			e("cait", "Cait", RaceClass.B, true, ChocoboColor.YELLOW),
			e("tifa", "Tifa", RaceClass.B, false, ChocoboColor.YELLOW),
			e("aerith", "Aerith", RaceClass.B, false, ChocoboColor.GREEN),
			e("reno", "Reno", RaceClass.B, false, ChocoboColor.BLACK),
			e("rude", "Rude", RaceClass.B, false, ChocoboColor.BLACK),
			e("elena", "Elena", RaceClass.B, false, ChocoboColor.WHITE),
			e("tseng", "Tseng", RaceClass.B, false, ChocoboColor.BLACK),
			e("reeve", "Reeve", RaceClass.B, false, ChocoboColor.WHITE),
			e("rufus", "Rufus", RaceClass.B, false, ChocoboColor.WHITE),
			e("scarlet", "Scarlet", RaceClass.B, false, ChocoboColor.WHITE),
			e("palmer", "Palmer", RaceClass.B, false, ChocoboColor.YELLOW),
			e("nanaki", "Nanaki", RaceClass.B, false, ChocoboColor.BLACK),

			// Class A. First five also appear on the S card.
			e("squall", "Squall", RaceClass.A, true, ChocoboColor.BLACK),
			e("rinoa", "Rinoa", RaceClass.A, true, ChocoboColor.WHITE),
			e("zell", "Zell", RaceClass.A, true, ChocoboColor.YELLOW),
			e("laguna", "Laguna", RaceClass.A, true, ChocoboColor.BLUE),
			e("kiros", "Kiros", RaceClass.A, true, ChocoboColor.BLACK),
			e("seifer", "Seifer", RaceClass.A, false, ChocoboColor.BLACK),
			e("edea", "Edea", RaceClass.A, false, ChocoboColor.WHITE),
			e("zidane", "Zidane", RaceClass.A, false, ChocoboColor.YELLOW),
			e("garnet", "Garnet", RaceClass.A, false, ChocoboColor.WHITE),
			e("vivi", "Vivi", RaceClass.A, false, ChocoboColor.BLACK),
			e("steiner", "Steiner", RaceClass.A, false, ChocoboColor.YELLOW),
			e("freya", "Freya", RaceClass.A, false, ChocoboColor.BLUE),
			e("quina", "Quina", RaceClass.A, false, ChocoboColor.GREEN),
			e("amarant", "Amarant", RaceClass.A, false, ChocoboColor.BLACK),
			e("kuja", "Kuja", RaceClass.A, false, ChocoboColor.WHITE),
			e("beatrix", "Beatrix", RaceClass.A, false, ChocoboColor.WHITE),

			// Class S: legends. No class above to guest in.
			e("lightning", "Lightning", RaceClass.S, false, ChocoboColor.WHITE),
			e("snow", "Snow", RaceClass.S, false, ChocoboColor.WHITE),
			e("yuna", "Yuna", RaceClass.S, false, ChocoboColor.WHITE),
			e("tidus", "Tidus", RaceClass.S, false, ChocoboColor.YELLOW),
			e("auron", "Auron", RaceClass.S, false, ChocoboColor.BLACK),
			e("wakka", "Wakka", RaceClass.S, false, ChocoboColor.YELLOW),
			e("lulu", "Lulu", RaceClass.S, false, ChocoboColor.BLACK),
			e("rikku", "Rikku", RaceClass.S, false, ChocoboColor.GREEN),
			e("jecht", "Jecht", RaceClass.S, false, ChocoboColor.BLACK),
			e("hope", "Hope", RaceClass.S, false, ChocoboColor.WHITE),
			e("fang", "Fang", RaceClass.S, false, ChocoboColor.BLACK),
			e("vanille", "Vanille", RaceClass.S, false, ChocoboColor.YELLOW),
			e("noel", "Noel", RaceClass.S, false, ChocoboColor.BLACK),
			e("serah", "Serah", RaceClass.S, false, ChocoboColor.WHITE),
			e("paine", "Paine", RaceClass.S, false, ChocoboColor.BLACK),
			e("kimahri", "Kimahri", RaceClass.S, false, ChocoboColor.BLUE),
	};

	private FieldRoster() {
	}

	private static Entry e(String id, String name, RaceClass home, boolean crossUp, ChocoboColor color) {
		return new Entry(id, name, home, crossUp, color);
	}

	public static List<Entry> all() {
		return List.of(ALL);
	}

	public static List<Entry> home(RaceClass heat) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : ALL) {
			if (e.home() == heat) {
				out.add(e);
			}
		}
		return out;
	}

	public static List<Entry> eligible(RaceClass heat) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : ALL) {
			if (e.eligible(heat)) {
				out.add(e);
			}
		}
		return out;
	}

	public static int crossUpCount(RaceClass home) {
		int n = 0;
		for (Entry e : ALL) {
			if (e.home() == home && e.crossUp()) {
				n++;
			}
		}
		return n;
	}

	/** Unique names for this heat, shuffled. Never repeats a name on the same card. */
	public static List<Entry> draw(RaceClass heat, int count, Random random) {
		List<Entry> pool = eligible(heat);
		if (count <= 0) {
			return List.of();
		}
		Collections.shuffle(pool, random);
		int n = Math.min(count, pool.size());
		return List.copyOf(pool.subList(0, n));
	}

	public static Set<String> names() {
		Set<String> out = new LinkedHashSet<>();
		for (Entry e : ALL) {
			out.add(e.name());
		}
		return out;
	}
}
