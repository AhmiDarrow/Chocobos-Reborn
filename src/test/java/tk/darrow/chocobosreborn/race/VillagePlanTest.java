package tk.darrow.chocobosreborn.race;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagePlanTest {
	@Test
	void paddockVersionThirteenRebuildsTheVillage() {
		assertEquals(14, SquareBuilder.PADDOCK_VERSION);
	}

	@Test
	void theIslandIsHalfAgainTheOldOne() {
		// v12 was a radius of 46 round the same centre (Ahmi: "increase its size by about 50%")
		assertEquals(69, VillageLayout.RADIUS);
		assertEquals(Math.round(46 * 1.5D), VillageLayout.RADIUS);
		assertEquals(VillageLayout.GATE_Z, SquareBuilder.PADDOCK_Z0);
		assertEquals(VillageLayout.ARCH_Z, SquareBuilder.PADDOCK_Z1);
	}

	@Test
	void everyPlotSitsOnTheIslandAndClearOfTheOthers() {
		List<VillageLayout.Plot> plots = VillageLayout.plots();
		for (VillageLayout.Plot p : plots) {
			for (int x : new int[]{p.x0(), p.x1()}) {
				for (int z : new int[]{p.z0(), p.z1()}) {
					assertTrue(VillageLayout.rimInset(x, z) >= 0.5D, p.name() + " corner " + x + "," + z + " off the rim");
				}
			}
			assertFalse(p.x0() <= 3 && p.x1() >= -3 && !p.name().equals("plaza") && !p.name().equals("fountain")
					&& !p.name().equals("gate"), p.name() + " blocks the avenue");
		}
		for (int i = 0; i < plots.size(); i++) {
			for (int j = i + 1; j < plots.size(); j++) {
				assertFalse(plots.get(i).overlaps(plots.get(j)), plots.get(i).name() + " overlaps " + plots.get(j).name());
			}
		}
	}

	@Test
	void keepersStandOnTheirOwnPlots() {
		for (TownPosts.KeeperPost post : TownPosts.keeperPosts()) {
			assertTrue(VillageLayout.onIsland((int) Math.floor(post.x()), (int) Math.floor(post.z())) || post.role() == TownRole.STEWARD,
					post.role() + " off the island");
		}
		var greens = post(TownRole.GREENS);
		assertTrue(plot("greens").contains((int) Math.floor(greens.x()), (int) Math.floor(greens.z())));
		assertTrue(plot("exchange").contains((int) Math.floor(post(TownRole.EXCHANGE).x()), (int) Math.floor(post(TownRole.EXCHANGE).z())));
		assertTrue(plot("bookie").contains((int) Math.floor(post(TownRole.BOOKIE).x()), (int) Math.floor(post(TownRole.BOOKIE).z())));
		assertTrue(plot("duel").contains((int) Math.floor(post(TownRole.DUEL).x()), (int) Math.floor(post(TownRole.DUEL).z())));
		assertEquals(VillageLayout.OVERLOOK_Z + 0.5D, post(TownRole.STEWARD).z(), 1.0E-9);
	}

	@Test
	void everyResidentSleepsInACottageOrTheInnAndHasAPost() {
		for (VillageLayout.Resident r : VillageLayout.residents()) {
			int hx = (int) Math.floor(r.homeX()), hz = (int) Math.floor(r.homeZ());
			boolean housed = plot("inn").contains(hx, hz);
			for (int i = 1; i <= VillageLayout.COTTAGES.length; i++) {
				housed |= plot("cottage" + i).contains(hx, hz);
			}
			assertTrue(housed, r.role() + " sleeps outdoors");
			assertTrue(VillageLayout.onIsland((int) Math.floor(r.workX()), (int) Math.floor(r.workZ())), r.role() + " works off the island");
			assertTrue(r.role().resident());
			assertTrue(TownPosts.keeperPosts().stream().anyMatch(p -> p.role() == r.role()), r.role() + " has no post");
			assertTrue(VillageLayout.indexOf(r.role()) >= 0);
		}
		assertEquals(10, VillageLayout.residents().size());
	}

	@Test
	void theDayRunsHomeWorkFountainInnAndAHeatEmptiesTheTownOntoTheOverlook() {
		assertEquals(VillageLayout.Activity.WORK, VillageLayout.activity(3000L, false));
		assertEquals(VillageLayout.Activity.SOCIAL, VillageLayout.activity(9000L, false));
		assertEquals(VillageLayout.Activity.EVENING, VillageLayout.activity(11500L, false));
		assertEquals(VillageLayout.Activity.HOME, VillageLayout.activity(18000L, false));
		assertEquals(VillageLayout.Activity.WATCH, VillageLayout.activity(3000L, true));
		assertEquals(VillageLayout.Activity.HOME, VillageLayout.activity(18000L, true), "nobody gets up for a night heat");
		assertEquals(VillageLayout.Activity.WORK, VillageLayout.activity(24000L + 3000L, false), "day time wraps");
		assertEquals(VillageLayout.Activity.SOCIAL, VillageLayout.activity(23700L, false), "dawn is social, not the inn");
		assertEquals(VillageLayout.Activity.SOCIAL, VillageLayout.activity(500L, false));
		for (int i = 0; i < VillageLayout.residents().size(); i++) {
			double[] w = VillageLayout.watchSpot(i);
			double r = Math.hypot(w[0] - 0.5D, w[1] - (VillageLayout.OVERLOOK_Z + 0.5D));
			assertTrue(r < VillageLayout.OVERLOOK_R - 1.0D, "watcher " + i + " on the rail");
			assertTrue(w[1] > VillageLayout.ARCH_Z, "watcher " + i + " beyond the arch");
		}
	}

	@Test
	void gysahlBedIsOnTheIslandWestOfTheGreensStall() {
		assertTrue(VillageLayout.onIsland(VillageLayout.GYSAHL_CX, VillageLayout.GYSAHL_CZ));
		assertTrue(VillageLayout.GYSAHL_CX < post(TownRole.GREENS).x());
		assertTrue(Math.hypot(VillageLayout.GYSAHL_CX - VillageLayout.PX,
				VillageLayout.GYSAHL_CZ - VillageLayout.PZ) > VillageLayout.PLAZA_R);
	}

	@Test
	void theGoldSaucerFitsAtTheStatuesFeet() {
		// the saucer stands 5 south of the statue with a disc reaching 2 more: inside the basin rim
		assertTrue(5 + 2 <= VillageLayout.FOUNTAIN_R);
		assertTrue(VillageLayout.FOUNTAIN_PAVE > VillageLayout.FOUNTAIN_R + 3);
	}

	@Test
	void theArrivalIsTheMedallion() {
		// Square.ARRIVAL (a Vec3, not loadable here) mirrors RaceTrack's paddock point
		assertEquals(VillageLayout.PX + 0.5D, RaceTrack.PADDOCK_X, 1.0E-9);
		assertEquals(VillageLayout.PZ + 0.5D, RaceTrack.PADDOCK_Z, 1.0E-9);
	}

	@Test
	void theWinnersBoardStandsBetweenThePlazaAndTheArch() {
		assertTrue(VillageLayout.BOARD_Z > VillageLayout.PZ + VillageLayout.PLAZA_R);
		assertTrue(VillageLayout.BOARD_Z < VillageLayout.ARCH_Z - 2);
		assertTrue(VillageLayout.BOARD_X0 > 3, "clear of the avenue");
	}

	@Test
	void everyVillageSignAndTownsfolkLineIsTranslated() throws IOException {
		String lang = Files.readString(Path.of("src/main/resources/assets/chocobosreborn/lang/en_us.json"));
		Pattern key = Pattern.compile("\"(chocobosreborn\\.(?:sign|board)\\.[a-z_]+(?:\\.[a-z_0-9]+)?)");
		for (String src : new String[]{"VillagePlan", "VillageDistrict", "VillageBuildings", "SquareBuilder", "TownLife"}) {
			String code = Files.readString(Path.of("src/main/java/tk/darrow/chocobosreborn/race/" + src + ".java"));
			Matcher m = key.matcher(code);
			while (m.find()) {
				String k = m.group(1);
				if (k.endsWith(".")) {
					continue;
				}
				// signs written as a prefix + ".0".."3"
				boolean present = lang.contains("\"" + k + "\"") || lang.contains("\"" + k + ".");   // a key or a family built in code
				assertTrue(present, src + " uses missing key " + k);
			}
		}
		for (TownRole role : TownRole.values()) {
			if (role.resident()) {
				assertTrue(lang.contains("\"chocobosreborn.kin." + role.id() + "\""), role + " name");
				assertTrue(lang.contains("\"chocobosreborn.resident." + role.id() + "\""), role + " line");
			}
		}
		for (int i = 0; i < 18; i++) {
			assertTrue(lang.contains("\"chocobosreborn.gossip." + i + "\""), "gossip " + i);
		}
		assertFalse(lang.contains("sign.fun_short"), "the practice gates are gone");
	}

	@Test
	void funGatesStillMapToSprintAndFirstGrandPrix() {
		// since the swap courses 0-2 are grands prix and 3-5 sprints: the short gate runs a sprint
		assertEquals(3, RaceScoring.funGateCourse(false));
		assertEquals(0, RaceScoring.funGateCourse(true));
		assertTrue(RaceTrack.forClass(RaceClass.C, RaceScoring.funGateCourse(false)).isSprint());
		assertTrue(RaceTrack.forClass(RaceClass.C, RaceScoring.funGateCourse(true)).isGrandPrix());
	}

	private static TownPosts.KeeperPost post(TownRole role) {
		return TownPosts.keeperPosts().stream().filter(p -> p.role() == role).findFirst().orElseThrow();
	}

	private static VillageLayout.Plot plot(String name) {
		return VillageLayout.plots().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
	}
}
