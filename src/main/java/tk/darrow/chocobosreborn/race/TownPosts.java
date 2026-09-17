package tk.darrow.chocobosreborn.race;

import java.util.List;

/** Where each Square keeper stands (Square dimension, Whiskerwind). Booths / desks are built around these; the crowd stands on the courses. */
public final class TownPosts {
	public record KeeperPost(TownRole role, double x, double y, double z, float yaw) {
	}

	private TownPosts() {
	}

	/** Every keeper in the Square, Esther included, one each. */
	public static List<KeeperPost> keeperPosts() {
		return List.of(
				new KeeperPost(TownRole.STEWARD, 0.5D, 65.0D, SquareBuilder.PADDOCK_Z1 + 7.5D, 180.0F),   // on the overlook beyond the arch, facing the town
				// market stalls round the plaza, each facing the medallion
				new KeeperPost(TownRole.GREENS, -17.5D, 65.0D, -52.5D, -90.0F),
				new KeeperPost(TownRole.TACK, 17.5D, 65.0D, -48.5D, 90.0F),
				new KeeperPost(TownRole.FAIR, -17.5D, 65.0D, -66.5D, -90.0F),
				new KeeperPost(TownRole.TREATS, 17.5D, 65.0D, -66.5D, 90.0F),
				new KeeperPost(TownRole.EXCHANGE, -10.5D, 65.0D, -90.5D, -90.0F),
				new KeeperPost(TownRole.BOOKIE, 10.5D, 65.0D, -90.5D, 90.0F),
				// the duel master and the broker by the south street, open ground in front for riders
				new KeeperPost(TownRole.DUEL, -16.5D, 65.0D, -99.5D, 180.0F),
				new KeeperPost(TownRole.BROKER, 16.5D, 65.0D, -99.5D, 180.0F)
		);
	}
}
