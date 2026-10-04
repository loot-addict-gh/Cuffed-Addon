package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the scroll wheel back to a player wearing an Illusion arm restraint.
 *
 * <p>[stated]: <i>"the hotbar can only be accessed via the hotbar keys and not
 * with the scroll wheel, which is wrong"</i>. The hotbar KEYS came free with
 * Illusion because Cuffed blocks them through {@code gatherBlockedInputs()},
 * which Illusion already filters. <b>The scroll wheel does not go through that
 * list at all</b> - Cuffed's {@code MouseHandlerMixin#onScroll} cancels the whole
 * method on a bare {@code cap.armsRestrained()} check, which is true for an
 * Illusion restraint like any other.
 *
 * <h2>Third attempt, and why the first two failed (1.5.21)</h2>
 * Both earlier attempts were built on a wrong model of when Mixin resolves an
 * injection point. Mixin applies the mixins for one class in phases: it resolves
 * <b>every</b> mixin's injection points against the pristine method first, and
 * only then applies the injections, in ascending priority order. That single fact
 * explains both failures:
 *
 * <ul>
 *   <li><b>1.5.19, HEAD at priority 1500.</b> Both callbacks resolved to
 *       instruction zero. Cuffed (1000) was applied first and inserted its call
 *       there; ours was applied second and inserted before the same original
 *       instruction - which by then sat <i>after</i> Cuffed's code. A higher
 *       priority buys being applied later, and at a shared point being applied
 *       later means running later. Exactly backwards.</li>
 *   <li><b>1.5.20, RETURN at priority 1500.</b> Cuffed's cancellation compiles
 *       down to a {@code return} inside this method, so the plan was to attach to
 *       that instead of racing. But that return did not exist when our injection
 *       points were resolved - it is created later, when Cuffed's injection is
 *       applied. We only ever attached to vanilla's own returns, which are
 *       unreachable once Cuffed has cancelled at the top.</li>
 * </ul>
 *
 * <p>So the lever is the one that was never pulled: <b>a priority BELOW Cuffed's
 * default 1000</b>, which applies this mixin first and therefore runs this
 * callback first. Nothing here depends on Cuffed's mixin having been applied - the
 * target is vanilla and the Cuffed classes referenced are ordinary classes - so
 * being applied first is safe.
 *
 * <p>Same reasoning says why the redirect in {@code PlayerSelfRestraintIllusionMixin}
 * works at priority 1500: the method it reaches into is <i>merged</i> by Cuffed
 * rather than injected, and merging happens before injection points are resolved.
 *
 * <h2>If this still does nothing, the log now says why</h2>
 * Two one-shot log lines make the remaining ambiguity answerable without another
 * guess. {@code scroll hook reached} proves the obfuscated name in the refmap is
 * right and this callback runs at all; {@code scroll hook acting} proves the
 * Illusion branch was taken. Which of them is missing says which half is wrong.
 *
 * <p>The arithmetic is vanilla's own {@code Inventory#swapPaint} body, inlined:
 * step one slot against the scroll direction and wrap at both ends. The server
 * learns about it the same way it learns about a hotbar KEY press, so this is
 * consistent with the path [stated] already confirmed working. Deliberately
 * simpler than vanilla in one respect: vanilla scales by the "mouse wheel
 * sensitivity" option and accumulates fractional scroll first, whereas this steps
 * exactly one slot per notch - what default settings produce anyway.
 *
 * <p>Vanilla target, so {@code onScroll} needs a refmap entry - it is in
 * mixins.cuffedaddon.refmap.json under this class as {@code m_91526_(JDD)V}.
 */
@Mixin(value = MouseHandler.class, priority = 500)
public class MouseHandlerIllusionScrollMixin {

    // No initializers: a static initializer in a mixin cannot be merged into the
    // target class. Both default to false, which is what is wanted.
    private static boolean loggedReached;
    private static boolean loggedActing;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$illusionArmsCanScroll(long windowId, double deltaX, double deltaY, CallbackInfo ci) {
        if (!loggedReached) {
            loggedReached = true;
            CuffedAddon.LOGGER.info("Illusion scroll hook reached: this addon's callback is running inside "
                    + "MouseHandler.onScroll, so the mixin target is correct.");
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // A screen or overlay is up, or there is no player: leave it entirely
        // alone. Cuffed bails out on the same conditions.
        if (player == null || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }
        // Spectators scroll to change flight speed, not the hotbar. Cuffed does
        // not block them anyway, so there is nothing to give back.
        if (player.isSpectator()) {
            return;
        }

        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (cap == null || !IllusionUtil.isIllusion(cap.getRestraint(RestraintType.Arm))) {
            return;
        }

        // Horizontal scroll counts as vertical on some platforms - Forge does the
        // same substitution further down this method.
        double delta = deltaY != 0.0d ? deltaY : deltaX;
        if (delta == 0.0d) {
            return;
        }

        if (!loggedActing) {
            loggedActing = true;
            CuffedAddon.LOGGER.info("Illusion scroll hook acting: an Illusion arm restraint was detected and "
                    + "the hotbar slot is being changed here instead of by vanilla.");
        }

        Inventory inventory = player.getInventory();
        inventory.selected -= (int) Math.signum(delta);
        while (inventory.selected < 0) {
            inventory.selected += 9;
        }
        while (inventory.selected >= 9) {
            inventory.selected -= 9;
        }
        ci.cancel();
    }
}
