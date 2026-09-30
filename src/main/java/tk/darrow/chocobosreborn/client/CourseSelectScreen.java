package tk.darrow.chocobosreborn.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import tk.darrow.chocobosreborn.net.RacePayloads;
import tk.darrow.chocobosreborn.race.DuelStakes;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceTrack;

/**
 * Pick a course: one class at a time (a tab per class, the bird's own and every class
 * below it), its sprints (one long lap) in the left column and its grands prix (three
 * to five laps of a shorter circuit) in the right, each labelled with its laps and
 * length and, where a ranked win would count toward promotion, its points
 * ({@link RaceTrack#winPoints}); the tooltip adds the first-place purse. However many courses a class has ({@link RaceTrack#sprintsOf} /
 * {@link RaceTrack#grandsPrixOf}), a class fits in six rows, so the whole picker sits on
 * a 1080p screen at GUI scale 3 (640 x 360) or 4 (480 x 270); anything taller scrolls.
 * Ranked heats start at once; a duel challenge also picks a GP stake and then waits
 * for a second rider at the duel master.
 */
public class CourseSelectScreen extends Screen {
	/** Duel stakes on offer for the shown class: never over its biggest purse, the server's cap ({@link RaceScoring#clampDuelStake(int, int)}). */
	private int[] stakes = {0};
	private final java.util.ArrayList<CourseButton> courseButtons = new java.util.ArrayList<>();
	private static final int HEADER = 38;
	private static final int ROW = 20;
	private final RaceClass raceClass;
	private final int mode;
	/** The class tab being shown; starts on the bird's own class. */
	private RaceClass shown;
	private int stakeIdx;
	private Button stakeButton;
	private int scroll;
	private int maxScroll;
	private int columnsY;
	private int left;
	private int columnWidth;
	private final java.util.ArrayList<Button> moving = new java.util.ArrayList<>();
	private final java.util.ArrayList<Integer> movingBaseY = new java.util.ArrayList<>();

	public CourseSelectScreen(int classId, int mode) {
		super(Component.translatable(mode == 1 ? "chocobosreborn.select.duel_title" : "chocobosreborn.select.title"));
		this.raceClass = RaceClass.byId(classId);
		this.shown = this.raceClass;
		this.mode = mode;
	}

	@Override
	protected void init() {
		// every course of the bird's class and below (lower classes pay half and do not count toward promotion)
		moving.clear();
		movingBaseY.clear();
		courseButtons.clear();
		scroll = 0;
		int w = Math.min(460, width - 16);
		int x = (width - w) / 2;
		left = x;
		int y = HEADER + 4;
		int classes = raceClass.getId() + 1;
		if (classes > 1) {
			int tab = (w - 4 * (classes - 1)) / classes;
			for (int c = 0; c < classes; c++) {
				RaceClass rc = RaceClass.byId(c);
				Button b = Button.builder(Component.translatable("chocobosreborn.select.tab", rc.name()), btn -> {
					shown = rc;
					rebuildWidgets();
				}).bounds(x + c * (tab + 4), y, tab, 18).build();
				b.active = rc != shown;
				track(addRenderableWidget(b), y);
			}
			y += ROW + 2;
		}
		columnWidth = (w - 4) / 2;
		columnsY = y;
		y += 12;
		List<RaceTrack> sprints = RaceTrack.sprintsOf(shown);
		List<RaceTrack> grands = RaceTrack.grandsPrixOf(shown);
		boolean lower = shown.getId() < raceClass.getId();
		if (mode == 1) {
			stakes = DuelStakes.upTo(DuelStakes.biggestPurse(shown));
			stakeIdx = Math.min(stakeIdx, stakes.length - 1);
		}
		for (int i = 0; i < sprints.size(); i++) {
			course(sprints.get(i), x, y + i * ROW, lower);
		}
		for (int i = 0; i < grands.size(); i++) {
			course(grands.get(i), x + columnWidth + 4, y + i * ROW, lower);
		}
		y += Math.max(sprints.size(), grands.size()) * ROW + 4;
		if (mode == 1) {
			y += 4;
			stakeButton = addRenderableWidget(Button.builder(stakeLabel(), b -> {
				stakeIdx = (stakeIdx + 1) % stakes.length;
				stakeButton.setMessage(stakeLabel());
				for (CourseButton cb : courseButtons) {
					cb.button().setTooltip(net.minecraft.client.gui.components.Tooltip.create(tip(cb.track(), cb.lower())));
				}
			}).bounds(x, y, w, 20).build());
			track(stakeButton, y);
			y += 22;
		}
		track(addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, y + 2, w, 20).build()),
				y + 2);
		maxScroll = Math.max(0, y + 26 - (height - 4));
	}

	/**
	 * One course button: name, laps and length, and the points a ranked win earns when it
	 * would count (a heat, the bird's own class, below S); the tooltip has the theme,
	 * features, lap length and the first-place purse.
	 */
	private void course(RaceTrack t, int bx, int by, boolean lower) {
		Component name = Component.translatable("chocobosreborn.track." + t.id());
		boolean counts = mode == 0 && !lower && shown != RaceClass.S;
		Component label = counts
				? Component.translatable("chocobosreborn.select.row_pts", name, length(t, false), t.winPoints())
				: Component.translatable("chocobosreborn.select.row", name, length(t, false));
		if (counts && font.width(label) > columnWidth - 8) {
			// the longest names: "5×630 m" keeps the points on the button at 480 x 270
			label = Component.translatable("chocobosreborn.select.row_pts", name, length(t, true), t.winPoints());
		}
		Button button = Button.builder(label, b -> choose(t)).bounds(bx, by, columnWidth, 18)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(tip(t, lower))).build();
		courseButtons.add(new CourseButton(button, t, lower));
		track(addRenderableWidget(button), by);
	}

	/** The course tooltip; in a duel it names the side bet this course actually takes. */
	private Component tip(RaceTrack t, boolean lower) {
		boolean counts = mode == 0 && !lower && shown != RaceClass.S;
		int purse = RaceScoring.purse(t);
		Component win;
		if (mode == 0) {
			win = counts ? Component.translatable("chocobosreborn.select.win", t.winPoints(), t.getRaceClass().pointsToPromote(), purse)
					: Component.translatable("chocobosreborn.select.win_gp", lower ? purse / 2 : purse);
		} else {
			win = Component.translatable("chocobosreborn.select.duel_stake", stakeFor(t), purse);
		}
		return Component.translatable("chocobosreborn.select.tip", Math.round(t.lapLength()), t.getLaps(), features(t),
				win, lower ? Component.translatable("chocobosreborn.select.lower") : Component.empty());
	}

	private int stakeFor(RaceTrack t) {
		return DuelStakes.onCourse(stakes[stakeIdx], t);
	}

	private record CourseButton(Button button, RaceTrack track, boolean lower) {
	}

	/**
	 * "1 lap · 1150 m" for a sprint, "5 laps × 600 m" for a grand prix, or "5×600 m" when
	 * {@code compact} (lap rounded to 10 blocks).
	 */
	static Component length(RaceTrack t, boolean compact) {
		long lap = Math.round(t.lapLength() / 10.0D) * 10L;
		if (t.isSprint()) {
			return Component.translatable("chocobosreborn.select.len.sprint", lap);
		}
		return Component.translatable(compact ? "chocobosreborn.select.len.gp_short" : "chocobosreborn.select.len.gp",
				t.getLaps(), lap);
	}

	private void track(Button button, int baseY) {
		moving.add(button);
		movingBaseY.add(baseY);
	}

	private void applyScroll() {
		for (int i = 0; i < moving.size(); i++) {
			moving.get(i).setY(movingBaseY.get(i) - scroll);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int next = net.minecraft.util.Mth.clamp(scroll - (int) Math.round(scrollY * 18), 0, maxScroll);
		if (next != scroll) {
			scroll = next;
			applyScroll();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseY < HEADER) {
			return false;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int cy = columnsY - scroll + 1;
		if (cy > HEADER) {
			g.drawString(font, Component.translatable("chocobosreborn.select.col.sprints"), left + 2, cy, 0xFFE8A416);
			g.drawString(font, Component.translatable("chocobosreborn.select.col.grands"), left + columnWidth + 6, cy, 0xFFE8A416);
		}
		g.fill(0, 0, width, HEADER, 0xCC101010);
		g.drawCenteredString(font, title, width / 2, 14, 0xFFE8A416);
		g.drawCenteredString(font, Component.translatable("chocobosreborn.select.class", raceClass.name()), width / 2, 26, 0xFFCCCCCC);
	}

	private Component stakeLabel() {
		return Component.translatable("chocobosreborn.select.stake", stakes[stakeIdx]);
	}

	private static Component features(RaceTrack t) {
		java.util.Map<RaceTrack.Feature.Type, Integer> counts = new java.util.EnumMap<>(RaceTrack.Feature.Type.class);
		for (RaceTrack.Feature f : t.features()) {
			counts.merge(f.type(), 1, Integer::sum);
		}
		net.minecraft.network.chat.MutableComponent out = Component.translatable("chocobosreborn.select.theme."
				+ t.theme().name().toLowerCase(java.util.Locale.ROOT));
		for (var e : counts.entrySet()) {
			out.append(" · ").append(Component.translatable("chocobosreborn.select.f." + e.getKey().name().toLowerCase(java.util.Locale.ROOT)));
			if (e.getValue() > 1) {
				out.append(" x" + e.getValue());
			}
		}
		return out;
	}

	private void choose(RaceTrack t) {
		PacketDistributor.sendToServer(new RacePayloads.CourseChoice(t.ordinal(), mode, mode == 1 ? stakeFor(t) : 0));
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static void open(int classId, int mode) {
		Minecraft.getInstance().setScreen(new CourseSelectScreen(classId, mode));
	}
}
