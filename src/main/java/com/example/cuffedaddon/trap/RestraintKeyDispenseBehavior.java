package com.example.cuffedaddon.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;

/**
 * The release half of the trap set: a dispensed key takes a player's ARM
 * restraints off.
 *
 * <h2>Why arms only, and why that is enough</h2>
 * [stated]'s reasoning, kept here because it is the justification for not
 * building anything more elaborate: "if a player is wearing leg restraints, they
 * cant walk over to a dispenser anyways, and head restraints are all removable
 * by the wearer with free hands, so this is a good solution". Freeing the arms is
 * therefore the only release that a trapped player cannot perform for themselves,
 * and it is the only one a dispenser needs to offer.
 *
 * <h2>Reach</h2>
 * The block in front of the dispenser, same as every other trap here -
 * [stated]'s choice when asked. Height does NOT matter: unlike
 * {@link RestraintDispenseBehavior}, a key aimed at either half of a player
 * frees their arms, because there is only one slot a key here can ever address.
 *
 * <h2>Where the items end up</h2>
 * Both drop at the player's feet, as asked. The restraint drops because
 * {@code UnequipRestraint} is passed a null releaser, which is precisely
 * Cuffed's own "nobody is holding a hand out for this" path - it builds an
 * {@code ItemEntity} at the freed player rather than putting the item in
 * someone's inventory. The key is then thrown after it by hand, so the whole
 * transaction lands in one place at the player's feet instead of the key
 * pinging out of the dispenser on the other side of the wall.
 */
public class RestraintKeyDispenseBehavior extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        if (!CuffedAddonServerConfig.TRAP_DISPENSER_KEY_ENABLED.get()) {
            return super.execute(source, stack);
        }
        return tryRelease(source, stack) ? stack : super.execute(source, stack);
    }

    /**
     * @return true if a player was freed, in which case one key has been taken
     *         from {@code stack} and dropped at that player.
     */
    private static boolean tryRelease(BlockSource source, ItemStack stack) {
        ServerLevel level = source.getLevel();
        BlockPos targetPos = TrapDispenseUtil.targetPos(source);
        ServerPlayer target = TrapDispenseUtil.playerAt(level, targetPos);
        if (target == null) {
            return false;
        }

        RestrainableCapability cap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(target);
        if (cap == null) {
            return false;
        }
        AbstractArmRestraint arms = cap.getArmRestraint();
        if (arms == null) {
            return false;
        }

        // Cuffed's own key rule, from RestrainableCapability#onInteractedByOther:
        // a restraint opens if it has no key item at all (anything opens it) or
        // if the key being used is exactly its key item. Honouring it here is
        // what makes a shackles key open shackles and a handcuffs key not.
        if (arms.getKeyItem() != null && arms.getKeyItem() != stack.getItem()) {
            return false;
        }

        // Null releaser on purpose: that is the branch in Cuffed's
        // UnequipRestraint that drops the restraint at the freed player instead
        // of handing it to a captor.
        if (!cap.TryUnequipRestraint(target, null, RestraintType.Arm)) {
            return false;
        }

        dropAtPlayer(target, stack.copyWithCount(1));
        stack.shrink(1);
        return true;
    }

    private static void dropAtPlayer(ServerPlayer target, ItemStack toDrop) {
        ItemEntity dropped = new ItemEntity(target.level(), target.getX(), target.getY() + 0.6D, target.getZ(),
                toDrop);
        dropped.setDefaultPickUpDelay();
        target.level().addFreshEntity(dropped);
    }
}
