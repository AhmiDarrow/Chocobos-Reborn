package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who a Chocobo Whistle may call. Pure: no entity, so a unit test can say so
 * without a world. Only a bird on Follow comes: Stay and Wander were put somewhere on purpose and stay
 * there. A bird already in range does not come either.
 */
public final class WhistleRules {
	/** Birds summoned on one blow. Further qualifying birds stay where they are. */
	public static final int CAP = 8;
	/** Same dimension and at most this far: already here, not called. */
	public static final double HERE_BLOCKS = 16.0D;
	/** {@code Facts.command} of a bird on Follow, the only order the whistle calls. */
	public static final int FOLLOW = 0;

	private WhistleRules() {
	}

	public enum Reason {
		CALL, HERE, MOUNTED, DEAD, OWNER, TAME, RECORD, NPC, TOWN, PARKED, RACING, LEASH, BUSY
	}

	/**
	 * One bird as the whistle sees it. {@code command} is Follow 0, Stay 1, Wander 2; only
	 * Follow is called. {@code chick} is not a gate.
	 */
	public record Facts(boolean owned, boolean recordAlive, boolean tame, boolean dead, boolean raceNpc,
	                    boolean townBird, boolean racing, boolean activeRacer, boolean scheduled, boolean leashed,
	                    boolean otherRider, boolean ownerRiding, boolean sameDimension, double distance, int command,
	                    boolean chick) {

		/** A tame owned bird in another dimension: the whistle would call it. */
		public static Facts ready(int command) {
			return new Facts(true, true, true, false, false, false, false, false, false, false, false, false,
					false, 64.0D, command, false);
		}

		public Facts withOwned(boolean owned) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withRecordAlive(boolean recordAlive) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withTame(boolean tame) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withDead(boolean dead) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withRaceNpc(boolean raceNpc) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withTown(boolean townBird) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withRacing(boolean racing) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withActive(boolean activeRacer) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withScheduled(boolean scheduled) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withLeashed(boolean leashed) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withOtherRider(boolean otherRider) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withOwnerRiding(boolean ownerRiding) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withPlace(boolean sameDimension, double distance) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}

		public Facts withChick(boolean chick) {
			return new Facts(owned, recordAlive, tame, dead, raceNpc, townBird, racing, activeRacer, scheduled, leashed,
					otherRider, ownerRiding, sameDimension, distance, command, chick);
		}
	}

	public static Reason reason(Facts facts) {
		if (!facts.owned()) {
			return Reason.OWNER;
		}
		if (!facts.recordAlive()) {
			return Reason.RECORD;
		}
		if (!facts.tame()) {
			return Reason.TAME;
		}
		if (facts.dead()) {
			return Reason.DEAD;
		}
		if (facts.raceNpc()) {
			return Reason.NPC;
		}
		if (facts.townBird()) {
			return Reason.TOWN;
		}
		if (facts.command() != FOLLOW) {
			return Reason.PARKED;
		}
		if (facts.racing() || facts.activeRacer() || facts.scheduled()) {
			return Reason.RACING;
		}
		if (facts.leashed()) {
			return Reason.LEASH;
		}
		if (facts.otherRider()) {
			return Reason.BUSY;
		}
		if (facts.ownerRiding()) {
			return Reason.MOUNTED;
		}
		if (facts.sameDimension() && facts.distance() <= HERE_BLOCKS) {
			return Reason.HERE;
		}
		return Reason.CALL;
	}

	public static boolean eligible(Facts facts) {
		return reason(facts) == Reason.CALL;
	}

	/** Ledger order in, first {@link #CAP} calls out. {@code capped} when more than the cap would come. */
	public record Selection(List<UUID> call, boolean capped, int here, int racing, int busy, int parked) {
	}

	public static Selection select(List<UUID> ids, List<Facts> facts) {
		if (ids.size() != facts.size()) {
			throw new IllegalArgumentException("ids and facts differ");
		}
		List<UUID> call = new ArrayList<>();
		int qualified = 0;
		int here = 0;
		int racing = 0;
		int busy = 0;
		int parked = 0;
		for (int i = 0; i < ids.size(); i++) {
			switch (reason(facts.get(i))) {
				case CALL -> {
					qualified++;
					if (call.size() < CAP) {
						call.add(ids.get(i));
					}
				}
				case HERE, MOUNTED -> here++;
				case RACING -> racing++;
				case BUSY -> busy++;
				case PARKED -> parked++;
				default -> {
				}
			}
		}
		return new Selection(List.copyOf(call), qualified > CAP, here, racing, busy, parked);
	}

	/** Lang key for the blow. A coming bird uses chat; the rest use the action bar. */
	public static String noticeKey(int coming, boolean capped, int here, int racing, int busy, int lost, int parked) {
		if (coming > 0) {
			if (capped) {
				return "chocobosreborn.whistle.capped";
			}
			if (coming == 1) {
				return "chocobosreborn.whistle.called";
			}
			return "chocobosreborn.whistle.called_many";
		}
		if (racing > 0) {
			return "chocobosreborn.whistle.racing";
		}
		if (busy > 0) {
			return "chocobosreborn.whistle.busy";
		}
		if (lost > 0) {
			return "chocobosreborn.whistle.lost";
		}
		if (here > 0) {
			return "chocobosreborn.whistle.here";
		}
		if (parked > 0) {
			return "chocobosreborn.whistle.parked";
		}
		return "chocobosreborn.whistle.none";
	}

	/** Chat the arrival, and still say once that a heat held another bird back. */
	public static boolean alsoRacing(int coming, int racing) {
		return coming > 0 && racing > 0;
	}
}
