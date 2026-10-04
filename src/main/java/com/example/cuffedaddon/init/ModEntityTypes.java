package com.example.cuffedaddon.init;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.entity.AnchorKnotEntity;
import com.example.cuffedaddon.entity.ArrowOfElectrizationEntity;
import com.example.cuffedaddon.entity.ArrowOfRestraintEntity;
import com.example.cuffedaddon.picker.PlayerPickerAnchorEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Same registration shape as Cuffed's own ModEntityTypes.CHAIN_KNOT - see
 * AnchorKnotEntity for why this is a separate entity type instead of reusing
 * ChainKnotEntity.
 */
public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CuffedAddon.MODID);

    public static final RegistryObject<EntityType<AnchorKnotEntity>> ANCHOR_KNOT = ENTITY_TYPES.register("anchor_knot",
            () -> EntityType.Builder.<AnchorKnotEntity>of(AnchorKnotEntity::new, MobCategory.MISC)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .setShouldReceiveVelocityUpdates(false)
                    .sized(6 / 16f, 0.5f).canSpawnFarFromPlayer().fireImmune()
                    .build(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "anchor_knot").toString()));

    /**
     * The invisible carrier entity a picked player rides (see
     * PlayerPickerAnchorEntity's own doc for why). Never spawned by a
     * spawn egg/naturally - only ever created directly by PlayerPickerUtil.
     * Small tracking range is enough since it only ever needs to matter to
     * its own rider and whoever's carrying/anchoring it; a short
     * updateInterval (unlike ANCHOR_KNOT's Integer.MAX_VALUE - that one
     * never moves after being placed, this one moves every tick) keeps its
     * position resynced promptly to nearby clients.
     */
    public static final RegistryObject<EntityType<PlayerPickerAnchorEntity>> PLAYER_PICKER_ANCHOR = ENTITY_TYPES.register(
            "player_picker_anchor",
            () -> EntityType.Builder.<PlayerPickerAnchorEntity>of(PlayerPickerAnchorEntity::new, MobCategory.MISC)
                    .noSave().noSummon()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(false)
                    .sized(0.01f, 0.01f)
                    .build(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "player_picker_anchor").toString()));

    /**
     * The two special arrows in flight (1.6.0; renamed and joined by a second
     * at 1.6.1). Every builder value is copied from vanilla's own
     * {@code EntityType.ARROW} - sized(0.5F, 0.5F), clientTrackingRange(4),
     * updateInterval(20) - because the brief is that they fly and hit exactly
     * like an arrow, and those three numbers are part of how an arrow feels.
     *
     * <p>Not noSave/noSummon: unlike PLAYER_PICKER_ANCHOR these are real
     * projectiles that can legitimately sit in a block through a chunk unload
     * and still be worth picking up afterwards, payload and all.
     */
    public static final RegistryObject<EntityType<ArrowOfRestraintEntity>> ARROW_OF_RESTRAINT = ENTITY_TYPES.register(
            "arrow_of_restraint",
            () -> EntityType.Builder.<ArrowOfRestraintEntity>of(ArrowOfRestraintEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "arrow_of_restraint").toString()));

    public static final RegistryObject<EntityType<ArrowOfElectrizationEntity>> ARROW_OF_ELECTRIZATION =
            ENTITY_TYPES.register("arrow_of_electrization",
            () -> EntityType.Builder.<ArrowOfElectrizationEntity>of(ArrowOfElectrizationEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "arrow_of_electrization").toString()));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
