package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.pose.IWallPose;
import com.example.cuffedaddon.pose.WallPoseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side conveniences for a wall-posed player, mirrors cuffedaddon's
 * ClientLiePoseEvents closely - most of it is a direct copy since the
 * underlying problems (movement/scroll should visually stop registering,
 * the inventory screen has no server-side open event to cancel, remote
 * observers see a periodic vanilla position resync jitter) are identical
 * for any permanently-static posed player, wall or bed.
 *
 * The one real difference: NO bounding-box reassertion anywhere in this
 * class - this pose never touches the hitbox at all, so there's nothing to
 * re-shrink every tick/frame the way the bed version does.
 *
 * The 180-degree look clamp (MAX_LOOK_OFFSET_DEGREES = 90 either side) is
 * cuffedaddon's OWN confirmed-working Bed Restraint feature (added there
 * per an explicit request identical in shape to this one) - reused here
 * verbatim rather than inventing a new mixin-based approach, since it's
 * already proven to work with no mixin/reflection needed at all.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public class ClientWallPoseEvents {

    private static final float MAX_LOOK_OFFSET_DEGREES = 90.0F;

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!WallPoseUtil.isPosed(event.getEntity())) return;

        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && WallPoseUtil.isPosed(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !WallPoseUtil.isPosed(player)) return;

        if (event.getScreen() instanceof AbstractContainerScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * Same periodic-resync jitter fix as ClientLiePoseEvents.onClientTick -
     * unconditional moveTo every tick for every OTHER (non-local) wall-posed
     * player this client is tracking. No bounding-box line here (see class
     * doc) - that's the only difference from the bed version's loop body.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Player localPlayer = mc.player;
        for (Player player : mc.level.players()) {
            IWallPose cap = WallPoseUtil.get(player);
            if (cap != null && cap.isPosed()) {
                // yBodyRot (the torso's own facing) is NOT a networked field
                // at all - it's independently recomputed by every client's
                // own simulation of the entity via head-yaw catch-up logic
                // (LivingEntity's own "body slowly turns to follow where
                // you're looking" animation). Setting it server-side (see
                // WallPoseEvents.onPlayerTick) only affects the SERVER's own
                // bookkeeping - it does nothing to stop any observing
                // client (or the posed player's own client) from continuing
                // to swing the torso toward wherever the camera points.
                // Bed Restraint never hit this because its render mixin
                // recomputes the whole model orientation directly from the
                // capability, bypassing yBodyRot entirely - this pose has
                // no equivalent render mixin (deliberately, to avoid
                // touching the hitbox/rotation machinery at all), so it
                // needs this correction instead. Forced every tick, same
                // correction-based pattern as the hotbar/position fixes
                // elsewhere in this class - not prevented, just corrected
                // back immediately.
                player.setYBodyRot(cap.getLockedYaw());
                player.yBodyRotO = cap.getLockedYaw();

                if (player != localPlayer) {
                    player.moveTo(cap.getLockedX(), cap.getLockedY(), cap.getLockedZ());
                }
            }
        }

        if (localPlayer != null) {
            correctLocalHotbarSlot(localPlayer);
            clampLocalLookYaw(localPlayer);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            correctLocalHotbarSlot(localPlayer);
            clampLocalLookYaw(localPlayer);
        }
    }

    private static void correctLocalHotbarSlot(Player localPlayer) {
        IWallPose localCap = WallPoseUtil.get(localPlayer);
        if (localCap != null && localCap.isPosed()
                && localPlayer.getInventory().selected != localCap.getLockedSlot()) {
            localPlayer.getInventory().selected = localCap.getLockedSlot();
        }
    }

    private static void clampLocalLookYaw(Player localPlayer) {
        IWallPose cap = WallPoseUtil.get(localPlayer);
        if (cap == null || !cap.isPosed()) return;

        float locked = cap.getLockedYaw();
        float offset = Mth.wrapDegrees(localPlayer.getYRot() - locked);
        if (offset > MAX_LOOK_OFFSET_DEGREES) {
            float clamped = locked + MAX_LOOK_OFFSET_DEGREES;
            localPlayer.setYRot(clamped);
            localPlayer.setYHeadRot(clamped);
        } else if (offset < -MAX_LOOK_OFFSET_DEGREES) {
            float clamped = locked - MAX_LOOK_OFFSET_DEGREES;
            localPlayer.setYRot(clamped);
            localPlayer.setYHeadRot(clamped);
        }
    }
}
