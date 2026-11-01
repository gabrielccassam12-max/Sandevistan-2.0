package com.sandevistan.net;

import com.sandevistan.SandevistanMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: a key was pressed. */
public record ActionPayload(int action) implements CustomPayload {
    public static final int TOGGLE = 0, LEVEL = 1, DASH = 2, BARRAGE = 3, SLASH = 4, KINETIC = 5,
            EXECUTE = 6, DODGE = 7, SUPERJUMP = 8, RIFT = 9, JUMP = 10;

    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(SandevistanMod.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
            PacketCodecs.VAR_INT.xmap(ActionPayload::new, ActionPayload::action).cast();

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
