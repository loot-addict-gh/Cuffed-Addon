package com.example.cuffedaddon.init;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.effect.ElectrizationEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * This addon's own mob effects. Cuffed registers its own two
 * ({@code cuffed:restrained}, {@code cuffed:wounded}) the same way - a plain
 * DeferredRegister on ForgeRegistries.MOB_EFFECTS, with the icon read from
 * {@code assets/<modid>/textures/mob_effect/<name>.png}.
 */
public class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, CuffedAddon.MODID);

    /** Electric yellow, used for the effect's particle/status tint. */
    private static final int ELECTRIZATION_COLOR = 0xFFE14D;

    public static final RegistryObject<MobEffect> ELECTRIZATION = MOB_EFFECTS.register(
            "electrization",
            () -> new ElectrizationEffect(MobEffectCategory.HARMFUL, ELECTRIZATION_COLOR));

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
