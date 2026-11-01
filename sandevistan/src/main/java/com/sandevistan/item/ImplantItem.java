package com.sandevistan.item;

import com.sandevistan.implant.ImplantTier;
import com.sandevistan.implant.SandManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Right click to install the implant (consumed). A better model replaces a worse one, never the reverse. */
public class ImplantItem extends Item {
    private final ImplantTier tier;

    public ImplantItem(Settings settings, ImplantTier tier) {
        super(settings);
        this.tier = tier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (player instanceof ServerPlayerEntity sp) {
            if (SandManager.get(sp).tier() >= tier.id) {
                SandManager.note(sp, "message.sandevistan.already", Formatting.YELLOW);
                return TypedActionResult.fail(stack);
            }
            SandManager.install(sp, tier);
            if (!sp.isCreative()) stack.decrement(1);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return tier == ImplantTier.ARASAKA;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.sandevistan." + tier.key).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.sandevistan.install").formatted(Formatting.GRAY));
    }
}
