package com.example.cuffedaddon.pose;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * registerCapabilities is called explicitly from CuffedAddon's constructor
 * (mod-bus listener), since RegisterCapabilitiesEvent only fires on the mod
 * bus. The attach/clone handlers below are ordinary gameplay events, so they
 * go through the usual @Mod.EventBusSubscriber (Forge bus).
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class LiePoseCapabilityEvents {

    public static final ResourceLocation LIE_POSE_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "lie_pose");

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ILiePose.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(LIE_POSE_ID, new LiePoseProvider());
        }
    }

    /**
     * ROUND 14 FIX (still needed): capabilities on a dying player's entity
     * are invalidated by LivingEntity#die() well before PlayerEvent.Clone
     * eventually fires (that only happens later, when the player actually
     * clicks respawn) - so this addon's own pose data has to be read here,
     * at LivingDeathEvent time, while the entity and its capability are
     * still valid. This is the exact same pitfall DeathRestraintHandler
     * (the ordinary-restraints equivalent of this feature) already works
     * around the same way.
     *
     * ROUND 18: [stated] reported the drop only happening once the player
     * actually clicked "Respawn", not immediately at death - because the
     * actual dropBedRestraint() call used to live in onPlayerClone, which
     * only fires at that later respawn-click moment. There was never a
     * real reason to wait that long: at LivingDeathEvent time the dying
     * entity's OWN level() is still perfectly valid (only its capabilities
     * get invalidated later, not level()/position()), so the drop can - and
     * now does - happen right here, immediately on death. The static map
     * this used to need to smuggle the locked position across to
     * onPlayerClone is gone; onPlayerClone now only has the (already-
     * redundant, kept for clarity) job of explicitly clearing the new
     * player's posed flag.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathCapture(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ILiePose cap = LiePoseUtil.get(player);
        if (cap != null && cap.isPosed() && player.level() instanceof ServerLevel serverLevel) {
            dropBedRestraint(serverLevel, cap.getLockedX(), cap.getLockedY(), cap.getLockedZ());
        }
    }

    // Carries pose state across the respawn/dimension-change clone. A real
    // death frees the player instead of carrying the lock into the respawn
    // (the item-drop itself now happens immediately at death - see
    // onDeathCapture above - not here).
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            // A freshly-attached capability on the new player already
            // defaults to posed=false, but set it explicitly for clarity/
            // safety rather than relying on that default silently.
            event.getEntity().getCapability(ModCapabilities.LIE_POSE)
                    .ifPresent(newCap -> newCap.setPosed(false));
            return;
        }

        // Non-death (dimension change): the original entity's capabilities
        // are NOT invalidated in this path the way death's Entity#remove()
        // invalidates them above, so a direct read here is fine.
        event.getOriginal().getCapability(ModCapabilities.LIE_POSE).ifPresent(oldCap ->
                event.getEntity().getCapability(ModCapabilities.LIE_POSE).ifPresent(newCap ->
                        newCap.deserializeNBT(oldCap.serializeNBT())));
    }

    private static void dropBedRestraint(ServerLevel serverLevel, double x, double y, double z) {
        ItemStack drop = new ItemStack(com.example.cuffedaddon.init.ModItems.BED_RESTRAINT.get());
        ItemEntity itemEntity = new ItemEntity(serverLevel, x, y, z, drop);
        serverLevel.addFreshEntity(itemEntity);
    }
}
