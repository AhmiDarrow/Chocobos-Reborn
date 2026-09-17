package tk.darrow.chocobosreborn.race;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.block.ModBlocks;
import tk.darrow.chocobosreborn.block.SquareGateBlock;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.KinStewardEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;

/**
 * Places Chocobo Square in the Square dimension: the paddock village (once
 * per {@link #PADDOCK_VERSION}), the keepers (exactly one per
 * {@link TownPosts} post, re-checked on every entry), and the current course
 * islands (each course is built once, at its own centre in the void; see
 * {@link RaceTrack#centerX()}). The village is a sky island of its own (VillagePlan).
 */
public final class SquareBuilder {
	static final int GROUND_Y = 64;
	/** The village: z from PADDOCK_Z0 (return gate) to PADDOCK_Z1 (the race arch), x within ±PADDOCK_HALF_W. */
	public static final int PADDOCK_Z0 = -104;
	public static final int PADDOCK_Z1 = -40;
	public static final int PADDOCK_HALF_W = 48;
	/**
	 * The village is laid ONCE per save: SquareData remembers this version and
	 * buildPaddock returns at once while it matches (Ahmi: "once Whiskerwind has been
	 * generated in a save it should not regenerate every time a player comes"). Bump it
	 * only for a deliberate village change; every bump scrubs and relays the village and
	 * clears the built-course set on the next visit.
	 */
	public static final int PADDOCK_VERSION = 11;
	/**
	 * Bump when RaceCourseLayout changes (arrow, kerbs, stands...): built islands are
	 * relaid on their next use, in place, without touching the village.
	 */
	public static final int COURSE_VERSION = 2;

	private static final Map<String, BlockState> STATES = new HashMap<>();
	/** Birds that live in the village (untamable scenery). */
	private static final int TOWN_BIRDS = 5;
	private static final tk.darrow.chocobosreborn.breed.ChocoboColor[] TOWN_COLOURS = {
			tk.darrow.chocobosreborn.breed.ChocoboColor.YELLOW, tk.darrow.chocobosreborn.breed.ChocoboColor.GREEN,
			tk.darrow.chocobosreborn.breed.ChocoboColor.BLUE, tk.darrow.chocobosreborn.breed.ChocoboColor.YELLOW,
			tk.darrow.chocobosreborn.breed.ChocoboColor.WHITE};

	private SquareBuilder() {
	}

	/** "minecraft:oak_stairs[facing=south]" style lookup, cached. Unknown blocks fall back to stone with a warning. */
	private static BlockState state(ServerLevel level, String id) {
		BlockState cached = STATES.get(id);
		if (cached != null) {
			return cached;
		}
		BlockState s;
		try {
			String full = id.contains(":") ? id : "minecraft:" + id;
			s = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), full, false).blockState();
		} catch (Exception e) {
			ChocobosReborn.LOGGER.warn("Square palette block missing: {}", id);
			s = Blocks.STONE.defaultBlockState();
		}
		STATES.put(id, s);
		return s;
	}

	static void set(ServerLevel level, int x, int y, int z, String id) {
		level.setBlock(new BlockPos(x, y, z), state(level, id), 2);
	}

	static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, String id) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(level, x, y, z, id);
				}
			}
		}
	}

	// ----------------------------------------------------------------- course

	/** Lay the course island for {@code track} once; every course has its own place in the void. */
	public static void buildTrack(ServerLevel level, RaceTrack track) {
		SquareData data = SquareData.get(level);
		if (data.courseVersion() < COURSE_VERSION) {
			// the course plan changed since these islands were laid: relay each on its next use
			data.clearBuilt();
			data.setCourseVersion(COURSE_VERSION);
		}
		if (data.isBuilt(track)) {
			return;
		}
		Map<RaceCourseLayout.Cell, String> plan = RaceCourseLayout.of(track).blocks();
		for (Map.Entry<RaceCourseLayout.Cell, String> e : plan.entrySet()) {
			RaceCourseLayout.Cell c = e.getKey();
			level.setBlock(new BlockPos(c.x(), c.y(), c.z()), state(level, e.getValue()), 2);
		}
		data.setBuilt(track);
		ChocobosReborn.LOGGER.info("Whiskerwind: built {} ({} blocks, {} chunks)", track.id(), plan.size(),
				RaceCourseLayout.of(track).chunks().size());
	}

	// ---------------------------------------------------------------- village

	/**
	 * Whiskerwind: an organic sky island (VillagePlan) with the plaza round the
	 * arrival medallion, the chocobo fountain, market stalls, cottages, the inn and
	 * bell tower, the stable yard, a windmill, the race arch with Esther and a
	 * viewing pier over the void, waterfalls and a shrine islet. Built once per
	 * {@link #PADDOCK_VERSION}.
	 */
	public static void buildPaddock(ServerLevel level) {
		SquareData data = SquareData.get(level);
		if (data.paddockVersion() >= PADDOCK_VERSION) {
			return;
		}
		int y = GROUND_Y;
		// clear the whole footprint (an older village may stand here), then the island
		int r = VillagePlan.RADIUS + 12;
		fill(level, VillagePlan.CX - r, y - 20, VillagePlan.CZ - r, VillagePlan.CX + r, y + 30, VillagePlan.CZ + r + 30, "air");
		fill(level, 44, y - 12, -72, 66, y + 12, -48, "air");
		VillagePlan.island(level);
		VillagePlan.plaza(level);
		VillagePlan.roads(level, PADDOCK_Z0, PADDOCK_Z1);
		VillagePlan.fountain(level, VillagePlan.PX, VillagePlan.FOUNTAIN_Z);
		// homes and halls
		VillageBuildings.cottage(level, -30, -98, VillageBuildings.INFILL, "spruce_stairs", "spruce_slab[type=bottom]");
		VillageBuildings.cottage(level, 30, -98, VillageBuildings.INFILL_ALT, "dark_oak_stairs", "dark_oak_slab[type=bottom]");
		VillageBuildings.cottage(level, -30, -48, VillageBuildings.INFILL_ALT, "deepslate_tile_stairs", "deepslate_tile_slab[type=bottom]");
		VillageBuildings.cottage(level, 30, -50, VillageBuildings.INFILL, "cherry_stairs", "cherry_slab[type=bottom]");
		VillageBuildings.inn(level, -26, -74);
		VillageBuildings.stable(level, 28, -82);
		VillageBuildings.windmill(level, -34, -86);
		VillageBuildings.raceHall(level, 26, -58);
		// stalls around the plaza (with striped awnings) and the desks by the south street
		for (TownPosts.KeeperPost post : TownPosts.keeperPosts()) {
			if (post.role().shops() || post.role() == TownRole.BOOKIE) {
				booth(level, post);
				int fx = (int) Math.round(-Math.sin(Math.toRadians(post.yaw())));
				int fz = (int) Math.round(Math.cos(Math.toRadians(post.yaw())));
				VillageBuildings.awning(level, (int) Math.floor(post.x()), (int) Math.floor(post.z()), fx, fz, -fz, fx, post.role().banner());
			}
		}
		for (TownPosts.KeeperPost post : TownPosts.keeperPosts()) {
			if (post.role() == TownRole.DUEL || post.role() == TownRole.BROKER) {
				desk(level, post);
			}
		}
		// trees and gardens where the streets leave room
		for (int[] t : new int[][]{{-12, -44}, {12, -44}, {-20, -58}, {-14, -88}, {14, -88}, {-40, -66}, {38, -70}}) {
			if (VillagePlan.onIsland(t[0], t[1])) {
				VillagePlan.cherry(level, t[0], t[1]);
			}
		}
		for (int[] t : new int[][]{{-8, -100}, {8, -100}, {-38, -78}, {38, -86}, {22, -100}, {-22, -100}}) {
			if (VillagePlan.onIsland(t[0], t[1])) {
				tree(level, t[0], y, t[1]);
			}
		}
		for (int[] g : new int[][]{{-6, -96}, {6, -96}, {-24, -62}, {24, -62}}) {
			garden(level, g[0], y, g[1]);
		}
		// the race arch with Esther, the pier beyond it, the return portal, the shrine islet
		VillagePlan.overlook(level, PADDOCK_Z1);
		arch(level, y);
		VillagePlan.returnGate(level, PADDOCK_Z0);
		VillagePlan.shrineIslet(level, 56, -60, 40, -60);
		data.setPaddockVersion(PADDOCK_VERSION);
		data.clearBuilt();   // the scrub can take a course lamp with it; lay the courses again
		ChocobosReborn.LOGGER.info("Chocobo Square: village v{} built", PADDOCK_VERSION);
	}

	/** A desk for the duel master / broker: a lectern on a paved pad with a banner and lantern, open in front. */
	private static void desk(ServerLevel level, TownPosts.KeeperPost post) {
		int y = GROUND_Y;
		int px = (int) Math.floor(post.x()), pz = (int) Math.floor(post.z());
		int fx = (int) Math.round(-Math.sin(Math.toRadians(post.yaw())));
		int fz = (int) Math.round(Math.cos(Math.toRadians(post.yaw())));
		String facing = fx > 0 ? "east" : fx < 0 ? "west" : fz > 0 ? "south" : "north";
		fill(level, px - 2, y, pz - 2, px + 2, y, pz + 2, "cut_sandstone");
		set(level, px, y, pz, "chiseled_sandstone");
		set(level, px + fx, y + 1, pz + fz, "lectern[facing=" + facing + "]");
		set(level, px - fx * 2, y + 1, pz - fz * 2, "stripped_oak_log");
		set(level, px - fx * 2, y + 2, pz - fz * 2, "stripped_oak_log");
		set(level, px - fx * 2, y + 3, pz - fz * 2, post.role().banner() + "_banner[rotation=8]");
		set(level, px - fx * 2 + fz * 2, y + 1, pz - fz * 2 - fx * 2, "oak_fence");
		set(level, px - fx * 2 + fz * 2, y + 2, pz - fz * 2 - fx * 2, "lantern[hanging=false]");
	}

	private static void wallOrLamp(ServerLevel level, int x, int y, int z, boolean lamp) {
		if (lamp) {
			set(level, x, y + 1, z, "cut_sandstone");
			set(level, x, y + 2, z, "oak_fence");
			set(level, x, y + 3, z, "oak_fence");
			set(level, x, y + 4, z, "lantern[hanging=false]");
		} else {
			set(level, x, y + 1, z, "sandstone_wall");
		}
	}

	/** A waxed oak wall sign with four translated lines, hung on the block behind it. */
	static void sign(ServerLevel level, int x, int y, int z, String facing, String... lines) {
		set(level, x, y, z, "oak_wall_sign[facing=" + facing + "]");
		if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
			writeSign(sign, lines);
		}
	}

	static void writeSign(net.minecraft.world.level.block.entity.SignBlockEntity sign, String... lines) {
		net.minecraft.world.level.block.entity.SignText text = sign.getFrontText();
		for (int i = 0; i < Math.min(4, lines.length); i++) {
			text = text.setMessage(i, net.minecraft.network.chat.Component.translatable(lines[i]));
		}
		sign.setText(text, true);
		sign.setWaxed(true);
	}

	/** A four-post stall: counter in front of the keeper, shelf and banner behind, slab roof with a lantern. */
	private static void booth(ServerLevel level, TownPosts.KeeperPost post) {
		int y = GROUND_Y;
		int px = (int) Math.floor(post.x());
		int pz = (int) Math.floor(post.z());
		// forward unit vector from yaw (0 = +z, 90 = -x, -90 = +x, 180 = -z)
		int fx = (int) Math.round(-Math.sin(Math.toRadians(post.yaw())));
		int fz = (int) Math.round(Math.cos(Math.toRadians(post.yaw())));
		int rx = -fz, rz = fx; // right-hand vector
		String facing = fx > 0 ? "east" : fx < 0 ? "west" : fz > 0 ? "south" : "north";
		String backFacing = fx > 0 ? "west" : fx < 0 ? "east" : fz > 0 ? "north" : "south";
		String banner = post.role().banner() + "_wall_banner[facing=" + facing + "]";
		// footprint: front (counter) = +1f, keeper = 0, shelf = -1f, back wall = -2f; width ±2 right
		for (int w = -2; w <= 2; w++) {
			for (int d = -2; d <= 1; d++) {
				int x = px + fx * d + rx * w, z = pz + fz * d + rz * w;
				set(level, x, y, z, d == 0 && w == 0 ? "chiseled_sandstone" : "cut_sandstone");
				set(level, x, y + 4, z, "oak_slab[type=bottom]");
				fill(level, x, y + 1, z, x, y + 3, z, "air");
			}
		}
		// posts
		for (int w : new int[]{-2, 2}) {
			for (int d : new int[]{-2, 1}) {
				fill(level, px + fx * d + rx * w, y + 1, pz + fz * d + rz * w, px + fx * d + rx * w, y + 3,
						pz + fz * d + rz * w, "stripped_oak_log");
			}
			// roof edge trims as stairs facing outward on the sides
			int x = px + fx * 1 + rx * w, z = pz + fz * 1 + rz * w;
			set(level, x, y + 4, z, "oak_stairs[facing=" + backFacing + ",half=top]");
		}
		// back wall with a shelf, barrel, banner and a hanging lantern
		for (int w = -1; w <= 1; w++) {
			fill(level, px - fx * 2 + rx * w, y + 1, pz - fz * 2 + rz * w, px - fx * 2 + rx * w, y + 3,
					pz - fz * 2 + rz * w, "oak_planks");
		}
		set(level, px - fx * 2, y + 2, pz - fz * 2, "bookshelf");
		set(level, px - fx + rx, y + 1, pz - fz + rz, "barrel[facing=up]");
		set(level, px - fx - rx, y + 1, pz - fz - rz, "barrel[facing=up]");
		set(level, px - fx - rx, y + 2, pz - fz - rz, "oak_slab[type=bottom]");
		set(level, px - fx, y + 2, pz - fz, banner);
		set(level, px, y + 3, pz, "lantern[hanging=true]");
		// counter: slabs across the front, a gap-free row so the keeper stays in the booth
		for (int w = -1; w <= 1; w++) {
			set(level, px + fx + rx * w, y + 1, pz + fz + rz * w, "oak_trapdoor[facing=" + facing + ",half=top,open=false]");
		}
		set(level, px + fx, y + 1, pz + fz, "smooth_sandstone_slab[type=top]");
		// role dressing on the counter
		String prop = switch (post.role()) {
			case GREENS -> "potted_fern";
			case TREATS -> "potted_oak_sapling";
			case TACK -> "lantern[hanging=false]";
			case FAIR -> "potted_pink_tulip";
			case EXCHANGE -> "gold_block";
			case BOOKIE -> "lectern[facing=" + facing + "]";
			default -> "air";
		};
		if (post.role() == TownRole.EXCHANGE) {
			set(level, px - fx + rx, y + 2, pz - fz + rz, "gold_block");
		} else if (post.role() == TownRole.BOOKIE) {
			set(level, px + fx, y + 1, pz + fz, prop);
		} else {
			set(level, px + fx + rx, y + 2, pz + fz + rz, prop);
		}
	}

	/** Sandstone archway over the course gates on the north edge; Esther stands beneath it. */
	/** The gatehouse: two crenellated sandstone towers with quartz corners, the archway, gold trim, banners, signs and Esther's dais. */
	private static void arch(ServerLevel level, int y) {
		int z = PADDOCK_Z1;
		for (int sx : new int[]{-1, 1}) {
			int x0 = sx * 8, x1 = sx * 11;
			int lo = Math.min(x0, x1), hi = Math.max(x0, x1);
			fill(level, lo, y + 1, z - 2, hi, y + 11, z + 1, "cut_sandstone");
			fill(level, lo + 1, y + 1, z - 1, hi - 1, y + 10, z, "air");
			for (int x : new int[]{lo, hi}) {
				for (int zz : new int[]{z - 2, z + 1}) {
					fill(level, x, y + 1, zz, x, y + 12, zz, "quartz_pillar");
				}
			}
			fill(level, lo, y + 12, z - 2, hi, y + 12, z + 1, "smooth_sandstone");
			for (int x = lo; x <= hi; x++) {
				for (int zz : new int[]{z - 2, z + 1}) {
					set(level, x, y + 13, zz, ((x + zz) & 1) == 0 ? "sandstone_wall" : "air");
				}
			}
			set(level, lo, y + 13, z - 2, "gold_block");
			set(level, hi, y + 13, z - 2, "gold_block");
			set(level, lo, y + 13, z + 1, "gold_block");
			set(level, hi, y + 13, z + 1, "gold_block");
			set(level, (lo + hi) / 2, y + 14, z - 1, "yellow_banner[rotation=8]");
			for (int h = 3; h <= 9; h += 3) {
				set(level, (lo + hi) / 2, y + h, z + 1, "glass_pane");
				set(level, (lo + hi) / 2, y + h, z - 2, "glass_pane");
			}
			set(level, lo + 1, y + 6, z + 1, "lantern[hanging=false]");
			set(level, hi - 1, y + 6, z + 1, "lantern[hanging=false]");
		}
		// the archway: beam, gold trim line, hanging lanterns, the town banner
		fill(level, -7, y + 7, z - 1, 7, y + 7, z, "smooth_sandstone");
		fill(level, -7, y + 8, z - 1, 7, y + 8, z, "cut_sandstone");
		fill(level, -7, y + 9, z - 1, 7, y + 9, z, "smooth_sandstone_slab[type=bottom]");
		fill(level, -7, y + 6, z, 7, y + 6, z, "yellow_glazed_terracotta");
		set(level, 0, y + 9, z, "gold_block");
		set(level, 0, y + 10, z, "yellow_banner[rotation=8]");
		set(level, -7, y + 6, z, "sandstone_stairs[facing=east,half=top]");
		set(level, 7, y + 6, z, "sandstone_stairs[facing=west,half=top]");
		for (int x : new int[]{-5, 0, 5}) {
			set(level, x, y + 6, z - 1, "lantern[hanging=true]");
		}
		set(level, -7, y + 4, z - 2, "yellow_wall_banner[facing=north]");
		set(level, 7, y + 4, z - 2, "yellow_wall_banner[facing=north]");
		for (int x : new int[]{-6, -5, 5, 6}) {
			set(level, x, y + 1, z, "sandstone_wall");
		}
		// Esther works from the overlook beyond the arch (Ahmi): her dais and the standing signs are there
		int ez = z + 7;
		fill(level, -2, y, ez - 2, 2, y, ez + 2, "chiseled_sandstone");
		for (int x = -2; x <= 2; x++) {
			set(level, x, y, ez - 2, "gold_block");
			set(level, x, y, ez + 2, "gold_block");
		}
		for (int sx : new int[]{-1, 1}) {
			set(level, sx * 4, y + 1, ez, "stripped_oak_log");
			set(level, sx * 4, y + 2, ez, "oak_sign[rotation=8]");
		}
		if (level.getBlockEntity(new BlockPos(-4, y + 2, ez)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity s1) {
			writeSign(s1, "chocobosreborn.sign.heat.0", "chocobosreborn.sign.heat.1", "chocobosreborn.sign.heat.2", "chocobosreborn.sign.heat.3");
		}
		if (level.getBlockEntity(new BlockPos(4, y + 2, ez)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity s2) {
			writeSign(s2, "chocobosreborn.sign.home.0", "chocobosreborn.sign.home.1", "chocobosreborn.sign.home.2", "chocobosreborn.sign.home.3");
		}
	}

	/** A small planted tree: log trunk in a moss bed, leaf crown, so the plaza isn't bare stone. */
	private static void tree(ServerLevel level, int x, int y, int z) {
		set(level, x, y, z, "moss_block");
		fill(level, x, y + 1, z, x, y + 4, z, "oak_log");
		fill(level, x - 1, y + 3, z - 1, x + 1, y + 5, z + 1, "oak_leaves[persistent=true]");
		fill(level, x - 2, y + 4, z, x + 2, y + 4, z, "oak_leaves[persistent=true]");
		fill(level, x, y + 4, z - 2, x, y + 4, z + 2, "oak_leaves[persistent=true]");
		set(level, x, y + 6, z, "oak_leaves[persistent=true]");
		fill(level, x, y + 1, z, x, y + 4, z, "oak_log");
	}

	/** A 3x3 moss bed with a flowering azalea and a sandstone-wall corner post. */
	private static void garden(ServerLevel level, int cx, int y, int cz) {
		fill(level, cx - 1, y, cz - 1, cx + 1, y, cz + 1, "moss_block");
		set(level, cx, y + 1, cz, "flowering_azalea");
		set(level, cx - 1, y + 1, cz - 1, "dandelion");
		set(level, cx + 1, y + 1, cz + 1, "poppy");
		set(level, cx + 1, y + 1, cz - 1, "oxeye_daisy");
		set(level, cx - 1, y + 1, cz + 1, "cornflower");
	}

	// ---------------------------------------------------------------- keepers

	/** Ticks left of deferred keeper syncing (chunks around the village load a few ticks after entry). */
	private static int pendingSync;

	/** Server stop: nothing pending for the next server in this JVM. */
	public static void resetPending() {
		pendingSync = 0;
	}

	/** Ask for a keeper sync on the next ticks, until every post's chunk has its entities loaded. */
	public static void requestKeeperSync() {
		pendingSync = 20 * 30;
	}

	/** Server tick hook: runs the sync every second while requested. */
	public static void tickKeeperSync(ServerLevel square) {
		if (pendingSync <= 0) {
			return;
		}
		pendingSync--;
		if (pendingSync % 20 == 0 && spawnKeepers(square)) {
			pendingSync = 0;
		}
	}

	/**
	 * Exactly one keeper per post. Posts whose chunk has no entities loaded yet are
	 * skipped (placing blind is how duplicates happen); the rest get a missing keeper
	 * placed, extras removed and wanderers walked back. Race birds left over from an
	 * interrupted heat are removed too.
	 *
	 * @return true when every post was checked
	 */
	public static boolean spawnKeepers(ServerLevel level) {
		SquareData data = SquareData.get(level);
		AABB village = new AABB(-PADDOCK_HALF_W - 8, GROUND_Y - 4, PADDOCK_Z0 - 8, PADDOCK_HALF_W + 8, GROUND_Y + 20,
				PADDOCK_Z1 + 60);
		List<KinStewardEntity> kin = level.getEntities(ModEntities.KIN_STEWARD.get(), village, e -> !e.isRemoved());
		int removed = 0;
		boolean complete = true;
		// Match kin to posts per role (a role can have several posts, e.g. the fans in
		// the stands): each post takes the nearest unclaimed kin of its role; kin left
		// over are duplicates; posts left over get a fresh keeper.
		java.util.Set<KinStewardEntity> claimed = new java.util.HashSet<>();
		java.util.Set<TownRole> rolesHere = new java.util.HashSet<>();
		for (TownPosts.KeeperPost post : TownPosts.keeperPosts()) {
			rolesHere.add(post.role());
			BlockPos at = BlockPos.containing(post.x(), post.y(), post.z());
			if (!level.areEntitiesLoaded(ChunkPos.asLong(at))) {
				complete = false;
				continue;
			}
			KinStewardEntity keep = null;
			double best = Double.MAX_VALUE;
			for (KinStewardEntity k : kin) {
				if (k.role() != post.role() || claimed.contains(k)) {
					continue;
				}
				double d = k.distanceToSqr(post.x(), post.y(), post.z());
				if (d < best) {
					best = d;
					keep = k;
				}
			}
			if (keep == null) {
				place(level, post.role(), post.x(), post.y(), post.z(), post.yaw());
			} else {
				claimed.add(keep);
				if (best > 9.0D) {
					keep.moveTo(post.x(), post.y(), post.z(), post.yaw(), 0.0F);
				}
			}
		}
		if (complete) {
			for (KinStewardEntity k : kin) {
				if (rolesHere.contains(k.role()) && !claimed.contains(k)) {
					k.discard();
					removed++;
				}
			}
		}
		// Kin with no post here (farm roles that somehow got in) and race birds left over from an interrupted heat.
		for (KinStewardEntity k : kin) {
			if (TownPosts.keeperPosts().stream().noneMatch(p -> p.role() == k.role()) && !k.isRemoved()) {
				k.discard();
				removed++;
			}
		}
		for (ChocoboEntity bird : level.getEntities(ModEntities.CHOCOBO.get(), village.inflate(80.0D), ChocoboEntity::raceNpc)) {
			if (!RaceManager.isActiveRacer(bird.getUUID())) {
				bird.discard();
				removed++;
			}
		}
		// the town's own birds: a few wander the village, never tamable, respawned when lost
		if (complete) {
			List<ChocoboEntity> town = level.getEntities(ModEntities.CHOCOBO.get(), village, ChocoboEntity::townBird);
			for (int i = town.size(); i < TOWN_BIRDS; i++) {
				ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
				if (bird == null) {
					break;
				}
				double x = (i % 2 == 0 ? -1 : 1) * (14 + i * 3), z = -70 - (i % 3) * 8;
				bird.moveTo(x + 0.5D, GROUND_Y + 1, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
				bird.finalizeSpawn(level, level.getCurrentDifficultyAt(bird.blockPosition()), net.minecraft.world.entity.MobSpawnType.EVENT, null);
				bird.setColor(TOWN_COLOURS[i % TOWN_COLOURS.length]);
				bird.setTownBird(true);
				bird.setPersistenceRequired();
				bird.restrictTo(new BlockPos(0, GROUND_Y, -72), 30);
				level.addFreshEntity(bird);
			}
		}
		if (removed > 0) {
			ChocobosReborn.LOGGER.info("Chocobo Square: removed {} duplicate keepers / stray racers", removed);
		}
		if (complete) {
			data.setKeepersSpawned(true);
		}
		return complete;
	}

	private static void place(ServerLevel level, TownRole role, double x, double y, double z, float yaw) {
		KinStewardEntity kin = ModEntities.KIN_STEWARD.get().create(level);
		if (kin == null) {
			return;
		}
		kin.moveTo(x, y, z, yaw, 0.0F);
		kin.setRole(role);
		kin.setPersistenceRequired();
		level.addFreshEntity(kin);
	}

	/** Entities the village owns, for tests. */
	static boolean inVillage(Entity e) {
		return e.getX() >= -PADDOCK_HALF_W && e.getX() <= PADDOCK_HALF_W && e.getZ() >= PADDOCK_Z0 && e.getZ() <= PADDOCK_Z1;
	}
}
