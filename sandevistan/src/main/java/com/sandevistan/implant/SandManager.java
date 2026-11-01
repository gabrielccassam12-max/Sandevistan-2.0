package com.sandevistan.implant;

import com.sandevistan.SandevistanMod;
import com.sandevistan.config.SandConfig;
import com.sandevistan.net.DilationPayload;
import com.sandevistan.net.FxPayload;
import com.sandevistan.net.HudPayload;
import com.sandevistan.time.ServerDilation;
import com.sandevistan.time.WorldDilation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side brain of the implant: install, activate, energy, stress, cyberpsychosis, attributes and syncing. */
public final class SandManager {
    private SandManager() {}

    public static final AttachmentType<ImplantData> DATA = AttachmentRegistry.<ImplantData>builder()
            .persistent(ImplantData.CODEC)
            .copyOnDeath()
            .buildAndRegister(SandevistanMod.id("implant"));

    private static final Map<UUID, SandState> STATES = new HashMap<>();
    private static final Map<UUID, Double> OTHER_SLOW = new HashMap<>();
    private static final Map<Integer, Integer> LAST_SENT = new HashMap<>();

    private static final Identifier SPEED_ID = SandevistanMod.id("owner_speed");
    private static final Identifier JUMP_ID = SandevistanMod.id("owner_jump");
    private static final Identifier SLOW_SPEED_ID = SandevistanMod.id("slow_speed");
    private static final Identifier SLOW_JUMP_ID = SandevistanMod.id("slow_jump");

    public static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.1f, 0.85f, 1.0f), 0.9f);
    public static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1.0f, 0.85f, 0.1f), 0.9f);

    // ------------------------------------------------------------ data
    public static ImplantData get(ServerPlayerEntity p) {
        return p.getAttachedOrElse(DATA, ImplantData.DEFAULT);
    }

    public static void set(ServerPlayerEntity p, ImplantData d) {
        p.setAttached(DATA, d);
    }

    public static ImplantTier tierOf(ServerPlayerEntity p) {
        return ImplantTier.of(get(p).tier());
    }

    public static SandState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> {
            SandState s = new SandState();
            s.energy = tierOf(p).energy;
            s.level = Math.max(1, get(p).level());
            return s;
        });
    }

    public static boolean isActive(ServerPlayerEntity p) {
        SandState s = STATES.get(p.getUuid());
        return s != null && s.active;
    }

    // ------------------------------------------------------------ setup
    public static void init() {
        ServerTickEvents.START_WORLD_TICK.register(SandManager::updateWorld);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tickPlayer(p);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            STATES.remove(p.getUuid());
            clearAttributes(p);
            sendHud(p, state(p));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            clearAttributes(p);
            STATES.remove(p.getUuid());
            OTHER_SLOW.remove(p.getUuid());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            STATES.remove(newPlayer.getUuid());
            clearAttributes(newPlayer);
            sendHud(newPlayer, state(newPlayer));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            STATES.clear();
            OTHER_SLOW.clear();
            LAST_SENT.clear();
            ServerDilation.clear();
        });
    }

    // ------------------------------------------------------------ install / switch
    public static void install(ServerPlayerEntity p, ImplantTier tier) {
        set(p, new ImplantData(tier.id, Math.min(get(p).level(), tier.maxLevel)));
        SandState s = state(p);
        if (s.active) deactivate(p, "manual");
        s.energy = tier.energy;
        s.stress = 0;
        s.cooldown = 0;
        s.level = Math.min(s.level, tier.maxLevel);
        ServerWorld w = (ServerWorld) p.getWorld();
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.4, p.getZ(), 40, 0.4, 0.5, 0.4, 0.3);
        sfx(w, p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1f, 0.7f);
        p.sendMessage(Text.translatable("message.sandevistan.installed", tier.displayName()).formatted(Formatting.AQUA), true);
        sendHud(p, s);
    }

    public static void toggle(ServerPlayerEntity p) {
        ImplantTier t = tierOf(p);
        SandState s = state(p);
        if (t == ImplantTier.NONE) {
            note(p, "message.sandevistan.no_implant", Formatting.RED);
            return;
        }
        if (s.active) {
            deactivate(p, "manual");
            return;
        }
        if (s.overload > 0) {
            note(p, "message.sandevistan.overloaded", Formatting.RED);
            return;
        }
        if (s.cooldown > 0) {
            note(p, "message.sandevistan.cooling", Formatting.YELLOW, s.cooldown / 20 + 1);
            return;
        }
        if (s.energy < t.energy * 0.1f) {
            note(p, "message.sandevistan.low_energy", Formatting.RED);
            return;
        }
        activate(p, s, t);
    }

    public static void cycleLevel(ServerPlayerEntity p) {
        ImplantTier t = tierOf(p);
        if (t == ImplantTier.NONE) {
            note(p, "message.sandevistan.no_implant", Formatting.RED);
            return;
        }
        SandState s = state(p);
        s.level = s.level % t.maxLevel + 1;
        set(p, new ImplantData(t.id, s.level));
        if (s.active) applyBoost(p, s, t);
        note(p, "message.sandevistan.level", Formatting.AQUA, s.level, Text.translatable("level.sandevistan." + s.level));
        sendHud(p, s);
    }

    private static void activate(ServerPlayerEntity p, SandState s, ImplantTier t) {
        s.active = true;
        s.activeTicks = 0;
        s.level = Math.min(Math.max(1, get(p).level()), t.maxLevel);
        s.rechargedNotified = false;
        applyBoost(p, s, t);

        ServerWorld w = (ServerWorld) p.getWorld();
        p.swingHand(Hand.MAIN_HAND, true);
        for (int i = 0; i < 4; i++) { // an energy pulse travelling up the body
            final int k = i;
            Scheduler.later(1 + i * 2, () -> {
                if (!p.isAlive()) return;
                double y = p.getY() + 0.2 + k * 0.5;
                for (int j = 0; j < 14; j++) {
                    double a = j * Math.PI * 2 / 14;
                    w.spawnParticles(CYAN, p.getX() + Math.cos(a) * 0.6, y, p.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
                }
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), y, p.getZ(), 6, 0.35, 0.1, 0.35, 0.2);
            });
        }
        ring(w, p, 4.5, 36, ParticleTypes.END_ROD);
        sfx(w, p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.3f, 0.5f);
        sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.7f, 1.4f);
        sfx(w, p, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.6f);
        ServerPlayNetworking.send(p, new FxPayload(FxPayload.ACTIVATE, s.level, 0f));
        sendHud(p, s);
    }

    public static void deactivate(ServerPlayerEntity p, String reason) {
        SandState s = state(p);
        if (!s.active) return;
        s.active = false;
        s.riftTicks = 0;
        s.wallRunning = false;
        s.slideTicks = 0;
        s.cooldown = SandConfig.INSTANCE.cooldownTicks;
        clearOwnerAttributes(p);

        ServerWorld w = (ServerWorld) p.getWorld();
        ring(w, p, 3.0, 24, ParticleTypes.CLOUD);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.7, 0.4, 0.25);
        sfx(w, p, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.7f);
        sfx(w, p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.6f);
        ServerPlayNetworking.send(p, new FxPayload(FxPayload.DEACTIVATE, 0, 0f));
        if ("energy".equals(reason)) note(p, "message.sandevistan.energy_out", Formatting.RED);
        sendHud(p, s);
    }

    /** CYBERPSYCHOSIS: the implant fries, shuts down and the player pays for it. */
    private static void overload(ServerPlayerEntity p, SandState s) {
        deactivate(p, "overload");
        s.overload = SandConfig.INSTANCE.overloadLockTicks;
        s.stress = 0.5f;
        s.cooldown = Math.max(s.cooldown, SandConfig.INSTANCE.cooldownTicks);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 140, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0));
        ServerWorld w = (ServerWorld) p.getWorld();
        p.damage(w.getDamageSources().magic(), 3f);
        w.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1.5, p.getZ(), 12, 0.3, 0.2, 0.3, 0.1);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.5, 0.7, 0.5, 0.5);
        sfx(w, p, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
        sfx(w, p, SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, 1.2f, 0.6f);
        ServerPlayNetworking.send(p, new FxPayload(FxPayload.OVERLOAD, 0, 1f));
        p.sendMessage(Text.translatable("message.sandevistan.cyberpsychosis").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        sendHud(p, s);
    }

    // ------------------------------------------------------------ skills
    public static boolean canUse(ServerPlayerEntity p, Skill skill, boolean quiet) {
        SandState s = state(p);
        ImplantTier t = tierOf(p);
        if (!s.active) {
            if (!quiet) note(p, "message.sandevistan.not_active", Formatting.RED);
            return false;
        }
        if (t.id < skill.minTier) {
            if (!quiet) note(p, "message.sandevistan.skill_locked", Formatting.RED);
            return false;
        }
        if (!SandConfig.INSTANCE.enabled(skill.key)) return false;
        if (s.skillCd[skill.ordinal()] > 0) return false;
        if (s.energy < skill.energy) {
            if (!quiet) note(p, "message.sandevistan.low_energy", Formatting.RED);
            return false;
        }
        return true;
    }

    public static void pay(ServerPlayerEntity p, Skill skill) {
        SandState s = state(p);
        s.energy -= skill.energy;
        s.skillCd[skill.ordinal()] = skill.cooldown;
        if (SandConfig.INSTANCE.overloadSystem) {
            s.stress = Math.min(1f, s.stress + skill.stress * (float) SandConfig.INSTANCE.stressMultiplier);
        }
    }

    // ------------------------------------------------------------ per-player tick
    private static void tickPlayer(ServerPlayerEntity p) {
        SandState s = state(p);
        ImplantTier t = tierOf(p);
        if (t == ImplantTier.NONE) return;

        if (s.cooldown > 0) s.cooldown--;
        if (s.overload > 0) s.overload--;
        if (s.dodgeLock > 0) s.dodgeLock--;
        if (s.slideCd > 0) s.slideCd--;
        if (s.wallCd > 0) s.wallCd--;
        for (int i = 0; i < s.skillCd.length; i++) {
            if (s.skillCd[i] > 0) s.skillCd[i]--;
        }

        if (s.active) {
            if (!p.isAlive() || p.isSpectator()) {
                deactivate(p, "manual");
                return;
            }
            s.activeTicks++;
            SandConfig cfg = SandConfig.INSTANCE;
            float drain = t.drain * ImplantTier.LEVEL_DRAIN[s.level - 1] * (float) cfg.energyDrainMultiplier;
            if (cfg.overloadSystem && s.stress > 0.5f) { // the energy flickers under heavy stress
                drain *= 1f + (s.stress - 0.5f) * 1.2f * p.getRandom().nextFloat();
            }
            s.energy -= drain;
            if (cfg.overloadSystem) {
                s.stress = Math.min(1f, s.stress + t.stress * ImplantTier.LEVEL_STRESS[s.level - 1] * (float) cfg.stressMultiplier);
            }
            if (s.riftTicks > 0 && --s.riftTicks == 0) Abilities.riftEnd(p);

            if (s.energy <= 0) {
                s.energy = 0;
                deactivate(p, "energy");
            } else if (cfg.overloadSystem && s.stress >= 1f) {
                overload(p, s);
            } else {
                Movement.tick(p, s, t);
                Abilities.autoDodge(p, s);
                if (cfg.overloadSystem && s.stress > 0.7f && p.getRandom().nextFloat() < 0.08f) { // sparking implant
                    ((ServerWorld) p.getWorld()).spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.6, p.getZ(), 4, 0.2, 0.2, 0.2, 0.2);
                }
            }
        } else {
            Movement.idle(p, s);
            if (s.cooldown == 0 && s.overload == 0 && s.energy < t.energy) {
                s.energy = Math.min(t.energy, s.energy + (float) SandConfig.INSTANCE.energyRegenPerTick);
                if (s.energy >= t.energy && !s.rechargedNotified) {
                    s.rechargedNotified = true;
                    ServerWorld w = (ServerWorld) p.getWorld();
                    sfx(w, p, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
                    sfx(w, p, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
                    ServerPlayNetworking.send(p, new FxPayload(FxPayload.RECHARGED, 0, 0f));
                }
            }
            s.stress = Math.max(0f, s.stress - 0.0015f);
        }

        if (--s.hudTimer <= 0) {
            s.hudTimer = s.active ? 2 : 10;
            sendHud(p, s);
        }
    }

    // ------------------------------------------------------------ world dilation
    private static void updateWorld(ServerWorld w) {
        WorldDilation d = ServerDilation.of(w);
        SandConfig cfg = SandConfig.INSTANCE;

        float target = 1f;
        List<Integer> owners = new ArrayList<>();
        d.owners.clear();
        for (ServerPlayerEntity p : w.getPlayers()) {
            SandState s = STATES.get(p.getUuid());
            if (s != null && s.active) {
                ImplantTier t = ImplantTier.of(get(p).tier());
                float f = s.riftTicks > 0 ? 0f : t.worldFactor(s.level);
                target = Math.min(target, f);
                owners.add(p.getId());
                d.owners.add(p.getId());
            }
        }
        d.target = target;
        d.mode = switch (cfg.affect.toUpperCase()) {
            case "MOBS_ONLY" -> WorldDilation.MODE_MOBS;
            case "SELECTED" -> WorldDilation.MODE_SELECTED;
            default -> WorldDilation.MODE_ALL;
        };
        d.projectiles = cfg.affectProjectiles;
        d.selected.clear();
        d.selected.addAll(cfg.selectedEntities);
        d.excluded.clear();
        d.excluded.addAll(cfg.excludedEntities);
        d.tickRamp();
        ServerDilation.refreshRunning();

        slowOtherPlayers(w, d);

        // tell the clients (on change, and every second as a heartbeat for late joiners / dimension changes)
        List<String> filter = new ArrayList<>(d.mode == WorldDilation.MODE_SELECTED ? cfg.selectedEntities : List.of());
        for (String ex : cfg.excludedEntities) filter.add("!" + ex);
        int hash = (Float.hashCode(target) * 31 + owners.hashCode()) * 31 + d.mode;
        boolean changed = LAST_SENT.getOrDefault(System.identityHashCode(w), 0) != hash;
        if (changed || w.getTime() % 20 == 0) {
            LAST_SENT.put(System.identityHashCode(w), hash);
            DilationPayload payload = new DilationPayload(target, d.mode, owners, filter);
            for (ServerPlayerEntity p : w.getPlayers()) ServerPlayNetworking.send(p, payload);
        }
    }

    /** Players are driven by their own client, so other players are slowed through their movement attributes. */
    private static void slowOtherPlayers(ServerWorld w, WorldDilation d) {
        boolean slow = d.running() && SandConfig.INSTANCE.affectOtherPlayers && d.mode == WorldDilation.MODE_ALL;
        for (ServerPlayerEntity p : w.getPlayers()) {
            boolean owner = d.owners.contains(p.getId());
            if (slow && !owner) {
                double mult = Math.max(0.02, d.current);
                Double last = OTHER_SLOW.get(p.getUuid());
                if (last == null || Math.abs(last - mult) > 0.01) {
                    modifier(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW_SPEED_ID, mult - 1.0);
                    modifier(p, EntityAttributes.GENERIC_JUMP_STRENGTH, SLOW_JUMP_ID, (mult - 1.0) * 0.6);
                    OTHER_SLOW.put(p.getUuid(), mult);
                }
            } else if (OTHER_SLOW.remove(p.getUuid()) != null) {
                remove(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW_SPEED_ID);
                remove(p, EntityAttributes.GENERIC_JUMP_STRENGTH, SLOW_JUMP_ID);
            }
        }
    }

    // ------------------------------------------------------------ attributes
    private static void modifier(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id, double value) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void remove(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst != null) inst.removeModifier(id);
    }

    private static void applyBoost(ServerPlayerEntity p, SandState s, ImplantTier t) {
        double mult = t.playerMultiplier(s.level);
        modifier(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, mult - 1.0);
        modifier(p, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID, (mult - 1.0) * 0.12);
    }

    private static void clearOwnerAttributes(ServerPlayerEntity p) {
        remove(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID);
        remove(p, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID);
    }

    private static void clearAttributes(ServerPlayerEntity p) {
        clearOwnerAttributes(p);
        remove(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW_SPEED_ID);
        remove(p, EntityAttributes.GENERIC_JUMP_STRENGTH, SLOW_JUMP_ID);
        OTHER_SLOW.remove(p.getUuid());
    }

    // ------------------------------------------------------------ sync + helpers
    public static void sendHud(ServerPlayerEntity p, SandState s) {
        ImplantTier t = tierOf(p);
        float energy = t.energy <= 0 ? 0 : s.energy / t.energy;
        int remaining = 0;
        if (s.active) {
            float drain = Math.max(0.01f, t.drain * ImplantTier.LEVEL_DRAIN[s.level - 1] * (float) SandConfig.INSTANCE.energyDrainMultiplier);
            remaining = (int) (s.energy / drain);
        }
        ServerPlayNetworking.send(p, new HudPayload(HudPayload.pack(s.active, s.level, t.id), energy, s.stress,
                s.cooldown, s.overload, remaining));
    }

    public static void fx(ServerPlayerEntity p, int type, int a, float b) {
        ServerPlayNetworking.send(p, new FxPayload(type, a, b));
    }

    public static void note(ServerPlayerEntity p, String key, Formatting color, Object... args) {
        p.sendMessage(Text.translatable(key, args).formatted(color), true);
    }

    public static void sfx(ServerWorld w, ServerPlayerEntity p, SoundEvent sound, float volume, float pitch) {
        w.playSound(null, p.getBlockPos(), sound, SoundCategory.PLAYERS, volume * (float) SandConfig.INSTANCE.soundIntensity, pitch);
    }

    public static void ring(ServerWorld w, ServerPlayerEntity p, double r, int n, net.minecraft.particle.ParticleEffect fx) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            w.spawnParticles(fx, p.getX() + Math.cos(a) * r, p.getY() + 0.3, p.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0.02);
        }
    }
}
