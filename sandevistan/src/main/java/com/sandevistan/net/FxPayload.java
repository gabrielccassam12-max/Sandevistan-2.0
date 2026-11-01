package com.sandevistan.net;

import com.sandevistan.SandevistanMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> owner: one-shot screen / camera / sound events. */
public record FxPayload(int type, int a, float b) implements CustomPayload {
    public static final int ACTIVATE = 0, DEACTIVATE = 1, DASH = 2, DODGE = 3, SHAKE = 4, CAMERA = 5,
            OVERLOAD = 6, IMPACT = 7, RECHARGED = 8;

    public static final CustomPayload.Id<FxPayload> ID = new CustomPayload.Id<>(SandevistanMod.id("fx"));
    public static final PacketCodec<RegistryByteBuf, FxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FxPayload::type,
            PacketCodecs.VAR_INT, FxPayload::a,
            PacketCodecs.FLOAT, FxPayload::b,
            FxPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
