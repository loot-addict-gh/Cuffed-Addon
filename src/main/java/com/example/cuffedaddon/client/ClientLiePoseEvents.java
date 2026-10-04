package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.pose.ILiePose;
import com.example.cuffedaddon.pose.LiePoseUtil;
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
 * Client-side conveniences for a lie-posed player - the server-side event
 * cancels in LiePoseEvents are the actual authoritative backstop (a modified
 * client can't bypass those), this is just for a clean, honest-client UX so
 * movement/scroll never even visually register while posed, plus two things
 * that can ONLY be handled client-side at all (see each method's doc).
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public class ClientLiePoseEvents {

    /**
     * ROUND 19: [stated] wants the posed player's own look range limited to
     * a 180-degree horizontal cone (90 degrees either side of their locked
     * body yaw) instead of the full free look this addon has always given
     * them - a nitpick, not expected to be security-critical (a modified
     * client could still send an out-of-range yaw; this is purely for the
     * honest-client feel, same framing as this whole class's own doc).
     *
     * No dedicated "player is about to rotate" event exists to cancel in
     * Forge 1.20.1 the way MovementInputUpdateEvent covers movement keys -
     * mouse-look is applied directly inside MouseHandler#turnPlayer, with
     * no clean interception point without a new mixin target. Same
     * correction-based shape as the hotbar-slot fix directly below instead:
     * let the client rotate freely, then clamp it back onto the allowed
     * range every tick (and again right before each frame's HUD draws, for
     * the same "zero visible gap" reason the hotbar fix does both) - feels
     * like hitting a soft wall past the limit, the same way vanilla's own
     * vertical pitch clamp already feels.
     */
    private static final float MAX_LOOK_OFFSET_DEGREES = 90.0F;

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!LiePoseUtil.isPosed(event.getEntity())) return;

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
        if (player != null && LiePoseUtil.isPosed(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks opening the player's own inventory screen (and, as a backstop,
     * any other container screen) while lie-posed. This has to be handled
     * here, not server-side: opening the survival inventory (E key) sends
     * no packet at all - the client already has all the inventory data
     * locally, so there's no server-side event to cancel, unlike right-
     * clicking a chest/furnace/etc (already blocked in LiePoseEvents'
     * onRightClickBlock, before a screen would ever be requested).
     * ScreenEvent.Opening is Forge's own cancellable pre-open hook, fired
     * for every screen about to replace the current one - only
     * AbstractContainerScreen (inventory, creative inventory, any container
     * GUI) is cancelled here, so the pause/options menu (not a container
     * screen) still opens normally.
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !LiePoseUtil.isPosed(player)) return;

        if (event.getScreen() instanceof AbstractContainerScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * Re-asserts the shrunk collision box every client tick for any player
     * this client currently has loaded who's lie-posed - guards against
     * normal entity-position interpolation silently recomputing the box
     * back to standing size between LiePoseSyncPacket deliveries (which
     * only fire on apply/clear/(re)tracking, not every tick). Cheap: the
     * player list is small and this only touches players the local
     * capability mirror already says are posed.
     *
     * Also self-corrects the LOCAL player's own selected hotbar slot back
     * to lockedSlot, same tick a number-key press changes it - the
     * server-side correction in LiePoseEvents (already authoritative, a
     * modified client can't bypass it) only runs on the SERVER's own tick
     * plus a network round-trip, which was visibly showing as the hotbar
     * briefly flickering to the pressed slot before "immediately"
     * reselecting the old one. Fixing it here too closes that gap to
     * effectively zero, purely for how it looks locally.
     *
     * ROUND (this session): [stated] did extensive elimination testing
     * (disabling shaders, vsync, the fps cap, every individual client mod
     * down to nothing, and confirmed near-zero ping via local hosting) and
     * the jitter persisted regardless - ruling out every external cause on
     * their end. That leaves the real, confirmed mechanism itself: vanilla
     * forces a full position resync to every OTHER client tracking a
     * player every 60 ticks (see LiePoseEvents' own onPlayerTick doc for
     * the full explanation) - normally invisible since a normally-moving
     * entity has nothing to visibly "jump" from, but for a permanently
     * static lie-posed entity, that periodic resync (and, per this
     * addon's own tick corrections, receiving another fresh sync) can
     * still set up a brief client-side interpolation window even when the
     * destination matches where it already renders.
     *
     * Rather than risk a mixin into Entity's low-level interpolation
     * internals (guessing the exact method signature there risks a hard
     * crash on mismatch, and 1.20.1's exact signature couldn't be pinned
     * down with confidence), this achieves the same end result with
     * something already proven safe: every client tick, for every posed
     * player this client knows about (not just the local one - the same
     * loop already reasserting the bounding box below), unconditionally
     * call Player#moveTo with the true locked position - a plain, publicly
     * inherited Entity method, no mixin or mapping needed at all.
     * Whatever partial interpolation vanilla's own tracking system set up
     * that tick gets immediately overwritten back to the true value before
     * the frame renders, every single tick, regardless of what triggered
     * it or why it's only visible on some hardware/rendering pipelines and
     * not others - this closes the gap unconditionally instead of trying
     * to chase why it manifests differently per client.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // ROUND (this session): [stated] found two real bugs this
        // introduced, both traced to the same two lines below.
        //
        // Bug 1 (camera locked to up/down only): the previous version
        // called the 5-argument Player#moveTo(x, y, z, yRot, xRot)
        // overload, passing cap.getLockedYaw() as yRot - that's the
        // player's REAL look/facing rotation (completely different from
        // setYBodyRot a few lines up, which only affects torso animation,
        // not the camera at all), so it was being forcibly stomped back to
        // the locked yaw 20 times a second, overriding any mouse-look
        // input the very next tick after it happened - only pitch (xRot,
        // left alone) still worked. Fixed by switching to the 3-argument
        // moveTo(x, y, z) overload, which only ever touches position, so
        // free look within the existing 90-degree cone (see
        // clampLocalLookYaw below) is untouched.
        //
        // Bug 2 (hitbox back to standing/upright): moveTo's own internal
        // repositioning recomputes the entity's bounding box back to its
        // normal standing dimensions as a side effect - since this ran
        // AFTER setBoundingBox(buildLieBoundingBox(...)) in the same tick,
        // it silently undid the custom shrunk/flat box every single tick.
        // Fixed by reordering: moveTo first, then setBoundingBox last, so
        // the custom box is always what's left standing at the end of the
        // tick.
        //
        // Bug 3 (this round): [stated] found the LOCAL player, when THEY
        // are the posed one, ends up rendered off the bed entirely while
        // the hitbox/wireframe stays correctly on it - a genuine new bug
        // from applying this same moveTo call to the local player too.
        // The original jitter this whole fix was built for was NEVER
        // about the local player's own view of themselves - every round
        // of testing confirmed self-view was always correct, the entire
        // problem was specifically OTHER clients watching a DIFFERENT
        // posed player. Calling moveTo unconditionally on ALL posed
        // players, local included, means it now also fights Minecraft's
        // own local-player movement/camera prediction system (a separate,
        // authoritative-feeling path the local player's position and
        // camera already follow correctly on their own) - forcing a raw
        // position set outside that system let the entity's actual
        // position and whatever the camera/prediction system still
        // thought drift apart. Fixed by skipping the local player in this
        // loop entirely - their own position was never broken and doesn't
        // need this correction; only other (remote) players being watched
        // do. The bounding box reassertion below is untouched and still
        // applies to the local player too - it was never part of the bug,
        // only the moveTo call was.
        Player localPlayer = mc.player;
        for (Player player : mc.level.players()) {
            ILiePose cap = LiePoseUtil.get(player);
            if (cap != null && cap.isPosed()) {
                if (player != localPlayer) {
                    player.moveTo(cap.getLockedX(), cap.getLockedY(), cap.getLockedZ());
                }
                player.setBoundingBox(LiePoseUtil.buildLieBoundingBox(
                        player.getX(), player.getY(), player.getZ(), cap.getLockedYaw()));
            }
        }

        if (localPlayer != null) {
            correctLocalHotbarSlot(localPlayer);
            clampLocalLookYaw(localPlayer);
        }
    }

    /**
     * Same correction as above, run again right before the HUD draws each
     * frame (RenderGuiEvent.Pre fires after that frame's input handling has
     * already run, unlike ClientTickEvent whose ordering relative to key
     * handling isn't something this environment can verify without a live
     * client). Belt-and-suspenders: whichever of the two actually runs
     * first each frame is harmless - both just reassert the same value.
     * This one is the one that actually guarantees the hotbar can never be
     * seen showing the wrong slot, even for a single frame.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            correctLocalHotbarSlot(localPlayer);
            clampLocalLookYaw(localPlayer);
        }
    }

    private static void correctLocalHotbarSlot(Player localPlayer) {
        ILiePose localCap = LiePoseUtil.get(localPlayer);
        if (localCap != null && localCap.isPosed()
                && localPlayer.getInventory().selected != localCap.getLockedSlot()) {
            localPlayer.getInventory().selected = localCap.getLockedSlot();
        }
    }

    /**
     * Clamps the local player's own yRot/yHeadRot to within
     * MAX_LOOK_OFFSET_DEGREES of their locked body yaw, wherever it
     * currently sits - see this class's own MAX_LOOK_OFFSET_DEGREES doc
     * for why this is a clamp-after rather than a prevent-before.
     * Mth.wrapDegrees normalizes the difference into (-180, 180] first, so
     * this works correctly across the yaw wraparound point regardless of
     * which way the locked yaw itself happens to face.
     */
    private static void clampLocalLookYaw(Player localPlayer) {
        ILiePose cap = LiePoseUtil.get(localPlayer);
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
