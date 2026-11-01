package com.sandevistan;

import com.sandevistan.implant.ImplantTier;
import com.sandevistan.item.ImplantItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModRegistry {
    private ModRegistry() {}

    public static final Item EXPERIMENTAL = register("sandevistan_experimental", ImplantTier.EXPERIMENTAL, Rarity.UNCOMMON);
    public static final Item MILITARY = register("sandevistan_military", ImplantTier.MILITARY, Rarity.RARE);
    public static final Item ARASAKA = register("sandevistan_arasaka", ImplantTier.ARASAKA, Rarity.EPIC);

    private static Item register(String name, ImplantTier tier, Rarity rarity) {
        return Registry.register(Registries.ITEM, SandevistanMod.id(name),
                new ImplantItem(new Item.Settings().maxCount(1).rarity(rarity), tier));
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(EXPERIMENTAL);
            entries.add(MILITARY);
            entries.add(ARASAKA);
        });
    }
}
