package com.sandevistan.net;

import com.sandevistan.SandevistanMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * Server -> clients: the time-dilation state of the world the player is in.
 * "filter" lists the selected entity types, and types prefixed with "!" are excluded.
 */
public record DilationPayload(float target, int mode, List<Integer> owners, List<String> filter) implements CustomPayload {
    public static final CustomPayload.Id<DilationPayload> ID = new CustomPayload.Id<>(SandevistanMod.id("dilation"));
    public static final PacketCodec<RegistryByteBuf, DilationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, DilationPayload::target,
            PacketCodecs.VAR_INT, DilationPayload::mode,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), DilationPayload::owners,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), DilationPayload::filter,
            DilationPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
