package com.example.cuffedaddon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.3.x - Block Locker's "Option A" enforcement. Cuffed's own
 * ModServerEvents.playerMineBlock already cancels breaking for anything
 * in the STATIC cuffed:reinforced_blocks tag (untouched, not our
 * concern). This is the parallel check for the DYNAMIC, per-position set
 * ReinforcedPositionsSavedData tracks - same cancellation shape, just a
 * different data source, since we can't add to Cuffed's own listener
 * (it's compiled into its jar).
 *
 * Deliberately server-side only - no client-side attack-input canceller
 * (unlike Cuffed's own ModClientEvents, which has one for its static tag
 * case). That canceller exists purely so the client doesn't show mining
 * progress for a split second before the server round-trip reverts it -
 * a responsiveness nicety, not a correctness requirement, and skipping it
 * avoids needing to sync this position set to every client at all (no new
 * network packet, no join-time sync, nothing to keep in sync as positions
 * are added/removed). The trade-off is a minor visual flicker on an
 * unauthorized mining attempt - reasonable for a first version. [stated]
 * confirmed this is fine as-is (not an absolute requirement) after seeing
 * what it actually looks like in practice (the block visually breaks and
 * is instantly replaced, rather than never appearing to break at all).
 *
 * Auto-clear behavior, per [stated]'s exact spec: a reinforced position
 * stops being reinforced the moment its block is actually removed,
 * however that happens.
 *   - Normal mining (with a pickaxe, or in creative): handled right here
 *     in onBreak - if the break is going to be ALLOWED, clear the marker
 *     in the same pass rather than needing a second event.
 *   - Explosions: BlockEvent.BreakEvent is player-specific and never
 *     fires for these, so ExplosionEvent.Detonate is a separate, second
 *     hook that clears markers for every position the explosion is about
 *     to destroy.
 *   - KNOWN GAP, not yet handled: other removal paths with no matching
 *     Forge event - pistons pushing/pulling the block away, other mods'
 *     block-removal code, admin commands (/setblock, /fill), WorldEdit,
 *     etc. None of these are covered by either hook below, so a position
 *     reinforced this way could still go stale in those specific cases.
 *     Covering every possible removal path in general would need a much
 *     heavier approach (e.g. a chunk-level mixin watching every block
 *     state change) - not attempted here without being asked for it.
 *     [stated] confirmed this is acceptable as-is - a normal break still
 *     clears the marker regardless of what caused the block to need
 *     re-breaking, so this isn't a real exploit in practice, just a
 *     documented edge case.
 *
 * Required-tool check (onBreak): NOT hardcoded to pickaxe. Reads which of
 * the 4 standard "mineable with X" block tags (pickaxe/axe/shovel/hoe)
 * the target block is actually in, and requires the matching item tag -
 * so a reinforced dirt block needs a shovel, reinforced wood needs an
 * axe, etc, matching whatever tool that block would normally prefer,
 * rather than one tool for everything. Blocks with none of those 4 tags
 * (e.g. leaves, wool, cobweb - anything vanilla handles via shears/sword
 * "tool actions" instead of a mineable/X tag) fall back to requiring a
 * pickaxe, same as before - full shears/sword-type support would need
 * Forge's separate ToolAction API and felt like real scope creep for a
 * case [stated] only raised as an example, not a concrete need.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class ReinforcedPositionEvents {

    private static boolean hasRequiredTool(Player player, BlockState state) {
        ItemStack held = player.getMainHandItem();
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return held.is(ItemTags.PICKAXES);
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return held.is(ItemTags.AXES);
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return held.is(ItemTags.SHOVELS);
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return held.is(ItemTags.HOES);
        }
        return held.is(ItemTags.PICKAXES);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        ReinforcedPositionsSavedData data = ReinforcedPositionsSavedData.get(level);
        if (!data.isReinforced(pos)) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.isCreative() && !hasRequiredTool(player, event.getState())) {
            event.setCanceled(true);
            return;
        }

        // The break is allowed to proceed - this position is no longer
        // reinforced once it actually happens.
        data.remove(pos);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ReinforcedPositionsSavedData data = ReinforcedPositionsSavedData.get(level);
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (data.isReinforced(pos)) {
                data.remove(pos);
            }
        }
    }
}
