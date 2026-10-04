package com.example.cuffedaddon.curios;

import com.example.cuffedaddon.CuffedAddon;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.entity.base.IDetainableEntity;
import com.lazrproductions.cuffed.init.ModItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Two listeners, both HIGHEST priority so they win the race against
 * Cuffed's own frisking listener (registered at HIGH, with no
 * receiveCanceled = true): if we cancel the event here first, Cuffed's
 * handler is skipped for this interaction entirely, by default Forge
 * event-bus behavior. Every other interaction (duct tape, anchoring,
 * ordinary player-on-player clicks, etc) is untouched, since both only
 * cancel when their own narrow condition matches.
 *
 * onFriskAttempt: the exact same possessions-box-frisk interaction Cuffed's
 * own ModServerEvents#playerInteractEntity already handles (item ==
 * possessions_box, main hand, target's arms restrained) - but only when the
 * "curios" mod is installed - and opens our own CurioFriskingMenu instead of
 * Cuffed's plain FriskingMenu.
 *
 * onFriskPilloryAttempt: bypasses Cuffed's armsRestrained() gate entirely
 * for a target detained by Cuffed's OWN Pillory (a block-tether system,
 * IDetainableEntity#getDetained() - NOT the restraint capability at all,
 * see that method's own doc for why), dispatching to Curios or plain frisk
 * depending on what's installed.
 *
 * Deliberately NOT importing any top.theillusivec4.curios.* type anywhere in
 * this class's fields or method signatures - only CurioFriskCompat (invoked
 * from inside the isLoaded() guard below) does that. That keeps this class
 * safe to load unconditionally: on a setup without Curios installed, these
 * listeners either no-op (onFriskAttempt) or fall back to plain frisking
 * (onFriskPilloryAttempt), and Cuffed's own vanilla-only frisking screen
 * behaves exactly as it did before this feature existed everywhere else.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class PossessionsBoxCurioEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFriskAttempt(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide() == LogicalSide.CLIENT) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!ModList.get().isLoaded("curios")) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }

        // Mirrors Cuffed's own early-return in ModServerEvents#playerInteractEntity:
        // a player who is themselves currently detained (pillory-style, distinct
        // from armsRestrained) can't interact with anyone at all. Our listener runs
        // BEFORE Cuffed's own check even executes (that's the whole point of
        // HIGHEST priority), so without this we'd let a detained frisker bypass
        // that restriction entirely whenever Curios happens to be installed.
        if (((IDetainableEntity) player).getDetained() > -1) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.POSSESSIONSBOX.get())) {
            return;
        }

        IRestrainableCapability targetCap = CuffedAPI.Capabilities.getRestrainableCapability(target);
        if (targetCap == null || !targetCap.armsRestrained()) {
            return;
        }

        CurioFriskCompat.frisk(player, target, stack);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * ROUND (this session, corrected): [stated] confirmed the menu doesn't
     * open AT ALL for a Pillory target - tracing Cuffed's real
     * PilloryBlock#use()/attemptToToggleDetained() source shows why my
     * first attempt at this was wrong: Pillory is implemented ENTIRELY
     * through IDetainableEntity#detainToBlock (a block-tether system) and
     * never touches the restraint capability at all - getHeadRestraint()
     * is null for a pilloried player, it's never set to a PilloryRestraint
     * instance by the block. (PilloryRestraint the class exists and is
     * registered in ModRestraints, but nothing in the actual pillory
     * block's interaction path ever equips it - it appears to only be
     * reachable, if at all, through some other trigger this addon doesn't
     * use.) So the previous `getHeadRestraint() instanceof PilloryRestraint`
     * check could never match a real pilloried player - always false,
     * hence the menu never opening.
     *
     * Fixed to check the actual real signal Cuffed's own PilloryBlock uses
     * for this: IDetainableEntity#getDetained() > -1 (block-tethered to
     * something). PilloryBlock is the only block extending
     * Cuffed's DetentionBlock as of the source reviewed, so this
     * uniquely identifies a pilloried target for now - if a future Cuffed
     * update adds another DetentionBlock subtype, this would need
     * narrowing back down to specifically Pillory-detained players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFriskPilloryAttempt(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide() == LogicalSide.CLIENT) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (((IDetainableEntity) player).getDetained() > -1) {
            return;
        }
        if (((IDetainableEntity) target).getDetained() <= -1) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.POSSESSIONSBOX.get())) {
            return;
        }

        if (ModList.get().isLoaded("curios")) {
            CurioFriskCompat.frisk(player, target, stack);
        } else {
            com.lazrproductions.cuffed.items.PossessionsBox.frisk(player, target, stack);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
