package tk.darrow.chocobosreborn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.breed.ChocoboNut;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.ledger.BirdRecord;
import tk.darrow.chocobosreborn.net.RacePayloads;
import tk.darrow.chocobosreborn.race.RaceClass;

/**
 * The Chocobo Almanac. Chapters on the left, an illustrated, scrolling page on
 * the right. Chapter bodies come from the lang file and use a small markup:
 * <pre>
 *   [item:chocobosreborn:gysahl_green] text     an item icon before the line
 *   [birds]                                     the eight breeds, rendered live
 *   [diagram:breeding]                          the farm-line diagram
 *   [h] Heading                                 a gold heading
 *   [step] text                                 a numbered how-to step
 * </pre>
 * "My Chocobos" is built from the ledger: each bird has a page with a live
 * preview, training bars, racing record, family line and a rename box.
 */
public class AlmanacScreen extends Screen {
	private static final String[] CHAPTERS = {"overview", "taming", "greens", "nuts", "colors", "riding",
			"square", "farm", "items"};
	private static final int LEFT_W = 112;
	private static final int PAD = 8;
	private static final int LINE = 10;

	/** One rendered element of a page. */
	private interface Block {
		int height();

		void draw(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY);
	}

	private final List<BirdRecord> birds = new ArrayList<>();
	/** The Release button asks twice; this is the first click. */
	private boolean confirmRelease;
	private final UUID player;
	private int chapter;
	@Nullable private BirdRecord shown;
	private double scroll;
	private final List<Block> blocks = new ArrayList<>();
	private final List<net.minecraft.client.gui.components.AbstractWidget> pageWidgets = new ArrayList<>();
	private int pageTop;
	@Nullable private EditBox nameBox;
	private final List<ChocoboEntity> previews = new ArrayList<>();
	private int stepCounter;

	public AlmanacScreen(CompoundTag data) {
		super(Component.translatable("item.chocobosreborn.chocobo_almanac"));
		for (Tag t : data.getList("Birds", Tag.TAG_COMPOUND)) {
			birds.add(BirdRecord.load((CompoundTag) t));
		}
		this.player = data.hasUUID("Player") ? data.getUUID("Player") : new UUID(0L, 0L);
	}

	// ----------------------------------------------------------------- layout

	@Override
	protected void init() {
		int y = PAD + 14;
		for (int i = 0; i < CHAPTERS.length; i++) {
			final int idx = i;
			addRenderableWidget(Button.builder(Component.translatable("chocobosreborn.almanac." + CHAPTERS[i] + ".title"),
					b -> select(idx)).bounds(PAD, y, LEFT_W, 18).build());
			y += 20;
		}
		addRenderableWidget(Button.builder(Component.translatable("chocobosreborn.almanac.stable.title"),
				b -> select(CHAPTERS.length)).bounds(PAD, y, LEFT_W, 18).build());
		pageTop = PAD + 14;
		select(chapter);
	}

	private void select(int idx) {
		chapter = idx;
		shown = null;
		scroll = 0;
		rebuild();
	}

	private int pageX() {
		return PAD + LEFT_W + PAD;
	}

	private int pageW() {
		return width - pageX() - PAD;
	}

	private void clearPage() {
		for (var w : pageWidgets) {
			removeWidget(w);
		}
		pageWidgets.clear();
		blocks.clear();
		nameBox = null;
		stepCounter = 0;
	}

	private void rebuild() {
		clearPage();
		if (chapter < CHAPTERS.length) {
			parse(Component.translatable("chocobosreborn.almanac." + CHAPTERS[chapter] + ".body").getString());
			return;
		}
		if (shown != null) {
			birdPage(shown);
			return;
		}
		List<BirdRecord> mine = birds.stream().filter(r -> player.equals(r.owner())).toList();
		if (mine.isEmpty()) {
			parse(Component.translatable("chocobosreborn.almanac.stable.empty").getString());
			return;
		}
		parse(Component.translatable("chocobosreborn.almanac.stable.body", mine.size()).getString());
		int y = pageTop;
		for (Block b : blocks) {
			y += b.height();
		}
		y += 6;
		for (BirdRecord r : mine) {
			Button b = Button.builder(label(r), btn -> {
				shown = r;
				scroll = 0;
				rebuild();
			}).bounds(pageX(), y, Math.min(pageW(), 240), 18).build();
			pageWidgets.add(addRenderableWidget(b));
			blocks.add(spacer(20));
			y += 20;
		}
	}

	// ------------------------------------------------------------------ markup

	private void parse(String body) {
		for (String raw : body.split("\n")) {
			String line = raw;
			if (line.startsWith("[h] ")) {
				blocks.add(heading(line.substring(4)));
			} else if (line.startsWith("[step] ")) {
				stepCounter++;
				blocks.add(text("§6" + stepCounter + ".§r " + line.substring(7), 12));
			} else if (line.startsWith("[item:")) {
				int end = line.indexOf(']');
				String id = line.substring(6, end);
				String rest = line.substring(end + 1).strip();
				blocks.add(itemLine(id, rest));
			} else if (line.equals("[birds]")) {
				blocks.add(birdRow());
			} else if (line.equals("[diagram:breeding]")) {
				blocks.add(breedingDiagram());
			} else if (line.isBlank()) {
				blocks.add(spacer(6));
			} else {
				blocks.add(text(line, 0));
			}
		}
	}

	private Block spacer(int h) {
		return new Block() {
			public int height() {
				return h;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
			}
		};
	}

	private Block heading(String s) {
		return new Block() {
			public int height() {
				return 16;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				g.drawString(font, s, x, y + 4, 0xFFE8A416, true);
				g.fill(x, y + 14, x + w, y + 15, 0x66E8A416);
			}
		};
	}

	private Block text(String s, int indent) {
		List<FormattedCharSequence> lines = font.split(Component.literal(s), pageW() - indent);
		return new Block() {
			public int height() {
				return lines.size() * LINE;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int yy = y;
				for (FormattedCharSequence l : lines) {
					g.drawString(font, l, x + indent, yy, 0xFFE0E0E0, false);
					yy += LINE;
				}
			}
		};
	}

	private Block itemLine(String id, String s) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
		List<FormattedCharSequence> lines = font.split(Component.literal(s), pageW() - 22);
		return new Block() {
			public int height() {
				return Math.max(18, lines.size() * LINE) + 2;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				g.renderItem(stack, x, y);
				int yy = y + (lines.size() == 1 ? 4 : 0);
				for (FormattedCharSequence l : lines) {
					g.drawString(font, l, x + 22, yy, 0xFFE0E0E0, false);
					yy += LINE;
				}
			}
		};
	}

	/** All eight breeds, live-rendered in a row with their names. */
	private Block birdRow() {
		ChocoboColor[] colors = ChocoboColor.values();
		return new Block() {
			public int height() {
				return 2 * 78;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int cell = Math.max(60, w / 4);
				for (int i = 0; i < colors.length; i++) {
					int cx = x + (i % 4) * cell + cell / 2;
					int cy = y + (i / 4) * 78;
					g.fill(cx - cell / 2 + 2, cy, cx + cell / 2 - 2, cy + 74, 0x33FFFFFF);
					drawBird(g, colors[i], false, cx, cy + 60, 22, mx, my);
					g.drawCenteredString(font, Component.translatable("chocobosreborn.color." + colors[i].id()), cx, cy + 64, 0xFFFFFFFF);
				}
			}
		};
	}

	/** Yellow + Yellow + Carob -> Green / Blue -> + Carob -> Black (White) -> + Wonderful Yellow + Zeio -> Gold. */
	private Block breedingDiagram() {
		return new Block() {
			public int height() {
				return 96;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int col = Math.max(70, (w - 20) / 4);
				box(g, x, y + 8, col - 8, "Yellow + Yellow", 0xFFF5B812, "chocobosreborn:carob_nut", "1 win each");
				arrow(g, x + col - 8, y + 28, x + col + 2);
				box(g, x + col + 2, y + 8, col - 8, "Green or Blue", 0xFF4CB05A, "chocobosreborn:carob_nut", "2 wins each");
				arrow(g, x + 2 * col - 6, y + 28, x + 2 * col + 4);
				box(g, x + 2 * col + 4, y + 8, col - 8, "Black (miss: White)", 0xFF2C2A32, "chocobosreborn:zeio_nut", "3 wins each");
				arrow(g, x + 3 * col - 4, y + 28, x + 3 * col + 6);
				box(g, x + 3 * col + 6, y + 8, col - 8, "Gold", 0xFFE8A416, null, "+ Wonderful Yellow");
				g.drawString(font, Component.translatable("chocobosreborn.almanac.diagram.note"), x, y + 76, 0xFFAAAAAA, false);
			}
		};
	}

	private void box(GuiGraphics g, int x, int y, int w, String title, int colour, @Nullable String nut, String sub) {
		g.fill(x, y, x + w, y + 60, 0xFF202020);
		g.fill(x, y, x + w, y + 3, colour | 0xFF000000);
		List<FormattedCharSequence> t = font.split(Component.literal(title), w - 6);
		int yy = y + 7;
		for (FormattedCharSequence l : t) {
			g.drawString(font, l, x + 3, yy, 0xFFFFFFFF, false);
			yy += LINE;
		}
		if (nut != null) {
			g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(nut))), x + 3, y + 38);
		}
		g.drawString(font, sub, x + (nut != null ? 22 : 3), y + 42, 0xFFBBBBBB, false);
	}

	private void arrow(GuiGraphics g, int x0, int y, int x1) {
		g.fill(x0, y, x1, y + 2, 0xFFE8A416);
		g.fill(x1 - 3, y - 2, x1, y + 4, 0xFFE8A416);
	}

	// --------------------------------------------------------------- bird page

	private void birdPage(BirdRecord r) {
		ChocoboColor c = ChocoboColor.byId(r.color());
		blocks.add(new Block() {
			public int height() {
				return 100;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				g.fill(x, y, x + 96, y + 96, 0x33FFFFFF);
				drawBird(g, c, true, x + 48, y + 84, 30, mx, my);
				g.drawString(font, label(r), x + 104, y + 4, 0xFFFFFFFF, true);
				g.drawString(font, tr("chocobosreborn.almanac.d.genes", colorName(r.color()),
						Component.translatable(r.male() ? "chocobosreborn.sex.male" : "chocobosreborn.sex.female"),
						gradeName(r.bornGrade()), gradeName(r.grade())), x + 104, y + 18, 0xFFE0E0E0, false);
				g.drawString(font, tr("chocobosreborn.almanac.d.racing",
						Component.translatable("chocobosreborn.class." + RaceClass.byId(r.raceClass()).id()), r.wins(),
						Math.max(0, RaceClass.WINS_TO_PROMOTE - r.wins() % RaceClass.WINS_TO_PROMOTE)), x + 104, y + 30, 0xFFE0E0E0, false);
				g.drawString(font, tr("chocobosreborn.almanac.d.born", r.bornDay()), x + 104, y + 42, 0xFFAAAAAA, false);
				// training bars
				String[] names = {"chocobosreborn.tip.speed", "chocobosreborn.tip.stamina", "chocobosreborn.tip.intelligence", "chocobosreborn.tip.cooperation"};
				int[] vals = {r.trSpeed(), r.trStamina(), r.trIntel(), r.trCoop()};
				int[] cols = {0xFFE8A416, 0xFF4CB05A, 0xFF3A8FD0, 0xFFE078A8};
				for (int i = 0; i < 4; i++) {
					int by = y + 56 + i * 11;
					g.drawString(font, Component.translatable(names[i]), x + 104, by, 0xFFBBBBBB, false);
					int bw = Math.max(60, w - 104 - 80 - 30);
					g.fill(x + 180, by + 1, x + 180 + bw, by + 8, 0xFF303030);
					g.fill(x + 180, by + 1, x + 180 + bw * vals[i] / 100, by + 8, cols[i]);
					g.drawString(font, String.valueOf(vals[i]), x + 184 + bw, by, 0xFFFFFFFF, false);
				}
			}
		});
		// rename
		blocks.add(spacer(24));
		blocks.add(heading(tr("chocobosreborn.almanac.d.family")));
		if (r.parentA() == null && r.parentB() == null) {
			blocks.add(text(tr("chocobosreborn.almanac.d.wild"), 0));
		} else {
			blocks.add(text(tr("chocobosreborn.almanac.d.parents", parentName(r.parentA(), r.parentColorA()),
					parentName(r.parentB(), r.parentColorB()),
					Component.translatable("chocobosreborn.nut." + ChocoboNut.byId(r.nut()).id())), 0));
		}
		List<BirdRecord> kids = birds.stream().filter(k -> r.id().equals(k.parentA()) || r.id().equals(k.parentB())).toList();
		if (!kids.isEmpty()) {
			blocks.add(text(tr("chocobosreborn.almanac.d.children", kids.size()), 0));
			for (BirdRecord k : kids) {
				blocks.add(text("  - " + label(k).getString(), 0));
			}
		}
		blocks.add(spacer(6));
		blocks.add(heading(tr("chocobosreborn.almanac.d.breeding")));
		blocks.add(text(breedingHint(r), 0));
		layoutWidgets(r);
	}

	/** Rename box + button and Back, positioned by the current scroll. */
	private void layoutWidgets(BirdRecord r) {
		int x = pageX();
		int y = pageTop + 100 - (int) scroll;
		int boxW = Math.min(160, pageW() - 70);
		nameBox = new EditBox(font, x + 104, y, boxW, 16, Component.translatable("chocobosreborn.almanac.d.name"));
		nameBox.setMaxLength(24);
		nameBox.setValue(r.name());
		nameBox.setHint(Component.translatable("chocobosreborn.almanac.d.name_hint"));
		pageWidgets.add(addRenderableWidget(nameBox));
		Button rename = Button.builder(Component.translatable("chocobosreborn.almanac.d.rename"), b -> {
			String n = nameBox == null ? "" : nameBox.getValue();
			PacketDistributor.sendToServer(new RacePayloads.RenameBird(r.id(), n));
			int i = birds.indexOf(r);
			BirdRecord updated = new BirdRecord(r.id(), r.owner(), n, r.color(), r.bornGrade(), r.grade(), r.male(), r.raceClass(),
					r.wins(), r.trSpeed(), r.trStamina(), r.trIntel(), r.trCoop(), r.parentA(), r.parentB(), r.parentColorA(),
					r.parentColorB(), r.nut(), r.bornDay(), r.alive(), n);
			if (i >= 0) {
				birds.set(i, updated);
			}
			shown = updated;
			rebuild();
		}).bounds(x + 104 + boxW + 4, y, 60, 16).build();
		pageWidgets.add(addRenderableWidget(rename));
		// release a living bird (asks twice) or remove a passed one from the book
		boolean living = r.alive();
		Button[] holder = new Button[1];
		holder[0] = Button.builder(Component.translatable(living ? "chocobosreborn.almanac.d.release" : "chocobosreborn.almanac.d.remove"), b -> {
			if (living && !confirmRelease) {
				confirmRelease = true;
				b.setMessage(Component.translatable("chocobosreborn.almanac.d.release_sure"));
				return;
			}
			PacketDistributor.sendToServer(new RacePayloads.ReleaseBird(r.id(), !living));
			birds.remove(r);
			confirmRelease = false;
			shown = null;
			scroll = 0;
			rebuild();
		}).bounds(width - PAD - 60, PAD + 22, 60, 18).build();
		pageWidgets.add(addRenderableWidget(holder[0]));
		Button back = Button.builder(Component.translatable("gui.back"), b -> {
			confirmRelease = false;
			shown = null;
			scroll = 0;
			rebuild();
		}).bounds(width - PAD - 60, PAD, 60, 18).build();
		pageWidgets.add(addRenderableWidget(back));
	}

	// ---------------------------------------------------------------- helpers

	private void drawBird(GuiGraphics g, ChocoboColor c, boolean saddled, int cx, int baseY, int scale, int mx, int my) {
		ChocoboEntity e = preview(c, saddled);
		if (e == null) {
			g.fill(cx - 16, baseY - 40, cx + 16, baseY, 0xFF000000 | swatch(c));
			return;
		}
		float yaw = (float) Math.atan((cx - mx) / 40.0F) * 20.0F;
		Quaternionf pose = new Quaternionf().rotationXYZ(0.0F, (float) Math.toRadians(180.0F + 35.0F + yaw), (float) Math.PI);
		InventoryScreen.renderEntityInInventory(g, cx, baseY, scale, new Vector3f(0.0F, 0.0F, 0.0F), pose, null, e);
	}

	@Nullable
	private ChocoboEntity preview(ChocoboColor c, boolean saddled) {
		for (ChocoboEntity e : previews) {
			if (e.color() == c && e.saddled() == saddled) {
				return e;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		ChocoboEntity e = ModEntities.CHOCOBO.get().create(mc.level);
		if (e == null) {
			return null;
		}
		e.setColor(c);
		e.setMale(true);
		if (saddled) {
			e.inventory().setItem(ChocoboEntity.SLOT_SADDLE, new ItemStack(tk.darrow.chocobosreborn.item.ModItems.SADDLE.get()));
			e.setSaddledForPreview(true);
		}
		previews.add(e);
		return e;
	}

	private static int swatch(ChocoboColor c) {
		return switch (c) {
			case YELLOW -> 0xF5B812;
			case GREEN -> 0x4CB05A;
			case BLUE -> 0x3A8FD0;
			case WHITE -> 0xE8E4DC;
			case BLACK -> 0x2C2A32;
			case GOLD -> 0xE8A416;
			case PURPLE -> 0x9254D6;
			case FLAME -> 0xBE2A1E;
		};
	}

	private static Component colorName(int id) {
		return Component.translatable("chocobosreborn.color." + ChocoboColor.byId(id).id());
	}

	private static Component gradeName(int rank) {
		return Component.translatable("chocobosreborn.grade." + ChocoboGrade.byRank(rank).id());
	}

	private Component label(BirdRecord r) {
		Component name = r.name().isEmpty() ? Component.translatable("chocobosreborn.almanac.unnamed", colorName(r.color()))
				: Component.literal(r.name());
		return Component.translatable("chocobosreborn.almanac.stable.entry", name, colorName(r.color()),
				gradeName(r.grade()), r.alive() ? "" : Component.translatable("chocobosreborn.almanac.dead").getString());
	}

	private Component parentName(@Nullable UUID id, int color) {
		BirdRecord p = id == null ? null : birds.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
		if (p != null && !p.name().isEmpty()) {
			return Component.literal(p.name());
		}
		return Component.translatable("chocobosreborn.almanac.unnamed", colorName(color < 0 ? 0 : color));
	}

	private static String breedingHint(BirdRecord r) {
		ChocoboColor c = ChocoboColor.byId(r.color());
		boolean good = r.grade() >= ChocoboGrade.GOOD.getRank();
		boolean wonderful = r.grade() >= ChocoboGrade.WONDERFUL.getRank();
		String key = switch (c) {
			case YELLOW -> wonderful ? "chocobosreborn.almanac.h.yellow_wonderful" : good ? "chocobosreborn.almanac.h.yellow_good" : "chocobosreborn.almanac.h.yellow_poor";
			case GREEN, BLUE -> "chocobosreborn.almanac.h.green_blue";
			case BLACK -> "chocobosreborn.almanac.h.black";
			case GOLD -> "chocobosreborn.almanac.h.gold";
			case WHITE -> "chocobosreborn.almanac.h.white";
			case PURPLE -> "chocobosreborn.almanac.h.purple";
			case FLAME -> "chocobosreborn.almanac.h.flame";
		};
		return tr(key, r.wins());
	}

	private static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	// ---------------------------------------------------------------- render

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawString(font, title, PAD, PAD, 0xFFE8A416, true);
		int x = pageX();
		int bottom = height - PAD;
		Component heading = chapter < CHAPTERS.length
				? Component.translatable("chocobosreborn.almanac." + CHAPTERS[chapter] + ".title")
				: Component.translatable("chocobosreborn.almanac.stable.title");
		g.drawString(font, heading, x, PAD, 0xFFFFFFFF, true);
		g.enableScissor(x, pageTop, x + pageW(), bottom);
		int y = pageTop - (int) scroll;
		for (Block b : blocks) {
			if (y + b.height() > pageTop - 4 && y < bottom) {
				b.draw(g, x, y, pageW(), mouseX, mouseY);
			}
			y += b.height();
		}
		g.disableScissor();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		g.fill(pageX() - 4, PAD + 12, width - PAD + 2, height - PAD + 2, 0x66000000);
	}

	private int contentHeight() {
		int h = 0;
		for (Block b : blocks) {
			h += b.height();
		}
		return h;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		int visible = height - PAD - pageTop;
		double max = Math.max(0, contentHeight() - visible);
		double before = scroll;
		scroll = Math.max(0.0D, Math.min(max, scroll - dy * 14.0D));
		int shift = (int) before - (int) scroll;
		for (var w : pageWidgets) {
			if (w instanceof Button b && b.getY() == PAD) {
				continue;   // the Back button stays put
			}
			w.setY(w.getY() + shift);
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
