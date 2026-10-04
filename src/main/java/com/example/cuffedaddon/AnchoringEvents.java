package com.example.cuffedaddon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.entity.AnchorKnotEntity;
import com.lazrproductions.cuffed.entity.base.IAnchorableEntity;
import com.lazrproductions.cuffed.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Addon's own, independent right-click-block listener for the five new
 * anchor points (Iron Bars, Reinforced Bars, Chain, Lightning Rod, End
 * Rod). Deliberately a SEPARATE listener from Cuffed's own
 * ModServerEvents#playerInteractBlock rather than trying to hook into it -
 * that method is compiled into Cuffed's jar and only checks Forge's
 * Tags.Blocks.FENCES + vanilla TRIPWIRE_HOOK. Since our block set doesn't
 * overlap with those at all, both listeners simply fire independently on
 * their own blocks with no conflict - same additive pattern as the render
 * layers and the fixClearBars gamerule.
 *
 * Max chain length, suffocation length, and "only restrained players can be
 * anchored" are all deliberately NOT re-implemented here - the initial
 * lead-to-player anchoring step (where that last setting is enforced) is
 * entirely Cuffed's own, untouched code, and the distance-based checks
 * happen generically against IAnchorableEntity#getAnchor()'s position
 * regardless of anchor type, so they already apply to AnchorKnotEntity for
 * free.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class AnchoringEvents {

    @SubscribeEvent
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Level level = event.getEntity().level();
        if (level.isClientSide()) {
            return;
        }

        Player interacting = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!isSupportedAnchorBlock(state)) {
            return;
        }

        ArrayList<IAnchorableEntity> entitiesAnchoredToInteractor = new ArrayList<>();
        ServerLevel server = (ServerLevel) event.getLevel();
        if (server != null) {
            for (Iterator<Entity> iterator = server.getAllEntities().iterator(); iterator.hasNext();) {
                Entity next = iterator.next();
                if (next instanceof IAnchorableEntity en)
                    if (en.isAnchored() && en.getAnchor() != null && en.getAnchor().getUUID().equals(interacting.getUUID()))
                        entitiesAnchoredToInteractor.add(en);
            }
        }

        if (entitiesAnchoredToInteractor.isEmpty()) {
            return;
        }

        for (int i = 0; i < entitiesAnchoredToInteractor.size(); i++) {
            AnchorKnotEntity.bindEntityToNewOrExistingKnot(
                    (LivingEntity) entitiesAnchoredToInteractor.get(i), level, pos);
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean isSupportedAnchorBlock(BlockState state) {
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_IRON_BARS.get() && state.is(Blocks.IRON_BARS))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS.get() && state.is(ModBlocks.REINFORCED_BARS.get()))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS_WINDOWS.get() && state.is(ModBlocks.REINFORCED_BARS_GAPPED.get()))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_CHAIN.get() && state.is(Blocks.CHAIN))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_LIGHTNING_ROD.get() && state.is(Blocks.LIGHTNING_ROD))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_END_ROD.get() && state.is(Blocks.END_ROD))
            return true;
        return false;
    }
}
