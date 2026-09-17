package tk.darrow.chocobosreborn.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import tk.darrow.chocobosreborn.net.RacePayloads;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceTrack;

/**
 * Pick one of the class's six courses (three short drags, three long ovals).
 * Ranked heats start at once; a duel challenge also picks a GP stake and then
 * waits for a second rider at the duel master.
 */
public class CourseSelectScreen extends Screen {
	private static final int[] STAKES = {0, 4, 8, 16, 32};
	private final RaceClass raceClass;
	private final int mode;
	private int stakeIdx;
	private Button stakeButton;

	public CourseSelectScreen(int classId, int mode) {
		super(Component.translatable(mode == 1 ? "chocobosreborn.select.duel_title" : "chocobosreborn.select.title"));
		this.raceClass = RaceClass.byId(classId);
		this.mode = mode;
	}

	@Override
	protected void init() {
		// every course of the bird's class and below (lower classes pay half and do not count toward promotion)
		int w = Math.min(340, width - 40);
		int x = (width - w) / 2;
		int y = 40;
		int col = (w - 4) / 2;
		for (int c = 0; c <= raceClass.getId(); c++) {
			RaceClass rc = RaceClass.byId(c);
			List<RaceTrack> tracks = RaceTrack.ofClass(rc);
			for (int i = 0; i < tracks.size(); i++) {
				RaceTrack t = tracks.get(i);
				Component label = Component.translatable("chocobosreborn.select.entry_short", rc.name(),
						Component.translatable("chocobosreborn.track." + t.id()),
						Component.translatable(t.isShort() ? "chocobosreborn.select.short" : "chocobosreborn.select.long"));
				Component tip = Component.translatable("chocobosreborn.select.tip", Math.round(t.lapLength()), t.getLaps(), features(t),
						c < raceClass.getId() ? Component.translatable("chocobosreborn.select.lower") : Component.empty());
				int bx = x + (t.isShort() ? 0 : col + 4), by = y + (i % 3) * 20;   // sprints left, grands prix right
				addRenderableWidget(Button.builder(label, b -> choose(t)).bounds(bx, by, col, 18)
						.tooltip(net.minecraft.client.gui.components.Tooltip.create(tip)).build());
			}
			y += 3 * 20 + 4;
		}
		if (mode == 1) {
			y += 8;
			stakeButton = addRenderableWidget(Button.builder(stakeLabel(), b -> {
				stakeIdx = (stakeIdx + 1) % STAKES.length;
				stakeButton.setMessage(stakeLabel());
			}).bounds(x, y, w, 20).build());
			y += 22;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, y + 4, w, 20).build());
	}

	private Component stakeLabel() {
		return Component.translatable("chocobosreborn.select.stake", STAKES[stakeIdx]);
	}

	private static Component features(RaceTrack t) {
		java.util.Map<RaceTrack.Feature.Type, Integer> counts = new java.util.EnumMap<>(RaceTrack.Feature.Type.class);
		for (RaceTrack.Feature f : t.features()) {
			counts.merge(f.type(), 1, Integer::sum);
		}
		net.minecraft.network.chat.MutableComponent out = Component.translatable("chocobosreborn.select.theme."
				+ t.theme().name().toLowerCase(java.util.Locale.ROOT));
		for (var e : counts.entrySet()) {
			out.append(" \u00b7 ").append(Component.translatable("chocobosreborn.select.f." + e.getKey().name().toLowerCase(java.util.Locale.ROOT)));
			if (e.getValue() > 1) {
				out.append(" x" + e.getValue());
			}
		}
		return out;
	}

	private void choose(RaceTrack t) {
		PacketDistributor.sendToServer(new RacePayloads.CourseChoice(t.ordinal(), mode, mode == 1 ? STAKES[stakeIdx] : 0));
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawCenteredString(font, title, width / 2, 14, 0xFFE8A416);
		g.drawCenteredString(font, Component.translatable("chocobosreborn.select.class", raceClass.name()), width / 2, 26, 0xFFCCCCCC);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static void open(int classId, int mode) {
		Minecraft.getInstance().setScreen(new CourseSelectScreen(classId, mode));
	}
}
