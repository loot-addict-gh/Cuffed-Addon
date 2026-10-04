package com.example.cuffedaddon.entity;

import com.example.cuffedaddon.items.ReinforcedBowItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;

/**
 * Shared base for the arrows only the Reinforced Bow is meant to fire.
 *
 * <p>Holds one thing: whether a Reinforced Bow actually fired this shot. That
 * flag is the second of the two layers that enforce
 * [stated]'s "it should be the only one able to fire restraining arrows (so
 * other modded bows cant)":
 *
 * <ol>
 *   <li>Neither arrow is in the {@code minecraft:arrows} item tag, which is what
 *       every tag-based ammo predicate in the game tests - so the vanilla bow,
 *       the vanilla crossbow and the great majority of modded bows will not load
 *       them at all.</li>
 *   <li>A modded bow that tests {@code instanceof ArrowItem} instead of the tag
 *       could still find them, and no predicate of ours can prevent that. So the
 *       special effect - the restraint, the electrization - is gated on this
 *       flag, and a foreign bow therefore fires something that does ordinary
 *       arrow damage and nothing more.</li>
 * </ol>
 *
 * <p>Crucially the payload is <b>not destroyed</b> in that case: each subclass's
 * {@code getPickupItem} still returns the fully loaded arrow, so a wasted shot
 * can be picked back up off the ground. Silently eating a crafted arrow would be
 * a much worse failure than a shot that did nothing.
 *
 * <p>The flag is saved to NBT rather than recomputed on load, because by the
 * time an arrow that stuck in a block and sat through a chunk unload comes back,
 * the bow that fired it is long out of anyone's hand.
 *
 * <p>Nothing else is overridden here or in either subclass: flight, damage,
 * sticking in blocks, pickup permission and the owner-immunity window are all
 * inherited from {@code AbstractArrow} unchanged. That is why these behave
 * exactly like vanilla arrows - because for all of that, they are.
 */
public abstract class AbstractReinforcedArrow extends AbstractArrow {

    private static final String TAG_FROM_REINFORCED_BOW = "FromReinforcedBow";

    private boolean firedFromReinforcedBow;

    /** Registry/reload constructor. Required by {@code EntityType.Builder.of}. */
    protected AbstractReinforcedArrow(EntityType<? extends AbstractReinforcedArrow> type, Level level) {
        super(type, level);
    }

    protected AbstractReinforcedArrow(EntityType<? extends AbstractReinforcedArrow> type,
                                      Level level, LivingEntity shooter) {
        super(type, shooter, level);
        this.firedFromReinforcedBow = isHoldingReinforcedBow(shooter);
    }

    /** Whether the special payload should fire on hit. See the class doc. */
    protected boolean firedFromReinforcedBow() {
        return firedFromReinforcedBow;
    }

    /**
     * {@code getUseItem()} is the authoritative answer during a bow release:
     * {@code LivingEntity#releaseUsingItem} calls {@code releaseUsing} on the use
     * item and only clears it afterwards, so the bow is still the use item while
     * {@code createArrow} runs. Both hands are checked as well, so the answer is
     * still right if some other mod fires an arrow without going through the
     * use-item machinery.
     */
    private static boolean isHoldingReinforcedBow(LivingEntity shooter) {
        if (shooter == null) {
            return false;
        }
        return shooter.getUseItem().getItem() instanceof ReinforcedBowItem
                || shooter.getMainHandItem().getItem() instanceof ReinforcedBowItem
                || shooter.getOffhandItem().getItem() instanceof ReinforcedBowItem;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_FROM_REINFORCED_BOW, firedFromReinforcedBow);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        firedFromReinforcedBow = tag.getBoolean(TAG_FROM_REINFORCED_BOW);
    }
}
