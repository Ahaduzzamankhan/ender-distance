package dev.enderdistance;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnderDistance implements ClientModInitializer {
    public static final String MOD_ID = "enderdistance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EnderDistanceConfig config = new EnderDistanceConfig();
    private static final CameraTracker TRACKER = new CameraTracker();
    private static final DirectionalCuller CULLER = new DirectionalCuller();
    private static final DebugOverlay OVERLAY = new DebugOverlay();

    private static ClientLevel level;
    private static String version = "unknown";
    private static long lastCullNanos;
    private static int droppedSections;

    @Override
    public void onInitializeClient() {
        config = EnderDistanceConfig.load();
        RenderCompat.detect();
        version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        LOGGER.info("Ender Distance {} loaded, renderer is {}", version, RenderCompat.renderer().label());
    }

    /**
     * Called once per frame with the sections vanilla wants to draw. They are already
     * frustum and occlusion tested; only the ones the camera demonstrably has no use
     * for are dropped. Nothing outside this list is touched.
     */
    public static void onSectionsVisible(ObjectArrayList<RenderSection> sections) {
        if (!config.enabled || RenderCompat.renderer() != RenderCompat.Renderer.VANILLA || sections.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (level != null) {
                level = null;
                TRACKER.reset();
                CULLER.reset();
            }
            return;
        }

        if (minecraft.level != level) {
            level = minecraft.level;
            TRACKER.reset();
            CULLER.reset();
        }

        Camera camera = minecraft.gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        TRACKER.update(camera, minecraft.player, config);

        int renderDistance = minecraft.options.getEffectiveRenderDistance();
        if (CULLER.needsRebuild(TRACKER, renderDistance)) {
            CULLER.rebuild(TRACKER, config, renderDistance);
        }

        long start = config.debug ? System.nanoTime() : 0L;
        droppedSections = CULLER.filter(sections);
        lastCullNanos = config.debug ? System.nanoTime() - start : 0L;
    }

    public static void drawDebugOverlay(GuiGraphicsExtractor graphics) {
        OVERLAY.render(graphics);
    }

    static EnderDistanceConfig config() {
        return config;
    }

    static CameraTracker tracker() {
        return TRACKER;
    }

    static DirectionalCuller culler() {
        return CULLER;
    }

    static String version() {
        return version;
    }

    static String status() {
        if (!config.enabled) {
            return "Disabled";
        }
        RenderCompat.Renderer renderer = RenderCompat.renderer();
        return renderer == RenderCompat.Renderer.VANILLA ? "Active" : "Inactive (" + renderer.label() + ")";
    }

    static long lastCullNanos() {
        return lastCullNanos;
    }

    static int droppedSections() {
        return droppedSections;
    }
}