package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each Square keeper stands (Square dimension, Whiskerwind). Booths / desks
 * are built around these; the crowd stands on the courses. Residents are posted at
 * their homes and walk their own day from there (see {@link VillageLayout#residents}).
 */
public final class TownPosts {
	public record KeeperPost(TownRole role, double x, double y, double z, float yaw) {
	}

	private static final List<KeeperPost> POSTS = build();

	private TownPosts() {
	}

	/** Every keeper in the Square, Esther included, one each, then the residents. */
	public static List<KeeperPost> keeperPosts() {
		return POSTS;
	}

	private static List<KeeperPost> build() {
		List<KeeperPost> out = new ArrayList<>(List.of(
				// on the overlook beyond the arch, facing the town
				new KeeperPost(TownRole.STEWARD, 0.5D, 65.0D, VillageLayout.ARCH_Z + 7.5D, 180.0F),
				// market stalls round the plaza, each facing the medallion
				new KeeperPost(TownRole.GREENS, -22.5D, 65.0D, -44.5D, -90.0F),
				new KeeperPost(TownRole.TACK, 22.5D, 65.0D, -44.5D, 90.0F),
				new KeeperPost(TownRole.FAIR, -22.5D, 65.0D, -60.5D, -90.0F),
				new KeeperPost(TownRole.TREATS, 22.5D, 65.0D, -60.5D, 90.0F),
				// either side of the avenue past the fountain
				new KeeperPost(TownRole.EXCHANGE, -15.5D, 65.0D, -97.5D, -90.0F),
				new KeeperPost(TownRole.BOOKIE, 15.5D, 65.0D, -97.5D, 90.0F),
				// the duel master and the broker by the north street, open ground in front for riders
				new KeeperPost(TownRole.DUEL, -24.5D, 65.0D, -110.5D, 180.0F),
				new KeeperPost(TownRole.BROKER, 24.5D, 65.0D, -110.5D, 180.0F)));
		for (VillageLayout.Resident r : VillageLayout.residents()) {
			out.add(new KeeperPost(r.role(), r.homeX(), 65.0D, r.homeZ(), 0.0F));
		}
		return List.copyOf(out);
	}
}
