package com.sandevistan.net;

import com.sandevistan.SandevistanMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> owner: values for the HUD. packed = bit0 active | bits1-2 level | bits3-4 tier. */
public record HudPayload(int packed, float energy, float stress, int cooldown, int overload, int remaining) implements CustomPayload {
    public static final CustomPayload.Id<HudPayload> ID = new CustomPayload.Id<>(SandevistanMod.id("hud"));
    public static final PacketCodec<RegistryByteBuf, HudPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HudPayload::packed,
            PacketCodecs.FLOAT, HudPayload::energy,
            PacketCodecs.FLOAT, HudPayload::stress,
            PacketCodecs.VAR_INT, HudPayload::cooldown,
            PacketCodecs.VAR_INT, HudPayload::overload,
            PacketCodecs.VAR_INT, HudPayload::remaining,
            HudPayload::new);

    public static int pack(boolean active, int level, int tier) {
        return (active ? 1 : 0) | ((level & 3) << 1) | ((tier & 3) << 3);
    }

    public boolean active() { return (packed & 1) != 0; }
    public int level() { return (packed >> 1) & 3; }
    public int tier() { return (packed >> 3) & 3; }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
