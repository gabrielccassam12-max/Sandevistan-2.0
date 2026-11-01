package com.sandevistan;

import com.sandevistan.config.SandConfig;
import com.sandevistan.implant.Abilities;
import com.sandevistan.implant.SandManager;
import com.sandevistan.implant.Scheduler;
import com.sandevistan.implant.Skill;
import com.sandevistan.implant.Movement;
import com.sandevistan.net.ActionPayload;
import com.sandevistan.net.DilationPayload;
import com.sandevistan.net.FxPayload;
import com.sandevistan.net.HudPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SandevistanMod implements ModInitializer {
    public static final String MOD_ID = "sandevistan";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        SandConfig.load();
        ModRegistry.init();
        Scheduler.init();
        SandManager.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DilationPayload.ID, DilationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HudPayload.ID, HudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FxPayload.ID, FxPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            switch (payload.action()) {
                case ActionPayload.TOGGLE -> SandManager.toggle(p);
                case ActionPayload.LEVEL -> SandManager.cycleLevel(p);
                case ActionPayload.DASH -> Abilities.use(p, Skill.DASH);
                case ActionPayload.BARRAGE -> Abilities.use(p, Skill.BARRAGE);
                case ActionPayload.SLASH -> Abilities.use(p, Skill.SLASH);
                case ActionPayload.KINETIC -> Abilities.use(p, Skill.KINETIC);
                case ActionPayload.EXECUTE -> Abilities.use(p, Skill.EXECUTE);
                case ActionPayload.DODGE -> Abilities.use(p, Skill.DODGE);
                case ActionPayload.SUPERJUMP -> Abilities.use(p, Skill.SUPERJUMP);
                case ActionPayload.RIFT -> Abilities.use(p, Skill.RIFT);
                case ActionPayload.JUMP -> Movement.jumpPressed(p, SandManager.state(p));
                default -> {}
            }
        });
    }
}
