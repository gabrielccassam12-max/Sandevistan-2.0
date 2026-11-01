package com.sandevistan.implant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Saved with the player and kept after death: which implant model is installed and the chosen level. */
public record ImplantData(int tier, int level) {
    public static final ImplantData DEFAULT = new ImplantData(0, 1);

    public static final Codec<ImplantData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("tier").forGetter(ImplantData::tier),
            Codec.INT.fieldOf("level").forGetter(ImplantData::level)
    ).apply(i, ImplantData::new));
}
