package com.example.cuffedaddon.pose;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mirrors cuffedaddon's LiePoseCapabilityEvents. The one real difference:
 * on death there's no item to drop (the Wall Restraint block itself isn't
 * consumed by use, unlike the Bed Restraint item) - the only thing that
 * needs to happen is flipping the block back to its unoccupied (red)
 * texture so someone else can use it again. registerCapabilities is called
 * explicitly from CuffedAddon's constructor (mod-bus listener), same as
 * LiePoseCapabilityEvents's own registerCapabilities.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class WallPoseCapabilityEvents {

    public static final ResourceLocation WALL_POSE_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "wall_pose");

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IWallPose.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(WALL_POSE_ID, new WallPoseProvider());
        }
    }

    /**
     * Same "capabilities are invalidated by the time PlayerEvent.Clone
     * fires" pitfall as cuffedaddon's onDeathCapture - read the pose data
     * here, at LivingDeathEvent time, while it's still valid.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathCapture(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        IWallPose cap = WallPoseUtil.get(player);
        if (cap != null && cap.isPosed()) {
            WallPoseUtil.unoccupyBlock(player.level(), cap.getLockedPrimaryPos());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().getCapability(ModCapabilities.WALL_POSE)
                    .ifPresent(newCap -> newCap.setPosed(false));
            return;
        }

        event.getOriginal().getCapability(ModCapabilities.WALL_POSE).ifPresent(oldCap ->
                event.getEntity().getCapability(ModCapabilities.WALL_POSE).ifPresent(newCap ->
                        newCap.deserializeNBT(oldCap.serializeNBT())));
    }
}
