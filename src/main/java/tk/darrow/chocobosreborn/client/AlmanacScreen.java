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
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.breed.ChocoboNut;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.ledger.BirdRecord;
import tk.darrow.chocobosreborn.net.RacePayloads;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceScoring;

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
	/** The journal's frame is 12 px of wood plus a margin: page content stays this far inside its edge. */
	private static final int INSET = 20;
	private static final ResourceLocation PAGE = ResourceLocation.fromNamespaceAndPath(
			ChocobosReborn.MOD_ID, "textures/gui/almanac.png");

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
	/** Back and Release: stay put when the page scrolls. */
	private final List<net.minecraft.client.gui.components.AbstractWidget> chromeWidgets = new ArrayList<>();
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

	/** Server pushed a fresh ledger while this book is already open (release, rename). */
	public void reload(CompoundTag data) {
		birds.clear();
		for (Tag t : data.getList("Birds", Tag.TAG_COMPOUND)) {
			birds.add(BirdRecord.load((CompoundTag) t));
		}
		UUID keep = shown == null ? null : shown.id();
		shown = null;
		if (keep != null) {
			for (BirdRecord r : birds) {
				if (r.id().equals(keep)) {
					shown = r;
					break;
				}
			}
		}
		rebuild();
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
		chapterBottom = y + 20;
		pageTop = frameY() + INSET;
		UUID keep = shown == null ? null : shown.id();
		if (keep == null || chapter != CHAPTERS.length) {
			rebuild();
			return;
		}
		shown = null;
		for (BirdRecord r : birds) {
			if (r.id().equals(keep)) {
				shown = r;
				break;
			}
		}
		rebuild();
	}

	private void select(int idx) {
		chapter = idx;
		shown = null;
		scroll = 0;
		rebuild();
	}

	private static boolean birdIsRacing(UUID id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return false;
		}
		if (mc.player.getVehicle() instanceof ChocoboEntity v && v.getUUID().equals(id) && v.racing()) {
			return true;
		}
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ChocoboEntity c && c.getUUID().equals(id) && c.racing()) {
				return true;
			}
		}
		return false;
	}

	/** Where the chapter buttons end; Back and Release sit under them. */
	private int chapterBottom;

	/** The journal frame: right of the chapter column, from under the title row to the bottom margin. */
	private int frameY() {
		return PAD + 14;
	}

	private int frameX() {
		return PAD + LEFT_W + PAD;
	}

	private int frameW() {
		return Math.max(32 + 2 * INSET, width - frameX() - PAD);
	}

	private int frameH() {
		return Math.max(32 + 2 * INSET, height - PAD - frameY());
	}

	/** Content area inside the frame. */
	private int pageX() {
		return frameX() + INSET;
	}

	private int pageW() {
		return frameW() - 2 * INSET;
	}

	private int pageBottom() {
		return frameY() + frameH() - INSET;
	}

	private void clearPage() {
		for (var w : pageWidgets) {
			removeWidget(w);
		}
		pageWidgets.clear();
		chromeWidgets.clear();
		blocks.clear();
		nameBox = null;
		stepCounter = 0;
	}

	private void rebuild() {
		confirmRelease = false;
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
				confirmRelease = false;
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

	private int drawWrapped(GuiGraphics g, Component c, int x, int y, int maxW, int color) {
		int yy = y;
		for (FormattedCharSequence l : font.split(c, Math.max(8, maxW))) {
			g.drawString(font, l, x, yy, color, false);
			yy += LINE;
		}
		return yy;
	}

	private Block heading(String s) {
		return new Block() {
			public int height() {
				int lines = Math.max(1, font.split(Component.literal(s), Math.max(40, pageW())).size());
				return 8 + lines * LINE;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int yy = y + 4;
				for (FormattedCharSequence l : font.split(Component.literal(s), Math.max(40, w))) {
					g.drawString(font, l, x, yy, 0xFFE8A416, true);
					yy += LINE;
				}
				g.fill(x, yy, x + w, yy + 1, 0x66E8A416);
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
				return 2 * 90;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int cell = Math.max(40, w / 4);
				for (int i = 0; i < colors.length; i++) {
					int cx = x + (i % 4) * cell + cell / 2;
					int cy = y + (i / 4) * 90;
					g.fill(cx - cell / 2 + 2, cy, cx + cell / 2 - 2, cy + 84, 0x33FFFFFF);
					// an adult is ADULT_H blocks tall: 18 px per block keeps the crest under the cell's top
					drawBird(g, colors[i], false, cx, cy + 68, 18, mx, my);
					g.drawCenteredString(font, Component.translatable("chocobosreborn.color." + colors[i].id()), cx, cy + 72, 0xFFFFFFFF);
				}
			}
		};
	}

	/** Yellow + Yellow + Carob -> Green / Blue -> + Carob -> Black (White) -> + Wonderful Yellow + Zeio -> Gold. */
	private Block breedingDiagram() {
		return new Block() {
			public int height() {
				int extra = Math.max(0, font.split(Component.translatable("chocobosreborn.almanac.diagram.note"),
						Math.max(40, pageW())).size() - 1);
				return 96 + extra * LINE;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int col = Math.max(48, (w - 16) / 4);
				box(g, x, y + 8, col - 8, "chocobosreborn.almanac.diagram.yy", 0xFFF5B812, "chocobosreborn:carob_nut",
						"chocobosreborn.almanac.diagram.wins1");
				arrow(g, x + col - 8, y + 28, x + col + 2);
				box(g, x + col + 2, y + 8, col - 8, "chocobosreborn.almanac.diagram.gb", 0xFF4CB05A, "chocobosreborn:carob_nut",
						"chocobosreborn.almanac.diagram.wins2");
				arrow(g, x + 2 * col - 6, y + 28, x + 2 * col + 4);
				box(g, x + 2 * col + 4, y + 8, col - 8, "chocobosreborn.almanac.diagram.bw", 0xFF2C2A32, "chocobosreborn:zeio_nut",
						"chocobosreborn.almanac.diagram.wins3");
				arrow(g, x + 3 * col - 4, y + 28, x + 3 * col + 6);
				box(g, x + 3 * col + 6, y + 8, col - 8, "chocobosreborn.almanac.diagram.gold", 0xFFE8A416, null,
						"chocobosreborn.almanac.diagram.wonderful");
				int ny = y + 76;
				for (FormattedCharSequence l : font.split(Component.translatable("chocobosreborn.almanac.diagram.note"),
						Math.max(40, w))) {
					g.drawString(font, l, x, ny, 0xFFAAAAAA, false);
					ny += LINE;
				}
			}
		};
	}

	private void box(GuiGraphics g, int x, int y, int w, String titleKey, int colour, @Nullable String nut, String subKey) {
		g.fill(x, y, x + w, y + 60, 0xFF202020);
		g.fill(x, y, x + w, y + 3, colour | 0xFF000000);
		List<FormattedCharSequence> t = font.split(Component.translatable(titleKey), w - 6);
		int yy = y + 7;
		for (FormattedCharSequence l : t) {
			g.drawString(font, l, x + 3, yy, 0xFFFFFFFF, false);
			yy += LINE;
		}
		if (nut != null) {
			g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(nut))), x + 3, y + 38);
		}
		int subX = x + (nut != null ? 22 : 3);
		int subW = Math.max(16, w - (nut != null ? 26 : 6));
		int sy = y + 42;
		for (FormattedCharSequence l : font.split(Component.translatable(subKey), subW)) {
			g.drawString(font, l, subX, sy, 0xFFBBBBBB, false);
			sy += LINE;
		}
	}

	private void arrow(GuiGraphics g, int x0, int y, int x1) {
		g.fill(x0, y, x1, y + 2, 0xFFE8A416);
		g.fill(x1 - 3, y - 2, x1, y + 4, 0xFFE8A416);
	}

	// --------------------------------------------------------------- bird page

	private void birdPage(BirdRecord r) {
		ChocoboColor c = ChocoboColor.byId(r.color());
		RaceClass rc = RaceClass.byId(r.raceClass());
		int more = RaceScoring.winsUntilPromote(rc, r.classWins());
		Component genes = Component.literal(tr("chocobosreborn.almanac.d.genes", colorName(r.color()),
				Component.translatable(r.male() ? "chocobosreborn.sex.male" : "chocobosreborn.sex.female"),
				gradeName(r.bornGrade()), gradeName(r.grade())));
		Component racing = Component.literal(more == 0
				? tr("chocobosreborn.almanac.d.racing_top",
				Component.translatable("chocobosreborn.class." + rc.id()), r.wins())
				: tr("chocobosreborn.almanac.d.racing",
				Component.translatable("chocobosreborn.class." + rc.id()), r.classWins(), more));
		Component born = Component.literal(tr("chocobosreborn.almanac.d.born", r.bornDay()));
		int headerH = birdHeaderHeight(genes, racing, born);
		blocks.add(new Block() {
			public int height() {
				return headerH;
			}

			public void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
				int tw = Math.max(40, w - 104);
				g.fill(x, y, x + 96, y + 96, 0x33FFFFFF);
				drawBird(g, c, true, x + 48, y + 88, 24, mx, my);
				int tx = x + 104;
				int yy = y + 4;
				g.drawString(font, label(r), tx, yy, 0xFFFFFFFF, true);
				yy += LINE;
				yy = drawWrapped(g, genes, tx, yy, tw, 0xFFE0E0E0);
				yy = drawWrapped(g, racing, tx, yy, tw, 0xFFE0E0E0);
				yy = drawWrapped(g, born, tx, yy, tw, 0xFFAAAAAA);
				String[] names = {"chocobosreborn.tip.speed", "chocobosreborn.tip.stamina", "chocobosreborn.tip.intelligence", "chocobosreborn.tip.cooperation"};
				int[] vals = {r.trSpeed(), r.trStamina(), r.trIntel(), r.trCoop()};
				int[] cols = {0xFFE8A416, 0xFF4CB05A, 0xFF3A8FD0, 0xFFE078A8};
				int labelW = 72;
				int numW = 16;
				int barX = tx + labelW;
				int barW = Math.max(24, tw - labelW - numW);
				for (int i = 0; i < 4; i++) {
					int by = yy + 4 + i * 11;
					g.drawString(font, Component.translatable(names[i]), tx, by, 0xFFBBBBBB, false);
					g.fill(barX, by + 1, barX + barW, by + 8, 0xFF303030);
					g.fill(barX, by + 1, barX + barW * vals[i] / 100, by + 8, cols[i]);
					g.drawString(font, String.valueOf(vals[i]), barX + barW + 2, by, 0xFFFFFFFF, false);
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
		layoutWidgets(r, headerH);
	}

	private int birdHeaderHeight(Component genes, Component racing, Component born) {
		int tw = Math.max(40, pageW() - 104);
		int lines = 1 + font.split(genes, tw).size() + font.split(racing, tw).size()
				+ font.split(born, tw).size();
		return Math.max(100, 8 + lines * LINE + 4 * 11);
	}

	/** Rename box + button and Back, positioned by the current scroll. */
	private void layoutWidgets(BirdRecord r, int headerH) {
		int x = pageX();
		int y = pageTop + headerH + 4 - (int) scroll;
		int boxW = Math.max(40, Math.min(160, pageW() - 104 - 64));
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
					r.wins(), r.classWins(), r.trSpeed(), r.trStamina(), r.trIntel(), r.trCoop(), r.parentA(), r.parentB(),
					r.parentColorA(), r.parentColorB(), r.nut(), r.bornDay(), r.alive(), n);
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
			if (living && birdIsRacing(r.id())) {
				if (minecraft != null && minecraft.player != null) {
					minecraft.player.displayClientMessage(Component.translatable("chocobosreborn.almanac.d.release_racing"), true);
				}
				confirmRelease = false;
				b.setMessage(Component.translatable("chocobosreborn.almanac.d.release"));
				return;
			}
			PacketDistributor.sendToServer(new RacePayloads.ReleaseBird(r.id(), !living));
			if (!living) {
				birds.remove(r);
				shown = null;
				scroll = 0;
			}
			confirmRelease = false;
			rebuild();
		}).bounds(PAD, Math.max(chapterBottom + 26, height - PAD - 18), LEFT_W, 18).build();
		pageWidgets.add(addRenderableWidget(holder[0]));
		chromeWidgets.add(holder[0]);
		Button back = Button.builder(Component.translatable("gui.back"), b -> {
			confirmRelease = false;
			shown = null;
			scroll = 0;
			rebuild();
		}).bounds(PAD, chapterBottom + 4, LEFT_W, 18).build();
		pageWidgets.add(addRenderableWidget(back));
		chromeWidgets.add(back);
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
		int bottom = pageBottom();
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
		int visible = bottom - pageTop;
		int content = contentHeight();
		if (content > visible) {
			// scrollbar in the frame's right margin
			int track = x + pageW() + 6;
			int thumbH = Math.max(12, visible * visible / content);
			int thumbY = pageTop + (int) ((visible - thumbH) * (scroll / (content - visible)));
			g.fill(track, pageTop, track + 3, bottom, 0x40000000);
			g.fill(track, thumbY, track + 3, thumbY + thumbH, 0xFFE8A416);
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		blitJournal(g, frameX(), frameY(), frameW(), frameH());
	}

	/** Nine-slice the 256 journal so the gold frame stays 16px on any page size. */
	private void blitJournal(GuiGraphics g, int x, int y, int w, int h) {
		int b = 16;
		g.blit(PAGE, x, y, b, b, 0, 0, b, b, 256, 256);
		g.blit(PAGE, x + w - b, y, b, b, 256 - b, 0, b, b, 256, 256);
		g.blit(PAGE, x, y + h - b, b, b, 0, 256 - b, b, b, 256, 256);
		g.blit(PAGE, x + w - b, y + h - b, b, b, 256 - b, 256 - b, b, b, 256, 256);
		g.blit(PAGE, x + b, y, w - 2 * b, b, b, 0, 256 - 2 * b, b, 256, 256);
		g.blit(PAGE, x + b, y + h - b, w - 2 * b, b, b, 256 - b, 256 - 2 * b, b, 256, 256);
		g.blit(PAGE, x, y + b, b, h - 2 * b, 0, b, b, 256 - 2 * b, 256, 256);
		g.blit(PAGE, x + w - b, y + b, b, h - 2 * b, 256 - b, b, b, 256 - 2 * b, 256, 256);
		g.blit(PAGE, x + b, y + b, w - 2 * b, h - 2 * b, b, b, 256 - 2 * b, 256 - 2 * b, 256, 256);
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
		int visible = pageBottom() - pageTop;
		double max = Math.max(0, contentHeight() - visible);
		double before = scroll;
		scroll = Math.max(0.0D, Math.min(max, scroll - dy * 14.0D));
		int shift = (int) before - (int) scroll;
		for (var w : pageWidgets) {
			if (chromeWidgets.contains(w)) {
				continue;
			}
			w.setY(w.getY() + shift);
			// widgets are not scissored: hide any that scrolled off the page
			w.visible = w.getY() >= pageTop && w.getY() + w.getHeight() <= pageBottom();
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
