package com.example.cuffedaddon.items;

import com.lazrproductions.cuffed.items.base.AbstractHeadRestraintItem;
import net.minecraft.world.item.Item;

/** Combination head restraint: Bundle (vision) + Rope (speech). See
 * AbstractComboHeadRestraint for the shared restraint behavior. */
public class BundleRopeItem extends AbstractHeadRestraintItem {
    public BundleRopeItem(Item.Properties properties) {
        super(properties);
    }
}
