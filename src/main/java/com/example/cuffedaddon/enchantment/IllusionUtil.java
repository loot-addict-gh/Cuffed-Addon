package com.example.cuffedaddon.enchantment;

import com.example.cuffedaddon.init.ModEnchantments;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Everything Illusion needs to answer two questions: "does this restraint
 * actually restrain?" and "should this player's legs be held still?".
 *
 * <p>Read {@link IllusionEnchantment} first for what the enchantment is. This
 * class is how it takes effect.
 *
 * <h2>Where the restrictions actually live</h2>
 * Cuffed funnels every restriction through remarkably few places, which is why
 * this feature is a handful of small mixins rather than a rewrite:
 *
 * <ul>
 *   <li>{@code encodeRestraintDisabilities()} packs no-mining / no-item-use /
 *       no-movement / no-jumping into the amplifier of a {@code RESTRAINED_EFFECT}
 *       that is synced to the client, and <b>everything downstream on both sides
 *       reads it back from there</b>. One interception frees all four.</li>
 *   <li>{@code restraintsDisabled*()} are read separately in three places
 *       (movement checks in {@code PlayerMixin}, {@code EntityMixin} and
 *       {@code ModServerEvents}), so they are intercepted too.</li>
 *   <li>{@code gatherBlockedInputs()} is the single source of the blocked key
 *       codes - the inventory and hotbar blocking [stated] asked about.</li>
 *   <li>{@code renderOverlay} is the single source of the vision block, and this
 *       addon already funnels it through {@code CompactHudRenderer}.</li>
 * </ul>
 *
 * <h2>What Illusion deliberately does NOT change</h2>
 * Only the <em>wearer's own</em> restrictions lift. To everyone and everything
 * else you are still restrained: you can be frisked with a Possessions Box,
 * escorted, picked up with the Player Picker, freed with a key, and you still
 * count as occupying that slot so nobody can put a second restraint on you. That
 * is the point - the disguise has to hold from the outside.
 */
public final class IllusionUtil {

    private IllusionUtil() {
    }

    /** The four things a restraint can take away, as one enum. */
    public enum Restriction {
        MINING, ITEM_USE, MOVEMENT, JUMPING
    }

    public static boolean isIllusion(@Nullable AbstractRestraint restraint) {
        return restraint != null
                && RestraintEnchantmentUtil.levelOn(restraint, ModEnchantments.ILLUSION.get()) > 0;
    }

    /**
     * Whether this restraint really takes {@code which} away.
     *
     * <p>An Illusion restraint answers no to all four without consulting the
     * restraint's own rules, which is the whole enchantment in one method. Every
     * mixin that reproduces one of Cuffed's aggregation methods calls this instead
     * of {@code Allow*()} directly.
     */
    public static boolean blocks(@Nullable AbstractRestraint restraint, Restriction which) {
        if (restraint == null || isIllusion(restraint)) {
            return false;
        }
        return switch (which) {
            case MINING -> !restraint.AllowBreakingBlocks();
            case ITEM_USE -> !restraint.AllowItemUse();
            case MOVEMENT -> !restraint.AllowMovement();
            case JUMPING -> !restraint.AllowJumping();
        };
    }

    /** Server-side truth: is this player's LEG restraint an illusion? */
    public static boolean hasIllusionLegsServerSide(Player player) {
        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        return cap != null && isIllusion(cap.getRestraint(RestraintType.Leg));
    }

    // ------------------------------------------------------- client-side mirror

    /**
     * Players whose legs are currently held still, as this client understands it.
     *
     * <h2>Why this has to be synced at all</h2>
     * The leg lock is the one part of Illusion that is not the wearer's own
     * business. Everything else - the vision block, the muffling, the key
     * blocking - happens on the wearer's own screen or through a synced effect
     * amplifier. But a walking player's legs are animated independently on
     * <b>every</b> client that renders them, so if only the wearer's client held
     * them still, everyone else would watch them stroll about with swinging legs
     * and the disguise would be pointless.
     *
     * <h2>Why a static set rather than a capability</h2>
     * This addon's other cross-client state (lie pose, wall pose, the collar) uses
     * a capability, because that state has to survive death, respawn and a world
     * save. This does not: it is derived every tick from what the player is
     * wearing, so the server can always recompute it and a client that misses an
     * update gets the next one. A set of UUIDs is the whole thing, and it is
     * cleared when the client leaves a world.
     *
     * <p>It still follows the same three-part rule the rest of this addon does -
     * broadcast on change, resend on {@code StartTracking}, resend on login - see
     * {@link IllusionEvents}. Miss any one of those and a player who walks into
     * view later never learns about it.
     */
    private static final Set<UUID> CLIENT_ILLUSION_LEGS = ConcurrentHashMap.newKeySet();

    /**
     * Whether {@link #CLIENT_ILLUSION_LEGS} has anything in it at all (1.5.22).
     *
     * <h2>Why a flag in front of a set lookup</h2>
     * {@link #hasIllusionLegsClientSide} is called from
     * {@code HumanoidModelIllusionLegsMixin}, which sits on
     * {@code HumanoidModel#setupAnim} - so it runs for every humanoid the client
     * draws, every frame: zombies, skeletons, husks, drowned, piglins, armour
     * stands, the lot. In a mob farm or a raid that is hundreds of calls a frame
     * into a set that is empty essentially always.
     *
     * <p>The set used to be a {@code Collections.synchronizedSet}, so each of
     * those calls took and released a monitor. Uncontended locks are cheap but
     * they are not free, and they block the JIT from folding the call away.
     * {@code ConcurrentHashMap.newKeySet()} reads lock-free, and this volatile
     * flag means the overwhelmingly common empty case does not touch the set at
     * all.
     *
     * <p>Set to true BEFORE the add and recomputed AFTER the remove, so the flag
     * is never false while the set is non-empty. The other way round would drop a
     * disguise for a frame.
     */
    private static volatile boolean anyClientIllusionLegs;

    public static void setClientIllusionLegs(UUID player, boolean illusion) {
        if (illusion) {
            anyClientIllusionLegs = true;
            CLIENT_ILLUSION_LEGS.add(player);
        } else {
            CLIENT_ILLUSION_LEGS.remove(player);
            anyClientIllusionLegs = !CLIENT_ILLUSION_LEGS.isEmpty();
        }
    }

    public static boolean hasIllusionLegsClientSide(@Nullable LivingEntity entity) {
        return anyClientIllusionLegs && entity != null && CLIENT_ILLUSION_LEGS.contains(entity.getUUID());
    }

    public static void clearClientState() {
        CLIENT_ILLUSION_LEGS.clear();
        anyClientIllusionLegs = false;
        localIllusionLegs = false;
    }

    /**
     * Whether <b>this client's own player</b> has an Illusion leg restraint,
     * refreshed once a tick by {@code IllusionClientEvents}.
     *
     * <p>It exists because of one caller that has no player to hand.
     * {@code LocalPlayerMixin#canStartSprinting} is where Cuffed stops a
     * leg-restrained player sprinting, and it decides by building a <b>fresh</b>
     * restraint from the worn restraint's id and asking that throwaway instance
     * {@code AllowSprinting()}. A fresh instance carries no enchantments and knows
     * no wearer, so the answer cannot come from the restraint. It has to come from
     * somewhere ambient - and ambient is sound here, because that is the only
     * caller of {@code AllowSprinting()} anywhere in Cuffed or this addon, and it
     * only ever asks about the local player.
     *
     * <p>Always false on a server, where nothing calls it.
     */
    private static volatile boolean localIllusionLegs = false;

    public static void setLocalIllusionLegs(boolean illusion) {
        localIllusionLegs = illusion;
    }

    public static boolean localPlayerHasIllusionLegs() {
        return localIllusionLegs;
    }

    // -------------------------------------------------------------- the pose

    /**
     * Holds both legs in the plain standing pose.
     *
     * <p>Rotations only - the offsets vanilla applies for crouching and riding are
     * left alone, so an Illusion player crouches and sits normally and simply does
     * not swing their legs while doing it.
     *
     * <p><b>Zero rotation is not an invented pose.</b> It is exactly what vanilla
     * produces for a player standing still, which is precisely what a genuinely
     * leg-restrained player looks like, because they cannot move. Cuffed has no
     * leg animation of its own at all - {@code LegRestraintAnimationFlags} has one
     * value, {@code NONE} - so matching the idle pose is what makes an Illusion
     * player indistinguishable from a real one in every state a real one can
     * actually be in.
     */
    public static void lockLegs(HumanoidModel<?> model) {
        model.rightLeg.xRot = 0.0F;
        model.leftLeg.xRot = 0.0F;
        model.rightLeg.yRot = 0.0F;
        model.leftLeg.yRot = 0.0F;
        model.rightLeg.zRot = 0.0F;
        model.leftLeg.zRot = 0.0F;
    }
}
