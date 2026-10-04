package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Bundle (vision) + Rope (speech), combined. Always mutes chat (see
 * ClientEvents) and always blocks vision (Bundle's own overlay, reused).
 * Deliberately does NOT get Rope's additive head-wrap layer - Bundle's own
 * geometry already covers the whole head, so it would never be visible
 * underneath (unlike SleepMaskRopeRestraint, see that class). */
public class BundleRopeRestraint extends AbstractComboHeadRestraint {

    public BundleRopeRestraint() {
    }

    public BundleRopeRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    public static final ResourceLocation ID = ModRestraints.BUNDLE_ROPE.getId();
    public ResourceLocation getId() {
        return ID;
    }

    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.bundle_rope.action_bar";
    }
    public String getName() {
        return "info.cuffedaddon.restraints.bundle_rope.name";
    }

    public static final Item ITEM = ModItems.BUNDLE_ROPE.get();
    public Item getItem() {
        return ITEM;
    }
    public static final Item KEY = null;
    public Item getKeyItem() {
        return KEY;
    }

    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/gui/bundle_rope_overlay.png");
    // Own, independently-editable copies - see BundleDuctTapeRestraint.
    private static final ResourceLocation MODEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/entity/bundle_rope.png");

    public ResourceLocation getVisionOverlayTexture() {
        return OVERLAY_TEXTURE;
    }
    protected ResourceLocation getModelTexture() {
        return MODEL_TEXTURE;
    }

    // [stated] noticed all 4 combo restraints share AbstractComboHeadRestraint's
    // generic WOOL_PLACE placeholder equip/unequip sound, and asked specifically
    // for the two Bundle-based combos to use Cuffed's own real Bundle restraint
    // sound instead (confirmed from Cuffed's actual source,
    // BundleRestraint#getEquipSound/getUnequipSound) - SleepMask's two combos are
    // deliberately left on the shared default, not asked for.
    public net.minecraft.sounds.SoundEvent getEquipSound() {
        return net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS;
    }
    public net.minecraft.sounds.SoundEvent getUnequipSound() {
        return net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS;
    }
}
