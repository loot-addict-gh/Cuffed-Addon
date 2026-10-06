package com.example.cuffedaddon.necklace;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.network.NecklaceSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Every state transition for the Key Necklace, in one place - the same reason
 * {@code ShockCollarUtil} exists: the interact listener, the left-click
 * self-gesture watcher, the two pose handlers, the death hook and the
 * {@code /cuffed <player> remove Necklace} command all put a necklace on or
 * take one off, and they must not drift apart on what that means.
 *
 * <p><b>Nothing here ever creates or destroys a necklace.</b> Every path moves
 * the one real {@link ItemStack} between a hand, this capability and the
 * ground, which is why {@link INecklaced#takeWorn()} exists rather than a
 * plain setter pair. The item is the player's only route to the Handcuffs Key
 * inside it, so losing one to a bookkeeping slip would be the same class of
 * problem the Player Picker's fire-proofing was added for.
 */
public final class NecklaceUtil {

    private NecklaceUtil() {
    }

    @Nullable
    public static INecklaced get(LivingEntity wearer) {
        return wearer.getCapability(ModCapabilities.NECKLACED).orElse(null);
    }

    /**
     * Whether this entity is wearing a necklace.
     *
     * <p>Typed on {@code LivingEntity} rather than {@code ServerPlayer} for two
     * separate reasons, both load-bearing.
     *
     * <p><b>Not {@code ServerPlayer}</b>, because the interact listener has to
     * reach the SAME verdict on both logical sides or the client falls through to
     * {@code Item#use} - the 1.5.17 lesson, written up on
     * {@code ShockCollarEvents#onInteractWithPlayer}. That works because
     * {@link NecklaceSyncPacket} mirrors this flag onto every tracking client's
     * copy of the wearer, exactly as the collar's does.
     *
     * <p><b>Not {@code Player}</b> (1.6.5, second pass), because a Fake Players
     * fake player wears one too. Their entity is a {@code PathfinderMob}, never a
     * {@code Player} - which is the wall the whole 1.5.x compatibility round was
     * built around - but nothing in this capability is Player-shaped: it holds one
     * worn ItemStack and nothing else. So the necklace needed no parallel
     * representation the way Cuffed's restraints did; the same capability is
     * simply attached to their entity as well.
     */
    public static boolean isWearing(LivingEntity wearer) {
        INecklaced cap = get(wearer);
        return cap != null && cap.isWearing();
    }

    // ---------------------------------------------------------------- apply

    /**
     * Takes ONE necklace out of {@code stack} and puts it on {@code target}.
     *
     * @return true if it went on. False leaves both the stack and the target
     *         untouched.
     */
    public static boolean applyNecklace(ServerPlayer actor, LivingEntity target, ItemStack stack) {
        if (!stack.is(ModItems.KEY_NECKLACE.get())) {
            return false;
        }
        INecklaced cap = get(target);
        if (cap == null || cap.isWearing()) {
            return false;
        }

        // split(1) rather than shrink(1) + a fresh ItemStack: it hands back the
        // exact item being worn, NBT and all, and leaves any remainder of the
        // stack in the actor's hand.
        cap.setWorn(stack.split(1));

        target.level().playSound(null, target.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER,
                SoundSource.PLAYERS, 0.8f, 1.1f);
        sync(target);
        return true;
    }

    // --------------------------------------------------------------- remove

    /**
     * Takes the necklace off {@code target} and hands it to {@code actor}.
     *
     * <p>{@code actor} and {@code target} are the same player for the
     * crouch + left-click self-removal; nothing here needs to distinguish the
     * two cases.
     */
    public static boolean removeNecklace(ServerPlayer actor, LivingEntity target) {
        INecklaced cap = get(target);
        if (cap == null || !cap.isWearing()) {
            return false;
        }

        give(actor, cap.takeWorn());
        target.level().playSound(null, target.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        sync(target);
        return true;
    }

    /**
     * Death handling: the necklace comes off and drops where its wearer died.
     *
     * <p>It DROPS rather than being destroyed because the necklace is ordinary
     * equipment, not a restraint being broken: there is no "Drop Item When
     * Broken" question to ask, and the key inside it belongs in the death pile
     * with everything else the player was carrying. Whether this is called at
     * all is {@code keepInventory}'s decision, not {@code freeAfterDeath}'s -
     * see {@code NecklaceEvents#onDeathCapture}.
     */
    public static void dropOnDeath(LivingEntity wearer) {
        INecklaced cap = get(wearer);
        if (cap == null || !cap.isWearing()) {
            return;
        }
        drop(wearer, cap.takeWorn());
        sync(wearer);
    }

    // ------------------------------------------------------------- plumbing

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void drop(LivingEntity at, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(at.level(), at.getX(), at.getY() + 0.6, at.getZ(), stack);
        entity.setDefaultPickUpDelay();
        at.level().addFreshEntity(entity);
    }

    /**
     * Pushes necklace state to every client that can see this player, plus the
     * player themselves.
     *
     * <p>Trackers need it for the same two reasons the collar's sync does: the
     * worn necklace is visible on other players and Forge capabilities are not
     * synced on their own. The addon's standing rule for anything that changes
     * how a player looks to others is three sends - on change (here), on
     * {@code StartTracking} and on login - all of which {@link NecklaceEvents}
     * has. A fake player needs only the first two; it never logs in.
     */
    public static void sync(LivingEntity wearer) {
        if (wearer.level().isClientSide()) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> wearer),
                new NecklaceSyncPacket(wearer.getId(), isWearing(wearer)));
    }
}
