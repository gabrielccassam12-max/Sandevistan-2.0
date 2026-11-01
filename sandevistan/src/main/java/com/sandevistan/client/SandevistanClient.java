package com.sandevistan.client;

import com.sandevistan.net.ActionPayload;
import com.sandevistan.net.DilationPayload;
import com.sandevistan.net.FxPayload;
import com.sandevistan.net.HudPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SandevistanClient implements ClientModInitializer {
    private static final String CAT = "key.categories.sandevistan";
    private static KeyBinding toggle, level, dash, barrage, slash, kinetic, execute, dodge, superJump, rift;
    private static boolean prevJump;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sandevistan." + name, InputUtil.Type.KEYSYM, code, CAT));
    }

    private static void send(int action) {
        ClientPlayNetworking.send(new ActionPayload(action));
    }

    @Override
    public void onInitializeClient() {
        toggle = key("toggle", GLFW.GLFW_KEY_Y);
        level = key("level", GLFW.GLFW_KEY_U);
        dash = key("dash", GLFW.GLFW_KEY_I);
        barrage = key("barrage", GLFW.GLFW_KEY_O);
        slash = key("slash", GLFW.GLFW_KEY_P);
        kinetic = key("kinetic", GLFW.GLFW_KEY_L);
        execute = key("execute", GLFW.GLFW_KEY_K);
        dodge = key("dodge", GLFW.GLFW_KEY_N);
        superJump = key("superjump", GLFW.GLFW_KEY_M);
        rift = key("rift", GLFW.GLFW_KEY_SEMICOLON);

        ClientTickEvents.START_WORLD_TICK.register(world -> ClientDilation.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSand.tick(client);
            if (client.player == null) return;
            while (toggle.wasPressed()) send(ActionPayload.TOGGLE);
            while (level.wasPressed()) send(ActionPayload.LEVEL);
            while (dash.wasPressed()) send(ActionPayload.DASH);
            while (barrage.wasPressed()) send(ActionPayload.BARRAGE);
            while (slash.wasPressed()) send(ActionPayload.SLASH);
            while (kinetic.wasPressed()) send(ActionPayload.KINETIC);
            while (execute.wasPressed()) send(ActionPayload.EXECUTE);
            while (dodge.wasPressed()) send(ActionPayload.DODGE);
            while (superJump.wasPressed()) send(ActionPayload.SUPERJUMP);
            while (rift.wasPressed()) send(ActionPayload.RIFT);

            // jump pressed in mid-air (wall jump)
            boolean jumping = client.options.jumpKey.isPressed();
            if (ClientSand.active && jumping && !prevJump && !client.player.isOnGround()) send(ActionPayload.JUMP);
            prevJump = jumping;
        });

        ClientPlayNetworking.registerGlobalReceiver(DilationPayload.ID, (payload, context) -> ClientDilation.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.ID, (payload, context) -> ClientSand.receiveHud(payload));
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.ID, (payload, context) -> ClientSand.receiveFx(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientDilation.reset();
            ClientSand.reset();
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SandOverlay.render(ctx, tickCounter.getTickDelta(false)));
    }
}
