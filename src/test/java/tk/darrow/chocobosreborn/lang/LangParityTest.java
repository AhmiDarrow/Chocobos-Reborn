package tk.darrow.chocobosreborn.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * English ({@code en_us.json}) is the source of truth. Every shipped locale must
 * carry the same keys, the same {@code %s} / {@code %d} placeholders in the same
 * order, and the same almanac markup. Minecraft loads {@code en_us} first and
 * overlays the selected language, so a missing key shows English rather than a
 * blank line or a crash. This mod does not ship a font: the vanilla font already
 * draws Japanese, Korean and Chinese.
 */
class LangParityTest {
	private static final Path LANG = Path.of("src/main/resources/assets/chocobosreborn/lang");
	private static final Set<String> LOCALES = Set.of(
			"es_es", "es_mx", "fr_fr", "de_de", "pt_br", "it_it", "ru_ru", "pl_pl", "ja_jp", "ko_kr", "zh_cn");
	private static final Pattern KEY_LINE = Pattern.compile("^  \"((?:\\\\.|[^\"\\\\])*)\"\\s*:");
	private static final Pattern MARKUP = Pattern.compile("\\[(?:h|step|birds|diagram:[a-z0-9_]+|item:[a-z0-9_.:]+)\\]");
	private static final Pattern WORD = Pattern.compile("[A-Za-z']+");
	/** Names and sounds that stay as in English. A line made only of these is not prose. */
	private static final Set<String> PROPER = Set.of(
			"ahmi", "risika", "esther", "rook", "wynn", "bilo", "marl", "jolo", "teiyo", "sable", "pell",
			"whiskerwind", "gysahl", "krakka", "tantal", "pahsana", "curiel", "mimett", "reagan", "sylkis",
			"pepio", "luchile", "saraha", "lasan", "pram", "porov", "carob", "zeio", "chocobo", "chocobos",
			"reborn", "gp", "minecraft", "nether", "end", "kweh", "wark", "tamsin", "orrin", "brisa", "hobb",
			"wren", "lark", "mabry", "dunmore", "quill");
	/** A finished lang key, not a prefix joined with {@code +}. */
	private static final Pattern TRANSLATABLE = Pattern.compile(
			"translatable\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*[,)]");
	private static final Pattern DATA_KEY = Pattern.compile("\"(?:translate|subtitle)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
	private static final Set<String> DIAGRAM = Set.of(
			"chocobosreborn.almanac.diagram.yy",
			"chocobosreborn.almanac.diagram.gb",
			"chocobosreborn.almanac.diagram.bw",
			"chocobosreborn.almanac.diagram.gold",
			"chocobosreborn.almanac.diagram.wonderful",
			"chocobosreborn.almanac.diagram.wins1",
			"chocobosreborn.almanac.diagram.wins2",
			"chocobosreborn.almanac.diagram.wins3");

	/** Registry ids that must have a display name. Hints are optional. Block items use the block key. */
	private static final List<String> REQUIRED = List.of(
			"itemGroup.chocobosreborn",
			"entity.chocobosreborn.chocobo",
			"entity.chocobosreborn.kin_steward",
			"block.chocobosreborn.gysahl_green",
			"block.chocobosreborn.wild_gysahl",
			"block.chocobosreborn.square_gate",
			"block.chocobosreborn.boost_pad",
			"item.chocobosreborn.gysahl_green",
			"item.chocobosreborn.gysahl_green_seeds",
			"item.chocobosreborn.krakka_green",
			"item.chocobosreborn.tantal_green",
			"item.chocobosreborn.pahsana_green",
			"item.chocobosreborn.curiel_green",
			"item.chocobosreborn.mimett_green",
			"item.chocobosreborn.reagan_green",
			"item.chocobosreborn.sylkis_green",
			"item.chocobosreborn.pepio_nut",
			"item.chocobosreborn.luchile_nut",
			"item.chocobosreborn.saraha_nut",
			"item.chocobosreborn.lasan_nut",
			"item.chocobosreborn.pram_nut",
			"item.chocobosreborn.porov_nut",
			"item.chocobosreborn.carob_nut",
			"item.chocobosreborn.zeio_nut",
			"item.chocobosreborn.chocobo_lure",
			"item.chocobosreborn.chocobo_saddle",
			"item.chocobosreborn.chocobo_almanac",
			"item.chocobosreborn.chocobo_pocketwatch",
			"item.chocobosreborn.chocobo_whistle",
			"item.chocobosreborn.gp",
			"item.chocobosreborn.saddlebags",
			"item.chocobosreborn.leather_chocobo_armor",
			"item.chocobosreborn.iron_chocobo_armor",
			"item.chocobosreborn.diamond_chocobo_armor",
			"item.chocobosreborn.netherite_chocobo_armor",
			"item.chocobosreborn.yellow_chocobo_spawn_egg",
			"item.chocobosreborn.green_chocobo_spawn_egg",
			"item.chocobosreborn.blue_chocobo_spawn_egg",
			"item.chocobosreborn.white_chocobo_spawn_egg",
			"item.chocobosreborn.black_chocobo_spawn_egg",
			"item.chocobosreborn.gold_chocobo_spawn_egg",
			"item.chocobosreborn.purple_chocobo_spawn_egg",
			"item.chocobosreborn.flame_chocobo_spawn_egg",
			"item.chocobosreborn.kin_steward_spawn_egg",
			"chocobosreborn.subtitle.kweh",
			"chocobosreborn.subtitle.wark",
			"chocobosreborn.subtitle.whistle",
			"chocobosreborn.command.unknown_track",
			"chocobosreborn.command.built",
			"chocobosreborn.command.unknown_course",
			"chocobosreborn.command.bad_index");

	@Test
	void everyLocaleMatchesEnglish() throws IOException {
		assertTrue(Files.isDirectory(LANG), "lang directory");
		List<Path> files;
		try (Stream<Path> list = Files.list(LANG)) {
			files = list.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
		Set<String> names = new HashSet<>();
		for (Path file : files) {
			String name = file.getFileName().toString().replace(".json", "");
			assertTrue(names.add(name), "duplicate locale file " + name);
		}
		assertEquals(Set.of("en_us"), new HashSet<>(names.stream().filter(n -> !LOCALES.contains(n)).toList()),
				"unexpected locale files");
		assertEquals(LOCALES, new HashSet<>(names.stream().filter(LOCALES::contains).toList()),
				"missing locale files");

		Map<String, String> english = load(LANG.resolve("en_us.json"));
		for (String key : REQUIRED) {
			assertTrue(english.containsKey(key), "en_us missing " + key);
			assertFalse(english.get(key).isBlank(), key);
		}
		for (String key : referencedKeys()) {
			assertTrue(english.containsKey(key), "code or data uses missing key " + key);
		}

		for (String locale : LOCALES) {
			Map<String, String> translated = load(LANG.resolve(locale + ".json"));
			assertEquals(english.keySet(), translated.keySet(), locale + " key set");
			for (String key : english.keySet()) {
				String en = english.get(key);
				String tr = translated.get(key);
				assertEquals(placeholders(en), placeholders(tr), locale + " placeholders for " + key);
				assertEquals(markup(en), markup(tr), locale + " markup for " + key);
				assertEquals(en.isEmpty(), tr.isEmpty(), locale + " empty mismatch " + key);
				if (en.contains("Chocobos Reborn")) {
					assertTrue(tr.contains("Chocobos Reborn"), locale + " translated the mod name in " + key);
				}
				if (en.contains("Ahmi")) {
					assertTrue(tr.contains("Ahmi"), locale + " translated Ahmi in " + key);
				}
				if (en.contains("Hope this helps")) {
					assertTrue(tr.contains("Hope this helps"), locale + " changed the Ahmi sign-off in " + key);
				}
				if ("itemGroup.chocobosreborn".equals(key)) {
					assertEquals("Chocobos Reborn", tr, locale);
				}
				if (en.equals(tr) && en.length() >= 12 && en.indexOf(' ') >= 0 && !"Chocobos Reborn".equals(en)) {
					assertFalse(hasTranslatableWord(en), locale + " left English prose at " + key);
				}
				assertFits(locale, key, tr);
			}
		}
	}

	private static void assertFits(String locale, String key, String value) {
		boolean cjk = containsCjk(value);
		int points = value.codePointCount(0, value.length());
		if (key.startsWith("chocobosreborn.sign.")) {
			assertTrue(points <= (cjk ? 14 : 28), locale + " sign line too long (" + points + "): " + key);
		}
		if (DIAGRAM.contains(key)) {
			assertTrue(points <= (cjk ? 14 : 28), locale + " diagram caption too long (" + points + "): " + key);
		}
		if (key.equals("chocobosreborn.command.follow") || key.equals("chocobosreborn.command.stay")
				|| key.equals("chocobosreborn.command.wander")) {
			assertTrue(points <= (cjk ? 8 : 16), locale + " command tab too long (" + points + "): " + key);
		}
		// Inventory slot marks only. select.len.gp_short is a format string ("%s×%s m").
		if (key.startsWith("chocobosreborn.inv.slot.") && key.endsWith("_short")) {
			assertEquals(1, points, locale + " slot mark " + key);
		}
	}

	/** One object of string values, the shape of a Minecraft lang file. No Gson on the test classpath. */
	private static Map<String, String> load(Path file) throws IOException {
		String text = Files.readString(file);
		assertFalse(text.startsWith("\uFEFF"), file + " has a BOM");
		Map<String, String> object = new LinkedHashMap<>();
		List<String> scanned = new ArrayList<>();
		for (String line : text.split("\n", -1)) {
			String raw = line.replace("\r", "");
			Matcher m = KEY_LINE.matcher(raw);
			if (!m.lookingAt()) {
				continue;
			}
			String key = jsonUnescape(m.group(1));
			scanned.add(key);
			String rest = raw.substring(m.end()).trim();
			if (rest.endsWith(",")) {
				rest = rest.substring(0, rest.length() - 1).trim();
			}
			assertTrue(rest.length() >= 2 && rest.charAt(0) == '"' && rest.charAt(rest.length() - 1) == '"',
					file + " value is not a one-line string: " + key);
			object.put(key, jsonUnescape(rest.substring(1, rest.length() - 1)));
		}
		assertEquals(scanned.size(), new HashSet<>(scanned).size(), file + " has duplicate keys");
		assertEquals(object.size(), scanned.size(), file + " key lines do not match the parsed object");
		return object;
	}

	private static String jsonUnescape(String body) {
		StringBuilder out = new StringBuilder(body.length());
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c != '\\') {
				out.append(c);
				continue;
			}
			if (i + 1 >= body.length()) {
				fail("trailing escape in " + body);
			}
			char next = body.charAt(++i);
			switch (next) {
				case '"', '\\', '/' -> out.append(next);
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'n' -> out.append('\n');
				case 'r' -> out.append('\r');
				case 't' -> out.append('\t');
				case 'u' -> {
					if (i + 4 >= body.length()) {
						fail("bad unicode escape in " + body);
					}
					out.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
					i += 4;
				}
				default -> fail("bad escape \\" + next);
			}
		}
		return out.toString();
	}

	private static List<String> referencedKeys() throws IOException {
		Set<String> keys = new HashSet<>();
		try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
			for (Path java : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
				Matcher m = TRANSLATABLE.matcher(Files.readString(java));
				while (m.find()) {
					String key = m.group(1).replace("\\\"", "\"");
					// gui.back and gui.cancel are vanilla. Everything else this mod shows is its own.
					if (key.contains("chocobosreborn") || "itemGroup.chocobosreborn".equals(key)) {
						keys.add(key);
					}
				}
			}
		}
		try (Stream<Path> walk = Files.walk(Path.of("src/main/resources/data/chocobosreborn/advancement"))) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
				collectDataKeys(Files.readString(file), keys);
			}
		}
		collectDataKeys(Files.readString(Path.of("src/main/resources/assets/chocobosreborn/sounds.json")), keys);
		List<String> sorted = new ArrayList<>(keys);
		sorted.sort(Comparator.naturalOrder());
		return sorted;
	}

	private static void collectDataKeys(String text, Set<String> keys) {
		Matcher m = DATA_KEY.matcher(text);
		while (m.find()) {
			keys.add(jsonUnescape(m.group(1)));
		}
	}

	/** Format tokens in order. A stray percent sign fails the comparison by throwing. */
	static List<String> placeholders(String value) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) != '%') {
				continue;
			}
			if (i + 1 < value.length() && value.charAt(i + 1) == '%') {
				out.add("%%");
				i++;
				continue;
			}
			int start = i;
			i++;
			while (i < value.length() && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
				i++;
			}
			if (i < value.length() && value.charAt(i) == '$') {
				i++;
			}
			assertTrue(i < value.length() && (value.charAt(i) == 's' || value.charAt(i) == 'd'),
					"bad placeholder in: " + value);
			out.add(value.substring(start, i + 1));
		}
		return out;
	}

	private static List<String> markup(String value) {
		List<String> out = new ArrayList<>();
		Matcher m = MARKUP.matcher(value);
		while (m.find()) {
			out.add(m.group());
		}
		return out;
	}

	private static boolean containsCjk(String value) {
		return value.codePoints().anyMatch(cp ->
				(cp >= 0x3040 && cp <= 0x30FF) || (cp >= 0x3400 && cp <= 0x9FFF) || (cp >= 0xAC00 && cp <= 0xD7AF));
	}

	/** True when the English line still has a word a translator should have changed. */
	private static boolean hasTranslatableWord(String value) {
		String stripped = value.replaceAll("%(\\d+\\$)?[sd]", " ").replace("%%", " ");
		stripped = MARKUP.matcher(stripped).replaceAll(" ");
		Matcher m = WORD.matcher(stripped);
		while (m.find()) {
			String w = m.group().toLowerCase(Locale.ROOT);
			if (w.length() > 1 && !PROPER.contains(w)) {
				return true;
			}
		}
		return false;
	}
}
