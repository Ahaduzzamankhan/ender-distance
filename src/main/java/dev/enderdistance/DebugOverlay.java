package dev.enderdistance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class DebugOverlay {
    private static final int TEXT_COLOR = 0xFFFFE0A0;
    private static final int DIM_COLOR = 0xFFB0B0B0;
    private static final int LINE_HEIGHT = 10;

    void render(GuiGraphicsExtractor graphics) {
        EnderDistanceConfig config = EnderDistance.config();
        if (!config.debug) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Font font = minecraft.font;
        DirectionalCuller culler = EnderDistance.culler();
        CameraTracker tracker = EnderDistance.tracker();
        int y = 4;

        y = draw(graphics, font, y, TEXT_COLOR,
                "Ender Distance " + EnderDistance.version() + "  " + EnderDistance.status());

        y = draw(graphics, font, y, DIM_COLOR,
                "fps " + minecraft.getFps()
                        + "  chunks culled " + culler.culledChunks() + "/" + culler.trackedChunks()
                        + "  sections dropped " + EnderDistance.droppedSections()
                        + "  grid " + (culler.lastRebuildNanos() / 1000L) + "us"
                        + "  filter " + (EnderDistance.lastCullNanos() / 1000L) + "us");

        y = draw(graphics, font, y, DIM_COLOR,
                "heading " + round(degrees(tracker.heading()))
                        + "  pitch " + round(tracker.pitch())
                        + "  turn stress " + round(tracker.turnStress() * 100.0f) + '%');

        float movement = tracker.movementHeading();
        String movementText = Float.isNaN(movement) ? "standing still" : round(degrees(movement)) + " deg";
        draw(graphics, font, y, DIM_COLOR, "movement " + movementText);
    }

    private static int draw(GuiGraphicsExtractor graphics, Font font, int y, int color, String text) {
        graphics.text(font, text, 4, y, color, true);
        return y + LINE_HEIGHT;
    }

    private static float degrees(float radians) {
        return (float) Math.toDegrees(radians);
    }

    private static int round(float value) {
        return Math.round(value);
    }
}