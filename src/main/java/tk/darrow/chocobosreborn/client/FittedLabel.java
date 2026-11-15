package tk.darrow.chocobosreborn.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws a label inside a fixed width. German and CJK lines are longer than the
 * English this UI was measured against. The game font already draws those glyphs;
 * scaling keeps the line inside the control.
 */
final class FittedLabel {
	private FittedLabel() {
	}

	static void left(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color, boolean shadow) {
		int width = font.width(text);
		if (width <= maxWidth || width <= 0 || maxWidth <= 0) {
			g.drawString(font, text, x, y, color, shadow);
			return;
		}
		float scale = maxWidth / (float) width;
		var pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.scale(scale, scale, 1.0F);
		g.drawString(font, text, 0, 0, color, shadow);
		pose.popPose();
	}

	static void center(GuiGraphics g, Font font, Component text, int centerX, int y, int maxWidth, int color, boolean shadow) {
		int width = font.width(text);
		if (width <= maxWidth || width <= 0 || maxWidth <= 0) {
			g.drawString(font, text, centerX - width / 2, y, color, shadow);
			return;
		}
		float scale = maxWidth / (float) width;
		var pose = g.pose();
		pose.pushPose();
		pose.translate(centerX, y, 0);
		pose.scale(scale, scale, 1.0F);
		g.drawString(font, text, -width / 2, 0, color, shadow);
		pose.popPose();
	}
}
