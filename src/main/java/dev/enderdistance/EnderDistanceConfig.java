package dev.enderdistance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EnderDistanceConfig {
    public boolean enabled = true;
    public float strength = 0.5f;
    public float forwardDistance = 1.0f;
    public float sideDistance = 0.85f;
    public float backDistance = 0.55f;
    public float smoothing = 0.5f;
    public boolean debug;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("enderdistance.json");
    }

    public static EnderDistanceConfig load() {
        EnderDistanceConfig loaded = new EnderDistanceConfig();
        Path file = configFile();

        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                EnderDistanceConfig parsed = GSON.fromJson(reader, EnderDistanceConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception e) {
                EnderDistance.LOGGER.warn("Could not read {}, keeping defaults", file, e);
            }
        }

        loaded.clamp();
        return loaded;
    }

    public void save() {
        clamp();
        Path file = configFile();

        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            EnderDistance.LOGGER.warn("Could not write {}", file, e);
        }
    }

    private void clamp() {
        strength = clamp(strength, 0.0f, 1.0f);
        forwardDistance = clamp(forwardDistance, 0.1f, 1.0f);
        sideDistance = clamp(sideDistance, 0.1f, 1.0f);
        backDistance = clamp(backDistance, 0.1f, 1.0f);
        smoothing = clamp(smoothing, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}