package com.example.cuffedaddon.items;

import com.lazrproductions.cuffed.items.base.AbstractRestraintItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Mirrors RopeItem exactly (which itself mirrors Cuffed's own DuckTapeItem).
 * Extending AbstractRestraintItem directly (rather than the Arm/Leg/Head
 * subclasses) marks this as an "ambiguous" restraint item, so Cuffed's
 * dispenser logic and generic restraint-application code decide head/arm/leg
 * placement based on context, exactly like Rope and Duck Tape - applicable
 * on legs, arms, and head, per [stated]'s explicit request rather than
 * splitting it into separate single-slot items.
 */
public class StraitjacketItem extends AbstractRestraintItem {
    public StraitjacketItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> components, @Nonnull TooltipFlag tooltipFlag) {
        components.add(Component.translatable("info.cuffed.restraint_type.head").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("info.cuffed.restraint_type.arm").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("info.cuffed.restraint_type.leg").withStyle(ChatFormatting.GRAY));
        components.add(Component.empty());
        components.add(Component.translatable("info.cuffedaddon.removed_with").withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(Component.translatable("info.cuffed.empty_hand").withStyle(ChatFormatting.WHITE)));
        super.appendHoverText(stack, level, components, tooltipFlag);
    }
}
