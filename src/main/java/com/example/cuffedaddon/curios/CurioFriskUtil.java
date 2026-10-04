package com.example.cuffedaddon.curios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Reads an entity's currently-equipped, VISIBLE curio items into a single
 * combined item handler plus a matching per-slot label list (the owning
 * ISlotType identifier, e.g. "curios:ring"), guaranteed index-aligned with
 * each other.
 *
 * Used from two places that must never disagree on slot count: the real
 * server-side CurioFriskingContainer, and CurioFriskCompat's extra-data
 * writer (which only needs the label list/count to tell the client how many
 * dummy slots to build before real contents arrive via normal slot-sync
 * packets). Keeping the filtering logic in one place is what guarantees
 * those two call sites can't drift apart.
 */
final class CurioFriskUtil {

    private CurioFriskUtil() {
    }

    static Result resolve(@Nonnull LivingEntity entity) {
        IItemHandlerModifiable handler = new CombinedInvWrapper();
        List<String> labels = new ArrayList<>();

        ICuriosItemHandler curiosHandler = CuriosApi.getCuriosInventory(entity).orElse(null);
        if (curiosHandler != null) {
            List<IItemHandlerModifiable> visibleHandlers = new ArrayList<>();
            for (Map.Entry<String, ICurioStacksHandler> entry : curiosHandler.getCurios().entrySet()) {
                ICurioStacksHandler stacksHandler = entry.getValue();
                if (!stacksHandler.isVisible()) {
                    continue;
                }
                visibleHandlers.add(stacksHandler.getStacks());
                int slotCount = stacksHandler.getStacks().getSlots();
                for (int i = 0; i < slotCount; i++) {
                    labels.add(entry.getKey());
                }
            }
            if (!visibleHandlers.isEmpty()) {
                handler = new CombinedInvWrapper(visibleHandlers.toArray(new IItemHandlerModifiable[0]));
            }
        }

        return new Result(handler, labels);
    }

    static final class Result {
        final IItemHandlerModifiable handler;
        final List<String> labels;

        private Result(IItemHandlerModifiable handler, List<String> labels) {
            this.handler = handler;
            this.labels = labels;
        }
    }
}
