package com.example.cuffedaddon.items;

import com.lazrproductions.cuffed.items.base.AbstractHeadRestraintItem;
import net.minecraft.world.item.Item;

/** Combination head restraint: Sleep Mask (vision) + Rope (speech). The
 * only one of the 4 combos that also gets Rope's additive head-wrap layer
 * underneath - see SleepMaskRopeRestraint and RopeWrapEntityLayer. */
public class SleepMaskRopeItem extends AbstractHeadRestraintItem {
    public SleepMaskRopeItem(Item.Properties properties) {
        super(properties);
    }
}
