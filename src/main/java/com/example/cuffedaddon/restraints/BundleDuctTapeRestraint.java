package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Bundle (vision) + Duck Tape (speech), combined. Always mutes chat (see
 * ClientEvents) and always blocks vision (Bundle's own overlay, reused). */
public class BundleDuctTapeRestraint extends AbstractComboHeadRestraint {

    public BundleDuctTapeRestraint() {
    }

    public BundleDuctTapeRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    public static final ResourceLocation ID = ModRestraints.BUNDLE_DUCT_TAPE.getId();
    public ResourceLocation getId() {
        return ID;
    }

    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.bundle_duct_tape.action_bar";
    }
    public String getName() {
        return "info.cuffedaddon.restraints.bundle_duct_tape.name";
    }

    public static final Item ITEM = ModItems.BUNDLE_DUCT_TAPE.get();
    public Item getItem() {
        return ITEM;
    }
    public static final Item KEY = null;
    public Item getKeyItem() {
        return KEY;
    }

    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/gui/bundle_duct_tape_overlay.png");
    // Own, independently-editable copies of Cuffed's own bundle.png/
    // bundle_overlay.png - [stated] wants every combo's art separable from
    // its source items, not a shared reference.
    private static final ResourceLocation MODEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/entity/bundle_duct_tape.png");

    public ResourceLocation getVisionOverlayTexture() {
        return OVERLAY_TEXTURE;
    }
    protected ResourceLocation getModelTexture() {
        return MODEL_TEXTURE;
    }

    // See BundleRopeRestraint's own comment - same request, same reasoning,
    // Cuffed's own real Bundle restraint sound instead of the shared
    // WOOL_PLACE placeholder.
    public net.minecraft.sounds.SoundEvent getEquipSound() {
        return net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS;
    }
    public net.minecraft.sounds.SoundEvent getUnequipSound() {
        return net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS;
    }
}
