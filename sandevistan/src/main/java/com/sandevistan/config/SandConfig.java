package com.sandevistan.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything adjustable lives in config/sandevistan.json (created on first launch).
 * Gameplay fields are read by the SERVER (so admins control multiplayer); visual/audio fields by each CLIENT.
 */
public class SandConfig {
    public static SandConfig INSTANCE = new SandConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---------------- gameplay (server)
    /** World speed for levels 1, 2, 3 (1.0 = normal). */
    public double[] worldSpeed = {0.40, 0.15, 0.03};
    /** Player speed multiplier for levels 1, 2, 3. */
    public double[] playerSpeed = {1.5, 2.5, 4.0};
    /** Who gets slowed: ALL (everything but the user), MOBS_ONLY, or SELECTED (only the types listed below). */
    public String affect = "ALL";
    public List<String> selectedEntities = new ArrayList<>(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"));
    /** Entity types that are never slowed (useful for fragile modded entities). */
    public List<String> excludedEntities = new ArrayList<>();
    public boolean affectProjectiles = true;
    public boolean affectOtherPlayers = true;
    /** Ticks of lockout after the implant is switched off. */
    public int cooldownTicks = 100;
    public double energyDrainMultiplier = 1.0;
    public double energyRegenPerTick = 0.6;
    /** Cyberpsychosis: stress builds while active and forces a shutdown at 100%. */
    public boolean overloadSystem = true;
    public double stressMultiplier = 1.0;
    public int overloadLockTicks = 200;
    public Map<String, Boolean> abilities = defaultAbilities();

    // ---------------- client
    public double visualIntensity = 1.0;
    public double soundIntensity = 1.0;
    public boolean screenFilter = true;
    public boolean chromaticAberration = true;
    public boolean speedLines = true;
    public boolean vignette = true;
    public boolean glitchEffects = true;
    public boolean cameraShake = true;

    private static Map<String, Boolean> defaultAbilities() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String s : new String[]{"dash", "barrage", "slash", "kinetic", "execute", "dodge", "superjump", "rift",
                "wallrun", "slide", "extrajump", "controlledfall"}) m.put(s, true);
        return m;
    }

    public boolean enabled(String ability) {
        return abilities.getOrDefault(ability, true);
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("sandevistan.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    SandConfig c = GSON.fromJson(r, SandConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            sanitize();
            try (Writer w = Files.newBufferedWriter(file)) { // rewrite so new options appear in old files
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[sandevistan] could not read config, using defaults: " + e);
            INSTANCE = new SandConfig();
        }
    }

    private static void sanitize() {
        SandConfig c = INSTANCE;
        if (c.worldSpeed == null || c.worldSpeed.length < 3) c.worldSpeed = new double[]{0.40, 0.15, 0.03};
        if (c.playerSpeed == null || c.playerSpeed.length < 3) c.playerSpeed = new double[]{1.5, 2.5, 4.0};
        if (c.affect == null) c.affect = "ALL";
        if (c.selectedEntities == null) c.selectedEntities = new ArrayList<>();
        if (c.excludedEntities == null) c.excludedEntities = new ArrayList<>();
        if (c.abilities == null) c.abilities = defaultAbilities();
        for (Map.Entry<String, Boolean> e : defaultAbilities().entrySet()) c.abilities.putIfAbsent(e.getKey(), e.getValue());
    }
}
