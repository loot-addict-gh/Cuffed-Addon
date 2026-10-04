package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sleep Mask (vision) + Rope (speech), combined. Always mutes chat (see
 * ClientEvents) and always blocks vision (Sleep Mask's own overlay,
 * reused).
 *
 * The one combo (of 4) that ALSO gets Rope's existing additive head-wrap
 * layer (RopeHeadWrapModel/rope_wrap_head.png) drawn underneath its own
 * base layer - see RopeWrapEntityLayer, which checks this restraint's ID
 * alongside RopeHeadRestraint's own. Unlike Bundle (a full head-covering
 * sack), Sleep Mask is going to be hand-edited down to a narrower shape
 * that won't fully hide the head, so the wrap layer will actually show
 * through/around it - the other 3 combos don't get this layer since their
 * base geometry (Bundle, or Sleep Mask paired with Duck Tape which has no
 * wrap layer to add) would hide it entirely or it doesn't apply. */
public class SleepMaskRopeRestraint extends AbstractComboHeadRestraint {

    public SleepMaskRopeRestraint() {
    }

    public SleepMaskRopeRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    public static final ResourceLocation ID = ModRestraints.SLEEP_MASK_ROPE.getId();
    public ResourceLocation getId() {
        return ID;
    }

    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.sleep_mask_rope.action_bar";
    }
    public String getName() {
        return "info.cuffedaddon.restraints.sleep_mask_rope.name";
    }

    public static final Item ITEM = ModItems.SLEEP_MASK_ROPE.get();
    public Item getItem() {
        return ITEM;
    }
    public static final Item KEY = null;
    public Item getKeyItem() {
        return KEY;
    }

    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/gui/sleep_mask_rope_overlay.png");
    // Own, independently-editable copies - see SleepMaskDuctTapeRestraint.
    private static final ResourceLocation MODEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/entity/sleep_mask_rope.png");

    public ResourceLocation getVisionOverlayTexture() {
        return OVERLAY_TEXTURE;
    }
    protected ResourceLocation getModelTexture() {
        return MODEL_TEXTURE;
    }
}
