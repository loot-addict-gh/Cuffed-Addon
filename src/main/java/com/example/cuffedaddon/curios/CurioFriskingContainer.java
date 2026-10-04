package com.example.cuffedaddon.curios;

import java.util.List;

import javax.annotation.Nonnull;

import com.lazrproductions.cuffed.inventory.FriskingContainer;
import com.lazrproductions.cuffed.items.PossessionsBox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Extends Cuffed's own FriskingContainer - indices 0-44 (target's armor,
 * offhand, main inventory, hotbar) stay completely untouched, exactly as
 * Cuffed built them - with extra indices 45+ mapped onto every VISIBLE curio
 * slot the target currently has equipped, whatever mod added it (rings,
 * belts, backpacks, Leashable Collars' paw/collar slots, etc). Nothing here
 * hardcodes a slot type identifier - we just read whatever
 * ICuriosItemHandler#getCurios() reports for this entity right now, so any
 * currently-installed curio-slot-adding mod is picked up automatically, and
 * a fresh container is built per frisk attempt so slot counts that change
 * at runtime (e.g. a "+2 trinket slots" modifier) are always current.
 *
 * Deliberately does NOT use ICuriosItemHandler#getEquippedCurios() directly
 * - that helper flattens every slot type in the map regardless of
 * isVisible(), which would (a) expose slots some mod intentionally hides
 * from players and (b) desync our curioSlotLabels list, which IS filtered
 * by isVisible(). Instead we rebuild the same kind of CombinedInvWrapper
 * Curios uses internally, filtering out non-visible slot types ourselves so
 * the label list and the wrapper's slot indices always line up 1:1.
 *
 * Only ever constructed from CurioFriskCompat, which is only ever reached
 * after confirming Curios is loaded.
 */
public class CurioFriskingContainer extends FriskingContainer {

    private static final int CURIO_INDEX_OFFSET = 45;

    private final IItemHandlerModifiable curios;
    private final List<String> curioSlotLabels;
    private final ItemStack boxStack;

    public CurioFriskingContainer(@Nonnull ServerPlayer player, @Nonnull ItemStack boxStack) {
        super(player, boxStack);
        this.boxStack = boxStack;

        CurioFriskUtil.Result resolved = CurioFriskUtil.resolve(player);
        this.curios = resolved.handler;
        this.curioSlotLabels = resolved.labels;
    }

    public int getCurioSlotCount() {
        return curios.getSlots();
    }

    @Nonnull
    public String getCurioSlotLabel(int curioIndex) {
        return curioIndex >= 0 && curioIndex < curioSlotLabels.size() ? curioSlotLabels.get(curioIndex) : "";
    }

    private boolean isCurioIndex(int index) {
        return index >= CURIO_INDEX_OFFSET && index < CURIO_INDEX_OFFSET + curios.getSlots();
    }

    @Override
    public int getContainerSize() {
        return CURIO_INDEX_OFFSET + curios.getSlots();
    }

    @Override
    public boolean isEmpty() {
        if (!super.isEmpty()) {
            return false;
        }
        for (int i = 0; i < curios.getSlots(); i++) {
            if (!curios.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    @Override
    public ItemStack getItem(int index) {
        if (isCurioIndex(index)) {
            return curios.getStackInSlot(index - CURIO_INDEX_OFFSET);
        }
        return super.getItem(index);
    }

    @Nonnull
    @Override
    public ItemStack removeItem(int index, int count) {
        if (isCurioIndex(index)) {
            takeCurio(index - CURIO_INDEX_OFFSET, count);
            return ItemStack.EMPTY;
        }
        return super.removeItem(index, count);
    }

    @Nonnull
    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (isCurioIndex(index)) {
            int curioIndex = index - CURIO_INDEX_OFFSET;
            takeCurio(curioIndex, curios.getStackInSlot(curioIndex).getCount());
            return ItemStack.EMPTY;
        }
        return super.removeItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, @Nonnull ItemStack stack) {
        if (isCurioIndex(index)) {
            // Frisking is take-only here too, same as Cuffed's own FriskingSlot
            // (mayPlace() == false) for the vanilla-inventory slots.
            return;
        }
        super.setItem(index, stack);
    }

    /**
     * Extracts first, THEN adds whatever actually came out to the box - unlike
     * Cuffed's own FriskingContainer#removeItem, which adds a copy of the full
     * stack to the box before removing anything. That ordering is harmless for
     * a plain player inventory (removal there can't fail), but Curios'
     * DynamicStackHandler#extractItem can legitimately refuse (e.g. a
     * curse-of-binding-equivalent item via CurioUnequipEvent) - extracting
     * first avoids ever duplicating an item into the box that the target
     * actually got to keep.
     */
    private void takeCurio(int curioIndex, int count) {
        ItemStack current = curios.getStackInSlot(curioIndex);
        if (current.isEmpty()) {
            return;
        }
        ItemStack extracted = curios.extractItem(curioIndex, count, false);
        if (!extracted.isEmpty()) {
            PossessionsBox.add(boxStack, extracted);
        }
    }
}
