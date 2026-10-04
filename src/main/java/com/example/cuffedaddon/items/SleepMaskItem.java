package com.example.cuffedaddon.items;

import com.lazrproductions.cuffed.items.base.AbstractHeadRestraintItem;
import net.minecraft.world.item.Item;

/**
 * Unlike RopeItem (an "ambiguous" AbstractRestraintItem that can go on
 * head/arms/legs depending on interaction context, mirroring Duck Tape),
 * this extends AbstractHeadRestraintItem directly - it can ONLY ever be
 * applied as a head restraint. AbstractHeadRestraintItem already supplies
 * the correct tooltip line ("info.cuffed.restraint_type.head") and routes
 * through Cuffed's PlayerCanEquipHeadRestraintEntitySelector / dispenser
 * logic automatically just by being that type, same as how Bundle is
 * special-cased for those - no extra plumbing needed here.
 */
public class SleepMaskItem extends AbstractHeadRestraintItem {
    public SleepMaskItem(Item.Properties properties) {
        super(properties);
    }
}
