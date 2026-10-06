package dev.enderdistance;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Resolves which renderer is in charge of the terrain draw calls. The directional
 * culler only touches the vanilla section list, so any renderer that replaces that
 * pipeline is detected and the optimisation steps aside instead of fighting it.
 */
final class RenderCompat {
    enum Renderer {
        VANILLA("Vanilla"),
        SODIUM("Sodium"),
        IRIS("Iris"),
        DISTANT_HORIZONS("Distant Horizons");

        private final String label;

        Renderer(String label) {
            this.label = label;
        }

        String label() {
            return this.label;
        }
    }

    private static Renderer renderer = Renderer.VANILLA;

    private RenderCompat() {
    }

    static void detect() {
        FabricLoader loader = FabricLoader.getInstance();

        if (loader.isModLoaded("sodium")) {
            renderer = Renderer.SODIUM;
        } else if (loader.isModLoaded("iris") || loader.isModLoaded("oculus")) {
            renderer = Renderer.IRIS;
        } else if (loader.isModLoaded("distanthorizons")) {
            renderer = Renderer.DISTANT_HORIZONS;
        } else {
            renderer = Renderer.VANILLA;
        }
    }

    static Renderer renderer() {
        return renderer;
    }
}