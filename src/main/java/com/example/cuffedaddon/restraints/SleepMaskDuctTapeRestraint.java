package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sleep Mask (vision) + Duck Tape (speech), combined. Always mutes chat
 * (see ClientEvents) and always blocks vision (Sleep Mask's own overlay,
 * reused). */
public class SleepMaskDuctTapeRestraint extends AbstractComboHeadRestraint {

    public SleepMaskDuctTapeRestraint() {
    }

    public SleepMaskDuctTapeRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    public static final ResourceLocation ID = ModRestraints.SLEEP_MASK_DUCT_TAPE.getId();
    public ResourceLocation getId() {
        return ID;
    }

    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.sleep_mask_duct_tape.action_bar";
    }
    public String getName() {
        return "info.cuffedaddon.restraints.sleep_mask_duct_tape.name";
    }

    public static final Item ITEM = ModItems.SLEEP_MASK_DUCT_TAPE.get();
    public Item getItem() {
        return ITEM;
    }
    public static final Item KEY = null;
    public Item getKeyItem() {
        return KEY;
    }

    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/gui/sleep_mask_duct_tape_overlay.png");
    // Own, independently-editable copies of Sleep Mask's own texture set -
    // see BundleDuctTapeRestraint for the same reasoning.
    private static final ResourceLocation MODEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/entity/sleep_mask_duct_tape.png");

    public ResourceLocation getVisionOverlayTexture() {
        return OVERLAY_TEXTURE;
    }
    protected ResourceLocation getModelTexture() {
        return MODEL_TEXTURE;
    }
}
