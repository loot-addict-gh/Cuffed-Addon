package com.example.cuffedaddon.interact;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.collar.ShockCollarUtil;
import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.items.ShockCollarItem;
import com.example.cuffedaddon.necklace.NecklaceUtil;
import com.example.cuffedaddon.picker.PlayerPickerUtil;
import com.example.cuffedaddon.pose.LiePoseUtil;
import com.example.cuffedaddon.pose.WallPoseUtil;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The LEFT-CLICK self gestures for this addon's two neck items - put one on
 * yourself with a plain left click, take it off yourself with crouch + left
 * click.
 *
 * <h2>Why these are not interact events</h2>
 * [stated] asked for the Shock Collar and the Key Necklace to follow the same
 * control scheme as every Cuffed restraint:
 *
 * <pre>
 *   right click               -&gt; act on ANOTHER player
 *   left click                -&gt; act on YOURSELF
 * </pre>
 *
 * The right-click halves are ordinary {@code PlayerInteractEvent.EntityInteract}
 * listeners ({@code ShockCollarEvents}, {@code NecklaceEvents}). The left-click
 * halves cannot be: that event <b>can never fire on yourself</b>, which is
 * precisely the gap that left the Shock Collar with no self-removal for six
 * versions. A left click is an arm SWING, and that is what Cuffed itself reads.
 *
 * <h2>How Cuffed does it, and why this copies the mechanism exactly</h2>
 * Cuffed's own self-restraining lives in {@code PlayerMixin#tick} (injected at
 * TAIL into {@code Player#tick}), and it is a plain watch on the server-side
 * {@code swinging} flag with a one-shot latch:
 *
 * <pre>
 *   if (SERVER_CONFIG.ALLOW_SELF_RESTRAINING.get()) {   // defaults to TRUE
 *       if (swinging) {
 *           if (!hasProcessedSwing) {
 *               hasProcessedSwing = true;
 *               attemptToRemoveRestraint(me, cap);      // -&gt; onInteractedByOther(me, me, ...)
 *           }
 *       } else hasProcessedSwing = false;
 *   }
 * </pre>
 *
 * Reading the same flag the same way is what makes these gestures feel
 * identical to the vanilla-Cuffed ones in every case the player can tell
 * apart - clicking air, clicking a block, clicking a mob - without a new
 * client packet. ({@code PlayerInteractEvent.LeftClickEmpty} is client-only, so
 * the event route would have needed one, and would still not have solved the
 * ordering problem below.)
 *
 * <h2>Phase.END, and why that is the one correct hook</h2>
 * Three things have to happen in a fixed order inside one tick, and
 * {@code PlayerTickEvent} at {@code Phase.END} is the only point that sits
 * between them all:
 *
 * <ol>
 *   <li><b>After</b> {@code LiePoseEvents}/{@code WallPoseEvents} have run their
 *       {@code suppressSpuriousSwing} at {@code Phase.START}. Those two zero the
 *       flag when the swing was really the side effect of a right-click on a
 *       posed target (several of their branches return SUCCESS, and SUCCESS is
 *       the one result that swings the arm). Running after them means such a
 *       swing is already gone and can never be mistaken for a left click here -
 *       which would otherwise have let a crouching right-click on a bed- or
 *       wall-restrained player silently strip the ACTOR's own necklace.</li>
 *   <li><b>After</b> {@code LivingEntity#updateSwingTime} inside the tick body,
 *       which is where the flag would have been cleared had the swing already
 *       run its course.</li>
 *   <li><b>Before</b> Cuffed's own TAIL injection on the same method. Forge's
 *       {@code onPlayerPostTick} call is the last STATEMENT of
 *       {@code Player#tick}, and a Mixin TAIL injection goes in before the final
 *       RETURN - so Phase.END fires first. That ordering is what lets
 *       {@link #consume} below take a gesture away from Cuffed, and it is the
 *       whole mechanism behind the priority rule in the next section.</li>
 * </ol>
 *
 * <h2>The necklace takes priority over a non-keyed restraint</h2>
 * [stated]: <i>"crouch + left click to remove from yourself ... it should take
 * priority over any other restraints."</i>
 *
 * <p>Crouch + left click with an EMPTY hand is a gesture this addon and Cuffed
 * both want: it is Cuffed's own way of taking a non-keyed restraint off
 * yourself, and it is the natural match for the necklace (which is not keyed and
 * not a restraint). They share it cleanly because of point 3 above - when this
 * handler takes a necklace off, it clears {@code swinging}, so Cuffed's TAIL
 * hook finds nothing to process and the restraint survives that click. When the
 * player is NOT wearing a necklace, nothing is consumed and Cuffed's gesture
 * behaves exactly as it always has.
 *
 * <p>So it is two clicks: necklace first, then the restraint. <b>Illusion needs
 * no special case</b> - an Illusion arm restraint is still really there in
 * Cuffed's capability, so it simply comes second like any other non-keyed one.
 *
 * <h2>The arms gate, and the point of the whole feature</h2>
 * [stated]: <i>"the point would be to give players a key that they wont be able
 * to get when arm restrained."</i> Every gesture here is gated on
 * {@link #canReachOwnNeck}, so a handcuffed player cannot take their own
 * necklace off and therefore cannot reach the Handcuffs Key inside it. Cuffed's
 * own self-interaction has the same gate (its first line is
 * {@code if (!cap.armsRestrained())}), so this is the same rule, not a new one.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class SelfGestureEvents {

    /**
     * Players whose current swing has already been offered to the dispatcher.
     *
     * <p>The mirror of Cuffed's own {@code hasProcessedSwing} field, kept
     * outside the entity because this class is not a mixin. Entries are removed
     * the moment the flag goes false, which is at most a few ticks later, and on
     * logout for the case where it never does.
     *
     * <p>Server thread only.
     */
    private static final Set<UUID> PROCESSED_SWING = new HashSet<>();

    /**
     * {@code LivingEntity#swinging}, resolved by name.
     *
     * <p>This is the THIRD copy of this lookup in the addon -
     * {@code LiePoseEvents} and {@code WallPoseEvents} each have their own, and
     * both are confirmed working in game. It is duplicated rather than factored
     * out of them on purpose: those two are long-confirmed code in a round that
     * is not about them, and the lookup is six lines. The mapping is the one
     * already verified for those two: SRG {@code f_20911_} in a real installed
     * Forge, dev/official {@code swinging} in a Gradle {@code runClient}.
     *
     * <p>Reflection rather than a direct field access because a string literal
     * is not touched by reobfuscation, so one expression covers both
     * environments - and because the flag has to be WRITTEN as well as read
     * (see {@link #consume}).
     */
    private static final Field SWINGING_FIELD;

    static {
        Field field;
        try {
            field = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("f_20911_");
        } catch (NoSuchFieldException srgFailed) {
            try {
                field = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("swinging");
            } catch (NoSuchFieldException officialFailed) {
                field = null;
            }
        }
        if (field != null) {
            field.setAccessible(true);
        } else {
            // Loud rather than silent: without this field BOTH left-click self
            // gestures simply never fire, and "nothing happens" is the hardest
            // kind of report to act on. The two pose classes predate the logger
            // and fail quietly; this one does not.
            CuffedAddon.LOGGER.error(
                    "Could not resolve LivingEntity#swinging (tried f_20911_ and swinging). "
                    + "Left-click self-application and self-removal of the Shock Collar and "
                    + "Key Necklace will not work this run.");
        }
        SWINGING_FIELD = field;
    }

    private SelfGestureEvents() {
    }

    // ------------------------------------------------------------ the watch

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (SWINGING_FIELD == null) {
            return;
        }

        UUID id = player.getUUID();
        if (!isSwinging(player)) {
            PROCESSED_SWING.remove(id);
            return;
        }
        if (!PROCESSED_SWING.add(id)) {
            // Same swing, already offered - one gesture per click, exactly as
            // Cuffed's own latch gives.
            return;
        }

        if (dispatch(player)) {
            consume(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PROCESSED_SWING.remove(event.getEntity().getUUID());
    }

    // --------------------------------------------------------- the gestures

    /**
     * Offers this swing to each gesture in turn.
     *
     * <p>The Key Necklace is asked first, per [stated]'s priority rule. In
     * practice the order cannot be observed: each gesture below is identified by
     * what is in the player's main hand, and no two of them want the same thing
     * there. It is fixed anyway so that a future neck item cannot make the
     * ordering accidental.
     *
     * @return whether a gesture fired, and therefore whether this swing should
     *         be taken away from Cuffed's own self-restraining.
     */
    private static boolean dispatch(ServerPlayer player) {
        // A player who is bed-restrained, wall-restrained or picked up is not
        // in a position to be adjusting their own neckwear. None of these are
        // Cuffed arm restraints, so canReachOwnNeck below does not cover them.
        if (LiePoseUtil.isPosed(player) || WallPoseUtil.isPosed(player)
                || PlayerPickerUtil.isPicked(player)) {
            return false;
        }
        if (!canReachOwnNeck(player)) {
            return false;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean crouching = player.isShiftKeyDown();

        return tryNecklace(player, held, crouching) || tryCollar(player, held, crouching);
    }

    /**
     * [stated], for the necklace: <i>"right click to apply them to another
     * player, left click for yourself"</i> and <i>"crouch + left click to remove
     * them from yourself"</i>.
     *
     * <p>Removal takes an EMPTY hand, matching the other-player gesture
     * (<i>"crouch + rclick to remove them from another player (with empty
     * hand)"</i>) and matching Cuffed's own rule for a non-keyed restraint.
     */
    private static boolean tryNecklace(ServerPlayer player, ItemStack held, boolean crouching) {
        if (!crouching && held.is(ModItems.KEY_NECKLACE.get())) {
            return NecklaceUtil.applyNecklace(player, player, held);
        }
        if (crouching && held.isEmpty()) {
            return NecklaceUtil.removeNecklace(player, player);
        }
        return false;
    }

    /**
     * [stated], for the collar: <i>"right click to apply them to another player,
     * left click for yourself ... crouch + left click to remove them from
     * yourself (keeping the crouch here because its a keyed item but without
     * crouching the collar activates shocking)"</i>.
     *
     * <p>The two cases are told apart by the stack's own NBT rather than by the
     * crouch: an UNBOUND Shock Collar is a collar to put on, a BOUND one is that
     * collaring's remote. The crouch is kept on the removal side anyway, exactly
     * as asked - it is the safety that stops a careless click on the remote
     * taking the collar off instead of shocking, and it mirrors what a keyed
     * restraint's removal gesture looks like everywhere else.
     *
     * <p>{@code removeCollar} checks the binding, so crouch + left click while
     * holding SOMEONE ELSE's remote does nothing here and the swing is left
     * alone.
     */
    private static boolean tryCollar(ServerPlayer player, ItemStack held, boolean crouching) {
        if (!held.is(ModItems.SHOCK_COLLAR.get())) {
            return false;
        }
        if (ShockCollarItem.isBound(held)) {
            return crouching && ShockCollarUtil.removeCollar(player, held);
        }
        return !crouching && ShockCollarUtil.applyCollar(player, player, held);
    }

    // ------------------------------------------------------------- the gate

    /**
     * Whether this player's arms are free enough to reach their own neck.
     *
     * <p>The test is <b>"is there a real arm restraint on"</b> - ANY of them, with
     * Illusion as the only exception. That is Cuffed's own rule for its own self
     * gestures, letter for letter: {@code attemptToRemoveRestraint} opens with
     * {@code if (!cap.armsRestrained())}, and this addon's
     * {@code PlayerSelfRestraintIllusionMixin} already redirects exactly that call
     * to {@code armsRestrained() && !IllusionUtil.isIllusion(getArmRestraint())}.
     * Nothing new is being decided here; the same expression is reproduced because
     * this watcher reaches the swing before that mixin's target does.
     *
     * <h2>Shackles are NOT an exception, and 1.6.5 tried to make them one</h2>
     * The first draft gated on {@code AllowItemUse()} instead, reasoning from the
     * Shock Collar's STRUGGLE gate, where shackles really are the one arm restraint
     * loose enough to let you work at your own collar. [stated] tested it: with
     * shackles on, a click never becomes a self gesture at all - Cuffed routes it
     * into struggling out of the shackles instead - and asked for shackles to stay
     * unable to take their own necklace off. So the looser gate was both wrong and
     * unreachable, and it is gone. <b>Do not reintroduce it.</b> The collar's
     * struggle gate is a different question with a different answer and is
     * untouched.
     */
    private static boolean canReachOwnNeck(ServerPlayer player) {
        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (cap == null) {
            return true;
        }
        AbstractArmRestraint arms = cap.getArmRestraint();
        return arms == null || IllusionUtil.isIllusion(arms);
    }

    // ---------------------------------------------------------- the consume

    /**
     * Clears the swing so Cuffed's own TAIL hook on this same
     * {@code Player#tick} finds nothing to process.
     *
     * <p>This is what makes the necklace win the crouch + empty-hand gesture
     * against Cuffed's non-keyed restraint removal, and it also stops any
     * gesture here from double-firing as a Cuffed self-restrain with whatever is
     * in hand. Only called when a gesture actually fired, so every click this
     * addon does not claim reaches Cuffed untouched.
     *
     * <p>Harmless to the visible animation: the swing was already relayed to
     * every nearby client by {@code ServerboundSwingPacket}'s own handler before
     * this tick began, and the clicking player animates their arm locally. This
     * is the same write {@code LiePoseEvents}/{@code WallPoseEvents} have been
     * making since 1.4.14.
     */
    private static void consume(Player player) {
        try {
            SWINGING_FIELD.set(player, false);
        } catch (IllegalAccessException ignored) {
            // setAccessible(true) succeeded above, so this cannot happen. If it
            // somehow did, the only cost is Cuffed also acting on this swing.
        }
    }

    private static boolean isSwinging(Player player) {
        try {
            return SWINGING_FIELD.getBoolean(player);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }
}
